#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <rg_system.h>
#include "art.h"
#include "shelf_system.h"
#include "board.h"

static uint32_t use_clock;

int cart_w(sys_t s) { return s == SYS_NES ? 132 : 112; }
int cart_h(sys_t s) { return s == SYS_NES ? 148 : 128; }
label_box_t cart_label_box(sys_t s)
{
    label_box_t nes = {13, 26, NES_LABEL_W, NES_LABEL_H}, gb = {11, 20, GB_LABEL_W, GB_LABEL_H};
    return s == SYS_NES ? nes : gb;
}

static const uint32_t DMG_HEX[4] = {0x142b1a, 0x355e30, 0x7fa13d, 0xc4d98b};   // the four classic greens
uint16_t dmg_color(int k) { return RGB(DMG_HEX[k & 3]); }
void img_dmgify(image_t *im, float kf)
{
    static float lut[256], lut_kf = -1;
    if (lut_kf != kf) {
        for (int i = 0; i < 256; i++) { float l = powf(i / 255.0f, 0.9f) * 1.06f * kf; lut[i] = (l > 1 ? 1 : l) * 3; }
        lut_kf = kf;
    }
    for (int y = 0; y < im->h; y++)
        for (int x = 0; x < im->w; x++) {
            uint16_t *p = &im->px[y * im->w + x];
            if (*p == KEY) continue;
            int r, g, b;
            unpack565(*p, &r, &g, &b);
            int lum = (77 * r + 151 * g + 28 * b) >> 8;
            float v = lut[lum > 255 ? 255 : lum];
            int k = (int)floorf(v) + (dither(x, y, v - floorf(v)) ? 1 : 0);
            if (k < 0) k = 0;
            if (k > 3) k = 3;
            *p = dmg_color(k);
        }
}

/* ---------- files ---------- */
// .img format: "SHL1", width (u16 LE), height (u16 LE), then RGB565 pixels (u16 LE)
image_t *art_load(const char *path)
{
    board_bus_lock();
    FILE *f = fopen(path, "rb");
    if (!f) { board_bus_unlock(); return NULL; }
    char magic[4];
    uint16_t wh[2];
    image_t *im = NULL;
    if (fread(magic, 1, 4, f) == 4 && !memcmp(magic, "SHL1", 4) && fread(wh, 2, 2, f) == 2 &&
        wh[0] > 0 && wh[1] > 0 && wh[0] <= 320 && wh[1] <= 240) {
        im = img_new(wh[0], wh[1]);
        if (im && fread(im->px, 2, im->w * im->h, f) != (size_t)(im->w * im->h)) { img_free(im); im = NULL; }
    }
    fclose(f);
    board_bus_unlock();
    if (!im) RG_LOGW("could not read %s", path);
    return im;
}
static image_t *load_art(game_t *g, const char *kind)
{
    char base[128], path[300];
    str_copy(base, sizeof(base), g->file);
    char *dot = strrchr(base, '.'); if (dot) *dot = 0;
    snprintf(path, sizeof(path), RG_STORAGE_ROOT "/shelf/art/%s/%s.%s.img", SYS_DIR[g->sys], base, kind);
    return art_load(path);
}

/* ---------- PNGs through retro-go: its cover art and save screenshots ---------- */
static image_t *load_png(const char *path, int max_w, int max_h)
{
    board_bus_lock();
    rg_image_t *src = rg_surface_load_image_file(path, 0);
    board_bus_unlock();
    if (!src) return NULL;
    float k = fminf(1.0f, fminf((float)max_w / src->width, (float)max_h / src->height));
    int w = (int)(src->width * k), h = (int)(src->height * k);
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    image_t *im = img_new(w, h);
    if (im) {
        const uint16_t *px = (const uint16_t *)src->data;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                uint16_t c = px[(y * src->height / h) * src->width + (x * src->width / w)];
                im->px[y * w + x] = c == KEY ? (KEY ^ 0x20) : c;
            }
    }
    rg_surface_free(src);
    return im;
}

