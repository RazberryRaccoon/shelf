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
let MESH, BG, STARS, PLANET, FIELDS;
function initGL() {
  MESH = shader(`
attribute vec3 aPos; attribute vec3 aNor; attribute vec2 aUV, aUV2;
uniform mat4 uVP, uM; uniform float uGrow; varying vec3 vN, vW, vL; varying vec2 vUV, vUV2;
void main() { vL = aPos; vUV2 = aUV2; vec4 w = uM * vec4(aPos + aNor * uGrow * aUV.y, 1.0); vW = w.xyz; vN = mat3(uM) * aNor; vUV = aUV; gl_Position = uVP * w; }`, `
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec3 vN, vW, vL; varying vec2 vUV, vUV2;
uniform sampler2D uTex, uAO; uniform float uTexOn, uAlpha, uSpec, uBright, uMode, uTime, uGrain, uGloss, uAOOn; uniform vec3 uColor, uRim, uEye, uEmit;
float hsh(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 45.164))) * 43758.5453); }
float vnoise(vec3 p) {   // smooth value noise, for a fine molded-plastic texture
  vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(hsh(i), hsh(i + vec3(1, 0, 0)), f.x), mix(hsh(i + vec3(0, 1, 0)), hsh(i + vec3(1, 1, 0)), f.x), f.y),
             mix(mix(hsh(i + vec3(0, 0, 1)), hsh(i + vec3(1, 0, 1)), f.x), mix(hsh(i + vec3(0, 1, 1)), hsh(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
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
  float specK = uSpec, ao = mix(1.0, texture2D(uAO, vUV2).r, uAOOn);   // soft shadows baked in Blender
  base *= ao; specK *= 0.4 + 0.6 * ao;
  if (uGrain > 0.001) {
    vec3 q = vL * 140.0; float g1 = vnoise(q), g2 = vnoise(q + 31.7), g3 = vnoise(q + 63.1);
    n = normalize(n + (vec3(g1, g2, g3) - 0.5) * 0.09 * uGrain);
    base *= 1.0 + (g1 - 0.5) * 0.035 * uGrain; specK *= 1.0 - 0.25 * uGrain * g2;
  }
  vec3 L = normalize(vec3(-0.45, 0.75, 0.55)), L2 = normalize(vec3(0.7, 0.1, 0.6));
  vec3 V = normalize(uEye - vW);
  float d = max(dot(n, L), 0.0), d2 = max(dot(n, L2), 0.0);
  float s = pow(max(dot(n, normalize(L + V)), 0.0), 36.0) * specK + pow(max(dot(n, normalize(L2 + V)), 0.0), 60.0) * specK * 0.25;
  float rim = pow(1.0 - max(dot(n, V), 0.0), 2.5);
  vec3 c = base * (0.18 + 0.66 * d + 0.12 * d2 + 0.26 * max(dot(n, V), 0.0)) * uBright + vec3(s) + uRim * rim * 0.55 + uEmit;
  float glossA = 0.0;
  if (uGloss > 0.001) {   // a soft studio reflection for glossy and clear plastic
    vec3 R = reflect(-V, n);
    vec3 env = mix(vec3(0.04, 0.045, 0.07), vec3(0.75, 0.78, 0.9), smoothstep(-0.25, 0.85, R.y)) + vec3(1.2) * pow(max(dot(R, normalize(vec3(-0.35, 0.6, 0.72))), 0.0), 50.0);
    float fres = 0.05 + 0.95 * pow(1.0 - max(dot(n, V), 0.0), 4.0);
    c += env * uGloss * fres * 0.7; glossA = clamp(dot(env, vec3(0.33)) * fres * uGloss, 0.0, 1.0);
  }
  float a = uAlpha * (uTexOn > 0.5 ? tc.a : 1.0);
  gl_FragColor = vec4(c, a + (1.0 - a) * glossA * min(1.0, uAlpha * 8.0));
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
  FIELDS = shader(`
attribute vec2 aP; varying vec2 vUV; void main() { vUV = aP * 0.5 + 0.5; gl_Position = vec4(aP, 0.0, 1.0); }`, `
precision mediump float; varying vec2 vUV; uniform vec3 uTop, uBot, uC1, uC2, uC3; uniform float uT, uAmt;
vec3 blob(vec3 col, vec3 c, vec2 p, float r) { vec2 d = (vUV - p) * vec2(1.78, 1.0); return mix(col, c, uAmt * exp(-dot(d, d) / (r * r))); }
void main() {
  vec3 col = mix(uBot, uTop, vUV.y);
  col = blob(col, uC1, vec2(0.25 + 0.08 * sin(uT * 0.05), 0.75 + 0.06 * cos(uT * 0.04)), 0.55);
  col = blob(col, uC2, vec2(0.80 + 0.07 * cos(uT * 0.045), 0.30 + 0.07 * sin(uT * 0.05)), 0.6);
  col = blob(col, uC3, vec2(0.55 + 0.10 * sin(uT * 0.035 + 2.0), 0.95 + 0.05 * sin(uT * 0.04)), 0.45);
  gl_FragColor = vec4(col, 1.0);
}`);
  PLANET = shader(`
attribute vec2 aP; uniform vec2 uC; uniform float uR; varying vec2 vL;
void main() { vL = aP * 1.3; vec2 px = uC + vL * uR; gl_Position = vec4(px.x / 667.0 - 1.0, 1.0 - px.y / 375.0, 0.0, 1.0); }`, `
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
function mesh(pos, nor, uv, idx, uv2) {
  const b = k => { const x = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, x); gl.bufferData(gl.ARRAY_BUFFER, k instanceof Float32Array ? k : new Float32Array(k), gl.STATIC_DRAW); return x; };
  const ib = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(idx), gl.STATIC_DRAW);
  return { p: b(pos), n: b(nor), t: b(uv), t2: uv2 ? b(uv2) : null, i: ib, count: idx.length };
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
  if (m.t2) { gl.enableVertexAttribArray(a.aUV2); gl.bindBuffer(gl.ARRAY_BUFFER, m.t2); gl.vertexAttribPointer(a.aUV2, 2, gl.FLOAT, false, 0, 0); }
  else { gl.disableVertexAttribArray(a.aUV2); gl.vertexAttrib2f(a.aUV2, 0, 0); }
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, m.i);
}
function drawPart(m, model, o) {
  if (NODRAW) return;
  const u = MESH.u;
  gl.uniformMatrix4fv(u.uM, false, model);
  gl.uniform1f(u.uTexOn, o.tex ? 1 : 0);
  if (o.tex) { gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, o.tex); gl.uniform1i(u.uTex, 0); }
  gl.uniform3fv(u.uColor, o.col || [1, 1, 1]); gl.uniform1f(u.uAlpha, (o.alpha == null ? 1 : o.alpha) * UNDER); gl.uniform1f(u.uSpec, o.spec || 0);
  gl.uniform1f(u.uBright, o.bright == null ? 1 : o.bright); gl.uniform1f(u.uGrain, o.grain || 0); gl.uniform1f(u.uGloss, o.gloss || 0);
  gl.uniform1f(u.uAOOn, o.ao ? 1 : 0); if (o.ao) { gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, o.ao); gl.uniform1i(u.uAO, 1); gl.activeTexture(gl.TEXTURE0); } gl.uniform3fv(u.uEmit, o.emit || [0, 0, 0]); gl.uniform1f(u.uMode, o.hull ? 2 : o.mask ? 1 : 0); gl.uniform1f(u.uGrow, o.grow || 0);
  bindMesh(m); gl.drawElements(gl.TRIANGLES, m.count, gl.UNSIGNED_SHORT, 0);
}
function beginMeshes() {
  if (NODRAW) return;
  gl.useProgram(MESH.p); const a = MESH.a;
  gl.enableVertexAttribArray(a.aPos); gl.enableVertexAttribArray(a.aNor); gl.enableVertexAttribArray(a.aUV);
  gl.uniformMatrix4fv(MESH.u.uVP, false, VP); gl.uniform3fv(MESH.u.uEye, EYE); gl.uniform3fv(MESH.u.uRim, RIM); gl.uniform1f(MESH.u.uTime, S.time);
  gl.enable(gl.DEPTH_TEST); gl.enable(gl.CULL_FACE); gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
}
function endMeshes() { if (NODRAW) return; const a = MESH.a; gl.disableVertexAttribArray(a.aNor); gl.disableVertexAttribArray(a.aUV); gl.disableVertexAttribArray(a.aUV2); gl.disable(gl.DEPTH_TEST); gl.disable(gl.CULL_FACE); }
/* ---------- the backdrop ---------- */
const MOOD = { cur: 'home', prev: 'home', t: 1 };
function moodColors(key) { return SYS[key] ? SYS[key] : { planet: ['#e6dcff', '#8b78e6', '#2b1f66'], neb: ['#2a1a5e', '#0d0b2c'], accent: getComputedStyle(stage).getPropertyValue('--theme-accent').trim() || '#c2b6ff' }; }
function setMood(key) {
  if (key === MOOD.cur) return;
  MOOD.prev = MOOD.cur; MOOD.cur = key; MOOD.t = 0; applyAccent();
}
function applyAccent() {   // the accent follows the console in view, or stays the theme's own
  const own = getComputedStyle(stage).getPropertyValue('--theme-accent').trim(), acc = P.prefs.accentFollows && SYS[MOOD.cur] ? SYS[MOOD.cur].accent : own;
  stage.style.setProperty('--accent', acc); RIM_T = hexRgb(acc);
}
let RIM_T = [0.6, 0.5, 1];
const THEMES = {
  space:  { name: 'Space', stars: 1, space: true },
  dark:   { name: 'Dark', top: '#050506', bot: '#000000', c: ['#121216', '#0b0b0e', '#101013'], amt: .6, stars: 0 },
  light:  { name: 'Light', top: '#f7f8fb', bot: '#eceef3', c: ['#ffffff', '#e4e8f2', '#f2f0f7'], amt: .7, stars: 0 },
  aurora: { name: 'Aurora', top: '#04070f', bot: '#060b12', c: ['#0f6a5a', '#3a2a7a', '#0b4a6a'], amt: .55, stars: .5 },
  dawn:   { name: 'Dawn', top: '#fff4ec', bot: '#f8ddd2', c: ['#ffd2b8', '#f6b6c4', '#ffe7c4'], amt: .65, stars: 0 },
  slate:  { name: 'Slate', top: '#232831', bot: '#191c22', c: ['#2c323d', '#3a3127', '#262b34'], amt: .5, stars: 0 },
  bloom:  { name: 'Bloom', top: '#f7f2ff', bot: '#efe9fb', c: ['#ffc1df', '#bfe0ff', '#c8f5dc'], amt: .75, stars: 0 },
};
function drawBackdrop(time, opts) {
  if (NODRAW) return;
  const T = THEMES[P.prefs.theme] || THEMES.space;
  gl.disable(gl.DEPTH_TEST); gl.disable(gl.CULL_FACE);
  if (T.space) {
    gl.useProgram(BG.p);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex('neb' + MOOD.prev, () => nebula(moodColors(MOOD.prev).neb, hash(MOOD.prev)))); gl.uniform1i(BG.u.uA, 0);
    gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, tex('neb' + MOOD.cur, () => nebula(moodColors(MOOD.cur).neb, hash(MOOD.cur)))); gl.uniform1i(BG.u.uB, 1);
    gl.uniform1f(BG.u.uMix, ease(MOOD.t)); gl.uniform1f(BG.u.uBright, opts.bright);
    gl.bindBuffer(gl.ARRAY_BUFFER, SCREENQ); gl.enableVertexAttribArray(BG.a.aP); gl.vertexAttribPointer(BG.a.aP, 2, gl.FLOAT, false, 0, 0);
    gl.drawArrays(gl.TRIANGLES, 0, 6); gl.activeTexture(gl.TEXTURE0);
  } else {
    gl.useProgram(FIELDS.p); const u = FIELDS.u;
    gl.uniform3fv(u.uTop, hexRgb(T.top)); gl.uniform3fv(u.uBot, hexRgb(T.bot));
    // one of the fields takes the current console's color, softly, when accents follow the console
    const acc = hexRgb(moodColors(MOOD.cur).accent), c1 = hexRgb(T.c[0]), follow = P.prefs.accentFollows && MOOD.cur !== 'home' ? .3 : 0;
    gl.uniform3fv(u.uC1, c1.map((v, i) => v * (1 - follow) + acc[i] * follow)); gl.uniform3fv(u.uC2, hexRgb(T.c[1])); gl.uniform3fv(u.uC3, hexRgb(T.c[2]));
    gl.uniform1f(u.uT, time); gl.uniform1f(u.uAmt, T.amt * opts.bright);
    gl.bindBuffer(gl.ARRAY_BUFFER, SCREENQ); gl.enableVertexAttribArray(FIELDS.a.aP); gl.vertexAttribPointer(FIELDS.a.aP, 2, gl.FLOAT, false, 0, 0);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
  }
  gl.enable(gl.BLEND);
  if (T.stars) {
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
    gl.useProgram(STARS.p);
    gl.uniform2f(STARS.u.uPan, opts.pan, 0); gl.uniform1f(STARS.u.uT, time); gl.uniform1f(STARS.u.uScale, cv.width / W); gl.uniform1f(STARS.u.uBright, opts.bright * T.stars);
    gl.bindBuffer(gl.ARRAY_BUFFER, STARBUF.b);
    const a = STARS.a; [['aP', 2, 0], ['aS', 1, 2], ['aD', 1, 3], ['aPh', 1, 4]].forEach(([n, k, off]) => { gl.enableVertexAttribArray(a[n]); gl.vertexAttribPointer(a[n], k, gl.FLOAT, false, 20, off * 4); });
    gl.drawArrays(gl.POINTS, 0, STARBUF.n);
    ['aS', 'aD', 'aPh'].forEach(n => gl.disableVertexAttribArray(a[n]));
  }
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  if (T.space && opts.planet) {
    const p = opts.planet;
    if (MOOD.t < 1) drawPlanet(MOOD.prev, p.x, p.y, p.r, time, opts.bright * (1 - ease(MOOD.t)) * p.a);
    drawPlanet(MOOD.cur, p.x, p.y, p.r, time, opts.bright * (MOOD.t < 1 ? ease(MOOD.t) : 1) * p.a);
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
function clipBand(y0, y1) { if (NODRAW) return; const k = cv.height / H; y0 += SHEETOFF; y1 += SHEETOFF; gl.enable(gl.SCISSOR_TEST); gl.scissor(0, Math.round((H - y1) * k), cv.width, Math.round((y1 - y0) * k)); }
function unclip() { if (!NODRAW) gl.disable(gl.SCISSOR_TEST); }

// the selection glow: a soft ring that hugs the cartridge's outline and moves with it
function drawCartGlow(g, st, c1, c2) {
  if (NODRAW) return;
  const cm = cartModel(g.sys), M = M4.trs(st.x, st.y, st.z, st.rx || 0, st.ry || 0, st.rz || 0, st.s);
  gl.disable(gl.CULL_FACE); gl.depthMask(false);
  drawPart(cm.ring, M, { hull: true, grow: 26 / (PX * st.s), col: c1, emit: c2, alpha: .95 * (st.alpha == null ? 1 : st.alpha) });
  gl.enable(gl.CULL_FACE); gl.depthMask(true);
}
function clipRect(x0, y0, x1, y1) { if (NODRAW) return; const k = cv.height / H; y0 += SHEETOFF; y1 += SHEETOFF; gl.enable(gl.SCISSOR_TEST); gl.scissor(Math.round(x0 * k), Math.round((H - y1) * k), Math.round((x1 - x0) * k), Math.round((y1 - y0) * k)); }

// an orthographic camera matching the pixel grid at z = 0, for the flat Library view
function setFlat() { const hw = W / 2 / PX, hh = H / 2 / PX; VP = new Float32Array([1 / hw, 0, 0, 0, 0, 1 / hh, 0, 0, 0, 0, -.02, 0, 0, 0, 0, 1]); EYE = [0, 0, CAMZ]; }
