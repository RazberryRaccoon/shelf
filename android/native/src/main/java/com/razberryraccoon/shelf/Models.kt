package com.razberryraccoon.shelf

import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.sqrt
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
    /** Places it by a whole transform. */
    fun placeMatrix(t: FloatArray) = tm.setTransform(tm.getInstance(asset.root), t)
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
class Case(stage: Stage, asset: FilamentAsset, title: String, private val sampler: TextureSampler, front: Bitmap? = null, back: Bitmap? = null, spine: Bitmap? = null) : Model(stage, asset) {
    private val textures = ArrayList<Texture>()
    private val coverRest = FloatArray(16)
    private val cover = asset.getFirstEntityByName("cover")
    private val media = asset.getFirstEntityByName("media")
    var open = 0f; set(v) { field = v; hinge() }
    // the manual: a booklet inside the cover (a jewel case's front booklet), which can be lifted a little or carried out
    private val manual = asset.getFirstEntityByName("manual")
    private val manualRest = FloatArray(16)
    private var manualParent = 0   // the entity it hangs from (the cover); it always stays there, see manualAt
    val hasManual get() = manual != 0
    /** A booklet without an inside face of its own (a case exported before 0.16.2). */
    val jewel get() = asset.getFirstEntityByName("manual-art") == 0
    /** The booklet's width and height, in millimetres: a jewel case's (its clear lid) is nearly the whole case. */
    val manualSize get() = if (asset.getFirstEntityByName("clear") != 0) floatArrayOf(2 * half[0] - 4, 2 * half[1] - 4) else floatArrayOf(2 * half[0] - 16, 2 * half[1] - 18)

    init {
        if (cover != 0) tm.getTransform(tm.getInstance(cover), coverRest)
        if (manual != 0) { val i = tm.getInstance(manual); tm.getTransform(i, manualRest); manualParent = tm.getParent(i) }   // an entity, not an instance
        // the front: the game's own box art if it's been downloaded (cropped to fill the box's real front, never
        // stretched), else Shelf's label painting at the front's shape
        val aspect = (half[0] / half[1]).coerceIn(.3f, 3f)
        // the manual's booklet wears the box art too, until the manual itself is downloaded (its first page then)
        if (manual != 0 && !jewel) manualSize.let { ms -> val a = (ms[0] / ms[1]).coerceIn(.3f, 3f)
            art("manual-art", front?.copy(Bitmap.Config.ARGB_8888, false)?.let { c -> fill(c, a).also { if (it !== c) c.recycle() } } ?: Paintings.label(400, (400 / a).toInt(), "$title manual")) }
        art("front", front?.let { f -> fill(f, aspect).also { if (it !== f) f.recycle() } } ?: Paintings.label(512, (512 / aspect).toInt(), title))
        art("back", back?.let { b -> fill(b, aspect).also { if (it !== b) b.recycle() } } ?: Paintings.picture(512, (512 / aspect).toInt(), "$title back"))
        // the spine is a narrow strip, as tall as the box; a downloaded spine is cropped to it the same way
        val sa = ((2 * half[2] - 2) / (2 * half[1] - 4)).coerceIn(.04f, .5f)
        art("spine", spine?.let { s0 -> upright(s0).let { b -> fill(b, sa).also { if (it !== b) b.recycle() } } } ?: Paintings.picture(96, 640, title))
        hinge()
    }

    /** A spine scanned lying down (wider than tall) stood up, turned a quarter clockwise so its text reads top to bottom. */
    private fun upright(b: Bitmap): Bitmap = if (b.width <= b.height) b else
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(90f) }, true).also { b.recycle() }

    /** A picture cropped to this shape (width / height), centred, so it fills it without stretching. */
    private fun fill(b: Bitmap, aspect: Float): Bitmap {
        val a = b.width.toFloat() / b.height
        val w = if (a > aspect) (b.height * aspect).toInt() else b.width; val h = if (a > aspect) b.height else (b.width / aspect).toInt()
        return Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w.coerceAtLeast(1), h.coerceAtLeast(1))
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

    /** The manual's own cover art (its PDF's first page), on a case whose booklet is its own. */
    fun manualArt(b: Bitmap) { if (jewel) b.recycle() else art("manual-art", b) }

    /** The manual back in its place in the cover, lifted toward you by `k` (0..1, a few millimetres) while it's chosen. */
    fun manualIn(k: Float) {
        if (manual == 0) return
        val m = FloatArray(16); Matrix.translateM(m, 0, manualRest, 0, 0f, 0f, if (jewel) 3f * k else -3f * k)   // toward you, out of the open cover
        tm.setTransform(tm.getInstance(manual), m)
    }
    /** The cover's place in the world (what the manual hangs from). */
    private fun parentWorld(): FloatArray = FloatArray(16).also {
        val pi = if (manualParent != 0) tm.getInstance(manualParent) else 0
        if (pi != 0) tm.getWorldTransform(pi, it) else Matrix.setIdentityM(it, 0)
    }
    /** Where the manual rests in the cover, in the world. */
    fun manualRestWorld(): FloatArray = FloatArray(16).also { Matrix.multiplyMM(it, 0, parentWorld(), 0, manualRest, 0) }
    /**
     * Carries the manual out of the case, to this place in the world. It stays attached to the cover (re-parenting in
     * Filament mixes entities and instances, and a wrong one stops the app outright), placed relative to it instead.
     */
    fun manualAt(world: FloatArray) {
        if (manual == 0) return
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, parentWorld(), 0)) return
        tm.setTransform(tm.getInstance(manual), FloatArray(16).also { Matrix.multiplyMM(it, 0, inv, 0, world, 0) })
    }

    /** Where a cartridge sits in the tray: the case's last placement, moved to the tray's centre (same millimetres). */
    fun mediaMatrix(): FloatArray {
        val local = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        if (media != 0) tm.getTransform(tm.getInstance(media), local)
        return FloatArray(16).also { Matrix.multiplyMM(it, 0, matrix, 0, local, 0) }
    }

    /**
     * Just over the tray, where a cartridge `h` tall and `d` deep waits before it goes in: up out of the well and a little
     * above it, so going down into it reads as slotting in (seen through the page's long lens, a lift toward you alone
     * hardly shows).
     */
    fun hoverMatrix(h: Float, d: Float): FloatArray = mediaMatrix().also { Matrix.translateM(it, 0, 0f, h * .2f + 10f, d * 2f + 12f) }

    override fun destroy() { super.destroy(); textures.forEach { stage.engine.destroyTexture(it) } }
}

