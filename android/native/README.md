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
| `Article.kt` | A game's About opened up as an article over the page (Wikipedia's article, its pictures between the paragraphs). |
| `Meta.kt` | A game's details and media, saved on the device, with where each piece came from. |
| `Media.kt` | Every source (libretro, Wikipedia, retrokit's manuals, ScreenScraper, TheGamesDB, RetroAchievements, SteamGridDB, IGDB), each kind's choices, Find everything missing. |
| `MediaPage.kt` | Game settings › Media: a tile for each kind of media, every source's choices. |
| `Manual.kt` | The manual reader: two-page spreads, turning pages, bookmarks. |
| `Player.kt` | A game's video, played when you choose it. |
| `Museum.kt` | The Museum: the timeline, the six views of each console (Console and Cartridge, each also Inside; the box open and closed), its story, its colors (and the console Studio) and its default shell. |
| `Consoles.kt` | Console colors (the real ones, yours, painting a console part by part) and its circuit board. |
| `Models.kt` | Consoles and cases: placing any model, a case's art, its hinged cover and its tray. |
| `Studio.kt` | The Studio: finishes, named colors, your saved shells, and its controls. |
| `Sounds.kt` | The quiet interface sounds, synthesized on the device. |
| `Setup.kt` | First-run setup (welcome, your name and color, games folders, emulators, done); its pages are also Settings. |
| `Backdrop.kt` | Everything behind the 3D layer. |
| `Sky.kt` | The theme environments other than Space, drawn into a texture of their own (`materials/sky.mat`). |
| `Music.kt` | Menu music, your own music, and the themes' ambient sounds, looping without gaps (`assets/sound`, made by `tools/music/compose.py`). |
| `Games.kt` | Your games, what you've played, each game's shell color, each console's emulator, launching. |
| `Ui.kt` | Colors, fonts, Shelf's own label art, and the 1334 × 750 board. |

## What's in version 0.34

The user's note on 0.33: the tabs over the row were in the way of the top bar. They're gone; Up from the row goes
straight to the top bar again. **Press left on the first game** and a panel slides out from the left edge: Recent and
every collection pinned to Home, with how many games each has and a mark on the one showing. **A** shows one in the
row (its games grow in where they belong, as before), **Y** picks one up to move it (up and down, A to put it down),
**B** or right closes it. L2 and R2 still step through them without opening the panel. A small ‹ at the left edge
shows when there's somewhere to go, and the collection showing (other than Recent) is named quietly over the row.

## What's in version 0.33

**Pinned collections on Home** (the user chose tabs over the row, "but I don't want it to get too much more crowded…
show only the first little bit of the next collection… as you cycle through it scrolls through and reveals more", and
"you should also somehow be able to change the order"):
- Pin any collection (yours, Favorites, Recently added, Not played yet, Most played) from its menu (Y) in the
  Library: **Pin to Home**. It becomes a tab over Home's row, after Recent.
- **L2 and R2** on Home change tab; the row's games fly in. Four tabs show in full, the next one peeks in and fades
  out, and the tabs scroll along as you go.
- **Up** from the row puts the cursor on the tabs: **Y** picks one up, left and right carry it, **A** puts it down.
  **Settings › Home › Pinned to Home** lists them in order too: left and right move one, A unpins it.
- A game played from Not played yet keeps its cartridge until the return animation is done, then leaves the tab.

## What's in version 0.32

Three more sound packs (Settings › Sound › Sound pack), from the user's "eventually" list: **Felt** (a piano with its
hammers muffled in felt), **Kalimba** (a thumb piano: a round note with a bright little ping) and **Air** (a breath of
soft noise around each note, like a current console). Measured against the first four so none is louder or clips.

## What's in version 0.31

**Themes are places now** (the user: the recolored themes were "kind of lame… each like a different environment"):
Space (still the default), City, Forest, Ocean, Aurora, Desert and Stormy, each by day and by night. Settings › Home
› Time of day: Automatic (by the clock, day from 7 to 7, easing between), Day or Night. First-time setup has a step
for choosing one. Space is drawn by the backdrop as before; the others by `Sky.kt` (`materials/sky.mat`) into a
smaller texture of its own, every other frame except on Best looks, so a forest costs a fraction of drawing it
full size every frame. The look came from the viewer (`prototypes/shelf-themes.html`) and the user's notes on it.

**Music and ambient sounds** (`Music.kt`): a track for each theme, written note by note and synthesized
(`tools/music/compose.py`, no samples), which follows the theme unless you choose another, your own file, or none
(Settings › Sound). Ambient sounds under it (off to start): the city far off, birds by day and crickets at night,
waves, wind, rain with thunder a moment after the lightning. Every loop is two players handing over without a gap.
The music fades out as a game goes in and back in when you return. The interface sounds moved to Settings › Sound.

