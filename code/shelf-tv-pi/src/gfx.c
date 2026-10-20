// The renderer, ported from the browser prototype (WebGL 1 is OpenGL ES 2.0).
#include "gfx.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

float PX, GTIME; mat4 VP; float EYE[3] = { 0, 0, CAMZ }; float RIM[3] = { .6f, .5f, 1 };
static int SW, SH;   // the real screen size
mesh_t PLANE;
static GLuint quad_buf, unit_buf, star_buf; static int star_count;

/* ---------- matrices (column major) ---------- */
mat4 m4_mul(mat4 a, mat4 b) {
    mat4 o;
    for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) { float s = 0; for (int k = 0; k < 4; k++) s += a.m[k * 4 + r] * b.m[c * 4 + k]; o.m[c * 4 + r] = s; }
    return o;
}
mat4 m4_persp(float fovy, float asp, float n, float f) {
    mat4 o = { { 0 } }; float t = 1 / tanf(fovy / 2);
    o.m[0] = t / asp; o.m[5] = t; o.m[10] = (f + n) / (n - f); o.m[11] = -1; o.m[14] = 2 * f * n / (n - f);
    return o;
}
// translate, rotate (Y, then X, then Z) and scale
mat4 m4_trs(float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
    float cx = cosf(rx), snx = sinf(rx), cy = cosf(ry), sny = sinf(ry), cz = cosf(rz), snz = sinf(rz);
    float r00 = cy * cz + sny * snx * snz, r01 = -cy * snz + sny * snx * cz, r02 = sny * cx;
    float r10 = cx * snz, r11 = cx * cz, r12 = -snx;
    float r20 = -sny * cz + cy * snx * snz, r21 = sny * snz + cy * snx * cz, r22 = cy * cx;
    mat4 o = { { r00 * sx, r10 * sx, r20 * sx, 0, r01 * sy, r11 * sy, r21 * sy, 0, r02 * sz, r12 * sz, r22 * sz, 0, x, y, z, 1 } };
    return o;
}
void m4_apply(const mat4 *m, const float p[3], float o[3]) {
    for (int i = 0; i < 3; i++) o[i] = m->m[i] * p[0] + m->m[4 + i] * p[1] + m->m[8 + i] * p[2] + m->m[12 + i];
}
void px_to_world(float px, float py, float *x, float *y) { *x = (px - VW / 2.f) / PX; *y = -(py - VH / 2.f) / PX; }

#define FOV (30.f * 3.14159265f / 180.f)
void cam_perspective(float camy, float camz, float looky) {
    mat4 proj = m4_persp(FOV, (float)VW / VH, .1f, 100);
    float pitch = atan2f(camy - looky, camz);
    mat4 view = m4_mul(m4_trs(0, 0, 0, pitch, 0, 0, 1, 1, 1), m4_trs(0, -camy, -camz, 0, 0, 0, 1, 1, 1));
    VP = m4_mul(proj, view); EYE[0] = 0; EYE[1] = camy; EYE[2] = camz;
}
void cam_flat(void) {   // no perspective: world units map straight onto the canvas
    float hw = VW / 2.f / PX, hh = VH / 2.f / PX;
    mat4 o = { { 1 / hw, 0, 0, 0, 0, 1 / hh, 0, 0, 0, 0, -.02f, 0, 0, 0, 0, 1 } };
    VP = o; EYE[0] = 0; EYE[1] = 0; EYE[2] = CAMZ;
}

