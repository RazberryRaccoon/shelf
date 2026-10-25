package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.View
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/**
 * A game's manual, read like the booklet it is (as in the prototype's reader): the cover on its own, then two pages
 * side by side, a page folding over at the spine as you turn. Left and right turn pages, Y bookmarks the spread you're
 * on (a ribbon hangs from it), L1 and R1 jump between bookmarks, B closes. Your place and bookmarks are kept.
 * The pages are drawn from the PDF on their own thread, the spreads either side made ready ahead.
 */
class Manual(ctx: Context, file: File, private val title: String, start: Int, marks: List<Int>, private val save: (Int, List<Int>) -> Unit) : View(ctx) {
    var onClose: (() -> Unit)? = null
    private var shown = 0f
    private var closing = false
    private var at = start
    private val marks = marks.toMutableList()
    private var pages = 0                     // 0 until the PDF is open
    private var failed = false
    private var turn = 0f; private var turnDir = 0; private var from = 0   // a page turning: 0..1, which way, from which spread
    private val bitmaps = HashMap<Int, Bitmap>()
    private val asked = HashSet<Int>()
    private val work = Executors.newSingleThreadExecutor()   // PdfRenderer is used from this one thread only
    private val ui = Handler(Looper.getMainLooper())
    private var pdf: PdfRenderer? = null; private var fd: ParcelFileDescriptor? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var note = ""; private var noteT = 0f

