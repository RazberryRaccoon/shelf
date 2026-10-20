#include <math.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <rg_system.h>
#include "ui.h"
#include "board.h"
#include "gfx.h"
#include "audio.h"
#include "library.h"
#include "art.h"
#include "backdrop.h"
#include "shelf_system.h"

#define W 320
#define H 240
#ifndef M_PI
#define M_PI 3.14159265f
#endif

/* ---------- small helpers ---------- */
static double T;
static inline float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
static inline float lerpf(float a, float b, float t) { return a + (b - a) * t; }
static inline float ease_out(float t) { return 1 - powf(1 - t, 3); }
static inline float ease_in(float t) { return t * t * t; }
static inline float ease_in_quad(float t) { return t * t; }
static inline float ease_in_out(float t) { return t < 0.5f ? 4 * t * t * t : 1 - powf(-2 * t + 2, 3) / 2; }
static inline float ease_out_back(float t) { const float c1 = 1.70158f, c3 = c1 + 1; return 1 + c3 * powf(t - 1, 3) + c1 * powf(t - 1, 2); }
static float ease_out_bounce(float t)
{
    const float n = 7.5625f, d = 2.75f;
    if (t < 1 / d) return n * t * t;
    if (t < 2 / d) { t -= 1.5f / d; return n * t * t + 0.75f; }
    if (t < 2.5f / d) { t -= 2.25f / d; return n * t * t + 0.9375f; }
    t -= 2.625f / d; return n * t * t + 0.984375f;
}
static void spring(float *v, float *vel, float target, float dt, float st, float dm)
{
    *vel += ((target - *v) * st - *vel * dm) * dt;
    *v += *vel * dt;
}
static inline int iround(float v) { return (int)lroundf(v); }
static float frand(void) { return (float)rand() / (float)RAND_MAX; }
static bool motion_on(void) { return PREFS.motion; }
static bool is_dmg(int sys) { return sys == SYS_GB && PREFS.gb_green; }
static const uint16_t SHADOW = 0x0821;   // near-black for text shadows

/* ---------- state ---------- */
typedef enum { SC_BOOT, SC_SHELF, SC_DETAIL, SC_LAUNCH, SC_RETURN, SC_ATTRACT, SC_MESSAGE } scene_t;
// The game page: four save slots (0-3), then the Favorite and About buttons.
enum { CUR_FAV = 4, CUR_ABOUT = 5 };
enum { POP_NONE, POP_SLOT, POP_DELETE };

static int vis[SYS_COUNT], nvis;          // shelves that have games
static struct {
    scene_t scene;
    int shelf;
    float cam, camV, fade;
    double titleT, sortAt, lastInput, volT, toastT, bootT0, nextShoot, selChangedAt;
    int titleDir, bootSnd;
    bool selDirty;
    char toast[48];
    char msg[512];
    bool msgDismiss;
    struct { bool open; float y, yV; int sel; } menu;
    struct { double t0, tc; bool closing, about; int cur, pop, pop_sel; float px, pxV, scroll, scrollV, scroll_to; } det;
    struct { double t0; int sys, idx, slot; bool resume, clunk, led; } lau;
    struct { double t0; int sys, idx; bool started, off, pop, fly; } ret;
    struct { int i, prev; double ts; } att;
    struct { double t0; float x, y; bool on; } shoot;
    uint32_t held;
    double down_at[10], next_rep[10];
} S;

static int cur_sys(void) { return vis[S.shelf]; }
static shelf_t *cur_shelf(void) { return &SHELVES[cur_sys()]; }
static game_t *cur_game(void) { shelf_t *sh = cur_shelf(); return sh->n ? &sh->games[sh->sel] : NULL; }

/* ---------- particles ---------- */
typedef struct { float x, y, vx, vy, life, age; int kind; uint16_t c; } particle_t;
#define MAXP 48
static particle_t P[MAXP];
static int np;
static void add_p(particle_t p) { if (np < MAXP) P[np++] = p; }
static void burst(float x, float y, bool on, bool dmg)
{
    for (int i = 0; i < (on ? 7 : 4); i++) {
        particle_t p = {x, y, (frand() - 0.5f) * 60, -40 - frand() * 50, 0.9f + frand() * 0.4f, 0, on ? 0 : 2,
                        dmg ? dmg_color(on ? 3 : 2) : RGB(on ? 0xff4a6a : 0x9aa3c0)};
        add_p(p);
    }
}
static void sparkles(float x, float y)
{
    for (int i = 0; i < 10; i++) {
        float a = i / 10.0f * 2 * M_PI;
        particle_t p = {x, y, cosf(a) * 70, sinf(a) * 50 - 25, 0.7f, 0, 1, RGB(0xfff3b0)};
        add_p(p);
    }
}
static void draw_particles(void)
{
    for (int i = 0; i < np; i++) {
        particle_t *p = &P[i];
        if (p->age > p->life * 0.65f && ((int)(p->age * 20)) % 2) continue;
        int x = iround(p->x), y = iround(p->y);
        if (p->kind == 0) draw_heart(x - 2, y - 2, p->c);
        else if (p->kind == 1) { gfx_fill(x - 1, y, 3, 1, p->c); gfx_fill(x, y - 1, 1, 3, p->c); }
        else gfx_fill(x, y, 2, 2, p->c);
    }
}
static void update_particles(float dt)
{
    for (int i = np - 1; i >= 0; i--) {
        particle_t *p = &P[i];
        p->age += dt; p->vy += 50 * dt; p->x += p->vx * dt; p->y += p->vy * dt; p->vx *= 0.97f;
        if (p->age > p->life) P[i] = P[--np];
    }
}

/* ---------- home screen ---------- */
static void draw_stars(int sys, int off)
{
    backdrop_t *B = &BACKDROPS[sys];
    bool dmg = is_dmg(sys);
    uint16_t cols[3] = {dmg ? dmg_color(1) : RGB(0x3e4672), dmg ? dmg_color(2) : RGB(0x8a94c8), dmg ? dmg_color(3) : 0xFFFF};
    float drift = motion_on() ? T * 2 : 0;
    float scroll = SHELVES[sys].scroll;
    for (int k = 0; k < STARS; k++) {
        float fx = fmodf(B->star[k].x - (scroll * 26 + drift) * B->star[k].z, 320);
        if (fx < 0) fx += 320;
        int x = (int)fx, y = iround(B->star[k].y + off * (0.25f + B->star[k].z * 0.4f));
        int c = B->star[k].z > 0.5f ? 2 : B->star[k].z > 0.3f ? 1 : 0;
        gfx_px(x, y, cols[c]);
        if (c == 2 && motion_on() && sinf(T * 2.3f + B->star[k].tw * 7) > 0.93f) { gfx_fill(x - 1, y, 3, 1, cols[c]); gfx_fill(x, y - 1, 1, 3, cols[c]); }
    }
}
static void draw_extras(int sys, int off)
{
    bool m = motion_on(), dmg = is_dmg(sys);
    int oy = iround(off * 0.5f);
    if (sys == SYS_NES) {   // a small moon on a slow orbit
        float a = m ? T * 0.04f : 0.6f;
        int mx = iround(160 + cosf(a) * 150), my = iround(54 + sinf(a) * 24) + oy;
        gfx_disc(mx, my, 6, RGB(0x4a3a52));
        for (int yy = -6; yy <= 6; yy++) { int hw = (int)floorf(sqrtf(36 - yy * yy) + 0.35f); gfx_fill(mx - hw, my + yy, hw > 1 ? hw : 1, 1, RGB(0xe8d8c8)); }
        gfx_fill(mx - 4, my - 2, 2, 2, RGB(0xb8a8a0)); gfx_fill(mx - 2, my + 2, 2, 1, RGB(0xb8a8a0));
    } else if (sys == SYS_GB) {   // a satellite drifting past, light blinking
        int x = m ? iround(fmodf(T * 5, W + 40) - 20) : 220, y = 32 + iround(sinf(x * 0.02f) * 6) + oy;
        uint16_t c1 = dmg ? dmg_color(2) : RGB(0x6a7a8a), c2 = dmg ? dmg_color(3) : RGB(0xe4ecf4);
        gfx_fill(x - 6, y, 4, 2, c1); gfx_fill(x + 3, y, 4, 2, c1); gfx_fill(x - 1, y - 1, 3, 4, c2);
        if (!m || fmod(T, 1.4) < 0.18) gfx_px(x, y - 3, c2);
    } else {   // a comet taking its time
        int x = m ? iround(fmodf(T * 4, W + 90) - 45) : 120, y = iround(22 + x * 0.06f) + oy;
        gfx_fill(x, y, 2, 2, RGB(0xe8fcff));
        for (int i = 1; i < 22; i++) if (dither(x - i, y, 1 - i / 22.0f)) gfx_px(x - i, iround(y - i * 0.06f), RGB(0x7ad8ff));
    }
}
static void draw_shooting_star(int sys, int off)
{
    if (!S.shoot.on) return;
    float q = (T - S.shoot.t0) / 0.7f;
    if (q > 1) { S.shoot.on = false; return; }
    float hx = S.shoot.x + q * 110, hy = S.shoot.y + q * 40 + off * 0.3f;
    uint16_t c = is_dmg(sys) ? dmg_color(3) : 0xFFFF;
    for (int i = 0; i < 14; i++) if (dither(i, (int)(q * 16), (1 - i / 14.0f) * (1 - q * 0.5f))) gfx_px(iround(hx - i * 2.75f), iround(hy - i), c);
}

