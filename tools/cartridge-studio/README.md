# Cartridge Studio

A procedural cartridge and console generator (three.js) and a page for tuning it. Each cartridge style is a 2D outline
in [`cartridges.js`](cartridges.js), Shelf's own outlines at the real sizes in millimetres, extruded with rounded bevels
and finished in physically based plastic. The consoles in [`consoles.js`](consoles.js) are Shelf's own code-built
consoles (from `prototype-source/android/r4c.js`), rebuilt the same way: NES, Super Nintendo, Nintendo 64, Genesis,
Game Boy, Game Boy Color, Game Boy Advance, PlayStation, Nintendo DS, PSP and a generic Android handheld. The SNES
cartridge and console are the North American versions. Besides cartridges there's a PlayStation disc, a PSP UMD and an
Android game card, so every console Shelf supports has a model.

**Open it:** https://razberryraccoon.github.io/shelf/tools/cartridge-studio/ (GitHub Pages). It uses JavaScript
modules, which browsers won't load from a file on disk, so opening `index.html` directly doesn't work; on a computer, run
`python -m http.server` in the repository folder and open http://localhost:8000/tools/cartridge-studio/.

## How it's built

- **Geometry** (`cartridge.js`): each outline (corners with radii, plus arcs) becomes a `THREE.Shape`, extruded with
  `ExtrudeGeometry` and bevels on. The outline is pulled in by the bevel size first, so the finished cartridge keeps its
  true size. A front plate with the label hole sits on the body, its back edge buried inside it, so the recess gets a
  rounded lip and no seam shows. The label is its own mesh just above the floor of the recess. Grip ridges are small
  beveled extrusions.
- **Material:** `MeshPhysicalMaterial`, metalness 0, with roughness, clearcoat and clearcoat roughness per style. The
  label has its own matte material.
- **Color variants:** each cartridge lists the shell colors it really came in (with the games each was used for; see
  `docs/RESEARCH.md`), picked from a menu. Gold and silver get a metallic sheen; clear and frosted shells use
  transmission, and show a circuit board inside.
- **Lighting:** a Poly Haven studio HDR through `RGBELoader` and `PMREMGenerator` as `scene.environment`, loaded from
  Poly Haven when online; otherwise a bundled 512 px Poly Haven studio (`vendor/studio.exr`, CC0, from pmndrs/assets).
  You can also load your own `.hdr` or `.exr`. One soft key light, ACES filmic tone mapping, sRGB output, antialiasing,
  and a soft contact shadow under the cartridge.
- **Consoles** (`console.js`): the same beveled extrusions for shells, buttons and ports, plus turned shapes (domes,
  discs, rings) for round parts; screens are dark glass, LEDs glow, and the PlayStation lid, the DS's top half and the
  PSP's disc door are on hinges (the **Lid** slider). **Edge rounding** scales every bevel on the console at once.
  Other plastic parts share the shell's roughness and clearcoat; the color picker changes the main shell.
- **Tuning page:** style, bevel size and segments, plastic color, roughness, clearcoat, clearcoat roughness,
  environment intensity, exposure and key light, a turntable, your own label art, and **Export .glb** (GLTFExporter,
  millimetres, label included). **Copy settings** gives the tuned values in the form `cartridges.js` uses. Your tuning
  is remembered per style in that browser (only the values you change, so new defaults still arrive).

## Adding a style

Add an entry to `STYLES` in `cartridges.js`: an outline, depth, label recess, any ridges, and a starting finish. A
console is an entry in `CONSOLES` in `consoles.js`: a finish and a `build` function that places parts with the kit
described at the top of that file.
A console's main body is `k.body` (a handheld, front and back) or `k.bodyY` (lying flat, lower and upper): two halves
with a seam, as the real shells are molded. `seam` in its entry says where, so the export can split every part into the
`lower` and `upper` halves the app opens. Give each of its plastics a part (`M.plastic(color, 'dpad')`; the shell is `'shell'`, anything without one is
`'trim'`): the app's console colors recolor a part by its material name, `role:<part>`. Its real colors go in
`CONSOLE_COLORS`, as colors per part (parts left out keep the model's own).

A console's circuit board is an entry in `BOARDS` in `board.js`: where it sits in the console (in millimetres, flat or
standing in a handheld), its size, and its parts (chips with legs, capacitors, resistors, crystals, regulators,
connectors, shields), placed roughly as on the real board. Small surface parts fill the free space on their own; the
probe of the export checks that every board fits inside its shell.

## Exporting for Shelf Native

`node export-models.mjs` (needs Node and Playwright) builds every cartridge with the Studio's default settings and
writes `.glb` files into `android/native/src/main/assets/models/`: each cartridge in its standard plastic, a
see-through version of each (the app's Studio offers clear finishes for every cartridge), and `cartridges.json` with
sizes and color variants. `--consoles` adds the consoles, `--cases` the game cases ([`case.js`](case.js): the
prototype's clamshell cases at the real boxes' sizes, with a cover on a hinge, art on the front, back and spine, and a
tray the cartridge sits in), and `--no-carts` leaves the cartridges as they are. Run it again after changing the models
and commit the results. Each model is merged into one mesh per material as it's exported
(`mergeByMaterial`), so a cartridge costs the phone about 8 draw calls instead of up to 71; it looks the same.

Every cartridge is built in two halves like the real ones, each half's edge molded with a small 45° chamfer, so where
they meet a little V-shaped canyon runs around the sides; screws (`screws` in `cartridges.js`) hold the backs on. Cartridges with an edge connector (NES, SNES, N64, Genesis, Game Boy,
Game Boy Color, GBA) are hollow and open at the bottom, so the circuit board shows from below; the DS and Android cards
have their gold contacts on the back, set into a pocket between plastic ribs (`contacts` in `cartridges.js`). The
board's back carries a printed silkscreen (`boardPrint` in `cartridge.js`): RaccoonTek in Orbitron, an invented part
number, traces and contacts, seen through a clear shell's back.

`vendor/` holds three.js 0.160.0 (MIT, see `THREE-LICENSE`) and the files it needs, so the page works offline, and
`vendor/fonts/Orbitron.ttf` (SIL Open Font License, see `Orbitron-OFL.txt`) for the boards' print.