/** Turning one placement into another smoothly. */
object Poses {
    /**
     * Between two placements: position and size in a straight line, the turn along the shortest way round (blending the
     * matrices themselves would squash the cartridge half way).
     */
    fun blend(a: FloatArray, b: FloatArray, k: Float): FloatArray {
        val sa = scaleOf(a); val sb = scaleOf(b)
        val qa = quat(a, sa); val qb = quat(b, sb)
        val q = slerp(qa, qb, k); val s = sa + (sb - sa) * k
        val out = rotation(q)
        for (i in 0..2) for (j in 0..2) out[i * 4 + j] *= s
        for (i in 12..14) out[i] = a[i] + (b[i] - a[i]) * k
        out[15] = 1f
        return out
    }
    private fun scaleOf(m: FloatArray) = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]).coerceAtLeast(1e-6f)
    /** A rotation (column-major, scale removed) as a quaternion (x, y, z, w). */
    private fun quat(m: FloatArray, s: Float): FloatArray {
        val m00 = m[0] / s; val m11 = m[5] / s; val m22 = m[10] / s
        val m21 = m[6] / s; val m12 = m[9] / s; val m02 = m[8] / s; val m20 = m[2] / s; val m10 = m[1] / s; val m01 = m[4] / s
        val tr = m00 + m11 + m22
        return when {
            tr > 0 -> { val r = sqrt(tr + 1f) * 2; floatArrayOf((m21 - m12) / r, (m02 - m20) / r, (m10 - m01) / r, .25f * r) }
            m00 > m11 && m00 > m22 -> { val r = sqrt(1f + m00 - m11 - m22) * 2; floatArrayOf(.25f * r, (m01 + m10) / r, (m02 + m20) / r, (m21 - m12) / r) }
            m11 > m22 -> { val r = sqrt(1f + m11 - m00 - m22) * 2; floatArrayOf((m01 + m10) / r, .25f * r, (m12 + m21) / r, (m02 - m20) / r) }
            else -> { val r = sqrt(1f + m22 - m00 - m11) * 2; floatArrayOf((m02 + m20) / r, (m12 + m21) / r, .25f * r, (m10 - m01) / r) }
        }
    }
    private fun slerp(a: FloatArray, b0: FloatArray, k: Float): FloatArray {
        var b = b0; var d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        if (d < 0) { b = FloatArray(4) { -b0[it] }; d = -d }
        if (d > .9995f) return FloatArray(4) { a[it] + (b[it] - a[it]) * k }.let { q -> val l = sqrt(q.sumOf { (it * it).toDouble() }).toFloat(); FloatArray(4) { q[it] / l } }
        val th = acos(d.coerceIn(-1f, 1f)); val s = sin(th)
        val wa = sin((1 - k) * th) / s; val wb = sin(k * th) / s
        return FloatArray(4) { a[it] * wa + b[it] * wb }
    }
    /** A quaternion as a column-major rotation matrix. */
    private fun rotation(q: FloatArray): FloatArray {
        val (x, y, z, w) = q
        return floatArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
            0f, 0f, 0f, 1f)
    }
}

/**
 * How a game goes into its box, as one line of steps, the same forwards and backwards (the game page and the Museum):
 * 0, the cartridge on its own and the box off to the left; 1, the open box slid in from the left and the cartridge
 * hovering over its tray; 2, the cartridge lowered into the tray (the open box); 3, the cover shut (the closed box).
 * Going from one view to another passes through every step between, each eased to a stop (so the cartridge distinctly
 * slots in), so the cartridge to the closed box slides the box in, slots the cartridge, then shuts the cover.
 */
class BoxFlow {
    var s = 0f; private set
    fun snap(v: Float) { s = v }
    /** Moves toward `target` at each step's own pace: the box sliding in .6 s, the cartridge going down .32 s, the cover .45 s. */
    fun tick(dt: Float, target: Float, speed: Float = 1f) {
        var left = dt * speed
        while (left > 0f && kotlin.math.abs(target - s) > 1e-4f) {
            val up = target > s
            val step = (if (up) kotlin.math.floor(s) else kotlin.math.ceil(s) - 1).toInt().coerceIn(0, 2)
            val edge = if (up) minOf(target, step + 1f) else maxOf(target, step.toFloat())
            val need = kotlin.math.abs(edge - s) * DUR[step]
            if (left >= need) { s = edge; left -= need } else { s += (if (up) 1 else -1) * left / DUR[step]; left = 0f }
        }
    }
    private fun part(a: Float) = Backdrop.ease((s - a).coerceIn(0f, 1f))
    /** The box in from the left (0 off, 1 in place); the cartridge from where it floats to over the tray, by the same. */
    val boxIn get() = part(0f)
    /** The cartridge from over the tray down into it. */
    val down get() = part(1f)
    /** The cover, 0 open to 1 shut. */
    val closed get() = part(2f)
    private companion object { val DUR = floatArrayOf(.6f, .32f, .45f) }
}
