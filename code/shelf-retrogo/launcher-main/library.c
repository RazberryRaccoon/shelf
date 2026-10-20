#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <ctype.h>
#include <dirent.h>
#include <sys/stat.h>
#include <rg_system.h>
#include "cJSON.h"
#include "library.h"
#include "board.h"

#define META_PATH RG_STORAGE_ROOT "/shelf/library.json"

const char *SYS_DIR[SYS_COUNT] = {"nes", "gb", "gbc"};
const char *SYS_NAME[SYS_COUNT] = {"NES", "Game Boy", "Game Boy Color"};
const char *SYS_LONG[SYS_COUNT] = {"NES cartridge", "Game Boy cartridge", "Game Boy Color cartridge"};
const char *SORT_LABEL[SORT_COUNT] = {"Sorted A to Z", "Recently played first", "Most played first", "Favorites first"};
shelf_t SHELVES[SYS_COUNT];

uint32_t shelf_hash(const char *s)
{
    uint32_t h = 2166136261u;
    while (*s) { h ^= (uint8_t)*s++; h *= 16777619u; }
    return h;
}

static const char *ext_of(const char *f)
{
    const char *d = strrchr(f, '.');
    return d ? d + 1 : "";
}

static bool wanted(sys_t s, const char *f)
{
    const char *e = ext_of(f);
    if (!strcasecmp(e, "zip")) return true;
    if (s == SYS_NES) return !strcasecmp(e, "nes");
    if (s == SYS_GB) return !strcasecmp(e, "gb");
    return !strcasecmp(e, "gbc");
}

// "Legend of Zelda, The - A Link to the Past (USA).sfc" -> "The Legend of Zelda - A Link to the Past"
static void make_title(const char *file, char *out, int len)
{
    char t[128];
    str_copy(t, sizeof(t), file);
    char *dot = strrchr(t, '.'); if (dot) *dot = 0;
    for (char *p = t; *p; p++) if (*p == '_') *p = ' ';
    char *cut = NULL;
    for (char *p = t + 1; *p; p++) if ((*p == '(' || *p == '[') && p[-1] == ' ') { cut = p - 1; break; }
    if (cut && cut > t) *cut = 0;
    int n = strlen(t);
    while (n > 0 && t[n - 1] == ' ') t[--n] = 0;
    // standard names move a leading article to the end: "Legend of Zelda, The"
    static const char *ARTICLES[] = {", The", ", A", ", An"};
    for (int a = 0; a < 3; a++) {
        size_t al = strlen(ARTICLES[a]);
        char *hit = NULL;
        for (char *p = t; (p = strstr(p, ARTICLES[a])); p++)
            if (p[al] == 0 || !strncmp(p + al, " - ", 3)) { hit = p; break; }
        if (hit) {
            char rest[128], moved[300];
            str_copy(rest, sizeof(rest), hit + al);   // "" or " - Subtitle"
            *hit = 0;
            snprintf(moved, sizeof(moved), "%s %s%s", ARTICLES[a] + 2, t, rest);
            str_copy(t, sizeof(t), moved);
            break;
        }
    }
    str_copy(out, len, t[0] ? t : file);
}

void lib_key(const game_t *g, char *out, int len) { snprintf(out, len, "%s/%s", SYS_DIR[g->sys], g->file); }
void lib_rom_path(const game_t *g, char *out, int len) { snprintf(out, len, "%s/%s/%s", RG_BASE_PATH_ROMS, SYS_DIR[g->sys], g->file); }

static char *dup_or_null(const char *s) { return (s && s[0]) ? strdup(s) : NULL; }

bool lib_check_save(game_t *g)
{
    char path[300];
    lib_rom_path(g, path, sizeof(path));
    board_bus_lock();
    rg_emu_states_t *st = rg_emu_get_states(path, SAVE_SLOTS);
    board_bus_unlock();
    for (int i = 0; i < SAVE_SLOTS; i++) {
        save_slot_t *s = &g->slot[i];
        free(s->file); free(s->preview); img_free(s->thumb);
        memset(s, 0, sizeof(*s));
    }
    free(g->preview);
    g->preview = NULL;
    g->has_save = false;
    g->save_time = 0;
    g->last_slot = 0;
    if (st) {
        for (size_t i = 0; i < st->total && i < SAVE_SLOTS; i++) {
            rg_emu_slot_t *r = &st->slots[i];
            save_slot_t *s = &g->slot[i];
            s->file = dup_or_null(r->file);
            s->preview = dup_or_null(r->preview);
            s->used = r->is_used;
            s->time = s->used ? (int64_t)r->mtime : 0;
        }
        rg_emu_slot_t *last = st->lastused ? st->lastused : st->latest;
        if (st->used > 0 && last) {
            g->has_save = true;
            g->last_slot = last->id < SAVE_SLOTS ? last->id : 0;
            g->save_time = (int64_t)last->mtime;
            g->preview = dup_or_null(last->preview);
        }
        free(st);
    }
    g->save_known = true;
    return g->has_save;
}

