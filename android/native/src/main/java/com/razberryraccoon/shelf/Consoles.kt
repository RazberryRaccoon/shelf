package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Color
import com.google.android.filament.MaterialInstance
import com.google.android.filament.gltfio.FilamentAsset
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToInt

/** One part's paint in a console's colors: a color in one of the Studio's finishes. */
data class PartPaint(val color: Int, val material: String)

/**
 * A console's colors: the real ones it came in (tools/cartridge-studio/consoles.js, CONSOLE_COLORS, via consoles.json),
 * or yours from the Studio. Parts it doesn't name keep the model's own colors.
 */
data class ConsoleLook(val name: String, val note: String, val parts: Map<String, PartPaint>, val custom: String? = null) {
    /** What the console saves to remember it: the real one's name, or "custom:<id>" for yours. */
    val key get() = custom?.let { Shell.KEY + it } ?: name
}

/** A console's paintable parts, in order ("shell" to "Shell"), and the colors it really came in, its first one standard. */
class ConsoleInfo(val sys: String, val name: String, val parts: List<Pair<String, String>>, val looks: List<ConsoleLook>, val board: BoardInfo?, val hinge: Hinge?)

/** Where a console's upper half (a handheld's front) opens: about the x or y axis through `at` (millimetres), by `angle`. */
class Hinge(val axis: Char, val at: FloatArray, val angle: Float)

/** Its circuit board's print: RaccoonTek's part number, the solder mask's color, the board's size in millimetres. */
class BoardInfo(val code: String, val mask: Int, val w: Float, val h: Float)

/** Whether these colors need the see-through console: its shell in a clear, frosted or smoke finish. */
fun ConsoleLook.seeThrough() = parts["shell"]?.let { Materials.of(it.material).clear != null } == true

/** Every console's real colors, and the ones you've made. */
class Consoles(ctx: Context, stage: Stage) {
    val info: Map<String, ConsoleInfo>
    val custom = ConsoleLooks(ctx)

    init {
        val j = try { JSONObject(String(stage.readAsset("models/consoles.json").let { b -> ByteArray(b.remaining()).also { b.get(it) } })) } catch (e: Exception) { JSONObject() }
        info = j.keys().asSequence().associateWith { sys -> j.getJSONObject(sys).let { c ->
            ConsoleInfo(sys, c.getString("name"),
                c.getJSONArray("parts").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } },
                c.getJSONArray("colors").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { l -> ConsoleLook(l.getString("name"), l.optString("note"), parts(l.getJSONObject("parts"))) } } },
                c.optJSONObject("board")?.let { b -> b.getJSONArray("size").let { z -> BoardInfo(b.getString("code"), Color.parseColor(b.getString("mask")), z.getDouble(0).toFloat(), z.getDouble(1).toFloat()) } },
                c.optJSONObject("open")?.let { o -> o.getJSONArray("at").let { a -> Hinge(o.getString("axis")[0], FloatArray(3) { a.getDouble(it).toFloat() }, o.getDouble("angle").toFloat()) } })
        } }
    }

    /** The colors a console wears: the ones saved for it, else its standard colors. */
    fun lookFor(sys: String, key: String?): ConsoleLook? = custom.get(sys, key) ?: info[sys]?.looks?.let { l -> l.firstOrNull { it.name == key } ?: l.firstOrNull() }

    /** consoles.json's parts: "#rrggbb" for plastic in that color, or ["#rrggbb", "metal"] for another Studio finish. */
    private fun parts(j: JSONObject): Map<String, PartPaint> = j.keys().asSequence().associateWith { k ->
        j.optJSONArray(k)?.let { PartPaint(Color.parseColor(it.getString(0)), it.getString(1)) } ?: PartPaint(Color.parseColor(j.getString(k)), "plastic")
    }
}