After the user's second look at the viewer: Forest is a foggy Appalachian forest (ridges fading into mist, hemlocks,
fireflies flashing together at night), Stormy's rain falls (it rose before) and is gentle, and ambient sounds are
quieter, with their own volume always in Settings › Sound next to the music's.

Also: libretro matching says what really went wrong and has "Type a name…"; the three automatic collections can each
be hidden (Settings › Library); the Museum says L2 and R2; the Library's and the Museum's side panels are taller;
Home's text has a soft shadow, for the bright themes.

## What's in version 0.30

The user's notes on 0.29:
1. **Apps names its buttons** along the top right: L2 R2 Tabs, A Open, X Hide emulators (in All), Y Options.
2. **Three tabs** (L2 and R2): All, Emulators and Games. An app you make a game leaves All (it's in Games, and on Home
   and in the Library); Not a game brings it back.
3. **You say what's an emulator:** Y › Mark as an emulator (or Not an emulator). The emulators Shelf knows start out
   marked. X hides emulators from All; they're always in the Emulators tab.
4. **Android game boxes are 2:3** (120 × 180 mm), the shape of SteamGridDB's covers, so cover art isn't squeezed.
5. **L1 and R1 step through the pages**: Home, Library, Apps, Museum, Settings, round and round (from any of them, at
   its top level). What L1 and R1 did inside the Library (jump a group) and the Museum (change console) is on L2 and R2.
6. **Settings is a gear,** in the top bar and on the game page.
7. **The Collections tile's cards** no longer show through each other before it's chosen.
8. **Manual covers:** a release for Japan and elsewhere ("(Japan, USA)") takes the English manual's front cover (the
   right half); NES manual pages are wide, so their single covers stay whole (and lie sideways in the box); a manual
   scanned in pairs throughout is halved only if a seam shows down the middle.
9. **Home's shadows** no longer show through Apps.

## What's in version 0.29

