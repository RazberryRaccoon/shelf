package com.razberryraccoon.shelf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Apps: every app on the handheld, as a grid of icons on soft plates, over the space backdrop (spec §10). It slides up
 * as a sheet from the top bar's Apps button, like the Library and the Museum. Three tabs (L2 and R2): All (the apps you
 * opened last, then every app A to Z; not the apps you made games, and not emulators while they're hidden), Emulators,
 * and Games (the apps you made games, which have a cartridge and box of their own on Home and in the Library).
 *
 * What's an emulator is yours to say (Y › Mark as an emulator; the user, after 0.29: guessing "just doesn't detect
 * what's actually an emulator very well"); the emulators Shelf knows start out marked, and can be unmarked.
 *
 * The chosen app's plate lifts a little and floats, with a thin ring and a soft glow in Android's green, and its full
 * name under it. A opens it: the plate presses in, the screen eases to black, and the app opens; coming back, Shelf
 * eases in again. X hides or shows the emulators in All. Y: Open, Make it a game, Mark as an emulator, App info. The
 * buttons are named along the top, where there's room. B closes the sheet; L1 and R1 step to the next page.
 *
 * Icons are drawn on another thread, once each, at the size the grid shows them.
 */
class Apps(private val ctx: Context, private val board: Board, private val backdrop: Backdrop, private val games: Games, private val host: Host) {
    interface Host {
        fun show(msg: String)
        /** An app became a game: Home shows it. */
        fun gamesChanged()
        /** Opens the app; false if it couldn't. */
        fun openApp(pkg: String): Boolean
        fun appInfo(pkg: String)
        fun closed(apps: Apps)
    }

    private class App(val name: String, val pkg: String, val known: Boolean) { @Volatile var icon: Bitmap? = null }
    /** A tile in the grid: which app, its cell, and its group. */
    private class Cell(val app: App, val x: Float, val y: Float, val row: Int, val col: Int, val group: Int) {
        var lift = 0f   // 0 resting, 1 chosen (eased)
    }
    private class Group(val name: String, val y: Float)

    companion object {
        private const val COLS = 7
        private const val CW = 160f          // a column's width
        private const val GX = (1334f - COLS * CW) / 2   // the grid's left edge, centred
        private const val TOP = 168f         // where the grid starts, under the heading
        private const val ROW = 176f         // a row's height: the plate and its name
        private const val HEAD = 62f         // a group's heading
        private const val PLATE = 112f       // the soft plate an icon sits on
        private const val ICON = 84f
        private val GREEN = Theme.accentOf("android")
        private val TABS = listOf("All", "Emulators", "Games")
    }

    private val prefs = ctx.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    private var all: List<App> = emptyList()
    private var cells: List<Cell> = emptyList()
    private var groups: List<Group> = emptyList()
    private var contentH = 0f
    private var sel = 0
    private var scroll = 0f; private var scrollTo = 0f
    private var shownK = 0f; private var leaving = false
    private var time = 0f
    private var loading = true
    // opening an app: the chosen plate presses in and the screen eases to black (0 to 1), then the app opens; coming
    // back, the black eases away again
    private var opening: App? = null; private var openK = 0f; private var away = false
    private val black = Curtain(ctx, Curtain.FADE)
    private val view = Grid(ctx)
    private var hideEmus: Boolean get() = prefs.getBoolean("apps:hideEmus", false); set(v) = prefs.edit().putBoolean("apps:hideEmus", v).apply()
    private var tab = 0
    /** Whether L1 and R1 may step to the next page (not while an app is opening). */
    val canLeave get() = opening == null && !leaving
    /** An emulator, by your say (Y), or else by Shelf's list of the emulators it knows. */
    private fun emulator(a: App) = prefs.getString("app-kind:" + a.pkg, null)?.let { it == "emulator" } ?: a.known
    private fun setEmulator(a: App, on: Boolean) = prefs.edit().putString("app-kind:" + a.pkg, if (on) "emulator" else "app").apply()
    private fun isGame(a: App) = games.isAppGame(a.pkg)

