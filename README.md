# Shelf

**A game launcher that blurs the line between museum and game launcher.**

In Shelf, your games are physical objects. Cartridges, discs and boxes are drawn as 3D models at their real
proportions, and you browse them, open the box, flip through the manual, and play. It aims to feel like a modern
handheld operating system: calm, minimal and smooth, not a retro arcade.

Shelf has been built three times, each one learning from the last:

| Version | Device | State | Download |
|---|---|---|---|
| **Shelf for Android** | Retroid Pocket 3+ (Android 11) | 🚧 **Current focus.** First version of the real app (0.1), plus the design prototype. | [**Shelf.apk**](https://github.com/RazberryRaccoon/shelf/releases/latest/download/Shelf.apk) · [Prototype](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-android-prototype.zip) |
| **Shelf for TV** | Raspberry Pi 3B+ on a TV | 🧪 Prototype finished. A first version for the Pi is written but has never been tried on a real Pi. | [Pi installer](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-tv.tar.gz) · [Prototype](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-tv-prototype.zip) |
| **Shelf for Odroid GO** | Odroid GO (320×240 handheld) | ✅ **Finished and working** on the device. NES, Game Boy and Game Boy Color. | [Launcher + tools](https://github.com/RazberryRaccoon/shelf/raw/main/downloads/shelf-odroid-go.zip) |

The prototypes can also be tried straight in the browser, no download needed:
[Android](https://razberryraccoon.github.io/shelf/prototypes/shelf-android.html) ·
[TV](https://razberryraccoon.github.io/shelf/prototypes/shelf-tv.html) ·
[Odroid GO](https://razberryraccoon.github.io/shelf/prototypes/shelf-odroid-launcher.html)
(use Chrome or Edge on a computer).

---

## Shelf for Android (current)

The serious one: the best launcher we can make for the Retroid Pocket 3+. It replaces the home screen and covers
ten consoles plus Android games:

- **Home** holds the games you've played recently, as cartridges, discs and cards. Press down on one and its box
  slides in, the cartridge goes into it, and the game's page appears right there.
- Round buttons along the top open the **Library** (every game as its box, at the real box's size), **Apps**,
  the **Museum** and **Settings**. They slide up over Home.
- **A game's page** shows its cartridge or disc, its manual, screenshots, saves and achievements.
- Games launch in RetroArch (or a standalone emulator), and the Home button saves and brings you back.

### Installing the app

1. On the Retroid, open the browser and go to
   **https://github.com/RazberryRaccoon/shelf/releases/latest/download/Shelf.apk**. It downloads the newest build.
2. Open the downloaded file (from the browser's downloads, or the Files app). The first time, Android asks to allow
   installing apps from the browser: allow it, go back, and tap **Install**. Later versions install over the top
   and keep your settings.
3. Open **Shelf**. In setup, **Allow access to my files** lets Shelf find your games: switch on *Allow access to manage
   all files*, then press Back.
4. Shelf looks for games in a **ROMs** folder on internal storage or the SD card, one folder per console: `nes`, `snes`,
   `n64`, `genesis` (or `megadrive`), `gb`, `gbc`, `gba`, `psx`, `nds`, `psp`. If yours are somewhere else, choose the
   folder for each console in setup, or later in Settings › Platforms › (a console) › Games folder.
5. To make Shelf the home screen, choose **Use Shelf as my home screen** in setup (or later in Android's Settings ›
   Apps › Default apps › Home app).

It launches games in RetroArch or a standalone emulator (DuckStation, PPSSPP, melonDS, Mupen64Plus FZ and others).
Art and details come from libretro's free database, and from ScreenScraper, TheGamesDB, RetroAchievements, SteamGridDB
and IGDB once you connect your accounts in Settings › Art and info. A game's settings has a picker for each kind of art
(box front and back, spine, label, title screen, screenshot) showing results from every connected service.
Save slots and achievements come next; see
[docs/ROADMAP.md](docs/ROADMAP.md). The app's source is in [`android/`](android).

### The prototype

There's also a **browser prototype**, where design ideas are tried first. Home is a row of your recent games;
press down on one and its box slides in and opens right there. The round buttons along the top open the
**Library**, **Apps**, the **Museum** (every console, cartridge and box up close, with facts about each
console) and **Settings**. It's fixed at the Retroid's
1334×750 screen size. The full design is in [docs/SPEC-shelf-android.md](docs/SPEC-shelf-android.md) and the
plan for the real app is in [docs/ROADMAP.md](docs/ROADMAP.md).

**Trying the prototype:** open the browser link above, or download the zip, unzip it and double-click
`shelf-android.html`. First-run setup comes first; **Quick setup with test data** gets through it fast.

| Key | Button |
|---|---|
| Arrow keys | D-pad |
| Enter | A |
| Esc | B |
| F | Y |
| Q / E | L1 / R1 (step through Home, Library, Apps, Museum and Settings) |
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
| [`android/`](android) | Shelf for Android, the app (built by GitHub Actions). |
| [`code/shelf-retrogo/`](code/shelf-retrogo) | Shelf for Odroid GO (retro-go launcher and PC tools). |
| [`code/shelf-tv-pi/`](code/shelf-tv-pi) | Shelf for TV on the Raspberry Pi (C, SDL2, OpenGL ES 2). |
| [`docs/`](docs) | The Android spec, every design decision, the history of each version, research and the roadmap. |
| [`CLAUDE.md`](CLAUDE.md) | Project notes for Claude, so any new Claude session can pick up where the last left off. |

**A note on art:** the prototypes use invented games, studios and generated art. Shelf doesn't copy any real
console or cartridge designs; it uses its own shapes at the real sizes. Real box art and labels come from your
own scraper accounts when the app runs.
