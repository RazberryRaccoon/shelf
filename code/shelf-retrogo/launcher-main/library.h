// The game library: ROMs on the SD card plus what Shelf remembers about them.
#pragma once
#include <stdint.h>
#include <stdbool.h>
#include "gfx.h"

typedef enum { SYS_NES, SYS_GB, SYS_GBC, SYS_COUNT } sys_t;

extern const char *SYS_DIR[SYS_COUNT];      // "nes", "gb", "gbc"
extern const char *SYS_NAME[SYS_COUNT];     // "NES", "Game Boy", ...
extern const char *SYS_LONG[SYS_COUNT];     // "NES cartridge", ...

typedef struct {
    char year[8], developer[64], publisher[64], genre[48];
    char source[32];         // where the description came from
    char *desc;              // a short description, may be NULL
} game_info_t;

#define SAVE_SLOTS 4
typedef struct {
    bool used;
    int64_t time;            // when it was saved (0 if unknown)
    char *file, *preview;    // the save state and its screenshot
    image_t *thumb;
} save_slot_t;

typedef struct {
    char file[128];          // file name in /sd/roms/<sys>/
    char title[96];          // shown on screen
    sys_t sys;
    uint32_t play_sec;       // total play time
    uint32_t seq;            // higher = played more recently
    bool fav, has_save, save_known;
    int64_t save_time;       // when the save was made (0 if unknown)
    char *preview;           // screenshot retro-go saved with it, if any
    game_info_t *info;       // from tools/fetch_info.py; NULL if there's none
    bool info_known;
    image_t *thumb;
    save_slot_t slot[SAVE_SLOTS];
    int last_slot;           // the slot played most recently
    // animation state
    float pos, posV, lift, liftV;
    // art cache
    image_t *cart, *box;
    bool art_dmg;
    uint32_t used;
} game_t;

typedef struct {
    game_t *games;
    int n, sel, sort;
    float scroll, scrollV;
} shelf_t;

extern shelf_t SHELVES[SYS_COUNT];

enum { SORT_AZ, SORT_RECENT, SORT_MOST, SORT_FAV, SORT_COUNT };
extern const char *SORT_LABEL[SORT_COUNT];

void lib_scan(void);
void lib_load_meta(void);
void lib_save_meta(void);
void lib_sort(shelf_t *sh, int mode);        // keeps the selected game selected
void lib_rom_path(const game_t *g, char *out, int len);
bool lib_check_save(game_t *g);              // asks retro-go about all save slots
void lib_delete_save(game_t *g, int slot);
game_info_t *lib_info(game_t *g);            // reads /shelf/info/<sys>/<rom>.json once
game_t *lib_find(const char *key, int *sys_out, int *index_out);
void lib_key(const game_t *g, char *out, int len);    // "nes/Game.nes"
uint32_t lib_max_seq(void);
uint32_t shelf_hash(const char *s);
