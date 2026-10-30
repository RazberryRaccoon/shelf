package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Color
import android.view.View
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Starting a game, and coming back from it (0.25, studied frame by frame in tools/lighting-rig's launch scene).
 *
 * Starting, about three seconds: the game's console rises into view in its colors from the Museum, settling into its
 * turn as it slows (one that takes its game in the back turns to show it on the way). The cartridge is lifted from where
 * it was, carried along a curve that passes in front of everything, and lines up over its slot, slowing as it arrives;
 * a breath, and it's pushed in, seating with a click. Each console then does what the real one does: the NES's door
 * swings in as the cartridge passes and drops shut once it's pressed down into the connector (clunk); the PlayStation's
 * open button is pressed and its lid pops up for the disc and closes over it; the PSP's door opens and closes; a
 * handheld turns back to face you; the DS opens. Then its power switch slides over (or its power button is pressed),
 * the power light comes on with a soft chime, a handheld's screen glows with the game's title screen, and the screen
 * closes to black by the transition chosen in Settings (see Curtain). Then the game starts.
 *
 * While it takes its game, the console and the game waiting over its slot both fit on the screen: a tall handheld
 * taking a cartridge from above sits lower and a little smaller, and comes back up to size as it turns to you.
 *
 * Coming back plays the same thing backwards, a little quicker, out of black: the screen dims, the switch slides back
 * and the light goes off, the game comes out of its slot and flies home, and the console sinks away.
 * Any button skips to the end of either.
 */
class Launch(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
             private val carts: Carts, private val consoles: Consoles, private val games: Games, private val meta: Meta,
             val game: Game, private val cart: Cart?, private val home: FloatArray, private val host: Host,
             /** Where the cartridge goes back to (on the page, its own spot: the box slides in for it again afterwards). */
             private val homeAgain: FloatArray = home) {
    interface Host {
        /** Starts the game; an error to show, or "" when it's on its way. */
        fun start(l: Launch): String
        /** Back where it started: Home (or the page) takes the cartridge again. */
        fun done(l: Launch)
    }

    enum class Phase { IN, PLAYING, OUT, DONE }
    var phase = Phase.IN; private set
    private var t = 0f
    private val info = consoles.info[game.sys]
    private val marks = info?.launch
    private val hand = game.sys in SCREENS        // it has a screen to show the title on
    private val upright = game.sys in UPRIGHT     // held upright, seen nearly face on (the DS lies open, like a home console)
    private val opensAfter = (marks?.lid?.rest ?: 0f) > .5f   // its lid rests open (the DS): closed for the card, then open
    private val lidFirst = marks?.lid != null && !opensAfter   // its lid opens for the game and closes after (the PlayStation, the PSP)
    private val back = (marks?.turn ?: 0f) != 0f   // it takes its game in the back: it turns round for it
    private val parts = marks?.parts ?: emptyMap()
    private val press = marks?.press
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val look = consoles.lookFor(game.sys, games.consoleLook(game.sys))
    private val console: Console? = run {
        val clear = look?.seeThrough() == true
        val name = if (clear) "console-${game.sys}-clear.glb" else "console-${game.sys}.glb"
        (carts.model(name) ?: carts.model("console-${game.sys}.glb"))?.let { Console(stage, it, carts.description(name), clear) }
            ?.also { c -> look?.let { c.paint(it.parts) }; c.power(0f); c.hide() }
    }
    private val circuit: ConsoleBoard? = if (console?.clear == true) carts.model("board-${game.sys}.glb")?.let { ConsoleBoard(stage, it, info?.board, sampler) } else null
    private var picture: Texture? = null
    private val black = Curtain(ctx, Curtain.pick(games.launchFx))
    private val sounded = HashSet<Int>()
    private var error = ""

    // ---------- the beats, in seconds ----------

    private val rise = .7f
    private val flight = floatArrayOf(.32f, 1.12f)
    private val openAt = .42f                                                             // the PlayStation's open button
    private val slide = floatArrayOf(flight[1] + .1f, flight[1] + .1f + if (press != null) .42f else .36f)
    private val after = slide[1] + if (press != null) .32f else .06f
    private val pressT = floatArrayOf(slide[1] + .06f, slide[1] + .26f)                    // the NES: pressed down
    private val lidClose = floatArrayOf(after + .02f, after + .4f)
    private val turnBack = floatArrayOf(after + .04f, after + .6f)
    private val lidOpen = floatArrayOf(after + .42f, after + .95f)                         // the DS opens
    private val powerAt = maxOf(after, if (lidFirst) lidClose[1] else 0f, if (back) turnBack[1] else 0f, if (opensAfter) lidOpen[0] + .3f else 0f) + .06f
    private val ledAt = if (parts["power"] != null) powerAt + .16f else powerAt
    private val screenT = floatArrayOf(ledAt + .12f, ledAt + .55f)
    private val darkT = floatArrayOf(ledAt + .4f, ledAt + .95f)
    /** Coming back: the same beats backwards from the screen fully on, this much quicker. */
    private val backFrom = screenT[1]; private val backSpeed = 1.3f
    private val frameOut = if (back) turnBack else floatArrayOf(after, after + .5f)

    init {
        board.put(black, -700, -700, 2734, 2150)   // past the board's edges too, so the letterbox goes dark with it
        if (hand) picture = titlePicture()
    }

    /** The game's title screen from libretro, or Shelf's own label painting until one has been downloaded. */
    private fun titlePicture(): Texture? = try {
        val b = meta.cached(game)?.pics?.get("title")?.let { meta.bitmap(it, 512) } ?: Paintings.label(480, 432, game.title)
        pictureTexture(stage, b).also { b.recycle() }
    } catch (e: Exception) { null }

    // ---------- timing ----------

    private fun clamp(v: Float) = v.coerceIn(0f, 1f)
    private fun ease(v: Float) = Backdrop.ease(clamp(v))
    /** Slowing to a stop (a cubic ease-out). */
    private fun out3(v: Float) = clamp(v).let { 1 - (1 - it) * (1 - it) * (1 - it) }
    /** Smoother still at both ends (smootherstep): nothing starts or stops with a jolt. */
    private fun smoother(v: Float) = clamp(v).let { it * it * it * (it * (it * 6 - 15) + 10) }
    private fun span(v: Float, a: FloatArray) = (v - a[0]) / (a[1] - a[0])
    /** A button: pressed in quickly, held a moment, let back to `rest` (an NES button stays a little in). */
    private fun pressK(u: Float, rest: Float) = when { u <= 0f -> 0f; u < .09f -> ease(u / .09f); else -> maxOf(rest, 1 - ease((u - .15f) / .14f) * (1 - rest)) }
    private fun once(k: Int, sound: Int) { if (sounded.add(k)) Sounds.play(sound) }

    /** Any button: straight to the fade (starting) or to the end (coming back); in the dark, back to Shelf. */
    fun input(a: String) {
        if (phase == Phase.PLAYING) return back()   // still here in the dark (the game never opened): come back
        if (phase == Phase.IN && t < darkT[0]) t = darkT[0]
        else if (phase == Phase.OUT) t = maxOf(t, backFrom / backSpeed - .12f)
    }

    /** Back from the game: the return animation, from black. */
    fun back() {
        if (phase != Phase.PLAYING) return
        phase = Phase.OUT; t = 0f; sounded.clear()
        black.kind = Curtain.FADE   // coming back: simply out of black (the transition is only for going in)
    }

    fun tick(dt: Float) {
        when (phase) {
            Phase.IN -> {
                t += dt
                place(t, going = true)
                black.k = if (phase == Phase.PLAYING) 1f else clamp(span(t, darkT))
                if (t >= darkT[1]) {
                    phase = Phase.PLAYING; black.k = 1f
                    error = host.start(this)
                    if (error.isNotEmpty()) back()   // it couldn't start: come straight back and say why
                }
            }
            Phase.PLAYING -> place(backFrom, going = true)
            Phase.OUT -> {
                t += dt
                val u = backFrom - t * backSpeed
                black.k = 1 - clamp(t / .45f)
                // the sounds going backwards: the screen and light off, the switch back, the game out of its seat
                if (u < ledAt + .05f) once(2, Sounds.POWER_OFF)
                if (parts["power"] != null && u < powerAt + .1f) once(3, Sounds.SWITCH)
                if (u < slide[1]) once(4, Sounds.CLICK)
                place(maxOf(0f, u), going = false)
                if (u <= 0f) finish()
            }
            Phase.DONE -> {}
        }
    }

    // ---------- where everything is ----------

    private val tmp = FloatArray(16); private val tmp2 = FloatArray(16)

    /** The console's size at rest (layout pixels across its larger side), as the Museum shows it. */
    private val px0 = console?.let { c -> 2 * 275f * maxOf(c.half[0], c.half[1]) / sqrt(c.half[0] * c.half[0] + c.half[1] * c.half[1] + c.half[2] * c.half[2]) } ?: 1f
    private fun yawOf(turnK: Float) = (if (upright) -.32f else -.5f) + (marks?.turn ?: 0f) * turnK
    private val pitch = if (upright) .1f else .42f

    /** Where the game's path along its slot is, from the console's placement: `path` 1 at its start, 0 in; `pressK` 1 pressed down. */
    private fun along(cm: FloatArray, path: Float, pressK: Float, out: FloatArray): FloatArray {
        val m = marks!!; val pr = press ?: ZERO
        android.opengl.Matrix.setIdentityM(tmp, 0)
        android.opengl.Matrix.translateM(tmp, 0, m.from[0] * path + pr[0] * (1 - pressK), m.from[1] * path + pr[1] * (1 - pressK), m.from[2] * path + pr[2] * (1 - pressK))
        android.opengl.Matrix.multiplyMM(tmp2, 0, tmp, 0, m.seat, 0)
        android.opengl.Matrix.multiplyMM(out, 0, cm, 0, tmp2, 0)
        return out
    }

    /**
     * How much smaller, and how much lower, the console sits while it takes its game, so the game waiting over the slot
     * fits on the screen too: measured once from where both will be, through the camera's own lens.
     */
    private val fit: FloatArray = run {
        val c = console; val m = marks
        if (c == null || m == null) return@run floatArrayOf(1f, 0f)
        c.place(667f, 372f, 30f, yawOf(if (back) 1f else 0f), pitch, px0)
        val cm = c.matrix.copyOf(); val a = along(cm, 1f, 0f, FloatArray(16))
        var top = 1e9f; var bot = -1e9f
        val v = FloatArray(4); val p = FloatArray(4)
        fun corners(mat: FloatArray, centre: FloatArray, half: FloatArray) { for (n in 0 until 8) {
            for (i in 0..2) v[i] = centre[i] + if ((n shr i) and 1 == 1) half[i] else -half[i]; v[3] = 1f
            android.opengl.Matrix.multiplyMV(p, 0, mat, 0, v, 0)
            val y = Stage.H - (Stage.H / 2 + (p[1] - Stage.H / 2) * Stage.DIST / (Stage.DIST - p[2]))
            top = minOf(top, y); bot = maxOf(bot, y) } }
        cart?.let { ct -> val bb = ct.asset.boundingBox; corners(a, bb.center, bb.halfExtent) }
        corners(cm, c.center, c.half)
        c.hide()
        val fr = minOf(1f, 610f / (bot - top)); val t1 = 372 + (top - 372) * fr; val b1 = 372 + (bot - 372) * fr
        floatArrayOf(fr, if (t1 < 70f) minOf(70 - t1, 715 - b1) else 0f)
    }

    /** Everything at time t of the start animation (coming back plays it backwards: `going` false only changes the sounds and where the game flies to). */
    private fun place(t: Float, going: Boolean) {
        val c = console
        backdrop.shadows.clear()
        val from = if (going) home else homeAgain
        if (c == null) { cart?.placeMatrix(from); return }
        if (going) {
            if (t >= slide[1]) once(0, Sounds.CLICK)
            if (press != null && t >= pressT[1]) once(5, Sounds.CLUNK)
            if (parts["power"] != null && t >= powerAt + .1f) once(6, Sounds.SWITCH)
            if (t >= ledAt) once(1, Sounds.CHIME)
            if (lidFirst && t >= openAt) once(7, Sounds.MOVE)
        }
        // the console: up from below, slowing into its turn (a back-loader turned to show its back, then back to you)
        val riseK = out3(t / rise)
        val turnK = if (back) smoother((t - .08f) / .74f) - smoother(span(t, turnBack)) else 0f
        val fk = 1 - smoother(span(t, frameOut)); val fr = 1 + (fit[0] - 1) * fk; val fdy = fit[1] * fk
        val x = 667f; val y = 372f + fdy + (1 - riseK) * 640f
        val size = px0 * fr * (.94f + .06f * riseK)
        c.place(x, y, 30f, yawOf(turnK) - .38f * (1 - riseK), pitch, size)
        // the lid: the PlayStation's pops up as its button is pressed and closes over the disc; the PSP's door opens as it
        // turns and closes after; the DS's opens at the end
        val lidK = when {
            lidFirst -> out3((t - openAt - .1f) / .4f) - smoother(span(t, lidClose))
            opensAfter -> smoother(span(t, lidOpen))
            else -> 0f
        }
        c.lid(lidK, marks?.lid)
        parts["open"]?.let { c.move("open", pressK(t - openAt, 0f), it) }
        // the power: the switch slides over (or the button is pressed), then the light, then a handheld's screen
        parts["power"]?.let { p -> c.move("power", if (p.button) pressK(t - powerAt, if (game.sys == "nes") .45f else 0f) else smoother((t - powerAt) / .2f), p) }
        c.power(clamp((t - ledAt) / .3f))
        c.picture(picture, sampler, if (hand) clamp(span(t, screenT)) else 0f)
        circuit?.follow(c.matrix)
        // the black closes on the power light (or the screen)
        val target = (if (hand) marks?.screen else marks?.led) ?: marks?.led ?: marks?.screen
        target?.let { tp -> val q = FloatArray(4); android.opengl.Matrix.multiplyMV(q, 0, c.matrix, 0, floatArrayOf(tp[0], tp[1], tp[2], 1f), 0); black.focus(q[0], Stage.H - q[1]) }
        backdrop.shadows.add(floatArrayOf(x, y + 200f * fr, 420f * fr, .8f * riseK))
        // the game: lifted from where it was, carried along a curve, lined up over its slot, pushed in (and pressed down)
        val m = marks ?: return
        val sK = smoother(span(t, slide)); val pK = if (press != null) smoother(span(t, pressT)) else 1f
        val cm = c.matrix
        // the NES's door swings in as the cartridge passes, and drops shut once it's pressed down
        parts["door"]?.let { d -> c.move("door", minOf(ease((sK - .02f) / .24f), 1 - ease((t - pressT[1] + .02f) / .3f)), d) }
        val ct = cart ?: return
        val path = along(cm, 1 - sK, pK, FloatArray(16))
        val u = span(t, flight)
        when {
            t < flight[0] -> {   // picked up: up a little and toward you
                val l = smoother(t / flight[0])
                ct.placeMatrix(from.copyOf().also { it[13] += 22f * l; it[14] += 60f * l })
            }
            u < 1f -> {
                val lifted = from.copyOf().also { it[13] += 22f; it[14] += 60f }
                val e = smoother(u)
                // turned to line up early (by four fifths of the way), so it arrives straight
                val pose = Poses.blend(lifted, path, smoother(minOf(1f, u / .8f)))
                // the way there, a curve: out toward you first, then in along the slot's own direction
                val dir = FloatArray(3) { i -> cm[i] * m.from[0] + cm[4 + i] * m.from[1] + cm[8 + i] * m.from[2] }
                val dl = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]).coerceAtLeast(1e-4f)
                for (i in 0..2) {
                    val a = lifted[12 + i]; val d = path[12 + i]
                    val b1 = a + floatArrayOf(40f, 60f, 160f)[i]; val b2 = d + dir[i] / dl * 140f + (if (i == 2) 60f else 0f)
                    val ie = 1 - e
                    pose[12 + i] = ie * ie * ie * a + 3 * ie * ie * e * b1 + 3 * ie * e * e * b2 + e * e * e * d
                }
                ct.placeMatrix(pose)
            }
            else -> ct.placeMatrix(path)
        }
    }

    private fun finish() {
        phase = Phase.DONE
        cart?.placeMatrix(homeAgain)
        console?.destroy(); circuit?.destroy(); picture?.let { stage.engine.destroyTexture(it) }
        board.removeView(black); backdrop.shadows.clear()
        if (error.isNotEmpty()) Sounds.play(Sounds.BUMP)
        host.done(this)
    }
    /** What went wrong starting it, if anything (shown once it's back). */
    val problem get() = error

    companion object {
        private val SCREENS = setOf("gb", "gbc", "gba", "ds", "psp", "android"); private val UPRIGHT = setOf("gb", "gbc", "gba", "psp", "android")
        private val ZERO = FloatArray(3)
    }
}

