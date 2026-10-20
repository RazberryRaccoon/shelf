# Roadmap: Shelf for Android

## Next: finish the design

1. **Prototype v1 feedback is in** (see `DECISIONS.md`). Part 1, the models and the Museum, is built; waiting on the user's review.
2. **Part 2 of the revision:** QWERTY keyboard, Back in setup, the console launch and return animations
   (consoles and their `slot`s are ready), saves that open the emulator, the case closing and the game panel
   sliding in, Library spacing for wide boxes, menu music with track choice, and the full emulator list.
3. **Revise the prototype** until the user is happy. Keep it a single HTML file, test every screen headlessly, and publish or hand it over.
3. **Verify the unconfirmed measurements:** the DS case, PSP case, DS card label and keep case (see `RESEARCH.md`).

## Then: the real app (GitHub Actions builds, sideloaded onto the Retroid Pocket 3+)

Build in **vertical slices** that run on the device as early as possible, as planned for the Pi version.

1. **Project and pipeline.** A Kotlin and Jetpack Compose project, plus a GitHub Actions workflow that builds a
   debug APK as a downloadable build output. Walk the user through creating the repository, pushing the code
   from Windows, and installing the APK (`adb install`, or copying the APK and opening it on the device).
2. **The thin slice.**
   - Home launcher registration.
   - Scanning ROM folders (with All files access).
   - Detecting the RetroArch package and its cores.
   - Showing Home with real 3D cartridges (port the renderer to OpenGL ES 3).
   - Launching a game through RetroArch's intent, with `QUITFOCUS` returning focus to Shelf.
   - The save-slot copy around each session.
   - Home → network `QUIT` → save → return.
3. **Library and boxes:** the case models at real sizes, the flat framed grid, the glow ring, floating and shadows.
4. **Opening the box:** the hinged cover, the manual booklet and tray, the panel in Compose layered over the 3D view, and turning the closed box.
5. **Art and info:** the ScreenScraper downloader with free fallbacks, label art, manuals as page images, and media.
6. **Readers:** the manual reader, media viewer and achievements page (RetroAchievements web API).
7. **Apps:** the app drawer, Mark as game, and Android game cases and cards.
8. **Settings:** themes, per-platform and per-game emulator choice, and standalone emulator launching
   (research each emulator's intent: DuckStation, PPSSPP, melonDS, Mupen64Plus FZ, and so on).
9. **First-run setup.**
10. **Polish:** higher-detail models, smoothed edges, materials, motion tuning, and the reduced-motion setting.

## Open questions

- Touch support, and how much of it.
- Whether to keep a cartridge "insert" animation on Android, since there's no console model.
- Multi-disc PlayStation games: one box with several discs in the tray?
- How standalone emulators report that a game has closed, so Shelf knows when to play its return animation.
- Whether ScreenScraper grants a developer ID for a personal project, and how the user stores it.
