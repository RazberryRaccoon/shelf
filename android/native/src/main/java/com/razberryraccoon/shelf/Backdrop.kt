package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import com.google.android.filament.Box
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/**
 * Everything behind the 3D objects: the night sky with a nebula in the selected console's color, stars, the planet up in
 * the top half, Home's picture of the selected game (dark, zoomed in, panning slowly, fading out at the top in a
 * windshield-style dot pattern), and soft shadows under the cartridges. Until 0.20 Android's canvas drew all this, every
 * frame, in its own layer under the 3D; now the 3D engine draws it first, in the same frame, with one small shader
 * (materials/backdrop.mat) that does the same math, and the shadows as flat quads over it (materials/shadow.mat).
 */
class Backdrop(ctx: Context, private val stage: Stage) {
    private val engine = stage.engine
    private var time = 0f
    private val clamp = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val skyMat = material("materials/backdrop.filamat")
    private val shadowMat = material("materials/shadow.filamat")
    private val sky = skyMat.createInstance()
    private val blank = texture(1, 1, Texture.InternalFormat.R8, Texture.Format.R, ByteBuffer.allocateDirect(1), 1)

    // the nebula and planet take the selected console's color, easing from one to the next
    private var mood = Theme.accentOf(null); private var moodFrom = mood; private var moodTo = mood; private var moodT = 1f
    fun mood(c: Int) { if (c != moodTo) { moodFrom = mood; moodTo = c; moodT = 0f } }

    // Home's picture: crossfades between games. Each one goes to the GPU once, when it arrives.
    private var pic: Bitmap? = null; private var picFrom: Bitmap? = null; private var picT = 1f
    private var picTex: Texture? = null; private var fromTex: Texture? = null
    var picVisible = 1f
    fun picture(b: Bitmap?) {
        if (b === pic) return
        picFrom?.takeIf { it !== b }?.recycle(); fromTex?.let { engine.destroyTexture(it) }
        picFrom = pic; fromTex = picTex; pic = b; picTex = b?.let { upload(it) }; picT = 0f
    }
    // how it's shown (Settings › Home picture): where it starts down the screen, how much of it fades and how, how
    // bright, how fast it pans. The fade's mask is made again only when one of those changes.
    private var picTop = Art.TOP; private val picH get() = Stage.H - picTop
    private var fade = Art.FADE; private var fadeType = Art.DOTS; private var pan = Art.PAN; private var panT = 0f
    private var mask = maskTexture()
    private var dim = Art.BRIGHT
    /** While Settings › Home picture is open, the picture shows over everything else's fading, so changes can be seen. */
    var previewArt = false
    fun art(bright: Float, top: Float, fadeLen: Float, type: Int, speed: Float) {
        dim = bright; pan = speed
        if (top != picTop || fadeLen != fade || type != fadeType) {
            picTop = top; fade = fadeLen; fadeType = type
            engine.destroyTexture(mask); mask = maskTexture()
        }
    }

    /** Shadows to draw under the cartridges: x, y (center), width, strength. */
    val shadows = ArrayList<FloatArray>()
    /** How dark everything behind the 3D is (0..1), while a model is looked at closely. */
    var shade = 0f
    /** Lighter drawing (Settings › Smoothness › Smoothest): the stars hold still. */
    var lite = false

    // the sky: one quad far bigger than any screen, its corners labeled with their layout pixels, so the shader knows
    // where each pixel is; the stage's camera for the backdrop shows just the screen's part of it
    private val quad = Quad(-4000f, -4000f, 6000f, 6000f)
    private val unit = Quad(-1f, -1f, 1f, 1f)
    private val skyEntity = EntityManager.get().create()
    private val shadowPool = ArrayList<Pair<Int, MaterialInstance>>()

