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

    /** Gives a cartridge's label this finish (if it hasn't it already), its picture drawn again for it. */
    fun refinish(c: Cart?, title: String, f: Int) { if (c != null && c.finish != f) c.finish(f, labelBitmap(c.style, title)) }

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

    /** Its label's finish (Labels): standard, matte paper, glossy, worn, holo, opalescent or foil. */
    var finish = Labels.STANDARD; private set

    /** Puts a label picture on (as big as the one it was made with): Filament reads from the bottom row up, so flipped. */
    private fun upload(label: Bitmap) {
        val fit = if (label.width == labelTex.getWidth(0) && label.height == labelTex.getHeight(0)) label else Bitmap.createScaledBitmap(label, labelTex.getWidth(0), labelTex.getHeight(0), true)
        val upright = Bitmap.createBitmap(fit, 0, 0, fit.width, fit.height, android.graphics.Matrix().apply { preScale(1f, -1f) }, false)
        TextureHelper.setBitmap(stage.engine, labelTex, 0, upright)
        if (upright !== fit) upright.recycle()
        if (fit !== label) fit.recycle()
        labelTex.generateMipmaps(stage.engine)
        label.recycle()
    }

    /**
     * Gives the label a finish: its picture (drawn again with the finish's wear or sheen baked in) and the paper's
     * material. Holo and opalescent also shimmer as the cartridge turns (see place).
     */
    fun finish(f: Int, plain: Bitmap) {
        finish = f
        upload(Labels.bake(plain, f, style.sys.hashCode()))
        val m = labelMat ?: return
        val l = Labels.LOOKS[f]
        m.set("roughnessFactor", l[0]); m.set("metallicFactor", l[1]); m.set("clearCoatFactor", l[2]); m.set("clearCoatRoughnessFactor", l[3])
        if (l[4] == 0f) m.set("sheenColorFactor", 0f, 0f, 0f)
    }

    init {
        upload(label)
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
     * Taken apart on a surface, 0 whole to 1 apart, one step after another so nothing passes through anything: the
     * front half lifts straight off, the halves move well apart (the front turning over on the way, so both show their
     * insides; the board still in the back half), the front settles onto the surface, then the board rises up out of
     * the back half, moves to its own place below the halves and is set down. The surface is the plane of the
     * cartridge's back; the page tilts the whole thing to look down on it.
     */
    fun teardown(k: Float) {
        if (!canOpen) return
        val w = style.w; val h = style.h; val d = style.depth; val b = style.board ?: return
        fun e(a: Float, b: Float) = Backdrop.ease(((k - a) / (b - a)).coerceIn(0f, 1f))
        val lift = e(0f, .2f); val apart = e(.2f, .48f); val settle = e(.48f, .6f); val up = e(.6f, .74f); val away = e(.74f, .94f)
        val m = FloatArray(16)
        val bx = -SPREAD * w * apart   // the back half (and the board in it) to the left
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, bx, ROW * h * apart, 0f)
        tm.setTransform(tm.getInstance(parts[0]), m)
        // the front half: straight up off the back half, across to the right turning over, then down onto the surface
        val high = d * 2.4f + w * .18f
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, SPREAD * w * apart, ROW * h * apart, high * lift * (1 - settle))
        Matrix.rotateM(m, 0, 180f * apart, 0f, 1f, 0f)
        tm.setTransform(tm.getInstance(parts[1]), m)
        // the board: with the back half, then up out of it, across to its place below, and down onto the surface
        val cx = (b[0] + b[3]) / 2; val cy = (b[1] + b[4]) / 2
        val down = -d / 2 - b[2]   // its underside onto the surface
        val rise = d * 2.6f
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, bx * (1 - away) - cx * away, ROW * h * apart * (1 - away) + (BOARD_Y * h - cy) * away, rise * up * (1 - away) + down * away)
        tm.setTransform(tm.getInstance(parts[2]), m)
    }
    /**
     * Taken apart like an exploded drawing (the Museum's Cartridge Inside), 0 whole to 1 apart: the front half floats off
     * toward you and the back half away, straight out from the board, which stays where it was. `turn`, when given, turns
     * the board on its own about its centre (a rotation in the cartridge's own frame: the stick turns only the board),
     * and `extra` moves the halves further off while a turned board needs the room.
     */
    fun inside(k: Float, turn: FloatArray? = null, extra: Float = 0f) {
        if (!canOpen) return
        val b = style.board ?: return
        val off = k * (style.depth * 1.4f + 24f + extra)
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, 0f, 0f, -off); tm.setTransform(tm.getInstance(parts[0]), m)
        Matrix.setIdentityM(m, 0); Matrix.translateM(m, 0, 0f, 0f, off); tm.setTransform(tm.getInstance(parts[1]), m)
        Matrix.setIdentityM(m, 0)
        if (turn != null) {
            val cx = (b[0] + b[3]) / 2; val cy = (b[1] + b[4]) / 2; val cz = (b[2] + b[5]) / 2
            Matrix.translateM(m, 0, cx, cy, cz)
            val t = FloatArray(16); Matrix.multiplyMM(t, 0, m, 0, turn, 0); t.copyInto(m)
            Matrix.translateM(m, 0, -cx, -cy, -cz)
        }
        tm.setTransform(tm.getInstance(parts[2]), m)
    }
    /** The board's half sizes (mm), for working out how far a turned board reaches. */
    val boardHalf: FloatArray? get() = style.board?.let { b -> floatArrayOf((b[3] - b[0]) / 2, (b[4] - b[1]) / 2, (b[5] - b[2]) / 2) }

    /** Where the screwdriver lies beside the parts (its transform within the cartridge's), and how big, 0 to 1. */
    fun toolSpot(k: Float): FloatArray? {
        if (!canOpen) return null
        val s = Backdrop.ease(((k - .86f) / .14f).coerceIn(0f, 1f)).coerceAtLeast(.001f)
        return FloatArray(16).also { Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, 0f, TOOL_Y * style.h - 14f, -style.depth / 2 + 5f + (1 - s) * 18f)   // settling onto the surface as it appears
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
        if (Labels.LOOKS[finish][4] > 0f) shimmer(ry + rx * .7f)
    }

    /** Holo and opalescent: the sheen's colour follows how the label faces you, through the rainbow (or pastels). */
    private fun shimmer(a: Float) {
        val l = Labels.LOOKS[finish]
        val c = android.graphics.Color.HSVToColor(floatArrayOf(((a * 140f) % 360f + 360f) % 360f, l[5], 1f))
        labelMat?.set("sheenColorFactor", android.graphics.Color.red(c) / 255f * l[4], android.graphics.Color.green(c) / 255f * l[4], android.graphics.Color.blue(c) / 255f * l[4])
        labelMat?.set("sheenRoughnessFactor", .3f)
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

// the teardown's layout, in the cartridge's widths and heights: the halves this far either side of the middle and this
// far up, the board below them, the screwdriver below that
private const val SPREAD = .9f; private const val ROW = .3f; private const val BOARD_Y = -.95f; private const val TOOL_Y = -1.7f

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

/**
 * Label finishes (Customize shell, per game; a default in Settings). Each is the paper's material and a little baked
 * into the picture:
 *   standard     the light-coated paper of a real label
 *   matte paper  uncoated, soft
 *   glossy       a thick clear coat, sharp reflections
 *   worn         faded, scuffed, a crease, a corner torn off, edges rubbed white
 *   holo         a trading-card hologram: rainbow bands and sparkle in the picture, a sheen that runs through the colours as it turns
 *   opalescent   milky, pastel, pearly: soft colours that drift as it turns
 *   foil         metallic, like a foil-stamped special edition
 */
object Labels {
    const val STANDARD = 0
    val NAMES = listOf("Standard", "Matte paper", "Glossy", "Worn", "Holo", "Opalescent", "Foil")
    // roughness, metallic, clear coat, clear coat roughness, sheen strength (0 none), sheen colour's saturation
    val LOOKS = listOf(
        floatArrayOf(.62f, 0f, .08f, .5f, 0f, 0f), floatArrayOf(.95f, 0f, 0f, .9f, 0f, 0f), floatArrayOf(.16f, 0f, 1f, .04f, 0f, 0f),
        floatArrayOf(.86f, 0f, 0f, .9f, 0f, 0f), floatArrayOf(.22f, .55f, .7f, .08f, 1f, .75f), floatArrayOf(.3f, .12f, 1f, .1f, .8f, .32f),
        floatArrayOf(.24f, .9f, .45f, .12f, 0f, 0f))

    /** The label's picture with the finish baked in (a new picture; `plain` is recycled). */
    fun bake(plain: Bitmap, f: Int, seed: Int): Bitmap {
        if (f !in 3..6) return plain
        val b = plain.copy(Bitmap.Config.ARGB_8888, true).also { plain.recycle() }
        val c = android.graphics.Canvas(b); val w = b.width.toFloat(); val h = b.height.toFloat()
        val r = java.util.Random(seed.toLong() * 31 + f)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        when (f) {
            3 -> {   // worn
                p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(.7f) })
                b.copy(Bitmap.Config.ARGB_8888, false).also { c.drawBitmap(it, 0f, 0f, p) }.recycle(); p.colorFilter = null
                p.color = android.graphics.Color.argb(40, 245, 238, 220); c.drawRect(0f, 0f, w, h, p)   // sun-faded
                p.style = android.graphics.Paint.Style.STROKE
                repeat(26) {   // scuffs
                    p.strokeWidth = .8f + r.nextFloat() * 1.8f; p.color = android.graphics.Color.argb(50 + r.nextInt(90), 250, 248, 240)
                    val x = r.nextFloat() * w; val y = r.nextFloat() * h; val l = 10f + r.nextFloat() * w * .25f; val a = (r.nextFloat() - .5f) * .8f
                    c.drawLine(x, y, x + l * kotlin.math.cos(a), y + l * kotlin.math.sin(a), p)
                }
                // a crease across it: a pale line with a shadow beside it
                val cy = h * (.3f + r.nextFloat() * .4f)
                p.strokeWidth = 2.2f; p.color = android.graphics.Color.argb(110, 255, 255, 250); c.drawLine(0f, cy, w, cy + h * .08f, p)
                p.strokeWidth = 1.6f; p.color = android.graphics.Color.argb(70, 0, 0, 0); c.drawLine(0f, cy + 3f, w, cy + h * .08f + 3f, p)
                // edges rubbed back to the paper, and a corner torn off
                p.style = android.graphics.Paint.Style.FILL; p.color = android.graphics.Color.argb(140, 238, 232, 218)
                repeat(70) { val t = r.nextFloat(); val e = r.nextInt(4); val rr = 1.5f + r.nextFloat() * 5f
                    val (x, y) = when (e) { 0 -> t * w to r.nextFloat() * 6f; 1 -> t * w to h - r.nextFloat() * 6f; 2 -> r.nextFloat() * 6f to t * h; else -> w - r.nextFloat() * 6f to t * h }
                    c.drawCircle(x, y, rr, p) }
                val tear = android.graphics.Path().apply { moveTo(w, 0f); lineTo(w - w * .16f, 0f)
                    var x = w - w * .16f; var y = 0f; while (x < w) { x += w * .02f + r.nextFloat() * w * .02f; y += h * .02f + r.nextFloat() * h * .03f; lineTo(minOf(x, w), y) }; lineTo(w, 0f); close() }
                p.color = android.graphics.Color.rgb(233, 227, 212); c.drawPath(tear, p)
            }
            4 -> {   // holo: rainbow bands on a slant, fine lines and sparkle, laid over in Screen (lightening, like light off foil)
                p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                val cols = intArrayOf(0xffff5ea8.toInt(), 0xffffd25e.toInt(), 0xff6dff9b.toInt(), 0xff5ed8ff.toInt(), 0xffa77bff.toInt(), 0xffff5ea8.toInt())
                p.shader = android.graphics.LinearGradient(0f, 0f, w * .45f, h * .45f, cols, null, android.graphics.Shader.TileMode.MIRROR); p.alpha = 95
                c.drawRect(0f, 0f, w, h, p); p.shader = null
                p.alpha = 40; p.color = android.graphics.Color.WHITE; p.strokeWidth = 1f
                var x = -h; while (x < w) { c.drawLine(x, 0f, x + h, h, p); x += 5f }
                repeat(90) { p.alpha = 80 + r.nextInt(150); c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, .6f + r.nextFloat() * 1.6f, p) }
                p.xfermode = null
            }
            5 -> {   // opalescent: milky, with soft pastel clouds
                p.color = android.graphics.Color.argb(46, 255, 255, 255); c.drawRect(0f, 0f, w, h, p)
                p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                val pastels = intArrayOf(0xffffc6e0.toInt(), 0xffc6e7ff.toInt(), 0xffd8ffd0.toInt(), 0xfffff1c6.toInt(), 0xffe4d0ff.toInt())
                repeat(9) { val cx = r.nextFloat() * w; val cy = r.nextFloat() * h; val rad = w * (.2f + r.nextFloat() * .3f)
                    p.shader = android.graphics.RadialGradient(cx, cy, rad, pastels[r.nextInt(pastels.size)] and 0x80ffffff.toInt(), 0, android.graphics.Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, rad, p) }
                p.shader = null; p.xfermode = null
            }
            6 -> {   // foil: a brushed, gold-and-silver light over it (the metal does the rest)
                p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY)
                p.shader = android.graphics.LinearGradient(0f, 0f, w, h, intArrayOf(0xfff2d58a.toInt(), 0xffffffff.toInt(), 0xffc9cdd6.toInt(), 0xfff2d58a.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                p.alpha = 150; c.drawRect(0f, 0f, w, h, p); p.shader = null
                p.alpha = 30; p.color = android.graphics.Color.WHITE; var y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 2f }
                p.xfermode = null
            }
        }
        return b
    }
}

