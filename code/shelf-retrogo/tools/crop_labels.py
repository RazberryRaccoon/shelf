#!/usr/bin/env python3
"""Crop cartridge labels out of whole-cartridge pictures, for Shelf.

    py -V:3.11 crop_labels.py C:\\my-carts E:\\

Put your cartridge pictures in a folder, ideally in nes, gb and gbc subfolders, and name them
roughly after the game. For each picture the window guesses where the label is; drag to fix it
and press Enter. The label goes straight onto the SD card as /shelf/art/<system>/<rom>.label.img,
and a copy of the crop is kept in <your folder>/cropped/ in case you want it again.

Mouse: drag inside the box to move it, drag its edges or corners to resize, drag outside to draw a new one.
Keys:  Enter save and go to the next picture   Page Down skip   Page Up go back
       A guess the label again   R rotate   L lock to Shelf's label shape
       arrows nudge the box (hold Shift to resize it)
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
try:
    from PIL import Image, ImageFilter, ImageOps, ImageEnhance
except ImportError:
    sys.exit("This Python doesn't have Pillow. Install it with:  py -V:3.11 -m pip install pillow")
import convert_art

SYSTEMS = ("nes", "gb", "gbc")
ROM_EXTS = {"nes": (".nes", ".zip"), "gb": (".gb", ".zip"), "gbc": (".gbc", ".zip")}
IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif")
PROGRESS = ".label-crops.json"


# ---------- matching pictures to ROMs ----------
def norm(name):
    name = re.sub(r"\s*[\(\[].*", "", name).lower()
    name = re.sub(r",\s*(the|a|an)\b", "", name)
    name = re.sub(r"^(the|a|an)\s+", "", name)
    name = re.sub(r"\b(cart|cartridge|label|front|nes|gbc|gb|game ?boy|color|usa|europe|japan|scan)\b", "", name)
    return re.sub(r"[^a-z0-9]+", "", name)


def words(name):
    name = re.sub(r"\s*[\(\[].*", "", name).lower()
    return {w for w in re.split(r"[^a-z0-9]+", name) if len(w) > 1 and w not in ("the", "cart", "cartridge", "label", "nes", "gb", "gbc")}


def find_roms(sd):
    """['nes/Super Mario Bros. (World)', ...] for every ROM on the card."""
    roms = []
    for s in SYSTEMS:
        d = os.path.join(sd, "roms", s)
        if os.path.isdir(d):
            roms += [f"{s}/{os.path.splitext(f)[0]}" for f in sorted(os.listdir(d))
                     if f.lower().endswith(ROM_EXTS[s]) and not f.startswith(".")]
    return roms


def match_rom(picture, roms, system=None):
    """The ROM a picture's file name most likely belongs to, or None."""
    base = os.path.splitext(os.path.basename(picture))[0]
    pool = [r for r in roms if system is None or r.startswith(system + "/")] or roms
    want = norm(base)
    for r in pool:
        if want and norm(r.split("/", 1)[1]) == want:
            return r
    w = words(base)
    best, score = None, 0.0
    for r in pool:
        rw = words(r.split("/", 1)[1])
        if not rw or not w:
            continue
        s = len(w & rw) / len(w | rw)
        if s > score:
            best, score = r, s
    return best if score >= 0.5 else None


def find_pictures(folder):
    """[(relative path, system or None)] for every picture in the folder and its nes/gb/gbc subfolders."""
    out = []
    for root, dirs, files in os.walk(folder):
        dirs[:] = [d for d in dirs if d != "cropped" and not d.startswith(".")]
        rel_root = os.path.relpath(root, folder)
        system = rel_root.split(os.sep)[0].lower() if rel_root != "." else None
        for f in sorted(files):
            if f.lower().endswith(IMAGE_EXTS):
                out.append((os.path.normpath(os.path.join(rel_root, f)), system if system in SYSTEMS else None))
    return out


# ---------- finding the label ----------
def load_picture(path):
    img = ImageOps.exif_transpose(Image.open(path))
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
        bg.alpha_composite(img)
        img = bg
    return img.convert("RGB")


