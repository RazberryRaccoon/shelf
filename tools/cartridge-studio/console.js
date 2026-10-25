// Builds a console from its entry in consoles.js, in the same finish as the cartridges: beveled extrusions in physically
// based plastic, glass screens, lit LEDs. The entries are written in Shelf's own units (1 = 10 cm, the units of its
// code-built consoles); everything here comes out in millimetres. Front faces +z, the top faces +y.
import * as THREE from 'three';
import { toCreasedNormals } from 'three/addons/utils/BufferGeometryUtils.js';
import { outlinePoints, slab } from './cartridge.js';

const MM = 100;
const area = p => p.reduce((s, a, i) => { const b = p[(i + 1) % p.length]; return s + a[0] * b[1] - b[0] * a[1]; }, 0) / 2;
const ccw = p => area(p) < 0 ? p.slice().reverse() : p;

// A counter-clockwise outline moved inward by d, keeping every point (so outlines at several depths line up point for
// point): each point moves along its corner's bisector, and where a tight corner would fold over itself, the points
// that crossed meet in the middle instead.
function inset(p, d) {
  const n = p.length, out = [];
  for (let i = 0; i < n; i++) {
    const a = p[(i + n - 1) % n], b = p[i], c = p[(i + 1) % n];
    const e1 = unit([b[0] - a[0], b[1] - a[1]]), e2 = unit([c[0] - b[0], c[1] - b[1]]);
    const n1 = [e1[1], -e1[0]], n2 = [e2[1], -e2[0]], m = unit([n1[0] + n2[0], n1[1] + n2[1]]), cos = Math.max(.35, m[0] * n2[0] + m[1] * n2[1]);
    out.push([b[0] - m[0] * d / cos, b[1] - m[1] * d / cos]);
  }
  for (let pass = 0, again = true; again && pass < 40; pass++) {
    again = false;
    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n, o = [p[j][0] - p[i][0], p[j][1] - p[i][1]], q = [out[j][0] - out[i][0], out[j][1] - out[i][1]];
      if (o[0] * q[0] + o[1] * q[1] < -1e-9) { const mid = [(out[i][0] + out[j][0]) / 2, (out[i][1] + out[j][1]) / 2]; out[i] = mid; out[j] = mid.slice(); again = true; }
    }
  }
  return out;
}
const unit = v => { const l = Math.hypot(v[0], v[1]) || 1; return [v[0] / l, v[1] / l]; };

/**
 * One half of a shell, hollow like the real ones (and the cartridges): a face `wall` thick, its outer edge rounded by
 * `bev`, sides `wall` thick, open toward the seam, where the outer edge has a small 45° chamfer (two halves together
 * leave a fine V-shaped seam). Built along z, from the face at zFace to the open rim at zRim; pts in millimetres.
 */
function cup(pts, zFace, zRim, bev, wall = 2.4, segs = 6) {
  pts = ccw(pts);
  const s = Math.sign(zRim - zFace), H = Math.abs(zRim - zFace), ch = Math.min(.5, H * .1);
  const b = Math.max(.2, Math.min(bev, H - ch - .3)), wf = Math.min(wall, H * .45), w = Math.max(wall, ch + .6);
  // the cross-section, as (how far in from the outline, how far from the face): the rounded edge, the side, the
  // seam's chamfer, the rim, then down the inside wall to the inside of the face
  const prof = [];
  for (let i = 0; i <= segs; i++) { const t = i / segs * Math.PI / 2; prof.push([b * (1 - Math.sin(t)), b * (1 - Math.cos(t))]); }
  prof.push([0, H - ch], [ch, H], [w, H], [w, wf]);
  const rings = prof.map(([d, h]) => inset(pts, d).map(([x, y]) => [x, y, zFace + s * h]));
  const P = [], caps = [], n = pts.length;
  const tri = (a, b, c) => P.push(...a, ...b, ...c);
  for (let k = 0; k + 1 < rings.length; k++) for (let i = 0; i < n; i++) {
    const j = (i + 1) % n, a = rings[k][i], b = rings[k][j], c = rings[k + 1][j], d = rings[k + 1][i];
    if (s > 0) { tri(a, b, c); tri(a, c, d); } else { tri(a, d, c); tri(a, c, b); }   // wound so every surface faces out of the plastic
  }
  // the outside of the face, and the inside of it (facing the open side), flat
  const flat = (ring, z, out) => {
    const idx = THREE.ShapeUtils.triangulateShape(ring.map(([x, y]) => new THREE.Vector2(x, y)), []);
    const start = P.length / 3;
    for (const [a, b, c] of idx) { const A = [...ring[a], z], B = [...ring[b], z], C = [...ring[c], z]; (out ? s < 0 : s > 0) ? tri(A, B, C) : tri(A, C, B); }
    caps.push([start, P.length / 3, out ? -s : s]);
  };
  flat(inset(pts, b), zFace, true); flat(inset(pts, w), zFace + s * wf, false);
  let g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(P, 3)); g.computeVertexNormals();
  g = toCreasedNormals(g, Math.PI / 5);   // smooth round the corners and over the rounded edge, sharp at real creases
  const nm = g.getAttribute('normal');
  for (const [a, e, z] of caps) for (let i = a; i < e; i++) nm.setXYZ(i, 0, 0, z);   // the faces stay perfectly flat
  return g;
}

