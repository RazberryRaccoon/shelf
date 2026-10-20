// Procedural art, ported from the prototype, plus PNG loading with libpng.
#include "art.h"
#include "models.h"
#include "text.h"
#include <math.h>
#include <png.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

uint32_t hash_str(const char *s) { uint32_t h = 2166136261u; while (*s) { h ^= (uint8_t)*s++; h *= 16777619u; } return h; }
typedef struct { uint32_t a; } rng_t;
static float rnd(rng_t *r) {   // mulberry32, as in the prototype
    uint32_t t = (r->a += 0x6D2B79F5u); t = (t ^ (t >> 15)) * (t | 1); t ^= t + (t ^ (t >> 7)) * (t | 61); return ((t ^ (t >> 14)) >> 0) / 4294967296.f;
}
static void hsl(float h, float s, float l, float out[3]) {
    h = fmodf(h, 360) / 360; float q = l < .5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
    for (int i = 0; i < 3; i++) { float t = h + (1 - i) / 3.f; if (t < 0) t += 1; if (t > 1) t -= 1;
        out[i] = t < 1 / 6.f ? p + (q - p) * 6 * t : t < .5f ? q : t < 2 / 3.f ? p + (q - p) * (2 / 3.f - t) * 6 : p; }
}
static void put(uint8_t *px, const float c[3], float a) {   // blend a color into one pixel
    for (int i = 0; i < 3; i++) px[i] = (uint8_t)(px[i] + (c[i] * 255 - px[i]) * a);
    px[3] = 255;
}

GLuint art_planet(const float cols[3][3], uint32_t seed) {
    int W = 512, H = 256; uint8_t *img = malloc(W * H * 4); rng_t r = { seed };
    float bands[9][3]; for (int i = 0; i < 9; i++) { bands[i][0] = rnd(&r) * 6 + 2; bands[i][1] = rnd(&r) * 6.28f; bands[i][2] = rnd(&r) * .5f + .2f; }
    for (int y = 0; y < H; y++) {
        float t = 0; for (int i = 0; i < 9; i++) t += sinf(y / (float)H * bands[i][0] * 3.14f + bands[i][1]) * bands[i][2];
        t = t * .25f + .5f;
        for (int x = 0; x < W; x++) {
            float wob = sinf(x / (float)W * 6.283f * 3 + y * .05f) * .05f + sinf(x / (float)W * 6.283f * 7 + y * .13f) * .025f;
            float k = clampf(t + wob, 0, 1); const float *c1 = k < .5f ? cols[2] : cols[1], *c2 = k < .5f ? cols[1] : cols[0]; float m = k < .5f ? k * 2 : (k - .5f) * 2;
            uint8_t *p = img + (y * W + x) * 4; for (int i = 0; i < 3; i++) p[i] = (uint8_t)((c1[i] + (c2[i] - c1[i]) * m) * 255); p[3] = 255;
        }
    }
    GLuint t = tex_rgba(W, H, img, false); free(img); return t;
}
GLuint art_nebula(const float cols[2][3], uint32_t seed) {
    int W = 480, H = 270; uint8_t *img = malloc(W * H * 4); rng_t r = { seed };
    for (int i = 0; i < W * H; i++) { img[i * 4] = 7; img[i * 4 + 1] = 11; img[i * 4 + 2] = 30; img[i * 4 + 3] = 255; }
    for (int b = 0; b < 7; b++) {   // soft colored clouds
        float cx = rnd(&r) * W, cy = rnd(&r) * H, rad = (100 + rnd(&r) * 190), a = .55f + rnd(&r) * .35f; const float *c = cols[b % 2];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float d = hypotf(x - cx, y - cy) / rad; if (d >= 1) continue;
            put(img + (y * W + x) * 4, c, a * (1 - d));   // fades out toward the edge, so clouds blend without seams
        }
    }
    GLuint t = tex_rgba(W, H, img, false); free(img); return t;
}

