// Each console's circuit board, in 3D: the board itself, its chips with their legs, capacitors, resistors, crystals,
// regulators, connectors and shields, laid out roughly as on the real console's main board (see docs/RESEARCH.md,
// "Console boards"). Everything is in millimetres, in the console's own space (console.js), so a board exported on
// its own sits exactly inside its console. The board's underside is printed by the app (RaccoonTek and a part number,
// like the cartridges' boards): its material is named "board-print"; the top's solder mask is "board-top".
//
// A board is built in its own frame: x across, z toward the console's front (or, standing in a handheld, down), and
// its parts rise along +y. `stand` turns it upright in a handheld, its parts facing the screen and its print the back.
import * as THREE from 'three';

const mat = (name, o) => Object.assign(new THREE.MeshPhysicalMaterial(o), { name });
function materials(mask, print, traces) {
  return {
    top: mat('board-top', { color: '#ffffff', map: traces || null, roughness: .55, clearcoat: .15, clearcoatRoughness: .45 }),   // its traces are drawn by the app, in the mask's color
    print: mat('board-print', { color: '#ffffff', map: print || null, roughness: .6, clearcoat: .15, clearcoatRoughness: .45 }),
    edge: mat('board-edge', { color: '#cdbb8a', roughness: .8 }),
    chip: mat('chip', { color: '#18181b', roughness: .62 }),
    lid: mat('chip-lid', { color: '#c9ccd2', metalness: 1, roughness: .32 }),
    lead: mat('lead', { color: '#d9dbe0', metalness: 1, roughness: .35 }),
    gold: mat('gold', { color: '#e0b04c', metalness: 1, roughness: .3 }),
    shield: mat('shield', { color: '#b8bcc4', metalness: 1, roughness: .38 }),
    sink: mat('heatsink', { color: '#c4c8ce', metalness: 1, roughness: .5 }),
    ecap: mat('ecap', { color: '#1e2027', roughness: .3, clearcoat: 1, clearcoatRoughness: .2 }),
    ecap2: mat('ecap-blue', { color: '#2e4f9e', roughness: .3, clearcoat: 1, clearcoatRoughness: .2 }),
    smd: mat('smd', { color: '#c9b58c', roughness: .6 }),
    smd2: mat('smd-dark', { color: '#232327', roughness: .5 }),
    res: mat('resistor', { color: '#d8c49a', roughness: .55, clearcoat: .4 }),
    band: mat('resistor-band', { color: '#7b3a22', roughness: .6 }),
    conn: mat('connector', { color: '#1c1c20', roughness: .55 }),
    white: mat('connector-white', { color: '#e8e4d8', roughness: .6 }),
    flex: mat('flex', { color: '#d4862c', roughness: .45, clearcoat: .6, clearcoatRoughness: .3 }),
    dark: mat('board-dark', { color: '#0d0d10', roughness: .8 }),
  };
}

// one triangle list split into groups by facing: up (0), down (1), sides (2)
function byFacing(g, flip) {
  g = g.index ? g.toNonIndexed() : g; g.computeVertexNormals();
  const pos = g.getAttribute('position'), groups = [[], [], []];
  for (let t = 0; t < pos.count; t += 3) {
    const a = new THREE.Vector3().fromBufferAttribute(pos, t), b = new THREE.Vector3().fromBufferAttribute(pos, t + 1), c = new THREE.Vector3().fromBufferAttribute(pos, t + 2);
    const n = b.sub(a).cross(c.sub(a)).normalize();
    groups[n.y > .9 ? 0 : n.y < -.9 ? 1 : 2].push(t);
  }
  const out = new THREE.BufferGeometry(), order = groups.flat(), P = [], U = [];
  const box = new THREE.Box3().setFromBufferAttribute(pos), w = box.max.x - box.min.x, d = box.max.z - box.min.z;
  for (const t of order) for (let k = 0; k < 3; k++) {
    const x = pos.getX(t + k), y = pos.getY(t + k), z = pos.getZ(t + k); P.push(x, y, z);
    // the board's own picture coordinates, filling each face. The underside's are mirrored so its print reads the right
    // way round where you see it: across, from behind in a handheld; front to back, from below with the front nearest
    const down = groups[1].includes(t), U0 = (x - box.min.x) / w, V0 = 1 - (z - box.min.z) / d;
    U.push(down && flip ? 1 - U0 : U0, down && !flip ? 1 - V0 : V0);
  }
  out.setAttribute('position', new THREE.Float32BufferAttribute(P, 3)); out.setAttribute('uv', new THREE.Float32BufferAttribute(U, 2)); out.computeVertexNormals();
  let s = 0; groups.forEach((l, i) => { out.addGroup(s * 3, l.length * 3, i); s += l.length; });
  return out;
}