/* ---------- shaders ---------- */
typedef struct { GLuint p; GLint u[24]; } prog_t;
static GLuint compile(GLenum type, const char *src) {
    GLuint s = glCreateShader(type); glShaderSource(s, 1, &src, NULL); glCompileShader(s);
    GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[2048]; glGetShaderInfoLog(s, sizeof log, NULL, log); fprintf(stderr, "shader error: %s\n", log); exit(1); }
    return s;
}
static prog_t program(const char *vs, const char *fs, const char **uniforms) {
    prog_t P = { glCreateProgram(), { 0 } };
    glAttachShader(P.p, compile(GL_VERTEX_SHADER, vs)); glAttachShader(P.p, compile(GL_FRAGMENT_SHADER, fs));
    glBindAttribLocation(P.p, 0, "aPos"); glBindAttribLocation(P.p, 1, "aNor"); glBindAttribLocation(P.p, 2, "aUV");
    glLinkProgram(P.p);
    GLint ok; glGetProgramiv(P.p, GL_LINK_STATUS, &ok);
    if (!ok) { char log[2048]; glGetProgramInfoLog(P.p, sizeof log, NULL, log); fprintf(stderr, "link error: %s\n", log); exit(1); }
    for (int i = 0; uniforms[i]; i++) P.u[i] = glGetUniformLocation(P.p, uniforms[i]);
    return P;
}
static prog_t MESH, BG, STARS, PLANET, UI;
enum { M_VP, M_M, M_TEX, M_TEXON, M_ALPHA, M_SPEC, M_BRIGHT, M_MODE, M_TIME, M_COLOR, M_RIM, M_EYE, M_EMIT, M_GROW };
static const char *MESH_U[] = { "uVP", "uM", "uTex", "uTexOn", "uAlpha", "uSpec", "uBright", "uMode", "uTime", "uColor", "uRim", "uEye", "uEmit", "uGrow", NULL };
static const char *MESH_VS =
    "attribute vec3 aPos; attribute vec3 aNor; attribute vec2 aUV;\n"
    "uniform mat4 uVP, uM; uniform float uGrow; varying vec3 vN, vW, vL; varying vec2 vUV;\n"
    "void main() { vL = aPos; vec4 w = uM * vec4(aPos + aNor * uGrow * aUV.y, 1.0); vW = w.xyz; vN = mat3(uM) * aNor; vUV = aUV; gl_Position = uVP * w; }\n";
static const char *MESH_FS =
    "precision mediump float;\n"
    "varying vec3 vN, vW, vL; varying vec2 vUV;\n"
    "uniform sampler2D uTex; uniform float uTexOn, uAlpha, uSpec, uBright, uMode, uTime; uniform vec3 uColor, uRim, uEye, uEmit;\n"
    "void main() {\n"
    "  if (uMode > 1.5) {   // selection glow: a soft ring, brighter toward the light, breathing slowly as a whole\n"
    "    float k = 0.5 + 0.5 * sin(atan(vL.y, vL.x) + 0.8);\n"
    "    float pulse = 0.86 + 0.14 * sin(uTime * 1.3);\n"
    "    float fade = pow(1.0 - vUV.y, 1.8);\n"
    "    gl_FragColor = vec4(mix(uColor, uEmit, k), uAlpha * fade * (0.55 + 0.45 * k) * pulse); return;\n"
    "  }\n"
    "  vec4 tc = texture2D(uTex, vUV);\n"
    "  if (uMode > 0.5) { gl_FragColor = vec4(uColor, tc.a * uAlpha); return; }\n"
    "  vec3 n = normalize(vN);\n"
    "  vec3 base = uTexOn > 0.5 ? tc.rgb : uColor;\n"
    "  vec3 L = normalize(vec3(-0.45, 0.75, 0.55));\n"
    "  vec3 V = normalize(uEye - vW);\n"
    "  float d = max(dot(n, L), 0.0);\n"
    "  float s = pow(max(dot(n, normalize(L + V)), 0.0), 36.0) * uSpec;\n"
    "  float rim = pow(1.0 - max(dot(n, V), 0.0), 2.5);\n"
    "  vec3 c = base * (0.20 + 0.70 * d + 0.30 * max(dot(n, V), 0.0)) * uBright + vec3(s) + uRim * rim * 0.55 + uEmit;\n"
    "  gl_FragColor = vec4(c, uAlpha);\n"
    "}\n";
