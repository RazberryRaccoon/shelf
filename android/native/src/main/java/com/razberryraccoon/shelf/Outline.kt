package com.razberryraccoon.shelf

import android.opengl.Matrix
import com.google.android.filament.Box
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The glow around whatever is chosen on a game's page (the cartridge, the closed box, the manual), in its console's
 * color, on top of it lifting a little: a thin bright line right at its edge, a soft halo fading out from it, a faint
 * wider aura, and a light that slowly travels round. It swells a little as something is chosen and then breathes gently.
 *
 * How (0.22, after the first version, a grown copy of the whole model, looked like "layers of glowing paper": every part
 * of the copy, halves, board, label, glowed on its own): a view of its own draws only the chosen object (put on a layer
 * of its own) into a half-size texture each frame, its silhouette; one panel around the object reads that silhouette,
 * blurred through the texture's smaller copies (mipmaps), and draws the glow (materials/glow.mat). The object marks its
 * own pixels in the stencil (`mark`) and the panel skips them, so the glow's inner edge is the object's real outline,
 * never over it, and nothing in front of the panel (the box's tray) can hide it. One silhouette, so one smooth glow.
 */
class Outline(private val stage: Stage) {
    private val engine = stage.engine
    private val mat: Material = stage.readAsset("materials/glow.filamat").let { Material.Builder().payload(it, it.remaining()).build(engine) }
    private val mi: MaterialInstance = mat.createInstance().apply { setStencilCompareFunction(TextureSampler.CompareFunction.NOT_EQUAL); setStencilWrite(false) }
    // the silhouette: half the 3D's size on screen, with its mipmaps (made again each frame, right after it's drawn)
    private val mw = max(2, stage.pixels[0] / 2); private val mh = max(2, stage.pixels[1] / 2)
    private val tex: Texture = Texture.Builder().width(mw).height(mh).levels(1 + log2(max(mw, mh).toFloat()).toInt())
        .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE or Texture.Usage.GEN_MIPMAPPABLE)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8).build(engine)
    private val target: RenderTarget = RenderTarget.Builder().texture(RenderTarget.AttachmentPoint.COLOR, tex).build(engine)
    private val maskView: View = engine.createView().apply {
        scene = stage.scene; camera = stage.camera; renderTarget = target; viewport = Viewport(0, 0, mw, mh)
        isPostProcessingEnabled = false; setShadowingEnabled(false); antiAliasing = View.AntiAliasing.NONE
        setVisibleLayers(0xff, LAYER)   // only what's on the glow's layer
    }
    // the panel: a square, stretched over the object and its glow each frame
    private val vb: VertexBuffer = VertexBuffer.Builder().bufferCount(1).vertexCount(4)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12).build(engine)
    private val ib: IndexBuffer = IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    private val panel = EntityManager.get().create()
    // the half sizes of the object (layout pixels across its own box), its glow's reach, and the console's colors
    private val radius = 20f * mw / 667f   // in silhouette pixels: about 40 screen pixels on the Retroid
    private var chosen: IntArray? = null; private var next: IntArray? = null; private var nextRef = 0
    private var k = 0f; private var swell = 9f; private var time = 0f
    private val mips: () -> Unit = { tex.generateMipmaps(engine) }

    init {
        val v = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder())
        for ((x, y) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) { v.putFloat(x); v.putFloat(y); v.putFloat(0f) }
        v.flip(); vb.setBufferAt(engine, 0, v)
        val i = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()); for (n in shortArrayOf(0, 1, 2, 2, 1, 3)) i.putShort(n); i.flip(); ib.setBuffer(engine, i)
        // drawn last, over everything around the object; only on the main view's layer
        RenderableManager.Builder(1).boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f)).culling(false).priority(7).castShadows(false).receiveShadows(false)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib).material(0, mi).build(engine, panel)
        stage.scene.addEntity(panel)
        mi.setParameter("mask", tex, TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE))
        mi.setParameter("texel", 1f / mw, 1f / mh)
        mi.setParameter("radius", radius)
        mi.setParameter("lod", max(0f, log2(radius / 4f)))
        mi.setParameter("aspect", Stage.W / Stage.H)
        hidePanel()
    }

    /**
     * Each frame: the object to glow around (its entities, the stencil number it marks its pixels with, where it is:
     * its model's transform and its box's centre and half size in its own millimetres), or null for none; `sys` gives
     * the color. Moving from one object to another, the glow fades from the first and swells in on the next.
     */
    fun frame(dt: Float, entities: IntArray?, ref: Int, sys: String?, world: FloatArray?, center: FloatArray?, half: FloatArray?) {
        time += dt; swell += dt
        val same = entities != null && chosen != null && entities.contentEquals(chosen)
        if (!same) {
            if (k > .01f && chosen != null) k = max(0f, k - dt / .14f)   // the old one fades quickly first
            else {   // then the glow moves to the new one
                chosen?.let { layer(it, false) }
                chosen = entities; entities?.let { layer(it, true); swell = 0f }
                mi.setStencilReferenceValue(ref)
                sys?.let { color(it) }
            }
        } else k = min(1f, k + dt / .3f)
        if (chosen == null || k < .005f || world == null || center == null || half == null) { stage.preViews.remove(maskView); return hidePanel() }
        stage.preViews[maskView] = mips
        // the panel: over the object's box as it lies on the screen, with room for the glow
        var x0 = 1e9f; var x1 = -1e9f; var y0 = 1e9f; var y1 = -1e9f
        val p = FloatArray(4); val c = FloatArray(4)
        for (n in 0 until 8) {
            c[0] = center[0] + if (n and 1 == 0) -half[0] else half[0]; c[1] = center[1] + if (n and 2 == 0) -half[1] else half[1]
            c[2] = center[2] + if (n and 4 == 0) -half[2] else half[2]; c[3] = 1f
            Matrix.multiplyMV(p, 0, world, 0, c, 0)
            x0 = min(x0, p[0]); x1 = max(x1, p[0]); y0 = min(y0, p[1]); y1 = max(y1, p[1])
        }
        val pad = radius * 2 * 2.2f + 10f   // the glow's reach on screen, and a little more (the long lens's perspective)
        val m = FloatArray(16); Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, (x0 + x1) / 2, (y0 + y1) / 2, 0f); Matrix.scaleM(m, 0, (x1 - x0) / 2 + pad, (y1 - y0) / 2 + pad, 1f)
        val tm = engine.transformManager; tm.setTransform(tm.getInstance(panel), m)
        mi.setParameter("center", (x0 + x1) / 2 / Stage.W, (y0 + y1) / 2 / Stage.H)   // world y is up, as the view's coordinates
        // a gentle swell as it's chosen (up a quarter and back over two thirds of a second), then breathing slowly; a
        // light travels once round about every eleven seconds
        val bump = if (swell < .7f) .25f * sin(PI.toFloat() * swell / .7f) else 0f
        mi.setParameter("strength", Backdrop.ease(k) * (1f + bump) * (.93f + .07f * sin(time * 1.7f)))
        mi.setParameter("orbit", time * .57f)
    }

    /** The console's color: the glow a richer version of its accent, the line lighter (tuned in tools/lighting-rig). */
    private fun color(sys: String) {
        val a = Theme.accentOf(sys)
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(a, hsv); hsv[1] = min(1f, hsv[1] * 1.45f)
        val rich = android.graphics.Color.HSVToColor(hsv)
        fun lin(c: Int) = floatArrayOf(srgbToLinear(android.graphics.Color.red(c) / 255f), srgbToLinear(android.graphics.Color.green(c) / 255f), srgbToLinear(android.graphics.Color.blue(c) / 255f))
        val g = lin(rich); val e = lin(a)
        mi.setParameter("glow", g[0] * .95f, g[1] * .95f, g[2] * .95f)
        mi.setParameter("edge", (e[0] * .7f + .3f) * 1.05f, (e[1] * .7f + .3f) * 1.05f, (e[2] * .7f + .3f) * 1.05f)
    }

    /** Puts these entities (and everything under them) on the glow's layer too, or takes them off it. */
    private fun layer(entities: IntArray, on: Boolean) {
        val rm = engine.renderableManager
        for (e in tree(stage, entities)) { val i = rm.getInstance(e); if (i != 0) rm.setLayerMask(i, 0xff, if (on) 0x1 or LAYER else 0x1) }
    }

    private fun hidePanel() { val tm = engine.transformManager; tm.setTransform(tm.getInstance(panel), FloatArray(16).also { Matrix.setIdentityM(it, 0); Matrix.translateM(it, 0, -4000f, 0f, 0f) }) }

    fun destroy() {
        chosen?.let { layer(it, false) }
        stage.preViews.remove(maskView)
        stage.scene.removeEntity(panel); engine.destroyEntity(panel); EntityManager.get().destroy(panel)
        engine.destroyVertexBuffer(vb); engine.destroyIndexBuffer(ib)
        engine.destroyView(maskView); engine.destroyRenderTarget(target); engine.destroyTexture(tex)
        engine.destroyMaterialInstance(mi); engine.destroyMaterial(mat)
    }

    companion object {
        /** The stencil numbers: each object glowed around on the page marks its pixels with its own. */
        const val CART = 1; const val BOX = 2; const val MANUAL = 3; const val CONSOLE = 4   // CONSOLE: the start animation's hit
        /** The layer only the silhouette's view shows (everything is on layer 1, the main view's). */
        private const val LAYER = 0x2

        /** These entities and everything under them. */
        fun tree(stage: Stage, entities: IntArray): List<Int> {
            val tm = stage.engine.transformManager; val all = ArrayList<Int>()
            fun walk(e: Int) { all += e; val ti = tm.getInstance(e); if (ti != 0) { val kids = IntArray(tm.getChildCount(ti)); tm.getChildren(ti, kids); kids.forEach { walk(it) } } }
            entities.forEach { walk(it) }
            return all
        }

        /** Makes these entities (and everything under them) mark their pixels with `ref`, or stop marking (`ref` 0). */
        fun mark(stage: Stage, entities: IntArray, ref: Int) {
            val rm = stage.engine.renderableManager
            for (e in tree(stage, entities)) {
                val i = rm.getInstance(e); if (i == 0) continue
                for (p in 0 until rm.getPrimitiveCount(i)) rm.getMaterialInstanceAt(i, p).apply {
                    setStencilWrite(ref != 0)
                    setStencilCompareFunction(TextureSampler.CompareFunction.ALWAYS)
                    setStencilOpDepthStencilPass(MaterialInstance.StencilOperation.REPLACE)
                    setStencilReferenceValue(ref)
                }
            }
        }
    }
}
