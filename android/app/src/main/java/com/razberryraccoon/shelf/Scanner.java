package com.razberryraccoon.shelf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds your games and apps. Games live in a ROMs folder (any capitalization) at the top of internal storage or an
 * SD card, one folder per console, named the way ES-DE and most handhelds name them (gba, psx, nds, ...).
 */
final class Scanner {
    private Scanner() { }

    /** Folder names → Shelf's console keys. */
    private static final Map<String, String> FOLDERS = new HashMap<>();
    /** File types per console (from ES-DE's Android system list, minus the ones that are only parts of a game). */
    private static final Map<String, Set<String>> EXT = new HashMap<>();
    static {
        String[][] f = {
            {"nes", "nes"}, {"famicom", "nes"}, {"fc", "nes"},
            {"snes", "snes"}, {"sfc", "snes"}, {"superfamicom", "snes"}, {"snesna", "snes"},
            {"n64", "n64"},
            {"genesis", "genesis"}, {"megadrive", "genesis"}, {"md", "genesis"},
            {"gb", "gb"}, {"gbc", "gbc"}, {"gba", "gba"},
            {"psx", "ps1"}, {"ps1", "ps1"}, {"playstation", "ps1"},
            {"nds", "ds"}, {"ds", "ds"},
            {"psp", "psp"},
        };
        for (String[] p : f) FOLDERS.put(p[0], p[1]);
        ext("nes", "nes fds unf unif zip 7z");
        ext("snes", "sfc smc fig swc bs bsx st zip 7z");
        ext("n64", "n64 z64 v64 ndd zip 7z");
        ext("genesis", "md gen smd bin 32x zip 7z");
        ext("gb", "gb sgb dmg zip 7z");
        ext("gbc", "gbc gb cgb zip 7z");
        ext("gba", "gba agb zip 7z");
        ext("ps1", "chd cue pbp m3u iso ecm ccd mds");
        ext("ds", "nds dsi zip 7z");
        ext("psp", "iso cso pbp chd");
    }
    private static void ext(String sys, String list) { EXT.put(sys, new HashSet<>(Arrays.asList(list.split(" ")))); }

    /** folders: {console: path} for consoles whose games folder you chose; ROMs folders are always searched too. */
    static JSONObject scan(Context ctx, String folders) {
        JSONObject out = new JSONObject();
        try {
            JSONArray games = new JSONArray();
            Set<String> seen = new HashSet<>();
            if (Environment.isExternalStorageManager()) {
                JSONObject chosen = new JSONObject(folders == null || folders.isEmpty() ? "{}" : folders);
                for (Iterator<String> it = chosen.keys(); it.hasNext(); ) {
                    String sys = it.next();
                    File dir = new File(chosen.getString(sys));
                    if (EXT.containsKey(sys) && dir.isDirectory()) walk(dir, sys, games, seen, 0);
                }
                for (File root : romRoots(ctx)) scanRoot(root, games, seen);
            }
            out.put("games", games);
            out.put("apps", apps(ctx));
            JSONArray emus = new JSONArray();
            for (String name : Emulators.names()) if (Emulators.installedPackage(ctx, name) != null) emus.put(name);
            out.put("emus", emus);
        } catch (Exception ignored) { }
        return out;
    }

    /** Every ROMs folder at the top of internal storage and each SD card. */
    static List<File> romRoots(Context ctx) {
        List<File> roots = new ArrayList<>();
        StorageManager sm = ctx.getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            File dir = v.getDirectory();
            File[] kids = dir == null ? null : dir.listFiles();
            if (kids == null) continue;
            for (File k : kids) if (k.isDirectory() && k.getName().equalsIgnoreCase("roms")) roots.add(k);
        }
        return roots;
    }

    /** The file path behind a folder chosen in Android's picker ("primary:Games/GBA" → /storage/emulated/0/Games/GBA). */
    static String treePath(Context ctx, Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        int c = id.indexOf(':');
        if (c < 0) return null;
        String vol = id.substring(0, c), rel = id.substring(c + 1);
        StorageManager sm = ctx.getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            File dir = v.getDirectory();
            if (dir == null) continue;
            boolean match = "primary".equalsIgnoreCase(vol) ? v.isPrimary() : vol.equalsIgnoreCase(v.getUuid());
            if (match) return rel.isEmpty() ? dir.getAbsolutePath() : new File(dir, rel).getAbsolutePath();
        }
        return null;
    }

    private static void scanRoot(File root, JSONArray games, Set<String> seen) throws Exception {
        File[] kids = root.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String sys = k.isDirectory() ? FOLDERS.get(k.getName().toLowerCase(Locale.ROOT)) : null;
            if (sys != null) walk(k, sys, games, seen, 0);
        }
    }

    private static void walk(File dir, String sys, JSONArray games, Set<String> seen, int depth) throws Exception {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        // a playlist (.m3u) stands for the discs next to it, so they aren't listed separately
        boolean playlist = false;
        for (File k : kids) if (k.isFile() && extOf(k).equals("m3u")) playlist = true;
        for (File k : kids) {
            String name = k.getName();
            if (name.startsWith(".")) continue;
            if (k.isDirectory()) { if (depth < 3) walk(k, sys, games, seen, depth + 1); continue; }
            String e = extOf(k);
            if (!EXT.get(sys).contains(e) || (playlist && !e.equals("m3u")) || !seen.add(k.getAbsolutePath())) continue;
            games.put(new JSONObject().put("sys", sys).put("path", k.getAbsolutePath()).put("title", title(name)));
        }
    }

    private static String extOf(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** "Legend of Zelda, The - A Link to the Past (USA) [!].sfc" → "The Legend of Zelda - A Link to the Past". Disc numbers stay. */
    static String title(String file) {
        String t = file.replaceAll("\\.[^.]+$", "").replace('_', ' ');
        t = t.replaceAll("\\[[^\\]]*\\]", "");
        t = t.replaceAll("\\((?![Dd]isc)[^)]*\\)", "");
        t = t.replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?), (The|A|An)( - .*| \\(.*|$)").matcher(t);
        if (m.find()) t = m.group(2) + " " + m.group(1) + m.group(3);
        return t.isEmpty() ? file : t;
    }

    private static final Map<String, String> ICONS = new HashMap<>();   // icons are slow to draw; keep them between scans

    private static JSONArray apps(Context ctx) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<JSONObject> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !seen.add(pkg)) continue;
            String icon = ICONS.get(pkg);
            if (icon == null) { icon = iconData(ri.loadIcon(pm)); ICONS.put(pkg, icon); }
            apps.add(new JSONObject().put("name", String.valueOf(ri.loadLabel(pm))).put("pkg", pkg).put("icon", icon));
        }
        apps.sort((a, b) -> a.optString("name").compareToIgnoreCase(b.optString("name")));
        JSONArray out = new JSONArray();
        for (JSONObject a : apps) out.put(a);
        return out;
    }

    private static String iconData(Drawable d) {
        int size = 112;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, png);
        return "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
    }
}
