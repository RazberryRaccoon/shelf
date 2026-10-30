# Shelf for Android: specification

Status: agreed design, prototyped (`prototypes/shelf-android.html`). The user had not yet reviewed prototype v1 when
this was written. Where the prototype and this spec differ, the spec states the intent; the prototype is a sketch.

## 1. The idea

A home-screen launcher for the **Retroid Pocket 3+** that treats games as physical objects:

- **Library** is your shelf at home: every game as its **box**, at the original box's real proportions.
- **Home** is what you carry with you: your recent games as **cartridges, discs and cards**.
- **Opening a box** shows its manual and its cartridge or disc inside, with everything else about the game beside it.

It should feel like a modern handheld operating system: minimalist, calm and charming. It should **not** feel
like a game room, an arcade, or a nostalgia piece ("I don't want nostalgiaslop, I want modern design languages and charm").

## 2. Device

- **Retroid Pocket 3+:** Unisoc T618 (Mali-G52 MC2 GPU), 4 GB RAM, 4.7-inch **1334×750** touchscreen, Android 11.
- Controls: D-pad, left stick, A, B, X, Y, L1/R1 (and L2/R2), Start, Select, Home. Touch is optional for now.
- Layout is designed at **1334×750**. The browser prototype is fixed at that size.
- The GPU has plenty of headroom compared with the Pi 3 and Odroid. Use it for smoother animation, more
  detailed models, smoothed edges and nicer gradients.

## 3. Platforms

Ten consoles plus Android games. Box sizes are width × height × depth in millimetres. "Modern case" means the
original was cardboard; Shelf shows it as a **modern clamshell case at the original box's size** (user decision).

| Platform | Box | Box size (mm) | Media | Label window (mm, front face) | Emulators offered (first is default) |
|---|---|---|---|---|---|
| NES | modern case | ~127 × 182 × 25 | cartridge "Slate" | 55 × 90 (real label 55 × 97 with a 7 mm top fold) | RetroArch (Mesen), RetroArch (FCEUmm), RetroArch (Nestopia) |
| SNES | modern case | ~174 × 121 × 28 | cartridge "Arc" | 82.5 × 37 (real 82.5 × 44, 7 mm fold) | RetroArch (Snes9x), RetroArch (bsnes), Snes9x EX+ |
| N64 | modern case | ~174 × 121 × 28 | cartridge "Crest" | 54.5 × 62.5, curved top | Mupen64Plus FZ, RetroArch (Mupen64Plus-Next), RetroArch (ParaLLEl N64) |
| Genesis | real clamshell case | 128 × 197 (incl. tab) × ~25 | cartridge "Monolith" | 74 × 60 (real 74 × 67, 7 mm fold) | RetroArch (Genesis Plus GX), RetroArch (PicoDrive), MD.emu |
| Game Boy | modern case | ~123 × 123 × 20 | cartridge "Pebble" | 42 × 37 | RetroArch (Gambatte), RetroArch (SameBoy), Pizza Boy GBC |
| Game Boy Color | modern case | ~123 × 123 × 20 | cartridge "Prism" (see-through) | 42 × 37 | same as Game Boy |
| Game Boy Advance | modern case | ~123 × 123 × 20 | cartridge "Wedge" | 43 × 22 | RetroArch (mGBA), RetroArch (gpSP), Pizza Boy GBA |
| PlayStation | jewel case | 142 × 125 × 9.9 | disc | printed disc | DuckStation, RetroArch (SwanStation), RetroArch (Beetle PSX) |
| DS | real case | ~125 × 137 × 14 **(verify)** | card "Chip" | ~28 × 20 (estimate, verify) | melonDS, DraStic, RetroArch (melonDS DS) |
| PSP | real case | ~104 × 175 × 14 **(verify)** | small disc in an original caddy | printed disc | PPSSPP, RetroArch (PPSSPP) |
| Android games | keep case, no branding | ~135 × 171 × 12 | game card | ~34 × 22 | opens the app |

Sources and caveats are in `RESEARCH.md`. The DS and PSP case sizes and the DS label size were not verified.

## 4. Structure

