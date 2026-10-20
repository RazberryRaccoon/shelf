/* ================= WebGL 1 renderer (the same API as the Pi 3's OpenGL ES 2.0) ================= */
const gl = cv.getContext('webgl', { antialias: true, alpha: false, premultipliedAlpha: false });
const M4 = {
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  persp(fovy, asp, n, f) { const t = 1 / Math.tan(fovy / 2), o = new Float32Array(16); o[0] = t / asp; o[5] = t; o[10] = (f + n) / (n - f); o[11] = -1; o[14] = 2 * f * n / (n - f); return o; },
  // translate, rotate (Y, then X, then Z) and scale; s may be a number or [sx, sy, sz]
  trs(x, y, z, rx, ry, rz, s) {
    const sx = Array.isArray(s) ? s[0] : s, sy = Array.isArray(s) ? s[1] : s, sz = Array.isArray(s) ? s[2] : s;
    const cx = Math.cos(rx), snx = Math.sin(rx), cy = Math.cos(ry), sny = Math.sin(ry), cz = Math.cos(rz), snz = Math.sin(rz);
    // R = Ry * Rx * Rz
    const r00 = cy * cz + sny * snx * snz, r01 = -cy * snz + sny * snx * cz, r02 = sny * cx;
    const r10 = cx * snz, r11 = cx * cz, r12 = -snx;
    const r20 = -sny * cz + cy * snx * snz, r21 = sny * snz + cy * snx * cz, r22 = cy * cx;
    return new Float32Array([r00 * sx, r10 * sx, r20 * sx, 0, r01 * sy, r11 * sy, r21 * sy, 0, r02 * sz, r12 * sz, r22 * sz, 0, x, y, z, 1]);
  },
};
const FOV = 30 * Math.PI / 180, CAMZ = 10, PX = (H / 2) / (CAMZ * Math.tan(FOV / 2));   // pixels per world unit at z = 0
const pw = (px, py) => [(px - W / 2) / PX, -(py - H / 2) / PX];

