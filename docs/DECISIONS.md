# Decisions and preferences

Everything the user decided or asked for, in order, grouped by project. Quotes are the user's own words where
available. Decisions that matter most for the current Android project are marked **(carries over)**.

## Constraints Claude set (and the user accepted)

- **Original designs, not replicas.** When the user asked for cartridges "AS CLOSE AS YOU CAN to the original
  designs," Claude explained it can't replicate Nintendo's or Sega's cartridge and console designs, and made
  six original shells instead. The user accepted this and has since asked for real label proportions and real
  box sizes, which are fine. **(carries over)**
- No logos or console-maker branding. The Android keep case is "like Xbox/PS4 boxes" without their bands or logos. **(carries over)**
- Prototypes use invented games, studios and generated art. **(carries over)**

## Shelf for Odroid GO (finished)

Summarized from the first part of the conversation; the full transcript is kept privately, outside this repo.

- A launcher for the Odroid GO, covering NES, Game Boy and Game Boy Color only, with animated cartridges over a space backdrop.
- Box art and label art fill their frames edge to edge, with no title band over games that have art.
- The game page flips the box forward and shows "Left off" (a thumbnail of your last spot), time saved and time played.
- Launch animation: the console rises, the cartridge drops in, power light and jingle. The return animation reverses it.
- Moved from standalone firmware to **retro-go integration**, so its working emulator menus could be used.
- Added an auto-save-on-quit patch to retro-go.
- Built a PC art converter and a label cropper (`code/shelf-retrogo/tools`).
- Audio quality on the speaker is a hardware limit. The fix is a ~33 Ω resistor in series with the speaker
  (three 100 Ω in parallel works).

## Shelf for TV (Raspberry Pi 3B+, prototype finished)

### The brief

- "A sleek, modern console that just plays old games." Not arcade-y, not pixelated, not loud. **(carries over)**
- Keep the cartridge idea: pressing Play puts the cartridge into the console. Keep the space motifs. **(carries over)**
- Wanted: RetroAchievements, borders around games, the highest menu resolution, charm, customization.
- Consoles: NES, SNES, Genesis, Game Boy, Game Boy Color, Game Boy Advance.
- Multiple profiles with a "Who's playing?" screen (the Android version drops this).
- Tabs: Home (recently played, scrolling horizontally) and Library (everything, by console, with filters).
- Browse cartridges; open one to see its game page with metadata, saves, and the 3D box (front, back, spine).

### Feedback rounds (in order)

1. **"Pretty barebones":** research modern console UIs and add their common features.
   - **Achievements** should open a full page listing them all, earned and locked.
   - **"I don't like the button prompts at the bottom right."** **(carries over)** Move the favorite shortcut
     into the game page, but keep Y working everywhere. Shoulder buttons switch tabs without a prompt.
   - Up should reach the tabs. Settings should be a button next to the profile.
   - **Settings should be "a whole page that opens up,"** not a side menu. **(carries over)**
   - **Library:** don't show game names while browsing the console list. Make the selection more visible.
     Keep the current row in the middle, with half of the rows above and below.
   - **The Home button** should always go to Home. Exiting a game should fly the cartridge back to the first
     Home spot, with no game page in between.
   - **Home** shows 10 recent games plus a tile for the whole Library.
   - "A little bit more structure and… panels," but "don't go too crazy."
2. **A bug:** backing out of the achievements page froze everything. Fixed, and the main loop now survives errors.
3. **Labels and selection:**
   - **Each cartridge's label window must match the real label's aspect ratio.** **(carries over)**
   - **Selection:** the highlight panel behind the cartridge never lined up because of parallax; use a glow
     around the model's own edge instead. **(carries over)**
   - **Frame:** add a border around the cartridge area so the half rows don't fade into nothing. **(carries over)**
4. **Art, motion and rows:**
   - **Art:** download all artwork except cartridge labels automatically, with an optional account (ScreenScraper) in settings. **(carries over)**
   - **Motion:** the glow's "wobble… makes me feel like I'm on acid." The selected item now holds still and
     the glow breathes slowly as a whole. **(carries over)**
   - **Rows:** don't start with the top row empty; start at the top, then center as you scroll. **(carries over)**
5. **Collections and flat camera:**
   - **Custom collections.** **(carries over)**
   - **"Just get rid of the parallax":** a flat camera, cartridges still 3D, every item gently floating, and
     the selected one floating more. **(carries over)**