image_t *art_thumb(game_t *g)
{
    if (g->thumb || !g->preview) return g->thumb;
    g->thumb = load_png(g->preview, 64, 48);
    if (g->thumb && g->sys == SYS_GB && PREFS.gb_green) img_dmgify(g->thumb, 1.0f);
    return g->thumb;
}

image_t *art_slot_thumb(game_t *g, int i)
{
    save_slot_t *s = &g->slot[i];
    if (s->thumb || !s->used || !s->preview) return s->thumb;
    s->thumb = load_png(s->preview, 64, 48);
    if (s->thumb && g->sys == SYS_GB && PREFS.gb_green) img_dmgify(s->thumb, 1.0f);
    return s->thumb;
}

void art_forget_slot_thumbs(game_t *g)
{
    for (int i = 0; i < SAVE_SLOTS; i++) { img_free(g->slot[i].thumb); g->slot[i].thumb = NULL; }
}

/* ---------- generated art for games without any ---------- */
static const uint32_t FALLBACK[5] = {0xb8325a, 0x2a7f9e, 0x6b4bb0, 0xc27a1e, 0x3f8f4f};
static void initials(const char *t, char *out)
{
    int n = 0;
    bool start = true;
    for (const char *p = t; *p && n < 2; p++) {
        if (*p == ' ') { start = true; continue; }
        if (start && ((*p >= 'A' && *p <= 'Z') || (*p >= 'a' && *p <= 'z') || (*p >= '0' && *p <= '9'))) {
            out[n++] = (*p >= 'a' && *p <= 'z') ? *p - 32 : *p;
        }
        start = false;
    }
    if (!n) out[n++] = '?';
    out[n] = 0;
}
static void pattern(image_t *im, uint32_t base)
{
    uint16_t a = RGB(base), b = RGB(mixh(base, 0xffffff, 0.16f));
    for (int y = 0; y < im->h; y++) for (int x = 0; x < im->w; x++) im->px[y * im->w + x] = (((x + y) >> 2) % 3 == 0) ? b : a;
}
static image_t *generated_label(game_t *g, int w, int h)
{
    image_t *im = img_new(w, h);
    if (!im) return NULL;
    uint32_t base = FALLBACK[shelf_hash(g->title) % 5];
    pattern(im, base);
    gfx_push_target(im);
    char ini[4], t[64];
    initials(g->title, ini);
    gfx_text(ini, w / 2 + 1, 12 + (h - 12) / 2 - 11, RGB(mixh(base, 0, 0.6f)), 3, AL_CENTER);
    gfx_text(ini, w / 2, 12 + (h - 12) / 2 - 12, 0xFFFF, 3, AL_CENTER);
    gfx_fill(0, 0, w, 12, RGB(0x16141c));
    gfx_ellipsize(g->title, w - 10, 1, t, sizeof(t));
    gfx_text(t, 5, 2, 0xFFFF, 1, AL_LEFT);
    gfx_pop_target();
    return im;
}
static image_t *generated_box(game_t *g)
{
    int w = g->sys == SYS_NES ? NES_BOX_W : GB_BOX_W, h = g->sys == SYS_NES ? NES_BOX_H : GB_BOX_H;
    image_t *im = img_new(w, h);
    if (!im) return NULL;
    uint32_t base = FALLBACK[shelf_hash(g->title) % 5];
    pattern(im, base);
    gfx_push_target(im);
    char ini[4], lines[3][64];
    initials(g->title, ini);
    gfx_text(ini, w / 2 + 2, h / 2 - 14, RGB(mixh(base, 0, 0.6f)), 4, AL_CENTER);
    gfx_text(ini, w / 2, h / 2 - 16, 0xFFFF, 4, AL_CENTER);
    int n = gfx_wrap(g->title, w - 12, 1, lines, 3), bh = n * 10 + 6;
    uint32_t band = g->sys == SYS_NES ? 0x16141c : g->sys == SYS_GB ? 0x2a2c3c : 0x6e1c44;
    gfx_fill(0, 0, w, bh, RGB(band));
    for (int i = 0; i < n; i++) gfx_text(lines[i], w / 2, 4 + i * 10, 0xFFFF, 1, AL_CENTER);
    gfx_pop_target();
    return im;
}

