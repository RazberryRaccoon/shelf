// Shelf for TV, first slice: choose a profile, browse your games as cartridges, play, come back.
#include "art.h"
#include "gfx.h"
#include "library.h"
#include "models.h"
#include "platform.h"
#include "text.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

enum { SCR_PROFILES, SCR_HOME };
static struct {
    int screen, psel, profile;
    int hi; float hf;                      // Home: the selected game and the smoothed scroll position
    int *order; int norder;                // Home: the games in order, most recently played first
    pose_t *pose;                          // one smoothed pose per game
    float profile_r[8];
    double launch_t; bool launching;       // the launch animation
    const game_t *returning; double return_t;
    char toast[160]; double toast_t;
    int mood, mood_prev; float mood_t;     // backdrop colors: a console, or -1 for the neutral one
    float pl[4];                           // planet x, y, radius, opacity
    double t; bool quit; int exit_code;
    bool snap;                             // put cartridges straight into place on the next frame
} S;
static GLuint neb[SYS_COUNT + 1], planet_tex[SYS_COUNT + 1];
static const float NEUTRAL_PLANET[3][3] = { HEXC(0xe6dcff), HEXC(0x8b78e6), HEXC(0x2b1f66) }, NEUTRAL_NEB[2][3] = { HEXC(0x2a1a5e), HEXC(0x0d0b2c) };
static const float STAR[4] = { .933f, .945f, 1, 1 }, DUST[4] = { .604f, .635f, .8f, 1 }, FAINT[4] = { .933f, .945f, 1, .55f };

/* ---------- sounds: soft sine tones ---------- */
static void tone(float freq, float dur, float gain, float glide) {
    static int16_t buf[22050]; int n = (int)(dur * 22050); if (n > 22050) n = 22050;
    float ph = 0;
    for (int i = 0; i < n; i++) {
        float t = i / 22050.f, env = fminf(t / .012f, 1) * expf(-t / (dur * .3f)), f = freq * (glide ? powf(glide, t / dur) : 1);
        ph += 6.2831853f * f / 22050; buf[i] = (int16_t)(sinf(ph) * env * gain * 32767);
    }
    plat_audio(buf, n);
}
static void sfx_move(void) { tone(1320, .05f, .05f, 0); }
static void sfx_select(void) { tone(660, .12f, .1f, 0); tone(990, .16f, .08f, 0); }
static void sfx_back(void) { tone(700, .12f, .08f, .7f); }

/* ---------- helpers ---------- */
static void approach(float *v, float target, float dt, float rate) { *v += (target - *v) * (1 - expf(-dt * rate)); }
static void approach_pose(pose_t *o, const pose_t *t, float dt, float rate) {
    approach(&o->x, t->x, dt, rate); approach(&o->y, t->y, dt, rate); approach(&o->z, t->z, dt, rate); approach(&o->rx, t->rx, dt, rate);
    approach(&o->ry, t->ry, dt, rate); approach(&o->s, t->s, dt, rate); approach(&o->alpha, t->alpha, dt, rate); approach(&o->bright, t->bright, dt, rate);
}
static void toast(const char *msg) { snprintf(S.toast, sizeof S.toast, "%s", msg); S.toast_t = S.t; }
static void set_mood(int m) { if (m == S.mood) return; S.mood_prev = S.mood; S.mood = m; S.mood_t = 0; }
static GLuint mood_neb(int m) { int k = m < 0 ? SYS_COUNT : m; if (!neb[k]) neb[k] = art_nebula(m < 0 ? NEUTRAL_NEB : SYSINFO[m].neb, 1000 + k); return neb[k]; }
static GLuint mood_planet(int m) { int k = m < 0 ? SYS_COUNT : m; if (!planet_tex[k]) planet_tex[k] = art_planet(m < 0 ? NEUTRAL_PLANET : SYSINFO[m].planet, 2000 + k); return planet_tex[k]; }
static const float *mood_atm(int m) { return m < 0 ? NEUTRAL_PLANET[0] : SYSINFO[m].planet[0]; }
static void ago(long long when, char *out, size_t n) {
    long long d = (long long)time(NULL) - when;
    if (!when) snprintf(out, n, "Not played yet");
    else if (d < 3600) snprintf(out, n, "Played just now");
    else if (d < 86400) snprintf(out, n, "Played %lld hour%s ago", d / 3600, d / 3600 > 1 ? "s" : "");
    else if (d < 2 * 86400) snprintf(out, n, "Played yesterday");
    else snprintf(out, n, "Played %lld days ago", d / 86400);
}
static void played(long long s, char *out, size_t n) {
    if (s < 60) out[0] = 0; else if (s < 3600) snprintf(out, n, "%lld minutes played", s / 60); else snprintf(out, n, "%lld hour%s played", s / 3600, s / 3600 > 1 ? "s" : "");
}