Made overnight on my own (the user: "work on whatever you think would improve it… pretend I'm here"), on top of 0.28:
1. **Apps** (the top bar's Apps button, which only said "later" before): every app on the handheld as icons on soft
   plates, the ones you opened lately first, then A to Z. The chosen one lifts, floats and glows in Android green. A
   opens it through a short fade (and Shelf fades back in when you return). X hides the emulators. Y: Open, Make it a
   game, App info. L1 and R1 jump between the two groups.
2. **Android games** (spec §10): Y › Make it a game turns an app into one of your games, with the Android cartridge
   and box, the Android handheld's start animation, a place on Home and in the Library (Android), and art from the
   art services where they have it. Game settings › Move back to Apps undoes it.
3. **Shelf as your home screen:** Android now offers Shelf Native when you press Home; Settings › System › Home screen
   opens Android's choice of home app.
4. **Themes** (from the "eventually" list): Settings › Home › Theme: Space, Midnight, Aurora, Dusk, Deep sea, Ember.
   Each is a background tone and a color the nebula and planet lean toward; panels take on the background.
5. **Sound packs** (from the list): Settings › System › Sound pack: Soft (as before), Glass (bell-like), Wood
   (marimba), Bubble (each note glides up into place). All made on the handheld from tones, as before.
6. **Play time:** how long you've played each game (from the game starting to coming back) shows on its page and
   under its box in the Library; a new order, Most time played.
7. **Surprise me:** a pill in the Library that picks a game from the list at random (leaning toward ones you haven't
   played lately) and glides to it.
8. **L1 and R1 on Home** open the Library and Settings (spec §4).

## What's in version 0.28

The user's notes on 0.27:
1. **Starting a game from the open box, choreographed instead of rushed.** The cartridge lifts out of its tray toward
   you and moves to where it waits, beside where the console will stand, already the size it will be in the console;
   the box slides away behind it at its usual pace; only then does the console rise. The waiting spot is measured
   from every pose the console takes on its way up (its spring's overshoot included), so it never rises into the
   cartridge. From Home the cartridge waits the same way.
2. **Every frame checked.** A clip test in tools/lighting-rig (`cliptest.mjs`) runs every console, from Home and from
   a page, every 20 ms: how deep any point of the cartridge gets inside the console, and whether the console hides
   any of it on the way. None, for all of them.
3. **No glow round the console** at the hit any more.
4. **Coming back:** the cartridge stays on the screen where it floats; the box slides in behind it, and it slots in.
5. **Shockwaves on every console:** level rings over a home console (half rings out of the NES's front, as before),
   arcs over a handheld's top; they stay where they were made when a handheld turns round afterwards.
6. **Long text in Settings and Game settings** starts at its beginning; on the chosen row it waits a moment, then
   glides slowly to the end, waits, and fades back to the start. Other rows end in "…".
7. **Manual covers:** the SNES's and N64's manual pages are upright (their front and back covers together fit the
   wide box), so their cover pairs are now found and halved, and the cover lies sideways in the box. A first page
   wider than one page should be is also checked for the seam where two pages meet.

## What's in version 0.27

The user's four notes on 0.26:
1. **Starting a game with the box open:** the box darts off to the left at once, clear of the console springing up
   (it used to go through its slower closing sequence and clip into it). It slides back in when you return.
2. **No more sparks.** The hit is now the console's own glow (as on the game page) swelling round it in its color,
   and thin shockwave rings spreading from the slot, lying flat in the console's plane like a ripple; the part of a
   ring inside the console isn't drawn (the NES's bursts out of the front). A handheld taking its cartridge from above
   has no ring (seen edge on it looked like a halo). Nothing stays on the screen any more: the effects are cleared
   when the game starts, when you skip, and on the way back (a power-light burst was left over before).
3. **"The Legend of Zelda" and every "…, The" game:** the art and details services are searched with the name as
   people write it ("The Legend of Zelda: A Link to the Past"), not the catalogue's "Legend of Zelda, The - …", which
   they didn't recognise (box backs came from those services). The page's title reads the same way.
4. **Manual covers:** a scan whose first page is the back and front covers side by side shows only the front cover
   (the right half; the left for a Japanese manual). Shelf tells by comparing the first page with the pages inside.
   The booklet in the box now takes the cover's real shape, as big as fits, upright or lying sideways when that fills
   the box better, so the cover is never squeezed; carried out to read, a sideways one turns upright on the way.

## What's in version 0.26

The start animations, livelier (the user: "bouncier, floatier, punchier… feel the impact… particle effects"):
1. **The console springs up** from below, overshooting a little and settling, then floats gently while it waits.
2. **The game is picked up** with a little dip first, flies along a higher curve leaning into the way it moves, and
   floats a moment over the slot.
3. **Then it slams in:** it winds up a little further out and drives in fast. The console squashes against the hit
   and rings back, with a tiny shake, and the game rebounds a touch. On the NES the cartridge slams in, then down.
4. **Sparks:** little square sparks with white cores and four-pointed twinkles burst from both edges of the slot in
   the console's color, with a ring spreading over the console and a soft glow. A few float up from the power light
   as it comes on.
5. **Springy parts:** lids pop open and clap shut, the NES's door springs shut, power switches snap over and buttons
   spring back, and a back-loader springs round to face you.
6. **Impact sounds:** every game now lands with the deep clunk as well as the click.

## What's in version 0.25

The start animations, rebuilt and studied frame by frame (tools/lighting-rig's launch scene renders every console's
sequence as a strip of pictures, so each clip and collision could be seen and fixed):
1. **The NES's door swings in.** It's hinged along its top edge, as on the real console: the cartridge pushes it in as
   it slides through, a dark opening behind it. Then the cartridge is pressed down into the connector with a soft clunk,
   and the door drops shut.
2. **Nothing passes through anything any more.** The game is lifted from where it was and carried along a curve in
   front of everything, lines up over its slot (slowing as it arrives), waits a breath, and is pushed in.
3. **Everything fits on the screen.** While a tall handheld takes a cartridge from above, it sits a little lower and
   smaller, so the cartridge waiting over it isn't cut off; it comes back up to size as it turns to face you.
4. **Each console does what the real one does.** The PlayStation's open button is pressed and its lid pops up for the
   disc. The PSP's disc now sits in the hollow under its door, where you can see it before the door closes.
5. **Power switches.** The SNES, N64, Genesis, Game Boy and Game Boy Color slide their power switches over before the
   light comes on; the NES's and the PlayStation's power buttons are pressed (the NES's stays a little in, as the real
   one latches).
6. **Smoother motion.** The console arrives turning gently into place as it slows, a back-loader already turning to
   show its back; no beat starts or stops with a jolt.
7. **Two new sounds,** as soft as the others: a switch's tiny double tick and the NES's deep clunk.
8. **Coming back** plays the same sequence backwards, a little quicker: the switch slides off, the light goes out,
   the game comes out of its slot, flies home and the console sinks away.

## What's in version 0.24

The user's twelve notes on 0.23 (the start animations get their own round next):
1. **Home's top bar:** the round buttons no longer lose the top of their circles. The row of games sits a little lower
   with the selected one a little smaller, so the buttons' names have room.
2. **The Library loads much faster.**
   - Boxes that scroll away are kept and dressed again for the next game, instead of being built from scratch.
   - Art is read on two threads at the size the grid shows it.
   - Paintings for games without art are drawn off the main thread.
   - Art read for boxes you've scrolled past is let go.
3. **Sorting and filtering.**
   - **All games** is one list; the default is A to Z. Its order can be name (either way), recently played, most
     played, recently added, release year (either way), or by console, genre, developer or publisher. Grouped orders,
     and the years by decade, get headings.
   - **Filters:** consoles, genres, played or not, favorites only, release decades, players, developers, publishers,
     in a collection, added this month.
   - **Save as a collection:** keeps the sort and filters as a live collection (✦) that updates itself. It can be
     renamed, recolored, refiltered, reordered and deleted like any other.
   - **Consoles and collections** can be sorted too; your own collections and Favorites stay console by console.
   - **Where:** the pills under the list's name (Up from the first row, or Start).
4. **The message pill** fades whatever is showing (it stuck while the Library was open).
5. **Crashes:**
   - Art read for boxes long scrolled past piled up in memory; now it's let go.
   - The Library and a game page could both grab the same freshly read box art and use it twice; the Library now
     reads its own.
   - A box that can't be made is left out instead of stopping Shelf.
   - If it still closes on an error, Shelf shows what happened on the next start: a photo of that screen helps.
6. **The glow works on pages opened from the Library.** The Library's glow and the page's took turns switching each
   other off; now each draws its own silhouette.
7. **Backing out of the open box from the Library** plays one close sound, not two.
8. **Group headings have more room** above the boxes, and the chosen box lifts less, so its glow stays clear of them.
9. **Box backs aren't cut off any more.** A scan rarely has exactly the box's measured shape, and cropping the
   difference cut the edges; now up to 12% out it's left whole and stretched imperceptibly, and only beyond that is the
   excess trimmed. (A test pattern in the rig showed the models themselves show the whole picture.)
10. *(The start animations: next round.)*
11. **PlayStation jewel cases:** the clear cover was a quarter opaque and mirror smooth, a sheet of glare over the art.
    It's now far thinner, so the art reads clearly with a soft sheen (compared side by side in the rig).
12. **A game's page:** the Back tile is gone (B and Up already go back), so the five tiles spread out and their names
    don't overlap.

## What's in version 0.23

**The Library**, as talked through with the user (SPEC §6):
- **Two panes:** your lists on the left (All games, Favorites, Recently added, Not played yet, Most played, your
  collections, each console, Hidden); the chosen list's boxes on the right, four across at their real relative sizes.
  There's a heading per console with its games after it, in the list's order (A to Z, recently played, most played,
  release year, or your own).
- **The chosen box** lifts, turns toward you and glows in its console's color, and the sky takes that color; its name
  and when you last played it show under it. A opens its page from the box; X brings up its collections card; Y makes
  it a favorite; L1/R1 jump a console.
- **Collections:**
  - X on any game (Home or the Library), or the new Collections tile on a game's page (where Favorite was; Y still
    makes it a favorite), ticks it in and out of Favorites and each collection, or starts a new one.
  - Y on a collection: Add games (every game, A ticks), Arrange (pick a game up and move it), Order, Rename, Color
    (eight), Move, Delete.
  - A new collection goes straight into Add games.
  - Collections go into media backups.
- **Hidden games:** hide any game (its card, or Game settings); Library › Hidden opens with a four-press code of the
  d-pad and A, B, X, Y. Settings › Library changes or resets the code, can take Hidden off the list (then Select on the
  list opens it), and turns the automatic collections off.
- **Small touches:** boxes arrive with a little rise as they load in; the ticks pop as you tap them; a wrong code fades
  out softly instead of shaking.

## What's in version 0.22

The user's note on 0.21: the lighting and Settings are great, but the glow around what's chosen "looks like its made up of
layers of glowing paper". It was a grown copy of the whole model, and every part of it (both halves, the board, the
label) glowed on its own, overlapping. Rebuilt from scratch:
- **One silhouette.** A view of its own draws only the chosen object into a half-size picture each frame. The glow is
  that silhouette blurred through the picture's smaller, pre-blurred copies (mipmaps), so it's one smooth shape, with
  no rings. The object's own outline still cuts it off crisply, and it never covers the object.
