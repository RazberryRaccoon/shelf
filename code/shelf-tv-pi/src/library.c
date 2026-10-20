// The library on disk:
//   ~/shelf/roms/<console>/            your games
//   ~/shelf/art/<console>/<game>.label.png   label art (from the label cropper)
//   ~/shelf/profiles.txt               one profile name per line
//   ~/shelf/profiles/<name>/recent.txt last played time, seconds played and the game, one per line
//   ~/shelf/saves/<name>/<console>/    RetroArch saves for each profile and console
//   ~/shelf/.run/                      the launch request and the note about the game that just ended
#include "library.h"
#include "art.h"
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

const sysinfo_t SYSINFO[SYS_COUNT] = {
    { "nes", "NES", "Nintendo Entertainment System", "nestopia", ".nes.zip.", { HEXC(0xffc2cf), HEXC(0xd9587f), HEXC(0x5c1a3a) }, { HEXC(0x3d1236), HEXC(0x140c2c) }, HEXC(0xff9db6), 256, 240, 4 / 3.f, false },
    { "snes", "SNES", "Super Nintendo", "snes9x", ".sfc.smc.zip.", { HEXC(0xe4dcff), HEXC(0x8f7ee8), HEXC(0x2f2470) }, { HEXC(0x271a5a), HEXC(0x0e0b2a) }, HEXC(0xc2b6ff), 256, 224, 4 / 3.f, false },
    { "genesis", "Genesis", "Sega Genesis", "genesis_plus_gx", ".md.gen.bin.smd.zip.", { HEXC(0xbcd6ff), HEXC(0x3f74e6), HEXC(0x0f2463) }, { HEXC(0x0f2556), HEXC(0x070c26) }, HEXC(0x86b0ff), 320, 224, 4 / 3.f, false },
    { "gb", "Game Boy", "Game Boy", "gambatte", ".gb.zip.", { HEXC(0xe6f2b8), HEXC(0x8fb34a), HEXC(0x2c4818) }, { HEXC(0x1d3324), HEXC(0x08121a) }, HEXC(0xb9db74), 160, 144, 10 / 9.f, true },
    { "gbc", "Game Boy Color", "Game Boy Color", "gambatte", ".gbc.zip.", { HEXC(0xbdf7ee), HEXC(0x2fb8ae), HEXC(0x0b4651) }, { HEXC(0x0b3a44), HEXC(0x061424) }, HEXC(0x5fe0d4), 160, 144, 10 / 9.f, true },
    { "gba", "Game Boy Advance", "Game Boy Advance", "mgba", ".gba.zip.", { HEXC(0xd9ccff), HEXC(0x7258ea), HEXC(0x21175e) }, { HEXC(0x22185a), HEXC(0x0b0a26) }, HEXC(0xab98ff), 240, 160, 1.5f, true },
};
static const float PALETTES[5][3][3] = {
    { HEXC(0xffd38a), HEXC(0xe8834a), HEXC(0x6a2a1e) }, { HEXC(0xbff3ff), HEXC(0x3fa4d9), HEXC(0x13355e) }, { HEXC(0xf6c8ff), HEXC(0xb05ad8), HEXC(0x401a5e) },
    { HEXC(0xd6ffc8), HEXC(0x5ac46a), HEXC(0x1a4a2a) }, { HEXC(0xffe0f0), HEXC(0xe0607e), HEXC(0x5a1a33) },
};

game_t *GAMES; int NGAMES; profile_t PROFILES[8]; int NPROFILES;
static char HOME_DIR[512];
const char *lib_home(void) { return HOME_DIR; }

static void mkdirs(const char *path) {   // like mkdir -p
    char tmp[1024]; snprintf(tmp, sizeof tmp, "%s", path);
    for (char *p = tmp + 1; *p; p++) if (*p == '/') { *p = 0; mkdir(tmp, 0755); *p = '/'; }
    mkdir(tmp, 0755);
}
static bool exists(const char *p) { struct stat st; return stat(p, &st) == 0; }
static bool copy_file(const char *from, const char *to) {
    FILE *a = fopen(from, "rb"); if (!a) return false; FILE *b = fopen(to, "wb"); if (!b) { fclose(a); return false; }
    char buf[65536]; size_t n; bool ok = true;
    while ((n = fread(buf, 1, sizeof buf, a)) > 0) if (fwrite(buf, 1, n, b) != n) { ok = false; break; }
    fclose(a); if (fclose(b)) ok = false; return ok;
}

