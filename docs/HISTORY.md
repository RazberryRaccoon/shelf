# History: what was built, what broke, what we learned

## 1. Shelf for Odroid GO (finished, working)

**Hardware:** Odroid GO, an ESP32-WROVER with a 320×240 screen. NES, Game Boy and Game Boy Color only.

**Built:**
- A browser prototype (`prototypes/shelf-odroid-launcher.html`).
- Standalone ESP-IDF firmware, later abandoned (kept in `code/shelf-firmware-standalone.zip`).
- **Shelf as retro-go's launcher** (`code/shelf-retrogo/`, the final version), with an installer script that
  patches retro-go to auto-save on quit.

**Tools:**
- `convert_art.py`: turns cover and label images into Shelf's `.img` format.
- `fetch_info.py`: identifies ROMs by checksum and downloads art and info from RetroArch's database, its
  thumbnail server and Wikipedia.
- `crop_labels.py`: a Tkinter tool that finds the label in a whole-cartridge photo and crops it, with
  automatic detection, ROM matching, saving straight to the card, and progress remembered between sessions.

**Fixed along the way:**
- Upside-down screen: `LCD_MADCTL = 0xA0`.
- Lag: the screen transfer moved to the second core, and flash and PSRAM run at 80 MHz.
- "Apps found: none": the partition table is read straight from flash.
- In-game menus: moving to retro-go solved them.
- No Save & Quit: the auto-save patch.

**Other notes:**
- The SD card shares the SPI bus with the screen, so every SD access takes a bus lock.
- The user built everything on Windows, with ESP-IDF 4.4.8 at `C:\esp\esp-idf-v4.4.8` and a helper `C:\esp\idf.bat`.
- Python 3.11 is used for ESP-IDF and the tools. Pillow wasn't available for the user's Python 3.15.

## 2. Shelf for TV (Raspberry Pi 3B+)

### Research findings
- The Pi 3B+ outputs up to 1080p60, has OpenGL ES 2.0 and 1 GB of RAM.
- It plays everything up to the 16-bit era perfectly and PS1 nearly so. N64 is hit and miss; Dreamcast,
  PSP and Saturn mostly don't run.
- Other frontends: ES-DE treats the Pi 4 as its minimum. Pegasus is stuck at an "alpha16" build.

### The prototype (`prototypes/shelf-tv.html`, final after 7 feedback rounds)
- Profiles, Home (cartridges with game-hub cards) and Library (rail, a framed grid with centered rows, glow
  selection, flat camera, floating, shadows).
- **Game page:** 3D box, saves, media row, achievements card, manual button.
- **Full pages:** achievements, manual reader (bookmarks, saved place), media viewer, settings (including art
  sources). Also the launch and return animations, custom collections, and the on-screen keyboard.

### The Pi slice (`code/shelf-tv-pi/`, never tested on hardware)
- **The app:** C with SDL2, OpenGL ES 2, FreeType and libpng. A platform layer separates the Pi (SDL2) from
  an offscreen test build (Mesa EGL surfaceless).
- **Scripts:** a session loop, a Home-button watcher using `python3-evdev`, and Shelf's RetroArch settings.
- **The installer** sets up Samba, auto-login, and display-mode detection for whether RetroArch can draw directly to the screen.
- **Tested here:** the renderer offscreen, the full launch → play → save → return loop with a stand-in
  RetroArch, and saves resuming.
- **Not tested:** real hardware. Unknowns are speed, whether Debian's RetroArch can draw without a display
  server, and controller Home-button reporting.

### Lessons (apply them to Android)

**Rendering:**
- **Perspective cameras make grids drift:** items near the edges get pushed outward and overlap frames. Use a
  flat camera for grids and rows.
- **A highlight panel behind a 3D object never lines up** with it. A flat ring mesh built from the object's
  own outline, attached to the model and fading outward, does. Growing the shell outward (an "inverted hull")
  gave uneven, stepped results.
