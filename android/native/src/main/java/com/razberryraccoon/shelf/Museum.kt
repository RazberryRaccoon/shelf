package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.filament.TextureSampler
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The Museum: every console as an exhibit, oldest first. On the left, the exhibit in 3D, shown four ways (the
 * console, its cartridge, the cartridge in its open box, the box closed), turned with the right stick like a game's
 * cartridge. The views flow into each other: the cartridge settles into the tray as the box appears, and the cover
 * swings shut. Along the bottom, a timeline of the consoles (L and R, or left and right on it, move through them).
 * On the right, the console's story; its colors (the real ones it came in, or your own, painted part by part in the
 * Studio); and its default shell: what its standard cartridges wear on Home (special editions keep their real colors,
 * and games you've customized keep yours).
 */
class Museum(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
             private val games: Games, private val carts: Carts, private val host: Host, startSys: String? = null) {
    interface Host {
        fun closed(museum: Museum, shellsChanged: Boolean)
        fun pick(p: Picker)
        fun askText(title: String, value: String, then: (String) -> Unit)
    }

    private class Exhibit(val sys: String, val name: String, val maker: String, val year: Int, val released: String, val sold: String,
                          val cpu: String, val plays: String, val facts: List<String>, val example: String)
    private val exhibits: List<Exhibit>
    private var at = 0
    private val views = listOf("Console", "Cartridge", "Cartridge Inside", "In its box", "Box closed", "Console Inside")   // Console Inside: left of Console
    /** A view's name for this console: its games by their own name (a CD, a UMD, a game card), not always "Cartridge". */
    private fun viewName(v: Int): String {
        val e = ex()
        val one = Games.mediaName(e.sys)
        return when (v) { 1 -> one; CART_INSIDE -> "$one Inside"; else -> views[v] }
    }
    private var view = 0
    private var shellsChanged = false

    // the models for the console on show
    private var console: Console? = null
    private var circuit: ConsoleBoard? = null   // its circuit board, loaded when it shows (see-through, or Inside)
    private var cart: Cart? = null
    private var case: Case? = null
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)

    // how present each piece is (eased toward its target every frame), and turning
    private var leaving = -1; private var slideX = 0f; private var slideTo = 0f   // switching consoles: a slide to the side
    private var consoleY = OFF; private var groupY = -OFF; private var age = 0f   // the console's and the cartridge-and-box's offsets from centre
    private val flow = BoxFlow(); private var inside = 0f; private var cartInside = 0f
    private var time = 0f; private var closing = false; private var shown = 0f
    private var stickX = 0f; private var stickY = 0f
    // Y: looking closely at whatever is showing, big in the middle with everything else dimmed (as on a game's page)
    private var inspecting = false
    private var inspK = 0f; private var inspZoom = 1f; private var inspZoomTo = 1f
    private var panX = 0f; private var panY = 0f; private var panToX = 0f; private var panToY = 0f
    private var yaw = -.4f; private var pitch = -.2f; private var vYaw = 0f; private var vPitch = 0f; private var idle = 9f
    private var flipped = false

    // the panel
    private val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(44, 34, 44, 26); clipToPadding = false; alpha = 0f }
    private val EDGE = 28
    private val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(36); overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false }
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipToPadding = false; clipChildren = false; setPadding(EDGE, 0, EDGE, 30) }
    private val label = text(18f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .1f }
    private val title = text(38f, Fonts.display, Theme.fg).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val stats = text(21f, Fonts.body, Theme.fg2).apply { setLineSpacing(0f, 1.42f) }
    private val rows = Options(ctx)
    private val factsHead = text(18f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .1f; text = "DID YOU KNOW" }
    private val facts = text(21f, Fonts.body, Theme.fg2).apply { setLineSpacing(0f, 1.45f); setPadding(18, 12, 18, 14) }
    private val listHead = text(18f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .1f; visibility = View.GONE }
    private val list = Options(ctx).apply { visibility = View.GONE }
    private val timeline = Timeline(ctx)
    private val hint = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; alpha = 0f }
    private val lookHint = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; alpha = 0f }   // while looking closely

    private enum class Zone { ROWS, FACTS, SHELLS, LOOKS, PARTS, PAINT }
    private var zone = Zone.ROWS
    private var before: String? = null   // the default shell to go back to if you press B in the list
    private val loaded = HashMap<String, Loaded>()   // consoles shown so far (load): declared before init, which uses it

    // the console's colors, and the console Studio (declared before init too)
    private val consoles = Consoles(ctx, stage)
    private var lookBefore: String? = null   // the colors to go back to if you press B in the list
    private var previewed: String? = null   // the colors last shown from the list (+ Add new starts from them)
    private var editingLook: ConsoleLook? = null   // yours, being changed in the Studio (null: new colors)
    private val working = LinkedHashMap<String, PartPaint>()   // the Studio's colors so far, part by part
    private var painting: StudioView? = null
    private var paintRole = ""; private var paintBefore: PartPaint? = null

    init {
        val j = JSONObject(String(stage.readAsset("museum.json").let { b -> ByteArray(b.remaining()).also { b.get(it) } }))
        exhibits = j.keys().asSequence().filter { carts.styles.containsKey(it) }.map { k -> j.getJSONObject(k).let { e ->
            Exhibit(k, LONG[k] ?: Games.NAMES[k] ?: k, e.getString("maker"), e.getInt("year"), e.getString("released"), e.getString("sold"), e.getString("cpu"), e.getString("plays"),
                e.getJSONArray("facts").let { a -> (0 until a.length()).map { a.getString(it) } }, e.optString("example", "Shelf")) } }
            .sortedWith(compareBy({ it.year }, { ORDER.indexOf(it.sys) })).toList()
        col.addView(label); col.addView(title, lp(10)); col.addView(stats, lp(12))
        col.addView(rows, LinearLayout.LayoutParams(-1, 64 + 80 * 2 + 8 * 3 + 12).apply { topMargin = 18; leftMargin = -30; rightMargin = -30 })
        col.addView(factsHead, lp(14)); col.addView(facts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4; leftMargin = -18; rightMargin = -18 })
        scroll.addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { leftMargin = -EDGE; rightMargin = -EDGE })
        panel.addView(listHead, lp(0).apply { bottomMargin = 10 })
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { leftMargin = -30; rightMargin = -30 })
        board.put(panel, 704, 104, 596, 618)
        board.put(timeline, 40, 590, 640, 70)
        hint.addView(key("L", "Previous console")); hint.addView(key("R", "Next console").apply { setPadding(24, 0, 0, 0) }); hint.addView(key("X", "Flip it over").apply { setPadding(24, 0, 0, 0) })
        hint.addView(key("Y", "Look closer").apply { setPadding(24, 0, 0, 0) })
        board.put(hint, 40, 676, 640)
        lookHint.addView(key("L1 R1", "Zoom")); lookHint.addView(key("R", "Turn").apply { setPadding(24, 0, 0, 0) })
        lookHint.addView(key("L", "Move").apply { setPadding(24, 0, 0, 0) }); lookHint.addView(key("B", "Back").apply { setPadding(24, 0, 0, 0) })
        board.put(lookHint, 0, 690, 1334)
        timeline.names = exhibits.map { it.year.toString() }
        show(exhibits.indexOfFirst { it.sys == startSys }.coerceAtLeast(0))   // opens on the console of the game you were on
        Sounds.play(Sounds.OPEN)
    }

    private fun ex() = exhibits[at]

    /** Puts a console on show: loads its models, fills the panel. */
    private fun show(k: Int) {
        console?.hide(); circuit?.hide(); case?.hide(); cart?.place(-4000f, 0f, 0f, 0f, 0f, 1f)   // the last console's models stay loaded, out of sight
        at = k.coerceIn(0, exhibits.size - 1)
        val e = ex()
        val l = load(e)
        console = l.console; circuit = l.board; case = l.case; cart = l.cart
        backdrop.mood(Theme.accentOf(e.sys))
        timeline.at = at; timeline.invalidate()
        label.text = "MUSEUM  ·  ${e.year}"
        title.text = e.name
        stats.text = "${e.maker}\nReleased ${e.released}\n${e.sold} sold\n${e.cpu}\nPlays ${e.plays.lowercase()}"
        facts.text = e.facts.joinToString("\n\n")
        fillRows()
        scroll.scrollTo(0, 0)
        // its own resting angle: home consoles a little from above, handhelds facing you
        yaw = rest()[0]; pitch = rest()[1]; vYaw = 0f; vPitch = 0f; flipped = false
    }

    private fun fillRows() {
        val e = ex(); val v = carts.variantFor(e.sys, games.defaultShell(e.sys))
        val look = consoles.lookFor(e.sys, games.consoleLook(e.sys))
        val keep = rows.current()?.key
        rows.rows = listOf(Options.Row("view", "Showing", value = "‹  ${viewName(view)}  ›")) +
            (if (look == null) emptyList() else listOf(Options.Row("look", "Customize console colors", value = look.name, sub = look.note.ifEmpty { "Your own, from the Studio" }, dot = shellDot(look)))) +
            Options.Row("shell", "Default shell", value = v?.name ?: "", sub = "Its standard ${viewName(1).let { if (it.length <= 3) it + "s" else it.lowercase() + "s" }} on Home; special editions keep theirs", dot = v?.color)
        rows.rows.indexOfFirst { it.key == keep }.takeIf { it > 0 }?.let { rows.focus(it) }
        rows.invalidate()
    }

    /**
     * A console's three models, loaded the first time it's shown and kept until the Museum closes, so moving back and
     * forth along the timeline doesn't stop to load (the neighbours load quietly ahead, one at a time: see tick).
     */
    private class Loaded(var console: Console?, val case: Case?, var cart: Cart?, var board: ConsoleBoard? = null)
    private fun load(e: Exhibit) = loaded.getOrPut(e.sys) {
        val look = consoles.lookFor(e.sys, games.consoleLook(e.sys))
        Loaded(consoleModel(e.sys, look?.seeThrough() == true)?.apply { look?.let { paint(it.parts) } }, carts.model("case-${e.sys}.glb")?.let { Case(stage, it, e.example, sampler) },
            carts.make(e.sys, e.example, games.defaultShell(e.sys)).also { carts.refinish(it, e.example, games.labelFinish) }).also { it.console?.hide(); it.case?.hide(); it.cart?.place(-4000f, 0f, 0f, 0f, 0f, 1f) }
    }

    /** A console's model: the see-through one (its shell blended, for clear, frosted and smoke shells) or the solid one. */
    private fun consoleModel(sys: String, clear: Boolean): Console? {
        if (clear) carts.model("console-$sys-clear.glb")?.let { return Console(stage, it, carts.description("console-$sys-clear.glb"), clear = true) }
        return carts.model("console-$sys.glb")?.let { Console(stage, it, carts.description("console-$sys.glb")) }
    }

    /** The board of the console on show, loaded the first time it's needed. */
    private fun circuit(): ConsoleBoard? {
        val e = ex(); val l = loaded[e.sys] ?: return null
        l.board?.let { return it }
        return carts.model("board-${e.sys}.glb")?.let { ConsoleBoard(stage, it, consoles.info[e.sys]?.board, sampler) }?.also { it.hide(); l.board = it; circuit = it }
    }

    /** Puts colors on the console on show, swapping in the see-through model when its shell needs it (or no longer does). */
    private fun paint(parts: Map<String, PartPaint>) {
        val l = loaded[ex().sys] ?: return
        val clear = ConsoleLook("", "", parts).seeThrough()
        var c = l.console
        if (c != null && c.clear != clear) { c.destroy(); c = consoleModel(ex().sys, clear); l.console = c; console = c }
        c?.paint(parts)
    }

    private fun unload() {
        loaded.values.forEach { it.console?.destroy(); it.case?.destroy(); it.cart?.destroy(); it.board?.destroy() }; loaded.clear()
        console = null; circuit = null; case = null; cart = null
    }

    // ---------- every frame ----------

    fun stick(x: Float, y: Float) { stickX = x; stickY = y }
    private var leftX = 0f; private var leftY = 0f
    /** The left stick: while looking closely it moves the model smoothly (true: taken, not a D-pad). */
    fun leftStick(x: Float, y: Float): Boolean { leftX = x; leftY = y; return inspecting }
    /** How far the looking-closely view has come in (0..1), so Home's top bar can step out of the way. */
    val lookK get() = Backdrop.ease(inspK)

    /**
     * Each view's resting angle. Home consoles (and the DS, which lies open on a table) are seen from a little above, so
     * their tops and fronts show; the upright handhelds face you.
     */
    /** The angle the console's halves hold still at, taken apart Inside: a three-quarter view, like an exploded drawing. */
    private fun insideRest(): FloatArray = if (ex().sys in HANDHELDS) floatArrayOf(-.95f, .18f) else floatArrayOf(-.45f, .35f)

    private fun rest(): FloatArray {
        val hand = ex().sys in HANDHELDS
        // Inside, the board's own resting angle: in line with the halves; turned over (X), its printed underside toward you
        if (view == INSIDE) return insideRest().also { if (flipped) { if (hand) it[0] += PI.toFloat() else it[1] = -.85f } }
        return when (view) {
            0 -> if (hand) floatArrayOf(-.3f, -.08f) else floatArrayOf(-.45f, .42f)
            1 -> floatArrayOf(-.28f, -.1f)
            CART_INSIDE -> CART_REST.copyOf()
            OPEN_BOX -> floatArrayOf(-.12f, -.42f)
            else -> floatArrayOf(-.35f, -.12f)
        }.also { if (flipped) it[0] += PI.toFloat() }
    }

    fun tick(dt: Float) {
        time += dt
        shown = if (closing) maxOf(0f, shown - dt / .35f) else min(1f, shown + dt / .45f)
        val sk = Backdrop.ease(shown)
        inspK = if (inspecting) min(1f, inspK + dt / .4f) else maxOf(0f, inspK - dt / .35f)
        val ik = Backdrop.ease(inspK)
        inspZoom += (inspZoomTo - inspZoom) * min(1f, dt * 8f)
        if (inspecting) {
            fun dz(v: Float) = if (abs(v) < .15f) 0f else v
            panToX -= dz(leftX) * 700f * dt; panToY -= dz(leftY) * 700f * dt
            val lim = 260f * inspZoomTo; panToX = panToX.coerceIn(-lim, lim); panToY = panToY.coerceIn(-lim * .7f, lim * .7f)
        }
        panX += ((if (inspecting) panToX else 0f) - panX) * min(1f, dt * 10f); panY += ((if (inspecting) panToY else 0f) - panY) * min(1f, dt * 10f)
        backdrop.shade = ik * .75f
        panel.alpha = sk * (1 - ik); panel.translationX = (1 - sk) * 70f; timeline.alpha = sk * (1 - ik); hint.alpha = sk * (1 - ik); lookHint.alpha = sk * ik
        fun ease(cur: Float, want: Float, rate: Float) = cur + (want - cur) * min(1f, dt * rate)
        // switching consoles: the exhibit slides off to the side, the next one slides in from the other
        slideX = ease(slideX, slideTo, 14f)
        if (leaving >= 0 && abs(slideX - slideTo) < 60f) { val next = leaving > at; show(leaving); leaving = -1; slideX = if (next) RIGHT else LEFT; slideTo = 0f }
        // while nothing's moving, the consoles either side load ahead, one per frame
        age += dt
        if (leaving < 0 && age > .8f) listOf(at + 1, at - 1).firstOrNull { it in exhibits.indices && exhibits[it].sys !in loaded }?.let { load(exhibits[it]) }
        // the views flow into each other: the console waits below the screen and the cartridge (with its box) above it
        val onConsole = view == 0 || view == INSIDE
        if (age > .3f) consoleY = ease(consoleY, if (onConsole) 0f else OFF, 7f)   // opening, it waits for Home's cartridges to clear
        groupY = ease(groupY, if (onConsole) -OFF else 0f, 7f)
        inside = ease(inside, if (view == INSIDE) 1f else 0f, 4.5f)
        val gone = groupY < -OFF * .85f   // the cartridge and box are out of sight above
        // one line of steps, as on a game's page (BoxFlow): the open box slides in from the left, the cartridge slots into
        // its tray, the cover shuts; and back the same way. Out of sight above (on the console), it's simply where the
        // next view wants it, so the console to the closed box arrives already closed
        // Cartridge Inside and the box take turns: the halves close round the board before the box comes, and the box
        // goes before the cartridge comes apart
        val boxTo = if (case == null || cartInside > .02f) 0f else when (view) { OPEN_BOX -> 2f; CLOSED_BOX -> 3f; else -> 0f }
        cartInside = ease(cartInside, if (view == CART_INSIDE && flow.s < .02f) 1f else 0f, 4.5f)
        if (cartInside < .002f && view != CART_INSIDE) cartInside = 0f
        if (gone) flow.snap(boxTo)
        else {
            val was = flow.s
            flow.tick(dt, boxTo)
            if (was < 2f && flow.s >= 2f) Sounds.play(Sounds.CLICK)
            if (was < 3f && flow.s >= 3f) Sounds.play(Sounds.CLOSE)
        }
        case?.open = 1 - flow.closed
        // turning: the stick while held, a coast when let go, then back to the view's resting angle
        val sx = dead(stickX); val sy = dead(stickY); val r = rest()
        if (!closing && (sx != 0f || sy != 0f)) { vYaw += (sx * 3.4f - vYaw) * min(1f, dt * 8f); vPitch += (sy * 2.6f - vPitch) * min(1f, dt * 8f); idle = 0f }
        else { vYaw *= exp(-dt * 1.7f); vPitch *= exp(-dt * 2.4f); idle += dt }
        yaw += vYaw * dt; pitch = (pitch + vPitch * dt).coerceIn(-1.35f, 1.35f)
        if (inspecting) idle = 0f   // looking closely: it stays as you turned it
        if (idle > 1.2f) {
            val target = r[0] + (2 * PI.toFloat()) * ((yaw - r[0]) / (2 * PI)).roundToLong()
            val k = min(1f, dt * 3f); yaw += (target - yaw) * k; pitch += (r[1] - pitch) * k
        }
        stage.turnKey(wrap(yaw - r[0]) * .3f)
        place(sk)
    }

    private fun place(sk: Float) {
        backdrop.shadows.clear()
        // looking closely: whatever is showing moves to the middle of the screen and grows (L1/R1 zoom, the d-pad moves)
        val look = Backdrop.ease(inspK); val grow = lerp(1f, 1.5f * inspZoom, look)
        val cx = lerp(360f + slideX, Stage.W / 2 + panX, look); val cy = lerp(330f + sin(time * .8f) * 4f, Stage.H / 2 - 10f + panY, look)
        // the console, sized by its whole depth too, so deep ones (seen from above) fit as well as flat ones
        // Its board shows through a see-through shell, and Inside the console comes apart like an exploded drawing: as it
        // turns to the Inside angle, its halves slide away from the board, which stays in the middle. There the stick turns
        // only the board; the halves hold still. Leaving, the board turns back into place and the halves close round it.
        console?.let { m ->
            val ik = Backdrop.ease(inside.coerceIn(0f, 1f)); val ir = insideRest()
            val y = cy + consoleY
            val b = if (m.clear || inside > .01f) circuit() else { circuit?.hide(); null }
            if (y > Stage.H + 400) { m.hide(); b?.hide() }
            else {
                val diag = kotlin.math.sqrt(m.half[0] * m.half[0] + m.half[1] * m.half[1] + m.half[2] * m.half[2])
                val cyaw = yaw + wrap(ir[0] - yaw) * ik; val cpitch = pitch + (ir[1] - pitch) * ik
                m.place(cx, y, 30f, cyaw, cpitch, 2 * 215f * maxOf(m.half[0], m.half[1]) / diag * (1 - .08f * ik) * grow)
                val apart = consoles.info[ex().sys]?.explode
                m.explode(ik, apart, if (b != null && apart != null) reach(b, apart.axis, cyaw, cpitch) else 0f)
                if (b != null) { if (ik < .002f) b.follow(m.matrix) else b.turnIn(m, yaw, pitch) }
                shadow(cx, y, 360f)
            }
        }
        // the case: open, at most 400 across, its cover lying to the left (so it sits right to stay centred), slid in from
        // off the left edge; closed, a little bigger (up to 470 tall), so the front shows well
        val gy = cy + groupY
        val c = case
        val inK = flow.boxIn
        if (c != null) {
            if (flow.s < .001f || gy < -400) c.hide()
            else {
                val big = 2 * maxOf(c.half[0], c.half[1])
                val open = min(400f / big, 300f / (2 * c.half[0]))
                val caseS = lerp(open, maxOf(open, min(470f / (2 * c.half[1]), 420f / (2 * c.half[0]))), flow.closed)
                val shift = maxOf(0f, -kotlin.math.cos(PI.toFloat() * .97f * c.open)) * c.half[0] * caseS * kotlin.math.cos(yaw)
                val at = cx + shift
                c.place(at - (1 - inK) * (at + 4 * c.half[0] * caseS + 40f), gy, 20f, yaw, pitch, caseS * big * grow)
            }
        }
        // the cartridge: free in the middle, then just over the tray as the box comes in, then down into it
        val ct = cart ?: return
        if (gy < -400) { ct.place(-4000f, 0f, 0f, 0f, 0f, 1f); return }
        // Cartridge Inside, as the console's Inside: it turns to a three-quarter view and comes apart like an exploded
        // drawing (the halves straight out from the board), a little smaller so the spread fits; there the stick turns
        // only the board, and the halves move further off while it needs the room
        val ck = if (ct.canOpen) Backdrop.ease(cartInside.coerceIn(0f, 1f)) else 0f
        val cyaw = yaw + wrap(CART_REST[0] - yaw) * ck; val cpitch = pitch + (CART_REST[1] - pitch) * ck
        val spread = run { val st = ct.style; val d = st.depth * 3.8f + 48f
            (maxOf(st.w, st.h) * 1.12f / (st.w * abs(kotlin.math.cos(CART_REST[0])) + d * abs(sin(CART_REST[0])))).coerceAtMost(1f) }
        val free = FloatArray(16).also { freeMatrix(it, ct, cx, gy, lerp(1f, spread, ck) * grow, cyaw, cpitch) }
        if (ct.canOpen) {
            if (ck < .001f) ct.inside(0f)
            else {
                val r = turnAgainst(cyaw, cpitch)
                val bh = ct.boardHalf!!
                val along = (0..2).sumOf { i -> (abs(r[i * 4 + 2]) * bh[i]).toDouble() }.toFloat()
                ct.inside(ck, r, maxOf(0f, along - bh[2]) * 1.08f)
            }
        }
        if (c == null || flow.s < .001f) ct.placeMatrix(free)
        else {
            val tray = c.mediaMatrix()
            val hover = c.hoverMatrix(ct.style.h, ct.style.depth)
            ct.placeMatrix(if (flow.s <= 1f) Poses.blend(free, hover, inK) else Poses.blend(hover, tray, flow.down))
        }
        shadow(cx, gy, 380f)
    }

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

    private fun freeMatrix(m: FloatArray, c: Cart, x: Float, y: Float, k: Float, ry: Float = yaw, rx: Float = pitch) {
        val s = 380f * k / maxOf(c.style.w, c.style.h)
        android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.translateM(m, 0, x, Stage.H - y, 60f)
        android.opengl.Matrix.rotateM(m, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f)
        android.opengl.Matrix.rotateM(m, 0, Math.toDegrees(ry.toDouble()).toFloat(), 0f, 1f, 0f)
        android.opengl.Matrix.scaleM(m, 0, s, s, s)
    }

    /**
     * How much further the halves move off so a turned board can't reach into them: how far the board, turned by the
     * stick against the halves' angle, now reaches along the seam's axis, beyond how far it reaches lying in place.
     */
    private fun reach(b: ConsoleBoard, axis: Char, cyaw: Float, cpitch: Float): Float {
        val r = turnAgainst(cyaw, cpitch)
        val a = if (axis == 'x') 0 else if (axis == 'y') 1 else 2
        val along = (0..2).sumOf { i -> (abs(r[i * 4 + a]) * b.half[i]).toDouble() }.toFloat()
        return maxOf(0f, along - b.half[a]) * 1.08f
    }

    /** The stick's turn (yaw, pitch) as seen from something held at (ry, rx): how a board turns against its still halves. */
    private fun turnAgainst(ry: Float, rx: Float): FloatArray {
        fun rot(ry: Float, rx: Float) = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0)
            android.opengl.Matrix.rotateM(it, 0, Math.toDegrees(rx.toDouble()).toFloat(), 1f, 0f, 0f); android.opengl.Matrix.rotateM(it, 0, Math.toDegrees(ry.toDouble()).toFloat(), 0f, 1f, 0f) }
        val ct = FloatArray(16); android.opengl.Matrix.transposeM(ct, 0, rot(ry, rx), 0)
        return FloatArray(16).also { android.opengl.Matrix.multiplyMM(it, 0, ct, 0, rot(yaw, pitch), 0) }
    }

    private fun shadow(x: Float, y: Float, w: Float) {
        val k = (1 - abs(y - 330f) / 500f).coerceIn(0f, 1f); if (k < .05f || w < 2f) return
        backdrop.shadows.add(floatArrayOf(x, y + 218f, w * k, .9f * k))
    }

    // ---------- buttons ----------

    fun input(a: String) {
        if (closing) return
        if (inspecting) {
            when (a) {
                "rb" -> if (inspZoomTo < 3f) { inspZoomTo += .5f; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "lb" -> if (inspZoomTo > 1f) { inspZoomTo -= .5f; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
                "left" -> panToX += 90f; "right" -> panToX -= 90f; "up" -> panToY += 90f; "down" -> panToY -= 90f
                "b", "y", "a" -> { inspecting = false; Sounds.play(Sounds.CLOSE) }
                "x" -> { flipped = !flipped; vYaw = 0f; idle = 9f; Sounds.play(Sounds.TURN) }
            }
            val lim = 260f * inspZoomTo; panToX = panToX.coerceIn(-lim, lim); panToY = panToY.coerceIn(-lim * .7f, lim * .7f)
            return
        }
        if (a == "y" && (zone == Zone.ROWS || zone == Zone.FACTS) && leaving < 0) { inspecting = true; inspZoomTo = 1f; panToX = 0f; panToY = 0f; Sounds.play(Sounds.OPEN); return }
        when (a) {
            "lt", "rt" -> return if (zone != Zone.ROWS && zone != Zone.FACTS) Sounds.play(Sounds.BUMP) else switchTo(at + if (a == "lt") -1 else 1)   // a list is about this console (L1 and R1 step between pages)
            "x" -> { flipped = !flipped; vYaw = 0f; idle = 9f; Sounds.play(Sounds.TURN); return }
        }
        when (zone) {
            Zone.SHELLS -> shellsInput(a)
            Zone.LOOKS -> looksInput(a)
            Zone.PARTS -> partsInput(a)
            Zone.PAINT -> painting?.input(a)
            Zone.FACTS -> when (a) {
                "up" -> if (scroll.scrollY > factsHead.top - 40 + 10) scroll.smoothScrollBy(0, -140) else toRows()
                "down" -> if (scroll.scrollY + scroll.height < col.height) scroll.smoothScrollBy(0, 140) else Sounds.play(Sounds.BUMP)
                "b" -> close()
                "left" -> switchTo(at - 1); "right" -> switchTo(at + 1)
            }
            Zone.ROWS -> when (a) {
                "up" -> if (!rows.move(-1)) Sounds.play(Sounds.BUMP) else Sounds.play(Sounds.MOVE)
                "down" -> if (!rows.move(1)) { zone = Zone.FACTS; facts.background = round(Theme.chip, 22f, Theme.sel, 2.5f); rows.active = false; scroll.smoothScrollTo(0, factsHead.top - 40); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.MOVE)
                "left", "right" -> if (rows.current()?.key == "view") setView(next(if (a == "left") -1 else 1)) else switchTo(at + if (a == "left") -1 else 1)
                "a" -> when (rows.current()?.key) { "view" -> setView(next(1)); "look" -> startLooks(); "shell" -> startShells() }
                "b" -> close()
            }
        }
    }

    private fun toRows() { zone = Zone.ROWS; facts.background = null; rows.active = true; rows.invalidate(); scroll.smoothScrollTo(0, 0); Sounds.play(Sounds.MOVE) }

    /** The next view that way, past Cartridge Inside for a disc or card (nothing to take apart). */
    private fun next(d: Int): Int {
        var v = (view + d + views.size) % views.size
        if (v == CART_INSIDE && cart?.canOpen != true) v = (v + d + views.size) % views.size
        return v
    }

    private fun setView(v: Int) {
        view = v; idle = 9f; vYaw = 0f; fillRows(); Sounds.play(Sounds.MOVE) }

    private fun switchTo(k: Int) {
        if (k !in exhibits.indices) return Sounds.play(Sounds.BUMP)
        if (leaving >= 0) return
        leaving = k; slideTo = if (k > at) LEFT else RIGHT; Sounds.play(Sounds.MOVE)
        timeline.at = k; timeline.invalidate()
    }

    // ---------- a console's default shell ----------

    private fun startShells() {
        val e = ex(); val style = carts.styles[e.sys] ?: return
        before = games.defaultShell(e.sys)
        val chosen = before ?: style.variants[0].name
        val l = ArrayList<Options.Row>()
        l += Options.Row(Options.HEAD, "Defaults for ${Games.NAMES[e.sys] ?: style.name}")
        style.variants.forEach { l += Options.Row("v", it.name, sub = it.games.ifEmpty { "A color it came in" }, check = it.key == chosen, dot = it.color, tag = it.key) }
        l += Options.Row(Options.HEAD, "Custom")
        val custom = carts.shells.all
        custom.forEach { l += Options.Row("c", it.name, sub = Materials.of(it.material).name, check = it.key == chosen, dot = it.color, tag = it.key) }
        if (custom.isEmpty()) l += Options.Row("none", "No shells of your own yet", sub = "Make one in the Studio: a game's Customize, then + Add new")
        list.onFocus = null
        list.rows = l; list.reset(); l.indexOfFirst { it.tag == chosen }.takeIf { it >= 0 }?.let { list.focus(it) }
        list.onFocus = { previewShell(); Sounds.play(Sounds.MOVE) }
        listHead.text = "DEFAULT SHELL"
        showList()
        zone = Zone.SHELLS
        if (view != 1) setView(1)   // show the cartridge while choosing
        Sounds.play(Sounds.OPEN)
    }

    private fun previewShell() {
        val key = list.current()?.tag as? String ?: return
        val e = ex(); val v = carts.variantFor(e.sys, key) ?: return
        val c = cart
        if (c == null || (v.clear != null) != (c.variant.clear != null)) { c?.destroy(); cart = carts.make(e.sys, e.example, v); carts.refinish(cart, e.example, games.labelFinish); loaded[e.sys]?.cart = cart } else c.apply(v)
    }

    private fun shellsInput(a: String) {
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "a" -> {
                val key = list.current()?.tag as? String ?: return
                val e = ex()
                games.setDefaultShell(e.sys, if (key == carts.styles[e.sys]?.variants?.get(0)?.name) null else key)
                shellsChanged = true; Sounds.play(Sounds.PICK); endShells()
                if (carts.variantFor(e.sys, key)?.let { it.sheen || it.clear != null || it.material != null } == true) vYaw += 1.6f   // show it off
            }
            "b" -> { Sounds.play(Sounds.CLOSE); endShells(); val e = ex(); carts.variantFor(e.sys, before)?.let { previewTo(it) } }
        }
    }
    private fun previewTo(v: Variant) {
        val e = ex(); val c = cart
        if (c == null || (v.clear != null) != (c.variant.clear != null)) { c?.destroy(); cart = carts.make(e.sys, e.example, v); carts.refinish(cart, e.example, games.labelFinish); loaded[e.sys]?.cart = cart } else c.apply(v)
    }
    private fun endShells() = endList()

    private fun showList() { scroll.visibility = View.GONE; listHead.visibility = View.VISIBLE; list.visibility = View.VISIBLE; hints(); panel.requestLayout() }
    private fun endList() {
        list.onFocus = null
        scroll.visibility = View.VISIBLE; listHead.visibility = View.GONE; list.visibility = View.GONE; panel.requestLayout()
        zone = Zone.ROWS; fillRows(); hints()
    }
    /** L and R move along the timeline only from the console's page, not from a list about it: their hints go with them. */
    private fun hints() { val on = if (zone == Zone.ROWS || zone == Zone.FACTS) View.VISIBLE else View.GONE; hint.getChildAt(0).visibility = on; hint.getChildAt(1).visibility = on }

    // ---------- a console's colors, and the console Studio ----------

    /** The shell's color in these colors, for the list's dots. */
    private fun shellDot(l: ConsoleLook) = l.parts["shell"]?.color ?: console?.ownColor("shell")

    private fun startLooks() {
        val e = ex(); if (consoles.info[e.sys] == null) return
        lookBefore = games.consoleLook(e.sys); previewed = lookBefore
        fillLooks(consoles.lookFor(e.sys, lookBefore)?.key)
        zone = Zone.LOOKS
        if (view != 0 && view != INSIDE) setView(0)   // show the console while choosing
        Sounds.play(Sounds.OPEN)
    }

    private fun fillLooks(focusKey: String?) {
        val e = ex(); val info = consoles.info[e.sys] ?: return
        val chosen = consoles.lookFor(e.sys, games.consoleLook(e.sys))?.key
        val l = ArrayList<Options.Row>()
        l += Options.Row(Options.HEAD, "Defaults for ${Games.NAMES[e.sys] ?: info.name}")
        info.looks.forEach { l += Options.Row("v", it.name, sub = it.note, check = it.key == chosen, dot = shellDot(it), tag = it.key) }
        l += Options.Row(Options.HEAD, "Custom")
        consoles.custom.all(e.sys).forEach { l += Options.Row("c", it.name, sub = "Your own  ·  Y to edit", check = it.key == chosen, dot = shellDot(it), tag = it.key) }
        l += Options.Row("new", "+ Add new", sub = "Paint each part in the Studio", tag = "new")
        list.onFocus = null
        list.rows = l; list.reset(); l.indexOfFirst { it.tag == focusKey }.takeIf { it >= 0 }?.let { list.focus(it) }
        list.onFocus = { previewLook(); Sounds.play(Sounds.MOVE) }
        listHead.text = "CONSOLE COLORS"
        showList()
    }

    private fun previewLook() {
        val key = list.current()?.tag as? String ?: return
        if (key == "new") return   // keeps showing the last one you were on
        consoles.lookFor(ex().sys, key)?.let { previewed = it.key; paint(it.parts) }
    }

    private fun looksInput(a: String) {
        val e = ex()
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "a" -> {
                val key = list.current()?.tag as? String ?: return
                if (key == "new") return openPaint(null)
                games.setConsoleLook(e.sys, if (key == consoles.info[e.sys]?.looks?.firstOrNull()?.name) null else key)
                Sounds.play(Sounds.PICK); endList()
                if (consoles.lookFor(e.sys, key)?.parts?.values?.any { it.material != "plastic" } == true) vYaw += 1.4f   // show off the finish
            }
            "b" -> { Sounds.play(Sounds.CLOSE); consoles.lookFor(e.sys, lookBefore)?.let { paint(it.parts) }; endList() }
            "y" -> consoles.custom.get(e.sys, list.current()?.tag as? String)?.let { lookOptions(it) }
        }
    }

    /** Y on your own colors: change them in the Studio, or delete them. */
    private fun lookOptions(l: ConsoleLook) {
        val e = ex()
        host.pick(Picker(ctx, l.name, listOf(Picker.Row("Edit in the Studio", "Paint its parts again"), Picker.Row("Delete", "The console goes back to its standard colors"))) { k ->
            if (k == 0) openPaint(l)
            else {
                consoles.custom.delete(l.custom ?: return@Picker)
                if (games.consoleLook(e.sys) == l.key) games.setConsoleLook(e.sys, null)
                if (lookBefore == l.key) lookBefore = null
                fillLooks(null); previewLook(); Sounds.play(Sounds.CLOSE)
            }
        })
    }

    /** The console Studio: its parts in a list, each painted with the cartridge Studio's materials and colors. */
    private fun openPaint(edit: ConsoleLook?) {
        val e = ex()
        // starts from yours being changed, or from the colors you were on in the list
        val start = edit ?: consoles.lookFor(e.sys, previewed)
        editingLook = edit
        working.clear(); start?.parts?.let { working.putAll(it) }
        paint(working)
        zone = Zone.PARTS; fillParts(0)
        Sounds.play(Sounds.OPEN)
    }

    private fun fillParts(at: Int) {
        val e = ex(); val info = consoles.info[e.sys] ?: return
        val l = ArrayList<Options.Row>()
        info.parts.forEach { (role, name) ->
            val p = working[role]
            l += Options.Row("part", name, sub = p?.let { Materials.of(it.material).name } ?: "As it was made", dot = p?.color ?: console?.ownColor(role), tag = role)
        }
        l += Options.Row("save", if (editingLook != null) "Save changes" else "Save these colors", sub = "B to leave without saving", primary = true)
        list.onFocus = null
        list.rows = l; list.reset(); list.focus(at.coerceIn(0, l.size - 1))
        list.onFocus = { Sounds.play(Sounds.MOVE) }
        listHead.text = "STUDIO  ·  ${(editingLook?.name ?: info.name).uppercase()}"
        showList()
    }

    private fun partsInput(a: String) {
        val e = ex()
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "a" -> when (list.current()?.key) {
                "part" -> openPart(list.current()?.tag as String)
                "save" -> host.askText("Name these colors", editingLook?.name ?: "My ${Games.NAMES[e.sys] ?: consoles.info[e.sys]?.name ?: "console"}") { name ->
                    val l = consoles.custom.save(e.sys, ConsoleLook(name.trim().ifEmpty { "My colors" }.take(28), "", LinkedHashMap(working),
                        editingLook?.custom ?: java.lang.Long.toString(System.currentTimeMillis(), 36)))
                    Sounds.play(Sounds.PICK); editingLook = null
                    zone = Zone.LOOKS; fillLooks(l.key); previewLook()
                }
            }
            "b" -> { Sounds.play(Sounds.CLOSE); val back = editingLook?.key; editingLook = null; zone = Zone.LOOKS; fillLooks(back ?: list.current()?.tag as? String); previewLook() }
        }
    }

    /** One part in the Studio: every change shows on the console at once; Done keeps it, Cancel puts it back. */
    private fun openPart(role: String) {
        val c = console ?: return
        val p = working[role]
        paintRole = role; paintBefore = p
        val v = StudioView(ctx, p?.color ?: c.ownColor(role), p?.material ?: c.ownMaterial(role), if (p == null) "As it was made" else "Custom", okLabel = "Done")
        v.onChange = { color, material -> working[role] = PartPaint(color, material); paint(working) }
        v.onSave = { closePart(keep = true) }
        v.onCancel = { closePart(keep = false) }
        painting = v
        list.visibility = View.GONE
        panel.addView(v, LinearLayout.LayoutParams(StudioView.W, StudioView.H).apply { topMargin = 8; leftMargin = -14; rightMargin = -14 })
        listHead.text = "STUDIO  ·  ${(consoles.info[ex().sys]?.parts?.firstOrNull { it.first == role }?.second ?: role).uppercase()}"
        zone = Zone.PAINT
        Sounds.play(Sounds.OPEN)
    }

    private fun closePart(keep: Boolean) {
        if (!keep) { paintBefore?.let { working[paintRole] = it } ?: working.remove(paintRole); paint(working); Sounds.play(Sounds.CLOSE) } else Sounds.play(Sounds.PICK)
        painting?.let { panel.removeView(it) }; painting = null
        zone = Zone.PARTS
        fillParts(consoles.info[ex().sys]?.parts?.indexOfFirst { it.first == paintRole } ?: 0)
    }

    /** Whether L1 and R1 may step to the next page: on the Museum's lists, not looking closely or changing colors. */
    val canLeave get() = !closing && !inspecting && (zone == Zone.ROWS || zone == Zone.FACTS)
    fun close() { if (!closing) { closing = true; Sounds.play(Sounds.CLOSE); board.postDelayed({ destroy(); host.closed(this, shellsChanged) }, 380) } }

    fun destroy() {
        unload(); backdrop.shadows.clear(); stage.turnKey(0f)
        board.removeView(panel); board.removeView(timeline); board.removeView(hint); board.removeView(lookHint); backdrop.shade = 0f
    }

    // ---------- helpers ----------

    private fun dead(v: Float) = if (abs(v) < .18f) 0f else (v - .18f * Math.signum(v)) / .82f
    private fun wrap(a: Float): Float { var x = a % (2 * PI.toFloat()); if (x > PI) x -= 2 * PI.toFloat(); if (x < -PI) x += 2 * PI.toFloat(); return x }
    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }
    private fun key(btn: String, say: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(17f, Fonts.bold, Theme.fg).apply { this.text = btn; gravity = Gravity.CENTER; background = round(Color.TRANSPARENT, 15f, Theme.fg3, 1.5f); minWidth = 31; setPadding(8, 5, 8, 5) })
        addView(text(19f, Fonts.body, Theme.fg2).apply { this.text = say; setPadding(9, 0, 0, 0) })
    }

    companion object {
        private val CART_REST = floatArrayOf(-1.05f, .18f)   // Cartridge Inside: a three-quarter view, like an upright handheld's Inside
        private val HANDHELDS = setOf("gb", "gbc", "gba", "psp", "android")   // upright; the DS lies open, like a home console
        private const val OFF = 720f   // how far off screen a piece waits
        // the views' order: Console, Cartridge, Cartridge Inside, In its box, Box closed, Console Inside (left of Console)
        private const val CART_INSIDE = 2; private const val OPEN_BOX = 3; private const val CLOSED_BOX = 4; private const val INSIDE = 5
        private const val LEFT = -900f; private const val RIGHT = 1300f   // slides that clear the screen: the exhibit sits left of centre
        private val ORDER = listOf("nes", "genesis", "gb", "snes", "ps1", "n64", "gbc", "gba", "ds", "psp", "android")
        private val LONG = mapOf("nes" to "Nintendo Entertainment System", "snes" to "Super Nintendo", "n64" to "Nintendo 64", "genesis" to "Sega Genesis",
            "gb" to "Game Boy", "gbc" to "Game Boy Color", "gba" to "Game Boy Advance", "ps1" to "PlayStation", "ds" to "Nintendo DS",
            "psp" to "PlayStation Portable", "android" to "Android handhelds")
    }
}

