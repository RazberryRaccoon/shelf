package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * A game's details and pictures, from libretro's database (the shared ArtIndex): the exact game by the ROM's checksum
 * where it can, otherwise the closest name; then its year, developer, publisher, genre and description, and its box
 * front, title screen and a screenshot from libretro's thumbnails. Free, no account. Everything is saved on the device
 * (files/meta), so it's looked up once per game.
 */
class Meta(private val ctx: Context, private val games: Games) {
    class Info(val name: String?, val fields: Map<String, String>, val pics: Map<String, File>)
    companion object {
        val KINDS = listOf("box" to "Box", "title" to "Title screen", "shot" to "Screenshot")
        private const val VERSION = 2
        private const val WIKI = "https://en.wikipedia.org/w/api.php?format=json&formatversion=2&action=query"
    }

    /**
     * The opening section of the game's Wikipedia article, as plain text: a search for the title as a video game on its
     * console, keeping only an article whose title matches the game's closely and that's about a video game.
     */
    private fun wikipedia(title: String, sys: String): String? = try {
        val clean = title.replace(Regex("\\s+"), " ").trim()
        val words = ArtIndex.norm(clean).split(" ").filter { it.isNotEmpty() }.toTypedArray()
        val q = "\"$clean\" video game ${Games.NAMES[sys] ?: ""}"
        val hits = JSONObject(String(ArtIndex.download("$WIKI&list=search&srlimit=5&srsearch=" + ArtIndex.enc(q)) ?: return null))
            .getJSONObject("query").getJSONArray("search")
        var best: String? = null; var bestScore = 0.0
        for (k in 0 until hits.length()) {
            val h = hits.getJSONObject(k); val t = h.getString("title")
            val tw = ArtIndex.norm(t.replace(Regex("\\([^)]*\\)"), " ")).split(" ").filter { it.isNotEmpty() }.toTypedArray()
            var s = ArtIndex.dice(words, tw)
            val gamey = t.contains("video game", true) || h.optString("snippet").contains("video game", true)
            if (!gamey) s -= .3
            if (s > bestScore) { bestScore = s; best = t }
        }
        if (best == null || bestScore < .6) null else {
            val page = JSONObject(String(ArtIndex.download("$WIKI&prop=extracts&exintro=1&explaintext=1&redirects=1&titles=" + ArtIndex.enc(best)) ?: return null))
                .getJSONObject("query").getJSONArray("pages").getJSONObject(0)
            page.optString("extract").trim().replace(Regex("\\n{2,}"), "\n\n").takeIf { it.length > 80 && !it.contains("may refer to") }
        }
    } catch (e: Exception) { null }

    private val dir = File(ctx.filesDir, "meta").apply { mkdirs() }
    private val work = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val known = HashMap<String, Info>()
    private val waiting = HashMap<String, MutableList<(Info?) -> Unit>>()

    private fun key(g: Game) = Integer.toHexString(g.path.hashCode())
    private fun file(g: Game) = File(dir, key(g) + ".json")

    /** What's already known about this game, without looking anything up. */
    fun cached(g: Game): Info? = known[g.path] ?: read(g)?.also { known[g.path] = it }

    /** Looks the game up (once; later calls answer from the saved copy) and calls back on the main thread. */
    fun get(g: Game, again: Boolean = false, then: (Info?) -> Unit) {
        if (!again) cached(g)?.let { return then(it) }
        val list = waiting.getOrPut(g.path) { ArrayList() }
        list.add(then)
        if (list.size > 1) return   // already looking
        work.execute {
            val info = try { lookUp(g) } catch (e: Exception) { null }
            ui.post { if (info != null) known[g.path] = info; waiting.remove(g.path)?.forEach { it(info) } }
        }
    }

    /** The closest names in libretro's list, for Game settings › Which game is this? (call off the main thread). */
    fun candidates(g: Game): List<String> = try { ArtIndex.load(g.sys, ctx.cacheDir).search(g.title, 8).map { it.e.name } } catch (e: Exception) { emptyList() }
    fun loadCandidates(g: Game, then: (List<String>) -> Unit) = work.execute { val c = candidates(g); ui.post { then(c) } }

    private fun lookUp(g: Game): Info {
        val ix = ArtIndex.load(g.sys, ctx.cacheDir)
        val name = games.match(g)
            ?: ix.byChecksum(File(g.path))?.name
            ?: ix.search(g.title, 1).firstOrNull()?.takeIf { it.score >= .6 }?.e?.name
        val fields = HashMap(name?.let { ix.details(it) } ?: emptyMap())
        // libretro rarely has a description; Wikipedia's article on the game usually does (its opening section)
        if ((fields["description"]?.length ?: 0) < 200) wikipedia(name?.let { ArtIndex.stripTags(it) } ?: g.title, g.sys)?.let { fields["description"] = it; fields["descsource"] = "Wikipedia" }
        val pics = HashMap<String, File>()
        if (name != null) for ((kind, _) in KINDS) {
            val f = File(dir, key(g) + "-" + kind + ".png")
            if (!f.exists() || f.length() == 0L) {
                val folder = mapOf("box" to "Named_Boxarts", "title" to "Named_Titles", "shot" to "Named_Snaps")[kind]!!
                val bytes = try { ArtIndex.download(ix.artUrl(folder, name)) } catch (e: Exception) { null }
                if (bytes != null) f.writeBytes(bytes) else f.delete()
            }
            if (f.exists()) pics[kind] = f
        }
        val j = JSONObject().put("v", VERSION).put("name", name ?: JSONObject.NULL).put("fields", JSONObject(fields as Map<*, *>))
            .put("pics", JSONObject().apply { pics.forEach { (k, f) -> put(k, f.path) } })
        file(g).writeText(j.toString())
        return Info(name, fields, pics)
    }

    /** Forgets what was found, so the next look-up starts over (after choosing which game it is). */
    fun forget(g: Game) {
        known.remove(g.path); file(g).delete()
        for ((kind, _) in KINDS) File(dir, key(g) + "-" + kind + ".png").delete()
    }

    private fun read(g: Game): Info? = try {
        val j = JSONObject(file(g).readText())
        if (j.optInt("v") < VERSION) return null   // looked up before Wikipedia descriptions: look again (pictures are kept)
        val f = j.getJSONObject("fields"); val p = j.getJSONObject("pics")
        Info(j.optString("name").takeIf { !j.isNull("name") && it.isNotEmpty() },
            f.keys().asSequence().associateWith { f.getString(it) },
            p.keys().asSequence().associateWith { File(p.getString(it)) }.filterValues { it.exists() })
    } catch (e: Exception) { null }

    /** A picture, shrunk to about `maxW` wide as it's read. */
    fun bitmap(f: File, maxW: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
        var s = 1; while (o.outWidth / (s * 2) >= maxW) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Exception) { null }
}