function shader(vs, fs) {
  const mk = (t, s) => { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(x)); return x; };
  const p = gl.createProgram(); gl.attachShader(p, mk(gl.VERTEX_SHADER, vs)); gl.attachShader(p, mk(gl.FRAGMENT_SHADER, fs)); gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
  const u = {}, n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < n; i++) { const info = gl.getActiveUniform(p, i); u[info.name.replace('[0]', '')] = gl.getUniformLocation(p, info.name); }
  const a = {}, na = gl.getProgramParameter(p, gl.ACTIVE_ATTRIBUTES);
  for (let i = 0; i < na; i++) { const info = gl.getActiveAttrib(p, i); a[info.name] = gl.getAttribLocation(p, info.name); }
  return { p, u, a };
}
let MESH, BG, STARS, PLANET;
function initGL() {
  MESH = shader(`
attribute vec3 aPos; attribute vec3 aNor; attribute vec2 aUV;
uniform mat4 uVP, uM; uniform float uGrow; varying vec3 vN, vW, vL; varying vec2 vUV;
void main() { vL = aPos; vec4 w = uM * vec4(aPos + aNor * uGrow * aUV.y, 1.0); vW = w.xyz; vN = mat3(uM) * aNor; vUV = aUV; gl_Position = uVP * w; }`, `
precision mediump float;
varying vec3 vN, vW, vL; varying vec2 vUV;
uniform sampler2D uTex; uniform float uTexOn, uAlpha, uSpec, uBright, uMode, uTime; uniform vec3 uColor, uRim, uEye, uEmit;
void main() {
  if (uMode > 1.5) {   // selection glow: two colors chasing each other around the outline
    float k = 0.5 + 0.5 * sin(atan(vL.y, vL.x) + 0.8);          // brighter toward the top left, where the light comes from
    float pulse = 0.86 + 0.14 * sin(uTime * 1.3);                 // the whole ring breathes together, slowly
    float fade = pow(1.0 - vUV.y, 1.8);
    gl_FragColor = vec4(mix(uColor, uEmit, k), uAlpha * fade * (0.55 + 0.45 * k) * pulse); return;
  }
  vec4 tc = texture2D(uTex, vUV);
  if (uMode > 0.5) { gl_FragColor = vec4(uColor, tc.a * uAlpha); return; }
  vec3 n = normalize(vN);
  vec3 base = uTexOn > 0.5 ? tc.rgb : uColor;
  vec3 L = normalize(vec3(-0.45, 0.75, 0.55));
  vec3 V = normalize(uEye - vW);
  float d = max(dot(n, L), 0.0);
  float s = pow(max(dot(n, normalize(L + V)), 0.0), 36.0) * uSpec;
  float rim = pow(1.0 - max(dot(n, V), 0.0), 2.5);
  vec3 c = base * (0.20 + 0.70 * d + 0.30 * max(dot(n, V), 0.0)) * uBright + vec3(s) + uRim * rim * 0.55 + uEmit;
  gl_FragColor = vec4(c, uAlpha);
}`);
  BG = shader(`
attribute vec2 aP; varying vec2 vUV; void main() { vUV = aP * 0.5 + 0.5; gl_Position = vec4(aP, 0.0, 1.0); }`, `
precision mediump float; varying vec2 vUV; uniform sampler2D uA, uB; uniform float uMix, uBright;
void main() { gl_FragColor = vec4(mix(texture2D(uA, vUV).rgb, texture2D(uB, vUV).rgb, uMix) * uBright, 1.0); }`);
  STARS = shader(`
attribute vec2 aP; attribute float aS, aD, aPh; uniform vec2 uPan; uniform float uT, uScale, uBright; varying float vA;
void main() {
  vec2 p = fract(aP + uPan * aD) * 2.0 - 1.0; p.y = -p.y;
  vA = (0.55 + 0.45 * sin(uT * (0.6 + aPh) + aPh * 20.0)) * uBright * (0.4 + 0.6 * aD);
  gl_PointSize = aS * uScale; gl_Position = vec4(p, 0.0, 1.0);
}`, `
precision mediump float; varying float vA;
void main() { float r = length(gl_PointCoord - 0.5) * 2.0; gl_FragColor = vec4(vec3(0.93, 0.95, 1.0), vA * smoothstep(1.0, 0.2, r)); }`);
  PLANET = shader(`
attribute vec2 aP; uniform vec2 uC; uniform float uR; varying vec2 vL;
void main() { vL = aP * 1.3; vec2 px = uC + vL * uR; gl_Position = vec4(px.x / 960.0 - 1.0, 1.0 - px.y / 540.0, 0.0, 1.0); }`, `
precision mediump float; varying vec2 vL; uniform sampler2D uTex; uniform float uRot, uAlpha; uniform vec3 uAtm;
void main() {
  float r = length(vL);
  vec3 L = normalize(vec3(-0.55, 0.55, 0.62));
  if (r < 1.0) {
    vec3 n = vec3(vL.x, -vL.y, sqrt(1.0 - r * r));
    float c = cos(0.35), s = sin(0.35);
    vec3 m = vec3(n.x * c - n.y * s, n.x * s + n.y * c, n.z);
    float lon = atan(m.x, m.z) / 6.28318 + uRot, lat = acos(clamp(m.y, -1.0, 1.0)) / 3.14159;
    vec3 col = texture2D(uTex, vec2(fract(lon), lat)).rgb;
    float d = smoothstep(-0.15, 0.6, dot(n, L));
    float edge = pow(1.0 - n.z, 3.0);
    vec3 outc = col * (0.06 + 0.94 * d) + uAtm * edge * (0.25 + 0.75 * d);
    gl_FragColor = vec4(outc + uAtm * smoothstep(0.97, 1.0, r) * 0.55, uAlpha);
  } else {
    float lit = clamp(dot(normalize(vec3(vL.x, -vL.y, 0.0)), L) * 0.5 + 0.5, 0.0, 1.0);
    float g = exp(-(r - 1.0) * 14.0) * (0.15 + 0.85 * lit);
    gl_FragColor = vec4(uAtm, g * 0.55 * uAlpha);
  }
}`);
}

