package com.razberryraccoon.shelf

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Everything Shelf can find for a game, kind by kind, and where from (the media page and Settings › Media).
 *
 * First the game is matched to a standard name (libretro's database, No-Intro and Redump names: by checksum, or chosen
 * in Which game is this?), so every source is searched with the same name. Then each kind comes from the sources that
 * have it; the media page lists every source's choices, labelled, and Find everything missing takes the first good one
 * from each kind's sources in order.
 *
 *   box front      libretro, ScreenScraper, TheGamesDB, RetroAchievements, SteamGridDB, IGDB
 *   box spine      ScreenScraper
 *   box back       ScreenScraper, TheGamesDB
 *   cartridge art  ScreenScraper (the cartridge, disc or card itself)
 *   title screen   libretro, ScreenScraper, TheGamesDB, RetroAchievements
 *   screenshot     libretro, ScreenScraper, TheGamesDB, RetroAchievements, IGDB
 *   video          ScreenScraper
 *   details        libretro, ScreenScraper, IGDB, TheGamesDB (year, developer, publisher, genre, players)
 *   description    Wikipedia, ScreenScraper, IGDB, TheGamesDB
 *   manual         retrokit's manual library (archive.org, no account), ScreenScraper
 */
object Media {
    class Kind(val id: String, val name: String, val text: Boolean = false, val ext: String = "png")
    val KINDS = listOf(Kind("box", "Box front"), Kind("spine", "Box spine"), Kind("back", "Box back"), Kind("label", "Cartridge art"),
        Kind("title", "Title screen"), Kind("shot", "Screenshot"), Kind("video", "Video", ext = "mp4"),
        Kind("details", "Details", text = true), Kind("description", "Description", text = true), Kind("manual", "Manual", ext = "pdf"))
    fun kind(id: String) = KINDS.first { it.id == id }

    /** The kinds a game can have (an Android game has no cartridge art or manual). */
    fun kindsFor(sys: String) = KINDS.filter { sys != "android" || it.id != "label" && it.id != "manual" }

    /** A kind's name for this console: cartridge art is a disc's, a UMD's or a card's for those. */
    fun name(kind: String, sys: String) = if (kind == "label") "${Games.mediaName(sys)} art" else kind(kind).name

    // ---------- sources ----------

    /**
     * A source: what it has, what it needs, and how to set it up (shown in Settings › Media, step by step). Fields:
     * key, label, secret, optional.
     */
    class Source(val id: String, val name: String, val kinds: Set<String>, val needs: String, val steps: List<String>,
                 val fields: List<Field>, val android: Boolean = true)
    class Field(val key: String, val label: String, val secret: Boolean = false, val optional: Boolean = false)

