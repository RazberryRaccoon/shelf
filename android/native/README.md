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
  light on the device (`IBLPrefilterContext`), one key light, ACES filmic tone mapping, exposure .72, environment .85,
  4× multisampling.
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
| `Backdrop.kt` | Everything behind the 3D layer. |
| `Games.kt` | Your games, what you've played, each game's shell color, each console's emulator, launching. |
| `Ui.kt` | Colors, fonts, Shelf's own label art, and the 1334 × 750 board. |

## What's in version 0.1

Home only: your most recently played games as Studio cartridges (PlayStation discs, PSP UMDs and Android game cards
too), the top bar, the picture and backdrop, launching, and Y for a game's shell color and its console's emulator.
Library, Apps, Museum, Settings, the game page, art downloads and setup come over in later rounds (see
`docs/ROADMAP.md`). Android apps marked as games aren't shown yet.

## Building

GitHub Actions builds it with the WebView app (`./gradlew assembleDebug` in `android/`) and publishes
`ShelfNative.apk`. In Claude's sandbox, Google's Maven is blocked, so the Kotlin is compile-checked against
Robolectric's `android-all` jar and Filament's AARs from Maven Central with the Kotlin 2.4 compiler; GitHub does the
real build.
