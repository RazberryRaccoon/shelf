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
    private val picTop = 424f; private val picH = Stage.H - picTop
    private val dots: Bitmap = dotMask()
    private val dim = LightingColorFilter(Color.rgb(51, 54, 66), 0)   // the prototype's [.2, .21, .26]: far in the background

    /** Shadows to draw under the cartridges: x, y (center), width, strength. */
    val shadows = ArrayList<FloatArray>()

    fun tick(dt: Float) {
        time += dt
        moodT = minOf(1f, moodT + dt / .8f); mood = blend(moodFrom, moodTo, ease(moodT))
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
        val sweep = .5f - .5f * cos(time * 2 * PI.toFloat() / 56f)   // there and back, about 28 seconds each way
        picFrom?.let { if (picT < 1) drawPicture(c, it, (1 - ease(picT)) * picVisible, sweep) }
        pic?.let { drawPicture(c, it, ease(picT) * picVisible, sweep) }
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

    private val dotShader = android.graphics.BitmapShader(dots, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setTranslate(0f, picTop) }) }
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

    /** Solid at the bottom, then rows of dots that shrink toward the top (as in the prototype's homeDotMask). */
    private fun dotMask(): Bitmap {
        val w = Stage.W.toInt(); val h = picH.toInt(); val b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8); val c = Canvas(b)
        val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val pitch = 13f; val solid = h * .5f
        c.drawRect(0f, solid, w.toFloat(), h.toFloat(), q)
        var y = solid; var row = 0
        while (y > -pitch) {
            val t = (y / solid).coerceIn(0f, 1f); val r = pitch * (.06f + .66f * Math.pow(t.toDouble(), 1.25).toFloat())
            q.alpha = ((.45f + .55f * t) * 255).toInt()
            var x = if (row % 2 == 1) pitch / 2 else 0f
            while (x < w + pitch) { c.drawCircle(x, y, r, q); x += pitch }
            y -= pitch * .866f; row++
        }
        return b
    }

    companion object {
        fun ease(t: Float) = t * t * (3 - 2 * t)
        fun blend(a: Int, b: Int, t: Float) = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    }
}
