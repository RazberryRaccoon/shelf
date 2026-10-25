package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything behind the 3D layer, drawn with Android's own canvas in layout pixels: the night sky with a nebula in the
 * selected console's color, stars, the planet up in the top half, Home's picture of the selected game (dark, zoomed in,
 * panning slowly, fading out at the top in a windshield-style dot pattern), and soft shadows under the cartridges.
 */
class Backdrop(ctx: Context) : View(ctx) {
    var scale = 1f; var ox = 0f; var oy = 0f
    private var time = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = Random(7).let { r -> List(240) { floatArrayOf(r.nextFloat() * Stage.W, r.nextFloat() * Stage.H, .5f + r.nextFloat() * 1.2f, r.nextFloat() * 6.3f) } }

    // the nebula and planet take the selected console's color, easing from one to the next
    private var mood = Theme.accentOf(null); private var moodFrom = mood; private var moodTo = mood; private var moodT = 1f
    fun mood(c: Int) { if (c != moodTo) { moodFrom = mood; moodTo = c; moodT = 0f } }

    // Home's picture: crossfades between games
    private var pic: Bitmap? = null; private var picFrom: Bitmap? = null; private var picT = 1f
    var picVisible = 1f
    fun picture(b: Bitmap?) { if (b === pic) return; picFrom?.takeIf { it !== b }?.recycle(); picFrom = pic; pic = b; picT = 0f }
    // how it's shown (Settings › Home picture): where it starts down the screen, how much of it fades and how, how
    // bright, how fast it pans. The fade's mask is made again only when one of those changes.
    private var picTop = Art.TOP; private val picH get() = Stage.H - picTop
    private var fade = Art.FADE; private var fadeType = Art.DOTS; private var pan = Art.PAN; private var panT = 0f
    private var dots: Bitmap = fadeMask()
    private var dim = dimFor(Art.BRIGHT)
    /** While Settings › Home picture is open, the picture shows over everything else's fading, so changes can be seen. */
    var previewArt = false
    fun art(bright: Float, top: Float, fadeLen: Float, type: Int, speed: Float) {
        dim = dimFor(bright); pan = speed
        if (top != picTop || fadeLen != fade || type != fadeType) {
            picTop = top; fade = fadeLen; fadeType = type
            dots.recycle(); dots = fadeMask()
            dotShader = android.graphics.BitmapShader(dots, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setTranslate(0f, picTop) }) }
        }
    }
    /** Dimmed (far in the background) with a little blue, as the prototype's [.2, .21, .26] at the start. */
    private fun dimFor(b: Float) = LightingColorFilter(Color.rgb((255 * b).toInt().coerceIn(0, 255), (255 * b * 1.06f).toInt().coerceIn(0, 255), (255 * b * 1.29f).toInt().coerceIn(0, 255)), 0)

    /** Shadows to draw under the cartridges: x, y (center), width, strength. */
    val shadows = ArrayList<FloatArray>()

    fun tick(dt: Float) {
        time += dt
        moodT = minOf(1f, moodT + dt / .8f); mood = blend(moodFrom, moodTo, ease(moodT))
        panT += dt * pan
        picT = minOf(1f, picT + dt / .45f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Theme.bg)
        c.save(); c.translate(ox, oy); c.scale(scale, scale)
        // the nebula: two soft glows in the console's color (their gradients made again only when the color changes)
        if (skyFor != mood) { skyFor = mood; sky = makeSky(mood) }
        p.shader = sky[0]; c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        p.shader = sky[1]; c.drawRect(0f, 0f, Stage.W, Stage.H, p); p.shader = null
        // stars, twinkling slowly
        for (s in stars) { p.color = Color.argb((90 + 110 * (.5f + .5f * sin(time * .6f + s[3]))).toInt(), 238, 241, 255); c.drawCircle(s[0], s[1], s[2], p) }
        planet(c)
        // the picture, then the shadows on top of it
        val sweep = .5f - .5f * cos(panT * 2 * PI.toFloat() / 56f)   // there and back, about 28 seconds each way at the usual speed
        val vis = if (previewArt) 1f else picVisible
        picFrom?.let { if (picT < 1) drawPicture(c, it, (1 - ease(picT)) * vis, sweep) }
        pic?.let { drawPicture(c, it, ease(picT) * vis, sweep) }
        // shadows: one soft round shadow, drawn once, stretched flat under each object
        for (s in shadows) {
            if (s[2] < 2f || s[3] < .01f) continue
            p.alpha = (255 * s[3]).toInt().coerceIn(0, 255)
            shadowDst.set(s[0] - s[2] / 2, s[1] - s[2] * .11f, s[0] + s[2] / 2, s[1] + s[2] * .11f)
            c.drawBitmap(shadowSprite, null, shadowDst, p)
        }
        p.alpha = 255
        c.restore()
    }

    private fun planet(c: Canvas) {
        val x = 1190f; val y = -175f; val r = 390f
        p.shader = sky[2]; c.drawCircle(x, y, r * 1.18f, p)   // the atmosphere's glow
        p.shader = sky[3]; c.drawCircle(x, y, r, p)
        p.shader = null
    }

    // the sky's gradients for one color: the nebula's two glows, the planet's atmosphere and the planet
    private var skyFor = 0; private var sky: Array<Shader> = makeSky(mood)
    private fun makeSky(m: Int): Array<Shader> {
        val x = 1190f; val y = -175f; val r = 390f
        return arrayOf(
            RadialGradient(300f, 160f, 700f, Color.argb(70, Color.red(m), Color.green(m), Color.blue(m)), Color.TRANSPARENT, Shader.TileMode.CLAMP),
            RadialGradient(1100f, 700f, 600f, Color.argb(40, Color.red(m), Color.green(m), Color.blue(m)), Color.TRANSPARENT, Shader.TileMode.CLAMP),
            RadialGradient(x, y, r * 1.18f, intArrayOf(Color.argb(90, Color.red(m), Color.green(m), Color.blue(m)), Color.TRANSPARENT), floatArrayOf(.82f, 1f), Shader.TileMode.CLAMP),
            RadialGradient(x - r * .35f, y + r * .55f, r * 1.25f, intArrayOf(blend(m, Color.WHITE, .55f), blend(m, Theme.bg, .45f), Color.rgb(6, 8, 20)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP))
    }

    private val shadowDst = android.graphics.RectF()
    private val shadowSprite: Bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { b ->
        Canvas(b).drawCircle(64f, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(64f, 64f, 64f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP) })
    }

    private var dotShader = android.graphics.BitmapShader(dots, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setTranslate(0f, picTop) }) }
    private val picMatrix = Matrix()

    /** The picture, panning diagonally from its top left to its bottom right, masked by the dots in one step. */
    private fun drawPicture(c: Canvas, b: Bitmap, a: Float, sweep: Float) {
        if (a < .01f) return
        val zoom = 1.3f; val k = maxOf(Stage.W / b.width, picH / b.height) * zoom
        val ySlack = b.height * k - picH
        picMatrix.setScale(k, k); picMatrix.postTranslate(-(b.width * k - Stage.W) * sweep, picTop - ySlack * (.15f + .7f * sweep))
        val pic = android.graphics.BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(picMatrix) }
        p.shader = android.graphics.ComposeShader(pic, dotShader, PorterDuff.Mode.DST_IN)   // the picture where the dots are
        p.colorFilter = dim; p.alpha = (255 * a).toInt()
        c.drawRect(0f, picTop, Stage.W, Stage.H, p)
        p.shader = null; p.colorFilter = null; p.alpha = 255
    }

    /**
     * Where the picture shows: solid at the bottom, then fading out toward the top over `fade` of its height, one of three
     * ways: a plain fade; rows of dots that shrink toward the top (as in the prototype's homeDotMask); or horizontal lines
     * that get thinner and farther apart the higher they are.
     */
    private fun fadeMask(): Bitmap {
        val w = Stage.W.toInt(); val h = picH.toInt().coerceAtLeast(8); val b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8); val c = Canvas(b)
        val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val solid = h * (1 - fade)   // the fading part is the top `fade` of the picture
        c.drawRect(0f, solid, w.toFloat(), h.toFloat(), q)
        when (fadeType) {
            Art.FADE_PLAIN -> { q.shader = android.graphics.LinearGradient(0f, 0f, 0f, solid, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w.toFloat(), solid, q) }
            Art.DOTS -> {
                val pitch = 13f; var y = solid; var row = 0
                while (y > -pitch) {
                    val t = (y / solid).coerceIn(0f, 1f); val r = pitch * (.06f + .66f * Math.pow(t.toDouble(), 1.25).toFloat())
                    q.alpha = ((.45f + .55f * t) * 255).toInt()
                    var x = if (row % 2 == 1) pitch / 2 else 0f
                    while (x < w + pitch) { c.drawCircle(x, y, r, q); x += pitch }
                    y -= pitch * .866f; row++
                }
            }
            else -> {   // lines: thick and close together near the solid part, thinner and farther apart further up
                var y = solid
                while (y > 0) {
                    val t = (y / solid).coerceIn(0f, 1f); val gap = 6f + 22f * (1 - t); val thick = gap * (.15f + .75f * Math.pow(t.toDouble(), 1.4).toFloat())
                    q.alpha = ((.5f + .5f * t) * 255).toInt()
                    c.drawRect(0f, y - thick, w.toFloat(), y, q)
                    y -= gap
                }
            }
        }
        return b
    }

    companion object {
        fun ease(t: Float) = t * t * (3 - 2 * t)
        fun blend(a: Int, b: Int, t: Float) = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    }
}

/** Home picture's settings: the starting values, and the three ways its top can fade. */
object Art {
    const val BRIGHT = .2f; const val TOP = 424f; const val FADE = .5f; const val PAN = 1f
    const val FADE_PLAIN = 0; const val DOTS = 1; const val LINES = 2
    val FADES = listOf("Fade", "Dots", "Lines")
    val KINDS = listOf("shot" to "Screenshot", "title" to "Title screen", "box" to "Box art", "none" to "None")
}
