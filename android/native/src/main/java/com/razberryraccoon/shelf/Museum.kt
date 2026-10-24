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
 * On the right, the console's story, and its default shell: what its standard cartridges wear on Home (special
 * editions keep their real colors, and games you've customized keep yours).
 */
class Museum(private val ctx: Context, private val stage: Stage, private val board: Board, private val backdrop: Backdrop,
             private val games: Games, private val carts: Carts, private val host: Host, startSys: String? = null) {
    interface Host { fun closed(museum: Museum, shellsChanged: Boolean) }

    private class Exhibit(val sys: String, val name: String, val maker: String, val year: Int, val released: String, val sold: String,
                          val cpu: String, val plays: String, val facts: List<String>, val example: String)
    private val exhibits: List<Exhibit>
    private var at = 0
    private val views = listOf("Console", "Cartridge", "In its box", "Box closed")
    private var view = 0
    private var shellsChanged = false

    // the models for the console on show
    private var console: Model? = null
    private var cart: Cart? = null
    private var case: Case? = null
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)

    // how present each piece is (eased toward its target every frame), and turning
    private var presence = 0f; private var leaving = -1   // switching consoles: fade out, swap, fade in
    private var consoleK = 0f; private var caseK = 0f; private var openK = 1f; private var inBox = 0f; private var cartK = 0f
    private var time = 0f; private var closing = false; private var shown = 0f
    private var stickX = 0f; private var stickY = 0f
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

    private enum class Zone { ROWS, FACTS, SHELLS }
    private var zone = Zone.ROWS
    private var before: String? = null   // the default shell to go back to if you press B in the list

    init {
        val j = JSONObject(String(stage.readAsset("museum.json").let { b -> ByteArray(b.remaining()).also { b.get(it) } }))
        exhibits = j.keys().asSequence().filter { carts.styles.containsKey(it) }.map { k -> j.getJSONObject(k).let { e ->
            Exhibit(k, LONG[k] ?: Games.NAMES[k] ?: k, e.getString("maker"), e.getInt("year"), e.getString("released"), e.getString("sold"), e.getString("cpu"), e.getString("plays"),
                e.getJSONArray("facts").let { a -> (0 until a.length()).map { a.getString(it) } }, e.optString("example", "Shelf")) } }
            .sortedWith(compareBy({ it.year }, { ORDER.indexOf(it.sys) })).toList()
        col.addView(label); col.addView(title, lp(10)); col.addView(stats, lp(12))
        col.addView(rows, LinearLayout.LayoutParams(-1, 64 + 80 + 8 * 2 + 12).apply { topMargin = 18; leftMargin = -30; rightMargin = -30 })
        col.addView(factsHead, lp(14)); col.addView(facts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4; leftMargin = -18; rightMargin = -18 })
        scroll.addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { leftMargin = -EDGE; rightMargin = -EDGE })
        panel.addView(listHead, lp(0).apply { bottomMargin = 10 })
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { leftMargin = -30; rightMargin = -30 })
        board.put(panel, 704, 104, 596, 618)
        board.put(timeline, 40, 590, 640, 70)
        hint.addView(key("R", "Turn it")); hint.addView(key("X", "Flip it over").apply { setPadding(26, 0, 0, 0) }); hint.addView(key("L R", "Other consoles").apply { setPadding(26, 0, 0, 0) })
        board.put(hint, 40, 676, 640)
        timeline.names = exhibits.map { it.year.toString() }
        show(exhibits.indexOfFirst { it.sys == startSys }.coerceAtLeast(0))   // opens on the console of the game you were on
        Sounds.play(Sounds.OPEN)
    }

    private fun ex() = exhibits[at]

    /** Puts a console on show: loads its models, fills the panel. */
    private fun show(k: Int) {
        unload()
        at = k.coerceIn(0, exhibits.size - 1)
        val e = ex()
        console = carts.model("console-${e.sys}.glb")?.let { Model(stage, it) }
        case = carts.model("case-${e.sys}.glb")?.let { Case(stage, it, e.example, sampler) }
        cart = carts.make(e.sys, e.example, games.defaultShell(e.sys))
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
        val keep = rows.current()?.key
        rows.rows = listOf(Options.Row("view", "Showing", value = "‹  ${views[view]}  ›"),
            Options.Row("shell", "Default shell", value = v?.name ?: "", sub = "Its standard cartridges on Home; special editions keep theirs", dot = v?.color))
        rows.rows.indexOfFirst { it.key == keep }.takeIf { it > 0 }?.let { rows.focus(it) }
        rows.invalidate()
    }

    private fun unload() {
        console?.destroy(); console = null; case?.destroy(); case = null; cart?.destroy(); cart = null
    }

    // ---------- every frame ----------

    fun stick(x: Float, y: Float) { stickX = x; stickY = y }

    private fun rest(): FloatArray = when (view) {
        0 -> if (ex().sys in HANDHELDS) floatArrayOf(-.3f, -.12f) else floatArrayOf(-.5f, -.38f)
        1 -> floatArrayOf(-.28f, -.1f)
        2 -> floatArrayOf(-.12f, -.42f)
        else -> floatArrayOf(-.35f, -.12f)
    }.also { if (flipped) it[0] += PI.toFloat() }

    fun tick(dt: Float) {
        time += dt
        shown = if (closing) maxOf(0f, shown - dt / .35f) else min(1f, shown + dt / .45f)
        val sk = Backdrop.ease(shown)
        panel.alpha = sk; panel.translationX = (1 - sk) * 70f; timeline.alpha = sk; hint.alpha = sk
        // switching consoles: the old one fades down, the new one comes up
        if (leaving >= 0) { presence = maxOf(0f, presence - dt / .22f); if (presence == 0f) { show(leaving); leaving = -1 } }
        else presence = min(1f, presence + dt / .35f)
        // each piece eases toward what this view shows
        fun ease(cur: Float, want: Float, rate: Float) = cur + (want - cur) * min(1f, dt * rate)
        consoleK = ease(consoleK, if (view == 0) 1f else 0f, 9f)
        cartK = ease(cartK, if (view == 0) 0f else 1f, 9f)
        caseK = ease(caseK, if (view >= 2) 1f else 0f, 7f)
        inBox = ease(inBox, if (view >= 2) 1f else 0f, 6f)
        openK = ease(openK, if (view == 3) 0f else 1f, 5f)
        case?.open = Backdrop.ease(openK.coerceIn(0f, 1f))
        // turning: the stick while held, a coast when let go, then back to the view's resting angle
        val sx = dead(stickX); val sy = dead(stickY); val r = rest()
        if (!closing && (sx != 0f || sy != 0f)) { vYaw += (sx * 3.4f - vYaw) * min(1f, dt * 8f); vPitch += (sy * 2.6f - vPitch) * min(1f, dt * 8f); idle = 0f }
        else { vYaw *= exp(-dt * 1.7f); vPitch *= exp(-dt * 2.4f); idle += dt }
        yaw += vYaw * dt; pitch = (pitch + vPitch * dt).coerceIn(-1.35f, 1.35f)
        if (idle > 1.2f) {
            val target = r[0] + (2 * PI.toFloat()) * ((yaw - r[0]) / (2 * PI)).roundToLong()
            val k = min(1f, dt * 3f); yaw += (target - yaw) * k; pitch += (r[1] - pitch) * k
        }
        stage.turnKey(wrap(yaw - r[0]) * .3f)
        place(Backdrop.ease(presence) * sk)
    }

    private fun place(p: Float) {
        backdrop.shadows.clear()
        val cx = 360f; val cy = 330f + sin(time * .8f) * 4f
        // the console
        val ck = Backdrop.ease(consoleK.coerceIn(0f, 1f)) * p
        console?.let { m -> if (ck < .02f) m.hide() else { m.place(cx, cy, 30f, yaw, pitch, 470f * ck) } }
        // the case: closed it's at most 400 across; open, the cover lies to the left, so it shrinks to fit and shifts right
        val c = case
        val caseS = if (c == null) 1f else min(400f / (2 * maxOf(c.half[0], c.half[1])), 300f / (2 * c.half[0]))
        val kk = Backdrop.ease(caseK.coerceIn(0f, 1f)) * p
        if (c != null) {
            if (kk < .02f) c.hide()
            else {
                val px = caseS * 2 * maxOf(c.half[0], c.half[1]) * kk
                // the open cover lies to the left of the base: move the whole box right by half the overhang, so it stays centred
                val shift = maxOf(0f, -kotlin.math.cos(PI.toFloat() * .97f * c.open)) * c.half[0] * caseS * kk * kotlin.math.cos(yaw)
                c.place(cx + shift, cy, 20f, yaw, pitch, px)
            }
        }
        // the cartridge: free in the middle, or sitting in the tray, or on its way between the two
        val ct = cart ?: return shadow(cx, ck, kk, 0f)
        val ak = Backdrop.ease(cartK.coerceIn(0f, 1f)) * p
        if (ak < .02f) { ct.place(-4000f, 0f, 0f, 0f, 0f, 1f); return shadow(cx, ck, kk, 0f) }
        val free = FloatArray(16).also { freeMatrix(it, ct, cx, cy, ak) }
        val ib = Backdrop.ease(inBox.coerceIn(0f, 1f))
        if (c == null || ib < .01f || kk < .02f) ct.placeMatrix(free)
        else { val tray = c.mediaMatrix(); ct.placeMatrix(FloatArray(16) { free[it] + (tray[it] - free[it]) * ib }) }
        shadow(cx, ck, kk, ak * (1 - ib))
    }

    private fun freeMatrix(m: FloatArray, c: Cart, x: Float, y: Float, k: Float) {
        val s = 380f * k / maxOf(c.style.w, c.style.h)
        android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.translateM(m, 0, x, Stage.H - y, 60f)
        android.opengl.Matrix.rotateM(m, 0, Math.toDegrees(pitch.toDouble()).toFloat(), 1f, 0f, 0f)
        android.opengl.Matrix.rotateM(m, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        android.opengl.Matrix.scaleM(m, 0, s, s, s)
    }

    private fun shadow(x: Float, a: Float, b: Float, c: Float) {
        val k = maxOf(a, b, c); if (k < .05f) return
        backdrop.shadows.add(floatArrayOf(x, 548f, 380f * k, .9f * k))
    }

    // ---------- buttons ----------

    fun input(a: String) {
        if (closing) return
        when (a) {
            "lb" -> return switchTo(at - 1)
            "rb" -> return switchTo(at + 1)
            "x" -> { flipped = !flipped; vYaw = 0f; idle = 9f; Sounds.play(Sounds.TURN); return }
        }
        when (zone) {
            Zone.SHELLS -> shellsInput(a)
            Zone.FACTS -> when (a) {
                "up" -> if (scroll.scrollY > factsHead.top - 40 + 10) scroll.smoothScrollBy(0, -140) else toRows()
                "down" -> if (scroll.scrollY + scroll.height < col.height) scroll.smoothScrollBy(0, 140) else Sounds.play(Sounds.BUMP)
                "b" -> close()
                "left" -> switchTo(at - 1); "right" -> switchTo(at + 1)
            }
            Zone.ROWS -> when (a) {
                "up" -> if (!rows.move(-1)) Sounds.play(Sounds.BUMP) else Sounds.play(Sounds.MOVE)
                "down" -> if (!rows.move(1)) { zone = Zone.FACTS; facts.background = round(Theme.chip, 22f, Theme.sel, 2.5f); rows.active = false; scroll.smoothScrollTo(0, factsHead.top - 40); Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.MOVE)
                "left", "right" -> if (rows.current()?.key == "view") setView((view + (if (a == "left") -1 else 1) + views.size) % views.size) else switchTo(at + if (a == "left") -1 else 1)
                "a" -> when (rows.current()?.key) { "view" -> setView((view + 1) % views.size); "shell" -> startShells() }
                "b" -> close()
            }
        }
    }

    private fun toRows() { zone = Zone.ROWS; facts.background = null; rows.active = true; rows.invalidate(); scroll.smoothScrollTo(0, 0); Sounds.play(Sounds.MOVE) }

    private fun setView(v: Int) { view = v; idle = 9f; vYaw = 0f; fillRows(); Sounds.play(if (v == 3) Sounds.CLOSE else Sounds.MOVE) }

    private fun switchTo(k: Int) {
        if (k !in exhibits.indices) return Sounds.play(Sounds.BUMP)
        if (leaving >= 0) return
        leaving = k; Sounds.play(Sounds.MOVE)
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
        scroll.visibility = View.GONE; listHead.visibility = View.VISIBLE; list.visibility = View.VISIBLE; panel.requestLayout()
        zone = Zone.SHELLS
        if (view != 1) setView(1)   // show the cartridge while choosing
        Sounds.play(Sounds.OPEN)
    }

    private fun previewShell() {
        val key = list.current()?.tag as? String ?: return
        val e = ex(); val v = carts.variantFor(e.sys, key) ?: return
        val c = cart
        if (c == null || (v.clear != null) != (c.variant.clear != null)) { c?.destroy(); cart = carts.make(e.sys, e.example, v) } else c.apply(v)
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
        if (c == null || (v.clear != null) != (c.variant.clear != null)) { c?.destroy(); cart = carts.make(e.sys, e.example, v) } else c.apply(v)
    }
    private fun endShells() {
        list.onFocus = null
        scroll.visibility = View.VISIBLE; listHead.visibility = View.GONE; list.visibility = View.GONE; panel.requestLayout()
        zone = Zone.ROWS; fillRows()
    }

    fun close() { if (!closing) { closing = true; Sounds.play(Sounds.CLOSE); board.postDelayed({ destroy(); host.closed(this, shellsChanged) }, 380) } }

    fun destroy() {
        unload(); backdrop.shadows.clear(); stage.turnKey(0f)
        board.removeView(panel); board.removeView(timeline); board.removeView(hint)
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
        private val HANDHELDS = setOf("gb", "gbc", "gba", "ds", "psp", "android")
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