6. **Media and manuals:**
   - **Media viewer:** "no autoplaying videos or anything obtrusive," a scrollable row on the game page and a
     larger view with the sequence as a strip below. **(carries over)**
   - **Manuals:** a button opens the game's manual, which saves your place and supports bookmarks. **(carries over)**
7. **Alignment:** line up the console list and the cartridge frame. Final TV prototype.

### Pi build decisions (written, never tested on hardware)

- Raspberry Pi OS Lite (64-bit) with Debian's RetroArch and core packages, rather than RetroPie, which would compile for hours on a 64-bit Pi 3.
- A native C app with SDL2 and OpenGL ES 2.0, sharing the prototype's renderer design.
- **Session loop:** Shelf exits completely while a game runs, and a script runs RetroArch, then restarts Shelf.
- **Home watcher:** a Python script, run during games, sends RetroArch `QUIT`. RetroArch auto-saves on quit.
- **Saves:** copied through `.state.auto` (RetroArch's own load-a-slot option turns auto-save off).
- Holding Select+Start restarts Shelf, which rescans for games.

## Shelf for Android (current)

### The brief, in the user's words

- "I don't want to just port Shelf for TV to handheld… make an Android game launcher that's the best it can possibly be."
- "Don't start building anything until we've talked it through and you have a good prototype idea."
- **Reverse the TV idea:** scroll through **game boxes**, not cartridges.
- "All the boxes should be scaled and modeled to be the same shape as the original boxes." Front, back and spine visible in 3D.
- **Opening a box:** selecting a game opens the box to fill about half the screen.
  - **Left half:** the manual as a 3D model, with the manual's first page as its cover. Pressing it opens the
    manual viewer, with bookmarks and saved place.
  - **Right half:** the cartridge or disc with its art.
- **The rest of the screen:** save states, Play, images, metadata, description, achievements, and a button to
  close the box and turn it.
- **Home tab is still cartridges:** "the carts you carry around with the console, while having the boxes on
  your shelf at home."
- **Not a literal game room:** "it would be tempting to make it… game room themed. I don't want this." More
  minimalist, like a handheld OS. "I don't want nostalgiaslop, I want modern design languages and charm."
- **Themes:** "Space, plain white, plain black, and a few other built-in themes that you choose and design."
  Claude added Aurora, Dawn, Slate and Bloom.
- **Use the extra headroom:** better animations, higher-detail 3D models, nicer gradients and colors.
- **One profile, created during setup.** No profile picker.
- **Deep settings:** a settings page per game (which emulator it uses) and per platform.
- **An Android launcher:** it needs a place to launch apps from.
- **Mark as game:** a way to turn an Android app into a game entry with a box and full functionality, moving it to the Library.

### Answers to Claude's questions

- **Build route:** GitHub Actions, not Android Studio.
- **Consoles:** "original six, PS1, N64, DS, and PSP, a flat 10, plus some sort of handling for Android games."
  Android games are modeled "after Xbox/PS4 boxes," as generic keep cases.
- **Cardboard boxes:** for Game Boy, Game Boy Color, Game Boy Advance and N64, "a modernized box (like DS/3DS
  games) that has a clamshell opening, but with the dimensions of the original box." Then: "Yes, all of
  them should work like that," NES and SNES included.

### Claude's proposals, built into prototype v1 (the user hasn't reviewed it yet)

- Every case opens the same way: the cover swings open on the spine hinge, the manual is held by clips on
  the cover's inside, and the media sits in a molded tray in the base.
- Manual booklets take their box's shape (landscape for SNES and N64).
- Home: A plays or resumes immediately. Down reaches cards for Open the box, Achievements and Saves.
- **Library:** a flat camera, true relative box sizes, the spine visible at a slight angle, a 5-column framed
  grid, and the same row rules as the TV version.
- **Saves:** Shelf's save slots work only with RetroArch; standalone emulators keep their own saves, and the interface says so.
- **Android games:** generic keep case, an original game card, and a cover built from the app's icon when no art is found.
- **Setup:** six steps (see the spec).
- **Accent:** follows the console by default; accent text is blended for contrast on light themes.

### Feedback on prototype v1 (October 2026), in order

