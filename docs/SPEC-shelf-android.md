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

## 5. Home tab: the cartridges you carry

- A row of the 10 most recent games as their media: cartridges, discs, cards. The selected one is anchored
  on the left, larger, and glowing; the rest recede to the right. A tile at the end opens the Library.
- **A plays or resumes immediately.** Down reaches three cards: **Open the box**, **Achievements**, **Saves**.
- Below the row: title, console chip, last played, play time, and whether A will resume or play.
- Everything floats very gently (about 3 px over about 8 seconds, each item out of step with its neighbors)
  and casts a soft shadow. The selected item floats a little higher. No swaying or rotating.

## 6. Library tab: the shelf of boxes

- **Grid:** 5 columns of closed boxes in a framed panel, with a rail on the left (All games, each console,
  then Collections: Favorites, In progress, and the user's own). Sort: Recently played, A to Z, Release year.
- **True relative sizes:** all boxes share one scale, so an N64 box is visibly bigger than a GBA box. Each
  box is turned slightly so its spine shows.
- **Flat camera, no perspective.** On the TV, perspective made items near the edges drift and overlap the
  frame, and the user asked for it to go.
- **Rows:** the first row starts at the top of the frame. As you move down, the current row centers, with
  half of the rows above and below visible, faded at the frame's edges. The last row settles near the
  bottom, leaving room for its title.
- **Selection:** a soft glow ring that hugs the box's outline and moves with it, brighter toward the
  top-left light and slowly breathing as a whole. The selected box floats higher above its shadow. Title and
  details appear under the selected box only. Nothing shows while you're in the rail.
- **Custom collections:** "+ New collection" with an on-screen keyboard (a physical keyboard also types into
  it). Y on a collection in the rail offers Rename and Delete. Games are added from the open box with "Add to collection."
- Y on any box toggles Favorite.

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
- Buttons: **Resume or Play** (primary), **New game**, **Close the box**, **Game settings**, **Add to collection**, **Favorite** (Y).
- **Saves:** four slots with screenshots and the last-played tag, for RetroArch games. For standalone
  emulators, a note saying which emulator keeps the saves.
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
  back, the DS card into the back edge, the PlayStation disc under its opening lid, the UMD into the back). The
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
  Saves section of the game page says so and has a button that opens that emulator, where the saves are.

## 10. Apps tab

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

## 16. Technical plan (agreed direction, not yet built)

- **App:** a native Android app in **Kotlin**. **Jetpack Compose** handles all 2D UI: settings, panels, setup,
  app drawer, crisp text. An **OpenGL ES 3 view** draws the 3D (boxes, media, backdrop), ported from the
  prototype's WebGL renderer, whose shaders are already GLSL ES.
- **Launching RetroArch:** an explicit intent to RetroArch's `RetroActivityFuture`, with extras `ROM`,
  `LIBRETRO` (core path), `CONFIGFILE`, and `QUITFOCUS` (hands focus back to Shelf on quit). Detect which
  RetroArch package is installed: `com.retroarch`, `com.retroarch.aarch64`, and so on.
- **Per-profile settings:** generate a config for RetroArch with Shelf's save folders, auto save and load,
  and network commands on.
- **Standalone emulators** each launch through their own intents. These still need researching, per emulator.
- **Home launcher:** an activity with the `HOME` and `DEFAULT` categories, so Shelf can be the default home app.
- **Permissions:** "All files access" (`MANAGE_EXTERNAL_STORAGE`) to read ROM folders and move RetroArch's
  save files, and package visibility (`QUERY_ALL_PACKAGES`, or `<queries>` declarations) for the app drawer.
- **Art and info:** a background downloader. ScreenScraper needs a developer ID, which the user requests on
  ScreenScraper's forum, plus the user's account. Fallbacks are RetroArch's thumbnail database, Wikipedia, and
  RetroAchievements' game images. Download the smaller "normalized" videos. Turn manual PDFs into page images
  when they're downloaded.
- **Build:** a Gradle project in a GitHub repository, with a **GitHub Actions** workflow that builds the APK.
  The user downloads the build output and sideloads it. The user chose this over installing Android Studio.

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

- A tab that treats the consoles as exhibits: a timeline of every supported console, oldest first.
- **Exhibits:** the console, its media and its box (closed and open), turned with the D-pad or by dragging.
  While looking at an exhibit, L and R switch between them; in the list they switch tabs as usual.
- **Info:** maker, North American release, units sold, processor, media, and a few facts.
- Models follow the real silhouettes and sizes, stylized, with no logos.

## 17. Not yet designed

- Touch interaction.
- Search.
- Sleep and power options. Android handles these, though a quick menu could be added.
- Multi-disc PlayStation games.
- Cloud sync.
- Controller remapping.
- Exact transitions between tabs.