/* ---------- Home ---------- */
static void build_order(void) {   // recently played first, then everything else alphabetically
    profile_t *p = &PROFILES[S.profile]; S.norder = 0;
    bool *used = calloc(NGAMES, 1);
    for (int i = 0; i < p->nrecent; i++) for (int g = 0; g < NGAMES; g++) if (!used[g] && !strcmp(GAMES[g].key, p->recent[i].key)) { used[g] = 1; S.order[S.norder++] = g; break; }
    for (int g = 0; g < NGAMES; g++) if (!used[g]) S.order[S.norder++] = g;
    free(used);
}
static void enter_home(void) {
    S.screen = SCR_HOME; build_order(); S.hi = 0; S.hf = 0;
    if (S.norder) set_mood(GAMES[S.order[0]].sys);
}
static void choose_profile(int i) {
    S.profile = i; sfx_select();
    char path[1024]; snprintf(path, sizeof path, "%s/.run/profile", lib_home());
    FILE *f = fopen(path, "w"); if (f) { fprintf(f, "%s\n", PROFILES[i].name); fclose(f); }
    enter_home();
}
static void start_launch(void) {
    if (!S.norder || S.launching) return;
    S.launching = true; S.launch_t = S.t; sfx_select();
}
static void finish_launch(void) {
    game_t *g = &GAMES[S.order[S.hi]]; profile_t *p = &PROFILES[S.profile];
    const char *err = lib_launch(p, g, lib_has_save(p, g));
    if (err) { toast(err); S.launching = false; return; }
    S.quit = true; S.exit_code = 0;   // the session script starts RetroArch, then Shelf again
}