    val SOURCES = listOf(
        Source("libretro", "libretro", setOf("box", "title", "shot", "details"), "No account needed",
            listOf("Free and always on: libretro's database and thumbnails, the same ones RetroArch uses."), emptyList(), android = false),
        Source("wikipedia", "Wikipedia", setOf("description"), "No account needed",
            listOf("Free and always on: the game's Wikipedia article, for About."), emptyList()),
        Source("retrokit", "retrokit manuals", setOf("manual"), "No account needed",
            listOf("Free and always on: about 20,000 game manuals catalogued by hand by the retrokit project and kept on archive.org."), emptyList(), android = false),
        Source("ss", "ScreenScraper", setOf("box", "spine", "back", "label", "title", "shot", "video", "details", "description", "manual"),
            "A free ScreenScraper account",
            listOf("Make a free account at screenscraper.fr (Créer un compte, top right).",
                "Enter your username and password here.",
                "Shelf also needs its own developer credentials from ScreenScraper, requested once on their forum. Until they arrive this says “Waiting for Shelf’s credentials”; if you have developer credentials of your own, you can enter them below."),
            listOf(Field("user", "Your ScreenScraper username"), Field("password", "Your ScreenScraper password", secret = true),
                Field("devid", "Developer ID (optional)", optional = true), Field("devpassword", "Developer password (optional)", secret = true, optional = true))),
        Source("tgdb", "TheGamesDB", setOf("box", "back", "title", "shot", "details", "description"), "A free API key",
            listOf("Make a free account at thegamesdb.net and sign in.", "Open the forum's API Key Request section and ask for a public key (a sentence saying it's for a personal launcher is enough).",
                "When it arrives (by private message, usually within a few days), enter it here."),
            listOf(Field("key", "TheGamesDB API key", secret = true))),
        Source("ra", "RetroAchievements", setOf("box", "title", "shot"), "Your account's web API key",
            listOf("Make a free account at retroachievements.org.", "Open Settings on the site and find Keys: copy the Web API key.", "Enter your username and the key here."),
            listOf(Field("user", "RetroAchievements username"), Field("key", "Web API key", secret = true)), android = false),
        Source("sgdb", "SteamGridDB", setOf("box"), "A free API key",
            listOf("Sign in at steamgriddb.com (with a Steam account).", "Open Preferences › API and generate a key.", "Enter the key here."),
            listOf(Field("key", "SteamGridDB API key", secret = true))),
        Source("igdb", "IGDB", setOf("box", "shot", "details", "description"), "A free Twitch developer app",
            listOf("Sign in at dev.twitch.tv (a Twitch account; turn on two-factor login if asked).", "Open Your Console › Applications › Register Your Application: any name, OAuth Redirect URL http://localhost, category Application Integration, client type Confidential.",
                "Open the app, copy its Client ID, press New Secret and copy the secret.", "Enter both here."),
            listOf(Field("id", "Twitch client ID"), Field("secret", "Twitch client secret", secret = true))))
    fun source(id: String) = SOURCES.first { it.id == id }
    val ORDER = mapOf(
        "box" to listOf("libretro", "ss", "tgdb", "igdb", "ra", "sgdb"), "spine" to listOf("ss"), "back" to listOf("ss", "tgdb"),
        "label" to listOf("ss"), "title" to listOf("libretro", "ss", "tgdb", "ra"), "shot" to listOf("libretro", "ss", "igdb", "tgdb", "ra"),
        "video" to listOf("ss"), "details" to listOf("libretro", "ss", "igdb", "tgdb"), "description" to listOf("wikipedia", "ss", "igdb", "tgdb"),
        "manual" to listOf("retrokit", "ss"))

    /**
     * Shelf's own ScreenScraper developer credentials, once they're granted (see docs/SCREENSCRAPER.md). Empty until then:
     * ScreenScraper then works only with developer credentials entered in Settings.
     */
    private const val SS_DEV_ID = ""
    private const val SS_DEV_PASSWORD = ""
    val ssWaiting get() = SS_DEV_ID.isEmpty()

    /** The keys ArtSources expects, with Shelf's own ScreenScraper credentials filled in. */
    fun keys(saved: JSONObject): JSONObject = JSONObject(saved.toString()).apply {
        val ss = optJSONObject("ss") ?: JSONObject().also { put("ss", it) }
        if (ss.optString("devid").isEmpty() && SS_DEV_ID.isNotEmpty()) { ss.put("devid", SS_DEV_ID); ss.put("devpassword", SS_DEV_PASSWORD) }
    }

    /** Whether a source can be used: free ones always; the others once their fields are filled in. */
    fun connected(id: String, saved: JSONObject): Boolean = when (id) {
        "libretro", "wikipedia", "retrokit" -> true
        "ss" -> keys(saved).optJSONObject("ss")?.let { it.optString("devid").isNotEmpty() && it.optString("user").isNotEmpty() } == true
        else -> ArtSources.connected(id, keys(saved))
    }
    /** A source's state in a few words, for Settings. */
    fun status(id: String, saved: JSONObject): String = when {
        id in setOf("libretro", "wikipedia", "retrokit") -> "Always on"
        connected(id, saved) -> "Connected"
        id == "ss" && saved.optJSONObject("ss")?.optString("user")?.isNotEmpty() == true && ssWaiting -> "Waiting for Shelf’s credentials"
        else -> "Not set up"
    }