/* ---------- a heart sticker for favorites ---------- */
static const uint8_t HEART[5] = {0x0A, 0x1F, 0x1F, 0x0E, 0x04};
void draw_heart(int x, int y, uint16_t c)
{
    for (int r = 0; r < 5; r++) for (int k = 0; k < 5; k++) if (HEART[r] & (1 << (4 - k))) gfx_px(x + k, y + r, c);
}
void draw_sticker(int x, int y, uint16_t c)
{
    draw_heart(x - 1, y, 0xFFFF); draw_heart(x + 1, y, 0xFFFF); draw_heart(x, y - 1, 0xFFFF); draw_heart(x, y + 1, 0xFFFF);
    draw_heart(x, y, c);
}

/* ---------- boxes ---------- */
static void finish_box(image_t *im, game_t *g)
{
    gfx_push_target(im);
    gfx_fill_a(0, 0, im->w, 1, 0xFFFF, 64); gfx_fill_a(0, 0, 1, im->h, 0xFFFF, 64);
    gfx_fill_a(im->w - 1, 0, 1, im->h, 0, 100); gfx_fill_a(0, im->h - 1, im->w, 1, 0, 100);
    if (g->fav) draw_sticker(im->w - 12, im->h - 12, RGB(0xff4a6a));
    gfx_pop_target();
    if (g->sys == SYS_GB && PREFS.gb_green) img_dmgify(im, 1.0f);
}
image_t *art_box(game_t *g)
{
    if (g->box) { g->used = ++use_clock; return g->box; }
    image_t *im = load_art(g, "box");
    if (!im) {   // fall back to retro-go's cover art folder, if there's a cover there
        char base[128], path[300];
        str_copy(base, sizeof(base), g->file);
        char *dot = strrchr(base, '.'); if (dot) *dot = 0;
        snprintf(path, sizeof(path), "%s/%s/%s.png", RG_BASE_PATH_COVERS, SYS_DIR[g->sys], base);
        im = load_png(path, g->sys == SYS_NES ? NES_BOX_W : GB_BOX_W, g->sys == SYS_NES ? NES_BOX_H : GB_BOX_H);
    }
    if (im) {   // never larger than the frame for its system
        int mw = g->sys == SYS_NES ? NES_BOX_W : GB_BOX_W, mh = g->sys == SYS_NES ? NES_BOX_H : GB_BOX_H;
        if (im->w > mw || im->h > mh) {
            float s = fminf((float)mw / im->w, (float)mh / im->h);
            image_t *sm = img_new((int)(im->w * s), (int)(im->h * s));
            if (sm) { gfx_push_target(sm); gfx_blit_ex(im, 0, 0, sm->w, sm->h, 0, 0); gfx_pop_target(); img_free(im); im = sm; }
        }
    } else im = generated_box(g);
    if (!im) return NULL;
    finish_box(im, g);
    g->box = im;
    g->art_dmg = PREFS.gb_green;
    g->used = ++use_clock;
    return im;
}

/* ---------- cartridges: original shell designs ---------- */
static int corner_inset(int i, int r)
{
    if (r <= 0 || i >= r) return 0;
    float d = r - i - 0.5f;
    int v = r - (int)roundf(sqrtf(fmaxf(0, r * r - d * d)));
    return v > 0 ? v : 0;
}
static uint32_t shade(uint32_t c, float t, uint32_t to) { return mixh(c, to, t); }
static const uint32_t GBC_SHELLS[6] = {0x2a9d9a, 0x6b4bb0, 0xd06a2a, 0x8fbf3a, 0x3a3d4a, 0xb8325a};

