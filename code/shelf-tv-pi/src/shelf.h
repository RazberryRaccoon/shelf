// Shelf for TV: shared definitions.
#pragma once
#include <stdbool.h>
#include <stdint.h>

#define VW 1920                       // everything is laid out on a 1920x1080 canvas, scaled to the real screen
#define VH 1080
#define HEXC(h) { ((h) >> 16 & 255) / 255.f, ((h) >> 8 & 255) / 255.f, ((h) & 255) / 255.f }

enum { SYS_NES, SYS_SNES, SYS_GEN, SYS_GB, SYS_GBC, SYS_GBA, SYS_COUNT };
typedef struct {
    const char *dir, *name, *long_name, *core, *exts;
    float planet[3][3], neb[2][3], accent[3];
    int res_w, res_h; float aspect; bool hand;
} sysinfo_t;
extern const sysinfo_t SYSINFO[SYS_COUNT];

static inline float clampf(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
static inline float lerpf(float a, float b, float t) { return a + (b - a) * t; }
static inline float easef(float t) { t = clampf(t, 0, 1); return t * t * (3 - 2 * t); }
static inline float easeoutf(float t) { t = 1 - clampf(t, 0, 1); return 1 - t * t * t; }
uint32_t hash_str(const char *s);
