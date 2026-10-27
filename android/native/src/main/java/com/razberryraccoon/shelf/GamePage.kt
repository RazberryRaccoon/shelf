package com.razberryraccoon.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * X flips it over. The key light follows a little as it turns, so the plastic catches a sweep of highlight.
 *
 * On the right, a panel that scrolls between three parts: the details and a row of icon tiles (Play, Emulator, Shell
 * color, Favorite, Settings, Back); Pictures (box, title screen, screenshot; A opens them big); and About (the
 * description). Details and pictures come from libretro's database (Meta). Customize shell (with the Studio) and Game settings take over the
 * panel as lists. Up from the tiles, or B, puts the cartridge back.
 */
class GamePage(private val ctx: Context, private val board: Board, private val backdrop: Backdrop, private val stage: Stage,
               private val games: Games, private val carts: Carts, private val meta: Meta, val game: Game, var cart: Cart?,
               private val from: FloatArray, private val host: Host,
               /** Opened from a box on Home: it starts as that closed box, and goes back into it closing. */
               private val fromBox: Boolean = false) {
    companion object {
        // The box's pictures for the game Home has settled on, read from their files ahead of time on another thread, so
        // opening its page doesn't wait on them (they were most of the pause when a page opened)
        private val early = java.util.concurrent.Executors.newSingleThreadExecutor()
        private var ahead: Pair<String, java.util.concurrent.Future<Array<Bitmap?>>>? = null

        private fun read(meta: Meta, g: Game): Array<Bitmap?> {
            val pics = meta.cached(g)?.pics
            return arrayOf(pics?.get("box")?.let { meta.bitmap(it, 512) }, pics?.get("back")?.let { meta.bitmap(it, 512) }, pics?.get("spine")?.let { meta.bitmap(it, 256) })
        }

        /** Home calls this when the selection settles on a game. */
        fun prepare(meta: Meta, g: Game) {
            if (ahead?.first == g.path) return
            drop()
            ahead = g.path to early.submit(java.util.concurrent.Callable { read(meta, g) })
        }

        private fun drop() { ahead?.second?.let { f -> early.execute { try { f.get().forEach { it?.recycle() } } catch (_: Exception) { } } }; ahead = null }

        /** The box's pictures: the ones read ahead if they're for this game (waiting for them if still reading), else read now. */
        fun boxArt(meta: Meta, g: Game): Array<Bitmap?> {
            val a = ahead
            if (a?.first == g.path) { ahead = null; try { return a.second.get() } catch (_: Exception) { } }
            return read(meta, g)
        }
    }

    interface Host {
        fun play(g: Game)
        fun pick(p: Picker)
        fun askText(title: String, value: String, then: (String) -> Unit)
        fun show(msg: String)
        fun cartChanged(g: Game, c: Cart?)
        fun closed(page: GamePage, homeChanged: Boolean)
        /** Android's file picker, for a file of your own on the media page. */
        fun openDocument(mimes: Array<String>, then: (android.net.Uri?) -> Unit)
        /** The game's collections card (the Collections tile); `changed` true if it was hidden. */
        fun collectionsCard(g: Game, changed: (Boolean) -> Unit)
        /** Hides the game (Game settings), choosing a code first if there isn't one. */
        fun hideGame(g: Game, then: () -> Unit)
        /** The names of your collections the game is in. */
        fun collectionNames(g: Game): List<String>
    }

    private var t = 0f                  // 0 = in the row, 1 = open
    private var closing = false
    /** How open the page is (0..1), eased; Home fades its own details out by this much. */
    val shownK get() = Backdrop.ease(t)
    private var time = 0f
    private var homeChanged = false     // shown on Home or not, changed in Game settings

    // turning it in your hands
    private var stickX = 0f; private var stickY = 0f
    // What the page shows (Settings chooses which first; L2 and R2 go through them): 0 the cartridge, 1 the cartridge in
    // its open box, 2 the box closed. They flow into each other along one line of steps (BoxFlow, as in the Museum): the
    // open box slides in from the left, the cartridge slots into its tray, the cover swings shut; and back the same way.
    private var view = games.pageView
    private val sampler = com.google.android.filament.TextureSampler(com.google.android.filament.TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, com.google.android.filament.TextureSampler.MagFilter.LINEAR, com.google.android.filament.TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val case: Case? = carts.model("case-${game.sys}.glb")?.let { val art = boxArt(meta, game)
        Case(stage, it, game.title, sampler, art[0], art[1], art[2]) }?.also { it.hide() }
    private val flow = BoxFlow()
    private val fromRy = from.getOrElse(3) { -.2f }   // how it was turned in Home's row
    // Game settings takes the cartridge apart on a surface (Cart.teardown), with a screwdriver beside it: how far, 0..1
    private var tk = 0f
    // loaded the first time the cartridge comes apart, not with every page
    private var tool: Model? = null; private var toolTried = false
    private val tearing get() = mode == Mode.SETTINGS && cart?.canOpen == true
    /** Starting the game: the start animation has the cartridge, and the page fades out of its way (and back after). */
    var launching = false
    private var launchK = 0f
    private var flipped = false
    // each view's resting angle: the cartridge's front a little turned so its side shows, the open box from a little
    // above so the tray shows, the closed box's front; X adds a half turn to show the back
    // taken apart (Game settings), looking down on the surface the parts lie on
    // Customize shell and the Studio always show the cartridge itself, out of its box, so you see the colors you pick
    private val shown get() = if (mode == Mode.PAGE) view else 0
    private val restPitch: Float get() = if (tearing) -.95f else when (shown) { 1 -> -.42f; 2 -> -.12f; else -> -.1f }
    private val restYaw: Float get() = (if (tearing) 0f else when (shown) { 1 -> -.12f; 2 -> -.35f; else -> -.28f }) + if (flipped) PI.toFloat() else 0f
    private var yaw = -.2f; private var pitch = -.1f
    private var vYaw = 0f; private var vPitch = 0f
    private var idle = 9f                // seconds since the stick was last used
    private var everTurned = false

    private val EDGE = 28   // how far the scrolling part reaches into the panel's side padding
    // the panel: a scrolling column, or a list (shell colors, game settings) in its place
    private val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(44, 34, 44, 26); clipToPadding = false; alpha = 0f }
    private val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(36); overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false }
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipToPadding = false; clipChildren = false; setPadding(EDGE, 0, EDGE, 30) }
    private val chipText = text(21f, Fonts.bold, Theme.fg)
    private val chipDot = View(ctx)
    private val favChip = text(21f, Fonts.bold, Theme.fg).apply { this.text = "Favorite"; background = round(Theme.chip, 19f); setPadding(16, 6, 16, 6) }
    private val title = text(38f, Fonts.display, Theme.fg).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.08f) }
    private val facts = text(21f, Fonts.body, Theme.fg2).apply { setLineSpacing(0f, 1.42f) }
    private val tiles = Tiles(ctx)
    private val note = text(20f, Fonts.body, Theme.fg2).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.3f) }
    private val picsHead = head("PICTURES")
    private val shots = Shots(ctx)
    private val aboutHead = head("ABOUT")
    private val about = text(21f, Fonts.body, Theme.fg2).apply { setLineSpacing(0f, 1.45f); setPadding(18, 14, 18, 16); maxLines = 9; ellipsize = TextUtils.TruncateAt.END }
    private val readMore = key("A", "Read the article").apply { alpha = 0f }
    // the manual: a wide button under the tiles (and the booklet itself, inside the open box)
    private val hasManuals = game.sys != "android"
    private val manualBtn = text(22f, Fonts.bold, Theme.fg).apply { gravity = Gravity.CENTER; background = round(Theme.chip, 26f); setPadding(0, 15, 0, 15) }
    private var manualStage = 0       // 0 in its box, 1 waiting for the box to open, 2 coming out, 3 being read, 4 going back in
    private var manualK = 0f          // how far out: 0 in the cover, 1 in front of you
    private var manualFocus = 0f      // chosen where it lies (lifted a little)
    // left of the tiles, the models themselves can be chosen: in the open box the cartridge (A starts the game) and the
    // manual; the cartridge on its own, or the closed box, to look at closely (Inspect)
    private var pick = 0              // in Zone.BOX: 0 the cartridge (or the closed box), 1 the manual
    private var pickFocus = 0f        // how lifted the chosen cartridge or box is
    private var inspecting = false
    private var inspK = 0f; private var inspZoom = 1f; private var inspZoomTo = 1f
    private var panX = 0f; private var panY = 0f; private var panToX = 0f; private var panToY = 0f
    private var reader: Manual? = null
    private var findingManual = false
    private var manualCoverFor: String? = null   // the manual whose cover the booklet wears
    private val credit = text(17f, Fonts.body, Theme.fg3).apply { setPadding(18, 0, 18, 0) }
    private val listHead = head("")
    private val list = Options(ctx)
    private val hint = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; alpha = 0f }

    private enum class Zone { TILES, MANUAL, PICS, ABOUT, BOX }   // BOX: the manual, chosen where it lies in the open box
    private var zone = Zone.TILES
    private enum class Mode { PAGE, COLORS, SETTINGS, STUDIO }
    private var mode = Mode.PAGE
    private var colorsFrom = Mode.PAGE   // where B goes back to from the shell colors
    private var before: Variant? = null  // the shell color to go back to if you press B
    private var info: Meta.Info? = null
    private var viewer: Viewer? = null
    private var article: Article? = null   // About, opened up as an article
    private var media: MediaPage? = null   // Game settings › Media
    private var player: Player? = null     // the game's video, when chosen
    private var shotsFor: Meta.Info? = null   // the look-up the thumbnails were made from

    init {
        if (fromBox && case != null) flow.snap(3f)   // it starts as Home's closed box, with the cartridge inside
        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val chip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = round(Theme.chip, 19f); setPadding(16, 6, 16, 6) }
        chip.addView(chipDot, LinearLayout.LayoutParams(12, 12).apply { rightMargin = 9 }); chip.addView(chipText)
        chips.addView(chip); chips.addView(favChip, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 10 })
        col.addView(chips)
        col.addView(title, lp(16))
        col.addView(facts, lp(12))
        col.addView(tiles, LinearLayout.LayoutParams(-1, Tiles.HEIGHT).apply { topMargin = 16; leftMargin = -Tiles.INSET; rightMargin = -Tiles.INSET })
        col.addView(note, LinearLayout.LayoutParams(-1, 82).apply { topMargin = 2 })   // a fixed three lines, so nothing moves and nothing is cut
        if (hasManuals) col.addView(manualBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8; leftMargin = -4; rightMargin = -4 })
        col.addView(picsHead, lp(18))
        col.addView(shots, LinearLayout.LayoutParams(-1, Shots.HEIGHT).apply { topMargin = 6; leftMargin = -Shots.INSET; rightMargin = -Shots.INSET })
        col.addView(aboutHead, lp(16))
        col.addView(about, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4; leftMargin = -18; rightMargin = -18 })
        col.addView(readMore, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 2; bottomMargin = 10 })
        col.addView(credit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4; leftMargin = -18; rightMargin = -18 })
        scroll.addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { leftMargin = -EDGE; rightMargin = -EDGE })   // reaches into the padding: room for focus rings
        panel.addView(listHead, lp(0).apply { bottomMargin = 10 })
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { leftMargin = -30; rightMargin = -30 })
        listHead.visibility = View.GONE; list.visibility = View.GONE
        board.put(panel, 704, 124, 596, 598)
        fillHint()
        board.put(hint, 30, 664, 660)
        tiles.onMove = { refreshNote() }
        info = meta.cached(game)
        refresh()
        if (info == null) meta.get(game) { i -> info = i; refresh() }
        Sounds.play(Sounds.OPEN)
    }

    /** Updates the details after anything changes (emulator, shell color, favorite, what was looked up). */
    fun refresh() {
        val i = info; val f = i?.fields ?: emptyMap()
        chipText.text = Games.NAMES[game.sys] ?: game.sys; chipDot.background = round(Theme.accentOf(game.sys), 6f)
        favChip.visibility = if (games.favorite(game)) View.VISIBLE else View.GONE
        title.text = i?.name?.let { ArtIndex.stripTags(it).replace(Regex("\\s+"), " ").trim() }?.ifEmpty { null } ?: game.title
        val played = games.lastPlayed(game); val n = games.timesPlayed(game)
        val about1 = listOfNotNull(f["releaseyear"], f["developer"] ?: f["publisher"], f["genre"]).joinToString(" · ")
        facts.text = listOfNotNull(
            about1.ifEmpty { null },
            if (played == 0L) "Not played yet" else "Played " + DateUtils.getRelativeTimeSpanString(played, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) + (if (n > 1) " · $n times" else ""),
            "Plays with " + (games.emulatorFor(game)?.let { emuName(it) } ?: "nothing yet")).joinToString("\n")
        tiles.fav = games.favorite(game)
        tiles.swatch = carts.variantFor(game.sys, carts.shellFor(games, game))?.color ?: Theme.fg2
        tiles.invalidate()
        // pictures: the box, title screen and a screenshot, as they arrive
        if (shotsFor !== i || i == null) {
            shotKinds = i?.let { Meta.pictures(game.sys).filter { (k, _) -> i.pics[k] != null }.map { it.first } } ?: emptyList()
            shots.set(null)
            // read from their files off the main thread (a video's frame especially is slow to get), then shown
            if (i != null) Thread {
                val list = Meta.pictures(game.sys).mapNotNull { (k, name) -> i.pics[k]?.let { f2 -> (if (k == "video") videoFrame(f2) else meta.bitmap(f2, 360))?.let { name to it } } }
                board.post { if (closing || shotsFor !== i) list.forEach { it.second.recycle() } else shots.set(list) }
            }.start()
        }
        shotsFor = i
        about.text = f["description"] ?: when {
            i == null -> "Looking this game up…"
            i.name == null -> "Shelf couldn’t find this game in libretro’s database. Game settings › Which game is this? can help."
            else -> listOfNotNull(f["publisher"]?.let { "Published by $it." }, f["releaseyear"]?.let { "Released in $it." }).joinToString(" ").ifEmpty { "No description for this one yet." }
        }
        credit.text = listOfNotNull(i?.name?.let { "Matched to $it (libretro’s database)" },
            i?.src?.get("details")?.let { "Details: $it" }, f["descsource"]?.let { if (it == "Wikipedia") "Description: Wikipedia (CC BY-SA)" else "Description: $it" }).joinToString("\n")
        refreshManual()
        refreshNote()
    }

    private fun emuName(e: String) = if (e.startsWith(Games.RA)) e.removePrefix(Games.RA) + ", in RetroArch" else e

    /** The Collections tile's line: which collections it's in, or what the tile does. */
    private fun collectionsNote(): String {
        val names = (if (games.favorite(game)) listOf("Favorites") else emptyList()) + host.collectionNames(game)
        return if (names.isEmpty()) "Put it in your collections, or start a new one. (Y makes it a favorite.)" else "In " + names.joinToString(", ") + "."
    }

    private fun refreshNote() {
        val emu = games.emulatorFor(game)?.let { emuName(it) }
        note.text = when (tiles.at) {
            0 -> if (emu == null) "Choose an emulator, then it starts." else "Starts it in $emu."
            1 -> if (games.ownEmulator(game) != null) "Plays with $emu, chosen for this game." else "Plays with $emu. Choose one just for this game."
            2 -> "Its shell: the real colors, or your own from the Studio."
            3 -> collectionsNote()
            4 -> "Its emulator, shell color, whether it shows on Home, and which game it is."
            else -> "Put it back on Home."
        }
    }

    // ---------- every frame ----------

    fun stick(x: Float, y: Float) { stickX = x; stickY = y; reader?.stick(x, y) }
    // the left stick, while looking closely: it moves the model smoothly (the d-pad still steps)
    private var leftX = 0f; private var leftY = 0f
    fun leftStick(x: Float, y: Float): Boolean { leftX = x; leftY = y; return inspecting }
    /** How far the looking-closely view has come in (0..1), for Home's top bar. */
    val inspectK get() = Backdrop.ease(inspK)

    fun tick(dt: Float) {
        time += dt
        viewer?.tick(dt); article?.tick(dt); media?.tick(dt); player?.tick(dt); reader?.tick(dt)
        // closing: the cartridge comes out of its box and the box slides away first, then it goes back to the row
        // from a box on Home, the box closes round the cartridge first instead, and that goes back
        val ready = if (fromBox && case != null) flow.s > 2.98f else flow.s <= .02f
        t = if (closing) (if (!ready) t else maxOf(0f, t - dt / .5f)) else min(1f, t + dt / .6f)
        val k = Backdrop.ease(t)
        val pk = Backdrop.ease(((t - .3f) / .7f).coerceIn(0f, 1f))
        studioK = if (studio != null) min(1f, studioK + dt / .3f) else maxOf(0f, studioK - dt / .25f)
        val sk = Backdrop.ease(studioK)
        launchK = if (launching) min(1f, launchK + dt / .3f) else maxOf(0f, launchK - dt / .4f)
        val lk = 1 - Backdrop.ease(launchK)
        val mk = 1 - Backdrop.ease(manualK)   // the panel steps back while the manual is out
        inspK = if (inspecting) min(1f, inspK + dt / .4f) else maxOf(0f, inspK - dt / .35f)
        val ik = Backdrop.ease(inspK)
        inspZoom += (inspZoomTo * (if (inspecting) 1f else 1f) - inspZoom) * min(1f, dt * 8f)
        if (inspecting) {   // the left stick glides it (as the right stick turns it); the d-pad's steps ease the same way
            panToX -= dead(leftX) * 700f * dt; panToY -= dead(leftY) * 700f * dt
            val lim = 260f * inspZoomTo; panToX = panToX.coerceIn(-lim, lim); panToY = panToY.coerceIn(-lim * .7f, lim * .7f)
        }
        panX += ((if (inspecting) panToX else 0f) - panX) * min(1f, dt * 10f); panY += ((if (inspecting) panToY else 0f) - panY) * min(1f, dt * 10f)
        backdrop.shade = ik * .75f
        pickFocus += ((if (zone == Zone.BOX && pick == 0 && !inspecting) 1f else 0f) - pickFocus) * min(1f, dt * 10f)
        panel.alpha = pk * (1 - sk) * lk * mk * (1 - ik); panel.translationX = (1 - pk) * 70f - sk * 40f
        studioPanel.alpha = pk * sk * lk; studioPanel.translationX = (1 - sk) * 50f
        if (studio == null && studioK == 0f && studioPanel.parent != null) board.removeView(studioPanel)
        // the stick: turn while held, coast when let go, then settle back to the front (or the back, after X)
        val sx = dead(stickX); val sy = dead(stickY)
        if (!closing && viewer == null && article == null && media == null && player == null && reader == null && manualStage == 0 && (sx != 0f || sy != 0f)) {
            vYaw += (sx * 3.4f - vYaw) * min(1f, dt * 8f); vPitch += (sy * 2.6f - vPitch) * min(1f, dt * 8f)
            idle = 0f
        } else {
            vYaw *= exp(-dt * 1.7f); vPitch *= exp(-dt * 2.4f); idle += dt
        }
        yaw += vYaw * dt; pitch = (pitch + vPitch * dt).coerceIn(-1.35f, 1.35f)
        if (inspecting) idle = 0f   // looking closely: it stays as you turned it
        if (idle > 1.2f || closing) {
            val target = restYaw + (2 * PI.toFloat()) * ((yaw - restYaw) / (2 * PI)).roundToLong()
            val r = min(1f, dt * (if (closing) 6f else 2.4f))
            yaw += (target - yaw) * r; pitch += (restPitch - pitch) * r
        }
        stage.turnKey(wrap(yaw - restYaw) * .3f * k)
        // from its place in the row to the left half, arcing up a little on the way
        val x0 = lerp(from[0], 350f, k); val y0 = lerp(from[1], 384f, k) - sin(PI.toFloat() * k) * 46f + sin(time * .8f) * 4f * k
        // looking closely: it moves to the middle of the screen, where the stick and buttons move it
        val x = lerp(x0, Stage.W / 2 + panX, ik); val y = lerp(y0, Stage.H / 2 - 20f + panY, ik)
        // taking it apart: once the box is out of the way; the layout is wider and taller than the cartridge, so it
        // shrinks to fit and moves up a little (the parts are laid out below the halves)
        tk = if (tearing && !closing && !launching && flow.s < .02f) min(1f, tk + dt / 2.6f) else maxOf(0f, tk - dt / 1.4f)
        val te = Backdrop.ease(tk)
        val big = cart?.let { maxOf(it.style.w, it.style.h) } ?: 1f
        val fit = cart?.let { c -> 1.3f * big / maxOf(2.9f * c.style.w, 1.45f * c.style.h) } ?: 1f   // the parts' spread, seen from above, about 520 across
        val px = lerp(from[2], 400f, k) * lerp(1f, fit, te) * lerp(1f, 1.55f * inspZoom, if (view == 0) ik else 0f)
        val ty = (cart?.style?.h ?: 0f) * .3f * (px / big) * te
        val ry = lerp(fromRy, yaw, k); val rx = lerp(-.1f, pitch, k)
        if (!launching) cart?.place(x, y - ty, lerp(40f, 70f, k), ry, rx, px, held = true)
        val boxedTo = when {
            case == null || launching || mode != Mode.PAGE || tk > .02f -> 0f
            closing -> if (fromBox) 3f else 0f
            t < .25f -> if (fromBox) 3f else 0f
            else -> when (view) { 1 -> 2f; 2 -> 3f; else -> 0f }
        }
        val was = flow.s
        flow.tick(dt, boxedTo, if (closing) 1.3f else 1f)
        if (was < 2f && flow.s >= 2f) Sounds.play(Sounds.CLICK)        // the cartridge seats in its tray
        if (was < 3f && flow.s >= 3f) Sounds.play(Sounds.CLOSE)        // the cover shuts
        cart?.teardown(tk)
        val spot = cart?.toolSpot(tk)
        if (tk > 0f && !toolTried) { toolTried = true; tool = carts.model("screwdriver.glb")?.let { Model(stage, it) }?.also { it.hide() } }
        val tool = tool
        if (tool != null) { if (spot != null && tk > .66f && !launching) tool.placeMatrix(FloatArray(16).also { android.opengl.Matrix.multiplyMM(it, 0, cart!!.placed, 0, spot, 0) }) else tool.hide() }
        placeBox(x, y, ry, rx, ik)
        if (flow.s > .001f) placeManual(dt)
        glows(dt)
        cart?.brightness(lerp(1.02f, 1f, k) + .14f * pickFocus * (if (view != 2) 1f else 0f))   // chosen: a little brighter
        if (!launching) cart?.let { c ->
            val big = maxOf(c.style.w, c.style.h)
            backdrop.shadows.add(floatArrayOf(x, lerp(from[1] + c.style.h / big * from[2] / 2 + 24f, 600f, k), lerp(c.style.w / big * from[2] * .9f, 330f, k), (1f - .25f * k) * (1 - ik)))
        }
        // the hint under the cartridge shows for a few seconds, then gets out of the way
        hint.alpha = pk * lk * mk
        hint.translationX = ik * (Stage.W / 2 - 360f)   // looking closely: the buttons centred under the model
        if (closing && t == 0f) { destroy(); host.closed(this, homeChanged) }
    }

    // ---------- buttons ----------

    fun input(a: String) {
        if (closing) return
        viewer?.let { if (it.input(a)) return }
        article?.let { if (it.input(a)) return }
        media?.let { if (it.input(a)) return }
        player?.let { if (it.input(a)) return }
        reader?.let { if (it.input(a)) return }
        if (manualStage != 0) return   // the manual is on its way out or back
        if (inspecting) return inspectInput(a)
        when (mode) {
            Mode.COLORS -> return colorsInput(a)
            Mode.STUDIO -> return if (a == "x") flip() else studio?.input(a) ?: Unit
            Mode.SETTINGS -> return settingsInput(a)
            Mode.PAGE -> {}
        }
        if (a == "x") return flip()
        if (a == "lt" || a == "rt") return setView(view + if (a == "lt") -1 else 1)
        if (a == "y") return toggleFavorite()
        if (a == "b") return close()
        when (zone) {
            Zone.TILES -> when (a) {
                // left from the first tile, with the box open, reaches the manual lying in it
                "left" -> if (tiles.move(-1)) Sounds.play(Sounds.MOVE) else if (canPick()) { pick = 0; goTo(Zone.BOX) } else Sounds.play(Sounds.BUMP)
                "right" -> Sounds.play(if (tiles.move(1)) Sounds.MOVE else Sounds.BUMP)
                "up" -> close()
                "down" -> goTo(if (hasManuals) Zone.MANUAL else if (shots.count > 0) Zone.PICS else Zone.ABOUT)
                "a" -> when (tiles.at) {
                    0 -> play()
                    1 -> chooseEmulator(then = false)
                    2 -> startColors(Mode.PAGE)
                    3 -> host.collectionsCard(game) { hidden -> if (hidden) { homeChanged = true; close() } else { tiles.fav = games.favorite(game); tiles.invalidate(); refreshNote() } }
                    4 -> startSettings()
                    else -> close()
                }
            }
            Zone.MANUAL -> when (a) {
                "up" -> goTo(Zone.TILES)
                "down" -> goTo(if (shots.count > 0) Zone.PICS else Zone.ABOUT)
                "a" -> readManual()
                "left", "right" -> Sounds.play(Sounds.BUMP)
            }
            Zone.BOX -> when (a) {
                "left" -> if (pick == 0 && manualInBox()) { pick = 1; Sounds.play(Sounds.MOVE); fillHint() } else Sounds.play(Sounds.BUMP)
                "right" -> if (pick == 1) { pick = 0; Sounds.play(Sounds.MOVE); fillHint() } else goTo(Zone.TILES)
                "b" -> goTo(Zone.TILES)
                "a" -> when { pick == 1 -> readManual(); view == 1 -> play(); else -> startInspect() }
                else -> Sounds.play(Sounds.BUMP)
            }
            Zone.PICS -> when (a) {
                "left" -> Sounds.play(if (shots.move(-1)) Sounds.MOVE else Sounds.BUMP)
                "right" -> Sounds.play(if (shots.move(1)) Sounds.MOVE else Sounds.BUMP)
                "up" -> goTo(if (hasManuals) Zone.MANUAL else Zone.TILES)
                "down" -> goTo(Zone.ABOUT)
                "a" -> openViewer()
            }
            Zone.ABOUT -> when (a) {
                // reading: up and down scroll the text, and leave it at either end
                "up" -> if (scroll.scrollY > aboutHead.top - 40 + 10) scroll.smoothScrollBy(0, -140) else goTo(if (shots.count > 0) Zone.PICS else if (hasManuals) Zone.MANUAL else Zone.TILES)
                "down" -> if (scroll.scrollY + scroll.height < col.height) scroll.smoothScrollBy(0, 140) else Sounds.play(Sounds.BUMP)
                "a" -> openArticle()
            }
        }
    }

    private fun goTo(z: Zone) {
        zone = z; Sounds.play(Sounds.MOVE)
        tiles.active = z == Zone.TILES; shots.active = z == Zone.PICS
        manualBtn.background = if (z == Zone.MANUAL) round(Theme.sel, 26f) else round(Theme.chip, 26f)
        manualBtn.setTextColor(if (z == Zone.MANUAL) Theme.selFg else Theme.fg)
        fillHint()
        about.background = if (z == Zone.ABOUT) round(Theme.chip, 22f, Theme.sel, 2.5f) else null
        readMore.animate().alpha(if (z == Zone.ABOUT && info?.fields?.get("description") != null) 1f else 0f).setDuration(200).start()
        tiles.invalidate(); shots.invalidate()
        // keep the part you're in in view
        scroll.smoothScrollTo(0, when (z) { Zone.TILES, Zone.BOX -> 0; Zone.MANUAL -> manualBtn.top - 260; Zone.PICS -> picsHead.top - 80; Zone.ABOUT -> aboutHead.top - 40 }.coerceAtLeast(0))
    }

    fun close() { if (!closing) { closing = true; inspecting = false; studio = null; viewer?.let { board.removeView(it); viewer = null }; article?.let { board.removeView(it); article = null }; media?.let { board.removeView(it); media = null }; player?.let { board.removeView(it); player = null }; reader?.let { board.removeView(it); reader = null }; if (mode == Mode.COLORS) endColors(keep = false); Sounds.play(Sounds.CLOSE) } }

    private fun play() {
        if (games.emulatorFor(game) == null) return chooseEmulator(then = true)
        Sounds.play(Sounds.PICK); host.play(game)
    }

    private fun flip() {
        flipped = !flipped
        vYaw = 0f; vPitch = 0f; idle = 9f
        Sounds.play(Sounds.TURN)
    }

    private fun toggleFavorite() {
        val on = !games.favorite(game); games.setFavorite(game, on)
        Sounds.play(if (on) Sounds.PICK else Sounds.MOVE)
        if (on) vYaw += 2.2f   // a little happy spin
        refresh()
    }

    /** This game's emulator: the same as its console's other games, or one of its own. */
    private fun chooseEmulator(then: Boolean) {
        val console = Games.NAMES[game.sys] ?: game.sys
        val choices = games.emulatorChoices(game.sys)
        val own = games.ownEmulator(game)
        val rows = listOf(Picker.Row("Same as other $console games", games.emulator(game.sys)?.let { emuName(it) } ?: "Not chosen yet")) +
            choices.map { (n, ok) -> Picker.Row(emuName(n), if (ok) "" else "Not installed") }
        host.pick(Picker(ctx, "Play ${title.text} with", rows, start = if (own == null) 0 else 1 + maxOf(0, choices.indexOfFirst { it.first == own }),
            note = if (choices.any { it.first.startsWith(Games.RA) }) "A RetroArch core has to be downloaded in RetroArch first (Main Menu › Load Core › Download a Core)." else null) { k ->
            if (k == 0) { games.setOwnEmulator(game, null); if (games.emulator(game.sys) == null) { host.show("Choose one for $console games in Settings › Emulators, or one for this game"); refresh(); return@Picker } }
            else games.setOwnEmulator(game, choices[k - 1].first)
            refresh(); if (mode == Mode.SETTINGS) fillSettings()
            if (then) play() else host.show("${title.text} plays with ${games.emulatorFor(game)?.let { emuName(it) }}")
        })
    }

    // ---------- the pictures, big ----------

    /** A frame from a game's video, for its place among the pictures. */
    private fun videoFrame(f: java.io.File): Bitmap? = try { android.media.MediaMetadataRetriever().run { try { setDataSource(f.path); getFrameAtTime(2_000_000) } finally { release() } } } catch (e: Exception) { null }
    private var shotKinds: List<String> = emptyList()

    private fun playVideo(f: java.io.File) {
        val v = Player(ctx, f, title.text.toString())
        v.onClose = { board.removeView(v); if (player === v) player = null }
        player = v; board.put(v, 0, 0, 1334, 750)
        Sounds.play(Sounds.OPEN)
    }

    private fun openViewer() {
        val i = info ?: return
        if (shotKinds.getOrNull(shots.at) == "video") return i.pics["video"]?.let { playVideo(it) } ?: Unit
        val pics = Meta.pictures(game.sys).filter { it.first != "video" }.mapNotNull { (k, name) -> i.pics[k]?.let { name to it } }
        if (pics.isEmpty()) return
        val v = Viewer(ctx, pics.map { it.first }, pics.map { meta.bitmap(it.second, 1200) }, shots.at.coerceAtMost(pics.size - 1), title.text.toString())
        v.onClose = { board.removeView(v); if (viewer === v) viewer = null }
        viewer = v; board.put(v, 0, 0, 1334, 750)
        Sounds.play(Sounds.OPEN)
    }

    // ---------- the manual ----------

    private fun manualFile() = info?.pics?.get("manual")?.takeIf { it.exists() }
    private fun manualInBox() = hasManuals && view == 1 && case?.hasManual == true && flow.s > 1.9f && manualStage == 0
    /** Whether the model can be chosen now: settled in its view (the open box open, the closed box shut, or the cartridge alone). */
    private fun canPick() = mode == Mode.PAGE && tk < .02f && manualStage == 0 && when (view) { 1 -> flow.s > 1.9f; 2 -> flow.s > 2.9f; else -> flow.s < .02f }

    // ---------- looking closely (Inspect) ----------

    /** The closed box or the cartridge, big in the middle of the screen with everything else dimmed. */
    private fun startInspect() {
        inspecting = true; inspZoomTo = 1f; panToX = 0f; panToY = 0f; idle = 0f
        Sounds.play(Sounds.OPEN); fillHint()
    }
    private fun inspectInput(a: String) {
        when (a) {
            "rb" -> if (inspZoomTo < 3f) { inspZoomTo += .5f; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "lb" -> if (inspZoomTo > 1f) { inspZoomTo -= .5f; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            // the left stick or the d-pad moves your view across it (the model the other way), a step at a time, eased
            "left" -> panToX += 90f; "right" -> panToX -= 90f; "up" -> panToY += 90f; "down" -> panToY -= 90f
            "b", "a" -> { inspecting = false; Sounds.play(Sounds.CLOSE); fillHint() }
            "x" -> flip()
        }
        val lim = 260f * inspZoomTo; panToX = panToX.coerceIn(-lim, lim); panToY = panToY.coerceIn(-lim * .7f, lim * .7f)
    }
    private fun refreshManual() {
        manualBtn.text = when { findingManual -> "Looking for the manual…"; manualFile() != null -> "Read the manual"; else -> "Find the manual" }
        // the booklet in the box wears the manual's own cover, once there is one
        val f = manualFile() ?: return
        if (manualCoverFor == f.path) return
        manualCoverFor = f.path
        Thread { val b = try { MediaPage.pdfCover(f, 512) } catch (e: Throwable) { null }; board.post { if (closing || b == null) b?.recycle() else case?.manualArt(b) } }.start()
    }
    /**
     * Reads the manual: the box comes in and opens (if it wasn't showing), the booklet lifts out of the cover and comes
     * up in front of you, and opens into the reader. Without a manual yet, it looks for one first.
     */
    private fun readManual() {
        if (manualStage != 0) return
        val f = manualFile()
        if (f == null) {
            if (findingManual) return Sounds.play(Sounds.BUMP)
            findingManual = true; refreshManual(); Sounds.play(Sounds.PICK)
            val name = info?.name ?: game.title
            Thread {
                var ok = false
                for (id in Media.ORDER["manual"] ?: emptyList()) {
                    val cs = try { Media.choices("manual", game.sys, name, java.io.File(game.path), games.artKeys, ctx.cacheDir, meta, only = id) } catch (e: Exception) { emptyList() }
                    if (cs.take(3).any { Media.keep(game, "manual", it, meta) }) { ok = true; break }
                }
                board.post {
                    findingManual = false; info = meta.cached(game); refreshManual()
                    if (closing) return@post
                    if (ok) readManual() else host.show("No manual found for this game. Game settings › Media can look on other sources.")
                }
            }.start()
            return
        }
        Sounds.play(Sounds.OPEN)
        if (case == null || !case.hasManual) return openReader(f)
        if (view != 1) { view = 1; idle = 9f; vYaw = 0f; fillHint() }
        manualStage = 1
    }

    private fun openReader(f: java.io.File) {
        val (at, marks) = games.manualPlace(game)
        val v = Manual(ctx, f, title.text.toString(), at, marks) { s, m -> games.setManualPlace(game, s, m) }
        v.onClose = { board.removeView(v); if (reader === v) reader = null; if (manualStage == 3) manualStage = 4 }
        reader = v; board.put(v, 0, 0, 1334, 750)
    }

    // The glow around what's chosen in the box view (Outline): the cartridge, the closed box or the manual, in the
    // console's color. Made the first time something is chosen, not with every page
    private var glow: Outline? = null; private var markedCart: Cart? = null; private var boxMarked = false

    private fun glows(dt: Float) {
        val choosing = zone == Zone.BOX && canPick() && !inspecting && !launching && !closing
        if (choosing && glow == null) glow = Outline(stage)
        val g = glow ?: return
        // each object marks its own pixels, so the glow's inner edge is its real outline
        val ct = cart
        if (ct !== markedCart) { markedCart?.let { Outline.mark(stage, it.asset.entities, 0) }; ct?.let { Outline.mark(stage, it.asset.entities, Outline.CART) }; markedCart = ct }
        val c = case
        if (c != null && !boxMarked) { boxMarked = true; Outline.mark(stage, c.asset.entities, Outline.BOX); if (c.hasManual) Outline.mark(stage, intArrayOf(c.manualEntity), Outline.MANUAL) }
        when {
            !choosing -> g.frame(dt, null, 0, null, null, null, null)
            pick == 1 && c != null && c.hasManual -> { val ms = c.manualSize
                g.frame(dt, intArrayOf(c.manualEntity), Outline.MANUAL, game.sys, c.manualNow(), floatArrayOf(0f, 0f, 0f), floatArrayOf(ms[0] / 2, ms[1] / 2, .9f)) }
            view == 2 && c != null -> g.frame(dt, c.asset.entities, Outline.BOX, game.sys, c.current(), c.center, c.half)
            ct != null -> { val bb = ct.asset.boundingBox; g.frame(dt, ct.asset.entities, Outline.CART, game.sys, ct.current(), bb.center, bb.halfExtent) }
            else -> g.frame(dt, null, 0, null, null, null, null)
        }
    }

    /** The manual's place each frame: in the cover (lifted while chosen), or on its way out to you and back. */
    private fun placeManual(dt: Float) {
        val c = case ?: return
        if (!c.hasManual) return
        manualFocus += ((if (zone == Zone.BOX && pick == 1 && manualStage == 0) 1f else 0f) - manualFocus) * min(1f, dt * 10f)
        when (manualStage) {
            1 -> if (flow.s >= 1.98f) manualStage = 2
            2 -> { manualK = min(1f, manualK + dt / .9f); if (manualK >= 1f) { manualStage = 3; manualFile()?.let { openReader(it) } ?: run { manualStage = 4 } } }
            4 -> { manualK = maxOf(0f, manualK - dt / .8f); if (manualK <= 0f) manualStage = 0 }
        }
        if (manualK <= 0f) return c.manualIn(manualFocus)
        // lifted off the cover toward you, then carried to the middle of the screen, facing you, as big as the reader's cover
        val rest = c.manualRestWorld(); val ms = c.manualSize
        val out = FloatArray(16).also { android.opengl.Matrix.translateM(it, 0, rest, 0, 0f, 0f, if (c.jewel) 30f else -30f) }
        val s = Manual.PAGE_H / ms[1]
        val target = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0); android.opengl.Matrix.translateM(it, 0, Stage.W / 2, Stage.H - 50f - Manual.PAGE_H / 2, 400f)
            if (!c.jewel) android.opengl.Matrix.rotateM(it, 0, 180f, 0f, 1f, 0f); android.opengl.Matrix.scaleM(it, 0, s, s, s) }
        val e = Backdrop.ease(manualK)
        val m = if (e < .3f) Poses.blend(rest, out, Backdrop.ease(e / .3f)) else Poses.blend(out, target, Backdrop.ease((e - .3f) / .7f))
        if (manualStage == 3) android.opengl.Matrix.translateM(m, 0, 0f, 0f, -5000f)   // under the reader: out of sight
        c.manualAt(m)
    }

    /** About as a full article over everything: the game's name and facts, the whole Wikipedia article, its pictures. */
    private fun openArticle() {
        val i = info ?: return Sounds.play(Sounds.BUMP)
        val f = i.fields
        val text = f["article"] ?: f["description"] ?: return Sounds.play(Sounds.BUMP)
        val facts = listOfNotNull(f["releaseyear"], f["developer"], f["publisher"]?.takeIf { it != f["developer"] }, f["genre"])
        val source = if (f["descsource"] == "Wikipedia") "From Wikipedia" + (f["wikititle"]?.let { "’s article “$it”" } ?: "") + ", under CC BY-SA 4.0." else null
        val pics = Meta.pictures(game.sys).filter { it.first != "video" }.mapNotNull { (k, name) -> i.pics[k]?.let { name to meta.bitmap(it, 900) } }
        val v = Article(ctx, game.sys, title.text.toString(), facts, text, source, pics)
        v.onClose = { board.removeView(v); if (article === v) article = null }
        article = v; board.put(v, 0, 0, 1334, 750)
        Sounds.play(Sounds.OPEN)
    }

    // ---------- lists in the panel: shell colors and game settings ----------

    private fun showList(heading: String, rows: List<Options.Row>, at: Int) {
        listHead.text = heading.uppercase()
        list.rows = rows; list.reset(); if (at > 0) list.focus(at)
        scroll.visibility = View.GONE; listHead.visibility = View.VISIBLE; list.visibility = View.VISIBLE
        list.invalidate(); panel.requestLayout()
    }
    private fun hideList() {
        list.onFocus = null
        scroll.visibility = View.VISIBLE; listHead.visibility = View.GONE; list.visibility = View.GONE
        panel.requestLayout(); mode = Mode.PAGE
    }

    // ---------- Customize shell: the console's real colors, your Studio shells, and + Add new ----------

    /** Opens Customize shell straight away (Y on Home). */
    fun customize() = startColors(Mode.PAGE)

    private fun startColors(from: Mode, focusKey: String? = null) {
        if (carts.styles[game.sys] == null) return
        if (mode != Mode.COLORS) { colorsFrom = from; before = carts.variantFor(game.sys, carts.shellFor(games, game)); finishPick = games.ownFinish(game) ?: -1 }
        mode = Mode.COLORS
        fillColors(focusKey ?: list.current()?.tag as? String ?: before?.key)
        Sounds.play(Sounds.OPEN)
    }

    private fun fillColors(focusKey: String?) {
        val style = carts.styles[game.sys] ?: return
        val chosen = carts.shellFor(games, game) ?: style.variants[0].name
        val rows = ArrayList<Options.Row>()
        rows += Options.Row("finish", "Label finish", value = "‹  ${finishName(finishPick)}  ›", sub = "How the label looks: paper, glossy, worn, holo…")
        rows += Options.Row(Options.HEAD, "Defaults for ${Games.NAMES[game.sys] ?: style.name}")
        style.variants.forEach { rows += Options.Row("v", it.name, sub = it.games.ifEmpty { "A color it came in" }, check = it.key == chosen, dot = it.color, tag = it.key) }
        rows += Options.Row(Options.HEAD, "Custom")
        carts.shells.all.forEach { rows += Options.Row("c", it.name, sub = Materials.of(it.material).name + "  ·  Y to edit", check = it.key == chosen, dot = it.color, tag = it.key) }
        rows += Options.Row("new", "+ Add new", sub = "Make your own in the Studio", tag = "new")
        list.onFocus = null
        showList("Customize shell", rows, 0)
        rows.indexOfFirst { it.tag == focusKey || it.key == focusKey }.takeIf { it >= 0 }?.let { list.focus(it) }
        list.onFocus = { preview(); Sounds.play(Sounds.MOVE) }
    }

    // the label finish chosen in Customize shell: -1 is the default (Settings), else one of Labels.NAMES
    private var finishPick = -1
    private val finishShown get() = if (finishPick < 0) games.labelFinish else finishPick
    private fun finishName(f: Int) = if (f < 0) "Default (${Labels.NAMES[games.labelFinish]})" else Labels.NAMES[f]
    private fun colorsInput(a: String) {
        if (list.current()?.key == "finish" && (a == "left" || a == "right" || a == "a")) {
            finishPick = (finishPick + 1 + (if (a == "left") Labels.NAMES.size else 1)) % (Labels.NAMES.size + 1) - 1
            carts.refinish(cart, game.title, finishShown); vYaw += .9f   // turned a little, so the new finish catches the light
            Sounds.play(Sounds.MOVE); fillColors("finish"); return
        }
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "a" -> if (list.current()?.key == "new") openStudio(null) else { Sounds.play(Sounds.PICK); endColors(keep = true) }
            "b" -> { Sounds.play(Sounds.CLOSE); endColors(keep = false) }
            "y" -> carts.shells.get(list.current()?.tag as? String)?.let { shellOptions(it) }
            "x" -> flip()
        }
    }

    /** Y on one of your shells: edit it in the Studio, or delete it. */
    private fun shellOptions(s: Shell) {
        host.pick(Picker(ctx, s.name, listOf(Picker.Row("Edit in the Studio", Materials.of(s.material).name), Picker.Row("Delete", "Cartridges using it go back to their console's default"))) { k ->
            if (k == 0) openStudio(s)
            else {
                carts.shells.delete(s.id)
                if (games.variant(game) == s.key) games.setVariant(game, carts.styles[game.sys]!!.variants[0].name)
                if (before?.key == s.key) before = carts.styles[game.sys]!!.variants[0]
                fillColors(null); preview(); host.show("Deleted ${s.name}")
            }
        })
    }

    private fun preview() {
        val key = list.current()?.tag as? String ?: return
        if (key == "new") return   // keeps showing the last one you were on
        carts.variantFor(game.sys, key)?.let { setVariant(it) }
    }

    private fun setVariant(v: Variant) {
        val c = cart
        if (c == null || (v.clear != null) != (c.variant.clear != null)) {   // clear shells are a different model: swap it in place
            c?.destroy(); cart = carts.make(game.sys, game.title, v); carts.refinish(cart, game.title, finishShown); host.cartChanged(game, cart)   // by the shell itself: one still being made in the Studio has no saved name to look up
        } else c.apply(v)
    }

    private fun endColors(keep: Boolean) {
        // the finish is kept with the shell (A) or put back (B)
        if (keep) games.setFinish(game, finishPick.takeIf { it >= 0 }) else finishPick = games.ownFinish(game) ?: -1
        carts.refinish(cart, game.title, games.finishFor(game))
        val key = (list.current()?.tag as? String)?.takeIf { it != "new" && it != "finish" }
        val picked = if (keep) key?.let { carts.variantFor(game.sys, it) } else null
        if (picked != null) { games.setVariant(game, picked.key); if (picked.sheen || picked.clear != null) vYaw += 1.6f }   // show off the new finish
        else before?.let { setVariant(it) }
        hideList(); refresh()
        if (colorsFrom == Mode.SETTINGS) startSettings(at = 1)
    }

    // ---------- the Studio ----------

    private var studio: StudioView? = null
    private val studioPanel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = round(Theme.panel, 33f, Theme.line); setPadding(34, 28, 34, 20); clipToPadding = false; alpha = 0f }
    private var editing: Shell? = null
    private var studioK = 0f   // 1 while the Studio is open

    /** Opens the Studio, starting from what's on the cartridge now (or from the shell you're editing). */
    private fun openStudio(edit: Shell?) {
        val start = edit?.variant() ?: cart?.variant ?: carts.styles[game.sys]?.variants?.get(0) ?: return
        val startMaterial = start.material ?: when { start.clear == "transparent" -> "clear"; start.clear == "frosted" -> "frosted"; start.sheen -> "metal"; else -> "plastic" }
        // a real gold or silver shell starts from the color that, as Studio metal, is the same measured gold or silver
        val startColor = if (start.material == null && start.sheen) Color.parseColor(if (Color.red(start.color) - Color.blue(start.color) > 60) "#ffdb74" else "#f3f1ea") else start.color
        editing = edit
        val v = StudioView(ctx, startColor, startMaterial, start.name)
        studio = v
        studioPanel.removeAllViews()
        studioPanel.addView(head("STUDIO  ·  ${(carts.styles[game.sys]?.name ?: "").uppercase()} CARTRIDGE"))
        studioPanel.addView(text(34f, Fonts.display, Theme.fg).apply { this.text = edit?.name ?: "New shell"; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, lp(10))
        studioPanel.addView(v, LinearLayout.LayoutParams(StudioView.W, StudioView.H).apply { topMargin = 16; leftMargin = -12 })
        if (studioPanel.parent == null) board.put(studioPanel, 694, 112, 610, 620)
        mode = Mode.STUDIO
        v.onChange = { color, material -> setVariant(Shell("", "", color, material).variant()) }
        v.onCancel = { closeStudio(saved = null) }
        v.onSave = {
            val m = Materials.of(v.material.id)
            host.askText("Name your shell", edit?.name ?: "${if (m.id == "plastic") "" else m.name + " "}${v.colorName}".trim()) { name ->
                val s = carts.shells.save(Shell(edit?.id ?: java.lang.Long.toString(System.currentTimeMillis(), 36), name.ifBlank { v.colorName }.trim().take(28), v.color, v.material.id))
                Sounds.play(Sounds.PICK); closeStudio(saved = s)
            }
        }
        Sounds.play(Sounds.OPEN)
    }

    private fun closeStudio(saved: Shell?) {
        studio = null; mode = Mode.COLORS
        if (saved == null) Sounds.play(Sounds.CLOSE)
        fillColors(saved?.key ?: editing?.key ?: "new")
        if (saved == null && editing == null) before?.let { setVariant(it) } else preview()   // cancelled: back to what it was
        if (saved != null) host.show("Saved ${saved.name}. It’s in Custom for every cartridge.")
        editing = null
    }

    private fun startSettings(at: Int = 0) {
        mode = Mode.SETTINGS
        fillSettings(at)
        Sounds.play(Sounds.OPEN)
    }

    /** Fills the settings list, staying on the row you were on (or on row `start`). */
    private fun fillSettings(start: Int = -1) {
        val console = Games.NAMES[game.sys] ?: game.sys
        val own = games.ownEmulator(game)
        val rows = listOf(
            Options.Row("emu", "Plays with", value = own?.let { emuName(it).substringBefore(",") } ?: "Same as $console", sub = games.emulatorFor(game)?.let { emuName(it) } ?: "Nothing chosen yet"),
            carts.variantFor(game.sys, carts.shellFor(games, game)).let { v -> Options.Row("shell", "Customize shell", value = v?.name ?: "", dot = v?.color) },
            Options.Row("home", "Show on Home", value = if (games.onHome(game)) "Yes" else "No", sub = "Off: it stays in the Library, out of your recent games"),
            Options.Row("hidegame", "Hide this game", sub = "Gone from Home and the Library until you open Library › Hidden with your code"),
            Options.Row("media", "Media", value = Media.kindsFor(game.sys).count { !Media.has(info, it.id) }.let { if (it == 0) "Complete" else "$it missing" },
                sub = "Box art, pictures, video, details and manual, and where they’re from"),
            Options.Row("done", "Done", primary = true))
        val at = if (start >= 0) start else rows.indexOfFirst { it.key == list.current()?.key }.coerceAtLeast(0)
        list.onFocus = null
        showList("Game settings", rows, at)
        list.onFocus = { Sounds.play(Sounds.MOVE) }
    }

    private fun settingsInput(a: String) {
        when (a) {
            "up" -> if (!list.move(-1)) Sounds.play(Sounds.BUMP)
            "down" -> if (!list.move(1)) Sounds.play(Sounds.BUMP)
            "b" -> { Sounds.play(Sounds.CLOSE); hideList() }
            "x" -> flip()
            "a" -> when (list.current()?.key) {
                "emu" -> { Sounds.play(Sounds.PICK); chooseEmulator(then = false) }
                "shell" -> { hideList(); startColors(Mode.SETTINGS) }
                "home" -> { games.setOnHome(game, !games.onHome(game)); homeChanged = true; Sounds.play(Sounds.PICK); fillSettings() }
                "hidegame" -> host.hideGame(game) { homeChanged = true; close() }
                "media" -> openMedia()
                "done" -> { Sounds.play(Sounds.CLOSE); hideList() }
            }
        }
    }

    private fun openMedia() {
        Sounds.play(Sounds.OPEN)
        val v = MediaPage(ctx, game, title.text.toString(), meta, games, ctx.cacheDir, object : MediaPage.Host {
            override fun changeMatch() = whichGame()
            override fun chooseFile(kind: String, then: (Boolean) -> Unit) {
                val mimes = when (kind) { "video" -> arrayOf("video/*"); "manual" -> arrayOf("application/pdf"); else -> arrayOf("image/*") }
                host.openDocument(mimes) { uri -> if (uri == null) return@openDocument then(false)
                    Thread { val ok = Backup.adopt(ctx, uri, game, kind, meta); board.post { then(ok) } }.start() }
            }
            override fun show(msg: String) = host.show(msg)
            override fun changed() { info = meta.cached(game); refresh(); if (mode == Mode.SETTINGS) fillSettings() }
        })
        v.onClose = { board.removeView(v); if (media === v) media = null }
        media = v; board.put(v, 0, 0, 1334, 750)
    }

    private fun whichGame() {
        Sounds.play(Sounds.PICK)
        host.show("Looking through libretro’s list…")
        meta.loadCandidates(game) { names ->
            if (closing) return@loadCandidates
            if (names.isEmpty()) return@loadCandidates host.show("Couldn’t get libretro’s list. Check the internet connection.")
            val rows = listOf(Picker.Row("Let Shelf find it", "By the file’s checksum, or the closest name")) + names.map { Picker.Row(ArtIndex.stripTags(it).trim(), it) }
            host.pick(Picker(ctx, "Which game is this?", rows, start = names.indexOf(games.match(game)) + 1) { k ->
                games.setMatch(game, if (k == 0) null else names[k - 1]); lookUpAgain(keepMatch = true)
            })
        }
    }

    private fun lookUpAgain(keepMatch: Boolean) {
        if (!keepMatch) games.setMatch(game, null)
        meta.forget(game); info = null; refresh(); if (mode == Mode.SETTINGS) fillSettings()
        host.show("Looking it up…")
        meta.get(game, again = true) { i -> if (closing) return@get; info = i; refresh(); if (mode == Mode.SETTINGS) fillSettings(); media?.matched(i)
            host.show(if (i?.name != null) "Found ${ArtIndex.stripTags(i.name).trim()}" else "Couldn’t find it. Try Which game is this?") }
    }

    // ---------- the box ----------

    private fun setView(v: Int) {
        val to = (v + 3) % 3
        if (case == null && to != 0) return Sounds.play(Sounds.BUMP)   // a console with no box made for it yet
        view = to; idle = 9f; vYaw = 0f
        if (zone == Zone.BOX) { zone = Zone.TILES; tiles.active = true; tiles.invalidate() }   // the model chosen has gone; back to the tiles
        fillHint()
        Sounds.play(Sounds.MOVE)
    }

    /**
     * The box and the cartridge, by how far along the flow is. The open box (its cover lying to the left, so it sits right
     * of the cartridge's spot to stay centred) at most 400 across, slid in from off the left edge; the cartridge from
     * where it floats to just over the tray, then down into it; the cover shut. Closed, it grows (up to 500 tall) to fill
     * the left half, so the box art shows big.
     */
    private fun placeBox(x: Float, y: Float, ry: Float, rx: Float, ik: Float = 0f) {
        val c = case ?: return
        if (flow.s < .001f) { c.hide(); return }
        val shut = flow.closed
        c.open = 1 - shut
        val big = 2 * maxOf(c.half[0], c.half[1])
        val open = min(400f / big, 300f / (2 * c.half[0]))
        val s = lerp(open, maxOf(open, min(500f / (2 * c.half[1]), 440f / (2 * c.half[0]))), shut) *
            // looking closely at the closed box: as big as the screen allows, then zoomed; chosen, it comes forward a touch
            lerp(1f, min(640f / (2 * c.half[1]), 1000f / (2 * c.half[0])) / min(500f / (2 * c.half[1]), 440f / (2 * c.half[0])) * inspZoom, ik * shut) *
            (1 + .035f * pickFocus * shut) *
            // from a box on Home: as big as it was there to begin with (and closing, back to that)
            (if (fromBox) lerp(from[2] / big / maxOf(.001f, min(500f / (2 * c.half[1]), 440f / (2 * c.half[0]))), 1f, Backdrop.ease(t)) else 1f)
        val shift = maxOf(0f, -kotlin.math.cos(PI.toFloat() * .97f * c.open)) * c.half[0] * s * kotlin.math.cos(ry)
        val at = x + shift
        val bx = at - (1 - flow.boxIn) * (at + 4 * c.half[0] * s + 40f)   // off the left edge, the open cover and all
        c.place(bx, y, 50f, ry, rx, s * big)
        if (launching) return
        val ct = cart ?: return
        val tray = c.mediaMatrix()
        val hover = c.hoverMatrix(ct.style.h, ct.style.depth)
        val m = if (flow.s <= 1f) Poses.blend(ct.placed, hover, flow.boxIn) else Poses.blend(hover, tray, flow.down)
        if (pickFocus > .001f && view == 1) android.opengl.Matrix.translateM(m, 0, 0f, 0f, 5f * pickFocus)   // chosen in its tray: lifted a little
        ct.placeMatrix(m)
    }

    /** Under the cartridge: what the triggers show, and the turning keys. */
    private fun fillHint() {
        hint.removeAllViews()
        if (inspecting) {
            hint.addView(key("L1 R1", "Zoom")); hint.addView(key("R", "Turn").apply { setPadding(26, 0, 0, 0) })
            hint.addView(key("L", "Move").apply { setPadding(26, 0, 0, 0) }); hint.addView(key("B", "Back").apply { setPadding(26, 0, 0, 0) }); return
        }
        if (zone == Zone.BOX) {
            hint.addView(key("A", when { pick == 1 -> "Read the manual"; view == 1 -> "Start the game"; view == 2 -> "Look at the box"; else -> "Look closer" }))
            if (pick == 0 && manualInBox()) hint.addView(key("Left", "The manual").apply { setPadding(26, 0, 0, 0) })
            hint.addView(key("Right", "Back to the page").apply { setPadding(26, 0, 0, 0) }); return
        }
        if (case != null) hint.addView(key("L2 R2", PAGE_VIEWS[view]))
        hint.addView(key("R", "Turn it").apply { setPadding(if (case != null) 26 else 0, 0, 0, 0) })
        hint.addView(key("X", "Flip it over").apply { setPadding(26, 0, 0, 0) })
    }

    fun destroy() {
        backdrop.shade = 0f
        glow?.destroy()
        markedCart?.let { Outline.mark(stage, it.asset.entities, 0) }   // the cartridge goes back to Home
        case?.destroy(); tool?.destroy()
        board.removeView(panel); board.removeView(hint); board.removeView(studioPanel); viewer?.let { board.removeView(it) }; article?.let { board.removeView(it) }; media?.let { board.removeView(it) }; player?.let { board.removeView(it) }; reader?.let { board.removeView(it) }
        stage.turnKey(0f)
        shots.recycle()
    }

    // ---------- helpers ----------

    private fun dead(v: Float) = if (abs(v) < .18f) 0f else (v - .18f * Math.signum(v)) / .82f
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun wrap(a: Float): Float { var x = a % (2 * PI.toFloat()); if (x > PI) x -= 2 * PI.toFloat(); if (x < -PI) x += 2 * PI.toFloat(); return x }
    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }
    private fun head(s: String) = text(18f, Fonts.bold, Theme.accentOf(null)).apply { letterSpacing = .1f; this.text = s }
    private fun text(size: Float, face: android.graphics.Typeface, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size); typeface = face; setTextColor(color); includeFontPadding = false
    }
    private fun key(btn: String, say: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(18f, Fonts.bold, Theme.fg).apply { this.text = btn; gravity = Gravity.CENTER; background = round(Color.TRANSPARENT, 16f, Theme.fg3, 1.5f); minWidth = 33; setPadding(8, 5, 8, 5) })
        addView(text(21f, Fonts.body, Theme.fg2).apply { this.text = say; setPadding(10, 0, 0, 0) })
    }
}