static const char *BG_U[] = { "uA", "uB", "uMix", "uBright", NULL };
static const char *BG_VS = "attribute vec2 aPos; varying vec2 vUV; void main() { vUV = aPos * 0.5 + 0.5; vUV.y = 1.0 - vUV.y; gl_Position = vec4(aPos, 0.0, 1.0); }\n";
static const char *BG_FS = "precision mediump float; varying vec2 vUV; uniform sampler2D uA, uB; uniform float uMix, uBright;\n"
    "void main() { gl_FragColor = vec4(mix(texture2D(uA, vUV).rgb, texture2D(uB, vUV).rgb, uMix) * uBright, 1.0); }\n";
static const char *STARS_U[] = { "uPan", "uT", "uScale", "uBright", NULL };
static const char *STARS_VS =   // aNor carries each star's size, depth and twinkle phase
    "attribute vec2 aPos; attribute vec3 aNor; uniform vec2 uPan; uniform float uT, uScale, uBright; varying float vA;\n"
    "void main() {\n"
    "  vec2 p = fract(aPos + uPan * aNor.y) * 2.0 - 1.0; p.y = -p.y;\n"
    "  vA = (0.55 + 0.45 * sin(uT * (0.6 + aNor.z) + aNor.z * 20.0)) * uBright * (0.4 + 0.6 * aNor.y);\n"
    "  gl_PointSize = aNor.x * uScale; gl_Position = vec4(p, 0.0, 1.0);\n"
    "}\n";
static const char *STARS_FS = "precision mediump float; varying float vA;\n"
    "void main() { float r = length(gl_PointCoord - 0.5) * 2.0; gl_FragColor = vec4(vec3(0.93, 0.95, 1.0), vA * smoothstep(1.0, 0.2, r)); }\n";
static const char *PLANET_U[] = { "uC", "uR", "uTex", "uRot", "uAlpha", "uAtm", NULL };
static const char *PLANET_VS = "attribute vec2 aPos; uniform vec2 uC; uniform float uR; varying vec2 vL;\n"
    "void main() { vL = aPos * 1.3; vec2 px = uC + vL * uR; gl_Position = vec4(px.x / 960.0 - 1.0, 1.0 - px.y / 540.0, 0.0, 1.0); }\n";
static const char *PLANET_FS =
    "precision mediump float; varying vec2 vL; uniform sampler2D uTex; uniform float uRot, uAlpha; uniform vec3 uAtm;\n"
    "void main() {\n"
    "  float r = length(vL);\n"
    "  vec3 L = normalize(vec3(-0.55, 0.55, 0.62));\n"
    "  if (r < 1.0) {\n"
    "    vec3 n = vec3(vL.x, -vL.y, sqrt(1.0 - r * r));\n"
    "    float c = cos(0.35), s = sin(0.35);\n"
    "    vec3 m = vec3(n.x * c - n.y * s, n.x * s + n.y * c, n.z);\n"
    "    float lon = atan(m.x, m.z) / 6.28318 + uRot, lat = acos(clamp(m.y, -1.0, 1.0)) / 3.14159;\n"
    "    vec3 col = texture2D(uTex, vec2(fract(lon), lat)).rgb;\n"
    "    float d = smoothstep(-0.15, 0.6, dot(n, L));\n"
    "    float edge = pow(1.0 - n.z, 3.0);\n"
    "    vec3 o = col * (0.06 + 0.94 * d) + uAtm * edge * (0.25 + 0.75 * d);\n"
    "    gl_FragColor = vec4(o + uAtm * smoothstep(0.97, 1.0, r) * 0.55, uAlpha);\n"
    "  } else {\n"
    "    float lit = clamp(dot(normalize(vec3(vL.x, -vL.y, 0.0)), L) * 0.5 + 0.5, 0.0, 1.0);\n"
    "    float g = exp(-(r - 1.0) * 14.0) * (0.15 + 0.85 * lit);\n"
    "    gl_FragColor = vec4(uAtm, g * 0.55 * uAlpha);\n"
    "  }\n"
    "}\n";