static int build_budget;
static void draw_carts(int sys, int off, bool hide_sel)
{
    shelf_t *sh = &SHELVES[sys];
    bool home = sys == SYS_NES, cur = sys == cur_sys(), dmg = is_dmg(sys);
    float sp1 = home ? 127 : 110, sp2 = home ? 102 : 92;
    uint16_t shade = dmg ? dmg_color(0) : RGB(MOODS[sys].bg[0]);
    float off2 = fabsf(sh->scroll - sh->sel);
    int order[10], n = 0;
    float rr[10];
    for (int j = 0; j < sh->n && n < 10; j++) {
        float r = sh->games[j].pos - sh->scroll;
        if (fabsf(r) < 2.6f) { order[n] = j; rr[n] = r; n++; }
    }
    for (int a = 1; a < n; a++) for (int b = a; b > 0 && fabsf(rr[b]) > fabsf(rr[b - 1]); b--) {
        int tj = order[b]; order[b] = order[b - 1]; order[b - 1] = tj;
        float tr = rr[b]; rr[b] = rr[b - 1]; rr[b - 1] = tr;
    }
    label_box_t L = cart_label_box(sys);
    for (int k = 0; k < n; k++) {
        int j = order[k];
        float r = rr[k], a = fabsf(r), s = r < 0 ? -1.f : r > 0 ? 1.f : 0.f;
        if (hide_sel && cur && j == sh->sel) continue;
        game_t *g = &sh->games[j];
        float x = 160 + s * (a <= 1 ? a * sp1 : sp1 + (a - 1) * sp2), sc = 1 - fminf(a, 2.5f) * 0.22f;
        int dw = iround(cart_w(sys) * sc), dh = iround(cart_h(sys) * sc);
        bool sel = cur && j == sh->sel;
        int bob = (sel && off2 < 0.12f && motion_on()) ? iround(sinf(T * 2.2f) * 1.3f) : 0;
        int hop = (T - S.sortAt < 0.9) ? iround(fminf(1, fabsf(g->pos - j)) * 12) : 0;
        int x0 = iround(x - dw / 2.f), y0 = iround(off + CY - g->lift + bob - hop - dh / 2.f);
        int dark = (int)(fminf(0.6f, a * 0.32f) * 256);
        bool had = g->cart != NULL;
        image_t *im = art_cart(g, build_budget > 0 || sel);
        if (im && !had) build_budget--;
        if (im) gfx_blit_ex(im, x0, y0, dw, dh, shade, dark);
        else { gfx_fill(x0 + 2, y0, dw - 4, dh, RGB(0x30343e)); gfx_fill(x0, y0 + 2, dw, dh - 4, RGB(0x30343e)); }
        if (sel && off2 < 0.12f) {   // pulsing focus ring
            int ox = x0 - 4, oy = y0 - 4, ow = dw + 8, oh = dh + 8;
            int al = (int)((0.55f + 0.45f * (motion_on() ? sinf(T * 4) : 1)) * (1 - off2 / 0.12f) * 256);
            uint16_t c = dmg ? dmg_color(3) : RGB(MOODS[sys].accent);
            gfx_fill_a(ox + 2, oy, ow - 4, 1, c, al); gfx_fill_a(ox + 2, oy + oh - 1, ow - 4, 1, c, al);
            gfx_fill_a(ox, oy + 2, 1, oh - 4, c, al); gfx_fill_a(ox + ow - 1, oy + 2, 1, oh - 4, c, al);
            gfx_px_a(ox + 1, oy + 1, c, al); gfx_px_a(ox + ow - 2, oy + 1, c, al); gfx_px_a(ox + 1, oy + oh - 2, c, al); gfx_px_a(ox + ow - 2, oy + oh - 2, c, al);
        }
        if (g->fav) draw_sticker(x0 + iround((L.lx + L.lw - 9) * sc), y0 + iround((L.ly + 4) * sc), dmg ? dmg_color(0) : RGB(0xff4a6a));
    }
}

static const uint8_t FLOPPY[7] = {0x7E, 0x63, 0x63, 0x7F, 0x41, 0x5D, 0x7F};
static void draw_floppy(int x, int y, uint16_t c, int a)
{
    for (int r = 0; r < 7; r++) for (int k = 0; k < 7; k++) if (FLOPPY[r] & (1 << (6 - k))) gfx_px_a(x + k, y + r, c, a);
}
static void fmt_played(uint32_t sec, char *out, int len)
{
    uint32_t m = sec / 60;
    if (!sec) snprintf(out, len, "Not played yet");
    else if (m < 1) snprintf(out, len, "Played under a minute");
    else if (m < 60) snprintf(out, len, "Played %um", (unsigned)m);
    else if (m % 60) snprintf(out, len, "Played %uh %um", (unsigned)(m / 60), (unsigned)(m % 60));
    else snprintf(out, len, "Played %uh", (unsigned)(m / 60));
}
// "Saved 2 days ago", or just "Saved game" if the clock can't be trusted
static void fmt_ago(int64_t when, char *out, int len)
{
    int64_t now = (int64_t)time(NULL), d = now - when;
    if (when < 1000000000LL || d < 0) { snprintf(out, len, "Saved game"); return; }
    if (d < 120) snprintf(out, len, "Saved just now");
    else if (d < 3600) snprintf(out, len, "Saved %d minutes ago", (int)(d / 60));
    else if (d < 7200) snprintf(out, len, "Saved an hour ago");
    else if (d < 86400) snprintf(out, len, "Saved %d hours ago", (int)(d / 3600));
    else if (d < 2 * 86400) snprintf(out, len, "Saved yesterday");
    else if (d < 14 * 86400) snprintf(out, len, "Saved %d days ago", (int)(d / 86400));
    else if (d < 60 * 86400) snprintf(out, len, "Saved %d weeks ago", (int)(d / (7 * 86400)));
    else snprintf(out, len, "Saved %d months ago", (int)(d / (30 * 86400)));
}