/* ---------- buffers ---------- */
function mesh(pos, nor, uv, idx) {
  const b = k => { const x = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, x); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(k), gl.STATIC_DRAW); return x; };
  const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(idx), gl.STATIC_DRAW);
  return { p: b(pos), n: b(nor), t: b(uv), i: ib, count: idx.length };
}
// A flat shape (convex, counter-clockwise) pushed out to a given depth, with smooth sides on rounded corners.
function extrude(pts, depth) {
  const pos = [], nor = [], uv = [], idx = [], n = pts.length, z = depth / 2;
  let minx = 1e9, maxx = -1e9, miny = 1e9, maxy = -1e9, cx = 0, cy = 0;
  pts.forEach(([x, y]) => { minx = Math.min(minx, x); maxx = Math.max(maxx, x); miny = Math.min(miny, y); maxy = Math.max(maxy, y); cx += x / n; cy += y / n; });
  const face = (zz, nz) => {
    const start = pos.length / 3;
    pos.push(cx, cy, zz); nor.push(0, 0, nz); uv.push((cx - minx) / (maxx - minx), (cy - miny) / (maxy - miny));
    pts.forEach(([x, y]) => { pos.push(x, y, zz); nor.push(0, 0, nz); uv.push((x - minx) / (maxx - minx), (y - miny) / (maxy - miny)); });
    for (let i = 0; i < n; i++) { const a = start + 1 + i, b = start + 1 + (i + 1) % n; nz > 0 ? idx.push(start, a, b) : idx.push(start, b, a); }
  };
  face(z, 1); face(-z, -1);
  const en = pts.map((p, i) => { const q = pts[(i + 1) % n], dx = q[0] - p[0], dy = q[1] - p[1], l = Math.hypot(dx, dy) || 1; return [dy / l, -dx / l]; });
  const vn = (i, e) => { const a = en[(i - 1 + n) % n], b = en[i]; const dot = a[0] * b[0] + a[1] * b[1]; if (dot < 0.7) return en[e]; const x = a[0] + b[0], y = a[1] + b[1], l = Math.hypot(x, y); return [x / l, y / l]; };
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n, a = pts[i], b = pts[j], na = vn(i, i), nb = vn(j, i), s = pos.length / 3;
    pos.push(a[0], a[1], z, a[0], a[1], -z, b[0], b[1], -z, b[0], b[1], z);
    nor.push(na[0], na[1], 0, na[0], na[1], 0, nb[0], nb[1], 0, nb[0], nb[1], 0);
    uv.push(0, 0, 0, 1, 1, 1, 1, 0);
    idx.push(s, s + 1, s + 2, s, s + 2, s + 3);
  }
  return mesh(pos, nor, uv, idx);
}
// outline of a w x h shape with per-corner radius; a negative radius cuts the corner off instead
function shape(w, h, r = [0, 0, 0, 0]) {   // radii: bottom-left, bottom-right, top-right, top-left
  const pts = [], hw = w / 2, hh = h / 2;
  const corner = (cx, cy, rad, a0) => { for (let k = 0; k <= 5; k++) { const a = a0 + k / 5 * Math.PI / 2; pts.push([cx + Math.cos(a) * rad, cy + Math.sin(a) * rad]); } };
  const R = r.map(x => Math.abs(x));
  // bottom-left
  if (r[0] < 0) { pts.push([-hw, -hh + R[0]], [-hw + R[0], -hh]); } else corner(-hw + R[0], -hh + R[0], R[0], Math.PI);
  if (r[1] < 0) { pts.push([hw - R[1], -hh], [hw, -hh + R[1]]); } else corner(hw - R[1], -hh + R[1], R[1], Math.PI * 1.5);
  if (r[2] < 0) { pts.push([hw, hh - R[2]], [hw - R[2], hh]); } else corner(hw - R[2], hh - R[2], R[2], 0);
  if (r[3] < 0) { pts.push([-hw + R[3], hh], [-hw, hh - R[3]]); } else corner(-hw + R[3], hh - R[3], R[3], Math.PI / 2);
  // drop accidental duplicates (zero radius corners give six identical points)
  return pts.filter((p, i) => { const q = pts[(i + pts.length - 1) % pts.length]; return Math.hypot(p[0] - q[0], p[1] - q[1]) > 1e-5; });
}
function circle(r, seg = 28) { const pts = []; for (let i = 0; i < seg; i++) { const a = i / seg * Math.PI * 2; pts.push([Math.cos(a) * r, Math.sin(a) * r]); } return pts; }
function ringMesh(pts) {   // inner edge on the outline (uv.y = 0), outer edge pushed out in the shader (uv.y = 1)
  const n = pts.length, pos = [], nor = [], uv = [], idx = [];
  const en = pts.map((p, i) => { const q = pts[(i + 1) % n], dx = q[0] - p[0], dy = q[1] - p[1], l = Math.hypot(dx, dy) || 1; return [dy / l, -dx / l]; });
  for (let i = 0; i <= n; i++) {
    const k = i % n, a = en[(k - 1 + n) % n], b = en[k]; let x = a[0] + b[0], y = a[1] + b[1]; const l = Math.hypot(x, y) || 1; x /= l; y /= l;
    pos.push(pts[k][0], pts[k][1], 0, pts[k][0], pts[k][1], 0); nor.push(x, y, 0, x, y, 0); uv.push(i / n, 0, i / n, 1);
    if (i < n) { const s = i * 2; idx.push(s, s + 1, s + 3, s, s + 3, s + 2); }
  }
  return mesh(pos, nor, uv, idx);
}
let QUAD, PLANE, SCREENQ, STARBUF;
function initMeshes() {
  PLANE = mesh([-.5, -.5, 0, .5, -.5, 0, .5, .5, 0, -.5, .5, 0], [0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1], [0, 0, 1, 0, 1, 1, 0, 1], [0, 1, 2, 0, 2, 3]);
  SCREENQ = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, SCREENQ); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1]), gl.STATIC_DRAW);
  const r = rng(99), st = [];
  for (let i = 0; i < 520; i++) { const d = r(); st.push(r(), r(), 1.2 + Math.pow(r(), 3) * 2.8, 0.15 + d * 0.85, r()); }
  STARBUF = { b: gl.createBuffer(), n: 520 }; gl.bindBuffer(gl.ARRAY_BUFFER, STARBUF.b); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(st), gl.STATIC_DRAW);
}

