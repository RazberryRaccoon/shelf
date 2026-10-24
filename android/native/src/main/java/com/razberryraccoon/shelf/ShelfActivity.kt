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
class ShelfActivity : Activity(), Home.Actions {
    private lateinit var stage: Stage
    private lateinit var backdrop: Backdrop
    private lateinit var board: Board
    private lateinit var games: Games
    private lateinit var home: Home
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
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
        home = Home(this, stage, board, backdrop, games, Carts(stage), this)
        // the interface and backdrop follow the 3D layer's fit, so all three line up
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val s = minOf((r - l) / Stage.W, (b - t) / Stage.H); val ox = ((r - l) - Stage.W * s) / 2; val oy = ((b - t) - Stage.H * s) / 2
            board.fit(s, ox, oy); backdrop.scale = s; backdrop.ox = ox; backdrop.oy = oy
        }
        stage.onFrame = { dt -> home.tick(dt); backdrop.tick(dt) }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        stage.start()
        home.needsStorage = !Environment.isExternalStorageManager()
        rescan()
    }

    override fun onPause() {
        stage.stop()
        held = null; ui.removeCallbacks(repeat)
        super.onPause()
    }

    override fun onDestroy() {
        stage.destroy()
        super.onDestroy()
    }

    private fun rescan() {
        Thread {
            games.scan()
            val list = games.recent()
            runOnUiThread { home.setGames(list) }
        }.start()
    }

    // ---------- Home's requests ----------

    override fun play(g: Game) {
        val err = games.launch(g)
        if (err.isNotEmpty()) home.show(err)
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
    private val repeat = object : Runnable { override fun run() { held?.let { home.input(it); ui.postDelayed(this, 110) } } }

    private fun actionFor(code: Int) = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"; KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "a"
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "b"
        KeyEvent.KEYCODE_BUTTON_X -> "x"; KeyEvent.KEYCODE_BUTTON_Y -> "y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "lb"; KeyEvent.KEYCODE_BUTTON_R1 -> "rb"
        else -> null
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val a = actionFor(e.keyCode) ?: return super.dispatchKeyEvent(e)
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) home.input(a)
            else if (a in DIRECTIONS) { val now = System.currentTimeMillis(); if (now - lastRepeat >= 110) { lastRepeat = now; home.input(a) } }
        }
        return true   // Shelf handles every one of these, Back included
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val pad = e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK || e.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        if (!pad || e.action != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(e)
        // the left stick, or a D-pad that reports itself as a hat
        var x = e.getAxisValue(MotionEvent.AXIS_HAT_X); var y = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (abs(x) < .5f && abs(y) < .5f) { x = e.getAxisValue(MotionEvent.AXIS_X); y = e.getAxisValue(MotionEvent.AXIS_Y) }
        val dir = if (abs(x) > .55f || abs(y) > .55f) (if (abs(x) > abs(y)) (if (x > 0) "right" else "left") else (if (y > 0) "down" else "up")) else null
        if (dir != held) {
            held = dir; ui.removeCallbacks(repeat)
            if (dir != null) { home.input(dir); ui.postDelayed(repeat, 380) }
        }
        return true
    }

    companion object { private val DIRECTIONS = setOf("up", "down", "left", "right") }
}