static void draw_info(int sys, int off)
{
    shelf_t *sh = &SHELVES[sys];
    if (!sh->n) return;
    game_t *g = &sh->games[sh->sel];
    bool cur = sys == cur_sys();
    float e = cur ? ease_out(clamp01((T - S.titleT) / 0.28f)) : 1;
    int dx = cur ? iround(S.titleDir * 10 * (1 - e)) : 0, a = (int)((0.2f + 0.8f * e) * 256);
    char t[96], played[40], meta[80];
    if (gfx_text_width(g->title, 2) <= 300) gfx_text_sh(g->title, 160 + dx, off + 189, 0xFFFF, SHADOW, 2, AL_CENTER, a);
    else { gfx_ellipsize(g->title, 300, 1, t, sizeof(t)); gfx_text_sh(t, 160 + dx, off + 194, 0xFFFF, SHADOW, 1, AL_CENTER, a); }
    if (cur && !g->save_known) lib_check_save(g);
    fmt_played(g->play_sec, played, sizeof(played));
    char ago[32];
    fmt_ago(g->save_time, ago, sizeof(ago));
    if (g->has_save) snprintf(meta, sizeof(meta), "%s     %s", ago, played); else snprintf(meta, sizeof(meta), "%s", played);
    int sx = 160 - gfx_text_width(meta, 1) / 2 + dx + (g->has_save ? 5 : 0);
    if (g->has_save) draw_floppy(sx - 11, off + 208, is_dmg(sys) ? dmg_color(3) : RGB(MOODS[sys].accent), a);
    gfx_text_sh(meta, sx, off + 208, RGB(0xb4bdd8), SHADOW, 1, AL_LEFT, a);
}
static void draw_world(int i, int off, bool hide_sel)
{
    int sys = vis[i];
    backdrop_t *B = &BACKDROPS[sys];
    gfx_clip(0, off, W, off + H);
    if (B->ready) gfx_blit_opaque(B->bg, 0, iround(off * 0.4f)); else gfx_fill(0, off, W, H, RGB(MOODS[sys].bg[1]));
    if (B->ready) { draw_stars(sys, off); draw_extras(sys, off); }
    if (i == S.shelf) draw_shooting_star(sys, off);
    if (B->ready) gfx_blit(B->planet, 0, PLANET_Y + iround(off * 0.8f));
    draw_carts(sys, off, hide_sel);
    draw_info(sys, off);
    gfx_noclip();
}
static void render_home(bool hide_sel)
{
    gfx_fill(0, 0, W, H, RGB(0x04050c));
    int lo = (int)floorf(S.cam), hi = (int)ceilf(S.cam);
    if (lo >= 0 && lo < nvis) draw_world(lo, iround((lo - S.cam) * H), hide_sel);
    if (hi != lo && hi >= 0 && hi < nvis) draw_world(hi, iround((hi - S.cam) * H), hide_sel);
}
static void draw_top_bar(void)
{
    gfx_clip(0, 0, 230, 16);
    for (int i = 0; i < nvis; i++) {
        int dy = iround((i - S.cam) * 16);
        if (abs(dy) >= 16) continue;
        int sys = vis[i], nw = gfx_text_sh(SYS_NAME[sys], 12, 6 + dy, 0xFFFF, SHADOW, 1, AL_LEFT, 256);
        char c[24];
        snprintf(c, sizeof(c), SHELVES[sys].n == 1 ? "%d game" : "%d games", SHELVES[sys].n);
        gfx_text(c, 12 + nw + 7, 6 + dy, RGB(0x7e88aa), 1, AL_LEFT);
    }
    gfx_noclip();
    for (int i = 0; i < nvis; i++) gfx_fill(5, 5 + i * 4, 2, 2, i == S.shelf ? 0xFFFF : RGB(0x3e4672));
    int pct = board_battery_percent();
    if (pct >= 0) {
        int x = W - 20, y = 6;
        uint16_t c = RGB(0xdfe4f0);
        gfx_fill(x, y, 14, 1, c); gfx_fill(x, y + 6, 14, 1, c); gfx_fill(x, y, 1, 7, c); gfx_fill(x + 13, y, 1, 7, c); gfx_fill(x + 14, y + 2, 1, 3, c);
        int fw = (pct * 10 + 50) / 100;
        gfx_fill(x + 2, y + 2, fw, 3, pct > 25 ? RGB(0x7ee08a) : pct > 10 ? RGB(0xffd25a) : RGB(0xff5a5a));
        char t[16];
        snprintf(t, sizeof(t), "%d%%", pct);
        gfx_text_sh(t, W - 25, 6, 0xFFFF, SHADOW, 1, AL_RIGHT, 256);
    }
}
typedef struct { const char *btn, *label; } hint_t;
static int draw_btn(const char *name, int x, int y)
{
    if (!name[1]) {
        static const int wd[9] = {3, 7, 9, 9, 9, 9, 9, 7, 3};
        for (int r = 0; r < 9; r++) gfx_fill(x + (9 - wd[r]) / 2, y + r, wd[r], 1, RGB(0xc4405a));
        gfx_text(name, x + 2, y + 1, 0xFFFF, 1, AL_LEFT);
        return 9;
    }
    int w = gfx_text_width(name, 1) + 6;
    gfx_fill(x + 1, y, w - 2, 9, RGB(0x3a4266)); gfx_fill(x, y + 1, w, 7, RGB(0x3a4266));
    gfx_text(name, x + 3, y + 1, 0xFFFF, 1, AL_LEFT);
    return w;
}
static void draw_hints(const hint_t *h, int n)
{
    gfx_fill_a(8, 224, W - 16, 1, 0xFFFF, 41);
    int x = 10;
    for (int i = 0; i < n; i++) { x += draw_btn(h[i].btn, x, 228) + 3; x += gfx_text(h[i].label, x, 229, RGB(0xdfe4f0), 1, AL_LEFT) + 10; }
}

/* ---------- detail page ---------- */
static void box_rect(game_t *g, int *x, int *y, int *w, int *h)
{
    image_t *b = art_box(g);
    *w = b ? b->w : 112; *h = b ? b->h : 160; *x = 14; *y = 30 + (184 - *h) / 2;
}
static int panel_x(game_t *g) { int x, y, w, h; box_rect(g, &x, &y, &w, &h); return x + w + 10; }
static float det_p(void) { return S.det.closing ? 1 - clamp01((T - S.det.tc) / 0.4f) : clamp01((T - S.det.t0) / 0.45f); }
static void draw_flip(game_t *g, int sys, float p, int dx)
{
    float cw = cart_w(sys), ch = cart_h(sys), cx0 = 160 - cw / 2, cy0 = CY - 4 - ch / 2;
    int bx, by, bw, bh;
    box_rect(g, &bx, &by, &bw, &bh);
    if (p < 0.5f) {
        float q = ease_in_out(p / 0.5f);
        float cx = lerpf(cx0 + cw / 2, bx + bw / 2.f, q) + dx, cy = lerpf(cy0 + ch / 2, by + bh / 2.f, q) - sinf(q * M_PI) * 14;
        int w = iround(lerpf(cw, bw, q) * (1 - q)), h = iround(lerpf(ch, bh, q));
        image_t *c = art_cart(g, true);
        if (w >= 1 && c) gfx_blit_ex(c, iround(cx - w / 2.f), iround(cy - h / 2.f), w, h, 0, 0);
        return;
    }
    float q = ease_out((p - 0.5f) / 0.5f);
    int w = iround(bw * q); if (w < 1) w = 1;
    int x = iround(bx + bw / 2.f - w / 2.f) + dx;
    gfx_fill_a(x + 4, by + 4, w, bh, 0, 115);
    gfx_blit_ex(art_box(g), x, by, w, bh, 0, 0);
    if (p >= 1 && motion_on()) {
        float gp = fmodf(T * 70, 420) - 80;
        gfx_clip(x, by, x + w, by + bh);
        for (int y = 0; y < bh; y++) gfx_fill_a(x + iround(gp - y * 0.5f), by + y, 7, 1, 0xFFFF, 51);
        gfx_noclip();
    }
}
// "2d ago" for the small slot captions
static void fmt_short(int64_t when, char *out, int len)
{
    int64_t d = (int64_t)time(NULL) - when;
    if (when < 1000000000LL || d < 0) snprintf(out, len, "Saved");
    else if (d < 120) snprintf(out, len, "Just now");
    else if (d < 3600) snprintf(out, len, "%dm ago", (int)(d / 60));
    else if (d < 86400) snprintf(out, len, "%dh ago", (int)(d / 3600));
    else if (d < 14 * 86400) snprintf(out, len, "%dd ago", (int)(d / 86400));
    else if (d < 60 * 86400) snprintf(out, len, "%dw ago", (int)(d / (7 * 86400)));
    else snprintf(out, len, "%dmo ago", (int)(d / (30 * 86400)));
}

