/* ================= models: cartridges, cases and consoles ================= */
// Silhouettes and sizes follow the real objects (1 unit = 100 mm). Surface details are Shelf's own, stylized, and there are no logos.

/* ---------- geometry tools: built as plain arrays, so parts that share a material merge into one mesh ---------- */
const IDENT = M4.trs(0, 0, 0, 0, 0, 0, 1);
const geo = () => ({ p: [], n: [], t: [], i: [] });
function geoAppend(dst, src, M) {
  const base = dst.p.length / 3;
  for (let k = 0; k < src.p.length; k += 3) {
    const q = M ? apply(M, [src.p[k], src.p[k + 1], src.p[k + 2]]) : [src.p[k], src.p[k + 1], src.p[k + 2]];
    let nx = src.n[k], ny = src.n[k + 1], nz = src.n[k + 2];
    if (M) { const x = M[0] * nx + M[4] * ny + M[8] * nz, y = M[1] * nx + M[5] * ny + M[9] * nz, z = M[2] * nx + M[6] * ny + M[10] * nz, l = Math.hypot(x, y, z) || 1; nx = x / l; ny = y / l; nz = z / l; }
    dst.p.push(q[0], q[1], q[2]); dst.n.push(nx, ny, nz);
  }
  for (const v of src.t) dst.t.push(v);
  for (const v of src.i) dst.i.push(v + base);
  return dst;
}
const upload = g => mesh(g.p, g.n, g.t, g.i);
const planeGeo = (w, h) => ({ p: [-w / 2, -h / 2, 0, w / 2, -h / 2, 0, w / 2, h / 2, 0, -w / 2, h / 2, 0], n: [0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1], t: [0, 0, 1, 0, 1, 1, 0, 1], i: [0, 1, 2, 0, 2, 3] });

const dedupe = pts => pts.filter((p, i) => { const q = pts[(i + pts.length - 1) % pts.length]; return Math.hypot(p[0] - q[0], p[1] - q[1]) > 1e-5; });
function polyArea(p) { let a = 0; for (let i = 0; i < p.length; i++) { const q = p[(i + 1) % p.length]; a += p[i][0] * q[1] - q[0] * p[i][1]; } return a / 2; }
const ccw = p => polyArea(p) < 0 ? p.slice().reverse() : p;
function earcut(p) {   // ear clipping, for the simple counter-clockwise outlines used here
  const idx = p.map((_, i) => i), tris = [], cross = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  while (idx.length > 3) {
    let cut = false;
    for (let k = 0; k < idx.length && !cut; k++) {
      const i0 = idx[(k + idx.length - 1) % idx.length], i1 = idx[k], i2 = idx[(k + 1) % idx.length], a = p[i0], b = p[i1], c = p[i2];
      if (cross(a, b, c) <= 1e-12) continue;
      if (idx.some(j => j !== i0 && j !== i1 && j !== i2 && cross(a, b, p[j]) > 1e-12 && cross(b, c, p[j]) > 1e-12 && cross(c, a, p[j]) > 1e-12)) continue;
      tris.push(i0, i1, i2); idx.splice(k, 1); cut = true;
    }
    if (!cut) { for (let k = 1; k < idx.length - 1; k++) tris.push(idx[0], idx[k], idx[k + 1]); return tris; }
  }
  tris.push(idx[0], idx[1], idx[2]); return tris;
}
// corners: [x, y, r]; r rounds that corner (0 keeps it sharp)
function rounded(corners, seg = 6) {
  const out = [], n = corners.length;
  for (let i = 0; i < n; i++) {
    const A = corners[(i + n - 1) % n], B = corners[i], C = corners[(i + 1) % n], r = B[2] || 0;
    let ux = A[0] - B[0], uy = A[1] - B[1], vx = C[0] - B[0], vy = C[1] - B[1];
    const lu = Math.hypot(ux, uy), lv = Math.hypot(vx, vy); ux /= lu; uy /= lu; vx /= lv; vy /= lv;
    const th = Math.acos(clamp(ux * vx + uy * vy, -1, 1));
    if (r <= 0 || th < 1e-3 || th > Math.PI - 1e-3) { out.push([B[0], B[1]]); continue; }
    const t = Math.min(r / Math.tan(th / 2), lu * .5, lv * .5), rr = t * Math.tan(th / 2);
    const bx = ux + vx, by = uy + vy, bl = Math.hypot(bx, by), dc = rr / Math.sin(th / 2), cx = B[0] + bx / bl * dc, cy = B[1] + by / bl * dc;
    const a0 = Math.atan2(B[1] + uy * t - cy, B[0] + ux * t - cx); let da = Math.atan2(B[1] + vy * t - cy, B[0] + vx * t - cx) - a0;
    while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
    const k = Math.max(2, Math.ceil(seg * Math.abs(da) / (Math.PI / 2)));
    for (let s = 0; s <= k; s++) { const a = a0 + da * s / k; out.push([cx + Math.cos(a) * rr, cy + Math.sin(a) * rr]); }
  }
  return dedupe(out);
}
const rrect = (w, h, r, seg) => { const R = Array.isArray(r) ? r : [r, r, r, r]; return rounded([[-w / 2, -h / 2, R[0]], [w / 2, -h / 2, R[1]], [w / 2, h / 2, R[2]], [-w / 2, h / 2, R[3]]], seg); };
const arcPts = (cx, cy, r, a0, a1, n) => Array.from({ length: n + 1 }, (_, k) => { const a = a0 + (a1 - a0) * k / n; return [cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0]; });

