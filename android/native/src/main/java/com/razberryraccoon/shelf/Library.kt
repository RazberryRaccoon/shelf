package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Library: every game you have, as its closed box, in two panes like Settings. On the left, your lists: All games,
 * Favorites, the three that fill themselves (Recently added, Not played yet, Most played), your collections (live ones
 * too: a saved sort and filters), each console, and Hidden. On the right, the chosen list's boxes, four across at their
 * real relative sizes, under a bar of pills: its order, its filters (All games, and live collections), and for your own
 * collections Add games and Arrange.
 *
 * All games, a console and a live collection are one list in their order; ordering by console, genre, developer,
 * publisher or release year puts a heading over each group. Your own collections and Favorites always go console by
 * console (the user: a collection of one series reads by platform), each console's games in the collection's order.
 *
 * The chosen box lifts toward you, turns a little more to face you, and glows in its console's color (Outline); its name
 * and how long ago you played it sit under it. A opens it (the Library slides away and the game's page opens from the
 * box), X brings up its collections card, Y makes it a favorite, L2 and R2 jump a group at a time (L1 and R1 step between pages), Start goes to the
 * pills. Y on a list on the left: what you can do with it.
 *
 * Boxes are made as they come near the screen and kept for reuse when they leave it (dressed again with the next game's
 * art, which is read on two threads at the size the grid needs), so a big library fills in quickly; each box arrives
 * with a small rise.
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
            "ps1" to (142f to 125f), "android" to (120f to 180f))
        private const val S = 1.2f          // layout pixels per millimetre: every box at the same scale, real relative sizes
        private const val GX = 360f         // the grid's left edge
        private const val CW = 236f         // a column's width (four of them)
        private const val COLS = 4
        private const val TOP = 214f        // where the grid starts, under the list's name, its pills and its summary (room for a lifted, glowing box)
        private const val HEAD = 84f        // a group's heading, with room for the box under it to lift and glow
        private const val UNDER = 66f       // room under a row for the chosen box's name
        private const val RY = .2f          // boxes turn a little, so their spines show
        private const val PILL_Y = 92f; private const val PILL_H = 42f
    }

    // ---------- the lists ----------

    /** A list on the left: what it's called and which games it shows. */
    private class Shelf(val id: String, val name: String, val dot: Int? = null)

    private var unlocked = false   // Hidden, opened with the code (until you leave the Library)
    private var shelves: List<Shelf> = emptyList()
    private var shelf: Shelf? = null
    private val listPanel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(30, 34, 30, 22); clipToPadding = false }
    private val label = text(20f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .08f; text = "LIBRARY" }
    private val list = Options(ctx).apply { compact = true; keep = true }
    private val heading = text(38f, Fonts.display, Theme.fg).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val labels = Labels(ctx)

    // ---------- the grid ----------

    private class Cell(val game: Game, val x: Float, val y: Float, val w: Float, val h: Float, val row: Int, val col: Int, val group: Int) {
        var case: Case? = null; var arrive = 0f; var lift = 0f
    }
    /** A group's heading: its name, a dot in its console's color (consoles only), how many, and where it sits. */
    private class Group(val name: String, val dot: Int?, val count: Int, val y: Float)
    private var cells: List<Cell> = emptyList()
    private var groups: List<Group> = emptyList()
    private var summary = ""
    private var contentH = 0f
    private var sel = 0
    private var scroll = 0f; private var scrollTo = 0f
    private var focus = "list"   // "list", "pills" or "grid"
    private var pillAt = 0
    private var adding: Collections.Collection? = null      // Add games: every game, ticks for the ones in this collection
    private var arranging: Collections.Collection? = null   // Arrange: the chosen game moves with the d-pad
    private var carrying = false
    private val boxes = HashMap<String, Case>()              // boxes dressed for a game, by game
    private val spare = HashMap<String, ArrayDeque<Case>>()  // boxes off the screen, kept to be dressed again, by console
    private val reading = HashMap<String, java.util.concurrent.Future<Array<Bitmap?>>>()
    private val reader = java.util.concurrent.Executors.newFixedThreadPool(2)
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
        board.put(heading, GX.toInt() + 10, 34, 940)
        board.put(labels, 0, 0, 1334, 750)
        list.onFocus = { (list.current()?.tag as? String)?.let { id -> shelves.firstOrNull { it.id == id }?.let { open(it, quick = true) } } }
        fillList()
        open(shelves.first())
        focusOn("list")
        Sounds.play(Sounds.OPEN)
        // every game's details (genre, year, makers, players), for ordering and filtering by them
        Facts.build(meta, games.all) { if (!leaving) { fillList(); shelf?.let { open(it, quick = true) } } }
    }

    /** The lists on the left, in order (counts updated). */
    private fun fillList() {
        val v = cols.visible()
        val s = ArrayList<Shelf>()
        s += Shelf("all", "All games")
        s += Shelf(Collections.FAVORITES, "Favorites")
        for ((id, name) in listOf(Collections.ADDED to "Recently added", Collections.UNPLAYED to "Not played yet", Collections.MOSTPLAYED to "Most played")) if (cols.smartOn(id)) s += Shelf(id, name)
        val mine = cols.all.map { Shelf("c:" + it.id, (if (it.live) "✦ " else "") + it.name, dot = it.color) }
        val consoles = Games.NAMES.keys.filter { k -> v.any { it.sys == k } }.map { Shelf("sys:$it", Games.NAMES[it] ?: it, dot = Theme.accentOf(it)) }
        val rows = ArrayList<Options.Row>()
        fun row(sh: Shelf) = Options.Row("shelf", sh.name, value = gamesOf(sh).size.toString(), dot = sh.dot, tag = sh.id)
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

    private fun colOf(sh: Shelf) = if (sh.id.startsWith("c:")) cols.byId(sh.id.removePrefix("c:")) else null

    /** All games' filters, or a live collection's; null where a list has none. */
    private fun filterOf(sh: Shelf): Filter? = when {
        sh.id == "all" -> cols.filterFor("all")
        else -> colOf(sh)?.takeIf { it.live }?.filter
    }
    private fun saveFilter(sh: Shelf, f: Filter) {
        if (sh.id == "all") cols.setFilterFor("all", f)
        else colOf(sh)?.takeIf { it.live }?.let { it.rule = (it.rule ?: org.json.JSONObject()).put("filter", f.json()); cols.save() }
        changed = true
    }

    private fun gamesOf(sh: Shelf): List<Game> = when {
        sh.id == "all" -> { val f = cols.filterFor("all"); cols.visible().filter { f.matches(it, games, cols) } }
        sh.id == "hidden" -> if (unlocked) games.all.filter { cols.hidden(it) } else emptyList()
        sh.id.startsWith("sys:") -> cols.visible().filter { it.sys == sh.id.removePrefix("sys:") }
        sh.id.startsWith("c:") -> colOf(sh)?.let { cols.gamesOf(it) } ?: emptyList()
        else -> cols.smart(sh.id)
    }

    /** Your own collections and Favorites go console by console; their order is within each console. */
    private fun byConsole(sh: Shelf) = sh.id == Collections.FAVORITES || colOf(sh)?.live == false
    /** Lists whose order is part of what they are. */
    private fun fixedSort(sh: Shelf) = sh.id == Collections.ADDED || sh.id == Collections.MOSTPLAYED

    private fun sortOf(sh: Shelf) = when {
        sh.id == Collections.ADDED -> Sorts.ADDED
        sh.id == Collections.MOSTPLAYED -> Sorts.MOST
        else -> colOf(sh)?.sort ?: cols.sortFor(sh.id)
    }
    private fun sortsFor(sh: Shelf): List<Int> = when {
        fixedSort(sh) -> emptyList()
        byConsole(sh) -> Sorts.WITHIN + (if (colOf(sh) != null) listOf(Sorts.MINE) else emptyList())
        sh.id.startsWith("sys:") -> Sorts.ALL - Sorts.CONSOLE
        else -> Sorts.ALL
    }
    private fun setSort(sh: Shelf, s: Int) { colOf(sh)?.let { it.sort = s; cols.save() } ?: cols.setSortFor(sh.id, s); changed = true }

    private fun year(g: Game) = Facts.of(g)?.year
    // a getter, not a stored value: the Library sorts while it opens (in init), before a value declared down here exists
    private val byName: Comparator<Game> get() = compareBy { it.title.lowercase() }

    /** The order within a group. */
    private fun within(sort: Int, col: Collections.Collection?): Comparator<Game> = when (sort) {
        Sorts.ZA -> compareByDescending { it.title.lowercase() }
        Sorts.RECENT -> compareByDescending<Game> { games.lastPlayed(it) }.then(byName)
        Sorts.MOST -> compareByDescending<Game> { games.timesPlayed(it) }.then(byName)
        Sorts.TIME -> compareByDescending<Game> { games.playTime(it) }.then(byName)
        Sorts.ADDED -> compareByDescending<Game> { cols.added(it) }.then(byName)
        Sorts.NEWEST -> compareBy<Game> { year(it) == null }.thenByDescending { year(it) ?: 0 }.then(byName)
        Sorts.OLDEST -> compareBy<Game> { year(it) == null }.thenBy { year(it) ?: 0 }.then(byName)
        Sorts.MINE -> if (col != null) compareBy { g -> col.entries.indexOfFirst { it.path == g.path || (it.sys == g.sys && it.title == g.title) } } else byName
        else -> byName
    }

    /** A list's games in order, in groups (a heading over each) or one group without one. */
    private fun arrange(sh: Shelf, list: List<Game>): List<Pair<Group?, List<Game>>> {
        val sort = sortOf(sh); val col = colOf(sh)
        val consoles = Games.NAMES.keys.toList()
        fun consoleGroups(order: Comparator<Game>) = list.groupBy { it.sys }.toList().sortedBy { consoles.indexOf(it.first) }
            .map { (sys, gs) -> Group(Games.NAMES[sys] ?: sys, Theme.accentOf(sys), gs.size, 0f) to gs.sortedWith(order) }
        fun keyed(key: (Game) -> String?, ascending: Boolean = true): List<Pair<Group?, List<Game>>> {
            val g = list.groupBy { key(it) ?: "" }
            val names = g.keys.filter { it.isNotEmpty() }.let { if (ascending) it.sortedBy { k -> k.lowercase() } else it.sortedByDescending { k -> k.lowercase() } } + (if ("" in g) listOf("") else emptyList())
            return names.map { k -> Group(k.ifEmpty { "Unknown" }, null, g[k]!!.size, 0f) to g[k]!!.sortedWith(byName) }
        }
        return when {
            byConsole(sh) -> consoleGroups(within(sort, col))
            sort == Sorts.CONSOLE -> consoleGroups(byName)
            sort == Sorts.GENRE -> keyed({ Facts.of(it)?.genres?.firstOrNull() })
            sort == Sorts.DEVELOPER -> keyed({ Facts.of(it)?.developer })
            sort == Sorts.PUBLISHER -> keyed({ Facts.of(it)?.publisher })
            sort == Sorts.NEWEST || sort == Sorts.OLDEST -> {
                val order = within(sort, col)
                list.sortedWith(order).groupBy { year(it)?.let { y -> "${y / 10 * 10}s" } ?: "Unknown" }.toList().map { (k, gs) -> Group(k, null, gs.size, 0f) to gs }
            }
            else -> listOf(null to list.sortedWith(within(sort, col)))
        }
    }

    /** Shows a list on the right. */
    private fun open(sh: Shelf, quick: Boolean = false) {
        val same = shelf?.id == sh.id
        shelf = sh
        if (sh.id == "hidden" && !unlocked) { heading.text = "Hidden"; layout(emptyList()); summary = "Locked"; return }
        val keep = cells.getOrNull(sel)?.game
        val add = adding
        layout(if (add != null) arrangeAll() else arrange(sh, gamesOf(sh)))
        sel = if (same && keep != null) cells.indexOfFirst { it.game == keep }.coerceAtLeast(0) else 0
        if (!same) { scroll = 0f; scrollTo = 0f }
        pillAt = pillAt.coerceIn(0, max(0, pills().size - 1))
        refreshHeading()
        if (!quick) Sounds.play(Sounds.MOVE)
    }
    /** Add games: every game, console by console, by name. */
    private fun arrangeAll() = cols.visible().groupBy { it.sys }.toList().sortedBy { Games.NAMES.keys.indexOf(it.first) }
        .map { (sys, gs) -> Group(Games.NAMES[sys] ?: sys, Theme.accentOf(sys), gs.size, 0f) as Group? to gs.sortedWith(byName) }

    private fun refreshHeading() {
        val sh = shelf ?: return
        val add = adding
        val n = cells.size
        heading.text = if (add != null) "Adding to ${add.name}" else sh.name.removePrefix("✦ ")
        val f = filterOf(sh)
        summary = when {
            add != null -> "${add.entries.size} in it  ·  A puts a game in or takes it out  ·  B when you're done"
            arranging != null -> "Arranging  ·  A picks up a game and puts it down  ·  B when you're done"
            sh.id == "hidden" && !unlocked -> "Locked"
            n == 0 -> when {
                f != null && f.count > 0 -> "No games match these filters."
                sh.id == Collections.FAVORITES -> "Nothing here yet. Y on any game makes it a favorite."
                sh.id == Collections.UNPLAYED -> "You've played everything."
                sh.id == Collections.MOSTPLAYED -> "Play something and it shows up here."
                colOf(sh) != null -> "Empty. Add games above, or X on any game."
                else -> "No games."
            }
            else -> "$n ${if (n == 1) "game" else "games"}" + (f?.takeIf { it.count > 0 }?.let { "  ·  " + it.summary(cols) } ?: "")
        }
    }

    /** Lays the groups out: each group's heading (if it has one), then rows of four, the boxes standing on each row's line. */
    private fun layout(list: List<Pair<Group?, List<Game>>>) {
        val out = ArrayList<Cell>(); val gs = ArrayList<Group>()
        var y = 0f; var row = -1
        for ((g, games) in list) {
            if (g != null) { gs += Group(g.name, g.dot, games.size, y); y += HEAD }
            games.chunked(COLS).forEach { line ->
                row++
                val sizes = line.map { BOX_MM[it.sys] ?: (130f to 170f) }
                val rh = sizes.maxOf { it.second } * S
                line.forEachIndexed { c, game -> val (w, h) = sizes[c]; out += Cell(game, GX + CW / 2 + c * CW, y + rh - h * S / 2, w * S, h * S, row, c, gs.size - 1) }
                y += rh + UNDER
            }
            y += 14f
        }
        // the boxes no longer in the list go back to be used again
        val now = out.map { it.game.path }.toSet()
        boxes.keys.filter { it !in now }.forEach { p -> boxes.remove(p)?.let { release(it, p) } }
        out.forEach { c -> boxes[c.game.path]?.let { c.case = it; c.arrive = 1f } }
        cells = out; groups = gs; contentH = y
        sel = sel.coerceIn(0, max(0, cells.size - 1))
    }

    private fun focusOn(f: String) {
        focus = f
        list.active = f == "list"; list.invalidate()
    }

    // ---------- the pills ----------

    private class Pill(val text: String, val key: String)
    /** The pills over the boxes: the order, the filters, and what a list of yours can do. */
    private fun pills(): List<Pill> {
        val sh = shelf ?: return emptyList()
        if (adding != null || arranging != null || (sh.id == "hidden" && !unlocked)) return emptyList()
        val out = ArrayList<Pill>()
        val col = colOf(sh)
        val sort = sortOf(sh)
        if (sortsFor(sh).isNotEmpty()) out += Pill((if (byConsole(sh)) "Each console by " else "Sorted by ") + Sorts.NAMES[sort].replaceFirstChar { it.lowercase() }, "sort")
        val f = filterOf(sh)
        if (f != null) out += Pill(if (f.count == 0) "Filter" else "Filters  ·  ${f.count}", "filter")
        if (f != null && f.count > 0) out += Pill("Clear", "clear")
        if (sh.id == "all" && f != null && f.count > 0) out += Pill("Save as a collection", "save")
        if (col != null && !col.live) { out += Pill("Add games", "add"); out += Pill("Arrange", "arrange") }
        if (cells.size > 2) out += Pill("Surprise me", "surprise")
        return out
    }

    private fun pillAct(p: Pill) {
        val sh = shelf ?: return
        val col = colOf(sh)
        when (p.key) {
            "sort" -> chooseSort(sh)
            "filter" -> filterCard(sh)
            "clear" -> { filterOf(sh)?.let { it.clear(); saveFilter(sh, it) }; Sounds.play(Sounds.CLICK); refresh(); toTop() }
            "save" -> host.askText("Name the collection", "") { name ->
                if (name.isBlank()) return@askText
                val c = cols.createLive(name.trim().take(40), sortOf(sh), cols.filterFor("all")); changed = true
                fillList(); list.rows.indexOfFirst { it.tag == "c:" + c.id }.takeIf { it >= 0 }?.let { list.focus(it) }
                open(shelves.first { it.id == "c:" + c.id }); host.show("Saved ${c.name}: it keeps itself up to date")
            }
            "add" -> col?.let { startAdding(it) }
            "surprise" -> surprise()
            "arrange" -> col?.let { startArranging(sh, it) }
        }
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
        for (v in listOf(heading, labels)) { v.translationY = (1 - k) * 760f; v.alpha = 1 - pageK }
        if (leaving && shownK == 0f) { destroy(); host.closed(this, changed); return }
        page?.tick(dt)
        // scrolling: the chosen row stays near the middle of what's left under the heading
        cells.getOrNull(sel)?.let { c -> val mid = (TOP + 750f) / 2 + 20f; scrollTo = (c.y - (mid - TOP)).coerceIn(0f, max(0f, contentH - (750f - TOP) + 30f)) }
        scroll += (scrollTo - scroll) * min(1f, dt * 9f)
        backdrop.shadows.clear()
        if (launching) { cells.forEach { it.case?.hide() }; return }
        dress()
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
            // the chosen one: toward you, turned a little more to face you (it rises only a little, so it stays clear of
            // the heading over its row)
            case.place(c.x, y - c.lift * 6f + (1 - arrive) * 18f + bob + (1 - k) * 760f, 10f + 46f * c.lift, RY * (1 - .5f * c.lift), -.06f, max(c.w, c.h) * grow, held = false)
            if (arrive > .05f) backdrop.shadows.add(floatArrayOf(c.x, y + c.h / 2 * grow + 16f + (1 - k) * 760f, c.w * grow * .95f, (.55f + .3f * c.lift) * edge * (1 - away)))
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

    /**
     * The boxes near the screen get dressed (two a frame from the spare ones, or one new one); those far from it go
     * back to the spares; art read for boxes no longer wanted is let go.
     */
    private fun dress() {
        val want = HashSet<String>()
        val near = cells.withIndex().filter { (_, c) -> sy(c) > -320 && sy(c) < 1100 }.sortedBy { (i, _) -> abs(i - sel) }
        var reused = 0; var made = 0
        for ((_, c) in near) {
            val path = c.game.path; want += path
            if (c.case != null) continue
            val f = reading[path] ?: reader.submit(java.util.concurrent.Callable { art(c.game) }).also { reading[path] = it }
            if (!f.isDone) continue
            val pool = spare[c.game.sys]
            if (pool.isNullOrEmpty() && made >= 1) continue
            if (!pool.isNullOrEmpty() && reused >= 2) continue
            reading.remove(path)
            val art = try { f.get() } catch (e: Exception) { arrayOfNulls<Bitmap>(3) }
            // one box that can't be dressed (a broken picture file, say) is left out, rather than closing Shelf
            val case = try {
                pool?.removeFirstOrNull()?.also { it.setArt(c.game.title, art[0], art[1], art[2]); reused++ }
                    ?: carts.model("case-${c.game.sys}.glb")?.let { Case(stage, it, c.game.title, sampler, art[0], art[1], art[2]) }?.also {
                        it.open = 0f; Outline.mark(stage, it.asset.entities, Outline.BOX); made++ }
            } catch (e: Exception) { android.util.Log.w("Shelf", "Couldn't dress the box for ${c.game.title}", e); null }
            if (case == null) { art.forEach { if (it?.isRecycled == false) it.recycle() }; continue }
            c.case = case; boxes[path] = case; c.arrive = 0f
        }
        // far off the screen: back to the spares
        for (c in cells) if (c.case != null && (sy(c) < -700 || sy(c) > 1500)) { boxes.remove(c.game.path)?.let { release(it, c.game.path) }; c.case = null }
        // art read for a box that's no longer wanted (scrolled past, another list): let go once it's read
        reading.entries.removeAll { (p, f) -> if (p !in want && f.isDone) { try { f.get().forEach { it?.recycle() } } catch (_: Exception) { }; true } else false }
    }

    /** A box no longer showing a game: kept (out of sight) to be dressed for another, or let go if there are plenty. */
    private fun release(case: Case, path: String) {
        val sys = cells.firstOrNull { it.game.path == path }?.game?.sys ?: games.all.firstOrNull { it.path == path }?.sys
        case.hide()
        val pool = sys?.let { spare.getOrPut(it) { ArrayDeque() } }
        if (pool != null && pool.size < 10) pool.addLast(case) else case.destroy()
    }

    /**
     * A game's box art for the grid, read on another thread: front, back and spine at the sizes the grid shows them
     * (smaller than the page needs), Shelf's paintings for any it doesn't have, drawn here too, not on the main thread.
     */
    private fun art(g: Game): Array<Bitmap?> {
        val pics = meta.cached(g)?.pics
        val (w, h) = BOX_MM[g.sys] ?: (130f to 170f); val aspect = w / h
        val front = pics?.get("box")?.let { meta.bitmap(it, 360) } ?: Paintings.label(360, (360 / aspect).toInt(), g.title)
        val back = pics?.get("back")?.let { meta.bitmap(it, 256) } ?: Paintings.picture(256, (256 / aspect).toInt(), "${g.title} back")
        val spine = pics?.get("spine")?.let { meta.bitmap(it, 160) } ?: Paintings.picture(64, 420, g.title)
        return arrayOf(front, back, spine)
    }

    /** Where a cell is on the screen now (its centre's height). */
    private fun sy(c: Cell) = TOP + c.y - scroll

    // ---------- buttons ----------

    fun input(a: String) {
        page?.let { it.input(a); return }
        if (leaving || launching) return
        when (focus) { "list" -> listInput(a); "pills" -> pillInput(a); else -> gridInput(a) }
    }

    private fun listInput(a: String) {
        val r = list.current()
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "right", "a" -> when {
                r?.key == "new" -> if (a == "a") newCollection() else Sounds.play(Sounds.BUMP)
                r?.tag == "hidden" && !unlocked -> if (a == "a") unlock() else Sounds.play(Sounds.BUMP)
                // A starts from the top (a quick way back up from deep in a list); right goes back to where you were
                cells.isNotEmpty() -> { Sounds.play(Sounds.PICK); if (a == "a") toTop(); focusOn("grid") }
                pills().isNotEmpty() -> { Sounds.play(Sounds.PICK); pillAt = 0; focusOn("pills") }
                else -> Sounds.play(Sounds.BUMP)
            }
            "start" -> if (pills().isNotEmpty()) { Sounds.play(Sounds.MOVE); pillAt = 0; focusOn("pills") }
            "y" -> shelf?.let { menu(it) }
            "select" -> if (!unlocked && !cols.hiddenEntry && cols.hasCode) unlock()   // Hidden not listed: Select, then the code
            "b" -> close()
        }
    }

    private fun pillInput(a: String) {
        val ps = pills()
        if (ps.isEmpty()) return focusOn("list")
        when (a) {
            "left" -> if (pillAt > 0) { pillAt--; Sounds.play(Sounds.MOVE) } else { Sounds.play(Sounds.MOVE); focusOn("list") }
            "right" -> if (pillAt < ps.size - 1) { pillAt++; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "down", "start" -> if (cells.isNotEmpty()) { Sounds.play(Sounds.MOVE); focusOn("grid") } else Sounds.play(Sounds.BUMP)
            "a" -> { Sounds.play(Sounds.PICK); pillAct(ps[pillAt.coerceIn(0, ps.size - 1)]) }
            "b" -> { Sounds.play(Sounds.MOVE); focusOn("list") }
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
            "up" -> if (c != null && c.row == 0 && pills().isNotEmpty()) { Sounds.play(Sounds.MOVE); focusOn("pills") } else vertical(-1)
            "down" -> vertical(1)
            "lt", "rt" -> jumpGroup(if (a == "lt") -1 else 1)   // L1 and R1 step between pages
            "start" -> if (pills().isNotEmpty()) { Sounds.play(Sounds.MOVE); focusOn("pills") }
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
        val inRow = cells.withIndex().filter { it.value.row == c.row + d }
        if (inRow.isEmpty()) return Sounds.play(Sounds.BUMP)
        sel = inRow.minByOrNull { abs(it.value.x - c.x) }!!.index; Sounds.play(Sounds.MOVE)
    }

    private fun jumpGroup(d: Int) {
        val c = cells.getOrNull(sel) ?: return
        val to = if (groups.isEmpty()) -1 else cells.indexOfFirst { it.group == c.group + d }
        if (to < 0) return Sounds.play(Sounds.BUMP)
        sel = to; Sounds.play(Sounds.MOVE)
    }

    /**
     * Back to the first game (a new order or new filters, or A on the list you're in): straight to the top, the boxes
     * there settling in as they do when a list opens, rather than a long slide past everything in between.
     */
    private fun toTop() {
        sel = 0; scroll = 0f; scrollTo = 0f
        cells.forEach { it.arrive = 0f }
    }

    /**
     * Surprise me: a game from this list, chosen at random (not the one you're on, and leaning toward games not played
     * lately); the list glides to it and it lifts, glowing, with a chime.
     */
    private fun surprise() {
        if (cells.size < 2) return Sounds.play(Sounds.BUMP)
        val now = System.currentTimeMillis()
        val weights = cells.mapIndexed { i, c -> if (i == sel) 0.0 else 1.0 + minOf(30.0, (now - games.lastPlayed(c.game)) / 86_400_000.0) / 10.0 }
        var r = Math.random() * weights.sum(); var pick = 0
        for ((i, w) in weights.withIndex()) { r -= w; if (r <= 0) { pick = i; break } }
        sel = pick; focusOn("grid"); Sounds.play(Sounds.CHIME)
        host.show("How about ${cells[pick].game.title}?")
    }

    /** After something changed (a favorite, a collection, a hidden game): the counts and the list shown again. */
    private fun refresh() { fillList(); shelf?.let { open(it, quick = true) } }

    // ---------- lists: making, ordering, filtering, changing ----------

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

    private fun startArranging(sh: Shelf, c: Collections.Collection) {
        c.sort = Sorts.MINE; cols.save(); arranging = c; adding = null; open(sh, quick = true); focusOn("grid"); refreshHeading()
    }

    /** Y on a list: what you can do with it. */
    private fun menu(sh: Shelf) {
        val col = colOf(sh)
        if ((sh.id == "hidden" && !unlocked) || (col == null && sortsFor(sh).isEmpty() && !cols.pinnable(sh.id))) return Sounds.play(Sounds.BUMP)
        val rows = ArrayList<ListCard.Row>()
        if (col != null && !col.live) { rows += ListCard.Row("Add games", icon = "grid", tag = "add"); rows += ListCard.Row("Arrange", note = "Your own order", icon = "move", tag = "arrange") }
        if (col != null && col.live) rows += ListCard.Row("Filters", note = col.filter.count.let { if (it == 0) "None" else "$it" }, icon = "eye", tag = "filter")
        if (sortsFor(sh).isNotEmpty()) rows += ListCard.Row("Order", note = Sorts.NAMES[sortOf(sh)], icon = "sort", tag = "sort")
        // pinned, Home's row can show it (0.33), chosen in the panel left of the first game (0.34)
        if (cols.pinnable(sh.id)) rows += ListCard.Row(if (cols.isPinned(sh.id)) "Unpin from Home" else "Pin to Home", note = if (cols.isPinned(sh.id)) "In Home's list, left of the first game" else "", icon = "house", tag = "pin")
        if (col != null) {
            rows += ListCard.Row("Rename", icon = "pencil", tag = "rename"); rows += ListCard.Row("Color", dot = col.color, tag = "color")
            rows += ListCard.Row("Move up the list", icon = "move", tag = "up"); rows += ListCard.Row("Move down the list", icon = "move", tag = "down")
            rows += ListCard.Row("Delete", icon = "bin", tag = "delete")
        }
        val sub = if (col == null) null else if (col.live) "Live: whichever games match its filters" else "${col.entries.size} ${if (col.entries.size == 1) "game" else "games"}"
        Overlays.show(ListCard(ctx, sh.name.removePrefix("✦ "), sub, { rows }) { _, r ->
            when (r.tag) {
                "add" -> { startAdding(col!!); true }
                "arrange" -> { startArranging(sh, col!!); true }
                "filter" -> { filterCard(sh); true }
                "sort" -> { chooseSort(sh); true }
                "pin" -> { val on = !cols.isPinned(sh.id); cols.setPinned(sh.id, on); changed = true
                    host.show(if (on) "${sh.name.removePrefix("✦ ")} is on Home: press left past the first game" else "Unpinned from Home"); true }
                "rename" -> { host.askText("Rename the collection", col!!.name) { n -> if (n.isNotBlank()) { col.name = n.trim().take(40); cols.save(); changed = true; fillList(); open(shelves.first { it.id == sh.id }, quick = true) } }; true }
                "color" -> { chooseColor(col!!); true }
                "up", "down" -> { cols.move(col!!, if (r.tag == "up") -1 else 1); changed = true; fillList(); false }
                "delete" -> { confirmDelete(col!!); true }
                else -> true
            }
        })
    }

    private fun chooseSort(sh: Shelf) {
        val options = sortsFor(sh)
        if (options.isEmpty()) return Sounds.play(Sounds.BUMP)
        Overlays.show(ListCard(ctx, "Order", if (byConsole(sh)) "Within each console" else null, { options.map { ListCard.Row(Sorts.NAMES[it], check = sortOf(sh) == it, tag = it) } },
            start = options.indexOf(sortOf(sh)).coerceAtLeast(0)) { _, r ->
            setSort(sh, r.tag as Int); open(sh, quick = true); toTop(); true
        })
    }

    /**
     * The filters (All games, or a live collection's own): each kind on its own row, saying what's chosen; A opens its
     * choices (ticks, the card stays open, the boxes behind change as you go).
     */
    private fun filterCard(sh: Shelf) {
        val f = filterOf(sh) ?: return
        fun apply() { saveFilter(sh, f); fillList(); open(sh, quick = true); toTop() }
        fun note(set: Collection<Any>, name: (Any) -> String) = when (set.size) { 0 -> "Any"; 1 -> name(set.first()); else -> "${set.size} chosen" }
        val all = cols.visible()
        lateinit var card: ListCard
        card = ListCard(ctx, "Filters", if (Facts.ready) "Show only games that match" else "Still reading your games' details…", {
            listOf(
                ListCard.Row("Consoles", note = note(f.consoles) { Games.NAMES[it as String] ?: it.toString() }, tag = "consoles"),
                ListCard.Row("Genres", note = note(f.genres) { it.toString() }, tag = "genres"),
                ListCard.Row("Played", note = Filter.PLAYED_NAMES[f.played], tag = "played"),
                ListCard.Row("Favorites only", check = f.favorites, tag = "favorites"),
                ListCard.Row("Release years", note = note(f.decades) { "${it}s" }, tag = "decades"),
                ListCard.Row("Players", note = Filter.PLAYER_NAMES[f.players], tag = "players"),
                ListCard.Row("Developers", note = note(f.developers) { it.toString() }, tag = "developers"),
                ListCard.Row("Publishers", note = note(f.publishers) { it.toString() }, tag = "publishers"),
                ListCard.Row("In a collection", note = note(f.collections) { cols.byId(it as String)?.name ?: "" }, tag = "collections"),
                ListCard.Row("Added this month", check = f.recent, tag = "recent")) +
                (if (f.count > 0) listOf(ListCard.Row("Clear all filters", icon = "bin", tag = "clear")) else emptyList())
        }) { _, r ->
            when (r.tag) {
                "favorites" -> { f.favorites = !f.favorites; apply() }
                "recent" -> { f.recent = !f.recent; apply() }
                "clear" -> { f.clear(); apply() }
                "played" -> radio("Played", Filter.PLAYED_NAMES, { f.played }) { f.played = it; apply(); card.invalidate() }
                "players" -> radio("Players", Filter.PLAYER_NAMES, { f.players }) { f.players = it; apply(); card.invalidate() }
                "consoles" -> ticks("Consoles", all.groupingBy { it.sys }.eachCount().toList().sortedBy { Games.NAMES.keys.indexOf(it.first) }.map { (k, n) -> Triple(k, Games.NAMES[k] ?: k, n) }, f.consoles, Theme::accentOf) { apply(); card.invalidate() }
                "genres" -> ticks("Genres", counted(all) { Facts.of(it)?.genres ?: emptyList() }, f.genres) { apply(); card.invalidate() }
                "developers" -> ticks("Developers", counted(all) { listOfNotNull(Facts.of(it)?.developer) }, f.developers) { apply(); card.invalidate() }
                "publishers" -> ticks("Publishers", counted(all) { listOfNotNull(Facts.of(it)?.publisher) }, f.publishers) { apply(); card.invalidate() }
                "collections" -> ticks("In a collection", cols.all.filter { !it.live }.map { Triple(it.id, it.name, cols.gamesOf(it).size) }, f.collections, { id -> cols.byId(id)?.color }) { apply(); card.invalidate() }
                "decades" -> {
                    val d = all.mapNotNull { year(it)?.let { y -> y / 10 * 10 } }.groupingBy { it }.eachCount().toList().sortedBy { it.first }
                    tickInts("Release years", d.map { (k, n) -> Triple(k, "${k}s", n) }, f.decades) { apply(); card.invalidate() }
                }
            }
            false
        }
        Overlays.show(card)
    }

    /** Every value a detail takes across these games, with how many have it, by name. */
    private fun counted(list: List<Game>, of: (Game) -> List<String>) =
        list.flatMap(of).groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }.map { (k, n) -> Triple(k, k, n) }

    /** A card of choices to tick (several), each with how many games have it. */
    private fun ticks(title: String, options: List<Triple<String, String, Int>>, set: MutableSet<String>, dot: (String) -> Int? = { null }, changed: () -> Unit) {
        if (options.isEmpty()) return host.show(if (Facts.ready) "None of your games say" else "Still reading your games' details; try again in a moment")
        Overlays.show(ListCard(ctx, title, "Any of these", { options.map { (k, name, n) -> ListCard.Row(name, note = "$n", dot = dot(k), check = k in set, tag = k) } }) { _, r ->
            val k = r.tag as String; if (!set.remove(k)) set += k; changed(); false
        })
    }
    private fun tickInts(title: String, options: List<Triple<Int, String, Int>>, set: MutableSet<Int>, changed: () -> Unit) {
        if (options.isEmpty()) return host.show(if (Facts.ready) "None of your games say" else "Still reading your games' details; try again in a moment")
        Overlays.show(ListCard(ctx, title, "Any of these", { options.map { (k, name, n) -> ListCard.Row(name, note = "$n", check = k in set, tag = k) } }) { _, r ->
            val k = r.tag as Int; if (!set.remove(k)) set += k; changed(); false
        })
    }
    /** A card of choices to pick one of. */
    private fun radio(title: String, names: List<String>, now: () -> Int, picked: (Int) -> Unit) {
        Overlays.show(ListCard(ctx, title, null, { names.mapIndexed { i, n -> ListCard.Row(n, check = now() == i, tag = i) } }, start = now()) { _, r -> picked(r.tag as Int); true })
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
        page = GamePage(ctx, board, backdrop, stage, games, carts, meta, g, cart, floatArrayOf(c.x, sy(c), max(c.w, c.h) * (1 + .06f * c.lift), RY * (1 - .5f * c.lift)), this, fromBox = true, tall = true)
        case.hide()
    }

    /** Starting a game from its page here: its cartridge goes to the start animation, and comes back to the page. */
    /** Whether L1 and R1 may step to the next page: not from a game's page, nor while adding or arranging. */
    val canLeave get() = page == null && adding == null && arranging == null && !leaving
    /** The game's page is open with its box out. */
    fun boxOut(g: Game) = page?.takeIf { it.game.path == g.path }?.boxOut == true
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
        spare.values.forEach { q -> q.forEach { it.destroy() } }; spare.clear()
        reading.values.forEach { f -> reader.execute { try { f.get().forEach { it?.recycle() } } catch (_: Exception) { } } }; reading.clear()
        reader.shutdown()
        backdrop.shadows.clear()
        board.removeView(listPanel); board.removeView(heading); board.removeView(labels)
    }

    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }

    /**
     * The words among the boxes, drawn in one go each frame: the pills and the summary under the heading, each group's
     * heading (a console's color dot, its name, how many, a fine line), the chosen box's name and when you last played
     * it, and in Add games a tick on every box that's in the collection.
     */
    private inner class Labels(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            // the pills
            var x = GX + 10f
            pills().forEachIndexed { i, pill ->
                p.typeface = Fonts.bold; p.textSize = 19f
                val w = p.measureText(pill.text) + 40f; val on = focus == "pills" && i == pillAt
                val r = RectF(x, PILL_Y, x + w, PILL_Y + PILL_H)
                p.color = if (on) Theme.sel else Theme.chip; c.drawRoundRect(r, PILL_H / 2, PILL_H / 2, p)
                if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.sel; c.drawRoundRect(RectF(r.left - 4, r.top - 4, r.right + 4, r.bottom + 4), PILL_H / 2 + 4, PILL_H / 2 + 4, p); p.style = Paint.Style.FILL }
                p.color = if (on) Theme.selFg else Theme.fg; c.drawText(pill.text, x + 20f, PILL_Y + PILL_H / 2 + 7f, p)
                x += w + 12f
            }
            p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2
            c.drawText(TextUtils.ellipsize(summary, android.text.TextPaint(p), 1300f - GX - 12f, TextUtils.TruncateAt.END).toString(), GX + 12f, PILL_Y + PILL_H + 32f, p)
            c.save(); c.clipRect(GX - 20, TOP - 16, 1334f, 750f)
            for (g in groups) {
                val y = TOP + g.y - scroll + 40f
                if (y < TOP - 40 || y > 790) continue
                var tx = GX + 12
                g.dot?.let { d -> p.color = d; c.drawCircle(GX + 22, y - 8, 7f, p); tx = GX + 40 }
                p.typeface = Fonts.bold; p.textSize = 21f; p.letterSpacing = .06f; p.color = Theme.fg
                val name = g.name.uppercase(); c.drawText(name, tx, y, p)
                val w = p.measureText(name); p.letterSpacing = 0f
                p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2; c.drawText("${g.count}", tx + 14 + w, y, p)
                p.color = Theme.line; c.drawRect(tx + 30 + w + p.measureText("${g.count}"), y - 8, 1300f, y - 7, p)
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
                    val note = (if (arranging != null && carrying) "Moving  ·  " else "") + if (played == 0L) "Not played yet" else "Played " + android.text.format.DateUtils.getRelativeTimeSpanString(played, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS) +
                        (playTimeText(games.playTime(cell.game))?.let { "  ·  $it" } ?: "")
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
