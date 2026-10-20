#!/usr/bin/env python3
"""Fetch box art, labels and game info for every ROM on your SD card.

    python fetch_info.py E:\\

For each ROM in /roms/nes, /roms/gb and /roms/gbc it:
  1. Identifies the game by its checksum, using RetroArch's free game database
     (falls back to matching the file name).
  2. Downloads box art (and, if you like, title screens) from RetroArch's thumbnail server.
  3. Collects release year, developer, publisher and genre from the same database,
     plus a short description from Wikipedia.
Everything is written to the SD card in the formats Shelf reads:
  /shelf/art/<system>/<rom name>.box.img and .label.img
  /shelf/info/<system>/<rom name>.json

Art you've already added yourself is never replaced unless you pass --force.
Needs Pillow (pip install pillow) and an internet connection.
"""
import argparse
import io
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
try:
    from PIL import Image
except ImportError:
    sys.exit("This Python doesn't have Pillow (" + sys.executable + ").\n"
             "Install it for this same Python with:\n  \"" + sys.executable + "\" -m pip install pillow\n"
             "If that fails, your Python may be too new for Pillow. Use Python 3.11 instead:\n"
             "  py -V:3.11 -m pip install pillow\n  py -V:3.11 fetch_info.py <SD card>")
try:
    import convert_art   # same sizes and file format as the manual converter
except ImportError:
    sys.exit("convert_art.py is missing. It has to be in the same folder as fetch_info.py (" + HERE + ").")

SYSTEMS = {
    "nes": {"db": "Nintendo - Nintendo Entertainment System", "exts": (".nes",), "word": "NES"},
    "gb": {"db": "Nintendo - Game Boy", "exts": (".gb",), "word": "Game Boy"},
    "gbc": {"db": "Nintendo - Game Boy Color", "exts": (".gbc",), "word": "Game Boy Color"},
}
FIELDS = ("releaseyear", "developer", "publisher", "genre")
DB_URL = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/{field}/{system}.dat"
THUMB_URLS = (
    "https://thumbnails.libretro.com/{system}/{kind}/{name}.png",
    "https://raw.githubusercontent.com/libretro-thumbnails/{repo}/master/{kind}/{name}.png",
)
WIKI_SEARCH = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&srsearch={q}"
WIKI_SUMMARY = "https://en.wikipedia.org/api/rest_v1/page/summary/{title}"
USER_AGENT = "ShelfFetcher/1.0 (Odroid GO launcher; personal use)"
CACHE = os.path.join(HERE, ".fetch-cache")


# ---------- small helpers ----------
def http_get(url, timeout=20):
    """Returns the body as bytes, or None if it isn't there."""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read()
    except Exception:
        return None


def cached_get(url, name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if os.path.isfile(path) and time.time() - os.path.getmtime(path) < 14 * 86400:
        return open(path, "rb").read()
    data = http_get(url, timeout=60)
    if data:
        open(path, "wb").write(data)
    elif os.path.isfile(path):
        data = open(path, "rb").read()   # offline: use the old copy
    return data


def to_ascii(text):
    """Shelf's pixel font covers plain ASCII; turn curly quotes, dashes and accents into it."""
    text = (text or "").replace("\u2019", "'").replace("\u2018", "'").replace("\u201c", '"').replace("\u201d", '"')
    text = text.replace("\u2013", "-").replace("\u2014", " - ").replace("\u2026", "...").replace("\u00d7", "x")
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"\s+", " ", text).strip()


def norm(name):
    """Comparable form of a game name: no tags, articles, punctuation or case."""
    name = re.sub(r"\s*[\(\[].*", "", name).lower()
    name = re.sub(r",\s*(the|a|an)\b", "", name)
    name = re.sub(r"^(the|a|an)\s+", "", name)
    return re.sub(r"[^a-z0-9]+", "", name)


def display_title(name):
    """'Legend of Zelda, The - Link's Awakening (USA)' -> 'The Legend of Zelda - Link's Awakening'"""
    t = re.sub(r"\s*[\(\[].*", "", name).strip()
    m = re.match(r"^(.*?), (The|A|An)( - .*)?$", t)
    if m:
        t = f"{m.group(2)} {m.group(1)}{m.group(3) or ''}"
    return t


def thumb_name(name):
    return re.sub(r'[&*/:`<>?\\|"]', "_", name)


REGION_RANK = ("(USA", "(World", "(Europe", "(USA, Europe")


def region_score(name):
    for i, r in enumerate(REGION_RANK):
        if r in name:
            return i
    return len(REGION_RANK) + (5 if "(Beta" in name or "(Proto" in name else 0)


