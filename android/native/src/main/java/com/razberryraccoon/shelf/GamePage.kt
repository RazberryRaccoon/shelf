package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * A game's page, opened in place from Home (Down): its cartridge lifts out of the row and grows on the left, where the
 * right stick turns it in your hands (it keeps spinning a moment when you let go, then settles back to facing you) and
 * X flips it over. The key light follows a little as it turns, so the plastic catches a sweep of highlight. On the
 * right, the details and a row of icon tiles: Play, Emulator, Shell color (previewed live on the big cartridge),
 * Favorite and Back. Up or B puts it back.
 */
class GamePage(private val ctx: Context, private val board: Board, private val backdrop: Backdrop, private val stage: Stage,
               private val games: Games, private val carts: Carts, val game: Game, var cart: Cart?, private val from: FloatArray, private val host: Host) {
    interface Host {
        fun play(g: Game)
        fun chooseEmulator(g: Game)
        fun cartChanged(g: Game, c: Cart?)
        fun closed(page: GamePage)
    }

    private var t = 0f                  // 0 = in the row, 1 = open
    private var closing = false
    /** How open the page is (0..1), eased; Home fades its own details out by this much. */
    val shownK get() = Backdrop.ease(t)
    private var time = 0f

    // turning it in your hands
    private var stickX = 0f; private var stickY = 0f
    private val restPitch = -.1f
    private var restYaw = -.28f          // the front, a little turned so its side shows; X adds a half turn to show the back
    private var yaw = -.2f; private var pitch = -.1f
    private var vYaw = 0f; private var vPitch = 0f
    private var idle = 9f                // seconds since the stick was last used
    private var everTurned = false

    // the panel on the right
    private val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(44, 38, 44, 30); clipToPadding = false; alpha = 0f }
    private val chipText = text(21f, Fonts.bold, Theme.fg)
    private val chipDot = View(ctx)
    private val favChip = text(21f, Fonts.bold, Theme.fg).apply { this.text = "Favorite"; background = round(Theme.chip, 19f); setPadding(16, 6, 16, 6) }
    private val title = text(40f, Fonts.display, Theme.fg).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.08f) }
    private val facts = text(22f, Fonts.body, Theme.fg2).apply { setLineSpacing(0f, 1.45f) }
    private val tiles = Tiles(ctx)
    private val note = text(21f, Fonts.body, Theme.fg2).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.3f) }
    private val colorsHead = text(20f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .08f; this.text = "SHELL COLOR"; visibility = View.GONE }
    private val colors = Options(ctx).apply { visibility = View.GONE }
    private lateinit var spacer: LinearLayout.LayoutParams
    private val hint = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; alpha = 0f }
    private var hintLeft = 6f

    // choosing a shell color: the one to go back to if you press B
    private var choosing = false
    private var before: Variant? = null

    init {
        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val chip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = round(Theme.chip, 19f); setPadding(16, 6, 16, 6) }
        chip.addView(chipDot, LinearLayout.LayoutParams(12, 12).apply { rightMargin = 9 }); chip.addView(chipText)
        chips.addView(chip); chips.addView(favChip, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 10 })
        panel.addView(chips)
        panel.addView(title, lp(18))
        panel.addView(facts, lp(14))
        panel.addView(colorsHead, lp(22))
        panel.addView(colors, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10; leftMargin = -30; rightMargin = -30 })
        panel.addView(View(ctx), LinearLayout.LayoutParams(-1, 0, 1f).also { spacer = it })
        panel.addView(tiles, LinearLayout.LayoutParams(-1, 136).apply { leftMargin = -8; rightMargin = -8 })
        panel.addView(note, lp(6))
        board.put(panel, 704, 124, 596, 598)
        hint.addView(key("R", "Turn it with the right stick")); hint.addView(key("X", "Flip it over").apply { setPadding(28, 0, 0, 0) })
        board.put(hint, 60, 664, 600)
        tiles.onMove = { refreshNote() }
        refresh()
        Sounds.play(Sounds.OPEN)
    }

    /** Updates the details after anything changes (emulator, shell color, favorite). */
    fun refresh() {
        chipText.text = Games.NAMES[game.sys] ?: game.sys; chipDot.background = round(Theme.accentOf(game.sys), 6f)
        favChip.visibility = if (games.favorite(game)) View.VISIBLE else View.GONE
        title.text = game.title
        val played = games.lastPlayed(game); val n = games.timesPlayed(game)
        val emu = games.emulator(game.sys)
        val style = carts.styles[game.sys]
        facts.text = listOfNotNull(
            if (played == 0L) "Not played yet" else "Played " + DateUtils.getRelativeTimeSpanString(played, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) + (if (n > 1) " · $n times" else ""),
            "Plays with " + (emu?.removePrefix(Games.RA)?.let { if (emu.startsWith(Games.RA)) "$it, in RetroArch" else it } ?: "nothing yet"),
            style?.let { "${it.variant(games.variant(game)).name} shell" }).joinToString("\n")
        tiles.fav = games.favorite(game)
        tiles.swatch = style?.variant(games.variant(game))?.color ?: Theme.fg2
        tiles.invalidate()
        refreshNote()
    }

    private fun refreshNote() {
        val emu = games.emulator(game.sys)?.removePrefix(Games.RA)
        note.text = when (tiles.at) {
            0 -> if (emu == null) "Choose an emulator first, then it starts." else "Starts it in $emu."
            1 -> "${Games.NAMES[game.sys]} games play with ${emu ?: "nothing yet"}. Choose another."
            2 -> "The colors these cartridges really came in. See each one on the cartridge as you choose."
            3 -> if (games.favorite(game)) "Remove it from your favorites." else "Add it to your favorites. (Y does this too.)"
            else -> "Put it back on Home."
        }
    }

    // ---------- every frame ----------

    fun stick(x: Float, y: Float) { stickX = x; stickY = y }

    fun tick(dt: Float) {
        time += dt
        t = if (closing) maxOf(0f, t - dt / .5f) else min(1f, t + dt / .6f)
        val k = Backdrop.ease(t)
        val pk = Backdrop.ease(((t - .3f) / .7f).coerceIn(0f, 1f))
        panel.alpha = pk; panel.translationX = (1 - pk) * 70f
        // the stick: turn while held, coast when let go, then settle back to the front (or the back, after X)
        val sx = dead(stickX); val sy = dead(stickY)
        if (!closing && (sx != 0f || sy != 0f)) {
            vYaw += (sx * 3.4f - vYaw) * min(1f, dt * 8f); vPitch += (sy * 2.6f - vPitch) * min(1f, dt * 8f)
            idle = 0f
            if (!everTurned) { everTurned = true; hintLeft = min(hintLeft, .6f) }
        } else {
            vYaw *= exp(-dt * 1.7f); vPitch *= exp(-dt * 2.4f); idle += dt
        }
        yaw += vYaw * dt; pitch = (pitch + vPitch * dt).coerceIn(-1.35f, 1.35f)
        if (idle > 1.2f || closing) {
            val target = restYaw + (2 * PI.toFloat()) * ((yaw - restYaw) / (2 * PI)).roundToLong()
            val r = min(1f, dt * (if (closing) 6f else 2.4f))
            yaw += (target - yaw) * r; pitch += (restPitch - pitch) * r
        }
        stage.turnKey(wrap(yaw - restYaw) * .3f * k)
        // from its place in the row to the left half, arcing up a little on the way
        val x = lerp(from[0], 350f, k); val y = lerp(from[1], 384f, k) - sin(PI.toFloat() * k) * 46f + sin(time * .8f) * 4f * k
        val px = lerp(from[2], 400f, k)
        val ry = lerp(-.2f, yaw, k); val rx = lerp(-.1f, pitch, k)
        cart?.place(x, y, lerp(40f, 70f, k), ry, rx, px, held = true)
        cart?.brightness(lerp(1.02f, 1f, k))
        cart?.let { c ->
            val big = maxOf(c.style.w, c.style.h)
            backdrop.shadows.add(floatArrayOf(x, lerp(from[1] + c.style.h / big * from[2] / 2 + 24f, 600f, k), lerp(c.style.w / big * from[2] * .9f, 330f, k), 1f - .25f * k))
        }
        // the hint under the cartridge shows for a few seconds, then gets out of the way
        hintLeft -= dt
        hint.alpha = if (closing) min(hint.alpha, pk) else pk * (hintLeft / .6f).coerceIn(0f, 1f)
        if (closing && t == 0f) { destroy(); host.closed(this) }
    }

    // ---------- buttons ----------

    fun input(a: String) {
        if (closing) return
        if (choosing) return chooseInput(a)
        when (a) {
            "left" -> Sounds.play(if (tiles.move(-1)) Sounds.MOVE else Sounds.BUMP)
            "right" -> Sounds.play(if (tiles.move(1)) Sounds.MOVE else Sounds.BUMP)
            "up", "b" -> close()
            "x" -> flip()
            "y" -> toggleFavorite()
            "a" -> when (tiles.at) {
                0 -> { Sounds.play(Sounds.PICK); host.play(game) }
                1 -> { Sounds.play(Sounds.PICK); host.chooseEmulator(game) }
                2 -> startChoosing()
                3 -> toggleFavorite()
                else -> close()
            }
        }
    }

    fun close() { if (!closing) { closing = true; if (choosing) endChoosing(keep = false); Sounds.play(Sounds.CLOSE) } }

    private fun flip() {
        restYaw = if (restYaw > 1f) -.28f else -.28f + PI.toFloat()
        vYaw = 0f; vPitch = 0f; idle = 9f
        hintLeft = min(hintLeft, .6f)
        Sounds.play(Sounds.TURN)
    }

    private fun toggleFavorite() {
        val on = !games.favorite(game); games.setFavorite(game, on)
        Sounds.play(if (on) Sounds.PICK else Sounds.MOVE)
        if (on) vYaw += 2.2f   // a little happy spin
        refresh()
    }

    // ---------- shell colors, previewed on the cartridge ----------

    private fun startChoosing() {
        val style = carts.styles[game.sys] ?: return
        choosing = true; before = style.variant(games.variant(game))
        colors.rows = style.variants.map { Options.Row("v", it.name, sub = it.games.ifEmpty { "A color it came in" }, check = it == before, dot = it.color) }
        colors.reset(); repeat(style.variants.indexOf(before)) { colors.move(1) }
        colors.onFocus = { preview(); Sounds.play(Sounds.MOVE) }
        for (v in listOf(facts, tiles, note)) v.visibility = View.GONE
        spacer.weight = 0f; colorsHead.visibility = View.VISIBLE; colors.visibility = View.VISIBLE
        panel.requestLayout()
        Sounds.play(Sounds.OPEN)
    }

    private fun chooseInput(a: String) {
        when (a) {
            "up" -> colors.move(-1)
            "down" -> colors.move(1)
            "a" -> { Sounds.play(Sounds.PICK); endChoosing(keep = true) }
            "b" -> { Sounds.play(Sounds.CLOSE); endChoosing(keep = false) }
            "x" -> flip()
        }
    }

    private fun preview() {
        val style = carts.styles[game.sys] ?: return
        setVariant(style.variants.firstOrNull { it.name == colors.current()?.label } ?: return)
    }

    private fun setVariant(v: Variant) {
        val c = cart
        if (c == null || (v.clear != null) != (c.variant.clear != null)) {   // clear shells are a different model: swap it in place
            c?.destroy(); cart = carts.make(game.sys, game.title, v.name); host.cartChanged(game, cart)
        } else c.apply(v)
    }

    private fun endChoosing(keep: Boolean) {
        choosing = false
        val style = carts.styles[game.sys]
        if (keep) colors.current()?.let { games.setVariant(game, it.label) } else before?.let { setVariant(it) }
        if (style != null && keep) style.variants.firstOrNull { it.name == colors.current()?.label }?.let { if (it.sheen || it.clear != null) vYaw += 1.6f }   // show off the new finish
        for (v in listOf(facts, tiles, note)) v.visibility = View.VISIBLE
        spacer.weight = 1f; colorsHead.visibility = View.GONE; colors.visibility = View.GONE
        panel.requestLayout()
        refresh()
    }

    fun destroy() {
        board.removeView(panel); board.removeView(hint)
        stage.turnKey(0f)
    }

    // ---------- helpers ----------

    private fun dead(v: Float) = if (abs(v) < .18f) 0f else (v - .18f * Math.signum(v)) / .82f
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun wrap(a: Float): Float { var x = a % (2 * PI.toFloat()); if (x > PI) x -= 2 * PI.toFloat(); if (x < -PI) x += 2 * PI.toFloat(); return x }
    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }
    private fun key(btn: String, say: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(18f, Fonts.bold, Theme.fg).apply { this.text = btn; gravity = Gravity.CENTER; background = round(Color.TRANSPARENT, 16f, Theme.fg3, 1.5f); minWidth = 33; setPadding(8, 5, 8, 5) })
        addView(text(21f, Fonts.body, Theme.fg2).apply { this.text = say; setPadding(10, 0, 0, 0) })
    }
}