// opts: { round (edge rounding, ×), segments, finish: {color, roughness, clearcoat, clearcoatRoughness, envIntensity}, lid (0 closed … 1 open) }
export function buildConsole(entry, opts) {
  const round = opts.round ?? 1, segs = opts.segments ?? 6;
  const plastics = [];
  // Each plastic belongs to a part (its role): the shell, the D-pad, the buttons… The app's console colors recolor a
  // part by finding every material named "role:<part>"; parts without a role ("trim") keep their own colors.
  const plastic = (color, main, role = 'trim') => {
    const m = new THREE.MeshPhysicalMaterial({ color, metalness: 0 }); m.userData.main = main; m.name = 'role:' + role; plastics.push(m); return m;
  };
  const shell = plastic(opts.finish.color, true, 'shell');
  const M = {
    shell, plastic: (col, role) => plastic(col, false, role),
    dark: new THREE.MeshPhysicalMaterial({ color: '#111116', roughness: .8 }),
    screen: new THREE.MeshPhysicalMaterial({ color: '#0c0f14', roughness: .12, clearcoat: 1, clearcoatRoughness: .05 }),   // dark glass
    lcd: col => new THREE.MeshPhysicalMaterial({ color: col, roughness: .55 }),   // a matte, unlit LCD
    led: col => new THREE.MeshStandardMaterial({ color: col, emissive: col, emissiveIntensity: 1.6, roughness: .3 }),
    metal: new THREE.MeshPhysicalMaterial({ color: '#d9dbe0', metalness: .9, roughness: .28 }),
    // the clear, glossy cover over a handheld's screen: mostly reflection, a faint cool tint
    glass: Object.assign(new THREE.MeshPhysicalMaterial({ color: '#dfe7f2', roughness: .04, clearcoat: 1, clearcoatRoughness: .02, transparent: true, opacity: .18, depthWrite: false }), { name: 'screen-glass' }),
  };

  // ---------- shapes, in Shelf's units ----------
  const rounded = (corners, seg = 8) => outlinePoints(corners.map(([x, y, r = 0]) => [x * MM, y * MM, r * MM]), seg);
  const rrect = (w, h, r, seg) => { const R = Array.isArray(r) ? r : [r, r, r, r]; return rounded([[-w / 2, -h / 2, R[0]], [w / 2, -h / 2, R[1]], [w / 2, h / 2, R[2]], [-w / 2, h / 2, R[3]]], seg); };
  // an outline pushed out along z, centered, with rounded edges
  const solid = (pts, depth, bev = .01, steps) => slab(ccw(pts), [], -depth * MM / 2, depth * MM / 2, bev * MM * round, steps ? Math.min(steps, segs) : segs);
  // a plan outline ([x, z]) pushed up along y, centered
  const slabY = (plan, h, bev = .02) => { const g = solid(plan.map(([x, z]) => [x, -z]), h, bev); g.rotateX(-Math.PI / 2); return g; };
  const bar = (w, h, t, r = .004, bev = .003) => solid(rrect(w, h, Math.min(r, w / 2 - 1e-4, h / 2 - 1e-4), 3), t, bev, 2);
  // A body molded in two halves, as the real shells are (and as the cartridges are built): each half's outer edge rounded,
  // and where they meet a small 45° chamfer on each, so together they leave a fine V-shaped seam around the sides.
  // `split` is where they meet, from the body's centre along its depth. Returns [back or lower half, front or upper half].
  // `backPts`: the back half's own outline, when it differs (a handheld's cartridge pocket, notch below).
  const seamed = (pts, depth, bev, split, backPts = pts) => {
    const D = depth * MM, b = Math.min(bev * MM * round, D * .2), z0 = -D / 2, z1 = D / 2;
    const sz = Math.min(Math.max(split * MM, z0 + 3), z1 - 3);
    return [[cup(backPts, z0, sz, b, 2.4, segs)], [cup(pts, z1, sz, b, 2.4, segs)]];
  };
  // A handheld's cartridge pocket: the outline (in millimetres, counter-clockwise) with the part above y = yc and within
  // hw of the middle cut away, so the back half is open at the top where the cartridge goes in.
  const notch = (pts, hw, yc) => {
    hw *= MM; yc *= MM; pts = ccw(pts);
    { const d = []; pts.forEach((a, i) => { const b = pts[(i + 1) % pts.length], k = Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / 2);   // points every 2 mm, so a straight top edge has some to cut
        for (let j = 0; j < k; j++) d.push([a[0] + (b[0] - a[0]) * j / k, a[1] + (b[1] - a[1]) * j / k]); }); pts = d; }
    const n = pts.length, inN = p => Math.abs(p[0]) < hw && p[1] > yc;
    const s = pts.findIndex((p, i) => inN(p) && !inN(pts[(i - 1 + n) % n])); if (s < 0) return pts;
    let e = s; while (inN(pts[(e + 1) % n])) e = (e + 1) % n;
    const cross = (p, q) => { const X = Math.sign(p[0]) * hw, t = (X - p[0]) / (q[0] - p[0]); return [X, p[1] + (q[1] - p[1]) * t]; };   // where the top edge crosses x = ±hw
    const out = []; for (let i = (e + 1) % n; i !== s; i = (i + 1) % n) out.push(pts[i]);
    const enter = cross(pts[(s - 1 + n) % n], pts[s]), leave = cross(pts[(e + 1) % n], pts[e]);
    return [...out, enter, [enter[0], yc], [leave[0], yc], leave];
  };
  // a [radius, z] profile turned around z. Each corner gets two close neighbours, so the smoothing stays at the corner.
  const lathe = (prof, seg = 32) => {
    const p = prof.map(([r, z]) => [r * MM, z * MM]).reverse(), pts = [];
    for (let i = 0; i < p.length; i++) {
      if (i > 0 && p[i][0] === p[i - 1][0] && p[i][1] === p[i - 1][1]) continue;
      const a = p[i - 1], b = p[i], c = p[i + 1];
      if (a && c) { const f = .04; pts.push([b[0] + (a[0] - b[0]) * f, b[1] + (a[1] - b[1]) * f], b, [b[0] + (c[0] - b[0]) * f, b[1] + (c[1] - b[1]) * f]); }
      else pts.push(b);
    }
    const g = new THREE.LatheGeometry(pts.map(([r, z]) => new THREE.Vector2(Math.max(0, r), z)), seg); g.rotateX(Math.PI / 2); return g;
  };
  const G = {
    rounded, rrect, solid, slabY, bar, lathe, seamed, notch,
    plane: (w, h) => new THREE.PlaneGeometry(w * MM, h * MM),
    disc: (r, t, seg = 32) => lathe([[0, t / 2], [r, t / 2], [r, -t / 2], [0, -t / 2]], seg),
    ring: (r0, r1, t, seg = 40) => lathe([[r0, t / 2], [r1, t / 2], [r1, -t / 2], [r0, -t / 2], [r0, t / 2]], seg),
    dome: (r, t, seg = 24) => lathe([[0, t], [r * .55, t * .97], [r * .85, t * .82], [r, t * .45], [r, 0], [0, 0]], seg),
    cross: (s, w, t) => {   // one piece, so the arms don't meet in a seam
      const a = s / 2, b = w / 2, o = w * .2, i = w * .12;
      return solid(rounded([[b, a, o], [-b, a, o], [-b, b, i], [-a, b, o], [-a, -b, o], [-b, -b, i], [-b, -a, o], [b, -a, o], [b, -b, i], [a, -b, o], [a, b, o], [b, b, i]]), t, .004, 3);
    },
    tri: (s, t) => solid(rounded([[0, -s, .004], [s * .8, s * .25, .004], [-s * .8, s * .25, .004]]), t, .002, 2),   // an arrow pointing down
  };

  // ---------- placing parts ----------
  const kit = parent => {
    let half = null;   // while building a seamed body: which half its pieces belong to
    const add = (g, [x, y, z, rx = 0, ry = 0, rz = 0], m) => {
      for (const geo of [].concat(g)) { const mesh = new THREE.Mesh(geo, m); mesh.position.set(x * MM, y * MM, z * MM); mesh.rotation.set(rx, ry, rz); if (half) mesh.userData.half = half; parent.add(mesh); }
    };
    const k = {
      ...G, M,
      at: (x, y, z, rx = 0, ry = 0, rz = 0) => [x, y, z, rx, ry, rz],
      on: add,
      // the main body in two halves with a seam (see seamed): standing (a handheld: back and front) or lying flat (lower
      // and upper, the outline a plan in [x, z] as for slabY); `split` from the body's centre
      body: (pts, depth, bev, split, at, m, backPts) => { const [b, f] = seamed(pts, depth, bev, split, backPts); half = 'lower'; add(b, at, m); half = 'upper'; add(f, at, m); half = null; },
      // one hollow half lying flat, its face at yFace and open at yRim (Shelf units), for bodies built half by half
      cupY: (plan, yFace, yRim, bev, m, which) => {
        const g = cup(plan.map(([x, z]) => [x, -z]), yFace * MM, yRim * MM, bev * MM * round, 2.4, segs); g.rotateX(-Math.PI / 2);
        half = which; add(g, [0, 0, 0], m); half = null;
      },
      // parts that belong to one half whatever their position (a pocket's floor)
      half: (which, build) => { half = which; build(); half = null; },
      bodyY: (plan, h, bev, split, at, m) => {
        const [b, f] = seamed(plan.map(([x, z]) => [x, -z]), h, bev, split); [...b, ...f].forEach(g => g.rotateX(-Math.PI / 2));
        half = 'lower'; add(b, at, m); half = 'upper'; add(f, at, m); half = null;
      },
      face: (g, x, y, z, m, rz = 0) => add(g, [x, y, z, 0, 0, rz], m),                 // on a front face
      up: (g, x, y, z, m) => add(g, [x, y, z, -Math.PI / 2, 0, 0], m),                  // on a top face (local +y points to the back)
      dpad: (x, y, z, s, m) => { k.face(G.disc(s * .62, .004), x, y, z + .001, M.plastic(shade(m.color, .15))); k.face(G.cross(s, s * .33, .035), x, y, z + .016, m); },
      grille: (x, y, z, cols, rows, gap) => { for (let i = 0; i < cols; i++) for (let j = 0; j < rows; j++) k.face(G.disc(.011, .002), x + (i - (cols - 1) / 2) * gap, y + (j - (rows - 1) / 2) * gap, z + .0005, M.dark); },
      // A screen set into its bezel, as on the real handhelds: the bezel (an outline around its own origin) with a window
      // cut through it, the screen down in the window, and a clear cover over the window, level with the bezel's top.
      // `at` places the bezel's origin (its back face); the screen is at (sx, sy) from that origin.
      window: (bezel, at, depth, sw, sh, sx, sy, bezelMat, screenMat) => {
        const hole = rrect(sw + .01, sh + .01, .008, 6).map(([x, y]) => [x + sx * MM, y + sy * MM]);
        add(slab(ccw(bezel), [ccw(hole)], 0, depth * MM, .0015 * MM * round, 2), at, bezelMat);
        add(G.plane(sw + .012, sh + .012).translate(sx * MM, sy * MM, .0015 * MM), at, screenMat);   // down in the window
        add(G.plane(sw + .01, sh + .01).translate(sx * MM, sy * MM, (depth - .0002) * MM), at, M.glass);
      },
      // a part on a hinge: built in its own space, turned about x at the hinge
      lid: ({ hinge, closed, open }, build) => {
        const pivot = new THREE.Group(); pivot.name = 'lid'; pivot.position.set(hinge[0] * MM, hinge[1] * MM, hinge[2] * MM);
        pivot.userData.lid = { closed, open }; parent.add(pivot); build(kit(pivot)); return pivot;
      },
    };
    return k;
  };

  const group = new THREE.Group(); group.name = entry.name;
  entry.build(kit(group));
  group.traverse(o => { if (o.isMesh) o.name = o.material === shell ? 'shell' : 'part'; });
  group.userData.materials = { shell, plastics };
  group.userData.lid = group.getObjectByName('lid') || null;
  setLid(group, opts.lid ?? entry.lid?.rest ?? 0);
  return group;
}

