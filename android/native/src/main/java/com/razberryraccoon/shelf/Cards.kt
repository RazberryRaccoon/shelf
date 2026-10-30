package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * A card over everything with a list on it: the collections card (X on a game), a list's menu (Y in the Library), the
 * colors and orders to choose from. Rows can carry a color dot, a check that ticks on and off where you are (the card
 * stays open), or an icon; A on a row calls `onRow`, which returns true to close the card. Its rows come from `rows`,
 * asked again after every press, so ticks show at once.
 */
class ListCard(ctx: Context, private val heading: String, private val sub: String?, private val rows: () -> List<Row>, start: Int = 0,
               private val onRow: (Int, Row) -> Boolean) : View(ctx), Overlay {
    class Row(val label: String, val note: String = "", val dot: Int? = null, val check: Boolean? = null, val icon: String? = null, val tag: Any? = null)
    override var onClose: (() -> Unit)? = null
    private var k = start
    private var top = 0
    private var shown = 0f; private var closing = false; private var last = 0L
    private var popAt = -1; private var pop = 1f   // the tick that just changed pops a little
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    companion object { const val SHOW = 6 }

    override fun input(a: String): Boolean {
        if (closing) return true
        val list = rows()
        when (a) {
            "up" -> if (k > 0) { k--; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "down" -> if (k < list.size - 1) { k++; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "a" -> list.getOrNull(k)?.let { r ->
                Sounds.play(if (r.check != null) Sounds.CLICK else Sounds.PICK)
                if (r.check != null) { popAt = k; pop = 0f }
                if (onRow(k, r)) close()
            }
            "b" -> { Sounds.play(Sounds.CLOSE); close() }
        }
        k = k.coerceIn(0, maxOf(0, rows().size - 1))
        top = top.coerceIn(maxOf(0, k - SHOW + 1), k); invalidate(); return true
    }

    fun close() { closing = true; invalidate() }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); val dt = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f); last = now
        shown = if (closing) maxOf(0f, shown - dt / .14f) else min(1f, shown + dt / .2f)
        pop = min(1f, pop + dt / .25f)
        if (closing && shown == 0f) { onClose?.invoke(); return }
        val e = Backdrop.ease(shown)
        val list = rows(); val n = min(SHOW, list.size); val w = 700f; val rowH = 74f
        val h = (if (sub != null) 126 else 104) + n * rowH + 26; val x = (Stage.W - w) / 2; val y = (Stage.H - h) / 2 + (1 - e) * 24f
        p.color = Color.argb((150 * e).toInt(), 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)   // the scrim
        c.saveLayerAlpha(0f, 0f, Stage.W, Stage.H, (255 * e).toInt())
        p.color = Color.rgb(16, 21, 50); c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Theme.line; c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p); p.style = Paint.Style.FILL
        p.typeface = Fonts.display; p.textSize = 27f; p.color = Theme.fg
        c.drawText(TextUtils.ellipsize(heading, android.text.TextPaint(p), w - 80, TextUtils.TruncateAt.END).toString(), x + 40, y + 60, p)
        if (sub != null) { p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2
            c.drawText(TextUtils.ellipsize(sub, android.text.TextPaint(p), w - 80, TextUtils.TruncateAt.END).toString(), x + 40, y + 92, p) }
        val y0 = y + (if (sub != null) 116 else 92)
        for (j in 0 until n) {
            val r = list[top + j]; val ry = y0 + j * rowH; val on = top + j == k
            if (on) { p.color = Theme.chip; c.drawRoundRect(RectF(x + 20, ry, x + w - 20, ry + rowH - 8), 21f, 21f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.sel; c.drawRoundRect(RectF(x + 20, ry, x + w - 20, ry + rowH - 8), 21f, 21f, p); p.style = Paint.Style.FILL }
            var tx = x + 44
            val mid = ry + (rowH - 8) / 2
            r.dot?.let { p.color = it; c.drawCircle(tx + 9, mid, 9f, p); tx += 34 }
            r.icon?.let { icon(c, it, tx + 11, mid, Theme.fg2); tx += 36 }
            p.typeface = Fonts.bold; p.textSize = 23f; p.color = Theme.fg
            val right = if (r.check != null) x + w - 96 else x + w - 44
            c.drawText(TextUtils.ellipsize(r.label, android.text.TextPaint(p), right - tx - (if (r.note.isNotEmpty()) 150 else 0), TextUtils.TruncateAt.END).toString(), tx, mid + 8, p)
            if (r.note.isNotEmpty()) { p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2; p.textAlign = Paint.Align.RIGHT
                c.drawText(r.note, right - 6, mid + 7, p); p.textAlign = Paint.Align.LEFT }
            r.check?.let { checked ->
                // a round tick on the right: filled in the row's color when it's in, an empty ring when it isn't
                val cx = x + w - 62; val s = if (top + j == popAt) 1f + .25f * sin(Math.PI.toFloat() * pop) else 1f
                if (checked) { p.color = r.dot ?: Theme.sel; c.drawCircle(cx, mid, 15f * s, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = 3.2f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = Theme.selFg
                    c.drawPath(Path().apply { moveTo(cx - 7 * s, mid); lineTo(cx - 2 * s, mid + 5 * s); lineTo(cx + 7 * s, mid - 5 * s) }, p); p.style = Paint.Style.FILL }
                else { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.fg3; c.drawCircle(cx, mid, 14f * s, p); p.style = Paint.Style.FILL }
            }
        }
        // more below or above: a soft hint of it
        if (list.size > SHOW) { p.typeface = Fonts.body; p.textSize = 17f; p.color = Theme.fg3; p.textAlign = Paint.Align.RIGHT
            c.drawText("${k + 1} of ${list.size}", x + w - 40, y + 60, p); p.textAlign = Paint.Align.LEFT }
        c.restore()
        if (shown < 1f || pop < 1f || closing) postInvalidateOnAnimation()
    }

    /** Small line icons: a heart, a lock, a plus, a pencil, a bin, arrows (order), a grid (add games), a palette. */
    private fun icon(c: Canvas, kind: String, cx: Float, cy: Float, ink: Int) {
        p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        when (kind) {
            "heart" -> c.drawPath(Path().apply { moveTo(cx, cy + 9); cubicTo(cx - 13, cy, cx - 11, cy - 10, cx - 5, cy - 10); cubicTo(cx - 2, cy - 10, cx, cy - 7, cx, cy - 5)
                cubicTo(cx, cy - 7, cx + 2, cy - 10, cx + 5, cy - 10); cubicTo(cx + 11, cy - 10, cx + 13, cy, cx, cy + 9); close() }, p)
            "lock" -> { c.drawRoundRect(RectF(cx - 9, cy - 2, cx + 9, cy + 11), 3f, 3f, p); c.drawArc(RectF(cx - 6, cy - 11, cx + 6, cy + 1), 180f, 180f, false, p) }
            "plus" -> { c.drawLine(cx - 9, cy, cx + 9, cy, p); c.drawLine(cx, cy - 9, cx, cy + 9, p) }
            "pencil" -> { c.drawLine(cx - 8, cy + 8, cx + 7, cy - 7, p); c.drawLine(cx - 9, cy + 9, cx - 5, cy + 9, p) }
            "bin" -> { c.drawLine(cx - 9, cy - 7, cx + 9, cy - 7, p); c.drawRoundRect(RectF(cx - 7, cy - 7, cx + 7, cy + 10), 2f, 2f, p); c.drawLine(cx - 3, cy - 10, cx + 3, cy - 10, p) }
            "sort" -> { c.drawLine(cx - 9, cy - 7, cx + 9, cy - 7, p); c.drawLine(cx - 9, cy, cx + 4, cy, p); c.drawLine(cx - 9, cy + 7, cx - 1, cy + 7, p) }
            "grid" -> for (gx in 0..1) for (gy in 0..1) c.drawRoundRect(RectF(cx - 9 + gx * 10f, cy - 9 + gy * 10f, cx - 1 + gx * 10f, cy - 1 + gy * 10f), 2f, 2f, p)
            "move" -> { c.drawLine(cx, cy - 10, cx, cy + 10, p); c.drawLine(cx - 4, cy - 6, cx, cy - 10, p); c.drawLine(cx + 4, cy - 6, cx, cy - 10, p); c.drawLine(cx - 4, cy + 6, cx, cy + 10, p); c.drawLine(cx + 4, cy + 6, cx, cy + 10, p) }
            "palette" -> { c.drawCircle(cx, cy, 10f, p); p.style = Paint.Style.FILL; c.drawCircle(cx - 4, cy - 3, 2f, p); c.drawCircle(cx + 3, cy - 5, 2f, p); c.drawCircle(cx + 5, cy + 2, 2f, p) }
            "eye" -> { c.drawArc(RectF(cx - 11, cy - 7, cx + 11, cy + 9), 200f, 140f, false, p); c.drawArc(RectF(cx - 11, cy - 9, cx + 11, cy + 7), 20f, 140f, false, p); p.style = Paint.Style.FILL; c.drawCircle(cx, cy, 3f, p) }
        }
        p.style = Paint.Style.FILL
    }
}

