// The six original cartridge shells.
#pragma once
#include "gfx.h"

typedef struct { mesh_t mesh; mat4 m; float col[3]; float spec, glass; bool label; } part_t;
typedef struct { float w, h, d; float label[4]; part_t parts[24]; int nparts; mesh_t ring; bool ready; } cart_t;
cart_t *cart_model(int sys);
// draw a cartridge at a pose; label is its label texture (0 for none)
typedef struct { float x, y, z, rx, ry, s, alpha, bright, glow; } pose_t;
void draw_cart(int sys, GLuint label, const pose_t *p);
void draw_cart_glow(int sys, const pose_t *p, const float c1[3], const float c2[3]);
float cart_scale(int sys, float px);   // the scale that makes a cartridge's longest side this many canvas pixels