// 2D: rounded rectangles with smooth edges, and images or text, in canvas pixels
static const char *UI_U[] = { "uRect", "uRadius", "uColor", "uMode", "uTex", "uAA", "uSize", NULL };   // uSize: the fragment half gets its own copy of the size
static const char *UI_VS = "attribute vec2 aPos; uniform vec4 uRect; varying vec2 vP; varying vec2 vUV;\n"
    "void main() { vUV = aPos; vP = aPos * uRect.zw; vec2 px = uRect.xy + vP; gl_Position = vec4(px.x / 960.0 - 1.0, 1.0 - px.y / 540.0, 0.0, 1.0); }\n";
static const char *UI_FS =
    "precision mediump float; varying vec2 vP; varying vec2 vUV; uniform vec4 uColor; uniform vec2 uSize; uniform float uRadius, uMode, uAA; uniform sampler2D uTex;\n"
    "void main() {\n"
    "  if (uMode > 1.5) { vec4 t = texture2D(uTex, vUV); gl_FragColor = vec4(t.rgb * uColor.rgb, t.a * uColor.a); return; }\n"
    "  if (uMode > 0.5) { gl_FragColor = vec4(uColor.rgb, texture2D(uTex, vUV).a * uColor.a); return; }\n"
    "  vec2 h = uSize * 0.5, q = abs(vP - h) - (h - vec2(uRadius));\n"
    "  float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;\n"
    "  gl_FragColor = vec4(uColor.rgb, uColor.a * clamp(0.5 - d / uAA, 0.0, 1.0));\n"
    "}\n";

/* ---------- buffers and textures ---------- */
mesh_t mesh_make(const float *pos, const float *nor, const float *uv, int nv, const uint16_t *idx, int ni) {
    mesh_t m = { 0 };
    glGenBuffers(1, &m.pos); glBindBuffer(GL_ARRAY_BUFFER, m.pos); glBufferData(GL_ARRAY_BUFFER, nv * 12, pos, GL_STATIC_DRAW);
    glGenBuffers(1, &m.nor); glBindBuffer(GL_ARRAY_BUFFER, m.nor); glBufferData(GL_ARRAY_BUFFER, nv * 12, nor, GL_STATIC_DRAW);
    glGenBuffers(1, &m.uv); glBindBuffer(GL_ARRAY_BUFFER, m.uv); glBufferData(GL_ARRAY_BUFFER, nv * 8, uv, GL_STATIC_DRAW);
    glGenBuffers(1, &m.idx); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m.idx); glBufferData(GL_ELEMENT_ARRAY_BUFFER, ni * 2, idx, GL_STATIC_DRAW);
    m.count = ni; return m;
}
static GLuint tex_upload(int w, int h, GLenum fmt, const uint8_t *data) {
    GLuint t; glGenTextures(1, &t); glBindTexture(GL_TEXTURE_2D, t);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, fmt, w, h, 0, fmt, GL_UNSIGNED_BYTE, data);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return t;
}
GLuint tex_rgba(int w, int h, const uint8_t *rgba, bool flip) {
    if (!flip) return tex_upload(w, h, GL_RGBA, rgba);
    uint8_t *f = malloc((size_t)w * h * 4);   // a model's texture coordinates start at the bottom row
    for (int y = 0; y < h; y++) memcpy(f + (size_t)y * w * 4, rgba + (size_t)(h - 1 - y) * w * 4, (size_t)w * 4);
    GLuint t = tex_upload(w, h, GL_RGBA, f); free(f); return t;
}
GLuint tex_alpha(int w, int h, const uint8_t *a) { return tex_upload(w, h, GL_ALPHA, a); }