// An outline pushed out to a depth along z, with rounded front and back edges (the bevel) and smooth sides.
function solidGeo(outline, depth, bev = .01, steps = 3) {
  const pts = ccw(dedupe(outline)), n = pts.length, h = depth / 2, G = geo();
  const [bf, bb] = (Array.isArray(bev) ? bev : [bev, bev]).map(v => Math.min(v, h * .95));   // the front and back bevels can differ
  const en = pts.map((p, i) => { const q = pts[(i + 1) % n], dx = q[0] - p[0], dy = q[1] - p[1], l = Math.hypot(dx, dy) || 1; return [dy / l, -dx / l]; });
  const vn = i => { const a = en[(i - 1 + n) % n], c = en[i], x = a[0] + c[0], y = a[1] + c[1], l = Math.hypot(x, y) || 1; return [x / l, y / l]; };
  const sharp = i => { const a = en[(i - 1 + n) % n], c = en[i]; return a[0] * c[0] + a[1] * c[1] < .6; };
  const MI = pts.map((_, i) => { const v = vn(i), k = Math.max(.35, v[0] * en[i][0] + v[1] * en[i][1]); return [v[0] / k, v[1] / k]; });
  const at = (i, inset) => [pts[i][0] - MI[i][0] * inset, pts[i][1] - MI[i][1] * inset];
  const prof = [];   // [inset, z, horizontal part of the normal, z part of the normal]
  for (let s = 0; s <= steps; s++) { const a = s / steps * Math.PI / 2; prof.push([bf * (1 - Math.sin(a)), h - bf * (1 - Math.cos(a)), Math.sin(a), Math.cos(a)]); }
  for (let s = 0; s <= steps; s++) { const a = s / steps * Math.PI / 2; prof.push([bb * (1 - Math.cos(a)), -h + bb * (1 - Math.sin(a)), Math.cos(a), -Math.sin(a)]); }
  const per = [0]; for (let i = 1; i <= n; i++) per.push(per[i - 1] + Math.hypot(pts[i % n][0] - pts[i - 1][0], pts[i % n][1] - pts[i - 1][1]));
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n, nI = sharp(i) ? en[i] : vn(i), nJ = sharp(j) ? en[i] : vn(j);
    for (let s = 0; s < prof.length - 1; s++) {
      const base = G.p.length / 3;
      [[i, prof[s], nI, per[i]], [i, prof[s + 1], nI, per[i]], [j, prof[s + 1], nJ, per[i + 1]], [j, prof[s], nJ, per[i + 1]]].forEach(([k, P, nn, u]) => {
        const q = at(k, P[0]); G.p.push(q[0], q[1], P[1]); G.n.push(nn[0] * P[2], nn[1] * P[2], P[3]); G.t.push(u / per[n], (h - P[1]) / depth); });
      G.i.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  let minx = 1e9, maxx = -1e9, miny = 1e9, maxy = -1e9; pts.forEach(([x, y]) => { minx = Math.min(minx, x); maxx = Math.max(maxx, x); miny = Math.min(miny, y); maxy = Math.max(maxy, y); });
  const tris = earcut(pts.map((_, i) => at(i, bf)));
  [[h, 1, bf], [-h, -1, bb]].forEach(([z, nz, b]) => {
    const base = G.p.length / 3, inner = pts.map((_, i) => at(i, b));
    inner.forEach(([x, y]) => { G.p.push(x, y, z); G.n.push(0, 0, nz); G.t.push((x - minx) / (maxx - minx), (y - miny) / (maxy - miny)); });
    for (let k = 0; k < tris.length; k += 3) nz > 0 ? G.i.push(base + tris[k], base + tris[k + 1], base + tris[k + 2]) : G.i.push(base + tris[k], base + tris[k + 2], base + tris[k + 1]);
  });
  return G;
}
// A profile of [radius, z] points turned around the z axis: buttons, screws, discs and knobs.
function latheGeo(prof, seg = 32) {
  const G = geo();
  for (let k = 0; k < prof.length - 1; k++) {
    const [r0, z0] = prof[k], [r1, z1] = prof[k + 1], l = Math.hypot(r1 - r0, z1 - z0) || 1, nr = -(z1 - z0) / l, nz = (r1 - r0) / l;
    for (let s = 0; s < seg; s++) {
      const a = s / seg * Math.PI * 2, b2 = (s + 1) / seg * Math.PI * 2, base = G.p.length / 3;
      const v = [[r0, z0, a], [r1, z1, a], [r1, z1, b2], [r0, z0, b2]];
      v.forEach(([r, z, t]) => { G.p.push(r * Math.cos(t), r * Math.sin(t), z); G.n.push(nr * Math.cos(t), nr * Math.sin(t), nz); G.t.push(s / seg, k / (prof.length - 1)); });
      const pa = [r0 * Math.cos(a), r0 * Math.sin(a), z0], pb = [r1 * Math.cos(a), r1 * Math.sin(a), z1], pc = [r1 * Math.cos(b2), r1 * Math.sin(b2), z1];
      const e1 = pb.map((x, i) => x - pa[i]), e2 = (r0 + r1 > 0 ? pc : [r0 * Math.cos(b2), r0 * Math.sin(b2), z0]).map((x, i) => x - pa[i]);
      const cr = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]], mid = (a + b2) / 2;
      const flip = cr[0] * nr * Math.cos(mid) + cr[1] * nr * Math.sin(mid) + cr[2] * nz < 0;
      flip ? G.i.push(base, base + 2, base + 1, base, base + 3, base + 2) : G.i.push(base, base + 1, base + 2, base, base + 2, base + 3);
    }
  }
  return G;
}
// a flat frame between two centered outlines (an outer and an inner rounded shape)
function frameGeo(outer, inner, t) {
  let O = ccw(outer); const IN = ccw(inner), G = geo(), h = t / 2;
  if (IN.length !== O.length) {   // spread points along the outer edges too, so a round inner outline stays round
    const D = []; O.forEach((p, i) => { const q = O[(i + 1) % O.length], k = Math.max(1, Math.ceil(Math.hypot(q[0] - p[0], q[1] - p[1]) / .03)); for (let j = 0; j < k; j++) D.push([lerp(p[0], q[0], j / k), lerp(p[1], q[1], j / k)]); });
    O = D;
  }
  const n = O.length;
  const hit = (dx, dy) => {   // where a ray from the middle crosses the inner outline (both outlines are centered and convex)
    let best = 0; for (let i = 0; i < IN.length; i++) { const p = IN[i], q = IN[(i + 1) % IN.length], ex = q[0] - p[0], ey = q[1] - p[1], den = dx * ey - dy * ex;
      if (Math.abs(den) < 1e-12) continue; const u = (p[0] * ey - p[1] * ex) / den, v = (p[0] * dy - p[1] * dx) / den; if (u > 0 && v >= -1e-9 && v <= 1 + 1e-9) best = u; }
    return [dx * best, dy * best]; };
  const I = IN.length === n ? IN : O.map(([x, y]) => hit(x, y));   // match the inner outline point for point
  const quad = (a, b, c, d, nn) => { const base = G.p.length / 3; [a, b, c, d].forEach((p, k) => { G.p.push(...p); G.n.push(...(Array.isArray(nn[0]) ? nn[k] : nn)); G.t.push(0, 0); }); G.i.push(base, base + 1, base + 2, base, base + 2, base + 3); };
  const en = (P, i) => { const q = P[(i + 1) % n], dx = q[0] - P[i][0], dy = q[1] - P[i][1], l = Math.hypot(dx, dy) || 1; return [dy / l, -dx / l, 0]; };
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n, o0 = O[i], o1 = O[j], i0 = I[i], i1 = I[j];
    quad([o0[0], o0[1], h], [o1[0], o1[1], h], [i1[0], i1[1], h], [i0[0], i0[1], h], [0, 0, 1]);
    quad([o0[0], o0[1], -h], [i0[0], i0[1], -h], [i1[0], i1[1], -h], [o1[0], o1[1], -h], [0, 0, -1]);
    quad([o0[0], o0[1], h], [o0[0], o0[1], -h], [o1[0], o1[1], -h], [o1[0], o1[1], h], en(O, i));
    const ni = en(I, i).map(v => -v);
    quad([i0[0], i0[1], h], [i1[0], i1[1], h], [i1[0], i1[1], -h], [i0[0], i0[1], -h], ni);
  }
  return G;
}
const barG = (w, h, t, r = .004, bev = .003) => solidGeo(rrect(w, h, Math.min(r, w / 2 - 1e-4, h / 2 - 1e-4), 3), t, bev, 2);
const discG = (r, t, seg = 32) => latheGeo([[0, t / 2], [r, t / 2], [r, -t / 2], [0, -t / 2]], seg);
const ringG = (r0, r1, t, seg = 40) => latheGeo([[r0, t / 2], [r1, t / 2], [r1, -t / 2], [r0, -t / 2], [r0, t / 2]], seg);
const domeG = (r, t, seg = 24) => latheGeo([[0, t], [r * .55, t * .97], [r * .85, t * .82], [r, t * .45], [r, 0], [0, 0]], seg);
const crossG = (s, w, t) => geoAppend(geoAppend(geo(), barG(s, w, t, w * .2)), barG(w, s, t, w * .2));
// a slab lying flat: outline seen from above as [x, z toward the front], extruded upward and centered on y = 0
const slabG = (outline, h, bev = .02, steps = 3) => geoAppend(geo(), solidGeo(outline.map(([x, z]) => [x, -z]), h, bev, steps), M4.trs(0, 0, 0, -Math.PI / 2, 0, 0, 1));
const triG = (s, t) => solidGeo(rounded([[0, -s, .004], [s * .8, s * .25, .004], [-s * .8, s * .25, .004]]), t, .002, 2);   // an arrow pointing down
function shadeHex(hex, k) { const c = hexRgb(hex); return '#' + c.map(v => Math.round(clamp(k < 0 ? v * (1 + k) : v + (1 - v) * k, 0, 1) * 255).toString(16).padStart(2, '0')).join(''); }

