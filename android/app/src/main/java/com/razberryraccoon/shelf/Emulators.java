package com.razberryraccoon.shelf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How to start each emulator Shelf knows, copied from ES-DE's Android configuration (es_find_rules.xml and
 * es_systems.xml), which documents the package, activity and extras each one expects.
 */
final class Emulators {
    private Emulators() { }

    /** How the ROM is handed over. */
    private enum Style {
        RETROARCH,     // extras ROM, LIBRETRO, CONFIGFILE
        VIEW_SAF,      // ACTION_VIEW with the storage document link (the emulator must be allowed into that folder)
        VIEW_PROVIDER, // ACTION_VIEW with Shelf's own content:// link, read access granted
        DATA_PROVIDER, // Shelf's link as the data, no action
        DATA_SAF,      // the storage document link as the data, no action
        EXTRA_SAF,     // the storage document link in a named extra
        EXTRA_PATH,    // the plain file path in a named extra
        VIEW_FILE,     // ACTION_VIEW with a file:// link
    }

    private static final class Emu {
        final String[] components; final Style style; final String action, extra; final boolean clearTask;
        Emu(Style style, String action, String extra, boolean clearTask, String... components) {
            this.style = style; this.action = action; this.extra = extra; this.clearTask = clearTask; this.components = components;
        }
    }

    private static final String VIEW = Intent.ACTION_VIEW;
    private static final Map<String, Emu> EMUS = new LinkedHashMap<>();
    static {
        String ra = "com.retroarch.browser.retroactivity.RetroActivityFuture";
        EMUS.put("RetroArch", new Emu(Style.RETROARCH, null, null, false, "com.retroarch.aarch64/" + ra, "com.retroarch/" + ra, "com.retroarch.ra32/" + ra));
        EMUS.put("DuckStation", new Emu(Style.EXTRA_SAF, null, "bootPath", true, "com.github.stenzek.duckstation/.EmulationActivity"));
        EMUS.put("ePSXe", new Emu(Style.EXTRA_PATH, Intent.ACTION_MAIN, "com.epsxe.ePSXe.isoName", false, "com.epsxe.ePSXe/.ePSXe"));
        EMUS.put("FPseNG", new Emu(Style.VIEW_PROVIDER, VIEW, null, false, "com.emulator.fpse64/.Main"));
        EMUS.put("FPse", new Emu(Style.VIEW_PROVIDER, VIEW, null, false, "com.emulator.fpse/.Main"));
        EMUS.put("PPSSPP", new Emu(Style.VIEW_SAF, VIEW, null, false, "org.ppsspp.ppsspp/.PpssppActivity"));
        EMUS.put("PPSSPP Gold", new Emu(Style.VIEW_SAF, VIEW, null, false, "org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity"));
        EMUS.put("melonDS", new Emu(Style.EXTRA_SAF, "me.magnum.melonds.LAUNCH_ROM", "uri", false, "me.magnum.melonds/.ui.emulator.EmulatorActivity"));
        EMUS.put("DraStic", new Emu(Style.DATA_SAF, null, null, true, "com.dsemu.drastic/.DraSticActivity"));
        EMUS.put("NooDS", new Emu(Style.EXTRA_PATH, null, "LaunchPath", true, "com.hydra.noods/.FileBrowser"));
        EMUS.put("SkyEmu", new Emu(Style.VIEW_PROVIDER, VIEW, null, true, "com.sky.SkyEmu/.EnhancedNativeActivity"));
        String m64 = "/paulscode.android.mupen64plusae.SplashActivity";
        EMUS.put("Mupen64Plus FZ", new Emu(Style.VIEW_SAF, VIEW, null, false, "org.mupen64plusae.v3.fzurita.pro" + m64, "org.mupen64plusae.v3.fzurita" + m64, "org.mupen64plusae.v3.fzurita.amazon" + m64));
        EMUS.put("Mupen64Plus AE", new Emu(Style.VIEW_SAF, VIEW, null, false, "org.mupen64plusae.v3.alpha" + m64));
        EMUS.put("Pizza Boy GBA", new Emu(Style.EXTRA_SAF, null, "rom_uri", true, "it.dbtecno.pizzaboygbapro/it.dbtecno.pizzaboygbapro.MainActivity", "it.dbtecno.pizzaboygba/it.dbtecno.pizzaboygba.MainActivity"));
        EMUS.put("Pizza Boy GBC", new Emu(Style.EXTRA_SAF, null, "rom_uri", true, "it.dbtecno.pizzaboypro/it.dbtecno.pizzaboypro.MainActivity", "it.dbtecno.pizzaboy/it.dbtecno.pizzaboy.MainActivity"));
        EMUS.put("Pizza Boy SC", new Emu(Style.EXTRA_SAF, null, "rom_uri", true, "it.dbtecno.pizzaboyscpro/.MainActivity", "it.dbtecno.pizzaboyscbasic/.MainActivity"));
        EMUS.put("My Boy!", new Emu(Style.VIEW_SAF, VIEW, null, false, "com.fastemulator.gba/.EmulatorActivity"));
        EMUS.put("My OldBoy!", new Emu(Style.VIEW_SAF, VIEW, null, false, "com.fastemulator.gbc/.EmulatorActivity"));
        EMUS.put("Linkboy", new Emu(Style.VIEW_SAF, VIEW, null, false, "com.pixelrespawn.linkboy/.EmulatorActivity"));
        EMUS.put("NES.emu", new Emu(Style.DATA_PROVIDER, null, null, false, "com.explusalpha.NesEmu/com.imagine.BaseActivity"));
        EMUS.put("GBC.emu", new Emu(Style.DATA_PROVIDER, null, null, false, "com.explusalpha.GbcEmu/com.imagine.BaseActivity"));
        EMUS.put("GBA.emu", new Emu(Style.DATA_PROVIDER, null, null, false, "com.explusalpha.GbaEmu/com.imagine.BaseActivity"));
        EMUS.put("MD.emu", new Emu(Style.DATA_PROVIDER, null, null, false, "com.explusalpha.MdEmu/com.imagine.BaseActivity"));
        EMUS.put("Snes9x EX+", new Emu(Style.DATA_SAF, null, null, false, "com.explusalpha.Snes9xPlus/com.imagine.BaseActivity"));
        EMUS.put("Nesoid", new Emu(Style.VIEW_FILE, VIEW, null, false, "com.androidemu.nes/.EmulatorActivity"));
    }

