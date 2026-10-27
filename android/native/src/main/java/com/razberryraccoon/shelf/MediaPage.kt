package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * A game's media, all in one place (Game settings › Media): over the page, the game's name and the standard name it's
 * matched to (Change match), Find everything missing, and a tile for each kind of media (box front, spine, back,
 * cartridge art, title screen, screenshot, video, details, description, manual) showing what it has and where it came
 * from, or a dashed outline where it's missing. A on a tile lists every source's choices for it, each labelled with its
 * source; X finds a missing one on its own. B goes back.
 */
class MediaPage(ctx: Context, private val game: Game, private val title: String, private val meta: Meta, private val games: Games,
                private val cache: File, private val host: Host) : View(ctx) {
    interface Host {
        fun changeMatch()
        /** A file of your own for this kind, from Android's file picker; `then` hears whether it was kept. */
        fun chooseFile(kind: String, then: (Boolean) -> Unit)
        fun show(msg: String)
        /** Something was kept or removed: the page shows it. */
        fun changed()
    }
    var onClose: (() -> Unit)? = null
    private var shown = 0f
    private var closing = false
    private val kinds = Media.kindsFor(game.sys)
    private var info = meta.cached(game)
    private var zone = 1                 // 0: the buttons at the top, 1: the tiles
    private var btn = 1                  // 0 Change match, 1 Find everything missing
    private var k = 0                    // the tile
    private val lift = FloatArray(kinds.size)
    private val thumbs = HashMap<String, Bitmap?>()
    private var busy: String? = null     // what's being found, shown under the title
    private var choosing: Choosing? = null
    private val net = Executors.newSingleThreadExecutor()
    private val pics = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Theme.accentOf(game.sys)

    /** The choices for one kind: loading, then every source's, with their thumbnails as they arrive. */
    private class Choosing(val kind: String) { var list: List<Media.Choice>? = null; var k = 0; val thumbs = HashMap<Int, Bitmap?>(); var saving = false }

    init { kinds.forEach { loadThumb(it.id) } }

    private val name get() = info?.name ?: game.title
    private val saved get() = games.artKeys

    // ---------- the tiles' pictures ----------

    private fun loadThumb(kind: String) {
        val f = info?.pics?.get(kind)
        thumbs.remove(kind)?.recycle()
        if (f == null || !f.exists()) { invalidate(); return }
        pics.execute {
            val b = try {
                when (kind) {
                    "video" -> android.media.MediaMetadataRetriever().run { try { setDataSource(f.path); getFrameAtTime(2_000_000)?.let { scaled(it, 360) } } finally { release() } }
                    "manual" -> pdfCover(f, 300)
                    else -> meta.bitmap(f, 360)
                }
            } catch (e: Throwable) { null }   // a damaged file, or one too big to read, leaves the tile empty
            ui.post { if (closing) b?.recycle() else { thumbs[kind] = b; invalidate() } }
        }
    }
    private fun scaled(b: Bitmap, w: Int): Bitmap = if (b.width <= w) b else Bitmap.createScaledBitmap(b, w, (b.height * w.toFloat() / b.width).toInt(), true).also { b.recycle() }

    // ---------- buttons ----------

    fun input(a: String): Boolean {
        if (closing) return true
        choosing?.let { chooseInput(it, a); invalidate(); return true }
        when (a) {
            "left" -> if (zone == 0) move({ btn > 0 }) { btn-- } else move({ k % COLS > 0 }) { k-- }
            "right" -> if (zone == 0) move({ btn < 1 }) { btn++ } else move({ k % COLS < COLS - 1 && k < kinds.size - 1 }) { k++ }
            "up" -> if (zone == 1 && k >= COLS) { k -= COLS; Sounds.play(Sounds.MOVE) } else if (zone == 1) { zone = 0; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "down" -> if (zone == 0) { zone = 1; Sounds.play(Sounds.MOVE) } else if (k + COLS < kinds.size) { k += COLS; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "a" -> if (zone == 0) { Sounds.play(Sounds.PICK); if (btn == 0) host.changeMatch() else findAll() } else openChoices(kinds[k].id)
            "x" -> if (zone == 1) findOne(kinds[k].id)
            "b" -> { closing = true; Sounds.play(Sounds.CLOSE) }
        }
        invalidate(); return true
    }
    private inline fun move(can: () -> Boolean, go: () -> Unit) { if (can()) { go(); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP) }

    /** After Change match: everything is looked up again for the new name. */
    fun matched(i: Meta.Info?) { info = i; kinds.forEach { loadThumb(it.id) }; invalidate() }

    private fun findAll() {
        if (busy != null) return Sounds.play(Sounds.BUMP)
        busy = "Looking…"
        net.execute {
            val found = Media.findMissing(game, name, info, saved, cache, meta) { what -> ui.post { busy = "Finding the ${what.lowercase()}…"; invalidate() } }
            ui.post {
                busy = null; refreshAll()
                host.show(if (found.isEmpty()) "Nothing more found. More sources can be connected in Settings › Media."
                    else "Found " + found.joinToString(", ") { Media.name(it, game.sys).lowercase() })
            }
        }
    }

    private fun findOne(kind: String) {
        if (busy != null) return Sounds.play(Sounds.BUMP)
        Sounds.play(Sounds.PICK); busy = "Finding the ${Media.name(kind, game.sys).lowercase()}…"
        net.execute {
            var from: String? = null
            for (id in Media.ORDER[kind] ?: emptyList()) {
                val cs = try { Media.choices(kind, game.sys, name, File(game.path), saved, cache, meta, only = id) } catch (e: Exception) { emptyList() }
                cs.take(3).firstOrNull { Media.keep(game, kind, it, meta) }?.let { from = it.source }
                if (from != null) break
            }
            ui.post { busy = null; refreshAll(); host.show(from?.let { "Found it on $it" } ?: "None of your sources has it. More can be connected in Settings › Media.") }
        }
    }

    /** Re-reads what's known (after a choice is kept, which lands on the main thread just before this). */
    private fun refreshAll() { ui.post { info = meta.cached(game); kinds.forEach { loadThumb(it.id) }; host.changed(); invalidate() } }

    private fun openChoices(kind: String) {
        Sounds.play(Sounds.OPEN)
        val c = Choosing(kind); choosing = c
        net.execute {
            val list = try { Media.choices(kind, game.sys, name, File(game.path), saved, cache, meta) } catch (e: Exception) { emptyList() }
            ui.post {
                if (choosing !== c) return@post
                c.list = list; invalidate()
                list.forEachIndexed { i, ch -> ch.thumb?.takeIf { !Media.kind(kind).text && kind != "video" && kind != "manual" }?.let { u ->
                    pics.execute { val b = try { ArtIndex.download(u)?.let { bytes -> decode(bytes, 300) } } catch (e: Exception) { null }
                        ui.post { if (choosing === c) { c.thumbs[i] = b; invalidate() } else b?.recycle() } } } }
            }
        }
    }
    private fun decode(bytes: ByteArray, w: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1; while (o.outWidth / (s * 2) >= w) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun chooseInput(c: Choosing, a: String) {
        val n = (c.list?.size ?: 0) + extras(c.kind).size   // after the sources' choices: a file of your own, and removing it
        val cols = if (wide(c.kind)) 1 else CCOLS
        when (a) {
            "left" -> if (cols > 1 && c.k % cols > 0) { c.k--; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "right" -> if (cols > 1 && c.k % cols < cols - 1 && c.k < n - 1) { c.k++; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "up" -> if (c.k >= cols) { c.k -= cols; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "down" -> if (c.k + cols < n) { c.k += cols; Sounds.play(Sounds.MOVE) } else if (c.k / cols < (n - 1) / cols) { c.k = n - 1; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "b" -> { close(c); Sounds.play(Sounds.CLOSE) }
            "a" -> {
                val list = c.list ?: return
                if (c.saving || n == 0) return Sounds.play(Sounds.BUMP)
                if (c.k >= list.size && extras(c.kind)[c.k - list.size] == CHOOSE) {   // a file of your own, from Android's picker
                    Sounds.play(Sounds.PICK)
                    host.chooseFile(c.kind) { ok -> if (ok) { close(c); refreshAll(); host.show("Kept your file") } else host.show("That file couldn’t be used") }
                    return
                }
                if (c.k >= list.size) {   // remove it
                    if (Media.kind(c.kind).text) meta.clearText(game, c.kind) else meta.setMedia(game, c.kind, null, null, null)
                    Sounds.play(Sounds.PICK); close(c); refreshAll(); host.show("Removed"); return
                }
                val ch = list[c.k]; c.saving = true; Sounds.play(Sounds.PICK)
                net.execute {
                    val ok = Media.keep(game, c.kind, ch, meta)
                    ui.post { c.saving = false; if (ok) { close(c); refreshAll(); host.show("Kept, from ${ch.source}") } else { host.show("That one wouldn’t download. Try another."); invalidate() } }
                }
            }
        }
    }
    private fun close(c: Choosing) { c.thumbs.values.forEach { it?.recycle() }; if (choosing === c) choosing = null }
    /** The choices after the sources' own: a file of your own (pictures, video, manual), and removing what it has. */
    private fun extras(kind: String) = listOfNotNull(CHOOSE.takeIf { !Media.kind(kind).text }, "Remove it".takeIf { Media.has(info, kind) })
    private fun wide(kind: String) = Media.kind(kind).text || kind == "video" || kind == "manual"

    fun tick(dt: Float) {
        shown = if (closing) maxOf(0f, shown - dt / .22f) else min(1f, shown + dt / .3f)
        for (j in lift.indices) lift[j] += ((if (zone == 1 && j == k && choosing == null) 1f else 0f) - lift[j]) * min(1f, dt * 12f)
        if (closing && shown == 0f) { net.shutdownNow(); pics.shutdownNow(); thumbs.values.forEach { it?.recycle() }; choosing?.let { close(it) }; onClose?.invoke() }
        invalidate()
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        val e = Backdrop.ease(shown)
        p.color = Color.argb((240 * e).toInt(), 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        c.save(); c.translate(0f, (1 - e) * 30f)
        val a = (255 * e).toInt()
        // the game and what it's matched to
        text(c, title, 96f, 92f, 40f, Fonts.display, Theme.fg, a, 760f)
        val how = if (games.match(game) != null) "chosen by you" else "found by Shelf"
        text(c, busy ?: info?.name?.let { "Matched to $it · $how" } ?: "Not matched to a standard name yet: Change match picks it", 96f, 132f, 21f, Fonts.body, if (busy != null) accent else Theme.fg2, a, 820f)
        button(c, "Change match", 0, Stage.W - 96f - 290f - 18f - 210f, 64f, 210f, a)
        button(c, "Find everything missing", 1, Stage.W - 96f - 290f, 64f, 290f, a)
        // the tiles
        val x0 = (Stage.W - (COLS * TW + (COLS - 1) * GAP)) / 2
        kinds.forEachIndexed { j, kd ->
            val x = x0 + (j % COLS) * (TW + GAP); val y = 196f + (j / COLS) * 262f - lift[j] * 6f
            tile(c, kd, x, y, j == k && zone == 1 && choosing == null, a)
        }
        if (choosing == null) {
            tp.typeface = Fonts.body; tp.textSize = 19f; tp.color = Theme.fg3; tp.alpha = (140 * e).toInt(); tp.textAlign = Paint.Align.CENTER
            c.drawText("A  every source’s choices      X  find a missing one      B  back", Stage.W / 2, Stage.H - 26f, tp); tp.textAlign = Paint.Align.LEFT
        }
        c.restore()
        choosing?.let { drawChoices(c, it, a) }
    }

    private fun tile(c: Canvas, kd: Media.Kind, x: Float, y: Float, on: Boolean, a: Int) {
        val r = RectF(x, y, x + TW, y + TH); val has = Media.has(info, kd.id)
        p.style = Paint.Style.FILL; p.color = Theme.chip; p.alpha = a * 26 / 255; c.drawRoundRect(r, 18f, 18f, p); p.alpha = 255
        val b = thumbs[kd.id]
        when {
            b != null -> { cover(c, b, r, a); if (kd.id == "video") play(c, r, a) }
            has && kd.id == "details" -> paragraph(c, info?.fields?.let { f -> listOfNotNull(f["releaseyear"], f["developer"], f["publisher"], f["genre"]).joinToString("\n") } ?: "", r, 19f, Theme.fg, a)
            has && kd.id == "description" -> paragraph(c, info?.fields?.get("description") ?: "", r, 16f, Theme.fg2, a)
            has -> { if (kd.id == "video") play(c, r, a) }
            else -> {   // missing: a dashed outline
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.fg3; p.alpha = a * 110 / 255; p.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
                c.drawRoundRect(RectF(r.left + 3, r.top + 3, r.right - 3, r.bottom - 3), 16f, 16f, p); p.pathEffect = null; p.style = Paint.Style.FILL; p.alpha = 255
                text(c, "Missing", r.centerX(), r.centerY() + 7f, 20f, Fonts.body, Theme.fg3, a * 160 / 255, 0f, center = true)
            }
        }
        if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; p.alpha = a; c.drawRoundRect(RectF(r.left - 6, r.top - 6, r.right + 6, r.bottom + 6), 23f, 23f, p); p.style = Paint.Style.FILL; p.alpha = 255 }
        text(c, Media.name(kd.id, game.sys), x + 2, y + TH + 32f, 21f, if (on) Fonts.bold else Fonts.body, if (on) Theme.fg else Theme.fg2, a, TW)
        val src = info?.src?.get(kd.id)
        text(c, if (has) src ?: "On this handheld" else "Missing", x + 2, y + TH + 58f, 18f, Fonts.body, if (has) Theme.fg3 else accent, a * (if (has) 200 else 230) / 255, TW)
    }

    private fun drawChoices(c: Canvas, ch: Choosing, a: Int) {
        p.color = Color.argb(a * 150 / 255, 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        val w = 1150f; val h = 600f; val x = (Stage.W - w) / 2; val y = (Stage.H - h) / 2
        p.color = Color.rgb(16, 21, 50); p.alpha = a; c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Theme.line; c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p); p.style = Paint.Style.FILL; p.alpha = 255
        text(c, Media.name(ch.kind, game.sys), x + 44, y + 62, 30f, Fonts.display, Theme.fg, a, 600f)
        val list = ch.list
        val from = (Media.ORDER[ch.kind] ?: emptyList()).filter { Media.connected(it, saved) && (game.sys != "android" || Media.source(it).android) }.map { Media.source(it).name }
        text(c, if (list == null) "Searching " + from.joinToString(", ") + "…" else if (list.isEmpty()) "Nothing found on " + from.joinToString(", ") + ". Settings › Media connects more sources."
            else "${list.size} from " + list.map { it.source }.distinct().joinToString(", ") + if (ch.saving) " · keeping it…" else "", x + 44, y + 98, 20f, Fonts.body, if (ch.saving) accent else Theme.fg2, a, w - 88)
        if (list == null) return
        val ex = extras(ch.kind); val items = list.size + ex.size
        c.save(); c.clipRect(x + 20, y + 120, x + w - 20, y + h - 20)
        if (wide(ch.kind)) {
            val rh = 96f; val top = (ch.k - 3).coerceAtLeast(0)
            for (i in top until items) {
                val ry = y + 130 + (i - top) * (rh + 12); if (ry > y + h) break
                val r = RectF(x + 40, ry, x + w - 40, ry + rh); val on = i == ch.k
                p.color = Theme.chip; p.alpha = a * (if (on) 40 else 20) / 255; c.drawRoundRect(r, 20f, 20f, p); p.alpha = 255
                if (on) ring(c, r, a)
                if (i >= list.size) { text(c, ex[i - list.size], r.left + 26, r.centerY() + 8, 22f, Fonts.bold, Theme.fg, a, 600f); continue }
                val o = list[i]
                text(c, o.source, r.left + 26, r.top + 36, 21f, Fonts.bold, Theme.fg, a, 300f)
                text(c, o.game, r.left + 330, r.top + 36, 19f, Fonts.body, Theme.fg2, a, r.width() - 360)
                text(c, (o.note ?: "").replace('\n', ' '), r.left + 26, r.top + 70, 18f, Fonts.body, Theme.fg3, a, r.width() - 52)
            }
        } else {
            val cw = (w - 80 - (CCOLS - 1) * 22f) / CCOLS; val chh = 170f; val rowH = chh + 66f
            val row0 = (ch.k / CCOLS - 1).coerceAtLeast(0)
            for (i in row0 * CCOLS until items) {
                val cx = x + 40 + (i % CCOLS) * (cw + 22); val cy = y + 132 + (i / CCOLS - row0) * rowH; if (cy > y + h) break
                val r = RectF(cx, cy, cx + cw, cy + chh); val on = i == ch.k
                p.color = Theme.chip; p.alpha = a * 26 / 255; c.drawRoundRect(r, 16f, 16f, p); p.alpha = 255
                if (i >= list.size) text(c, ex[i - list.size], r.centerX(), r.centerY() + 8, 22f, Fonts.bold, Theme.fg, a, 0f, center = true)
                else {
                    ch.thumbs[i]?.let { contain(c, it, r, a) } ?: text(c, if (ch.thumbs.containsKey(i)) "No picture" else "…", r.centerX(), r.centerY() + 7, 19f, Fonts.body, Theme.fg3, a, 0f, center = true)
                    text(c, list[i].source, cx + 2, cy + chh + 28, 19f, Fonts.bold, if (on) Theme.fg else Theme.fg2, a, cw)
                    text(c, list[i].game, cx + 2, cy + chh + 52, 16f, Fonts.body, Theme.fg3, a, cw)
                }
                if (on) ring(c, r, a)
            }
        }
        c.restore()
    }

    // ---------- small drawing helpers ----------

    private fun ring(c: Canvas, r: RectF, a: Int) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; p.alpha = a; c.drawRoundRect(RectF(r.left - 5, r.top - 5, r.right + 5, r.bottom + 5), 21f, 21f, p); p.style = Paint.Style.FILL; p.alpha = 255 }
    private fun button(c: Canvas, s: String, i: Int, x: Float, y: Float, w: Float, a: Int) {
        val on = zone == 0 && btn == i && choosing == null; val r = RectF(x, y, x + w, y + 50)
        p.color = if (on) Theme.sel else Theme.chip; p.alpha = if (on) a else a * 26 / 255; c.drawRoundRect(r, 25f, 25f, p); p.alpha = 255
        text(c, s, r.centerX(), r.centerY() + 7, 20f, Fonts.bold, if (on) Theme.selFg else Theme.fg, a, 0f, center = true)
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, face: android.graphics.Typeface, color: Int, a: Int, maxW: Float, center: Boolean = false) {
        tp.typeface = face; tp.textSize = size; tp.color = color; tp.alpha = a * Color.alpha(color) / 255; tp.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(if (maxW > 0) TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString() else s, x, y, tp); tp.textAlign = Paint.Align.LEFT
    }
    private fun paragraph(c: Canvas, s: String, r: RectF, size: Float, color: Int, a: Int) {
        tp.typeface = Fonts.body; tp.textSize = size; tp.color = color; tp.alpha = a
        val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, (r.width() - 28).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(((r.height() - 24) / (size * 1.25f)).toInt()).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.translate(r.left + 14, r.top + 12); l.draw(c); c.restore()
    }
    /** A picture filling a tile (cropped to it). */
    private fun cover(c: Canvas, b: Bitmap, r: RectF, a: Int) {
        val s = maxOf(r.width() / b.width, r.height() / b.height)
        p.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(r.centerX() - b.width * s / 2, r.centerY() - b.height * s / 2) }) }
        p.alpha = a; c.drawRoundRect(r, 18f, 18f, p); p.shader = null; p.alpha = 255
    }
    /** A whole picture inside a card (choices are seen whole, to judge them). */
    private fun contain(c: Canvas, b: Bitmap, r: RectF, a: Int) {
        val s = min((r.width() - 16) / b.width, (r.height() - 16) / b.height); val w = b.width * s; val h = b.height * s
        val d = RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2)
        p.alpha = a; c.drawBitmap(b, null, d, p); p.alpha = 255
    }
    private fun play(c: Canvas, r: RectF, a: Int) {
        p.color = Color.argb(a * 150 / 255, 5, 8, 22); c.drawCircle(r.centerX(), r.centerY(), 30f, p)
        val path = android.graphics.Path().apply { moveTo(r.centerX() - 9, r.centerY() - 14); lineTo(r.centerX() + 15, r.centerY()); lineTo(r.centerX() - 9, r.centerY() + 14); close() }
        p.color = Theme.fg; p.alpha = a; c.drawPath(path, p); p.alpha = 255
    }

    companion object {
        private const val COLS = 5; private const val CCOLS = 4
        private const val CHOOSE = "Choose a file…"
        private const val TW = 204f; private const val TH = 150f; private const val GAP = 34f

        /** A PDF's first page, about `w` wide (a manual's cover). */
        fun pdfCover(f: File, w: Int): Bitmap? = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { pg ->
                    val h = (w * pg.height.toFloat() / pg.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b -> b.eraseColor(Color.WHITE); pg.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            }
        }
    }
}