/**
 * The game page's row of square icon tiles, each with its name underneath; the focused one fills in and lifts.
 * Drawn inset from its own edges (INSET), so the focus ring around the end tiles is never cut off.
 */
class Tiles(ctx: Context) : View(ctx) {
    companion object { const val INSET = 10; const val HEIGHT = 138; private const val SIZE = 76f; private const val TOP = 12f }
    private val names = listOf("Play", "Emulator", "Customize", "Collections", "Settings", "Back")
    var at = 0; private set
    var active = true
    var fav = false
    var swatch = Color.WHITE
    var onMove: (() -> Unit)? = null
    private val lift = FloatArray(names.size)
    private var last = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun move(d: Int): Boolean { val k = (at + d).coerceIn(0, names.size - 1); if (k == at) return false; at = k; onMove?.invoke(); invalidate(); return true }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); val dt = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f); last = now
        val inner = width - 2f * INSET; val gap = (inner - SIZE * names.size) / (names.size - 1)
        var moving = false
        names.forEachIndexed { k, name ->
            val on = k == at && active
            val want = if (on) 1f else 0f
            lift[k] += (want - lift[k]) * min(1f, if (dt == 0f) 1f else dt * 14f); if (abs(want - lift[k]) > .01f) moving = true
            val x = INSET + k * (SIZE + gap); val y = TOP - lift[k] * 5f
            val box = RectF(x, y, x + SIZE, y + SIZE)
            p.style = Paint.Style.FILL
            p.color = when { on -> Theme.sel; k == 0 -> Theme.accentOf(null); else -> Theme.chip }
            c.drawRoundRect(box, 24f, 24f, p)
            if (on) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 5, box.top - 5, box.right + 5, box.bottom + 5), 29f, 29f, p) }
            else if (k == at) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.fg3; c.drawRoundRect(box, 24f, 24f, p) }   // where you'll be when you come back up
            val ink = if (on || k == 0) Theme.selFg else Theme.fg
            c.save(); c.translate(x + SIZE / 2 - 17, y + SIZE / 2 - 17); icon(c, k, ink); c.restore()
            p.style = Paint.Style.FILL; p.typeface = if (on) Fonts.bold else Fonts.body; p.textSize = 16.5f; p.color = if (on) Theme.fg else Theme.fg2
            p.textAlign = Paint.Align.CENTER; c.drawText(name, x + SIZE / 2, TOP + SIZE + 30f, p); p.textAlign = Paint.Align.LEFT
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
            3 -> {   // collections: three cards fanned, the front one with a heart when it's a favorite
                p.style = Paint.Style.STROKE
                c.save(); c.rotate(-12f, 17f, 30f); c.drawRoundRect(RectF(7f, 6f, 23f, 28f), 3f, 3f, p); c.restore()
                c.save(); c.rotate(12f, 17f, 30f); c.drawRoundRect(RectF(11f, 6f, 27f, 28f), 3f, 3f, p); c.restore()
                p.style = Paint.Style.FILL; p.color = if (k == at && active) Theme.sel else Theme.chip; c.drawRoundRect(RectF(9f, 5f, 25f, 29f), 3f, 3f, p)
                p.color = ink; p.style = Paint.Style.STROKE; c.drawRoundRect(RectF(9f, 5f, 25f, 29f), 3f, 3f, p)
                if (fav) { p.style = Paint.Style.FILL; c.drawPath(Path().apply { moveTo(17f, 22f); cubicTo(10f, 17f, 11f, 11f, 14.5f, 11f); cubicTo(16f, 11f, 17f, 12.5f, 17f, 13.5f)
                    cubicTo(17f, 12.5f, 18f, 11f, 19.5f, 11f); cubicTo(23f, 11f, 24f, 17f, 17f, 22f); close() }, p) } }
            4 -> { p.style = Paint.Style.STROKE   // three sliders
                for ((j, kx) in listOf(10f, 22f, 14f).withIndex()) { val y = 9f + j * 8; c.drawLine(4f, y, 30f, y, p); p.style = Paint.Style.FILL; c.drawCircle(kx, y, 3.4f, p); p.style = Paint.Style.STROKE } }
            else -> { p.style = Paint.Style.STROKE; c.drawLine(8f, 17f, 27f, 17f, p); c.drawLine(8f, 17f, 15f, 10f, p); c.drawLine(8f, 17f, 15f, 24f, p) }
        }
    }
}

