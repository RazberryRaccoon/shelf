// Cartridge models: flat outlines pushed out to a depth, plus label windows sized to the real labels.
#include "models.h"
#include <math.h>
#include <stdlib.h>
#include <string.h>

typedef struct { float x, y; } pt;
#define MAXPTS 128

// outline of a w x h shape with per-corner radius (bottom left, bottom right, top right, top left);
// a negative radius cuts the corner off instead of rounding it
static int shape(float w, float h, const float r[4], pt *out) {
    int n = 0; float hw = w / 2, hh = h / 2;
    float cxs[4] = { -hw, hw, hw, -hw }, cys[4] = { -hh, -hh, hh, hh }, sx[4] = { 1, -1, -1, 1 }, sy[4] = { 1, 1, -1, -1 };
    float a0[4] = { 3.14159265f, 4.71238898f, 0, 1.57079633f };
    for (int c = 0; c < 4; c++) {
        float R = fabsf(r[c]);
        if (r[c] < 0) {   // a cut corner: two points
            // the point before the corner, then the point after, going counter-clockwise
            if (c == 0) { out[n++] = (pt){ -hw, -hh + R }; out[n++] = (pt){ -hw + R, -hh }; }
            if (c == 1) { out[n++] = (pt){ hw - R, -hh }; out[n++] = (pt){ hw, -hh + R }; }
            if (c == 2) { out[n++] = (pt){ hw, hh - R }; out[n++] = (pt){ hw - R, hh }; }
            if (c == 3) { out[n++] = (pt){ -hw + R, hh }; out[n++] = (pt){ -hw, hh - R }; }
        } else {
            float ccx = cxs[c] + sx[c] * R, ccy = cys[c] + sy[c] * R;
            for (int k = 0; k <= 5; k++) { float a = a0[c] + k / 5.f * 1.57079633f; out[n++] = (pt){ ccx + cosf(a) * R, ccy + sinf(a) * R }; }
        }
    }
    // drop duplicates (zero-radius corners), comparing against an untouched copy
    pt src[MAXPTS]; memcpy(src, out, n * sizeof(pt));
    int m = 0;
    for (int i = 0; i < n; i++) { pt q = src[(i + n - 1) % n]; if (fabsf(src[i].x - q.x) + fabsf(src[i].y - q.y) > 1e-5f) out[m++] = src[i]; }
    return m;
}
static int circle(float r, int seg, pt *out) { for (int i = 0; i < seg; i++) { float a = i / (float)seg * 6.2831853f; out[i] = (pt){ cosf(a) * r, sinf(a) * r }; } return seg; }