/** Your console colors, kept in the app's preferences. Each is for one console: its parts are that console's own. */
class ConsoleLooks(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    private class Saved(val sys: String, val look: ConsoleLook)
    private val saved: List<Saved> get() = try {
        JSONArray(prefs.getString("console-looks", "[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { j ->
            val p = j.getJSONObject("parts")
            Saved(j.getString("sys"), ConsoleLook(j.getString("name"), "", p.keys().asSequence().associateWith { k -> p.getJSONObject(k).let { PartPaint(it.getInt("color"), it.getString("material")) } }, j.getString("id")))
        } } }
    } catch (e: Exception) { emptyList() }

    fun all(sys: String) = saved.filter { it.sys == sys }.map { it.look }
    fun get(sys: String, key: String?): ConsoleLook? = key?.takeIf { it.startsWith(Shell.KEY) }?.removePrefix(Shell.KEY)?.let { id -> all(sys).firstOrNull { it.custom == id } }
    /** Saves new colors, or replaces the ones with the same id; returns them. */
    fun save(sys: String, l: ConsoleLook): ConsoleLook { write(saved.filter { it.look.custom != l.custom } + Saved(sys, l)); return l }
    fun delete(id: String) = write(saved.filter { it.look.custom != id })
    private fun write(l: List<Saved>) = prefs.edit().putString("console-looks", JSONArray().apply {
        l.forEach { s -> put(JSONObject().put("id", s.look.custom).put("sys", s.sys).put("name", s.look.name).put("parts", JSONObject().apply {
            s.look.parts.forEach { (k, p) -> put(k, JSONObject().put("color", p.color).put("material", p.material)) } })) } }.toString()).apply()
}

/**
 * A console on show, its parts paintable. Every plastic in the exported model is named for its part ("role:shell",
 * "role:dpad"; a part in two shades has "role:band" and "role:band#2"). The file's own colors are kept, so a part
 * that new colors leave alone goes back to how the model was made.
 */
class Console(stage: Stage, asset: FilamentAsset, file: JSONObject?, val clear: Boolean = false) : Model(stage, asset) {
    private class Own(val color: FloatArray, val rough: Float, val metal: Float, val coat: Float, val coatRough: Float)
    private val mats = ArrayList<Pair<String, MaterialInstance>>()
    private val own = HashMap<String, Own>()

    init {
        for (e in asset.renderableEntities) {
            val ri = rm.getInstance(e); if (ri == 0) continue
            for (k in 0 until rm.getPrimitiveCount(ri)) {
                val mi = rm.getMaterialInstanceAt(ri, k); val n = mi.name ?: continue
                if (n.startsWith("role:") && !n.startsWith("role:trim") && mats.none { it.second === mi }) mats += n to mi
            }
        }
        file?.optJSONArray("materials")?.let { a ->
            for (k in 0 until a.length()) {
                val m = a.getJSONObject(k); val n = m.optString("name"); if (!n.startsWith("role:")) continue
                val p = m.optJSONObject("pbrMetallicRoughness"); val c = p?.optJSONArray("baseColorFactor")
                val cc = m.optJSONObject("extensions")?.optJSONObject("KHR_materials_clearcoat")
                own[n] = Own(FloatArray(4) { c?.optDouble(it, 1.0)?.toFloat() ?: 1f }, (p?.optDouble("roughnessFactor", 1.0) ?: 1.0).toFloat(),
                    (p?.optDouble("metallicFactor", 1.0) ?: 1.0).toFloat(), (cc?.optDouble("clearcoatFactor", 0.0) ?: 0.0).toFloat(), (cc?.optDouble("clearcoatRoughnessFactor", 0.0) ?: 0.0).toFloat())
            }
        }
    }

    private fun role(n: String) = n.removePrefix("role:").substringBefore('#')

    // the upper half (a handheld's front), its own node in the file, and where it rests when closed
    private val upper = asset.getFirstEntityByName("upper")
    private val upperRest = FloatArray(16).also { if (upper != 0) tm.getTransform(tm.getInstance(upper), it) else android.opengl.Matrix.setIdentityM(it, 0) }

    /**
     * Opens the console along its seam, 0 closed to 1 open: the upper half turns on its hinge (a home console's top
     * swinging up and back, a handheld's front opening like a book), leaving the lower half with the board on it.
     */
    fun open(k: Float, h: Hinge?) {
        if (upper == 0 || h == null) return
        val m = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
        android.opengl.Matrix.translateM(m, 0, h.at[0], h.at[1], h.at[2])
        android.opengl.Matrix.rotateM(m, 0, Math.toDegrees((h.angle * k).toDouble()).toFloat(), if (h.axis == 'x') 1f else 0f, if (h.axis == 'y') 1f else 0f, if (h.axis == 'z') 1f else 0f)
        android.opengl.Matrix.translateM(m, 0, -h.at[0], -h.at[1], -h.at[2])
        val out = FloatArray(16); android.opengl.Matrix.multiplyMM(out, 0, m, 0, upperRest, 0)
        tm.setTransform(tm.getInstance(upper), out)
    }

