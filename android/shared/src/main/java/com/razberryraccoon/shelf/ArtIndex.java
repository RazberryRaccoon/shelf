package com.razberryraccoon.shelf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Every known game for one console, from libretro's database (the official dump names that RetroArch's art is
 * named after), with release year, developer, publisher, genre and, for some consoles, a description. Free, and no
 * account needed. Plain Java, so it can be tested on a computer.
 */
final class ArtIndex {
    static final String DB = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/";
    static final String THUMBS = "https://raw.githubusercontent.com/libretro-thumbnails/";
    static final Map<String, String> SYSTEM = new HashMap<>();
    static {
        SYSTEM.put("nes", "Nintendo - Nintendo Entertainment System");
        SYSTEM.put("snes", "Nintendo - Super Nintendo Entertainment System");
        SYSTEM.put("n64", "Nintendo - Nintendo 64");
        SYSTEM.put("genesis", "Sega - Mega Drive - Genesis");
        SYSTEM.put("gb", "Nintendo - Game Boy");
        SYSTEM.put("gbc", "Nintendo - Game Boy Color");
        SYSTEM.put("gba", "Nintendo - Game Boy Advance");
        SYSTEM.put("ps1", "Sony - PlayStation");
        SYSTEM.put("ds", "Nintendo - Nintendo DS");
        SYSTEM.put("psp", "Sony - PlayStation Portable");
    }
    private static final String[] DETAIL_FILES = { "developer", "publisher", "genre", "releaseyear" };
    private static final long MAX_AGE = 30L * 24 * 3600 * 1000;   // fetch the lists again once a month

    static final class Entry {
        final String name, base; final String[] words; String crc, serial;
        Entry(String name) { this.name = name; this.base = norm(stripTags(name)); this.words = base.isEmpty() ? new String[0] : base.split(" "); }
    }

    final String sys;
    final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> byCrc = new HashMap<>();
    private final Map<String, Map<String, String>> details = new HashMap<>();   // "crc:…", "serial:…" or "name:…" → fields

    private static final Map<String, ArtIndex> LOADED = new HashMap<>();

    private ArtIndex(String sys) { this.sys = sys; }

    /** The index for a console, downloading libretro's lists into cacheDir the first time. */
    static synchronized ArtIndex load(String sys, File cacheDir) throws IOException {
        ArtIndex ix = LOADED.get(sys);
        if (ix != null) return ix;
        String system = SYSTEM.get(sys);
        if (system == null) throw new IOException("No art source for this console");
        ix = new ArtIndex(sys);
        File dir = new File(cacheDir, "artdb/" + sys);
        boolean disc = sys.equals("ps1") || sys.equals("psp");
        String names = cached(DB + (disc ? "redump/" : "no-intro/") + enc(system) + ".dat", new File(dir, "names.dat"));
        if (names == null) throw new IOException("Couldn’t download the list of games. Check the internet connection.");
        ix.readNames(names);
        for (String f : DETAIL_FILES) { String t = cached(DB + f + "/" + enc(system) + ".dat", new File(dir, f + ".dat")); if (t != null) ix.readDetails(t); }
        LOADED.put(sys, ix);
        return ix;
    }

    // ---------- reading libretro's .dat files (clrmamepro format: game ( key "value" … rom ( … ) ) ----------

    private static final Pattern FIELD = Pattern.compile("^\\s*(\\w+)\\s+\"(.*)\"\\s*$");
    private static final Pattern CRC = Pattern.compile("\\bcrc ([0-9A-Fa-f]{8})");
    private static final Pattern SERIAL = Pattern.compile("\\bserial \"([^\"]+)\"");

    private interface Block { void game(Map<String, String> fields, List<String> crcs, List<String> serials); }

