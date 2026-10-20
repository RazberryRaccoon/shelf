# Research and sources

Facts gathered during the project, with where they came from. Items marked *(to verify)* weren't confirmed.

## Retroid Pocket 3+
- Unisoc T618 with a Mali-G52 MC2 GPU, 4 GB RAM, a 4.7-inch 1334×750 touchscreen, Android 11.
  Source: Retroid's product page, https://www.goretroid.com/en-ca/products/retroid-pocket-3-handheld-retro-gaming-system-1

## Cartridge labels (ConsoleMods "Cart Labels" table)
Source: https://consolemods.org/wiki/Cart_Labels

| Console | Label (mm) | Top fold | Notes |
|---|---|---|---|
| Game Boy | 42 × 37 | none | |
| Game Boy Advance | 43 × 22 | none | confirmed by test print |
| N64 | 54.5 × 62.5 | none | 4 mm top and 3 mm bottom corner radius, curved top (325 mm diameter) |
| NES | 55 × 97 | 7 mm | front face about 55 × 90 |
| SNES | 82.5 × 44 | 7 mm | front face about 82.5 × 37, confirmed |
| Genesis | 74 × 67 | 7 mm | front face about 74 × 60, confirmed |

- **NES orientation** is confirmed by an EverDrive label listing (https://stoneagegamer.com/everdrive-n8-label.html):
  2.18 inches wide by 3.83 inches tall, with the fold 7.2 mm from the top. Also see https://www.nesdev.org/wiki/NES_cartridge_dimensions.
- **DS card label:** not found. Estimated at about 28 × 20 mm *(to verify)*.

## Box and case sizes
- **NES box:** about 127 × 182 × 25 mm. NES box protectors measure 5.12 × 1.02 × 7.17 inches inside.
- **SNES and N64 boxes:** about 174 × 121 × 28 mm. An acrylic protector for both is 18.2 × 3.6 × 12.9 cm on
  the outside, with 4 mm walls. Source: https://topshelfco.ca/products/evoretro-acrylic-protector-nintendo-64-game-box-n64
- **Game Boy and GBA boxes:** about 123 × 123 × 20 mm. A protector for both is 12.5 × 12.5 × 2.3 cm.
  Source: Hand Held Legend's "Game Box Protector" listing.
- **Genesis case:** 128 mm wide and 197 mm tall, including the tab, per a YouTube measurement. Depth is about 25 mm *(to verify)*.
- **CD jewel case** (PlayStation): 142 × 125 × 9.9 mm. Source: https://en.wikipedia.org/wiki/Keep_case
- **DS case:** about 125 × 137 × 14 mm *(to verify)*.
- **PSP case:** about 104 × 175 × 14 mm *(to verify)*.
- **Blu-ray keep case** (Android games): about 135 × 171 × 12 mm *(to verify)*.

## RetroArch on Android
- **Launching a game:** frontends use an explicit intent. The Pegasus frontend's documentation
  (https://pegasus-frontend.org/docs/user-guide/platform-android-example) gives this example:
  `am start -n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture -e ROM {file.path} -e LIBRETRO /data/data/com.retroarch.aarch64/cores/<core>_libretro_android.so -e CONFIGFILE <path>/retroarch.cfg -e QUITFOCUS`
  `QUITFOCUS` returns focus to the frontend when the game is quit.
- **Loading a save state on launch:** the `-e/--entryslot` option loads specially named entry states and
  turns auto-save off. A cleaner method, used by RetroPie users, is to turn on auto-load and copy the chosen
  state to `<game>.state.auto` before launching. Source: https://forums.libretro.com/t/load-save-states-from-command-line-or-playlist-try-out-now/35965
- **Network commands** (UDP port 55355 when `network_cmd_enable` is on) include `QUIT`, `SAVE_STATE`,
  `PAUSE_TOGGLE` and `SCREENSHOT`. Shelf's TV and Pi versions rely on `QUIT` plus auto-save.

## ScreenScraper
- **Two sets of credentials:** apps need a developer ID and password, requested from ScreenScraper on their
  forum, and users sign in with their own free account, which raises their daily limit. Lookups can use the
  ROM's checksums plus the system, file name and size.
  Source: https://www.screenscraper.fr/webapi2.php and RomM's implementation.
- **Media types** (from ES-DE's scraper code): `box-2D` (front), `box-2D-back`, `box-3D`, `ss` (screenshot),
  `sstitle` (title screen), `wheel` (logo), `video`, `video-normalized` (smaller files), and `manuel` (manual PDF).
  Every item you download counts toward the daily quota.

## RetroAchievements
- **The web API** `API_GetGameInfoAndUserProgress`
  (https://api-docs.retroachievements.org/v1/get-game-info-and-user-progress.html) returns, per achievement:
  title, description, points, how many players unlocked it (normally and in hardcore), its type (progression,
  win condition, missable), the badge, and when the user earned it, in each mode. It also returns game details
  and images (box art, title screen, in-game). It needs the user's web API key.

## Free art sources
- **RetroArch's thumbnail server**, `https://thumbnails.libretro.com/<System>/Named_Boxarts/<name>.png`, with
  `Named_Snaps` and `Named_Titles` alongside. Names follow No-Intro, with `&*/:`<>?\|` replaced by `_`.
- **The libretro database** (`metadat/<field>/<System>.dat`) gives release year, developer, publisher and genre, matched by CRC.
- **Wikipedia's REST API** for summaries. Check that the page's description mentions a video game.

## Raspberry Pi (TV version)
- Debian ships RetroArch plus Nestopia, Snes9x and Genesis Plus GX (non-free), and Gambatte and mGBA, for arm64.
  Source: https://packages.debian.org/bookworm/libretro-gambatte and its neighbors.
- RetroPie's setup script reportedly works on Bookworm and Trixie Lite, 64-bit, but compiles from source,
  which takes a long time. Source: https://github.com/RetroPie/RetroPie-Setup/issues/4013
- ES-DE treats the Pi 4 as its minimum.

## Modern console interfaces (what Shelf borrowed)
- **PlayStation 5:** a hub for the selected game, with activities and media below it, and quick access to friends and power options.
- **Xbox:** a quick-access row on Home, recently played games, and pinned games and groups.
- **Switch:** a user page at the top left, plus Album, Controllers, System Settings and Sleep.
