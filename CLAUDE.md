# Shelf: context for Claude

You're picking up a long-running project from another Claude conversation. Read this file first, then
`docs/SPEC-shelf-android.md` and `docs/DECISIONS.md`. Everything else is reference.

## What Shelf is

Shelf is a game launcher with one idea at its heart: your games are physical objects. Cartridges, discs and
boxes are rendered as 3D models you browse, open and play. It has been built three times:

1. **Shelf for Odroid GO** (finished, working on hardware). A launcher for retro-go on a 320×240 ESP32 handheld.
2. **Shelf for TV** (prototype finished; a first native slice for Raspberry Pi 3B+ was written but never tested on hardware).
3. **Shelf for Android** (current focus). The best launcher we can make for the user's **Retroid Pocket 3+**.
   A browser prototype exists (`prototypes/shelf-android.html`) and the user had not yet given feedback on it
   when the conversation moved here. The next big step is the real Android app, built with GitHub Actions.

The user called the first two "me messing around." The Android version is the serious one: "take everything
that we've learned … and make an Android game launcher that's the best it can possibly be."

## Where things stand

- The user reviewed prototype v1 (nine notes, in `docs/DECISIONS.md`). Part 1 of the revision, detailed models
  with real silhouettes, eleven consoles and the new Museum tab, is built. Part 2 (the other notes) comes next;
  see `docs/ROADMAP.md`.
- After that: set up the Android project and GitHub Actions build → build it in vertical slices.
- The user asked to "talk it through" before building anything new. Propose, confirm, then build.

## The user

- New to GitHub. Explain any GitHub step (branches, pull requests, merging, releases) in plain terms.

- Uses a **Windows** PC. Owns an Odroid GO, a Raspberry Pi 3B+ and a Retroid Pocket 3+ (Android 11).
- Comfortable following step-by-step technical instructions (built ESP-IDF firmware, flashed devices), but
  isn't a professional developer. Give exact commands and explain what each step does.
- Reviews with numbered lists of notes. Notices details (alignment, motion, contrast) and says clearly what
  bothers them.
- Wants to be consulted on design before building, and wants research behind decisions (real measurements,
  how other frontends do it).

### Taste (consistent across every round)

- **Wants:** modern, minimalist, like a current handheld or console OS. Charm through motion and materials.
  Calm, smooth animation. Real proportions. Depth via 3D models, soft shadows and glows.
- **Doesn't want:** "nostalgiaslop," arcade styling, pixelated UI, literal game-room or shelf themes, loud
  sounds, white flashes, wobbling or swaying objects ("makes me feel like I'm on acid"), perspective that
  makes things drift out of alignment, button-prompt bars cluttering the corners, autoplaying video.
- Likes: the space theme, the cartridge-into-console idea, outline glows that hug the selected object,
  gentle floating with shadows, framed panels that keep things contained.

## Working agreements that went well

- **Research first:** real label and box measurements, how RetroArch and other frontends work. The research
  and its sources are in `docs/RESEARCH.md`.
- **Prototype in the browser first:** a single HTML file, WebGL 1 with our own small renderer (WebGL 1 is
  OpenGL ES 2.0, so shaders port directly). Publish it, get feedback, iterate.
- **Test before handing over:** previously done with headless Chrome plus software WebGL, a test hook
  `window.__step(seconds)` that advances animation deterministically, and contact sheets of screenshots.
  Every round found real bugs this way. Say plainly what was and wasn't tested.
- **Explain changes plainly,** in terms of what the user will see.

## Hard constraints

- **Silhouettes follow the real objects, details stay stylized** (the user's call, October 2026, relaxing the
  earlier "original shell designs" rule): real outlines, proportions and sizes, label windows at the real label
  sizes, but no logos or console-maker branding anywhere, and no exact replicas. Revisit before any public release.
- **Prototypes use invented games, studios and generated art.** Real box art, labels, manuals and media
  come from the user's own scraper accounts at runtime.

## Map of this package

- `README.md`: the GitHub front page, written for the user and their brother. Keep it current.
- `docs/SPEC-shelf-android.md`: the full spec for the current project.
- `docs/DECISIONS.md`: every decision and preference, in the user's words where possible.
- `docs/HISTORY.md`: what was built in each project, what broke, and what we learned.
- `docs/RESEARCH.md`: facts with sources (label and box sizes, RetroArch on Android, ScreenScraper, RetroAchievements).
- `docs/ROADMAP.md`: the plan for the real Android app, and open questions.
- `prototypes/`: the three working browser prototypes. Open in a desktop browser.
- `prototype-source/`: the prototypes split into the parts they were built from, with a build script.
- `code/`: the Odroid GO firmware integration and the Raspberry Pi C app.
- `downloads/`: the zips linked from the README. Rebuild them with `bash scripts/make-downloads.sh` whenever
  `code/` or `prototypes/` change, and commit the result.
- The raw transcript of the first conversation (the Odroid project) was left out of this public repo; its
  decisions are summarized in `docs/`.
