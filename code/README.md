# Code from the earlier versions

| Folder | What it is |
|---|---|
| `shelf-retrogo/` | **Shelf for Odroid GO**, final. It replaces retro-go's launcher; `tools/install_into_retrogo.py` installs it and patches auto-save. `tools/` also has the art converter, the art and info fetcher, and the label cropper. Working on hardware. |
| `shelf-tv-pi/` | **Shelf for TV**, the first native slice for the Raspberry Pi 3B+: C with SDL2 and OpenGL ES 2, plus the session scripts and installer. Never tested on hardware. `make shelf-test` builds an offscreen test version. |
| `shelf-firmware-standalone.zip` | The abandoned first Odroid firmware, before the move to retro-go. Kept for reference. |

The Pi app's renderer (`shelf-tv-pi/src/gfx.c`, `models.c`) is a C port of the browser renderer, a useful starting point for the Android OpenGL ES view.