    // ---------- choices ----------

    /** One thing to choose: a file to download (a picture, video or manual), or text fields; from a named source. */
    class Choice(val source: String, val game: String, val urls: List<String> = emptyList(), val thumb: String? = null,
                 val fields: Map<String, String>? = null, val note: String? = null)

    /**
     * Every source's choices for one kind (each source tried in turn; one failing doesn't stop the rest). `name` is the
     * game's standard name (or its title), `rom` its file. Call off the main thread.
     */
    fun choices(kind: String, sys: String, name: String, rom: File, saved: JSONObject, cache: File, meta: Meta, only: String? = null): List<Choice> {
        val keys = keys(saved); val out = ArrayList<Choice>()
        for (id in ORDER[kind] ?: emptyList()) {
            if (only != null && id != only) continue
            val s = source(id)
            if (sys == "android" && !s.android) continue
            if (!connected(id, saved) || id !in setOf("wikipedia", "retrokit") && !ArtSources.covers(id, sys)) continue
            try { out += from(id, kind, sys, name, rom, keys, cache, meta) } catch (e: Exception) { }
        }
        return out
    }

    private fun from(id: String, kind: String, sys: String, name: String, rom: File, keys: JSONObject, cache: File, meta: Meta): List<Choice> = when {
        kind == "details" -> listOfNotNull(details(id, sys, name, rom, keys, cache))
        kind == "description" -> listOfNotNull(description(id, sys, name, rom, keys, meta))
        kind == "manual" && id == "retrokit" -> retrokit(sys, name, cache)
        id == "wikipedia" || id == "retrokit" -> emptyList()
        else -> ArtSources.ALL[id]!!.find(sys, name, kind, keys, cache).map { Choice(it.source, it.game, listOf(it.url), it.thumb) }
    }

    // details and descriptions: each source's own, as text to choose from

