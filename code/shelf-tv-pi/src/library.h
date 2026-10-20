// Games, profiles, recently played, saves, and handing games to RetroArch.
#pragma once
#include "gfx.h"

typedef struct { int sys; char file[256], base[256], title[128], key[300]; GLuint label; bool label_tried; } game_t;
typedef struct { char key[300]; long long last, seconds; } recent_t;
#define MAX_RECENT 256
typedef struct { char name[64]; float colors[3][3]; recent_t recent[MAX_RECENT]; int nrecent; GLuint planet; } profile_t;

extern game_t *GAMES; extern int NGAMES;
extern profile_t PROFILES[8]; extern int NPROFILES;

void lib_init(void);
const char *lib_home(void);
game_t *lib_find(const char *key);
void lib_load_recent(profile_t *p);
void lib_touch(profile_t *p, const char *key, long long seconds);   // moves a game to the front and adds play time
recent_t *lib_recent(profile_t *p, const char *key);
bool lib_has_save(profile_t *p, const game_t *g);
// writes the launch request and prepares the save; returns NULL on success, or a message saying what's missing
const char *lib_launch(profile_t *p, const game_t *g, bool resume);
bool lib_read_return(char *key, size_t n, long long *seconds, bool *saved);
GLuint lib_label(game_t *g);
