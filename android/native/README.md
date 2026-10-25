# Shelf Native

Shelf rebuilt as a native Android app, replacing the WebView over several rounds. It installs next to the WebView
Shelf as **Shelf Native** (`com.razberryraccoon.shelfnative`), so you can keep using the current Shelf until this one
does everything; then it takes over the name. Download: the newest release's **ShelfNative.apk**.

## How it's built

Three layers, the same way the prototype stacks a WebGL canvas and a page:

| Layer | What draws it |
|---|---|
| The backdrop: night sky, nebula, stars, planet, Home's picture with its dot fade, shadows under the objects | `Backdrop.kt`, Android's canvas |
| The 3D objects: cartridges, discs and cards | `Stage.kt` and `Carts.kt`, Google's [Filament](https://github.com/google/filament) engine in a transparent `TextureView` |
| The interface: top bar, titles, hints, pickers | Android views on a 1334 × 750 board (`Ui.kt`), scaled to fit any screen |

- **The models** are the Cartridge Studio's (`tools/cartridge-studio/`), exported as `.glb` files into
  `src/main/assets/models/` by `node export-models.mjs` (see the Studio's README). Filament's built-in glTF materials
  read their clear coat, transmission and volume, so the plastic looks as it does in the Studio.
- **Lighting** matches the Studio: the Poly Haven studio HDR (`assets/env/studio.hdr`, CC0) made into image-based
  light on the device (`IBLPrefilterContext`), one key light, ACES filmic tone mapping, 4× multisampling. The Studio's
  numbers (exposure .72, environment .85) read far too bright on the device; the user tuned it there to environment .1,
  exposure .3, key light .2 (`Stage.Look`), and Settings › Lighting changes them live.
- **Color variants:** each game's shell color comes from `cartridges.json` (the Studio's research); the Studio's rules
  are repeated in `Cart.apply`. Clear and frosted colors use the see-through model, with its circuit board inside.
- **The camera** is a long lens (14°) at Shelf's layout, so a point at depth 0 lands on that pixel of the
  1334 × 750 layout and nothing drifts out of line.
- **Shared with the WebView app** (`../shared`): finding games (`Scanner`), launching emulators (`Emulators`),
  lending ROMs (`RomProvider`) and the art services.
- **Fonts:** Manrope and Unbounded, as in the prototype (SIL Open Font License, in `assets/fonts`).

| File | What it does |
|---|---|
| `ShelfActivity.kt` | The one screen: the three layers, controller buttons, storage permission, scanning and launching. |
| `Stage.kt` | Filament: engine, transparent view, studio lighting, camera, the frame loop. |
| `Carts.kt` | Loads the models, draws each game's label, applies shell colors, places each cartridge. |
| `Home.kt` | Home: the row of recent games, the top bar, details, the shell color and emulator pickers. |
| `GamePage.kt` | A game's page: the cartridge to turn with the right stick, the details and icon tiles, live shell colors. |
| `Meta.kt` | A game's details and pictures from libretro's database, saved on the device. |
| `Museum.kt` | The Museum: the timeline, the four views of each console, its story, its colors (and the console Studio) and its default shell. |
| `Consoles.kt` | Console colors: the real ones, yours, and painting a console model part by part. |
| `Models.kt` | Consoles and cases: placing any model, a case's art, its hinged cover and its tray. |
| `Studio.kt` | The Studio: finishes, named colors, your saved shells, and its controls. |
| `Sounds.kt` | The quiet interface sounds, synthesized on the device. |
| `Setup.kt` | First-run setup (welcome, your name and color, games folders, emulators, done); its pages are also Settings. |
| `Backdrop.kt` | Everything behind the 3D layer. |
| `Games.kt` | Your games, what you've played, each game's shell color, each console's emulator, launching. |
| `Ui.kt` | Colors, fonts, Shelf's own label art, and the 1334 × 750 board. |

## What's in version 0.7