void lib_delete_save(game_t *g, int i)
{
    if (i < 0 || i >= SAVE_SLOTS) return;
    board_bus_lock();
    if (g->slot[i].file) remove(g->slot[i].file);
    if (g->slot[i].preview) remove(g->slot[i].preview);
    board_bus_unlock();
    img_free(g->thumb);
    g->thumb = NULL;
    lib_check_save(g);
}

game_info_t *lib_info(game_t *g)
{
    if (g->info_known) return g->info;
    g->info_known = true;
    char base[128], path[300];
    str_copy(base, sizeof(base), g->file);
    char *dot = strrchr(base, '.'); if (dot) *dot = 0;
    snprintf(path, sizeof(path), RG_STORAGE_ROOT "/shelf/info/%s/%s.json", SYS_DIR[g->sys], base);
    board_bus_lock();
    FILE *f = fopen(path, "rb");
    char *buf = NULL;
    if (f) {
        fseek(f, 0, SEEK_END);
        long n = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (n > 0 && n < 16384 && (buf = malloc(n + 1))) { size_t got = fread(buf, 1, n, f); buf[got] = 0; }
        fclose(f);
    }
    board_bus_unlock();
    if (!buf) return NULL;
    cJSON *root = cJSON_Parse(buf);
    free(buf);
    if (!root) return NULL;
    game_info_t *in = calloc(1, sizeof(game_info_t));
    if (in) {
        const struct { const char *key; char *dst; size_t len; } F[] = {
            {"year", in->year, sizeof(in->year)}, {"developer", in->developer, sizeof(in->developer)},
            {"publisher", in->publisher, sizeof(in->publisher)}, {"genre", in->genre, sizeof(in->genre)},
            {"source", in->source, sizeof(in->source)},
        };
        for (size_t i = 0; i < sizeof(F) / sizeof(F[0]); i++) {
            cJSON *v = cJSON_GetObjectItem(root, F[i].key);
            if (cJSON_IsString(v) && v->valuestring) str_copy(F[i].dst, F[i].len, v->valuestring);
        }
        cJSON *d = cJSON_GetObjectItem(root, "description");
        if (cJSON_IsString(d) && d->valuestring && d->valuestring[0]) in->desc = strdup(d->valuestring);
    }
    cJSON_Delete(root);
    g->info = in;
    return in;
}

void lib_scan(void)
{
    for (int s = 0; s < SYS_COUNT; s++) {
        shelf_t *sh = &SHELVES[s];
        free(sh->games);
        memset(sh, 0, sizeof(*sh));
        char dir[160];
        snprintf(dir, sizeof(dir), "%s/%s", RG_BASE_PATH_ROMS, SYS_DIR[s]);
        board_bus_lock();
        DIR *d = opendir(dir);
        if (!d) { board_bus_unlock(); RG_LOGW("no folder %s", dir); continue; }
        int cap = 32;
        sh->games = calloc(cap, sizeof(game_t));
        struct dirent *e;
        while ((e = readdir(d))) {
            if (e->d_name[0] == '.' || !wanted(s, e->d_name)) continue;
            if (strlen(e->d_name) >= sizeof(((game_t *)0)->file)) continue;
            if (sh->n == cap) { cap *= 2; sh->games = realloc(sh->games, cap * sizeof(game_t)); }
            game_t *g = &sh->games[sh->n++];
            memset(g, 0, sizeof(*g));
            str_copy(g->file, sizeof(g->file), e->d_name);
            make_title(g->file, g->title, sizeof(g->title));
            g->sys = s;
        }
        closedir(d);
        board_bus_unlock();
        RG_LOGI("%s: %d games", SYS_NAME[s], sh->n);
    }
}