1. Make the on-screen keyboard QWERTY.
2. Add a back button to every step of first-run setup.
3. Bring back the console launch: the media goes into "a version of the console" with a cute sound, and the
   reverse when returning Home, "if possible" with Android's app switcher. Use the Pi version for inspiration.
4. **Models:** "too simple, I want them to have more texture and depth and detail. And they still don't look
   enough like the real cartridges." Not exact copies: "more of the general shape needs to be the same… the
   outline, the silhouette." Boxes too: "it's all just too minimalist. I'm not going for realism, I still want
   there to be some simplification and stylization." The same goes for the new console models.
5. For games whose saves can't be shown in Shelf, the saves area should open the right app.
6. Close the case when backing out of a game, and make every animation flow into the next state; the game panel
   shouldn't just pop up (slide in from the right, or something cleverer).
7. Library: the widest boxes (SNES, N64) sit too close to the frame's left edge.
8. Menu music: a few original tracks, selectable in Settings.
9. Support every emulator people might use for each console, standalone or RetroArch core, and make it easy to
   choose what each game launches with.

**Decisions:**
- **Models first, as their own update;** the rest of the list follows in a second update.
- **Silhouettes now follow the real objects** (outline, proportions, the features that make them recognizable).
  This relaxes the earlier "original shell designs" rule. Still no logos or console-maker branding, and details
  stay stylized. Accepted because Shelf is a free, personal project; revisit before any public release.
- **SNES uses the Japanese and European cartridge and console shape** (rounded), as described to the user.
- **The model viewer becomes a feature, the Museum:** "in the spirit of the tagline of the project… Eventually I
  want to make that a feature rather than just for debugging. Add in some info about the consoles and fun facts."
  In the prototype it's a fourth tab, with the consoles in release order.
- **Emulator support** will use ES-DE's published Android emulator list as the reference, plus an "Other app"
  option, per-console defaults, per-game overrides and "Ask every time".

### The layout (October 2026)

- The UI direction ideas (gallery, orbit, cover-driven color, print, physical UI): "cool in theory, but just a
  little too extra. I feel like I'd be losing utility."
- "You know how the PS4, PS5, Xbox, Switch, and Steam layouts are all distinctly different, but still feel like
  modern, easy to navigate game launchers? I want something like that, but different from all the others…
  the same foundations as the best of the best, but making it my own."
- The parts: a home for recently played games, a library, a place for Android apps, and the Museum. "Both the
  museum and the android app library don't have to be on the same 'level' as the Home and Library."
- Chosen: Claude's options 1 (row and dock, Switch-like) and 2 (game first, PS5-like) combined. "Pressing down opens
  the 'page' for that game, but… it's right there… the box flies in from the left and the cartridge slots in, while
  the rest of the cartridges in the row move 'up'." A "Switch-like row of circular, iconographic buttons at the top
  for Library, Apps, Museum, and Settings," the profile picture top left, status top right.
- Tried as a flat browser mockup first. Notes on it: the cartridge flying into the case overlapped the details
  (a timing issue: the details now wait for it); "I like the library sliding in from the bottom. When selecting a
  game from there, the library should slide back down, the box should get larger and open, and the details should
  appear on the right. Then, do that in reverse. The row of recently played games should never be visible, even in
  the background."
- Kept from before: A on Home plays or resumes straight away (down is for the page).
- Notes on the built layout: "press up on the dpad/analog stick to get out of the game page view. You press down to
  get into it." "I still want the shoulder buttons to… cycle through the different 'tabs'… with the default view
  being the 'first' tab." "A lot of empty space in the lower half of the main menu. I want that to show a
  customizable scraped piece of media (box art, or title screen, or screenshot, or cartridge art). This should be
  dimmed/darkened and fade as it goes up, with the edge of the fade lining up with the bottom of the row… You should
  also be able to turn this off." The planet moves to the top half: "I do like it so keep that around." 

### The Android app (October 2026)

- "I think this looks good enough for a full android version now! Do whatever you need to do, ping me when you need
  permission or something, and lmk when and how to download the APK."
- Claude's call: run the approved interface itself inside a small Java app (a WebView plus a native bridge) instead
  of rebuilding it in Kotlin, Compose and OpenGL ES, so the look and motion stay exactly as approved. Launch
  commands follow ES-DE's Android configuration. Builds come from GitHub Actions as a release with `Shelf.apk`.

