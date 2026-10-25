// Cartridge styles for the cartridge studio: Shelf's own outlines, at the real sizes, in millimetres.
// Origin at the centre of the front face, x to the right, y up. Silhouettes follow the real objects; details stay
// stylized, with no logos (see CLAUDE.md, "Hard constraints").
//
// outline   corners as [x, y, radius]; a corner with radius 0 stays sharp.
//           ['arc', cx, cy, r, fromDegrees, toDegrees] adds a curved run (counter-clockwise), e.g. an arched top.
// depth     front to back.
// label     the recess the label sits in: centre x, y, width, height, corner radius, recess depth.
// recesses  more cut-outs in the front, at the label's depth: [x, y, width, height, corner radius].
// ridges    raised grip details: { x, y, w, h, r, height, side: 'front' | 'back' }.
// finish    starting values for the tuning page.
// variants  the shell colors this cartridge really came in, the first being the standard one: { name, color, games,
//           clear: 'transparent' | 'frosted' (see-through plastic, with the circuit board inside showing), sheen
//           (a metallic-looking plastic, for gold and silver) }. Sources are in docs/RESEARCH.md.

const plastic = (color, roughness = .42, clearcoat = .45) => ({ color, roughness, clearcoat, clearcoatRoughness: .75 });
const v = (name, color, games = '', more = {}) => ({ name, color, games, ...more });
const repeat = (n, f) => Array.from({ length: n }, (_, i) => f(i));

// the arched top of the N64 cartridge: a circle through both top corners, 15 mm higher in the middle
const n64Arc = (() => { const w = 58, rise = 15, R = (w * w + rise * rise) / (2 * rise), cy = 40 - R, a = Math.atan2(25 - cy, w) * 180 / Math.PI; return ['arc', 0, cy, R, a, 180 - a]; })();

