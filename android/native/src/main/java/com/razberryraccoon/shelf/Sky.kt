package com.razberryraccoon.shelf

import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.random.Random

/**
 * The theme environments other than Space (City, Forest, Ocean, Aurora, Desert, Stormy; 0.31): drawn by
 * materials/sky.mat into a texture of their own, which the backdrop lays under Home's picture. Their scenes are softer
 * and slower than anything else on screen, so the texture is smaller than the screen (three quarters, or half on
 * Smoothest) and, except on Best looks, drawn every other frame: a whole forest of leafy treetops costs a fraction of
 * what it would drawn at full size every frame. The stormy theme's lightning is decided here, so the thunder can follow.
 */
class Sky(private val stage: Stage) {
    private val engine = stage.engine
    private val mat: Material = stage.readAsset("materials/sky.filamat").let { Material.Builder().payload(it, it.remaining()).build(engine) }
    private val mi = mat.createInstance()
    private val scene = engine.createScene()
    private val camera = engine.createCamera(EntityManager.get().create()).apply {
        setProjection(Camera.Projection.ORTHO, 0.0, 1.0, 0.0, 1.0, 0.0, 10.0); lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0) }
    private val view: View = engine.createView().apply {
        scene = this@Sky.scene; camera = this@Sky.camera
        isPostProcessingEnabled = false; setShadowingEnabled(false); antiAliasing = View.AntiAliasing.NONE
    }
    private val quad = EntityManager.get().create()
    private val noise: Texture
    private var tex: Texture? = null; private var target: RenderTarget? = null; private var size = IntArray(2)
    private val old = ArrayList<Pair<Texture, RenderTarget>>()   // replaced ones, let go a frame later (the backdrop may still hold them)
    private var frame = 0; private var time = 0f; private var drawnTheme = -1
    private val draw: () -> Unit = {}

    // lightning (Stormy): when the next one comes, where, and how bright it is now; Music plays the thunder
    var onLightning: (() -> Unit)? = null
    private var flashAt = -99f; private var nextFlash = 5f; private var fx = 600f; private var fy = 220f

    init {
        // the random values: 256 × 256 bytes, repeating (the shader's hash and value noise)
        val r = Random(7); val b = ByteBuffer.allocateDirect(256 * 256)
        repeat(256 * 256) { b.put(r.nextInt(256).toByte()) }; b.flip()
        noise = Texture.Builder().width(256).height(256).levels(1).sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.R8).build(engine)
        noise.setImage(engine, 0, Texture.PixelBufferDescriptor(b, Texture.Format.R, Texture.Type.UBYTE))
        mi.setParameter("noise", noise, TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT))
        // one square over the whole view (the camera shows 0..1 both ways)
        val vb = VertexBuffer.Builder().bufferCount(1).vertexCount(4).attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12).build(engine)
        val v = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder())
        for ((x, y) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) { v.putFloat(x); v.putFloat(y); v.putFloat(0f) }
        v.flip(); vb.setBufferAt(engine, 0, v)
        val ib = IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        val i = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()); for (n in shortArrayOf(0, 1, 2, 2, 1, 3)) i.putShort(n); i.flip(); ib.setBuffer(engine, i)
        RenderableManager.Builder(1).boundingBox(Box(.5f, .5f, 0f, 1f, 1f, 1f)).culling(false)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib).material(0, mi).build(engine, quad)
        scene.addEntity(quad)
    }

    /**
     * Each frame: the theme (0 is Space, which the backdrop draws itself: then nothing here, and null back), how much it's
     * day, the console's color, and the Smoothness setting. Gives the texture to lay under everything.
     */
    fun frame(dt: Float, theme: Int, day: Float, mood: FloatArray, smooth: Int): Texture? {
        if (old.isNotEmpty()) { old.forEach { (t, rt) -> engine.destroyRenderTarget(rt); engine.destroyTexture(t) }; old.clear() }
        time = (time + dt) % 7200f   // the scenes' clock, wrapped every two hours (the shader's numbers stay precise)
        if (theme <= 0) { stage.preViews.remove(view); drawnTheme = -1; return null }
        // the texture: three quarters of the screen's size, or half on Smoothest; made again if that changes
        val k = if (smooth == 2) .5f else .75f
        val w = (stage.pixels[0] * k).toInt().coerceAtLeast(16); val h = (stage.pixels[1] * k).toInt().coerceAtLeast(16)
        if (tex == null || size[0] != w || size[1] != h) {
            val t = tex; val rt = target; if (t != null && rt != null) old += t to rt
            tex = Texture.Builder().width(w).height(h).levels(1).sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8)
                .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE).build(engine)
            target = RenderTarget.Builder().texture(RenderTarget.AttachmentPoint.COLOR, tex).build(engine)
            view.renderTarget = target; view.viewport = Viewport(0, 0, w, h); size = intArrayOf(w, h); drawnTheme = -1
        }
        // lightning: every 7 to 13 seconds, somewhere in the clouds; two flickers, fading
        if (theme == 6) {
            if (time < flashAt) { flashAt = -99f; nextFlash = time + 5f }   // the clock wrapped
            if (time > nextFlash) { flashAt = time; fx = 200f + 900f * Math.random().toFloat(); fy = 160f + 160f * Math.random().toFloat(); nextFlash = time + 7f + 6f * Math.random().toFloat(); onLightning?.invoke() }
        }
        val u = time - flashAt
        val env = if (theme != 6) 0f else (if (u > 0) exp(-u / .35f) else 0f) + (if (u > .22f) .7f * exp(-(u - .22f) / .3f) else 0f)
        mi.setParameter("theme", theme.toFloat()); mi.setParameter("day", day); mi.setParameter("time", time)
        mi.setParameter("mood", mood[0], mood[1], mood[2]); mi.setParameter("flash", env, fx, fy)
        // drawn each frame on Best looks, every other frame otherwise (always at once when the theme changes, and while
        // lightning flickers, so it stays smooth)
        frame++
        val now = smooth == 0 || drawnTheme != theme || frame % 2 == 0 || env > .02f
        if (now) { stage.preViews[view] = draw; drawnTheme = theme } else stage.preViews.remove(view)
        return tex
    }

    fun destroy() {
        stage.preViews.remove(view)
        engine.destroyEntity(quad); engine.destroyView(view); engine.destroyScene(scene)
        target?.let { engine.destroyRenderTarget(it) }; tex?.let { engine.destroyTexture(it) }; engine.destroyTexture(noise)
        engine.destroyCameraComponent(camera.entity)
    }
}
