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
        fun openDocument(mimes: Array<String>, then: (android.net.Uri?) -> Unit)
        fun createDocument(name: String, mime: String, then: (android.net.Uri?) -> Unit)
        fun openMediaFolder()
        fun rescan(then: () -> Unit)
        fun toast(msg: String)
        fun finished(setup: Setup)
        /** Settings › Home picture changed something: show it now, behind the page. */
        fun artChanged()
        /** Your collections and hidden games (Settings › Library). */
        fun collections(): Collections
    }

    private enum class Step { WELCOME, WHO, GAMES, EMUS, DONE, HOME, LIBRARY, PAGES, LIGHT, MEDIA, SOURCE, SAVED, SYSTEM }
    private var step = if (settings) Step.WHO else Step.WELCOME
    private val flow = listOf(Step.WELCOME, Step.WHO, Step.GAMES, Step.EMUS, Step.DONE)

    // Settings is two panes in one frame: its sections down the left, the chosen section's settings on the right, which
    // change as you move down the sections (nothing replaces the whole screen). First-run setup uses the right pane alone.
    private class Section(val step: Step, val name: String)
    private val sections = listOf(Section(Step.WHO, "You"), Section(Step.GAMES, "Games"), Section(Step.EMUS, "Emulators"),
        Section(Step.HOME, "Home"), Section(Step.LIBRARY, "Library"), Section(Step.PAGES, "Game pages"), Section(Step.LIGHT, "Lighting"), Section(Step.MEDIA, "Media"),
        Section(Step.SAVED, "Saved colors"), Section(Step.SYSTEM, "System"))
    private val frame = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL; background = round(Theme.panel, 33f, Theme.line)
        setPadding(if (settings) 30 else 48, 40, 48, 26); clipToPadding = false }
    private val side = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; clipToPadding = false }
    private val sideTitle = text(20f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .08f; text = "SETTINGS" }
    private val cats = Options(ctx).apply { compact = true; keep = true }
    private val divider = View(ctx).apply { setBackgroundColor(Theme.line) }
    /** Which pane has the focus in Settings: the sections (left) or the settings (right). */
    private var inSide = settings
    // the right pane stacks its parts top to bottom, so a long line of text pushes the list down instead of covering it
    private val panel = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL
        clipToPadding = false; clipChildren = false }   // the list reaches into the side padding, so its focus ring isn't cut off
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
    private val PX = 34; private val PY = 34; private val PW = if (settings) 930 else 812; private val PH = 682
    private val cx = (PX + PW + Stage.W) / 2f; private val cy = Stage.H / 2f - 20f
    private val cartSize = if (settings) 235f else 300f
    private val order = listOf("gba", "snes", "n64", "gb", "nes", "ds", "genesis", "gbc", "psp", "ps1", "android")
    private var shownCount = 0f   // the found-games count, counting up
    // Settings › Media: the source being set up, and how many games are missing each kind (counted when Media opens)
    private var srcId = "libretro"
    private var missing: Map<String, Int>? = null
    private val looks = ConsoleLooks(ctx)   // your console colors (Your saved colors): above init, which fills the menu with their count

    init {
        board.put(frame, PX, PY, PW, PH)
        frame.clipChildren = false
        if (settings) {
            side.addView(sideTitle, android.widget.LinearLayout.LayoutParams(-1, -2).apply { leftMargin = 12 })
            side.addView(cats, android.widget.LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 26; leftMargin = -30; rightMargin = -30 })
            frame.addView(side, android.widget.LinearLayout.LayoutParams(232, -1))
            frame.addView(divider, android.widget.LinearLayout.LayoutParams(2, -1).apply { leftMargin = 22; rightMargin = 34; topMargin = 6; bottomMargin = 10 })
            cats.rows = sections.map { Options.Row("section", it.name, tag = it.step) }
            cats.onFocus = { val st = cats.current()?.tag as? Step; if (st != null && st != step) { Sounds.play(Sounds.MOVE); show(st, quick = true) } }
        }
        frame.addView(panel, android.widget.LinearLayout.LayoutParams(0, -1, 1f))
        val lp = { top: Int -> android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = top } }
        panel.addView(label, lp(0))
        panel.addView(title, lp(8))
        panel.addView(lead, lp(14))
        panel.addView(options, android.widget.LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 18; leftMargin = -30; rightMargin = -30 })   // the list fills what's left
        panel.addView(dots, android.widget.LinearLayout.LayoutParams(-1, 20).apply { topMargin = 6 })
        title.maxLines = 1; title.ellipsize = TextUtils.TruncateAt.END
        options.onFocus = { refreshShowcase(); preview() }
        show(step)
        focusSide(settings)
    }

    /** Moves the focus between Settings' two panes. */
    private fun focusSide(on: Boolean) {
        inSide = on
        cats.active = on; options.active = !on || !settings
        cats.invalidate(); options.invalidate(); preview()
    }

    /** While the Home picture's settings are chosen, the picture shows over everything else's fading, so changes can be seen. */
    private fun preview() { backdrop.previewArt = step == Step.HOME && !inSide && options.current()?.key?.startsWith("art") == true }

    // ---------- pages ----------

    private fun show(s: Step, quick: Boolean = false) {
        step = s
        if (s == Step.MEDIA) { count(); MediaJob.onChange = { if (step == Step.MEDIA) { if (!MediaJob.running) count(); fill() } } }
        options.reset()
        fill()
        dots.visibility = if (settings) View.GONE else View.VISIBLE
        dots.at = flow.indexOf(s); dots.invalidate()
        // the page rises in: label, title, text, then the options, a beat apart; moving down Settings' sections, the right
        // pane changes with a shorter, quieter version of it, so it keeps up with the d-pad
        views.forEachIndexed { k, v -> v.animate().cancel(); v.alpha = 0f; v.translationY = if (quick) 8f else 18f
            v.animate().alpha(1f).translationY(0f).setStartDelay(k * (if (quick) 25L else 70L)).setDuration(if (quick) 220 else 420).setInterpolator(DecelerateInterpolator(1.6f)).start() }
        refreshShowcase(); preview()
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
                if (!settings) { rows += Options.Row("next", done, primary = true); rows += Options.Row("back", "Back") }
            }
            Step.GAMES -> if (!host.hasStorage()) {
                page(if (settings) "Settings" else "Step 2 of 3", "Let Shelf find your games", "Android asks you to allow access to all files. Switch it on for Shelf Native, then come back here.")
                rows += Options.Row("storage", "Allow access to my files", primary = true)
                if (!settings) { rows += Options.Row("next", "Skip for now"); rows += Options.Row("back", "Back") }
            } else {
                page(if (settings) "Settings" else "Step 2 of 3", "Found ${shownCount.toInt()} ${if (n == 1) "game" else "games"}",
                    "Shelf looks in ROMs folders on internal storage and SD cards. Pick the folder for any console it missed, or the one folder they’re all in.")
                if (!settings) rows += Options.Row("next", done, primary = true)
                val roots = games.roots
                rows += Options.Row("root", "My main games folder", value = if (roots.isEmpty()) "Choose" else "Change", sub = roots.firstOrNull()?.let { prettyPath(it) } ?: "One folder with a folder for each console inside")
                val counts = games.counts()
                for ((k, name) in Games.NAMES) {
                    if (k == "android") continue   // Android games come from Apps, not a folder
                    val c = counts[k] ?: 0
                    rows += Options.Row("folder", name, value = if (c == 0) "None yet" else "$c ${if (c == 1) "game" else "games"}",
                        sub = games.folder(k)?.let { prettyPath(it) } ?: if (c > 0) "Found on its own" else "Choose its folder", tag = k, dot = Theme.accentOf(k))
                }
                if (!settings) rows += Options.Row("back", "Back")
            }
            Step.EMUS -> {
                val counts = games.counts()
                val list = Games.NAMES.keys.filter { it != "android" && (counts[it] ?: 0) > 0 }.ifEmpty { Games.NAMES.keys.filter { it != "android" } }   // Android games open on their own
                page(if (settings) "Settings" else "Step 3 of 3", "How your games start",
                    if (games.installed.isEmpty()) "No emulators found yet. RetroArch, from the Play Store, plays most of these consoles; you can choose here any time."
                    else "Each console’s games open in an emulator. Shelf chose from what’s installed; press A to change one.")
                if (!settings) rows += Options.Row("next", done, primary = true)
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
            Step.HOME -> {
                page("Settings", "Home", "How your games line up, and the picture behind them. Left and right change a value; the picture shows behind as you go.")
                rows += Options.Row("homeboxes", "Home shows", value = "‹  ${if (games.homeBoxes) "Boxes" else "Cartridges"}  ›", sub = "Your games as cartridges, or as their closed boxes")
                rows += Options.Row("theme", "Theme", value = "‹  ${Theme.look.name}  ›", sub = Theme.look.note)
                rows += Options.Row("homescroll", "Scrolling", value = "‹  ${Home.STYLES[games.homeScroll.coerceIn(0, Home.STYLES.size - 1)]}  ›", sub = SCROLL_SUB[games.homeScroll.coerceIn(0, SCROLL_SUB.size - 1)])
                rows += Options.Row(Options.HEAD, "The picture behind")
                rows += Options.Row("art", "Picture", value = "‹  ${Art.KINDS.firstOrNull { it.first == games.artKind }?.second}  ›", sub = "Which picture of the selected game (another if it doesn’t have that one)", tag = "kind")
                rows += Options.Row("art", "Brightness", value = "${(games.artBright * 100).roundToInt()}%", sub = "How bright it is, behind everything", tag = "bright")
                rows += Options.Row("art", "Height", value = "${((Stage.H - games.artTop) / Stage.H * 100).roundToInt()}% of the screen", sub = "How far up the screen it reaches", tag = "top")
                rows += Options.Row("art", "Fade length", value = "${(games.artFade * 100).roundToInt()}%", sub = "How much of it fades out toward the top", tag = "fade")
                rows += Options.Row("art", "Fade", value = "‹  ${Art.FADES[games.artFadeType]}  ›", sub = "A plain fade, dots that shrink, or lines that thin out", tag = "type")
                rows += Options.Row("art", "Pan speed", value = if (games.artPan == 0f) "Still" else "${"%.2g".format(games.artPan)}×", sub = "How fast it drifts across", tag = "pan")
                rows += Options.Row("art-reset", "Back to the starting look")
            }
            Step.LIBRARY -> {
                val cols = host.collections()
                page("Settings", "Library", "Lists that fill themselves, and the games you've hidden.")
                rows += Options.Row("smart", "Automatic collections", value = if (cols.smartOn) "On" else "Off", sub = "Recently added, Not played yet and Most played, in the Library's list")
                rows += Options.Row(Options.HEAD, "Hidden games")
                val n = games.all.count { cols.hidden(it) }
                rows += Options.Row("none", "Hidden", value = "$n ${if (n == 1) "game" else "games"}", sub = "Hide one with X on it, or in its Game settings; Library › Hidden shows them")
                rows += Options.Row("hiddenentry", "Hidden in the Library's list", value = if (cols.hiddenEntry) "Shown" else "Not shown", sub = "Not shown: press Select on the Library's list, then your code")
                if (cols.hasCode) rows += Options.Row("changecode", "Change the code", sub = "Asks for the one you have now")
                rows += Options.Row("resetcode", if (cols.hasCode) "Reset the code" else "Choose a code", sub = if (cols.hasCode) "A new one, without the old one (in case you forget it)" else "Four presses of the d-pad and A, B, X, Y")
            }
            Step.PAGES -> {
                page("Settings", "Game pages", "How a game’s page first shows it, how labels look, and how starting a game ends.")
                rows += Options.Row("pageview", "A page opens on", value = "‹  ${PAGE_VIEWS[games.pageView]}  ›", sub = "On the page itself, L2 and R2 go through all three")
                rows += Options.Row("labelfinish", "Label finish", value = "‹  ${Labels.NAMES[games.labelFinish]}  ›", sub = "Every game's label, unless one has its own (in its Customize shell)")
                rows += Options.Row("launchfx", "Starting a game ends with", value = "‹  ${Curtain.NAMES[games.launchFx.coerceIn(0, Curtain.NAMES.size - 1)]}  ›", sub = "How the screen goes to black after the game goes in")
            }
            Step.SYSTEM -> {
                page("Settings", "System", "How smooth it runs, how it sounds, and starting over.")
                rows += Options.Row("smooth", "Smoothness", value = "‹  ${SMOOTH[games.smoothness]}  ›", sub = SMOOTH_SUB[games.smoothness])
                rows += Options.Row("fps", "Show frame rate", value = if (games.showFps) "On" else "Off", sub = "A small meter in the top left: frames per second, and which part is slow")
                rows += Options.Row("sounds", "Sounds", value = if (games.sounds) "On" else "Off", sub = "Soft clicks and chimes as you move around")
                if (games.sounds) rows += Options.Row("soundpack", "Sound pack", value = "‹  ${Sounds.PACKS[games.soundPack.coerceIn(0, Sounds.PACKS.size - 1)]}  ›",
                    sub = PACK_SUB[games.soundPack.coerceIn(0, PACK_SUB.size - 1)])
                rows += Options.Row("again", "Run first-time setup again")
                rows += Options.Row("none", "Version", value = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" })
            }
            Step.SAVED -> {
                page("Settings", "Your saved colors", "The shells and console colors you’ve made. A deletes one; cartridges and consoles wearing it go back to their standard colors.")
                val shells = carts.shells.all
                if (shells.isNotEmpty()) rows += Options.Row(Options.HEAD, "Cartridge shells")
                shells.forEach { rows += Options.Row("del-shell", it.name, sub = Materials.of(it.material).name, dot = it.color, tag = it) }
                val mine = Games.NAMES.keys.flatMap { sys -> looks.all(sys).map { sys to it } }
                if (mine.isNotEmpty()) rows += Options.Row(Options.HEAD, "Console colors")
                mine.forEach { (sys, l) -> rows += Options.Row("del-look", l.name, sub = Games.NAMES[sys] ?: sys, dot = l.parts["shell"]?.color, tag = l) }
                if (shells.isEmpty() && mine.isEmpty()) rows += Options.Row("none", "Nothing saved yet", sub = "Make shells in a game’s Customize, console colors in the Museum")
            }
            Step.MEDIA -> {
                page("Settings", "Media", "Where Shelf finds each game’s box art, pictures, video, details and manual. The free sources are always on; the others need a free account or key, and each one shows how to get it.")
                rows += Options.Row(Options.HEAD, "Your games")
                val job = MediaJob
                rows += if (job.running) Options.Row("media-stop", "Finding missing media…", value = "Stop", sub = job.status)
                    else Options.Row("media-all", "Find everything missing", value = missing?.let { m -> m.values.sum().let { if (it == 0) "Nothing missing" else "$it pieces" } } ?: "Counting…",
                        sub = if (job.status == "Done") "Finished: found ${job.found}. Anything still missing isn’t on your sources." else "For every game, from every connected source; carries on in the background")
                for (k in Media.KINDS) {
                    val n = missing?.get(k.id)
                    rows += Options.Row("media-kind", k.name, value = when { n == null -> "…"; n == 0 -> "All found"; else -> "$n missing" }, sub = if ((n ?: 0) > 0 && !job.running) "A finds them" else null, tag = k.id)
                }
                rows += Options.Row(Options.HEAD, "Your media folder")
                rows += Options.Row("media-open", "Open the media folder", value = if (host.hasStorage()) "Shelf › Media" else "Inside Shelf",
                    sub = "Add your own pictures, videos and manuals to a game's folder: named box, back, spine, cartridge, title, screenshot (any picture type), a video, a PDF")
                rows += Options.Row("media-backup", "Back up all media", sub = "Every game's pictures, videos, manuals and details, in one zip, where you choose")
                rows += Options.Row("media-restore", "Restore a backup", sub = "Puts a backup's media back for every game it has (by the games' file names)")
                rows += Options.Row(Options.HEAD, "Sources")
                for (s in Media.SOURCES) rows += Options.Row("source", s.name, value = Media.status(s.id, games.artKeys),
                    sub = s.kinds.joinToString(", ") { Media.kind(it).name.lowercase() }.replaceFirstChar { it.uppercase() }, tag = s.id)
            }
            Step.SOURCE -> {
                val s = Media.source(srcId)
                page("Settings › Media", s.name, s.steps.mapIndexed { i, t -> if (s.steps.size > 1) "${i + 1}.  $t" else t }.joinToString("\n"))
                val mine = games.artKeys.optJSONObject(s.id) ?: org.json.JSONObject()
                for (f in s.fields) {
                    val v = mine.optString(f.key)
                    rows += Options.Row("field", f.label, value = if (v.isEmpty()) (if (f.optional) "Optional" else "Not set") else if (f.secret) "••••••" + v.takeLast(3) else v, tag = f)
                }
                if (s.fields.isNotEmpty()) rows += Options.Row("test", "Test it", value = Media.status(s.id, games.artKeys), sub = "A quick search, to check the account works")
            }
            Step.LIGHT -> {
            page("Settings", "Lighting", "Press left or right to change a value, and watch the cartridge. Your choice is saved.")
            rows += Options.Row("light", "Environment", value = "%.2f".format(games.lightEnv), sub = "The studio around the cartridges: reflections and fill light", tag = "env")
            rows += Options.Row("light", "Brightness", value = "%.2f".format(games.lightBright), sub = "Everything brighter or darker", tag = "bright")
            rows += Options.Row("light", "Key light", value = "%.1f".format(games.lightKey), sub = "The light from the upper left that shapes things and makes highlights", tag = "key")
            rows += Options.Row("light-reset", "Back to the starting values", value = "%.2f · %.2f · %.1f".format(Look.ENV, Look.BRIGHT, Look.KEY))
            }
        }
        if (step != Step.WELCOME) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (settings) 38f else 44f)
        options.rows = rows
        options.invalidate()
    }

    // ---------- buttons ----------

    fun input(a: String) {
        picker?.let { it.input(a); return }
        if (settings && inSide) return when (a) {
            "up" -> { if (!cats.move(-1)) Sounds.play(Sounds.BUMP); Unit }
            "down" -> { if (!cats.move(1)) Sounds.play(Sounds.BUMP); Unit }
            "right", "a" -> if (options.rows.any { it.key != Options.HEAD }) { Sounds.play(Sounds.PICK); focusSide(false) } else Sounds.play(Sounds.BUMP)
            "b" -> back()
            else -> Unit
        }
        val r = options.current()
        if (r?.key == "light" && (a == "left" || a == "right")) return adjustLight(r.tag as String, if (a == "left") -1 else 1)
        if (r?.key == "art" && (a == "left" || a == "right" || a == "a")) return adjustArt(r.tag as String, if (a == "left") -1 else 1)
        if (r?.key == "homeboxes" && (a == "left" || a == "right" || a == "a")) { games.homeBoxes = !games.homeBoxes; Sounds.play(Sounds.MOVE); return fill() }
        if (r?.key == "homescroll" && (a == "left" || a == "right" || a == "a")) {
            val n = Home.STYLES.size; games.homeScroll = (games.homeScroll + (if (a == "left") n - 1 else 1)) % n; Sounds.play(Sounds.MOVE); return fill()
        }
        if (r?.key == "labelfinish" && (a == "left" || a == "right" || a == "a")) {
            val n = Labels.NAMES.size; games.labelFinish = (games.labelFinish + (if (a == "left") n - 1 else 1)) % n
            showcase.forEach { (sys, c) -> carts.refinish(c, Games.NAMES[sys] ?: "Android games", games.labelFinish) }; Sounds.play(Sounds.MOVE); return fill()
        }
        if (r?.key == "theme" && (a == "left" || a == "right" || a == "a")) {
            val n = Theme.LOOKS.size; games.theme = (games.theme + (if (a == "left") n - 1 else 1)) % n
            Theme.setLook(games.theme); backdrop.theme(); Sounds.play(Sounds.MOVE)   // the sky changes behind at once; panels as they next open
            return fill()
        }
        if (r?.key == "soundpack" && (a == "left" || a == "right" || a == "a")) {
            val n = Sounds.PACKS.size; games.soundPack = (games.soundPack + (if (a == "left") n - 1 else 1)) % n
            Sounds.setPack(ctx, games.soundPack); board.postDelayed({ Sounds.play(Sounds.OPEN) }, 260)   // a taste of it, once it's loaded
            return fill()
        }
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
            "left" -> if (settings) back()
            "b" -> back()
            "a" -> { val r = options.current() ?: return; Sounds.play(Sounds.PICK); act(r) }
        }
    }

    private fun back() = when {
        settings && step == Step.SOURCE -> { show(Step.MEDIA); options.focus(options.rows.indexOfFirst { it.key == "source" && it.tag == srcId }) }
        settings && !inSide -> { Sounds.play(Sounds.CLOSE); focusSide(true) }
        settings -> host.finished(this)
        flow.indexOf(step) > 0 -> show(flow[flow.indexOf(step) - 1])
        else -> Unit
    }
    private fun next() { if (!settings) show(flow[flow.indexOf(step) + 1]) }

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
            "again" -> { games.setupDone = false; host.finished(this) }
            "source" -> { srcId = r.tag as String; show(Step.SOURCE) }
            "field" -> (r.tag as Media.Field).let { f ->
                val all = games.artKeys; val mine = all.optJSONObject(srcId) ?: org.json.JSONObject().also { all.put(srcId, it) }
                host.askText(f.label, mine.optString(f.key)) { v -> mine.put(f.key, v.trim()); games.artKeys = all; fill() }
            }
            "test" -> testSource()
            "media-all" -> findAll(null)
            "media-kind" -> findAll(setOf(r.tag as String))
            "media-stop" -> { MediaJob.cancel(); fill() }
            "media-open" -> if (host.hasStorage()) host.openMediaFolder() else host.toast("Shelf needs access to your files to keep media where you can see it: Settings › Games folders")
            "media-backup" -> host.createDocument("Shelf media backup " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip", "application/zip") { uri ->
                if (uri == null) return@createDocument
                host.toast("Backing up…")
                Thread {
                    val r = try { ctx.contentResolver.openOutputStream(uri)?.use { out -> "Backed up ${Backup.write(meta.root, out)} files" } ?: "Couldn’t write there" } catch (e: Exception) { "The backup didn’t finish: ${e.message}" }
                    board.post { host.toast(r) }
                }.start()
            }
            "media-restore" -> host.openDocument(arrayOf("application/zip", "application/x-zip-compressed")) { uri ->
                if (uri == null) return@openDocument
                host.toast("Restoring…")
                Thread {
                    val r = try { ctx.contentResolver.openInputStream(uri)?.use { i -> "Restored ${Backup.read(i, meta.root)} files" } ?: "Couldn’t read that file" } catch (e: Exception) { "That backup couldn’t be read: ${e.message}" }
                    board.post { meta.reload(); host.collections().restored(); count(); host.toast(r) }
                }.start()
            }
            "del-shell" -> (r.tag as Shell).let { s -> confirmDelete(s.name) { carts.shells.delete(s.id) } }
            "del-look" -> (r.tag as ConsoleLook).let { l -> confirmDelete(l.name) { l.custom?.let { looks.delete(it) } } }
            "fps" -> { games.showFps = !games.showFps; fill() }
            "smart" -> { host.collections().let { it.smartOn = !it.smartOn }; fill() }
            "hiddenentry" -> { host.collections().let { it.hiddenEntry = !it.hiddenEntry }; fill() }
            "changecode" -> host.collections().let { c -> GameCard.askCode(ctx, c) { GameCard.chooseCode(ctx, c, "Choose a new code", "Four presses of the d-pad and A, B, X, Y.") { host.toast("Code changed"); fill() } } }
            "resetcode" -> host.collections().let { c -> GameCard.chooseCode(ctx, c, "Choose a new code", "Four presses of the d-pad and A, B, X, Y.") { host.toast("Code set"); fill() } }
            "sounds" -> { games.sounds = !games.sounds; Sounds.enabled = games.sounds; Sounds.play(Sounds.PICK); fill() }
            "art-reset" -> { games.artKind = "shot"; games.artBright = Art.BRIGHT; games.artTop = Art.TOP; games.artFade = Art.FADE; games.artFadeType = Art.DOTS; games.artPan = Art.PAN; host.artChanged(); fill() }
            "light-reset" -> { games.lightEnv = Look.ENV; games.lightBright = Look.BRIGHT; games.lightKey = Look.KEY; applyLight(); fill() }
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
            "bright" -> games.lightBright = (games.lightBright + step).coerceIn(.2f, 3f)
            "key" -> games.lightKey = (games.lightKey + step * 2).coerceIn(0f, 10f)
        }
        applyLight(); fill()
    }
    private fun applyLight() = stage.setLook(games.lightEnv, games.lightBright, games.lightKey)

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
        val cart = shown?.let { s -> showcase.getOrPut(s) { carts.make(s, Games.NAMES[s] ?: "Android games", null).also { carts.refinish(it, Games.NAMES[s] ?: "Android games", games.labelFinish) } } }
        val k = Backdrop.ease(swapK)
        cart?.place(cx, cy + sin(time * .8f) * 4f, 30f, time * .5f, -.12f, cartSize * (.6f + .4f * k))
        cart?.brightness(.4f + .6f * k)
        backdrop.shadows.clear()
        cart?.let { backdrop.shadows.add(floatArrayOf(cx, cy + 170f * cartSize / 300f, 260f * cartSize / 300f * k, .8f * k)) }
    }

    private fun refreshShowcase() {
        val r = options.current()
        val t = r?.tag; if (t is String && t in Games.NAMES.keys) { want = t; cycle = 0f }
    }

    fun destroy() {
        backdrop.previewArt = false
        board.removeView(frame); picker?.let { board.removeView(it) }
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
    /** Shorter rows (Settings' sections). */
    var compact = false
    /** The chosen row stays marked while the focus is elsewhere (Settings' sections, while you're in one). */
    var keep = false
    private var i = 0; set(v) { if (v != field) since = android.os.SystemClock.uptimeMillis(); field = v }
    private var since = 0L   // when the chosen row was chosen: its long text waits a moment from then before it moves
    private var gliding = false
    private var top = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun h(r: Row) = if (r.key == HEAD) 46f else if (compact) 54f else if (r.sub.isNullOrEmpty()) 64f else 80f
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

    /**
     * Text too long for its room. On the chosen row it starts at its beginning, waits a moment, glides slowly to its end,
     * waits, and fades back to the start (it used to be cut at the front: "…nfo about this setting"); on other rows it
     * ends in an ellipsis.
     */
    private fun line(c: Canvas, s: String, x: Float, y: Float, room: Float, moving: Boolean) {
        val w = p.measureText(s)
        if (w <= room) { c.drawText(s, x, y, p); return }
        if (!moving) { c.drawText(TextUtils.ellipsize(s, android.text.TextPaint(p), room, TextUtils.TruncateAt.END).toString(), x, y, p); return }
        val over = w - room + 6f; val run = over / 40f   // 40 layout pixels a second: slow enough to read as it goes
        val cycle = 1.5f + run + 1.6f + .45f
        val u = ((android.os.SystemClock.uptimeMillis() - since) / 1000f) % cycle
        val k = ((u - 1.5f) / run).coerceIn(0f, 1f); val off = over * k * k * (3 - 2 * k)   // easing in and out of the glide
        val fade = when { u > cycle - .45f -> (cycle - u) / .45f; u < .3f && since != 0L && android.os.SystemClock.uptimeMillis() - since > cycle * 1000 -> u / .3f; else -> 1f }
        val a0 = p.alpha; p.alpha = (a0 * fade.coerceIn(0f, 1f)).toInt()
        c.save(); c.clipRect(x - 2, y - p.textSize * 1.3f, x + room, y + p.textSize * .5f); c.drawText(s, x - off, y, p); c.restore()
        p.alpha = a0; gliding = true
    }

    override fun onDraw(c: Canvas) {
        gliding = false
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
            val kept = keep && !active && k == i
            p.color = when { r.primary -> Theme.accentOf(null); on || kept -> Theme.chip; else -> Color.TRANSPARENT }; c.drawRoundRect(box, 22f, 22f, p)
            // the section you're in: a short bar in the accent color at its left edge
            if (kept) { p.color = Theme.accentOf(null); c.drawRoundRect(RectF(box.left + 10, box.top + 15, box.left + 15, box.bottom - 15), 2.5f, 2.5f, p) }
            if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 3, box.top - 3, box.right + 3, box.bottom + 3), 25f, 25f, p); p.style = Paint.Style.FILL }
            var x = 54f
            r.avatar?.let { Theme.drawAvatar(c, x + 16, y + rh / 2, 16f, it); x += 46 }
            r.dot?.let { p.color = it; c.drawCircle(x + 6, y + (if (r.sub.isNullOrEmpty()) rh / 2 else 30f), 6.5f, p); x += 24 }
            val fg = if (r.primary) Theme.selFg else if (keep && k != i) Theme.fg2 else Theme.fg
            p.typeface = Fonts.bold; p.textSize = if (compact) 22f else 23f; p.color = fg
            // a long name gives way to the value on the right (cut short with an ellipsis)
            val room = width - 54f - x - (r.value?.let { v -> p.textSize = 21f; p.measureText(v) + 14f } ?: 0f); p.textSize = if (compact) 22f else 23f
            line(c, r.label + if (r.check) "  ✓" else "", x, y + (if (r.sub.isNullOrEmpty()) rh / 2 + 8 else 38f), room, on)
            r.value?.let { p.typeface = Fonts.bold; p.textSize = 21f; p.color = if (r.primary) fg else Theme.fg2; p.textAlign = Paint.Align.RIGHT; c.drawText(it, width - 54f, y + (if (r.sub.isNullOrEmpty()) rh / 2 + 8 else 38f), p); p.textAlign = Paint.Align.LEFT }
            r.sub?.takeIf { it.isNotEmpty() }?.let { p.typeface = Fonts.body; p.textSize = 18f
                p.color = if (r.primary) Color.argb(200, Color.red(Theme.selFg), Color.green(Theme.selFg), Color.blue(Theme.selFg)) else Theme.fg2   // dark on the light button
                line(c, it, x, y + 64f, width - x - 60, on) }
            y += rh + 8
        }
        c.restore()
        if (gliding) postInvalidateOnAnimation()   // a long line on the chosen row is moving
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

private val PACK_SUB = listOf("Sine tones with a quiet octave: the first sounds", "Bell-like, the ring of a struck glass", "Warm and dry, like a marimba",
    "Each note glides up into place: a soft bloop")
private val SMOOTH = listOf("Best looks", "Balanced", "Smoothest")
private val SMOOTH_SUB = listOf("The smoothest edges; the most work for the handheld", "Softer edges, less work for the 3D",
    "Lightest: the sky's stars hold still, edges smoothed more cheaply")
private val SCROLL_SUB = listOf("The selected game on the left, the rest in a line", "The same line, the selected game in the middle",
    "The selected game faces you; the others turn toward it either side", "The others turn almost edge on, packed close (boxes show their spines)",
    "Round a wide turntable, the selected game nearest", "The others tucked behind either side, like a fanned deck")
