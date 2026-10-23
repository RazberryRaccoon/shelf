// Builds a cartridge from a style in cartridges.js: a beveled, extruded body in physically based plastic, a label
// recess with rounded edges, a matte label set into it, and beveled grip ridges. Units are millimetres.
import * as THREE from 'three';

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
function slab(pts, holes, z0, z1, bevel, segments) {
  const t = Math.max(0, Math.min(bevel, (z1 - z0) / 2 - .01));
  const shape = toShape(t > 0 ? offset(pts, -t) : pts);
  for (const h of holes) shape.holes.push(toPath(t > 0 ? offset(h, t) : h));
  const g = new THREE.ExtrudeGeometry(shape, {
    depth: Math.max(.01, z1 - z0 - 2 * t), bevelEnabled: t > 0, bevelThickness: t, bevelSize: t, bevelOffset: 0, bevelSegments: segments, curveSegments: 12, steps: 1,
  });
  g.translate(0, 0, z0 + t);
  return g;
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
  const { bevel, segments } = opts, D = style.depth, front = D / 2, back = -D / 2;
  const body = outlinePoints(style.outline);
  const [lx, ly, lw, lh, lr, recess] = style.label;
  const hole = rrect(lx, ly, lw, lh, lr);
  const b = Math.min(bevel, D * .2);
  const plastic = plasticMaterial(opts.finish);
  const group = new THREE.Group(); group.name = style.name;

  // the body, up to the bottom of the label recess…
  const core = new THREE.Mesh(slab(body, [], back, front - recess, b, segments), plastic); core.name = 'body';
  // …and a front plate with the recess cut through it. Its back edge is buried inside the body, so no seam shows;
  // the hole's own bevel rounds the edge of the recess.
  const overlap = 2 * Math.min(b, recess) + .3;
  const plate = new THREE.Mesh(slab(body, [hole], front - recess - overlap, front, Math.min(b, (recess + overlap) / 2 - .02), segments), plastic); plate.name = 'front';
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
  group.userData.materials = { plastic, label: label.material };
  return group;
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
