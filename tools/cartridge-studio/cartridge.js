// Builds a cartridge from a style in cartridges.js: a beveled, extruded body in physically based plastic, a label
// recess with rounded edges, a matte label set into it, and beveled grip ridges. Units are millimetres.
import * as THREE from 'three';
import { toCreasedNormals, mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';

// ---------- outlines ----------

// Corner list → polygon points, with each corner rounded by its radius (and 'arc' entries expanded).
export function outlinePoints(spec, seg = 10) {
  const corners = [];
  for (const c of spec) {
    if (c[0] === 'arc') {
      const [, cx, cy, r, a0, a1] = c, n = Math.max(8, Math.round(Math.abs(a1 - a0) / 3));
      for (let i = 1; i < n; i++) { const a = (a0 + (a1 - a0) * i / n) * Math.PI / 180; corners.push([cx + r * Math.cos(a), cy + r * Math.sin(a), 0]); }
    } else corners.push(c);
  }
  const out = [], n = corners.length;
  for (let i = 0; i < n; i++) {
    const A = corners[(i + n - 1) % n], B = corners[i], C = corners[(i + 1) % n], r = B[2] || 0;
    let ux = A[0] - B[0], uy = A[1] - B[1], vx = C[0] - B[0], vy = C[1] - B[1];
    const lu = Math.hypot(ux, uy), lv = Math.hypot(vx, vy); ux /= lu; uy /= lu; vx /= lv; vy /= lv;
    const th = Math.acos(Math.max(-1, Math.min(1, ux * vx + uy * vy)));
    if (r <= 0 || th < 1e-3 || th > Math.PI - 1e-3) { out.push([B[0], B[1]]); continue; }
    const t = Math.min(r / Math.tan(th / 2), lu * .5, lv * .5), rr = t * Math.tan(th / 2);
    const bx = ux + vx, by = uy + vy, bl = Math.hypot(bx, by), dc = rr / Math.sin(th / 2), cx = B[0] + bx / bl * dc, cy = B[1] + by / bl * dc;
    const a0 = Math.atan2(B[1] + uy * t - cy, B[0] + ux * t - cx); let da = Math.atan2(B[1] + vy * t - cy, B[0] + vx * t - cx) - a0;
    while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
    const k = Math.max(2, Math.ceil(seg * Math.abs(da) / (Math.PI / 2)));
    for (let j = 0; j <= k; j++) { const a = a0 + da * j / k; out.push([cx + rr * Math.cos(a), cy + rr * Math.sin(a)]); }
  }
  return ccw(out);
}

const area = p => p.reduce((s, a, i) => { const b = p[(i + 1) % p.length]; return s + a[0] * b[1] - b[0] * a[1]; }, 0) / 2;
const ccw = p => area(p) < 0 ? p.slice().reverse() : p;

// Moves a counter-clockwise outline outward by d (inward when d is negative), mitring the corners.
export function offset(p, d) {
  const n = p.length, out = [];
  for (let i = 0; i < n; i++) {
    const a = p[(i + n - 1) % n], b = p[i], c = p[(i + 1) % n];
    const e1 = norm([b[0] - a[0], b[1] - a[1]]), e2 = norm([c[0] - b[0], c[1] - b[1]]);
    const n1 = [e1[1], -e1[0]], n2 = [e2[1], -e2[0]];   // outward normals of a counter-clockwise outline
    const m = norm([n1[0] + n2[0], n1[1] + n2[1]]), cos = Math.max(.35, m[0] * n2[0] + m[1] * n2[1]);
    out.push([b[0] + m[0] * d / cos, b[1] + m[1] * d / cos]);
  }
  return d < 0 ? unfold(p, out) : out;
}
// Pulling an outline inward folds any corner rounder than the pull back over itself, leaving little spikes under the
// bevel. Drop the points whose edges turned around, until none do.
function unfold(orig, pts) {
  let o = orig.slice(), q = pts.slice();
  for (let pass = 0; pass < 50; pass++) {
    let removed = false;
    for (let i = 0; i < q.length && q.length > 3; i++) {
      const j = (i + 1) % q.length, e = [o[j][0] - o[i][0], o[j][1] - o[i][1]], f = [q[j][0] - q[i][0], q[j][1] - q[i][1]];
      if (e[0] * f[0] + e[1] * f[1] < 0) { q.splice(j, 1); o.splice(j, 1); removed = true; i--; }
    }
    if (!removed) break;
  }
  return q;
}
const norm = v => { const l = Math.hypot(v[0], v[1]) || 1; return [v[0] / l, v[1] / l]; };

const rrect = (x, y, w, h, r) => outlinePoints([[x - w / 2, y - h / 2, r], [x + w / 2, y - h / 2, r], [x + w / 2, y + h / 2, r], [x - w / 2, y + h / 2, r]]);
const toShape = pts => new THREE.Shape(pts.map(p => new THREE.Vector2(p[0], p[1])));
const toPath = pts => new THREE.Path(pts.slice().reverse().map(p => new THREE.Vector2(p[0], p[1])));   // holes wind the other way

// An extrusion from z0 to z1 whose rounded bevels stay inside the outline, so the finished part keeps its true size.
export function slab(pts, holes, z0, z1, bevel, segments) {
  const t = Math.max(0, Math.min(bevel, (z1 - z0) / 2 - .01));
  const shape = toShape(t > 0 ? offset(pts, -t) : pts);
  for (const h of holes) shape.holes.push(toPath(t > 0 ? offset(h, t) : h));
  const g = new THREE.ExtrudeGeometry(shape, {
    depth: Math.max(.01, z1 - z0 - 2 * t), bevelEnabled: t > 0, bevelThickness: t, bevelSize: t, bevelOffset: 0, bevelSegments: segments, curveSegments: 12, steps: 1,
  });
  g.translate(0, 0, z0 + t);
  return flatCaps(toCreasedNormals(g, Math.PI / 5));   // smooth across the rounded corners and the bevel steps, keep real creases sharp
}
// The front and back faces stay perfectly flat: smoothing them into the bevel bends the light across the whole face.
function flatCaps(g) {
  const n = g.attributes.normal;
  for (const grp of g.groups.filter(x => x.materialIndex === 0))
    for (let i = grp.start; i < grp.start + grp.count; i++) { const z = Math.sign(n.getZ(i)) || 1; n.setXYZ(i, 0, 0, z); }
  n.needsUpdate = true; return g;
}

// ---------- materials ----------

export function plasticMaterial(f) {
  return new THREE.MeshPhysicalMaterial({
    color: new THREE.Color(f.color), metalness: 0, roughness: f.roughness,
    clearcoat: f.clearcoat, clearcoatRoughness: f.clearcoatRoughness, envMapIntensity: f.envIntensity ?? 1,
  });
}
export function labelMaterial(texture, f = {}) {
  return new THREE.MeshPhysicalMaterial({ map: texture, metalness: 0, roughness: f.labelRoughness ?? .62, clearcoat: f.labelGloss ?? .08, clearcoatRoughness: .5, envMapIntensity: f.envIntensity ?? 1 });
}

// ---------- the cartridge ----------

// opts: { bevel, segments, finish: {color, roughness, clearcoat, clearcoatRoughness, envIntensity}, labelTexture }
export function buildCartridge(style, opts) {
  if (style.kind === 'disc') return buildDisc(style, opts);
  const { bevel, segments } = opts, D = style.depth, front = D / 2, back = -D / 2;
  const body = outlinePoints(style.outline);
  const [lx, ly, lw, lh, lr, recess] = style.label;
  const holes = [rrect(lx, ly, lw, lh, lr), ...(style.recesses || []).map(([x, y, w, h, r]) => rrect(x, y, w, h, r))];
  const b = Math.min(bevel, D * .2);
  const plastic = plasticMaterial(opts.finish);
  const group = new THREE.Group(); group.name = style.name;

  // The body is built like the real thing, from two halves: a back wall and a front wall (each rounded on its outer
  // edge) and a middle layer between them with no bevel, reaching into the walls' rounded inner ends so the sides stay
  // one smooth surface. Where the halves meet, a fine seam runs around the sides: the middle is split there, and a
  // slightly smaller strip fills the gap. Cartridges with an edge connector are hollow and open at the bottom, a slot
  // cut up into the middle layer as tall and wide as the circuit board, so it shows from below. Cards keep their
  // contacts on the outside: a shallow pocket in the back wall with gold contacts between plastic ribs.
  const floorZ = front - recess, [W0, H0] = style.size;
  const bw0 = W0 * .78, bh0 = H0 * .72, pcbTop = -H0 / 2 + bh0 + H0 * .03;
  const slotted = style.slot !== false && D >= 6;
  const skin = Math.min(2 * b + .4, D * .3), mid0 = back + skin - b, mid1 = floorZ - skin + b;
  const core = new THREE.Group(); core.name = 'shell';
  const ct = style.contacts, pocket = ct && rrect(0, -H0 / 2 + ct.bottom + ct.h / 2 + .1, ct.n * ct.pitch + .5, ct.h + .5, .4);
  const backWall = new THREE.Mesh(slab(body, pocket ? [pocket] : [], back, back + skin, b, segments), plastic); backWall.name = 'body';
  const frontWall = new THREE.Mesh(slab(body, [], floorZ - skin, floorZ, b, segments), plastic); frontWall.name = 'body';
  core.add(backWall, frontWall);
  const middleLine = slotted ? withSlot(body, bw0 + 2, pcbTop + 1.2) : body;
  const seam = Math.max(.3, D * .025), seamZ = Math.min(Math.max((back + front) / 2, mid0 + seam + .2), mid1 - seam - .2);
  const halves = [[mid0, seamZ - seam / 2], [seamZ + seam / 2, mid1]];
  for (const [z0, z1] of halves) { const m = new THREE.Mesh(slab(middleLine, [], z0, z1, 0, 1), plastic); m.name = 'body'; core.add(m); }
  const filler = new THREE.Mesh(slab(offset(middleLine, -Math.min(.2, seam * .5)), [], seamZ - seam / 2 - .05, seamZ + seam / 2 + .05, 0, 1), plastic); filler.name = 'body';
  core.add(filler);
  if (ct) {   // the card's contacts: gold, on the pocket's floor, with ribs between them up to the back's surface
    const gold = new THREE.MeshPhysicalMaterial({ color: '#d6b25a', metalness: 1, roughness: .28, clearcoat: .2 }); gold.name = 'contact-gold';
    const cy = -H0 / 2 + ct.bottom + ct.h / 2 + .1, depth = mid0 - back, x0 = -(ct.n - 1) * ct.pitch / 2;
    for (let i = 0; i < ct.n; i++) {
      const pad = new THREE.Mesh(new THREE.BoxGeometry(ct.w, ct.h, .08), gold); pad.position.set(x0 + i * ct.pitch, cy, mid0 - .04); pad.name = 'contact'; core.add(pad);
      if (i < ct.n - 1) { const rib = new THREE.Mesh(new THREE.BoxGeometry(ct.pitch - ct.w - .1, ct.h + .5, depth), plastic); rib.position.set(x0 + (i + .5) * ct.pitch, cy, back + depth / 2); rib.name = 'body'; core.add(rib); }
    }
  }
  // …and a front plate with the recess cut through it. Its back edge is buried in the front wall and the middle, so no
  // groove shows; the hole's own bevel rounds the edge of the recess.
  const overlap = 2 * Math.min(b, recess) + .3;
  const plate = new THREE.Mesh(slab(body, holes, front - recess - overlap, front, Math.min(b, (recess + overlap) / 2 - .02), segments), plastic); plate.name = 'front';
  group.add(core, plate);

  // the label, set just above the floor of the recess, on its own matte material
  const inset = Math.min(.4, lw * .02);
  const lg = new THREE.ShapeGeometry(toShape(rrect(lx, ly, lw - inset * 2, lh - inset * 2, Math.max(0, lr - inset))), 8);
  const uv = lg.attributes.uv, pos = lg.attributes.position;
  for (let i = 0; i < uv.count; i++) uv.setXY(i, (pos.getX(i) - (lx - lw / 2)) / lw, (pos.getY(i) - (ly - lh / 2)) / lh);
  lg.translate(0, 0, front - recess + .06);
  const label = new THREE.Mesh(lg, labelMaterial(opts.labelTexture, opts.finish)); label.name = 'label';
  group.add(label);

  // grip ridges, raised from the face they sit on
  for (const r of style.ridges || []) {
    const z0 = r.side === 'back' ? back - r.height : front - .2, z1 = r.side === 'back' ? back + .2 : front + r.height;
    const m = new THREE.Mesh(slab(rrect(r.x, r.y, r.w, r.h, Math.min(r.r, r.h / 2)), [], z0, z1, Math.min(b, r.height * .45, r.h * .3), Math.max(2, segments - 2)), plastic);
    m.name = 'ridge'; group.add(m);
  }
  // the circuit board inside, seen through clear plastic and, on slotted cartridges, from below
  const [W, H] = style.size, inside = new THREE.Group(); inside.name = 'inside'; inside.visible = slotted;
  const bw = W * .78, bh = H * .72, bz = back + D * .38, by = -H / 2 + bh / 2 + H * .03;
  const pcb = new THREE.Mesh(new THREE.BoxGeometry(bw, bh, Math.min(1, D * .12)), new THREE.MeshPhysicalMaterial({ color: '#2c6a43', roughness: .55, clearcoat: .3 }));
  pcb.position.set(0, by, bz); inside.add(pcb);
  // the board's back, printed like a real one (boardPrint): seen through a clear shell's back
  const pt = Math.min(1, D * .12), print = new THREE.Mesh(new THREE.PlaneGeometry(bw * .98, bh * .98).rotateY(Math.PI),
    new THREE.MeshPhysicalMaterial({ map: opts.boardTexture || boardPrint(style), roughness: .6, clearcoat: .2, clearcoatRoughness: .5 }));
  print.position.set(0, by, bz - pt / 2 - .03); print.name = 'pcb-print'; inside.add(print);
  const chip = new THREE.Mesh(new THREE.BoxGeometry(bw * .38, bh * .3, Math.min(1.4, D * .15)), new THREE.MeshPhysicalMaterial({ color: '#141418', roughness: .5 }));
  chip.position.set(-bw * .12, by + bh * .12, bz + Math.min(1.2, D * .14)); inside.add(chip);
  const gold = new THREE.MeshPhysicalMaterial({ color: '#d6b25a', metalness: 1, roughness: .3 }), n = Math.max(8, Math.round(bw / 2.6));
  for (let i = 0; i < n; i++) {   // the edge connector's contacts, along the bottom
    const c = new THREE.Mesh(new THREE.BoxGeometry(bw / n * .6, bh * .12, .1), gold);
    c.position.set(-bw / 2 + (i + .5) * bw / n, by - bh / 2 + bh * .07, bz + Math.min(.55, D * .07)); inside.add(c);
  }
  group.add(inside);
  group.userData.materials = { plastic, label: label.material }; group.userData.slotted = slotted;
  return group;
}

// The outline with a slot cut up into it from the middle of its bottom edge: `w` wide (at most the straight part of
// the bottom edge, less a millimetre each side) and reaching up to `top`.
function withSlot(pts, w, top) {
  const minY = Math.min(...pts.map(p => p[1])), out = [];
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], c = pts[(i + 1) % pts.length];
    out.push(a);
    if (Math.abs(a[1] - minY) < 1e-6 && Math.abs(c[1] - minY) < 1e-6 && c[0] - a[0] > 4) {   // the bottom edge, left to right
      const mid = (a[0] + c[0]) / 2, half = Math.min(w, c[0] - a[0] - 2) / 2;
      out.push([mid - half, minY], [mid - half, top], [mid + half, top], [mid + half, minY]);
    }
  }
  return out;
}