// "Legend of Zelda, The - Link's Awakening (USA, Europe) [!]" -> "The Legend of Zelda - Link's Awakening"
static void clean_title(const char *base, char *out, size_t n) {
    char t[600]; snprintf(t, sizeof t, "%s", base);
    for (char *p = t; *p; p++) if ((*p == '(' || *p == '[') && p > t) { *p = 0; break; }
    size_t L = strlen(t); while (L && t[L - 1] == ' ') t[--L] = 0;
    for (char *p = t; *p; p++) { if (*p == '_') *p = ' '; }
    const char *arts[] = { ", The", ", A", ", An" };
    for (int i = 0; i < 3; i++) {
        char *hit = strstr(t, arts[i]); size_t al = strlen(arts[i]);
        if (hit && (hit[al] == 0 || !strncmp(hit + al, " - ", 3))) {
            char rest[600]; snprintf(rest, sizeof rest, "%s", hit + al); *hit = 0;
            char moved[1300]; snprintf(moved, sizeof moved, "%s %s%s", arts[i] + 2, t, rest); snprintf(t, sizeof t, "%.599s", moved); break;
        }
    }
    snprintf(out, n, "%.*s", (int)n - 1, t[0] ? t : base);
}
static int by_title(const void *a, const void *b) { return strcasecmp(((const game_t *)a)->title, ((const game_t *)b)->title); }

void lib_init(void) {
    const char *h = getenv("SHELF_HOME");
    if (h) snprintf(HOME_DIR, sizeof HOME_DIR, "%s", h); else snprintf(HOME_DIR, sizeof HOME_DIR, "%s/shelf", getenv("HOME") ? getenv("HOME") : ".");
    char p[1024];
    snprintf(p, sizeof p, "%s/.run", HOME_DIR); mkdirs(p);
    // games
    int cap = 256; GAMES = malloc(cap * sizeof *GAMES); NGAMES = 0;
    for (int s = 0; s < SYS_COUNT; s++) {
        snprintf(p, sizeof p, "%s/roms/%s", HOME_DIR, SYSINFO[s].dir); mkdirs(p);
        DIR *d = opendir(p); if (!d) continue;
        struct dirent *e;
        while ((e = readdir(d))) {
            const char *dot = strrchr(e->d_name, '.'); if (!dot || e->d_name[0] == '.') continue;
            char ext[16]; snprintf(ext, sizeof ext, "%s.", dot); for (char *q = ext; *q; q++) if (*q >= 'A' && *q <= 'Z') *q += 32;
            if (!strstr(SYSINFO[s].exts, ext)) continue;
            if (NGAMES == cap) { cap *= 2; GAMES = realloc(GAMES, cap * sizeof *GAMES); }
            game_t *g = &GAMES[NGAMES++]; memset(g, 0, sizeof *g); g->sys = s;
            snprintf(g->file, sizeof g->file, "%s", e->d_name);
            snprintf(g->base, sizeof g->base, "%.*s", (int)(dot - e->d_name), e->d_name);
            clean_title(g->base, g->title, sizeof g->title);
            snprintf(g->key, sizeof g->key, "%s/%s", SYSINFO[s].dir, g->file);
        }
        closedir(d);
    }
    qsort(GAMES, NGAMES, sizeof *GAMES, by_title);
    // profiles
    snprintf(p, sizeof p, "%s/profiles.txt", HOME_DIR);
    FILE *f = fopen(p, "r");
    if (!f) { f = fopen(p, "w"); if (f) { fputs("Player 1\n", f); fclose(f); } f = fopen(p, "r"); }
    char line[128]; NPROFILES = 0;
    while (f && NPROFILES < 8 && fgets(line, sizeof line, f)) {
        line[strcspn(line, "\r\n")] = 0; if (!line[0]) continue;
        profile_t *pr = &PROFILES[NPROFILES]; memset(pr, 0, sizeof *pr);
        snprintf(pr->name, sizeof pr->name, "%.63s", line); memcpy(pr->colors, PALETTES[NPROFILES % 5], sizeof pr->colors);
        lib_load_recent(pr); NPROFILES++;
    }
    if (f) fclose(f);
    if (!NPROFILES) { NPROFILES = 1; memset(&PROFILES[0], 0, sizeof PROFILES[0]); snprintf(PROFILES[0].name, 64, "Player 1"); memcpy(PROFILES[0].colors, PALETTES[0], sizeof PALETTES[0]); }
}
game_t *lib_find(const char *key) { for (int i = 0; i < NGAMES; i++) if (!strcmp(GAMES[i].key, key)) return &GAMES[i]; return NULL; }

