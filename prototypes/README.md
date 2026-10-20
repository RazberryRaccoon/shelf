# The prototypes

Each one is a single, self-contained HTML file. Open it in Chrome or Edge on a computer. The fonts load from
Google Fonts when online; the prototypes still work offline with fallback fonts.

| File | Screen | Status |
|---|---|---|
| `shelf-android.html` | 1334×750 (Retroid Pocket 3+) | Current. Prototype v1, not yet reviewed by the user. |
| `shelf-tv.html` | 1920×1080 | Final Shelf for TV prototype, after 7 rounds of feedback. |
| `shelf-odroid-launcher.html` | 320×240 (Odroid GO) | The original design reference for the Odroid launcher. |

**Keys** (Android and TV): arrows move, Enter is A, Esc is B, F is Y, Q and E are the shoulder buttons, H is
Home. A connected controller works through the browser's Gamepad API.

**Android prototype:** setup runs first. To skip it from the browser console, run `P.name = 'Alex'; finishSetup()`.

**Testing hook** (Android and TV): `window.__step(seconds)` advances the animation by a fixed amount without
waiting. It's useful for headless screenshot tests, where software rendering is slow.

All games, studios and art in the prototypes are invented or generated.