static image_t *build_cart(game_t *g)
{
    bool home = g->sys == SYS_NES, clear = g->sys == SYS_GBC;
    int w = cart_w(g->sys), h = cart_h(g->sys);
    image_t *c = img_new(w, h);
    if (!c) return NULL;
    img_clear(c, KEY);
    uint32_t seed = shelf_hash(g->title);
    uint32_t body = home ? 0x85878f : g->sys == SYS_GB ? 0xaaabb3 : GBC_SHELLS[seed % 6];
    uint16_t mid = RGB(body), dark = RGB(shade(body, 0.32f, 0x0a0a14)), darker = RGB(shade(body, 0.55f, 0x0a0a14));
    uint16_t light = RGB(shade(body, 0.28f, 0xffffff)), lighter = RGB(shade(body, 0.5f, 0xffffff)), edge = RGB(shade(body, 0.74f, 0));
    int rt = 3, rb = home ? 3 : 7;
    int *ins = malloc(h * sizeof(int));
    for (int y = 0; y < h; y++) { int a = corner_inset(y, rt), b = corner_inset(h - 1 - y, rb); ins[y] = a > b ? a : b; }

    gfx_push_target(c);
    for (int y = 0; y < h; y++) gfx_fill(ins[y], y, w - 2 * ins[y], 1, mid);
    if (clear) {   // see-through shell: the board shows through the plastic
        int bx = 12, by = h - 40, bw = w - 24, bh = 34;
        gfx_fill(bx, by, bw, bh, RGB(mixh(0x1f5a3a, body, 0.45f)));
        gfx_fill(bx + 26, by + 6, 22, 12, RGB(mixh(0x14141c, body, 0.4f)));
        gfx_fill(bx + 56, by + 8, 14, 9, RGB(mixh(0x14141c, body, 0.4f)));
        for (int x = bx + 4; x < bx + bw - 4; x += 3) gfx_fill(x, by + bh - 7, 2, 6, RGB(mixh(0xe0b84a, body, 0.35f)));
        gfx_fill(bx + 6, by + 22, 40, 1, RGB(mixh(0x58a070, body, 0.5f)));
        gfx_fill(bx + 50, by + 4, 1, 14, RGB(mixh(0x58a070, body, 0.5f)));
        for (int y = by; y < by + bh; y++) for (int x = bx; x < bx + bw; x++) if (dither(x, y, 0.3f)) gfx_px(x, y, mid);
    }
    for (int y = 1; y < h - 1; y++) { gfx_px(ins[y] + 1, y, light); gfx_px(w - 2 - ins[y], y, dark); }
    gfx_fill(ins[1] + 1, 1, w - 2 * ins[1] - 2, 1, light);
    gfx_fill(ins[h - 2] + 1, h - 2, w - 2 * ins[h - 2] - 2, 1, dark);
    for (int y = 0; y < h; y++) {
        int a = ins[y], prev = y > 0 ? ins[y - 1] : a, next = y < h - 1 ? ins[y + 1] : a;
        int m = prev > next ? prev : next, span = m - a > 1 ? m - a : 1;
        gfx_fill(a, y, span, 1, edge);
        gfx_fill(w - a - span, y, span, 1, edge);
    }
    gfx_fill(ins[0], 0, w - 2 * ins[0], 1, edge);
    gfx_fill(ins[h - 1], h - 1, w - 2 * ins[h - 1], 1, edge);
    free(ins);

    // grip ridges
    int gx0 = home ? 12 : 10, gx1 = w - gx0;
    for (int k = 0, y = 5; k < (home ? 6 : 4); k++, y += 3) { gfx_fill(gx0, y, gx1 - gx0, 1, dark); gfx_fill(gx0, y + 1, gx1 - gx0, 1, light); }
    if (home) { int xs[2] = {6, w - 8}; for (int i = 0; i < 2; i++) { gfx_fill(xs[i], 30, 1, h - 62, dark); gfx_fill(xs[i] + 1, 30, 1, h - 62, light); } }

    // the label recess takes the label art's own proportions
    label_box_t L = cart_label_box(g->sys);
    image_t *lab = load_art(g, "label");
    if (!lab) lab = generated_label(g, L.lw, L.lh);
    if (lab) {
        float fs = fminf(1.0f, fminf((float)L.lw / lab->w, (float)L.lh / lab->h));
        int lw = (int)roundf(lab->w * fs), lh = (int)roundf(lab->h * fs);
        int lx = L.lx + (L.lw - lw) / 2, ly = L.ly + (L.lh - lh) / 2;
        gfx_fill(lx - 2, ly - 2, lw + 4, 2, darker); gfx_fill(lx - 2, ly - 2, 2, lh + 4, darker);
        gfx_fill(lx - 2, ly + lh, lw + 4, 2, lighter); gfx_fill(lx + lw, ly - 2, 2, lh + 4, lighter);
        gfx_blit_ex(lab, lx, ly, lw, lh, 0, 0);
        for (int i = 0; i < lh; i++) {   // gloss
            int x = lx + (int)roundf(lw * 0.62f) - i;
            if (x >= lx && x + 4 <= lx + lw) gfx_fill_a(x, ly + i, 4, 1, 0xFFFF, 33);
            if (x + 8 >= lx && x + 9 <= lx + lw) gfx_px_a(x + 8, ly + i, 0xFFFF, 33);
        }
        if (seed % 3 == 0) {   // a label corner that has started to lift
            for (int i = 0; i < 4; i++) { gfx_fill(lx + lw - 4 + i, ly + i, 4 - i, 1, RGB(0xf4f0e6)); gfx_px_a(lx + lw - 5 + i, ly + i, 0, 77); }
        }
        img_free(lab);
    }
    // embossed arrow
    int ax = w / 2, ay = L.ly + L.lh + (home ? 7 : 6);
    gfx_fill(ax - 4, ay - 1, 9, 1, light);
    for (int i = 0; i < 5; i++) gfx_fill(ax - 4 + i, ay + i, 9 - 2 * i, 1, dark);
    if (home) {
        int by = h - 20;
        gfx_fill(8, by, w - 16, 14, dark); gfx_fill(8, by - 1, w - 16, 1, light);
        for (int x = 14; x < w - 16; x += 9) gfx_fill(x, by + 4, 4, 7, darker);
    } else if (!clear) {
        gfx_fill(16, h - 12, w - 32, 1, dark); gfx_fill(16, h - 11, w - 32, 1, light);
    }
    uint32_t r = seed;   // a few scuffs
    for (int k = 0; k < 7; k++) {
        r = r * 1103515245 + 12345; int x = 4 + (r >> 16) % (w - 8);
        r = r * 1103515245 + 12345; int y = (r >> 16) & 1 ? L.ly + L.lh + 14 + ((r >> 8) & 3) : 2 + ((r >> 18) % 18);
        if (y < h - 22 || !home) gfx_px(x, y, lighter);
    }
    gfx_pop_target();
    if (g->sys == SYS_GB && PREFS.gb_green) img_dmgify(c, 0.88f);
    return c;
}