    private fun details(id: String, sys: String, name: String, rom: File, keys: JSONObject, cache: File): Choice? {
        val f = HashMap<String, String>()
        var game = name
        when (id) {
            "libretro" -> ArtIndex.load(sys, cache).details(name).forEach { (k, v) -> if (k in Meta.DETAILS) f[k] = v }
            "ss" -> ssGame(sys, name, rom, keys)?.let { g ->
                game = ssText(g.optJSONArray("noms")) ?: name
                ssText(g.optJSONArray("dates"))?.take(4)?.let { f["releaseyear"] = it }
                g.optJSONObject("developpeur")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { f["developer"] = it }
                g.optJSONObject("editeur")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { f["publisher"] = it }
                g.optJSONArray("genres")?.optJSONObject(0)?.optJSONArray("noms")?.let { ssText(it, "langue", "en") }?.let { f["genre"] = it }
                g.optJSONObject("joueurs")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { f["players"] = it }
            }
            "igdb" -> igdbGame(sys, name, keys, "name,first_release_date,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher")?.let { g ->
                game = g.optString("name", name)
                if (g.has("first_release_date")) f["releaseyear"] = java.util.Calendar.getInstance().apply { timeInMillis = g.getLong("first_release_date") * 1000 }.get(java.util.Calendar.YEAR).toString()
                val cos = g.optJSONArray("involved_companies")
                fun company(role: String) = cos?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optBoolean(role) }?.optJSONObject("company")?.optString("name") }
                company("developer")?.let { f["developer"] = it }; company("publisher")?.let { f["publisher"] = it }
                g.optJSONArray("genres")?.optJSONObject(0)?.optString("name")?.let { f["genre"] = it }
            }
            "tgdb" -> tgdbGame(sys, name, keys)?.let { g ->
                game = g.optString("game_title", name)
                g.optString("release_date").take(4).takeIf { it.length == 4 }?.let { f["releaseyear"] = it }
                g.optInt("players").takeIf { it > 0 }?.let { f["players"] = it.toString() }
            }
        }
        if (f.isEmpty()) return null
        return Choice(source(id).name, game, fields = f, note = listOfNotNull(f["releaseyear"], f["developer"], f["publisher"], f["genre"]).joinToString(" · "))
    }

    private fun description(id: String, sys: String, name: String, rom: File, keys: JSONObject, meta: Meta): Choice? {
        val clean = ArtIndex.stripTags(name).replace(Regex("\\s+"), " ").trim()
        return when (id) {
            "wikipedia" -> meta.wikipedia(clean, sys)?.let { (t, intro, all) ->
                Choice("Wikipedia", t, fields = mapOf("description" to intro, "article" to all, "wikititle" to t, "descsource" to "Wikipedia"), note = intro) }
            "ss" -> ssGame(sys, name, rom, keys)?.let { g -> ssText(g.optJSONArray("synopsis"), "langue", "en")?.let { s ->
                Choice("ScreenScraper", ssText(g.optJSONArray("noms")) ?: clean, fields = mapOf("description" to s, "descsource" to "ScreenScraper"), note = s) } }
            "igdb" -> igdbGame(sys, name, keys, "name,summary,storyline")?.let { g -> g.optString("summary").takeIf { it.isNotEmpty() }?.let { s ->
                val all = listOfNotNull(s, g.optString("storyline").takeIf { it.isNotEmpty() }?.let { "== Story ==\n$it" }).joinToString("\n\n")
                Choice("IGDB", g.optString("name", clean), fields = mapOf("description" to s, "article" to all, "descsource" to "IGDB"), note = s) } }
            "tgdb" -> tgdbGame(sys, name, keys)?.let { g -> g.optString("overview").takeIf { it.isNotEmpty() }?.let { s ->
                Choice("TheGamesDB", g.optString("game_title", clean), fields = mapOf("description" to s, "descsource" to "TheGamesDB"), note = s) } }
            else -> null
        }
    }

    private fun ssGame(sys: String, name: String, rom: File, keys: JSONObject): JSONObject? {
        try { ArtSources.ssGame(sys, rom, keys)?.let { return it } } catch (e: Exception) { }
        val url = "https://api.screenscraper.fr/api2/jeuRecherche.php?" + ArtSources.ssAuth(keys) + "&systemeid=" + SS_SYSTEMS[sys] + "&recherche=" + ArtIndex.enc(ArtIndex.stripTags(name).trim())
        return JSONObject(ArtSources.get(url, null)).getJSONObject("response").optJSONArray("jeux")?.optJSONObject(0)
    }
    private val SS_SYSTEMS = mapOf("nes" to 3, "snes" to 4, "n64" to 14, "genesis" to 1, "gb" to 9, "gbc" to 10, "gba" to 12, "ps1" to 57, "ds" to 15, "psp" to 61, "android" to 63)
    /** ScreenScraper's text in English (or for the US, or worldwide), else the first. */
    private fun ssText(a: JSONArray?, field: String = "region", want: String = "us"): String? {
        if (a == null || a.length() == 0) return null
        val all = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        return (all.firstOrNull { it.optString(field) == want } ?: all.firstOrNull { it.optString(field) in setOf("wor", "en", "ss") } ?: all.first()).optString("text").takeIf { it.isNotEmpty() }
    }

    private fun igdbGame(sys: String, name: String, keys: JSONObject, fields: String): JSONObject? {
        val k = keys.getJSONObject("igdb")
        val t = JSONObject(ArtSources.request("POST", "https://id.twitch.tv/oauth2/token?client_id=" + ArtSources.q(k.optString("id")) + "&client_secret=" + ArtSources.q(k.optString("secret")) + "&grant_type=client_credentials", null, ""))
        val h = mapOf("Client-ID" to k.optString("id"), "Authorization" to "Bearer " + t.getString("access_token"))
        val q = "search \"" + ArtIndex.stripTags(name).trim().replace("\"", "") + "\"; fields $fields; where platforms = (" + IGDB[sys] + "); limit 1;"
        return JSONArray(ArtSources.request("POST", "https://api.igdb.com/v4/games", h, q)).optJSONObject(0)
    }
    private val IGDB = mapOf("nes" to 18, "snes" to 19, "n64" to 4, "genesis" to 29, "gb" to 33, "gbc" to 22, "gba" to 24, "ps1" to 7, "ds" to 20, "psp" to 38, "android" to 34)

    private fun tgdbGame(sys: String, name: String, keys: JSONObject): JSONObject? {
        val url = "https://api.thegamesdb.net/v1.1/Games/ByGameName?apikey=" + ArtSources.q(keys.getJSONObject("tgdb").optString("key")) + "&name=" + ArtSources.q(ArtIndex.stripTags(name).trim()) +
            "&fields=players,overview&filter%5Bplatform%5D=" + TGDB[sys]
        return JSONObject(ArtSources.get(url, null)).optJSONObject("data")?.optJSONArray("games")?.optJSONObject(0)
    }
    private val TGDB = mapOf("nes" to 7, "snes" to 6, "n64" to 3, "genesis" to 18, "gb" to 4, "gbc" to 41, "gba" to 5, "ps1" to 10, "ds" to 8, "psp" to 13, "android" to 4916)

    // ---------- manuals: retrokit's library ----------

    /** retrokit's name for each console (its data files and archive folders). */
    private val RETROKIT = mapOf("nes" to "nes", "snes" to "snes", "n64" to "n64", "genesis" to "megadrive", "gb" to "gb", "gbc" to "gbc",
        "gba" to "gba", "ps1" to "psx", "ds" to "nds", "psp" to "psp")

    /**
     * Manuals from retrokit's hand-catalogued list (github.com/obrie/retrokit, data/<system>.json, refreshed weekly):
     * games by their parent name (No-Intro / Redump, without region tags), each with manuals by language. Each comes
     * from retrokit's copy on archive.org (one PDF out of the console's archive, no account), with the original
     * website as a fallback. English first.
     */
    fun retrokit(sys: String, name: String, cache: File): List<Choice> {
        val rs = RETROKIT[sys] ?: return emptyList()
        val f = File(cache, "retrokit-$rs.json")
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600 * 1000)
            try { ArtIndex.download("https://raw.githubusercontent.com/obrie/retrokit/main/data/$rs.json")?.let { f.writeBytes(it) } } catch (e: Exception) { if (!f.exists()) throw e }
        val all = JSONObject(f.readText())
        val want = ArtIndex.norm(ArtIndex.stripTags(name)).split(" ").filter { it.isNotEmpty() }.toTypedArray()
        var best: String? = null; var score = 0.0
        for (k in all.keys()) {
            if (!all.getJSONObject(k).has("manuals")) continue
            val s = ArtIndex.dice(want, ArtIndex.norm(k).split(" ").filter { it.isNotEmpty() }.toTypedArray())
            if (s > score) { score = s; best = k }
        }
        val title = best?.takeIf { score >= .8 } ?: return emptyList()
        val list = all.getJSONObject(title).getJSONArray("manuals")
        val out = ArrayList<Choice>()
        for (i in 0 until list.length()) {
            val m = list.getJSONObject(i)
            val langs = m.optJSONArray("languages")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val archive = "https://archive.org/download/retrokit-manuals/$rs/$rs-compressed.zip/" + ArtIndex.enc("$title (${langs.joinToString(",")}).pdf").replace("+", "%20")
            val urls = listOfNotNull(archive, m.optString("url").takeIf { it.isNotEmpty() }?.replace(" ", "%20"))
            out += Choice("retrokit (archive.org)", title, urls, note = langs.joinToString(", ") { LANGS[it] ?: it })
        }
        return out.sortedBy { c -> if (c.note?.startsWith("English") == true) 0 else 1 }
    }
    private val LANGS = mapOf("en" to "English", "en-gb" to "English (UK)", "ja" to "Japanese", "fr" to "French", "de" to "German", "es" to "Spanish", "it" to "Italian", "nl" to "Dutch", "pt" to "Portuguese", "sv" to "Swedish", "ko" to "Korean", "zh" to "Chinese")

    // ---------- keeping a choice ----------

    /**
     * Downloads a choice into the game's own file for its kind (the first of its addresses that works), or keeps its
     * text; then records it with its source. Call off the main thread; `then` runs on the main thread with whether it
     * worked.
     */
    fun keep(g: Game, kind: String, c: Choice, meta: Meta): Boolean {
        val k = kind(kind)
        if (k.text) { val f = c.fields ?: return false; meta.post { meta.setMedia(g, kind, null, f, c.source) }; return true }
        val dest = meta.mediaFile(g, kind, k.ext)
        for (u in c.urls) {
            val tmp = File(dest.path + ".part")
            try {
                fetch(u, tmp)
                if (tmp.length() < 512 || !looksRight(kind, tmp)) { tmp.delete(); continue }
                dest.delete(); tmp.renameTo(dest)
                meta.post { meta.setMedia(g, kind, dest, null, c.source) }
                return true
            } catch (e: Exception) { tmp.delete() }
        }
        return false
    }

    /** A manual is a PDF and a video an MP4 (some sites answer a missing file with a web page). */
    private fun looksRight(kind: String, f: File): Boolean {
        val head = ByteArray(12); f.inputStream().use { it.read(head) }
        return when (kind) {
            "manual" -> String(head, 0, 4) == "%PDF"
            "video" -> String(head, 4, 4) == "ftyp"
            else -> true
        }
    }

    private fun fetch(url: String, to: File) {
        var u = url
        repeat(5) {   // follows redirects, across http and https too (archive.org sends files on to its data servers)
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 60000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Shelf (Android game launcher)")
            try {
                val code = c.responseCode
                if (code in 300..399) { u = URL(URL(u), c.getHeaderField("Location")).toString(); return@repeat }
                if (code >= 400) throw java.io.IOException("HTTP $code")
                c.inputStream.use { i -> to.outputStream().use { o -> i.copyTo(o) } }
                return
            } finally { c.disconnect() }
        }
        throw java.io.IOException("Too many redirects")
    }

    /** Whether a game has this kind yet. */
    fun has(i: Meta.Info?, kind: String): Boolean = when (kind) {
        "details" -> i?.fields?.let { f -> f["releaseyear"] != null || f["developer"] != null || f["genre"] != null } == true
        "description" -> i?.fields?.get("description") != null
        else -> i?.pics?.get(kind)?.exists() == true
    }

    /**
     * Finds every missing kind for one game: for each, the first source in order whose first choice downloads. Returns
     * the kinds found. Call off the main thread; `progress` gets each kind's name as it's tried.
     */
    fun findMissing(g: Game, name: String, info: Meta.Info?, saved: JSONObject, cache: File, meta: Meta, only: Set<String>? = null, progress: (String) -> Unit = {}): List<String> {
        val found = ArrayList<String>()
        for (k in kindsFor(g.sys)) {
            if (has(info, k.id) || only != null && k.id !in only) continue
            progress(name(k.id, g.sys))
            for (id in ORDER[k.id] ?: emptyList()) {
                val cs = try { choices(k.id, g.sys, name, File(g.path), saved, cache, meta, only = id) } catch (e: Exception) { emptyList() }
                if (cs.take(3).any { keep(g, k.id, it, meta) }) { found += k.id; break }
            }
        }
        return found
    }
}