/** The Museum's timeline: a dot for each console along a line, oldest first, the one on show lit with its year. */
class Timeline(ctx: Context) : View(ctx) {
    var names: List<String> = emptyList()
    var at = 0
    private var shownAt = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        if (names.isEmpty()) return
        shownAt += (at - shownAt) * .25f; if (abs(at - shownAt) > .01f) postInvalidateOnAnimation() else shownAt = at.toFloat()
        val l = 30f; val r = width - 30f; val y = 46f; val step = (r - l) / maxOf(1, names.size - 1)
        p.color = Theme.line; p.strokeWidth = 2f; c.drawLine(l, y, r, y, p)
        p.color = Theme.accentOf(null); c.drawLine(l, y, l + step * shownAt, y, p)   // the line lights up to here
        names.forEachIndexed { k, n ->
            val x = l + step * k; val on = k == at
            p.color = if (k <= at) Theme.accentOf(null) else Theme.fg3; c.drawCircle(x, y, if (on) 8f else 4.5f, p)
            if (on || names.getOrNull(k - 1) != n) {   // years shared by two consoles are written once
                p.typeface = if (on) Fonts.bold else Fonts.body; p.textSize = if (on) 20f else 15f; p.color = if (on) Theme.fg else Theme.fg3
                p.textAlign = Paint.Align.CENTER; c.drawText(n, x, y - 18f, p); p.textAlign = Paint.Align.LEFT
            }
        }
    }
}
