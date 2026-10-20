// Builds the backdrop layers once. Uses only its own pixel writes (no gfx
// target), so it can run on the second core while the boot animation plays.
#include <math.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "backdrop.h"
#include "art.h"
#include "board.h"
#include "shelf_system.h"

const mood_t MOODS[SYS_COUNT] = {
    [SYS_NES] = {.bg = {0x06071a, 0x0c0b22, 0x170f2c}, .glow = 0x2e1634, .accent = 0xff7a90,
                 .planet = {0x9a4452, 0x4e1e36, 0x1c0b1e}, .rim = 0xffb4a6, .dust = 0x211a38, .has_dust = true},
    [SYS_GB] = {.bg = {0x05090c, 0x0a1410, 0x12221a}, .glow = 0x1a3020, .accent = 0xc4d98b,
                .planet = {0x8a8e98, 0x454852, 0x1c1e26}, .rim = 0xf4f6ff, .craters = true},
    [SYS_GBC] = {.bg = {0x080720, 0x100d36, 0x1a134c}, .glow = 0x22226a, .accent = 0x7ad8ff,
                 .planet = {0x2e9aa0, 0x1a4e70, 0x0c1636}, .rim = 0xb8f6ff, .nebula = {0x6a3ac8, 0xc0489c}, .has_nebula = true},
};
backdrop_t BACKDROPS[SYS_COUNT];
static volatile bool building;

typedef struct { uint32_t a; } rng_t;
static float rnd(rng_t *r)
{
    uint32_t t;
    r->a += 0x6D2B79F5u;
    t = r->a;
    t = (t ^ (t >> 15)) * (1u | t);
    t = (t + ((t ^ (t >> 7)) * (61u | t))) ^ t;
    return ((t ^ (t >> 14)) & 0xFFFFFF) / 16777216.0f;
}

static inline void put(image_t *im, int x, int y, uint16_t c) { if (x >= 0 && y >= 0 && x < im->w && y < im->h) im->px[y * im->w + x] = c; }
static inline float clampf(float v, float a, float b) { return v < a ? a : v > b ? b : v; }

static void gradient(image_t *im, const uint32_t *stops, int n)
{
    uint32_t steps[16];
    int k = 0;
    for (int i = 0; i < n - 1; i++) for (int j = 0; j < 4; j++) steps[k++] = mixh(stops[i], stops[i + 1], j / 4.0f);
    steps[k++] = stops[n - 1];
    int last = k - 1;
    for (int y = 0; y < im->h; y++) {
        float t = (float)y / (im->h - 1) * last;
        int i = (int)t; if (i > last - 1) i = last - 1;
        float f = t - i;
        uint16_t a = RGB(steps[i]), b = RGB(steps[i + 1]);
        for (int x = 0; x < im->w; x++) im->px[y * im->w + x] = dither(x, y, f) ? b : a;
    }
}