- **The console's color.** The halo is a richer version of the console's accent: violet for the GBA, amber for the N64,
  coral for the DS, lime for the Game Boy. A thin bright line hugs the edge in a lighter tint of it, with a faint wide
  aura further out.
- **Charm.** It swells a little as something is chosen, then breathes slowly. A soft light travels round it about once
  every eleven seconds. Moving between the cartridge, the manual and the box, the glow fades off one and swells onto
  the next.

Tuned in the lighting rig with Filament's own renderer, on light and dark backgrounds and in several consoles' colors.

## What's in version 0.21

The user's notes on 0.20 (a cleanup pass before the next big feature set):
1. **The lighting, redone and measured.** Every model was rendered with the app's own 3D engine (Filament 1.75.1's web
   build, in `tools/lighting-rig`) and its numbers measured. Three findings:
   - The tone curve (ACES) darkened everything: a mid-grey label came out at half its brightness, and dark plastic
     sank toward black.
   - The key light reached the cartridges' fronts almost edge-on, so it added nearly nothing.
   - Filament's exposure works as its inverse, so Settings › Lighting's Exposure was backwards.

   Now:
   - **Tone curve:** PBR Neutral, which keeps colors true and only rolls off bright highlights.
   - **Key light:** from the upper left, lighting the fronts at about half strength, with its reflection kept off them.
   - **Balance:** about half studio light and half key light.
   - **Result:** a grey label comes out within a few shades of its own value (128 → 124, where it was 67), shells
     within a couple. Gold is a deeper gold.
   - **Settings:** Settings › Lighting has Brightness in place of Exposure, and everyone starts from the new values once.