// ---------- the circuit board's print ----------

// Orbitron (SIL Open Font License, vendor/fonts) for the board's maker: RaccoonTek. ready resolves once it's loaded.
export const fontReady = typeof FontFace === 'undefined' ? Promise.resolve() :
  new FontFace('Orbitron', 'url(./vendor/fonts/Orbitron.ttf)', { weight: '400 900' }).load().then(f => document.fonts.add(f)).catch(() => {});

// The back of a circuit board: green solder mask, faint copper traces, white silkscreen: RaccoonTek, the board's part
// number, and a few tiny markings. Drawn for each cartridge style at its board's proportions.
// RaccoonTek's own part numbers, one per board (invented; no console maker's names on the boards)
const BOARD_CODES = { 'NES': 'RT-8B-01', 'Super Nintendo': 'RT-16S-02', 'Nintendo 64': 'RT-64X-03', 'Sega Genesis': 'RT-16G-04',
  'Game Boy': 'RT-PK1-05', 'Game Boy Color': 'RT-PK2-06', 'Game Boy Advance': 'RT-PK3-07' };
export function boardPrint(style) {
  const [W0, H0] = style.size, aspect = (W0 * .78) / (H0 * .72), w = 1024, h = Math.round(w / aspect);
  const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d');
  x.fillStyle = '#1d5a3b'; x.fillRect(0, 0, w, h);
  let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  x.strokeStyle = 'rgba(120, 190, 140, .28)'; x.lineWidth = w * .006; x.lineCap = 'round'; x.lineJoin = 'round';
  for (let i = 0; i < 26; i++) {   // traces: straight runs with 45° turns, the way boards are routed
    let px = rnd() * w, py = h * (.12 + rnd() * .76); x.beginPath(); x.moveTo(px, py);
    for (let k = 0; k < 3; k++) { const d = w * (.05 + rnd() * .14), dir = Math.floor(rnd() * 4); if (dir === 0) px += d; else if (dir === 1) px -= d; else { px += d * (dir === 2 ? .7 : -.7); py += d * .7 * (rnd() < .5 ? 1 : -1); } x.lineTo(px, py); }
    x.stroke(); x.fillStyle = 'rgba(200, 220, 160, .45)'; x.beginPath(); x.arc(px, py, w * .007, 0, Math.PI * 2); x.fill();
  }
  x.fillStyle = '#c9a54e';   // the contacts' row along the bottom edge
  const n = Math.max(8, Math.round(W0 * .78 / 2.6));
  for (let i = 0; i < n; i++) x.fillRect((i + .2) * w / n, h * .9, w / n * .6, h * .1);
  x.strokeStyle = 'rgba(240, 244, 236, .85)'; x.lineWidth = w * .004; x.strokeRect(w * .04, h * .06, w * .92, h * .78);
  x.fillStyle = '#f0f4ec'; x.textAlign = 'center'; x.textBaseline = 'middle';
  let size = w * .13; x.font = `800 ${size}px Orbitron, sans-serif`;
  const tw = x.measureText('RaccoonTek').width; if (tw > w * .78) { size *= w * .78 / tw; x.font = `800 ${size}px Orbitron, sans-serif`; }
  x.fillText('RaccoonTek', w / 2, h * .4);
  x.font = `500 ${size * .3}px Orbitron, sans-serif`;
  x.fillText(`${BOARD_CODES[style.name] || 'RT-01'}  ·  REV 2.0`, w / 2, h * .4 + size * .78);
  x.textAlign = 'left'; x.font = `500 ${size * .2}px Orbitron, sans-serif`;
  x.fillText('U1', w * .08, h * .14); x.fillText('C3', w * .84, h * .72);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4;
  return t;
}