- **Tabs:** Home, Library, Apps. Switch with L1/R1, or press Up to the top bar and choose one. No list of
  button prompts in the corners.
- **Top bar, right side:** status (Wi-Fi, battery, clock), Settings button, profile button.
- **One profile,** created in first-run setup. No profile picker.
- **The Home button** always returns to Shelf's Home tab. If a game is running in RetroArch, Shelf saves it first (see §9).

### First-run setup

1. Welcome.
2. Name and profile color.
3. Theme, with live previews.
4. Games found (folders scanned, counts per console), emulators found, and "Add another folder" (Android folder picker).
5. Sign-ins: ScreenScraper and RetroAchievements, both optional.
6. "Make Shelf your home screen?" (Android's default-home prompt).

Every step after the first has a **Back** option (B also goes back). The welcome step has a **Quick setup with
test data** option for testing, which walks through every step with test values; it's for development builds only.
The on-screen keyboard is **QWERTY**, with staggered rows like a real keyboard.

## 5. Layout and Home

- **Home is the base screen.** The top bar has the profile on the left (opens the Profile settings), a row of
  round icon buttons in the middle (**Library**, **Apps**, **Museum**, **Settings**; the focused one fills in
  and shows its name), and status on the right. No tabs.
- Library, Apps and the Museum **slide up from the bottom as a sheet** that covers Home completely; B slides it
  back down. The recent-games row is never visible while a sheet is up, and only comes back once it's fully down.
  Settings rises from the bottom as its own full screen.
- A row of the 10 most recent games as their media: cartridges, discs, cards. The selected one is anchored
  on the left, larger, and glowing; the rest recede to the right.
- **A plays or resumes immediately. Down opens the game's page in place:** the open case slides in from the left,
  the cartridge or disc lifts into its tray, the rest of the row moves up into a small strip under the top bar,
  and the details rise from the bottom once the cartridge is in. Up from the top of the page (or B) plays it backwards.
- **L1 and R1** step through Home, Library, Apps, Museum and Settings in that order (wrapping), Home being the first. In the
  Museum's viewing mode they switch exhibits instead.
- **Home's picture:** under the row, the selected game's media fills the lower part of the screen, darkened and
  fading out toward the top, with the fade's edge at the bottom of the row. Settings › Appearance › Home background:
  Box art, Title screen, Screenshot (default), Cartridge art, or Off. It crossfades between games and hides while a
  page is open. The planet sits in the top half on Home.
- **From the Library:** the sheet slides down out of the way while the box grows and opens, and the details
  appear on the right. Closing reverses it: the box closes and shrinks back into its place as the sheet rises.
- Below the row: title, console chip, last played, play time, and whether A will resume or play.
- Everything floats very gently (about 3 px over about 8 seconds, each item out of step with its neighbors)
  and casts a soft shadow. The selected item floats a little higher. No swaying or rotating.

## 6. Library: the shelf of boxes

Built in Shelf Native 0.23 (`Library.kt`, `Collections.kt`, `Cards.kt`), from the vision talked through with the user.

- **Two panes, like Settings.** Left: All games, Favorites, Recently added, Not played yet, Most played (these three
  fill themselves; Settings › Library turns them off), then Collections (yours, each with its color and count, and
  "+ New collection"), then Consoles, then Hidden. Moving down the list changes the right pane at once; Right or A goes
  into it, Left or B comes back, B on the list closes the Library.
- **Order and filters (0.24):** under the list's name, a bar of pills: the order, the filters (All games and live
  collections), Clear, Save as a collection (All games), Add games and Arrange (your collections); Up from the first
  row or Start reaches them. All games is one list (A to Z by default); its orders: name either way, recently played,
  most played, recently added, release year either way (headed by decade), console, genre, developer, publisher (headed
  by group). Filters: consoles, genres, played, favorites only, release decades, players, developers, publishers, in a
  collection, added this month; each opens a card of ticks with counts. A saved filter is a live collection (✦): its
  games are whichever match, and it's edited, renamed, recolored and deleted like any other. Console pages sort;
  your collections and Favorites stay console by console with an order within.
