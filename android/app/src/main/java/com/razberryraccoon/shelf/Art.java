package com.razberryraccoon.shelf;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Finds and downloads art and details for games: libretro's database for the names and details (ArtIndex), and
 * libretro's thumbnails for box art, title screens and screenshots. Everything runs in the background and answers
 * the page through callbacks (__nativeArt, __nativeArtProgress).
 */
final class Art {
    private final MainActivity act;
    private final ExecutorService jobs = Executors.newSingleThreadExecutor();
    private final ExecutorService fetch = Executors.newFixedThreadPool(4);
    private volatile boolean cancel;

    /** Saved kinds: Shelf's name → libretro's folder. */
    static final String[][] KINDS = { { "box", "Named_Boxarts" }, { "title", "Named_Titles" }, { "shot", "Named_Snaps" } };

    Art(MainActivity act) { this.act = act; }

    private File cache() { return act.getCacheDir(); }
    private File dir(String key) { return new File(act.getFilesDir(), "art/" + key.replaceAll("[^A-Za-z0-9_-]", "")); }

    private void answer(String fn, String req, JSONObject body) {
        act.js("window." + fn + " && " + fn + "(" + JSONObject.quote(req) + ", " + body + ")");
    }
    private static JSONObject error(Exception e) {
        try { return new JSONObject().put("error", e.getMessage() == null ? "Something went wrong" : e.getMessage()); } catch (Exception x) { return new JSONObject(); }
    }

    /** Search one console's games, with a small box-art picture for each result. The ROM's checksum, when it matches, goes first. */
    void search(String req, String sys, String query, String path) {
        jobs.execute(() -> {
            try {
                ArtIndex ix = ArtIndex.load(sys, cache());
                List<ArtIndex.Entry> list = new ArrayList<>();
                ArtIndex.Entry exact = path == null || path.isEmpty() ? null : ix.byChecksum(new File(path));
                if (exact != null) list.add(exact);
                for (ArtIndex.Hit h : ix.search(query, 10)) if (h.e != exact && list.size() < 9) list.add(h.e);
                List<Future<String>> thumbs = new ArrayList<>();
                for (ArtIndex.Entry e : list) thumbs.add(fetch.submit(() -> dataUrl(ArtIndex.download(ix.artUrl("Named_Boxarts", e.name)), 220)));
                JSONArray results = new JSONArray();
                for (int i = 0; i < list.size(); i++) {
                    String t = null; try { t = thumbs.get(i).get(); } catch (Exception ignored) { }
                    results.put(new JSONObject().put("name", list.get(i).name).put("thumb", t == null ? "" : t).put("exact", list.get(i) == exact));
                }
                answer("__nativeArt", req, new JSONObject().put("results", results));
            } catch (Exception e) { answer("__nativeArt", req, error(e)); }
        });
    }

    /** Download a game's art (by its libretro name) and look up its details. */
    void apply(String req, String key, String sys, String name) {
        jobs.execute(() -> {
            try { answer("__nativeArt", req, applyNow(key, sys, name)); }
            catch (Exception e) { answer("__nativeArt", req, error(e)); }
        });
    }

    private JSONObject applyNow(String key, String sys, String name) throws Exception {
        ArtIndex ix = ArtIndex.load(sys, cache());
        File d = dir(key);
        d.mkdirs();
        List<Future<Boolean>> got = new ArrayList<>();
        for (String[] k : KINDS) got.add(fetch.submit(() -> save(ArtIndex.download(ix.artUrl(k[1], name)), new File(d, k[0] + ".jpg"), k[0].equals("box") ? 720 : 640)));
        JSONObject art = new JSONObject();
        for (int i = 0; i < KINDS.length; i++) { boolean ok = false; try { ok = got.get(i).get(); } catch (Exception ignored) { } if (ok) art.put(KINDS[i][0], 1); }
        JSONObject out = new JSONObject().put("name", name).put("art", art).put("t", System.currentTimeMillis());
        for (Map.Entry<String, String> f : ix.details(name).entrySet()) out.put(f.getKey(), f.getValue());
        return out;
    }

    /** Find and download art for many games, one after another: [{key, sys, path, query}]. Reports after each game. */
    void auto(String req, String listJson) {
        cancel = false;
        jobs.execute(() -> {
            try {
                JSONArray list = new JSONArray(listJson);
                int found = 0;
                for (int i = 0; i < list.length() && !cancel; i++) {
                    JSONObject g = list.getJSONObject(i), r = new JSONObject().put("i", i).put("n", list.length()).put("key", g.getString("key"));
                    try {
                        ArtIndex ix = ArtIndex.load(g.getString("sys"), cache());
                        ArtIndex.Entry e = ix.byChecksum(new File(g.optString("path")));
                        if (e == null) { List<ArtIndex.Hit> h = ix.search(g.getString("query"), 1); if (!h.isEmpty() && h.get(0).score >= .9) e = h.get(0).e; }
                        if (e != null) { r.put("result", applyNow(g.getString("key"), g.getString("sys"), e.name)); found++; }
                    } catch (Exception ex) { r.put("error", ex.getMessage()); }
                    answer("__nativeArtProgress", req, r);
                }
                answer("__nativeArtProgress", req, new JSONObject().put("done", true).put("found", found).put("cancelled", cancel));
            } catch (Exception e) { answer("__nativeArtProgress", req, error(e)); }
        });
    }

    void stop() { cancel = true; }

    /** A saved picture for the page, as a data: link ("" if there isn't one). */
    String data(String key, String kind) {
        File f = new File(dir(key), kind.replaceAll("[^a-z]", "") + ".jpg");
        try { return f.exists() ? "data:image/jpeg;base64," + Base64.encodeToString(Files.readAllBytes(f.toPath()), Base64.NO_WRAP) : ""; }
        catch (Exception e) { return ""; }
    }

    void remove(String key) {
        File d = dir(key); File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    // ---------- pictures ----------

    private static Bitmap decode(byte[] b, int max) {
        if (b == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        int side = Math.max(o.outWidth, o.outHeight), sample = 1;
        while (side / (sample * 2) >= max) sample *= 2;
        o = new BitmapFactory.Options(); o.inSampleSize = sample;
        Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, o);
        if (bm == null) return null;
        float k = Math.min(1f, (float) max / Math.max(bm.getWidth(), bm.getHeight()));
        return k < 1 ? Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * k), Math.round(bm.getHeight() * k), true) : bm;
    }

    private static boolean save(byte[] b, File to, int max) {
        Bitmap bm = decode(b, max);
        if (bm == null) { //noinspection ResultOfMethodCallIgnored
            to.delete(); return false; }
        try (FileOutputStream o = new FileOutputStream(to)) { return bm.compress(Bitmap.CompressFormat.JPEG, 88, o); }
        catch (Exception e) { return false; }
    }

    private static String dataUrl(byte[] b, int max) {
        Bitmap bm = decode(b, max);
        if (bm == null) return null;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 85, o);
        return "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }
}
