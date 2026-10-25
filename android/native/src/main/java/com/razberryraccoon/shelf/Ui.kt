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
        if (r <= 0f) return
        val cols = profile[k.coerceIn(0, profile.size - 1)]
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.RadialGradient(x - r * .3f, y - r * .4f, r * 1.6f, cols, floatArrayOf(0f, .55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }
}

/** Manrope for text and Unbounded for titles, as in the prototype (both SIL Open Font License, in assets/fonts). */
object Fonts {
    lateinit var body: Typeface; lateinit var bold: Typeface; lateinit var display: Typeface; lateinit var displayBold: Typeface
    /** Orbitron (SIL Open Font License, assets/fonts): RaccoonTek's lettering on the circuit boards. */
    lateinit var tech: Typeface; lateinit var techLight: Typeface
    fun load(ctx: Context) {
        fun f(file: String, wght: Int) = Typeface.Builder(ctx.assets, "fonts/$file").setFontVariationSettings("'wght' $wght").build()
        body = f("Manrope.ttf", 500); bold = f("Manrope.ttf", 700); display = f("Unbounded.ttf", 500); displayBold = f("Unbounded.ttf", 600)
        tech = f("Orbitron.ttf", 800); techLight = f("Orbitron.ttf", 500)
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
    /**
     * The back of a cartridge's circuit board (the Studio's boardPrint, drawn here): green solder mask, faint copper
     * traces, the row of gold contacts, and white silkscreen: RaccoonTek, the board's part number, a few markings.
     * `aspect` is the board's width over its height.
     */
    fun board(aspect: Float, code: String, contacts: Int, mask: Int = Color.rgb(29, 90, 59)): Bitmap {
        val w = 768; val h = (w / aspect).toInt().coerceIn(128, 1536)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(mask)
        var seed = 7L; fun rnd(): Float { seed = (seed * 16807) % 2147483647; return seed / 2147483647f }
        p.style = Paint.Style.STROKE; p.strokeWidth = w * .006f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        repeat(26) {   // traces: straight runs with 45° turns, the way boards are routed
            var x = rnd() * w; var y = h * (.12f + rnd() * .76f); val path = android.graphics.Path().apply { moveTo(x, y) }
            repeat(3) { val d = w * (.05f + rnd() * .14f); when ((rnd() * 4).toInt()) { 0 -> x += d; 1 -> x -= d; 2 -> { x += d * .7f; y += d * .7f * (if (rnd() < .5f) 1 else -1) }; else -> { x -= d * .7f; y += d * .7f * (if (rnd() < .5f) 1 else -1) } }; path.lineTo(x, y) }
            p.style = Paint.Style.STROKE; p.color = Color.argb(72, 120, 190, 140); c.drawPath(path, p)
            p.style = Paint.Style.FILL; p.color = Color.argb(115, 200, 220, 160); c.drawCircle(x, y, w * .007f, p)
        }
        p.style = Paint.Style.FILL; p.color = Color.rgb(201, 165, 78)   // the contacts along the bottom edge
        for (i in 0 until contacts) c.drawRect((i + .2f) * w / contacts, h * .9f, (i + .8f) * w / contacts, h.toFloat(), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = w * .004f; p.color = Color.argb(217, 240, 244, 236); c.drawRect(w * .04f, h * .06f, w * .96f, h * .84f, p)
        p.style = Paint.Style.FILL; p.color = Color.rgb(240, 244, 236); p.textAlign = Paint.Align.CENTER; p.typeface = Fonts.tech
        var size = w * .13f; p.textSize = size
        val tw = p.measureText("RaccoonTek"); if (tw > w * .78f) { size *= w * .78f / tw; p.textSize = size }
        c.drawText("RaccoonTek", w / 2f, h * .4f + size * .35f, p)
        p.typeface = Fonts.techLight; p.textSize = size * .3f
        c.drawText("$code  ·  REV 2.0", w / 2f, h * .4f + size * 1.05f, p)
        p.textAlign = Paint.Align.LEFT; p.textSize = size * .2f
        c.drawText("U1", w * .08f, h * .16f, p); c.drawText("C3", w * .84f, h * .74f, p)
        return b
    }
    /**
     * A console board's top: copper traces under the solder mask, routed straight with 45° turns, the round pads of
     * vias, and the silkscreen's little white marks. The parts themselves are 3D, on top of it.
     */
    fun traces(aspect: Float, mask: Int): Bitmap {
        val w = 1024; val h = (w / aspect).toInt().coerceIn(256, 2048)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(mask)
        var seed = 11L; fun rnd(): Float { seed = (seed * 16807) % 2147483647; return seed / 2147483647f }
        val copper = Color.argb(150, (Color.red(mask) + 60).coerceAtMost(255), (Color.green(mask) + 70).coerceAtMost(255), (Color.blue(mask) + 45).coerceAtMost(255))
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        repeat(w * h / 5200) {
            var x = rnd() * w; var y = rnd() * h; val path = android.graphics.Path().apply { moveTo(x, y) }
            repeat(2 + (rnd() * 3).toInt()) { val d = w * (.02f + rnd() * .1f); when ((rnd() * 6).toInt()) { 0 -> x += d; 1 -> x -= d; 2 -> y += d; 3 -> y -= d; 4 -> { x += d * .7f; y += d * .7f * (if (rnd() < .5f) 1 else -1) }; else -> { x -= d * .7f; y += d * .7f * (if (rnd() < .5f) 1 else -1) } }; path.lineTo(x, y) }
            p.style = Paint.Style.STROKE; p.strokeWidth = w * (if (rnd() < .15f) .006f else .0028f); p.color = copper; c.drawPath(path, p)
            p.style = Paint.Style.FILL; p.color = Color.argb(210, 214, 196, 140); c.drawCircle(x, y, w * .0045f, p)   // a via's pad
        }
        p.style = Paint.Style.FILL; p.color = Color.argb(200, 236, 240, 232); p.typeface = Fonts.techLight; p.textSize = w * .012f
        repeat(w * h / 26000) { c.drawText(listOf("C", "R", "U", "L", "Q", "TP")[(rnd() * 6).toInt()] + (1 + (rnd() * 120).toInt()), rnd() * w, rnd() * h, p) }
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
