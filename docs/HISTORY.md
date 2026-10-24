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
- Bug: after scraping, Shelf couldn't start again. The app's start-up code (in `rn.js`) ran before a constant defined
  further down (`cleanDetail`), which only mattered once saved details existed. Any restart of the page (Android does
  that when RetroArch hangs, or to free memory) left a dead page showing the keyboard help, with Home not working.
  Start-up now runs at the end of `rn.js` and is guarded. Tested by reloading with saved details.
- NES opened RetroArch to a black screen: the default core (Mesen) is probably not downloaded on the user's RetroArch,
  and Shelf can't see RetroArch's private core folder. Settings now say so on each console's emulator choice.
- Lesson: test a cold start with every kind of saved state, not just a fresh one.
- RetroArch cores: Shelf used to assume every core was there. It now keeps the list of cores you have (`P.cores`), asks
  on the first launch of a console whose core isn't on it, and has a tick list in Settings › Emulators.
- Art from several services: `ArtSources.java` searches one kind of art at a time in libretro, ScreenScraper
  (jeuRecherche, and jeuInfos by the ROM for Find art for every game), TheGamesDB (Games/ByGameName and Games/Images),
  RetroAchievements (API_GetGameList and API_GetGame), SteamGridDB (search/autocomplete and grids) and IGDB (Twitch
  token, then /v4/games), following each one's published API. Only libretro could be tested for real from Claude's
  sandbox; TheGamesDB was reachable and refused a fake key as expected; the rest are blocked there and need checking on
  the device with real accounts. Account fields use Android's own text box (`ShelfNative.askText`).

**Cartridge Studio (October 2026):** `tools/cartridge-studio/`, from the user's brief. Eight styles from Shelf's
outlines. Problems found in testing: pulling an outline in by the bevel folded corners smaller than the bevel, leaving
spikes (fixed by dropping points whose edges turn around); a studio HDR at exposure 1 washed light plastics out to white
(defaults now exposure .72, environment .85). Poly Haven is blocked in Claude's sandbox, so the bundled studio EXR was
what got tested. Shelf's own renderer (WebGL 1, its own model format) doesn't use these models yet.

**Cartridge Studio consoles (October 2026):** Shelf's eleven code-built consoles ported to the studio. Found in testing:
three.js extrusions are flat-shaded, so the N64's big rounded front showed bands (fixed with creased normals, which also
smooth the cartridges' corners); light gray shells clipped to white under the studio, whose main softbox lights front and
top faces about six times more than the sides (light shells darkened a notch, consoles get exposure .65 and key light .6).

**Cartridge Studio, the user's notes (October 2026):** North American SNES, color variants and seven console fixes.
Found in testing: the corner smoothing from the last round also bent the light across big flat faces (diagonal bands,
clearest on a black cartridge), fixed by keeping front and back faces flat; the page saved every value of every item
you looked at, so new defaults never reached you, fixed by saving only what you change (under a new storage name).

**Game page tiles and Home's dot fade (October 2026):** the Saves section removed, the action pills replaced by icon
tiles, Close the box moved to the middle with the details out of the way, and Home's picture darker, zoomed, panning,
with a windshield-style dot fade. The pan needed a new shader mode: the picture's texture coordinates move while a
second texture, the dot mask, stays fixed to the screen.

