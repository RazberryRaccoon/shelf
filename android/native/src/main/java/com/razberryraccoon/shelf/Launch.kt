package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Color
import android.view.View
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Starting a game, and coming back from it. Starting (about 2.5 seconds): the game's console rises into view in its
 * colors from the Museum; the cartridge flies from where it was (Home or the open page), lines up with the slot and
 * slides in (a Game Boy turns round to take it in its back, a DS or PSP too); it seats with a click, the power light
 * comes on with a soft chime, a handheld's screen glows with the game's title screen, and the view eases in on the power
 * light or the screen, and the screen closes to black by the transition chosen in Settings (an iris closing on the
 * power light or screen, a wipe, blinds, a clock sweep… see Curtain). Then the game starts. Coming back plays it the
 * other way: the black opens the same way, the screen dims and the light goes off, the cartridge slides out and flies
 * home, and the console sinks away.
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
    private val hand = game.sys in SCREENS        // it has a screen to show the title on (and eases in on it)
    private val upright = game.sys in UPRIGHT     // held upright, seen nearly face on (the DS lies open, like a home console)
    private val opensAfter = (marks?.lid?.rest ?: 0f) > .5f   // its lid rests open (the DS): closed for the card, then open
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
    private fun once(k: Int, sound: Int) { if (sounded.add(k)) Sounds.play(sound) }

    /** Any button: straight to the fade (starting) or to the end (coming back); in the dark, back to Shelf. */
    fun input(a: String) {
        if (phase == Phase.PLAYING) return back()   // still here in the dark (the game never opened): come back
        if (phase == Phase.IN && t < 2.05f) t = 2.05f
        else if (phase == Phase.OUT && t < 1.7f) t = maxOf(t, 1.7f)
    }

    /** Back from the game: the return animation, from black. */
    fun back() {
        if (phase != Phase.PLAYING) return
        phase = Phase.OUT; t = 0f; sounded.clear()
    }

    fun tick(dt: Float) {
        when (phase) {
            Phase.IN -> {
                t += dt
                place(intoK = 1f)
                if (t >= 2.5f) {
                    phase = Phase.PLAYING
                    error = host.start(this)
                    if (error.isNotEmpty()) back()   // it couldn't start: come straight back and say why
                }
            }
            Phase.PLAYING -> place(intoK = 1f)
            Phase.OUT -> {
                t += dt
                place(intoK = 0f)
                if (t >= 1.75f) finish()
            }
            Phase.DONE -> {}
        }
    }

    // ---------- where everything is ----------

    private val tmp = FloatArray(16); private val tmp2 = FloatArray(16)

    /**
     * Everything at time t: going in (intoK 1) or coming out (0). The beats, going in: the console rises (0–.55 s),
     * turns round if it takes its game in the back (.2–.7), the cartridge flies to the start of its slot's path
     * (.15–1.0) and slides in (1.0–1.3, click), the console turns back (1.35–1.85), the power light comes on (1.45, chime),
     * a handheld's screen glows (1.6–2.0), and the view eases in (1.95–2.5) as it fades to black (2.05–2.5).
     */
    private fun place(intoK: Float) {
        val c = console
        val going = intoK > .5f
        // how far through each beat, the same measures going in and coming out
        val rise: Float; val turnK: Float; val fly: Float; val slide: Float; val powerK: Float; val screenK: Float; val inK: Float; val dark: Float; val lidK: Float
        if (going) {
            rise = ease(t / .55f)
            turnK = ease((t - .2f) / .5f) - ease((t - 1.35f) / .5f)
            fly = ease((t - .15f) / .85f)
            slide = Backdrop.ease(clamp((t - 1f) / .3f))
            if (t >= 1.3f) once(0, Sounds.CLICK)
            powerK = clamp((t - 1.45f) / .3f); if (t >= 1.45f) once(1, Sounds.CHIME)
            // a lid that rests open (the DS) starts closed and opens once the card's in; one that rests closed (the
            // PlayStation's lid, the PSP's door) opens for the game and closes after it
            lidK = if (opensAfter) ease((t - 1.3f) / .45f) else ease((t - .35f) / .4f) - ease((t - 1.3f) / .3f)
            screenK = if (hand) clamp((t - 1.6f) / .4f) else 0f
            inK = 0f   // no close-up any more: the transition ends it
            dark = if (phase == Phase.PLAYING) 1f else clamp((t - 1.95f) / .55f)
        } else {
            // coming back: out of black and back out of the close-up, the screen and light go off, the console turns
            // round if it took its game in the back, the cartridge slides out and flies home, the console sinks away
            dark = 1 - clamp(t / .45f)
            inK = 0f
            screenK = if (hand) 1 - clamp((t - .3f) / .3f) else 0f
            powerK = 1 - clamp((t - .45f) / .25f); if (t >= .45f) once(2, Sounds.POWER_OFF)
            turnK = ease((t - .5f) / .3f)
            slide = 1 - Backdrop.ease(clamp((t - .75f) / .25f))
            fly = 1 - ease((t - 1f) / .6f)
            lidK = if (opensAfter) 1 - ease((t - .5f) / .3f) else ease((t - .5f) / .25f) - ease((t - 1.02f) / .3f)
            rise = 1 - ease((t - 1.05f) / .5f)
        }
        black.k = dark
        backdrop.shadows.clear()
        val from = if (going) home else homeAgain
        if (c == null) { cart?.placeMatrix(from); return }
        // the console: centred a little left of middle, seen a little from above (a handheld nearly face on)
        val diag = sqrt(c.half[0] * c.half[0] + c.half[1] * c.half[1] + c.half[2] * c.half[2])
        val px0 = 2 * 275f * maxOf(c.half[0], c.half[1]) / diag
        val yaw = (if (upright) -.32f else -.5f) + (marks?.turn ?: 0f) * turnK
        val pitch = if (upright) .1f else .42f
        var x = 667f; var y = 372f + (1 - rise) * 760f
        val zoom = 1 + inK * (if (hand) 1.5f else 1.1f)
        c.place(x, y, 30f, yaw, pitch, px0 * zoom)
        // easing in: the power light (or the screen) drifts toward the middle of the view as it grows
        val target = (if (hand) marks?.screen else marks?.led) ?: marks?.led ?: marks?.screen
        if (target != null && inK > 0f) {
            val p = FloatArray(4); android.opengl.Matrix.multiplyMV(p, 0, c.matrix, 0, floatArrayOf(target[0], target[1], target[2], 1f), 0)
            x += (667f - p[0]) * inK; y += (375f - (Stage.H - p[1])) * inK
            c.place(x, y, 30f, yaw, pitch, px0 * zoom)
        }
        c.power(powerK)
        c.lid(lidK, marks?.lid)
        c.picture(picture, sampler, screenK)
        circuit?.follow(c.matrix)
        // the iris closes on the power light (or the screen)
        target?.let { tp -> val q = FloatArray(4); android.opengl.Matrix.multiplyMV(q, 0, c.matrix, 0, floatArrayOf(tp[0], tp[1], tp[2], 1f), 0); black.focus(q[0], Stage.H - q[1]) }
        backdrop.shadows.add(floatArrayOf(x, y + 200f * zoom, 420f * zoom, .8f * rise * (1 - inK)))
        // the cartridge: home → the start of the slot's path (flying, arcing up a little), then along it into the seat
        val ct = cart ?: return
        val m = marks
        if (m == null) { ct.placeMatrix(from); return }
        val path = 1 - slide   // 1 at the start of the path, 0 seated
        android.opengl.Matrix.setIdentityM(tmp, 0); android.opengl.Matrix.translateM(tmp, 0, m.from[0] * path, m.from[1] * path, m.from[2] * path)
        android.opengl.Matrix.multiplyMM(tmp2, 0, tmp, 0, m.seat, 0)
        val along = FloatArray(16).also { android.opengl.Matrix.multiplyMM(it, 0, c.matrix, 0, tmp2, 0) }
        if (fly >= 1f) ct.placeMatrix(along)
        else {
            val f = Poses.blend(from, along, fly)
            f[13] += sin(PI.toFloat() * fly) * 70f   // arcing up on the way
            ct.placeMatrix(f)
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

    companion object { private val SCREENS = setOf("gb", "gbc", "gba", "ds", "psp", "android"); private val UPRIGHT = setOf("gb", "gbc", "gba", "psp", "android") }
}

/**
 * The way the screen goes to black before a game starts, and comes out of it after (Settings › Starting a game). Drawn
 * over everything, the letterbox included; `k` is how much is covered, 0 to 1. The ideas come from film and video
 * transitions: an iris, wipes, blinds, a clock wipe, a dissolve of dots, a plain fade.
 */
class Curtain(ctx: Context, private val kind: Int) : View(ctx) {
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

