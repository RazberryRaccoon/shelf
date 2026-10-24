package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Home: your most recently played games as a row of cartridges, the selected one large on the left with its title
 * under it, and the top bar above (profile, the round buttons for Library, Apps, Museum and Settings, the time).
 * Positions follow the prototype's drawHome. Library, Apps, Museum and Settings come to the native app in later versions.
 */
class Home(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
           private val games: Games, private val carts: Carts, private val actions: Actions) : GamePage.Host {
    interface Actions { fun play(g: Game); fun allowStorage(); fun openSettings() }

    private class Slot(val game: Game, var cart: Cart?, var x: Float = 270f, var y: Float = 326f, var z: Float = 0f, var px: Float = 156f, var bright: Float = .72f)
    private val slots = ArrayList<Slot>()
    private var i = 0
    private var f = 0f
    private var time = 0f
    private var zone = "row"   // "row" or "top"
    private var topI = 1
    private var picTimer = 0f
    private var picFor: Game? = null
    var picker: Picker? = null; private set
    /** The open game's page, if one is open (Down on a game). */
    var page: GamePage? = null; private set
    var needsStorage = false
    /** Home hides while setup or settings are open; coming back, the cartridges rise into place one after another. */
    var visible = true; set(v) { if (v && !field) { rise = 0f; riseT = 0f }; field = v }
    private var rise = 0f
    private var riseT = 0f   // seconds since Home came back; each cartridge starts rising a moment after the one before

    private val title = text(48f, Fonts.display, Theme.fg)
    private val meta = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val chipText = text(23f, Fonts.bold, Theme.fg)
    private val dot = View(ctx)
    private val message = text(28f, Fonts.body, Theme.fg2).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.35f) }
    private val toast = text(23f, Fonts.bold, Theme.fg).apply { background = round(Theme.panel, 27f, Theme.line); setPadding(30, 14, 30, 14); alpha = 0f }
    private val clock = text(24.5f, Fonts.bold, Theme.fg2)
    private val profile = Chip(ctx)
    private val dock = listOf("library", "apps", "museum", "settings").map { DockButton(ctx, it) }
    private val dockNames = listOf("Library", "Apps", "Museum", "Settings")
    private val dockLabel = text(20f, Fonts.bold, Theme.fg).apply { gravity = Gravity.CENTER; alpha = 0f }

    init {
        // the top bar
        board.put(profile, 32, 20, h = 72)
        dock.forEachIndexed { k, b -> board.put(b, 667 - 2 * 76 - 45 + k * 106, 16, 76, 76) }
        board.put(dockLabel, 0, 96, 1334)
        board.put(clock, 1334 - 36 - 240, 38, 240); clock.gravity = Gravity.END
        // the selected game's details, under the row
        board.put(title, 64, 520, 1100)
        title.maxLines = 1; title.ellipsize = TextUtils.TruncateAt.END
        val chip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = round(Theme.chip, 21f); setPadding(18, 6, 18, 6) }
        chip.addView(dot, LinearLayout.LayoutParams(13, 13).apply { rightMargin = 10 })
        chip.addView(chipText)
        meta.addView(chip)
        meta.addView(hint("A", "Play"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 24 })
        meta.addView(hint("↓", "Details"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 24 })
        meta.addView(hint("Y", "Shell color and emulator"), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 24 })
        board.put(meta, 64, 600)
        board.put(message, 167, 300, 1000)
        board.put(toast, 0, 640); toast.layoutParams = (toast.layoutParams as android.widget.FrameLayout.LayoutParams).apply { width = -2; gravity = Gravity.CENTER_HORIZONTAL }
        refreshTop(); clockTick(); refreshProfile()
    }

    fun refreshProfile() = profile.set(games.name.ifEmpty { "Player" }, games.color)

    // ---------- games in the row ----------

    fun setGames(list: List<Game>) {
        val keep = list.take(15)
        page?.let { p -> if (keep.none { it.path == p.game.path }) { p.destroy(); page = null } }   // its game is gone
        val old = slots.associateBy { it.game.path }
        slots.filter { s -> keep.none { it.path == s.game.path } }.forEach { it.cart?.destroy() }
        slots.clear()
        for (g in keep) slots.add(old[g.path] ?: Slot(g, carts.make(g.sys, g.title, games.variant(g))))
        i = i.coerceIn(0, maxOf(0, slots.size - 1))
        // back from playing with a page open: the game moved to the front of the row, so follow it there
        page?.let { p -> i = slots.indexOfFirst { it.game.path == p.game.path }; f = i.toFloat(); p.refresh() }
        refreshInfo()
    }

    private fun current() = slots.getOrNull(i)?.game

    private fun refreshInfo() {
        val g = current()
        title.text = g?.title ?: ""
        meta.visibility = if (g == null) View.INVISIBLE else View.VISIBLE
        if (g != null) { chipText.text = Games.NAMES[g.sys]; dot.background = round(Theme.accentOf(g.sys), 6.5f); backdrop.mood(Theme.accentOf(g.sys)) }
        message.text = when {
            needsStorage -> "Shelf Native needs permission to see your games folders.\n\nPress A to allow it in Android’s settings."
            slots.isEmpty() -> "No games found yet.\n\nPress A to choose your games folders."
            else -> ""
        }
        message.visibility = if (message.text.isEmpty()) View.GONE else View.VISIBLE
        picTimer = .25f   // the picture follows once the selection settles
    }

    // ---------- every frame ----------

    fun tick(dt: Float) {
        time += dt
        f += (i - f) * min(1f, dt * 8f)
        rise = if (visible) min(1f, rise + dt / 1.4f) else maxOf(0f, rise - dt / .35f)
        if (visible) riseT += dt
        val shownK = Backdrop.ease(min(1f, rise * 2f))
        val pageK = page?.shownK ?: 0f
        for (v in listOf(clock, profile) + dock) v.alpha = shownK
        for (v in listOf(title, meta, message)) v.alpha = shownK * (1 - pageK)
        dockLabel.alpha = minOf(dockLabel.alpha, shownK)
        backdrop.picVisible = shownK * (1 - pageK)
        if (!visible && rise == 0f) { slots.forEach { it.cart?.place(-2000f, 0f, 0f, 0f, 0f, 1f) }; return }
        if (visible) backdrop.shadows.clear()
        slots.forEachIndexed { k, s ->
            if (k == i && page != null) return@forEachIndexed   // the page holds this one
            val off = k - f; val foc = k == i && picker == null
            val bob = sin(time * .8f + k * 1.7f) * (if (foc) 4f else 3f)
            val tx = 270f + off * 196f + sign(off) * min(abs(off), 1f) * 70f
            val ty = (if (k == i) 302f else 326f)
            val ease = min(1f, dt * 10f)
            s.x += (tx - s.x) * ease; s.y += (ty - s.y) * min(1f, dt * 7f)
            s.z += ((if (k == i) 40f else 0f) - s.z) * ease
            s.px += ((if (k == i) 240f else 156f) - s.px) * ease
            s.bright += ((if (k == i) 1.02f else .72f) - s.bright) * ease
            val cart = s.cart ?: return@forEachIndexed
            // rising into place, each a moment after the one before
            val rest = if (visible) Backdrop.ease(((riseT - k * .07f) / .9f).coerceIn(0f, 1f)) else 1f
            // while a page is open, the others sink out of the way, each a beat after the one before
            val away = page?.let { Backdrop.ease((pageK * 1.4f - abs(k - i) * .06f).coerceIn(0f, 1f)) } ?: 0f
            val up = rest * (1 - away); val lift = (1 - rest) * 560f + away * 520f
            cart.place(s.x, s.y + bob + lift, s.z, -.2f, -.1f, s.px)
            cart.brightness(s.bright)
            if (s.x > -200 && s.x < 1534) {
                val big = maxOf(cart.style.w, cart.style.h)
                backdrop.shadows.add(floatArrayOf(s.x, s.y + cart.style.h / big * s.px / 2 + (if (k == i) 24f else 12f), cart.style.w / big * s.px * .9f, (if (k == i) 1f else .7f) * up))
            }
        }
        page?.tick(dt)
        if (picTimer > 0) { picTimer -= dt; if (picTimer <= 0) current().let { g -> if (g != picFor) { picFor = g; backdrop.picture(g?.let { Paintings.picture(800, 196, it.title) }) } } }
        if (toastLeft > 0) { toastLeft = (toastUntil - android.os.SystemClock.uptimeMillis()) / 1000f; toast.alpha = min(1f, toastLeft / .3f).coerceAtLeast(0f) }
        clockLeft -= dt; if (clockLeft <= 0) clockTick()
    }

    // ---------- buttons ----------

    fun input(a: String) {
        picker?.let { if (it.input(a)) return }
        page?.let { it.input(a); return }
        if (needsStorage) { if (a == "a") actions.allowStorage(); return }
        if (slots.isEmpty() && zone == "row") { if (a == "a") actions.openSettings() else if (a == "up") { zone = "top"; refreshTop() }; return }
        when (zone) {
            "row" -> when (a) {
                "left" -> if (i > 0) { i--; refreshInfo(); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "right" -> if (i < slots.size - 1) { i++; refreshInfo(); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "up" -> { zone = "top"; refreshTop(); Sounds.play(Sounds.MOVE) }
                "down" -> openPage()
                "a" -> current()?.let { g -> Sounds.play(Sounds.PICK); if (games.emulator(g.sys) == null) chooseEmulator(g, then = true) else actions.play(g) }
                "y" -> current()?.let { options(it) }
            }
            "top" -> when (a) {
                "left" -> if (topI > 0) { topI--; refreshTop(); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "right" -> if (topI < dock.size) { topI++; refreshTop(); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "down", "b" -> { zone = "row"; refreshTop(); Sounds.play(Sounds.MOVE) }
                "a" -> if (topI == 0 || topI == dock.size) actions.openSettings() else show("${dockNames[topI - 1]} comes to Shelf Native in a later version. The web Shelf has it today.")
            }
        }
    }

    // ---------- a game's page ----------

    private fun openPage() {
        val s = slots.getOrNull(i) ?: return
        page = GamePage(ctx, board, backdrop, stage, games, carts, s.game, s.cart, floatArrayOf(s.x, s.y, s.px), this)
    }
    /** The right stick, for turning the cartridge on a game's page. */
    fun stick(x: Float, y: Float) { page?.stick(x, y) }

    override fun play(g: Game) = actions.play(g)
    override fun chooseEmulator(g: Game) = chooseEmulator(g, then = false)
    override fun cartChanged(g: Game, c: Cart?) { slots.firstOrNull { it.game == g }?.cart = c }
    override fun closed(page: GamePage) {
        if (this.page === page) this.page = null
        slots.firstOrNull { it.game == page.game }?.let { it.cart?.brightness(it.bright) }
        refreshInfo()
    }

    private fun refreshTop() {
        val on = zone == "top"
        profile.focused = on && topI == 0
        dock.forEachIndexed { k, b -> b.focused = on && topI == k + 1 }
        dockLabel.alpha = if (on && topI > 0) 1f else 0f
        if (on && topI > 0) { dockLabel.text = dockNames[topI - 1]; dockLabel.translationX = (667 - 2 * 76 - 45 + (topI - 1) * 106 + 38 - 667).toFloat() }
    }

    /** Y on a game: its shell color and its console's emulator. */
    private fun options(g: Game) {
        val style = carts.styles[g.sys] ?: return
        val v = style.variant(games.variant(g))
        open(Picker(ctx, g.title, listOf(Picker.Row("Shell color", v.name), Picker.Row("Emulator for ${Games.NAMES[g.sys]}", games.emulator(g.sys) ?: "Not chosen yet"))) { k ->
            if (k == 0) chooseVariant(g) else chooseEmulator(g, then = false)
        })
    }

    private fun chooseVariant(g: Game) {
        val style = carts.styles[g.sys] ?: return
        val cur = style.variant(games.variant(g))
        open(Picker(ctx, "Shell color: ${style.name}", style.variants.map { Picker.Row(it.name, it.games) }, start = style.variants.indexOf(cur)) { k ->
            val v = style.variants[k]; games.setVariant(g, v.name)
            val s = slots.firstOrNull { it.game == g } ?: return@Picker
            if ((v.clear != null) != (s.cart?.variant?.clear != null)) {   // clear shells are a different model: swap it in place
                s.cart?.destroy(); s.cart = carts.make(g.sys, g.title, v.name)
            } else s.cart?.apply(v)
            show("${g.title}: ${v.name}")
        })
    }

    private fun chooseEmulator(g: Game, then: Boolean) {
        val choices = games.emulatorChoices(g.sys)
        val cur = games.emulator(g.sys)
        open(Picker(ctx, "Play ${Games.NAMES[g.sys]} games with", choices.map { (n, ok) -> Picker.Row(n, if (ok) "" else "Not installed") },
            start = maxOf(0, choices.indexOfFirst { it.first == cur }),
            note = if (choices.any { it.first.startsWith(Games.RA) }) "A RetroArch core has to be downloaded in RetroArch first (Main Menu › Load Core › Download a Core)." else null) { k ->
            games.setEmulator(g.sys, choices[k].first)
            page?.refresh()
            if (then) actions.play(g) else show("${Games.NAMES[g.sys]} games play with ${choices[k].first}")
        })
    }

    /** A message over everything, such as what went wrong last time. */
    fun report(heading: String, lines: List<String>, note: String) = open(Picker(ctx, heading, lines.map { Picker.Row(it.take(80), "") }, note = note) { })

    private fun open(p: Picker) {
        picker?.let { board.removeView(it) }
        if (picker == null) Sounds.play(Sounds.OPEN)
        picker = p; p.onClose = { board.removeView(p); if (picker === p) picker = null }
        board.put(p, 0, 0, 1334, 750)
    }

    private var toastLeft = 0f
    private var toastUntil = 0L
    fun show(msg: String) { toast.text = msg; toast.alpha = 1f; toastLeft = 2.5f; toastUntil = android.os.SystemClock.uptimeMillis() + 2500; toast.bringToFront() }

    private var clockLeft = 0f
    private fun clockTick() {
        clockLeft = 20f
        val pct = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        clock.text = "$pct%   ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}"
    }

    // ---------- small view helpers ----------

    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }
    private fun hint(btn: String, say: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(19f, Fonts.bold, Theme.fg).apply { this.text = btn; gravity = Gravity.CENTER; background = round(Color.TRANSPARENT, 16.5f, Theme.fg3, 1.5f); minWidth = 35; setPadding(9, 6, 9, 6) })
        addView(text(24.5f, Fonts.body, Theme.fg2).apply { this.text = say; setPadding(10, 0, 0, 0) })
    }
}

fun round(fill: Int, radius: Float, stroke: Int = 0, strokeW: Float = 1f) = GradientDrawable().apply {
    setColor(fill); cornerRadius = radius; if (stroke != 0) setStroke(strokeW.toInt().coerceAtLeast(1), stroke)
}

/** The profile chip at the top left: your avatar and name. */
class Chip(ctx: Context) : LinearLayout(ctx) {
    var focused = false; set(v) { field = v; background = if (v) round(Theme.chip, 36f, Theme.sel, 2f) else null }
    private var color = 0
    private val avatar = object : View(ctx) { override fun onDraw(c: Canvas) = Theme.drawAvatar(c, width / 2f, height / 2f, width / 2f, color) }
    private val label = TextView(ctx).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX, 25.5f); typeface = Fonts.bold; setTextColor(Theme.fg) }
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(10, 0, 26, 0)
        addView(avatar, LayoutParams(52, 52).apply { rightMargin = 15 })
        addView(label)
    }
    fun set(name: String, k: Int) { label.text = name; color = k; avatar.invalidate() }
}