### First notes from the Retroid (October 2026)

- "Let's keep the web version for now for testing and such." (Asked whether to rebuild natively; the user chose to
  keep the WebView app for now.)
- "Everything is too small on this screen… Redesign it around the actual size of my screen." Built: type and
  controls about 1.75 times larger (body text about 22 to 25 px on the 1334 × 750 screen, close to Android's 14sp at
  2× density), a 124 px top bar, four Library columns, five app columns, wider side lists, and setup lists in two
  columns when they're long.
- "I need the ability to change the folder that it's looking for games in… Let me choose the folder for each
  platform." Built: Settings › Platforms › a console › Games folder (Android's folder picker), and the same from setup.
  ROMs folders are still searched too.
- "The tall boxes need some breathing room at the top and bottom." Built: each Library row is as tall as its tallest
  box when raised, plus room for its title.
- "Launching android apps works great!" from the app drawer and as a marked game.
- Next round: GBA and Android games launch fine. "Use the shoulder buttons to cycle into settings after the museum."
  Library: the side list "hiccups with each click" past Game Boy; the title under a box "lags behind the box art";
  no "how long ago" text under the selected box. Museum: big consoles reach past their space; the info panel is too
  wide and overlaps the middle. All fixed.
- Then: "The most recently played game showing up first should be based on what was most recently launched, not what
  was most recently closed," and the return animation must always play. Built: Home reorders at launch; the running
  game is saved, so the return plays even if Android closed Shelf's page meanwhile.
- Art: "automatically scrape everything when the scrape button is pressed, but I also want the ability to scrape
  individual games from their settings pages… present me with multiple options… and I need to be able to edit the
  search term. It should search automatically based on the game/rom name." Built with libretro's free database and art
  (no account): Settings › Art and info › Find art for every game, and a game's settings › Art and details.
- "We're getting close to a full native rebuild & release!" Next round, the user tests launching on every console.
- NES opened RetroArch to a black screen (the default core, Mesen, wasn't downloaded). "Make it expect alternate
  RetroArch cores, like Nestopia for example, and make that adjustable." Built: Shelf asks which core you use the first
  time you play a console with a core it doesn't know you have, and Settings › Emulators › RetroArch cores lists them.
- Art from more services, with a picker per art type: proposed (ScreenScraper, TheGamesDB, RetroAchievements,
  SteamGridDB, IGDB; MobyGames skipped, its API is now paid). The user: "Yes, all except MobyGames." "I already have most
  of them." Built: a picker per art type in each game's settings, results from every connected service, and accounts
  entered with Android's own text box in Settings › Art and info.

### Premium cartridges (October 2026)

- A brief from the user, "Make the 3D cartridges look premium", with the rule "Use our own original cartridge
  outlines; don't copy any other app's assets or exact geometry." A three.js generator (outlines in a data file, ExtrudeGeometry with bevels, MeshPhysicalMaterial, a
  studio HDR through PMREM, ACES filmic, contact shadow) and a tuning page with .glb export. Built as
  `tools/cartridge-studio/`, in the brief's order of work.

### Prototype v2, part 2 (built)

- "Add in a button that skips the setup, just for debugging… not literally skip all the setup, but instead just
  choose dummy data to get through it quickly." Built as **Quick setup with test data** on the welcome step.
- Notes 1–9 from the v1 review, as proposed and agreed: QWERTY keyboard; Back in setup; the console launch and
  return animations; saves that open the emulator; the box closing on Back and the game panel sliding in from the
  right with its sections following one after another; more room around wide boxes in the Library; four menu
  music tracks; the full emulator list with per-console defaults, per-game choice, Ask every time and Other app.

- Opening a box from Home: "The box should come in from the LEFT, and exit from the left too… it should look like
  the cartridge/CD is being put in the case."
- Behind an open box: "It should always be in the back, just really dimmed dark," and fade back in as the box closes.
- Then: "I don't want the stuff in the background to just dim, I want… a dark color overlay too? So it gets more
  translucent and also the whole background darkens."
- Launches: handheld cartridges should "fully go into the console's model… look like the real thing." "The PSP's
  should open up the back and slot the UMD disk in like the real thing, and the DS should start closed, fit the
  cartridge fully into the slot, then open up and turn on."
- Theme picker: vertical selection as well as horizontal.