// push a convex, counter-clockwise outline out to a depth, with smooth sides on rounded corners
static mesh_t extrude(const pt *p, int n, float depth) {
    int nv = (n + 1) * 2 + n * 4, ni = n * 6 + n * 6;
    float *pos = malloc(nv * 12), *nor = malloc(nv * 12), *uv = malloc(nv * 8); uint16_t *idx = malloc(ni * 2);
    int v = 0, k = 0; float z = depth / 2, cx = 0, cy = 0, minx = 1e9f, maxx = -1e9f, miny = 1e9f, maxy = -1e9f;
    for (int i = 0; i < n; i++) { cx += p[i].x / n; cy += p[i].y / n; minx = fminf(minx, p[i].x); maxx = fmaxf(maxx, p[i].x); miny = fminf(miny, p[i].y); maxy = fmaxf(maxy, p[i].y); }
    #define V(px_, py_, pz_, nx_, ny_, nz_, u_, w_) do { pos[v*3]=px_; pos[v*3+1]=py_; pos[v*3+2]=pz_; nor[v*3]=nx_; nor[v*3+1]=ny_; nor[v*3+2]=nz_; uv[v*2]=u_; uv[v*2+1]=w_; v++; } while (0)
    for (int f = 0; f < 2; f++) {
        float zz = f ? -z : z, nz = f ? -1 : 1; int start = v;
        V(cx, cy, zz, 0, 0, nz, (cx - minx) / (maxx - minx), (cy - miny) / (maxy - miny));
        for (int i = 0; i < n; i++) V(p[i].x, p[i].y, zz, 0, 0, nz, (p[i].x - minx) / (maxx - minx), (p[i].y - miny) / (maxy - miny));
        for (int i = 0; i < n; i++) { int a = start + 1 + i, b = start + 1 + (i + 1) % n;
            idx[k++] = start; if (!f) { idx[k++] = a; idx[k++] = b; } else { idx[k++] = b; idx[k++] = a; } }
    }
    float (*en)[2] = malloc(n * sizeof *en);
    for (int i = 0; i < n; i++) { pt q = p[(i + 1) % n]; float dx = q.x - p[i].x, dy = q.y - p[i].y, l = hypotf(dx, dy); if (l == 0) l = 1; en[i][0] = dy / l; en[i][1] = -dx / l; }
    for (int i = 0; i < n; i++) {
        int j = (i + 1) % n;
        float na[2], nb[2];
        for (int s = 0; s < 2; s++) {   // smooth the normal across gentle corners, keep it sharp across hard ones
            int vi = s ? j : i; float *a = en[(vi - 1 + n) % n], *b = en[vi], *out = s ? nb : na;
            if (a[0] * b[0] + a[1] * b[1] < .7f) { out[0] = en[i][0]; out[1] = en[i][1]; }
            else { float x = a[0] + b[0], y = a[1] + b[1], l = hypotf(x, y); out[0] = x / l; out[1] = y / l; }
        }
        int s = v;
        V(p[i].x, p[i].y, z, na[0], na[1], 0, 0, 0); V(p[i].x, p[i].y, -z, na[0], na[1], 0, 0, 1);
        V(p[j].x, p[j].y, -z, nb[0], nb[1], 0, 1, 1); V(p[j].x, p[j].y, z, nb[0], nb[1], 0, 1, 0);
        idx[k++] = s; idx[k++] = s + 1; idx[k++] = s + 2; idx[k++] = s; idx[k++] = s + 2; idx[k++] = s + 3;
    }
    mesh_t m = mesh_make(pos, nor, uv, v, idx, k);
    free(pos); free(nor); free(uv); free(idx); free(en);
    return m;
}
// a flat ring around an outline: inner edge on the outline (uv.y = 0), outer edge pushed out in the shader (uv.y = 1)
static mesh_t ring_mesh(const pt *p, int n) {
    float *pos = malloc((n + 1) * 2 * 12), *nor = malloc((n + 1) * 2 * 12), *uv = malloc((n + 1) * 2 * 8); uint16_t *idx = malloc(n * 6 * 2);
    float (*en)[2] = malloc(n * sizeof *en); int v = 0, k = 0;
    for (int i = 0; i < n; i++) { pt q = p[(i + 1) % n]; float dx = q.x - p[i].x, dy = q.y - p[i].y, l = hypotf(dx, dy); if (l == 0) l = 1; en[i][0] = dy / l; en[i][1] = -dx / l; }
    for (int i = 0; i <= n; i++) {
        int c = i % n; float *a = en[(c - 1 + n) % n], *b = en[c], x = a[0] + b[0], y = a[1] + b[1], l = hypotf(x, y); if (l == 0) l = 1; x /= l; y /= l;
        for (int e = 0; e < 2; e++) { pos[v*3] = p[c].x; pos[v*3+1] = p[c].y; pos[v*3+2] = 0; nor[v*3] = x; nor[v*3+1] = y; nor[v*3+2] = 0; uv[v*2] = i / (float)n; uv[v*2+1] = (float)e; v++; }
        if (i < n) { int s = i * 2; idx[k++] = s; idx[k++] = s + 1; idx[k++] = s + 3; idx[k++] = s; idx[k++] = s + 3; idx[k++] = s + 2; }
    }
    mesh_t m = mesh_make(pos, nor, uv, v, idx, k);
    free(pos); free(nor); free(uv); free(idx); free(en);
    return m;
}