#define CELL_W 64
#define CELL_H 48
static int panel_title(game_t *g, int x, int y, int maxw)
{
    char lines[3][64];
    if (gfx_text_width(g->title, 2) <= maxw) { gfx_text(g->title, x, y, RGB(0xfff3d6), 2, AL_LEFT); return y + 18; }
    int n = gfx_wrap(g->title, maxw, 1, lines, 2);
    for (int i = 0; i < n; i++) { gfx_text(lines[i], x, y, RGB(0xfff3d6), 1, AL_LEFT); y += 10; }
    return y + 3;
}
static void draw_slot(game_t *g, int i, int x, int y, bool sel, uint16_t accent)
{
    save_slot_t *s = &g->slot[i];
    char cap[24];
    if (s->used) {
        image_t *th = art_slot_thumb(g, i);
        gfx_fill(x - 1, y - 1, CELL_W + 2, CELL_H + 2, RGB(0x3a4472));
        if (th) gfx_blit_ex(th, x, y, CELL_W, CELL_H, 0, 0);
        else { gfx_fill(x, y, CELL_W, CELL_H, RGB(0x1c2240)); draw_floppy(x + CELL_W / 2 - 3, y + CELL_H / 2 - 3, accent, 256); }
        fmt_short(s->time, cap, sizeof(cap));
        if (i == g->last_slot) {   // the one Resume picks
            gfx_fill_a(x + 1, y + CELL_H - 10, 11, 9, RGB(0x060812), 190);
            gfx_text("▶", x + 4, y + CELL_H - 9, accent, 1, AL_LEFT);
        }
    } else {
        for (int k = 0; k < CELL_W; k += 4) { gfx_fill(x + k, y, 2, 1, RGB(0x3a4472)); gfx_fill(x + k, y + CELL_H - 1, 2, 1, RGB(0x3a4472)); }
        for (int k = 0; k < CELL_H; k += 4) { gfx_fill(x, y + k, 1, 2, RGB(0x3a4472)); gfx_fill(x + CELL_W - 1, y + k, 1, 2, RGB(0x3a4472)); }
        gfx_text("New game", x + CELL_W / 2, y + CELL_H / 2 - 4, RGB(0x6a7398), 1, AL_CENTER);
        snprintf(cap, sizeof(cap), "Slot %d", i + 1);
    }
    gfx_text(cap, x + CELL_W / 2, y + CELL_H + 2, sel ? 0xFFFF : RGB(0x8f99b8), 1, AL_CENTER);
    if (sel) {
        int al = (int)((0.6f + 0.4f * (motion_on() ? sinf(T * 5) : 1)) * 256);
        gfx_fill_a(x - 3, y - 3, CELL_W + 6, 2, accent, al); gfx_fill_a(x - 3, y + CELL_H + 1, CELL_W + 6, 2, accent, al);
        gfx_fill_a(x - 3, y - 1, 2, CELL_H + 2, accent, al); gfx_fill_a(x + CELL_W + 1, y - 1, 2, CELL_H + 2, accent, al);
    }
}
static int draw_button(const char *label, int x, int y, bool sel)
{
    int w = gfx_text_width(label, 1) + 12;
    uint16_t bg = sel ? RGB(0xffd25a) : RGB(0x283058);
    gfx_fill(x + 1, y, w - 2, 12, bg); gfx_fill(x, y + 1, w, 10, bg);
    gfx_text(label, x + 6, y + 2, sel ? RGB(0x2a2010) : RGB(0xdfe4f0), 1, AL_LEFT);
    return w;
}
static const char *POP_SLOT_ITEMS[4] = {"Resume", "Start over here", "Delete save", "Cancel"};
static const char *POP_DELETE_ITEMS[2] = {"Delete it", "Keep it"};
static void draw_popup(int px)
{
    const char **items = S.det.pop == POP_SLOT ? POP_SLOT_ITEMS : POP_DELETE_ITEMS;
    int n = S.det.pop == POP_SLOT ? 4 : 2;
    const char *title = S.det.pop == POP_SLOT ? NULL : "Delete this save?";
    int w = 120, h = n * 14 + 10 + (title ? 14 : 0), x = px + (W - px - 6 - w) / 2, y = 112 - h / 2;
    gfx_fill(x, y, w, h, RGB(0x141a34));
    gfx_fill(x, y, w, 2, RGB(0xffd25a));
    gfx_fill(x, y + h - 1, w, 1, RGB(0x3a4472)); gfx_fill(x, y, 1, h, RGB(0x3a4472)); gfx_fill(x + w - 1, y, 1, h, RGB(0x3a4472));
    int iy = y + 7;
    if (title) { gfx_text(title, x + w / 2, iy, RGB(0xfff3d6), 1, AL_CENTER); iy += 14; }
    for (int i = 0; i < n; i++, iy += 14) {
        bool sel = i == S.det.pop_sel;
        if (sel) gfx_fill(x + 4, iy - 3, w - 8, 13, RGB(0x283058));
        gfx_text(items[i], x + 14, iy, sel ? 0xFFFF : RGB(0xaab3cc), 1, AL_LEFT);
        if (sel) gfx_text("▶", x + 6, iy, RGB(0xffd25a), 1, AL_LEFT);
    }
}
static void draw_about(game_t *g, int px)
{
    int x = px + 7, maxw = W - px - 20;
    int y = panel_title(g, x, 37, maxw) + 1;
    game_info_t *in = lib_info(g);
    if (!in) {
        char lines[6][64];
        int n = gfx_wrap("No details yet. Run tools/fetch_info.py on your computer to add release year, publisher and a description.", maxw, 1, lines, 6);
        for (int i = 0; i < n; i++) { gfx_text(lines[i], x, y + 4 + i * 11, RGB(0x8f99b8), 1, AL_LEFT); }
        return;
    }
    const char *labels[4] = {"Released", "Developer", "Publisher", "Genre"};
    const char *values[4] = {in->year, in->developer, in->publisher, in->genre};
    char v[64];
    for (int i = 0; i < 4; i++) {
        if (!values[i][0]) continue;
        gfx_text(labels[i], x, y, RGB(0x8f99b8), 1, AL_LEFT);
        gfx_ellipsize(values[i], maxw - 56, 1, v, sizeof(v));
        gfx_text(v, x + 56, y, RGB(0xdfe5f2), 1, AL_LEFT);
        y += 11;
    }
    if (!in->desc) return;
    y += 4;
    static char lines[40][64];   // the description, scrolled inside the panel
    int n = gfx_wrap(in->desc, maxw, 1, lines, 40), top = y, bottom = 206;
    int max_scroll = n * 10 - (bottom - top) + (in->source[0] ? 14 : 0);
    if (max_scroll < 0) max_scroll = 0;
    if (S.det.scroll_to > max_scroll) S.det.scroll_to = max_scroll;
    gfx_clip(px, top, W - 6, bottom);
    int yy = top - iround(S.det.scroll);
    for (int i = 0; i < n; i++, yy += 10) gfx_text(lines[i], x, yy, RGB(0xdfe5f2), 1, AL_LEFT);
    if (in->source[0]) { char src[48]; snprintf(src, sizeof(src), "From %s", in->source); gfx_text(src, x, yy + 4, RGB(0x6a7398), 1, AL_LEFT); }
    gfx_noclip();
    if (max_scroll > 0) {   // a thin scroll bar
        int track = bottom - top, bar = track * (bottom - top) / (bottom - top + max_scroll);
        int by = top + (int)((track - bar) * fminf(1, S.det.scroll / max_scroll));
        gfx_fill(W - 9, top, 1, track, RGB(0x283058));
        gfx_fill(W - 9, by, 1, bar, RGB(0xffd25a));
    }
}
static void draw_panel(game_t *g, int px)
{
    if (px >= W) return;
    gfx_fill_a(px, 30, W - px - 6, 190, RGB(0x0a0c1a), 230);
    gfx_fill_a(px, 30, W - px - 6, 1, 0xFFFF, 36);
    if (S.det.about) { draw_about(g, px); return; }
    int x = px + 7, maxw = W - px - 20;
    int y = panel_title(g, x, 37, maxw);
    // one line of facts: year and publisher if we have them
    game_info_t *in = lib_info(g);
    char meta[96], line[96], played[40];
    if (in && in->year[0] && in->publisher[0]) snprintf(meta, sizeof(meta), "%s  %s", in->year, in->publisher);
    else if (in && in->year[0]) snprintf(meta, sizeof(meta), "%s  %s", in->year, SYS_LONG[g->sys]);
    else snprintf(meta, sizeof(meta), "%s", SYS_LONG[g->sys]);
    gfx_ellipsize(meta, maxw, 1, line, sizeof(line));
    gfx_text(line, x, y, RGB(0x8f99b8), 1, AL_LEFT); y += 11;
    fmt_played(g->play_sec, played, sizeof(played));
    gfx_text(played, x, y, RGB(0xdfe5f2), 1, AL_LEFT); y += 14;
    // the four save slots
    uint16_t accent = RGB(MOODS[g->sys].accent);
    for (int i = 0; i < SAVE_SLOTS; i++)
        draw_slot(g, i, x + 2 + (i % 2) * (CELL_W + 10), y + (i / 2) * (CELL_H + 14), S.det.pop == POP_NONE && S.det.cur == i, accent);
    y += 2 * (CELL_H + 14);
    int bx = x + 2;
    bx += draw_button(g->fav ? "♥ Favorite" : "Favorite", bx, y, S.det.cur == CUR_FAV && S.det.pop == POP_NONE) + 6;
    draw_button("About", bx, y, S.det.cur == CUR_ABOUT && S.det.pop == POP_NONE);
    if (S.det.pop != POP_NONE) { gfx_fill_a(px, 30, W - px - 6, 190, RGB(0x060812), 120); draw_popup(px); }
}