/* ---------- textures ---------- */
const texCache = new Map();
function tex(key, make, rgba) {
  if (texCache.has(key)) return texCache.get(key);
  const c = make(), t = gl.createTexture(), f = rgba ? gl.RGBA : gl.RGB;
  gl.bindTexture(gl.TEXTURE_2D, t);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
  gl.texImage2D(gl.TEXTURE_2D, 0, f, f, gl.UNSIGNED_BYTE, c);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  texCache.set(key, t); return t;
}
function planetSurface(cols, seed) {
  const c = canvas(512, 256), ctx = c.getContext('2d'), r = rng(seed);
  const [a, b, d] = cols.map(hexRgb);
  const img = ctx.createImageData(512, 256);
  const bands = []; for (let i = 0; i < 9; i++) bands.push([r() * 6 + 2, r() * 6.28, r() * .5 + .2]);
  for (let y = 0; y < 256; y++) {
    let t = 0; bands.forEach(([f, ph, amp]) => t += Math.sin(y / 256 * f * 3.14 + ph) * amp);
    t = t * .25 + .5;
    for (let x = 0; x < 512; x++) {
      const wob = Math.sin(x / 512 * 6.283 * 3 + y * .05) * .05 + Math.sin(x / 512 * 6.283 * 7 + y * .13) * .025;
      const k = clamp(t + wob, 0, 1), c1 = k < .5 ? d : b, c2 = k < .5 ? b : a, m = k < .5 ? k * 2 : (k - .5) * 2;
      const i = (y * 512 + x) * 4;
      img.data[i] = (c1[0] + (c2[0] - c1[0]) * m) * 255; img.data[i + 1] = (c1[1] + (c2[1] - c1[1]) * m) * 255; img.data[i + 2] = (c1[2] + (c2[2] - c1[2]) * m) * 255; img.data[i + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  return c;
}
function nebula(cols, seed) {
  const c = canvas(960, 540), ctx = c.getContext('2d'), r = rng(seed);
  ctx.fillStyle = '#070b1e'; ctx.fillRect(0, 0, 960, 540);
  for (let i = 0; i < 7; i++) {
    const x = r() * 960, y = r() * 540, rad = 200 + r() * 380, g = ctx.createRadialGradient(x, y, 0, x, y, rad);
    g.addColorStop(0, cols[i % 2]); g.addColorStop(1, 'rgba(7,11,30,0)');
    ctx.globalAlpha = .55 + r() * .35; ctx.fillStyle = g; ctx.fillRect(0, 0, 960, 540);
  }
  ctx.globalAlpha = 1;
  return c;
}

/* ---------- models: original designs, one distinct shell per console ---------- */
// Label windows match the part of each real label that shows on the front (one unit is 100 mm, so .55 is 55 mm):
// NES 55 x 90, SNES 82.5 x 37, Genesis 74 x 60, Game Boy and Color 42 x 37, Game Boy Advance 43 x 22.
const CART = {
  nes:     { w: 1.20, h: 1.34, d: .17, label: [0, .155, .55, .90], frame: '#5d5955' },
  snes:    { w: 1.30, h: .95, d: .2, label: [0, .22, .825, .37], frame: '#8f8ca0' },
  genesis: { w: .98, h: 1.34, d: .22, label: [0, .29, .74, .60], frame: '#1e3570' },
  gb:      { w: .60, h: .66, d: .09, label: [0, -.06, .42, .37], frame: '#9a99a2' },
  gbc:     { w: .60, h: .66, d: .09, label: [0, -.06, .42, .37], frame: '#17635c' },
  gba:     { w: .60, h: .38, d: .085, label: [0, .02, .43, .22], frame: '#2a2656' },
};
const cartMeshes = {};
function cartModel(sys) {
  if (cartMeshes[sys]) return cartMeshes[sys];
  const c = CART[sys], P = [], f = c.d / 2;
  const add = (mesh, x, y, z, s, col, o = {}) => P.push(Object.assign({ mesh, m: M4.trs(x, y, z, 0, 0, 0, s), col: hexRgb(col), spec: .3 }, o));
  const box = (w, h, d, r) => extrude(shape(w, h, [r, r, r, r]), d);
  const [lx, ly, lw, lh] = c.label;
  let outline;
  if (sys === 'nes') {            // "Slate": a tall label between two ribbed grips, a slot band and a round emblem
    outline = shape(c.w, c.h, [.025, .025, .025, .025]); add(extrude(outline, c.d), 0, 0, 0, 1, '#8e8a86');
    for (const sx of [-1, 1]) for (let i = 0; i < 6; i++) add(box(.02, .62, .016, .008), sx * (.34 + i * .042), .2, f + .004, 1, '#6f6b67');
    add(box(.9, .08, .014, .03), 0, -.47, f + .004, 1, '#5d5955');
    add(extrude(circle(.04, 18), .02), .45, -.57, f + .006, 1, '#ff9db6', { spec: .7 });
  } else if (sys === 'snes') {    // "Arc": an arched top, a pair of grip lines and an accent stripe
    outline = shape(c.w, c.h, [.05, .05, .34, .34]); add(extrude(outline, c.d), 0, 0, 0, 1, '#b4b1c2');
    [-.2, -.25, -.3].forEach(y => add(box(.8, .022, .016, .008), 0, y, f + .004, 1, '#9794a8'));
    add(box(1.02, .03, .016, .01), 0, -.43, f + .004, 1, '#8f7ee8', { spec: .6 });
  } else if (sys === 'genesis') { // "Monolith": glossy black, cut corners, three vents
    outline = shape(c.w, c.h, [.03, .03, -.14, -.14]); add(extrude(outline, c.d), 0, 0, 0, 1, '#1f2026', { spec: .75 });
    [-.42, -.48, -.54].forEach(y => add(box(.5, .028, .016, .01), 0, y, f + .004, 1, '#0b0b0f'));
  } else if (sys === 'gb') {      // "Pebble": soft, fully rounded, a single thumb dimple
    outline = shape(c.w, c.h, [.1, .1, .1, .1]); add(extrude(outline, c.d), 0, 0, 0, 1, '#bcbbc3');
    add(extrude(circle(.055, 20), .012), 0, .25, f + .002, 1, '#8f8e97', { spec: .1 });
  } else if (sys === 'gbc') {     // "Prism": a see-through tinted shell with the circuit board inside
    add(box(.5, .52, .01, .02), 0, 0, 0, 1, '#1d5b4c', { spec: .4 });
    [[-.12, .2], [.1, .2]].forEach(([x, y]) => add(box(.12, .06, .02, .005), x, y, .015, 1, '#0e1412', { spec: .5 }));
    outline = shape(c.w, c.h, [-.07, -.07, -.07, -.07]); add(extrude(outline, c.d), 0, 0, 0, 1, '#38c7bd', { spec: .9, glass: .5 });
  } else {                        // "Wedge": small and wide, with a stepped connector tab
    outline = shape(c.w, c.h, [.03, .03, .09, .09]); add(extrude(outline, c.d), 0, 0, 0, 1, '#3f3a7a', { spec: .4 });
    add(box(.44, .07, .06, .015), 0, -c.h / 2 - .03, 0, 1, '#2a2656');
    for (let i = -2; i <= 2; i++) add(box(.012, .04, .012, .004), i * .07, -c.h / 2 - .03, .034, 1, '#8b84d8');
  }
  add(PLANE, lx, ly, f + .003, [lw + .05, lh + .05, 1], c.frame, { spec: .1 });
  add(PLANE, lx, ly, f + .005, [lw, lh, 1], '#ffffff', { label: true, spec: .25 });
  return (cartMeshes[sys] = { parts: P, c, ring: ringMesh(outline) });
}
const BOX = { nes: [1.27, 1.8, .32], snes: [1.8, 1.3, .32], genesis: [1.36, 1.86, .2], gb: [1.3, 1.3, .26], gbc: [1.3, 1.3, .26], gba: [1.3, 1.3, .26] };

// original consoles for the launch scene, in each console's planet colors
const consoleMeshes = {};
function consoleModel(sys) {
  if (consoleMeshes[sys]) return consoleMeshes[sys];
  const P = [], add = (mesh, x, y, z, rx, ry, rz, s, col, extra = {}) => P.push(Object.assign({ mesh, m: M4.trs(x, y, z, rx, ry, rz, s), col: hexRgb(col), spec: .3 }, extra));
  const slab = (w, h, d, r) => extrude(shape(w, d, Array.isArray(r) ? r : [r, r, r, r]), h);
  const flat = Math.PI / 2;
  let slot;
  if (sys === 'nes') {            // a two-tone deck that loads from the front
    add(slab(2.6, .36, 2.0, .06), 0, -.2, 0, flat, 0, 0, 1, '#4a4744');
    add(slab(2.6, .4, 2.0, .06), 0, .18, 0, flat, 0, 0, 1, '#a8a39e');
    add(PLANE, 0, .19, 1.001, 0, 0, 0, [1.5, .22, 1], '#1a1918');
    add(PLANE, 0, -.3, 1.001, 0, 0, 0, [2.0, .03, 1], '#5a2a36', { led: true });
    slot = { kind: 'front', x: 0, y: .19, z: 1.0 };
  } else if (sys === 'snes') {    // a low, rounded deck with a lit stripe
    add(slab(2.4, .5, 1.9, .38), 0, 0, 0, flat, 0, 0, 1, '#c9c6d6');
    add(PLANE, 0, .252, -.15, -flat, 0, 0, [1.42, .26, 1], '#4b485a');
    const btn = extrude(circle(.1, 20), .08);
    add(btn, -.75, .27, .5, flat, 0, 0, 1, '#ecebf3'); add(btn, .75, .27, .5, flat, 0, 0, 1, '#ecebf3');
    add(PLANE, 0, -.05, .951, 0, 0, 0, [1.6, .04, 1], '#3e3570', { led: true });
    slot = { kind: 'top', x: 0, y: .25, z: -.15 };
  } else if (sys === 'genesis') { // a faceted black block with a glowing ring around the slot
    add(slab(2.3, .5, 1.9, [-.3, -.3, -.3, -.3]), 0, 0, 0, flat, 0, 0, 1, '#202128', { spec: .65 });
    add(PLANE, 0, .251, -.1, -flat, 0, 0, [1.26, .38, 1], '#16284f', { led: true });
    add(PLANE, 0, .253, -.1, -flat, 0, 0, [1.1, .24, 1], '#0a0a0d');
    slot = { kind: 'top', x: 0, y: .25, z: -.1 };
  } else {                        // handhelds
    const dim = { gb: [.92, 1.46, '#cfced6', '#5f6070', [.07, .07, .07, .07], [.62, .52, .3]], gbc: [.84, 1.34, '#2a9d95', '#163f45', [.12, .12, .08, .08], [.58, .48, .28]],
      gba: [1.5, .84, '#4a40a8', '#211d4a', [.26, .26, .26, .26], [.68, .46, .04]] }[sys];
    const [w, h, body, bez, rr, scr] = dim, d = .26;
    add(extrude(shape(w, h, rr), d), 0, 0, 0, 0, 0, 0, 1, body);
    add(PLANE, 0, scr[2], d / 2 + .002, 0, 0, 0, [scr[0] + .12, scr[1] + .12, 1], bez);
    add(PLANE, 0, scr[2], d / 2 + .004, 0, 0, 0, [scr[0], scr[1], 1], '#151a16', { screen: true });
    const pad = sys === 'gba' ? [-.56, 0] : [-w * .26, -h * .28], btn = sys === 'gba' ? [.56, 0] : [w * .25, -h * .26];
    const bar = extrude(shape(.17, .055, [.012, .012, .012, .012]), .04);
    add(bar, pad[0], pad[1], d / 2 + .02, 0, 0, 0, 1, '#25252e'); add(bar, pad[0], pad[1], d / 2 + .02, 0, 0, Math.PI / 2, 1, '#25252e');
    const b = extrude(circle(.05, 18), .04);
    add(b, btn[0] - .07, btn[1], d / 2 + .02, 0, 0, 0, 1, '#ecebf3'); add(b, btn[0] + .07, btn[1] + .05, d / 2 + .02, 0, 0, 0, 1, '#ecebf3');
    add(PLANE, 0, sys === 'gba' ? -.33 : -h / 2 + .1, d / 2 + .003, 0, 0, 0, [sys === 'gba' ? .5 : .4, .025, 1], '#4a2030', { led: true });
    slot = { kind: 'hand', x: 0, y: h / 2, z: -.04 };
  }
  return (consoleMeshes[sys] = { parts: P, slot });
}

/* ---------- drawing ---------- */
let VP, EYE = [0, 0, CAMZ], RIM = [0.6, 0.5, 1];
function setCamera(camY = 0, camZ = CAMZ, lookY = 0) {
  const proj = M4.persp(FOV, W / H, 0.1, 100);
  const pitch = Math.atan2(camY - lookY, camZ);
  const view = M4.mul(M4.trs(0, 0, 0, pitch, 0, 0, 1), M4.trs(0, -camY, -camZ, 0, 0, 0, 1));
  VP = M4.mul(proj, view); EYE = [0, camY, camZ];
}
function bindMesh(m) {
  const a = MESH.a;
  gl.bindBuffer(gl.ARRAY_BUFFER, m.p); gl.vertexAttribPointer(a.aPos, 3, gl.FLOAT, false, 0, 0);
  gl.bindBuffer(gl.ARRAY_BUFFER, m.n); gl.vertexAttribPointer(a.aNor, 3, gl.FLOAT, false, 0, 0);
  gl.bindBuffer(gl.ARRAY_BUFFER, m.t); gl.vertexAttribPointer(a.aUV, 2, gl.FLOAT, false, 0, 0);
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, m.i);
}
function drawPart(m, model, o) {
  if (NODRAW) return;
  const u = MESH.u;
  gl.uniformMatrix4fv(u.uM, false, model);
  gl.uniform1f(u.uTexOn, o.tex ? 1 : 0);
  if (o.tex) { gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, o.tex); gl.uniform1i(u.uTex, 0); }
  gl.uniform3fv(u.uColor, o.col || [1, 1, 1]); gl.uniform1f(u.uAlpha, o.alpha == null ? 1 : o.alpha); gl.uniform1f(u.uSpec, o.spec || 0);
  gl.uniform1f(u.uBright, o.bright == null ? 1 : o.bright); gl.uniform3fv(u.uEmit, o.emit || [0, 0, 0]); gl.uniform1f(u.uMode, o.hull ? 2 : o.mask ? 1 : 0); gl.uniform1f(u.uGrow, o.grow || 0);
  bindMesh(m); gl.drawElements(gl.TRIANGLES, m.count, gl.UNSIGNED_SHORT, 0);
}
function beginMeshes() {
  if (NODRAW) return;
  gl.useProgram(MESH.p); const a = MESH.a;
  gl.enableVertexAttribArray(a.aPos); gl.enableVertexAttribArray(a.aNor); gl.enableVertexAttribArray(a.aUV);
  gl.uniformMatrix4fv(MESH.u.uVP, false, VP); gl.uniform3fv(MESH.u.uEye, EYE); gl.uniform3fv(MESH.u.uRim, RIM); gl.uniform1f(MESH.u.uTime, S.time);
  gl.enable(gl.DEPTH_TEST); gl.enable(gl.CULL_FACE); gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
}
function endMeshes() { if (NODRAW) return; const a = MESH.a; gl.disableVertexAttribArray(a.aNor); gl.disableVertexAttribArray(a.aUV); gl.disable(gl.DEPTH_TEST); gl.disable(gl.CULL_FACE); }
function drawCart(g, st) {   // st: x y z rx ry rz s alpha bright glow
  const cm = cartModel(g.sys), M = M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, st.rz || 0, st.s);
  const c = cm.c, aspect = c.label[2] / c.label[3];
  for (const p of cm.parts) {
    drawPart(p.mesh, M4.mul(M, p.m), { col: p.col, spec: p.spec, alpha: st.alpha * (p.glass || 1), bright: st.bright,
      tex: p.label ? tex('label' + g.id, () => labelArt(g, aspect)) : null, emit: st.glow ? RIM.map(v => v * st.glow * .08) : null });
  }
}
function drawBox(g, st) {
  const [bw, bh, bd] = BOX[g.sys], M = M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, 0, st.s);
  const pxs = 360, k = pxs / Math.min(bw, bh);
  const front = tex('front' + g.id, () => coverFront(g, bw * k, bh * k)), back = tex('back' + g.id, () => coverBack(g, bw * k, bh * k));
  const spine = tex('spine' + g.id, () => spineArt(g, bh * k, Math.max(48, bd * k))), edge = hexRgb('#1a1c2a');
  const faces = [
    [0, 0, bd / 2, 0, 0, [bw, bh, 1], front], [0, 0, -bd / 2, 0, Math.PI, [bw, bh, 1], back],
    [-bw / 2, 0, 0, 0, -Math.PI / 2, [bd, bh, 1], spine], [bw / 2, 0, 0, 0, Math.PI / 2, [bd, bh, 1], null],
    [0, bh / 2, 0, -Math.PI / 2, 0, [bw, bd, 1], null], [0, -bh / 2, 0, Math.PI / 2, 0, [bw, bd, 1], null]];
  for (const [x, y, z, rx, ry, s, t] of faces)
    drawPart(PLANE, M4.mul(M, M4.trs(x, y, z, rx, ry, 0, s)), { tex: t, col: edge, alpha: st.alpha, spec: .25, bright: st.bright });
}
function drawConsole(sys, st, power) {
  const cm = consoleModel(sys), M = M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, 0, st.s);
  for (const p of cm.parts) {
    let emit = null, col = p.col;
    if (p.led && power > 0) { const a = hexRgb(SYS[sys].accent); emit = a.map(v => v * power * .9); }
    if (p.screen && power > 0) emit = sys === 'gb' ? [.45 * power, .62 * power, .2 * power] : [.55 * power, .6 * power, .75 * power];
    drawPart(p.mesh, M4.mul(M, p.m), { col, spec: p.spec, alpha: st.alpha, emit });
  }
  return M;
}