/** The game page's row of square icon tiles, each with its name underneath; the focused one fills in and lifts. */
class Tiles(ctx: Context) : View(ctx) {
    private val names = listOf("Play", "Emulator", "Shell color", "Favorite", "Back")
    var at = 0; private set
    var fav = false
    var swatch = Color.WHITE
    var onMove: (() -> Unit)? = null
    private var lift = FloatArray(5)
    private var last = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun move(d: Int): Boolean { val k = (at + d).coerceIn(0, names.size - 1); if (k == at) return false; at = k; onMove?.invoke(); invalidate(); return true }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); val dt = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f); last = now
        val size = 92f; val gap = (width - size * names.size) / (names.size - 1)
        var moving = false
        names.forEachIndexed { k, name ->
            val want = if (k == at) 1f else 0f
            lift[k] += (want - lift[k]) * min(1f, if (dt == 0f) 1f else dt * 14f); if (abs(want - lift[k]) > .01f) moving = true
            val x = k * (size + gap); val y = 6f - lift[k] * 5f
            val box = RectF(x, y, x + size, y + size)
            p.style = Paint.Style.FILL
            p.color = if (k == 0) Theme.accentOf(null) else Theme.chip
            if (k == at) p.color = Theme.sel
            c.drawRoundRect(box, 26f, 26f, p)
            if (k == at) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 4, box.top - 4, box.right + 4, box.bottom + 4), 30f, 30f, p) }
            val ink = if (k == at || k == 0) Theme.selFg else Theme.fg
            c.save(); c.translate(x + size / 2 - 17, y + size / 2 - 17); icon(c, k, ink); c.restore()
            p.style = Paint.Style.FILL; p.typeface = if (k == at) Fonts.bold else Fonts.body; p.textSize = 18f; p.color = if (k == at) Theme.fg else Theme.fg2
            p.textAlign = Paint.Align.CENTER; c.drawText(name, x + size / 2, size + 34f, p); p.textAlign = Paint.Align.LEFT
        }
        if (moving) postInvalidateOnAnimation() else last = 0L
    }

    /** Each tile's icon, a 34 × 34 drawing in simple strokes like the top bar's. */
    private fun icon(c: Canvas, k: Int, ink: Int) {
        p.color = ink; p.strokeWidth = 2.6f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        when (k) {
            0 -> { p.style = Paint.Style.FILL; c.drawPath(Path().apply { moveTo(11f, 6f); lineTo(29f, 17f); lineTo(11f, 28f); close() }, p) }
            1 -> { p.style = Paint.Style.STROKE; c.drawRoundRect(RectF(2f, 9f, 32f, 27f), 9f, 9f, p)
                c.drawLine(9f, 18f, 15f, 18f, p); c.drawLine(12f, 15f, 12f, 21f, p)
                p.style = Paint.Style.FILL; c.drawCircle(22f, 16f, 2.2f, p); c.drawCircle(26.5f, 20f, 2.2f, p) }
            2 -> { p.style = Paint.Style.FILL; p.color = swatch; c.drawCircle(17f, 17f, 12f, p)
                p.style = Paint.Style.STROKE; p.color = ink; c.drawCircle(17f, 17f, 13.5f, p) }
            3 -> { val heart = Path().apply { moveTo(17f, 29f); cubicTo(2f, 19f, 3f, 6f, 11f, 6f); cubicTo(14.5f, 6f, 16.5f, 8.5f, 17f, 11f)
                    cubicTo(17.5f, 8.5f, 19.5f, 6f, 23f, 6f); cubicTo(31f, 6f, 32f, 19f, 17f, 29f); close() }
                p.style = if (fav) Paint.Style.FILL else Paint.Style.STROKE; c.drawPath(heart, p) }
            else -> { p.style = Paint.Style.STROKE; c.drawLine(8f, 17f, 27f, 17f, p); c.drawLine(8f, 17f, 15f, 10f, p); c.drawLine(8f, 17f, 15f, 24f, p) }
        }
    }
}