/**
 * The hidden games' code: four presses of the d-pad and A, B, X, Y (B is one of them, so Start or Select backs out).
 * Four dots fill as you press, never showing which buttons. A wrong code empties them softly. `onCode` gets the four
 * presses and says what happened: OK (it closes), WRONG (try again) or AGAIN (ask for it once more, to confirm a new one).
 */
class CodePad(ctx: Context, private var heading: String, private var sub: String, private val onCode: (List<String>) -> Int) : View(ctx), Overlay {
    companion object { const val OK = 0; const val WRONG = 1; const val AGAIN = 2 }
    override var onClose: (() -> Unit)? = null
    private val got = ArrayList<String>()
    private var shown = 0f; private var closing = false; private var last = 0L
    private var wrong = 0f   // after a wrong code: the dots glow coral and fade empty
    private var filled = FloatArray(4)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun input(a: String): Boolean {
        if (closing || wrong > 0f) return true
        if (a == "start" || a == "select") { Sounds.play(Sounds.CLOSE); closing = true; invalidate(); return true }
        if (a !in Collections.KEYS) return true
        got += a; Sounds.play(Sounds.MOVE)
        if (got.size == 4) when (onCode(got.toList())) {
            OK -> { Sounds.play(Sounds.CHIME); closing = true }
            WRONG -> { Sounds.play(Sounds.BUMP); wrong = 1f }
            AGAIN -> { Sounds.play(Sounds.PICK); got.clear(); filled.fill(0f) }
        }
        invalidate(); return true
    }

