// Pictures made at startup (planets, nebulas, stand-in labels) and pictures loaded from disk (real labels).
#pragma once
#include "gfx.h"
GLuint art_planet(const float cols[3][3], uint32_t seed);
GLuint art_nebula(const float cols[2][3], uint32_t seed);
GLuint art_label(int sys, const char *title, const char *png_path);   // uses the PNG if it exists
uint8_t *png_load(const char *path, int *w, int *h);                     // RGBA, or NULL
bool png_save(const char *path, const uint8_t *rgba, int w, int h);
