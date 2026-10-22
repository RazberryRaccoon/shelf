package com.razberryraccoon.shelf;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The art services Shelf can search, one type of art at a time. Each answers with pictures to choose from.
 * Kinds: box (front), back, spine, label (cartridge or disc), title (title screen), shot (screenshot).
 *
 *   libretro           box, title, shot              no account
 *   ScreenScraper      all six                        account, plus developer credentials
 *   TheGamesDB         box, back, title, shot         API key
 *   RetroAchievements  box, title, shot               web API key
 *   SteamGridDB        box                            API key
 *   IGDB               box, shot                      Twitch app (client ID and secret)
 *
 * Endpoints follow each service's published documentation. Plain Java (with org.json), so it runs on a computer too.
 */
final class ArtSources {
    private ArtSources() { }

    /** One picture to choose from. */
    static final class Option {
        final String source, game, url, thumb;
        Option(String source, String game, String url, String thumb) { this.source = source; this.game = game; this.url = url; this.thumb = thumb == null ? url : thumb; }
    }

    interface Source { List<Option> find(String sys, String query, String kind, JSONObject keys, File cache) throws Exception; }

    static final Map<String, Source> ALL = new HashMap<>();
    static {
        ALL.put("libretro", ArtSources::libretro);
        ALL.put("ss", ArtSources::screenScraper);
        ALL.put("tgdb", ArtSources::theGamesDb);
        ALL.put("ra", ArtSources::retroAchievements);
        ALL.put("sgdb", ArtSources::steamGridDb);
        ALL.put("igdb", ArtSources::igdb);
    }

    /** Is this source's account filled in? (libretro needs none.) */
    static boolean connected(String id, JSONObject keys) {
        JSONObject k = keys.optJSONObject(id);
        switch (id) {
            case "libretro": return true;
            case "ss": return k != null && !k.optString("devid").isEmpty() && !k.optString("devpassword").isEmpty();
            case "tgdb": case "ra": case "sgdb": return k != null && !k.optString("key").isEmpty();
            case "igdb": return k != null && !k.optString("id").isEmpty() && !k.optString("secret").isEmpty();
            default: return false;
        }
    }

    // ---------- helpers ----------

    static String get(String url, Map<String, String> headers) throws IOException { return request("GET", url, headers, null); }