// A disc: a thin ring, glossy underneath, with the label printed across the top and a clear hub around the hole.
function buildDisc(style, opts) {
  const { radius: R, hole, hub, depth: D } = style, plastic = plasticMaterial(opts.finish);
  const group = new THREE.Group(); group.name = style.name;
  const prof = [[hole, -D / 2], [R - .4, -D / 2], [R, -D / 2 + .3], [R, D / 2 - .3], [R - .4, D / 2], [hole, D / 2]].map(([r, z]) => new THREE.Vector2(r, z));
  const g = new THREE.LatheGeometry(prof, 96); g.rotateX(Math.PI / 2);   // the disc's edge, turned around z
  const caps = [new THREE.RingGeometry(hole, R - .4, 96, 1), new THREE.RingGeometry(hole, R - .4, 96, 1)];
  caps[0].translate(0, 0, D / 2); caps[1].rotateX(Math.PI); caps[1].translate(0, 0, -D / 2);
  const body = new THREE.Mesh(mergeGeometries([g, ...caps].map(x => x.toNonIndexed())), plastic); body.name = 'body';
  const lg = new THREE.RingGeometry(hub, R - 1, 96, 1); lg.translate(0, 0, D / 2 + .05);   // planar UVs: the square label across the whole disc
  const label = new THREE.Mesh(lg, labelMaterial(opts.labelTexture, opts.finish)); label.name = 'label';
  const ring = new THREE.Mesh(new THREE.RingGeometry(hole + .5, hub, 64, 1).translate(0, 0, D / 2 + .03),
    new THREE.MeshPhysicalMaterial({ color: '#c8ccd4', roughness: .25, transmission: .6, thickness: .8, clearcoat: .6 })); ring.name = 'hub';
  group.add(body, label, ring);
  group.add(Object.assign(new THREE.Group(), { name: 'inside', visible: false }));
  group.userData.materials = { plastic, label: label.material };
  return group;
}