/* ---------- the backdrop ---------- */
const MOOD = { cur: 'home', prev: 'home', t: 1 };
function moodColors(key) { return SYS[key] ? SYS[key] : { planet: ['#e6dcff', '#8b78e6', '#2b1f66'], neb: ['#2a1a5e', '#0d0b2c'], accent: '#c2b6ff' }; }
function setMood(key) {
  if (key === MOOD.cur) return;
  MOOD.prev = MOOD.cur; MOOD.cur = key; MOOD.t = 0;
  const acc = moodColors(key).accent; stage.style.setProperty('--accent', acc); RIM_T = hexRgb(acc);
}
let RIM_T = [0.6, 0.5, 1];
function drawBackdrop(time, opts) {
  if (NODRAW) return;
  gl.disable(gl.DEPTH_TEST); gl.disable(gl.CULL_FACE);
  gl.useProgram(BG.p);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex('neb' + MOOD.prev, () => nebula(moodColors(MOOD.prev).neb, hash(MOOD.prev)))); gl.uniform1i(BG.u.uA, 0);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, tex('neb' + MOOD.cur, () => nebula(moodColors(MOOD.cur).neb, hash(MOOD.cur)))); gl.uniform1i(BG.u.uB, 1);
  gl.uniform1f(BG.u.uMix, ease(MOOD.t)); gl.uniform1f(BG.u.uBright, opts.bright);
  gl.bindBuffer(gl.ARRAY_BUFFER, SCREENQ); gl.enableVertexAttribArray(BG.a.aP); gl.vertexAttribPointer(BG.a.aP, 2, gl.FLOAT, false, 0, 0);
  gl.drawArrays(gl.TRIANGLES, 0, 6);
  gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
  gl.useProgram(STARS.p);
  gl.uniform2f(STARS.u.uPan, opts.pan, 0); gl.uniform1f(STARS.u.uT, time); gl.uniform1f(STARS.u.uScale, cv.width / W); gl.uniform1f(STARS.u.uBright, opts.bright);
  gl.bindBuffer(gl.ARRAY_BUFFER, STARBUF.b);
  const a = STARS.a; [['aP', 2, 0], ['aS', 1, 2], ['aD', 1, 3], ['aPh', 1, 4]].forEach(([n, k, off]) => { gl.enableVertexAttribArray(a[n]); gl.vertexAttribPointer(a[n], k, gl.FLOAT, false, 20, off * 4); });
  gl.drawArrays(gl.POINTS, 0, STARBUF.n);
  ['aS', 'aD', 'aPh'].forEach(n => gl.disableVertexAttribArray(a[n]));
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  if (opts.planet) {
    const p = opts.planet;
    if (MOOD.t < 1) drawPlanet(MOOD.prev, p.x, p.y, p.r, time, opts.bright * (1 - ease(MOOD.t)));
    drawPlanet(MOOD.cur, p.x, p.y, p.r, time, opts.bright * (MOOD.t < 1 ? ease(MOOD.t) : 1));
  }
}
function drawPlanet(key, x, y, r, time, alpha, cols) {
  if (NODRAW) return;
  const c = cols || moodColors(key).planet;
  gl.useProgram(PLANET.p);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex('planet' + key, () => planetSurface(c, hash(key)))); gl.uniform1i(PLANET.u.uTex, 0);
  gl.uniform2f(PLANET.u.uC, x, y); gl.uniform1f(PLANET.u.uR, r); gl.uniform1f(PLANET.u.uRot, time * .004); gl.uniform1f(PLANET.u.uAlpha, alpha);
  gl.uniform3fv(PLANET.u.uAtm, hexRgb(c[0]));
  gl.bindBuffer(gl.ARRAY_BUFFER, SCREENQ); gl.enableVertexAttribArray(PLANET.a.aP); gl.vertexAttribPointer(PLANET.a.aP, 2, gl.FLOAT, false, 0, 0);
  gl.drawArrays(gl.TRIANGLES, 0, 6);
}

