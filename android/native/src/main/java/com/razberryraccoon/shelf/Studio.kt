package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * A finish for a cartridge shell, as the Studio offers them. Numbers left null keep the cartridge's own (its real
 * plastic's roughness and clear coat). `clear` picks the see-through model; `dim` darkens the color (smoke);
 * `glow` makes it give off its own light; `sheen` is a soft pearly edge glow.
 */
data class Finish(val id: String, val name: String, val rough: Float?, val metallic: Float, val coat: Float?, val coatRough: Float?,
                    val clear: String? = null, val alpha: Float = 1f, val dim: Float = 1f, val glow: Float = 0f, val sheen: Float = 0f, val lift: Float = 0f) {
    val metal get() = metallic >= .5f
}

object Materials {
    val ALL = listOf(
        Finish("plastic", "Plastic", null, 0f, null, null),
        Finish("matte", "Matte", .85f, 0f, 0f, .5f),
        Finish("glossy", "Glossy", .2f, 0f, 1f, .04f),
        // these four use exactly the numbers of the real frosted, clear and gold or silver shells (Cart.apply), so a real
        // shell opened in the Studio looks the same until you change something
        Finish("frosted", "Frosted", null, 0f, null, null, clear = "frosted", alpha = .7f),   // roughness: at least .4 (applyMaterial)
        Finish("clear", "Clear", .06f, 0f, null, .05f, clear = "transparent", alpha = .38f),
        Finish("smoke", "Smoke", .06f, 0f, null, .05f, clear = "transparent", alpha = .6f, dim = .35f),
        Finish("metal", "Metallic", .28f, 1f, .5f, null),
        Finish("brushed", "Brushed metal", .5f, 1f, 0f, .5f),
        Finish("anodized", "Anodized aluminum", .34f, 1f, .25f, .35f, lift = .06f),   // colored, satin metal, a little brighter than its dye
        Finish("pearl", "Pearl", .3f, .15f, 1f, .05f, sheen = .7f),
        Finish("flake", "Metal flake", .42f, .6f, 1f, .03f),
        Finish("glow", "Glow in the dark", .55f, 0f, .2f, .5f, glow = .9f))
    fun of(id: String?): Finish = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/** The Studio's named colors: everyday plastics, metals, and some favorites from the cartridges' history. */
object Swatches {
    val ALL = listOf(
        "Snow" to "#f2f2f0", "Silver" to "#c0c3c8", "Ash" to "#8d9096", "Gunmetal" to "#4a4e55", "Charcoal" to "#2b2d31", "Black" to "#121214",
        "Cherry" to "#c8102e", "Crimson" to "#9e1b32", "Coral" to "#ff6f61", "Watermelon" to "#fc6c85", "Bubblegum" to "#ff8fc7", "Blush" to "#f4c2c2",
        "Magenta" to "#d6249f", "Rose gold" to "#e8b4a0", "Gold" to "#e6c068", "Champagne" to "#e8d9b5", "Copper" to "#d98c5f", "Bronze" to "#b08d57",
        "Tangerine" to "#ff8c1a", "Amber" to "#ffb000", "Sunflower" to "#ffd23f", "Lemon" to "#f4e87c", "Butter" to "#f6e3a1", "Sand" to "#d8c3a0",
        "Lime" to "#9bd13c", "Jungle green" to "#2e8b57", "Emerald" to "#1fa774", "Mint" to "#98e5c8", "Olive" to "#6b7a3a", "Forest" to "#234d2c",
        "Teal" to "#1aa6a6", "Ice" to "#cfeef7", "Sky" to "#7cc6f2", "Azure" to "#2f80ed", "Cobalt" to "#1f4fbf", "Navy" to "#1b2a4a",
        "Lavender" to "#b9a7e8", "Atomic purple" to "#6a4c9c", "Grape" to "#5b2a86", "Orchid" to "#c86bd1", "Plum" to "#6e2c4e", "Cocoa" to "#6b4a3a",
        "Glow green" to "#b8f5a0", "Midnight" to "#232a5c").map { (n, c) -> n to Color.parseColor(c) }
}

/** A shell you made in the Studio. Shared by every cartridge: a design made on a GBA can go on an N64 cartridge too. */
data class Shell(val id: String, val name: String, val color: Int, val material: String) {
    val key get() = KEY + id
    fun variant() = Materials.of(material).let { m -> Variant(name, color, m.name, m.clear, m.metal, material = material, custom = id) }
    companion object { const val KEY = "custom:" }
}

/** Your saved shells, kept in the app's preferences. */
class Shells(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    val all: List<Shell> get() = try {
        JSONArray(prefs.getString("shells", "[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { j -> Shell(j.getString("id"), j.getString("name"), j.getInt("color"), j.getString("material")) } } }
    } catch (e: Exception) { emptyList() }
    fun get(key: String?): Shell? = key?.takeIf { it.startsWith(Shell.KEY) }?.removePrefix(Shell.KEY)?.let { id -> all.firstOrNull { it.id == id } }
    /** Saves a new shell, or replaces the one with the same id; returns it. */
    fun save(s: Shell): Shell { write(all.filter { it.id != s.id } + s); return s }
    fun delete(id: String) = write(all.filter { it.id != id })
    private fun write(l: List<Shell>) = prefs.edit().putString("shells", JSONArray().apply {
        l.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("material", it.material)) } }.toString()).apply()
}

/**
 * The Studio's controls, drawn in one view: a row of materials, a grid of named colors, and hue, saturation and
 * brightness bars for any color at all, then Save and Cancel. Every change shows on the cartridge at once (onChange).
 */
class StudioView(ctx: Context, startColor: Int, startMaterial: String, private val startName: String = "Custom") : View(ctx) {
    var onChange: ((Int, String) -> Unit)? = null
    var onSave: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private enum class Zone { MAT, GRID, HUE, SAT, VAL, BUTTONS }
    private var zone = Zone.MAT
    private var mat = Materials.ALL.indexOfFirst { it.id == startMaterial }.coerceAtLeast(0)
    private var cell = Swatches.ALL.indexOfFirst { it.second == startColor }   // -1: a color of your own, from the bars
    private var gridAt = cell.coerceAtLeast(0)
    private val hsv = FloatArray(3).also { Color.colorToHSV(startColor, it) }
    private var button = 0   // 0 Save, 1 Cancel
    private var chipScroll = 0f; private var chipScrollTo = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    val color get() = Color.HSVToColor(hsv)
    val material get() = Materials.ALL[mat]
    /** The color's name: the swatch's, or "Custom" for one from the bars. */
    val colorName get() = if (cell >= 0) Swatches.ALL[cell].first else if (nudged) "Custom" else startName
    private var nudged = false   // the bars were used: it's a color of your own now

