package com.razberryraccoon.shelf;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** What the interface can ask of Android, as `ShelfNative` in the page. Called on a background thread. */
public class Bridge {
    private final MainActivity act;
    private final Handler ui = new Handler(Looper.getMainLooper());

    Bridge(MainActivity act) { this.act = act; }

    /** Runs on the main thread and waits for the answer (starting activities belongs there). */
    private String onUi(java.util.concurrent.Callable<String> job) {
        AtomicReference<String> out = new AtomicReference<>("");
        CountDownLatch done = new CountDownLatch(1);
        ui.post(() -> {
            try { out.set(job.call()); } catch (Exception e) { out.set("Something went wrong: " + e.getMessage()); }
            done.countDown();
        });
        try { done.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        return out.get();
    }

    /** Your games, apps and installed emulators, as JSON. */
    @JavascriptInterface
    public String scan() { return Scanner.scan(act).toString(); }

    @JavascriptInterface
    public boolean hasStorage() { return Environment.isExternalStorageManager(); }

    /** Opens Android's "All files access" switch for Shelf. */
    @JavascriptInterface
    public void requestStorage() {
        ui.post(() -> {
            try {
                act.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + act.getPackageName())));
            } catch (ActivityNotFoundException e) {
                act.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        });
    }

    @JavascriptInterface
    public void openHomeSettings() {
        ui.post(() -> {
            try { act.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (ActivityNotFoundException e) { act.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
    }

    /** Starts a game: {emu, core, path, sys}. Returns "" when the emulator opened, or what went wrong. */
    @JavascriptInterface
    public String launch(String json) {
        return onUi(() -> {
            String err = Emulators.launch(act, new org.json.JSONObject(json));
            if (err.isEmpty()) act.launchedOut = true;
            return err;
        });
    }

    @JavascriptInterface
    public boolean openApp(String pkg) {
        return onUi(() -> {
            Intent i = act.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return "no";
            act.startActivity(i);
            act.launchedOut = true;
            return "";
        }).isEmpty();
    }

    /** Opens an emulator by Shelf's name for it (to see its saves, for example). */
    @JavascriptInterface
    public boolean openEmulator(String name) {
        String pkg = Emulators.installedPackage(act, name);
        return pkg != null && openApp(pkg);
    }

    private File stateFile() { return new File(act.getFilesDir(), "state.json"); }

    @JavascriptInterface
    public String loadState() {
        try { File f = stateFile(); return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : ""; }
        catch (Exception e) { return ""; }
    }

    @JavascriptInterface
    public void saveState(String s) {
        File tmp = new File(act.getFilesDir(), "state.json.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        } catch (Exception e) { return; }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(stateFile());   // written whole, then swapped in, so a crash never leaves half a file
    }
}
