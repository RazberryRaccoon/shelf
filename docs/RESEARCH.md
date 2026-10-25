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
- **DS case:** 135 mm wide × 122 mm tall × 15 mm (black North American and Japanese cases; clear European ones are
  20 mm deep): a little *wider* than tall. Corrected in 0.16: the user measured a real case ("it is more wide
  horizontally"), and libretro's DS box art is wider than tall too (the old shape cut off the art's left and right
  edges, including the DS strip on the left). The listings below give the two sizes without saying which is which;
  the first reading had them the wrong way round. Retail replacement cases agree within a few millimetres.
  Sources: https://en.wikipedia.org/wiki/Keep_case, https://www.walvisproducts.eu/a-56765154/blue-ray-boxes-game-cases/nintendo-ds-game-case-colour-transparent/
- **PSP (UMD) case:** 104 × 177 × 14 mm. Sources: https://en.wikipedia.org/wiki/Universal_Media_Disc,
  https://www.walvisproducts.eu/a-97378107/blue-ray-boxes-game-cases/standard-umd-psp-case-colour-transparent/
- **Box art on the boxes:** front scans (libretro's Named_Boxarts are at most 512 px wide, any height) are fitted to
  the box's real front by cropping to fill, never stretched, so a scan of any shape sits right on the model.
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

## Game manuals (no account)
- **retrokit's manual library** (github.com/obrie/retrokit): about 20,000 manuals "catalogued by hand" over 40+
  systems. Each system's data file (`data/<system>.json`, e.g. `nes`, `snes`, `megadrive`, `psx`, `nds`) lists games by
  their *parent* No-Intro / Redump title (no region tags), each with its manuals' languages and original source URL.
  Counts in October 2026: NES 890, SNES 959, N64 306, Genesis 787, Game Boy 417, GBC 170, GBA 463, PlayStation 1,109,
  DS 339, PSP 432 games with manuals. Matching our libretro names with their tags stripped found every test game
  exactly (Super Mario World, Zelda, Final Fantasy VII with "(Disc 1)", Metroid Fusion, Sonic, New Super Mario Bros.).
- **Its archive.org copy:** every manual, already turned into a PDF, in one zip per system; archive.org serves single
  files out of a zip, so one manual downloads on its own:
  `https://archive.org/download/retrokit-manuals/{system}/{system}-compressed.zip/{name} ({languages}).pdf`
  (from retrokit's `config/settings.json`). The original sites are the fallback (some are .cbr or web pages, which
  Shelf skips). Not reachable from Claude's sandbox, so first tried on the Retroid.
  Sources: https://archive.org/details/retrokit-manuals, https://retrokit.dev/manuals, retrokit's manual/manualkit.md.
- **ScreenScraper** has manuals too (`manuel`), with an account (see above).
- **Not free:** MobyGames' API is paid; LaunchBox's manuals come through EmuMovies (an account).

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

## Cartridge shell colors (for the Cartridge Studio's variants)
Collector sources, which don't always agree; colors in the studio are approximations.
- **NES:** gray standard; gold for Nintendo's two Zelda games. Unlicensed: black (Tengen, which also used its own
  rounder shell), baby blue (Color Dreams and Wisdom Tree; sources disagree on which), gold then silver (Camerica).
  Sources: https://en.wikipedia.org/wiki/Tengen_(company), https://en.wikipedia.org/wiki/Color_Dreams,
  https://famiwiki.net/wiki/Nintendo_Entertainment_System_cartridge_variations