/* ---------- consoles for the launch and return animations ---------- */
static void led(int x, int y, bool on)
{
    if (on) { gfx_fill_a(x - 2, y - 1, 7, 4, RGB(0xff5a5a), 90); gfx_fill_a(x - 1, y - 2, 5, 6, RGB(0xff5a5a), 90); }
    gfx_fill(x, y, 3, 2, on ? RGB(0xff5a5a) : RGB(0x4a1c1c));
}
static void draw_home_console(int cx, int y, bool on)
{
    int x = cx - 88;
    gfx_fill(x + 10, y + 64, 14, 3, RGB(0x1a1c24)); gfx_fill(x + 152, y + 64, 14, 3, RGB(0x1a1c24));
    gfx_fill(x, y, 176, 14, RGB(0x5a6072)); gfx_fill(x, y, 176, 1, RGB(0x7a8298));
    gfx_fill(cx - 50, y + 3, 100, 1, RGB(0x3a3f4e)); gfx_fill(cx - 50, y + 10, 100, 1, RGB(0x3a3f4e));
    gfx_fill(cx - 48, y + 4, 96, 6, RGB(0x0c0d12));
    gfx_fill(x, y + 14, 176, 50, RGB(0x3f4454)); gfx_fill(x, y + 62, 176, 2, RGB(0x2c303c));
    gfx_fill(x, y + 20, 176, 3, RGB(0xd9475b));
    gfx_fill(x + 16, y + 34, 26, 10, RGB(0x2a2e3a)); gfx_fill(x + 48, y + 34, 26, 10, RGB(0x2a2e3a));
    gfx_fill(x + 16, y + 34, 26, 1, RGB(0x525868)); gfx_fill(x + 48, y + 34, 26, 1, RGB(0x525868));
    for (int i = 0; i < 6; i++) gfx_fill(x + 118 + i * 8, y + 30, 3, 22, RGB(0x2a2e3a));
    led(x + 18, y + 52, on);
}
static void draw_pocket_console(int cx, int y, bool on, float lt, game_t *g)
{
    int x = cx - 56;
    uint16_t body = RGB(0x3e4a72);
    gfx_fill(x + 3, y, 106, 146, body); gfx_fill(x, y + 3, 112, 140, body); gfx_fill(x + 1, y + 1, 110, 144, body);
    gfx_fill(x + 3, y + 1, 106, 1, RGB(0x56649a));
    gfx_fill(x + 20, y, 72, 3, RGB(0x2a3252));
    gfx_fill(x + 10, y + 12, 92, 70, RGB(0x1e2236));
    float lit = clamp01(lt / 0.3f);
    gfx_fill(x + 16, y + 18, 80, 58, RGB(mixh(0x26304a, 0xbfe3ff, lit)));
    if (lt > 0.35f && g) {
        char ini[4] = {0};
        int n = 0;
        bool st = true;
        for (const char *p = g->title; *p && n < 3; p++) { if (*p == ' ') { st = true; continue; } if (st) ini[n++] = (*p >= 'a' && *p <= 'z') ? *p - 32 : *p; st = false; }
        gfx_text(ini, cx, y + 39, RGB(0x26304a), 2, AL_CENTER);
    }
    led(x + 50, y + 85, on);
    gfx_fill(x + 14, y + 104, 22, 7, RGB(0x1e2236)); gfx_fill(x + 21, y + 97, 8, 21, RGB(0x1e2236));
    gfx_disc(x + 76, y + 107, 6, RGB(0xffd25a)); gfx_disc(x + 94, y + 107, 6, RGB(0xffd25a));
    for (int i = 0; i < 4; i++) gfx_fill(x + 44 + i * 7, y + 130, 3, 3, RGB(0x2a3252));
}
typedef struct { bool home; image_t *spr; int dw, dh, ct, insert; } geom_t;
static geom_t console_geom(game_t *g)
{
    geom_t G;
    G.home = g->sys == SYS_NES;
    G.spr = art_cart(g, true);
    float s = G.home ? 0.62f : 0.6f;
    G.dw = iround(cart_w(g->sys) * s); G.dh = iround(cart_h(g->sys) * s);
    G.ct = G.home ? 128 : 96;
    G.insert = G.home ? G.ct + 7 + 40 - G.dh : G.ct + 44 - G.dh;
    return G;
}
static void draw_console_and_cart(geom_t *G, game_t *g, int cy, float cart_x, float cart_y, int dw, int dh, bool on, float lt, bool clip)
{
    if (G->home) {
        draw_home_console(160, cy, on);
        if (clip) gfx_clip(0, 0, W, cy + 7);
        if (G->spr) gfx_blit_ex(G->spr, iround(cart_x - dw / 2.f), iround(cart_y), dw, dh, 0, 0);
        gfx_noclip();
    } else {
        if (G->spr) gfx_blit_ex(G->spr, iround(cart_x - dw / 2.f), iround(cart_y), dw, dh, 0, 0);
        draw_pocket_console(160, cy, on, lt, g);
    }
}
static void dim(float a) { gfx_fill_a(0, 0, W, H, RGB(0x060812), (int)(a * 256)); }

static void render_launch(void)
{
    game_t *g = &SHELVES[S.lau.sys].games[S.lau.idx];
    float t = T - S.lau.t0;
    geom_t G = console_geom(g);
    render_home(true);
    dim(lerpf(0.68f, 0.9f, clamp01(t / 0.4f)));
    if (t < 0.35f) { float e = ease_in(t / 0.35f); draw_flip(g, g->sys, 1, -iround(e * 170)); draw_panel(g, iround(panel_x(g) + e * 180)); }
    int cy = iround(lerpf(H + 10, G.ct, ease_out_back(clamp01((t - 0.3f) / 0.45f))));
    int shake = (t > 1.15f && t < 1.32f && motion_on()) ? ((rand() & 1) ? 2 : -2) : 0;
    float yc;
    if (t < 0.6f) yc = -300;
    else if (t < 1.15f) yc = lerpf(-G.dh - 10, G.insert, ease_in_quad((t - 0.6f) / 0.55f));
    else { float k = t - 1.15f; yc = G.insert - fabsf(sinf(k * 18)) * 4 * expf(-k * 9); }
    draw_console_and_cart(&G, g, cy + shake / 2, 160 + shake, yc, G.dw, G.dh, t > 1.35f, t - 1.35f, true);
    draw_particles();
    if (t > 1.9f) gfx_iris(160, G.home ? cy + 7 : cy + 46, iround((1 - ease_in_quad(clamp01((t - 1.9f) / 0.6f))) * 230));
}
static void render_return(void)
{
    game_t *g = &SHELVES[S.ret.sys].games[S.ret.idx];
    if (!S.ret.started) { gfx_fill(0, 0, W, H, 0); return; }
    float t = T - S.ret.t0;
    geom_t G = console_geom(g);
    render_home(true);
    dim(t < 1.15f ? 0.9f : lerpf(0.9f, 0, ease_in_out(clamp01((t - 1.15f) / 0.6f))));
    int cy = t < 1.0f ? G.ct : iround(lerpf(G.ct, H + 20, ease_in(clamp01((t - 1.0f) / 0.45f))));
    float pop = 58, top0 = G.insert, ctop = top0;
    int dw = G.dw, dh = G.dh;
    bool clip = true;
    if (t >= 0.8f && t < 1.15f) ctop = top0 - ease_out_back(clamp01((t - 0.8f) / 0.35f)) * pop;
    else if (t >= 1.15f) {
        float q = ease_in_out(clamp01((t - 1.15f) / 0.6f)), s = lerpf(G.home ? 0.62f : 0.6f, 1, q);
        dw = iround(cart_w(g->sys) * s); dh = iround(cart_h(g->sys) * s);
        float cyc = lerpf(top0 - pop + G.dh / 2.f, CY - 4, q) - sinf(q * M_PI) * 10;
        ctop = cyc - dh / 2.f;
        clip = false;
    }
    float lt = t < 0.55f ? 1 : 0.3f * (1 - clamp01((t - 0.55f) / 0.25f));
    if (G.home || t < 1.15f) draw_console_and_cart(&G, g, cy, 160, ctop, dw, dh, t < 0.55f, lt, clip);
    else { draw_pocket_console(160, cy, false, 0, g); if (G.spr) gfx_blit_ex(G.spr, iround(160 - dw / 2.f), iround(ctop), dw, dh, 0, 0); }
    draw_particles();
    if (t < 0.55f) gfx_iris(160, G.home ? cy + 7 : cy + 46, iround(ease_out(t / 0.55f) * 230));
}

/* ---------- boot ---------- */
static image_t *boot_bg;
static void render_boot(void)
{
    float t = T - S.bootT0;
    if (!boot_bg && (boot_bg = img_new(W, H))) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float f = (float)y / H;
            boot_bg->px[y * W + x] = dither(x, y, f) ? RGB(0x16102e) : RGB(0x05061a);
        }
        uint32_t r = 7;
        for (int k = 0; k < 80; k++) { r = r * 1103515245 + 12345; int x = (r >> 16) % W; r = r * 1103515245 + 12345; int y = (r >> 16) % H; boot_bg->px[y * W + x] = (k % 3) ? RGB(0x6a74a8) : 0xFFFF; }
    }
    if (boot_bg) gfx_blit_opaque(boot_bg, 0, 0); else gfx_fill(0, 0, W, H, 0);
    const char *word = "Shelf";
    int sc = 4, x = 160 - gfx_text_width(word, sc) / 2;
    for (int i = 0; word[i]; i++) {
        char ch[2] = {word[i], 0};
        float ti = t - (0.25f + i * 0.13f);
        if (ti >= 0) {
            int y = ti < 0.5f ? iround(lerpf(-40, 82, ease_out_bounce(ti / 0.5f))) : 82;
            gfx_text(ch, x + 2, y + 2, RGB(0x2a1c3a), sc, AL_LEFT);
            gfx_text(ch, x, y, 0xFFFF, sc, AL_LEFT);
        }
        x += gfx_text_width(ch, sc) + sc;
    }
    if (t > 1.15f) gfx_text_a("Your games, ready to play.", 160, 130, RGB(0x8a94c8), 1, AL_CENTER, (int)(clamp01((t - 1.15f) / 0.4f) * 256));
    if (t > 2.05f) gfx_fill_a(0, 0, W, H, 0, (int)(clamp01((t - 2.05f) / 0.3f) * 256));
}