/**
 * Finding missing media for every game (Settings › Media), one game after another on its own thread, so it carries on
 * while you do other things. `status` says how far it is; `onChange` (on the main thread) hears about each step.
 */
object MediaJob {
    @Volatile var running = false; private set
    @Volatile private var stop = false
    var status = ""; private set
    var found = 0; private set
    var onChange: (() -> Unit)? = null
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    fun start(list: List<Game>, only: Set<String>?, meta: Meta, saved: JSONObject, cache: File) {
        if (running) return
        running = true; stop = false; found = 0; status = "Starting…"
        Thread {
            list.forEachIndexed { n, g ->
                if (stop) return@forEachIndexed
                val info = meta.peek(g) ?: meta.lookUpNow(g)
                val name = info?.name ?: g.title
                val kinds = Media.kindsFor(g.sys).filter { (only == null || it.id in only) && !Media.has(info, it.id) }
                if (kinds.isEmpty()) return@forEachIndexed
                found += Media.findMissing(g, name, info, saved, cache, meta, only) { what ->
                    status = "${n + 1} of ${list.size} · ${ArtIndex.stripTags(name).trim()}: ${what.lowercase()}"; ui.post { onChange?.invoke() } }.size
            }
            running = false; status = if (stop) "Stopped" else "Done"
            ui.post { onChange?.invoke() }
        }.apply { isDaemon = true; name = "Shelf media" }.start()
    }
    fun cancel() { stop = true; status = "Stopping after this one…" }
}


