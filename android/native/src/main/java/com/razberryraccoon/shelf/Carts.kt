package com.razberryraccoon.shelf

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.Matrix
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.FilamentAsset
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.pow

/** One of a cartridge's real shell colors (tools/cartridge-studio/cartridges.js, via cartridges.json). */
data class Variant(val name: String, val color: Int, val games: String, val clear: String?, val sheen: Boolean)

/** A cartridge style: its size in millimetres, label window, finish and color variants. */
class CartStyle(val sys: String, j: JSONObject) {
    val name: String = j.getString("name")
    val w = j.getJSONArray("size").getDouble(0).toFloat()
    val h = j.getJSONArray("size").getDouble(1).toFloat()
    val labelAspect = j.getJSONArray("label").let { (it.getDouble(2) / it.getDouble(3)).toFloat() }
    val roughness = j.getJSONObject("finish").getDouble("roughness").toFloat()
    val clearcoat = j.getJSONObject("finish").getDouble("clearcoat").toFloat()
    val clearcoatRoughness = j.getJSONObject("finish").getDouble("clearcoatRoughness").toFloat()
    val variants: List<Variant> = j.getJSONArray("variants").let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { v ->
            Variant(v.getString("name"), Color.parseColor(v.getString("color")), v.optString("games"),
                v.optString("clear").ifEmpty { null }, v.optBoolean("sheen")) } }
    }
    fun variant(name: String?) = variants.firstOrNull { it.name == name } ?: variants[0]
}

/** The exported Studio models, loaded once and copied for each cartridge on screen. */
class Carts(private val stage: Stage) {
    val styles: Map<String, CartStyle>
    private val files = HashMap<String, ByteBuffer>()
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)

    init {
        val j = JSONObject(String(stage.readAsset("models/cartridges.json").let { b -> ByteArray(b.remaining()).also { b.get(it) } }))
        styles = j.keys().asSequence().associateWith { CartStyle(it, j.getJSONObject(it)) }
    }

    private fun file(name: String) = files.getOrPut(name) { stage.readAsset("models/$name") }

    /** A cartridge for this game, in its chosen shell color, with a label drawn for it. */
    fun make(sys: String, title: String, variantName: String?): Cart? {
        val style = styles[sys] ?: return null
        val v = style.variant(variantName)
        val name = if (v.clear != null) "cart-$sys-clear.glb" else "cart-$sys.glb"
        val asset = stage.assets.createAsset(file(name).duplicate()) ?: return null
        stage.resources.loadResources(asset)
        asset.releaseSourceData()
        val cart = Cart(stage, asset, style, sampler, labelBitmap(style, title))
        cart.apply(v)
        stage.scene.addEntities(asset.entities)
        return cart
    }

    /** Until art downloads come to the native app: Shelf's own label, sized to this cartridge's label window. */
    private fun labelBitmap(style: CartStyle, title: String) =
        Paintings.label(512, (512 / style.labelAspect).toInt().coerceIn(128, 1024), title)
}

/** One cartridge in the scene: where it is, and its shell color. */
class Cart(private val stage: Stage, val asset: FilamentAsset, val style: CartStyle, sampler: TextureSampler, label: Bitmap) {
    private val rm = stage.engine.renderableManager
    private val tm = stage.engine.transformManager
    private val plastic: MaterialInstance? = materialOf("body")
    private val labelMat: MaterialInstance? = materialOf("label")
    // GEN_MIPMAPPABLE: Filament refuses to make the smaller, sharper copies (mipmaps) of a texture without it, and stops the app
    private val labelTex: Texture = Texture.Builder().width(label.width).height(label.height).levels(32 - Integer.numberOfLeadingZeros(maxOf(label.width, label.height)))
        .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(stage.engine)
    var variant: Variant = style.variants[0]; private set
    private var base = floatArrayOf(1f, 1f, 1f)
    private var bright = 1f
    private val m = FloatArray(16)

