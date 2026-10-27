package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * First-run setup (the prototype's, rebuilt): welcome, who's playing, finding your games (storage access, a folder for
 * any console, or one main folder), how each console's games start, and done. The same pages open from Settings later.
 * A Studio cartridge turns slowly on the right: it cycles through the consoles, and follows the one you're on while
 * you choose folders and emulators.
 */
class Setup(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
            private val games: Games, private val carts: Carts, private val host: Host, private val settings: Boolean, private val meta: Meta) {
    interface Host {
        fun hasStorage(): Boolean
        fun allowStorage()
        fun pickFolder(then: (String?) -> Unit)
        fun askText(title: String, value: String, then: (String) -> Unit)
        fun rescan(then: () -> Unit)
        fun toast(msg: String)
        fun finished(setup: Setup)
        /** Settings › Home picture changed something: show it now, behind the page. */
        fun artChanged()
    }

    private enum class Step { WELCOME, WHO, GAMES, EMUS, DONE, MENU, LIGHT, SAVED, ART, MEDIA, SOURCE }
    private var step = if (settings) Step.MENU else Step.WELCOME
    private val flow = listOf(Step.WELCOME, Step.WHO, Step.GAMES, Step.EMUS, Step.DONE)

    // the panel stacks its parts top to bottom, so a long line of text pushes the list down instead of covering it
    private val panel = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(48, 40, 48, 26)
        clipToPadding = false }   // the list reaches into the side padding, so its focus ring isn't cut off
    private val label = text(20f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .08f }
    private val title = text(44f, Fonts.display, Theme.fg)
    private val lead = text(23f, Fonts.body, Theme.fg2).apply { setLineSpacing(0f, 1.3f); maxLines = 4; ellipsize = TextUtils.TruncateAt.END }
    private val options = Options(ctx)
    private val dots = Dots(ctx)
    private val views = listOf(label, title, lead, options)

    // the cartridge on the right
    private val showcase = HashMap<String, Cart?>()
    private var shown: String? = null
    private var want = "gba"
    private var swapK = 0f      // 1 = fully shown, dips to 0 while one cartridge gives way to the next
    private var cycle = 0f
    private var time = 0f
    // the panel fills the left, a little in from the edges; the cartridge floats in the middle of what's left
    private val PX = 34; private val PY = 34; private val PW = 812; private val PH = 682
    private val cx = (PX + PW + Stage.W) / 2f; private val cy = Stage.H / 2f - 20f
    private val order = listOf("gba", "snes", "n64", "gb", "nes", "ds", "genesis", "gbc", "psp", "ps1", "android")
    private var shownCount = 0f   // the found-games count, counting up
    // Settings › Media: the source being set up, and how many games are missing each kind (counted when Media opens)
    private var srcId = "libretro"
    private var missing: Map<String, Int>? = null
    private val looks = ConsoleLooks(ctx)   // your console colors (Your saved colors): above init, which fills the menu with their count

    init {
        board.put(panel, PX, PY, PW, PH)
        val lp = { top: Int -> android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = top } }
        panel.addView(label, lp(0))
        panel.addView(title, lp(8))
        panel.addView(lead, lp(14))
        panel.addView(options, android.widget.LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 18; leftMargin = -30; rightMargin = -30 })   // the list fills what's left
        panel.addView(dots, android.widget.LinearLayout.LayoutParams(-1, 20).apply { topMargin = 6 })
        title.maxLines = 1; title.ellipsize = TextUtils.TruncateAt.END
        options.onFocus = { refreshShowcase() }
        show(step)
    }

    // ---------- pages ----------

    private fun show(s: Step) {
        step = s
        backdrop.previewArt = s == Step.ART
        if (s == Step.MEDIA) { count(); MediaJob.onChange = { if (step == Step.MEDIA) { if (!MediaJob.running) count(); fill() } } }
        options.reset()
        fill()
        dots.visibility = if (settings) View.INVISIBLE else View.VISIBLE
        dots.at = flow.indexOf(s); dots.invalidate()
        // the page rises in: label, title, text, then the options, a beat apart
        views.forEachIndexed { k, v -> v.alpha = 0f; v.translationY = 18f; v.animate().alpha(1f).translationY(0f).setStartDelay(k * 70L).setDuration(420).setInterpolator(DecelerateInterpolator(1.6f)).start() }
        refreshShowcase()
    }

    private fun page(l: String, t: String, d: String) {
        label.text = l.uppercase(); title.text = t; lead.text = d
        // a source's setup steps are longer than any other page's text
        val steps = step == Step.SOURCE
        lead.maxLines = if (steps) 14 else 4; lead.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (steps) 20f else 23f)
    }

    /** Fills the current page; called again whenever something on it changes. */
    fun fill() {
        val n = games.all.size
        val rows = ArrayList<Options.Row>()
        val done = if (settings) "Done" else "Continue"
        when (step) {
            Step.WELCOME -> {
                page("Welcome", "Shelf", "A home for your games, where each one is something you can pick up. Let’s set up this handheld; it takes about a minute.")
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 52f)
                rows += Options.Row("next", "Get started", primary = true)
            }
            Step.WHO -> {
                page(if (settings) "Settings" else "Step 1 of 3", "Who’s playing?", "Your name and color show at the top of the screen.")
                rows += Options.Row("name", "Your name", value = games.name.ifEmpty { "Choose" })
                Theme.profile.forEachIndexed { k, _ -> rows += Options.Row("color", Theme.profileNames[k], avatar = k, check = games.color == k, tag = k) }
                rows += Options.Row("next", done, primary = true)
                if (!settings) rows += Options.Row("back", "Back")
            }
            Step.GAMES -> if (!host.hasStorage()) {
                page(if (settings) "Settings" else "Step 2 of 3", "Let Shelf find your games", "Android asks you to allow access to all files. Switch it on for Shelf Native, then come back here.")
                rows += Options.Row("storage", "Allow access to my files", primary = true)
                if (!settings) { rows += Options.Row("next", "Skip for now"); rows += Options.Row("back", "Back") }
            } else {
                page(if (settings) "Games folders" else "Step 2 of 3", "Found ${shownCount.toInt()} ${if (n == 1) "game" else "games"}",
                    "Shelf looks in ROMs folders on internal storage and SD cards. Pick the folder for any console it missed, or the one folder they’re all in.")
                rows += Options.Row("next", done, primary = true)
                val roots = games.roots
                rows += Options.Row("root", "My main games folder", value = if (roots.isEmpty()) "Choose" else "Change", sub = roots.firstOrNull()?.let { prettyPath(it) } ?: "One folder with a folder for each console inside")
                val counts = games.counts()
                for ((k, name) in Games.NAMES) {
                    val c = counts[k] ?: 0
                    rows += Options.Row("folder", name, value = if (c == 0) "None yet" else "$c ${if (c == 1) "game" else "games"}",
                        sub = games.folder(k)?.let { prettyPath(it) } ?: if (c > 0) "Found on its own" else "Choose its folder", tag = k, dot = Theme.accentOf(k))
                }
                if (!settings) rows += Options.Row("back", "Back")
            }
            Step.EMUS -> {
                val counts = games.counts()
                val list = Games.NAMES.keys.filter { (counts[it] ?: 0) > 0 }.ifEmpty { Games.NAMES.keys.toList() }
                page(if (settings) "Emulators" else "Step 3 of 3", "How your games start",
                    if (games.installed.isEmpty()) "No emulators found yet. RetroArch, from the Play Store, plays most of these consoles; you can choose here any time."
                    else "Each console’s games open in an emulator. Shelf chose from what’s installed; press A to change one.")
                rows += Options.Row("next", done, primary = true)
                for (k in list) {
                    val chosen = games.emulator(k); val e = chosen ?: games.suggestion(k)
                    rows += Options.Row("emu", Games.NAMES[k] ?: k, value = e ?: "Choose", sub = when { e == null -> "Nothing installed for it yet"; chosen == null -> "Suggested"; else -> "" }, tag = k, dot = Theme.accentOf(k))
                }
                if (!settings) rows += Options.Row("back", "Back")
            }
            Step.DONE -> {
                val consoles = games.counts().size
                page("All set", "Ready when you are", if (n == 0) "No games yet. Settings › Games folders finds them whenever you’re ready."
                    else "$n ${if (n == 1) "game" else "games"} on $consoles ${if (consoles == 1) "console" else "consoles"}. Your most recent ones wait on Home.")
                rows += Options.Row("finish", "Let’s play", primary = true)
                rows += Options.Row("back", "Back")
            }
            Step.MENU -> {
                page("Settings", "Settings", "More settings come over from Shelf as the native app grows.")
                rows += Options.Row("go", "Games folders", value = "${games.all.size} games", tag = Step.GAMES)
                rows += Options.Row("go", "Emulators", tag = Step.EMUS)
                rows += Options.Row("go", "Your name and color", value = games.name, tag = Step.WHO)
                rows += Options.Row("go", "Lighting", value = "Brightness of the 3D", tag = Step.LIGHT)
                rows += Options.Row("go", "Home picture", value = Art.KINDS.firstOrNull { it.first == games.artKind }?.second ?: "", sub = "The picture of the selected game behind Home", tag = Step.ART)
                rows += Options.Row("go", "Media", value = Media.SOURCES.count { Media.connected(it.id, games.artKeys) }.let { "$it of ${Media.SOURCES.size} sources" },
                    sub = "Where box art, pictures, videos, details and manuals come from; what’s missing", tag = Step.MEDIA)
                rows += Options.Row("go", "Your saved colors", value = "${carts.shells.all.size + looks.count()}", sub = "Shells and console colors you’ve made, to delete", tag = Step.SAVED)
                rows += Options.Row("pageview", "A game’s page opens on", value = "‹  ${PAGE_VIEWS[games.pageView]}  ›", sub = "On the page itself, L2 and R2 go through all three")
                rows += Options.Row("launchfx", "Starting a game ends with", value = "‹  ${Curtain.NAMES[games.launchFx.coerceIn(0, Curtain.NAMES.size - 1)]}  ›", sub = "How the screen goes to black after the game goes in (and opens again when you come back)")
                rows += Options.Row("smooth", "Smoothness", value = "‹  ${SMOOTH[games.smoothness]}  ›", sub = SMOOTH_SUB[games.smoothness])
                rows += Options.Row("fps", "Show frame rate", value = if (games.showFps) "On" else "Off", sub = "A small meter in the top left: frames per second, and which part is slow")
                rows += Options.Row("sounds", "Sounds", value = if (games.sounds) "On" else "Off", sub = "Soft clicks and chimes as you move around")
                rows += Options.Row("again", "Run first-time setup again")
                rows += Options.Row("close", "Done", primary = true)
            }
            Step.SAVED -> {
                page("Settings", "Your saved colors", "The shells and console colors you’ve made. A deletes one; cartridges and consoles wearing it go back to their standard colors.")
                rows += Options.Row("close-saved", "Done", primary = true)
                val shells = carts.shells.all
                if (shells.isNotEmpty()) rows += Options.Row(Options.HEAD, "Cartridge shells")
                shells.forEach { rows += Options.Row("del-shell", it.name, sub = Materials.of(it.material).name, dot = it.color, tag = it) }
                val mine = Games.NAMES.keys.flatMap { sys -> looks.all(sys).map { sys to it } }
                if (mine.isNotEmpty()) rows += Options.Row(Options.HEAD, "Console colors")
                mine.forEach { (sys, l) -> rows += Options.Row("del-look", l.name, sub = Games.NAMES[sys] ?: sys, dot = l.parts["shell"]?.color, tag = l) }
                if (shells.isEmpty() && mine.isEmpty()) rows += Options.Row("none", "Nothing saved yet", sub = "Make shells in a game’s Customize, console colors in the Museum")
            }
            Step.ART -> {
                page("Settings", "Home picture", "Left and right change a value; it shows behind this page as you go.")
                rows += Options.Row("close-art", "Done", primary = true)
                rows += Options.Row("art", "Picture", value = "‹  ${Art.KINDS.firstOrNull { it.first == games.artKind }?.second}  ›", sub = "Which picture of the selected game (another if it doesn’t have that one)", tag = "kind")
                rows += Options.Row("art", "Brightness", value = "${(games.artBright * 100).roundToInt()}%", sub = "How bright it is, behind everything", tag = "bright")
                rows += Options.Row("art", "Height", value = "${((Stage.H - games.artTop) / Stage.H * 100).roundToInt()}% of the screen", sub = "How far up the screen it reaches", tag = "top")
                rows += Options.Row("art", "Fade length", value = "${(games.artFade * 100).roundToInt()}%", sub = "How much of it fades out toward the top", tag = "fade")
                rows += Options.Row("art", "Fade", value = "‹  ${Art.FADES[games.artFadeType]}  ›", sub = "A plain fade, dots that shrink, or lines that thin out", tag = "type")
                rows += Options.Row("art", "Pan speed", value = if (games.artPan == 0f) "Still" else "${"%.2g".format(games.artPan)}×", sub = "How fast it drifts across", tag = "pan")
                rows += Options.Row("art-reset", "Back to the starting look")
            }
            Step.MEDIA -> {
                page("Settings", "Media", "Where Shelf finds each game’s box art, pictures, video, details and manual. The free sources are always on; the others need a free account or key, and each one shows how to get it.")
                rows += Options.Row("close-media", "Done", primary = true)
                rows += Options.Row(Options.HEAD, "Your games")
                val job = MediaJob
                rows += if (job.running) Options.Row("media-stop", "Finding missing media…", value = "Stop", sub = job.status)
                    else Options.Row("media-all", "Find everything missing", value = missing?.let { m -> m.values.sum().let { if (it == 0) "Nothing missing" else "$it pieces" } } ?: "Counting…",
                        sub = if (job.status == "Done") "Finished: found ${job.found}. Anything still missing isn’t on your sources." else "For every game, from every connected source; carries on in the background")
                for (k in Media.KINDS) {
                    val n = missing?.get(k.id)
                    rows += Options.Row("media-kind", k.name, value = when { n == null -> "…"; n == 0 -> "All found"; else -> "$n missing" }, sub = if ((n ?: 0) > 0 && !job.running) "A finds them" else null, tag = k.id)
                }
                rows += Options.Row(Options.HEAD, "Sources")
                for (s in Media.SOURCES) rows += Options.Row("source", s.name, value = Media.status(s.id, games.artKeys),
                    sub = s.kinds.joinToString(", ") { Media.kind(it).name.lowercase() }.replaceFirstChar { it.uppercase() }, tag = s.id)
            }
            Step.SOURCE -> {
                val s = Media.source(srcId)
                page("Settings › Media", s.name, s.steps.mapIndexed { i, t -> if (s.steps.size > 1) "${i + 1}.  $t" else t }.joinToString("\n"))
                rows += Options.Row("close-source", "Done", primary = true)
                val mine = games.artKeys.optJSONObject(s.id) ?: org.json.JSONObject()
                for (f in s.fields) {
                    val v = mine.optString(f.key)
                    rows += Options.Row("field", f.label, value = if (v.isEmpty()) (if (f.optional) "Optional" else "Not set") else if (f.secret) "••••••" + v.takeLast(3) else v, tag = f)
                }
                if (s.fields.isNotEmpty()) rows += Options.Row("test", "Test it", value = Media.status(s.id, games.artKeys), sub = "A quick search, to check the account works")
            }
            Step.LIGHT -> {
            page("Settings", "Lighting", "Press left or right to change a value, and watch the cartridge. Your choice is saved.")
            rows += Options.Row("close-light", "Done", primary = true)
            rows += Options.Row("light", "Environment", value = "%.2f".format(games.lightEnv), sub = "The studio around the cartridges: reflections and fill light", tag = "env")
            rows += Options.Row("light", "Exposure", value = "%.2f".format(games.lightExposure), sub = "Everything brighter or darker", tag = "exposure")
            rows += Options.Row("light", "Key light", value = "%.2f".format(games.lightKey), sub = "The light from the upper left that makes highlights", tag = "key")
            rows += Options.Row("light-reset", "Back to the starting values", value = "%.2f · %.2f · %.2f".format(Look.ENV, Look.EXPOSURE, Look.KEY))
            }
        }
        if (step != Step.WELCOME) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 44f)
        options.rows = rows
        options.invalidate()
    }

    // ---------- buttons ----------

    fun input(a: String) {
        picker?.let { it.input(a); return }
        val r = options.current()
        if (r?.key == "light" && (a == "left" || a == "right")) return adjustLight(r.tag as String, if (a == "left") -1 else 1)
        if (r?.key == "art" && (a == "left" || a == "right" || a == "a")) return adjustArt(r.tag as String, if (a == "left") -1 else 1)
        if (r?.key == "launchfx" && (a == "left" || a == "right" || a == "a")) {
            val n = Curtain.NAMES.size; games.launchFx = (games.launchFx + (if (a == "left") n - 1 else 1)) % n; Sounds.play(Sounds.MOVE); return fill()
        }
        if (r?.key == "smooth" && (a == "left" || a == "right" || a == "a")) {
            games.smoothness = (games.smoothness + (if (a == "left") 2 else 1)) % 3
            stage.quality(games.smoothness); backdrop.lite = games.smoothness == 2; Sounds.play(Sounds.MOVE); return fill()
        }
        if (r?.key == "pageview" && (a == "left" || a == "right" || a == "a")) { games.pageView = (games.pageView + (if (a == "left") 2 else 1)) % 3; Sounds.play(Sounds.MOVE); return fill() }
        when (a) {
            "up" -> Sounds.play(if (options.move(-1)) Sounds.MOVE else Sounds.BUMP)
            "down" -> Sounds.play(if (options.move(1)) Sounds.MOVE else Sounds.BUMP)
            "b" -> back()
            "a" -> { val r = options.current() ?: return; Sounds.play(Sounds.PICK); act(r) }
        }
    }

    private fun back() = when {
        settings && step != Step.MENU -> show(Step.MENU)
        settings -> host.finished(this)
        flow.indexOf(step) > 0 -> show(flow[flow.indexOf(step) - 1])
        else -> Unit
    }
    private fun next() { if (settings) show(Step.MENU) else show(flow[flow.indexOf(step) + 1]) }

    private fun act(r: Options.Row) {
        when (r.key) {
            "next" -> { if (step == Step.EMUS) keepSuggestions(); if (step == Step.WHO && games.name.isEmpty()) askName { next() } else next() }
            "back" -> back()
            "name" -> askName { fill() }
            "color" -> { games.color = r.tag as Int; fill() }
            "storage" -> host.allowStorage()
            "root" -> host.pickFolder { p ->
                if (p == null) return@pickFolder host.toast("That folder can’t be used. Choose one on internal storage or an SD card.")
                games.roots = listOf(p); rescan { host.toast("Found ${games.all.size} games") }
            }
            "folder" -> { val k = r.tag as String; host.pickFolder { p ->
                if (p == null) return@pickFolder host.toast("That folder can’t be used. Choose one on internal storage or an SD card.")
                games.setFolder(k, p); rescan { val c = games.counts()[k] ?: 0; host.toast("${Games.NAMES[k]}: found $c ${if (c == 1) "game" else "games"}") }
            } }
            "emu" -> chooseEmulator(r.tag as String)
            "finish" -> { keepSuggestions(); games.setupDone = true; host.finished(this) }
            "go" -> show(r.tag as Step)
            "again" -> { games.setupDone = false; host.finished(this) }
            "close" -> host.finished(this)
            "close-light", "close-saved", "close-art", "close-media" -> show(Step.MENU)
            "close-source" -> show(Step.MEDIA)
            "source" -> { srcId = r.tag as String; show(Step.SOURCE) }
            "field" -> (r.tag as Media.Field).let { f ->
                val all = games.artKeys; val mine = all.optJSONObject(srcId) ?: org.json.JSONObject().also { all.put(srcId, it) }
                host.askText(f.label, mine.optString(f.key)) { v -> mine.put(f.key, v.trim()); games.artKeys = all; fill() }
            }
            "test" -> testSource()
            "media-all" -> findAll(null)
            "media-kind" -> findAll(setOf(r.tag as String))
            "media-stop" -> { MediaJob.cancel(); fill() }
            "del-shell" -> (r.tag as Shell).let { s -> confirmDelete(s.name) { carts.shells.delete(s.id) } }
            "del-look" -> (r.tag as ConsoleLook).let { l -> confirmDelete(l.name) { l.custom?.let { looks.delete(it) } } }
            "fps" -> { games.showFps = !games.showFps; fill() }
            "sounds" -> { games.sounds = !games.sounds; Sounds.enabled = games.sounds; Sounds.play(Sounds.PICK); fill() }
            "art-reset" -> { games.artKind = "shot"; games.artBright = Art.BRIGHT; games.artTop = Art.TOP; games.artFade = Art.FADE; games.artFadeType = Art.DOTS; games.artPan = Art.PAN; host.artChanged(); fill() }
            "light-reset" -> { games.lightEnv = Look.ENV; games.lightExposure = Look.EXPOSURE; games.lightKey = Look.KEY; applyLight(); fill() }
        }
    }

    private fun adjustArt(which: String, d: Int) {
        when (which) {
            "kind" -> games.artKind = Art.KINDS[(Art.KINDS.indexOfFirst { it.first == games.artKind } + d + Art.KINDS.size) % Art.KINDS.size].first
            "bright" -> games.artBright = (((games.artBright + .02f * d) * 50).roundToInt() / 50f).coerceIn(.02f, 1f)
            "top" -> games.artTop = (games.artTop - 20f * d).coerceIn(150f, 650f)
            "fade" -> games.artFade = ((games.artFade + .05f * d) * 20).roundToInt().coerceIn(1, 18) / 20f
            "type" -> games.artFadeType = (games.artFadeType + d + 3) % 3
            "pan" -> games.artPan = ((games.artPan + .25f * d) * 4).roundToInt().coerceIn(0, 16) / 4f
        }
        Sounds.play(Sounds.MOVE); host.artChanged(); fill()
    }

    private fun adjustLight(which: String, d: Int) {
        val step = .05f * d
        when (which) {
            "env" -> games.lightEnv = (games.lightEnv + step).coerceIn(0f, 3f)
            "exposure" -> games.lightExposure = (games.lightExposure + step).coerceIn(.05f, 3f)
            "key" -> games.lightKey = (games.lightKey + step).coerceIn(0f, 4f)
        }
        applyLight(); fill()
    }
    private fun applyLight() = stage.setLook(games.lightEnv, games.lightExposure, games.lightKey)

    private fun askName(then: () -> Unit) = host.askText("Your name", games.name) { v -> if (v.isNotBlank()) games.name = v.trim().take(24); then() }

    private fun rescan(then: () -> Unit) = host.rescan { fill(); then() }

    /** Whatever Shelf suggested, and you didn't change, becomes the choice. */
    private fun keepSuggestions() { for (k in Games.NAMES.keys) if (games.emulator(k) == null) games.suggestion(k)?.let { games.setEmulator(k, it) } }

    private fun ConsoleLooks.count() = Games.NAMES.keys.sumOf { all(it).size }

    /** Asks before deleting something you made. */
    private fun confirmDelete(name: String, delete: () -> Unit) {
        val p = Picker(ctx, "Delete $name?", listOf(Picker.Row("Delete", "It can’t be brought back"), Picker.Row("Keep it", "")), start = 1) { k ->
            if (k == 0) { delete(); host.toast("Deleted $name") }
            fill()
        }
        p.onClose = { board.removeView(p); picker = null }
        picker = p; board.put(p, 0, 0, 1334, 750)
    }

    // ---------- Settings › Media ----------

    private fun count() {
        missing = null
        val list = games.all
        Thread {
            val m = HashMap<String, Int>()
            for (g in list) { val i = meta.peek(g); for (k in Media.kindsFor(g.sys)) if (!Media.has(i, k.id)) m[k.id] = (m[k.id] ?: 0) + 1 }
            board.post { missing = m; if (step == Step.MEDIA) fill() }
        }.start()
    }
    private fun findAll(only: Set<String>?) {
        if (MediaJob.running) return
        MediaJob.onChange = { if (step == Step.MEDIA) { if (!MediaJob.running) count(); fill() } }
        MediaJob.start(games.all, only, meta, games.artKeys, ctx.cacheDir)
        host.toast(if (only == null) "Finding everything missing. It carries on if you leave Settings." else "Finding every game’s ${Media.kind(only.first()).name.lowercase()}")
        fill()
    }
    private fun testSource() {
        val id = srcId; val keys = Media.keys(games.artKeys)
        if (!Media.connected(id, games.artKeys)) return host.toast(if (id == "ss" && Media.ssWaiting) "Your account is saved. ScreenScraper starts working when Shelf’s developer credentials arrive." else "Fill in the fields first")
        host.toast("Testing…")
        Thread {
            val r = try { ArtSources.test(id, keys, ctx.cacheDir) } catch (e: Exception) { "Didn’t work: " + (e.message ?: "no answer") }
            board.post { host.toast(r) }
        }.start()
    }

    private var picker: Picker? = null
    private fun chooseEmulator(sys: String) {
        val choices = games.emulatorChoices(sys)
        val cur = games.emulator(sys) ?: games.suggestion(sys)
        val p = Picker(ctx, "Play ${Games.NAMES[sys]} games with", choices.map { (n, ok) -> Picker.Row(n, if (ok) "" else "Not installed") },
            start = maxOf(0, choices.indexOfFirst { it.first == cur }),
            note = "A RetroArch core has to be downloaded in RetroArch first (Main Menu › Load Core › Download a Core).") { k -> games.setEmulator(sys, choices[k].first); fill() }
        p.onClose = { board.removeView(p); picker = null }
        picker = p; board.put(p, 0, 0, 1334, 750)
    }

    // ---------- every frame ----------

    fun tick(dt: Float) {
        time += dt
        // the count on the games page climbs to the real number
        val n = games.all.size.toFloat()
        if (shownCount != n) { shownCount = if (shownCount < n) min(n, shownCount + maxOf(1f, (n - shownCount) * dt * 6f)) else n; if (step == Step.GAMES && host.hasStorage()) title.text = "Found ${shownCount.toInt()} ${if (n.toInt() == 1) "game" else "games"}" }
        // on the welcome and done pages the cartridge changes every few seconds
        if (options.current()?.tag !in Games.NAMES.keys) { cycle += dt; if (cycle > 4.5f) { cycle = 0f; want = order[(order.indexOf(want) + 1) % order.size] } }
        if (want != shown) swapK = maxOf(0f, swapK - dt / .28f) else swapK = min(1f, swapK + dt / .4f)
        if (swapK == 0f) { showcase[shown]?.place(-2000f, 0f, 0f, 0f, 0f, 1f); shown = want; backdrop.mood(Theme.accentOf(want)) }
        val cart = shown?.let { s -> showcase.getOrPut(s) { carts.make(s, Games.NAMES[s] ?: "Android games", null) } }
        val k = Backdrop.ease(swapK)
        cart?.place(cx, cy + sin(time * .8f) * 4f, 30f, time * .5f, -.12f, 300f * (.6f + .4f * k))
        cart?.brightness(.4f + .6f * k)
        backdrop.shadows.clear()
        cart?.let { backdrop.shadows.add(floatArrayOf(cx, cy + 170f, 260f * k, .8f * k)) }
    }

    private fun refreshShowcase() {
        val r = options.current()
        val t = r?.tag; if (t is String && t in Games.NAMES.keys) { want = t; cycle = 0f }
    }

    fun destroy() {
        backdrop.previewArt = false
        board.removeView(panel); picker?.let { board.removeView(it) }
        showcase.values.forEach { it?.destroy() }; showcase.clear()
        backdrop.shadows.clear()
    }

    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }
}

