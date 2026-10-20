// The home screen backdrops: quiet space, one mood per console.
#pragma once
#include <stdbool.h>
#include "gfx.h"
#include "library.h"

#define STARS 72
typedef struct {
    image_t *bg, *planet;               // planet: rows 150-239 only, KEY for empty pixels
#define PLANET_Y 150
    struct { float x, y, z, tw; } star[STARS];
    bool ready;
} backdrop_t;

typedef struct {
    uint32_t bg[3], glow, accent, planet[3], rim, dust, nebula[2];
    bool craters, has_dust, has_nebula;
} mood_t;

extern const mood_t MOODS[SYS_COUNT];
extern backdrop_t BACKDROPS[SYS_COUNT];
#define CY 106                           // vertical center of the carousel

void backdrops_build_async(void);       // builds on the second core
void backdrop_rebuild(sys_t s);
bool backdrops_ready(void);