2. **A glow around what's chosen.** On a game's page, the chosen cartridge, closed box or manual gets a crisp
   white-blue line along its edge, fading into a soft halo, breathing slowly, on top of lifting a little. It hugs the
   object's real outline. It's drawn only where the object isn't, so the box's tray can't hide it and it never covers
   the object (`Outline.kt`, `materials/outline.mat`).
3. **Settings in two panes.** Its sections (You, Games, Emulators, Home, Game pages, Lighting, Media, Saved colors,
   System) run down the left. The chosen one's settings show on the right and change as you move down. Right or A goes
   in; left or B comes back out. A media source's page still opens in the right pane. Home's picture settings are part
   of Home; System has Smoothness, the frame-rate meter, Sounds, setup again and the version.

## What's in version 0.20

The user's two notes on 0.19:
1. **Smoother: the drawing is rebuilt.** The meter showed Android spending 14–16 ms of every frame passing the picture
   along, and about half the frames running late. Two things caused it: the starry background was repainted by Android's
   canvas every frame, in its own full-screen layer, and the 3D went through an extra copy (a TextureView) before reaching
   the screen. Now the 3D engine draws the background itself, first, in the same frame (one small shader,
   `materials/backdrop.mat`, doing the canvas's own math; the shadows are flat shapes over it), and draws straight to the
   screen (a SurfaceView). Only the panels are left to Android. Checked against the old drawing: the same within one or
   two shades out of 255. Smoothness now starts at Balanced (2× smoothing) for everyone, once.
2. **Opening a game's page doesn't pause.** The box's pictures are read ahead while you rest on a game in Home, the
   pictures in the panel are read on another thread (they appear a moment later), and the screwdriver is only loaded when
   a cartridge first comes apart.
3. **The foil finish, tuned by measuring.** Rendered under the app's own lighting next to the plain label: at rest it is
   as bright as the plain label, and turned into the light about twice as bright. Its brushed lines are a little fainter.

The meter's second line is now "Panels": since the 3D and the background no longer go through Android, it times the
panels alone.

To change the shaders: edit `android/native/materials/*.mat` and run `materials/build.sh` with Filament's `matc` 1.75.1
(from the Filament release on GitHub); it writes the compiled `.filamat` files into the app's assets.

## What's in version 0.19

The user's five notes on 0.18:
1. Home's scrolling styles are roomier (about as many in view as Classic): Cover flow, Spines, Carousel and Stack
   spread out. Spines turns the other way, so a box's real spine (its left side, where the art is) faces you.
2. The foil finish is less metallic, so at rest it isn't almost black (it still flashes as it turns).
3. Coming back from a game is simply out of black: the chosen transition only plays going in.
4. The frame-rate meter adds Android's own timings for each frame (drawing, sending to the graphics chip, swapping,
   and how many took over 16.7 ms), which the first meter couldn't see.
5. Looking closely: the left stick moves the model smoothly (as the right stick turns it); the d-pad still steps. The
   top bar (profile, Library, Apps, Museum) goes away while you look, on a page and in the Museum.

## What's in version 0.18

The rest of the user's notes on 0.16:
1. **Home shows boxes** (Settings › Home shows): each game as its closed box, art and all. Opening a game's page starts
   from that closed box and flows to the page's view (it opens, and for the cartridge view the cartridge lifts out and
   the box slides away); closing goes back into the box. Starting a game from Home, the cartridge comes out of its box.
