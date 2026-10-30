package com.razberryraccoon.shelf

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * What Shelf knows about each game for sorting and filtering, from libretro's details (Meta): its release year, its
 * genres, developer, publisher and how many players. Read for every game on another thread when the Library opens
 * (each is a small file), so the Library never waits on it; until it's done, the details just aren't there yet.
 */
object Facts {
    class F(val year: Int?, val genres: List<String>, val developer: String?, val publisher: String?, val players: Int?)
    private val map = ConcurrentHashMap<String, F>()
    @Volatile var ready = false; private set
    private var building = false

    fun of(g: Game): F? = map[g.path]

    /** Reads every game's details (again), then `done` on the main thread. */
    fun build(meta: Meta, list: List<Game>, done: () -> Unit) {
        if (building) return
        building = true
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            for (g in list) {
                val f = try { meta.peek(g)?.fields } catch (_: Exception) { null } ?: continue
                map[g.path] = F(
                    f["releaseyear"]?.take(4)?.toIntOrNull(),
                    f["genre"]?.split('/', ',', ';', '|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    f["developer"]?.trim()?.ifEmpty { null }, f["publisher"]?.trim()?.ifEmpty { null },
                    f["players"]?.let { p -> Regex("\\d+").findAll(p).mapNotNull { it.value.toIntOrNull() }.maxOrNull() })
            }
            ui.post { building = false; ready = true; done() }
        }.start()
    }
}

/**
 * Which games a list shows: any of the chosen consoles, genres, decades, developers, publishers and collections (an
 * empty choice means any), played or not, favorites only, single or multiplayer, added in the last month. Kept as JSON
 * (All games remembers its own; a live collection keeps its own).
 */
class Filter {
    val consoles = LinkedHashSet<String>(); val genres = LinkedHashSet<String>(); val decades = LinkedHashSet<Int>()
    val developers = LinkedHashSet<String>(); val publishers = LinkedHashSet<String>(); val collections = LinkedHashSet<String>()
    var played = ANY; var favorites = false; var players = ANY; var recent = false

    companion object {
        const val ANY = 0; const val PLAYED = 1; const val UNPLAYED = 2
        const val SINGLE = 1; const val MULTI = 2
        val PLAYED_NAMES = listOf("Any", "Played", "Not played yet")
        val PLAYER_NAMES = listOf("Any", "Single player", "Multiplayer")
        private const val MONTH = 30L * 24 * 3600 * 1000

        fun from(j: JSONObject?): Filter = Filter().also { f ->
            if (j == null) return@also
            fun set(k: String, to: MutableSet<String>) { j.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) to += a.optString(i) } }
            set("consoles", f.consoles); set("genres", f.genres); set("developers", f.developers); set("publishers", f.publishers); set("collections", f.collections)
            j.optJSONArray("decades")?.let { a -> for (i in 0 until a.length()) f.decades += a.optInt(i) }
            f.played = j.optInt("played"); f.favorites = j.optBoolean("favorites"); f.players = j.optInt("players"); f.recent = j.optBoolean("recent")
        }
    }

    fun json(): JSONObject = JSONObject().put("consoles", JSONArray(consoles)).put("genres", JSONArray(genres)).put("decades", JSONArray(decades))
        .put("developers", JSONArray(developers)).put("publishers", JSONArray(publishers)).put("collections", JSONArray(collections))
        .put("played", played).put("favorites", favorites).put("players", players).put("recent", recent)

    /** How many kinds of filter are on. */
    val count get() = listOf(consoles.isNotEmpty(), genres.isNotEmpty(), decades.isNotEmpty(), developers.isNotEmpty(), publishers.isNotEmpty(),
        collections.isNotEmpty(), played != ANY, favorites, players != ANY, recent).count { it }

    fun clear() { consoles.clear(); genres.clear(); decades.clear(); developers.clear(); publishers.clear(); collections.clear(); played = ANY; favorites = false; players = ANY; recent = false }

    fun matches(g: Game, games: Games, cols: Collections): Boolean {
        if (consoles.isNotEmpty() && g.sys !in consoles) return false
        if (played == PLAYED && games.timesPlayed(g) == 0) return false
        if (played == UNPLAYED && games.timesPlayed(g) > 0) return false
        if (favorites && !games.favorite(g)) return false
        if (recent && System.currentTimeMillis() - cols.added(g) > MONTH) return false
        if (collections.isNotEmpty() && collections.none { id -> cols.byId(id)?.let { c -> c.rule == null && cols.has(c, g) } == true }) return false
        val f = Facts.of(g)
        if (genres.isNotEmpty() && (f == null || f.genres.none { it in genres })) return false
        if (decades.isNotEmpty() && (f?.year == null || f.year / 10 * 10 !in decades)) return false
        if (developers.isNotEmpty() && (f?.developer == null || f.developer !in developers)) return false
        if (publishers.isNotEmpty() && (f?.publisher == null || f.publisher !in publishers)) return false
        if (players == SINGLE && (f?.players == null || f.players > 1)) return false
        if (players == MULTI && (f?.players == null || f.players < 2)) return false
        return true
    }

    /** The filters in a few words: "Game Boy Advance · Platform · Not played yet". */
    fun summary(cols: Collections): String = listOfNotNull(
        consoles.takeIf { it.isNotEmpty() }?.joinToString(", ") { Games.NAMES[it] ?: it },
        genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
        decades.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", ") { "${it}s" },
        developers.takeIf { it.isNotEmpty() }?.joinToString(", "),
        publishers.takeIf { it.isNotEmpty() }?.joinToString(", "),
        collections.takeIf { it.isNotEmpty() }?.mapNotNull { cols.byId(it)?.name }?.joinToString(", ")?.let { "In $it" },
        if (played != ANY) PLAYED_NAMES[played] else null, if (favorites) "Favorites" else null,
        if (players != ANY) PLAYER_NAMES[players] else null, if (recent) "Added this month" else null).joinToString("  ·  ")
}

/** The orders a list can be in, and which of them put headings between groups. */
object Sorts {
    const val AZ = 0; const val RECENT = 1; const val MOST = 2; const val OLDEST = 3; const val MINE = 4; const val ZA = 5; const val ADDED = 6
    const val NEWEST = 7; const val CONSOLE = 8; const val GENRE = 9; const val DEVELOPER = 10; const val PUBLISHER = 11
    val NAMES = listOf("Name, A to Z", "Recently played", "Most played", "Release year, oldest first", "Your order", "Name, Z to A", "Recently added",
        "Release year, newest first", "Console", "Genre", "Developer", "Publisher")
    /** In the order they're offered. */
    val ALL = listOf(AZ, ZA, RECENT, MOST, ADDED, NEWEST, OLDEST, CONSOLE, GENRE, DEVELOPER, PUBLISHER)
    /** Inside a collection the games are always grouped by console, so these are each console's own order. */
    val WITHIN = listOf(AZ, ZA, RECENT, MOST, ADDED, NEWEST, OLDEST)
}
