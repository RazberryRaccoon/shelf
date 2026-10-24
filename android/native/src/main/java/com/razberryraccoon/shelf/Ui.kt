package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout

/** The prototype's colors (the Space theme), as Android colors. */
object Theme {
    val bg = Color.parseColor("#070b1e")
    val fg = Color.parseColor("#eef1ff")
    val fg2 = Color.parseColor("#9aa2cc")
    val fg3 = Color.argb(140, 238, 241, 255)
    val panel = Color.argb(184, 12, 17, 44)
    val line = Color.argb(31, 238, 241, 255)
    val chip = Color.argb(26, 238, 241, 255)
    val sel = Color.parseColor("#eef1ff")
    val selFg = Color.parseColor("#0b1030")
    /** Each console's accent, from the prototype's PLAT table. */
    val accent = mapOf("nes" to "#ff9db6", "snes" to "#c2b6ff", "n64" to "#ffd06b", "genesis" to "#86b0ff", "gb" to "#b9db74",
        "gbc" to "#5fe0d4", "gba" to "#ab98ff", "ps1" to "#9fb2d8", "ds" to "#ff9a82", "psp" to "#a7e8ff", "android" to "#8ff0b4")
        .mapValues { Color.parseColor(it.value) }
    fun accentOf(sys: String?) = accent[sys] ?: Color.parseColor("#b3a6ff")
    /** The profile colors (the prototype's PROFILE_COLORS): a light, a middle and a dark tone for each avatar. */
    val profile = listOf(listOf("#ffd38a", "#e8834a", "#6a2a1e"), listOf("#bff3ff", "#3fa4d9", "#13355e"), listOf("#f6c8ff", "#b05ad8", "#401a5e"),
        listOf("#d6ffc8", "#5ac46a", "#1a4a2a"), listOf("#ffe0f0", "#e0607e", "#5a1a33")).map { l -> l.map { Color.parseColor(it) }.toIntArray() }
    val profileNames = listOf("Amber", "Sky", "Orchid", "Fern", "Rose")
    /** A round avatar lit from the top left, as in the prototype. */
    fun drawAvatar(c: android.graphics.Canvas, x: Float, y: Float, r: Float, k: Int) {
        val cols = profile[k.coerceIn(0, profile.size - 1)]
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.RadialGradient(x - r * .3f, y - r * .4f, r * 1.6f, cols, floatArrayOf(0f, .55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
}

/** Manrope for text and Unbounded for titles, as in the prototype (both SIL Open Font License, in assets/fonts). */
object Fonts {
    lateinit var body: Typeface; lateinit var bold: Typeface; lateinit var display: Typeface; lateinit var displayBold: Typeface
    fun load(ctx: Context) {
        fun f(file: String, wght: Int) = Typeface.Builder(ctx.assets, "fonts/$file").setFontVariationSettings("'wght' $wght").build()
        body = f("Manrope.ttf", 500); bold = f("Manrope.ttf", 700); display = f("Unbounded.ttf", 500); displayBold = f("Unbounded.ttf", 600)
    }

    /** A title in white with a soft dark edge, wrapped onto lines no wider than `maxW`, its last line ending at `bottom`. */
    fun titleOn(c: Canvas, title: String, x: Float, bottom: Float, maxW: Float, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = displayBold; textSize = size }
        val lines = wrap(p, title, maxW).take(3)
        var y = bottom - (lines.size - 1) * size * 1.08f
        for (l in lines) {
            p.style = Paint.Style.STROKE; p.strokeWidth = size * .16f; p.strokeJoin = Paint.Join.ROUND; p.color = Color.argb(190, 10, 8, 30); c.drawText(l, x, y, p)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawText(l, x, y, p)
            y += size * 1.08f
        }
    }
    fun wrap(p: Paint, text: String, maxW: Float): List<String> {
        val out = ArrayList<String>(); var line = ""
        for (w in text.split(' ')) { val t = if (line.isEmpty()) w else "$line $w"; if (p.measureText(t) > maxW && line.isNotEmpty()) { out.add(line); line = w } else line = t }
        if (line.isNotEmpty()) out.add(line); return out
    }
}

/** Shelf's own art, until downloaded art comes to the native app: a calm scene in a color from the game's title. */
object Paintings {
    fun scene(c: Canvas, w: Float, h: Float, title: String) {
        val hue = ((title.hashCode() and 0x7fffffff) % 360).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, Color.HSVToColor(floatArrayOf(hue, .55f, .3f)), Color.HSVToColor(floatArrayOf((hue + 50) % 360, .6f, .55f)), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null; p.color = Color.HSVToColor(220, floatArrayOf((hue + 160) % 360, .45f, .95f))
        c.drawCircle(w * .72f, h * .4f, minOf(w, h) * .24f, p)
        p.color = Color.argb(30, 255, 255, 255)
        for (i in 0 until 5) c.drawOval(w * (.05f + i * .15f), h * .78f, w * (.35f + i * .15f), h * 1.05f, p)
    }
    fun label(w: Int, h: Int, title: String): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        scene(c, w.toFloat(), h.toFloat(), title)
        Fonts.titleOn(c, title, w * .07f, h * .9f, w * .86f, minOf(w, h) * .13f)
        return b
    }
    fun picture(w: Int, h: Int, title: String): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { scene(Canvas(it), w.toFloat(), h.toFloat(), title) }
}

/** The interface's layer: laid out at 1334 × 750 like the prototype, then scaled and centered to fit the screen. */
class Board(ctx: Context) : FrameLayout(ctx) {
    init { clipChildren = false; pivotX = 0f; pivotY = 0f }
    fun fit(scale: Float, ox: Float, oy: Float) { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy }
    /** Adds a child at a fixed spot in layout pixels (width or height may be WRAP_CONTENT). */
    fun put(v: android.view.View, x: Int, y: Int, w: Int = ViewGroup.LayoutParams.WRAP_CONTENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        addView(v, LayoutParams(w, h).apply { leftMargin = x; topMargin = y })
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(Stage.W.toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Stage.H.toInt(), MeasureSpec.EXACTLY))
    }
}
