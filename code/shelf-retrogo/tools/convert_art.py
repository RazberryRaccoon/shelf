#!/usr/bin/env python3
"""Convert cover and label scans into the images Shelf shows on the Odroid GO.

Put your images in a folder like this, named after the ROM file (without its extension):

    my-art/
      nes/  Super Game (USA).box.png   Super Game (USA).label.png
      gb/   Pocket Thing.box.jpg       Pocket Thing.label.jpg
      gbc/  ...

Then run:

    python convert_art.py my-art  E:\\        (the SD card's drive or mount point)

Each image is resized to fit its system's frame, keeping its own proportions,
and written to <sd>/shelf/art/<system>/<name>.box.img or .label.img.
Needs Pillow:  pip install pillow
"""
import argparse
import os
import struct
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("This tool needs Pillow. Install it with:  pip install pillow")

# Largest size per system; must match art.h in the firmware.
FRAMES = {
    "nes": {"label": (106, 88), "box": (112, 160)},
    "gb": {"label": (90, 78), "box": (128, 128)},
    "gbc": {"label": (90, 78), "box": (128, 128)},
}
EXTS = (".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif")
KEY = 0xF81F   # Shelf's transparent color; nudged so art never disappears


def to_rgb565(img):
    out = bytearray()
    raw = img.tobytes()
    for i in range(0, len(raw), 3):
        r, g, b = raw[i], raw[i + 1], raw[i + 2]
        v = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3)
        if v == KEY:
            v ^= 0x0020
        out += struct.pack("<H", v)
    return bytes(out)


def fit(img, max_w, max_h):
    scale = min(max_w / img.width, max_h / img.height)
    w = max(1, min(max_w, round(img.width * scale)))
    h = max(1, min(max_h, round(img.height * scale)))
    return img.resize((w, h), Image.LANCZOS)


def load(path):
    img = Image.open(path)
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        bg = Image.new("RGBA", img.size, (0, 0, 0, 255))
        bg.alpha_composite(img)
        img = bg
    return img.convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", help="folder with nes/, gb/ and gbc/ subfolders of images")
    ap.add_argument("sd", help="the SD card's root folder (or any folder to copy over later)")
    ap.add_argument("--preview", action="store_true", help="also write .preview.png files so you can check the results")
    args = ap.parse_args()

    done = skipped = 0
    for system, frames in FRAMES.items():
        src_dir = os.path.join(args.source, system)
        if not os.path.isdir(src_dir):
            continue
        out_dir = os.path.join(args.sd, "shelf", "art", system)
        os.makedirs(out_dir, exist_ok=True)
        for name in sorted(os.listdir(src_dir)):
            stem, ext = os.path.splitext(name)
            kind = stem.rsplit(".", 1)[-1].lower() if "." in stem else ""
            if ext.lower() not in EXTS or kind not in frames:
                if ext.lower() in EXTS:
                    print(f"  skipped {system}/{name}: name it '<rom name>.box{ext}' or '<rom name>.label{ext}'")
                    skipped += 1
                continue
            rom = stem[: -(len(kind) + 1)]
            img = fit(load(os.path.join(src_dir, name)), *frames[kind])
            with open(os.path.join(out_dir, f"{rom}.{kind}.img"), "wb") as f:
                f.write(b"SHL1" + struct.pack("<HH", img.width, img.height) + to_rgb565(img))
            if args.preview:
                img.save(os.path.join(out_dir, f"{rom}.{kind}.preview.png"))
            print(f"  {system}/{rom}: {kind} {img.width}x{img.height}")
            done += 1
    print(f"Converted {done} image(s)" + (f", skipped {skipped}" if skipped else "") + ".")


if __name__ == "__main__":
    main()