// Puts a color variant from cartridges.js on a built cartridge: plain, metallic-looking (gold and silver), or see-through.
export function applyVariant(group, variant, tune) {
  const m = group.userData.materials.plastic, clear = variant.clear;
  m.color.set(variant.color);
  m.roughness = clear === 'transparent' ? .06 : clear === 'frosted' ? Math.max(.4, tune.roughness) : variant.sheen ? tune.roughness * .8 : tune.roughness;
  m.metalness = variant.sheen ? .45 : 0;
  m.clearcoat = tune.clearcoat; m.clearcoatRoughness = clear === 'transparent' ? .05 : tune.clearcoatRoughness;
  m.transmission = clear ? 1 : 0; m.thickness = clear ? 2.5 : 0; m.ior = 1.49;
  m.attenuationColor.set(clear ? variant.color : '#ffffff'); m.attenuationDistance = clear === 'transparent' ? 30 : clear ? 10 : Infinity;
  if (clear) m.color.lerp(new THREE.Color(1, 1, 1), .35);   // see-through plastic is tinted mostly by its depth, not its surface
  const inside = group.getObjectByName('inside'); if (inside && !group.userData.slotted) inside.visible = !!clear;   // a slotted cartridge's board shows from below too
}

// ---------- a label to show until you load your own art ----------

export function sampleLabel(style, hue = 205) {
  const [, , lw, lh] = style.label, W = 1024, H = Math.round(W * lh / lw), c = document.createElement('canvas'); c.width = W; c.height = H;
  const x = c.getContext('2d'), g = x.createLinearGradient(0, 0, W, H);
  g.addColorStop(0, `hsl(${hue},55%,22%)`); g.addColorStop(1, `hsl(${(hue + 50) % 360},60%,42%)`);
  x.fillStyle = g; x.fillRect(0, 0, W, H);
  x.fillStyle = `hsla(${(hue + 160) % 360},70%,70%,.85)`; x.beginPath(); x.arc(W * .72, H * .42, Math.min(W, H) * .26, 0, 7); x.fill();
  x.fillStyle = 'rgba(255,255,255,.12)'; for (let i = 0; i < 5; i++) { x.beginPath(); x.ellipse(W * (.2 + i * .15), H * .9, W * .2, H * .18, 0, 0, 7); x.fill(); }
  x.fillStyle = '#fff'; x.font = `600 ${Math.round(Math.min(W, H) * .12)}px system-ui, sans-serif`; x.textBaseline = 'bottom';
  x.fillText('Starfall Tactics', W * .07, H * .9);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; return t;
}