image_t *art_cart(game_t *g, bool may_build)
{
    if (g->cart) { g->used = ++use_clock; return g->cart; }
    if (!may_build) return NULL;
    g->cart = build_cart(g);
    g->art_dmg = PREFS.gb_green;
    g->used = ++use_clock;
    return g->cart;
}

void art_forget(game_t *g) { img_free(g->cart); img_free(g->box); img_free(g->thumb); g->cart = g->box = g->thumb = NULL; }
void art_forget_system(sys_t s) { for (int i = 0; i < SHELVES[s].n; i++) art_forget(&SHELVES[s].games[i]); }

void art_trim(void)
{
    const int MAX_CARTS = 28, MAX_BOXES = 6;
    while (1) {
        int carts = 0, boxes = 0;
        game_t *oldc = NULL, *oldb = NULL;
        for (int s = 0; s < SYS_COUNT; s++)
            for (int i = 0; i < SHELVES[s].n; i++) {
                game_t *g = &SHELVES[s].games[i];
                if (g->cart) { carts++; if (!oldc || g->used < oldc->used) oldc = g; }
                if (g->box) { boxes++; if (!oldb || g->used < oldb->used) oldb = g; }
            }
        if (carts > MAX_CARTS) { img_free(oldc->cart); oldc->cart = NULL; continue; }
        if (boxes > MAX_BOXES) { img_free(oldb->box); oldb->box = NULL; continue; }
        break;
    }
}