# ---------- RetroArch's database ----------
def parse_dat(text, field):
    """Yields (name, crc, value) from a clrmamepro .dat file."""
    for block in re.finditer(r"game \((.*?)\n\)", text, re.S):
        body = block.group(1)
        name = re.search(r'(?:comment|name) "([^"]*)"', body)
        crc = re.search(r"crc ([0-9A-Fa-f]{8})", body)
        value = re.search(rf'\b{field} "([^"]*)"', body) if field else None
        if name:
            yield name.group(1), (crc.group(1).upper() if crc else None), (value.group(1) if value else None)


def load_database(system):
    """Returns ({crc: name}, {name: {field: value}}) for one system."""
    by_crc, info = {}, {}
    for field in FIELDS:
        url = DB_URL.format(field=field, system=urllib.parse.quote(system))
        data = cached_get(url, f"{system} - {field}.dat")
        if not data:
            print(f"  (no {field} list for {system}, skipping it)")
            continue
        for name, crc, value in parse_dat(data.decode("utf-8", "replace"), field):
            if crc:
                by_crc.setdefault(crc, name)
            if value:
                info.setdefault(name, {})[field] = value
    return by_crc, info


# ---------- identifying ROMs ----------
def rom_crcs(path, exts):
    """CRC32 of the ROM, plus the headerless CRC for NES files (the database may use either)."""
    try:
        if path.lower().endswith(".zip"):
            with zipfile.ZipFile(path) as z:
                inner = [n for n in z.namelist() if n.lower().endswith(exts)] or z.namelist()
                data = z.read(inner[0])
        else:
            data = open(path, "rb").read()
    except Exception:
        return []
    crcs = [format(zlib.crc32(data) & 0xFFFFFFFF, "08X")]
    if data[:4] == b"NES\x1a" and len(data) > 16:
        crcs.append(format(zlib.crc32(data[16:]) & 0xFFFFFFFF, "08X"))
    return crcs


def identify(path, base, exts, dbs):
    for crc in rom_crcs(path, exts):
        for by_crc, _ in dbs:
            if crc in by_crc:
                return by_crc[crc], "checksum"
    want = norm(base)
    names = {n for by_crc, info in dbs for n in list(by_crc.values()) + list(info)}
    hits = sorted((n for n in names if norm(n) == want), key=region_score)
    if hits:
        return hits[0], "name"
    return None, None


# ---------- art ----------
def fetch_thumb(system, kind, name):
    for pattern in THUMB_URLS:
        url = pattern.format(system=urllib.parse.quote(system), repo=urllib.parse.quote(system.replace(" ", "_")),
                             kind=kind, name=urllib.parse.quote(thumb_name(name)))
        data = http_get(url)
        if data:
            try:
                return convert_art.load(io.BytesIO(data))
            except Exception:
                pass
        time.sleep(0.1)
    return None