2. **Home scrolling** (Settings › Home scrolling): Classic, Centered, Cover flow (the selected game faces you, the
   others turned toward it, mirrored either side), Spines (the others edge on and close, a box's spine showing),
   Carousel (round a wide turntable) and Stack (tucked behind either side like a fanned deck). Every style glides as
   you scroll, with cartridges or boxes; the game's name and chips sit in the middle for the centred styles.
3. **Label finishes**: Standard, Matte paper, Glossy, Worn (faded, scuffed, creased, a corner torn off), Holo (rainbow
   bands and sparkle, a sheen that runs through the colours as it turns), Opalescent (milky pastels that drift) and
   Foil. Each game's Customize shell has a Label finish row (seen live); Settings › Label finish is the default.
6. **The media folder**: everything lives in Shelf/Media/<console>/<game>/ on storage (moved there from inside the
   app the first time each game is read): box, back, spine, cartridge, title, screenshot, video, manual and info.json.
   Settings › Media can open the folder in Android's Files app, back everything up into a zip wherever you choose,
   and restore a backup. Files you put in a game's folder are picked up by their names (box, front or cover; back;
   spine; cart, label or disc; title; screen, snap or shot; any video; a PDF), and each tile on a game's media page
   has "Choose a file…" too. A .nomedia file keeps it all out of the Gallery.
7. **The cartridge in the open box**: left from the first tile chooses it (lifted a little in its tray); A starts the
   game. Left again chooses the manual.
9. **Looking closely**: the cartridge alone or the closed box can be chosen the same way (left from the first tile),
   and A shows it big in the middle with everything else dimmed: L1 and R1 zoom, the right stick turns it, the d-pad
   or left stick moves it, B goes back. In the Museum, Y does the same for whatever is showing.

## What's in version 0.17

The user's bugs and the first of their notes on 0.16:
- PlayStation manuals show: a jewel case's booklet now has an inside, seen through the open lid (its art faced away).
- The battery: Shelf listens to Android's battery news (level and charger, the moment either changes) instead of
  asking every 20 seconds, so the bolt shows while plugged in and the level is right at once.
- The console Studio's "B to leave without saving" is dark on its light button.
- Starting a game no longer zooms in on the screen or power light: it ends with a transition to black, chosen in
  Settings › Starting a game ends with: Fade, Iris (closing on the power light or screen), Wipe down, Diagonal sweep,
  Blinds, Clock, Dots, Shutter, or a different one each time. Coming back opens the same way.
- Frame rate: Shelf already draws with the screen's refresh (60 a second); Settings › Show frame rate puts a meter in
  the corner (frames per second, frames drawn and skipped, how long the screens and the 3D take, the slowest frame),
  and Settings › Smoothness trades looks for speed (Best looks: 4× smoothing; Balanced: 2×; Smoothest: a cheaper edge
  blur and a sky painted once). The background picture's shaders are no longer made again every frame.
- The manual reader: a line along the bottom fills as you read (bookmarks marked on it), L1 and R1 zoom (the pages
  drawn again sharper), the right stick moves around a zoomed page, the buttons are named along the bottom; L2 and R2
  now jump between bookmarks.

## What's in version 0.16.1

- Reading a manual closed the app. Carrying the booklet out of the box re-attached it to its cover each frame with
  the cover's *entity* where Filament wanted its *instance*, and Filament stops the app outright on that. The booklet
  now always stays attached to its cover and is placed relative to it. Reading pages is guarded against damaged or
  oversized PDFs too.
- A crash inside the 3D engine or Android's own code (below Kotlin) left no report. Shelf now asks Android how it last
  stopped, and a native crash shows on the problem page the next time it opens, with Android's log from then.

## What's in version 0.16

The user's six notes on 0.15:
1. The NES box's clips sit above and below the cartridge, inside the box (they poked out of its sides).
2. Moving along Home is lighter: the background picture is read off the main thread, the sky's colors are made a
   few times a second while they shift instead of every frame, the row follows a little faster, and holding a
   direction repeats a little sooner. Nothing was taken away.
