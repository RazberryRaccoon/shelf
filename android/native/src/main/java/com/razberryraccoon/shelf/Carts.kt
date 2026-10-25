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
import kotlin.math.sin
import kotlin.math.PI

/** One of a cartridge's real shell colors (tools/cartridge-studio/cartridges.js, via cartridges.json). */
data class Variant(val name: String, val color: Int, val games: String, val clear: String?, val sheen: Boolean,
                   val material: String? = null, val custom: String? = null) {
    /** What a game saves to remember it: the default's name, or "custom:<id>" for a Studio shell. */
    val key get() = custom?.let { Shell.KEY + it } ?: name
}

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
    val depth = j.optDouble("depth", 10.0).toFloat()
    /** Where it comes apart (Game settings): its board's extent (min x, y, z, max x, y, z, in millimetres), if it has one. */
    val board: FloatArray? = j.optJSONObject("apart")?.optJSONArray("board")?.let { a -> FloatArray(6) { a.getDouble(it).toFloat() } }
}

/** The exported Studio models, loaded once and copied for each cartridge on screen. */
class Carts(private val stage: Stage, val shells: Shells) {
    val styles: Map<String, CartStyle>
    private val files = HashMap<String, ByteBuffer>()
    private val boards = HashMap<String, Texture>()   // each style's circuit-board print, drawn once and shared
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)

    init {
        val j = JSONObject(String(stage.readAsset("models/cartridges.json").let { b -> ByteArray(b.remaining()).also { b.get(it) } }))
        styles = j.keys().asSequence().associateWith { CartStyle(it, j.getJSONObject(it)) }
    }

    /** Any exported model (a console or a case), added to the scene; null if the file is missing. */
    fun model(name: String): FilamentAsset? {
        val data = try { file(name) } catch (e: java.io.IOException) { return null }
        val asset = stage.assets.createAsset(data.duplicate()) ?: return null
        stage.resources.loadResources(asset); asset.releaseSourceData()
        stage.scene.addEntities(asset.entities)
        return asset
    }

    /** A model file's glTF description (its materials' own colors, for parts a console scheme leaves alone). */
    fun description(name: String): JSONObject? = try {
        val b = file(name).duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN); val len = b.getInt(12)
        val bytes = ByteArray(len); b.position(20); b.get(bytes); JSONObject(String(bytes, Charsets.UTF_8))
    } catch (e: Exception) { null }

    private fun file(name: String) = files.getOrPut(name) { stage.readAsset("models/$name").let { if (name.endsWith("-clear.glb")) seeThrough(it) else it } }


    /** A cartridge for this game, in its chosen shell color, with a label drawn for it. */
    fun make(sys: String, title: String, variantName: String?): Cart? = variantFor(sys, variantName)?.let { make(sys, title, it) }

    /** A cartridge in exactly this shell, saved or not (the Studio's shell while you're still making it). */
    fun make(sys: String, title: String, v: Variant): Cart? {
        val style = styles[sys] ?: return null
        // the see-through model if the shell is see-through; if that file is ever missing, the solid one rather than an error
        val data = (if (v.clear != null) try { file("cart-$sys-clear.glb") } catch (e: java.io.IOException) { null } else null)
            ?: try { file("cart-$sys.glb") } catch (e: java.io.IOException) { return null }
        val asset = stage.assets.createAsset(data.duplicate()) ?: return null
        stage.resources.loadResources(asset)
        asset.releaseSourceData()
        val cart = Cart(stage, asset, style, sampler, labelBitmap(style, title), boardFor(style))
        cart.apply(v)
        stage.scene.addEntities(asset.entities)
        return cart
    }

    /**
     * The circuit board's printed back, drawn by the app: pictures embedded in the exported models don't show on the
     * device (the print was there in the Studio's renders but never on the Retroid), so it's put on here, as labels are.
     */
    private fun boardFor(style: CartStyle): Texture = boards.getOrPut(style.sys) {
        val b = Paintings.board((style.w * .78f) / (style.h * .72f), BOARD_CODES[style.sys] ?: "RT-01", maxOf(8, Math.round(style.w * .78f / 2.6f)))
        pictureTexture(stage, b).also { b.recycle() }
    }

    /** Until art downloads come to the native app: Shelf's own label, sized to this cartridge's label window. */
    private fun labelBitmap(style: CartStyle, title: String) =
        Paintings.label(512, (512 / style.labelAspect).toInt().coerceIn(128, 1024), title)
}

