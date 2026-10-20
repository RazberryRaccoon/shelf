#include <stdio.h>
#include <string.h>
#include <time.h>
#include <rg_system.h>
#include "shelf_system.h"
#include "board.h"

prefs_t PREFS;

// Shelf keeps its settings in the launcher's section of retro-go's settings file.
#define NS NS_APP

static int num(const char *key, int def) { return (int)rg_settings_get_number(NS, key, def); }

void prefs_load(void)
{
    memset(&PREFS, 0, sizeof(PREFS));
    PREFS.sound = num("ShelfSound", 1);
    PREFS.motion = num("ShelfMotion", 1);
    PREFS.gb_green = num("ShelfGbGreen", 1);
    PREFS.attract = num("ShelfAttract", 1);
    PREFS.shelf = num("ShelfShelf", 0);
    char k[24];
    for (int s = 0; s < SYS_COUNT; s++) {
        snprintf(k, sizeof(k), "ShelfSort%d", s);
        PREFS.sort[s] = num(k, 0);
        snprintf(k, sizeof(k), "ShelfSel%d", s);
        char *v = rg_settings_get_string(NS, k, "");
        str_copy(PREFS.sel[s], sizeof(PREFS.sel[s]), v ? v : "");
    }
}

void prefs_save(void)
{
    rg_settings_set_number(NS, "ShelfSound", PREFS.sound);
    rg_settings_set_number(NS, "ShelfMotion", PREFS.motion);
    rg_settings_set_number(NS, "ShelfGbGreen", PREFS.gb_green);
    rg_settings_set_number(NS, "ShelfAttract", PREFS.attract);
    rg_settings_set_number(NS, "ShelfShelf", PREFS.shelf);
    char k[24];
    for (int s = 0; s < SYS_COUNT; s++) {
        snprintf(k, sizeof(k), "ShelfSort%d", s);
        rg_settings_set_number(NS, k, PREFS.sort[s]);
        snprintf(k, sizeof(k), "ShelfSel%d", s);
        rg_settings_set_string(NS, k, PREFS.sel[s]);
    }
    board_bus_lock();
    rg_settings_commit();
    board_bus_unlock();
}

bool pending_take(char *key, int len, uint32_t *played)
{
    char *v = rg_settings_get_string(NS, "ShelfPending", "");
    if (!v || !v[0]) return false;
    str_copy(key, len, v);
    double t0 = rg_settings_get_number(NS, "ShelfPendingTime", 0);
    double d = (double)time(NULL) - t0;
    *played = (t0 > 0 && d > 0 && d < 48 * 3600) ? (uint32_t)d : 0;
    rg_settings_set_string(NS, "ShelfPending", "");
    return true;
}

/* ---------- Shelf's look for retro-go's menus ---------- */
bool rg_gui_set_theme(const char *theme_name);   // declared in rg_gui.h too; repeated here to be safe

static const char *THEME_JSON =
    "{\n"
    "  \"description\": \"Shelf\",\n"
    "  \"author\": \"Shelf launcher\",\n"
    "  \"dialog\": {\n"
    "    \"background\": \"0x141a34\",\n"
    "    \"foreground\": \"0xe8ecf6\",\n"
    "    \"border\": \"0x3a4472\",\n"
    "    \"header\": \"0xffd25a\",\n"
    "    \"scrollbar\": \"0xffd25a\",\n"
    "    \"shadow\": \"none\",\n"
    "    \"item_standard\": \"0xe8ecf6\",\n"
    "    \"item_disabled\": \"0x5a6488\",\n"
    "    \"item_message\": \"0xb4bdd8\"\n"
    "  }\n"
    "}\n";

void install_theme(void)
{
    char dir[160], path[200];
    snprintf(dir, sizeof(dir), "%s/shelf", RG_BASE_PATH_THEMES);
    snprintf(path, sizeof(path), "%s/theme.json", dir);
    board_bus_lock();
    rg_storage_mkdir(dir);
    FILE *f = fopen(path, "rb");
    bool exists = f != NULL;
    if (f) fclose(f);
    if (!exists && (f = fopen(path, "wb"))) { fputs(THEME_JSON, f); fclose(f); }
    board_bus_unlock();
    // Use it the first time Shelf runs; after that, respect whatever theme is chosen in settings.
    if (!num("ShelfThemeSet", 0)) {
        board_bus_lock();
        rg_gui_set_theme("shelf");
        board_bus_unlock();
        rg_settings_set_number(NS, "ShelfThemeSet", 1);
    }
}

/* ---------- launching ---------- */
static const char *EMU_NAME[SYS_COUNT] = {"nes", "gb", "gbc"};

void launch_game(game_t *g, bool resume, int slot)
{
    char path[300], key[160];
    lib_rom_path(g, path, sizeof(path));
    lib_key(g, key, sizeof(key));
    rg_settings_set_string(NS, "ShelfPending", key);
    rg_settings_set_number(NS, "ShelfPendingTime", (double)time(NULL));
    prefs_save();

    // The slot travels with the game either way: resuming loads from it, and a fresh
    // start saves into it when you quit.
    uint32_t flags = ((uint32_t)slot << 4) & RG_BOOT_SLOT_MASK;
    if (resume) flags |= RG_BOOT_RESUME;
    board_sync();   // let the last frame finish before retro-go takes over the screen
    board_bus_lock();
    rg_system_switch_app("retro-core", EMU_NAME[g->sys], path, flags);
}