static void draw_home(float dt) {
    profile_t *p = &PROFILES[S.profile];
    approach(&S.hf, (float)S.hi, dt, 8);
    float launch = S.launching ? (float)(S.t - S.launch_t) : 0;
    cam_perspective(0, CAMZ, 0); meshes_begin();
    // farthest first, so the selected cartridge is drawn last
    for (int pass = 6; pass >= 0; pass--) for (int i = 0; i < S.norder; i++) {
        int dist = abs(i - S.hi); if (dist != pass && !(pass == 6 && dist > 6)) continue;
        game_t *g = &GAMES[S.order[i]]; pose_t *o = &S.pose[S.order[i]];
        float off = i - S.hf; bool foc = i == S.hi;
        float xpx = 620 + off * 320 + (off > 0 ? 1 : off < 0 ? -1 : 0) * fminf(fabsf(off), 1) * 110, ypx = foc ? 400 : 445;
        float alpha = off < 0 ? (off > -1 ? 1 + .55f * off : .45f * (1 - clampf(-off - 1, 0, 1))) : 1 - clampf((off - 4.2f) / 1.2f, 0, 1);
        pose_t t = { 0 }; px_to_world(xpx, ypx, &t.x, &t.y);
        t.z = foc ? .6f : 0; t.ry = clampf(-off * .32f, -.62f, .62f) + (foc ? sinf((float)S.t * .9f) * .12f : 0); t.rx = foc ? -.06f : 0;
        t.s = cart_scale(g->sys, foc ? 450 : 260); t.alpha = alpha; t.bright = foc ? 1 : .7f;
        if (foc && S.launching) { float k = easef(launch / .7f); t.y += k * 1.2f; t.z += k * 3; t.alpha = 1 - easef((launch - .35f) / .35f); }
        float rate = (S.returning == g && S.t - S.return_t < 1.2) ? 4.5f : 10;
        if (S.snap && S.returning != g) *o = t; else approach_pose(o, &t, dt, rate);
        if (o->alpha > .01f) draw_cart(g->sys, lib_label(g), o);
    }
    S.snap = false;
    meshes_end();
    ui_begin();
    // the top bar: the profile and the clock
    char clock[16]; time_t now = time(NULL); struct tm *tm = localtime(&now); int h12 = tm->tm_hour % 12 ? tm->tm_hour % 12 : 12;
    snprintf(clock, sizeof clock, "%d:%02d %s", h12, tm->tm_min, tm->tm_hour < 12 ? "AM" : "PM");
    float nw = text_width(p->name, FONT_BODY, 700, 26), cw = text_width(clock, FONT_BODY, 600, 24);
    float px0 = VW - 96 - (nw + 92);
    ui_rect(px0, 52, nw + 92, 64, 32, (float[]){ .047f, .067f, .17f, .64f });
    ui_rect(px0 + 9, 61, 46, 46, 23, (float[]){ p->colors[1][0], p->colors[1][1], p->colors[1][2], 1 });
    text_draw(p->name, FONT_BODY, 700, 26, px0 + 70, 68, STAR, 0);
    ui_rect(px0 - cw - 60, 52, cw + 44, 64, 32, (float[]){ .047f, .067f, .17f, .64f });
    text_draw(clock, FONT_BODY, 600, 24, px0 - cw - 38, 70, FAINT, 0);
    text_draw("Shelf", FONT_DISPLAY, 500, 30, 96, 66, STAR, 0);
    if (!S.norder) {
        text_draw("No games yet", FONT_DISPLAY, 500, 48, VW / 2.f, 380, STAR, 1);
        text_draw("From your computer, open \\\\shelf\\shelf\\roms and copy games into the folder for each console.", FONT_BODY, 500, 26, VW / 2.f, 470, DUST, 1);
        text_draw("Then hold Select and Start for two seconds, and Shelf will find them.", FONT_BODY, 500, 26, VW / 2.f, 515, DUST, 1);
        return;
    }
    game_t *g = &GAMES[S.order[S.hi]]; recent_t *r = lib_recent(p, g->key);
    float info_a = S.launching ? 1 - easef(launch / .3f) : 1, c[4] = { 1, 1, 1, info_a };
    text_draw(g->title, FONT_DISPLAY, 500, 58, 160, 690, (float[]){ STAR[0], STAR[1], STAR[2], info_a }, 0);
    const float *acc = SYSINFO[g->sys].accent; float chipw = text_width(SYSINFO[g->sys].name, FONT_BODY, 700, 21) + 52;
    ui_rect(160, 786, chipw, 36, 18, (float[]){ .933f, .945f, 1, .1f * info_a });
    ui_rect(178, 798, 12, 12, 6, (float[]){ acc[0], acc[1], acc[2], info_a });
    text_draw(SYSINFO[g->sys].name, FONT_BODY, 700, 21, 200, 791, c, 0);
    char a[64], b[64]; ago(r ? r->last : 0, a, sizeof a); played(r ? r->seconds : 0, b, sizeof b);
    float x = 160 + chipw + 28; x += text_draw(a, FONT_BODY, 500, 24, x, 790, (float[]){ DUST[0], DUST[1], DUST[2], info_a }, 0) + 30;
    if (b[0]) x += text_draw(b, FONT_BODY, 500, 24, x, 790, (float[]){ DUST[0], DUST[1], DUST[2], info_a }, 0) + 30;
    if (lib_has_save(p, g)) text_draw("Saved", FONT_BODY, 600, 24, x, 790, (float[]){ acc[0], acc[1], acc[2], info_a }, 0);
}