- **OpenGL ES:** a uniform declared in both the vertex and fragment shader with different precision fails
  to link. Mesa caught this; give the fragment shader its own uniform instead.
- **Software rendering in tests runs at about 1 fps,** so animations stall in screenshots. Advance them
  deterministically with a test hook such as `window.__step(seconds)` rather than waiting.
- **A loop that throws an error freezes everything.** Wrap each frame in try/catch and keep scheduling frames.

**Interaction:**
- **Back must return to wherever a screen was opened from.** A missing game id once froze the whole interface.
- **On-screen keyboard:** Enter has to type the selected key, and only submit right after real-keyboard typing.

**Art:**
- **Nebula art:** fade each cloud's alpha to transparent, not toward a base color, or it shows seams.

**Data:**
- **Real data:** label windows must match the real labels' visible face. NES labels are tall and narrow and
  SNES labels very wide; both fold 7 mm over the cartridge's top edge.

## 3. Shelf for Android (current)

**Discussion:**
- Settled the concept and the console list.
- Researched label sizes, box sizes, the Retroid Pocket 3+, and RetroArch's Android intents.
- Settled modern clamshell cases at original sizes.

**Prototype v1:** `prototypes/shelf-android.html`, about 2,270 lines, built from the parts in `prototype-source/android/`.
- **Contents:**
  - setup, Home, Library
  - the open box with its hinged cover, manual and media
  - Close the box and turning it
  - launching and returning
  - Apps and Mark as game
  - all settings, including per-platform and per-game pages
  - seven themes
  - manual reader, media viewer and achievements
- **Bugs found while testing:**
  - camera functions called by their C names
  - landscape manuals squeezed into a tall booklet
  - back-cover text running into the barcode on wide boxes
  - an unreadable hint over the planet
  - accent text too pale on light themes
- **Status:** published and working. The user moved to Claude Code before reviewing it.

**Prototype v2, part 1 (models):** the user's first review asked for more detailed models with real silhouettes
(see `DECISIONS.md`).
- **New geometry tools** (`r4m.js`): outlines with rounded corners, solids with bevelled edges, lathed parts,
  and merging parts by material so each model is a handful of draw calls.
- **Shader:** a subtle molded-plastic texture (smooth noise, only up close), a second light, and glossy
  reflections for clear sleeves, discs and see-through shells.
- **Rebuilt media:** all eleven, with real outlines (NES ridges, rounded SNES top, arched N64 top with side
  notches, stepped Genesis, notched Game Boy, clear GBC with the board inside, black-bottomed PS1 disc,
  rounded UMD caddy), recessed labels, labels folding over the top edge, screws, back labels and gold contacts.
- **Rebuilt cases:** bevelled shells, a raised rim and clear sleeve on the cover, hinge knuckles, a molded tray
  with a rim or disc hub, the finger notch, the Genesis hanging tab and a clear PS1 jewel case.
- **Eleven consoles** (`r4c.js`), each with a `slot` for the coming launch animation.
- **The Museum tab** (`r5m.js`): a timeline of consoles, an exhibit viewer (console, media, box, open box)
  you can turn with the D-pad or by dragging, and facts about each console.
- **Tested:** headless screenshots of every exhibit, Home, Library, the game page, keyboard navigation through
  the Museum, and the crossfade between exhibits (which found and fixed an exhibit hiding the next one).
- **Measurements:** the cartridge sizes are best estimates; the reference sites (ConsoleMods, NESdev) couldn't
  be reached from the build environment. Verify against real cartridges.