/* ---------- labels ---------- */
uint8_t *png_load(const char *path, int *w, int *h) {
    png_image im; memset(&im, 0, sizeof im); im.version = PNG_IMAGE_VERSION;
    if (!png_image_begin_read_from_file(&im, path)) return NULL;
    im.format = PNG_FORMAT_RGBA; uint8_t *buf = malloc(PNG_IMAGE_SIZE(im));
    if (!png_image_finish_read(&im, NULL, buf, 0, NULL)) { free(buf); return NULL; }
    *w = (int)im.width; *h = (int)im.height; return buf;
}
bool png_save(const char *path, const uint8_t *rgba, int w, int h) {
    png_image im; memset(&im, 0, sizeof im); im.version = PNG_IMAGE_VERSION; im.width = w; im.height = h; im.format = PNG_FORMAT_RGBA;
    return png_image_write_to_file(&im, path, 0, rgba, 0, NULL) != 0;
}
// fill a dst_w x dst_h image from src, cropped to fill (like CSS object-fit: cover), with simple box filtering
static uint8_t *cover(const uint8_t *src, int sw, int sh, int dw, int dh) {
    uint8_t *d = malloc((size_t)dw * dh * 4); float sc = fmaxf((float)dw / sw, (float)dh / sh), ox = (sw * sc - dw) / 2, oy = (sh * sc - dh) / 2;
    for (int y = 0; y < dh; y++) for (int x = 0; x < dw; x++) {
        float fx0 = (x + ox) / sc, fy0 = (y + oy) / sc, fx1 = (x + 1 + ox) / sc, fy1 = (y + 1 + oy) / sc; float acc[4] = { 0 }; int n = 0;
        for (int sy = (int)fy0; sy < (int)ceilf(fy1) && sy < sh; sy++) for (int sx = (int)fx0; sx < (int)ceilf(fx1) && sx < sw; sx++) {
            const uint8_t *p = src + ((size_t)sy * sw + sx) * 4; for (int i = 0; i < 4; i++) acc[i] += p[i]; n++; }
        for (int i = 0; i < 4; i++) d[((size_t)y * dw + x) * 4 + i] = n ? (uint8_t)(acc[i] / n) : 0;
    }
    return d;
}
GLuint art_label(int sys, const char *title, const char *png_path) {
    cart_t *c = cart_model(sys); float aspect = c->label[2] / c->label[3];
    int W = 512, H = (int)lroundf(512 / aspect);
    if (png_path) {   // the real label, from the label cropper or the art download
        int w, h; uint8_t *src = png_load(png_path, &w, &h);
        if (src) { uint8_t *img = cover(src, w, h, W, H); GLuint t = tex_rgba(W, H, img, true); free(src); free(img); return t; }
    }
    // a stand-in: a little landscape and the title, in colors picked from the title
    uint8_t *img = malloc((size_t)W * H * 4); rng_t r = { hash_str(title) * 31u + 7 };
    float hue = rnd(&r) * 360, sky1[3], sky2[3], sun[3], far[3], mid[3], near[3];
    hsl(hue, .55f, .16f, sky1); hsl(hue + 40, .7f, .56f + rnd(&r) * .08f, sky2); hsl(hue + 180, .9f, .72f, sun);
    hsl(hue + 15, .4f, .34f, far); hsl(hue + 5, .42f, .24f, mid); hsl(hue + 350, .45f, .12f, near);
    for (int y = 0; y < H; y++) { float t = y / (float)H; float col[3]; for (int i = 0; i < 3; i++) col[i] = sky1[i] + (sky2[i] - sky1[i]) * t;
        for (int x = 0; x < W; x++) { uint8_t *p = img + ((size_t)y * W + x) * 4; for (int i = 0; i < 3; i++) p[i] = (uint8_t)(col[i] * 255); p[3] = 255; } }
    for (int i = 0; i < 70; i++) { int x = (int)(rnd(&r) * W), y = (int)(rnd(&r) * H * .6f); float a = rnd(&r) * .8f + .2f; const float w3[3] = { 1, 1, 1 }; put(img + ((size_t)y * W + x) * 4, w3, a); }
    float sr = fminf(W, H) * (.12f + rnd(&r) * .08f), sx = W * (.25f + rnd(&r) * .5f), sy = H * (.25f + rnd(&r) * .15f);
    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
        float d = hypotf(x - sx, y - sy); uint8_t *p = img + ((size_t)y * W + x) * 4; const float w3[3] = { 1, 1, 1 };
        if (d < sr * 3) put(p, w3, .35f * (1 - d / (sr * 3)));
        if (d < sr) put(p, sun, 1);
    }
    // three ridges of hills, jagged in the back, soft in front
    const float *layers[3] = { far, mid, near }; float bases[3] = { .62f, .78f, .92f }, amps[3] = { .3f, .22f, .12f };
    for (int l = 0; l < 3; l++) {
        float ph = rnd(&r) * 6.28f, jag = l < 2 ? 1 : 0;
        for (int x = 0; x < W; x++) {
            float u = x / (float)W, peak = jag ? fabsf(fmodf(u * 7 + ph, 2) - 1) : (sinf(u * 20 + ph) * .5f + .5f);
            int top = (int)(H * bases[l] - peak * amps[l] * H);
            for (int y = top < 0 ? 0 : top; y < H; y++) put(img + ((size_t)y * W + x) * 4, layers[l], 1);
        }
    }
    for (int y = (int)(H * .55f); y < H; y++) { float a = .7f * (y - H * .55f) / (H * .45f); const float dark[3] = { 8 / 255.f, 6 / 255.f, 24 / 255.f };
        for (int x = 0; x < W; x++) put(img + ((size_t)y * W + x) * 4, dark, a); }
    // the title: up to two lines near the bottom, white with a dark outline
    float size = fminf(W, H) * .13f; char lines[2][96] = { "", "" }; int nl = 1;
    for (;;) {
        nl = 1; lines[0][0] = lines[1][0] = 0; char buf[192]; snprintf(buf, sizeof buf, "%s", title);
        for (char *w = strtok(buf, " "); w; w = strtok(NULL, " ")) {
            char trial[96]; snprintf(trial, sizeof trial, "%s%s%s", lines[nl - 1], lines[nl - 1][0] ? " " : "", w);
            if (lines[nl - 1][0] && text_width(trial, FONT_DISPLAY, 600, size) > W * .86f) { if (nl == 2) { nl = 3; break; } nl++; snprintf(lines[1], 96, "%s", w); }
            else snprintf(lines[nl - 1], 96, "%s", trial);
        }
        bool fits = nl <= 2 && text_width(lines[0], FONT_DISPLAY, 600, size) <= W * .86f && text_width(lines[1], FONT_DISPLAY, 600, size) <= W * .86f;
        if (fits || size < 12) break;
        size *= .9f;
    }
    if (nl > 2) nl = 2;
    for (int li = 0; li < nl; li++) {
        int tw, th; uint8_t *a = text_raster(lines[li], FONT_DISPLAY, 600, size, &tw, &th);
        int x0 = (W - tw) / 2, y0 = (int)(H - size * (nl > 1 ? 2.5f : 1.5f) + li * size * 1.08f - size * .2f), rad = (int)(size * .08f) + 1;
        for (int y = -rad; y < th + rad; y++) for (int x = -rad; x < tw + rad; x++) {
            int X = x0 + x, Y = y0 + y; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
            float o = 0;   // outline: the strongest coverage nearby
            for (int dy = -rad; dy <= rad; dy += rad) for (int dx = -rad; dx <= rad; dx += rad) { int sx2 = x + dx, sy2 = y + dy; if (sx2 >= 0 && sy2 >= 0 && sx2 < tw && sy2 < th) o = fmaxf(o, a[sy2 * tw + sx2] / 255.f); }
            const float dark[3] = { 10 / 255.f, 8 / 255.f, 30 / 255.f }, white[3] = { 1, 1, 1 };
            uint8_t *p = img + ((size_t)Y * W + X) * 4; put(p, dark, o * .75f);
            if (x >= 0 && y >= 0 && x < tw && y < th) put(p, white, a[y * tw + x] / 255.f);
        }
        free(a);
    }
    GLuint t = tex_rgba(W, H, img, true); free(img); return t;
}
