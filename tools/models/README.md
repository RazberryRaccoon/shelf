# Models built in Blender

Consoles and cartridges are being remade as proper 3D models in Blender, from reference photos. Each script
builds a model with real rounded edges and cut-outs, bakes its soft shadows (ambient occlusion) into a texture,
and writes it to `prototype-source/models/` in Shelf's own small format. `prototype-source/android/build.sh`
joins those files into the prototype, and the prototype uses a Blender model wherever one exists (the older
code-built models in `r4m.js` and `r4c.js` fill in the rest).

| Script | Models |
|---|---|
| `build_gba.py` | Game Boy Advance console and cartridge |

**Running a script** needs Blender's Python module (about 400 MB):

```
python3 -m venv blenv
blenv/bin/pip install bpy
blenv/bin/python tools/models/build_gba.py
sh prototype-source/android/build.sh
```

`shelf_blender.py` has the shared tools: rounded outlines, solids with rounded edges, cut-outs, the shadow
bake and the export. Coordinates are Shelf's (x right, y up, z toward you, 1 unit = 100 mm). Materials carry
Shelf's settings (shine, gloss, plastic texture) and flags: `label` marks where a game's label art goes,
`screen` and `led` light up when the console powers on.

Rules: real outlines and proportions, stylized details, no logos or printed text.
