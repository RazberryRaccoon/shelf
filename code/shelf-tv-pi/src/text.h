// Text: FreeType renders strings; they become alpha textures cached by their content.
#pragma once
#include "gfx.h"
enum { FONT_DISPLAY, FONT_BODY };
bool text_init(const char *dir);   // looks for Unbounded and Manrope in dir, falling back to DejaVu Sans
// render into a new alpha bitmap (caller frees); returns width and height, baseline at ascent
uint8_t *text_raster(const char *s, int font, int weight, float size, int *w, int *h);
// draw on the canvas; align 0 left, 1 center, 2 right; returns the width drawn
float text_draw(const char *s, int font, int weight, float size, float x, float y, const float rgba[4], int align);
float text_width(const char *s, int font, int weight, float size);
void text_frame_end(void);   // forgets textures not used for a while