// corners: one radius, or four: back left, back right, front right, front left (standing: top left, top right, bottom right, bottom left)
const rrectShape = (w, d, r) => {
  const s = new THREE.Shape(), x = w / 2, y = d / 2, [a, b, c, e] = [].concat(r, r, r, r).slice(0, 4).map(k => Math.min(k, x - .01, y - .01));
  s.moveTo(-x + a, -y); s.lineTo(x - b, -y); s.quadraticCurveTo(x, -y, x, -y + b); s.lineTo(x, y - c); s.quadraticCurveTo(x, y, x - c, y);
  s.lineTo(-x + e, y); s.quadraticCurveTo(-x, y, -x, y - e); s.lineTo(-x, -y + a); s.quadraticCurveTo(-x, -y, -x + a, -y); return s;
};

export const MASK = '#14532f';   // the usual green solder mask
export function buildBoard(spec, printTexture, traceTexture) {
  const M = materials(spec.mask || MASK, printTexture, traceTexture), root = new THREE.Group(); root.name = 'board';
  const T = spec.thick ?? 1.6;
  // ---------- the board ----------
  {
    const g = new THREE.ExtrudeGeometry(rrectShape(spec.size[0], spec.size[1], spec.corner ?? 3), { depth: T, bevelEnabled: false, curveSegments: 6 });
    g.rotateX(Math.PI / 2);   // the outline's (x, y) lies in (x, z), the thickness goes down from y = 0
    const m = new THREE.Mesh(byFacing(g, spec.stand), [M.top, M.print, M.edge]); root.add(m);
  }
  // ---------- parts ----------
  const add = (geo, m, x, y, z, ry = 0) => { const o = new THREE.Mesh(geo, m); o.position.set(x, y, z); o.rotation.y = ry; root.add(o); return o; };
  const box = (w, h, d) => new THREE.BoxGeometry(w, h, d);
  const cyl = (r, h, seg = 20) => new THREE.CylinderGeometry(r, r, h, seg);
  // a part turned by `rot` (radians about the board's normal) around its own centre (u, v)
  const turned = (u, v, rot) => (dx, dz) => [u + dx * Math.cos(rot) + dz * Math.sin(rot), v - dx * Math.sin(rot) + dz * Math.cos(rot)];
  const P = {
    // a through-hole chip in two rows of legs (DIP): 2.54 mm between legs, rows 7.6 mm apart
    dip(u, v, pins, rot = 0) {
      const n = pins / 2, L = n * 2.54 + 1, at = turned(u, v, rot), H = 3.3, lift = .8;
      add(box(L, H, 6.4), M.chip, u, lift + H / 2, v, rot);
      { const [x, z] = at(-L / 2 + 2, -1.6); add(cyl(.7, .2, 12), M.dark, x, lift + H + .05, z); }   // pin 1's dimple
      for (let i = 0; i < n; i++) for (const s of [-1, 1]) {
        const [x, z] = at((i - (n - 1) / 2) * 2.54, s * 3.55);
        add(box(.5, lift + 1.6, .3), M.lead, x, (lift + 1.6) / 2, z, rot);
      }
    },
    // a flat square chip with legs on all four sides (QFP)
    qfp(u, v, w, d, nx, nz = nx, rot = 0) {
      const at = turned(u, v, rot), H = 1.5, lift = .25;
      add(box(w, H, d), M.chip, u, lift + H / 2, v, rot);
      { const [x, z] = at(-w / 2 + 2, -d / 2 + 2); add(cyl(.6, .15, 12), M.dark, x, lift + H + .04, z); }
      const legs = (count, len, side, along) => { const pitch = (len - 2) / count;
        for (let i = 0; i < count; i++) { const t = -len / 2 + 1 + pitch * (i + .5);
          const [x, z] = along ? at(t, side) : at(side, t);
          add(along ? box(.28, .25, 1.6) : box(1.6, .25, .28), M.lead, x, .125, z, rot); } };
      legs(nx, w, -d / 2 - .7, true); legs(nx, w, d / 2 + .7, true); legs(nz, d, -w / 2 - .7, false); legs(nz, d, w / 2 + .7, false);
    },
    // a small two-row surface chip (SOIC / TSOP): memory, mostly
    soic(u, v, w, d, n, rot = 0) {
      const at = turned(u, v, rot), H = 1.3;
      add(box(w, H, d), M.chip, u, .2 + H / 2, v, rot);
      for (let i = 0; i < n; i++) for (const s of [-1, 1]) { const [x, z] = at(-w / 2 + (i + .5) * w / n, s * (d / 2 + .6)); add(box(.3, .25, 1.3), M.lead, x, .125, z, rot); }
    },
    // a modern chip on its substrate, with a bare metal lid (a phone-class processor)
    bga(u, v, s, rot = 0) { add(box(s, .6, s), M.top, u, .3, v, rot); add(box(s * .62, .45, s * .62), M.lid, u, .82, v, rot); },
    // a metal can over the parts under it
    shield(u, v, w, d, h = 2, rot = 0) { add(box(w, h, d), M.shield, u, h / 2, v, rot); add(box(w - 1.2, .05, d - 1.2), M.sink, u, h + .02, v, rot); },
    // a solid block of metal over a chip (the N64's heat spreaders)
    spreader(u, v, w, d, h) { add(box(w, h, d), M.sink, u, h / 2 + 1.5, v); add(box(w * .5, 1.5, d * .5), M.chip, u, .75, v); },
    // an electrolytic capacitor: a can in a sleeve, its scored top in bare aluminium
    ecap(u, v, d, h, blue = false) { add(cyl(d / 2, h - .4), blue ? M.ecap2 : M.ecap, u, (h - .4) / 2 + .3, v); add(cyl(d / 2 - .3, .4), M.sink, u, h - .1, v);
      add(box(d * .7, .1, .25), M.dark, u, h + .12, v); add(box(.25, .1, d * .7), M.dark, u, h + .12, v); },
    // rows of tiny surface-mount capacitors and resistors
    smd(u, v, n, du, dv = 0, dark = false) { for (let i = 0; i < n; i++) { add(box(1.6, .5, .8), dark ? M.smd2 : M.smd, u + i * du, .25, v + i * dv); add(box(.3, .52, .82), M.lead, u + i * du - .65, .26, v + i * dv); add(box(.3, .52, .82), M.lead, u + i * du + .65, .26, v + i * dv); } },
    // a through-hole resistor: a beige body with color bands, its legs bent down into the board
    res(u, v, rot = 0) { const at = turned(u, v, rot);
      const b = add(cyl(1.15, 6, 14), M.res, u, 1.6, v); b.rotation.set(0, rot, Math.PI / 2);
      [-1.6, -.6, .4].forEach(t => { const [x, z] = at(t, 0); const r = add(cyl(1.2, .45, 14), M.band, x, 1.6, z); r.rotation.set(0, rot, Math.PI / 2); });
      for (const s of [-1, 1]) { const [x, z] = at(s * 4.2, 0); add(box(.4, 1.6, .4), M.lead, x, .8, z); const [x2, z2] = at(s * 3.6, 0); add(box(1.4, .35, .35), M.lead, x2, 1.6, z2, rot); } },
    // a quartz crystal in its oval can (HC-49)
    xtal(u, v, rot = 0) { const at = turned(u, v, rot); add(box(7, 3.5, 4.4), M.shield, u, 1.95, v, rot); for (const s of [-1, 1]) { const [x, z] = at(s * 3.5, 0); add(cyl(2.2, 3.5, 16), M.shield, x, 1.95, z); } },
    // a regulator on its finned heatsink (TO-220)
    reg(u, v, rot = 0) { const at = turned(u, v, rot);
      add(box(10, 4.5, 2), M.chip, u, 6, v, rot);
      for (let i = 0; i < 5; i++) { const [x, z] = at(0, -2.5 - i * 1.6); add(box(16, 14, .7), M.sink, x, 7, z, rot); }
      for (const t of [-2.54, 0, 2.54]) { const [x, z] = at(t, 1.2); add(box(.6, 4, .4), M.lead, x, 2, z); } },
    // the cartridge connector: a block with a slot down its length and gold fingers inside
    slot(u, v, w, d, h, rot = 0) { const at = turned(u, v, rot);
      add(box(w, h, d), M.conn, u, h / 2, v, rot); add(box(w - 6, .2, d * .32), M.dark, u, h + .01, v, rot);
      const n = Math.round((w - 8) / 2.5); for (let i = 0; i < n; i++) for (const s of [-1, 1]) { const [x, z] = at(-w / 2 + 4 + (i + .5) * (w - 8) / n, s * d * .12); add(box(.8, .25, .5), M.gold, x, h + .05, z, rot); } },
    // any connector, jack or module as a plain block (controller ports, the RF box, a battery plug)
    block(u, v, w, d, h, m = 'conn', rot = 0) { add(box(w, h, d), M[m], u, h / 2, v, rot); },
    // a white pin header with its wires' plug
    header(u, v, n, rot = 0) { add(box(n * 2.5 + 1, 6, 5), M.white, u, 3, v, rot); },
    // a ribbon to the screen or the disc drive
    flex(u, v, w, len, rot = 0) { add(box(w, .25, len), M.flex, u, .3, v, rot); add(box(w + 3, 2, 4), M.white, u, 1, v + len / 2 * Math.cos(rot), rot); },
    // a round headphone jack or volume wheel lying on the board
    wheel(u, v, r, h) { add(cyl(r, h, 24), M.conn, u, h / 2, v); },
    // a screw hole: a ring of bare copper around a hole
    hole(u, v) { add(cyl(3, .06, 20), M.lead, u, .03, v); add(cyl(1.6, .08, 16), M.dark, u, .05, v); },
  };
  // what each part covers on the board (across, deep), so the small parts scattered after don't land on it
  const FOOT = { dip: (u, v, n, r) => [n * 1.27 + 2, 10, r], qfp: (u, v, w, d, nx, nz, r) => [w + 4, d + 4, r], soic: (u, v, w, d, n, r) => [w + 1, d + 3.5, r],
    bga: (u, v, s, r) => [s, s, r], shield: (u, v, w, d, h, r) => [w, d, r], spreader: (u, v, w, d) => [w, d], ecap: (u, v, d) => [d, d], smd: (u, v, n, du, dv = 0) => [Math.abs(du) * n + 2, Math.abs(dv) * n + 2, 0, (n - 1) * du / 2, (n - 1) * dv / 2],
    res: (u, v, r) => [10, 3, r], xtal: (u, v, r) => [12, 5, r], reg: (u, v, r) => [16, 12, r], slot: (u, v, w, d, h, r) => [w, d, r], block: (u, v, w, d, h, m, r) => [w, d, r],
    header: (u, v, n, r) => [n * 2.5 + 1, 5, r], flex: (u, v, w, len, r) => [w + 3, len + 4, r], wheel: (u, v, r) => [2 * r, 2 * r], hole: () => [6, 6] };
  const taken = [];
  for (const [k, ...a] of spec.parts) {
    P[k](...a);
    const [w, d, rot = 0, du = 0, dv = 0] = FOOT[k](...a), side = Math.abs(Math.sin(rot)) > .7;
    taken.push([a[0] + du, a[1] + dv, (side ? d : w) / 2 + 1, (side ? w : d) / 2 + 1]);
  }
  // Then the small parts real boards are covered in, in the free space: surface-mount capacitors and resistors (alone
  // or in little rows), a few tiny chips and capacitors. Placed the same way every time (a fixed seed per board).
  let seed = [...spec.code].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 2147483647, 7);
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const [W, D] = spec.size, cr = Math.max(...[].concat(spec.corner ?? 3)), free = (u, v, hw, hd) =>
    Math.abs(u) + hw < W / 2 - 3 && Math.abs(v) + hd < D / 2 - 3 && !(Math.abs(u) + hw > W / 2 - cr && Math.abs(v) + hd > D / 2 - cr) &&
    taken.every(([x, z, a, b]) => Math.abs(u - x) > a + hw || Math.abs(v - z) > b + hd);
  const want = spec.scatter ?? Math.round(W * D / 260);
  for (let tries = 0, n = 0; n < want && tries < want * 30; tries++) {
    const u = (rnd() - .5) * W, v = (rnd() - .5) * D, r = rnd(), turn = rnd() < .5;
    const [kind, hw, hd] = r < .55 ? ['smd', 1.2, .7] : r < .75 ? ['row', 4.5, .7] : r < .87 ? ['soic', 3.5, 3] : r < .95 ? ['ecap', 2.2, 2.2] : ['res', 5, 1.5];
    const [a, b] = turn ? [hd, hw] : [hw, hd];
    if (!free(u, v, a + .6, b + .6)) continue;
    taken.push([u, v, a + .6, b + .6]); n++;
    if (kind === 'smd') turn ? P.smd(u, v, 1, 0, 0, rnd() < .35) : P.smd(u, v, 1, 0, 0, rnd() < .35);
    else if (kind === 'row') turn ? P.smd(u, v - 3, 3, 0, 3, rnd() < .3) : P.smd(u - 3, v, 3, 3, 0, rnd() < .3);
    else if (kind === 'soic') P.soic(u, v, 5, 3.6, 3, turn ? Math.PI / 2 : 0);
    else if (kind === 'ecap') P.ecap(u, v, 4, spec.low ? 3.5 : 5.5, rnd() < .2);
    else P.res(u, v, turn ? Math.PI / 2 : 0);
  }
  return root;
}