    static String request(String method, String url, Map<String, String> headers, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "Shelf (Android game launcher)");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        if (body != null) { c.setDoOutput(true); try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); } }
        try {
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (in != null) try (InputStream i = in) { byte[] b = new byte[1 << 15]; int n; while ((n = i.read(b)) > 0) out.write(b, 0, n); }
            String text = out.toString("UTF-8");
            if (code == 401 || code == 403) throw new IOException("The account details were refused");
            if (code == 429) throw new IOException("Too many requests for now; try again later");
            if (code >= 400) throw new IOException("HTTP " + code + (text.length() < 160 ? ": " + text.trim() : ""));
            return text;
        } finally { c.disconnect(); }
    }

    static String q(String s) { return ArtIndex.enc(s); }

    /** Games from a service, closest to the search first. */
    private static <T> List<T> closest(List<T> items, Map<T, String> names, String query, int max) {
        String[] qw = ArtIndex.norm(ArtIndex.stripTags(query)).split(" ");
        items.sort((a, b) -> Double.compare(ArtIndex.dice(qw, ArtIndex.norm(names.get(b)).split(" ")), ArtIndex.dice(qw, ArtIndex.norm(names.get(a)).split(" "))));
        return items.size() > max ? new ArrayList<>(items.subList(0, max)) : items;
    }

    // ---------- libretro: thumbnails named after its database's names ----------

    private static final Map<String, String> LIBRETRO = new HashMap<>();
    static { LIBRETRO.put("box", "Named_Boxarts"); LIBRETRO.put("title", "Named_Titles"); LIBRETRO.put("shot", "Named_Snaps"); }

    static List<Option> libretro(String sys, String query, String kind, JSONObject keys, File cache) throws Exception {
        List<Option> out = new ArrayList<>();
        String folder = LIBRETRO.get(kind); if (folder == null) return out;
        ArtIndex ix = ArtIndex.load(sys, cache);
        for (ArtIndex.Hit h : ix.search(query, 4)) out.add(new Option("libretro", h.e.name, ix.artUrl(folder, h.e.name), null));
        return out;   // some may not exist; the thumbnail download weeds them out
    }

    // ---------- ScreenScraper (api.screenscraper.fr, API v2) ----------

    private static final Map<String, Integer> SS_SYSTEM = new HashMap<>();
    static { SS_SYSTEM.put("nes", 3); SS_SYSTEM.put("snes", 4); SS_SYSTEM.put("n64", 14); SS_SYSTEM.put("genesis", 1); SS_SYSTEM.put("gb", 9);
             SS_SYSTEM.put("gbc", 10); SS_SYSTEM.put("gba", 12); SS_SYSTEM.put("ps1", 57); SS_SYSTEM.put("ds", 15); SS_SYSTEM.put("psp", 61); }
    private static final Map<String, String[]> SS_TYPES = new HashMap<>();
    static { SS_TYPES.put("box", new String[] { "box-2D" }); SS_TYPES.put("back", new String[] { "box-2D-back" }); SS_TYPES.put("spine", new String[] { "box-2D-side" });
             SS_TYPES.put("label", new String[] { "support-2D", "support-texture" }); SS_TYPES.put("title", new String[] { "sstitle" }); SS_TYPES.put("shot", new String[] { "ss" }); }

    static String ssAuth(JSONObject keys) {
        JSONObject k = keys.optJSONObject("ss");
        String s = "devid=" + q(k.optString("devid")) + "&devpassword=" + q(k.optString("devpassword")) + "&softname=Shelf&output=json";
        if (!k.optString("user").isEmpty()) s += "&ssid=" + q(k.optString("user")) + "&sspassword=" + q(k.optString("password"));
        return s;
    }

    static List<Option> screenScraper(String sys, String query, String kind, JSONObject keys, File cache) throws Exception {
        List<Option> out = new ArrayList<>();
        if (!connected("ss", keys) || !SS_TYPES.containsKey(kind)) return out;
        String url = "https://api.screenscraper.fr/api2/jeuRecherche.php?" + ssAuth(keys) + "&systemeid=" + SS_SYSTEM.get(sys) + "&recherche=" + q(ArtIndex.stripTags(query).trim());
        JSONArray games = new JSONObject(get(url, null)).getJSONObject("response").optJSONArray("jeux");
        if (games == null) return out;
        for (int i = 0; i < games.length() && i < 3; i++) out.addAll(ssMedia(games.getJSONObject(i), kind));
        return out;
    }

    static List<Option> ssMedia(JSONObject game, String kind) {
        List<Option> out = new ArrayList<>();
        String name = ssName(game);
        JSONArray medias = game.optJSONArray("medias");
        if (medias == null) return out;
        for (String type : SS_TYPES.get(kind))
            for (int j = 0; j < medias.length(); j++) {
                JSONObject m = medias.optJSONObject(j);
                if (m == null || !type.equals(m.optString("type"))) continue;
                String u = m.optString("url"); if (u.isEmpty()) continue;
                String region = m.optString("region");
                out.add(new Option("ScreenScraper", name + (region.isEmpty() ? "" : " (" + region.toUpperCase(Locale.ROOT) + ")"), u, u + "&maxwidth=240"));
                if (out.size() >= 4) return out;
            }
        return out;
    }

    private static String ssName(JSONObject game) {
        JSONArray n = game.optJSONArray("noms");
        if (n != null) for (int i = 0; i < n.length(); i++) { JSONObject x = n.optJSONObject(i); if (x != null && ("us".equals(x.optString("region")) || "wor".equals(x.optString("region")) || "ss".equals(x.optString("region")))) return x.optString("text"); }
        return n != null && n.length() > 0 ? n.optJSONObject(0).optString("text") : "?";
    }

    /** The exact game, by the ROM's checksum, name and size (for Find art for every game). */
    static JSONObject ssGame(String sys, File rom, JSONObject keys) throws Exception {
        String crc = ArtIndex.checksum(rom);
        String url = "https://api.screenscraper.fr/api2/jeuInfos.php?" + ssAuth(keys) + "&systemeid=" + SS_SYSTEM.get(sys)
                + "&romtype=rom&romnom=" + q(rom.getName()) + "&romtaille=" + rom.length() + (crc == null ? "" : "&crc=" + crc);
        try { return new JSONObject(get(url, null)).getJSONObject("response").getJSONObject("jeu"); }
        catch (IOException e) { if (e.getMessage() != null && e.getMessage().startsWith("HTTP 404")) return null; throw e; }
    }

    // ---------- TheGamesDB (api.thegamesdb.net, v1) ----------

    private static final Map<String, Integer> TGDB_PLATFORM = new HashMap<>();
    static { TGDB_PLATFORM.put("nes", 7); TGDB_PLATFORM.put("snes", 6); TGDB_PLATFORM.put("n64", 3); TGDB_PLATFORM.put("genesis", 18); TGDB_PLATFORM.put("gb", 4);
             TGDB_PLATFORM.put("gbc", 41); TGDB_PLATFORM.put("gba", 5); TGDB_PLATFORM.put("ps1", 10); TGDB_PLATFORM.put("ds", 8); TGDB_PLATFORM.put("psp", 13); }

    static List<Option> theGamesDb(String sys, String query, String kind, JSONObject keys, File cache) throws Exception {
        List<Option> out = new ArrayList<>();
        if (!connected("tgdb", keys) || !(kind.equals("box") || kind.equals("back") || kind.equals("title") || kind.equals("shot"))) return out;
        String key = q(keys.getJSONObject("tgdb").optString("key"));
        String url = "https://api.thegamesdb.net/v1.1/Games/ByGameName?apikey=" + key + "&name=" + q(ArtIndex.stripTags(query).trim()) + "&filter%5Bplatform%5D=" + TGDB_PLATFORM.get(sys);
        JSONArray games = new JSONObject(get(url, null)).getJSONObject("data").optJSONArray("games");
        if (games == null || games.length() == 0) return out;
        List<JSONObject> list = new ArrayList<>(); Map<JSONObject, String> names = new HashMap<>();
        for (int i = 0; i < games.length(); i++) { JSONObject g = games.getJSONObject(i); list.add(g); names.put(g, g.optString("game_title")); }
        list = closest(list, names, query, 3);
        StringBuilder ids = new StringBuilder();
        for (JSONObject g : list) ids.append(ids.length() > 0 ? "," : "").append(g.getInt("id"));
        String type = kind.equals("title") ? "titlescreen" : kind.equals("shot") ? "screenshot" : "boxart";
        JSONObject data = new JSONObject(get("https://api.thegamesdb.net/v1/Games/Images?apikey=" + key + "&games_id=" + ids + "&filter%5Btype%5D=" + type, null)).getJSONObject("data");
        JSONObject base = data.getJSONObject("base_url"), images = data.getJSONObject("images");
        for (JSONObject g : list) {
            JSONArray imgs = images.optJSONArray(String.valueOf(g.getInt("id")));
            if (imgs == null) continue;
            for (int j = 0; j < imgs.length() && out.size() < 5; j++) {
                JSONObject im = imgs.getJSONObject(j);
                if (type.equals("boxart") && !im.optString("side").equals(kind.equals("back") ? "back" : "front")) continue;
                String f = im.optString("filename");
                out.add(new Option("TheGamesDB", g.optString("game_title"), base.optString("original") + f, base.optString("thumb", base.optString("small")) + f));
            }
        }
        return out;
    }

    // ---------- RetroAchievements (retroachievements.org web API) ----------

    private static final Map<String, Integer> RA_CONSOLE = new HashMap<>();
    static { RA_CONSOLE.put("nes", 7); RA_CONSOLE.put("snes", 3); RA_CONSOLE.put("n64", 2); RA_CONSOLE.put("genesis", 1); RA_CONSOLE.put("gb", 4);
             RA_CONSOLE.put("gbc", 6); RA_CONSOLE.put("gba", 5); RA_CONSOLE.put("ps1", 12); RA_CONSOLE.put("ds", 18); RA_CONSOLE.put("psp", 41); }
    private static final Map<String, JSONArray> RA_LISTS = new HashMap<>();

    static List<Option> retroAchievements(String sys, String query, String kind, JSONObject keys, File cache) throws Exception {
        List<Option> out = new ArrayList<>();
        if (!connected("ra", keys) || !(kind.equals("box") || kind.equals("title") || kind.equals("shot"))) return out;
        JSONObject k = keys.getJSONObject("ra");
        String auth = "y=" + q(k.optString("key")) + (k.optString("user").isEmpty() ? "" : "&z=" + q(k.optString("user")));
        JSONArray all;
        synchronized (RA_LISTS) {
            all = RA_LISTS.get(sys);
            if (all == null) { all = new JSONArray(get("https://retroachievements.org/API/API_GetGameList.php?" + auth + "&i=" + RA_CONSOLE.get(sys), null)); RA_LISTS.put(sys, all); }
        }
        List<JSONObject> list = new ArrayList<>(); Map<JSONObject, String> names = new HashMap<>();
        for (int i = 0; i < all.length(); i++) { JSONObject g = all.getJSONObject(i); list.add(g); names.put(g, g.optString("Title")); }
        for (JSONObject g : closest(list, names, query, 3)) {
            JSONObject info = new JSONObject(get("https://retroachievements.org/API/API_GetGame.php?" + auth + "&i=" + g.getInt("ID"), null));
            String path = info.optString(kind.equals("box") ? "ImageBoxArt" : kind.equals("title") ? "ImageTitle" : "ImageIngame");
            if (!path.isEmpty() && !path.endsWith("000002.png")) out.add(new Option("RetroAchievements", g.optString("Title"), "https://media.retroachievements.org" + path, null));
        }
        return out;
    }

    // ---------- SteamGridDB (steamgriddb.com API v2) ----------

    static List<Option> steamGridDb(String sys, String query, String kind, JSONObject keys, File cache) throws Exception {
        List<Option> out = new ArrayList<>();
        if (!connected("sgdb", keys) || !kind.equals("box")) return out;
        Map<String, String> auth = new HashMap<>(); auth.put("Authorization", "Bearer " + keys.getJSONObject("sgdb").optString("key"));
        JSONArray games = new JSONObject(get("https://www.steamgriddb.com/api/v2/search/autocomplete/" + q(ArtIndex.stripTags(query).trim()), auth)).optJSONArray("data");
        if (games == null) return out;
        for (int i = 0; i < games.length() && i < 2; i++) {
            JSONObject g = games.getJSONObject(i);
            JSONArray grids = new JSONObject(get("https://www.steamgriddb.com/api/v2/grids/game/" + g.getInt("id") + "?dimensions=600x900,342x482,660x930", auth)).optJSONArray("data");
            if (grids == null) continue;
            for (int j = 0; j < grids.length() && j < 4; j++) { JSONObject x = grids.getJSONObject(j); out.add(new Option("SteamGridDB", g.optString("name"), x.optString("url"), x.optString("thumb"))); }
        }
        return out;
    }

    // ---------- IGDB (api.igdb.com v4, signed in with a Twitch app) ----------

    private static final Map<String, Integer> IGDB_PLATFORM = new HashMap<>();
    static { IGDB_PLATFORM.put("nes", 18); IGDB_PLATFORM.put("snes", 19); IGDB_PLATFORM.put("n64", 4); IGDB_PLATFORM.put("genesis", 29); IGDB_PLATFORM.put("gb", 33);
             IGDB_PLATFORM.put("gbc", 22); IGDB_PLATFORM.put("gba", 24); IGDB_PLATFORM.put("ps1", 7); IGDB_PLATFORM.put("ds", 20); IGDB_PLATFORM.put("psp", 38); }
    private static String igdbToken; private static long igdbExpires;

    static List<Option> igdb(String sys, String query, String kind, JSONObject keys, File cache) throws Exception {
        List<Option> out = new ArrayList<>();
        if (!connected("igdb", keys) || !(kind.equals("box") || kind.equals("shot"))) return out;
        JSONObject k = keys.getJSONObject("igdb");
        synchronized (ArtSources.class) {
            if (igdbToken == null || System.currentTimeMillis() > igdbExpires) {
                JSONObject t = new JSONObject(request("POST", "https://id.twitch.tv/oauth2/token?client_id=" + q(k.optString("id")) + "&client_secret=" + q(k.optString("secret")) + "&grant_type=client_credentials", null, ""));
                igdbToken = t.getString("access_token"); igdbExpires = System.currentTimeMillis() + t.optLong("expires_in", 3600) * 900;
            }
        }
        Map<String, String> h = new HashMap<>(); h.put("Client-ID", k.optString("id")); h.put("Authorization", "Bearer " + igdbToken);
        String body = "search \"" + ArtIndex.stripTags(query).trim().replace("\"", "") + "\"; fields name,cover.image_id,screenshots.image_id; where platforms = (" + IGDB_PLATFORM.get(sys) + "); limit 4;";
        JSONArray games = new JSONArray(request("POST", "https://api.igdb.com/v4/games", h, body));
        for (int i = 0; i < games.length(); i++) {
            JSONObject g = games.getJSONObject(i);
            if (kind.equals("box")) { JSONObject c = g.optJSONObject("cover"); if (c != null) out.add(igdbImage(g, c.optString("image_id"), "t_cover_big_2x", "t_cover_small")); }
            else { JSONArray s = g.optJSONArray("screenshots"); if (s != null) for (int j = 0; j < s.length() && j < 3; j++) out.add(igdbImage(g, s.getJSONObject(j).optString("image_id"), "t_screenshot_big", "t_screenshot_med")); }
        }
        return out;
    }
    private static Option igdbImage(JSONObject g, String id, String size, String small) {
        String base = "https://images.igdb.com/igdb/image/upload/";
        return new Option("IGDB", g.optString("name"), base + size + "/" + id + ".jpg", base + small + "/" + id + ".jpg");
    }

    /** A small request to check an account (the result text says what worked). */
    static String test(String id, JSONObject keys, File cache) throws Exception {
        List<Option> r = ALL.get(id).find("snes", "Super Mario World", "box", keys, cache);
        return r.isEmpty() ? "Connected, but found nothing for a test search" : "Connected";
    }

    static List<String> ids() { List<String> l = new ArrayList<>(); for (Iterator<String> i = ALL.keySet().iterator(); i.hasNext(); ) l.add(i.next()); return l; }
}