/** The game page's pictures: a row of rounded thumbnails, with a soft placeholder while they download. */
class Shots(ctx: Context) : View(ctx) {
    companion object { const val INSET = 10; const val HEIGHT = 168; private const val H = 118f; private const val TOP = 10f }
    private var pics: List<Pair<String, Bitmap>>? = null   // null: still looking
    val count get() = pics?.size ?: 0
    var at = 0; private set
    var active = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f; private var last = 0L

    fun set(list: List<Pair<String, Bitmap>>?) { recycle(); pics = list; at = at.coerceIn(0, maxOf(0, count - 1)); invalidate() }
    fun recycle() { pics?.forEach { it.second.recycle() }; pics = null }
    fun move(d: Int): Boolean { val k = (at + d).coerceIn(0, maxOf(0, count - 1)); if (k == at) return false; at = k; invalidate(); return true }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); time += if (last == 0L) 0f else (now - last) / 1e9f; last = now
        val list = pics
        p.typeface = Fonts.body; p.textSize = 16.5f
        if (list == null || list.isEmpty()) {
            // three soft tiles breathing while the pictures download, or a plain line if there are none
            if (list == null) for (k in 0 until 3) { p.color = Color.argb((22 + 14 * sin(time * 2.4f - k * .7f)).toInt().coerceAtLeast(0), 238, 241, 255)
                c.drawRoundRect(RectF(INSET + k * 172f, TOP, INSET + k * 172f + 160f, TOP + H), 18f, 18f, p) }
            p.color = Theme.fg3; c.drawText(if (list == null) "Looking for pictures…" else "No pictures found for this game.", INSET.toFloat() + 4, TOP + (if (list == null) H + 32f else 40f), p)
            if (list == null) postInvalidateOnAnimation() else last = 0L
            return
        }
        last = 0L
        var x = INSET.toFloat()
        list.forEachIndexed { k, (name, b) ->
            val w = (H * b.width / b.height).coerceIn(80f, 220f)
            val box = RectF(x, TOP, x + w, TOP + H)
            val m = Matrix(); val s = maxOf(w / b.width, H / b.height); m.setScale(s, s); m.postTranslate(x + (w - b.width * s) / 2, TOP + (H - b.height * s) / 2)
            p.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            p.alpha = if (active && k != at) 170 else 255; c.drawRoundRect(box, 16f, 16f, p); p.shader = null; p.alpha = 255
            if (active && k == at) { p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Theme.sel; c.drawRoundRect(RectF(box.left - 5, box.top - 5, box.right + 5, box.bottom + 5), 21f, 21f, p); p.style = Paint.Style.FILL }
            p.color = if (active && k == at) Theme.fg else Theme.fg2; p.typeface = if (active && k == at) Fonts.bold else Fonts.body
            c.drawText(name, x + 2, TOP + H + 30f, p)
            x += w + 18
        }
    }
}