**Prototype v2, part 1 review fixes:**
- Museum: once you're looking at an exhibit, L and R switch between console, media, box and open box; back in the
  list they switch tabs again (user's request).
- Game Boy speaker slots moved inside the rounded corner; SNES sliders moved off the raised middle section.
- Game Boy Advance: firmer corners, and the shoulder buttons sit on the top edge instead of floating off it.
- Box art inset so it never runs past the case's rounded edges: front art inside the cover's rim, spine and
  back art inside the bevels, hinge knuckles flush with the spine.
- Box art corners: the art was drawn as sharp-cornered rectangles on cases with rounded corners, so its corners
  hung past the curve. Art is now drawn with matching rounded corners, and the spine art stops before the rounded
  corners. The grip ridges on DS and keep-case covers were removed because they poked through the art.
- The raised rim around each cover was four straight bars with square corners that stuck out past the case's
  rounded corners. It's now one frame with rounded corners that follow the case (`frameGeo` in `r4m.js`).

**Prototype v2, part 2:** the rest of the v1 review (see `DECISIONS.md`).
- **Launch and return** (`r5b.js`): a launch scene drawn over the screen it came from. Each console's `slot`
  kind (top, hand, front, edge, back, lid) decides how its media goes in. The PlayStation lid is its own part
  so it can open.
- **Music** (`r2m.js`): four tracks generated live with Web Audio, scheduled a bar at a time. The real app should
  ship them as recorded audio files.
- **Emulators:** the full list per console from ES-DE's Android configuration, a picker dialog that scrolls,
  per-console defaults and Ask every time, per-game choice from the game page.
- **Game page:** closes the box and glides it back to the Library or Home on Back; the panel slides in, its
  sections staggered.
- **Library:** boxes slightly smaller (BOXK .76) and columns pulled in, so wide boxes clear the frame.
- **Tested:** headless screenshots of quick setup, the keyboard, the emulator picker, the box closing, the
  Sound and platform settings, the saves card, and launches for SNES, PlayStation (lid), NES (front slot),
  Game Boy, DS and PSP, plus the return to Home. Not tested: how the music sounds (headless has no audio).

**Prototype v2 review fixes (round 2):**
- Keyboard and dialogs are solid and dim the screen behind them.
- "Now loading" and the game's name show at the bottom while the console loads it.
- Cases are hollow now: a floor, a thin outer wall and a molded tray with a well, so the media sits below the
  rim and the cover closes over it. Before, a cartridge thicker than the cover poked through while closing.
  The Genesis cartridge is 18 mm thick so it fits its case.
- The Home button closes an open box first. From the Library, the closed box sinks away; from Home, it reverses.
- Opening a box from Home: the open box slides in from the right edge while the cartridge flies into its tray;
  closing reverses it, with Home fading back in underneath.
- The planet moves while the box closes, not after.
- Up and down move between the rows of buttons on the game page.
- The Genesis manual glow was drawn without the case's tab offset; fixed (the cartridge and tray had the same
  offset problem).
