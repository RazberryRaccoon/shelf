package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/**
 * A game's About, opened up over the page as an article (A on About), the way a picture opens: everything behind dims,
 * and a column of reading rises into view. At the top the game's console, its name and what's known (year, developer,
 * publisher, genre); then the Wikipedia article, in larger type with indented paragraphs and its section headings, the
 * game's pictures set between the paragraphs with captions, and the credit at the end. Up and down read (scroll), left
 * and right go to the previous or next section, B (or A) closes.
 */
class Article(ctx: Context, sys: String, name: String, facts: List<String>, text: String, source: String?,
              private val pics: List<Pair<String, Bitmap?>>) : FrameLayout(ctx) {
    var onClose: (() -> Unit)? = null
    private var shown = 0f
    private var closing = false
    private val scroll = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(70); overScrollMode = View.OVER_SCROLL_NEVER
    }
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 74, 0, 120) }
    private val heads = ArrayList<View>()   // the section headings, for left and right
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Theme.accentOf(sys)

    init {
        setWillNotDraw(false)
        col.addView(tv(19f, Fonts.bold, accent).apply { this.text = (Games.NAMES[sys] ?: sys).uppercase(); letterSpacing = .14f })
        col.addView(tv(50f, Fonts.display, Theme.fg).apply { this.text = name; setLineSpacing(0f, 1.05f) }, lp(14))
        if (facts.isNotEmpty()) col.addView(tv(23f, Fonts.body, Theme.fg2).apply { this.text = facts.joinToString("  ·  ") }, lp(14))
        col.addView(View(ctx).apply { background = round(accent, 2f) }, LinearLayout.LayoutParams(64, 4).apply { topMargin = 30; bottomMargin = 12 })
        // the article: paragraphs and headings, with a picture after every few paragraphs, spread through it
        val blocks = parse(text)
        val paras = blocks.count { !it.head }
        val shownPics = pics.filter { it.second != null }
        val at = shownPics.indices.map { k -> if (k == 0) 1 else ((k + 1) * paras / (shownPics.size + 1)).coerceAtLeast(k + 1) }
        var n = 0; var lead = true; var fig = 0
        for (b in blocks) {
            if (b.head) {
                val h = tv(if (b.level > 2) 25f else 30f, Fonts.displayBold, Theme.fg).apply { this.text = b.text }
                col.addView(h, lp(if (b.level > 2) 30 else 46)); heads += h
                continue
            }
            col.addView(tv(if (lead) 27f else 25f, Fonts.body, if (lead) Theme.fg else READ).apply {
                setLineSpacing(0f, 1.6f)
                // indented paragraphs, as in a book (the opening one stands as the lead, without)
                this.text = if (lead) b.text else SpannableString(b.text).apply { setSpan(LeadingMarginSpan.Standard(46, 0), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }
            }, lp(if (lead) 18 else 16))
            lead = false; n++
            while (fig < shownPics.size && n >= at[fig]) { figure(shownPics[fig]); fig++ }
        }
        while (fig < shownPics.size) { figure(shownPics[fig]); fig++ }
        if (source != null) col.addView(tv(18f, Fonts.body, Theme.fg3).apply { this.text = source; setLineSpacing(0f, 1.4f) }, lp(56))
        scroll.addView(col, LayoutParams(-1, -2))
        addView(scroll, LayoutParams(COL.toInt(), -1).apply { leftMargin = ((Stage.W - COL) / 2).toInt() })
        alpha = 0f
    }

    private class Block(val text: String, val head: Boolean, val level: Int)

    /** The plain text into paragraphs and headings, without the reference sections (and headings left with nothing under them). */
    private fun parse(text: String): List<Block> {
        val out = ArrayList<Block>()
        var skip = false
        for (raw in text.split("\n")) {
            val line = raw.trim(); if (line.isEmpty()) continue
            val m = Regex("^(=+)\\s*(.*?)\\s*=+$").find(line)
            if (m != null) {
                val level = m.groupValues[1].length; val t = m.groupValues[2]
                if (level <= 2) skip = t.lowercase() in DROP
                if (!skip) out += Block(t, true, level)
            } else if (!skip) out += Block(line, false, 0)
        }
        // a heading straight before another of the same or higher rank (or at the end) had only a table or list under it
        return out.filterIndexed { k, b -> !b.head || out.drop(k + 1).firstOrNull()?.let { nx -> !nx.head || nx.level > b.level } == true }
    }

    /** One of the game's pictures between the paragraphs, centred, rounded, with its name under it. */
    private fun figure(f: Pair<String, Bitmap?>) {
        val b = f.second ?: return
        val s = min(620f / b.width, 360f / b.height); val w = (b.width * s).toInt(); val h = (b.height * s).toInt()
        col.addView(Picture(context, b), LinearLayout.LayoutParams(w, h).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = 38 })
        col.addView(tv(18f, Fonts.body, Theme.fg3).apply { this.text = f.first; gravity = Gravity.CENTER }, lp(12).apply { bottomMargin = 18 })
    }

    private class Picture(ctx: Context, private val b: Bitmap) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(c: Canvas) {
            val s = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
            p.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setScale(s, s) }) }
            c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 18f, 18f, p)
        }
    }

    fun input(a: String): Boolean {
        if (closing) return true
        when (a) {
            "down" -> if (scroll.canScrollVertically(1)) scroll.smoothScrollBy(0, 190) else Sounds.play(Sounds.BUMP)
            "up" -> if (scroll.canScrollVertically(-1)) scroll.smoothScrollBy(0, -190) else Sounds.play(Sounds.BUMP)
            // to the next or previous section's heading
            "right" -> heads.firstOrNull { it.top - 60 > scroll.scrollY + 4 }?.let { scroll.smoothScrollTo(0, it.top - 60); Sounds.play(Sounds.MOVE) } ?: Sounds.play(Sounds.BUMP)
            "left" -> if (scroll.scrollY > 0) { scroll.smoothScrollTo(0, (heads.lastOrNull { it.top - 60 < scroll.scrollY - 4 }?.top?.minus(60) ?: 0).coerceAtLeast(0)); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "b", "a" -> { closing = true; Sounds.play(Sounds.CLOSE) }
        }
        return true
    }

    fun tick(dt: Float) {
        shown = if (closing) maxOf(0f, shown - dt / .22f) else min(1f, shown + dt / .3f)
        val k = Backdrop.ease(shown)
        alpha = k; scroll.translationY = (1 - k) * 40f
        if (closing && shown == 0f) { pics.forEach { it.second?.recycle() }; onClose?.invoke() }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        p.color = Color.argb(238, 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        // how far through it you are: a thin line beside the column that fills as you read
        val total = (col.height - scroll.height).coerceAtLeast(1)
        val x = (Stage.W + COL) / 2 + 40f; val top = 120f; val len = Stage.H - 240f
        p.color = Theme.line; c.drawRoundRect(RectF(x, top, x + 4, top + len), 2f, 2f, p)
        p.color = accent; c.drawRoundRect(RectF(x, top, x + 4, top + len * (scroll.scrollY.toFloat() / total).coerceIn(0f, 1f).coerceAtLeast(.02f)), 2f, 2f, p)
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun tv(size: Float, face: android.graphics.Typeface, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }

    private companion object {
        const val COL = 880f
        val READ = Color.parseColor("#d4d9f2")   // the reading color: softer than titles, brighter than notes
        val DROP = setOf("references", "external links", "see also", "notes", "further reading", "citations", "bibliography", "sources", "footnotes", "notes and references")
    }
}
