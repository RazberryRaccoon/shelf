package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Color
import android.view.View
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
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

    private val rise = .8f
    private val flight = floatArrayOf(.3f, 1.0f)
    private val openAt = .4f                                                              // the PlayStation's open button
    private val slide = floatArrayOf(flight[1] + .16f, flight[1] + .46f)                   // wound up, then slammed in
    private val hit = slide[1]
    private val pressT = floatArrayOf(hit + .1f, hit + .26f)                              // the NES: slammed down too
    private val hit2 = if (press != null) pressT[1] else -1f
    private val after = (if (press != null) hit2 else hit) + .14f
    private val lidClose = floatArrayOf(after, after + .3f)
    private val turnBack = floatArrayOf(after + .02f, after + .55f)
    private val lidOpen = floatArrayOf(after + .4f, after + .9f)                          // the DS opens
    private val powerAt = maxOf(after, if (lidFirst) lidClose[1] + .1f else 0f, if (back) turnBack[1] else 0f, if (opensAfter) lidOpen[0] + .3f else 0f) + .06f
    private val ledAt = if (parts["power"] != null) powerAt + .14f else powerAt
    private val screenT = floatArrayOf(ledAt + .1f, ledAt + .5f)
    private val darkT = floatArrayOf(ledAt + .45f, ledAt + 1f)
    /** Coming back: the same beats backwards from the screen fully on, this much quicker. */
    private val backFrom = screenT[1]; private val backSpeed = 1.3f
    private val frameOut = if (back) turnBack else floatArrayOf(after, after + .5f)
    /** The sparks (going in only), in the console's color. */
    private val sparks = Sparks(ctx, Theme.accentOf(game.sys))

    init {
        board.put(sparks, 0, 0, 1334, 750)
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
    /** To 1, overshooting and settling, like a spring (u in seconds; d how quickly it settles, w how fast it swings). */
    private fun spring(u: Float, d: Float = 7f, w: Float = 13f) = if (u <= 0f) 0f else 1 - exp(-d * u) * cos(w * u)
    /** A hit, ringing down. */
    private fun bump(u: Float, d: Float = 7f, w: Float = 21f) = if (u <= 0f) 0f else exp(-d * u) * cos(w * u) * minOf(1f, u / .03f)
    /** A small rebound after a hit (its positive half-swings). */
    private fun rebound(u: Float) = if (u <= 0f) 0f else maxOf(0f, exp(-9 * u) * sin(24 * u))
    /** A button: snapped in, springing back to `rest` (an NES button stays a little in). */
    private fun pressK(u: Float, rest: Float) = when { u <= 0f -> 0f; u < .07f -> out3(u / .07f); else -> rest + (1 - rest) * (1 - spring(u - .12f, 9f, 16f)) }
    private fun once(k: Int, sound: Int) { if (sounded.add(k)) Sounds.play(sound) }

    /** Any button: straight to the fade (starting) or to the end (coming back); in the dark, back to Shelf. */
    fun input(a: String) {
        if (phase == Phase.PLAYING) return back()   // still here in the dark (the game never opened): come back
        if (phase == Phase.IN && t < darkT[0]) { t = darkT[0]; sparks.clear() }
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
                if (u < hit) once(4, Sounds.CLICK)
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
        val cm = c.matrix.copyOf(); val a = along(cm, 1.12f, 0f, FloatArray(16))
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
        val fr = minOf(1f, 600f / (bot - top)); val t1 = 372 + (top - 372) * fr; val b1 = 372 + (bot - 372) * fr
        floatArrayOf(fr, if (t1 < 75f) minOf(75 - t1, 712 - b1) else 0f)
    }

    /** The axis (the console's own) a hit squashes along: the way the game went in last. */
    private val hitDir = press ?: marks?.from ?: floatArrayOf(0f, 1f, 0f)
    private val ax = (0..2).maxByOrNull { abs(hitDir[it]) } ?: 1
    /** Where the game goes in: along its path from the seat until it leaves the console's box (the sparks fly from there). */
    private val mouth: FloatArray? = run {
        val c = console; val m = marks ?: return@run null; if (c == null) return@run null
        val d = m.from.let { f -> val l = sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]).coerceAtLeast(1e-4f); FloatArray(3) { f[it] / l } }
        val s0 = floatArrayOf(m.seat[12], m.seat[13], m.seat[14]); var k = 0f
        while (k < 400f && (0..2).all { abs(s0[it] + d[it] * k - c.center[it]) < c.half[it] }) k += 1f
        FloatArray(3) { s0[it] + d[it] * maxOf(0f, k - 2f) }
    }
    private val sparked = HashSet<Int>()
    private val cartHalfW = cart?.asset?.boundingBox?.halfExtent?.get(0) ?: 20f

    /** A point in a model's millimetres on the screen, through the camera's lens (layout pixels, y down). */
    private fun screen(mat: FloatArray, p: FloatArray): FloatArray {
        val v = FloatArray(4); android.opengl.Matrix.multiplyMV(v, 0, mat, 0, floatArrayOf(p[0], p[1], p[2], 1f), 0)
        val f = Stage.DIST / (Stage.DIST - v[2])
        return floatArrayOf(Stage.W / 2 + (v[0] - Stage.W / 2) * f, Stage.H - (Stage.H / 2 + (v[1] - Stage.H / 2) * f))
    }

    /**
     * Everything at time t of the start animation (coming back plays it backwards: `going` false only changes the sounds,
     * leaves out the sparks, and where the game flies to).
     */
    private fun place(t: Float, going: Boolean) {
        val c = console
        backdrop.shadows.clear()
        val from = if (going) home else homeAgain
        if (c == null) { cart?.placeMatrix(from); return }
        if (going) {
            if (t >= hit) { once(0, Sounds.CLICK); if (press == null) once(8, Sounds.CLUNK) }
            if (press != null && t >= hit2) once(5, Sounds.CLUNK)
            if (parts["power"] != null && t >= powerAt + .06f) once(6, Sounds.SWITCH)
            if (t >= ledAt) once(1, Sounds.CHIME)
            if (lidFirst && t >= openAt) once(7, Sounds.MOVE)
        }
        // arriving: a spring up from below, overshooting a little and settling; then floating gently
        val rs = spring(t, 6.5f, 8.5f)
        val turnK = if (back) spring(t - .05f, 6f, 9f) - spring(t - turnBack[0], 7f, 11f) else 0f
        val float = 4f * sin((t - .8f) * 2.3f) * smoother((t - .6f) / .5f)
        // the hit: a squash along the way the game went in, ringing down, and a tiny shake
        val hitK = .1f * bump(t - hit) + (if (press != null) .12f * bump(t - hit2) else 0f)
        fun shakeAt(h: Float) = if (h < 0f || t < h) 0f else exp(-14 * (t - h)) * sin(55 * (t - h))
        val shake = 3f * (shakeAt(hit) + if (press != null) shakeAt(hit2) else 0f)
        val fk = 1 - smoother(span(t, frameOut)); val fr = 1 + (fit[0] - 1) * fk; val fdy = fit[1] * fk
        val x = 667f + shake; val y = 372f + fdy + (1 - rs) * 640f + float
        val size = px0 * fr * (.9f + .1f * spring(t, 7f, 12f))
        c.place(x, y, 30f, yawOf(turnK) - .38f * (1 - rs), pitch, size)
        val cm = c.matrix.copyOf()
        if (hitK != 0f) {   // squashed against the far side from the hit (the floor it stands on), keeping its volume
            val sq = FloatArray(3) { 1 + hitK * .5f }; sq[ax] = 1 - hitK
            val piv = c.center.copyOf(); piv[ax] = c.center[ax] - Math.signum(hitDir[ax]) * c.half[ax]
            android.opengl.Matrix.translateM(cm, 0, piv[0], piv[1], piv[2]); android.opengl.Matrix.scaleM(cm, 0, sq[0], sq[1], sq[2])
            android.opengl.Matrix.translateM(cm, 0, -piv[0], -piv[1], -piv[2])
            c.placeMatrix(cm)
        }
        // the lid: the PlayStation's pops up as its button is pressed and claps shut over the disc; the PSP's door the
        // same; the DS springs open at the end
        val lidK = when {
            lidFirst -> spring(t - openAt - .08f, 7f, 13f) - (if (t < lidClose[0]) 0f else clamp(span(t, lidClose)).toDouble().pow(2.2).toFloat() - (if (t > lidClose[1]) .06f * bump(t - lidClose[1], 8f, 26f) else 0f))
            opensAfter -> spring(t - lidOpen[0], 6.5f, 10f)
            else -> 0f
        }
        c.lid(lidK, marks?.lid)
        parts["open"]?.let { c.move("open", pressK(t - openAt, 0f), it) }
        // the power: the switch snaps over (or the button is pressed), then the light, then a handheld's screen
        parts["power"]?.let { p -> c.move("power", if (p.button) pressK(t - powerAt, if (game.sys == "nes") .45f else 0f) else spring(t - powerAt, 9f, 16f), p) }
        c.power(clamp((t - ledAt) / .2f) * (1 + .5f * bump(t - ledAt - .05f, 6f, 9f)))
        c.picture(picture, sampler, if (hand) clamp(span(t, screenT)) else 0f)
        circuit?.follow(cm)
        // the black closes on the power light (or the screen)
        val target = (if (hand) marks?.screen else marks?.led) ?: marks?.led ?: marks?.screen
        target?.let { tp -> val q = screen(cm, tp); black.focus(q[0], q[1]) }
        backdrop.shadows.add(floatArrayOf(x, y - float + 200f * fr, 420f * fr * (1 - float / 60f), .8f * clamp(t / .5f)))
        // the sparks: where the game went in, from both edges of the slot, when it hits; a few from the power light
        val m = marks ?: return
        if (going) {
            sparks.time = t
            // each burst placed when it happens, where the console is then
            mouth?.let { mo ->
                fun burst(key: Int, t0: Float, n: Int, power: Float, seed: Int, up: Boolean) {
                    if (t < t0 || !sparked.add(key)) return
                    val l = sqrt(m.from[0] * m.from[0] + m.from[1] * m.from[1] + m.from[2] * m.from[2]).coerceAtLeast(1e-4f)
                    val m0 = screen(cm, mo); val sd = screen(cm, FloatArray(3) { mo[it] + m.from[it] / l * 20f })
                    val ang = if (up) (-PI / 2).toFloat() else atan2(sd[1] - m0[1], sd[0] - m0[0])
                    val wx = floatArrayOf(m.seat[0], m.seat[1], m.seat[2]); val hw = cartHalfW * .9f
                    sparks.add(Sparks.Burst(t0, m0, screen(cm, FloatArray(3) { mo[it] - wx[it] * hw }), screen(cm, FloatArray(3) { mo[it] + wx[it] * hw }), ang, n, power, seed))
                }
                if (press == null) burst(0, hit, 30, 1f, 7, false)
                else { burst(0, hit, 12, .6f, 3, false); burst(1, hit2, 30, 1f, 7, true) }
            }
            m.led?.let { led -> if (t >= ledAt && sparked.add(2)) sparks.add(Sparks.Burst(ledAt, screen(cm, led), null, null, (-PI / 2).toFloat(), 9, .35f, 11, soft = true)) }
        }
        // the game: wound up (a little further out) and slammed in, a small rebound; on the NES, slammed down too
        val u = span(t, slide)
        var path = when { u <= 0f -> 1f; u < .55f -> 1 + .14f * smoother(u / .55f); u < 1f -> 1.14f * (1 - ((u - .55f) / .45f).toDouble().pow(2.6).toFloat()); else -> 0f }
        if (press == null) path += .045f * rebound(t - hit)
        val pK = when {
            press == null -> 1f
            t < hit -> 0f
            t < pressT[0] -> -.25f * smoother((t - hit - .02f) / .08f)
            t < pressT[1] -> -.25f + 1.25f * span(t, pressT).toDouble().pow(2.4).toFloat()
            else -> 1 - .08f * rebound(t - hit2)
        }
        // the NES's door swings in as the cartridge passes, and springs shut once it's pressed down
        parts["door"]?.let { d -> c.move("door", if (t < hit2) spring((u - .55f) / 1.6f, 8f, 14f) else maxOf(-.08f, 1 - spring(t - hit2 - .04f, 7f, 15f)), d) }
        val ct = cart ?: return
        val at = along(cm, path, pK, FloatArray(16))
        // lifted from where it was (a little dip first), carried along a high curve leaning into the way it moves, a
        // breath floating over the slot
        fun lifted(tt: Float): FloatArray { val l = spring(tt - .1f, 8f, 12f); val dip = if (tt < .12f) sin(PI.toFloat() * clamp(tt / .12f)) else 0f
            return from.copyOf().also { it[13] += 30f * l - 9f * dip; it[14] += 70f * l } }
        val fu = span(t, flight)
        when {
            t < flight[0] -> ct.placeMatrix(lifted(t))
            fu < 1f -> {
                val l0 = lifted(t); val start = along(cm, 1f, 0f, FloatArray(16))
                val dir = FloatArray(3) { i -> cm[i] * m.from[0] + cm[4 + i] * m.from[1] + cm[8 + i] * m.from[2] }
                val dl = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]).coerceAtLeast(1e-4f)
                fun bez(e: Float, i: Int): Float {
                    val a = l0[12 + i]; val d = start[12 + i]
                    val b1 = a + floatArrayOf(30f, 170f, 170f)[i]; val b2 = d + dir[i] / dl * 150f + (if (i == 2) 70f else 0f); val ie = 1 - e
                    return ie * ie * ie * a + 3 * ie * ie * e * b1 + 3 * ie * e * e * b2 + e * e * e * d
                }
                val e = smoother(fu); val e2 = smoother(minOf(1f, fu + .02f))
                val pose = Poses.blend(l0, start, smoother(minOf(1f, fu / .75f)))
                val p = FloatArray(3) { bez(e, it) }
                val vx = (bez(e2, 0) - p[0]) / (.02f * (flight[1] - flight[0]))
                val lean = (-vx * .0007f).coerceIn(-.4f, .4f)   // leaning into the way it moves, like something carried
                for (i in 0..2) pose[12 + i] = p[i]
                val r = FloatArray(16); android.opengl.Matrix.setIdentityM(r, 0)
                android.opengl.Matrix.translateM(r, 0, p[0], p[1], p[2]); android.opengl.Matrix.rotateM(r, 0, Math.toDegrees(lean.toDouble()).toFloat(), 0f, 0f, 1f)
                android.opengl.Matrix.translateM(r, 0, -p[0], -p[1], -p[2])
                ct.placeMatrix(FloatArray(16).also { android.opengl.Matrix.multiplyMM(it, 0, r, 0, pose, 0) })
            }
            else -> {
                if (t < slide[0]) at[13] += 7f * sin(PI.toFloat() * clamp((t - flight[1]) / (slide[0] - flight[1])))   // a breath, floating
                ct.placeMatrix(at)
            }
        }
    }

    private fun finish() {
        phase = Phase.DONE
        cart?.placeMatrix(homeAgain)
        console?.destroy(); circuit?.destroy(); picture?.let { stage.engine.destroyTexture(it) }
        board.removeView(black); board.removeView(sparks); backdrop.shadows.clear()
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
 * The sparks when a game goes in (and a few from the power light): little square bits flying out from both edges of
 * the slot and falling, a few four-pointed twinkles, a ring spreading over the console and a soft glow, in the
 * console's color with white cores. Drawn over the 3D. Each burst is worked out from its start time alone (seeded), so
 * slow frames can't change how it looks; mirrored in tools/lighting-rig's launch scene.
 */
class Sparks(ctx: Context, accent: Int) : View(ctx) {
    class Burst(val t0: Float, val at: FloatArray, val e1: FloatArray?, val e2: FloatArray?, val ang: Float, val n: Int, val power: Float, val seed: Int, val soft: Boolean = false)
    private val bursts = ArrayList<Burst>()
    var time = 0f; set(v) { field = v; invalidate() }
    fun add(b: Burst) { bursts += b }
    fun clear() { bursts.clear(); invalidate() }
    // the console's color made richer (as the glow does), so it shows on a white console too
    private val acc = FloatArray(3).let { hsv -> Color.colorToHSV(accent, hsv); hsv[1] = minOf(1f, hsv[1] * 1.45f + .1f); hsv[2] = minOf(1f, hsv[2] * .95f); Color.HSVToColor(hsv) }
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()

    override fun onDraw(c: android.graphics.Canvas) { for (b in bursts) draw(c, b) }

    private fun draw(c: android.graphics.Canvas, b: Burst) {
        val u = time - b.t0; if (u < 0f || u > 1.2f) return
        var seed = (b.seed * 9301L + 49297L)
        fun rnd(): Float { seed = (seed * 16807L) % 2147483647L; return seed / 2147483647f }
        fun out3(v: Float) = v.coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) * (1 - it) }
        val x = b.at[0]; val y = b.at[1]
        if (!b.soft) {
            val rk = (u / .42f).coerceIn(0f, 1f)
            if (rk < 1f) {   // the ring
                p.style = android.graphics.Paint.Style.STROKE; p.color = acc; p.alpha = (255 * .55f * (1 - rk)).toInt(); p.strokeWidth = 4 * (1 - rk) + .5f
                val r = 18 + 160 * out3(rk) * b.power; c.drawOval(x - r, y - r * .42f, x + r, y + r * .42f, p); p.style = android.graphics.Paint.Style.FILL
            }
            val gk = (u / .28f).coerceIn(0f, 1f)
            if (gk < 1f) {   // a soft glow
                p.shader = android.graphics.RadialGradient(x, y, 70 * b.power, acc, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
                p.alpha = (255 * .45f * (1 - gk)).toInt(); c.drawRect(x - 80, y - 80, x + 80, y + 80, p); p.shader = null
            }
        }
        for (i in 0 until b.n) {
            // from either edge of the slot, out to that side and along the way the game came from
            val o = (if (i % 2 == 1) b.e2 else b.e1) ?: b.at
            val a = if (b.soft) b.ang + (rnd() - .5f) * 2.4f else {
                val ex = o[0] - x; val ey = o[1] - y; val el = kotlin.math.hypot(ex, ey).coerceAtLeast(1e-3f); val k2 = .35f + .9f * rnd()
                kotlin.math.atan2(ey / el + k2 * kotlin.math.sin(b.ang), ex / el + k2 * kotlin.math.cos(b.ang)) + (rnd() - .5f) * .5f
            }
            val sp = (if (b.soft) 40 + 70 * rnd() else 480 + 640 * rnd()) * b.power; val life = (if (b.soft) .7f else .5f) + .45f * rnd()
            val size = Math.round(4 + 6 * rnd()); val white = rnd() < .3f; val star = rnd() < .2f
            if (u > life) continue
            val k = u / life; val drag = if (b.soft) 1f else 1 / (1 + 2.2f * u)
            val px = o[0] + kotlin.math.cos(a) * sp * u * drag; val py = o[1] + kotlin.math.sin(a) * sp * u * drag + (if (b.soft) -30 * u else 900 * u * u)
            val al = (1 - k) * (1 - k) * .95f + .05f
            val col = if (white) Color.WHITE else acc
            if (star) {   // a four-pointed twinkle
                val r = (size + 5) * (1 - k * .5f) * (.7f + .3f * kotlin.math.sin(u * 40 + i)); p.color = col; p.alpha = (255 * al).toInt()
                path.reset(); path.moveTo(px, py - r); path.lineTo(px + r * .22f, py - r * .22f); path.lineTo(px + r, py); path.lineTo(px + r * .22f, py + r * .22f)
                path.lineTo(px, py + r); path.lineTo(px - r * .22f, py + r * .22f); path.lineTo(px - r, py); path.lineTo(px - r * .22f, py - r * .22f); path.close(); c.drawPath(path, p)
            } else {   // a little square, crisp, with a soft square halo and a white core
                val s2 = maxOf(2, Math.round(size * (1 - k * .6f))).toFloat(); val rx = Math.round(px).toFloat(); val ry = Math.round(py).toFloat()
                p.color = acc; p.alpha = (255 * al * .35f).toInt(); c.drawRect(rx - s2, ry - s2, rx + s2, ry + s2, p)
                p.color = col; p.alpha = (255 * al).toInt(); c.drawRect(rx - s2 / 2, ry - s2 / 2, rx + s2 / 2, ry + s2 / 2, p)
                if (!white && s2 > 3) { p.color = Color.WHITE; p.alpha = (255 * al * .8f).toInt(); c.drawRect(rx - s2 / 4, ry - s2 / 4, rx + s2 / 4, ry + s2 / 4, p) }
            }
        }
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

