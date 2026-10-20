// Shelf's settings, and handing a game over to retro-go's emulators.
#pragma once
#include <stdint.h>
#include <stdbool.h>
#include "library.h"

typedef struct {
    uint8_t sound, motion, gb_green, attract, shelf;
    uint8_t sort[SYS_COUNT];
    char sel[SYS_COUNT][128];      // selected file per shelf
} prefs_t;
extern prefs_t PREFS;

void prefs_load(void);
void prefs_save(void);

// Set when a game is launched; read back when the emulator returns to Shelf.
bool pending_take(char *key, int len, uint32_t *played_sec);

// Installs Shelf's look for retro-go's own menus (the in-game menu, settings).
void install_theme(void);

// Hands the game to retro-go's emulator and restarts into it. Never returns.
void launch_game(game_t *g, bool resume, int slot);
