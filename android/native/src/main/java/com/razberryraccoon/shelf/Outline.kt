package com.razberryraccoon.shelf

import android.opengl.Matrix
import com.google.android.filament.Box
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The glow that hugs whatever is chosen on a game's page (the cartridge, the closed box, the manual), on top of it
 * lifting a little: a bright, crisp line right at its edge fading into a soft halo, breathing slowly.
 *
 * How: a copy of the object's shape, a little bigger in every direction, drawn after everything else and only where the
 * object itself isn't. The object marks its own pixels in the stencil buffer with its own number (`mark`), and the copy
 * skips those, so the glow is a ring around its real outline that nothing in front of the copy, like the box's tray, can
 * cover, and that never covers the object. The copy is either the object's own model loaded again (a cartridge, a box)
 * or a plain box (the manual, a flat booklet).
 */
class Outline private constructor(private val stage: Stage, private val ref: Int, private val asset: FilamentAsset?) {
    private val engine = stage.engine
    private val mi: MaterialInstance = material(stage).createInstance().apply {
        setStencilCompareFunction(TextureSampler.CompareFunction.NOT_EQUAL)
        setStencilReferenceValue(ref)
        setStencilWrite(false)
    }
    private var cube = 0
    private val root: Int
    // the object's box (centre and half size, in its own millimetres)
    private val center = FloatArray(3); private val half = FloatArray(3)
    /** How strongly it shows, 0..1 (eases toward `want`). */
    private var k = 0f
    private var time = 0f

