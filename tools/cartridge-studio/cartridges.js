// Cartridge styles for the cartridge studio: Shelf's own outlines, at the real sizes, in millimetres.
// Origin at the centre of the front face, x to the right, y up. Silhouettes follow the real objects; details stay
// stylized, with no logos (see CLAUDE.md, "Hard constraints").
//
// outline   corners as [x, y, radius]; a corner with radius 0 stays sharp.
//           ['arc', cx, cy, r, fromDegrees, toDegrees] adds a curved run (counter-clockwise), e.g. an arched top.
// depth     front to back.
// label     the recess the label sits in: centre x, y, width, height, corner radius, recess depth.
// ridges    raised grip details: { x, y, w, h, r, height, side: 'front' | 'back' }.
// finish    starting values for the tuning page.

const plastic = (color, roughness = .42, clearcoat = .45) => ({ color, roughness, clearcoat, clearcoatRoughness: .3 });
const repeat = (n, f) => Array.from({ length: n }, (_, i) => f(i));

// the arched top of the N64 cartridge: a circle through both top corners, 15 mm higher in the middle
const n64Arc = (() => { const w = 58, rise = 15, R = (w * w + rise * rise) / (2 * rise), cy = 40 - R, a = Math.atan2(25 - cy, w) * 180 / Math.PI; return ['arc', 0, cy, R, a, 180 - a]; })();

export const STYLES = {
  nes: {
    name: 'NES', size: [120, 133], depth: 17,
    outline: [[-60, -66.5, 2], [60, -66.5, 2], [60, 66.5, 2], [-60, 66.5, 2]],
    label: [0, 17.5, 55, 90, 1.5, 1],
    ridges: repeat(7, i => ({ x: 0, y: -40 - i * 3.6, w: 78, h: 1.4, r: .7, height: .7, side: 'front' })),
    finish: plastic('#8f8b86', .5, .3),
  },
  snes: {
    name: 'Super Nintendo', size: [122, 100], depth: 20,
    outline: [[-61, -50, 3], [61, -50, 3], [61, 50, 30], [-61, 50, 30]],
    label: [0, 22, 82.5, 37, 1.5, .9],
    ridges: [...repeat(5, i => ({ x: -50, y: -10 - i * 4, w: 10, h: 1.4, r: .7, height: .6, side: 'front' })),
             ...repeat(5, i => ({ x: 50, y: -10 - i * 4, w: 10, h: 1.4, r: .7, height: .6, side: 'front' }))],
    finish: plastic('#a8a6b4', .45, .35),
  },
  n64: {
    name: 'Nintendo 64', size: [116, 80], depth: 18,
    outline: [[-58, -40, 2.5], [58, -40, 2.5], [58, -30, 0], [54.5, -30, .6], [54.5, -22, .6], [58, -22, 0], [58, 25, 7],
      n64Arc, [-58, 25, 7], [-58, -22, 0], [-54.5, -22, .6], [-54.5, -30, .6], [-58, -30, 0]],
    label: [0, 2, 54.5, 62.5, 1.5, .9],
    ridges: [],   // a smooth front
    finish: plastic('#5a5d66', .4, .45),
  },
  genesis: {
    name: 'Sega Genesis', size: [100, 130], depth: 18,
    outline: [[-42, -65, 2], [42, -65, 2], [42, -40, 1.5], [50, -32, 1.5], [50, 65, 6], [-50, 65, 6], [-50, -32, 1.5], [-42, -40, 1.5]],
    label: [0, 25, 74, 60, 1.5, .9],
    ridges: repeat(4, i => ({ x: 0, y: -18 - i * 4, w: 60, h: 1.6, r: .8, height: .6, side: 'front' })),
    finish: plastic('#1d1e23', .38, .55),
  },
  gb: {
    name: 'Game Boy', size: [57, 65], depth: 8,
    outline: [[-28.5, -32.5, 1.2], [28.5, -32.5, 1.2], [28.5, 26.5, .6], [22.5, 32.5, .6], [-28.5, 32.5, 2.5]],
    label: [0, -3.5, 42, 37, 1, .6],
    ridges: repeat(4, i => ({ x: -2, y: 20 + i * 2.6, w: 34, h: 1, r: .5, height: .4, side: 'front' })),
    finish: plastic('#bdbcc4', .48, .3),
  },
  gbc: {
    name: 'Game Boy Color', size: [57, 65], depth: 8,
    outline: [[-28.5, -32.5, 1.2], [28.5, -32.5, 1.2], [28.5, 32.5, 3], [-28.5, 32.5, 3]],
    label: [0, -3.5, 42, 37, 1, .6],
    ridges: repeat(4, i => ({ x: 0, y: 20 + i * 2.6, w: 34, h: 1, r: .5, height: .4, side: 'front' })),
    finish: plastic('#2e3036', .4, .45),
  },
  gba: {
    name: 'Game Boy Advance', size: [57.5, 33.5], depth: 8,
    outline: [[-27.35, -16.75, 1.2], [-11, -16.75, .4], [-9, -17.95, .4], [9, -17.95, .4], [11, -16.75, .4], [27.35, -16.75, 1.2],
      [27.35, 8.95, .3], [28.75, 9.75, .3], [28.75, 15.15, .3], [27.15, 16.75, .3], [-27.15, 16.75, .3], [-28.75, 15.15, .3], [-28.75, 9.75, .3], [-27.35, 8.95, .3]],
    label: [0, -4.5, 44, 21.2, 1, .5],
    ridges: repeat(3, i => ({ x: 0, y: 10.4 + i * 1.8, w: 34, h: .8, r: .4, height: .35, side: 'front' })),
    finish: plastic('#303236', .4, .5),
  },
  ds: {
    name: 'Nintendo DS', size: [35, 33], depth: 3.8,
    outline: [[-17.5, -16.5, 1], [17.5, -16.5, 1], [17.5, 13.5, .4], [14.5, 16.5, .4], [-17.5, 16.5, 1.2]],
    label: [0, -3, 28, 20, .8, .35],
    ridges: repeat(3, i => ({ x: -1.5, y: 10.6 + i * 1.6, w: 18, h: .7, r: .35, height: .25, side: 'front' })),
    finish: plastic('#3b3d47', .42, .45),
  },
};