/** One cartridge in the scene: where it is, and its shell color. */
class Cart(private val stage: Stage, val asset: FilamentAsset, val style: CartStyle, sampler: TextureSampler, label: Bitmap, board: Texture? = null) {
    private val rm = stage.engine.renderableManager
    private val tm = stage.engine.transformManager
    private val plastic: MaterialInstance? = materialOf("body")
    // the shell is in two halves now (taken apart in Game settings): every piece of it takes the same color
    private val plastics: List<MaterialInstance> = asset.getEntitiesByName("body").toList().mapNotNull { e -> rm.getInstance(e).takeIf { it != 0 }?.let { rm.getMaterialInstanceAt(it, 0) } }.distinct().ifEmpty { listOfNotNull(plastic) }
    private fun body(name: String, vararg v: Float) = plastics.forEach { it.set(name, *v) }
    private val labelMat: MaterialInstance? = materialOf("label")
    // GEN_MIPMAPPABLE: Filament refuses to make the smaller, sharper copies (mipmaps) of a texture without it, and stops the app
    private val labelTex: Texture = Texture.Builder().width(label.width).height(label.height).levels(32 - Integer.numberOfLeadingZeros(maxOf(label.width, label.height)))
        .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
        .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(stage.engine)
    var variant: Variant = style.variants[0]; private set
    private var base = floatArrayOf(1f, 1f, 1f)
    private var bright = 1f
    private var alpha = 1f   // how much of the shell you see, for clear and frosted colors
    private val m = FloatArray(16)
    /** Its last placement (from place). */
    val placed: FloatArray get() = m
    /** Where it is now, however it was put there (place or placeMatrix). */
    fun current(): FloatArray = FloatArray(16).also { tm.getTransform(tm.getInstance(asset.root), it) }

    init {
        // Filament reads a texture from the bottom row up, so the label goes in flipped, or it shows upside down
        val upright = Bitmap.createBitmap(label, 0, 0, label.width, label.height, android.graphics.Matrix().apply { preScale(1f, -1f) }, false)
        TextureHelper.setBitmap(stage.engine, labelTex, 0, upright)
        if (upright !== label) upright.recycle()
        labelTex.generateMipmaps(stage.engine)
        label.recycle()
        labelMat?.takeIf { m -> m.material.parameters.any { it.name == "baseColorMap" && it.type == Material.Parameter.Type.SAMPLER_2D } }?.setParameter("baseColorMap", labelTex, sampler)
        // the board's printed back: the face whose material is named pcb-print (the box's sixth face, -z, if names are missing)
        val pcb = asset.getFirstEntityByName("pcb").takeIf { it != 0 }?.let { rm.getInstance(it) }?.takeIf { it != 0 }
        if (board != null && pcb != null) {
            val n = rm.getPrimitiveCount(pcb)
            val k = (0 until n).firstOrNull { rm.getMaterialInstanceAt(pcb, it).name == "pcb-print" } ?: if (n == 6) 5 else -1
            if (k >= 0) rm.getMaterialInstanceAt(pcb, k).takeIf { m -> m.material.parameters.any { it.name == "baseColorMap" && it.type == Material.Parameter.Type.SAMPLER_2D } }?.setParameter("baseColorMap", board, sampler)
        }
    }

