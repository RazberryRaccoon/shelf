package com.razberryraccoon.shelf

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.TextureView
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Shelf Native's one screen, in three layers: the backdrop (Android canvas), the 3D objects (Filament, in a transparent
 * TextureView) and the interface (Android views, laid out at 1334 × 750 and scaled to fit). Controller buttons come in
 * here and go to the screen that's showing.
 */
class ShelfActivity : Activity(), Home.Actions, Setup.Host, Museum.Host, Launch.Host {
    private lateinit var stage: Stage
    private lateinit var backdrop: Backdrop
    private lateinit var board: Board
    private lateinit var games: Games
    private lateinit var home: Home
    private lateinit var carts: Carts
    private var setup: Setup? = null
    private var museum: Museum? = null
    private var launch: Launch? = null   // a game starting, being played, or coming back
    private lateinit var meta: Meta
    private lateinit var consoles: Consoles
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // If Shelf Native ever closes on an error in its own code, keep what happened, and show it the next time it opens
        val crashFile = java.io.File(filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText(android.util.Log.getStackTraceString(e)) } catch (_: Exception) { }
            previous?.uncaughtException(t, e)
        }
        // If the last start never got going (or closed on an error), show what happened instead of starting the 3D again
        booting = java.io.File(filesDir, "booting")
        if (booting.exists() || crashFile.exists()) { safeMode = true; showProblem(crashFile); return }
        booting.writeText("")
        // Some emulators still take a plain file:// path; without this Android refuses to hand one over.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        Fonts.load(this)
        val root = FrameLayout(this)
        backdrop = Backdrop(this)
        val surface = TextureView(this).apply { isOpaque = false }
        board = Board(this)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(board, FrameLayout.LayoutParams(Stage.W.toInt(), Stage.H.toInt()))
        setContentView(root)
        immersive()

        stage = Stage(this, surface)
        games = Games(this)
        stage.setLook(games.lightEnv, games.lightExposure, games.lightKey)
        Sounds.init(this, games.sounds)
        carts = Carts(stage, Shells(this))
        meta = Meta(this, games)
        consoles = Consoles(this, stage)
        home = Home(this, stage, board, backdrop, games, carts, meta, this)
        home.refreshPicture()   // Home picture's look from Settings
        if (!games.setupDone) openSetup(settings = false)
        // the interface and backdrop follow the 3D layer's fit, so all three line up
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val s = minOf((r - l) / Stage.W, (b - t) / Stage.H); val ox = ((r - l) - Stage.W * s) / 2; val oy = ((b - t) - Stage.H * s) / 2
            board.fit(s, ox, oy); backdrop.scale = s; backdrop.ox = ox; backdrop.oy = oy
        }
        stage.onFrame = { dt -> home.tick(dt); setup?.tick(dt); museum?.tick(dt); launch?.tick(dt); backdrop.tick(dt); if (++frames == 90) booting.delete() }   // started fine
    }

    // ---------- when the last start went wrong ----------

    private var safeMode = false
    private lateinit var booting: java.io.File
    private var frames = 0

    /** A plain screen (no 3D, no custom fonts) with what went wrong: Shelf's own saved error, or Android's log of it. */
    private fun showProblem(crashFile: java.io.File) {
        val lines = if (crashFile.exists()) crashFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.take(14) else lastErrors()
        val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(64, 48, 64, 48); setBackgroundColor(Theme.bg) }
        fun line(t: String, size: Float, color: Int, mono: Boolean = false) = android.widget.TextView(this).apply {
            text = t; textSize = size; setTextColor(color); if (mono) typeface = android.graphics.Typeface.MONOSPACE; setPadding(0, 6, 0, 6) }
        col.addView(line("Shelf Native closed while starting last time", 22f, Theme.fg))
        col.addView(line("A photo of this screen helps Claude fix it.", 15f, Theme.fg2))
        for (l in lines.ifEmpty { listOf("No details were saved.") }) col.addView(line(l.take(160), 11f, Theme.fg2, mono = true))
        col.addView(line("A  Try again        B  Close", 17f, Theme.fg))
        setContentView(android.widget.ScrollView(this).apply { setBackgroundColor(Theme.bg); addView(col) })
    }

    /** The app's own error lines in Android's log from before this start (Filament's fatal errors land there). */
    private fun lastErrors(): List<String> = try {
        val me = android.os.Process.myPid(); val pid = Regex("\\(\\s*(\\d+)\\)")
        Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "brief", "-t", "800")).inputStream.bufferedReader().readLines()
            .filter { l -> (l.startsWith("E/") || l.startsWith("F/")) && pid.find(l)?.groupValues?.get(1)?.toIntOrNull() != me }.takeLast(14)
    } catch (e: Exception) { listOf("Couldn't read Android's log: ${e.message}") }

    private fun safeInput(a: String) {
        if (a == "a") { booting.delete(); java.io.File(filesDir, "last-crash.txt").delete(); recreate() }
        else if (a == "b") finish()
    }

    override fun onResume() {
        super.onResume()
        immersive()
        if (safeMode) return
        stage.start()
        home.needsStorage = !Environment.isExternalStorageManager()
        launch?.back()   // back from a game: the return animation
        rescan { setup?.fill() }   // back from Android's settings, a game, or the folder picker
    }

    override fun onPause() {
        if (safeMode) return super.onPause()
        stage.stop()
        held = null; ui.removeCallbacks(repeat)
        super.onPause()
    }

    override fun onStop() {
        if (!safeMode && frames > 0) booting.delete()   // closed normally
        super.onStop()
    }

    override fun onDestroy() {
        if (!safeMode) stage.destroy()
        super.onDestroy()
    }

    private var scanning = false
    private val afterScan = ArrayList<() -> Unit>()
    override fun rescan(then: () -> Unit) {
        afterScan.add(then)
        if (scanning) return
        scanning = true
        Thread {
            games.scan()
            val list = games.recent()
            runOnUiThread {
                scanning = false
                if (setup == null) home.setGames(list)
                val done = afterScan.toList(); afterScan.clear(); done.forEach { it() }
            }
        }.start()
    }

    // ---------- setup and settings ----------

    private fun openSetup(settings: Boolean) {
        home.visible = false
        setup = Setup(this, stage, board, backdrop, games, carts, this, settings)
    }
    override fun openSettings() = openSetup(settings = true)

    // ---------- the Museum ----------

    override fun openMuseum(sys: String?) {
        if (museum != null) return
        home.visible = false
        museum = Museum(this, stage, board, backdrop, games, carts, this, sys)
    }
    override fun pick(p: Picker) = home.pick(p)   // over the Museum too: input goes to a message first
    override fun closed(museum: Museum, shellsChanged: Boolean) {
        this.museum = null
        if (shellsChanged) home.reshell()
        home.visible = true
    }

    override fun finished(setup: Setup) {
        setup.destroy(); this.setup = null
        if (!games.setupDone) { openSetup(settings = false); return }   // "run first-time setup again"
        home.refreshProfile()
        home.reshell()   // a shell you deleted in Settings comes off the cartridges wearing it
        rescan { home.visible = true }
    }

    override fun hasStorage() = Environment.isExternalStorageManager()
    override fun artChanged() = home.refreshPicture()
    override fun toast(msg: String) = home.show(msg)

    private var folderThen: ((String?) -> Unit)? = null
    override fun pickFolder(then: (String?) -> Unit) {
        if (!hasStorage()) { allowStorage(); return }
        folderThen = then
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER) }
        catch (e: Exception) { folderThen = null; toast("This handheld has no folder picker") }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != PICK_FOLDER) return
        val then = folderThen ?: return; folderThen = null
        if (result != RESULT_OK) return
        then(data?.data?.let { Scanner.treePath(this, it) })
    }

    /** Android's own text box, for typing a name (as the WebView app's Bridge.askText). */
    override fun askText(title: String, value: String, then: (String) -> Unit) {
        val field = android.widget.EditText(this).apply { setSingleLine(true); setText(value); setSelection(text.length) }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ -> then(field.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
        field.requestFocus()
    }

    // ---------- Home's requests ----------

    /** Play: the start animation, which starts the game at its end (start), and plays the way back when Shelf returns. */
    override fun play(g: Game) {
        if (launch != null) return
        val (cart, from, back) = home.takeForLaunch(g)
        launch = Launch(this, stage, board, backdrop, carts, consoles, games, meta, g, cart, from, this, back)
    }
    override fun start(l: Launch): String = games.launch(l.game)
    override fun done(l: Launch) {
        launch = null
        home.landed(l.game)
        if (l.problem.isNotEmpty()) home.show(l.problem)
    }

    override fun allowStorage() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
        catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    // ---------- the screen ----------

    private fun immersive() {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    // ---------- controller and keyboard (as in the WebView app's MainActivity) ----------

    private var held: String? = null
    private var lastRepeat = 0L
    private val repeat = object : Runnable { override fun run() { held?.let { input(it); ui.postDelayed(this, 110) } } }
    /** Buttons go to whichever screen is showing. */
    private fun input(a: String) { if (safeMode) return safeInput(a); launch?.let { return it.input(a) }; if (home.picker != null) home.input(a) else museum?.input(a) ?: setup?.input(a) ?: home.input(a) }   // a game starting takes every button (any one skips); then a message over everything

    private fun actionFor(code: Int) = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"; KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "a"
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "b"
        KeyEvent.KEYCODE_BUTTON_X -> "x"; KeyEvent.KEYCODE_BUTTON_Y -> "y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "lb"; KeyEvent.KEYCODE_BUTTON_R1 -> "rb"
        KeyEvent.KEYCODE_BUTTON_L2 -> "lt"; KeyEvent.KEYCODE_BUTTON_R2 -> "rt"
        else -> null
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val a = actionFor(e.keyCode) ?: return super.dispatchKeyEvent(e)
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) input(a)
            else if (a in DIRECTIONS) { val now = System.currentTimeMillis(); if (now - lastRepeat >= 110) { lastRepeat = now; input(a) } }
        }
        return true   // Shelf handles every one of these, Back included
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val pad = e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK || e.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        if (!pad || e.action != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(e)
        // the right stick turns the cartridge on a game's page
        if (!safeMode && launch == null) { val x = e.getAxisValue(MotionEvent.AXIS_Z); val y = e.getAxisValue(MotionEvent.AXIS_RZ); museum?.stick(x, y) ?: home.stick(x, y) }
        // the left stick, or a D-pad that reports itself as a hat
        var x = e.getAxisValue(MotionEvent.AXIS_HAT_X); var y = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (abs(x) < .5f && abs(y) < .5f) { x = e.getAxisValue(MotionEvent.AXIS_X); y = e.getAxisValue(MotionEvent.AXIS_Y) }
        val dir = if (abs(x) > .55f || abs(y) > .55f) (if (abs(x) > abs(y)) (if (x > 0) "right" else "left") else (if (y > 0) "down" else "up")) else null
        if (dir != held) {
            held = dir; ui.removeCallbacks(repeat)
            if (dir != null) { input(dir); ui.postDelayed(repeat, 380) }
        }
        return true
    }

    companion object { private val DIRECTIONS = setOf("up", "down", "left", "right"); private const val PICK_FOLDER = 7 }
}