/* glowing panels (library selection, the "All games" tile) and clipping to a band of the screen */
function drawPlate(key, make, px, py, wpx, hpx, z, color, alpha) {
  if (NODRAW || alpha <= 0.005) return;
  const [x, y] = pw(px, py);
  gl.depthMask(false);
  drawPart(PLANE, M4.trs(x, y, z, 0, 0, 0, [wpx / PX, hpx / PX, 1]), { tex: tex(key, make, true), col: color, alpha, mask: true });
  gl.depthMask(true);
}
function clipBand(y0, y1) { if (NODRAW) return; const k = cv.height / H; gl.enable(gl.SCISSOR_TEST); gl.scissor(0, Math.round((H - y1) * k), cv.width, Math.round((y1 - y0) * k)); }
function unclip() { if (!NODRAW) gl.disable(gl.SCISSOR_TEST); }

// the selection glow: a soft ring that hugs the cartridge's outline and moves with it
function drawCartGlow(g, st, c1, c2) {
  if (NODRAW) return;
  const cm = cartModel(g.sys), M = M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, st.rz || 0, st.s);
  gl.disable(gl.CULL_FACE); gl.depthMask(false);
  drawPart(cm.ring, M, { hull: true, grow: 26 / (PX * st.s), col: c1, emit: c2, alpha: .95 * (st.alpha == null ? 1 : st.alpha) });
  gl.enable(gl.CULL_FACE); gl.depthMask(true);
}
function clipRect(x0, y0, x1, y1) { if (NODRAW) return; const k = cv.height / H; gl.enable(gl.SCISSOR_TEST); gl.scissor(Math.round(x0 * k), Math.round((H - y1) * k), Math.round((x1 - x0) * k), Math.round((y1 - y0) * k)); }

// an orthographic camera matching the pixel grid at z = 0, for the flat Library view
function setFlat() { const hw = W / 2 / PX, hh = H / 2 / PX; VP = new Float32Array([1 / hw, 0, 0, 0, 0, 1 / hh, 0, 0, 0, 0, -.02, 0, 0, 0, 0, 1]); EYE = [0, 0, CAMZ]; }