    private fun materialOf(name: String): MaterialInstance? {
        val e = asset.getFirstEntityByName(name); if (e == 0) return null
        val r = rm.getInstance(e); return if (r == 0) null else rm.getMaterialInstanceAt(r, 0)
    }

    /** The Studio's rules (cartridge.js, applyVariant): metallic gold and silver, see-through clear and frosted shells. */
    fun apply(v: Variant) {
        variant = v
        body("emissiveFactor", 0f, 0f, 0f); body("sheenColorFactor", 0f, 0f, 0f)
        if (v.material != null) return applyMaterial(v, Materials.of(v.material))
        val lin = intArrayOf(Color.red(v.color), Color.green(v.color), Color.blue(v.color)).map { srgbToLinear(it / 255f) }.toFloatArray()
        base = when {
            v.clear != null -> lin   // see-through now (seeThrough), so no white mixed in
            // metal's color is what it reflects: measured gold and silver (Filament's metal table), not the paint swatch
            v.sheen -> if (Color.red(v.color) - Color.blue(v.color) > 60) floatArrayOf(1f, .766f, .336f) else floatArrayOf(.972f, .96f, .915f)
            else -> lin
        }
        if (plastics.isEmpty()) return
        val r = style.roughness
        body("roughnessFactor", when (v.clear) { "transparent" -> .06f; "frosted" -> maxOf(.4f, r); else -> if (v.sheen) .28f else r })
        body("metallicFactor", if (v.sheen) 1f else 0f)
        body("clearCoatFactor", if (v.sheen) .5f else style.clearcoat)
        body("clearCoatRoughnessFactor", if (v.clear == "transparent") .05f else style.clearcoatRoughness)
        alpha = when (v.clear) { "transparent" -> .38f; "frosted" -> .7f; else -> 1f }
        body("emissiveFactor", 0f, 0f, 0f)
        tint()
    }

    /** A Studio shell: your color in one of the Studio's materials. */
    private fun applyMaterial(v: Variant, m: Finish) {
        val lin = intArrayOf(Color.red(v.color), Color.green(v.color), Color.blue(v.color)).map { srgbToLinear(it / 255f) }.toFloatArray()
        base = if (m.metallic > 0) metalColor(lin, v.color, m) else FloatArray(3) { (lin[it] + (1 - lin[it]) * m.lift) * m.dim }
        alpha = m.alpha
        if (plastics.isEmpty()) return tint()
        body("roughnessFactor", m.rough ?: if (m.id == "frosted") maxOf(.4f, style.roughness) else style.roughness)
        body("metallicFactor", m.metallic)
        body("clearCoatFactor", m.coat ?: style.clearcoat)
        body("clearCoatRoughnessFactor", m.coatRough ?: style.clearcoatRoughness)
        if (m.glow > 0) body("emissiveFactor", base[0] * m.glow, base[1] * m.glow, base[2] * m.glow)
        if (m.sheen > 0) { body("sheenColorFactor", m.sheen, m.sheen, m.sheen); body("sheenRoughnessFactor", .35f) }
        tint()
    }

    /** Dims the cartridges you're not on, a little. */
    fun brightness(k: Float) { if (k != bright) { bright = k; tint() } }
    private fun tint() {
        body("baseColorFactor", base[0] * bright, base[1] * bright, base[2] * bright, alpha)
        labelMat?.set("baseColorFactor", bright, bright, bright, 1f)
    }

    // ---------- taken apart (Game settings) ----------

    private val parts = listOf("back", "front", "board").map { asset.getFirstEntityByName(it) }
    /** Whether it can be taken apart: a cartridge with halves and a board (not a card or a disc). */
    val canOpen get() = style.board != null && parts.all { it != 0 }