/** A picture over everything, as big as it fits: left and right go through the game's pictures, B closes. */
class Viewer(ctx: Context, private val names: List<String>, private val pics: List<Bitmap?>, start: Int, private val game: String) : View(ctx) {
    var onClose: (() -> Unit)? = null
    private var at = start.coerceIn(0, names.size - 1)
    private var shown = 0f            // fades in when opened, out when closed
    private var closing = false
    private var slide = 0f            // eases to 0 after moving: the new picture glides in from the side
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun input(a: String): Boolean {
        when (a) {
            "left" -> if (at > 0) { at--; slide = -1f; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "right" -> if (at < names.size - 1) { at++; slide = 1f; Sounds.play(Sounds.MOVE) } else Sounds.play(Sounds.BUMP)
            "b", "a" -> { closing = true; Sounds.play(Sounds.CLOSE) }
        }
        invalidate(); return true
    }

    fun tick(dt: Float) {
        shown = if (closing) maxOf(0f, shown - dt / .2f) else min(1f, shown + dt / .25f)
        slide *= exp(-dt * 12f)
        if (closing && shown == 0f) { pics.forEach { it?.recycle() }; onClose?.invoke() }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val k = Backdrop.ease(shown)
        p.color = Color.argb((230 * k).toInt(), 5, 8, 22); c.drawRect(0f, 0f, Stage.W, Stage.H, p)
        val b = pics[at]
        val maxW = 1160f; val maxH = 580f
        if (b != null) {
            val s = min(maxW / b.width, maxH / b.height); val w = b.width * s; val h = b.height * s
            val x = (Stage.W - w) / 2 + slide * 60f; val y = (Stage.H - 50 - h) / 2 + (1 - k) * 20f
            val m = Matrix(); m.setScale(s, s); m.postTranslate(x, y)
            p.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            p.alpha = (255 * k * (1 - abs(slide) * .7f)).toInt(); c.drawRoundRect(RectF(x, y, x + w, y + h), 20f, 20f, p); p.shader = null; p.alpha = 255
        }
        p.typeface = Fonts.bold; p.textSize = 23f; p.color = Theme.fg; p.alpha = (255 * k).toInt(); p.textAlign = Paint.Align.CENTER
        c.drawText(names[at], Stage.W / 2, Stage.H - 60f, p)
        p.typeface = Fonts.body; p.textSize = 19f; p.color = Theme.fg2; p.alpha = (255 * k).toInt()
        c.drawText("$game  ·  ${at + 1} of ${names.size}", Stage.W / 2, Stage.H - 30f, p)
        p.textAlign = Paint.Align.LEFT; p.alpha = 255
    }
}