**Shelf Native 0.1 (October 2026):** `android/native/`, Kotlin + Filament, Home only. The Studio's models exported as
.glb (`tools/cartridge-studio/export-models.mjs`) and rendered with Filament's built-in glTF materials; the studio HDR
prefiltered on the device. The shared Java moved into `android/shared/` and the ROM provider's name now follows each
app's package, so both apps install together. Found while compile-checking: Filament 1.77's utilities are built with
Kotlin 2.4 (an older Kotlin can't read them), and the renderer's clear color takes doubles. Found by
GitHub's build: Filament's libraries need AndroidX switched on, and from 1.76 they need compileSdk 37 (newer than
the project's Android Gradle plugin), so Shelf Native uses Filament 1.75.1.

**Shelf Native 0.2 (October 2026):** first-run setup and Settings › Games folders, after 0.1 found no games on the
Retroid (it only looked in ROMs/<console>, and had no setup to choose folders). The scanner now also takes a main folder
with a folder per console inside, matches folder names written out, and looks in Games and Emulation/roms.
0.2 closed as soon as it opened on the Retroid: the label texture asked Filament to make mipmaps without the
GEN_MIPMAPPABLE flag, which Filament treats as a fatal error. 0.1 never drew a cartridge on the device (it found no
games), so 0.2's setup cartridge was the first. Fixed, material values are now type-checked before they're set, and
errors in Shelf Native's own code are saved and shown the next time it opens.
It still closed on opening: the setup cartridge grows in from nothing, and so did its shadow, and Android refuses to draw
a gradient with a radius of 0 (an error on the first frame, before the saved error could be shown). Zero-sized
shadows are now skipped, and a start that never got going, or ended on an error, is followed by a plain screen
showing what went wrong (Shelf's saved error, or the app's error lines from Android's log, Filament's included) instead
of starting the 3D again.

The user's notes on 0.2 once it opened: setup worked and games launched, but (1) some setup cartridges were plain
white and the labels looked mirrored, (2) text on the welcome and name pages overlapped and was cut off, (3) the setup
box felt cramped and Continue belonged at the top, (4) Home was far too bright (dark grey read as light grey, the clear
green GBA looked washed out), and (5) each cartridge further along Home's row sat lower while its shadow stayed put.
Fixes: the label goes into Filament upside down (Filament reads textures from the bottom row up); clear shells are
mixed with much less white; the setup box is bigger and stacks its parts so text can't cover the list; Home's rise-in
animation now runs from when Home appears, rather than piling up per cartridge (the cause of the sinking row); the
lighting starts lower (environment .5, exposure .5, key light .6) and Settings › Lighting lets the user tune it live
on the device, since why Filament reads brighter than the Studio's three.js isn't yet known.

Second round of notes on 0.2: the setup box should also use the space at its top and left, with the cartridge centered
in what's left; the list's first row (and every row's sides) was cut off; the "Found … games" message stayed too long;
the cartridge stopped changing after the first page; gold looked yellow. The box now fills the left from 34 px in; the
panel no longer clips the list to its padding (the cause of the cut sides) and the list leaves room for the focus
ring; the message lasts 2.5 seconds by the clock; the cartridge keeps changing on every page unless a console's row is
focused; gold and silver are fully metallic, colored by measured metal reflectance. The user's lighting (environment
.1, exposure .3, key light .2) is now the default, and the Lighting page stays.

**Shelf Native 0.3 (October 2026):** the game page, cartridge first. The full design opens the game's box, but Shelf
Native has no box models yet, so the user chose to start with the cartridge as the page's centerpiece (boxes come with
the Library). The user picked all four suggested touches: spin with momentum, the light catching it as it turns, live
shell color previews, and soft sounds (synthesized sine tones, quiet, with a Settings switch).

The user's notes on 0.3: (1) clear and frosted shells showed as solid color, without the circuit board the Studio
shows; (2) Home's angle and the key light's direction lined up so the flat fronts caught the light's whole reflection
("the gold looks pale yellow/tan in the home screen … when I rotate it just a little bit to the right, it suddenly
looks properly gold"); (3) the tiles' focus ring was cut off; (4) the tiles moved up and down with the length of the
line under them; (5) the page needed its settings, pictures and details. **0.4** fixes the first four (blended shells,
the key light moved high and to the side, about 45° from the fronts' mirror angle, inset tiles, a fixed-height line)
and fills in the page from libretro's database. Checked: the see-through change was run on the real GBA model and the
result read back as a valid glTF file. Not checked on a device before handing over.

Notes on 0.4: the focus rings were still cut off at the left and right (the scrolling part clipped them; it now reaches
into the panel's padding); the line under the tiles was cut off by Pictures (now three fixed lines, shorter text); the
hint under the cartridge should stay; libretro's descriptions are thin (Wikipedia's article intro is now looked up
too, no account); and gold still read tan at the resting angle but right when facing head on. The user: "Preserve the
angle the cartridge is at now … and move the light itself." So the whole lighting (the studio's reflections and the key
light) is turned by twice the resting angle (a reflection turns twice as far as the surface): at rest, a cartridge now
sees the light it saw head on. Filament's own source confirms IndirectLight rotation turns the reflections in the same
sense as the key light's direction. Not checked on a device; Wikipedia couldn't be reached from Claude's sandbox.

After the 0.4 fixes the user wrote: "Gold is FIXED! … everything looks lit so much better now … the color of all the
other cartridges now looks like I remember from holding these cartridges for real." Turning the lighting to the
resting angle was the answer. Then: Home's cartridges bigger with fewer in view (half on the left, two and a half on
the right), a charging indicator, and the Studio (update A of three; B is the Museum, C the console Studio and the
console animations). **0.5** builds them. Not checked on a device: the glow-in-the-dark and pearl finishes rely on
Filament's emissive and sheen values, whose strength on the device is a guess.

Notes on 0.5: (1) see-through finishes weren't see-through in the Studio: a shell still being made has no saved name,
so the model was looked up by name, fell back to the console's default and loaded the solid cartridge (the cartridge
is now made from the shell itself); (2) metal finishes looked nearly black at rest: a paint swatch used as a metal's
reflectance is far too dark (real gold reflects about (1, .77, .34), the "Gold" swatch gave .79, .53, .14), so a metal
now takes its hue from the swatch, a fifth of the way to white, with its brightness setting how much it reflects;
(4) the rest of the row now flies up when a page opens. Note 3 is a design for the box round (DECISIONS.md).