/**
 * Backups of all media and Shelf's notes on each game (Settings › Media): the whole media folder in one zip, and back.
 * Each game's info.json names its files relative to its own folder, so a restored backup works wherever it lands, on
 * this handheld or another, as long as the games' files have the same names.
 */
object Backup {
    /** Writes the media folder into a zip; returns how many files went in. Off the main thread. */
    fun write(root: File, out: java.io.OutputStream, progress: (Int) -> Unit = {}): Int {
        var n = 0
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(out)).use { z ->
            z.setComment("Shelf media backup")
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(java.util.zip.ZipEntry(f.relativeTo(root).invariantSeparatorsPath).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry(); if (++n % 25 == 0) progress(n)
            }
        }
        return n
    }

    /** Puts a backup's files back in the media folder (replacing any with the same names); returns how many. */
    fun read(input: java.io.InputStream, root: File, progress: (Int) -> Unit = {}): Int {
        var n = 0
        val base = root.canonicalFile
        java.util.zip.ZipInputStream(java.io.BufferedInputStream(input)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val to = File(base, e.name).canonicalFile
                if (!to.path.startsWith(base.path + File.separator)) continue   // nothing outside the media folder, whatever the zip says
                if (e.isDirectory) { to.mkdirs(); continue }
                to.parentFile?.mkdirs()
                to.outputStream().use { z.copyTo(it) }
                if (e.time > 0) to.setLastModified(e.time)
                if (++n % 25 == 0) progress(n)
            }
        }
        return n
    }

    /** A file you chose (on the media page) copied into the game's folder as this kind; it's yours from then on. */
    fun adopt(ctx: android.content.Context, uri: android.net.Uri, g: Game, kind: String, meta: Meta): Boolean {
        val type = ctx.contentResolver.getType(uri) ?: ""
        val ext = when { kind == "manual" -> "pdf"; kind == "video" -> when { "matroska" in type -> "mkv"; "webm" in type -> "webm"; else -> "mp4" }; type.endsWith("jpeg") -> "jpg"; else -> type.substringAfter("image/", "png").ifEmpty { "png" } }
        val dest = meta.mediaFile(g, kind, ext)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { i.copyTo(it) } } ?: return false
            meta.post { meta.setMedia(g, kind, dest, null, Meta.YOURS) }; true
        } catch (e: Exception) { false }
    }
}
