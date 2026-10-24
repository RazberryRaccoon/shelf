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

## Exporting for Shelf Native

`node export-models.mjs` (needs Node and Playwright) builds every cartridge with the Studio's default settings and
writes `.glb` files into `android/native/src/main/assets/models/`: each cartridge in its standard plastic, a
see-through version for those with clear or frosted colors, and `cartridges.json` with sizes and color variants.
`--consoles` adds the consoles. Run it again after changing `cartridges.js` and commit the results.

`vendor/` holds three.js 0.160.0 (MIT, see `THREE-LICENSE`) and the files it needs, so the page works offline.