def detect_label(img):
    """A guess at the label's box (x0, y0, x1, y1) in the picture.

    Labels are where the color and detail are; cartridge plastic and photo backgrounds are flat and
    gray-ish. So: score every pixel on saturation plus local detail, keep the high scorers, and take
    the biggest connected patch."""
    small = img.copy()
    small.thumbnail((180, 180))
    w, h = small.size
    px = small.load()
    corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]
    bg = tuple(sorted(c[i] for c in corners)[1] for i in range(3))
    detail = small.convert("L").filter(ImageFilter.FIND_EDGES).filter(ImageFilter.BoxBlur(2)).load()
    # Where the cartridge is: everything that isn't background, minus a thin band along its outline.
    # The outline is sharp "detail" too, and would otherwise link up into a ring around the whole cartridge.
    fg = Image.new("L", (w, h), 0)
    fp = fg.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            if abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) >= 36:
                fp[x, y] = 255
    fg = fg.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))   # fill specks inside
    inner = fg.filter(ImageFilter.MinFilter(9)).load()
    score = [[0.0] * w for _ in range(h)]
    values = []
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            if not inner[x, y]:
                continue   # background, or the cartridge's outline
            s = (max(r, g, b) - min(r, g, b)) / 255 * 0.7 + min(1.0, detail[x, y] / 60) * 0.3
            score[y][x] = s
            values.append(s)
    if not values:
        return center_box(img)
    values.sort()
    cut = max(0.22, values[int(len(values) * 0.5)])   # the more colorful half of the cartridge
    mask = Image.new("L", (w, h), 0)
    mp = mask.load()
    for y in range(h):
        for x in range(w):
            if score[y][x] >= cut:
                mp[x, y] = 255
    mask = mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(5))   # close small gaps
    mp = mask.load()
    seen = [[False] * w for _ in range(h)]
    best = None
    for y in range(h):
        for x in range(w):
            if mp[x, y] and not seen[y][x]:
                stack, n = [(x, y)], 0
                seen[y][x] = True
                x0 = x1 = x
                y0 = y1 = y
                while stack:
                    cx, cy = stack.pop()
                    n += 1
                    x0, x1, y0, y1 = min(x0, cx), max(x1, cx), min(y0, cy), max(y1, cy)
                    for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                        if 0 <= nx < w and 0 <= ny < h and mp[nx, ny] and not seen[ny][nx]:
                            seen[ny][nx] = True
                            stack.append((nx, ny))
                if best is None or n > best[0]:
                    best = (n, x0, y0, x1 + 1, y1 + 1)
    if best is None or best[0] < w * h * 0.04:
        return center_box(img)
    k = img.width / w
    return (best[1] * k, best[2] * k, best[3] * k, best[4] * k)


def center_box(img):
    return (img.width * 0.2, img.height * 0.2, img.width * 0.8, img.height * 0.65)


# ---------- saving ----------
def label_from(img, box, system):
    x0, y0, x1, y1 = [int(round(v)) for v in box]
    crop = img.crop((max(0, x0), max(0, y0), min(img.width, x1), min(img.height, y1)))
    return crop, convert_art.fit(crop, *convert_art.FRAMES[system]["label"])


def save_label(img, box, rom, sd, folder):
    """Writes the label to the card and keeps a full-size copy of the crop. Returns the small label."""
    system, base = rom.split("/", 1)
    crop, small = label_from(img, box, system)
    art_dir = os.path.join(sd, "shelf", "art", system)
    os.makedirs(art_dir, exist_ok=True)
    with open(os.path.join(art_dir, base + ".label.img"), "wb") as f:
        f.write(b"SHL1" + convert_art.struct.pack("<HH", small.width, small.height) + convert_art.to_rgb565(small))
    keep = os.path.join(folder, "cropped", system)
    os.makedirs(keep, exist_ok=True)
    crop.save(os.path.join(keep, base + ".label.png"))
    return small


def cart_preview(label, system, scale=2):
    """Roughly what the cartridge will look like in Shelf, with this label in it."""
    home = system == "nes"
    w, h = (132, 148) if home else (112, 128)
    lx, ly, lw, lh = (13, 26, 106, 88) if home else (11, 20, 90, 78)
    body = (133, 135, 143) if home else (170, 171, 179)
    img = Image.new("RGB", (w, h), (24, 26, 40))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=3 if home else 6, fill=body, outline=(60, 62, 70))
    for k in range(6 if home else 4):
        y = 5 + k * 3
        d.line((12, y, w - 13, y), fill=tuple(int(c * 0.7) for c in body))
    if label is not None:
        q = convert_art.fit(label, lw, lh)
        x, y = lx + (lw - q.width) // 2, ly + (lh - q.height) // 2
        d.rectangle((x - 2, y - 2, x + q.width + 1, y + q.height + 1), fill=tuple(int(c * 0.5) for c in body))
        img.paste(q, (x, y))
    return img.resize((w * scale, h * scale), Image.NEAREST)