3. Media, all in one place:
   - **Game settings › Media** opens a page of its own: the game's standard name (Change match picks it), Find
     everything missing, and a tile for each kind (box front, spine, back, cartridge art, title screen, screenshot,
     video, details, description, manual) showing what it has and where it came from, or a dashed outline where
     it's missing. A on a tile lists every source's choices, each labelled with its source (Remove it, too); X finds
     a missing one on its own.
   - **Settings › Media**: how many games are missing each kind, with A to find them all (it carries on in the
     background), Find everything missing, and every source with its state and step-by-step setup (what to sign up
     for, where to find the key), its fields, and Test it.
   - Sources: libretro, Wikipedia and retrokit's manual library need nothing; ScreenScraper, TheGamesDB,
     RetroAchievements, SteamGridDB and IGDB need a free account or key. ScreenScraper also needs Shelf's own
     developer credentials (see docs/SCREENSCRAPER.md).
   - The page shows them: a downloaded back and spine go on the 3D box, the pictures row has the box back,
     cartridge art and the video (which plays only when you choose it), and the credits say where things came from.
     Choices you make are kept when a game is looked up again.
4. The DS box is wider than tall (135 × 122 mm), so its art isn't cut off.
5. The Museum calls each console's games by their name: Cartridge, CD (PlayStation), UMD (PSP), Game card (DS),
   App cartridge (Android), and their Inside views too.
6. Manuals: a booklet inside each box's cover (a jewel case's front booklet), wearing the manual's own cover once
   it's downloaded. "Read the manual" (under the tiles) brings in the open box if it isn't showing, lifts the booklet
   out of the cover and up in front of you, and opens it into the reader; or, with the box open, left from the first
   tile chooses the booklet where it lies. The reader shows two-page spreads with a page folding over as you turn,
   Y bookmarks, L1/R1 jump between bookmarks, and your place is kept. Manuals come free from retrokit's library on
   archive.org (and ScreenScraper, once connected); "Find the manual" fetches one the first time.

## What was in version 0.15

The user's three notes on 0.14:
1. Customize shell (and the Studio) always shows the cartridge itself: from Game settings it puts itself back together
   and stays out of its box, turned to its usual angle, so you see the colors as you pick them.
2. About opens up as an article (A on About): everything behind dims and a reading column rises in, with the game's
   console, name, year, developer, publisher and genre at the top, then the whole Wikipedia article (not just its
   opening) in larger type with indented paragraphs and section headings, the box, title screen and screenshot set
   between the paragraphs with captions, and the credit at the end. Up and down read, left and right jump between
   sections, a thin line beside the column fills as you read, B closes. (Games looked up before this are looked up
   again once, quietly, for the whole article; pictures are kept.) The panel's About shows the opening few lines
   and "A Read the article".
3. The Museum's views are Console, Cartridge, Cartridge Inside, In its box, Box closed and Console Inside. Cartridge
   Inside takes the cartridge apart like Console Inside: it turns to a three-quarter view, the halves float straight
   out from the board, the stick turns only the board, and the halves move further off when it needs the room; X
   turns the board over. Leaving it, the halves close round the board before the box comes (`Cart.inside`). Discs and
   cards skip it.

## What was in version 0.14

The user's five notes on 0.13:
1. Opening a game's page from Home: the cartridge floats down to its spot and, for the open or closed box, the open box
   slides in from the left, the cartridge comes to rest just over its tray and then drops in with a click, and (closed
   box) the cover swings shut. Going back to Home plays it the other way. Starting a game from a boxed page, the box
   slides away as the cartridge flies off; coming back, the cartridge returns to its spot and the box slides in for it
   again.
2. Game settings takes the cartridge apart more deliberately (about 2.6 seconds): the front half lifts off, the halves
   move well apart and settle on the surface, then the board rises out of the back half and is set down below them,
   then the screwdriver appears. Everything is spaced further apart and shown bigger. Settings work while it plays.
3. L2 and R2 between the cartridge, the open box and the closed box walk through the same steps in either direction
   (`BoxFlow` in `Models.kt`): the cartridge to the closed box slides the box in, slots the cartridge, shuts the
   cover; the closed box to the cartridge opens it, lifts the cartridge out and slides the box away. The box never
   grows out of nothing. The Museum's "Showing" views use the same steps.
4. The Game Boy, Game Boy Color and Game Boy Advance cartridge pockets are smaller: only about the top of a seated
   cartridge shows.
5. The closed box is bigger on the game page (up to 500 tall), filling the left half so the front art shows well;
   a little bigger in the Museum too.

## What was in version 0.13

The user's ten notes on 0.12:
1. The GBA's cartridge goes all the way in, flush, as on the real one (a deeper pocket).
2. The lids move: the DS starts closed, takes its card and opens; the PlayStation's lid and the PSP's door open for the
   disc and close after it. Each lid is its own hinged part in the console's file (`Console.lid`).
3. Settings › Your saved colors lists every shell and console color you've made; A deletes one (after asking).
4. The box does one thing at a time: from the closed box to the cartridge, the cover opens, then the cartridge lifts
   out, then the box goes (and closing, the cartridge settles in before the cover shuts). Game page and Museum.