/* ---------- attract mode ---------- */
static image_t *attA, *attB;
static void draw_attract_frame(game_t *g, float tt)
{
    image_t *b = art_box(g);
    int r = 0, gg = 0, bl = 0, n = 0;
    if (b) for (int i = 0; i < b->w * b->h; i += 7) { int a, c, d; unpack565(b->px[i], &a, &c, &d); r += a; gg += c; bl += d; n++; }
    uint32_t avg = n ? ((uint32_t)(r / n) << 16) | ((uint32_t)(gg / n) << 8) | (uint32_t)(bl / n) : 0x101020;
    gfx_fill(0, 0, W, H, RGB(mixh(avg, 0, 0.65f)));
    uint16_t dots = RGB(mixh(avg, 0, 0.5f));
    for (int y = 0; y < H; y += 2) for (int x = (y >> 1) & 1; x < W; x += 4) gfx_px(x, y, dots);
    if (b) {
        int dw = b->w * H / b->h;
        int ax = (W - dw) / 2 + (motion_on() ? iround(sinf(tt * 0.5f) * 8) : 0);
        gfx_blit_ex(b, ax, 0, dw, H, 0, 0);
    }
    gfx_fill_a(0, 200, W, 40, RGB(0x060810), 200);
    char t[96];
    if (gfx_text_width(g->title, 2) <= 304) gfx_text(g->title, 160, 207, RGB(0xfff3d6), 2, AL_CENTER);
    else { gfx_ellipsize(g->title, 304, 1, t, sizeof(t)); gfx_text(t, 160, 211, RGB(0xfff3d6), 1, AL_CENTER); }
    if (((int)(T * 1.4)) % 2 == 0) gfx_text("Press any button", 160, 228, RGB(0xa8b2d8), 1, AL_CENTER);
}
static void render_attract(void)
{
    shelf_t *sh = cur_shelf();
    float tt = T - S.att.ts;
    if (!attA) attA = img_new(W, H);
    if (!attB) attB = img_new(W, H);
    if (!attA || !attB) { S.scene = SC_SHELF; return; }
    gfx_push_target(attB); draw_attract_frame(&sh->games[S.att.i], tt); gfx_pop_target();
    if (S.att.prev >= 0 && tt < 0.7f) {
        gfx_push_target(attA); draw_attract_frame(&sh->games[S.att.prev], tt + 5.5f); gfx_pop_target();
        gfx_blit_opaque(attA, 0, 0);
        float p = tt / 0.7f;
        for (int by = 0; by < H / 8; by++) for (int bx = 0; bx < W / 8; bx++) if (dither(bx, by, p)) gfx_blit_part(attB, bx * 8, by * 8, 8, 8, bx * 8, by * 8);
    } else gfx_blit_opaque(attB, 0, 0);
}

/* ---------- overlays ---------- */
static void draw_toast(void)
{
    if (!S.toast[0]) return;
    float t = T - S.toastT;
    if (t > 1.9f) { S.toast[0] = 0; return; }
    int y = iround(t < 0.2f ? lerpf(-12, 20, ease_out_back(t / 0.2f)) : t > 1.65f ? lerpf(20, -12, ease_in((t - 1.65f) / 0.25f)) : 20);
    int tw = gfx_text_width(S.toast, 1) + 14, x = 160 - tw / 2;
    gfx_fill(x + 1, y, tw - 2, 12, RGB(0xffd25a)); gfx_fill(x, y + 1, tw, 10, RGB(0xffd25a));
    gfx_text(S.toast, 160, y + 2, RGB(0x2a2010), 1, AL_CENTER);
}
static void toast(const char *s) { str_copy(S.toast, sizeof(S.toast), s); S.toastT = T; }
static void draw_volume(void)
{
    float t = T - S.volT;
    if (t > 1.5f || t < 0) return;
    int y = iround(t < 0.15f ? lerpf(-24, 20, ease_out(t / 0.15f)) : t > 1.3f ? lerpf(20, -24, ease_in((t - 1.3f) / 0.2f)) : 20), x = 118;
    gfx_fill(x, y, 84, 20, RGB(0x141a34)); gfx_fill(x, y, 84, 1, RGB(0x3a4472)); gfx_fill(x, y + 19, 84, 1, RGB(0x3a4472));
    gfx_fill(x + 7, y + 8, 3, 5, 0xFFFF);
    for (int i = 0; i < 4; i++) gfx_fill(x + 10 + i, y + 7 - i, 1, 7 + i * 2, 0xFFFF);
    int level = (rg_audio_get_volume() + 12) / 25;   // 0..4
    if (level == 0) gfx_text("×", x + 16, y + 6, RGB(0xff7a8a), 1, AL_LEFT);
    for (int i = 0; i < 4; i++) { int h = 4 + i * 2; gfx_fill(x + 28 + i * 13, y + 15 - h, 9, h, i < level ? RGB(0xffd25a) : RGB(0x3a4472)); }
}
#define MENU_N 5
static const char *MENU_LABEL[MENU_N] = {"Sounds", "Background motion", "Game Boy shelf", "Attract mode", "System settings"};
static const char *menu_value(int i)
{
    switch (i) {
    case 0: return PREFS.sound ? "On" : "Off";
    case 1: return PREFS.motion ? "On" : "Off";
    case 2: return PREFS.gb_green ? "Classic green" : "Full color";
    case 3: return PREFS.attract == 0 ? "Off" : PREFS.attract == 1 ? "After 20 s" : "After 1 min";
    default: return "Brightness, volume…";
    }
}
static void draw_menu(void)
{
    if (S.menu.y < -138) return;
    int pw = 240, ph = 26 + MENU_N * 16 + 16, px = 40, py = iround(S.menu.y);
    dim(0.55f * clamp01((S.menu.y + 140) / 180));
    gfx_fill(px, py, pw, ph, RGB(0x141a34));
    gfx_fill(px, py + ph - 1, pw, 1, RGB(0x3a4472)); gfx_fill(px, py, 1, ph, RGB(0x3a4472)); gfx_fill(px + pw - 1, py, 1, ph, RGB(0x3a4472));
    gfx_fill(px, py, pw, 2, RGB(0xffd25a));
    gfx_text("Settings", px + 10, py + 8, RGB(0xfff3d6), 2, AL_LEFT);
    for (int i = 0; i < MENU_N; i++) {
        int y = py + 30 + i * 16;
        bool sel = i == S.menu.sel;
        if (sel) gfx_fill(px + 4, y - 4, pw - 8, 14, RGB(0x262e5a));
        gfx_text(MENU_LABEL[i], px + 10, y, sel ? 0xFFFF : RGB(0xaab3cc), 1, AL_LEFT);
        char v[40];
        if (sel && i < 4) snprintf(v, sizeof(v), "◀ %s ▶", menu_value(i)); else snprintf(v, sizeof(v), "%s", menu_value(i));
        gfx_text(v, px + pw - 10, y, sel ? RGB(0xffd25a) : RGB(0xdfe4f0), 1, AL_RIGHT);
    }
    gfx_text("Press B to close", px + pw / 2, py + ph - 12, RGB(0x7a84a8), 1, AL_CENTER);
}

