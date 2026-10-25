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
 */
object Sounds {
    const val MOVE = 0; const val BUMP = 1; const val OPEN = 2; const val CLOSE = 3; const val PICK = 4; const val TURN = 5
    const val CLICK = 6; const val CHIME = 7; const val POWER_OFF = 8
    private var pool: SoundPool? = null
    private val ids = IntArray(9)
    private var lastMove = 0L
    var enabled = true

    fun init(ctx: Context, on: Boolean) {
        enabled = on
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
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
            listOf(floatArrayOf(1568f, 0f, .14f), floatArrayOf(1047f, .08f, .24f)) to .06f)   // power off: G6 falling to C6
        recipes.forEachIndexed { k, (notes, gain) ->
            val f = File(dir, "s$k-v2.wav")
            if (!f.exists()) writeWav(f, synth(notes, gain))
            ids[k] = p.load(f.path, 1)
        }
        pool = p
    }

    fun play(k: Int) {
        if (!enabled) return
        val p = pool ?: return
        if (k == MOVE) { val now = System.currentTimeMillis(); if (now - lastMove < 45) return; lastMove = now }   // a held direction doesn't buzz
        p.play(ids[k], 1f, 1f, 0, 0, 1f)
    }

    private const val RATE = 44100

    /** Sine tones with a 4 ms fade in and a smooth decay, plus a quiet octave above for warmth. */
    private fun synth(notes: List<FloatArray>, gain: Float): ShortArray {
        val len = notes.maxOf { it[1] + it[2] } + .02f
        val out = FloatArray((len * RATE).toInt())
        for (n in notes) {
            val f = n[0]; val start = (n[1] * RATE).toInt(); val count = (n[2] * RATE).toInt()
            for (i in 0 until count) {
                val t = i.toFloat() / RATE
                val env = min(1f, t / .004f) * exp(-t * 5f / n[2])
                val s = sin(2 * PI * f * t).toFloat() + .18f * sin(4 * PI * f * t).toFloat()
                if (start + i < out.size) out[start + i] += s * env
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
