// Builds a console from its entry in consoles.js, in the same finish as the cartridges: beveled extrusions in physically
// based plastic, glass screens, lit LEDs. The entries are written in Shelf's own units (1 = 10 cm, the units of its
// code-built consoles); everything here comes out in millimetres. Front faces +z, the top faces +y.
import * as THREE from 'three';
import { outlinePoints, slab } from './cartridge.js';

const MM = 100;
const area = p => p.reduce((s, a, i) => { const b = p[(i + 1) % p.length]; return s + a[0] * b[1] - b[0] * a[1]; }, 0) / 2;
const ccw = p => area(p) < 0 ? p.slice().reverse() : p;

// opts: { round (edge rounding, ×), segments, finish: {color, roughness, clearcoat, clearcoatRoughness, envIntensity}, lid (0 closed … 1 open) }
export function buildConsole(entry, opts) {
  const round = opts.round ?? 1, segs = opts.segments ?? 6;
  const plastics = [];
  const plastic = (color, main) => {
    const m = new THREE.MeshPhysicalMaterial({ color, metalness: 0 }); m.userData.main = main; plastics.push(m); return m;
  };
  const shell = plastic(opts.finish.color, true);
  const M = {
    shell, plastic: col => plastic(col, false),
    dark: new THREE.MeshPhysicalMaterial({ color: '#111116', roughness: .8 }),
    screen: new THREE.MeshPhysicalMaterial({ color: '#0c0f14', roughness: .12, clearcoat: 1, clearcoatRoughness: .05 }),   // dark glass
    lcd: col => new THREE.MeshPhysicalMaterial({ color: col, roughness: .55 }),   // a matte, unlit LCD
    led: col => new THREE.MeshStandardMaterial({ color: col, emissive: col, emissiveIntensity: 1.6, roughness: .3 }),
    metal: new THREE.MeshPhysicalMaterial({ color: '#d9dbe0', metalness: .9, roughness: .28 }),
  };

  // ---------- shapes, in Shelf's units ----------
  const rounded = (corners, seg = 8) => outlinePoints(corners.map(([x, y, r = 0]) => [x * MM, y * MM, r * MM]), seg);
  const rrect = (w, h, r, seg) => { const R = Array.isArray(r) ? r : [r, r, r, r]; return rounded([[-w / 2, -h / 2, R[0]], [w / 2, -h / 2, R[1]], [w / 2, h / 2, R[2]], [-w / 2, h / 2, R[3]]], seg); };
  // an outline pushed out along z, centered, with rounded edges
  const solid = (pts, depth, bev = .01, steps) => slab(ccw(pts), [], -depth * MM / 2, depth * MM / 2, bev * MM * round, steps ? Math.min(steps, segs) : segs);
  // a plan outline ([x, z]) pushed up along y, centered
  const slabY = (plan, h, bev = .02) => { const g = solid(plan.map(([x, z]) => [x, -z]), h, bev); g.rotateX(-Math.PI / 2); return g; };
  const bar = (w, h, t, r = .004, bev = .003) => solid(rrect(w, h, Math.min(r, w / 2 - 1e-4, h / 2 - 1e-4), 3), t, bev, 2);
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
    rounded, rrect, solid, slabY, bar, lathe,
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
    const add = (g, [x, y, z, rx = 0, ry = 0, rz = 0], m) => {
      for (const geo of [].concat(g)) { const mesh = new THREE.Mesh(geo, m); mesh.position.set(x * MM, y * MM, z * MM); mesh.rotation.set(rx, ry, rz); parent.add(mesh); }
    };
    const k = {
      ...G, M,
      at: (x, y, z, rx = 0, ry = 0, rz = 0) => [x, y, z, rx, ry, rz],
      on: add,
      face: (g, x, y, z, m, rz = 0) => add(g, [x, y, z, 0, 0, rz], m),                 // on a front face
      up: (g, x, y, z, m) => add(g, [x, y, z, -Math.PI / 2, 0, 0], m),                  // on a top face (local +y points to the back)
      dpad: (x, y, z, s, m) => { k.face(G.disc(s * .62, .004), x, y, z + .001, M.plastic(shade(m.color, .15))); k.face(G.cross(s, s * .33, .035), x, y, z + .016, m); },
      grille: (x, y, z, cols, rows, gap) => { for (let i = 0; i < cols; i++) for (let j = 0; j < rows; j++) k.face(G.disc(.011, .002), x + (i - (cols - 1) / 2) * gap, y + (j - (rows - 1) / 2) * gap, z + .0005, M.dark); },
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

export function setLid(group, v) {
  const p = group.userData.lid; if (!p) return;
  const { closed, open } = p.userData.lid; p.rotation.x = closed + (open - closed) * v;
}

// a lighter (k > 0) or darker (k < 0) version of a color
function shade(color, k) {
  const c = new THREE.Color(color), t = k < 0 ? new THREE.Color(0, 0, 0) : new THREE.Color(1, 1, 1);
  return c.lerp(t, Math.abs(k));
}