    init {
        // Filament reads a texture from the bottom row up, so the label goes in flipped, or it shows upside down
        val upright = Bitmap.createBitmap(label, 0, 0, label.width, label.height, android.graphics.Matrix().apply { preScale(1f, -1f) }, false)
        TextureHelper.setBitmap(stage.engine, labelTex, 0, upright)
        if (upright !== label) upright.recycle()
        labelTex.generateMipmaps(stage.engine)
        label.recycle()
        labelMat?.takeIf { m -> m.material.parameters.any { it.name == "baseColorMap" && it.type == Material.Parameter.Type.SAMPLER_2D } }?.setParameter("baseColorMap", labelTex, sampler)
    }

    private fun materialOf(name: String): MaterialInstance? {
        val e = asset.getFirstEntityByName(name); if (e == 0) return null
        val r = rm.getInstance(e); return if (r == 0) null else rm.getMaterialInstanceAt(r, 0)
    }
    /** Sets a value only if the material has it, with exactly that many numbers; Filament stops the app on a mismatch. */
    private fun MaterialInstance.set(name: String, vararg v: Float) {
        val type = material.parameters.firstOrNull { it.name == name }?.type ?: return
        when {
            v.size == 1 && type == Material.Parameter.Type.FLOAT -> setParameter(name, v[0])
            v.size == 3 && type == Material.Parameter.Type.FLOAT3 -> setParameter(name, v[0], v[1], v[2])
            v.size == 4 && type == Material.Parameter.Type.FLOAT4 -> setParameter(name, v[0], v[1], v[2], v[3])
        }
    }

    /** The Studio's rules (cartridge.js, applyVariant): metallic gold and silver, see-through clear and frosted shells. */
    fun apply(v: Variant) {
        variant = v
        val lin = intArrayOf(Color.red(v.color), Color.green(v.color), Color.blue(v.color)).map { srgbToLinear(it / 255f) }.toFloatArray()
        base = when {
            v.clear != null -> FloatArray(3) { lin[it] + (1 - lin[it]) * .15f }   // the Studio uses .35; on the device that read as milky
            // metal's color is what it reflects: measured gold and silver (Filament's metal table), not the paint swatch
            v.sheen -> if (Color.red(v.color) - Color.blue(v.color) > 60) floatArrayOf(1f, .766f, .336f) else floatArrayOf(.972f, .96f, .915f)
            else -> lin
        }
        val mi = plastic ?: return
        val r = style.roughness
        mi.set("roughnessFactor", when (v.clear) { "transparent" -> .06f; "frosted" -> maxOf(.4f, r); else -> if (v.sheen) .28f else r })
        mi.set("metallicFactor", if (v.sheen) 1f else 0f)
        mi.set("clearCoatFactor", if (v.sheen) .5f else style.clearcoat)
        mi.set("clearCoatRoughnessFactor", if (v.clear == "transparent") .05f else style.clearcoatRoughness)
        if (v.clear != null) { mi.set("attenuationColor", lin[0], lin[1], lin[2]); mi.set("attenuationDistance", if (v.clear == "transparent") 30f else 10f) }
        tint()
    }

    /** Dims the cartridges you're not on, a little. */
    fun brightness(k: Float) { if (k != bright) { bright = k; tint() } }
    private fun tint() {
        plastic?.set("baseColorFactor", base[0] * bright, base[1] * bright, base[2] * bright, 1f)
        labelMat?.set("baseColorFactor", bright, bright, bright, 1f)
    }

    /** Places it: (x, y) in layout pixels from the top left, z toward you, turned by ry and rx, `px` tall at its largest. */
    fun place(x: Float, y: Float, z: Float, ry: Float, rx: Float, px: Float) {
        val s = px / maxOf(style.w, style.h)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, Stage.H - y, z)
        Matrix.rotateM(m, 0, Math.toDegrees(ry.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(m, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.scaleM(m, 0, s, s, s)
        tm.setTransform(tm.getInstance(asset.root), m)
    }

    fun destroy() {
        stage.scene.removeEntities(asset.entities)
        stage.assets.destroyAsset(asset)
        stage.engine.destroyTexture(labelTex)
    }

    private fun srgbToLinear(c: Float) = if (c <= .04045f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f)
}