    init {
        if (asset != null) {
            // every part of the copy wears the glow, and it's drawn last (so the stencil is complete)
            val rm = engine.renderableManager
            for (e in asset.renderableEntities) {
                val i = rm.getInstance(e); if (i == 0) continue
                for (p in 0 until rm.getPrimitiveCount(i)) rm.setMaterialInstanceAt(i, p, mi)
                rm.setPriority(i, 7); rm.setCastShadows(i, false); rm.setReceiveShadows(i, false)
            }
            asset.boundingBox.center.copyInto(center); asset.boundingBox.halfExtent.copyInto(half)
            stage.scene.addEntities(asset.entities)
            root = asset.root
        } else {
            cube = EntityManager.get().create()
            RenderableManager.Builder(1).boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f)).culling(false).priority(7).castShadows(false).receiveShadows(false)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, cubeVb(engine), cubeIb(engine)).material(0, mi).build(engine, cube)
            stage.scene.addEntity(cube)
            root = cube
        }
        hide()
    }

    /** For a plain box (the manual): its centre and half size in its own millimetres. */
    fun size(c: FloatArray, h: FloatArray) { c.copyInto(center); h.copyInto(half) }

    /**
     * Each frame: around the object placed by `world` (its model's transform), `on` whether it's the chosen one. The
     * ring reaches `width` layout pixels out.
     */
    fun place(world: FloatArray, on: Boolean, dt: Float, width: Float = 13f) {
        time += dt
        k += ((if (on) 1f else 0f) - k) * kotlin.math.min(1f, dt * 9f)
        if (k < .01f) return hide()
        // the transform's own scale (layout pixels per millimetre): it's placed at the same scale on every axis
        val s = sqrt(world[0] * world[0] + world[1] * world[1] + world[2] * world[2]).coerceAtLeast(1e-4f)
        val m = FloatArray(16)
        Matrix.translateM(m, 0, world, 0, center[0], center[1], center[2])
        // the copy: grown by `width` on every side, whatever the object's proportions
        if (asset != null) {
            Matrix.scaleM(m, 0, 1 + width / (half[0] * s), 1 + width / (half[1] * s), 1 + width / (half[2] * s))
            Matrix.translateM(m, 0, -center[0], -center[1], -center[2])
        } else Matrix.scaleM(m, 0, half[0] + width / s, half[1] + width / s, half[2] + width / s)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(root), m)
        // the shader measures how far out each point is, in the object's own box, in layout pixels
        val box = FloatArray(16); Matrix.translateM(box, 0, world, 0, center[0], center[1], center[2]); Matrix.scaleM(box, 0, 1 / s, 1 / s, 1 / s)
        val inv = FloatArray(16); Matrix.invertM(inv, 0, box, 0)
        mi.setParameter("toBox", MaterialInstance.FloatElement.MAT4, inv, 0, 1)
        mi.setParameter("extent", half[0] * s, half[1] * s, half[2] * s)
        mi.setParameter("width", width)
        // Theme.sel's soft white-blue, a little over the screen's white so the line stays crisp through the tone curve;
        // breathing gently (never off), and easing in and out as the choice moves. Tuned in a render with the app's
        // Filament (tools: the lighting rig): a bright line about two pixels wide, then a halo fading out over the rest
        val a = Backdrop.ease(k) * (.86f + .14f * sin(time * 2.4f))
        mi.setParameter("color", 1.3f, 1.36f, 1.6f, a)
    }

    fun hide() { val tm = engine.transformManager; tm.setTransform(tm.getInstance(root), FloatArray(16).also { Matrix.setIdentityM(it, 0); Matrix.translateM(it, 0, -4000f, 0f, 0f) }) }

    fun destroy() {
        if (asset != null) { stage.scene.removeEntities(asset.entities); stage.assets.destroyAsset(asset) }
        else { stage.scene.removeEntity(cube); engine.destroyEntity(cube); EntityManager.get().destroy(cube) }
        engine.destroyMaterialInstance(mi)
    }

    companion object {
        /** The stencil numbers: each object outlined on the page marks its pixels with its own. */
        const val CART = 1; const val BOX = 2; const val MANUAL = 3

        private var mat: Material? = null; private var matFor: Stage? = null
        private fun material(stage: Stage): Material {
            if (matFor !== stage) { val b = stage.readAsset("materials/outline.filamat"); mat = Material.Builder().payload(b, b.remaining()).build(stage.engine); matFor = stage }
            return mat!!
        }

        /** A copy of a model (its .glb's bytes) for the glow. */
        fun of(stage: Stage, data: ByteBuffer, ref: Int): Outline? {
            val a = stage.assets.createAsset(data.duplicate()) ?: return null
            stage.resources.loadResources(a); a.releaseSourceData()
            return Outline(stage, ref, a)
        }
        /** A plain box for the glow (set its size with `size`). */
        fun box(stage: Stage, ref: Int) = Outline(stage, ref, null)

        /** Makes these entities (and everything under them) mark their pixels with `ref`, or stop marking (`ref` 0). */
        fun mark(stage: Stage, entities: IntArray, ref: Int) {
            val rm = stage.engine.renderableManager; val tm = stage.engine.transformManager
            val all = ArrayList<Int>()
            fun walk(e: Int) { all += e; val ti = tm.getInstance(e); if (ti != 0) { val kids = IntArray(tm.getChildCount(ti)); tm.getChildren(ti, kids); kids.forEach { walk(it) } } }
            entities.forEach { walk(it) }
            for (e in all) {
                val i = rm.getInstance(e); if (i == 0) continue
                for (p in 0 until rm.getPrimitiveCount(i)) rm.getMaterialInstanceAt(i, p).apply {
                    setStencilWrite(ref != 0)
                    setStencilCompareFunction(TextureSampler.CompareFunction.ALWAYS)
                    setStencilOpDepthStencilPass(MaterialInstance.StencilOperation.REPLACE)
                    setStencilReferenceValue(ref)
                }
            }
        }

        private fun cubeVb(engine: com.google.android.filament.Engine): VertexBuffer {
            val vb = VertexBuffer.Builder().bufferCount(1).vertexCount(8)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12).build(engine)
            val b = ByteBuffer.allocateDirect(8 * 12).order(ByteOrder.nativeOrder())
            for (z in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) for (x in listOf(-1f, 1f)) { b.putFloat(x); b.putFloat(y); b.putFloat(z) }
            b.flip(); vb.setBufferAt(engine, 0, b); return vb
        }
        private fun cubeIb(engine: com.google.android.filament.Engine): IndexBuffer {
            // vertex n is at x = n & 1, y = n >> 1 & 1, z = n >> 2 (0 for -1); faces wound outward
            val idx = shortArrayOf(0, 2, 1, 1, 2, 3,  4, 5, 6, 5, 7, 6,  0, 1, 4, 1, 5, 4,  2, 6, 3, 3, 6, 7,  0, 4, 2, 2, 4, 6,  1, 3, 5, 3, 7, 5)
            val ib = IndexBuffer.Builder().indexCount(idx.size).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
            val b = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()); idx.forEach { b.putShort(it) }; b.flip(); ib.setBuffer(engine, b)
            return ib
        }
    }
}