    init {
        work.execute {
            try {
                fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); pdf = PdfRenderer(fd!!)
                val n = pdf!!.pageCount
                ui.post { pages = n; at = at.coerceIn(0, spreads() - 1); want(); invalidate() }
            } catch (e: Throwable) { ui.post { failed = true; invalidate() } }
        }
    }

    private fun spreads() = if (pages <= 1) 1 else 1 + (pages - 1 + 1) / 2
    private fun pagesOf(s: Int): List<Int> = if (s == 0) listOf(0) else listOf(2 * s - 1, 2 * s).filter { it < pages }

    /** Makes the spreads around this one ready, and lets go of the rest. */
    private fun want() {
        val keep = (at - 1..at + 1).flatMap { pagesOf(it.coerceIn(0, spreads() - 1)) }.toSet()
        bitmaps.keys.filter { it !in keep }.forEach { bitmaps.remove(it)?.recycle(); asked.remove(it) }
        for (pg in pagesOf(at) + keep) if (asked.add(pg)) work.execute {
            val b = try {
                pdf!!.openPage(pg).use { page ->
                    // as tall as the reader allows, but never wider than half of it (a scan of two pages side by side)
                    val s = min(PAGE_H / page.height, PAGE_MAX_W / page.width)
                    val w = (page.width * s).toInt().coerceAtLeast(1); val h = (page.height * s).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            } catch (e: Throwable) { null }
            ui.post { if (b == null) return@post; if (closing || pg !in asked) b.recycle() else { bitmaps[pg] = b; invalidate() } }
        }
    }

    fun input(a: String): Boolean {
        if (closing) return true
        if (pages == 0) { if (a == "b") close(); return true }
        when (a) {
            "left" -> go(at - 1)
            "right" -> go(at + 1)
            "y" -> { if (!marks.remove(at)) { marks += at; marks.sort(); say("Bookmarked") } else say("Bookmark removed"); Sounds.play(Sounds.PICK); save(at, marks) }
            "lb" -> marks.lastOrNull { it < at }?.let { go(it) } ?: say(if (marks.isEmpty()) "Y bookmarks a page" else "No bookmarks before this")
            "rb" -> marks.firstOrNull { it > at }?.let { go(it) } ?: say(if (marks.isEmpty()) "Y bookmarks a page" else "No more bookmarks ahead")
            "b", "a" -> close()
        }
        invalidate(); return true
    }
    private fun say(s: String) { note = s; noteT = 1.6f }
    private fun go(s: Int) {
        val to = s.coerceIn(0, spreads() - 1)
        if (to == at) return Sounds.play(Sounds.BUMP)
        from = at; turnDir = if (to > at) 1 else -1; turn = 0f; at = to
        want(); save(at, marks); Sounds.play(Sounds.TURN)
    }
    private fun close() { closing = true; save(at, marks); Sounds.play(Sounds.CLOSE) }

    fun tick(dt: Float) {
        shown = if (closing) maxOf(0f, shown - dt / .25f) else min(1f, shown + dt / .3f)
        if (turnDir != 0) { turn += dt / .5f; if (turn >= 1f) { turn = 0f; turnDir = 0 } }
        if (noteT > 0) noteT -= dt
        if (closing && shown == 0f) {
            work.execute { try { pdf?.close(); fd?.close() } catch (e: Throwable) { } }; work.shutdown()
            bitmaps.values.forEach { it.recycle() }; bitmaps.clear(); onClose?.invoke()
        }
        invalidate()
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        val k = Backdrop.ease(shown); val a = (255 * k).toInt()
        p.color = Color.argb((236 * k).toInt(), 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        val cx = Stage.W / 2; val top = 50f + (1 - k) * 24f
        if (pages == 0) { label(c, if (failed) "This manual couldn’t be opened" else "Opening the manual…", cx, Stage.H / 2, 24f, Theme.fg2, a); return }
        if (turnDir == 0) spread(c, at, cx, top, a)
        else {
            // a page folding over at the spine: under it, the spread it uncovers on one side and the one it leaves on the other
            val e = Backdrop.ease(turn)
            val now = pagesOf(at); val was = pagesOf(from)
            if (turnDir > 0) {
                side(c, was.firstOrNull()?.takeIf { from > 0 }, cx, top, left = true, a, single = from == 0)   // the old left page stays
                side(c, now.getOrNull(1) ?: now.firstOrNull()?.takeIf { at == 0 }, cx, top, left = false, a)      // the new right page, uncovered
                if (e < .5f) fold(c, if (from == 0) was[0] else was.getOrNull(1), cx, top, left = false, 1 - 2 * e, a)
                else fold(c, now.firstOrNull(), cx, top, left = true, 2 * e - 1, a)
            } else {
                side(c, now.firstOrNull()?.takeIf { at > 0 }, cx, top, left = true, a)
                side(c, was.getOrNull(1) ?: was.firstOrNull()?.takeIf { from == 0 }, cx, top, left = false, a)
                if (e < .5f) fold(c, was.firstOrNull()?.takeIf { from > 0 }, cx, top, left = true, 1 - 2 * e, a)
                else fold(c, if (at == 0) now[0] else now.getOrNull(1), cx, top, left = false, 2 * e - 1, a)
            }
        }
        // where you are
        val ps = pagesOf(at)
        label(c, if (at == 0) "$title · cover" else "Pages ${ps.first() + 1}${if (ps.size > 1) "–" + (ps.last() + 1) else ""} of $pages", cx, Stage.H - 24f, 20f, Theme.fg2, a)
        if (noteT > 0) label(c, note, cx, Stage.H - 58f, 21f, Theme.fg, (a * min(1f, noteT / .3f)).toInt())
    }

    /** One spread at rest: the cover alone, in the middle; pages either side of the spine. */
    private fun spread(c: Canvas, s: Int, cx: Float, top: Float, a: Int) {
        val ps = pagesOf(s)
        if (s == 0) { side(c, ps[0], cx, top, left = false, a, single = true); return }
        side(c, ps.getOrNull(0), cx, top, left = true, a)
        side(c, ps.getOrNull(1), cx, top, left = false, a)
        if (s in marks) ribbon(c, cx + 60f + pageW(ps.getOrNull(1) ?: ps[0]) * .62f, top, a)
    }

    private fun pageW(pg: Int?) = pg?.let { bitmaps[it]?.width?.toFloat() } ?: PAGE_H * .72f
    private fun pageH(pg: Int?) = pg?.let { bitmaps[it]?.height?.toFloat() } ?: PAGE_H

    /** A page beside the spine (or the cover, centred), with a soft shadow, the fold darkening toward the spine. */
    private fun side(c: Canvas, pg: Int?, cx: Float, top: Float, left: Boolean, a: Int, single: Boolean = false) {
        if (pg == null) return
        val w = pageW(pg); val h = pageH(pg); val x = if (single) cx - w / 2 else if (left) cx - w else cx; val y = top + (PAGE_H - h) / 2
        page(c, pg, RectF(x, y, x + w, y + h), a, if (single) 0 else if (left) 1 else -1)
    }

    /** The turning page, squeezed toward the spine as it folds over (1 flat, 0 edge on). */
    private fun fold(c: Canvas, pg: Int?, cx: Float, top: Float, left: Boolean, sx: Float, a: Int) {
        if (pg == null || sx <= .01f) return
        val w = pageW(pg) * sx; val h = pageH(pg); val x = if (left) cx - w else cx; val y = top + (PAGE_H - h) / 2
        page(c, pg, RectF(x, y - (1 - sx) * 10f, x + w, y + h + (1 - sx) * 10f), a, if (left) 1 else -1, lift = 1 - sx)
    }

    private fun page(c: Canvas, pg: Int, r: RectF, a: Int, spine: Int, lift: Float = 0f) {
        p.color = Color.BLACK; p.alpha = (a * (.35f + .25f * lift)).toInt(); c.drawRect(r.left + 6, r.top + 10, r.right + 6, r.bottom + 12, p)   // its shadow
        val b = bitmaps[pg]
        if (b == null) { p.color = Color.rgb(240, 238, 232); p.alpha = a; c.drawRect(r, p) }
        else { p.alpha = a; c.drawBitmap(b, null, r, p) }
        if (spine != 0) {   // the fold: a little darker toward the spine
            val sx = if (spine > 0) r.right else r.left
            p.shader = LinearGradient(sx, 0f, sx - spine * 70f, 0f, Color.argb((70 * a / 255f).toInt(), 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(r, p); p.shader = null
        }
        if (lift > 0) { p.color = Color.WHITE; p.alpha = (a * lift * .25f).toInt(); c.drawRect(r, p) }
        p.alpha = 255
    }

    private fun ribbon(c: Canvas, x: Float, top: Float, a: Int) {
        p.color = Theme.accentOf(null); p.alpha = a
        val path = android.graphics.Path().apply { moveTo(x, top - 6); lineTo(x + 26, top - 6); lineTo(x + 26, top + 70); lineTo(x + 13, top + 58); lineTo(x, top + 70); close() }
        c.drawPath(path, p); p.alpha = 255
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, a: Int) {
        p.typeface = Fonts.body; p.textSize = size; p.color = color; p.alpha = a; p.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, p); p.textAlign = Paint.Align.LEFT; p.alpha = 255
    }

    companion object { const val PAGE_H = 620f; const val PAGE_MAX_W = 640f }
}
