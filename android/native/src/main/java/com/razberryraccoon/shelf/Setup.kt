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
import kotlin.math.sin

/**
 * First-run setup (the prototype's, rebuilt): welcome, who's playing, finding your games (storage access, a folder for
 * any console, or one main folder), how each console's games start, and done. The same pages open from Settings later.
 * A Studio cartridge turns slowly on the right: it cycles through the consoles, and follows the one you're on while
 * you choose folders and emulators.
 */
class Setup(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
            private val games: Games, private val carts: Carts, private val host: Host, private val settings: Boolean) {
    interface Host {
        fun hasStorage(): Boolean
        fun allowStorage()
        fun pickFolder(then: (String?) -> Unit)
        fun askText(title: String, value: String, then: (String) -> Unit)
        fun rescan(then: () -> Unit)
        fun toast(msg: String)
        fun finished(setup: Setup)
    }

    private enum class Step { WELCOME, WHO, GAMES, EMUS, DONE, MENU, LIGHT }
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
        options.reset()
        fill()
        dots.visibility = if (settings) View.INVISIBLE else View.VISIBLE
        dots.at = flow.indexOf(s); dots.invalidate()
        // the page rises in: label, title, text, then the options, a beat apart
        views.forEachIndexed { k, v -> v.alpha = 0f; v.translationY = 18f; v.animate().alpha(1f).translationY(0f).setStartDelay(k * 70L).setDuration(420).setInterpolator(DecelerateInterpolator(1.6f)).start() }
        refreshShowcase()
    }

    private fun page(l: String, t: String, d: String) { label.text = l.uppercase(); title.text = t; lead.text = d }

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
                rows += Options.Row("sounds", "Sounds", value = if (games.sounds) "On" else "Off", sub = "Soft clicks and chimes as you move around")
                rows += Options.Row("again", "Run first-time setup again")
                rows += Options.Row("close", "Done", primary = true)
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
            "close-light" -> show(Step.MENU)
            "sounds" -> { games.sounds = !games.sounds; Sounds.enabled = games.sounds; Sounds.play(Sounds.PICK); fill() }
            "light-reset" -> { games.lightEnv = Look.ENV; games.lightExposure = Look.EXPOSURE; games.lightKey = Look.KEY; applyLight(); fill() }
        }
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
    var rows: List<Row> = emptyList(); set(v) { field = v; i = i.coerceIn(0, maxOf(0, v.size - 1)) }
    var onFocus: (() -> Unit)? = null
    private var i = 0
    private var top = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun h(r: Row) = if (r.sub.isNullOrEmpty()) 64f else 80f

    fun reset() { i = 0; top = 0f }
    fun current() = rows.getOrNull(i)
    fun move(d: Int): Boolean { val k = (i + d).coerceIn(0, rows.size - 1); if (k == i) return false; i = k; onFocus?.invoke(); invalidate(); return true }

    override fun onDraw(c: Canvas) {
        // keep the focused row in view
        var y0 = 0f; for (k in 0 until i) y0 += h(rows[k]) + 8
        val hi = rows.getOrNull(i)?.let { h(it) } ?: 0f
        if (y0 < top) top = y0; if (y0 + hi > top + height - 12) top = y0 + hi - height + 12
        c.save(); c.clipRect(0f, 0f, width.toFloat(), height.toFloat()); c.translate(0f, 4f - top)   // 4: room for the focus ring
        var y = 0f
        rows.forEachIndexed { k, r ->
            val rh = h(r); val on = k == i; val box = RectF(30f, y, width - 30f, y + rh)
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
            r.sub?.takeIf { it.isNotEmpty() }?.let { p.typeface = Fonts.body; p.textSize = 18f; p.color = Theme.fg2
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
