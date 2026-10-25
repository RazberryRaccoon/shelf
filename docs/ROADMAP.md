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

## Open questions

- Touch support, and how much of it.
- Whether to keep a cartridge "insert" animation on Android, since there's no console model.
- Multi-disc PlayStation games: one box with several discs in the tray?
- How standalone emulators report that a game has closed, so Shelf knows when to play its return animation.
- Whether ScreenScraper grants a developer ID for a personal project, and how the user stores it.