/**
 * The way the screen goes to black before a game starts, and comes out of it after (Settings › Starting a game). Drawn
 * over everything, the letterbox included; `k` is how much is covered, 0 to 1. The ideas come from film and video
 * transitions: an iris, wipes, blinds, a clock wipe, a dissolve of dots, a plain fade.
 */
class Curtain(ctx: Context, var kind: Int) : View(ctx) {
    var k = 0f; set(v) { field = v; invalidate() }
    private var fx = Stage.W / 2; private var fy = Stage.H / 2
    fun focus(x: Float, y: Float) { fx = x; fy = y }
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val path = android.graphics.Path()

    override fun onDraw(c: android.graphics.Canvas) {
        if (k <= 0f) return
        c.save(); c.translate(OFF, OFF)   // the view reaches past the board's edges; (0, 0) is the board's corner
        val e = Backdrop.ease(k.coerceIn(0f, 1f))
        if (e >= 1f) { c.drawColor(Color.BLACK); c.restore(); return }
        // the letterbox around the board darkens with it
        p.alpha = (255 * e).toInt(); c.drawRect(-OFF, -OFF, Stage.W + OFF, 0f, p); c.drawRect(-OFF, Stage.H, Stage.W + OFF, Stage.H + OFF, p)
        c.drawRect(-OFF, 0f, 0f, Stage.H, p); c.drawRect(Stage.W, 0f, Stage.W + OFF, Stage.H, p); p.alpha = 255
        val w = Stage.W; val h = Stage.H
        when (kind) {
            IRIS -> {   // darkness closing in from the edges to a circle around the power light or screen
                val far = maxOf(kotlin.math.hypot(fx, fy), kotlin.math.hypot(w - fx, fy), kotlin.math.hypot(fx, h - fy), kotlin.math.hypot(w - fx, h - fy))
                path.reset(); path.fillType = android.graphics.Path.FillType.EVEN_ODD
                path.addRect(0f, 0f, w, h, android.graphics.Path.Direction.CW); path.addCircle(fx, fy, far * (1 - e), android.graphics.Path.Direction.CW)
                c.drawPath(path, p)
            }
            WIPE -> soft(c, 0f, (h + 60f) * e - 60f, w)   // down from the top, its edge soft
            DIAGONAL -> {   // a slanted edge sweeping across from the left
                val x = -h * .5f + (w + h) * e
                path.reset(); path.moveTo(-1f, -1f); path.lineTo(x + h * .25f, -1f); path.lineTo(x - h * .25f, h + 1); path.lineTo(-1f, h + 1); path.close(); c.drawPath(path, p)
            }
            BLINDS -> { val n = 12; val bh = h / n; for (i in 0 until n) { val g = Backdrop.ease(((e * 1.4f) - i * .4f / n).coerceIn(0f, 1f)); c.drawRect(0f, i * bh, w, i * bh + bh * g, p) } }
            CLOCK -> drawClock(c, e)
            DOTS -> {   // a grid of dots growing from the middle outward until they join
                val s = 46f; val maxD = kotlin.math.hypot(w / 2, h / 2)
                var y = s / 2; while (y < h + s) { var x = s / 2; while (x < w + s) {
                    val d = kotlin.math.hypot(x - w / 2, y - h / 2) / maxD
                    val g = ((e * 1.6f) - d * .6f).coerceIn(0f, 1f)
                    if (g > 0f) c.drawCircle(x, y, s * .75f * g, p)
                    x += s }; y += s }
            }
            SHUTTER -> { val half = h / 2 * e; c.drawRect(0f, 0f, w, half, p); c.drawRect(0f, h - half, w, h, p) }   // closing from above and below, like eyelids
            else -> { p.alpha = (255 * e).toInt(); c.drawRect(0f, 0f, w, h, p); p.alpha = 255 }   // a plain fade
        }
        c.restore()
    }