/** Puts a board where it sits in its console: lying flat (home consoles, the DS) or standing in a handheld. */
export function placeBoard(board, spec) {
  const g = new THREE.Group(); g.name = 'board-place'; g.add(board);
  g.position.set(...spec.at);
  if (spec.stand) g.rotation.x = Math.PI / 2;   // parts toward the screen (+z), the print to the back; the board's z runs down
  return g;
}

// ---------- the boards, from the consoles' main boards (positions are approximate, the parts stylized) ----------
// at: where the board's top face sits, in the console's millimetres (lying flat, its underside on the console's lower half,
// just above the seam, so it stays on the base when the top opens); size: [across, deep] (handhelds: [across, tall]).
// u is across from the centre; v is toward the front (home consoles) or down (handhelds).
const ROW = (k, u0, v, n, du, ...a) => Array.from({ length: n }, (_, i) => [k, u0 + i * du, v, ...a]);
export const BOARDS = {
  nes: { at: [0, -34, 0], size: [228, 176], code: 'RT-8B-CPU', parts: [   // NES-CPU: the 72-pin connector mid-board, the CPU and PPU side by side behind it
    ['slot', 0, 32, 112, 22, 12], ['dip', -48, -22, 40], ['dip', 6, -22, 40], ['dip', 58, -34, 24], ['dip', 58, -12, 24],
    ['dip', -92, 34, 16], ...ROW('dip', -60, 2, 4, 26, 14), ['xtal', -30, -54], ['block', 88, -62, 44, 30, 16, 'shield'], ['reg', -84, -62],
    ['header', -96, 72, 7], ['header', -66, 72, 7], ['ecap', -60, -60, 8, 11], ['ecap', 30, -62, 6, 9], ['ecap', 92, 20, 10, 14], ['ecap', -100, 6, 6, 9],
    ...ROW('res', -20, 64, 5, 12), ...ROW('smd', 20, -48, 3, 9, 0, 1), ['hole', -104, -78], ['hole', 104, -78], ['hole', -104, 78], ['hole', 104, 78]] },
  snes: { at: [0, -3.4, 0], size: [186, 222], code: 'RT-16S-CPU', parts: [   // SNS-CPU: the slot across the middle, sound behind it, CPU and PPUs in front
    ['slot', 0, -38, 98, 14, 16], ['qfp', -52, 30, 20, 14, 25, 15], ['qfp', 6, 30, 20, 14, 25, 15], ['qfp', 54, 30, 20, 14, 25, 15],
    ['soic', -18, 62, 14, 8, 14], ['soic', 30, 62, 14, 8, 14], ['soic', 56, 62, 14, 8, 14], ['qfp', -40, -76, 14, 14, 16], ['qfp', 2, -76, 14, 14, 16],
    ['soic', 44, -76, 12, 8, 12], ['soic', 64, -76, 12, 8, 12], ['dip', -74, -2, 18], ['xtal', -20, 6], ['reg', -66, -92],
    ['header', -50, 100, 7], ['header', -14, 100, 7], ['ecap', 80, -96, 8, 12], ['ecap', -84, 60, 6, 9], ['ecap', 80, 92, 6, 9],
    ...ROW('smd', -40, 48, 6, 7, 0), ...ROW('smd', -10, -58, 5, 7, 0, 1), ...ROW('res', 70, 4, 3, 0, 0), ['hole', -84, -102], ['hole', 84, 102]] },
  n64: { at: [0, -25, 0], size: [232, 168], corner: [3, 3, 26, 26], code: 'RT-64X-CPU', parts: [   // NUS-CPU: the slot in the middle, three heat spreaders, four controller ports at the front
    ['slot', 0, -8, 112, 14, 22], ['spreader', 0, 34, 34, 34, 7], ['spreader', -52, 36, 26, 26, 6], ['spreader', 52, 32, 34, 22, 6],
    ['qfp', 78, 62, 14, 14, 14], ['block', -75, 76, 18, 12, 12], ['block', -25, 76, 18, 12, 12], ['block', 25, 76, 18, 12, 12], ['block', 75, 76, 18, 12, 12],
    ['slot', 0, 62, 40, 10, 12], ['xtal', -88, 30], ['reg', -92, -56], ['reg', 92, -56], ['ecap', -50, -58, 10, 13], ['ecap', 50, -60, 8, 11], ['ecap', 100, 20, 6, 9],
    ...ROW('smd', -30, -40, 9, 7, 0), ...ROW('smd', -88, 50, 4, 0, 7, 1), ['soic', 30, 60, 10, 6, 8], ['hole', -106, -72], ['hole', 106, -72], ['hole', -106, 70], ['hole', 106, 70]] },
  genesis: { at: [0, -22.5, 0], size: [252, 192], code: 'RT-16G-CPU', parts: [   // VA board: the slot under the round dome, the long 68000, the Z80, the video chip, the FM chip
    ['slot', -20, -6, 100, 16, 20], ['dip', -26, 44, 64], ['dip', 62, -44, 40], ['qfp', 66, 34, 26, 26, 20], ['dip', 100, -6, 24], ['soic', 26, 70, 14, 8, 14], ['soic', 48, 70, 14, 8, 14],
    ['block', 92, -76, 44, 30, 16, 'shield'], ['reg', -96, -70], ['wheel', -104, 84, 4, 10], ['block', -45, 86, 22, 14, 12], ['block', -12, 86, 22, 14, 12], ['xtal', 20, -52],
    ['ecap', -60, -56, 8, 12], ['ecap', -108, 20, 10, 14, true], ['ecap', 30, 40, 6, 9], ...ROW('res', -74, 70, 4, 12), ...ROW('smd', 10, -32, 5, 7, 0, 1), ['hole', -112, -84], ['hole', 112, 84]] },
  ps1: { at: [0, -22.5, 0], size: [252, 170], code: 'RT-32P-CPU', parts: [   // PU board: the processor, video and sound chips to the right, the disc drive's chips under it on the left
    ['qfp', 40, -24, 28, 28, 26], ['qfp', 90, -28, 28, 28, 26], ['qfp', 90, 22, 20, 20, 20], ...ROW('soic', 26, 18, 2, 22, 18, 10, 14), ['soic', 52, 46, 18, 10, 14], ['soic', 74, 52, 14, 8, 12],
    ['soic', 0, 34, 16, 9, 14], ['qfp', -64, -10, 20, 20, 20], ['qfp', -28, -30, 14, 14, 14], ['flex', -64, -50, 24, 30], ['xtal', 8, -2],
    ['block', -60, 78, 24, 12, 14], ['block', -20, 78, 24, 12, 14], ['ecap', -108, -60, 12, 16], ['ecap', -88, -62, 10, 14], ['ecap', 116, 60, 6, 9], ['reg', -110, 30],
    ...ROW('smd', 20, -52, 8, 8, 0), ...ROW('smd', -100, 50, 5, 0, 6, 1), ['hole', -116, -76], ['hole', 116, -76], ['hole', -116, 76], ['hole', 116, 76]] },
  gb: { at: [0, 0, -4.5], stand: true, size: [82, 130], corner: [4, 4, 24, 4], code: 'RT-PK1-CPU', parts: [   // DMG-CPU: the cartridge connector along the top, the processor in the middle
    ['slot', 0, -56, 64, 9, 8], ['qfp', 0, -14, 20, 14, 20, 12], ['soic', -26, -14, 10, 7, 10], ['soic', 26, -14, 10, 7, 10], ['xtal', -14, 12, 0],
    ['flex', 0, -36, 30, 10], ['block', -34, 20, 9, 16, 7], ['wheel', 34, -32, 6, 4], ['wheel', 28, 54, 3.5, 5], ['block', -26, -58, 12, 6, 5, 'white'],
    ['ecap', 22, 18, 6, 7], ['ecap', -26, 44, 6, 7, true], ...ROW('smd', -18, 30, 6, 6, 0), ...ROW('smd', 8, 40, 4, 6, 0, 1), ['hole', -34, -46], ['hole', 34, 58]] },
  gbc: { at: [0, 0, -2.5], stand: true, size: [70, 118], corner: 6, code: 'RT-PK2-CPU', parts: [
    ['slot', 0, -50, 56, 8, 7], ['qfp', 0, -10, 18, 18, 24], ['soic', 22, 14, 10, 7, 10], ['xtal', -18, 16, 0], ['flex', 0, -32, 30, 8],
    ['block', 28, -30, 8, 12, 5, 'dark'], ['wheel', -24, 46, 3.5, 4], ['ecap', 20, 36, 5, 6], ['ecap', -6, 40, 5, 6, true],
    ...ROW('smd', -20, 30, 5, 6, 0), ...ROW('smd', -26, -28, 4, 0, 6, 1), ['hole', -28, 52], ['hole', 28, 52]] },
  gba: { at: [0, 0, -1.8], stand: true, size: [128, 70], corner: 8, code: 'RT-PK3-CPU', parts: [   // AGB-CPU: the slot along the top edge, the big processor centred under the screen
    ['slot', 0, -28, 64, 8, 7], ['qfp', 0, 4, 24, 24, 30], ['soic', 26, 4, 12, 8, 12], ['flex', -30, -10, 26, 8], ['block', -50, -20, 8, 10, 5, 'dark'],
    ['wheel', 46, 22, 4, 4], ['xtal', -22, 22, 0], ['ecap', 44, -6, 5, 6], ['ecap', -46, 14, 5, 6], ['block', 0, 30, 14, 6, 4],
    ...ROW('smd', -50, 26, 6, 5, 0), ...ROW('smd', 22, 24, 4, 5, 0, 1), ['hole', -56, -24], ['hole', 56, -24]] },
  ds: { at: [0, -2.2, 0], size: [140, 74], corner: 10, thick: 1, low: true, code: 'RT-DS-CPU', parts: [   // NTR-CPU: the card slot at the back, the game pak slot at the front, the processor in the middle
    ['slot', 0, -30, 44, 7, 4], ['slot', 0, 32, 64, 6, 4], ['qfp', -6, 0, 22, 22, 28], ['soic', 24, -2, 12, 9, 12], ['qfp', -38, 8, 10, 10, 10],
    ['shield', 46, -14, 22, 18, 2.4], ['flex', -46, -16, 18, 8], ['ecap', 56, 16, 4, 4], ...ROW('smd', -30, -22, 6, 6, 0), ...ROW('smd', 14, 18, 5, 6, 0, 1), ['hole', -60, 0], ['hole', 60, 0]] },
  psp: { at: [0, 0, -1], stand: true, size: [148, 60], corner: 26, mask: '#1d5a43', code: 'RT-UM-CPU', parts: [   // TA board: the processor under its shield, memory beside it, the radio on its own
    ['shield', -6, -2, 40, 30, 2.4], ['soic', 26, -10, 14, 10, 12], ['bga', 26, 12, 12], ['shield', 54, 4, 20, 16, 2], ['block', -48, 16, 16, 10, 3, 'conn'],
    ['flex', -44, -14, 22, 8], ['flex', 0, 22, 30, 6], ['ecap', -60, -4, 4, 4], ...ROW('smd', -30, 20, 6, 5, 0, 1), ...ROW('smd', 20, -24, 6, 5, 0), ['hole', -62, 14], ['hole', 62, -10]] },
  android: { at: [0, 0, -2], stand: true, size: [160, 64], corner: 10, mask: '#16191f', code: 'RT-AC-CPU', parts: [   // a phone-class board: the processor and memory under a can, the radio, the charging port
    ['shield', 0, -4, 34, 30, 1.8], ['bga', -34, -6, 14], ['soic', -34, 14, 12, 8, 10], ['shield', 40, -4, 22, 18, 1.6], ['qfp', 40, 18, 8, 8, 8],
    ['block', 0, 27, 10, 6, 3.2, 'shield'], ['flex', -58, 0, 16, 10], ['flex', 62, 6, 14, 10], ['block', 62, -20, 10, 6, 2.4, 'white'],
    ...ROW('smd', -24, 20, 8, 4, 0), ...ROW('smd', 18, -22, 6, 4, 0, 1), ...ROW('smd', 24, 22, 5, 4, 0), ['hole', -72, -24], ['hole', 72, 24]] },
};