    /** Puts on these colors; parts they don't name go back to the model's own. */
    fun paint(parts: Map<String, PartPaint>) {
        for ((n, mi) in mats) {
            val o = own[n] ?: Own(floatArrayOf(1f, 1f, 1f, 1f), .5f, 0f, 0f, 0f)
            val p = parts[role(n)]
            mi.set("emissiveFactor", 0f, 0f, 0f); mi.set("sheenColorFactor", 0f, 0f, 0f)
            if (p == null) {
                mi.set("baseColorFactor", o.color[0], o.color[1], o.color[2], 1f)   // the see-through model's shell, too, is solid until it has a clear finish
                mi.set("roughnessFactor", o.rough); mi.set("metallicFactor", o.metal)
                mi.set("clearCoatFactor", o.coat); mi.set("clearCoatRoughnessFactor", o.coatRough)
                continue
            }
            // the cartridges' rules (Cart.applyMaterial), except that consoles stay solid: see-through finishes become a
            // glassy, polished plastic in their color (as the translucent consoles look on a shelf)
            val f = Materials.of(p.material)
            val lin = intArrayOf(Color.red(p.color), Color.green(p.color), Color.blue(p.color)).map { srgbToLinear(it / 255f) }.toFloatArray()
            val base = if (f.metallic > 0) metalColor(lin, p.color, f) else FloatArray(3) { (lin[it] + (1 - lin[it]) * f.lift) * f.dim }
            // the see-through model's shell shows as much as the cartridges' clear shells do; elsewhere a clear finish is
            // a glassy, polished plastic
            val alpha = if (clear && n == "role:shell" && f.clear != null) f.alpha else 1f
            mi.set("baseColorFactor", base[0], base[1], base[2], alpha)
            mi.set("roughnessFactor", f.rough ?: if (f.clear == "frosted") maxOf(.4f, o.rough) else o.rough)
            mi.set("metallicFactor", f.metallic)
            mi.set("clearCoatFactor", f.coat ?: if (f.clear != null) 1f else o.coat)
            mi.set("clearCoatRoughnessFactor", f.coatRough ?: o.coatRough)
            if (f.glow > 0) mi.set("emissiveFactor", base[0] * f.glow, base[1] * f.glow, base[2] * f.glow)
            if (f.sheen > 0) { mi.set("sheenColorFactor", f.sheen, f.sheen, f.sheen); mi.set("sheenRoughnessFactor", .35f) }
        }
    }

    /** A part's own color as the model was made, for the Studio to start from. */
    fun ownColor(role: String): Int = own["role:$role"]?.color?.let { c ->
        fun s(v: Float) = ((if (v <= .0031308f) v * 12.92f else 1.055f * v.pow(1 / 2.4f) - .055f) * 255).roundToInt().coerceIn(0, 255)
        Color.rgb(s(c[0]), s(c[1]), s(c[2]))
    } ?: Color.GRAY
    fun ownMaterial(role: String) = if ((own["role:$role"]?.metal ?: 0f) >= .5f) "metal" else "plastic"
}

/**
 * A console's circuit board (board.js in the Studio), exported on its own in the console's own millimetres: placed with
 * its console's transform, it sits exactly inside it. The app draws its top (traces under the solder mask) and its
 * underside (RaccoonTek and the board's part number), as it does the cartridges' boards.
 */
class ConsoleBoard(stage: Stage, asset: FilamentAsset, info: BoardInfo?, sampler: com.google.android.filament.TextureSampler) : Model(stage, asset) {
    private val textures = ArrayList<com.google.android.filament.Texture>()

    init {
        val aspect = info?.let { it.w / it.h } ?: 1.5f
        val mask = info?.mask ?: Color.rgb(20, 83, 47)
        picture("board-top", Paintings.traces(aspect, mask), sampler)
        picture("board-print", Paintings.board(aspect, info?.code ?: "RT-01", 0, mask), sampler)
    }

    private fun picture(name: String, b: android.graphics.Bitmap, sampler: com.google.android.filament.TextureSampler) {
        val mi = asset.renderableEntities.asSequence().mapNotNull { e -> rm.getInstance(e).takeIf { it != 0 } }
            .flatMap { ri -> (0 until rm.getPrimitiveCount(ri)).asSequence().map { rm.getMaterialInstanceAt(ri, it) } }
            .firstOrNull { it.name == name && it.material.parameters.any { p -> p.name == "baseColorMap" && p.type == com.google.android.filament.Material.Parameter.Type.SAMPLER_2D } }
        if (mi == null) { b.recycle(); return }
        val t = pictureTexture(stage, b); b.recycle(); textures += t
        mi.setParameter("baseColorMap", t, sampler)
    }

    /** Puts it where its console is: the console's last placement. */
    fun follow(m: FloatArray) = tm.setTransform(tm.getInstance(asset.root), m)

    override fun destroy() { super.destroy(); textures.forEach { stage.engine.destroyTexture(it) } }
}