    init {
        board.put(view, 0, 0, 1334, 750)
        board.put(black, -700, -700, 2734, 2150)
        Sounds.play(Sounds.OPEN)
        load()
    }

    // ---------- the apps ----------

    /** Every app with a launcher icon (but Shelf), read on another thread; icons follow one by one. */
    private fun load() {
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            val list = try {
                val pm = ctx.packageManager
                val emus = Emulators.packages(ctx)
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val seen = HashSet<String>()
                pm.queryIntentActivities(main, 0).mapNotNull { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg == ctx.packageName || pkg == "com.razberryraccoon.shelf" || !seen.add(pkg)) null
                    else App(ri.loadLabel(pm).toString().trim(), pkg, pkg in emus) to ri
                }.sortedBy { it.first.name.lowercase() }
            } catch (e: Exception) { emptyList() }
            ui.post { all = list.map { it.first }; loading = false; layout(); view.invalidate() }
            // the icons, at the grid's size, each as soon as it's drawn
            val px = (ICON * 1.5f).toInt()
            for ((app, ri) in list) {
                if (leaving) break
                app.icon = try {
                    val d = ri.loadIcon(ctx.packageManager)
                    Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b -> val c = Canvas(b); d.setBounds(0, 0, px, px); d.draw(c) }
                } catch (e: Exception) { null }
                ui.post { view.invalidate() }
            }
        }.start()
    }

    private fun opened(a: App) = prefs.getLong("app-opened:" + a.pkg, 0L)

    /** What the tab shows: All (the apps opened lately, then every app but games and hidden emulators), Emulators, Games. */
    private fun groupsFor(t: Int): List<Pair<String, List<App>>> = when (t) {
        1 -> listOf("Emulators" to all.filter { emulator(it) && !isGame(it) })
        2 -> listOf("Your Android games" to all.filter { isGame(it) })
        else -> { val shown = all.filter { !isGame(it) && !(hideEmus && emulator(it)) }
            listOf("Recently opened" to shown.filter { opened(it) > 0 }.sortedByDescending { opened(it) }.take(COLS), "All apps" to shown) }
    }

    /** The groups and their tiles, for the tab. */
    private fun layout() {
        val keep = cells.getOrNull(sel)?.app
        val out = ArrayList<Cell>(); val gs = ArrayList<Group>()
        var y = 0f; var row = -1
        for ((name, list) in groupsFor(tab)) {
            if (list.isEmpty()) continue
            gs += Group(name, y); y += HEAD
            list.chunked(COLS).forEach { line -> row++; line.forEachIndexed { c, a -> out += Cell(a, GX + CW / 2 + c * CW, y + ROW / 2 - 14f, row, c, gs.size - 1) }; y += ROW }
            y += 18f
        }
        // the lift of a tile that stays chosen is kept, so laying out again doesn't make it jump
        cells.forEach { old -> out.firstOrNull { it.app === old.app && it.group == old.group }?.lift = old.lift }
        cells = out; groups = gs; contentH = y
        sel = keep?.let { k -> out.indexOfFirst { it.app === k } }?.takeIf { it >= 0 } ?: sel.coerceIn(0, max(0, out.size - 1))
    }

    // ---------- every frame ----------

    fun tick(dt: Float) {
        time += dt
        backdrop.shadows.clear()   // Home's cartridges' shadows don't show through
        shownK = if (leaving) max(0f, shownK - dt / .35f) else min(1f, shownK + dt / .45f)
        if (leaving && shownK == 0f) { destroy(); host.closed(this); return }
        // opening an app: to black, then open it (once); back from it: out of black
        val o = opening
        if (o != null && !away) {
            openK = min(1f, openK + dt / .38f)
            if (openK >= 1f) { away = true; if (!host.openApp(o.pkg)) { host.show("${o.name} couldn't be opened"); back() } }
        } else if (o == null) openK = max(0f, openK - dt / .45f)
        black.k = openK
        // the chosen row stays near the middle of what's left under the heading
        cells.getOrNull(sel)?.let { c -> val room = 750f - TOP; scrollTo = (c.y - room / 2 + 40f).coerceIn(0f, max(0f, contentH - room + 20f)) }
        scroll += (scrollTo - scroll) * min(1f, dt * 9f)
        cells.forEachIndexed { i, c -> c.lift += ((if (i == sel) 1f else 0f) - c.lift) * min(1f, dt * 11f) }
        view.translationY = (1 - Backdrop.ease(shownK)) * 760f
        view.invalidate()
    }

    /** Back from an app: Shelf eases in again. */
    fun back() {
        if (opening == null) return
        opening = null; away = false
        layout()   // it's recently opened now
    }

    // ---------- buttons ----------

    fun input(a: String) {
        if (leaving || opening != null) return
        val c = cells.getOrNull(sel)
        when (a) {
            "left" -> if (c != null && c.col > 0) step(sel - 1) else Sounds.play(Sounds.BUMP)
            "right" -> if (c != null && cells.getOrNull(sel + 1)?.row == c.row) step(sel + 1) else Sounds.play(Sounds.BUMP)
            "up" -> vertical(-1)
            "down" -> vertical(1)
            "lt", "rt" -> { tab = (tab + if (a == "lt") TABS.size - 1 else 1) % TABS.size; sel = 0; scroll = 0f; scrollTo = 0f; layout(); Sounds.play(Sounds.MOVE) }
            "a" -> c?.let { open(it.app) }
            "x" -> if (tab == 0) { hideEmus = !hideEmus; Sounds.play(Sounds.CLICK); layout(); host.show(if (hideEmus) "Emulators hidden here: they're in the Emulators tab" else "Emulators shown") } else Sounds.play(Sounds.BUMP)
            "y" -> c?.let { menu(it.app) }
            "b" -> close()
        }
    }

    private fun step(to: Int) { sel = to; Sounds.play(Sounds.MOVE) }

    /** Up or down a row, to the nearest tile (the last row can be shorter). */
    private fun vertical(d: Int) {
        val c = cells.getOrNull(sel) ?: return
        val inRow = cells.withIndex().filter { it.value.row == c.row + d }
        if (inRow.isEmpty()) return Sounds.play(Sounds.BUMP)
        sel = inRow.minByOrNull { kotlin.math.abs(it.value.col - c.col) }!!.index; Sounds.play(Sounds.MOVE)
    }

    private fun open(a: App) {
        Sounds.play(Sounds.PICK)
        prefs.edit().putLong("app-opened:" + a.pkg, System.currentTimeMillis()).apply()
        opening = a; away = false
    }

    private fun menu(a: App) {
        val game = isGame(a); val emu = emulator(a)
        Overlays.show(ListCard(ctx, a.name, when { game -> "One of your games: on Home and in the Library"; emu -> "An emulator"; else -> null }, {
            listOfNotNull(ListCard.Row("Open", icon = "grid", tag = "open"),
                if (game) ListCard.Row("Not a game", note = "Back among your apps", icon = "bin", tag = "ungame")
                else ListCard.Row("Make it a game", note = "A cartridge and box of its own", icon = "plus", tag = "game"),
                if (game) null else if (emu) ListCard.Row("Not an emulator", note = "Back among your apps", icon = "move", tag = "unemu")
                else ListCard.Row("Mark as an emulator", note = "It goes in the Emulators tab", icon = "move", tag = "emu"),
                ListCard.Row("App info", note = "Android's page for it", icon = "eye", tag = "info"))
        }) { _, r ->
            when (r.tag) {
                "open" -> open(a)
                "info" -> host.appInfo(a.pkg)
                "game" -> { games.markApp(a.pkg, a.name); Sounds.play(Sounds.CHIME); host.gamesChanged(); layout()
                    host.show("${a.name} is one of your games now: it has its own cartridge on Home and in the Library") }
                "ungame" -> { games.unmarkApp(a.pkg); host.gamesChanged(); layout(); host.show("${a.name} is back among your apps") }
                "emu" -> { setEmulator(a, true); layout(); host.show("${a.name} is in the Emulators tab" + if (hideEmus) " (hidden from All)" else "") }
                "unemu" -> { setEmulator(a, false); layout(); host.show("${a.name} is back among your apps") }
            }
            true
        })
    }

    fun close() { if (!leaving) { leaving = true; Sounds.play(Sounds.CLOSE) } }

    private fun destroy() {
        board.removeView(view); board.removeView(black)
        all.forEach { it.icon?.recycle(); it.icon = null }
    }

    // ---------- drawing ----------

    /** The heading, each group's name, and the tiles. */
    private inner class Grid(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dst = RectF()

        override fun onDraw(c: Canvas) {
            // the heading, the tabs and how many; the buttons named along the top right, where there's room
            p.typeface = Fonts.display; p.textSize = 38f; p.color = Theme.fg; p.textAlign = Paint.Align.LEFT
            c.drawText("Apps", GX + 12f, 78f, p)
            var x = GX + 12f
            for ((k, name) in TABS.withIndex()) {
                p.typeface = Fonts.bold; p.textSize = 18f; val w = p.measureText(name) + 36f; val on = k == tab
                p.color = if (on) GREEN else Theme.chip; c.drawRoundRect(x, 98f, x + w, 136f, 19f, 19f, p)
                p.color = if (on) Theme.selFg else Theme.fg2; c.drawText(name, x + 18f, 124f, p); x += w + 10f
            }
            val shown = cells.map { it.app }.distinct().size
            p.typeface = Fonts.body; p.textSize = 18f; p.color = Theme.fg2
            c.drawText(if (loading) "Finding your apps…" else "$shown ${if (shown == 1) "app" else "apps"}" + (if (tab == 0 && hideEmus) "  ·  emulators hidden" else ""), x + 12f, 124f, p)
            var kx = GX + COLS * CW - 12f
            for ((key, what) in listOfNotNull("Y" to "Options", if (tab == 0) "X" to (if (hideEmus) "Show emulators" else "Hide emulators") else null, "A" to "Open", "L2 R2" to "Tabs")) kx = keyHint(c, key, what, kx, 66f) - 26f
            c.save(); c.clipRect(0f, TOP - 30f, 1334f, 750f)
            for (g in groups) {
                val y = TOP + g.y - scroll + 30f
                if (y < TOP - 40 || y > 790) continue
                p.typeface = Fonts.bold; p.textSize = 18f; p.letterSpacing = .1f; p.color = GREEN
                val name = g.name.uppercase(); c.drawText(name, GX + 12f, y, p)
                val w = p.measureText(name); p.letterSpacing = 0f
                p.color = Theme.line; c.drawRect(GX + 30f + w, y - 7f, GX + COLS * CW - 12f, y - 6f, p)
            }
            cells.forEachIndexed { i, cell -> val y = TOP + cell.y - scroll; if (y > TOP - 140 && y < 900) tile(c, cell, y, i == sel) }
            c.restore()
            if (!loading && cells.isEmpty()) { p.textAlign = Paint.Align.CENTER; p.typeface = Fonts.body; p.textSize = 22f; p.color = Theme.fg2
                c.drawText(when (tab) { 1 -> "No emulators yet. Y on an app › Mark as an emulator."; 2 -> "No Android games yet. Y on an app › Make it a game."; else -> "No apps found." }, 667f, 400f, p); p.textAlign = Paint.Align.LEFT }
        }

        /** A button and what it does, ending at `right`; returns where it starts. */
        private fun keyHint(c: Canvas, key: String, what: String, right: Float, y: Float): Float {
            p.typeface = Fonts.body; p.textSize = 17f; val tw = p.measureText(what)
            p.typeface = Fonts.bold; p.textSize = 14f; val kw = maxOf(28f, p.measureText(key) + 16f)
            val x0 = right - tw - 10f - kw
            p.color = Theme.chip; c.drawRoundRect(x0, y - 15f, x0 + kw, y + 13f, 8f, 8f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Theme.line; c.drawRoundRect(x0, y - 15f, x0 + kw, y + 13f, 8f, 8f, p); p.style = Paint.Style.FILL
            p.color = Theme.fg; p.textAlign = Paint.Align.CENTER; c.drawText(key, x0 + kw / 2, y + 4f, p); p.textAlign = Paint.Align.LEFT
            p.typeface = Fonts.body; p.textSize = 17f; p.color = Theme.fg2; c.drawText(what, x0 + kw + 10f, y + 6f, p)
            return x0
        }

        /** One app: its plate (lifted, ringed and glowing when chosen, pressed in as it opens), its icon, its name. */
        private fun tile(c: Canvas, cell: Cell, y: Float, chosen: Boolean) {
            val e = Backdrop.ease(cell.lift)
            val press = if (chosen && opening != null) sin(Math.PI.toFloat() * min(1f, openK * 2.2f)) * .08f else 0f
            val s = (1 + .1f * e - press)
            val bob = if (chosen) sin(time * 1.6f) * 2.5f * e else 0f   // floating gently, as the chosen cartridge does on Home
            val cx = cell.x; val cy = y - 6f * e + bob; val h = PLATE / 2 * s
            // a soft shadow under it, further and fainter as it lifts
            p.color = Color.BLACK; p.alpha = (60 + 30 * e).toInt(); p.maskFilter = android.graphics.BlurMaskFilter(12f + 8f * e, android.graphics.BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(cx - h + 6, cy - h + 14 + 6 * e, cx + h - 6, cy + h + 14 + 6 * e, 30f * s, 30f * s, p); p.maskFilter = null
            // the glow, in Android's green, as the chosen thing glows elsewhere in Shelf
            if (e > .01f) { p.color = GREEN; p.alpha = (110 * e).toInt(); p.maskFilter = android.graphics.BlurMaskFilter(22f, android.graphics.BlurMaskFilter.Blur.OUTER)
                c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 30f * s, 30f * s, p); p.maskFilter = null }
            // the plate: a little lighter when chosen
            p.color = Color.argb((26 + 22 * e).toInt(), 238, 241, 255); c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 30f * s, 30f * s, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f + 1.5f * e
            p.color = if (e > .01f) GREEN else Theme.line; p.alpha = if (e > .01f) (255 * e).toInt() else Color.alpha(Theme.line)
            c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 30f * s, 30f * s, p); p.style = Paint.Style.FILL
            // the icon
            val icon = cell.app.icon
            val ih = ICON / 2 * s
            if (icon != null && !icon.isRecycled) { dst.set(cx - ih, cy - ih, cx + ih, cy + ih); p.alpha = 255; p.isFilterBitmap = true; c.drawBitmap(icon, null, dst, p) }
            else { p.color = Theme.chip; c.drawRoundRect(cx - ih, cy - ih, cx + ih, cy + ih, 22f, 22f, p) }   // still being drawn
            // the name: all of it when chosen (on two lines if it must), shortened otherwise
            p.textAlign = Paint.Align.CENTER
            if (chosen) {
                p.typeface = Fonts.bold; p.textSize = 19f; p.color = Theme.fg
                c.drawText(TextUtils.ellipsize(cell.app.name, android.text.TextPaint(p), CW * 1.9f, TextUtils.TruncateAt.END).toString(), cx, cy + h + 34f, p)
            } else {
                p.typeface = Fonts.body; p.textSize = 16f; p.color = Theme.fg2
                c.drawText(TextUtils.ellipsize(cell.app.name, android.text.TextPaint(p), CW - 18f, TextUtils.TruncateAt.END).toString(), cx, cy + h + 30f, p)
            }
            p.textAlign = Paint.Align.LEFT
        }
    }
}
