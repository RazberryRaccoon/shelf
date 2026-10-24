# Shelf: context for Claude

You're picking up a long-running project from another Claude conversation. Read this file first, then
`docs/SPEC-shelf-android.md` and `docs/DECISIONS.md`. Everything else is reference.

## What Shelf is

Shelf is a game launcher with one idea at its heart: your games are physical objects. Cartridges, discs and
boxes are rendered as 3D models you browse, open and play. It has been built three times:

1. **Shelf for Odroid GO** (finished, working on hardware). A launcher for retro-go on a 320×240 ESP32 handheld.
2. **Shelf for TV** (prototype finished; a first native slice for Raspberry Pi 3B+ was written but never tested on hardware).
3. **Shelf for Android** (current focus). The best launcher we can make for the user's **Retroid Pocket 3+**.
   A browser prototype exists (`prototypes/shelf-android.html`) and the user had not yet given feedback on it
   when the conversation moved here. The next big step is the real Android app, built with GitHub Actions.

The user called the first two "me messing around." The Android version is the serious one: "take everything
that we've learned … and make an Android game launcher that's the best it can possibly be."

## Where things stand

- Prototype v2 is built: both parts of the v1 review (models, consoles and the Museum; then the launch animation,
  music, emulators and the rest). Waiting on the user's review; see `docs/ROADMAP.md`.
- Blender models: GBA done (second pass); the user paused the rest ("will take a lot more effort than I feel like
  doing right now").
- New layout built (October 2026): Home is the base, with a row of recent games; a game's page opens in place;
  Library, Apps and Museum slide up as a sheet from round buttons in the top bar. First notes built (up closes
  the page, L1/R1 cycle, a picture of the selected game under the row). Waiting on the user's notes.
- **The Android app, version 0.1** (October 2026): `android/`, a Java app running the interface in a WebView with a
  native bridge (`ShelfNative`). GitHub Actions builds it; pushes to `main` publish `Shelf.apk` as the latest release.
  Next: the user tries it on the Retroid, then art downloads, saves, achievements (see `docs/ROADMAP.md`).
- **Shelf Native** (October 2026, the user: "good enough for a full android release - no more glorified web file"):
  `android/native/`, Kotlin + Google's Filament, installed next to the WebView app until it does everything. Version
  0.1 is Home with the Cartridge Studio's models, lighting and color variants; 0.2 setup and Settings (with live
  Lighting); 0.3 the game page (turn the cartridge with the right stick, live shell colors, soft sounds). Next: Library
  and box models. The user paused inspecting the rarer shell colors until they can be seen big (0.3 makes that possible).
  Kotlin is compile-checked locally with the Kotlin 2.4 compiler against `android-all` and Filament's AARs.
- The Android SDK can't be downloaded in Claude's sandbox (dl.google.com is blocked): compile-check the Java against
  Robolectric's `android-all` jar from Maven Central, and let GitHub Actions do the real build.
- The user asked to "talk it through" before building anything new. Propose, confirm, then build.
- **Shelf Native polish (standing request):** while porting each screen, add extra polish, detail and charm on your own
  initiative ("on par with the quality and polish of a Nintendo console. I want to be impressed"). Small touches don't
  need asking; keep to the taste rules below, and still propose new screens or big changes first. Tell the user what
  you added each round so they can veto it.

## The user

- New to GitHub. Explain any GitHub step (branches, pull requests, merging, releases) in plain terms.

- Uses a **Windows** PC. Owns an Odroid GO, a Raspberry Pi 3B+ and a Retroid Pocket 3+ (Android 11).
- Comfortable following step-by-step technical instructions (built ESP-IDF firmware, flashed devices), but
  isn't a professional developer. Give exact commands and explain what each step does.
- Reviews with numbered lists of notes. Notices details (alignment, motion, contrast) and says clearly what
  bothers them.
- Wants to be consulted on design before building, and wants research behind decisions (real measurements,
  how other frontends do it).

### Taste (consistent across every round)

- **Wants:** modern, minimalist, like a current handheld or console OS. Charm through motion and materials.
  Calm, smooth animation. Real proportions. Depth via 3D models, soft shadows and glows.
- **Doesn't want:** "nostalgiaslop," arcade styling, pixelated UI, literal game-room or shelf themes, loud
  sounds, white flashes, wobbling or swaying objects ("makes me feel like I'm on acid"), perspective that
  makes things drift out of alignment, button-prompt bars cluttering the corners, autoplaying video.
- Likes: the space theme, the cartridge-into-console idea, outline glows that hug the selected object,
  gentle floating with shadows, framed panels that keep things contained.

## Working agreements that went well

- **Research first:** real label and box measurements, how RetroArch and other frontends work. The research
  and its sources are in `docs/RESEARCH.md`.
- **Prototype in the browser first:** a single HTML file, WebGL 1 with our own small renderer (WebGL 1 is
  OpenGL ES 2.0, so shaders port directly). Publish it, get feedback, iterate.
- **Test before handing over:** previously done with headless Chrome plus software WebGL, a test hook
  `window.__step(seconds)` that advances animation deterministically, and contact sheets of screenshots.
  Every round found real bugs this way. Say plainly what was and wasn't tested.
- **Explain changes plainly,** in terms of what the user will see.

## Hard constraints

- **Silhouettes follow the real objects, details stay stylized** (the user's call, October 2026, relaxing the
  earlier "original shell designs" rule): real outlines, proportions and sizes, label windows at the real label
  sizes, but no logos or console-maker branding anywhere, and no exact replicas. Revisit before any public release.
- **Prototypes use invented games, studios and generated art.** Real box art, labels, manuals and media
  come from the user's own scraper accounts at runtime.

## Map of this package

- `README.md`: the GitHub front page, written for the user and their brother. Keep it current.
- `docs/SPEC-shelf-android.md`: the full spec for the current project.
- `docs/DECISIONS.md`: every decision and preference, in the user's words where possible.
- `docs/HISTORY.md`: what was built in each project, what broke, and what we learned.
- `docs/RESEARCH.md`: facts with sources (label and box sizes, RetroArch on Android, ScreenScraper, RetroAchievements).
- `docs/ROADMAP.md`: the plan for the real Android app, and open questions.
- `prototypes/`: the three working browser prototypes. Open in a desktop browser, or at
  `https://razberryraccoon.github.io/shelf/prototypes/` (GitHub Pages, updates about a minute after a merge).
- `prototype-source/`: the prototypes split into the parts they were built from, with a build script.
- `tools/models/`: Blender scripts that build consoles and cartridges from the user's reference photos (see its
  README). Output goes to `prototype-source/models/`. GBA done; the rest still use the code-built models.
- `android/`: Shelf for Android, the app (see its README); `android/shared/` is the Java both apps use;
  `android/native/` is Shelf Native. `prototype-source/android/rn.js` is the page's native mode.
- `tools/cartridge-studio/`: three.js cartridge and console generator and tuning page; `export-models.mjs` writes the
  `.glb` files Shelf Native uses (beveled extrusions, physical plastic, studio
  HDR, .glb export). Not yet used by Shelf's own renderer.
- `code/`: the Odroid GO firmware integration and the Raspberry Pi C app.
- `downloads/`: the zips linked from the README. Rebuild them with `bash scripts/make-downloads.sh` whenever
  `code/` or `prototypes/` change, and commit the result.
- The raw transcript of the first conversation (the Odroid project) was left out of this public repo; its
  decisions are summarized in `docs/`.