static void recent_path(const profile_t *p, char *out, size_t n) { snprintf(out, n, "%s/profiles/%s/recent.txt", HOME_DIR, p->name); }
void lib_load_recent(profile_t *p) {
    char path[1024]; recent_path(p, path, sizeof path); p->nrecent = 0;
    FILE *f = fopen(path, "r"); if (!f) return;
    char line[512];
    while (p->nrecent < MAX_RECENT && fgets(line, sizeof line, f)) {
        recent_t *r = &p->recent[p->nrecent]; char key[300];
        if (sscanf(line, "%lld\t%lld\t%299[^\r\n]", &r->last, &r->seconds, key) == 3) { snprintf(r->key, sizeof r->key, "%s", key); p->nrecent++; }
    }
    fclose(f);
}
static void save_recent(const profile_t *p) {
    char path[1024], dir[1024]; recent_path(p, path, sizeof path); snprintf(dir, sizeof dir, "%s/profiles/%s", HOME_DIR, p->name); mkdirs(dir);
    char tmp[1100]; snprintf(tmp, sizeof tmp, "%s.new", path);
    FILE *f = fopen(tmp, "w"); if (!f) return;
    for (int i = 0; i < p->nrecent; i++) fprintf(f, "%lld\t%lld\t%s\n", p->recent[i].last, p->recent[i].seconds, p->recent[i].key);
    fclose(f); rename(tmp, path);   // written in full first, so a power cut can't leave half a file
}
recent_t *lib_recent(profile_t *p, const char *key) { for (int i = 0; i < p->nrecent; i++) if (!strcmp(p->recent[i].key, key)) return &p->recent[i]; return NULL; }
void lib_touch(profile_t *p, const char *key, long long seconds) {
    recent_t r = { { 0 }, (long long)time(NULL), seconds }; snprintf(r.key, sizeof r.key, "%s", key);
    recent_t *old = lib_recent(p, key);
    if (old) { r.seconds += old->seconds; memmove(old, old + 1, (size_t)(p->recent + p->nrecent - old - 1) * sizeof *old); p->nrecent--; }
    if (p->nrecent == MAX_RECENT) p->nrecent--;
    memmove(p->recent + 1, p->recent, (size_t)p->nrecent * sizeof *p->recent); p->recent[0] = r; p->nrecent++;
    save_recent(p);
}