export const STYLES = {
  nes: {
    name: 'NES', size: [120, 133], depth: 17,
    screws: { r: 2.3, at: [[-53, -50], [53, -50], [0, 55]] },
    outline: [[-60, -66.5, 2], [60, -66.5, 2], [60, 66.5, 2], [-60, 66.5, 2]],
    label: [0, 17.5, 55, 90, 1.5, 1],
    ridges: repeat(7, i => ({ x: 0, y: -40 - i * 3.6, w: 78, h: 1.4, r: .7, height: .7, side: 'front' })),
    finish: plastic('#8f8b86', .5, .3),
    variants: [v('Gray', '#8f8b86', 'Standard'), v('Gold', '#c9a24a', 'The Legend of Zelda, Zelda II', { sheen: true }),
      v('Black', '#222326', 'Unlicensed (Tengen)'), v('Baby blue', '#8db6d6', 'Unlicensed (Color Dreams, Wisdom Tree)'),
      v('Silver', '#b9bcc2', 'Unlicensed (Camerica, later releases)', { sheen: true })],
  },
  snes: {   // the North American cartridge: wider and squarer than the Japanese and European one, with inset grooves
    name: 'Super Nintendo', size: [136, 88], depth: 20,
    screws: { r: 2.3, at: [[-45, 33], [45, 33]] },
    outline: [[-68, -44, 2], [68, -44, 2], [68, 41, 1.5], [65, 44, 1.5], [-65, 44, 1.5], [-68, 41, 1.5]],
    label: [0, 21.5, 82.5, 37, 1.5, .9],
    recesses: [[-56.5, -4, 6, 62, 2.5], [56.5, -4, 6, 62, 2.5]],
    ridges: repeat(5, i => ({ x: 0, y: -10 - i * 4.4, w: 70, h: 1.5, r: .75, height: .6, side: 'front' })),
    finish: plastic('#9d9ba6', .45, .35),
    variants: [v('Gray', '#9d9ba6', 'Standard'), v('Black', '#1f2024', 'Killer Instinct')],
  },
  n64: {
    name: 'Nintendo 64', size: [116, 80], depth: 18,
    screws: { r: 2.2, at: [[-30, 30], [30, 30]] },
    outline: [[-58, -40, 2.5], [58, -40, 2.5], [58, -30, 0], [54.5, -30, .6], [54.5, -22, .6], [58, -22, 0], [58, 25, 7],
      n64Arc, [-58, 25, 7], [-58, -22, 0], [-54.5, -22, .6], [-54.5, -30, .6], [-58, -30, 0]],
    label: [0, 2, 54.5, 62.5, 1.5, .9],
    ridges: [],   // a smooth front
    finish: plastic('#5a5d66', .4, .45),
    variants: [v('Gray', '#5a5d66', 'Standard'), v('Black', '#1d1e22', 'Turok 2, Turok 3, Turok: Rage Wars'),
      v('Blue', '#2f5fb3', '007: The World Is Not Enough, Hydro Thunder'), v('Red', '#b3282d', 'Rocket: Robot on Wheels, Spider-Man'),
      v('Green', '#2f8a4a', 'A few third-party games'), v('Yellow', '#e0b51f', 'Donkey Kong 64'),
      v('Gold', '#c9a24a', 'Ocarina of Time, Majora\'s Mask, Pokémon Stadium 2', { sheen: true })],
  },
  genesis: {
    name: 'Sega Genesis', size: [100, 130], depth: 18,
    screws: { r: 2.2, at: [[0, 50]] },
    outline: [[-42, -65, 2], [42, -65, 2], [42, -40, 1.5], [50, -32, 1.5], [50, 65, 6], [-50, 65, 6], [-50, -32, 1.5], [-42, -40, 1.5]],
    label: [0, 25, 74, 60, 1.5, .9],
    ridges: repeat(4, i => ({ x: 0, y: -18 - i * 4, w: 60, h: 1.6, r: .8, height: .6, side: 'front' })),
    finish: plastic('#1d1e23', .38, .55),
    variants: [v('Black', '#1d1e23', 'Standard (the only color found)')],
  },
  gb: {
    name: 'Game Boy', size: [57, 65], depth: 8,
    screws: { r: 1.6, at: [[0, 24]] },
    outline: [[-28.5, -32.5, 1.2], [28.5, -32.5, 1.2], [28.5, 26.5, .6], [22.5, 32.5, .6], [-28.5, 32.5, 2.5]],
    label: [0, -3.5, 42, 37, 1, .6],
    ridges: repeat(4, i => ({ x: -2, y: 20 + i * 2.6, w: 34, h: 1, r: .5, height: .4, side: 'front' })),
    finish: plastic('#bdbcc4', .48, .3),
    variants: [v('Gray', '#bdbcc4', 'Standard'), v('Red', '#c0392b', 'Pokémon Red'), v('Blue', '#2d5fa8', 'Pokémon Blue'),
      v('Yellow', '#e8c228', 'Pokémon Yellow, Donkey Kong Land'), v('Black', '#2a2b30', 'Color games that also play on the original')],
  },
  gbc: {
    name: 'Game Boy Color', size: [57, 65], depth: 8,
    screws: { r: 1.6, at: [[0, 24]] },
    outline: [[-28.5, -32.5, 1.2], [28.5, -32.5, 1.2], [28.5, 32.5, 3], [-28.5, 32.5, 3]],
    label: [0, -3.5, 42, 37, 1, .6],
    ridges: repeat(4, i => ({ x: 0, y: 20 + i * 2.6, w: 34, h: 1, r: .5, height: .4, side: 'front' })),
    finish: plastic('#2e3036', .4, .45),
    variants: [v('Black', '#2e3036', 'Plays on both (dual-mode)'), v('Clear', '#e4ebf0', 'Game Boy Color only', { clear: 'transparent' }),
      v('Gold', '#c9a24a', 'Pokémon Gold', { sheen: true }), v('Silver', '#b9bcc2', 'Pokémon Silver', { sheen: true }),
      v('Clear blue', '#4f9fde', 'Pokémon Crystal', { clear: 'transparent' })],
  },
  gba: {
    name: 'Game Boy Advance', size: [57.5, 33.5], depth: 8,
    screws: { r: 1.5, at: [[0, 12.5]] },
    outline: [[-27.35, -16.75, 1.2], [27.35, -16.75, 1.2],
      [27.35, 8.95, .3], [28.75, 9.75, .3], [28.75, 15.15, .3], [27.15, 16.75, .3], [-27.15, 16.75, .3], [-28.75, 15.15, .3], [-28.75, 9.75, .3], [-27.35, 8.95, .3]],
    label: [0, -4.5, 44, 21.2, 1, .5],
    ridges: repeat(3, i => ({ x: 0, y: 10.4 + i * 1.8, w: 34, h: .8, r: .4, height: .35, side: 'front' })),
    finish: plastic('#303236', .4, .5),
    variants: [v('Dark gray', '#303236', 'Standard'), v('Clear red', '#d23a3a', 'Pokémon Ruby', { clear: 'transparent' }),
      v('Clear blue', '#2f6fd0', 'Pokémon Sapphire', { clear: 'transparent' }), v('Clear green', '#2fa860', 'Pokémon Emerald', { clear: 'transparent' }),
      v('Frosted red', '#e0453a', 'Pokémon FireRed', { clear: 'frosted' }), v('Frosted green', '#5cc06a', 'Pokémon LeafGreen', { clear: 'frosted' })],
  },
  ds: {
    name: 'Nintendo DS', size: [35, 33], depth: 3.8,
    outline: [[-17.5, -16.5, 1], [17.5, -16.5, 1], [17.5, 13.5, .4], [14.5, 16.5, .4], [-17.5, 16.5, 1.2]],
    label: [0, -3, 28, 20, .8, .35],
    contacts: { n: 17, pitch: 1.45, w: .85, h: 4.2, bottom: .7 },   // on the back, along the bottom edge, set in between ribs
    ridges: repeat(3, i => ({ x: -1.5, y: 10.6 + i * 1.6, w: 18, h: .7, r: .35, height: .25, side: 'front' })),
    finish: plastic('#3b3d47', .42, .45),
    variants: [v('Gray', '#3b3d47', 'Standard'), v('White', '#e6e6ea', 'DSi-only games'),
      v('Smoked black', '#24252b', 'Infrared games: HeartGold, SoulSilver, Pokémon Black and White', { clear: 'frosted' })],
  },
  psp: {   // a UMD: the disc's shell, rounded across the top, with the label over the disc
    name: 'PSP (UMD)', size: [65, 64.5], depth: 4.2,
    outline: [[-32.5, -32, 3], [32.5, -32, 3], [32.5, 0, 0], ['arc', 0, 0, 32.5, 0, 180], [-32.5, 0, 0]],
    label: [0, 2, 44, 44, 22, .4],
    ridges: [],
    finish: plastic('#2a2b31', .35, .55),
    variants: [v('Dark gray', '#2a2b31', 'Standard')],
  },
  android: {   // Android games: a small game card, the size of a memory card's case
    name: 'Android game card', size: [42, 30], depth: 3.5,
    outline: [[-21, -15, 2.2], [21, -15, 2.2], [21, 15, 2.2], [-21, 15, 2.2]],
    label: [0, -2, 34, 20, 1.2, .35],
    contacts: { n: 10, pitch: 2.3, w: 1.5, h: 4, bottom: .7 },
    ridges: repeat(3, i => ({ x: 0, y: 10.6 + i * 1.5, w: 20, h: .6, r: .3, height: .25, side: 'front' })),
    finish: plastic('#262a3a', .4, .5),
    variants: [v('Midnight', '#262a3a', 'Standard'), v('White', '#e6e6ea', ''), v('Red', '#c0392b', ''), v('Blue', '#2d5fa8', '')],
  },
  ps1: {   // a CD: printed on top, the black data side underneath
    name: 'PlayStation disc', kind: 'disc', size: [120, 120], depth: 1.2, radius: 60, hole: 7.5, hub: 23,
    outline: [], label: [0, 0, 120, 120, 60, 0], ridges: [],
    finish: plastic('#121216', .12, .9),
    variants: [v('Black', '#121216', 'Standard: every PlayStation disc has a black data side')],
  },
};