    /**
     * Taken apart on a surface, 0 whole to 1 apart, quickly and in order: the halves separate, slide aside and lie down
     * (the front turned over, so both show their insides), then the board lifts up and out and is set down below them.
     * The surface is the plane of the cartridge's back; the page tilts the whole thing to look down on it.
     */
    fun teardown(k: Float) {
        if (!canOpen) return
        val w = style.w; val h = style.h; val d = style.depth; val b = style.board ?: return
        fun e(v: Float) = Backdrop.ease(v.coerceIn(0f, 1f))
        val s1 = e(k / .3f); val s2 = e((k - .2f) / .4f); val s3 = e((k - .45f) / .25f); val s4 = e((k - .65f) / .3f)
        val m = FloatArray(16)
        // the back half: aside to the left and up a little, lying as it is
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, -.58f * w * s2, .18f * h * s2, 0f)
        tm.setTransform(tm.getInstance(parts[0]), m)
        // the front half: lifts off, then turns over as it goes to the right, and lies down with its inside up
        val lift = d * 1.2f * s1 * (1 - s2) + sin(PI.toFloat() * s2) * w * .22f
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, .58f * w * s2, .18f * h * s2, lift)
        Matrix.rotateM(m, 0, 180f * s2, 0f, 1f, 0f)
        tm.setTransform(tm.getInstance(parts[1]), m)
        // the board: up and out of the back half, then down below the halves, lying on the surface
        val cx = (b[0] + b[3]) / 2; val cy = (b[1] + b[4]) / 2
        val down = -d / 2 - b[2]   // its underside onto the surface
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, -cx * s4, (-.7f * h - cy) * s4, d * 1.6f * s3 * (1 - s4) + down * s4)
        tm.setTransform(tm.getInstance(parts[2]), m)
    }
    /** Where the screwdriver lies beside the parts (its transform within the cartridge's), and how big, 0 to 1. */
    fun toolSpot(k: Float): FloatArray? {
        if (!canOpen) return null
        val s = Backdrop.ease(((k - .7f) / .3f).coerceIn(0f, 1f)).coerceAtLeast(.001f)
        return FloatArray(16).also { Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, 0f, -1.22f * style.h, -style.depth / 2 + 5f + (1 - s) * 18f)   // settling onto the surface as it appears
            Matrix.rotateM(it, 0, 8f, 0f, 0f, 1f); Matrix.scaleM(it, 0, s, s, s); Matrix.translateM(it, 0, -48f, 0f, 0f) }
    }

    /** Places it by a whole transform (a cartridge sitting in its case's tray). */
    fun placeMatrix(t: FloatArray) = tm.setTransform(tm.getInstance(asset.root), t)

    /**
     * Places it: (x, y) in layout pixels from the top left, z toward you, turned by ry and rx, `px` tall at its largest.
     * `held`: tip it toward you around the screen's own horizontal axis (as when you turn something in your hands)
     * rather than its own, so it turns the way the stick points from any side.
     */
    fun place(x: Float, y: Float, z: Float, ry: Float, rx: Float, px: Float, held: Boolean = false) {
        val s = px / maxOf(style.w, style.h)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, Stage.H - y, z)
        if (held) Matrix.rotateM(m, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(m, 0, Math.toDegrees(ry.toDouble()).toFloat(), 0f, 1f, 0f)
        if (!held) Matrix.rotateM(m, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.scaleM(m, 0, s, s, s)
        tm.setTransform(tm.getInstance(asset.root), m)
    }

    fun destroy() {
        stage.scene.removeEntities(asset.entities)
        stage.assets.destroyAsset(asset)
        stage.engine.destroyTexture(labelTex)
    }

}

/**
 * The Studio's clear shells use glTF transmission (light passing through, bent like real plastic). Filament's
 * built-in glTF materials draw that as solid color on this phone-class renderer, so the circuit board vanished.
 * Here the shell becomes an ordinary blended (see-through) material instead: the file's JSON is changed as it
 * loads, the shell keeps its clear coat for reflections, and Cart.apply sets how see-through each color is.
 */