static void save_dir(const profile_t *p, const game_t *g, char *out, size_t n) { snprintf(out, n, "%s/saves/%s/%s", HOME_DIR, p->name, SYSINFO[g->sys].dir); }
bool lib_has_save(profile_t *p, const game_t *g) {
    char d[1024], s[1400]; save_dir(p, g, d, sizeof d); snprintf(s, sizeof s, "%s/%s.slot0.state", d, g->base); return exists(s);
}
static bool find_core(const char *name, char *out, size_t n) {
    const char *dirs[] = { "/usr/lib/aarch64-linux-gnu/libretro", "/usr/lib/arm-linux-gnueabihf/libretro", "/usr/lib/x86_64-linux-gnu/libretro", "/usr/lib/libretro", NULL };
    char user[1024]; snprintf(user, sizeof user, "%.900s/.config/retroarch/cores", getenv("HOME") ? getenv("HOME") : ".");
    snprintf(out, n, "%s/%s_libretro.so", user, name); if (exists(out)) return true;   // cores you installed yourself come first
    for (int i = 0; dirs[i]; i++) { snprintf(out, n, "%s/%s_libretro.so", dirs[i], name); if (exists(out)) return true; }
    return false;
}
// RetroArch loads <game>.state.auto when it starts and writes it back when it quits.
// Shelf keeps the real save as <game>.slot0.state and copies it in and out around each session.
const char *lib_launch(profile_t *p, const game_t *g, bool resume) {
    static char msg[160];
    char core[1100]; if (!find_core(SYSINFO[g->sys].core, core, sizeof core)) { snprintf(msg, sizeof msg, "The %s emulator isn’t installed", SYSINFO[g->sys].name); return msg; }
    char dir[1024], rom[1400], cfg[1100], autos[1400], slot[1400], req[1100], tmp[1200];
    save_dir(p, g, dir, sizeof dir); mkdirs(dir);
    snprintf(rom, sizeof rom, "%s/roms/%s/%s", HOME_DIR, SYSINFO[g->sys].dir, g->file);
    snprintf(autos, sizeof autos, "%s/%s.state.auto", dir, g->base); snprintf(slot, sizeof slot, "%s/%s.slot0.state", dir, g->base);
    if (resume && exists(slot)) {
        if (!copy_file(slot, autos)) { snprintf(msg, sizeof msg, "Couldn’t prepare the save (%s)", strerror(errno)); return msg; }
    } else { remove(autos); }
    snprintf(cfg, sizeof cfg, "%s/.run/launch.cfg", HOME_DIR);
    FILE *f = fopen(cfg, "w"); if (!f) { snprintf(msg, sizeof msg, "Couldn’t write the launch settings"); return msg; }
    fprintf(f, "savestate_directory = \"%s\"\nsavefile_directory = \"%s\"\n", dir, dir); fclose(f);
    snprintf(req, sizeof req, "%s/.run/launch.req", HOME_DIR); snprintf(tmp, sizeof tmp, "%s.new", req);
    f = fopen(tmp, "w"); if (!f) { snprintf(msg, sizeof msg, "Couldn’t write the launch request"); return msg; }
    fprintf(f, "CORE=%s\nROM=%s\nCONFIG=%s\nAUTO=%s\nSLOT=%s\nGAME=%s\nPROFILE=%s\n", core, rom, cfg, autos, slot, g->key, p->name);
    fclose(f); rename(tmp, req);
    return NULL;
}
// the session script leaves a note when a game ends: GAME=..., SECONDS=..., SAVED=1
bool lib_read_return(char *key, size_t n, long long *seconds, bool *saved) {
    char path[1024]; snprintf(path, sizeof path, "%s/.run/return.txt", HOME_DIR);
    FILE *f = fopen(path, "r"); if (!f) return false;
    char line[600]; key[0] = 0; *seconds = 0; *saved = false;
    while (fgets(line, sizeof line, f)) {
        line[strcspn(line, "\r\n")] = 0;
        if (!strncmp(line, "GAME=", 5)) snprintf(key, n, "%s", line + 5);
        else if (!strncmp(line, "SECONDS=", 8)) *seconds = atoll(line + 8);
        else if (!strncmp(line, "SAVED=", 6)) *saved = line[6] == '1';
    }
    fclose(f); remove(path);
    return key[0] != 0;
}
GLuint lib_label(game_t *g) {
    if (!g->label_tried) {
        g->label_tried = true;
        char png[1400]; snprintf(png, sizeof png, "%s/art/%s/%s.label.png", HOME_DIR, SYSINFO[g->sys].dir, g->base);
        g->label = art_label(g->sys, g->title, exists(png) ? png : NULL);
    }
    return g->label;
}