5. Game settings takes the cartridge apart on a surface: the halves separate and lie side by side (the front turned
   over), the board comes up and out and is set down below them, and a small screwdriver appears (`Cart.teardown`).
   Leaving puts it back together. The box steps aside first.
6. Cartridge boards have a program chip with its legs, a second chip, and a coin battery on the cartridges that kept
   saves on one (NES, SNES, Genesis, Game Boy, Game Boy Color).
7. The Museum's row says "Customize console colors".
8. Boxes are their real sizes (the DS case 122 × 135 mm, the PSP's 104 × 177; see RESEARCH.md), and a game's box shows
   its downloaded front box art, cropped to fill, never stretched.
9. Settings › Home picture: which picture (screenshot, title screen, box art, none), brightness, how far up it
   reaches, how much fades, the fade (plain, dots, or lines that thin out), and pan speed, shown live as you change them.
10. Inside is closer, and the halves move further off as you turn the board toward them, so it never goes through them.

## What was in version 0.12

**Starting a game, and coming back** (`Launch.kt`). Play: Home (or the page) steps aside and the game's console rises
into view in its Museum colors; the cartridge flies from where it was, lines up with the slot and slides in (the Game
Boys turn round to take it in their back pockets, the DS takes its card in the back edge, the PSP its disc behind the
back door, the PlayStation its disc in the well, the NES flat through its front door). It seats with a soft click, the
power light comes on with a quiet chime, a handheld's screen glows with the game's title screen (the label art until
one's downloaded), and the view eases in on the power light or the screen as everything fades to black: about 2.5
seconds, then the game starts. Coming back plays it backwards: out of black, the screen dims, the light goes off, the
cartridge slides out and flies home, and the console sinks away. Any button skips to the end of either. Where each game
sits, and the path it slides in along, are each console's `insert` in the Studio's `consoles.js`.

**The box on the game page.** A game's page shows the cartridge, the cartridge in its open box, or the box closed;
L2 and R2 go through them, and they flow into each other as in the Museum. Settings › "A game's page opens on" chooses
which comes first (the open box, unless you change it).

## What was in version 0.11

A fix: 0.10's hollow console halves were built inside out (every surface wound to face into the plastic), so the
renderer hid each half's real outside and showed the inside of the far wall, 2.4 mm in and lit backwards; the rounded
edges looked dark and thin. They face out now, and look exactly as the solid shells did.

## What was in version 0.10

Inside is an exploded drawing now: as the console turns to a three-quarter view, its halves slide straight apart along
the seam (a home console's top up and its base down, a handheld's front toward you and its back away), leaving the
board floating between them. The right stick turns only the board (X turns it over to its print); the halves hold
still. Moving to another view turns the board back into place and the halves close round it (`Console.explode`,
`ConsoleBoard.turnIn`; how far each half travels is in `consoles.json`). And every half is hollow, like the real shells:
a 2.4 mm face and sides, rounded outside, open at the seam (`cup` in the Studio's `console.js`); the Game Boy pockets
have floors.

## What was in version 0.9

Fixes after trying 0.8. The consoles' real translucent editions (the N64's Funtastic colors, the clear Game Boy, the
Atomic purple Game Boy Color, the Glacier Game Boy Advance) really are see-through now (they were stored as glossy).
Every console is molded in two halves with a fine seam around its sides, like the cartridges; a home console's seam
is low, just under its board, and a handheld's is near the back. Inside, the console opens along that seam as it
turns: a home console's top swings up and back like a lid, a handheld's front opens like a book, and the board stays on
the lower half (the hinge is in `consoles.json`, `Console.open`). The Game Boy, Game Boy Color and Game Boy Advance
have real cartridge pockets in their backs, open at the top, with the connector's mouth on the floor. Anodized
aluminum cartridges are brighter (`Finish.floor`: they reflect more than a plain metal of their color).

## What was in version 0.8

Every console has its circuit board inside: the real board's layout, roughly (the cartridge connector, the processors,
memory, the sound and video chips, regulators on their heatsinks, crystals, capacitors, connectors and ports), with
small surface parts scattered over the free space, copper traces drawn under the solder mask, and RaccoonTek's print on
the underside (the back, on handhelds). Two ways to see it in the Museum: give the shell a clear, frosted or smoke
finish (the console's real translucent editions now really are see-through), or choose the new **Inside** view (left
of Console), where the console lifts away off the top of the screen and leaves its board to turn; X turns it over to
its print. `ConsoleBoard` in `Consoles.kt`; the boards are `board-*.glb`, the see-through consoles `console-*-clear.glb`.

## What was in version 0.7

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
