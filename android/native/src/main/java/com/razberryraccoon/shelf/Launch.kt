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
 * it was to where it waits, clear of where the console will stand (from the page's open box: first, while the box slides
 * away behind it, `lead`), carried along a curve that passes in front of everything, and lines up over its slot;
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
             private val homeAgain: FloatArray = home,
             /**
              * Seconds before the console comes (the page's open box leaving): the cartridge lifts out of its tray toward
              * you and moves to where it waits while the box slides away behind it; only then does the console rise.
              */
             private val lead: Float = 0f) {
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
    private var startedAt = 0L   // when the game opened (play time)

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
    /** The hit's shockwave rings (going in only), in the console's color. */
    private val waves = Shockwave(ctx, Theme.accentOf(game.sys))

    init {
        board.put(waves, 0, 0, 1334, 750)
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
        if (phase == Phase.IN && t < lead + darkT[0]) { t = lead + darkT[0]; quiet() }
        else if (phase == Phase.OUT) t = maxOf(t, (backFrom + lead) / backSpeed - .12f)
    }

    /** Back from the game: the return animation, from black. */
    fun back() {
        if (phase != Phase.PLAYING) return
        // how long it was played: from the game starting to coming back (not a moment's look, nor a handheld left on all night)
        if (startedAt > 0L) { val s = (System.currentTimeMillis() - startedAt) / 1000; if (s in 20L..12 * 3600L) games.addPlayTime(game, s); startedAt = 0L }
        phase = Phase.OUT; t = 0f; sounded.clear(); quiet()
        black.kind = Curtain.FADE   // coming back: simply out of black (the transition is only for going in)
    }

    fun tick(dt: Float) {
        when (phase) {
            Phase.IN -> {
                t += dt
                place(t - lead, going = true)   // the timeline below counts from the console's arrival; before it, the lead
                black.k = clamp(span(t - lead, darkT))
                if (t - lead >= darkT[1]) {
                    phase = Phase.PLAYING; black.k = 1f; quiet()
                    error = host.start(this)
                    startedAt = if (error.isEmpty()) System.currentTimeMillis() else 0L
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
                place(maxOf(-lead, u), going = false)
                if (u <= -lead) finish()
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

    /**
     * Where the cartridge waits for the console (the pose it's lifted to): already the size it will be in the console,
     * turned as it was, toward you, and beside where the console will stand, so the console rising never passes
     * through it (it rose into the page's cartridge before). Measured once, through the camera's lens, from every
     * pose the console takes on its way up and while it takes its game; the spot nearest where the cartridge was that
     * keeps clear of all of them: to the left, above, or to the right.
     */
    private val wait: FloatArray? = run {
        val c = console ?: return@run null; val ct = cart ?: return@run null; if (marks == null) return@run null
        var x0 = 1e9f; var x1 = -1e9f; var y0 = 1e9f; var y1 = -1e9f
        fun rect(mat: FloatArray) { for (n in 0 until 8) {
            val q = screen(mat, FloatArray(3) { i -> c.center[i] + if ((n shr i) and 1 == 1) c.half[i] else -c.half[i] })
            x0 = minOf(x0, q[0]); x1 = maxOf(x1, q[0]); y0 = minOf(y0, q[1]); y1 = maxOf(y1, q[1]) } }
        for ((turn, extra, size) in listOf(Triple(0f, 0f, 1f), Triple(if (back) 1f else 0f, 0f, 1f), Triple(0f, -.38f, 1f), Triple(0f, -.38f, .9f), Triple(.5f, 0f, 1f)))
            for (lift in listOf(0f, -60f))   // and where its spring carries it, a little past its place, as it arrives
                { c.place(667f, 372f + fit[1] + lift, 30f, yawOf(if (back) turn else 0f) + extra, pitch, px0 * fit[0] * size); rect(c.matrix) }
        c.place(667f, 372f + fit[1], 30f, yawOf(if (back) 1f else 0f), pitch, px0 * fit[0])
        val a = along(c.matrix, 1f, 0f, FloatArray(16)); c.hide()
        val s = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val hb = ct.asset.boundingBox.halfExtent; val r = s * maxOf(hb[0], hb[1]) * 1.05f   // its reach on the screen, any way it's turned
        val z = home[14] + 80f
        val f = Stage.DIST / (Stage.DIST - z)
        val hx = Stage.W / 2 + (home[12] - Stage.W / 2) * f; val hy = Stage.H - (Stage.H / 2 + (home[13] - Stage.H / 2) * f)
        val gap = 36f
        fun clampY(y: Float) = y.coerceIn(r + 24f, Stage.H - r - 24f)
        fun clampX(x: Float) = x.coerceIn(r + 24f, Stage.W - r - 24f)
        val spots = listOf(floatArrayOf(x0 - gap - r, clampY(hy)), floatArrayOf(clampX(hx), y0 - gap - r), floatArrayOf(x1 + gap + r, clampY(hy)))
            .filter { (x, y) -> x >= r + 20f && x <= Stage.W - r - 20f && y >= r + 20f && y <= Stage.H - r - 20f }
        val (sx, sy) = spots.minByOrNull { (x, y) -> (x - hx) * (x - hx) + (y - hy) * (y - hy) }?.let { it[0] to it[1] } ?: ((r + 24f) to clampY(hy))
        // the pose: the cartridge's own turn, at that size, there (from the screen back into the world at its depth)
        val hs = sqrt(home[0] * home[0] + home[1] * home[1] + home[2] * home[2]).coerceAtLeast(1e-6f)
        FloatArray(16).also { m -> for (i in 0..11) m[i] = home[i] / hs * s
            m[12] = Stage.W / 2 + (sx - Stage.W / 2) / f; m[13] = Stage.H / 2 + ((Stage.H - sy) - Stage.H / 2) / f; m[14] = z; m[15] = 1f }
    }

    /** The axis (the console's own) a hit squashes along: the way the game went in last. */
    private val hitDir = press ?: marks?.from ?: floatArrayOf(0f, 1f, 0f)
    private val ax = (0..2).maxByOrNull { abs(hitDir[it]) } ?: 1
    /** Where the game goes in: along its path from the seat until it leaves the console's box (the shockwave spreads from there). */
    private val mouth: FloatArray? = run {
        val c = console; val m = marks ?: return@run null; if (c == null) return@run null
        val d = m.from.let { f -> val l = sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]).coerceAtLeast(1e-4f); FloatArray(3) { f[it] / l } }
        val s0 = floatArrayOf(m.seat[12], m.seat[13], m.seat[14]); var k = 0f
        while (k < 400f && (0..2).all { abs(s0[it] + d[it] * k - c.center[it]) < c.half[it] }) k += 1f
        FloatArray(3) { s0[it] + d[it] * (k + 2f) }   // just outside, so the part of a ring inside the console can be left out
    }
    private var ringAt: FloatArray? = null
    private val cartHalfW = cart?.asset?.boundingBox?.halfExtent?.get(0) ?: 20f

    /** A point in a model's millimetres on the screen, through the camera's lens (layout pixels, y down). */
    private fun screen(mat: FloatArray, p: FloatArray): FloatArray {
        val v = FloatArray(4); android.opengl.Matrix.multiplyMV(v, 0, mat, 0, floatArrayOf(p[0], p[1], p[2], 1f), 0)
        val f = Stage.DIST / (Stage.DIST - v[2])
        return floatArrayOf(Stage.W / 2 + (v[0] - Stage.W / 2) * f, Stage.H - (Stage.H / 2 + (v[1] - Stage.H / 2) * f))
    }

    /**
     * Everything at time t of the start animation (coming back plays it backwards: `going` false only changes the sounds,
     * leaves out the hit's rings, and where the game flies to).
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
        // the hit: thin rings spread from the slot, the part inside the console left out. Level (a ripple over a home
        // console's top; on the NES they burst out of the front, half rings) when the console's top faces you; on an
        // upright handheld, in the plane of its face (an arc over its top, never a halo seen edge on)
        val m = marks ?: return
        if (going && phase == Phase.IN) {
            val gk = if (press != null) hit2 else hit
            // the rings stay where they were made: the console's placement at the first hit (a handheld turning round
            // afterwards doesn't carry them with it)
            if (t >= hit && ringAt == null) ringAt = cm.copyOf()
            val rm = ringAt
            if (rm != null) mouth?.let { mo ->
                val rings = ArrayList<FloatArray>(); val looks = ArrayList<FloatArray>()
                fun facing(i: Int) = abs(rm[4 * i + 2]) / sqrt(rm[4 * i] * rm[4 * i] + rm[4 * i + 1] * rm[4 * i + 1] + rm[4 * i + 2] * rm[4 * i + 2]).coerceAtLeast(1e-6f)
                val n = if (facing(1) > .3f) 1 else 2   // the plane's normal: the console's up, or its face
                val a1 = (n + 1) % 3; val a2 = (n + 2) % 3; val reach = 1.5f * maxOf(c.half[a1], c.half[a2])
                for ((h, delay, strength) in listOf(Triple(gk, 0f, 1f), Triple(gk, .08f, .5f), Triple(if (press != null) hit else -1f, 0f, .45f))) {
                    if (h < 0f) continue
                    val u = t - h - delay; if (u < 0f || u > .55f) continue
                    val k = u / .55f; val e = 1 - (1 - k) * (1 - k) * (1 - k)
                    val r = 6f + reach * e * (if (strength < .6f && delay == 0f) .55f else 1f)
                    val pts = FloatArray(2 * 49)
                    for (i in 0..48) { val th = (2 * PI * i / 48).toFloat(); val p = mo.copyOf(); p[a1] += r * cos(th); p[a2] += r * sin(th)
                        // inside the console: a gap, the console hides it
                        if ((0..2).all { abs(p[it] - c.center[it]) < c.half[it] - .5f }) { pts[2 * i] = Float.NaN; pts[2 * i + 1] = Float.NaN; continue }
                        val q = screen(rm, p); pts[2 * i] = q[0]; pts[2 * i + 1] = q[1] }
                    rings += pts; looks += floatArrayOf(strength * (1 - k) * (1 - k), 3.2f * (1 - k) + .6f)
                }
                waves.set(rings, looks)
            }
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
        // lifted from where it was (a little dip first) to where it waits for the console, floating there; then carried
        // along a high curve leaning into the way it moves, a breath floating over the slot
        val liftEnd = lead + flight[0]
        fun lifted(tm: Float): FloatArray {
            val ta = tm + lead; val dip = if (ta < .12f) sin(PI.toFloat() * clamp(ta / .12f)) else 0f
            val w = wait ?: return from.copyOf().also { it[13] += 30f * spring(ta - .1f, 8f, 12f) - 9f * dip; it[14] += 70f * spring(ta - .1f, 8f, 12f) }
            val k = smoother(ta / (liftEnd * .9f))
            return Poses.blend(from, w, k).also { it[13] += -9f * dip + 5f * sin(ta * 3.1f) * k }
        }
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
        board.removeView(black); board.removeView(waves); backdrop.shadows.clear()
        if (error.isNotEmpty()) Sounds.play(Sounds.BUMP)
        host.done(this)
    }
    /** The hit's rings put away (the game starting, skipping, coming back): nothing of them stays on the screen. */
    private fun quiet() {
        waves.set(emptyList(), emptyList())
    }

    /** What went wrong starting it, if anything (shown once it's back). */
    val problem get() = error

    companion object {
        private val SCREENS = setOf("gb", "gbc", "gba", "ds", "psp", "android"); private val UPRIGHT = setOf("gb", "gbc", "gba", "psp", "android")
        private val ZERO = FloatArray(3)
    }
}

/**
 * The shockwave when a game hits home: thin rings in the console's color (richer, as its glow is), each a closed line
 * of points Launch has already put through the camera, so they lie in the console's own plane; a soft wider line under
 * each for a little bloom. Nothing else: no particles (the user, after 0.26: sparks looked "goofy and princess-y").
 */
class Shockwave(ctx: Context, accent: Int) : View(ctx) {
    private var rings: List<FloatArray> = emptyList(); private var looks: List<FloatArray> = emptyList()
    private val acc = FloatArray(3).let { hsv -> Color.colorToHSV(accent, hsv); hsv[1] = minOf(1f, hsv[1] * 1.45f + .1f); Color.HSVToColor(hsv) }
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeJoin = android.graphics.Paint.Join.ROUND }
    private val path = android.graphics.Path()

    /** The rings to draw: their points (x, y pairs) and each one's (strength, width). */
    fun set(r: List<FloatArray>, l: List<FloatArray>) { if (r.isEmpty() && rings.isEmpty()) return; rings = r; looks = l; invalidate() }

    override fun onDraw(c: android.graphics.Canvas) {
        for ((i, pts) in rings.withIndex()) {
            val (a, w) = looks[i].let { it[0] to it[1] }
            // a closed line, broken where Launch marked a gap (NaN: hidden inside the console)
            path.reset(); var pen = false; var whole = true; var j = 0
            while (j < pts.size) { if (pts[j].isNaN()) { pen = false; whole = false } else if (pen) path.lineTo(pts[j], pts[j + 1]) else { path.moveTo(pts[j], pts[j + 1]); pen = true }; j += 2 }
            if (whole) path.close()
            p.color = acc; p.alpha = (255 * a * .25f).toInt().coerceIn(0, 255); p.strokeWidth = w * 4f; c.drawPath(path, p)   // the soft bloom
            p.alpha = (255 * a * .85f).toInt().coerceIn(0, 255); p.strokeWidth = w; c.drawPath(path, p)
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