/** The list of choices on a setup page: one row each, the focused one ringed; scrolls when there are more than fit. */
class Options(ctx: Context) : View(ctx) {
    data class Row(val key: String, val label: String, val value: String? = null, val sub: String? = null, val primary: Boolean = false,
                   val avatar: Int? = null, val check: Boolean = false, val tag: Any? = null, val dot: Int? = null)
    var rows: List<Row> = emptyList(); set(v) { field = v; i = i.coerceIn(0, maxOf(0, v.size - 1)); if (rows.getOrNull(i)?.key == HEAD) move(1) || move(-1) }
    var onFocus: (() -> Unit)? = null
    /** False while the focus is elsewhere on the screen: the row stays chosen but isn't ringed. */
    var active = true
    private var i = 0
    private var top = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun h(r: Row) = if (r.key == HEAD) 46f else if (r.sub.isNullOrEmpty()) 64f else 80f
    companion object { /** A row that's a section heading: drawn small and skipped over. */ const val HEAD = "head" }

    fun reset() { i = rows.indexOfFirst { it.key != HEAD }.coerceAtLeast(0); top = 0f }
    fun current() = rows.getOrNull(i)
    /** Puts the focus on row k (not a heading), without the focus callback. */
    fun focus(k: Int) { if (k in rows.indices && rows[k].key != HEAD) { i = k; invalidate() } }
    fun move(d: Int): Boolean {
        var k = i + d
        while (k in rows.indices && rows[k].key == HEAD) k += d   // headings aren't stops
        if (k !in rows.indices) { if (d < 0 && i > 0) top = 0f; invalidate(); return false }   // at the top, show its heading too
        i = k; onFocus?.invoke(); invalidate(); return true
    }

