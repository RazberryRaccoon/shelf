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
import android.view.SurfaceView
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Shelf Native's one screen, in two layers: the backdrop and the 3D objects (both drawn by Filament, straight to the
 * screen in a SurfaceView) and the interface (Android views, laid out at 1334 × 750 and scaled to fit). Controller buttons come in
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
    private var library: Library? = null
    private var apps: Apps? = null
    private var fromLibrary = false        // the game playing now was started from the Library (it comes back there)
    private lateinit var cols: Collections
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
        // A crash below Kotlin (in the 3D engine or Android's own code) can't be caught above; Android keeps why the app
        // last stopped, though, so a native crash since the last look is shown the same way
        nativeCrash(crashFile)
        // If the last start never got going (or closed on an error), show what happened instead of starting the 3D again
        booting = java.io.File(filesDir, "booting")
        if (booting.exists() || crashFile.exists()) { safeMode = true; showProblem(crashFile); return }
        booting.writeText("")
        // Some emulators still take a plain file:// path; without this Android refuses to hand one over.
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        Fonts.load(this)
        val root = FrameLayout(this)
        val surface = SurfaceView(this)
        board = Board(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(board, FrameLayout.LayoutParams(Stage.W.toInt(), Stage.H.toInt()))
        setContentView(root)
        immersive()

        stage = Stage(this, surface)
        backdrop = Backdrop(this, stage)
        games = Games(this)
        stage.setLook(games.lightEnv, games.lightBright, games.lightKey)
        stage.quality(games.smoothness); backdrop.lite = games.smoothness == 2; backdrop.smooth = games.smoothness
        Theme.setEnv(games.theme); Theme.timeMode = games.timeOfDay; backdrop.theme(snap = true)
        Sounds.init(this, games.sounds, games.soundPack)
        // menu music and the theme's ambient sounds; Forest's change with the time of day, Stormy's thunder follows its lightning
        Music.init(this, games); Music.day(backdrop.day > .5f)
        backdrop.onDayChange = { Music.day(it) }; backdrop.scenery.onLightning = { Music.lightning() }
        carts = Carts(stage, Shells(this))
        meta = Meta(this, games)
        consoles = Consoles(this, stage)
        cols = Collections(this, games) { meta.root.takeIf { it.isDirectory || it.mkdirs() } }
        Overlays.board = board
        home = Home(this, stage, board, backdrop, games, carts, meta, cols, this)
        home.refreshPicture()   // Home picture's look from Settings
        if (!games.setupDone) openSetup(settings = false)
        // the interface follows the 3D layer's fit, so the two line up
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val s = minOf((r - l) / Stage.W, (b - t) / Stage.H); val ox = ((r - l) - Stage.W * s) / 2; val oy = ((b - t) - Stage.H * s) / 2
            board.fit(s, ox, oy)
        }
        stage.onFrame = { dt -> home.lookK = museum?.lookK ?: 0f; home.tick(dt); setup?.tick(dt); museum?.tick(dt); library?.tick(dt); apps?.tick(dt); launch?.tick(dt); backdrop.tick(dt); if (++frames == 90) booting.delete()   // started fine
            meterLeft -= dt; if (meterLeft <= 0) { meterLeft = .5f; showMeter() } }
    }

    // ---------- when the last start went wrong ----------

    private var safeMode = false
    private lateinit var booting: java.io.File
    private var frames = 0

    /** A plain screen (no 3D, no custom fonts) with what went wrong: Shelf's own saved error, or Android's log of it. */
    /** Android's record of how Shelf last stopped: a native crash not seen before is written where crashes are kept. */
    private fun nativeCrash(crashFile: java.io.File) {
        try {
            val am = getSystemService(android.app.ActivityManager::class.java) ?: return
            val last = am.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return
            val prefs = getSharedPreferences("shelf", MODE_PRIVATE)
            if (last.timestamp <= prefs.getLong("exit-seen", 0L)) return
            prefs.edit().putLong("exit-seen", last.timestamp).apply()
            if (last.reason != android.app.ApplicationExitInfo.REASON_CRASH_NATIVE || crashFile.exists()) return
            val lines = ArrayList<String>()
            lines += "Native crash (in the 3D engine or Android): ${last.description ?: "no description"}"
            lines += "At ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last.timestamp))}"
            lines += lastErrors()   // Android's log from then, where it still has it (the native backtrace's first lines)
            crashFile.writeText(lines.joinToString("\n"))
        } catch (_: Exception) { }
    }

    private fun showProblem(crashFile: java.io.File) {
        val lines = if (crashFile.exists()) crashFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.take(14) else lastErrors()
        val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(64, 48, 64, 48); setBackgroundColor(Theme.bg) }
        fun line(t: String, size: Float, color: Int, mono: Boolean = false) = android.widget.TextView(this).apply {
            text = t; textSize = size; setTextColor(color); if (mono) typeface = android.graphics.Typeface.MONOSPACE; setPadding(0, 6, 0, 6) }
        col.addView(line(if (crashFile.exists()) "Shelf Native closed on an error last time" else "Shelf Native closed while starting last time", 22f, Theme.fg))
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
        Music.active(true)
        home.needsStorage = !Environment.isExternalStorageManager()
        launch?.back()   // back from a game: the return animation
        apps?.back()     // back from an app: Shelf eases in again
        rescan { setup?.fill() }   // back from Android's settings, a game, or the folder picker
    }

    override fun onPause() {
        if (safeMode) return super.onPause()
        stage.stop(); Music.active(false)
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
                if (setup == null) home.refreshRow()   // its tab's games: the recent ones, or a pinned collection's
                val done = afterScan.toList(); afterScan.clear(); done.forEach { it() }
            }
        }.start()
    }

    // ---------- setup and settings ----------

    private fun openSetup(settings: Boolean) {
        home.visible = false
        setup = Setup(this, stage, board, backdrop, games, carts, this, settings, meta)
    }
    override fun openSettings() = openSetup(settings = true)

    // ---------- the Museum ----------

    override fun openMuseum(sys: String?) {
        if (museum != null) return
        home.visible = false
        museum = Museum(this, stage, board, backdrop, games, carts, this, sys)
    }
    override fun pick(p: Picker) = home.pick(p)   // over the Museum too: input goes to a message first

    // ---------- the Library ----------

    override fun openLibrary() {
        if (library != null) return
        home.visible = false
        library = Library(this, stage, board, backdrop, games, carts, meta, cols, object : Library.Host {
            override fun play(g: Game) = this@ShelfActivity.play(g)
            override fun pick(p: Picker) = home.pick(p)
            override fun askText(title: String, value: String, then: (String) -> Unit) = this@ShelfActivity.askText(title, value, then)
            override fun openDocument(mimes: Array<String>, then: (android.net.Uri?) -> Unit) = this@ShelfActivity.openDocument(mimes, then)
            override fun show(msg: String) = home.show(msg)
            override fun closed(library: Library, changed: Boolean) {
                if (this@ShelfActivity.library === library) this@ShelfActivity.library = null
                if (changed) home.refreshRow()   // hidden games, plays, pins
                pageClosed()
            }
        })
    }
    // ---------- Apps ----------

    override fun openApps() {
        if (apps != null) return
        home.visible = false
        apps = Apps(this, board, backdrop, games, object : Apps.Host {
            override fun show(msg: String) = home.show(msg)
            override fun gamesChanged() = home.refreshRow()
            override fun openApp(pkg: String): Boolean = try {
                val i = packageManager.getLaunchIntentForPackage(pkg) ?: throw IllegalStateException("no launcher")
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            } catch (e: Exception) { false }
            override fun appInfo(pkg: String) {
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) } catch (e: Exception) { home.show("Android's app info isn't available here") }
            }
            override fun closed(apps: Apps) { if (this@ShelfActivity.apps === apps) this@ShelfActivity.apps = null; pageClosed() }
        })
    }

    override fun collectionsCard(g: Game, changed: (Boolean) -> Unit) = GameCard.show(this, games, cols, g, ::askText, home::show, changed)
    override fun hideGame(g: Game, then: () -> Unit) = GameCard.hide(this, cols, g, home::show, then)
    override fun collectionNames(g: Game) = cols.of(g).map { it.name }
    override fun collections() = cols
    override fun closed(museum: Museum, shellsChanged: Boolean) {
        this.museum = null
        if (shellsChanged) home.reshell()
        pageClosed()
    }

    override fun finished(setup: Setup) {
        setup.destroy(); this.setup = null
        if (!games.setupDone) { openSetup(settings = false); return }   // "run first-time setup again"
        home.refreshProfile()
        home.reshell()   // a shell you deleted in Settings comes off the cartridges wearing it
        val next = nextPage
        if (next != null) { rescan { }; pageClosed() } else rescan { home.visible = true }
    }

    override fun hasStorage() = Environment.isExternalStorageManager()
    override fun isHomeApp() = try { packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == packageName } catch (e: Exception) { false }
    override fun chooseHomeApp() {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (e2: Exception) { toast("Choose a home app in Android's Settings › Apps › Default apps") } }
    }
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
    // Android's own pickers: a file to open (a backup, a picture for a game), or a new file to write (a backup)
    private var docThen: ((android.net.Uri?) -> Unit)? = null
    override fun openDocument(mimes: Array<String>, then: (android.net.Uri?) -> Unit) {
        docThen = then
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if (mimes.size == 1) mimes[0] else "*/*").putExtra(Intent.EXTRA_MIME_TYPES, mimes), PICK_DOC) }
        catch (e: Exception) { docThen = null; toast("This handheld has no file picker") }
    }
    override fun createDocument(name: String, mime: String, then: (android.net.Uri?) -> Unit) {
        docThen = then
        try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name), MAKE_DOC) }
        catch (e: Exception) { docThen = null; toast("This handheld has no file picker") }
    }
    /** The media folder in Android's Files app (or, failing that, the folder picker opened there). */
    override fun openMediaFolder() {
        val doc = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Shelf/Media")
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(doc, android.provider.DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(view) }
        catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, doc)) }
            catch (e2: Exception) { toast("Open a file manager and go to Shelf › Media") }
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == PICK_DOC || request == MAKE_DOC) { val then = docThen ?: return; docThen = null; then(if (result == RESULT_OK) data?.data else null); return }
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
        // from a page with its box out: the console waits while the box slides away behind the lifted cartridge
        val lead = if (library?.boxOut(g) ?: home.boxOut(g)) .55f else 0f
        val fromLib = library?.takeForLaunch(g)
        fromLibrary = fromLib != null
        val (cart, from, back) = fromLib ?: home.takeForLaunch(g)
        launch = Launch(this, stage, board, backdrop, carts, consoles, games, meta, g, cart, from, this, back, lead)
        Music.active(false, 1.6f)   // the music fades out as the game goes in
    }
    // Settings › Show frame rate: the meter, over everything in the top left corner
    private var meterLeft = 0f
    private var meter: android.widget.TextView? = null
    // Android's own timings for each frame it puts together (since 0.20 only the panels; the backdrop and the 3D go straight to the screen):
    // what the meter's own numbers can't see. Gathered on their own thread, summed each half second.
    private val fmThread by lazy { android.os.HandlerThread("Shelf frame metrics").also { it.start() } }
    private var fmOn = false
    private val fm = LongArray(6)   // frames, total ns, draw ns, issue ns, swap ns, frames over 16.7 ms
    private var fmWorst = 0L
    private val fmListener = android.view.Window.OnFrameMetricsAvailableListener { _, m, _ ->
        val total = m.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
        synchronized(fm) {
            fm[0]++; fm[1] += total; fm[2] += m.getMetric(android.view.FrameMetrics.DRAW_DURATION)
            fm[3] += m.getMetric(android.view.FrameMetrics.COMMAND_ISSUE_DURATION); fm[4] += m.getMetric(android.view.FrameMetrics.SWAP_BUFFERS_DURATION)
            if (total > 16_700_000L) fm[5]++; fmWorst = maxOf(fmWorst, total)
        }
    }
    private fun androidStats(): String = synchronized(fm) {
        // since 0.20 the 3D and the backdrop go straight to the screen, so these are the panels alone (text, menus, the meter)
        if (fm[0] == 0L) return "Panels: nothing to redraw this half second"
        val n = fm[0].toFloat()
        val s = "Panels %.1f ms a frame (draw %.1f · send %.1f · swap %.1f)  ·  %d over 16.7  ·  worst %.0f ms".format(fm[1] / n / 1e6f, fm[2] / n / 1e6f, fm[3] / n / 1e6f, fm[4] / n / 1e6f, fm[5], fmWorst / 1e6f)
        fm.fill(0L); fmWorst = 0L; s
    }

    private fun showMeter() {
        if (!games.showFps) {
            meter?.let { board.removeView(it); meter = null }
            if (fmOn) { try { window.removeOnFrameMetricsAvailableListener(fmListener) } catch (_: Exception) { }; fmOn = false }
            return
        }
        if (!fmOn) { try { window.addOnFrameMetricsAvailableListener(fmListener, android.os.Handler(fmThread.looper)); fmOn = true } catch (_: Exception) { } }
        val m = meter ?: android.widget.TextView(this).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 17f); typeface = android.graphics.Typeface.MONOSPACE; setTextColor(Theme.fg)
            setBackgroundColor(android.graphics.Color.argb(170, 5, 8, 22)); setPadding(12, 8, 12, 8)
        }.also { meter = it; board.put(it, 8, 8) }
        m.text = stage.stats + "\n" + androidStats(); m.bringToFront()
    }

    override fun start(l: Launch): String = games.launch(l.game)
    override fun done(l: Launch) {
        launch = null
        Music.active(true)   // back (or the game didn't start): the music comes back in
        if (fromLibrary) { fromLibrary = false; library?.landed(l.game) } else home.landed(l.game)
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
    private val repeat = object : Runnable { override fun run() { held?.let { input(it); ui.postDelayed(this, 95) } } }
    /** Buttons go to whichever screen is showing. */
    // ---------- L1 and R1: from page to page ----------

    /** The top bar's pages in order, Home first; L1 and R1 step through them (wrapping). */
    private val PAGES = listOf("home", "library", "apps", "museum", "settings")
    /** The page to open once the one closing has gone (stepping with L1 and R1). */
    private var nextPage: String? = null
    private fun currentPage(): String? = when {
        museum != null -> "museum".takeIf { museum!!.canLeave }
        library != null -> "library".takeIf { library!!.canLeave }
        apps != null -> "apps".takeIf { apps!!.canLeave }
        setup != null -> "settings".takeIf { setup!!.canLeave }
        else -> "home".takeIf { home.canLeave }
    }
    /** L1 or R1: the page before or after this one. False when this page can't be left now (it keeps the button). */
    private fun cycle(d: Int): Boolean {
        if (nextPage != null) return true
        val now = currentPage() ?: return false
        val to = PAGES[(PAGES.indexOf(now) + d + PAGES.size) % PAGES.size]
        Sounds.play(Sounds.MOVE)
        if (now == "home") { openPage(to); return true }
        nextPage = to
        when (now) { "library" -> library?.close(); "apps" -> apps?.close(); "museum" -> museum?.close(); "settings" -> setup?.let { finished(it) } }
        return true
    }
    private fun openPage(p: String) { when (p) { "library" -> openLibrary(); "apps" -> openApps(); "museum" -> openMuseum(null); "settings" -> openSettings(); else -> home.visible = true } }
    /** A page has gone: the next one (L1 or R1) comes up, or Home shows again. */
    private fun pageClosed() { val p = nextPage; nextPage = null; if (p != null) openPage(p) else home.visible = true }

    private fun input(a: String) { if (safeMode) return safeInput(a); launch?.let { return it.input(a) }; if (Overlays.input(a)) return
        if ((a == "lb" || a == "rb") && home.picker == null && cycle(if (a == "lb") -1 else 1)) return; if (home.picker != null) home.input(a) else museum?.input(a) ?: library?.input(a) ?: apps?.input(a) ?: setup?.input(a) ?: home.input(a) }   // a game starting takes every button (any one skips); then a message over everything

    private fun actionFor(code: Int) = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"; KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "a"
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "b"
        KeyEvent.KEYCODE_BUTTON_X -> "x"; KeyEvent.KEYCODE_BUTTON_Y -> "y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "lb"; KeyEvent.KEYCODE_BUTTON_R1 -> "rb"
        KeyEvent.KEYCODE_BUTTON_L2 -> "lt"; KeyEvent.KEYCODE_BUTTON_R2 -> "rt"
        KeyEvent.KEYCODE_BUTTON_START -> "start"; KeyEvent.KEYCODE_BUTTON_SELECT -> "select"
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
        if (!safeMode && launch == null) { val x = e.getAxisValue(MotionEvent.AXIS_Z); val y = e.getAxisValue(MotionEvent.AXIS_RZ); museum?.stick(x, y) ?: library?.stick(x, y) ?: home.stick(x, y) }
        // the left stick moves a model smoothly while you look at it closely (and isn't a D-pad then)
        val lx = e.getAxisValue(MotionEvent.AXIS_X); val ly = e.getAxisValue(MotionEvent.AXIS_Y)
        val analog = !safeMode && launch == null && (museum?.leftStick(lx, ly) ?: library?.leftStick(lx, ly) ?: home.leftStick(lx, ly))
        // the left stick, or a D-pad that reports itself as a hat
        var x = e.getAxisValue(MotionEvent.AXIS_HAT_X); var y = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (abs(x) < .5f && abs(y) < .5f && !analog) { x = lx; y = ly }
        val dir = if (abs(x) > .55f || abs(y) > .55f) (if (abs(x) > abs(y)) (if (x > 0) "right" else "left") else (if (y > 0) "down" else "up")) else null
        if (dir != held) {
            held = dir; ui.removeCallbacks(repeat)
            if (dir != null) { input(dir); ui.postDelayed(repeat, 320) }
        }
        return true
    }

    companion object { private val DIRECTIONS = setOf("up", "down", "left", "right"); private const val PICK_FOLDER = 7; private const val PICK_DOC = 8; private const val MAKE_DOC = 9 }
}
