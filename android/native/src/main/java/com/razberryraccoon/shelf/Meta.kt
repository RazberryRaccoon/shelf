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
    companion object { val KINDS = listOf("box" to "Box", "title" to "Title screen", "shot" to "Screenshot") }

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
        val fields = name?.let { ix.details(it) } ?: emptyMap()
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
        val j = JSONObject().put("name", name ?: JSONObject.NULL).put("fields", JSONObject(fields as Map<*, *>))
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