/* ---------- the shells ---------- */
// Label windows match the part of each real label that shows on the front (one unit is 100 mm):
// NES 55 x 90 mm, SNES 82.5 x 37, Genesis 74 x 60, Game Boy and Color 42 x 37, Game Boy Advance 43 x 22.
static cart_t CARTS[SYS_COUNT] = {
    { .w = 1.20f, .h = 1.34f, .d = .17f, .label = { 0, .155f, .55f, .90f } },
    { .w = 1.30f, .h = .95f, .d = .20f, .label = { 0, .22f, .825f, .37f } },
    { .w = .98f, .h = 1.34f, .d = .22f, .label = { 0, .29f, .74f, .60f } },
    { .w = .60f, .h = .66f, .d = .09f, .label = { 0, -.06f, .42f, .37f } },
    { .w = .60f, .h = .66f, .d = .09f, .label = { 0, -.06f, .42f, .37f } },
    { .w = .60f, .h = .38f, .d = .085f, .label = { 0, .02f, .43f, .22f } },
};
static void add(cart_t *c, mesh_t mesh, float x, float y, float z, float sx, float sy, unsigned col, float spec, float glass, bool label) {
    part_t *p = &c->parts[c->nparts++];
    p->mesh = mesh; p->m = m4_trs(x, y, z, 0, 0, 0, sx, sy, 1);
    p->col[0] = (col >> 16 & 255) / 255.f; p->col[1] = (col >> 8 & 255) / 255.f; p->col[2] = (col & 255) / 255.f;
    p->spec = spec; p->glass = glass; p->label = label;
}
static mesh_t box(float w, float h, float d, float r) { pt p[MAXPTS]; float rr[4] = { r, r, r, r }; int n = shape(w, h, rr, p); return extrude(p, n, d); }
cart_t *cart_model(int sys) {
    cart_t *c = &CARTS[sys]; if (c->ready) return c;
    pt out[MAXPTS]; int n; float f = c->d / 2;
    if (sys == SYS_NES) {   // "Slate": a tall label between two ribbed grips, a slot band and a round emblem
        n = shape(c->w, c->h, (float[]){ .025f, .025f, .025f, .025f }, out); add(c, extrude(out, n, c->d), 0, 0, 0, 1, 1, 0x8e8a86, .3f, 0, false);
        mesh_t rib = box(.02f, .62f, .016f, .008f);
        for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 6; i++) add(c, rib, s * (.34f + i * .042f), .2f, f + .004f, 1, 1, 0x6f6b67, .3f, 0, false);
        add(c, box(.9f, .08f, .014f, .03f), 0, -.47f, f + .004f, 1, 1, 0x5d5955, .3f, 0, false);
        pt cp[32]; int cn = circle(.04f, 18, cp); add(c, extrude(cp, cn, .02f), .45f, -.57f, f + .006f, 1, 1, 0xff9db6, .7f, 0, false);
    } else if (sys == SYS_SNES) {   // "Arc": an arched top, grip lines and an accent stripe
        n = shape(c->w, c->h, (float[]){ .05f, .05f, .34f, .34f }, out); add(c, extrude(out, n, c->d), 0, 0, 0, 1, 1, 0xb4b1c2, .3f, 0, false);
        mesh_t line = box(.8f, .022f, .016f, .008f);
        for (int i = 0; i < 3; i++) add(c, line, 0, -.2f - i * .05f, f + .004f, 1, 1, 0x9794a8, .3f, 0, false);
        add(c, box(1.02f, .03f, .016f, .01f), 0, -.43f, f + .004f, 1, 1, 0x8f7ee8, .6f, 0, false);
    } else if (sys == SYS_GEN) {    // "Monolith": glossy black, cut corners, three vents
        n = shape(c->w, c->h, (float[]){ .03f, .03f, -.14f, -.14f }, out); add(c, extrude(out, n, c->d), 0, 0, 0, 1, 1, 0x1f2026, .75f, 0, false);
        mesh_t vent = box(.5f, .028f, .016f, .01f);
        for (int i = 0; i < 3; i++) add(c, vent, 0, -.42f - i * .06f, f + .004f, 1, 1, 0x0b0b0f, .3f, 0, false);
    } else if (sys == SYS_GB) {     // "Pebble": soft, fully rounded, a single thumb dimple
        n = shape(c->w, c->h, (float[]){ .1f, .1f, .1f, .1f }, out); add(c, extrude(out, n, c->d), 0, 0, 0, 1, 1, 0xbcbbc3, .3f, 0, false);
        pt cp[32]; int cn = circle(.055f, 20, cp); add(c, extrude(cp, cn, .012f), 0, .25f, f + .002f, 1, 1, 0x8f8e97, .1f, 0, false);
    } else if (sys == SYS_GBC) {    // "Prism": a see-through tinted shell with the circuit board inside
        add(c, box(.5f, .52f, .01f, .02f), 0, 0, 0, 1, 1, 0x1d5b4c, .4f, 0, false);
        mesh_t chip = box(.12f, .06f, .02f, .005f);
        add(c, chip, -.12f, .2f, .015f, 1, 1, 0x0e1412, .5f, 0, false); add(c, chip, .1f, .2f, .015f, 1, 1, 0x0e1412, .5f, 0, false);
        n = shape(c->w, c->h, (float[]){ -.07f, -.07f, -.07f, -.07f }, out); add(c, extrude(out, n, c->d), 0, 0, 0, 1, 1, 0x38c7bd, .9f, .5f, false);
    } else {                        // "Wedge": small and wide, with a stepped connector tab
        n = shape(c->w, c->h, (float[]){ .03f, .03f, .09f, .09f }, out); add(c, extrude(out, n, c->d), 0, 0, 0, 1, 1, 0x3f3a7a, .4f, 0, false);
        add(c, box(.44f, .07f, .06f, .015f), 0, -c->h / 2 - .03f, 0, 1, 1, 0x2a2656, .3f, 0, false);
        mesh_t pin = box(.012f, .04f, .012f, .004f);
        for (int i = -2; i <= 2; i++) add(c, pin, i * .07f, -c->h / 2 - .03f, .034f, 1, 1, 0x8b84d8, .3f, 0, false);
    }
    static const unsigned frames[SYS_COUNT] = { 0x5d5955, 0x8f8ca0, 0x1e3570, 0x9a99a2, 0x17635c, 0x2a2656 };
    float *L = c->label;
    add(c, PLANE, L[0], L[1], f + .003f, L[2] + .05f, L[3] + .05f, frames[sys], .1f, 0, false);
    add(c, PLANE, L[0], L[1], f + .005f, L[2], L[3], 0xffffff, .25f, 0, true);
    c->ring = ring_mesh(out, n);
    c->ready = true; return c;
}
float cart_scale(int sys, float px) {
    cart_t *c = cart_model(sys);
    return px / PX / fmaxf(c->w, c->h) * (SYSINFO[sys].hand ? .86f : 1);
}
void draw_cart(int sys, GLuint label, const pose_t *p) {
    cart_t *c = cart_model(sys);
    mat4 M = m4_trs(p->x, p->y, p->z, p->rx, p->ry, 0, p->s, p->s, p->s);
    float emit[3] = { RIM[0] * p->glow * .08f, RIM[1] * p->glow * .08f, RIM[2] * p->glow * .08f };
    for (int i = 0; i < c->nparts; i++) {
        part_t *q = &c->parts[i];
        draw_opts o = { q->col, p->alpha * (q->glass > 0 ? q->glass : 1), q->spec, p->bright, emit, q->label ? label : 0, MODE_LIT, 0 };
        if (q->label && !label) o.col = (float[]){ .25f, .27f, .35f };
        draw_mesh(&q->mesh, m4_mul(M, q->m), &o);
    }
}
void draw_cart_glow(int sys, const pose_t *p, const float c1[3], const float c2[3]) {
    cart_t *c = cart_model(sys);
    mat4 M = m4_trs(p->x, p->y, p->z, p->rx, p->ry, 0, p->s, p->s, p->s);
    glDisable(GL_CULL_FACE); glDepthMask(GL_FALSE);
    draw_opts o = { c1, .95f * p->alpha, 0, 1, c2, 0, MODE_GLOW, 26 / (PX * p->s) };
    draw_mesh(&c->ring, M, &o);
    glEnable(GL_CULL_FACE); glDepthMask(GL_TRUE);
}