- **SNES (North America):** gray; black for Killer Instinct. Source: https://strategywiki.org/wiki/Killer_Instinct/Versions
- **N64:** gray standard; black, blue, red, green, yellow and gold, about 42 colored carts in North America (Turok 2 and 3
  and Rage Wars black; 007: The World Is Not Enough and Hydro Thunder blue; Rocket and Spider-Man red; Donkey Kong 64
  yellow; Ocarina of Time, Majora's Mask and Pokémon Stadium 2 gold). Sources:
  https://retrorecall.net/guides/n64-colored-cartridges/, https://www.svg.com/253703/what-you-didnt-know-about-n64s-colored-cartridges/,
  https://www.oldschoolgamermagazine.com/an-overview-of-special-colored-game-cartridges/
- **Genesis:** black; no other colors found.
- **Game Boy:** gray; red and blue for Pokémon Red and Blue, yellow for Pokémon Yellow and Donkey Kong Land; black for
  color games that also play on the original. **Game Boy Color only:** clear; gold, silver and clear blue for Pokémon
  Gold, Silver and Crystal. Source: https://en.wikipedia.org/wiki/Game_Pak
- **Game Boy Advance:** dark gray; transparent red, blue and green for Pokémon Ruby, Sapphire and Emerald; frosted red and
  green for FireRed and LeafGreen (a fan-compiled list). Source: https://www.reddit.com/r/Gameboy/comments/6i19k4/gba_game_cartridge_colors
- **DS:** gray; white for DSi-only games; darker, glossy, see-through cards for infrared games (HeartGold, SoulSilver,
  Pokémon Black and White, the last unverified). Source: https://en.wikipedia.org/wiki/Nintendo_Game_Card

## Console colors (for the console Studio's defaults)
North American editions unless noted; colors are approximations from photos. Series names are the makers' own; the
app's names describe the color. Sources: the consoles' Wikipedia articles (https://en.wikipedia.org/wiki/Nintendo_64,
https://en.wikipedia.org/wiki/Game_Boy, https://en.wikipedia.org/wiki/Game_Boy_Color,
https://en.wikipedia.org/wiki/Game_Boy_Advance, https://en.wikipedia.org/wiki/Nintendo_DS,
https://en.wikipedia.org/wiki/PlayStation_Portable, https://en.wikipedia.org/wiki/PlayStation_(console)) and collector
listings of the limited editions.
- **NES:** gray. The Famicom's red and white (Japan, 1983).
- **SNES:** North America two grays with purple switches; the Super Famicom and Europe a paler gray with gray switches.
- **N64:** charcoal (1996); the Funtastic translucents (1999): jungle green, ice blue, grape purple, fire orange,
  watermelon red, smoke black; a limited gold edition.
- **Genesis:** black, every model.
- **Game Boy:** gray (1989); the Play It Loud series (1995): red, yellow, green, blue, black, white and clear.
- **Game Boy Color:** launch colors (1998) teal, berry, grape, kiwi, dandelion, and translucent atomic purple.
- **Game Boy Advance:** indigo, arctic, fuchsia and translucent glacier at launch (2001); black and platinum (2002);
  orange in Japan.
- **PlayStation:** gray (1995); white for the later slim model (2000).
- **DS:** platinum at launch (2004); graphite black, electric blue, titanium, candy pink and red (2005).
- **PSP:** piano black (2005); ceramic white, ice silver, rose pink, Felicia blue and radiant red in the years after.
- **Android handhelds:** no single maker; four of today's common colors (midnight, snow, indigo, and gray with purple
  buttons).

## Console boards (for the circuit boards inside the consoles)
Layouts are approximate: the reference sites with labelled board photos couldn't be opened from the sandbox, only
searched. What the boards follow:
- **NES (NES-CPU):** the 72-pin cartridge connector mid-board; the CPU (RP2A03) and picture chip (RP2C02), both 40-pin
  DIPs; two 2 KB RAMs; the lockout chip; the RF modulator box; a regulator on a heatsink.
  Sources: https://nesdev.org/wiki/Cartridge_connector, https://boardmaps.silvestron.com/nintendo-entertainment-system/NES-CPU-07
- **SNES (SNS-CPU):** "the cartridge slot divides the system in two": the sound processors (S-SMP, S-DSP) and their RAM
  on one side, the CPU, the two picture chips and their RAM on the other. Sources: https://fabiensanglard.net/snes_mobos/,
  https://snes.nesdev.org/wiki/Version_differences
- **N64 (NUS-CPU):** the CPU (VR4300), the RCP and the RDRAM under "three very pronounced steel blocks" (heat spreaders);
  four controller ports along one side. Sources: https://n64brew.dev/wiki/Motherboard, https://www.ifixit.com/Teardown/Nintendo+64+Teardown/74923
- **Genesis (VA boards):** the 68000 (a long 64-pin DIP), the Z80, the video chip (315-5313), the YM2612 FM chip.
  Sources: https://segaretro.org/Mega_Drive_PCB_revisions, https://consolemods.org/wiki/Genesis:Motherboard_Differences
- **Game Boy (DMG-CPU):** the cartridge connector at the top, the one-package CPU with its RAM and crystal, the screen's
  ribbon, the link port, the volume wheel. Sources: https://gbdev.gg8.se/wiki/articles/DMG_Schematics, https://github.com/Gekkio/gb-schematics
- **Game Boy Color, Game Boy Advance:** the slot along the top; the CPU (CPU CGB / CPU AGB) in the middle with one RAM;
  the GBA's sound amplifier on the board's back. Sources: https://boardmaps.silvestron.com/nintendo-game-boy-color/CGB-CPU-02,
  https://copetti.org/writings/consoles/game-boy-advance/
- **PlayStation (PU boards):** CPU, GPU and SPU as large flat chips with their RAM, the disc drive's controller chips.
  Sources: https://consolemods.org/wiki/PS1:PS1_Model_Differences, https://playstationdev.wiki/ps1devwiki/index.php/Motherboards
- **DS (NTR-CPU):** the "CPU NTR" chip in the middle, the card slot at the back and the Game Boy Advance slot at the front.
  Source: https://copetti.org/writings/consoles/nintendo-ds/
- **PSP (TA-079):** the processor under a shield, memory, the radio on its own. Sources: https://www.ifixit.com/Teardown/PSP+1000+Teardown/1234,
  https://consolemods.org/wiki/PSP:PSP_Model_Differences
- **Android handhelds:** a phone-style board (a processor and memory under a metal can, a dark solder mask).

## North American SNES
- **Cartridge:** 136 × 88 × 20 mm (the Japanese and European one is about 130 × 86 × 20), more angular, with inset
  grooves. Source: https://en.wikipedia.org/wiki/Super_Nintendo_Entertainment_System_Game_Pak,
  https://skdesu.com/en/super-famicom-vs-snes-differences/
- **Console:** about 203 × 254 × 68 mm (the Computer History Museum lists 7.75 × 9.375 × 3 in): a boxier shell than the
  Super Famicom's, with larger purple power and reset switches. Sources: https://omeka.ischool.utoronto.ca/items/show/302,
  https://www.computerhistory.org/collections/catalog/102618771,
  https://computinghistory.org.uk/det/39976/Super-Nintendo-Entertainment-System-(SNES)-(US)