    companion object { const val W = 528; const val H = 478; private const val COLS = 11; private const val CELL = 46f; private const val ROW = 42f
        private const val CHIPS_Y = 26f; private const val GRID_Y = 118f; private const val BARS_Y = 318f; private const val BTN_Y = 426f; private const val X0 = 12f }

    fun input(a: String) {
        val before = zone
        when (zone) {
            Zone.MAT -> when (a) {
                "left" -> stepMat(-1); "right" -> stepMat(1); "down" -> zone = Zone.GRID; "up" -> Sounds.play(Sounds.BUMP)
                "a" -> { zone = Zone.BUTTONS; button = 0 }
            }
            Zone.GRID -> {
                val col = gridAt % COLS; val row = gridAt / COLS; val rows = (Swatches.ALL.size + COLS - 1) / COLS
                when (a) {
                    "left" -> if (col > 0) pickCell(gridAt - 1) else Sounds.play(Sounds.BUMP)
                    "right" -> if (col < COLS - 1 && gridAt + 1 < Swatches.ALL.size) pickCell(gridAt + 1) else Sounds.play(Sounds.BUMP)
                    "up" -> if (row > 0) pickCell(gridAt - COLS) else zone = Zone.MAT
                    "down" -> if (row < rows - 1 && gridAt + COLS < Swatches.ALL.size) pickCell(gridAt + COLS) else zone = Zone.HUE
                    "a" -> { zone = Zone.BUTTONS; button = 0 }
                }
            }
            Zone.HUE, Zone.SAT, Zone.VAL -> when (a) {
                "left" -> nudge(-1); "right" -> nudge(1)
                "up" -> zone = if (zone == Zone.HUE) Zone.GRID else Zone.values()[zone.ordinal - 1]
                "down" -> zone = if (zone == Zone.VAL) Zone.BUTTONS else Zone.values()[zone.ordinal + 1]
                "a" -> { zone = Zone.BUTTONS; button = 0 }
            }
            Zone.BUTTONS -> when (a) {
                "left" -> if (button > 0) { button = 0; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "right" -> if (button < 1) { button = 1; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "up" -> zone = Zone.VAL
                "a" -> if (button == 0) onSave?.invoke() else onCancel?.invoke()
            }
        }
        if (a == "b") onCancel?.invoke()
        if (zone != before) Sounds.play(Sounds.MOVE)
        invalidate()
    }

    private fun stepMat(d: Int) {
        val k = (mat + d).coerceIn(0, Materials.ALL.size - 1)
        if (k == mat) return Sounds.play(Sounds.BUMP)
        mat = k; Sounds.play(Sounds.MOVE); changed()
    }
    private fun pickCell(k: Int) { gridAt = k; cell = k; Color.colorToHSV(Swatches.ALL[k].second, hsv); Sounds.play(Sounds.MOVE); changed() }
    private fun nudge(d: Int) {
        when (zone) {
            Zone.HUE -> hsv[0] = (hsv[0] + d * 4f + 360f) % 360f
            Zone.SAT -> hsv[1] = (hsv[1] + d * .03f).coerceIn(0f, 1f)
            else -> hsv[2] = (hsv[2] + d * .03f).coerceIn(0f, 1f)
        }
        cell = -1; nudged = true; Sounds.play(Sounds.MOVE); changed()
    }
    private fun changed() = onChange?.invoke(color, material.id)

    override fun onDraw(c: Canvas) {
        p.typeface = Fonts.bold; p.textSize = 17f; p.letterSpacing = .1f; p.color = Theme.accentOf(null)
        c.drawText("MATERIAL  ·  ${material.name.uppercase()}", X0, 16f, p)
        c.drawText("COLOR  ·  ${colorName.uppercase()}", X0, GRID_Y - 18f, p)
        p.letterSpacing = 0f
        drawChips(c); drawGrid(c); drawBars(c); drawButtons(c)
        if (abs(chipScrollTo - chipScroll) > .5f) { chipScroll += (chipScrollTo - chipScroll) * .25f; postInvalidateOnAnimation() } else chipScroll = chipScrollTo
    }

    private fun drawChips(c: Canvas) {
        p.textSize = 18f
        val widths = Materials.ALL.map { p.measureText(it.name) + 40f }
        var x = X0; val xs = widths.map { w -> x.also { x += w + 10 } }
        // scroll the row so the chosen one is in view
        val l = xs[mat]; val r = l + widths[mat]
        if (l - chipScrollTo < X0) chipScrollTo = l - X0; if (r - chipScrollTo > W - X0) chipScrollTo = r - (W - X0)
        c.save(); c.clipRect(0f, 0f, W.toFloat(), CHIPS_Y + 60f); c.translate(-chipScroll, 0f)
        Materials.ALL.forEachIndexed { k, m ->
            val box = RectF(xs[k], CHIPS_Y + 6, xs[k] + widths[k], CHIPS_Y + 50)
            val on = k == mat; val focus = on && zone == Zone.MAT
            p.style = Paint.Style.FILL; p.color = if (focus) Theme.sel else Theme.chip; c.drawRoundRect(box, 22f, 22f, p)
            if (on && !focus) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.accentOf(null); c.drawRoundRect(box, 22f, 22f, p) }
            if (focus) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 4, box.top - 4, box.right + 4, box.bottom + 4), 26f, 26f, p) }
            p.style = Paint.Style.FILL; p.color = if (focus) Theme.selFg else Theme.fg; p.typeface = if (on) Fonts.bold else Fonts.body
            p.textAlign = Paint.Align.CENTER; c.drawText(m.name, box.centerX(), box.centerY() + 6.5f, p); p.textAlign = Paint.Align.LEFT
        }
        c.restore()
    }

    private fun drawGrid(c: Canvas) {
        val m = material
        Swatches.ALL.forEachIndexed { k, (_, col) ->
            val cx = X0 + 22 + (k % COLS) * CELL; val cy = GRID_Y + 20 + (k / COLS) * ROW
            // each swatch is a little ball of the chosen material: lit from the top left, see-through ones show the panel
            val shade = if (m.metal) Backdrop.blend(col, Color.BLACK, .45f) else Backdrop.blend(col, Color.BLACK, .25f)
            val hi = if (m.metal || m.coat == 1f) Backdrop.blend(col, Color.WHITE, .7f) else Backdrop.blend(col, Color.WHITE, .25f)
            p.shader = RadialGradient(cx - 6, cy - 7, 22f, intArrayOf(hi, col, shade), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
            p.alpha = (255 * (if (m.clear != null) .35f + .65f * m.alpha else 1f)).toInt()
            c.drawCircle(cx, cy, 16f, p); p.shader = null; p.alpha = 255
            if (m.glow > 0) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.argb(120, Color.red(col), Color.green(col), Color.blue(col)); c.drawCircle(cx, cy, 18.5f, p); p.style = Paint.Style.FILL }
            if (k == cell) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.fg; c.drawCircle(cx, cy, 19.5f, p); p.style = Paint.Style.FILL }
            if (k == gridAt && zone == Zone.GRID) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawCircle(cx, cy, 22f, p); p.style = Paint.Style.FILL }
        }
    }

    private fun drawBars(c: Canvas) {
        val names = listOf("Hue", "Saturation", "Brightness")
        val left = X0 + 128; val right = W - X0 - 10
        for (k in 0..2) {
            val y = BARS_Y + k * 34f; val on = zone.ordinal == Zone.HUE.ordinal + k
            p.typeface = if (on) Fonts.bold else Fonts.body; p.textSize = 18f; p.color = if (on) Theme.fg else Theme.fg2
            c.drawText(names[k], X0, y + 6, p)
            val colors = when (k) {
                0 -> IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, maxOf(.35f, hsv[1]), maxOf(.5f, hsv[2]))) }
                1 -> intArrayOf(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])), Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
                else -> intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
            }
            p.shader = LinearGradient(left, 0f, right, 0f, colors, null, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(left, y - 7, right, y + 7), 7f, 7f, p); p.shader = null
            val v = when (k) { 0 -> hsv[0] / 360f; 1 -> hsv[1]; else -> hsv[2] }
            val kx = left + (right - left) * v
            p.color = Color.WHITE; c.drawCircle(kx, y, if (on) 12f else 9f, p)
            p.color = color; c.drawCircle(kx, y, if (on) 8f else 6f, p)
            if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(left - 8, y - 15, right + 8, y + 15), 15f, 15f, p); p.style = Paint.Style.FILL }
        }
    }

    private fun drawButtons(c: Canvas) {
        val w = (W - 2 * X0 - 14) / 2f
        listOf("Save", "Cancel").forEachIndexed { k, label ->
            val box = RectF(X0 + k * (w + 14), BTN_Y, X0 + k * (w + 14) + w, BTN_Y + 46)
            val focus = zone == Zone.BUTTONS && button == k
            p.style = Paint.Style.FILL; p.color = when { focus -> Theme.sel; k == 0 -> Theme.accentOf(null); else -> Theme.chip }
            c.drawRoundRect(box, 23f, 23f, p)
            if (focus) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 4, box.top - 4, box.right + 4, box.bottom + 4), 27f, 27f, p); p.style = Paint.Style.FILL }
            p.typeface = Fonts.bold; p.textSize = 20f; p.color = if (focus || k == 0) Theme.selFg else Theme.fg
            p.textAlign = Paint.Align.CENTER; c.drawText(label, box.centerX(), box.centerY() + 7, p); p.textAlign = Paint.Align.LEFT
        }
    }
}

/** A cartridge style's own defaults, or one of your Studio shells, from what a game has saved ("custom:…" for shells). */
fun Carts.variantFor(sys: String, key: String?): Variant? {
    val style = styles[sys] ?: return null
    return shells.get(key)?.variant() ?: style.variant(key)
}