/* ---------- Who's playing ---------- */
static void draw_profiles(float dt) {
    int n = NPROFILES;
    ui_begin();
    text_draw("Shelf", FONT_DISPLAY, 500, 30, 96, 60, STAR, 0);
    text_draw("Who’s playing?", FONT_DISPLAY, 400, 56, VW / 2.f, 220, STAR, 1);
    for (int i = 0; i < n; i++) {
        profile_t *p = &PROFILES[i]; bool sel = i == S.psel;
        if (!p->planet) p->planet = art_planet(p->colors, hash_str(p->name));
        approach(&S.profile_r[i], sel ? 112 : 80, dt, 10);
        float x = VW / 2.f + (i - (n - 1) / 2.f) * 340;
        draw_planet(p->planet, p->colors[0], x, sel ? 495 : 505, S.profile_r[i], S.t * .004f, sel ? 1 : .75f);
        ui_begin();
        text_draw(p->name, FONT_DISPLAY, 400, 30, x, sel ? 700 : 686, sel ? STAR : FAINT, 1);
        if (sel) { char s[64]; if (p->nrecent) snprintf(s, sizeof s, "%d game%s played", p->nrecent, p->nrecent == 1 ? "" : "s"); else snprintf(s, sizeof s, "New profile");
            text_draw(s, FONT_BODY, 500, 21, x, 748, DUST, 1); }
    }
}

/* ---------- the frame ---------- */
static bool held[BTN_COUNT], was[BTN_COUNT]; static float rep[BTN_COUNT];
static bool pressed(int b, float dt) {   // a new press, or a repeat while a direction is held
    if (held[b] && !was[b]) { rep[b] = 0; return true; }
    if (held[b] && b <= BTN_RIGHT) { rep[b] += dt; if (rep[b] > .38f) { rep[b] -= .11f; return true; } }
    return false;
}
static float combo_t;
static void input(float dt) {
    if (held[BTN_SELECT] && held[BTN_START]) { combo_t += dt; if (combo_t > 2) { S.quit = true; S.exit_code = 3; } } else combo_t = 0;   // restart Shelf, which looks for new games
    if (S.launching) return;
    if (S.screen == SCR_PROFILES) {
        if (pressed(BTN_LEFT, dt) && S.psel > 0) { S.psel--; sfx_move(); }
        if (pressed(BTN_RIGHT, dt) && S.psel < NPROFILES - 1) { S.psel++; sfx_move(); }
        if (pressed(BTN_A, dt)) choose_profile(S.psel);
    } else {
        if (pressed(BTN_LEFT, dt) && S.hi > 0) { S.hi--; sfx_move(); }
        if (pressed(BTN_RIGHT, dt) && S.hi < S.norder - 1) { S.hi++; sfx_move(); }
        if (pressed(BTN_A, dt)) start_launch();
        if (pressed(BTN_B, dt) && NPROFILES > 1) { S.screen = SCR_PROFILES; set_mood(-1); sfx_back(); }
        if (S.norder) set_mood(GAMES[S.order[S.hi]].sys);
    }
}
static void frame(float dt) {
    S.t += dt; GTIME = (float)S.t;
    S.mood_t = fminf(1, S.mood_t + dt / .8f);
    const float *acc = S.mood < 0 ? (float[]){ .76f, .71f, 1 } : SYSINFO[S.mood].accent;
    for (int i = 0; i < 3; i++) approach(&RIM[i], acc[i], dt, 4);
    float tp[4] = { 960, 1560, 760, 1 }; if (S.screen == SCR_HOME) { tp[0] = 1760; tp[1] = 1300; tp[2] = 600; }
    for (int i = 0; i < 4; i++) approach(&S.pl[i], tp[i], dt, 3);
    gfx_begin_frame();
    float m = easef(S.mood_t);
    draw_backdrop(mood_neb(S.mood_prev), mood_neb(S.mood), m, 1, S.screen == SCR_HOME ? S.hf * .006f : 0);
    if (m < 1) draw_planet(mood_planet(S.mood_prev), mood_atm(S.mood_prev), S.pl[0], S.pl[1], S.pl[2], S.t * .004f, 1 - m);
    draw_planet(mood_planet(S.mood), mood_atm(S.mood), S.pl[0], S.pl[1], S.pl[2], S.t * .004f, m);
    if (S.screen == SCR_PROFILES) draw_profiles(dt); else draw_home(dt);
    ui_begin();
    if (S.launching) {   // dip into the console's color, then hand over
        float k = easef(((float)(S.t - S.launch_t) - .45f) / .45f), *a = (float *)SYSINFO[GAMES[S.order[S.hi]].sys].accent;
        ui_rect(0, 0, VW, VH, 0, (float[]){ .027f + a[0] * .1f, .043f + a[1] * .1f, .118f + a[2] * .1f, k });
        if (S.t - S.launch_t > .95) finish_launch();
    }
    if (S.toast[0] && S.t - S.toast_t < 3) {   // a short message at the top
        float k = easeoutf((float)(S.t - S.toast_t) / .4f) * (1 - easef(((float)(S.t - S.toast_t) - 2.6f) / .4f));
        float w = text_width(S.toast, FONT_BODY, 700, 24) + 68;
        ui_rect(VW / 2.f - w / 2, 60 - (1 - k) * 160, w, 60, 30, STAR);
        text_draw(S.toast, FONT_BODY, 700, 24, VW / 2.f, 75 - (1 - k) * 160, (float[]){ .027f, .043f, .118f, 1 }, 1);
    }
    text_frame_end();
}