    init {
        sky.setParameter("stars", starTexture(), clamp)
        sky.setParameter("bg", Color.red(Theme.bg) / 255f, Color.green(Theme.bg) / 255f, Color.blue(Theme.bg) / 255f)
        RenderableManager.Builder(1).boundingBox(Box(1000f, 0f, 0f, 5000f, 5000f, 1f)).culling(false).priority(0)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, quad.vb, quad.ib).material(0, sky).build(engine, skyEntity)
        stage.backScene.addEntity(skyEntity)
        tick(0f)
    }

    fun tick(dt: Float) {
        time += dt
        moodT = minOf(1f, moodT + dt / .8f); mood = blend(moodFrom, moodTo, ease(moodT))
        panT += dt * pan
        picT = minOf(1f, picT + dt / .45f)
        // the sky
        sky.setParameter("mood", Color.red(mood) / 255f, Color.green(mood) / 255f, Color.blue(mood) / 255f)
        sky.setParameter("twinkle", if (lite) -1f else time * .6f)
        sky.setParameter("shade", shade)
        // the picture, panning diagonally from its top left to its bottom right: there and back, about 28 seconds each
        // way at the usual speed
        val sweep = .5f - .5f * cos(panT * 2 * PI.toFloat() / 56f)
        val vis = if (previewArt) 1f else picVisible
        val a = if (pic != null) ease(picT) * vis else 0f; val af = if (picFrom != null && picT < 1) (1 - ease(picT)) * vis else 0f
        sky.setParameter("picA", a, af)
        place("pic", "picUv", pic, picTex, sweep); place("picFrom", "fromUv", picFrom, fromTex, sweep)
        // dimmed (far in the background) with a little blue, as the prototype's [.2, .21, .26] at the start
        sky.setParameter("dim", dim.coerceAtMost(1f), (dim * 1.06f).coerceAtMost(1f), (dim * 1.29f).coerceAtMost(1f))
        sky.setParameter("picBox", picTop, picH.toInt().coerceAtLeast(8).toFloat())
        sky.setParameter("mask", mask, clamp)
        // the shadows: one soft round shadow stretched flat under each object
        val tm = engine.transformManager; var n = 0
        for (s in shadows) {
            if (s[2] < 2f || s[3] < .01f) continue
            if (n == shadowPool.size) shadowPool += newShadow()
            val (e, mi) = shadowPool[n++]
            mi.setParameter("strength", s[3].coerceIn(0f, 1f))
            // flat: as tall as .22 of its width; the backdrop's world has y going up, so layout y is negated
            val w = s[2] / 2; val h = s[2] * .11f
            tm.setTransform(tm.getInstance(e), floatArrayOf(w, 0f, 0f, 0f, 0f, h, 0f, 0f, 0f, 0f, 1f, 0f, s[0], -s[1], 0f, 1f))
            stage.backScene.addEntity(e)
        }
        for (i in n until shadowPool.size) stage.backScene.removeEntity(shadowPool[i].first)
    }

    /** One picture's place: zoomed to cover its part of the screen and a bit more, moved along by the sweep. */
    private fun place(tex: String, uv: String, b: Bitmap?, t: Texture?, sweep: Float) {
        sky.setParameter(tex, t ?: blank, clamp)
        if (b == null || t == null) return
        val zoom = 1.3f; val k = maxOf(Stage.W / b.width, picH / b.height) * zoom
        val ySlack = b.height * k - picH
        val tx = -(b.width * k - Stage.W) * sweep; val ty = picTop - ySlack * (.15f + .7f * sweep)
        sky.setParameter(uv, 1f / (k * b.width), -tx / (k * b.width), 1f / (k * b.height), -ty / (k * b.height))
    }

    private fun newShadow(): Pair<Int, MaterialInstance> {
        val e = EntityManager.get().create(); val mi = shadowMat.createInstance()
        RenderableManager.Builder(1).boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f)).culling(false).priority(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, unit.vb, unit.ib).material(0, mi).build(engine, e)
        return e to mi
    }

    private fun material(path: String): Material {
        val b = stage.readAsset(path)
        return Material.Builder().payload(b, b.remaining()).build(engine)
    }

    /** A texture filled from raw bytes, rows `stride` pixels apart. */
    private fun texture(w: Int, h: Int, f: Texture.InternalFormat, pf: Texture.Format, data: ByteBuffer, stride: Int): Texture {
        val t = Texture.Builder().width(w).height(h).levels(1).sampler(Texture.Sampler.SAMPLER_2D).format(f).build(engine)
        t.setImage(engine, 0, Texture.PixelBufferDescriptor(data, pf, Texture.Type.UBYTE, 1, 0, 0, stride, null, null))
        return t
    }

    /** A picture's pixels, copied once to the GPU (as they are: the shader works in the same sRGB values the canvas did). */
    private fun upload(b: Bitmap): Texture {
        val src = if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
        val buf = ByteBuffer.allocateDirect(src.byteCount).order(ByteOrder.nativeOrder())
        src.copyPixelsToBuffer(buf); buf.flip()
        if (src !== b) src.recycle()
        return texture(b.width, b.height, Texture.InternalFormat.RGBA8, Texture.Format.RGBA, buf, b.width)
    }

    /** The stars, drawn once: each one's coverage (red) and its twinkle's phase (green, a little wider than the star). */
    private fun starTexture(): Texture {
        val w = Stage.W.toInt(); val h = Stage.H.toInt()
        val cover = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8); val phase = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val cc = Canvas(cover); val pc = Canvas(phase)
        val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }; val qp = Paint()
        Random(7).let { r -> repeat(240) {
            val x = r.nextFloat() * Stage.W; val y = r.nextFloat() * Stage.H; val rad = .5f + r.nextFloat() * 1.2f; val ph = r.nextFloat() * 6.3f
            cc.drawCircle(x, y, rad, q)
            qp.color = Color.argb((ph / 6.3f * 255).toInt().coerceIn(0, 255), 255, 255, 255); pc.drawCircle(x, y, rad + 2f, qp)
        } }
        val a = ByteBuffer.allocate(cover.rowBytes * h); cover.copyPixelsToBuffer(a)
        val b = ByteBuffer.allocate(phase.rowBytes * h); phase.copyPixelsToBuffer(b)
        val out = ByteBuffer.allocateDirect(w * h * 2)
        for (y in 0 until h) for (x in 0 until w) { out.put(a.get(y * cover.rowBytes + x)); out.put(b.get(y * phase.rowBytes + x)) }
        out.flip(); cover.recycle(); phase.recycle()
        return texture(w, h, Texture.InternalFormat.RG8, Texture.Format.RG, out, w)
    }

    /** The fade's mask as a texture, one byte a pixel. */
    private fun maskTexture(): Texture {
        val m = fadeMask(); val buf = ByteBuffer.allocateDirect(m.rowBytes * m.height); m.copyPixelsToBuffer(buf); buf.flip()
        val t = texture(m.width, m.height, Texture.InternalFormat.R8, Texture.Format.R, buf, m.rowBytes)
        m.recycle(); return t
    }

    /** A flat rectangle, its corners' texture coordinates the same as their positions (y taken as down, as in the layout). */
    private inner class Quad(x0: Float, y0: Float, x1: Float, y1: Float) {
        val vb: VertexBuffer = VertexBuffer.Builder().bufferCount(1).vertexCount(4)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 12, 20).build(engine)
        val ib: IndexBuffer = IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        init {
            val v = ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder())
            for ((x, y) in listOf(x0 to y0, x1 to y0, x0 to y1, x1 to y1)) { v.putFloat(x); v.putFloat(-y); v.putFloat(0f); v.putFloat(x); v.putFloat(y) }
            v.flip(); vb.setBufferAt(engine, 0, v)
            val i = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
            for (n in shortArrayOf(0, 1, 2, 2, 1, 3)) i.putShort(n)
            i.flip(); ib.setBuffer(engine, i)
        }
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