function Builder() {
  const groups = [], extra = [], lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
  return {
    add(src, M, mat) {
      const key = JSON.stringify(mat); let g = groups.find(x => x.key === key && x.g.p.length < 140000);
      if (!g) groups.push(g = { key, mat, g: geo() });
      const from = g.g.p.length; geoAppend(g.g, src, M);
      for (let k = from; k < g.g.p.length; k += 3) for (let a = 0; a < 3; a++) { lo[a] = Math.min(lo[a], g.g.p[k + a]); hi[a] = Math.max(hi[a], g.g.p[k + a]); }
      return this;
    },
    part(m, M, mat) { extra.push(Object.assign({ mesh: m, m: M }, mat)); return this; },
    build(info = {}) {
      const parts = groups.map(({ mat, g }) => Object.assign({ mesh: upload(g), m: IDENT }, mat)).concat(extra);
      parts.forEach(p => { if (typeof p.col === 'string') p.col = hexRgb(p.col); });
      parts.sort((a, b) => (a.glass ? 1 : 0) - (b.glass ? 1 : 0));
      return Object.assign({ parts, lo, hi }, info);
    },
  };
}
// st: the usual pose (alpha, bright, glow, s); x.roles maps role names to colors, x.tex gives label textures; pass is 'opaque' or 'glass' to draw half
function drawParts(model, M, st, x = {}, pass) {
  if (NODRAW) return;
  const detail = st.detail != null ? st.detail : clamp(((st.s || 0) * PX - 160) / 260, 0, 1);
  for (const p of model.parts) {
    if (pass && (pass === 'glass') !== !!p.glass) continue;
    const col = p.role ? x.roles[p.role] : p.col;
    let emit = st.glow ? RIM.map(v => v * st.glow * .06) : null;
    if (p.led) emit = col.map(v => v * (st.power == null ? 1 : st.power) * .9);
    if (p.screen && st.screenOn) emit = [.16, .2, .28].map(v => v * st.screenOn);   // screens light up when the power comes on
    drawPart(p.mesh, p.m === IDENT ? M : M4.mul(M, p.m), { col: p.led ? col.map(v => v * .45) : col, spec: p.spec, grain: (p.grain || 0) * detail, gloss: p.gloss || 0,
      alpha: (st.alpha == null ? 1 : st.alpha) * (p.glass || 1), bright: st.bright, tex: p.label && x.tex ? x.tex(p.label) : null, emit });
  }
}
const shellM = (col, o) => Object.assign({ col, spec: .3, grain: 1 }, o);
const DARK = { col: '#111116', spec: .05 }, GOLD = { col: '#d6b25a', spec: .9 }, PCB = { col: '#2c6a43', spec: .25 };

