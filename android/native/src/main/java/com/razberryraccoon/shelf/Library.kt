package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Library: every game you have, as its closed box, in two panes like Settings. On the left, your lists: All games,
 * Favorites, the three that fill themselves (Recently added, Not played yet, Most played), your own collections, each
 * console, and Hidden. On the right, the chosen list's boxes, four across at their real relative sizes, a heading for
 * each console with its games after it (so a collection of one series reads console by console), each console's games in
 * the list's order.
 *
 * The chosen box lifts toward you, turns a little more to face you, and glows in its console's color (Outline); its name
 * and how long ago you played it sit under it. A opens it (the Library slides away and the game's page opens from the
 * box), X brings up its collections card, Y makes it a favorite, L1 and R1 jump a console at a time. Y on a list on the
 * left: its order, and for your own collections Add games, Arrange, Rename, Color, Move and Delete.
 *
 * Boxes are made as they come near the screen (their art read on another thread, one box made a frame) and let go when
 * they're well off it, so a big library scrolls smoothly; each one arrives with a small rise.
 */
class Library(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
              private val games: Games, private val carts: Carts, private val meta: Meta, private val cols: Collections,
              private val host: Host) : GamePage.Host {
    interface Host {
        fun play(g: Game)
        fun pick(p: Picker)
        fun askText(title: String, value: String, then: (String) -> Unit)
        fun openDocument(mimes: Array<String>, then: (android.net.Uri?) -> Unit)
        fun show(msg: String)
        fun closed(library: Library, changed: Boolean)
    }

    companion object {
        /** Each console's closed box, in millimetres (its model's size): the grid's cells and rows are made from these. */
        private val BOX_MM = mapOf("nes" to (127f to 182f), "snes" to (174f to 121f), "n64" to (174f to 121f), "genesis" to (128f to 197f),
            "gb" to (123f to 123f), "gbc" to (123f to 123f), "gba" to (123f to 123f), "ds" to (135f to 122f), "psp" to (104f to 177f),
            "ps1" to (142f to 125f), "android" to (135f to 171f))
        private const val S = 1.2f          // layout pixels per millimetre: every box at the same scale, real relative sizes
        private const val GX = 360f         // the grid's left edge
        private const val CW = 236f         // a column's width (four of them)
        private const val COLS = 4
        private const val TOP = 154f        // where the grid starts under the list's name
        private const val HEAD = 58f        // a console's heading
        private const val UNDER = 66f       // room under a row for the chosen box's name
        private const val RY = .2f          // boxes turn a little, so their spines show
    }

    // ---------- the lists ----------

    /** A list on the left: what it's called and which games it shows. */
    private class Shelf(val id: String, val name: String, val dot: Int? = null, val icon: String? = null)

    private var unlocked = false   // Hidden, opened with the code (until you leave the Library)
    private var shelves: List<Shelf> = emptyList()
    private var shelf: Shelf? = null
    private val listPanel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(30, 34, 30, 22); clipToPadding = false }
    private val label = text(20f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .08f; text = "LIBRARY" }
    private val list = Options(ctx).apply { compact = true; keep = true }
    private val heading = text(40f, Fonts.display, Theme.fg).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val subline = text(21f, Fonts.body, Theme.fg2).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val labels = Labels(ctx)

    // ---------- the grid ----------

    private class Cell(val game: Game, var x: Float = 0f, var y: Float = 0f, val w: Float, val h: Float, val row: Int, val col: Int, val section: Int) {
        var case: Case? = null; var arrive = 0f; var lift = 0f
    }
    private class Section(val sys: String, val count: Int, val y: Float)
    private var cells: List<Cell> = emptyList()
    private var sections: List<Section> = emptyList()
    private var rowTops = FloatArray(0); private var rowHeights = FloatArray(0)
    private var contentH = 0f
    private var sel = 0
    private var scroll = 0f; private var scrollTo = 0f
    private var focus = "list"   // "list" or "grid"
    private var adding: Collections.Collection? = null      // Add games: every game, ticks for the ones in this collection
    private var arranging: Collections.Collection? = null   // Arrange: the chosen game moves with the d-pad
    private var carrying = false
    private val boxes = HashMap<String, Case>()              // made boxes, by game
    private val reading = HashMap<String, java.util.concurrent.Future<Array<android.graphics.Bitmap?>>>()
    private val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val sampler = com.google.android.filament.TextureSampler(com.google.android.filament.TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, com.google.android.filament.TextureSampler.MagFilter.LINEAR, com.google.android.filament.TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private var glow: Outline? = null

    // ---------- coming and going ----------

    private var shownK = 0f; private var leaving = false; private var changed = false
    private var time = 0f
    private var page: GamePage? = null; private var pageCart: Cart? = null; private var pageGame: Game? = null
    private var launching = false

    init {
        board.put(listPanel, 24, 24, 306, 702)
        listPanel.addView(label, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = 12 })
        listPanel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 22; leftMargin = -30; rightMargin = -30 })
        board.put(heading, GX.toInt() + 10, 40, 940)
        board.put(subline, GX.toInt() + 12, 98, 940)
        board.put(labels, 0, 0, 1334, 750)
        list.onFocus = { (list.current()?.tag as? String)?.let { id -> shelves.firstOrNull { it.id == id }?.let { open(it, quick = true) } } }
        fillList()
        open(shelves.first())
        focusOn("list")
        Sounds.play(Sounds.OPEN)
    }

    /** The lists on the left, in order (counts updated). */
    private fun fillList() {
        val v = cols.visible()
        val s = ArrayList<Shelf>()
        s += Shelf("all", "All games")
        s += Shelf(Collections.FAVORITES, "Favorites", icon = "heart")
        if (cols.smartOn) { s += Shelf(Collections.ADDED, "Recently added"); s += Shelf(Collections.UNPLAYED, "Not played yet"); s += Shelf(Collections.MOSTPLAYED, "Most played") }
        val mine = cols.all.map { Shelf("c:" + it.id, it.name, dot = it.color) }
        val consoles = Games.NAMES.keys.filter { k -> v.any { it.sys == k } }.map { Shelf("sys:$it", Games.NAMES[it] ?: it, dot = Theme.accentOf(it)) }
        val rows = ArrayList<Options.Row>()
        fun row(sh: Shelf) = Options.Row("shelf", sh.name, value = count(sh).toString(), dot = sh.dot, tag = sh.id)
        s.forEach { rows += row(it) }
        rows += Options.Row(Options.HEAD, "Collections")
        mine.forEach { rows += row(it) }
        rows += Options.Row("new", "+  New collection", tag = "new")
        if (consoles.isNotEmpty()) { rows += Options.Row(Options.HEAD, "Consoles"); consoles.forEach { rows += row(it) } }
        val hidden = Shelf("hidden", "Hidden")
        if (unlocked || cols.hiddenEntry) rows += Options.Row("shelf", "Hidden", value = if (unlocked) games.all.count { cols.hidden(it) }.toString() else "Locked", tag = "hidden")
        shelves = s + mine + consoles + hidden
        val at = list.current()?.tag
        list.rows = rows
        rows.indexOfFirst { it.tag == at }.takeIf { it >= 0 }?.let { list.focus(it) }
        list.invalidate()
    }

    private fun count(sh: Shelf) = gamesOf(sh).size

    private fun gamesOf(sh: Shelf): List<Game> = when {
        sh.id == "all" -> cols.visible()
        sh.id == "hidden" -> if (unlocked) games.all.filter { cols.hidden(it) } else emptyList()
        sh.id.startsWith("sys:") -> cols.visible().filter { it.sys == sh.id.removePrefix("sys:") }
        sh.id.startsWith("c:") -> cols.byId(sh.id.removePrefix("c:"))?.let { cols.gamesOf(it) } ?: emptyList()
        else -> cols.smart(sh.id)
    }

    private fun sortOf(sh: Shelf) = if (sh.id.startsWith("c:")) cols.byId(sh.id.removePrefix("c:"))?.sort ?: Collections.AZ else cols.sortFor(sh.id)

    /** A list's games in its order, within each console (consoles in the usual order). */
    private fun ordered(sh: Shelf, list: List<Game>): List<Game> {
        val sort = sortOf(sh)
        val col = if (sh.id.startsWith("c:")) cols.byId(sh.id.removePrefix("c:")) else null
        val order = Games.NAMES.keys.toList()
        val within: Comparator<Game> = when {
            sh.id == Collections.ADDED -> compareByDescending { cols.added(it) }
            sh.id == Collections.MOSTPLAYED || sort == Collections.MOST -> compareByDescending<Game> { games.timesPlayed(it) }.thenBy { it.title.lowercase() }
            sort == Collections.RECENT -> compareByDescending<Game> { games.lastPlayed(it) }.thenBy { it.title.lowercase() }
            sort == Collections.YEAR -> compareBy<Game> { meta.cached(it)?.fields?.get("releaseyear")?.take(4)?.toIntOrNull() ?: 9999 }.thenBy { it.title.lowercase() }
            sort == Collections.MINE && col != null -> compareBy { g -> col.entries.indexOfFirst { it.path == g.path || (it.sys == g.sys && it.title == g.title) } }
            else -> compareBy { it.title.lowercase() }
        }
        return list.sortedWith(compareBy<Game> { order.indexOf(it.sys) }.then(within))
    }

    /** Shows a list on the right. */
    private fun open(sh: Shelf, quick: Boolean = false) {
        if (sh.id == "hidden" && !unlocked) {
            shelf = sh; heading.text = "Hidden"; subline.text = "Locked. A to enter your code."; layout(emptyList(), single = true); return
        }
        val same = shelf?.id == sh.id
        shelf = sh
        val list = adding?.let { ordered(Shelf("all", ""), cols.visible()) } ?: ordered(sh, gamesOf(sh))
        val keep = cells.getOrNull(sel)?.game
        layout(list, single = sh.id.startsWith("sys:") && adding == null)
        sel = if (same && keep != null) cells.indexOfFirst { it.game == keep }.coerceAtLeast(0) else 0
        if (!same) { scroll = 0f; scrollTo = 0f }
        refreshHeading()
        if (!quick) Sounds.play(Sounds.MOVE)
    }

    private fun refreshHeading() {
        val sh = shelf ?: return
        val add = adding
        val n = cells.size
        heading.text = if (add != null) "Adding to ${add.name}" else sh.name
        subline.text = when {
            add != null -> "${add.entries.size} in it  ·  A puts a game in or takes it out  ·  B when you're done"
            arranging != null -> "Arranging  ·  A picks up a game and puts it down  ·  B when you're done"
            n == 0 -> when (sh.id) {
                Collections.FAVORITES -> "Nothing here yet. Y on any game makes it a favorite."
                Collections.UNPLAYED -> "You've played everything."
                Collections.MOSTPLAYED -> "Play something and it shows up here."
                else -> if (sh.id.startsWith("c:")) "Empty. Y here, then Add games, or X on any game." else "No games."
            }
            else -> "$n ${if (n == 1) "game" else "games"}  ·  " + when (sh.id) { Collections.ADDED -> "Newest first"; Collections.MOSTPLAYED -> "Most played first"; else -> Collections.SORTS[sortOf(sh)] }
        }
    }

    /** Lays the games out: a heading per console (unless it's one console's list), then rows of four. */
    private fun layout(list: List<Game>, single: Boolean) {
        val out = ArrayList<Cell>(); val secs = ArrayList<Section>(); val tops = ArrayList<Float>(); val hs = ArrayList<Float>()
        var y = 0f; var row = -1
        val bySys = list.groupBy { it.sys }
        for ((sys, gs) in bySys) {
            if (!single) { secs += Section(sys, gs.size, y); y += HEAD }
            val (w, h) = BOX_MM[sys] ?: (130f to 170f)
            gs.chunked(COLS).forEach { line ->
                row++; tops += y; val rh = h * S; hs += rh
                line.forEachIndexed { c, g -> out += Cell(g, GX + CW / 2 + c * CW, y + rh / 2, w * S, h * S, row, c, secs.size - 1) }
                y += rh + UNDER
            }
            y += 14f
        }
        // the boxes no longer in the list go
        val now = out.map { it.game.path }.toSet()
        boxes.keys.filter { it !in now }.forEach { boxes.remove(it)?.destroy() }
        out.forEach { c -> boxes[c.game.path]?.let { c.case = it; c.arrive = 1f } }
        cells = out; sections = secs; rowTops = tops.toFloatArray(); rowHeights = hs.toFloatArray(); contentH = y
        sel = sel.coerceIn(0, max(0, cells.size - 1))
    }

    private fun focusOn(f: String) {
        focus = f
        list.active = f == "list"; list.invalidate()
    }

    // ---------- every frame ----------

    fun tick(dt: Float) {
        time += dt
        shownK = if (leaving) max(0f, shownK - dt / .35f) else min(1f, shownK + dt / .45f)
        val k = Backdrop.ease(shownK)
        val pageK = page?.shownK ?: 0f
        val away = max(1 - k, pageK)
        // the panes slide up with the sheet, and step aside for a game's page
        listPanel.translationY = (1 - k) * 760f; listPanel.translationX = -pageK * 340f; listPanel.alpha = 1 - pageK
        for (v in listOf(heading, subline, labels)) { v.translationY = (1 - k) * 760f; v.alpha = 1 - pageK }
        if (leaving && shownK == 0f) { destroy(); host.closed(this, changed); return }
        page?.tick(dt)
        // scrolling: the chosen row stays near the middle of what's left under the heading
        cells.getOrNull(sel)?.let { c -> val mid = (TOP + 750f) / 2 + 20f; scrollTo = (c.y - (mid - TOP)).coerceIn(0f, max(0f, contentH - (750f - TOP) + 30f)) }
        scroll += (scrollTo - scroll) * min(1f, dt * 9f)
        backdrop.shadows.clear()
        if (launching) { cells.forEach { it.case?.hide() }; return }
        // which boxes should exist: those within a screen of what's showing, the chosen one's neighbours first
        var made = 0
        val near = cells.withIndex().filter { (_, c) -> sy(c) > -380 && sy(c) < 1130 }.sortedBy { (i, _) -> abs(i - sel) }
        for ((_, c) in near) {
            if (c.case != null) continue
            val path = c.game.path
            val f = reading[path] ?: reader.submit(java.util.concurrent.Callable { GamePage.boxArt(meta, c.game) }).also { reading[path] = it }
            if (!f.isDone || made >= 1) continue
            reading.remove(path)
            val art = try { f.get() } catch (e: Exception) { arrayOfNulls<android.graphics.Bitmap>(3) }
            val case = carts.model("case-${c.game.sys}.glb")?.let { Case(stage, it, c.game.title, sampler, art[0], art[1], art[2]) }
            if (case == null) { art.forEach { it?.recycle() }; continue }
            case.open = 0f; Outline.mark(stage, case.asset.entities, Outline.BOX)
            c.case = case; boxes[path] = case; c.arrive = 0f; made++
        }
        // and those far off it go
        for (c in cells) if (c.case != null && (sy(c) < -900 || sy(c) > 1650)) { boxes.remove(c.game.path)?.destroy(); c.case = null }
        // placing them
        cells.forEachIndexed { i, c ->
            val case = c.case ?: return@forEachIndexed
            if (page != null && c.game == pageGame) { case.hide(); return@forEachIndexed }   // the page has its own
            val chosen = i == sel && focus == "grid" && page == null
            c.arrive = min(1f, c.arrive + dt / .32f)
            c.lift += ((if (chosen) (if (carrying) 1.6f else 1f) else 0f) - c.lift) * min(1f, dt * 10f)
            val y = sy(c)
            // near the top and bottom edges the boxes sink away, so nothing slides under the heading
            val edge = Backdrop.ease(((y - (TOP - 40f)) / 90f).coerceIn(0f, 1f)) * Backdrop.ease(((800f - y) / 90f).coerceIn(0f, 1f))
            val arrive = Backdrop.ease(c.arrive)
            val grow = edge * arrive * (1 - away) * (1 + .06f * c.lift)
            if (grow < .02f) { case.hide(); return@forEachIndexed }
            val bob = if (chosen) sin(time * 1.6f) * 3f else 0f
            // the chosen one: up toward you, turned a little more to face you
            case.place(c.x, y - c.lift * 14f + (1 - arrive) * 18f + bob + (1 - k) * 760f, 10f + 46f * c.lift, RY * (1 - .5f * c.lift), -.06f, max(c.w, c.h) * grow, held = false)
            val big = max(c.w, c.h)
            if (arrive > .05f) backdrop.shadows.add(floatArrayOf(c.x, y + c.h / 2 * grow + 16f + (1 - k) * 760f, c.w * grow * .95f, (.55f + .3f * c.lift) * edge * (1 - away)))
            if (big <= 0f) return@forEachIndexed
        }
        // the glow around the chosen box, in its console's color (and the sky's nebula takes that color too)
        val c = cells.getOrNull(sel)?.takeIf { focus == "grid" && page == null && !leaving }
        c?.let { backdrop.mood(Theme.accentOf(it.game.sys)) }
        val case = c?.case
        if (case != null || glow != null) {
            val g = glow ?: Outline(stage).also { glow = it }
            if (case != null && c.arrive > .9f) g.frame(dt, case.asset.entities, Outline.BOX, c.game.sys, case.current(), case.center, case.half)
            else g.frame(dt, null, 0, null, null, null, null)
        }
        labels.invalidate()
    }

    /** Where a cell is on the screen now (its centre's height). */
    private fun sy(c: Cell) = TOP + c.y - scroll

    // ---------- buttons ----------

    fun input(a: String) {
        page?.let { it.input(a); return }
        if (leaving || launching) return
        if (focus == "list") listInput(a) else gridInput(a)
    }

    private fun listInput(a: String) {
        val r = list.current()
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "right", "a" -> when {
                r?.key == "new" -> if (a == "a") newCollection() else Sounds.play(Sounds.BUMP)
                r?.tag == "hidden" && !unlocked -> if (a == "a") unlock() else Sounds.play(Sounds.BUMP)
                cells.isNotEmpty() -> { Sounds.play(Sounds.PICK); focusOn("grid") }
                else -> Sounds.play(Sounds.BUMP)
            }
            "y" -> shelf?.let { menu(it) }
            "select" -> if (!unlocked && !cols.hiddenEntry && cols.hasCode) unlock()   // Hidden not listed: Select, then the code
            "b" -> close()
        }
    }

    private fun gridInput(a: String) {
        val c = cells.getOrNull(sel)
        val arr = arranging
        if (arr != null && carrying && c != null) {
            // carrying a game: the d-pad moves it among its console's games
            val d = when (a) { "left" -> -1; "right" -> 1; "up" -> -COLS; "down" -> COLS; else -> 0 }
            if (d != 0) {
                var moved = 0; repeat(abs(d)) { if (cols.nudge(arr, c.game, if (d > 0) 1 else -1)) moved++ }
                if (moved > 0) { Sounds.play(Sounds.MOVE); val g = c.game; open(shelf!!, quick = true); sel = cells.indexOfFirst { it.game == g }.coerceAtLeast(0); changed = true } else Sounds.play(Sounds.BUMP)
                return
            }
            if (a == "a" || a == "b") { carrying = false; Sounds.play(Sounds.CLICK); refreshHeading(); return }
            return
        }
        when (a) {
            "left" -> if (c != null && c.col > 0) step(sel - 1) else if (adding == null && arranging == null) { Sounds.play(Sounds.MOVE); focusOn("list") } else Sounds.play(Sounds.BUMP)
            "right" -> if (c != null && c.col < COLS - 1 && cells.getOrNull(sel + 1)?.row == c.row) step(sel + 1) else Sounds.play(Sounds.BUMP)
            "up" -> vertical(-1)
            "down" -> vertical(1)
            "lb", "rb" -> jumpSection(if (a == "lb") -1 else 1)
            "a" -> c?.let { cell ->
                val add = adding
                when {
                    add != null -> { val on = cols.toggle(add, cell.game); Sounds.play(Sounds.CLICK); changed = true; refreshHeading(); host.show(if (on) "Added to ${add.name}" else "Taken out of ${add.name}") }
                    arr != null -> { carrying = true; Sounds.play(Sounds.PICK); refreshHeading() }
                    else -> openPage(cell)
                }
            }
            "x" -> c?.let { cell -> if (adding == null && arranging == null) GameCard.show(ctx, games, cols, cell.game, host::askText, host::show) { _ -> changed = true; refresh() } }
            "y" -> c?.let { cell -> if (adding == null && arranging == null) { val on = !games.favorite(cell.game); games.setFavorite(cell.game, on); Sounds.play(Sounds.CLICK); host.show(if (on) "Added to Favorites" else "Taken out of Favorites"); changed = true; refresh() } }
            "b" -> when {
                adding != null -> { val add = adding!!; adding = null; host.show("${add.name}: ${add.entries.size} ${if (add.entries.size == 1) "game" else "games"}"); fillList(); open(shelves.first { it.id == "c:" + add.id }) }
                arranging != null -> { arranging = null; carrying = false; refreshHeading() }
                else -> { Sounds.play(Sounds.MOVE); focusOn("list") }
            }
        }
    }

    private fun step(to: Int) { sel = to; Sounds.play(Sounds.MOVE) }

    /** Up or down a row, to the nearest box by position (rows can be different lengths). */
    private fun vertical(d: Int) {
        val c = cells.getOrNull(sel) ?: return
        val row = c.row + d
        val inRow = cells.withIndex().filter { it.value.row == row }
        if (inRow.isEmpty()) return Sounds.play(Sounds.BUMP)
        sel = inRow.minByOrNull { abs(it.value.x - c.x) }!!.index; Sounds.play(Sounds.MOVE)
    }

    private fun jumpSection(d: Int) {
        val c = cells.getOrNull(sel) ?: return
        val to = cells.indexOfFirst { it.section == c.section + d }
        if (to < 0 || sections.isEmpty()) return Sounds.play(Sounds.BUMP)
        sel = to; Sounds.play(Sounds.MOVE)
    }

    /** After something changed (a favorite, a collection, a hidden game): the counts and the list shown again. */
    private fun refresh() { fillList(); shelf?.let { open(it, quick = true) } }

    // ---------- lists: making, ordering, changing ----------

    private fun newCollection() = host.askText("Name the new collection", "") { name ->
        if (name.isBlank()) return@askText
        val c = cols.create(name.trim().take(40)); changed = true
        fillList()
        list.rows.indexOfFirst { it.tag == "c:" + c.id }.takeIf { it >= 0 }?.let { list.focus(it) }
        open(shelves.first { it.id == "c:" + c.id })
        startAdding(c)
    }

    private fun startAdding(c: Collections.Collection) {
        adding = c; arranging = null
        open(shelf!!, quick = true); sel = 0; scroll = 0f; scrollTo = 0f
        focusOn("grid"); refreshHeading()
    }

    /** Y on a list: its order, and for your own collections everything else you can do with it. */
    private fun menu(sh: Shelf) {
        val col = if (sh.id.startsWith("c:")) cols.byId(sh.id.removePrefix("c:")) else null
        if (sh.id == Collections.ADDED || sh.id == Collections.MOSTPLAYED || (sh.id == "hidden" && !unlocked)) return Sounds.play(Sounds.BUMP)
        val rows = ArrayList<ListCard.Row>()
        if (col != null) { rows += ListCard.Row("Add games", icon = "grid", tag = "add"); rows += ListCard.Row("Arrange", note = "Your own order", icon = "move", tag = "arrange") }
        rows += ListCard.Row("Order", note = Collections.SORTS[sortOf(sh)], icon = "sort", tag = "sort")
        if (col != null) {
            rows += ListCard.Row("Rename", icon = "pencil", tag = "rename"); rows += ListCard.Row("Color", dot = col.color, tag = "color")
            rows += ListCard.Row("Move up the list", icon = "move", tag = "up"); rows += ListCard.Row("Move down the list", icon = "move", tag = "down")
            rows += ListCard.Row("Delete", icon = "bin", tag = "delete")
        }
        Overlays.show(ListCard(ctx, sh.name, if (col != null) "${col.entries.size} ${if (col.entries.size == 1) "game" else "games"}" else null, { rows }) { _, r ->
            when (r.tag) {
                "add" -> { startAdding(col!!); true }
                "arrange" -> { col!!.sort = Collections.MINE; cols.save(); arranging = col; adding = null; open(sh, quick = true); focusOn("grid"); refreshHeading(); true }
                "sort" -> { chooseSort(sh, col); true }
                "rename" -> { host.askText("Rename the collection", col!!.name) { n -> if (n.isNotBlank()) { col.name = n.trim().take(40); cols.save(); changed = true; fillList(); open(shelves.first { it.id == sh.id }, quick = true) } }; true }
                "color" -> { chooseColor(col!!); true }
                "up", "down" -> { cols.move(col!!, if (r.tag == "up") -1 else 1); changed = true; fillList(); false }
                "delete" -> { confirmDelete(col!!); true }
                else -> true
            }
        })
    }

    private fun chooseSort(sh: Shelf, col: Collections.Collection?) {
        val options = Collections.SORTS.indices.filter { col != null || it != Collections.MINE }
        Overlays.show(ListCard(ctx, "Order", "Within each console", { options.map { ListCard.Row(Collections.SORTS[it], check = sortOf(sh) == it, tag = it) } }, start = options.indexOf(sortOf(sh)).coerceAtLeast(0)) { _, r ->
            val s = r.tag as Int
            if (col != null) { col.sort = s; cols.save() } else cols.setSortFor(sh.id, s)
            open(sh, quick = true); false
        })
    }

    private fun chooseColor(col: Collections.Collection) {
        Overlays.show(ListCard(ctx, "Color", col.name, { Collections.COLORS.map { (n, c) -> ListCard.Row(n, dot = c, check = col.color == c, tag = c) } },
            start = Collections.COLORS.indexOfFirst { it.second == col.color }.coerceAtLeast(0)) { _, r -> col.color = r.tag as Int; cols.save(); changed = true; fillList(); false })
    }

    private fun confirmDelete(col: Collections.Collection) {
        Overlays.show(ListCard(ctx, "Delete ${col.name}?", "The games stay in your Library", { listOf(ListCard.Row("Delete", icon = "bin", tag = true), ListCard.Row("Keep it", tag = false)) }, start = 1) { _, r ->
            if (r.tag == true) { cols.delete(col); changed = true; fillList(); open(shelves.first()); list.focus(0); host.show("Deleted ${col.name}") }
            true
        })
    }

    /** Hidden: the code opens it, until you leave the Library. */
    private fun unlock() {
        if (!cols.hasCode) { host.show("Nothing's hidden yet. X on a game, then Hide this game."); return }
        GameCard.askCode(ctx, cols) {
            unlocked = true; fillList()
            list.rows.indexOfFirst { it.tag == "hidden" }.takeIf { it >= 0 }?.let { list.focus(it) }
            open(shelves.first { it.id == "hidden" })
            if (cells.isNotEmpty()) focusOn("grid")
        }
    }

    // ---------- a game's page ----------

    private fun openPage(c: Cell) {
        val case = c.case ?: return Sounds.play(Sounds.BUMP)
        Sounds.play(Sounds.PICK)
        val g = c.game
        val cart = carts.make(g.sys, g.title, carts.shellFor(games, g))?.also { carts.refinish(it, g.title, games.finishFor(g)); it.place(-2000f, 0f, 0f, 0f, 0f, 1f) }
        pageCart = cart; pageGame = g
        page = GamePage(ctx, board, backdrop, stage, games, carts, meta, g, cart, floatArrayOf(c.x, sy(c), max(c.w, c.h) * (1 + .06f * c.lift), RY * (1 - .5f * c.lift)), this, fromBox = true)
        case.hide()
    }

    /** Starting a game from its page here: its cartridge goes to the start animation, and comes back to the page. */
    fun takeForLaunch(g: Game): Triple<Cart?, FloatArray, FloatArray>? {
        val p = page?.takeIf { it.game.path == g.path } ?: return null
        val c = p.cart
        launching = true; p.launching = true
        val now = c?.current() ?: FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
        return Triple(c, now, c?.placed?.copyOf() ?: now)
    }
    fun landed(g: Game) { launching = false; page?.launching = false; changed = true }

    fun stick(x: Float, y: Float) { page?.stick(x, y) }
    fun leftStick(x: Float, y: Float): Boolean = page?.leftStick(x, y) ?: false

    override fun play(g: Game) = host.play(g)
    override fun pick(p: Picker) = host.pick(p)
    override fun askText(title: String, value: String, then: (String) -> Unit) = host.askText(title, value, then)
    override fun show(msg: String) = host.show(msg)
    override fun openDocument(mimes: Array<String>, then: (android.net.Uri?) -> Unit) = host.openDocument(mimes, then)
    override fun cartChanged(g: Game, c: Cart?) { if (g == pageGame) pageCart = c }
    override fun collectionsCard(g: Game, changed: (Boolean) -> Unit) = GameCard.show(ctx, games, cols, g, host::askText, host::show) { h -> this.changed = true; changed(h); refresh() }
    override fun hideGame(g: Game, then: () -> Unit) = GameCard.hide(ctx, cols, g, host::show) { this.changed = true; then() }
    override fun collectionNames(g: Game) = cols.of(g).map { it.name }
    override fun closed(page: GamePage, homeChanged: Boolean) {
        if (this.page === page) this.page = null
        pageCart?.destroy(); pageCart = null; pageGame = null
        if (homeChanged) changed = true
        refresh()
    }

    // ---------- leaving ----------

    fun close() { if (!leaving) { leaving = true; Sounds.play(Sounds.CLOSE); glow?.frame(0f, null, 0, null, null, null, null) } }

    fun destroy() {
        page?.destroy(); page = null; pageCart?.destroy(); pageCart = null
        glow?.destroy(); glow = null
        boxes.values.forEach { it.destroy() }; boxes.clear()
        reading.values.forEach { f -> reader.execute { try { f.get().forEach { it?.recycle() } } catch (_: Exception) { } } }; reading.clear()
        reader.shutdown()
        backdrop.shadows.clear()
        board.removeView(listPanel); board.removeView(heading); board.removeView(subline); board.removeView(labels)
    }

    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }

    /**
     * The words among the boxes, drawn in one go each frame: each console's heading (its color's dot, its name, how many),
     * the chosen box's name and when you last played it, and in Add games a tick on every box that's in the collection.
     */
    private inner class Labels(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            c.save(); c.clipRect(GX - 20, TOP - 30, 1334f, 750f)
            for (s in sections) {
                val y = TOP + s.y - scroll + 34f
                if (y < TOP - 40 || y > 790) continue
                p.color = Theme.accentOf(s.sys); c.drawCircle(GX + 22, y - 8, 7f, p)
                p.typeface = Fonts.bold; p.textSize = 21f; p.letterSpacing = .06f; p.color = Theme.fg
                c.drawText((Games.NAMES[s.sys] ?: s.sys).uppercase(), GX + 40, y, p)
                val w = p.measureText((Games.NAMES[s.sys] ?: s.sys).uppercase()); p.letterSpacing = 0f
                p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2; c.drawText("${s.count}", GX + 54 + w, y, p)
                p.color = Theme.line; c.drawRect(GX + 70 + w + p.measureText("${s.count}"), y - 8, 1300f, y - 7, p)
            }
            val add = adding
            cells.forEachIndexed { i, cell ->
                val y = sy(cell)
                if (y < TOP - 120 || y > 870) return@forEachIndexed
                if (add != null && cols.has(add, cell.game)) {
                    // a tick on the box's top right, in the collection's color
                    val cx = cell.x + cell.w / 2 - 6; val cy = y - cell.h / 2 + 8
                    p.color = add.color; c.drawCircle(cx, cy, 16f, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = 3.4f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = Theme.selFg
                    c.drawPath(Path().apply { moveTo(cx - 7, cy); lineTo(cx - 2, cy + 5); lineTo(cx + 7, cy - 5) }, p); p.style = Paint.Style.FILL
                }
                if (i == sel && focus == "grid" && page == null) {
                    val ty = y + cell.h / 2 + 34f
                    p.textAlign = Paint.Align.CENTER
                    p.typeface = Fonts.bold; p.textSize = 21f; p.color = Theme.fg
                    c.drawText(TextUtils.ellipsize(cell.game.title, android.text.TextPaint(p), CW * 1.6f, TextUtils.TruncateAt.END).toString(), cell.x, ty, p)
                    val played = games.lastPlayed(cell.game)
                    val note = (if (arranging != null && carrying) "Moving  ·  " else "") + if (played == 0L) "Not played yet" else "Played " + android.text.format.DateUtils.getRelativeTimeSpanString(played, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
                    p.typeface = Fonts.body; p.textSize = 17f; p.color = Theme.fg2; c.drawText(note, cell.x, ty + 24, p)
                    p.textAlign = Paint.Align.LEFT
                }
            }
            if (cells.isEmpty() && shelf?.id == "hidden" && !unlocked) {
                p.textAlign = Paint.Align.CENTER; p.typeface = Fonts.body; p.textSize = 22f; p.color = Theme.fg2
                c.drawText("Press A on Hidden and enter your code.", (GX + 1310) / 2, 400f, p); p.textAlign = Paint.Align.LEFT
            }
            c.restore()
        }
    }
}