- **Covers (the default view):** the closed boxes, four across, every box at one scale (1.2 layout px per mm), so
  sizes are true to each other, each standing on its row's line and turned a little so its spine shows. Collections
  and Favorites have a heading per console (its color's dot, its name, how many), its games after it: a collection of
  one series reads console by console. Each console's games in
  the list's order: A to Z, Recently played, Most played, Release year, or (your collections) Your order. Spines come
  as a second view once spine art is in.
- **The chosen box** lifts toward you, turns to face you a little more, glows in its console's color (the 0.22 glow)
  and the sky's nebula takes that color; its name and when you last played it show under it, nothing under the others.
  A opens it (the Library slides away and the page opens from the box; back, the Library returns), X its collections
  card, Y favorite, L1/R1 the previous or next console.
- **Collections:** a name, a color (eight to choose from), any games; a game can be in many. Adding and taking out:
  X on any game (Home, the Library) or the Collections tile on its page brings up a card with Favorites and every
  collection, each with a tick (A ticks, the card stays), "New collection…" and Hide this game. For many at once, Y on a
  collection › Add games: every game with ticks on the ones in it, A toggles, L1/R1 jump consoles, B done. A new
  collection goes straight into Add games. Y on a collection also: Arrange (your own order: A picks a game up, the
  d-pad moves it within its console, A puts it down), Order, Rename, Color, Move up/down the list, Delete (asks; the
  games stay). Saved in the preferences and in Shelf/Media/collections.json (backups carry it; games are matched by
  file, or by console and name on another handheld).
- **Hidden games:** any game can be hidden (its card, or Game settings › Hide this game); it's gone from Home, the
  Library and every collection. Library › Hidden asks for a code: four presses of the d-pad and A, B, X, Y (dots, never
  the buttons; Start or Select cancels; a wrong code empties softly). Open until you leave the Library. The first hide
  chooses the code (twice). Settings › Library: change it (asks for the old one), reset it (doesn't: against snooping,
  not security), and take Hidden off the Library's list (then Select on the list, then the code).
- Pinned collections on Home: wanted, place not decided yet.

## 7. Opening a box: the game page

**The motion:** the box moves to the left half of the screen, faces you, and **the front cover swings open on
the spine hinge**, ending to the left of the base. The open box takes about half the screen (about 620 px wide).

**Inside:**
- **Left half (the cover's inside):** the **manual** as a thin 3D booklet, shaped like its box (landscape for
  SNES and N64, square for the Game Boy family). Its cover is the manual's first page. A on it opens the manual
  reader. With no manual, it shows an empty booklet outline, and A explains why.
- **Right half (the base):** a molded tray holding the **cartridge, disc or card** with its label art. A on it
  plays or resumes. The cartridge lifts slightly when selected.
- A label ("Read the manual", "Play" or "Resume") appears over whichever object is selected.

**The panel (right side of the screen):**
- Console name, title, and facts: released, developer, genre, players, and which emulator it plays with.
- Buttons, as a row of square icon tiles with a short name under each (no pills): **Play or Resume** (primary),
  **Emulator**, **Close box**, **Settings**, **Collection**, **Favorite** (Y). A line under the row says what the
  focused one does ("Plays with DuckStation. Choose another for this game.").
- No Saves section for now (the user, October 2026: saves all showed as handled by the emulator app anyway).
  Shelf's save slots (§15) come back when they work with RetroArch on the device.
- **Close box:** the details step aside and the closed box takes the middle of the screen to be turned around.
- **Media:** a row of thumbnails (video, title screen, screenshots, box front and back). A opens the media viewer.
- **Achievements:** count, bar, points and recent badges. A opens the achievements page.
- Description, and a credit line naming the art source.

The panel scrolls to keep the focused section in view.

**Moving around:** Left from the panel's first column goes to the cartridge, then to the manual. Right goes back.

**Close the box:** the cover swings shut. The D-pad turns the box freely, Up and Down tilt it, LB and RB snap
to the front, spine and back, and B reopens it.

**Media models:**
- Cartridges and cards are **original designs**, one per console, with label windows at the real label sizes.
- PlayStation games use a disc. PSP games use a small disc in an original caddy.

## 8. Manual reader and media viewer

These carry over from Shelf for TV.

- **Manual reader:** two-page spreads with the cover first, Left and Right to turn pages, and a page track
  along the bottom. **Your place is saved per game,** and reopening says which page you're on. **Y bookmarks
  a spread,** shown as a ribbon on the page and a marker on the track, and **LB and RB jump between bookmarks.**
  Scanned manuals should show at their true page shape (the prototype's generated pages are always tall).
- **Media viewer:** one item large, with the whole sequence as a strip below that stays centered on the
  current item. Left and Right move. **Videos never autoplay;** A plays and pauses. Game pictures show at
  each console's real screen shape.

## 9. Playing and coming back

- **Launch animation:** the game's console rises into view, the media flies from where it was (Home or the
  open box) to the console's slot and clicks in (cartridges into the top or front, Game Boy cartridges into the
  back, the DS card fully into the back edge of the closed DS, which then opens; the PlayStation disc under its
  opening lid; the UMD behind the PSP's back door, with the PSP turning around to show it). The
  power light comes on with a short jingle, the camera eases in, and the screen fades to black. No white flash.
  Shelf plays this before handing off to the emulator.
- **Coming back:** the reverse. The power light goes off, the media pops out and flies back to the first spot
  on Home or into its box's tray, and the screen it came from fades back in around it. It plays whenever Shelf
  returns to the front after a game, including through Android's app switcher.
- **Starting the game:** see §16 for how each emulator is launched.
- **Coming back:** the Home button (Shelf is the home launcher) brings Shelf forward. If RetroArch is still
  running a game, Shelf tells it to save and quit through RetroArch's network commands, then shows a short
  "Saved to slot N" message. The cartridge settles back into its tray, or into the first spot on Home if
  you launched from there.
- **Standalone emulators** keep their own saves. Shelf only notes when you last played and for how long. The
  game page has no Saves section for now (see §6).

## 10. Apps

- **App drawer:** a grid of every installed app, with an optional filter to hide emulators.
- **Y on an app** offers **Mark as game**, App info and Cancel.
- **Mark as game** moves the app to the Library's Android shelf as a game with full treatment: a generic keep
  case (no branding) and an original game card. Its art comes from game databases where possible, otherwise
  from a cover built from the app's icon.
- **Move back to Apps** lives in that game's settings.
- Saves and achievements don't apply to Android games, so those sections say so or are hidden.

## 11. Achievements page

- **Side panel:** completion ring, earned count, points, hardcore unlocks, number of players, and the
  Beaten or Mastered award.
- **List:** filters for All, Earned and Locked. Each row shows the badge (gray with a lock if locked), title,
  description, tags (Progression, Win condition, Missable, Hardcore), points, earn date, and the percentage
  of players who have it.
- The fields match RetroAchievements' `API_GetGameInfoAndUserProgress` (see `RESEARCH.md`).

## 12. Settings

- **Layout:** a full page, with categories on the left and rows on the right. Values cycle with A. Long
  sections scroll to keep the selected row in view.
- **Profile:** name (on-screen keyboard), color, run setup again.
- **Appearance:**
  - theme tiles with previews
  - accent follows the console (on or off)
  - case color: neutral, or tinted with the console's color
  - background motion
  - interface sounds
- **Platforms:** a list of all eleven. Each platform's page has:
  - default emulator
  - how saves are handled
  - screen filter
  - game folder
  - BIOS status
  - box type
- **Emulators:** installed emulators with status, "Get it" for missing ones, and "Look for emulators again."
- **Art and info:**
  - source: ScreenScraper or free sources
  - ScreenScraper account
  - gameplay videos and manuals switches
  - preferred region
  - update everything now
- **Achievements:** RetroAchievements account, hardcore mode (disables saves and rewind), unlock pop-ups.
- **Controls:** button layout (Nintendo or Xbox, i.e. which button confirms), Home button behavior, vibration.
- **Home screen and apps:** use Shelf as the home screen, what the Apps tab shows, Android game count.
- **About:** device, storage, version.
- **Per-game settings** (from the game page):
  - emulator override, defaulting to the platform's
  - saves note
  - screen filter override
  - choose different box art
  - manual status
  - show on Home
  - for Android games: move back to Apps

## 13. Themes

Seven built-in themes, switchable live. Each one sets: background, panel color, text colors, accent, case
plastic color, shadow color, and the 3D rim light.

| Theme | Character |
|---|---|
| **Space** | Deep indigo nebula, twinkling stars, a large planet colored by the current console (the favorite from the TV version). |
| **Dark** | Plain black. |
| **Light** | Plain cool white. |
| **Aurora** | Dark, with slow soft teal and violet fields, and faint stars. |
| **Dawn** | Warm peach and cream. |
| **Slate** | Cool matte grays with a single warm accent. |
| **Bloom** | Light, with vivid pastel fields. |

- **Accent follows the console** (default on): the accent color, and one background field, take on each
  console's color. Accent-colored text on the light themes is blended toward the text color for contrast.
- Light themes get light case plastic.

## 14. Visual and motion principles

- **Cameras:** flat (orthographic) for grids and rows, so things stay aligned. Perspective only for the open
  box and turning it.
- **Motion:**
  - Exponential easing toward targets (springs).
  - Floating at about 3 px over about 8 seconds.
  - The selection glow breathes slowly as a whole ring. It never travels around the shape or wobbles.
  - Everything stops when Background motion is off or the system asks for reduced motion.
- **Shadows** are soft ellipses on the panel under each object. The selected object rises while its shadow stays put.
- **Panels:** soft rounded rectangles in the theme's color with a thin border. Content fades at a frame's edge rather than being cut.
- **Typography:** Unbounded for titles and Manrope for text, in sentence case. Prompts appear inline where needed (for example "Y" on the Favorite button), never as a corner prompt bar.
- **Sound:** soft and optional. No loud or arcade sounds.
- **Detail:** higher-detail models than the TV version (more segments on rounded corners, beveled edges, smoothed edges), and nicer gradients and lighting.

## 15. Saves

- **RetroArch games** get four slots per game:
  - Before launching, Shelf copies the chosen slot to `<game>.state.auto` in the save-state folder.
  - RetroArch is configured with `savestate_auto_load` and `savestate_auto_save` on, so it loads that state and writes it back when it quits.
  - Afterwards, Shelf copies `.state.auto` back into the slot, along with the screenshot RetroArch saves alongside it.
  - "New game" starts with no auto state. Hardcore mode disables saves.
  - This was proven working end to end in the Pi version's session tests, with a stand-in for RetroArch.
- **Standalone emulators** manage their own saves. Shelf says so in the panel and on the Home card.

## 16. Technical design (first version built, October 2026)

- **App:** a small native Android app in **Java** (`android/`) that runs the Shelf interface, the same WebGL page
  as the browser prototype, in a full-screen WebView loaded from the app's assets. This replaced the earlier plan
  (Kotlin, Jetpack Compose and a port of the renderer to OpenGL ES 3): the user approved the prototype's look and
  motion, and running the same page keeps both exactly, on day one. Porting to a native renderer stays possible if
  the WebView is ever too slow.
- **Native mode:** when the page sees `ShelfNative` (the Java bridge), it switches from the invented library to your
  ROMs and apps, launches real emulators, and saves its state to the app's storage (`state.json`). The browser
  prototype is unchanged. The invented games stay on as the Museum's examples.
- **Finding games:** a `ROMs` folder (any capitalization) at the top of internal storage or an SD card, with one folder
  per console, named as ES-DE names them (`nes`, `snes`, `n64`, `genesis` or `megadrive`, `gb`, `gbc`, `gba`, `psx`,
  `nds`, `psp`, plus a few aliases). Titles come from file names, without region and dump tags. Disc numbers stay;
  an `.m3u` playlist stands in for the discs next to it. Each console can also have a folder you choose (Settings ›
  Platforms › Games folder, or in setup), searched along with the ROMs folders.
- **Launching:** ES-DE's Android configuration is the reference for every emulator's package, activity and extras.
  RetroArch gets `ROM`, `LIBRETRO` (`/data/data/<package>/cores/<core>_libretro_android.so`), `CONFIGFILE` and
  `QUITFOCUS`. Standalone emulators get either the storage document link (ES-DE's `%ROMSAF%`), Shelf's own read-only
  `content://` link (`RomProvider`, ES-DE's `%ROMPROVIDER%`), or a plain path, as each expects. 25 emulators.
- **RetroArch cores:** Android keeps RetroArch's core folder private, so Shelf can't list the downloaded cores. It keeps
  its own list: the first time you play a console whose core isn't on it, Shelf asks which core you use and makes it
  that console's default. Settings › Emulators › RetroArch cores ticks them on and off.
- **Coming back:** when Shelf returns to the front after a launch, the page plays the return animation and records
  the play time.
- **Controller:** Android's key events (D-pad, A, B, Y, L1, R1, Back) and the left stick or hat are passed in as
  Shelf's actions; holding a direction repeats it. Settings › Controls › Button layout swaps A and B.
- **Home launcher:** the activity has the `HOME` category, so Shelf can be chosen as the home app; the Home button
  while Shelf is in front acts as Shelf's own Home.
- **Permissions:** All files access (`MANAGE_EXTERNAL_STORAGE`) for the ROM folders, `QUERY_ALL_PACKAGES` for the app
  drawer and emulator detection.
- **Build:** a Gradle project built by GitHub Actions (`.github/workflows/android.yml`). Every push to `main` publishes
  a release with `Shelf.apk`; one fixed signing key (checked in, debug only) lets each build install over the last.
- **Art and details** (built): libretro's database gives every official game name for a console, with year, developer,
  publisher, genre and sometimes a description; libretro's thumbnails give box art, title screens and screenshots. All
  free, no account. Find art for every game (Settings › Art and info) matches each ROM by checksum or a near-exact
  name and leaves the rest; a game's settings › Art and details shows the closest matches with their box art, starting
  from the file name, with a search you can change. Real box art also becomes the cartridge label and the disc print.
- **Art from several services** (built): libretro (no account), ScreenScraper, TheGamesDB, RetroAchievements,
  SteamGridDB and IGDB. A game's settings has a picker for each kind (box front, box back, spine, cartridge label or
  disc art, title screen, screenshot); each says which services give it and which you've connected, and searches all
  the connected ones at once. Accounts are entered in Settings › Art and info with Android's own text box (type or
  paste), checked with a small search, and kept in Shelf's private storage. Find art for every game also uses
  ScreenScraper, by the ROM itself, for the kinds libretro doesn't have. Back, spine and label art are drawn on the box
  and cartridge.
- **Not yet:** manuals and videos (ScreenScraper), Shelf's own save slots
  (RetroArch keeps its own saves for now), RetroAchievements, manuals and media, the RetroArch network commands.
  These stay as designed above.

## 16a. Choosing emulators

- **Every known emulator for each console** is offered, RetroArch cores and standalone apps, following ES-DE's
  Android configuration (for example 9 for PlayStation, 9 for DS, 10 for Game Boy). Installed ones are marked;
  choosing one that isn't installed opens its store page. RetroArch cores download on first use.
- **Per console:** a default emulator, and an **Ask every time** option.
- **Per game:** the game page's "Plays with" button (and Game settings) overrides the console's default.
- **Other app…** picks any installed app, so nothing is unsupported.

## 16c. Menu music

- Four original tracks: Orbit (pads and bells), Afternoon (soft electric piano and a light beat), Tidepool
  (warm and minimal) and Lanterns (plucked strings, a little melody). Settings › Sound: on or off, track or
  Shuffle, volume. It fades out while a game launches or plays, and dips while a video plays.

## 16b. Museum

- A screen that treats the consoles as exhibits: a timeline of every supported console, oldest first.
- **Exhibits:** the console, its media and its box (closed and open), turned with the D-pad or by dragging.
  While looking at an exhibit, L and R switch between them.
- **Info:** maker, North American release, units sold, processor, media, and a few facts.
- Models follow the real silhouettes and sizes, stylized, with no logos.

## 17. Not yet designed

- Touch interaction.
- Search.
- Sleep and power options. Android handles these, though a quick menu could be added.
- Multi-disc PlayStation games.
- Cloud sync.
- Controller remapping.
- Exact transitions between screens.