/* ---------- media: cartridges, discs and cards ---------- */
const CART = {
  nes:     { w: 1.20, h: 1.33, d: .17, label: [0, .175, .55, .90], col: '#8f8b86', fold: .07 },
  snes:    { w: 1.22, h: 1.00, d: .20, label: [0, .22, .825, .37], col: '#a8a6b4', fold: .07, foldW: .58 },
  n64:     { w: 1.16, h: .80, d: .18, label: [0, .02, .545, .625], col: '#5a5d66' },
  genesis: { w: 1.00, h: 1.30, d: .18, label: [0, .25, .74, .60], col: '#1d1e23', fold: .07 },
  gb:      { w: .57, h: .65, d: .08, label: [0, -.035, .42, .37], col: '#bdbcc4' },
  gbc:     { w: .57, h: .65, d: .08, label: [0, -.035, .42, .37], col: '#34c3b7' },
  gba:     { w: .57, h: .345, d: .08, label: [0, -.01, .43, .22], col: '#4a4956' },
  ds:      { w: .35, h: .33, d: .038, label: [0, -.03, .28, .2], col: '#3b3d47' },
  android: { w: .42, h: .30, d: .035, label: [0, -.02, .34, .2], col: '#262a3a' },
  ps1:     { w: 1.2, h: 1.2, d: .012, disc: true, r: .6 },
  psp:     { w: .65, h: .645, d: .042, umd: true, r: .3 },
};
function cartOutline(sys) {
  const c = CART[sys], w = c.w / 2, h = c.h / 2;
  switch (sys) {
    case 'nes': return rrect(c.w, c.h, .02);
    case 'snes': return rrect(c.w, c.h, [.03, .03, .3, .3], 8);   // the rounded top of the Japanese and European cartridge
    case 'n64': {   // an arched top, straight sides with a notch near the bottom
      const R = (w * w + .15 * .15) / .3, cy = h - R, a = Math.atan2(.25 - cy, w);
      return rounded([[-w, -h, .025], [w, -h, .025], [w, -.30, 0], [w - .035, -.30, .006], [w - .035, -.22, .006], [w, -.22, 0], [w, .25, .07],
        ...arcPts(0, cy, R, a, Math.PI - a, 24).slice(1, -1), [-w, .25, .07], [-w, -.22, 0], [-w + .035, -.22, .006], [-w + .035, -.30, .006], [-w, -.30, 0]]);
    }
    case 'genesis': return rounded([[-.42, -h, .02], [.42, -h, .02], [.42, -.40, .015], [w, -.32, .015], [w, h, .06], [-w, h, .06], [-w, -.32, .015], [-.42, -.40, .015]]);
    case 'gb': return rounded([[-w, -h, .012], [w, -h, .012], [w, h - .06, .006], [w - .06, h, .006], [-w, h, .025]]);   // the notched corner
    case 'gbc': return rrect(c.w, c.h, [.012, .012, .03, .03]);
    case 'gba': return rounded([[-w, -h, .01], [w, -h, .01], [w, h, .05], [.12, h, .02], [.07, h + .016, .02], [-.07, h + .016, .02], [-.12, h, .02], [-w, h, .05]]);
    case 'ds': return rounded([[-w, -h, .01], [w, -h, .01], [w, h - .03, .004], [w - .03, h, .004], [-w, h, .012]]);
    case 'psp': return rounded([[-w, -.32, .03], [w, -.32, .03], [w, 0, 0], ...arcPts(0, 0, w, 0, Math.PI, 28).slice(1, -1), [-w, 0, 0]]);
    case 'ps1': return circle(c.r, 72);
    default: return rrect(c.w, c.h, .03);
  }
}
const cartMeshes = {};
function cartModel(sys) {
  if (cartMeshes[sys]) return cartMeshes[sys];
  const c = CART[sys], B = Builder(), out = cartOutline(sys), f = c.d / 2, h = c.h / 2;
  const S0 = shellM(c.col || '#888'), dk = shellM(shadeHex(c.col || '#888', -.28), { spec: .15 });
  const front = (g, x, y, t, m = S0, rz = 0) => B.add(g, M4.trs(x, y, f + t / 2 - .001, 0, 0, rz, 1), m);
  const back = (g, x, y, t, m = S0) => B.add(g, M4.trs(x, y, -f - t / 2 + .001, 0, Math.PI, 0, 1), m);
  const frontP = (g, x, y, lift, m) => B.add(g, M4.trs(x, y, f + lift, 0, 0, 0, 1), m), backP = (g, x, y, lift, m) => B.add(g, M4.trs(x, y, -f - lift, 0, Math.PI, 0, 1), m);
  const ridgesV = (n, x0, dx, y, len, w, t) => { for (let i = 0; i < n; i++) for (const sx of [-1, 1]) front(barG(w, len, t, w / 2), sx * (x0 + i * dx), y, t); };
  const ridgesH = (n, y0, dy, len, w, t, x = 0, side = front) => { for (let i = 0; i < n; i++) side(barG(len, w, t, w / 2), x, y0 + i * dy, t); };
  const screw = (x, y, r = .028) => { backP(discG(r * 1.45, .002), x, y, 0, dk); back(domeG(r, .007, 18), x, y, .0, { col: '#a3a5ad', spec: .9 });
    B.add(barG(r * 1.4, r * .3, .004, .001, .001), M4.trs(x, y, -f - .0068, 0, 0, Math.PI / 4, 1), DARK); };
  const lip = (t = .006, wd = .02) => { const [lx, ly, lw, lh] = c.label;   // a raised lip around the label, so it sits in a recess
    front(barG(lw + wd * 2, wd, t), lx, ly + lh / 2 + wd / 2, t); front(barG(lw + wd * 2, wd, t), lx, ly - lh / 2 - wd / 2, t);
    front(barG(wd, lh, t), lx - lw / 2 - wd / 2, ly, t); front(barG(wd, lh, t), lx + lw / 2 + wd / 2, ly, t); };
  const connector = (w, n) => {   // the opening along the bottom edge, with the board and its gold contacts inside
    B.add(planeGeo(w, c.d * .62), M4.trs(0, -h - .0008, 0, Math.PI / 2, 0, 0, 1), DARK);
    B.add(planeGeo(w * .94, c.d * .16), M4.trs(0, -h - .0016, 0, Math.PI / 2, 0, 0, 1), PCB);
    for (let i = 0; i < n; i++) B.add(planeGeo(w * .94 / n * .6, c.d * .15), M4.trs(-w * .47 + (i + .5) * w * .94 / n, -h - .0024, 0, Math.PI / 2, 0, 0, 1), GOLD); };
  const label = (z = f + .0025) => { const [lx, ly, lw, lh] = c.label; B.part(PLANE, M4.trs(lx, ly, z, 0, 0, 0, [lw, lh, 1]), { col: '#ffffff', spec: .22, label: true });
    if (c.fold) B.part(PLANE, M4.trs(lx, h + .0012, f - c.fold / 2 - .004, -Math.PI / 2, 0, 0, [Math.min(lw, c.foldW || lw), c.fold, 1]), { col: '#ffffff', spec: .22, label: 'fold' }); };   // the label wraps over the top edge

  if (c.disc) {   // a compact disc with a black underside: printed on top, clear hub in the middle
    B.add(ringG(.075, c.r, c.d, 96), IDENT, { col: '#131318', spec: 1, gloss: .7 });
    B.add(ringG(.076, .19, c.d + .001, 64), IDENT, { col: '#d4d8e2', spec: .9, gloss: .6, glass: .55 });
    B.part(PLANE, M4.trs(0, 0, f + .0012, 0, 0, 0, [c.r * 2 - .01, c.r * 2 - .01, 1]), { col: '#ffffff', spec: .4, label: 'disc' });
    return (cartMeshes[sys] = B.build({ c, ring: ringMesh(circle(c.r, 72)) }));
  }
  if (c.umd) {    // a small disc in a clear caddy with a rounded top
    B.add(ringG(.03, c.r, .006, 64), M4.trs(0, 0, .002, 0, 0, 0, 1), { col: '#d8dce6', spec: 1 });
    B.part(PLANE, M4.trs(0, 0, .0055, 0, 0, 0, [c.r * 2 - .01, c.r * 2 - .01, 1]), { col: '#ffffff', spec: .4, label: 'disc' });
    B.add(barG(.6, .09, .004), M4.trs(0, -.262, f + .001, 0, 0, 0, 1), { col: '#1d2a3b', spec: .4 });
    B.add(triG(.022, .003), M4.trs(.24, -.262, f + .004, 0, 0, Math.PI, 1), { col: '#c9d4e2', spec: .5 });
    B.add(barG(.34, .46, .003, .02), M4.trs(0, -.03, -f - .001, 0, Math.PI, 0, 1), { col: '#a9b1bb', spec: 1, gloss: .5 });
    B.add(solidGeo(out, c.d, .008, 3), IDENT, { col: '#c8d6e4', spec: .9, gloss: .7, glass: .42 });
    return (cartMeshes[sys] = B.build({ c, ring: ringMesh(out) }));
  }
  const glass = sys === 'gbc';
  if (glass) {   // a see-through shell: the board and its chips show inside
    B.add(barG(.47, .52, .012, .01), M4.trs(0, -.02, -.006, 0, 0, 0, 1), PCB);
    [[-.09, .07, .15, .11], [.11, .07, .1, .1], [.0, -.15, .2, .06]].forEach(([x, y, w, hh]) => B.add(barG(w, hh, .012, .006), M4.trs(x, y, .006, 0, 0, 0, 1), { col: '#16171b', spec: .5 }));
  }
  B.add(solidGeo(out, c.d, Math.min(.016, c.d * .2), 3), IDENT, glass ? { col: c.col, spec: .9, gloss: .6, glass: .5 } : S0);
  const G2 = glass ? { col: c.col, spec: .9, gloss: .6, glass: .5 } : S0;
  switch (sys) {
    case 'nes':
      ridgesV(7, .335, .036, .2, .78, .016, .008);
      front(barG(1.02, .05, .008), 0, -.40, .008); frontP(planeGeo(1.0, .012), 0, -.47, .0015, dk);
      front(triG(.045, .006), 0, -.565, .006);
      backP(planeGeo(.8, .5), 0, .22, .001, { col: '#d8d2c4', spec: .1 });
      for (let i = 0; i < 6; i++) backP(planeGeo(.6 - (i % 3) * .1, .014), 0, .38 - i * .055, .002, { col: '#a9a294', spec: .1 });
      [[.47, .52], [-.47, .52], [.47, -.48], [-.47, -.48], [0, -.2]].forEach(([x, y]) => screw(x, y));
      connector(1.0, 30); lip(); break;
    case 'snes':
      front(barG(1.0, .016, .007), 0, -.03, .007); front(barG(1.0, .016, .007), 0, -.13, .007); frontP(planeGeo(1.0, .084), 0, -.08, .0012, dk);
      ridgesH(4, -.22, -.05, .78, .018, .007);
      [[-.42, -.3], [.42, -.3]].forEach(([x, y]) => screw(x, y));
      [-.5, .5].forEach(x => backP(planeGeo(.12, .1), x, -.44, .0015, DARK));
      backP(planeGeo(.7, .28), 0, .18, .001, { col: '#d8d2c4', spec: .1 });
      connector(1.0, 31); lip(); break;
    case 'n64':
      ridgesV(4, .37, .045, -.02, .42, .018, .008);
      backP(planeGeo(.7, .34), 0, -.06, .001, { col: '#d8d2c4', spec: .1 });
      [[-.3, .2], [.3, .2]].forEach(([x, y]) => screw(x, y));
      connector(.9, 25); lip(); break;
    case 'genesis':
      ridgesH(5, -.11, -.045, .8, .018, .008);
      front(triG(.04, .006), 0, -.53, .006);
      backP(planeGeo(.7, .5), 0, .25, .001, { col: '#2a2b31', spec: .2 }); screw(0, -.15);
      connector(.8, 32); lip(); break;
    case 'gb': case 'gbc':
      front(barG(.46, .085, .006, .01), 0, .245, .006, G2); ridgesH(3, .272, -.027, .4, .008, .004, 0, (g, x, y, t) => front(g, x, y, t + .01, G2));
      front(triG(.024, .004), 0, -.28, .004, G2);
      if (!glass) screw(0, .1, .022);
      connector(.5, 32); lip(.004, .012); break;
    case 'gba':
      ridgesH(4, .14, -.022, .3, .009, .004, 0, back);
      connector(.48, 32); lip(.004, .01); break;
    case 'ds':
      ridgesH(3, .148, -.018, .22, .007, .003);
      backP(planeGeo(.3, .05), 0, -.13, .001, DARK);
      for (let i = 0; i < 17; i++) backP(planeGeo(.01, .038), -.12 + i * .015, -.13, .0018, GOLD);
      lip(.003, .008); break;
    default:
      ridgesH(3, .118, -.016, .26, .007, .003); lip(.003, .008);
  }
  label(f + (sys === 'gb' || sys === 'gbc' || sys === 'gba' ? .002 : sys === 'ds' || sys === 'android' ? .0016 : .0025));
  return (cartMeshes[sys] = B.build({ c, ring: ringMesh(ccw(out)) }));
}
const labelAspect = sys => { const c = CART[sys]; return c.disc || c.umd ? 1 : c.label[2] / c.label[3]; };
function labelTex(g) { const c = CART[g.sys]; return c.disc || c.umd ? tex('disc' + g.id, () => discArt(g), true) : tex('label' + g.id, () => labelArt(g, labelAspect(g.sys)), true); }
function foldTex(g) {   // the strip of label that folds over the top: the label's top edge, mirrored so the two meet
  return tex('fold' + g.id, () => { const a = labelArt(g, labelAspect(g.sys)), c = canvas(512, 64), x = c.getContext('2d');
    x.translate(0, 64); x.scale(1, -1); x.drawImage(a, 0, 0, a.width, a.height * .12, 0, 0, 512, 64); x.setTransform(1, 0, 0, 1, 0, 0);
    x.fillStyle = 'rgba(8,6,24,.18)'; x.fillRect(0, 0, 512, 64); return c; });
}
function drawCartM(g, M, st) { drawParts(cartModel(g.sys), M, st, { tex: k => k === 'fold' ? foldTex(g) : labelTex(g) }); }