- Opening a box from Home, redone after review ("horrible… it should look like the cartridge/CD is being put in
  the case"): the open box slides in from the left while the media lifts off Home and hovers over the tray, then
  lowers straight in with a soft click. Closing lifts it out, slides the box away to the left, and floats the
  media back to its spot.
- Bug found while testing: the disc well in the PlayStation case was a rounded square, which cut the disc into a
  square. The frame builder now spreads points along straight edges so round holes stay round.
- Behind an open box, the screen it came from (Library or Home) now stays drawn, faded far back, instead of
  disappearing; it fades forward again while the box closes, so nothing pops in. The fade is applied to the
  objects themselves (`UNDER` in `r6.js`), not a dark sheet over everything, which is what made Home's dimming
  jump before. The tab's panels (rail, cards, top bar) fade the same way.
- The faded Library or Home panels showed through the open box: the HTML panels sit above the 3D canvas, so
  anything faded but visible there draws on top of the box. Behind an open box the panels now hide completely
  and fade back in once the box is out of the way; the 3D boxes and cartridges stay, translucent, under a dark
  veil that also darkens the backdrop and planet (`drawShade`).

**Launch animations, round 3:**
- Each slot kind now has its own timeline (`TIMELINE` in `r5b.js`): when the media flies and goes in, when a
  hinged part opens and closes, when the console turns its back to you, when the power comes on. Coming back
  plays the same timeline backwards, 1.3 times faster, so every launch has a matching return.
- Consoles can have one hinged part (`lid` in `r4c.js`): the PlayStation lid, the DS top half, the PSP door.
- PSP: turns around, opens the UMD door on its back, the UMD goes in, the door shuts, it turns back and powers on.
- DS: starts closed and turned so the back edge faces you; the card goes fully into the slot; it turns back,
  opens and powers on. Both screens light up softly.
- Game Boy, Game Boy Color, Game Boy Advance and the Android handheld: the cartridge goes 80–88% of the way in,
  like the real ones, instead of sticking out.
- Bug: a flat screen with full shine turned solid white when it faced the light; screens are less shiny now.
- Settings › Appearance: up and down move between the two rows of themes.
- Bug: the slot setting called `deep` actually meant how much of the media is left *outside* the slot, so the
  previous round's "deeper" Game Boy values pushed the cartridges further out. Renamed to `outside` and fixed:
  handheld cartridges, the NES cartridge and the DS card now go fully in; SNES, N64 and Genesis still show
  about half, like the real ones.
- PSP: the UMD now sits on the back under a raised door (like the real PSP's bulge), and the door swings up
  past its hinge so the disc never passes through it.

**Models in Blender (started with the Game Boy Advance):**
- The user asked about reusing another frontend's models; declined (the developer's paid work, and this repo is
  public). Agreed instead: build models in Blender from the user's reference photos.
- `tools/models/`: Blender scripts run through the `bpy` module (no Blender install needed). They build the
  model, bake soft shadows into a grayscale texture, and export positions, normals, two sets of UVs and indices
  per material. The prototype embeds them (`MODEL_DATA`, joined in by `build.sh`) and `r4g.js` loads them.
- Game Boy Advance console: rounded outline with gray shoulder buttons wrapping the top corners, back grips,
  battery cover, screws, glossy bezel, D-pad in its well, A and B in rings, Start and Select with their pads,
  speaker slots, and a slot open at the top and back so an inserted cartridge's back shows. Cartridge: chamfered
  top corners, the bottom tab with 32 gold contacts in its opening, recessed label, raised top band, back screws.
- Second pass, after the user's review ("floating screws, the shape isn't right, molded out of play-doh"): rebuilt
  by comparing renders side by side with the user's photos. The purple face now has the big diagonal cuts at its
  top corners with the gray shoulder buttons stepped back behind them; crisp front edges with a seam between the
  shell halves; screws sunk into holes with cross heads; D-pad in a cross-shaped opening; round Start and Select
  with their slanted pads; A and B in shallow dishes; an enclosed cartridge slot; the battery door with its latch.
  The cartridge got its wider grip at the top, a sunk grip band, a label recess at the real 43 × 21 mm size and
  contacts tucked inside the opening.
- Lessons: compare against the photos at every step, not from memory; the "puffy" look came from big bevels on
  every edge, and real plastic shells have crisp front edges and softer backs.
- Lessons: Blender's default ambient occlusion distance (1 unit = 100 mm here) blacks everything out; use about
  8 mm. Smoothed normals next to rounded edges streak across big flat faces; the exporter uses each large
  face's own flat direction. `r2.js` opens the script tag, so generated data has to come after it.

**The new layout (October 2026):**
- The tabs are gone. Home is the base: the recent-games row, with a top bar of profile, round buttons (Library,
  Apps, Museum, Settings) and status. Agreed through a flat browser mockup before touching the prototype.
- Library, Apps and the Museum are a sheet that slides up over Home. In the prototype, everything on the sheet is
  drawn with the WebGL viewport shifted down (`SHEETOFF`), and its HTML panels get the same `translateY`, so the
  3D boxes and the panels move as one; clipping adds the same offset.
- A game's page opens in place from Home (the row moves into a strip up top) or from the Library (the sheet slides
  away and back). The details wait half a second so the cartridge never crosses them.
- Lesson: when the sheet finished sliding away, its panels jumped back to the top while fading out; they now stay
  below the screen until the sheet opens again.