def load_progress(folder):
    try:
        return json.load(open(os.path.join(folder, PROGRESS), encoding="utf-8"))
    except Exception:
        return {}


def save_progress(folder, progress):
    with open(os.path.join(folder, PROGRESS), "w", encoding="utf-8") as f:
        json.dump(progress, f, indent=1)


# ---------- the window ----------
def run_window(folder, sd):
    try:
        import tkinter as tk
        from tkinter import ttk, messagebox
        from PIL import ImageTk
    except ImportError:
        sys.exit("This Python doesn't include Tkinter, which the window needs. The Python 3.11 from python.org "
                 "or 'py install 3.11' includes it.")
    pictures = find_pictures(folder)
    roms = find_roms(sd)
    if not pictures:
        sys.exit(f"No pictures found in {folder}.")
    if not roms:
        sys.exit(f"No ROMs found in {os.path.join(sd, 'roms')}. Is {sd} the SD card?")
    progress = load_progress(folder)

    root = tk.Tk()
    root.title("Shelf label cropper")
    root.configure(bg="#141a34")
    CW, CH = 860, 640
    canvas = tk.Canvas(root, width=CW, height=CH, bg="#0a0c1a", highlightthickness=0, cursor="crosshair")
    canvas.grid(row=0, column=0, rowspan=2, padx=10, pady=10)
    side = tk.Frame(root, bg="#141a34")
    side.grid(row=0, column=1, sticky="n", padx=(0, 12), pady=10)
    title = tk.Label(side, bg="#141a34", fg="#fff3d6", font=("Segoe UI", 12, "bold"), anchor="w", justify="left", wraplength=290)
    title.pack(fill="x")
    tk.Label(side, text="Game:", bg="#141a34", fg="#b4bdd8", anchor="w").pack(fill="x", pady=(10, 0))
    rom_var = tk.StringVar()
    combo = ttk.Combobox(side, textvariable=rom_var, values=roms, width=44, state="readonly")
    combo.pack(fill="x")
    preview = tk.Canvas(side, width=270, height=300, bg="#0a0c1a", highlightthickness=0)
    preview.pack(pady=10)
    status = tk.Label(side, bg="#141a34", fg="#ffd25a", anchor="w", justify="left", wraplength=290)
    status.pack(fill="x")
    buttons = tk.Frame(side, bg="#141a34")
    buttons.pack(fill="x", pady=10)

    st = {"i": 0, "img": None, "box": None, "scale": 1, "ox": 0, "oy": 0, "rot": 0, "lock": False,
          "drag": None, "base": None, "dark": None, "photo": None, "pphoto": None}

    def first_unfinished():
        for i, (rel, _) in enumerate(pictures):
            if rel not in progress:
                return i
        return 0

    def system_of():
        r = rom_var.get()
        return r.split("/", 1)[0] if "/" in r else (pictures[st["i"]][1] or "nes")

    def open_picture(i):
        st["i"] = i % len(pictures)
        rel, system = pictures[st["i"]]
        saved = progress.get(rel, {})
        st["rot"] = saved.get("rot", 0)
        img = load_picture(os.path.join(folder, rel))
        st["img"] = img.rotate(st["rot"], expand=True) if st["rot"] else img
        rom = saved.get("rom") or match_rom(rel, roms, system) or ""
        rom_var.set(rom)
        st["box"] = list(saved["box"]) if saved.get("box") else list(detect_label(st["img"]))
        title.config(text=f"Picture {st['i'] + 1} of {len(pictures)}\n{rel}")
        status.config(text=("Already saved. Enter saves it again." if rel in progress else
                            "" if rom else "Pick which game this is from the list."))
        rescale()

    def rescale():
        img = st["img"]
        s = min(CW / img.width, CH / img.height, 4)
        st["scale"] = s
        dw, dh = max(1, int(img.width * s)), max(1, int(img.height * s))
        st["ox"], st["oy"] = (CW - dw) // 2, (CH - dh) // 2
        st["base"] = img.resize((dw, dh), Image.LANCZOS)
        st["dark"] = ImageEnhance.Brightness(st["base"]).enhance(0.35)
        redraw()

    def to_canvas(v, axis):
        return v * st["scale"] + (st["ox"] if axis == 0 else st["oy"])

    def to_image(v, axis):
        return (v - (st["ox"] if axis == 0 else st["oy"])) / st["scale"]

    def clamp_box():
        img, b = st["img"], st["box"]
        b[0], b[2] = sorted((min(max(b[0], 0), img.width), min(max(b[2], 0), img.width)))
        b[1], b[3] = sorted((min(max(b[1], 0), img.height), min(max(b[3], 0), img.height)))
        if b[2] - b[0] < 4: b[2] = min(img.width, b[0] + 4)
        if b[3] - b[1] < 4: b[3] = min(img.height, b[1] + 4)

    def apply_lock(anchor_x=None, anchor_y=None):
        if not st["lock"]:
            return
        lw, lh = convert_art.FRAMES[system_of()]["label"]
        b = st["box"]
        w = b[2] - b[0]
        h = w * lh / lw
        if anchor_y == "top":
            b[1] = b[3] - h
        else:
            b[3] = b[1] + h

    def redraw():
        clamp_box()
        b = st["box"]
        cx0, cy0, cx1, cy1 = [int(round(v)) for v in (b[0] * st["scale"], b[1] * st["scale"], b[2] * st["scale"], b[3] * st["scale"])]
        comp = st["dark"].copy()
        comp.paste(st["base"].crop((cx0, cy0, cx1, cy1)), (cx0, cy0))
        st["photo"] = ImageTk.PhotoImage(comp)
        canvas.delete("all")
        canvas.create_image(st["ox"], st["oy"], image=st["photo"], anchor="nw")
        x0, y0, x1, y1 = cx0 + st["ox"], cy0 + st["oy"], cx1 + st["ox"], cy1 + st["oy"]
        canvas.create_rectangle(x0, y0, x1, y1, outline="#ffd25a", width=2)
        for hx in (x0, (x0 + x1) // 2, x1):
            for hy in (y0, (y0 + y1) // 2, y1):
                if (hx, hy) != ((x0 + x1) // 2, (y0 + y1) // 2):
                    canvas.create_rectangle(hx - 4, hy - 4, hx + 4, hy + 4, fill="#ffd25a", outline="#141a34")
        lock = "  (shape locked)" if st["lock"] else ""
        canvas.create_text(10, CH - 12, anchor="w", fill="#b4bdd8",
                           text=f"{int(b[2] - b[0])} x {int(b[3] - b[1])} pixels{lock}")
        _, small = label_from(st["img"], b, system_of())
        st["pphoto"] = ImageTk.PhotoImage(cart_preview(small, system_of()))
        preview.delete("all")
        preview.create_image(135, 150, image=st["pphoto"])

    def press(e):
        canvas.focus_set()
        b = [to_canvas(st["box"][0], 0), to_canvas(st["box"][1], 1), to_canvas(st["box"][2], 0), to_canvas(st["box"][3], 1)]
        near = lambda a, v: abs(a - v) <= 8
        edges = ("l" if near(e.x, b[0]) else "r" if near(e.x, b[2]) else "") + ("t" if near(e.y, b[1]) else "b" if near(e.y, b[3]) else "")
        inside_x, inside_y = b[0] - 8 <= e.x <= b[2] + 8, b[1] - 8 <= e.y <= b[3] + 8
        if edges and inside_x and inside_y:
            st["drag"] = ("resize", edges)
        elif b[0] < e.x < b[2] and b[1] < e.y < b[3]:
            st["drag"] = ("move", (to_image(e.x, 0), to_image(e.y, 1), list(st["box"])))
        else:
            x, y = to_image(e.x, 0), to_image(e.y, 1)
            st["box"] = [x, y, x + 1, y + 1]
            st["drag"] = ("resize", "rb")

    def motion(e):
        if not st["drag"]:
            return
        kind, info = st["drag"]
        x, y = to_image(e.x, 0), to_image(e.y, 1)
        b = st["box"]
        if kind == "move":
            sx, sy, start = info
            w, h = start[2] - start[0], start[3] - start[1]
            nx = min(max(start[0] + x - sx, 0), st["img"].width - w)
            ny = min(max(start[1] + y - sy, 0), st["img"].height - h)
            st["box"] = [nx, ny, nx + w, ny + h]
        else:
            if "l" in info: b[0] = x
            if "r" in info: b[2] = x
            if "t" in info: b[1] = y
            if "b" in info: b[3] = y
            apply_lock(anchor_y="top" if "t" in info and "b" not in info else None)
        redraw()

    def release(e):
        st["drag"] = None

    def nudge(dx, dy, resize):
        b = st["box"]
        step = max(1, 1 / st["scale"])
        if resize:
            b[2] += dx * step
            b[3] += dy * step
            apply_lock()
        else:
            b[0] += dx * step; b[2] += dx * step
            b[1] += dy * step; b[3] += dy * step
        redraw()

    def save_next(e=None):
        rom = rom_var.get()
        if not rom:
            status.config(text="Pick which game this picture is for first.")
            return
        rel = pictures[st["i"]][0]
        save_label(st["img"], st["box"], rom, sd, folder)
        progress[rel] = {"rom": rom, "box": [round(v, 1) for v in st["box"]], "rot": st["rot"]}
        save_progress(folder, progress)
        if len(progress) >= len(pictures) and all(p[0] in progress for p in pictures):
            messagebox.showinfo("All done", f"Saved labels for all {len(pictures)} pictures.\n"
                                "Put the SD card back in the Odroid GO to see them.")
        open_picture(st["i"] + 1)

    def rotate(e=None):
        st["rot"] = (st["rot"] + 90) % 360
        st["img"] = st["img"].rotate(90, expand=True)
        st["box"] = list(detect_label(st["img"]))
        rescale()

    def toggle_lock(e=None):
        st["lock"] = not st["lock"]
        apply_lock()
        redraw()

    def reguess(e=None):
        st["box"] = list(detect_label(st["img"]))
        redraw()

    def on_combo(e=None):
        canvas.focus_set()
        status.config(text="")
        redraw()

    for text, cmd in (("Guess the label again (A)", reguess), ("Rotate (R)", rotate),
                      ("Lock to Shelf's label shape (L)", toggle_lock), ("Back (Page Up)", lambda: open_picture(st["i"] - 1)),
                      ("Skip (Page Down)", lambda: open_picture(st["i"] + 1)), ("Save and next (Enter)", save_next)):
        tk.Button(buttons, text=text, command=lambda c=cmd: (c(), canvas.focus_set()), bg="#283058", fg="#ffffff",
                  activebackground="#ffd25a", relief="flat", padx=8, pady=4).pack(fill="x", pady=2)
    combo.bind("<<ComboboxSelected>>", on_combo)
    canvas.bind("<ButtonPress-1>", press)
    canvas.bind("<B1-Motion>", motion)
    canvas.bind("<ButtonRelease-1>", release)
    canvas.bind("<Return>", save_next)
    canvas.bind("<Next>", lambda e: open_picture(st["i"] + 1))
    canvas.bind("<Prior>", lambda e: open_picture(st["i"] - 1))
    for key, fn in (("a", reguess), ("r", rotate), ("l", toggle_lock)):
        canvas.bind(f"<{key}>", fn)
        canvas.bind(f"<{key.upper()}>", fn)
    for key, (dx, dy) in (("Left", (-1, 0)), ("Right", (1, 0)), ("Up", (0, -1)), ("Down", (0, 1))):
        canvas.bind(f"<{key}>", lambda e, dx=dx, dy=dy: nudge(dx, dy, False))
        canvas.bind(f"<Shift-{key}>", lambda e, dx=dx, dy=dy: nudge(dx, dy, True))
    open_picture(first_unfinished())
    canvas.focus_set()
    root.mainloop()


def main():
    import argparse
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pictures", help="folder with your cartridge pictures")
    ap.add_argument("sd", help="the SD card's root folder, for example E:\\")
    args = ap.parse_args()
    run_window(os.path.abspath(args.pictures), os.path.abspath(args.sd))


if __name__ == "__main__":
    main()