/* ---------- cases: the originals' sizes; cardboard boxes become modern clamshell cases ---------- */
const BOX = {}; for (const k in PLAT) BOX[k] = PLAT[k].box;
const CASE_KIND = { genesis: 'genesis', ps1: 'jewel', ds: 'ds', psp: 'keep', android: 'keep' };
const caseMeshes = {};
function caseColors(sys) {
  const light = ['light', 'dawn', 'bloom'].includes(P.prefs.theme);
  const neutral = light ? '#e8eaf0' : '#2b2f3c', inside = light ? '#d7dae2' : '#1d2029';
  const real = { genesis: ['#17181d', '#101114'], ps1: ['#24262e', '#16171c'], ds: ['#d9dce4', '#c4c8d2'], psp: ['#1f2128', '#15161b'], android: ['#262b3a', '#1b1f2b'] }[sys];
  let body = real ? real[0] : neutral, inner = real ? real[1] : inside;
  if (P.prefs.caseTint && !real) { const a = hexRgb(PLAT[sys].accent), b = hexRgb(body); body = '#' + a.map((v, i) => Math.round((v * .35 + b[i] * .65) * 255).toString(16).padStart(2, '0')).join(''); }
  const bodyC = hexRgb(body), innerC = hexRgb(inner);
  return { body: bodyC, inner: innerC, roles: { body: bodyC, inner: innerC, well: innerC.map(v => v * .7), edge: bodyC.map(v => v * .8) } };
}
function caseModel(sys) {
  if (caseMeshes[sys]) return caseMeshes[sys];
  const [W, H, D] = BOX[sys], kind = CASE_KIND[sys] || 'clam', tab = kind === 'genesis' ? .12 : 0, bh = H - tab, by = -tab / 2;
  const r = kind === 'jewel' ? .012 : .04, ct = Math.min(.03, D * .22), top = D / 2 - ct, c = CART[sys], outline = rrect(W, bh, r);
  const m = { W, H, D, ct, r, bh, by, kind, top,
    manual: extrude(shape(1, 1, [.012, .012, .012, .012]), 1), manualSize: [W * .8, bh * .8], mediaSize: [c.w, c.h] };
  const tw = W * .42;
  m.ring = ringMesh(tab ? ccw(rounded([[-W / 2, by - bh / 2, r], [W / 2, by - bh / 2, r], [W / 2, by + bh / 2, r], [tw / 2, by + bh / 2, .02], [tw / 2, H / 2, .04], [-tw / 2, H / 2, .04], [-tw / 2, by + bh / 2, .02], [-W / 2, by + bh / 2, r]])) : outline);
  const body = { role: 'body', spec: .35, grain: 1 }, inner = { role: 'inner', spec: .15, grain: 1 }, well = { role: 'well', spec: .12, grain: 1 }, edge = { role: 'edge', spec: .2, grain: 1 };
  const jewelTray = { col: '#15161b', spec: .5, grain: 1 }, clear = { col: '#dfe8f2', spec: 1, gloss: 1, glass: .28 };
  // the base: its shell, the hinge along the spine, and the molded tray inside
  const Bs = Builder(), at = (x, y, z, rx = 0, ry = 0, rz = 0) => M4.trs(x, y, z, rx, ry, rz, 1);
  // The base is hollow: a floor, a thin outer wall up to the hinge line, and a molded tray a little lower, with a
  // well the media sits in. That keeps the media (and the manual on the closed cover) inside the case.
  const [mw, mh] = m.mediaSize, tz = top - .02, floor = Math.max(-D / 2 + .012, tz - c.d - .007), shellMat = kind === 'jewel' ? jewelTray : body;
  const wellPts = c.disc ? rrect(1.24, 1.24, .62, 6) : rrect(mw + .03, mh + .03, c.umd ? .04 : .02, 6), ir = Math.max(.004, r - .012);
  Object.assign(m, { tz, floor, mediaZ: floor + .003 + c.d / 2 });
  Bs.add(solidGeo(outline, floor + D / 2, [0, .014], 3), at(0, by, (floor - D / 2) / 2), shellMat);
  Bs.add(frameGeo(outline, rrect(W - .024, bh - .024, ir, 6), top - floor), at(0, by, (top + floor) / 2), shellMat);
  Bs.add(frameGeo(rrect(W - .024, bh - .024, ir, 6), wellPts, tz - floor), at(0, by, (tz + floor) / 2), inner);
  const flat = pts => { const P = ccw(pts), G = geo(); P.forEach(([x, y]) => { G.p.push(x, y, 0); G.n.push(0, 0, 1); G.t.push(0, 0); }); G.i = earcut(P); return G; };
  Bs.add(flat(wellPts), at(0, by, floor + .0015), well);
  if (tab) { Bs.add(solidGeo(rrect(tw, tab + .05, [0, 0, .04, .04]), D * .3, .01), at(0, H / 2 - (tab + .05) / 2, -D / 2 + D * .15), body);
    [1, -1].forEach(s => Bs.add(discG(.03, .002), at(0, H / 2 - tab * .5, -D / 2 + D * .15 + s * D * .151), DARK)); }
  const kn = kind === 'jewel' ? clear : body;
  [-.4, 0, .4].forEach(k => Bs.add(discG(.01, bh * .14), at(-W / 2 + .011, by + k * bh, top - .002, -Math.PI / 2), kn));   // flush with the spine
  for (const s of [-1, 1]) Bs.add(barG(.006, D * .55, .01, .002), at(W / 2 - .03, by + s * bh * .34, tz + .004), edge);
  if (c.disc) {   // a hub with teeth that grips the disc
    Bs.add(ringG(.3, .6, .003, 72), at(0, by, floor + .003), edge);
    Bs.add(discG(.09, .008, 32), at(0, by, floor + .005), edge);
    for (let i = 0; i < 8; i++) { const a = i / 8 * Math.PI * 2; Bs.add(barG(.02, .05, .01, .006), at(Math.cos(a) * .075, by + Math.sin(a) * .075, floor + .006, 0, 0, a + Math.PI / 2), edge); }
  } else {        // two clips at the edge of the well
    Bs.add(barG(.1, .028, .01, .008), at(mw / 2 + .03, by + mh * .2, tz + .004), edge); Bs.add(barG(.1, .028, .01, .008), at(-mw / 2 - .03, by - mh * .2, tz + .004), edge);
  }
  Bs.add(planeGeo(D * .5, .2), at(W / 2 + .0015, by, -ct / 2, 0, Math.PI / 2), well);   // the finger notch on the open side
  m.base = Bs.build();
  // the cover: its shell, a raised rim around the art, and the clips that hold the manual inside
  const Bc = Builder(), [nw, nh] = m.manualSize, cz = D / 2;
  Bc.add(solidGeo(outline, ct, .01, 3), at(0, by, cz - ct / 2), kind === 'jewel' ? clear : body);
  if (kind !== 'jewel') {
    const rw = .016, rt = .004, ro = r - .01;   // one rounded frame that follows the case's corners
    Bc.add(frameGeo(rrect(W - .02, bh - .02, ro, 6), rrect(W - .02 - rw * 2, bh - .02 - rw * 2, Math.max(.004, ro - rw), 6), rt), at(0, by, cz + rt / 2 - .001), body);
  }
  Bc.add(planeGeo(W - .05, bh - .05), at(0, by, top - .001, 0, Math.PI), inner);
  [nh / 2 - .02, -nh / 2 + .02].forEach(y => Bc.add(barG(.12, .05, .012, .012), at(nw / 2 - .02, by + y, top - .008), body));
  m.cover = Bc.build();
  return (caseMeshes[sys] = m);
}
// a flat piece of art with rounded corners, so it follows the case's own rounded corners instead of overhanging them
const artPlanes = {};
function artPlane(w, h, r) {
  const key = [w, h, r].map(v => v.toFixed(4)).join();
  if (artPlanes[key]) return artPlanes[key];
  const pts = ccw(rrect(w, h, Math.min(r, w / 2 - 1e-4, h / 2 - 1e-4), 6)), G = geo();
  pts.forEach(([x, y]) => { G.p.push(x, y, 0); G.n.push(0, 0, 1); G.t.push(x / w + .5, y / h + .5); });
  G.i = earcut(pts);
  return (artPlanes[key] = upload(G));
}
// the cover swings open on the spine hinge: 0 is closed, 1 is fully open and lying to the left
function coverMatrix(m, open) {
  const hx = -m.W / 2, hz = m.D / 2 - m.ct / 2;
  return M4.mul(M4.trs(hx, 0, hz, 0, -Math.PI * open, 0, 1), M4.trs(-hx, 0, -hz, 0, 0, 0, 1));
}
function drawCase(g, st, open = 0, lift = 0, hideMedia = false) {
  if (NODRAW) return;
  const m = caseModel(g.sys), C = caseColors(g.sys), M = st.M || M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, 0, st.s);
  const { W, D, bh, by, top } = m, k = 360 / Math.min(W, bh), x = { roles: C.roles }, al = st.alpha == null ? 1 : st.alpha;
  const front = tex('front' + g.id, () => coverFront(g, W * k, bh * k)), back = tex('back' + g.id, () => coverBack(g, W * k, bh * k));
  const spine = tex('spine' + g.id, () => spineArt(g, bh * k, Math.max(28, D * k)));
  const o = (extra) => Object.assign({ alpha: al, bright: st.bright, spec: .35 }, extra);
  drawParts(m.base, M, st, x, 'opaque');
  drawPart(artPlane(W - .04, bh - .04, m.r), M4.mul(M, M4.trs(0, by, -D / 2 - .0015, 0, Math.PI, 0, 1)), o({ tex: back, spec: .5, gloss: .35 }));
  drawPart(artPlane(D - m.ct - .034, bh - 2 * m.r - .012, .004), M4.mul(M, M4.trs(-W / 2 - .0015, by, -m.ct / 2, 0, -Math.PI / 2, 0, 1)), o({ tex: spine, spec: .5, gloss: .35 }));
  if (open > .01 && !hideMedia) drawCartM(g, M4.mul(M, M4.trs(0, by, m.mediaZ + lift, 0, 0, 0, 1)), { alpha: al, bright: st.bright, glow: 0, s: st.s, detail: st.detail });
  drawParts(m.base, M, st, x, 'glass');
  const CM = M4.mul(M, coverMatrix(m, open)), jewel = m.kind === 'jewel';
  drawParts(m.cover, CM, st, x, 'opaque');
  drawPart(artPlane(W - (jewel ? .03 : .044), bh - (jewel ? .03 : .044), m.r), M4.mul(CM, M4.trs(0, by, jewel ? D / 2 - .004 : D / 2 + .002, 0, 0, 0, 1)), o({ tex: front, spec: .45 }));
  if (open > .01) {
    const [mw, mh] = m.manualSize, inner = top;
    drawPart(m.manual, M4.mul(CM, M4.trs(0, by, inner - .008, 0, 0, 0, [mw, mh, .012])), o({ col: [.93, .91, .87], spec: .1 }));
    const mtex = hasManual(g) ? tex('mancover' + g.id, () => coverFront(g, Math.round(mw * 400), Math.round(mh * 400)), true) : null;
    drawPart(PLANE, M4.mul(CM, M4.trs(0, by, inner - .0145, 0, Math.PI, 0, [mw - .01, mh - .01, 1])), o(mtex ? { tex: mtex, spec: .2 } : { col: C.inner.map(v => v * .85), spec: .1 }));
  }
  drawParts(m.cover, CM, st, x, 'glass');
  if (!jewel) drawPart(artPlane(W - .05, bh - .05, m.r), M4.mul(CM, M4.trs(0, by, D / 2 + .0034, 0, 0, 0, 1)), o({ col: [1, 1, 1], alpha: al * .06, spec: 1, gloss: 1 }));   // the clear sleeve over the art
}
function drawCaseGlow(g, st, c1, c2) {
  if (NODRAW) return;
  const m = caseModel(g.sys), M = M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, 0, st.s);
  gl.disable(gl.CULL_FACE); gl.depthMask(false);
  drawPart(m.ring, M, { col: c1, emit: c2, alpha: .95 * st.alpha, mask: false, hull: true, grow: 22 / (PX * st.s) });
  gl.enable(gl.CULL_FACE); gl.depthMask(true);
}
function drawCartGlowM(g, M, s, c1, c2, alpha = 1) {
  if (NODRAW) return;
  const cm = cartModel(g.sys); gl.disable(gl.CULL_FACE); gl.depthMask(false);
  drawPart(cm.ring, M, { col: c1, emit: c2, alpha: .95 * alpha, hull: true, grow: 20 / (PX * s) });
  gl.enable(gl.CULL_FACE); gl.depthMask(true);
}