def crop_to(img, w, h):
    """Cuts the top of an image to the label's shape (box art usually has its best part up top)."""
    want = w / h
    if img.width / img.height > want:
        cw = int(img.height * want)
        return img.crop(((img.width - cw) // 2, 0, (img.width - cw) // 2 + cw, img.height))
    return img.crop((0, 0, img.width, int(img.width / want)))


def write_img(img, path):
    with open(path, "wb") as f:
        f.write(b"SHL1" + convert_art.struct.pack("<HH", img.width, img.height) + convert_art.to_rgb565(img))


# ---------- descriptions ----------
def wiki_description(title, word):
    for query in (f'"{title}" {word} video game', f"{title} video game"):
        data = http_get(WIKI_SEARCH.format(q=urllib.parse.quote(query)))
        if not data:
            return None
        try:
            hits = json.loads(data)["query"]["search"]
        except Exception:
            return None
        for hit in hits[:5]:
            time.sleep(0.1)
            s = http_get(WIKI_SUMMARY.format(title=urllib.parse.quote(hit["title"].replace(" ", "_"))))
            if not s:
                continue
            try:
                page = json.loads(s)
            except Exception:
                continue
            desc = (page.get("description") or "").lower()
            if page.get("type") == "standard" and "video game" in desc and norm(title)[:6] in norm(page.get("title", "")):
                return shorten(to_ascii(page.get("extract", "")), 700)
    return None


def shorten(text, limit):
    if len(text) <= limit:
        return text
    cut = text[:limit]
    end = cut.rfind(". ")
    return cut[: end + 1] if end > limit // 2 else cut.rstrip() + "..."


# ---------- main ----------
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("sd", help="the SD card's root folder, for example E:\\")
    ap.add_argument("--labels", choices=("box", "title", "none"), default="box",
                    help="what to put on cartridge labels: the top of the box art (default), the title screen, or nothing")
    ap.add_argument("--no-art", action="store_true", help="only fetch info, no images")
    ap.add_argument("--no-info", action="store_true", help="only fetch images, no info")
    ap.add_argument("--no-descriptions", action="store_true", help="skip Wikipedia descriptions")
    ap.add_argument("--force", action="store_true", help="replace art and info that already exist")
    ap.add_argument("--only", help="limit to some systems, for example: nes,gb")
    args = ap.parse_args()

    only = set(args.only.split(",")) if args.only else set(SYSTEMS)
    totals = {"games": 0, "matched": 0, "box": 0, "label": 0, "info": 0}
    unmatched = []
    dbs = {}
    for key, spec in SYSTEMS.items():
        if key in only:
            print(f"Loading the {spec['db']} database...")
            dbs[key] = load_database(spec["db"])
    for key, spec in SYSTEMS.items():
        rom_dir = os.path.join(args.sd, "roms", key)
        if key not in only or not os.path.isdir(rom_dir):
            continue
        art_dir = os.path.join(args.sd, "shelf", "art", key)
        info_dir = os.path.join(args.sd, "shelf", "info", key)
        os.makedirs(art_dir, exist_ok=True)
        os.makedirs(info_dir, exist_ok=True)
        # a Game Boy game might be listed under Game Boy Color, or the other way round
        search = [dbs[key]] + ([dbs[o] for o in ("gb", "gbc") if o != key and o in dbs] if key in ("gb", "gbc") else [])
        for f in sorted(os.listdir(rom_dir)):
            if not f.lower().endswith(spec["exts"] + (".zip",)) or f.startswith("."):
                continue
            totals["games"] += 1
            base = os.path.splitext(f)[0]
            name, how = identify(os.path.join(rom_dir, f), base, spec["exts"], search)
            if not name:
                print(f"  {key}/{base}: not found in the database")
                unmatched.append(f"{key}/{f}")
                continue
            totals["matched"] += 1
            got = []
            box_path = os.path.join(art_dir, base + ".box.img")
            label_path = os.path.join(art_dir, base + ".label.img")
            if not args.no_art:
                box = None
                if args.force or not os.path.exists(box_path):
                    box = fetch_thumb(spec["db"], "Named_Boxarts", name)
                    if box:
                        write_img(convert_art.fit(box, *convert_art.FRAMES[key]["box"]), box_path)
                        totals["box"] += 1
                        got.append("box")
                if args.labels != "none" and (args.force or not os.path.exists(label_path)):
                    lw, lh = convert_art.FRAMES[key]["label"]
                    src = box if args.labels == "box" else None
                    if args.labels == "box" and src is None:
                        src = fetch_thumb(spec["db"], "Named_Boxarts", name)
                    if args.labels == "title":
                        src = fetch_thumb(spec["db"], "Named_Titles", name)
                    if src:
                        write_img(convert_art.fit(crop_to(src, lw, lh), lw, lh), label_path)
                        totals["label"] += 1
                        got.append("label")
            info_path = os.path.join(info_dir, base + ".json")
            if not args.no_info and (args.force or not os.path.exists(info_path)):
                fields = {}
                for _, info in search:
                    fields = info.get(name) or fields
                    if fields:
                        break
                record = {
                    "year": to_ascii(fields.get("releaseyear", "")),
                    "developer": to_ascii(fields.get("developer", "")),
                    "publisher": to_ascii(fields.get("publisher", "")),
                    "genre": to_ascii(fields.get("genre", "")),
                }
                if not args.no_descriptions:
                    desc = wiki_description(display_title(name), spec["word"])
                    if desc:
                        record["description"] = desc
                        record["source"] = "Wikipedia"
                if any(record.values()):
                    with open(info_path, "w", encoding="ascii") as out:
                        json.dump(record, out, indent=1)
                    totals["info"] += 1
                    got.append("info" + (" + description" if "description" in record else ""))
            print(f"  {key}/{base}: {name} (by {how})" + (f" -> {', '.join(got)}" if got else ", nothing new"))
    print(f"\n{totals['games']} games, {totals['matched']} identified. "
          f"Added {totals['box']} boxes, {totals['label']} labels and info for {totals['info']}.")
    if unmatched:
        print("Not identified (give these art by hand with convert_art.py, or rename them to their standard names):")
        for u in unmatched:
            print("  " + u)


if __name__ == "__main__":
    main()