int main(int argc, char **argv) {
    (void)argc; (void)argv;
    int w, h; if (!plat_init(&w, &h)) { fprintf(stderr, "Shelf: couldn’t open the screen\n"); return 1; }
    gfx_init(w, h);
    lib_init();
    char fonts[1024]; snprintf(fonts, sizeof fonts, "%s/fonts", lib_home());
    if (!text_init(fonts)) return 1;
    S.order = malloc((NGAMES + 1) * sizeof(int)); S.pose = calloc(NGAMES + 1, sizeof(pose_t));
    S.mood = S.mood_prev = -1; S.mood_t = 1; S.pl[0] = 960; S.pl[1] = 1560; S.pl[2] = 760; S.pl[3] = 1;
    // coming back from a game? go straight to Home with that cartridge flying back in
    char key[300]; long long secs; bool saved;
    if (lib_read_return(key, sizeof key, &secs, &saved)) {
        char path[1024], name[64] = ""; snprintf(path, sizeof path, "%s/.run/profile", lib_home());
        FILE *f = fopen(path, "r"); if (f) { if (fgets(name, sizeof name, f)) name[strcspn(name, "\r\n")] = 0; fclose(f); }
        for (int i = 0; i < NPROFILES; i++) if (!strcmp(PROFILES[i].name, name)) S.profile = i;
        lib_touch(&PROFILES[S.profile], key, secs);
        enter_home();
        game_t *g = lib_find(key);
        if (g) {
            S.returning = g; S.return_t = 0; S.mood = S.mood_prev = g->sys; S.pl[0] = 1760; S.pl[1] = 1300; S.pl[2] = 600;
            pose_t *o = &S.pose[g - GAMES]; o->x = 0; o->y = 4.5f; o->z = 2; o->s = cart_scale(g->sys, 520); o->alpha = 1; o->bright = 1; o->ry = .4f;
            toast(saved ? "Saved" : "Back on Home");
            S.snap = true;
        }
    } else { S.screen = SCR_PROFILES; S.psel = 0; }
    double last = plat_time();
    while (!S.quit) {
        memcpy(was, held, sizeof held);
        if (!plat_poll(held)) { S.exit_code = 1; break; }
        double now = plat_time(); float dt = (float)fmin(.05, now - last); last = now;
        input(dt);
        frame(dt);
        plat_swap();
    }
    plat_shutdown();
    return S.exit_code;
}