fun seeThrough(glb: ByteBuffer): ByteBuffer {
    val all = ByteArray(glb.remaining()).also { glb.duplicate().get(it) }
    val b = ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val jsonLen = b.getInt(12)
    val j = JSONObject(String(all, 20, jsonLen, Charsets.UTF_8))
    val mats = j.optJSONArray("materials") ?: return glb
    for (k in 0 until mats.length()) {
        val m = mats.getJSONObject(k); val ext = m.optJSONObject("extensions") ?: continue
        if (!ext.has("KHR_materials_transmission")) continue
        ext.remove("KHR_materials_transmission"); ext.remove("KHR_materials_volume"); ext.remove("KHR_materials_ior")
        m.put("alphaMode", "BLEND")
    }
    j.optJSONArray("extensionsUsed")?.let { u -> val keep = org.json.JSONArray(); for (k in 0 until u.length()) u.getString(k).takeIf { it !in setOf("KHR_materials_transmission", "KHR_materials_volume", "KHR_materials_ior") }?.let { keep.put(it) }; j.put("extensionsUsed", keep) }
    var text = j.toString().toByteArray(Charsets.UTF_8)
    if (text.size % 4 != 0) text += ByteArray(4 - text.size % 4) { ' '.code.toByte() }   // chunks are padded to 4 bytes with spaces
    val rest = all.size - 20 - jsonLen   // the binary chunk, untouched
    val out = ByteBuffer.allocateDirect(20 + text.size + rest).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    out.putInt(0x46546C67).putInt(2).putInt(20 + text.size + rest)
    out.putInt(text.size).putInt(0x4E4F534A).put(text)
    out.put(all, 20 + jsonLen, rest)
    out.flip(); return out
}

/** RaccoonTek's own part numbers for each board (the Studio's BOARD_CODES; invented, no console maker's names). */
private val BOARD_CODES = mapOf("nes" to "RT-8B-01", "snes" to "RT-16S-02", "n64" to "RT-64X-03", "genesis" to "RT-16G-04",
    "gb" to "RT-PK1-05", "gbc" to "RT-PK2-06", "gba" to "RT-PK3-07", "ds" to "RT-DS-08", "psp" to "RT-UM-09", "android" to "RT-AC-10")

    /** Sets a value only if the material has it, with exactly that many numbers; Filament stops the app on a mismatch. */
fun MaterialInstance.set(name: String, vararg v: Float) {
        val type = material.parameters.firstOrNull { it.name == name }?.type ?: return
        when {
            v.size == 1 && type == Material.Parameter.Type.FLOAT -> setParameter(name, v[0])
            v.size == 3 && type == Material.Parameter.Type.FLOAT3 -> setParameter(name, v[0], v[1], v[2])
            v.size == 4 && type == Material.Parameter.Type.FLOAT4 -> setParameter(name, v[0], v[1], v[2], v[3])
        }
    }

/**
 * A metal's color is how much light it reflects, and real metals reflect most of it: gold about (1, .77, .34),
 * silver .97. A paint swatch used as is ("Gold" .79, .53, .14; "Silver" about .55) made dim, nearly black metal. So
 * the swatch gives the metal its hue, and its brightness sets how much it reflects, from about a third (dark
 * metals, like a navy anodized) to nearly all of it.
 */
fun metalColor(lin: FloatArray, color: Int, m: Finish): FloatArray {
    val top = maxOf(lin[0], lin[1], lin[2], .001f)
    val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
    val k = (m.floor + (1 - m.floor) * hsv[2]) * m.dim   // from m.floor (dark metals) up to nearly all of it (bright ones)
    val metalPart = FloatArray(3) { (lin[it] / top * .8f + .2f) * k }   // real metals are paler than paint: a fifth of the way to white
    // part-metal finishes (metal flake, pearl) keep some of the plain color underneath
    return FloatArray(3) { (lin[it] + (metalPart[it] - lin[it]) * minOf(1f, m.metallic * 1.4f)).let { c -> c + (1 - c) * m.lift } }
}


fun srgbToLinear(c: Float) = if (c <= .04045f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f)