- First notes on it: up closes a game's page; L1/R1 step through Home, Library, Apps and Museum again; Home got a
  darkened picture of the selected game under the row (a new flat-picture mode in the shader, mode 3, with the fade
  baked into the texture), and the planet moved to the top half.

**The Android app, version 0.1 (October 2026):**
- `android/`: MainActivity (the WebView, controller input, coming back from games, the Home button), Bridge (what the
  page can ask: scan, launch, open apps, save state, permissions), Scanner (ROM folders, apps, emulators), Emulators
  (25 launch recipes from ES-DE) and RomProvider (lends a ROM to emulators that want a `content://` link).
- The page's native mode (`rn.js`) swaps the invented library for real games and apps, keeps the invented games for the
  Museum, and saves state through the bridge. Tested in headless Chrome with a stand-in bridge before the first build.
- The sandbox can't download the Android SDK, so the Java was compile-checked against Robolectric's `android-all` jar
  and the APK is built only by GitHub Actions.
- First run on the Retroid: the page came up zoomed in, showing one corner, and could be panned. The WebView wasn't
  told to honor the page's viewport tag or to stay unzoomed. Fixed on both sides: the WebView uses the viewport tag
  with zoom off, and the page's `fit()` reads the visible viewport, re-fits on every size change (and when Java says
  the view changed), and can't be panned. Checked at the Retroid's size and at phone and tablet shapes.
- Second photo: the 3D scene fit, but every panel sat centered on the top-left corner and setup's options had no
  spacing. The Retroid's Android System WebView was years old (no CSS `inset`, no flex `gap`, no canvas `roundRect`).
  Fixed: `inset` written out long-hand, a flex-gap fallback that turns each flex rule's gap into child margins when
  the engine lacks it, a `roundRect` stand-in, and setup and Settings › About offer to update the WebView from the
  Play Store when it's older than Chrome 100. `?compat` forces the fallback in a current browser for testing.
- Lesson: test against the oldest engine a device might have, not just current Chrome.
- Third round, from using it on the Retroid: everything enlarged for the 4.7-inch screen (the stylesheet's sizes were
  scaled by script, then every screen's containers laid out again by hand: top bar, Home row, game page, Library,
  Apps, Museum, Settings, setup, dialogs, keyboard); the Library rows size themselves to their tallest box; Apps and the
  Museum's list scroll; a games folder per console through Android's folder picker (`ShelfNative.pickFolder`, answered
  by `__nativeFolder`; the tree link is turned back into a file path and scanned with the ROMs folders).
- Fourth round: the Library's side list is rebuilt on every move, and the new list started at the top and slid back
  (the "hiccup", visible once the list scrolls, from Game Boy down). It now starts where the last one was. The title
  under a box follows the box's own animated position. Museum exhibits are sized by their diagonal, so nothing reaches
  past the stage as it turns. L1/R1 continue into Settings.
- Fifth round: Home's order moved to launch time (it was saved before it changed, on return). The running game is in
  the saved state (`P.playing`), so a page Android killed mid-game (`onRenderProcessGone` now recreates instead of
  crashing) still plays the return. Art: `ArtIndex.java` (plain Java, tested on a computer against the real files)
  reads libretro's database (no-intro or redump names, plus developer, publisher, genre and year files; PlayStation's
  also has descriptions), ranks names by shared words with region and disc bonuses, and matches a cartridge ROM exactly
  by its CRC (zips by their header). `Art.java` downloads Named_Boxarts, Named_Titles and Named_Snaps from the
  libretro-thumbnails repositories on GitHub, shrinks them to JPEGs in the app's storage, and answers the page through
  `__nativeArt` and `__nativeArtProgress`. The page draws covers, labels, discs, media and Home's picture from them.
- Lesson: libretro's art server is blocked in Claude's sandbox but its GitHub mirrors aren't; the database names are
  the same names the art files use, so no file listing is needed.
