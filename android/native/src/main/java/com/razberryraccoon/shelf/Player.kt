package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.widget.FrameLayout
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import java.io.File
import kotlin.math.min

/**
 * A game's video, over everything, when you choose it among its pictures (it never plays on its own): everything behind
 * dims, the video fades in and plays once with its sound; A pauses or plays, B closes.
 */
class Player(ctx: Context, file: File, private val name: String) : FrameLayout(ctx) {
    var onClose: (() -> Unit)? = null
    private var shown = 0f
    private var closing = false
    // a TextureView, not a VideoView: the board is scaled to the screen, and only a texture follows that
    private val view = TextureView(ctx)
    private val mp = MediaPlayer()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        addView(view, LayoutParams(1100, 620, Gravity.CENTER).apply { bottomMargin = 40 })
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                try {
                    mp.setSurface(Surface(st)); mp.setDataSource(file.path)
                    mp.setOnPreparedListener {
                        // fitted inside the frame at the video's own shape
                        val s = min(1100f / it.videoWidth.coerceAtLeast(1), 620f / it.videoHeight.coerceAtLeast(1))
                        view.layoutParams = LayoutParams((it.videoWidth * s).toInt(), (it.videoHeight * s).toInt(), Gravity.CENTER).apply { bottomMargin = 40 }
                        it.start()
                    }
                    mp.setOnErrorListener { _, _, _ -> closing = true; true }
                    mp.prepareAsync()
                } catch (e: Exception) { closing = true }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        alpha = 0f
    }

    fun input(a: String): Boolean {
        if (closing) return true
        when (a) {
            "a" -> { try { if (mp.isPlaying) mp.pause() else mp.start() } catch (e: Exception) { }; Sounds.play(Sounds.PICK) }
            "b" -> { closing = true; try { mp.pause() } catch (e: Exception) { }; Sounds.play(Sounds.CLOSE) }
        }
        return true
    }

    fun tick(dt: Float) {
        shown = if (closing) maxOf(0f, shown - dt / .2f) else min(1f, shown + dt / .3f)
        alpha = Backdrop.ease(shown)
        if (closing && shown == 0f) { mp.release(); onClose?.invoke() }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        p.color = Color.argb(240, 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        p.typeface = Fonts.bold; p.textSize = 23f; p.color = Theme.fg; p.textAlign = Paint.Align.CENTER
        c.drawText(name, Stage.W / 2, Stage.H - 40f, p); p.textAlign = Paint.Align.LEFT
    }
}