static void build(sys_t s)
{
    const mood_t *M = &MOODS[s];
    backdrop_t *B = &BACKDROPS[s];
    rng_t R = {shelf_hash(SYS_DIR[s]) ^ 0x5eed};
    if (!B->bg) B->bg = img_new(320, 240);
    if (!B->planet) B->planet = img_new(320, 240 - PLANET_Y);
    if (!B->bg || !B->planet) return;
    image_t *g = B->bg;
    gradient(g, M->bg, 3);
    if (M->has_dust) {   // a faint band of dust across the sky
        uint16_t c = RGB(M->dust);
        for (int y = 0; y < 180; y++) for (int x = 0; x < 320; x++) {
            float d = fabsf((y - 24) - (x - 40) * 0.32f) / 26.0f;
            if (d < 1 && dither(x, y, (1 - d) * 0.45f * (0.6f + 0.4f * sinf(x * 0.05f)))) put(g, x, y, c);
        }
    }
    if (M->has_nebula) {   // soft nebula wisps
        const float blobs[4][4] = {{70, 46, 50, 0}, {120, 26, 36, 1}, {256, 64, 44, 0}, {214, 26, 30, 1}};
        uint16_t nc[2] = {RGB(mixh(M->nebula[0], M->bg[1], 0.45f)), RGB(mixh(M->nebula[1], M->bg[1], 0.45f))};
        for (int y = 0; y < 180; y++) for (int x = 0; x < 320; x++) {
            float v = 0, best = 0;
            int ci = 0;
            for (int b = 0; b < 4; b++) {
                float dx = x - blobs[b][0], dy = (y - blobs[b][1]) * 1.6f, r = blobs[b][2];
                float e = expf(-(dx * dx + dy * dy) / (r * r));
                v += e;
                if (e > best) { best = e; ci = (int)blobs[b][3]; }
            }
            if (dither(x, y, fminf(1, v) * 0.38f)) put(g, x, y, nc[ci]);
        }
    }
    uint16_t glow = RGB(M->glow);   // a quiet spotlight behind the selected cartridge
    for (int y = 0; y < 200; y++) for (int x = 30; x < 290; x++) {
        float d = hypotf((x - 160) / 1.25f, (float)(y - CY)) / 105.0f;
        if (d < 1 && dither(x, y, (1 - d) * 0.5f)) put(g, x, y, glow);
    }

    // the planet rising along the bottom edge
    image_t *P = B->planet;
    img_clear(P, KEY);
    const float PX = 160, PY = 442, PR = 262;
    float craters[16][3];
    for (int k = 0; k < 16; k++) { craters[k][0] = rnd(&R) * 320; craters[k][1] = 186 + rnd(&R) * 54; craters[k][2] = 3 + rnd(&R) * 9; }
    uint32_t haze = mixh(M->rim, M->bg[2], 0.55f);
    for (int y = 150; y < 240; y++) for (int x = 0; x < 320; x++) {
        float dx = x - PX, dy = y - PY, dist = sqrtf(dx * dx + dy * dy);
        if (dist <= PR) {
            float depth = PR - dist, t = fminf(1, fminf(1, depth / 46) * 0.8f + (x / 320.0f) * 0.2f);
            uint32_t c = t < 0.5f ? mixh(M->planet[0], M->planet[1], t * 2) : mixh(M->planet[1], M->planet[2], (t - 0.5f) * 2);
            if (!M->craters) {
                float band = sinf((dy + dx * 0.12f) * 0.32f + sinf(dx * 0.03f) * 2);
                if (band > 0.55f) c = mixh(c, M->planet[0], 0.16f); else if (band < -0.7f) c = mixh(c, M->planet[2], 0.2f);
            } else for (int k = 0; k < 16; k++) {
                float ex = (x - craters[k][0]) / craters[k][2], ey = (y - craters[k][1]) / (craters[k][2] * 0.45f);
                if (ex * ex + ey * ey < 1) { c = mixh(c, M->planet[2], 0.35f); if (ey > 0.55f) c = mixh(c, M->planet[0], 0.5f); }
            }
            if (depth < 1.2f) c = M->rim; else if (depth < 3 && dither(x, y, 0.5f)) c = mixh(c, M->rim, 0.5f);
            uint16_t px = RGB(c);
            if (px == KEY) px ^= 1;
            put(P, x, y - PLANET_Y, px);
        } else if (dist <= PR + 9 && dither(x, y, (1 - (dist - PR) / 9) * 0.5f)) put(P, x, y - PLANET_Y, RGB(haze));
    }
    for (int k = 0; k < STARS; k++) {
        static const float Z[3] = {0.18f, 0.35f, 0.6f};
        B->star[k].x = rnd(&R) * 320; B->star[k].y = 4 + rnd(&R) * 176; B->star[k].z = Z[k % 3]; B->star[k].tw = rnd(&R) * 6.28f;
    }
    if (s == SYS_GB && PREFS.gb_green) { img_dmgify(B->bg, 0.3f); img_dmgify(B->planet, 0.9f); }
    B->ready = true;
}

static void build_task(void *arg)
{
    for (int s = 0; s < SYS_COUNT; s++) build(s);
    building = false;
    vTaskDelete(NULL);
}

void backdrops_build_async(void)
{
    building = true;
    xTaskCreatePinnedToCore(build_task, "backdrops", 6144, NULL, 3, NULL, 1);
}
bool backdrops_ready(void) { return !building; }
void backdrop_rebuild(sys_t s) { BACKDROPS[s].ready = false; build(s); }