void lib_load_meta(void)
{
    board_bus_lock();
    FILE *f = fopen(META_PATH, "rb");
    if (!f) { board_bus_unlock(); return; }
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *buf = malloc(n + 1);
    if (!buf) { fclose(f); board_bus_unlock(); return; }
    size_t got = fread(buf, 1, n, f);
    buf[got] = 0;
    fclose(f);
    board_bus_unlock();
    cJSON *root = cJSON_Parse(buf);
    free(buf);
    if (!root) return;
    char key[160];
    for (int s = 0; s < SYS_COUNT; s++)
        for (int i = 0; i < SHELVES[s].n; i++) {
            game_t *g = &SHELVES[s].games[i];
            lib_key(g, key, sizeof(key));
            cJSON *o = cJSON_GetObjectItem(root, key);
            if (!o) continue;
            cJSON *t = cJSON_GetObjectItem(o, "t"), *q = cJSON_GetObjectItem(o, "q"), *fv = cJSON_GetObjectItem(o, "f");
            if (cJSON_IsNumber(t)) g->play_sec = (uint32_t)t->valuedouble;
            if (cJSON_IsNumber(q)) g->seq = (uint32_t)q->valuedouble;
            if (cJSON_IsNumber(fv)) g->fav = fv->valuedouble != 0;
        }
    cJSON_Delete(root);
}

void lib_save_meta(void)
{
    cJSON *root = cJSON_CreateObject();
    char key[160];
    for (int s = 0; s < SYS_COUNT; s++)
        for (int i = 0; i < SHELVES[s].n; i++) {
            game_t *g = &SHELVES[s].games[i];
            if (!g->play_sec && !g->seq && !g->fav) continue;
            cJSON *o = cJSON_CreateObject();
            cJSON_AddNumberToObject(o, "t", g->play_sec);
            cJSON_AddNumberToObject(o, "q", g->seq);
            cJSON_AddNumberToObject(o, "f", g->fav ? 1 : 0);
            lib_key(g, key, sizeof(key));
            cJSON_AddItemToObject(root, key, o);
        }
    char *txt = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (!txt) return;
    board_bus_lock();
    mkdir(RG_STORAGE_ROOT "/shelf", 0777);
    FILE *f = fopen(META_PATH ".tmp", "wb");
    if (f) {
        fputs(txt, f);
        fclose(f);
        remove(META_PATH);
        rename(META_PATH ".tmp", META_PATH);
    }
    board_bus_unlock();
    free(txt);
}

static int sort_mode;
static int cmp_title(const game_t *a, const game_t *b) { return strcasecmp(a->title, b->title); }
static int cmp(const void *pa, const void *pb)
{
    const game_t *a = pa, *b = pb;
    int r = 0;
    if (sort_mode == SORT_RECENT) r = (b->seq > a->seq) - (b->seq < a->seq);
    else if (sort_mode == SORT_MOST) r = (b->play_sec > a->play_sec) - (b->play_sec < a->play_sec);
    else if (sort_mode == SORT_FAV) r = (int)b->fav - (int)a->fav;
    return r ? r : cmp_title(a, b);
}

void lib_sort(shelf_t *sh, int mode)
{
    if (!sh->n) { sh->sort = mode; return; }
    char keep[128];
    str_copy(keep, sizeof(keep), sh->games[sh->sel].file);
    sort_mode = mode;
    sh->sort = mode;
    qsort(sh->games, sh->n, sizeof(game_t), cmp);
    for (int i = 0; i < sh->n; i++) if (!strcmp(sh->games[i].file, keep)) { sh->sel = i; break; }
}

game_t *lib_find(const char *key, int *sys_out, int *index_out)
{
    char k[160];
    for (int s = 0; s < SYS_COUNT; s++)
        for (int i = 0; i < SHELVES[s].n; i++) {
            lib_key(&SHELVES[s].games[i], k, sizeof(k));
            if (!strcmp(k, key)) { if (sys_out) *sys_out = s; if (index_out) *index_out = i; return &SHELVES[s].games[i]; }
        }
    return NULL;
}

uint32_t lib_max_seq(void)
{
    uint32_t m = 0;
    for (int s = 0; s < SYS_COUNT; s++) for (int i = 0; i < SHELVES[s].n; i++) if (SHELVES[s].games[i].seq > m) m = SHELVES[s].games[i].seq;
    return m;
}