    /** A clock wipe: a hand sweeping round from twelve, darkness behind it. */
    private fun drawClock(c: android.graphics.Canvas, e: Float) {
        val r = kotlin.math.hypot(Stage.W, Stage.H)
        c.save(); c.clipRect(0f, 0f, Stage.W, Stage.H)
        c.drawArc(Stage.W / 2 - r, Stage.H / 2 - r, Stage.W / 2 + r, Stage.H / 2 + r, -90f, 360f * e, true, p)
        c.restore()
    }

    /** A black band from the top down to y, with a soft edge below it. */
    private fun soft(c: android.graphics.Canvas, x0: Float, y: Float, w: Float) {
        c.drawRect(x0, 0f, w, maxOf(0f, y), p)
        p.shader = android.graphics.LinearGradient(0f, y, 0f, y + 60f, Color.BLACK, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(x0, y, w, y + 60f, p); p.shader = null
    }

    companion object {
        const val OFF = 700f
        const val FADE = 0; const val IRIS = 1; const val WIPE = 2; const val DIAGONAL = 3; const val BLINDS = 4; const val CLOCK = 5; const val DOTS = 6; const val SHUTTER = 7
        /** The names in Settings, in order; the last is a different one each time. */
        val NAMES = listOf("Fade", "Iris", "Wipe down", "Diagonal sweep", "Blinds", "Clock", "Dots", "Shutter", "A different one each time")
        fun pick(choice: Int) = if (choice >= NAMES.size - 1) (0 until NAMES.size - 1).random() else choice
    }
}