/** A round button in the top bar, with its icon drawn as simple strokes (books, apps, a museum, the sun). */
class DockButton(ctx: Context, private val kind: String) : View(ctx) {
    var focused = false; set(v) { field = v; animate().scaleX(if (v) 1.1f else 1f).scaleY(if (v) 1.1f else 1f).setDuration(220).start(); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val r = w / 2
        p.style = Paint.Style.FILL; p.color = if (focused) Theme.sel else Theme.chip; c.drawCircle(r, r, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.6f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = if (focused) Theme.selFg else Theme.fg
        c.save(); c.translate(r - 17, r - 17)   // a 34 × 34 icon, as in the prototype
        when (kind) {
            "library" -> { c.drawRoundRect(RectF(4f, 6f, 10f, 28f), 1.5f, 1.5f, p); c.drawRoundRect(RectF(13f, 6f, 19f, 28f), 1.5f, 1.5f, p); c.save(); c.rotate(-14f, 26f, 28f); c.drawRoundRect(RectF(22f, 7f, 28f, 28f), 1.5f, 1.5f, p); c.restore() }
            "apps" -> for (x in 0..1) for (y in 0..1) c.drawRoundRect(RectF(5f + x * 14, 5f + y * 14, 15f + x * 14, 15f + y * 14), 3f, 3f, p)
            "museum" -> { val roof = Path().apply { moveTo(4f, 12f); lineTo(17f, 4f); lineTo(30f, 12f); close() }; c.drawPath(roof, p); for (x in listOf(8f, 14f, 20f, 26f)) c.drawLine(x, 15f, x, 26f, p); c.drawLine(4f, 30f, 30f, 30f, p) }
            else -> { c.drawCircle(17f, 17f, 6f, p); for (k in 0 until 8) { val a = k * Math.PI / 4; c.drawLine(17 + 10 * Math.cos(a).toFloat(), 17 + 10 * Math.sin(a).toFloat(), 17 + 13 * Math.cos(a).toFloat(), 17 + 13 * Math.sin(a).toFloat(), p) } }
        }
        c.restore()
    }
}

/** A list in a panel over the screen (the prototype's dialog): up and down to choose, A to pick, B to close. */
class Picker(ctx: Context, private val heading: String, private val rows: List<Row>, start: Int = 0, private val note: String? = null, private val onPick: (Int) -> Unit) : View(ctx) {
    data class Row(val label: String, val sub: String)
    var onClose: (() -> Unit)? = null
    private var k = start.coerceIn(0, maxOf(0, rows.size - 1))
    private var top = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    companion object { const val SHOW = 6 }

