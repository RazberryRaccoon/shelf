# Roadmap: Shelf for Android

## Next: finish the design

1. **Prototype v2 is built** (both parts of the v1 review). Waiting on the user's review.
2. **Revise the prototype** until the user is happy. Keep it a single HTML file, test every screen headlessly, and publish or hand it over.
3. **Verify the unconfirmed measurements:** the DS case, PSP case, DS card label and keep case (see `RESEARCH.md`).

## The real app

**Version 0.1 is built** (October 2026): the Shelf interface in a small Java app (see SPEC §16), with real ROMs and
apps, launching through RetroArch and 24 standalone emulators, the controller, the home-screen role, and builds
published by GitHub Actions. Next, in order:

1. **Try it on the Retroid Pocket 3+** and fix what the user finds (buttons, speed, launching each emulator).
2. **Art and info:** libretro's database and art are built (version 0.2). Still to come: ScreenScraper with the
   user's account, for manuals, videos and more art.
3. **Saves:** Shelf's four save slots around RetroArch, as designed (SPEC §15), and RetroArch's network `QUIT` so
   the Home button saves before leaving a game.
4. **RetroAchievements:** sign-in and the achievements page from the web API.
5. **Manuals and media** from ScreenScraper.
6. **More models in Blender**, when the user wants to pick that back up.
7. **Shelf Native** (`android/native/`, started October 2026), round by round until it replaces the WebView app:
   0.1 Home, 0.2 setup and Settings, 0.3–0.5 the game page with the cartridge and the Studio, 0.6 the Museum, 0.7 console colors and the console
   Studio, 0.8 the consoles' circuit boards (built); 0.12 the start and return
   animations and the box on the game page (built); next the Library; then the box on the game page (the cartridge in the open box by default; selecting it picks
   it up and hides the box, as the page is now; the box's own controls under it, see DECISIONS.md); then Library and Apps; art downloads and the
   per-game art pickers; Settings and first-run setup; the Museum with the Studio's consoles; the launch animation
   (the cartridge going into the console); outline glows on the selected object (a custom Filament material); then
   it takes the name "Shelf" and the home-screen role.
8. **The Cartridge Studio look in Shelf** (`tools/cartridge-studio/`), once the user approves it: the beveled models,
   physical plastic and studio lighting in Shelf's own renderer, the North American SNES shapes (the prototype still
   has the Japanese and European ones), and the cartridge **color variant as a per-game setting** ("Eventually I want
   this feature in the per game settings of the actual launcher"). Being done in Shelf Native (0.1 has the colors).

## Shelf Native: next, and later (the user's list, October 2026)

- **Done in 0.25–0.26:** the start animations, studied frame by frame in the rig: the NES door hinges inward, nothing
  clips, power switches and buttons, framing; 0.26 springs and the hit; 0.27 the glow and shockwave in place of sparks; 0.28 the cartridge waits clear of the console, checked by a clip test. Waiting on the user's notes.
- **Done overnight (0.29):** Apps (with Make it a game), Shelf as the home screen, themes and sound packs (the first
  two of the "eventually" list), play time, Surprise me.
- **Done in 0.31:** theme environments (city, forest, ocean, aurora, desert, stormy, and Space), each by day and by
  night, with music for each and ambient sounds. Waiting on the user's notes on the device (smoothness on the Retroid
  especially).
- **Pinned collections on Home:** wanted, place not decided.
- **Backburner: emulators built in** (the user, after 0.30: "VERY interested… it would come down the line once I'm
  happy with Shelf"): libretro cores inside Shelf through LibretroDroid (GPL-3, as Lemuroid), cores downloaded from
  libretro's build server; NES to N64 (PS1 maybe; PSP and DS stay outside). Would give suspend to Home, saves and
  states Shelf owns, filter presets, the live game on the handheld's screen, later rewind and RetroAchievements. The
  user may sell Shelf one day: then two editions, one to sell as Shelf works now, one personal with cores built in
  (GPL-3 and the non-commercial cores, Snes9x and Genesis Plus GX, rule them out of a sold edition).
- **The Library's Spines view,** once there's real spine art.
- **Eventually** (the user: "things I'm generally not happy with and want to … tackle eventually, so don't forget
  these"):
  1. More themes.
  2. More sound effect packs.
  3. Better icons, fonts and style.
  4. The cartridge and console models still need work: a larger project, perhaps bringing back the Cartridge Studio
     for testing lighting and the models together.

## Open questions

- Touch support, and how much of it.
- Whether to keep a cartridge "insert" animation on Android, since there's no console model.
- Multi-disc PlayStation games: one box with several discs in the tray?
- How standalone emulators report that a game has closed, so Shelf knows when to play its return animation.
- Whether ScreenScraper grants a developer ID for a personal project, and how the user stores it.