    /** Changes the words (setting a code: "choose" then "once more"). */
    fun say(h: String, s: String) { heading = h; sub = s; invalidate() }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); val dt = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f); last = now
        shown = if (closing) maxOf(0f, shown - dt / .16f) else min(1f, shown + dt / .22f)
        if (closing && shown == 0f) { onClose?.invoke(); return }
        if (wrong > 0f) { wrong = maxOf(0f, wrong - dt / .7f); if (wrong == 0f) { got.clear(); filled.fill(0f) } }
        for (j in 0 until 4) filled[j] += ((if (j < got.size) 1f else 0f) - filled[j]) * min(1f, dt * 16f)
        val e = Backdrop.ease(shown)
        p.color = Color.argb((170 * e).toInt(), 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        c.saveLayerAlpha(0f, 0f, Stage.W, Stage.H, (255 * e).toInt())
        val w = 560f; val h = 330f; val x = (Stage.W - w) / 2; val y = (Stage.H - h) / 2 + (1 - e) * 24f
        p.color = Color.rgb(16, 21, 50); c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Theme.line; c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p); p.style = Paint.Style.FILL
        // a small lock above the heading
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Theme.fg2; p.strokeCap = Paint.Cap.ROUND
        c.drawRoundRect(RectF(Stage.W / 2 - 14, y + 44, Stage.W / 2 + 14, y + 66), 5f, 5f, p); c.drawArc(RectF(Stage.W / 2 - 9, y + 28, Stage.W / 2 + 9, y + 48), 180f, 180f, false, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.typeface = Fonts.display; p.textSize = 27f; p.color = Theme.fg; c.drawText(heading, Stage.W / 2, y + 112, p)
        p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2
        Fonts.wrap(p, sub, w - 80).take(2).forEachIndexed { j, l -> c.drawText(l, Stage.W / 2, y + 146 + j * 26, p) }
        // the four dots
        val coral = Color.parseColor("#ff8a7a")
        for (j in 0 until 4) {
            val cx = Stage.W / 2 + (j - 1.5f) * 54f; val cy = y + 230
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = if (wrong > 0f) coral else Theme.fg3; c.drawCircle(cx, cy, 14f, p)
            p.style = Paint.Style.FILL; p.color = if (wrong > 0f) Color.argb((255 * wrong).toInt(), Color.red(coral), Color.green(coral), Color.blue(coral)) else Theme.sel
            if (filled[j] > .01f) c.drawCircle(cx, cy, 10f * filled[j], p)
        }
        p.typeface = Fonts.body; p.textSize = 17f; p.color = Theme.fg3
        c.drawText("Any four of the d-pad and A, B, X, Y  ·  Start to cancel", Stage.W / 2, y + h - 30, p)
        p.textAlign = Paint.Align.LEFT
        c.restore()
        if (shown < 1f || closing || wrong > 0f || filled.any { it in .01f..0.99f }) postInvalidateOnAnimation()
    }
}

