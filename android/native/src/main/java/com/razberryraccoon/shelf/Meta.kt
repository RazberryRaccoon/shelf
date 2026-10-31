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
 * front, title screen and a screenshot from libretro's thumbnails. Free, no account. Everything is saved on the device,
 * so it's looked up once per game: in Shelf/Media/<console>/<game's file name>/ on storage (box.png, back.png,
 * spine.png, cartridge.png, title.png, screenshot.png, video.mp4, manual.pdf and info.json, Shelf's notes on it), where
 * you can see it, back it up, and add your own files (recognised by their names, see detect). Without access to
 * storage, the same inside the app. Media from before 0.18 (files/meta) moves there the first time a game is read.
 */
class Meta(private val ctx: Context, private val games: Games) {
    /**
     * What's known: the matched name, text fields, media files by kind (box, spine, back, label, title, shot, video,
     * manual), and where each came from (`src`, by kind, "details" and "description" included).
     */
    class Info(val name: String?, val fields: Map<String, String>, val pics: Map<String, File>, val src: Map<String, String> = emptyMap())
    companion object {
        /** What libretro's thumbnails have, looked up on their own. */
        val KINDS = listOf("box" to "Box", "title" to "Title screen", "shot" to "Screenshot")
        /** The pictures a game's page shows, in order (each where it has one); the video plays when chosen. */
        fun pictures(sys: String) = listOf("box" to "Box", "back" to "Box back", "label" to "${Games.mediaName(sys)} art", "title" to "Title screen", "shot" to "Screenshot", "video" to "Video")
        private const val VERSION = 3   // 3: the whole Wikipedia article, for About's article page
        /** Sources Shelf uses on its own when it first looks a game up; anything else was chosen and is kept. */
        val AUTO = setOf("libretro", "Wikipedia")
        /** The source of a file you added yourself (in the folder, or chosen on the media page). */
        const val YOURS = "Your file"
        /** Each kind's file name in a game's folder. */
        val NAMES = mapOf("box" to "box", "back" to "back", "spine" to "spine", "label" to "cartridge", "title" to "title", "shot" to "screenshot", "video" to "video", "manual" to "manual")
        private val PICS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        private val VIDEOS = setOf("mp4", "webm", "mkv", "3gp", "mov")
        /**
         * What kind a file is, from its name: a video by its type, a PDF as the manual; a picture by a word in its name
         * (spine or side; back; cart, label, disc, card or UMD; title; screen, snap or shot; box, front or cover).
         */
        fun detect(f: File): String? {
            val n = f.nameWithoutExtension.lowercase(); val e = f.extension.lowercase()
            return when {
                e in VIDEOS -> "video"
                e == "pdf" -> "manual"
                e !in PICS -> null
                "spine" in n || "side" in n -> "spine"
                "back" in n -> "back"
                listOf("cart", "label", "disc", "card", "umd", "support").any { it in n } -> "label"
                "title" in n -> "title"
                listOf("screen", "snap", "shot").any { it in n } -> "shot"
                listOf("box", "front", "cover").any { it in n } -> "box"
                else -> null
            }
        }
        val DETAILS = listOf("releaseyear", "developer", "publisher", "genre", "players")
        private const val WIKI = "https://en.wikipedia.org/w/api.php?format=json&formatversion=2&action=query"
    }

    /**
     * The game's Wikipedia article as plain text, with its section headings as "== Gameplay ==" lines (its title, the
     * opening section, and the whole thing): a search for the title as a video game on its console, keeping only an
     * article whose title matches the game's closely and that's about a video game.
     */
    fun wikipedia(title: String, sys: String): Triple<String, String, String>? = try {
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
            val page = JSONObject(String(ArtIndex.download("$WIKI&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&titles=" + ArtIndex.enc(best)) ?: return null))
                .getJSONObject("query").getJSONArray("pages").getJSONObject(0)
            val all = page.optString("extract").trim().replace(Regex("\\n{2,}"), "\n\n")
            val intro = all.substringBefore("\n==").trim()
            if (intro.length > 80 && !intro.contains("may refer to")) Triple(page.optString("title", best!!), intro, all) else null
        }
    } catch (e: Exception) { null }

    private val oldDir = File(ctx.filesDir, "meta")   // where media was kept before 0.18
    /** Where all media lives: Shelf/Media on storage, or inside the app without access to storage. */
    val root: File get() = (if (android.os.Environment.isExternalStorageManager()) File(android.os.Environment.getExternalStorageDirectory(), "Shelf/Media") else File(ctx.filesDir, "media"))
        .also { if (!it.exists()) { it.mkdirs(); try { File(it, ".nomedia").createNewFile() } catch (e: Exception) { } } }   // kept out of the Gallery
    /** A game's own folder: its console, then its file's name. */
    fun gameDir(g: Game): File = File(File(root, safe(Games.NAMES[g.sys] ?: g.sys)), safe(File(g.path).nameWithoutExtension))
    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "_" }
    private val work = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val known = java.util.concurrent.ConcurrentHashMap<String, Info>()
    private val waiting = HashMap<String, MutableList<(Info?) -> Unit>>()

    private fun key(g: Game) = Integer.toHexString(g.path.hashCode())
    private fun file(g: Game) = File(gameDir(g), "info.json")

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
    /**
     * The closest names in libretro's list to `query` (the game's own name unless you typed one), and what went wrong
     * if nothing came back: the list couldn't be downloaded, the console has none (Android games), or no name is close
     * (it looks again, less strictly, first). It used to say "check the internet" for all three.
     */
    fun candidates(g: Game, query: String = g.title): Pair<List<String>, String?> {
        val ix = try { ArtIndex.load(g.sys, ctx.cacheDir) } catch (e: Exception) {
            return emptyList<String>() to (if (g.sys == "android") "libretro has no list of Android games. Media › its pictures can come from SteamGridDB and the other services."
                else "Couldn’t download libretro’s list of ${Games.NAMES[g.sys] ?: g.sys} games. Check the internet connection, and try again.")
        }
        val names = try { ix.search(query, 8).ifEmpty { ix.search(query, 12, .12) }.map { it.e.name } } catch (e: Exception) { emptyList() }
        return names to (if (names.isEmpty()) "Nothing in libretro’s list is close to “$query”. Try typing its name." else null)
    }
    fun loadCandidates(g: Game, query: String = g.title, then: (List<String>, String?) -> Unit) = work.execute { val (c, why) = candidates(g, query); ui.post { then(c, why) } }

    private fun lookUp(g: Game): Info {
        val ix = ArtIndex.load(g.sys, ctx.cacheDir)
        val name = games.match(g)
            ?: ix.byChecksum(File(g.path))?.name
            ?: ix.search(g.title, 1).firstOrNull()?.takeIf { it.score >= .6 }?.e?.name
        // what was chosen on the media page (anything not found automatically from libretro or Wikipedia) is kept
        val old = readRaw(g)
        val src = HashMap<String, String>(); val kept = HashMap<String, File>()
        old?.let { o -> o.src.forEach { (k, s) -> if (s !in AUTO) { src[k] = s; o.pics[k]?.let { f -> kept[k] = f } } } }
        val fields = HashMap(name?.let { ix.details(it) } ?: emptyMap())
        // details and a description chosen on the media page stay as they were; otherwise libretro's, and Wikipedia's
        if ("details" in src) old?.fields?.let { o -> DETAILS.forEach { k -> o[k]?.let { fields[k] = it } ?: fields.remove(k) } }
        else if (name != null && DETAILS.any { it in fields }) src["details"] = "libretro"
        val text = listOf("description", "descsource", "article", "wikititle")
        if ("description" in src) old?.fields?.let { o -> text.forEach { k -> o[k]?.let { fields[k] = it } ?: fields.remove(k) } }
        // libretro rarely has a description; Wikipedia's article on the game usually does (its opening section)
        else if ((fields["description"]?.length ?: 0) < 200) wikipedia(name?.let { ArtIndex.searchName(it) } ?: g.title, g.sys)?.let { (t, intro, all) ->
            fields["description"] = intro; fields["descsource"] = "Wikipedia"; fields["article"] = all; fields["wikititle"] = t; src["description"] = "Wikipedia" }
        val pics = HashMap<String, File>(kept)
        if (name != null) for ((kind, _) in KINDS) {
            if (kind in kept) continue
            val f = mediaFile(g, kind, "png")
            if (!f.exists() || f.length() == 0L) {
                val folder = mapOf("box" to "Named_Boxarts", "title" to "Named_Titles", "shot" to "Named_Snaps")[kind]!!
                val bytes = try { ArtIndex.download(ix.artUrl(folder, name)) } catch (e: Exception) { null }
                if (bytes != null) f.writeBytes(bytes) else f.delete()
            }
            if (f.exists()) { pics[kind] = f; src[kind] = "libretro" }
        }
        return Info(name, fields, pics, src).also { write(g, it) }
    }

    private fun write(g: Game, i: Info) {
        val d = gameDir(g).apply { mkdirs() }
        // files in the game's own folder by their name alone, so a restored backup finds them wherever it lands
        val j = JSONObject().put("v", VERSION).put("name", i.name ?: JSONObject.NULL).put("game", g.title).put("fields", JSONObject(i.fields as Map<*, *>))
            .put("pics", JSONObject().apply { i.pics.forEach { (k, f) -> put(k, if (f.parentFile == d) f.name else f.path) } })
            .put("src", JSONObject(i.src as Map<*, *>))
        file(g).writeText(j.toString(1))
    }

    /** Forgets a game's details or description (Remove it, on the media page). */
    fun clearText(g: Game, kind: String) {
        val i = cached(g) ?: return
        val keys = if (kind == "details") DETAILS else listOf("description", "article", "wikititle", "descsource")
        val n = Info(i.name, i.fields - keys.toSet(), i.pics, i.src - kind); known[g.path] = n; write(g, n)
    }

    /** What's known, without looking anything up or remembering it (safe off the main thread: Settings › Media counts). */
    fun peek(g: Game): Info? = known[g.path] ?: read(g)

    /** Looks a game up now, on the calling thread (off the main thread: finding media for every game). */
    fun lookUpNow(g: Game): Info? = try { lookUp(g).also { known[g.path] = it } } catch (e: Exception) { null }

    /** Runs on the main thread. */
    fun post(r: () -> Unit) { ui.post(r) }

    /** Where a game's media of this kind is kept on the device: its own folder, by the kind's plain name. */
    fun mediaFile(g: Game, kind: String, ext: String) = File(gameDir(g).apply { mkdirs() }, (NAMES[kind] ?: kind) + "." + ext)

    /**
     * Keeps a choice from the media page: a file of this kind (or none, to forget it), and/or text fields, and where
     * it came from. Call on the main thread; saved at once.
     */
    fun setMedia(g: Game, kind: String, file: File?, fields: Map<String, String>? = null, source: String?) {
        val i = cached(g) ?: Info(games.match(g), emptyMap(), emptyMap())
        val pics = HashMap(i.pics); val f = HashMap(i.fields); val src = HashMap(i.src)
        if (file != null) { pics[kind]?.takeIf { it != file && it.parentFile == gameDir(g) }?.delete(); pics[kind] = file }   // one file per kind
        else if (fields == null) pics.remove(kind)?.takeIf { it.parentFile == gameDir(g) }?.delete()
        if (fields != null) { if (kind == "description") listOf("description", "descsource", "article", "wikititle").forEach { f.remove(it) }; f.putAll(fields) }
        if (source != null) src[kind] = source else src.remove(kind)
        val n = Info(i.name, f, pics, src); known[g.path] = n; write(g, n)
    }

    /** Forgets what was found, so the next look-up starts over (after choosing which game it is). */
    fun forget(g: Game) {
        // a different game: what was found for the old one goes, but files you put in the folder yourself stay
        val i = readRaw(g)
        known.remove(g.path); file(g).delete()
        i?.pics?.forEach { (k, f) -> if (i.src[k] != YOURS && f.parentFile == gameDir(g)) f.delete() }
    }

    private fun read(g: Game): Info? = readRaw(g)?.takeIf { raw(g)?.optInt("v") ?: 0 >= VERSION }   // older: look again (pictures and choices are kept)
    private fun raw(g: Game): JSONObject? = try { migrate(g); JSONObject(file(g).readText()) } catch (e: Exception) { null }
    private fun readRaw(g: Game): Info? = try {
        val j = raw(g)
        val d = gameDir(g)
        val pics = HashMap<String, File>(); val src = HashMap<String, String>(); val fields = HashMap<String, String>()
        if (j != null) {
            val f = j.getJSONObject("fields"); val p = j.getJSONObject("pics"); val s = j.optJSONObject("src") ?: JSONObject()
            f.keys().forEach { fields[it] = f.getString(it) }
            p.keys().forEach { k -> val path = p.getString(k); val file = if (path.contains('/')) File(path) else File(d, path); if (file.exists()) pics[k] = file }
            s.keys().forEach { src[it] = s.getString(it) }
        }
        // files you've put in the folder yourself: each recognised by its name, and used if it's newer than what's there
        d.listFiles()?.forEach { file ->
            val k = detect(file) ?: return@forEach
            if (pics.values.contains(file)) return@forEach
            val had = pics[k]
            if (had == null || file.lastModified() > had.lastModified()) { pics[k] = file; src[k] = YOURS }
        }
        if (j == null && pics.isEmpty()) null
        else Info(j?.optString("name")?.takeIf { !j.isNull("name") && it.isNotEmpty() }, fields, pics, src)
    } catch (e: Exception) { null }

    /** Media kept inside the app before 0.18 moves to the game's own folder, by the new names. */
    private fun migrate(g: Game) {
        val old = File(oldDir, key(g) + ".json")
        if (!old.exists() || file(g).exists()) return
        try {
            val j = JSONObject(old.readText()); val p = j.getJSONObject("pics"); val moved = JSONObject()
            p.keys().forEach { k -> val f = File(p.getString(k)); if (f.exists()) {
                val to = mediaFile(g, k, f.extension.ifEmpty { "png" })
                if (f.renameTo(to) || (try { f.copyTo(to, true); f.delete(); true } catch (e: Exception) { false })) moved.put(k, to.name) } }
            j.put("pics", moved)
            gameDir(g).mkdirs(); file(g).writeText(j.toString(1)); old.delete()
        } catch (e: Exception) { }
    }

    /** Forgets everything remembered, so it's all read again from the folders (after restoring a backup). */
    fun reload() { known.clear() }

    /** A picture, shrunk to about `maxW` wide as it's read. */
    fun bitmap(f: File, maxW: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
        var s = 1; while (o.outWidth / (s * 2) >= maxW) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Exception) { null }
}