    static Iterable<String> names() { return EMUS.keySet(); }

    private static ComponentName installed(Context ctx, Emu e) {
        PackageManager pm = ctx.getPackageManager();
        for (String c : e.components) {
            ComponentName cn = ComponentName.unflattenFromString(c);
            if (cn == null) continue;
            try { pm.getPackageInfo(cn.getPackageName(), 0); return cn; } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    static String installedPackage(Context ctx, String name) {
        Emu e = EMUS.get(name);
        ComponentName cn = e == null ? null : installed(ctx, e);
        return cn == null ? null : cn.getPackageName();
    }

    /** Starts {emu, core, path}. Returns "" on success, or a sentence saying what went wrong. */
    static String launch(Context ctx, JSONObject req) {
        String name = req.optString("emu"), path = req.optString("path"), core = req.optString("core");
        Emu e = EMUS.get(name);
        if (e == null) return "Shelf can’t start " + name + " yet";
        ComponentName cn = installed(ctx, e);
        if (cn == null) return name + " isn’t installed";
        File rom = new File(path);
        if (!rom.exists()) return "Couldn’t find the game file. Was it moved?";

        Intent i = new Intent();
        i.setComponent(cn);
        if (e.action != null) i.setAction(e.action);
        if (e.clearTask) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        switch (e.style) {
            case RETROARCH: {
                if (core.isEmpty()) return "Choose a RetroArch core for this console in Settings";
                String pkg = cn.getPackageName();
                i.putExtra("ROM", path);
                i.putExtra("LIBRETRO", "/data/data/" + pkg + "/cores/" + core + "_libretro_android.so");
                i.putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/" + pkg + "/files/retroarch.cfg");
                i.putExtra("QUITFOCUS", "");   // RetroArch hands focus back to Shelf when you quit
                break;
            }
            case VIEW_SAF: case DATA_SAF: i.setData(documentUri(ctx, rom)); break;
            case VIEW_PROVIDER: case DATA_PROVIDER: i.setData(RomProvider.uriFor(rom)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); break;
            case EXTRA_SAF: i.putExtra(e.extra, documentUri(ctx, rom).toString()); break;
            case EXTRA_PATH: i.putExtra(e.extra, path); break;
            case VIEW_FILE: i.setData(Uri.fromFile(rom)); break;
        }
        if (name.equals("DuckStation")) i.putExtra("resumeState", false);
        try {
            ctx.startActivity(i);
            return "";
        } catch (Exception ex) {
            return "Couldn’t start " + name;
        }
    }

    /** The storage document link Android's Files app would use for this file (what ES-DE calls %ROMSAF%). */
    static Uri documentUri(Context ctx, File f) {
        StorageManager sm = ctx.getSystemService(StorageManager.class);
        StorageVolume v = sm.getStorageVolume(f);
        File root = v == null ? null : v.getDirectory();
        if (root == null) return Uri.fromFile(f);
        String id = v.isPrimary() ? "primary" : v.getUuid();
        String rel = f.getAbsolutePath().substring(root.getAbsolutePath().length()).replaceFirst("^/", "");
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", id + ":" + rel);
    }
}
