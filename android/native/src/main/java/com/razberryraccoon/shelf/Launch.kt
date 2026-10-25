package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Color
import android.view.View
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Starting a game, and coming back from it. Starting (about 2.5 seconds): the game's console rises into view in its
 * colors from the Museum; the cartridge flies from where it was (Home or the open page), lines up with the slot and
 * slides in (a Game Boy turns round to take it in its back, a DS or PSP too); it seats with a click, the power light
 * comes on with a soft chime, a handheld's screen glows with the game's title screen, and the view eases in on the power
 * light or the screen as everything fades to black. Then the game starts. Coming back plays it the other way: out of
 * black, the screen dims and the light goes off, the cartridge slides out and flies home, and the console sinks away.
 * Any button skips to the end of either.
 */
class Launch(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
             private val carts: Carts, private val consoles: Consoles, private val games: Games, private val meta: Meta,
             val game: Game, private val cart: Cart?, private val home: FloatArray, private val host: Host) {
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
    private val black = View(ctx).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
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
            inK = ease((t - 1.95f) / .55f)
            dark = if (phase == Phase.PLAYING) 1f else clamp((t - 2.05f) / .45f)
        } else {
            // coming back: out of black and back out of the close-up, the screen and light go off, the console turns
            // round if it took its game in the back, the cartridge slides out and flies home, the console sinks away
            dark = 1 - clamp(t / .4f)
            inK = 1 - ease(t / .5f)
            screenK = if (hand) 1 - clamp((t - .3f) / .3f) else 0f
            powerK = 1 - clamp((t - .45f) / .25f); if (t >= .45f) once(2, Sounds.POWER_OFF)
            turnK = ease((t - .5f) / .3f)
            slide = 1 - Backdrop.ease(clamp((t - .75f) / .25f))
            fly = 1 - ease((t - 1f) / .6f)
            lidK = if (opensAfter) 1 - ease((t - .5f) / .3f) else ease((t - .5f) / .25f) - ease((t - 1.02f) / .3f)
            rise = 1 - ease((t - 1.05f) / .5f)
        }
        black.alpha = dark
        backdrop.shadows.clear()
        if (c == null) { cart?.placeMatrix(home); return }
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
        backdrop.shadows.add(floatArrayOf(x, y + 200f * zoom, 420f * zoom, .8f * rise * (1 - inK)))
        // the cartridge: home → the start of the slot's path (flying, arcing up a little), then along it into the seat
        val ct = cart ?: return
        val m = marks
        if (m == null) { ct.placeMatrix(home); return }
        val path = 1 - slide   // 1 at the start of the path, 0 seated
        android.opengl.Matrix.setIdentityM(tmp, 0); android.opengl.Matrix.translateM(tmp, 0, m.from[0] * path, m.from[1] * path, m.from[2] * path)
        android.opengl.Matrix.multiplyMM(tmp2, 0, tmp, 0, m.seat, 0)
        val along = FloatArray(16).also { android.opengl.Matrix.multiplyMM(it, 0, c.matrix, 0, tmp2, 0) }
        if (fly >= 1f) ct.placeMatrix(along)
        else {
            val f = blend(home, along, fly)
            f[13] += sin(PI.toFloat() * fly) * 70f   // arcing up on the way
            ct.placeMatrix(f)
        }
    }

    private fun finish() {
        phase = Phase.DONE
        cart?.placeMatrix(home)
        console?.destroy(); circuit?.destroy(); picture?.let { stage.engine.destroyTexture(it) }
        board.removeView(black); backdrop.shadows.clear()
        if (error.isNotEmpty()) Sounds.play(Sounds.BUMP)
        host.done(this)
    }
    /** What went wrong starting it, if anything (shown once it's back). */
    val problem get() = error

    // ---------- turning one transform into another smoothly ----------

    /**
     * Between two placements: position and size in a straight line, the turn along the shortest way round (blending the
     * matrices themselves would squash the cartridge half way).
     */
    private fun blend(a: FloatArray, b: FloatArray, k: Float): FloatArray {
        val sa = scaleOf(a); val sb = scaleOf(b)
        val qa = quat(a, sa); val qb = quat(b, sb)
        val q = slerp(qa, qb, k); val s = sa + (sb - sa) * k
        val out = rotation(q)
        for (i in 0..2) for (j in 0..2) out[i * 4 + j] *= s
        for (i in 12..14) out[i] = a[i] + (b[i] - a[i]) * k
        out[15] = 1f
        return out
    }
    private fun scaleOf(m: FloatArray) = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]).coerceAtLeast(1e-6f)
    /** A rotation (column-major, scale removed) as a quaternion (x, y, z, w). */
    private fun quat(m: FloatArray, s: Float): FloatArray {
        val m00 = m[0] / s; val m11 = m[5] / s; val m22 = m[10] / s
        val m21 = m[6] / s; val m12 = m[9] / s; val m02 = m[8] / s; val m20 = m[2] / s; val m10 = m[1] / s; val m01 = m[4] / s
        val tr = m00 + m11 + m22
        return when {
            tr > 0 -> { val r = sqrt(tr + 1f) * 2; floatArrayOf((m21 - m12) / r, (m02 - m20) / r, (m10 - m01) / r, .25f * r) }
            m00 > m11 && m00 > m22 -> { val r = sqrt(1f + m00 - m11 - m22) * 2; floatArrayOf(.25f * r, (m01 + m10) / r, (m02 + m20) / r, (m21 - m12) / r) }
            m11 > m22 -> { val r = sqrt(1f + m11 - m00 - m22) * 2; floatArrayOf((m01 + m10) / r, .25f * r, (m12 + m21) / r, (m02 - m20) / r) }
            else -> { val r = sqrt(1f + m22 - m00 - m11) * 2; floatArrayOf((m02 + m20) / r, (m12 + m21) / r, .25f * r, (m10 - m01) / r) }
        }
    }
    private fun slerp(a: FloatArray, b0: FloatArray, k: Float): FloatArray {
        var b = b0; var d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        if (d < 0) { b = FloatArray(4) { -b0[it] }; d = -d }
        if (d > .9995f) return FloatArray(4) { a[it] + (b[it] - a[it]) * k }.let { q -> val l = sqrt(q.sumOf { (it * it).toDouble() }).toFloat(); FloatArray(4) { q[it] / l } }
        val th = acos(d.coerceIn(-1f, 1f)); val s = sin(th)
        val wa = sin((1 - k) * th) / s; val wb = sin(k * th) / s
        return FloatArray(4) { a[it] * wa + b[it] * wb }
    }
    /** A quaternion as a column-major rotation matrix. */
    private fun rotation(q: FloatArray): FloatArray {
        val (x, y, z, w) = q
        return floatArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
            0f, 0f, 0f, 1f)
    }

    companion object { private val SCREENS = setOf("gb", "gbc", "gba", "ds", "psp", "android"); private val UPRIGHT = setOf("gb", "gbc", "gba", "psp", "android") }
}
