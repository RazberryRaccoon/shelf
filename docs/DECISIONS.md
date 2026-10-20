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
