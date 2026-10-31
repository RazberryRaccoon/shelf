package com.razberryraccoon.shelf

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Shelf's sounds: short, quiet and soft, made on the device from a few sine tones (no recordings), so they're ours and
 * tiny. A tick for moving, a bump at the end of a row, two rising notes for opening, two falling for closing, a bell
 * for choosing. Settings › Sounds turns them off.
 *
 * Sound packs (Settings › System › Sound pack): the same notes in four voices. Soft (the first: a sine with a quiet
 * octave), Glass (bell-like, with the inharmonic partials of a struck glass, ringing a little longer), Wood (an octave
 * lower, dry and woody, like a marimba bar), and Bubble (each note glides up into its pitch, a soft "bloop").
 */
object Sounds {
    const val MOVE = 0; const val BUMP = 1; const val OPEN = 2; const val CLOSE = 3; const val PICK = 4; const val TURN = 5
    const val CLICK = 6; const val CHIME = 7; const val POWER_OFF = 8; const val SWITCH = 9; const val CLUNK = 10
    private var pool: SoundPool? = null
    private val ids = IntArray(11)
    private var lastMove = 0L
    var enabled = true
    /** The sound packs, by name, in the order Settings offers them. */
    val PACKS = listOf("Soft", "Glass", "Wood", "Bubble")
    var pack = 0; private set

    fun init(ctx: Context, on: Boolean, pack: Int = 0) {
        enabled = on
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        pool = p
        load(ctx, pack)
    }

    /** Changes the pack: its sounds are made (once; kept in the cache) and loaded in place of the last ones. */
    fun setPack(ctx: Context, k: Int) { if (k != pack) load(ctx, k) }

    private fun load(ctx: Context, k0: Int) {
        val p = pool ?: return
        val k = k0.coerceIn(0, PACKS.size - 1); pack = k
        val dir = File(ctx.cacheDir, "sounds").apply { mkdirs() }
        // each sound: notes as (frequency, start, length) in seconds, and how loud (0..1)
        val recipes = listOf(
            listOf(floatArrayOf(1568f, 0f, .045f)) to .10f,                                  // move: a light tick (G6)
            listOf(floatArrayOf(392f, 0f, .07f)) to .12f,                                     // bump: a low, soft knock (G4)
            listOf(floatArrayOf(784f, 0f, .09f), floatArrayOf(1175f, .07f, .16f)) to .12f,    // open: G5 then D6
            listOf(floatArrayOf(1175f, 0f, .09f), floatArrayOf(784f, .07f, .16f)) to .11f,    // close: D6 then G5
            listOf(floatArrayOf(1047f, 0f, .22f), floatArrayOf(1568f, 0f, .22f)) to .10f,     // pick: C6 and G6 together, a small bell
            listOf(floatArrayOf(660f, 0f, .12f)) to .07f,                                     // turn: a breath of a tone
            listOf(floatArrayOf(262f, 0f, .05f), floatArrayOf(2093f, .012f, .025f)) to .13f,  // click: a game seating, a low knock with a tiny tick
            listOf(floatArrayOf(1319f, 0f, .32f), floatArrayOf(1568f, .09f, .32f), floatArrayOf(2093f, .18f, .5f)) to .07f,   // chime: power on, E6 G6 C7, soft
            listOf(floatArrayOf(1568f, 0f, .14f), floatArrayOf(1047f, .08f, .24f)) to .06f,   // power off: G6 falling to C6
            listOf(floatArrayOf(1760f, 0f, .02f), floatArrayOf(2637f, .03f, .025f)) to .07f,  // switch: a power switch sliding over, two tiny ticks
            listOf(floatArrayOf(147f, 0f, .09f), floatArrayOf(294f, 0f, .05f), floatArrayOf(1568f, .006f, .02f)) to .14f)   // clunk: the NES's cartridge pressed down, deep and soft
        recipes.forEachIndexed { i, (notes, gain) ->
            val f = File(dir, if (k == 0) "s$i-v2.wav" else "s$i-${PACKS[k].lowercase()}-v1.wav")
            if (!f.exists()) writeWav(f, synth(notes, gain, k))
            val old = ids[i]
            ids[i] = p.load(f.path, 1)
            if (old != 0) p.unload(old)
        }
    }

    fun play(k: Int) {
        if (!enabled) return
        val p = pool ?: return
        if (k == MOVE) { val now = System.currentTimeMillis(); if (now - lastMove < 45) return; lastMove = now }   // a held direction doesn't buzz
        p.play(ids[k], 1f, 1f, 0, 0, 1f)
    }

    private const val RATE = 44100

    /**
     * Each note in the pack's voice, with a 4 ms fade in and a smooth decay. Soft: a sine and a quiet octave. Glass: a
     * sine with a struck glass's partials (2.76× and 5.4×, fading faster than the note), ringing longer. Wood: an octave
     * lower, with a marimba bar's 4× and 9.8× partials that die almost at once, and a short decay. Bubble: the pitch glides
     * up into the note over its first few hundredths of a second.
     */
    private fun synth(notes: List<FloatArray>, gain: Float, pack: Int = 0): ShortArray {
        val ring = when (pack) { 1 -> 1.6f; 2 -> .7f; else -> 1f }   // how long each note lasts, against Soft
        val len = notes.maxOf { it[1] + it[2] * ring } + .02f
        val out = FloatArray((len * RATE).toInt())
        for (n in notes) {
            val f = if (pack == 2) n[0] / 2 else n[0]; val start = (n[1] * RATE).toInt(); val d = n[2] * ring; val count = (d * RATE).toInt()
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toFloat() / RATE
                val w = 2 * PI * f
                val s = when (pack) {
                    1 -> { val env = min(1f, t / .002f) * exp(-t * 3.2f / d)
                        env * (sin(w * t).toFloat() + .4f * sin(w * 2.76 * t).toFloat() * exp(-t * 3f / d) + .2f * sin(w * 5.4 * t).toFloat() * exp(-t * 6f / d)) * .85f }
                    2 -> { val env = min(1f, t / .002f) * exp(-t * 8f / d)
                        env * (sin(w * t).toFloat() + .55f * sin(w * 4 * t).toFloat() * exp(-t * 14f / d) + .25f * sin(w * 9.8 * t).toFloat() * exp(-t * 25f / d)) * 1.1f }
                    3 -> { phase += w * (1 - .45 * kotlin.math.exp(-t * 38.0)) / RATE; val env = min(1f, t / .004f) * exp(-t * 6f / d)
                        env * sin(phase).toFloat() }
                    else -> { val env = min(1f, t / .004f) * exp(-t * 5f / d)
                        env * (sin(w * t).toFloat() + .18f * sin(2 * w * t).toFloat()) }
                }
                if (start + i < out.size) out[start + i] += s
            }
        }
        return ShortArray(out.size) { (out[it] * gain * 32767 / 1.18f).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val b = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach { b.putShort(it) }
        FileOutputStream(f).use { it.write(b.array()) }
    }
}