void gfx_init(int w, int h) {
    SW = w; SH = h;
    PX = (VH / 2.f) / (CAMZ * tanf(FOV / 2));
    MESH = program(MESH_VS, MESH_FS, MESH_U); BG = program(BG_VS, BG_FS, BG_U);
    STARS = program(STARS_VS, STARS_FS, STARS_U); PLANET = program(PLANET_VS, PLANET_FS, PLANET_U); UI = program(UI_VS, UI_FS, UI_U);
    float quad[] = { -1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1 }, unit[] = { 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1 };
    glGenBuffers(1, &quad_buf); glBindBuffer(GL_ARRAY_BUFFER, quad_buf); glBufferData(GL_ARRAY_BUFFER, sizeof quad, quad, GL_STATIC_DRAW);
    glGenBuffers(1, &unit_buf); glBindBuffer(GL_ARRAY_BUFFER, unit_buf); glBufferData(GL_ARRAY_BUFFER, sizeof unit, unit, GL_STATIC_DRAW);
    float pp[] = { -.5f, -.5f, 0, .5f, -.5f, 0, .5f, .5f, 0, -.5f, .5f, 0 }, pn[] = { 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1 }, pu[] = { 0, 0, 1, 0, 1, 1, 0, 1 };
    uint16_t pi[] = { 0, 1, 2, 0, 2, 3 };
    PLANE = mesh_make(pp, pn, pu, 4, pi, 6);
    star_count = 520; float *st = malloc(star_count * 5 * sizeof(float)); uint32_t r = 99;
    #define RND() (((r = r * 1664525u + 1013904223u) >> 8) / 16777216.f)
    for (int i = 0; i < star_count; i++) { float d = RND(); st[i * 5] = RND(); st[i * 5 + 1] = RND(); float k = RND(); st[i * 5 + 2] = 1.2f + k * k * k * 2.8f; st[i * 5 + 3] = .15f + d * .85f; st[i * 5 + 4] = RND(); }
    glGenBuffers(1, &star_buf); glBindBuffer(GL_ARRAY_BUFFER, star_buf); glBufferData(GL_ARRAY_BUFFER, star_count * 20, st, GL_STATIC_DRAW); free(st);
}
void gfx_begin_frame(void) {
    glViewport(0, 0, SW, SH); glClearColor(.027f, .043f, .118f, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
}

/* ---------- 3D ---------- */
void meshes_begin(void) {
    glUseProgram(MESH.p);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1); glEnableVertexAttribArray(2);
    glUniformMatrix4fv(MESH.u[M_VP], 1, GL_FALSE, VP.m); glUniform3fv(MESH.u[M_EYE], 1, EYE); glUniform3fv(MESH.u[M_RIM], 1, RIM); glUniform1f(MESH.u[M_TIME], GTIME);
    glUniform1i(MESH.u[M_TEX], 0);
    glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); glEnable(GL_CULL_FACE); glCullFace(GL_BACK); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}