/* ---------- actions ---------- */
static void note_selection(void)
{
    shelf_t *sh = cur_shelf();
    if (sh->n) str_copy(PREFS.sel[cur_sys()], sizeof(PREFS.sel[0]), sh->games[sh->sel].file);
    PREFS.shelf = cur_sys();
    S.selDirty = true;
    S.selChangedAt = T;
}
static void move_sel(int d)
{
    shelf_t *sh = cur_shelf();
    int ni = sh->sel + d;
    if (ni < 0 || ni >= sh->n) { sh->scrollV += d * 3.2f; sfx_bump(); return; }
    sh->sel = ni; S.titleT = T; S.titleDir = d; sfx_tick(d);
    note_selection();
}
static void switch_shelf(int d)
{
    int ni = S.shelf + d;
    if (ni < 0 || ni >= nvis) { S.camV += d * 2.4f; sfx_bump(); return; }
    S.shelf = ni; S.titleT = T; S.titleDir = 0; sfx_shelf();
    note_selection();
}
static void toggle_fav(game_t *g, bool detail)
{
    g->fav = !g->fav;
    img_free(g->box); g->box = NULL;     // the box carries the sticker
    if (detail) { int x, y, w, h; box_rect(g, &x, &y, &w, &h); burst(x + w - 10, y + h - 10, g->fav, is_dmg(g->sys)); }
    else burst(160, CY - cart_h(g->sys) / 2 + 8, g->fav, is_dmg(g->sys));
    sfx_fav(g->fav);
    lib_save_meta();
}
static void cycle_sort(void)
{
    shelf_t *sh = cur_shelf();
    int m = (sh->sort + 1) % SORT_COUNT;
    lib_sort(sh, m);
    PREFS.sort[cur_sys()] = m;
    S.sortAt = T;
    toast(SORT_LABEL[m]);
    sfx_sort();
    note_selection();
}
static void open_detail(void)
{
    game_t *g = cur_game();
    if (g) lib_check_save(g);   // fresh: the save list may have changed since startup
    memset(&S.det, 0, sizeof(S.det));
    S.det.t0 = T; S.det.px = 330;
    if (g) {   // start on the slot Resume would use, or the first free one
        S.det.cur = g->has_save ? g->last_slot : 0;
        if (!g->has_save) for (int i = 0; i < SAVE_SLOTS; i++) if (!g->slot[i].used) { S.det.cur = i; break; }
    }
    S.scene = SC_DETAIL;
    sfx_open();
}
static void start_launch(game_t *g, bool resume, int slot)
{
    S.scene = SC_LAUNCH;
    S.lau.t0 = T; S.lau.sys = g->sys; S.lau.idx = cur_shelf()->sel; S.lau.resume = resume; S.lau.slot = slot;
    S.lau.clunk = S.lau.led = false;
    sound_tone(440, 0.08f, WAVE_SQUARE, 0, 0.4f, 880);
}
static void enter_attract(void) { S.scene = SC_ATTRACT; S.att.i = cur_shelf()->sel; S.att.prev = -1; S.att.ts = T; }
static void exit_attract(void)
{
    cur_shelf()->sel = S.att.i;
    S.scene = SC_SHELF; S.fade = 0.8f; S.titleT = T; S.titleDir = 0; S.lastInput = T;
    note_selection();
}
static void start_home(void)
{
    S.scene = SC_SHELF; S.fade = 1; S.lastInput = T; S.titleT = T + 0.3; S.titleDir = 1;
    for (int s = 0; s < SYS_COUNT; s++) for (int j = 0; j < SHELVES[s].n; j++) { SHELVES[s].games[j].pos = j + 4 + j * 0.7f; SHELVES[s].games[j].posV = 0; }
}
static void menu_input(uint32_t b)
{
    if (b & (BTN_B | BTN_MENU)) { S.menu.open = false; sfx_back(); prefs_save(); return; }
    if (b & BTN_UP) { S.menu.sel = (S.menu.sel + MENU_N - 1) % MENU_N; sfx_tick(-1); }
    else if (b & BTN_DOWN) { S.menu.sel = (S.menu.sel + 1) % MENU_N; sfx_tick(1); }
    else if (S.menu.sel == 4) {
        if (b & BTN_A) {   // retro-go's own settings, drawn in Shelf's theme
            board_sync();
            board_bus_lock();
            rg_gui_options_menu();
            rg_settings_commit();
            board_bus_unlock();
            S.held = board_input_read();   // don't let the closing press leak into Shelf
        }
    }
    else if (b & (BTN_LEFT | BTN_RIGHT | BTN_A)) {
        int d = (b & BTN_LEFT) ? -1 : 1;
        switch (S.menu.sel) {
        case 0: PREFS.sound = !PREFS.sound; sound_set_enabled(PREFS.sound); break;
        case 1: PREFS.motion = !PREFS.motion; break;
        case 2: PREFS.gb_green = !PREFS.gb_green; art_forget_system(SYS_GB); backdrop_rebuild(SYS_GB); break;
        default: PREFS.attract = (PREFS.attract + 3 + d) % 3; break;
        }
        sfx_tick(d);
    }
}

static void press(uint32_t b)
{
    S.lastInput = T;
    if (b & BTN_VOLUME) {
        int v = rg_audio_get_volume();
        v = v >= 100 ? 0 : ((v + 12) / 25 + 1) * 25;
        rg_audio_set_volume(v > 100 ? 100 : v);
        S.volT = T; sfx_vol();
        S.selDirty = true; S.selChangedAt = T;
        return;
    }
    if (S.scene == SC_ATTRACT) { exit_attract(); return; }
    if (S.menu.open) { menu_input(b); return; }
    if (S.scene == SC_BOOT) { if (backdrops_ready()) start_home(); return; }
    if (S.scene == SC_SHELF) {
        if (b & BTN_LEFT) move_sel(-1);
        else if (b & BTN_RIGHT) move_sel(1);
        else if (b & BTN_UP) switch_shelf(-1);
        else if (b & BTN_DOWN) switch_shelf(1);
        else if (b & BTN_A) open_detail();
        else if (b & BTN_SELECT) toggle_fav(cur_game(), false);
        else if (b & BTN_START) cycle_sort();
        else if (b & BTN_MENU) { S.menu.open = true; sfx_open(); }
        else if (b & BTN_B) sfx_bump();
        return;
    }
    if (S.scene == SC_DETAIL) {
        game_t *g = cur_game();
        if (S.det.closing) return;
        if (S.det.pop != POP_NONE) {   // the small menu over a save slot
            int n = S.det.pop == POP_SLOT ? 4 : 2;
            if (b & (BTN_UP | BTN_DOWN)) { S.det.pop_sel = (S.det.pop_sel + ((b & BTN_UP) ? -1 : 1) + n) % n; sfx_tick(1); }
            else if (b & BTN_B) { S.det.pop = POP_NONE; sfx_back(); }
            else if (b & BTN_A) {
                int slot = S.det.cur, choice = S.det.pop_sel;
                if (S.det.pop == POP_SLOT) {
                    if (choice == 0) start_launch(g, true, slot);
                    else if (choice == 1) start_launch(g, false, slot);
                    else if (choice == 2) { S.det.pop = POP_DELETE; S.det.pop_sel = 1; sfx_open(); }
                    else { S.det.pop = POP_NONE; sfx_back(); }
                } else {
                    if (choice == 0) { lib_delete_save(g, slot); toast("Save deleted"); sfx_back(); }
                    else sfx_back();
                    S.det.pop = POP_NONE;
                }
            }
            return;
        }
        if (S.det.about) {   // reading about the game
            if (b & (BTN_UP | BTN_DOWN)) { S.det.scroll_to += (b & BTN_UP) ? -30 : 30; if (S.det.scroll_to < 0) S.det.scroll_to = 0; }
            else if (b & (BTN_B | BTN_A)) { S.det.about = false; sfx_back(); }
            return;
        }
        int c = S.det.cur, nc = c;
        if (b & BTN_LEFT) nc = (c == 1 || c == 3) ? c - 1 : (c == CUR_ABOUT ? CUR_FAV : c);   // no wrapping
        else if (b & BTN_RIGHT) nc = (c == 0 || c == 2) ? c + 1 : (c == CUR_FAV ? CUR_ABOUT : c);
        else if (b & BTN_UP) nc = c >= SAVE_SLOTS ? (c == CUR_FAV ? 2 : 3) : (c >= 2 ? c - 2 : c);
        else if (b & BTN_DOWN) nc = c < 2 ? c + 2 : (c < SAVE_SLOTS ? (c == 2 ? CUR_FAV : CUR_ABOUT) : c);
        if (nc != c) { S.det.cur = nc; sfx_tick(1); return; }
        if (b & BTN_A) {
            if (c == CUR_FAV) toggle_fav(g, true);
            else if (c == CUR_ABOUT) { S.det.about = true; S.det.scroll = S.det.scroll_to = 0; sfx_open(); }
            else if (g->slot[c].used) { S.det.pop = POP_SLOT; S.det.pop_sel = 0; sfx_open(); }
            else start_launch(g, false, c);   // an empty slot: a new game that saves here
        }
        else if (b & BTN_B) { S.det.closing = true; S.det.tc = T; sfx_back(); }
        else if (b & BTN_SELECT) toggle_fav(g, true);
        else if (b & BTN_MENU) { S.menu.open = true; sfx_open(); }
    }
}

static void handle_input(uint32_t now)
{
    uint32_t pressed = now & ~S.held;
    for (int i = 0; i < 10; i++) {
        uint32_t bit = 1u << i;
        if (pressed & bit) { S.down_at[i] = T; S.next_rep[i] = T + 0.3; press(bit); }
        else if ((now & bit) && (bit & (BTN_UP | BTN_DOWN | BTN_LEFT | BTN_RIGHT)) && T >= S.next_rep[i]) {
            press(bit);
            S.next_rep[i] = T + (T - S.down_at[i] > 1.2 ? 0.055 : 0.095);
        }
    }
    S.held = now;
}