Console colors, and the console Studio (in the Museum). A console's page has a **Console colors** row: under
"Defaults for …", the colors it really came in, each with when and where (the N64's Funtastic translucents and its
gold edition, the Game Boy's 1995 colors, the Game Boy Advance's launch colors, the DS and PSP editions…); under
"Custom", your own; then "+ Add new", the console Studio. It lists the console's parts (shell, screen bezel, D-pad,
buttons, Start and Select, shoulder buttons, stick, lid, switches: whichever it really has), and each part opens the
cartridge Studio's controls (the same twelve finishes and 44 colors), showing on the console as you change it. Done
keeps a part, Cancel puts it back; Save names the colors. Y on your own edits or deletes them. See-through finishes are
shown as glassy, polished plastic on a console (there's nothing inside them to see yet). The colors are kept per console
for the start and return animations, which come next. `Consoles.kt`; the parts are named in the exported models
(`role:<part>`) and the real colors are in `consoles.json`.

## What was in version 0.6

The Museum (the round museum button in the top bar): every console, oldest first, along a timeline at the bottom (L
and R, or left and right, move through it). Each is shown four ways, turned with the right stick: the console, its
cartridge, the cartridge in its open box, and the box closed; the views flow into each other (the cartridge settles
into the tray as the box appears, the cover swings shut). The panel tells its story (maker, release, units sold,
processor, facts) and sets its **default shell**: what its standard cartridges wear on Home. Special editions keep
their real colors, recognized from the title (Pokémon Gold, Zelda on the NES, Donkey Kong 64…; `special` in
`Studio.kt`), and so do games you've customized. The consoles (`console-*.glb`) and cases (`case-*.glb`, `Models.kt`)
are exported from the Studio and lit like the cartridges.

## What was in version 0.5

The Studio. On a game's page, Customize (or Y on Home) lists the console's real colors under "Defaults for …", then
your own shells under "Custom", then "+ Add new", which opens the Studio: the cartridge stays on the left to turn, and
the panel has twelve finishes (plastic, matte, glossy, frosted, clear, smoke, metallic, brushed metal, anodized
aluminum, pearl, metal flake, glow in the dark), 44 named colors, and hue, saturation and brightness bars for any
color. Everything shows on the cartridge as you change it. Saved shells are shared by every cartridge type (Y on one
edits or deletes it). `Studio.kt`. Also: Home's cartridges are bigger, with half of one showing on the left and two and
a half on the right, and the top right shows a battery with a bolt while it's charging.

## What was in version 0.4

The game page, filled in. Under the title: the year, developer and genre, when you last played it, and what it plays
with. The tiles are Play, Emulator (this game's own, or the same as its console's), Shell color, Favorite, Settings and
Back. Down goes to Pictures (box, title screen, screenshot; A shows them full screen, left and right go through them)
and then About (the description, which up and down scroll). Game settings: its emulator, shell color, whether it shows
on Home, which game it is (when Shelf guessed wrong), and looking it up again. Details and pictures come from libretro's
database (`Meta.kt`, using the shared `ArtIndex`), free and with no account, matched by the ROM's checksum or the
closest name, and saved on the device; descriptions from Wikipedia's article on the game when libretro's are thin. Home's picture is now the game's screenshot once it's been looked up.

Fixes: clear and frosted shells are see-through again (Filament's glTF transmission drew them solid, so the shell is
loaded as a blended material instead; `seeThrough` in `Carts.kt`); the key light comes from higher and more to the
side, so the cartridges' fronts no longer catch its full reflection on Home (that washed gold out to tan); the tiles'
focus ring is no longer cut off; the line under the tiles has a fixed height, so the tiles don't move.

## What was in version 0.3

A game's page, opened in place from Home (Down): the cartridge lifts out of the row in an arc and grows on the left
while the rest of the row sinks away; the right stick turns it in your hands (it coasts when you let go, then settles
back to facing you), X flips it over, and the key light swings a little as it turns so the plastic catches a sweep of
highlight. On the right: the console, the title, when you last played it and how often, its emulator and shell, and a
row of icon tiles (Play, Emulator, Shell color, Favorite, Back). Shell color lists the real colors and shows each one
on the big cartridge as you move through them; A keeps it, B puts the old one back. Up or B closes the page. Sounds:
short, quiet sine-tone clicks and chimes made on the device (`Sounds.kt`), off in Settings › Sounds. Box models and
the box opening come with the Library.

## What was in version 0.2

First-run setup, which the prototype has: welcome; your name and color; finding your games (storage access, then a
folder for any console, or one main folder with a folder per console inside); each console's emulator; done. Settings
(the round button, or your profile) opens the same pages later. Polish added: each page rises in, a Studio cartridge
turns beside the panel and follows the console you're on, the found-games count climbs, and Home's cartridges rise
into place one after another when you arrive. Settings › Lighting tunes the 3D's brightness live. The shared Scanner also recognizes folder names written out ("Game Boy
Advance", "PlayStation", "Mega Drive") and looks in Games and Emulation/roms as well as ROMs.

## What was in version 0.1

Home only: your most recently played games as Studio cartridges (PlayStation discs, PSP UMDs and Android game cards
too), the top bar, the picture and backdrop, launching, and Y for a game's shell color and its console's emulator.
Library, Apps, Museum, Settings, the game page, art downloads and setup come over in later rounds (see
`docs/ROADMAP.md`). Android apps marked as games aren't shown yet.

## Building

GitHub Actions builds it with the WebView app (`./gradlew assembleDebug` in `android/`) and publishes
`ShelfNative.apk`. In Claude's sandbox, Google's Maven is blocked, so the Kotlin is compile-checked against
Robolectric's `android-all` jar and Filament's AARs from Maven Central with the Kotlin 2.4 compiler; GitHub does the
real build.
