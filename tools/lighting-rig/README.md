# Lighting rig

Shelf Native's 3D, rendered by the same Filament the app uses (1.75.1, its web build) with the app's camera, studio
light, key light and models, so lighting and materials can be tuned with real renders and measurements instead of
guesses. Used for 0.21's lighting pass.

## Setup (once)

```
cd tools/lighting-rig
sh setup.sh
```

Downloads Filament's web build and `cmgen`, makes the studio light from the app's HDR (`ibl/`), and links the app's
models. Needs Node with Playwright (the Claude sandbox has it).

## Use

```
node shot.mjs "rig.html?scene=carts" lin.png          # render (no tone curve, a quarter of the light)
node shot.mjs "rig.html?scene=none" bg.png            # the backdrop alone, once
python3 tonemap.py lin.png bg.png out.png neutral     # PBR Neutral, as the app; over the backdrop
python3 calmeasure.py out.png                         # with scene=calib: grey shells and labels in → out
```

Scenes: `carts` (every cartridge at Home's turn), `colors&sys=nes` (one cartridge in each of its colors), `page&sys=gba`
(one big cartridge at the page's turn; `ry`, `rx` turn it), `cases`, `consoles`, `calib` (flat greys, for measuring),
`library` (Library.kt's grid at its real sizes and spacing: `flat=gba,snes,ds,…`, `sel`), `caseback` (a box's back and front with an edge test pattern), `jewel` (PlayStation cases with the clear cover varied: `cl`), `glow` (the selection glow, Outline.kt: `what=box` for the closed box, `nobox` on the dark backdrop, `acc`/`eacc` the
glow's and line's colors, `r` its reach, `orbit` where the travelling light is, `moveTest` checks the silhouette clears
when the object moves).

Lighting parameters (the app's defaults when left out): `env` (studio), `key`, `exp` (Filament's direct exposure:
it brightens by 1/exp), `bright` (the brightness itself; overrides exp), `dir` (key light direction), `yaw`/`pitch`
(the rig's turn), `gold`/`silver` (metal colors), `ov` (material overrides as JSON, e.g. `{"all":{"roughness":0.5}}`,
`{"label":[rough,metal,coat,coatRough]}`).

Why the tone curve is applied afterwards: the web build's color grading doesn't offer PBR Neutral, so the rig renders
without a curve at a quarter of the light (nothing clips) and `tonemap.py` applies Khronos PBR Neutral (the same
formula as Filament's) on 4× the values.
