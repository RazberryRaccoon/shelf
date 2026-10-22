package com.razberryraccoon.shelf;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/**
 * Shelf's one screen. The interface is the same page as the browser prototype (WebGL, drawn by our own renderer),
 * loaded from the app's assets. This activity passes controller buttons into it, tells it when you come back from
 * a game, and gives it {@link Bridge} as `ShelfNative` for the things a web page can't do.
 */
public class MainActivity extends Activity {
    private WebView web;
    private boolean loaded, resumed;
    private long pausedAt;
    boolean launchedOut;   // set by the bridge when Shelf opens an emulator or app

    private final Handler ui = new Handler(Looper.getMainLooper());
    private String held;   // the direction the stick or hat is held in, for repeating
    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (held == null) return;
            send(held);
            ui.postDelayed(this, 110);
        }
    };
    private long lastRepeat;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Some emulators still take a plain file:// path; without this Android refuses to hand one over.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        WebView.setWebContentsDebuggingEnabled(true);   // lets chrome://inspect on a computer see the page, over USB

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // so the menu music and sounds can start
        s.setTextZoom(100);
        // Lay the page out at the screen's real size (it reads the viewport tag) and never zoom it;
        // the page then scales Shelf's 1334 × 750 screen to fit whatever display this is.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setFocusable(false);   // buttons come in through dispatchKeyEvent, never as page focus changes
        web.addJavascriptInterface(new Bridge(this), "ShelfNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { loaded = true; js("window.fit && fit()"); }
            // Android can end the page's process to free memory while a big emulator runs. Start a fresh page instead
            // of letting that close Shelf; it remembers the running game and plays the return animation.
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                ui.post(MainActivity.this::recreate);
                return true;
            }
        });
        // and again whenever the view changes size (rotation, split screen, a different display)
        web.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (r - l != or - ol || b - t != ob - ot) js("window.fit && fit()"); });
        setContentView(web);
        immersive();
        web.loadUrl("file:///android_asset/index.html");
    }

    private void immersive() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
        immersive();
        resumed = true;
        if (loaded) js("window.__nativeResume && __nativeResume()");   // back from a game, an app or Android's settings
        launchedOut = false;
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        js("window.__nativePause && __nativePause()");   // saves Shelf's state
        resumed = false;
        pausedAt = System.currentTimeMillis();
        held = null;
        ui.removeCallbacks(repeat);
        web.onPause();
        web.pauseTimers();   // nothing animates while a game is running
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home button while Shelf is already in front (it's the home screen): treat it as Shelf's own Home.
        boolean home = intent != null && intent.hasCategory(Intent.CATEGORY_HOME);
        boolean wasInFront = resumed || (!launchedOut && System.currentTimeMillis() - pausedAt < 500);
        if (home && wasInFront && loaded) send("home");
    }

    private static final int PICK_FOLDER = 7;
    private String folderFor;   // the console whose games folder is being chosen

    void pickFolder(String sys) {
        folderFor = sys;
        launchedOut = true;
        try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER); }
        catch (ActivityNotFoundException e) { js("window.toast && toast('This device has no folder picker')"); }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FOLDER || folderFor == null || result != RESULT_OK || data == null || data.getData() == null) return;
        String path = Scanner.treePath(this, data.getData());
        js("window.__nativeFolder && __nativeFolder(" + JSONObject.quote(folderFor) + ", " + JSONObject.quote(path == null ? "" : path) + ")");
        folderFor = null;
    }

    void js(String code) {
        ui.post(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    private void send(String action) {
        if (loaded) js("window.__nativeKey && __nativeKey('" + action + "')");
    }

    /** Controller and keyboard buttons, mapped to Shelf's actions. */
    private static String actionFor(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: return "a";
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE: return "b";
            case KeyEvent.KEYCODE_BUTTON_Y: return "y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "lb";
            case KeyEvent.KEYCODE_BUTTON_R1: return "rb";
            default: return null;
        }
    }

    private static boolean isDirection(String a) {
        return "up".equals(a) || "down".equals(a) || "left".equals(a) || "right".equals(a);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String a = actionFor(e.getKeyCode());
        if (a == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) send(a);
            else if (isDirection(a)) {   // holding a direction repeats it, at a calm pace
                long now = System.currentTimeMillis();
                if (now - lastRepeat >= 110) { lastRepeat = now; send(a); }
            }
        }
        return true;   // Shelf handles every one of these, Back included
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        boolean pad = (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (e.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        if (!pad || e.getAction() != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(e);
        // the left stick, or a D-pad that reports itself as a hat
        float x = e.getAxisValue(MotionEvent.AXIS_HAT_X), y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(x) < .5f && Math.abs(y) < .5f) { x = e.getAxisValue(MotionEvent.AXIS_X); y = e.getAxisValue(MotionEvent.AXIS_Y); }
        String dir = null;
        if (Math.abs(x) > .55f || Math.abs(y) > .55f) dir = Math.abs(x) > Math.abs(y) ? (x > 0 ? "right" : "left") : (y > 0 ? "down" : "up");
        if (dir == null ? held != null : !dir.equals(held)) {
            held = dir;
            ui.removeCallbacks(repeat);
            if (dir != null) { send(dir); ui.postDelayed(repeat, 380); }
        }
        return true;
    }
}