/**
 * For the export: every part goes into one of two groups, "lower" and "upper" (a handheld's back and front), on its side
 * of the seam (entry.seam: the axis and where along it, in Shelf units), unless it was built as one of the halves.
 * The app opens the upper half on a hinge to show the board (the Museum's Inside view). The lid keeps its pose.
 */
export function splitHalves(group, seam) {
  group.updateMatrixWorld(true);
  const lower = new THREE.Group(), upper = new THREE.Group(); lower.name = 'lower'; upper.name = 'upper';
  const meshes = []; group.traverse(o => { if (o.isMesh) meshes.push(o); });
  for (const m of meshes) {
    let h = m.userData.half;
    if (!h) { const c = new THREE.Box3().setFromObject(m).getCenter(new THREE.Vector3()); h = c[seam.axis] >= seam.at * MM ? 'upper' : 'lower'; }
    (h === 'upper' ? upper : lower).attach(m);
  }
  const out = new THREE.Group(); out.name = group.name; out.add(lower, upper); out.userData = group.userData; return out;
}

export function setLid(group, v) {
  const p = group.userData.lid; if (!p) return;
  const { closed, open } = p.userData.lid; p.rotation.x = closed + (open - closed) * v;
}

// a lighter (k > 0) or darker (k < 0) version of a color
function shade(color, k) {
  const c = new THREE.Color(color), t = k < 0 ? new THREE.Color(0, 0, 0) : new THREE.Color(1, 1, 1);
  return c.lerp(t, Math.abs(k));
}
