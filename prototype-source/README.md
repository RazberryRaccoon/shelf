# Prototype sources

The prototypes were written in parts and joined into one HTML file. Run `build.sh` in a folder (Git Bash or
WSL on Windows) to rebuild it.

**Android parts:**

| File | Contents |
|---|---|
| `r1.html` | Page structure and styles, including the seven themes. |
| `r2.js` | Platforms, emulators, games, apps, the profile, sounds and achievements. |
| `r2m.js` | Menu music: four tracks played live with Web Audio. |
| `r3.js` | Generated art: labels, covers, discs, icons and manuals. |
| `r4.js` | The WebGL renderer: shaders, cameras, textures and backdrops. |
| `r4g.js` | Loads the models built in Blender (`../models/`, from `tools/models/`). |
| `r4m.js` | Geometry tools (rounded outlines, bevelled solids, lathe), cartridges, discs, cards and cases. |
| `r4c.js` | The eleven consoles. |
| `r5a.js` | Setup, Home, Library and the game page. |
| `r5b.js` | Apps, settings, the emulator picker, and the launch and return animations. |
| `r5_reused.js` | Achievements, dialogs, keyboard, media viewer and manual reader. |
| `r5m.js` | The Museum tab: console facts, the exhibit viewer and its controls. |
| `r6.js` | Input, drawing and the main loop. |

The TV parts follow the same pattern (`head.html`, `p1b.html`, `p2.js` … `p6.js`).

The renderer's shaders are GLSL ES 1.00, so they port directly to OpenGL ES on Android. The math is in `r4.js`.