    fun input(a: String): Boolean {
        when (a) {
            "up" -> if (k > 0) { k--; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "down" -> if (k < rows.size - 1) { k++; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "a" -> { Sounds.play(Sounds.PICK); onClose?.invoke(); onPick(k) }
            "b" -> { Sounds.play(Sounds.CLOSE); onClose?.invoke() }
            else -> return true
        }
        top = top.coerceIn(maxOf(0, k - SHOW + 1), k); invalidate(); return true
    }

    override fun onDraw(c: Canvas) {
        p.color = Color.argb(158, 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)   // the scrim
        val n = min(SHOW, rows.size); val w = 680f; val rowH = 76f
        val h = 110 + n * rowH + (if (note != null) 70 else 0) + 30; val x = (Stage.W - w) / 2; val y = (Stage.H - h) / 2
        p.color = Color.rgb(16, 21, 50); c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Theme.line; c.drawRoundRect(RectF(x, y, x + w, y + h), 33f, 33f, p); p.style = Paint.Style.FILL
        p.typeface = Fonts.display; p.textSize = 28f; p.color = Theme.fg
        c.drawText(TextUtils.ellipsize(heading, android.text.TextPaint(p), w - 80, TextUtils.TruncateAt.END).toString(), x + 40, y + 62, p)
        for (j in 0 until n) {
            val r = rows[top + j]; val ry = y + 96 + j * rowH; val on = top + j == k
            if (on) { p.color = Theme.chip; c.drawRoundRect(RectF(x + 20, ry, x + w - 20, ry + rowH - 8), 21f, 21f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.sel; c.drawRoundRect(RectF(x + 20, ry, x + w - 20, ry + rowH - 8), 21f, 21f, p); p.style = Paint.Style.FILL }
            p.typeface = Fonts.bold; p.textSize = 24f; p.color = Theme.fg
            c.drawText(r.label, x + 44, ry + (if (r.sub.isEmpty()) 44 else 32), p)
            if (r.sub.isNotEmpty()) { p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2
                c.drawText(TextUtils.ellipsize(r.sub, android.text.TextPaint(p), w - 100, TextUtils.TruncateAt.END).toString(), x + 44, ry + 58, p) }
        }
        if (note != null) { p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2
            Fonts.wrap(p, note, w - 80).take(2).forEachIndexed { j, l -> c.drawText(l, x + 40, y + 110 + n * rowH + 20 + j * 26, p) } }
    }
}