    private static void blocks(String text, Block each) {
        Map<String, String> fields = null; List<String> crcs = null, serials = null;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith("game (")) { fields = new HashMap<>(); crcs = new ArrayList<>(); serials = new ArrayList<>(); continue; }
            if (fields == null) continue;
            if (t.equals(")")) { each.game(fields, crcs, serials); fields = null; continue; }
            if (t.startsWith("rom (")) {
                Matcher c = CRC.matcher(t); if (c.find()) crcs.add(c.group(1).toUpperCase(Locale.ROOT));
                Matcher s = SERIAL.matcher(t); if (s.find()) serials.add(s.group(1));
                continue;
            }
            Matcher m = FIELD.matcher(line);
            if (m.find()) fields.put(m.group(1), m.group(2).replace("\\\"", "\""));
        }
    }

    private void readNames(String text) {
        Set<String> seen = new LinkedHashSet<>();
        blocks(text, (f, crcs, serials) -> {
            String name = f.get("name");
            if (name == null || !seen.add(name)) return;
            Entry e = new Entry(name);
            if (!crcs.isEmpty()) { e.crc = crcs.get(0); for (String c : crcs) byCrc.putIfAbsent(c, e); }
            e.serial = f.containsKey("serial") ? f.get("serial") : serials.isEmpty() ? null : serials.get(0);
            entries.add(e);
        });
    }

    private void readDetails(String text) {
        blocks(text, (f, crcs, serials) -> {
            List<String> keys = new ArrayList<>();
            for (String c : crcs) keys.add("crc:" + c);
            if (f.containsKey("serial")) keys.add("serial:" + f.get("serial"));
            for (String s : serials) keys.add("serial:" + s);
            for (String n : new String[] { f.get("name"), f.get("comment") }) if (n != null) keys.add("name:" + norm(n));
            for (String k : keys) {
                Map<String, String> d = details.computeIfAbsent(k, x -> new HashMap<>());
                for (String field : new String[] { "developer", "publisher", "genre", "releaseyear", "description" })
                    if (f.containsKey(field) && !d.containsKey(field)) d.put(field, f.get(field));
            }
        });
    }

    /** Year, developer, publisher, genre and description, where libretro knows them. */
    Map<String, String> details(String name) {
        Map<String, String> out = new HashMap<>();
        Entry e = null;
        for (Entry x : entries) if (x.name.equals(name)) { e = x; break; }
        List<String> keys = new ArrayList<>();
        if (e != null && e.crc != null) keys.add("crc:" + e.crc);
        if (e != null && e.serial != null) keys.add("serial:" + e.serial);
        keys.add("name:" + norm(name));
        for (String k : keys) { Map<String, String> d = details.get(k); if (d != null) for (Map.Entry<String, String> f : d.entrySet()) out.putIfAbsent(f.getKey(), f.getValue()); }
        return out;
    }

    // ---------- matching ----------

    static String stripTags(String s) { return s.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " "); }

    /**
     * A catalogue name as people (and the other art and details services) write it: libretro's and No-Intro's
     * "Legend of Zelda, The - A Link to the Past (USA)" → "The Legend of Zelda: A Link to the Past". Searching the
     * services with the catalogue's order found the wrong game or none (the user, after 0.26: box backs for Zelda).
     */
    static String searchName(String s) {
        String t = stripTags(s).replaceAll("\\s+", " ").trim();
        Matcher m = ARTICLE.matcher(t);
        if (m.matches()) t = m.group(2) + " " + m.group(1) + (m.group(3) == null ? "" : m.group(3));
        return t.replace(" - ", ": ");
    }
    private static final Pattern ARTICLE = Pattern.compile("^(.+?), (The|A|An|Die|Der|Das|Le|La|Les|El|Los|Il)( - .*|: .*)?$");

    /** Lower case, "&" as "and", everything that isn't a letter or digit as a single space. */
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("['’]", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static final class Hit { final Entry e; final double score; Hit(Entry e, double s) { this.e = e; this.score = s; } }

    /** The closest names to a search, best first. A file name with region tags ranks that region first. */
    List<Hit> search(String query, int max) {
        String q = norm(stripTags(query)), tags = query.toLowerCase(Locale.ROOT);
        String[] qw = q.isEmpty() ? new String[0] : q.split(" ");
        List<Hit> hits = new ArrayList<>();
        for (Entry e : entries) {
            double s = dice(qw, e.words);
            if (s < .3) continue;
            if (e.base.equals(q)) s += .3;
            String n = e.name.toLowerCase(Locale.ROOT);
            for (String region : new String[] { "usa", "europe", "japan", "world" }) if (tags.contains(region) && n.contains(region)) s += .08;
            s += n.contains("usa") ? .04 : n.contains("world") ? .03 : n.contains("europe") ? .02 : 0;
            Matcher qd = DISC.matcher(tags), ed = DISC.matcher(n);
            if (qd.find() && ed.find() && qd.group(1).equals(ed.group(1))) s += .1;
            if (n.matches(".*\\((beta|proto|demo|sample|kiosk|debug|pirate|unl|hack|aftermarket)[^)]*\\).*") && !tags.matches(".*(beta|proto|demo|sample|kiosk|debug|pirate|unl|hack|aftermarket).*")) s -= .15;
            if (n.contains("(rev ") || n.contains("(alt")) s -= .01;
            hits.add(new Hit(e, s));
        }
        hits.sort((a, b) -> Double.compare(b.score, a.score));
        return hits.size() > max ? new ArrayList<>(hits.subList(0, max)) : hits;
    }
    private static final Pattern DISC = Pattern.compile("disc (\\d+)");

    /** How many words two titles share, 0 to 1 (Dice's coefficient over words, so word order doesn't matter). */
    static double dice(String[] a, String[] b) {
        if (a.length == 0 || b.length == 0) return 0;
        Map<String, Integer> count = new HashMap<>();
        for (String w : a) count.merge(w, 1, Integer::sum);
        int common = 0;
        for (String w : b) { Integer c = count.get(w); if (c != null && c > 0) { common++; count.put(w, c - 1); } }
        return 2.0 * common / (a.length + b.length);
    }

    /** The exact game, by the ROM's checksum, for cartridge-sized files (and zips, from their header). */
    Entry byChecksum(File rom) {
        String crc = checksum(rom);
        return crc == null ? null : byCrc.get(crc);
    }

    static String checksum(File f) {
        try {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                try (ZipFile z = new ZipFile(f)) {
                    ZipEntry best = null;
                    for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) { ZipEntry x = en.nextElement(); if (!x.isDirectory() && (best == null || x.getSize() > best.getSize())) best = x; }
                    return best == null || best.getCrc() < 0 ? null : String.format(Locale.ROOT, "%08X", best.getCrc());
                }
            }
            if (f.length() > 64L * 1024 * 1024) return null;   // discs are too big to read through quickly
            CRC32 crc = new CRC32();
            byte[] buf = new byte[1 << 16];
            try (InputStream in = new FileInputStream(f)) { int n; while ((n = in.read(buf)) > 0) crc.update(buf, 0, n); }
            return String.format(Locale.ROOT, "%08X", crc.getValue());
        } catch (IOException e) { return null; }
    }

    // ---------- art and downloads ----------

    /** Where libretro keeps one kind of art for a game: Named_Boxarts, Named_Titles or Named_Snaps. */
    String artUrl(String kind, String name) {
        String file = name.replaceAll("[&*/:`<>?\\\\|\"]", "_");
        return THUMBS + SYSTEM.get(sys).replace(' ', '_') + "/master/" + kind + "/" + enc(file) + ".png";
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    /** Downloads a URL, or returns null when it isn't there. */
    static byte[] download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Shelf (Android game launcher)");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1 << 16]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }

    /** A text file, from the cache when it's recent enough, otherwise downloaded (falling back to an old copy). */
    private static String cached(String url, File file) {
        try {
            if (!file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE) {
                byte[] b = download(url);
                if (b != null) { file.getParentFile().mkdirs(); Files.write(file.toPath(), b); }
                else if (!file.exists()) return null;
            }
        } catch (IOException e) { if (!file.exists()) return null; }
        try { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); } catch (IOException e) { return null; }
    }
}
