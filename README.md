# Shelf

**A game launcher that blurs the line between museum and game launcher.**

In Shelf, your games are physical objects. Cartridges, discs and boxes are drawn as 3D models at their real
proportions, and you browse them, open the box, flip through the manual, and play. It aims to feel like a modern
handheld operating system: calm, minimal and smooth, not a retro arcade.

Shelf has been built three times, each one learning from the last:

| Version | Device | State | Download |
|---|---|---|---|
| **Shelf for Android** | Retroid Pocket 3+ (Android 11) | 🚧 **Current focus.** Design prototype, being revised (runs in a browser). The real app hasn't been started. | [Prototype](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-android-prototype.zip) |
| **Shelf for TV** | Raspberry Pi 3B+ on a TV | 🧪 Prototype finished. A first version for the Pi is written but has never been tried on a real Pi. | [Pi installer](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-tv.tar.gz) · [Prototype](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-tv-prototype.zip) |
| **Shelf for Odroid GO** | Odroid GO (320×240 handheld) | ✅ **Finished and working** on the device. NES, Game Boy and Game Boy Color. | [Launcher + tools](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-odroid-go.zip) |

The prototypes can also be tried straight in the browser, no download needed:
[Android](https://raw.githack.com/RazberryRaccoon/shelf/main/prototypes/shelf-android.html) ·
[TV](https://raw.githack.com/RazberryRaccoon/shelf/main/prototypes/shelf-tv.html) ·
[Odroid GO](https://raw.githack.com/RazberryRaccoon/shelf/main/prototypes/shelf-odroid-launcher.html)
(use Chrome or Edge on a computer).

---

## Shelf for Android (current)

The serious one: the best launcher we can make for the Retroid Pocket 3+. It replaces the home screen and covers
ten consoles plus Android games:

- **Home** holds the games you carry with you, as cartridges, discs and cards.
- **Library** is your shelf: every game as its box, at the real box's size.
- **Opening a box** shows its cartridge or disc, its manual, screenshots, saves and achievements.
- Games launch in RetroArch (or a standalone emulator), and the Home button saves and brings you back.

Right now there's a **browser prototype** to look at and give feedback on. Its new **Museum** tab shows every
console, cartridge and box up close, with facts about each console. It's fixed at the Retroid's
1334×750 screen size. The full design is in [docs/SPEC-shelf-android.md](docs/SPEC-shelf-android.md) and the
plan for the real app is in [docs/ROADMAP.md](docs/ROADMAP.md).

**Trying the prototype:** open the browser link above, or download the zip, unzip it and double-click
`shelf-android.html`. First-run setup comes first.

| Key | Button |
|---|---|
| Arrow keys | D-pad |
| Enter | A |
| Esc | B |
| F | Y |
| Q / E | L1 / R1 (switch tabs) |
| H | Home |

A controller plugged into the computer works too.

## Shelf for TV (Raspberry Pi 3B+)

A version for the living room: profiles, Home with cartridges, a Library grid, game pages with boxes,
manuals, media and achievements, all at 1920×1080.

- **The prototype** is the finished design after seven rounds of feedback. Same keys as the Android one.
- **The Pi installer** (`shelf-tv.tar.gz`) is a first slice of the real thing: it boots the Pi straight into
  Shelf, shows your games as 3D cartridges, launches them in RetroArch, and saves when you press Home.
  It was tested on a computer but **never on a real Pi**, so expect surprises.

**How to install it:** the full walkthrough is in [code/shelf-tv-pi/README.md](code/shelf-tv-pi/README.md).
In short:

1. Use Raspberry Pi Imager to put **Raspberry Pi OS Lite (64-bit)** on a microSD card, with hostname `shelf`
   and SSH turned on.
2. From Windows Terminal, in the folder where you downloaded `shelf-tv.tar.gz`:
   ```
   scp shelf-tv.tar.gz you@shelf.local:~
   ssh you@shelf.local
   ```
3. On the Pi:
   ```
   tar xzf shelf-tv.tar.gz
   cd shelf-tv
   ./install.sh
   ```
4. Reboot, then add games from Windows at `\\shelf\shelf\roms`.

## Shelf for Odroid GO (finished)

Where it started. Shelf replaces the launcher in [retro-go](https://github.com/ducalex/retro-go) on the
Odroid GO: animated cartridges over a space backdrop, a game page with your save slots and screenshots of where
you left off, and saving automatically whenever you quit a game.

The download contains:

- `launcher-main/`: Shelf's launcher code.
- `tools/install_into_retrogo.py`: installs Shelf into a copy of retro-go.
- `tools/fetch_info.py`: downloads box art, labels and game info for your ROMs automatically.
- `tools/crop_labels.py`: crops cartridge labels out of photos of your own cartridges.
- `tools/convert_art.py`: converts your own art into Shelf's format.
- `prototype/`: the original browser prototype.
- `old-standalone-firmware.zip`: the first, abandoned attempt (before switching to retro-go). Reference only.

**How to build and install it:** the full walkthrough is in
[code/shelf-retrogo/README.md](code/shelf-retrogo/README.md). It needs ESP-IDF 4.4 on Windows. In short:

1. Unzip the download to `C:\shelf-retrogo`.
2. Get retro-go: `cd C:\` then `git clone -b master https://github.com/ducalex/retro-go`
3. Install Shelf into it: `C:\esp\idf.bat` then
   `python C:\shelf-retrogo\tools\install_into_retrogo.py C:\retro-go`
4. Build: `cd C:\retro-go` then `python rg_tool.py --target odroid-go build-fw launcher retro-core`
5. Copy the new `.fw` file to `\odroid\firmware\` on the SD card, hold **B** while turning the Odroid GO on,
   and install it.

**Odroid prototype keys:** arrows are the D-pad, X or Space is A, Z or Backspace is B, Enter is Start,
Shift is Select, Esc or M is Menu, V is Volume.

---

## What's in this repository

| Folder | What it is |
|---|---|
| [`downloads/`](downloads) | The ready-to-download files linked above. |
| [`prototypes/`](prototypes) | The three browser prototypes, each a single HTML file. |
| [`prototype-source/`](prototype-source) | The prototypes split into the parts they're built from. |
| [`code/shelf-retrogo/`](code/shelf-retrogo) | Shelf for Odroid GO (retro-go launcher and PC tools). |
| [`code/shelf-tv-pi/`](code/shelf-tv-pi) | Shelf for TV on the Raspberry Pi (C, SDL2, OpenGL ES 2). |
| [`docs/`](docs) | The Android spec, every design decision, the history of each version, research and the roadmap. |
| [`CLAUDE.md`](CLAUDE.md) | Project notes for Claude, so any new Claude session can pick up where the last left off. |

**A note on art:** the prototypes use invented games, studios and generated art. Shelf doesn't copy any real
console or cartridge designs; it uses its own shapes at the real sizes. Real box art and labels come from your
own scraper accounts when the app runs.