/** Something over everything that takes the buttons while it's open (a card, the code pad). */
interface Overlay { fun input(a: String): Boolean; var onClose: (() -> Unit)? }

/** The cards and pads open over everything: the newest takes the buttons. The activity routes buttons here first. */
object Overlays {
    var board: Board? = null
    private val stack = ArrayList<Overlay>()
    val open get() = stack.isNotEmpty()
    fun show(o: Overlay) {
        val v = o as View; val b = board ?: return
        if (stack.isEmpty()) Sounds.play(Sounds.OPEN)
        val then = o.onClose
        o.onClose = { b.removeView(v); stack.remove(o); then?.invoke() }
        stack += o; b.put(v, 0, 0, 1334, 750)
    }
    fun input(a: String): Boolean = stack.lastOrNull()?.input(a) != null
}

/**
 * The collections card for one game (X on Home and in the Library; the Collections tile on its page): Favorites and
 * each of your collections with a tick to put it in or take it out, a new collection (named, then the game goes in), and
 * Hide this game. `changed` hears about anything that changed (`hidden` true when it's been hidden).
 */
object GameCard {
    fun show(ctx: Context, games: Games, cols: Collections, g: Game, askText: (String, String, (String) -> Unit) -> Unit, show: (String) -> Unit, changed: (hidden: Boolean) -> Unit) {
        lateinit var card: ListCard
        card = ListCard(ctx, "Collections", g.title, {
            listOf(ListCard.Row("Favorites", icon = "heart", check = games.favorite(g), tag = "fav")) +
                cols.all.filter { !it.live }.map { ListCard.Row(it.name, dot = it.color, check = cols.has(it, g), tag = it) } +
                listOf(ListCard.Row("New collection…", icon = "plus", tag = "new"),
                    if (cols.hidden(g)) ListCard.Row("Show this game again", note = "Out of Hidden", icon = "eye", tag = "unhide") else ListCard.Row("Hide this game", note = "Library › Hidden", icon = "lock", tag = "hide"))
        }) { _, r ->
            when (val t = r.tag) {
                "fav" -> { games.setFavorite(g, !games.favorite(g)); changed(false); false }
                is Collections.Collection -> { cols.toggle(t, g); changed(false); false }
                "new" -> { askText("Name the new collection", "") { name -> if (name.isNotBlank()) { cols.add(cols.create(name.trim().take(40)), g); changed(false); card.invalidate() } }; false }
                "hide" -> { hide(ctx, cols, g, show) { changed(true) }; true }
                "unhide" -> { cols.setHidden(g, false); show("${g.title} is back in your Library"); changed(false); true }
                else -> false
            }
        }
        Overlays.show(card)
    }

    /** Hides a game; the first time, a code is chosen first (entered twice). */
    fun hide(ctx: Context, cols: Collections, g: Game, show: (String) -> Unit, then: () -> Unit) {
        fun done() { cols.setHidden(g, true); show("Hidden. Library › Hidden shows it again."); then() }
        if (cols.hasCode) return done()
        chooseCode(ctx, cols, "Choose a code for hidden games", "Four presses of the d-pad and A, B, X, Y. You'll need it to see them again.") { done() }
    }

    /** Choosing a new code: entered, then once more to be sure. */
    fun chooseCode(ctx: Context, cols: Collections, heading: String, sub: String, then: () -> Unit) {
        var first: List<String>? = null
        lateinit var pad: CodePad
        pad = CodePad(ctx, heading, sub) { code ->
            val f = first
            when {
                f == null -> { first = code; pad.say("Once more", "The same four presses, to be sure."); CodePad.AGAIN }
                f == code -> { cols.code = code; then(); CodePad.OK }
                else -> { first = null; pad.say(heading, "Those didn't match. Choose your four presses again."); CodePad.WRONG }
            }
        }
        Overlays.show(pad)
    }

    /** Asking for the code; `then` when it's right. */
    fun askCode(ctx: Context, cols: Collections, then: () -> Unit) {
        Overlays.show(CodePad(ctx, "Hidden games", "Enter your code.") { code -> if (code == cols.code) { then(); CodePad.OK } else CodePad.WRONG })
    }
}