/* ---------- update and draw ---------- */
static void update(float dt)
{
    for (int s = 0; s < SYS_COUNT; s++) {
        shelf_t *sh = &SHELVES[s];
        spring(&sh->scroll, &sh->scrollV, sh->sel, dt, 200, 25);
        for (int j = 0; j < sh->n; j++) {
            game_t *g = &sh->games[j];
            spring(&g->pos, &g->posV, j, dt, 150, 21);
            spring(&g->lift, &g->liftV, (nvis && s == cur_sys() && j == sh->sel) ? 4 : 0, dt, 260, 22);
        }
    }
    spring(&S.cam, &S.camV, S.shelf, dt, 140, 19);
    spring(&S.menu.y, &S.menu.yV, S.menu.open ? 40 : -150, dt, 240, 24);
    update_particles(dt);
    switch (S.scene) {
    case SC_BOOT: {
        float ti = T - S.bootT0 - (0.25f + S.bootSnd * 0.13f);
        if (S.bootSnd < 5 && ti >= 0.18f) { sfx_letter(S.bootSnd); S.bootSnd++; }
        if (T - S.bootT0 > 2.4 && backdrops_ready()) start_home();
        break;
    }
    case SC_DETAIL: {
        game_t *g = cur_game();
        spring(&S.det.px, &S.det.pxV, S.det.closing ? W + 12 : panel_x(g), dt, 220, 24);
        spring(&S.det.scroll, &S.det.scrollV, S.det.scroll_to, dt, 220, 24);
        if (S.det.closing && T - S.det.tc > 0.4) { art_forget_slot_thumbs(g); S.scene = SC_SHELF; }
        break;
    }
    case SC_LAUNCH: {
        float t = T - S.lau.t0;
        bool home = S.lau.sys == SYS_NES;
        if (t >= 1.15f && !S.lau.clunk) { S.lau.clunk = true; sfx_clunk(); }
        if (t >= 1.35f && !S.lau.led) { S.lau.led = true; sfx_jingle(); sparkles(160, home ? 135 : 112); }
        if (t >= 2.6f) {
            game_t *g = &SHELVES[S.lau.sys].games[S.lau.idx];
            launch_game(g, S.lau.resume, S.lau.slot);   // restarts into the emulator
        }
        break;
    }
    case SC_RETURN: {
        if (!S.ret.started) { if (backdrops_ready()) { S.ret.started = true; S.ret.t0 = T; } break; }
        float t = T - S.ret.t0;
        game_t *g = &SHELVES[S.ret.sys].games[S.ret.idx];
        geom_t G = console_geom(g);
        if (t >= 0.55f && !S.ret.off) { S.ret.off = true; sfx_unjingle(); }
        if (t >= 0.8f && !S.ret.pop) { S.ret.pop = true; sfx_pop(); sparkles(160, G.home ? G.ct + 7 : G.ct + 2); }
        if (t >= 1.15f && !S.ret.fly) { S.ret.fly = true; sfx_whoosh(); }
        if (t >= 1.8f) {
            S.scene = SC_SHELF; g->lift = 4; S.titleT = T; S.titleDir = 0; S.lastInput = T;
            if (g->has_save) { toast("Progress saved"); sfx_saved(); }
        }
        break;
    }
    case SC_ATTRACT:
        if (T - S.att.ts > 5.5) { S.att.prev = S.att.i; S.att.i = (S.att.i + 1) % cur_shelf()->n; S.att.ts = T; }
        break;
    case SC_SHELF:
        if (!S.menu.open && PREFS.attract && T - S.lastInput > (PREFS.attract == 1 ? 20 : 60) && fabsf(S.cam - S.shelf) < 0.01f) enter_attract();
        if (motion_on() && T > S.nextShoot) { S.shoot.on = true; S.shoot.t0 = T; S.shoot.x = 30 + frand() * 170; S.shoot.y = 18 + frand() * 50; S.nextShoot = T + 7 + frand() * 9; }
        break;
    default: break;
    }
    if (S.selDirty && T - S.selChangedAt > 4) { S.selDirty = false; prefs_save(); }
    S.fade = fmaxf(0, S.fade - dt * 2.2f);
}

static void render(void)
{
    build_budget = 1;
    switch (S.scene) {
    case SC_MESSAGE: {
        gfx_fill(0, 0, W, H, RGB(0x06071a));
        char lines[18][64];
        int n = 0;
        bool first[18] = {0};
        const char *p = S.msg;
        while (*p && n < 18) {   // wrap each paragraph separately
            char para[512];
            int k = 0;
            while (*p && *p != '\n' && k < 511) para[k++] = *p++;
            para[k] = 0;
            if (*p == '\n') p++;
            first[n] = n == 0;
            n += gfx_wrap(para, 296, 1, lines + n, 18 - n);
            if (n < 18) lines[n++][0] = 0;   // space between paragraphs
        }
        int y = (H - n * 11) / 2;
        for (int i = 0; i < n; i++) gfx_text(lines[i], 160, y + i * 11, first[i] ? 0xFFFF : RGB(0xb4bdd8), 1, AL_CENTER);
        return;
    }
    case SC_BOOT: render_boot(); break;
    case SC_SHELF: {
        render_home(false); draw_top_bar(); draw_particles();
        game_t *g = cur_game();
        hint_t h[4] = {{"A", "Open"}, {"Select", g && g->fav ? "Unfavorite" : "Favorite"}, {"Start", "Sort"}, {"Menu", "Settings"}};
        draw_hints(h, 4);
        break;
    }
    case SC_DETAIL: {
        game_t *g = cur_game();
        float p = det_p();
        render_home(true);
        dim(0.68f * fminf(1, p * 1.6f));
        draw_flip(g, g->sys, p, 0);
        draw_panel(g, iround(S.det.px));
        draw_top_bar(); draw_particles();
        hint_t pop[2] = {{"A", "Choose"}, {"B", "Cancel"}}, about[2] = {{"Up/Down", "Scroll"}, {"B", "Back"}};
        hint_t grid[3] = {{"A", "Choose"}, {"B", "Back"}, {"Select", "♥"}};
        if (S.det.pop != POP_NONE) draw_hints(pop, 2);
        else if (S.det.about) draw_hints(about, 2);
        else draw_hints(grid, 3);
        break;
    }
    case SC_LAUNCH: render_launch(); break;
    case SC_RETURN: render_return(); break;
    case SC_ATTRACT: render_attract(); break;
    }
    draw_toast(); draw_volume(); draw_menu();
    if (S.fade > 0) gfx_fill_a(0, 0, W, H, 0, (int)(fminf(1, S.fade) * 256));
}

void ui_frame(float dt, uint32_t buttons)
{
    T = board_seconds();
    if (S.scene == SC_MESSAGE) {
        if (S.msgDismiss && (buttons & ~S.held)) { S.scene = SC_SHELF; S.fade = 1; S.lastInput = T; }
        S.held = buttons;
    } else if (S.scene != SC_LAUNCH && S.scene != SC_RETURN) handle_input(buttons);
    else S.held = buttons;
    if (S.scene != SC_MESSAGE) {
        // animations follow real time: long frames are split into small steps
        if (dt > 0.25f) dt = 0.25f;
        int steps = (int)ceilf(dt / (1.0f / 60));
        if (steps < 1) steps = 1;
        for (int i = 0; i < steps; i++) update(dt / steps);
    }
    render();
    art_trim();
}

void ui_message(const char *text, bool dismissable)
{
    S.scene = SC_MESSAGE;
    S.msgDismiss = dismissable;
    snprintf(S.msg, sizeof(S.msg), "%s", text ? text : "");
}

void ui_init(void)
{
    T = board_seconds();
    memset(&S, 0, sizeof(S));
    S.menu.y = -150;
    S.titleT = -9; S.sortAt = -9; S.volT = -9;
    S.nextShoot = T + 4;
    sound_set_enabled(PREFS.sound);

    nvis = 0;
    for (int s = 0; s < SYS_COUNT; s++) if (SHELVES[s].n) vis[nvis++] = s;
    if (!nvis) { ui_message("No games found.\nPut ROMs in /roms/nes, /roms/gb and /roms/gbc on the SD card, then restart.", false); return; }

    for (int s = 0; s < SYS_COUNT; s++) {
        shelf_t *sh = &SHELVES[s];
        sh->sel = 0;
        lib_sort(sh, PREFS.sort[s] < SORT_COUNT ? PREFS.sort[s] : 0);
        sh->sel = 0;
        for (int j = 0; j < sh->n; j++) if (!strcmp(sh->games[j].file, PREFS.sel[s])) sh->sel = j;
        sh->scroll = sh->sel;
        for (int j = 0; j < sh->n; j++) sh->games[j].pos = j;
    }
    for (int i = 0; i < nvis; i++) if (vis[i] == PREFS.shelf) S.shelf = i;

    // coming back from a game?
    char key[160];
    uint32_t played = 0;
    if (pending_take(key, sizeof(key), &played)) {
        int sys, idx;
        game_t *g = lib_find(key, &sys, &idx);
        if (g) {
            g->play_sec += played;
            g->seq = lib_max_seq() + 1;
            lib_check_save(g);
            lib_save_meta();
            if (SHELVES[sys].sort == SORT_RECENT || SHELVES[sys].sort == SORT_MOST) {
                lib_sort(&SHELVES[sys], SHELVES[sys].sort);
                g = lib_find(key, &sys, &idx);
            }
            SHELVES[sys].sel = idx; SHELVES[sys].scroll = idx;
            for (int j = 0; j < SHELVES[sys].n; j++) SHELVES[sys].games[j].pos = j;
            for (int i = 0; i < nvis; i++) if (vis[i] == sys) S.shelf = i;
            S.cam = S.shelf;
            S.scene = SC_RETURN; S.ret.sys = sys; S.ret.idx = idx; S.ret.started = false;
            S.lastInput = T;
            return;
        }
    }
    S.cam = S.shelf;
    S.scene = SC_BOOT; S.bootT0 = T;
}
