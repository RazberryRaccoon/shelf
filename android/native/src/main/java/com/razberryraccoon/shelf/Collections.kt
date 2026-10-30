package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Your collections, and the games you've hidden.
 *
 * A collection is a name, a color and any games (a game can be in as many as you like), shown in the Library one console
 * after another, each console's games in the collection's own order: A to Z, recently played, most played, by release
 * year, or your own arrangement. Favorites is one too (the same favorites as Y and the heart), plus three that fill
 * themselves: Recently added, Not played yet and Most played (Settings can turn those off).
 *
 * Kept in the app's preferences and copied to Shelf/Media/collections.json, so a media backup carries them. Each game is
 * remembered by its file, and by its console and name too, so a backup restored on another handheld (where the files
 * sit somewhere else) still finds them.
 *
 * Hidden games disappear everywhere (Home, the Library, collections) until you open the Library's Hidden with your code:
 * four presses of the d-pad and face buttons. Not real security, just against snooping: Settings can reset the code
 * without knowing it.
 */
class Collections(ctx: Context, private val games: Games, private val mediaRoot: () -> File?) {
    private val prefs = ctx.getSharedPreferences("shelf", Context.MODE_PRIVATE)

    class Entry(val path: String, val sys: String, val title: String)
    /**
     * A collection. `rule` set: a live one, a saved sort and filters (from the Library's Sort and Filter), whose games
     * are whichever match now; renamed, recolored, edited or deleted like any other.
     */
    class Collection(val id: String, var name: String, var color: Int, var sort: Int, val entries: MutableList<Entry>, var rule: JSONObject? = null) {
        val live get() = rule != null
        val filter get() = Filter.from(rule?.optJSONObject("filter"))
    }

    companion object {
        /** The orders (see Sorts; these keep their old numbers, so saved collections read the same). */
        const val AZ = Sorts.AZ; const val RECENT = Sorts.RECENT; const val MOST = Sorts.MOST; const val MINE = Sorts.MINE
        /** Colors to choose from: soft, bright enough to glow on the dark backdrop. */
        val COLORS = listOf("Coral" to "#ff8a7a", "Amber" to "#ffc35c", "Lime" to "#b6e36a", "Mint" to "#62e0b8", "Sky" to "#6fc8ff",
            "Iris" to "#9a8cff", "Orchid" to "#e28cf5", "Rose" to "#ff8fb8").map { it.first to Color.parseColor(it.second) }
        /** The buttons a hidden-games code is made of, and how they're shown. */
        val KEYS = listOf("up", "down", "left", "right", "a", "b", "x", "y")
        const val FAVORITES = "favorites"; const val ADDED = "added"; const val UNPLAYED = "unplayed"; const val MOSTPLAYED = "mostplayed"
    }

    val all = ArrayList<Collection>()

    init { load() }