    override fun onDraw(c: Canvas) {
        // keep the focused row in view
        var y0 = 0f; for (k in 0 until i) y0 += h(rows[k]) + 8
        val hi = rows.getOrNull(i)?.let { h(it) } ?: 0f
        val yTop = if (i > 0 && rows[i - 1].key == HEAD) y0 - h(rows[i - 1]) - 8 else y0   // a row's heading comes into view with it
        if (yTop < top) top = yTop; if (y0 + hi > top + height - 12) top = y0 + hi - height + 12
        c.save(); c.clipRect(0f, 0f, width.toFloat(), height.toFloat()); c.translate(0f, 4f - top)   // 4: room for the focus ring
        var y = 0f
        rows.forEachIndexed { k, r ->
            val rh = h(r); val on = k == i && active; val box = RectF(30f, y, width - 30f, y + rh)
            if (r.key == HEAD) {
                p.typeface = Fonts.bold; p.textSize = 17f; p.letterSpacing = .1f; p.color = Theme.accentOf(null)
                c.drawText(r.label.uppercase(), 42f, y + rh - 14f, p); p.letterSpacing = 0f
                y += rh + 8; return@forEachIndexed
            }
            p.style = Paint.Style.FILL
            p.color = when { r.primary -> Theme.accentOf(null); on -> Theme.chip; else -> Color.TRANSPARENT }; c.drawRoundRect(box, 22f, 22f, p)
            if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 3, box.top - 3, box.right + 3, box.bottom + 3), 25f, 25f, p); p.style = Paint.Style.FILL }
            var x = 54f
            r.avatar?.let { Theme.drawAvatar(c, x + 16, y + rh / 2, 16f, it); x += 46 }
            r.dot?.let { p.color = it; c.drawCircle(x + 6, y + (if (r.sub.isNullOrEmpty()) rh / 2 else 30f), 6.5f, p); x += 24 }
            val fg = if (r.primary) Theme.selFg else Theme.fg
            p.typeface = Fonts.bold; p.textSize = 23f; p.color = fg
            c.drawText(r.label + if (r.check) "  ✓" else "", x, y + (if (r.sub.isNullOrEmpty()) rh / 2 + 8 else 38f), p)
            r.value?.let { p.typeface = Fonts.bold; p.textSize = 21f; p.color = if (r.primary) fg else Theme.fg2; p.textAlign = Paint.Align.RIGHT; c.drawText(it, width - 54f, y + (if (r.sub.isNullOrEmpty()) rh / 2 + 8 else 38f), p); p.textAlign = Paint.Align.LEFT }
            r.sub?.takeIf { it.isNotEmpty() }?.let { p.typeface = Fonts.body; p.textSize = 18f
                p.color = if (r.primary) Color.argb(200, Color.red(Theme.selFg), Color.green(Theme.selFg), Color.blue(Theme.selFg)) else Theme.fg2   // dark on the light button
                c.drawText(TextUtils.ellipsize(it, android.text.TextPaint(p), width - x - 60, TextUtils.TruncateAt.START).toString(), x, y + 64f, p) }
            y += rh + 8
        }
        c.restore()
        // a soft fade at the bottom when there's more below
        if (y - top > height) { p.shader = android.graphics.LinearGradient(0f, height - 40f, 0f, height.toFloat(), Color.TRANSPARENT, Color.argb(200, 12, 17, 44), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, height - 40f, width.toFloat(), height.toFloat(), p); p.shader = null }
    }
}

/** The setup's progress: five short bars, filled up to this page. */
class Dots(ctx: Context) : View(ctx) {
    var at = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        for (k in 0 until 5) { p.color = if (k <= at) Theme.accentOf(null) else Theme.chip; c.drawRoundRect(RectF(k * 44f, 6f, k * 44f + 35, 12f), 3f, 3f, p) }
    }
}

/** The game page's three views, in order (Games.pageView). */
val PAGE_VIEWS = listOf("Cartridge", "Open box", "Closed box")

private val SMOOTH = listOf("Best looks", "Balanced", "Smoothest")
private val SMOOTH_SUB = listOf("The smoothest edges; the most work for the handheld", "Softer edges, less work for the 3D",
    "Lightest: the sky's stars hold still, edges smoothed more cheaply")