void meshes_end(void) { glDisableVertexAttribArray(1); glDisableVertexAttribArray(2); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); }
static GLuint white_tex;
void draw_mesh(const mesh_t *m, mat4 model, const draw_opts *o) {
    static const float white[3] = { 1, 1, 1 }, zero[3] = { 0, 0, 0 };
    if (!white_tex) { uint8_t w[4] = { 255, 255, 255, 255 }; white_tex = tex_rgba(1, 1, w, false); }
    glUniformMatrix4fv(MESH.u[M_M], 1, GL_FALSE, model.m);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, o->tex ? o->tex : white_tex);
    glUniform1f(MESH.u[M_TEXON], o->tex && o->mode == MODE_LIT ? 1 : 0);
    glUniform3fv(MESH.u[M_COLOR], 1, o->col ? o->col : white);
    glUniform1f(MESH.u[M_ALPHA], o->alpha); glUniform1f(MESH.u[M_SPEC], o->spec); glUniform1f(MESH.u[M_BRIGHT], o->bright);
    glUniform3fv(MESH.u[M_EMIT], 1, o->emit ? o->emit : zero); glUniform1f(MESH.u[M_MODE], (float)o->mode); glUniform1f(MESH.u[M_GROW], o->grow);
    glBindBuffer(GL_ARRAY_BUFFER, m->pos); glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, 0);
    glBindBuffer(GL_ARRAY_BUFFER, m->nor); glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 0, 0);
    glBindBuffer(GL_ARRAY_BUFFER, m->uv); glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m->idx); glDrawElements(GL_TRIANGLES, m->count, GL_UNSIGNED_SHORT, 0);
}
void clip_rect(float x0, float y0, float x1, float y1) {
    float k = (float)SH / VH; glEnable(GL_SCISSOR_TEST);
    glScissor((int)(x0 * k), (int)((VH - y1) * k), (int)((x1 - x0) * k), (int)((y1 - y0) * k));
}
void unclip(void) { glDisable(GL_SCISSOR_TEST); }

/* ---------- the backdrop ---------- */
void draw_backdrop(GLuint a, GLuint b, float mix, float bright, float pan) {
    glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
    glUseProgram(BG.p);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, a); glUniform1i(BG.u[0], 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, b); glUniform1i(BG.u[1], 1);
    glUniform1f(BG.u[2], mix); glUniform1f(BG.u[3], bright);
    glBindBuffer(GL_ARRAY_BUFFER, quad_buf); glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glActiveTexture(GL_TEXTURE0);
    glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE);
    glUseProgram(STARS.p);
    glUniform2f(STARS.u[0], pan, 0); glUniform1f(STARS.u[1], GTIME); glUniform1f(STARS.u[2], (float)SW / VW); glUniform1f(STARS.u[3], bright);
    glBindBuffer(GL_ARRAY_BUFFER, star_buf);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 20, 0); glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 20, (void *)8);
    glDrawArrays(GL_POINTS, 0, star_count);
    glDisableVertexAttribArray(1);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}
void draw_planet(GLuint surface, const float atm[3], float cx, float cy, float r, float rot, float alpha) {
    glUseProgram(PLANET.p); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, surface); glUniform1i(PLANET.u[2], 0);
    glUniform2f(PLANET.u[0], cx, cy); glUniform1f(PLANET.u[1], r); glUniform1f(PLANET.u[3], rot); glUniform1f(PLANET.u[4], alpha); glUniform3fv(PLANET.u[5], 1, atm);
    glBindBuffer(GL_ARRAY_BUFFER, quad_buf); glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 6);
}

/* ---------- 2D ---------- */
void ui_begin(void) {
    glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(UI.p); glUniform1f(UI.u[5], (float)VW / SW); glUniform1i(UI.u[4], 0); glActiveTexture(GL_TEXTURE0);
    glBindBuffer(GL_ARRAY_BUFFER, unit_buf); glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
}
void ui_rect(float x, float y, float w, float h, float radius, const float c[4]) {
    glUniform4f(UI.u[0], x, y, w, h); glUniform2f(UI.u[6], w, h); glUniform1f(UI.u[1], fminf(radius, fminf(w, h) / 2)); glUniform4fv(UI.u[2], 1, c); glUniform1f(UI.u[3], 0);
    glDrawArrays(GL_TRIANGLES, 0, 6);
}
void ui_image(GLuint tex, float x, float y, float w, float h, const float c[4], bool alpha_tex) {
    glBindTexture(GL_TEXTURE_2D, tex);
    glUniform4f(UI.u[0], x, y, w, h); glUniform4fv(UI.u[2], 1, c); glUniform1f(UI.u[3], alpha_tex ? 1 : 2);
    glDrawArrays(GL_TRIANGLES, 0, 6);
}