    private fun load() {
        all.clear()
        val a = try { JSONArray(prefs.getString("collections", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val list = o.optJSONArray("games") ?: JSONArray()
            all += Collection(o.optString("id"), o.optString("name"), o.optInt("color", COLORS[0].second), o.optInt("sort", AZ),
                MutableList(list.length()) { j -> list.getJSONObject(j).let { Entry(it.optString("path"), it.optString("sys"), it.optString("title")) } }, o.optJSONObject("rule"))
        }
    }

    private fun json(): JSONArray = JSONArray().also { a ->
        for (c in all) a.put(JSONObject().put("id", c.id).put("name", c.name).put("color", c.color).put("sort", c.sort).put("rule", c.rule ?: JSONObject.NULL)
            .put("games", JSONArray().also { g -> c.entries.forEach { e -> g.put(JSONObject().put("path", e.path).put("sys", e.sys).put("title", e.title)) } }))
    }

    fun save() {
        val s = json().toString()
        prefs.edit().putString("collections", s).apply()
        // a copy in the media folder, so a media backup carries your collections
        try { mediaRoot()?.let { File(it, "collections.json").writeText(s) } } catch (_: Exception) { }
    }

    /** After a media backup is restored: its collections replace these (if it has any). */
    fun restored() {
        val f = mediaRoot()?.let { File(it, "collections.json") } ?: return
        if (!f.isFile) return
        try { JSONArray(f.readText()); prefs.edit().putString("collections", f.readText()).apply(); load() } catch (_: Exception) { }
    }

    // ---------- membership ----------

    /** The game behind an entry: by its file, or else by its console and name (a restored backup on another handheld). */
    private fun matches(e: Entry, g: Game) = e.path == g.path || (e.sys == g.sys && e.title == g.title)
    fun has(c: Collection, g: Game) = if (c.live) c.filter.matches(g, games, this) else c.entries.any { matches(it, g) }
    /** The ones you put the game in yourself (live collections choose their own games). */
    fun of(g: Game) = all.filter { !it.live && has(it, g) }
    fun add(c: Collection, g: Game) { if (!has(c, g)) { c.entries += Entry(g.path, g.sys, g.title); save() } }
    fun remove(c: Collection, g: Game) { c.entries.removeAll { matches(it, g) }; save() }
    fun toggle(c: Collection, g: Game): Boolean { val on = !has(c, g); if (on) add(c, g) else remove(c, g); return on }

    /** A live collection: these filters and this order, saved under a name. */
    fun createLive(name: String, sort: Int, filter: Filter): Collection =
        create(name).also { it.sort = sort; it.rule = JSONObject().put("filter", filter.json()); save() }

    fun create(name: String): Collection {
        // the next color nobody's using yet (or round again)
        val used = all.map { it.color }.toSet()
        val color = COLORS.firstOrNull { it.second !in used }?.second ?: COLORS[all.size % COLORS.size].second
        return Collection("c" + System.currentTimeMillis().toString(36), name, color, AZ, ArrayList()).also { all += it; save() }
    }
    fun delete(c: Collection) { all.remove(c); save() }
    fun move(c: Collection, d: Int) { val i = all.indexOf(c); val j = (i + d).coerceIn(0, all.size - 1); if (i != j) { all.removeAt(i); all.add(j, c); save() } }
    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Your own order, within a console: moves the game one place earlier or later among that console's games. */
    fun nudge(c: Collection, g: Game, d: Int): Boolean {
        val mine = c.entries.withIndex().filter { it.value.sys == g.sys }
        val at = mine.indexOfFirst { matches(it.value, g) }; val to = at + d
        if (at < 0 || to !in mine.indices) return false
        val a = mine[at].index; val b = mine[to].index
        val t = c.entries[a]; c.entries[a] = c.entries[b]; c.entries[b] = t
        save(); return true
    }

    // ---------- what each list holds ----------

    /** Every game you can see: hidden ones only while the Library's Hidden is open (`unlocked`). */
    fun visible(unlocked: Boolean = false) = games.all.filter { unlocked || !hidden(it) }

    fun gamesOf(c: Collection): List<Game> { val v = visible(); val f = if (c.live) c.filter else null
        return if (f != null) v.filter { f.matches(it, games, this) } else v.filter { has(c, it) } }

    /** When a game's file arrived on the handheld (as near as Shelf can tell: the file's date). */
    fun added(g: Game) = addedAt.getOrPut(g.path) { try { File(g.path).lastModified() } catch (_: Exception) { 0L } }
    private val addedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()   // read from storage once a game

    /** The three that fill themselves, by their rules. */
    fun smart(id: String): List<Game> = when (id) {
        FAVORITES -> visible().filter { games.favorite(it) }
        ADDED -> visible().sortedByDescending { added(it) }.take(30)
        UNPLAYED -> visible().filter { games.timesPlayed(it) == 0 }
        MOSTPLAYED -> visible().filter { games.timesPlayed(it) > 0 }.sortedByDescending { games.timesPlayed(it) }.take(30)
        else -> emptyList()
    }
    var smartOn: Boolean get() = prefs.getBoolean("smartCollections", true); set(v) = prefs.edit().putBoolean("smartCollections", v).apply()

    /** The order for a list that isn't one of yours (All games, a console, Favorites…), remembered per list. */
    fun sortFor(id: String) = prefs.getInt("libsort:$id", if (id == ADDED) RECENT else if (id == MOSTPLAYED) MOST else AZ)
    fun setSortFor(id: String, s: Int) = prefs.edit().putInt("libsort:$id", s).apply()
    /** All games' filters, remembered. */
    fun filterFor(id: String): Filter = Filter.from(try { JSONObject(prefs.getString("libfilter:$id", "{}") ?: "{}") } catch (_: Exception) { null })
    fun setFilterFor(id: String, f: Filter) = prefs.edit().putString("libfilter:$id", f.json().toString()).apply()

    // ---------- hidden games ----------

    fun hidden(g: Game) = prefs.getBoolean("hidden:" + g.path, false)
    fun setHidden(g: Game, on: Boolean) = prefs.edit().putBoolean("hidden:" + g.path, on).apply()
    /** The code: four of KEYS, or empty if none has been chosen yet. */
    var code: List<String>
        get() = prefs.getString("hiddenCode", "")?.split(",")?.filter { it in KEYS } ?: emptyList()
        set(v) = prefs.edit().putString("hiddenCode", v.joinToString(",")).apply()
    val hasCode get() = code.size == 4
    /** Whether the Library lists Hidden at all (off: only your code, entered on the Library's list, opens it). */
    var hiddenEntry: Boolean get() = prefs.getBoolean("hiddenEntry", true); set(v) = prefs.edit().putBoolean("hiddenEntry", v).apply()
}
