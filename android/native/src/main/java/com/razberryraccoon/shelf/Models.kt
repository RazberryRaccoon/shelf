package com.razberryraccoon.shelf

import android.graphics.Bitmap
import android.opengl.Matrix
import com.google.android.filament.Material
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.FilamentAsset

/** A picture as a Filament texture, upright and with mipmaps (as the cartridge labels are made). */
fun pictureTexture(stage: Stage, b: Bitmap): Texture {
    // GEN_MIPMAPPABLE: Filament refuses to make the smaller, sharper copies (mipmaps) of a texture without it, and stops the app
    val t = Texture.Builder().width(b.width).height(b.height).levels(32 - Integer.numberOfLeadingZeros(maxOf(b.width, b.height)))
        .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(stage.engine)
    // Filament reads a texture from the bottom row up, so the picture goes in flipped, or it shows upside down
    val upright = Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { preScale(1f, -1f) }, false)
    TextureHelper.setBitmap(stage.engine, t, 0, upright)
    if (upright !== b) upright.recycle()
    t.generateMipmaps(stage.engine)
    return t
}

/**
 * A whole exported model on its own: a console or a case. It's placed like a cartridge (layout pixels, turned by ry
 * and rx, `px` across at its largest side), centred on its own bounding box, so any model can be shown the same way.
 */
open class Model(protected val stage: Stage, val asset: FilamentAsset) {
    protected val tm = stage.engine.transformManager
    protected val rm = stage.engine.renderableManager
    private val box = asset.boundingBox
    val center = box.center.copyOf()
    val half = box.halfExtent.copyOf()
    /** The last transform it was placed with (for things that sit on it, like a cartridge in a case's tray). */
    val matrix = FloatArray(16)
    /** The scale it was last placed at (layout pixels per millimetre). */
    var scale = 1f; private set

    fun place(x: Float, y: Float, z: Float, ry: Float, rx: Float, px: Float, held: Boolean = true) {
        val s = px / (2 * maxOf(half[0], half[1]).coerceAtLeast(.001f)); scale = s
        Matrix.setIdentityM(matrix, 0)
        Matrix.translateM(matrix, 0, x, Stage.H - y, z)
        if (held) Matrix.rotateM(matrix, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(matrix, 0, Math.toDegrees(ry.toDouble()).toFloat(), 0f, 1f, 0f)
        if (!held) Matrix.rotateM(matrix, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.scaleM(matrix, 0, s, s, s)
        Matrix.translateM(matrix, 0, -center[0], -center[1], -center[2])
        tm.setTransform(tm.getInstance(asset.root), matrix)
    }
    /** Out of sight, without unloading it. */
    fun hide() = place(-4000f, 0f, 0f, 0f, 0f, 1f)

    /** The material of a part by its name, if the model has it. */
    protected fun materialOf(name: String) = asset.getFirstEntityByName(name).takeIf { it != 0 }?.let { e ->
        rm.getInstance(e).takeIf { it != 0 }?.let { rm.getMaterialInstanceAt(it, 0) } }

    open fun destroy() { stage.scene.removeEntities(asset.entities); stage.assets.destroyAsset(asset) }
}

/**
 * A game's case (case.js in the Studio): its art on the front, back and spine, and a cover that opens on the spine
 * hinge. `open` 0 is closed, 1 lies open to the left with the tray showing.
 */
class Case(stage: Stage, asset: FilamentAsset, title: String, private val sampler: TextureSampler) : Model(stage, asset) {
    private val textures = ArrayList<Texture>()
    private val coverRest = FloatArray(16)
    private val cover = asset.getFirstEntityByName("cover")
    private val media = asset.getFirstEntityByName("media")
    var open = 0f; set(v) { field = v; hinge() }

    init {
        if (cover != 0) tm.getTransform(tm.getInstance(cover), coverRest)
        art("front", Paintings.label(512, 640, title))
        art("back", Paintings.picture(512, 640, "$title back"))
        art("spine", Paintings.picture(96, 640, title))
        hinge()
    }

    private fun art(part: String, b: Bitmap) {
        val mi = materialOf(part) ?: return b.recycle()
        if (mi.material.parameters.none { it.name == "baseColorMap" && it.type == Material.Parameter.Type.SAMPLER_2D }) return b.recycle()
        val t = pictureTexture(stage, b); b.recycle(); textures += t
        mi.setParameter("baseColorMap", t, sampler)
    }

    private fun hinge() {
        if (cover == 0) return
        val m = FloatArray(16)
        Matrix.rotateM(m.also { Matrix.setIdentityM(it, 0) }, 0, -180f * open * .97f, 0f, 1f, 0f)
        val out = FloatArray(16); Matrix.multiplyMM(out, 0, coverRest, 0, m, 0)
        tm.setTransform(tm.getInstance(cover), out)
    }

    /** Where a cartridge sits in the tray: the case's last placement, moved to the tray's centre (same millimetres). */
    fun mediaMatrix(): FloatArray {
        val local = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        if (media != 0) tm.getTransform(tm.getInstance(media), local)
        return FloatArray(16).also { Matrix.multiplyMM(it, 0, matrix, 0, local, 0) }
    }

    override fun destroy() { super.destroy(); textures.forEach { stage.engine.destroyTexture(it) } }
}
