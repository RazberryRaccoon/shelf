package com.razberryraccoon.shelf

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Menu music and ambient sounds (0.31). Each theme has a track and an ambient loop of its own (tools/music/compose.py
 * writes them, note by note, and synthesizes them: assets/sound), but the music is its own setting: the theme's track,
 * any other theme's, your own file (copied into Shelf, looping), or none. Ambient sounds sit under the music: Forest's
 * birds turn to crickets at night, and Stormy's thunder follows its lightning a moment later.
 *
 * Every loop is two players chained one after the other (Android's gapless hand-over), so rain and waves never stop
 * for a beat at the seam. Changes crossfade; starting a game fades everything out; coming back fades it in again.
 */
object Music {
    private lateinit var ctx: Context
    private lateinit var games: Games
    private val ui = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var music: Loop? = null; private var amb: Loop? = null
    private val leaving = ArrayList<Loop>()   // fading out, let go when silent
    private var active = false                // Shelf is on screen and not starting a game
    private var day = true
    private var focus: AudioFocusRequest? = null
    /** The tracks offered in Settings › Sound › Music, after "Matches the theme": one per theme. */
    val TRACKS = listOf("space" to "Drift", "city" to "After Hours", "forest" to "Canopy", "ocean" to "Tide", "aurora" to "Northern Lights", "desert" to "Dunes", "stormy" to "Rain on Glass")
    val ownFile get() = File(ctx.filesDir, "music/own")

    fun init(c: Context, g: Games) { ctx = c.applicationContext; games = g; ui.post(tick) }

    /** Shelf is showing (true) or has gone behind something (a game, an app, the screen off). */
    fun active(on: Boolean, fade: Float = .5f) { if (on == active) return; active = on; if (on) apply() else fadeAll(fade) }
    /** Day or night changed (Forest's ambient changes with it). */
    fun day(d: Boolean) { if (d != day) { day = d; apply() } }

    /** What should be playing, from the settings and the theme; crossfades to it. */
    fun apply() {
        if (!::games.isInitialized || !active) return
        val theme = Theme.ENVS[Theme.env].id
        val m = when (val k = games.music) { "off" -> null; "theme" -> "music-$theme"; "own" -> if (ownFile.exists()) "own" else "music-$theme"; else -> "music-$k" }
        val a = if (!games.ambient) null else if (theme == "forest") (if (day) "amb-forest-day" else "amb-forest-night") else "amb-$theme"
        music = swap(music, m, curve(games.musicVol / 100f))
        amb = swap(amb, a, curve(games.ambientVol / 100f) * .9f)
        if (music != null || amb != null) askFocus()
    }

    /** Volumes changed (Settings): the playing loops follow at once. */
    fun volumes() { music?.want = curve(games.musicVol / 100f); amb?.want = curve(games.ambientVol / 100f) * .9f }

    /** Stormy's lightning just flickered: thunder rolls in a moment later, if ambient sounds are on. */
    fun lightning() {
        if (!active || !games.ambient || Theme.ENVS[Theme.env].id != "stormy") return
        ui.postDelayed({
            if (!active) return@postDelayed
            try {
                val p = MediaPlayer(); p.setAudioAttributes(attrs); open(p, "thunder-${1 + (Math.random() * 3).toInt().coerceAtMost(2)}")
                val v = curve(games.ambientVol / 100f) * .8f; p.setVolume(v, v); p.prepare(); p.setOnCompletionListener { it.release() }; p.start()
            } catch (e: Exception) { }
        }, 1200L + (Math.random() * 1600).toLong())
    }

    /** Your own file (Settings › Sound › Your own music): copied into Shelf, so it keeps playing whatever happens to the original. */
    fun setOwn(uri: android.net.Uri): Boolean = try {
        ownFile.parentFile?.mkdirs()
        val tmp = File(ownFile.path + ".new")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        // it has to play: try it before keeping it
        MediaPlayer().apply { setDataSource(tmp.path); prepare(); release() }
        music?.let { if (it.key == "own") { leaving += it; it.want = 0f; music = null } }
        tmp.renameTo(ownFile).also { games.ownMusicName = name(uri) }
    } catch (e: Exception) { false }

    private fun name(uri: android.net.Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Your file"
    } catch (e: Exception) { "Your file" }

    // ---------- the loops ----------

    private fun curve(v: Float) = v.coerceIn(0f, 1f).let { it * it }   // loudness feels even across the slider

    private fun swap(cur: Loop?, key: String?, vol: Float): Loop? {
        if (cur != null && cur.key == key) { cur.want = vol; if (!cur.playing) cur.play(); return cur }
        cur?.let { it.want = 0f; it.fade = .6f; leaving += it }
        if (key == null) return null
        return try { Loop(key).also { it.want = vol; it.play() } } catch (e: Exception) { null }
    }

    private fun fadeAll(sec: Float) {
        for (l in listOfNotNull(music, amb)) { l.want = 0f; l.fade = sec; leaving += l }
        music = null; amb = null
    }

    private fun open(p: MediaPlayer, key: String) {
        if (key == "own") p.setDataSource(ownFile.path)
        else ctx.assets.openFd("sound/$key.ogg").use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
    }

    private fun askFocus() {
        if (focus != null) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { c -> if (c == AudioManager.AUDIOFOCUS_LOSS) { fadeAll(.3f); focus = null } }.build()
        if (am.requestAudioFocus(f) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = f
    }

    /** One looping sound: the playing player and the next one waiting, handed over without a gap at the end. */
    private class Loop(val key: String) {
        var cur: MediaPlayer = make(); var next: MediaPlayer = make()
        var vol = 0f; var want = 0f; var fade = 1.2f; var playing = false
        init { cur.setNextMediaPlayer(next) }
        fun make(): MediaPlayer = MediaPlayer().apply {
            setAudioAttributes(attrs); open(this, key); prepare(); setVolume(0f, 0f)
            setOnCompletionListener { done -> if (done === cur) handOver() }
        }
        private fun handOver() {
            val done = cur; cur = next
            try { next = make(); cur.setNextMediaPlayer(next); next.setVolume(vol, vol) } catch (e: Exception) { }
            done.release()
        }
        fun play() { if (!playing) { cur.start(); playing = true } }
        fun step(dt: Float) {
            vol += (want - vol) * minOf(1f, dt * 3f / fade); if (kotlin.math.abs(want - vol) < .003f) vol = want
            try { cur.setVolume(vol, vol); next.setVolume(vol, vol) } catch (e: Exception) { }
        }
        fun release() { try { cur.release() } catch (e: Exception) { }; try { next.release() } catch (e: Exception) { } }
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            val dt = .04f
            music?.step(dt); amb?.step(dt)
            val it = leaving.iterator()
            while (it.hasNext()) { val l = it.next(); l.step(dt); if (l.vol <= .001f) { l.release(); it.remove() } }
            if (music == null && amb == null && leaving.isEmpty() && focus != null) {
                ctx.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(focus!!); focus = null
            }
            ui.postDelayed(this, 40)
        }
    }
}
