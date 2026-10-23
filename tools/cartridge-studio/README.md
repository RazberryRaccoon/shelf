# Cartridge Studio

A procedural cartridge generator (three.js) and a page for tuning it. Each cartridge style is a 2D outline in
[`cartridges.js`](cartridges.js), Shelf's own outlines at the real sizes in millimetres, extruded with rounded bevels
and finished in physically based plastic.

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
- **Lighting:** a Poly Haven studio HDR through `RGBELoader` and `PMREMGenerator` as `scene.environment`, loaded from
  Poly Haven when online; otherwise a bundled 512 px Poly Haven studio (`vendor/studio.exr`, CC0, from pmndrs/assets).
  You can also load your own `.hdr` or `.exr`. One soft key light, ACES filmic tone mapping, sRGB output, antialiasing,
  and a soft contact shadow under the cartridge.
- **Tuning page:** style, bevel size and segments, plastic color, roughness, clearcoat, clearcoat roughness,
  environment intensity, exposure and key light, a turntable, your own label art, and **Export .glb** (GLTFExporter,
  millimetres, label included). **Copy settings** gives the tuned values in the form `cartridges.js` uses. Your tuning
  is remembered per style in that browser.

## Adding a style

Add an entry to `STYLES` in `cartridges.js`: an outline, depth, label recess, any ridges, and a starting finish.

`vendor/` holds three.js 0.160.0 (MIT, see `THREE-LICENSE`) and the files it needs, so the page works offline.
