# Shelf on retro-go

Shelf is now the launcher inside **retro-go**, an actively developed set of emulators for
the Odroid GO. Retro-go supplies the NES, Game Boy and Game Boy Color emulators, the in-game
menu, saving, and the handoff between Shelf and the games. Shelf supplies everything you see
before a game starts.

What you get:
- **Saving on quit:** pressing **Menu** in a game opens retro-go's menu, and **every way of
  quitting saves your spot automatically**. The game's page in Shelf then offers Resume, with a
  thumbnail of where you left off.
- **Settings in-game:** the **Volume** button in a game opens retro-go's settings: brightness,
  volume, scaling, speed, palette and more.
- **Matching menus:** both menus use Shelf's colors. Shelf installs a theme the first time it runs.
- **Settings from Shelf:** Shelf's own settings menu has a **System settings** entry that opens
  the same retro-go settings.

## 1. Get retro-go

In Command Prompt, put it somewhere without spaces in the path:

    cd C:\
    git clone -b master https://github.com/ducalex/retro-go

## 2. Install Shelf into it

Unzip this package (for example to `C:\shelf-retrogo`), then:

    C:\esp\idf.bat
    python C:\shelf-retrogo\tools\install_into_retrogo.py C:\retro-go

The script:
- replaces retro-go's launcher with Shelf (the original is kept in `launcher\main-original`)
- makes quitting a game always save first
- applies retro-go's patches to ESP-IDF, including the SD card fix the Odroid GO needs

It's safe to run again. After updating Shelf, run it again, then build again.

## 3. Build

    cd C:\retro-go
    python rg_tool.py --target odroid-go build-fw launcher retro-core

The first build takes a while, because `retro-core` holds all the emulators. When it finishes,
there's a new `.fw` file in `C:\retro-go`.

## 4. Install

Copy that `.fw` file to `\odroid\firmware\` on the SD card. Then hold **B** while turning the
Odroid GO on, and install it like before.

## The SD card

    /roms/nes, /roms/gb, /roms/gbc   your games (same folders as before)
    /shelf/art/...                   art from tools/convert_art.py (same as before)
    /romart/nes/<rom name>.png       retro-go cover art; Shelf uses it as box art when it has none of its own
    /retro-go/saves/...              save states (retro-go makes this)
    /retro-go/themes/shelf/          Shelf's menu colors (Shelf makes this)

Saves from Retro ESP32 don't carry over. Retro-go's emulators use a different save format.

Optional: Game Boy and Game Boy Color boot screens need BIOS files in `/retro-go/bios/`
(`gb_bios.bin`, `gbc_bios.bin`). Games run without them.

## Art and game info, automatically

With the SD card in your computer (here as `E:`), in a regular Command Prompt:

    python C:\shelf-retrogo\tools\fetch_info.py E:\

For every ROM, it identifies the game from the file's checksum, using RetroArch's free game
database, or from its name if that fails. Then it downloads:
- the box art, from RetroArch's thumbnail server
- a cartridge label, cut from the top of the box art
- release year, developer, publisher and genre, from the same database
- a short description, from Wikipedia

Everything goes straight onto the card. Art you've added yourself is never replaced, and running
it again only fills in what's missing. Useful options:
- `--labels title`: use the game's title screen on the cartridge label instead of box art
- `--force`: replace what's already there
- `--only nes`: just one system (`nes`, `gb`, `gbc`)
- `--no-descriptions`: skip Wikipedia

At the end it lists any ROMs it couldn't identify. Give those art by hand.

## Cropping labels from cartridge pictures

If you have pictures of whole cartridges, this crops the labels out for you:

    py -V:3.11 C:\shelf-retrogo\tools\crop_labels.py C:\my-carts E:\

Put the pictures in `C:\my-carts`, ideally in `nes`, `gb` and `gbc` subfolders, named roughly
after the game ("mega man 2 cart.jpg" is fine). For each picture, the window guesses where the label
is and which game it belongs to. Fix the box if needed and press **Enter**. The label goes straight
onto the card, replacing the generated one, and a full-size copy is kept in `C:\my-carts\cropped`.

- **Mouse:** drag inside the box to move it, drag an edge or corner to resize, or drag anywhere else to draw a new box.
- **Arrow keys** nudge the box, and **Shift + arrows** resize it.
- **A** guesses again, **R** rotates a sideways picture, and **L** locks the box to Shelf's label shape.
- **Page Down** skips a picture and **Page Up** goes back.
- If the game is wrong or missing, pick it from the **Game** list.

It remembers your progress, so you can close it and carry on later. Washed-out or mostly gray labels
are hard to spot automatically, so expect to draw those boxes yourself.

Note: `fetch_info.py --force` would replace these labels. To refresh everything else, use
`--force --labels none`.

## Art by hand

Name images after the ROM file (`Super Game (USA).box.png`, `Super Game (USA).label.png`) in
`nes`, `gb` and `gbc` folders, then:

    python C:\shelf-retrogo\tools\convert_art.py C:\my-art E:\

## The game page

Open a game with **A** to see all four save slots, each with a screenshot of where you were.
The one marked ▶ is your most recent.
- Choose a **saved slot** to Resume, Start over in that slot, or Delete it.
- Choose an **empty slot** ("New game") to start fresh. Quitting will save into that slot.
- **About** shows the release year, developer, publisher, genre and description. Up and Down scroll.

## If something goes wrong

- **The build fails:** send me everything from the first line with `error:` to the end.
- **It's stuck in a crash loop:** hold **Down** while powering on to get back to the launcher.
  Retro-go also saves crash details to `/crash.log` on the SD card.
- **Changing the menu theme:** use **System settings → Theme**. Shelf only picks its own theme
  the first time it runs.
