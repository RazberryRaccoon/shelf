// Cartridges, labels and boxes: loaded from the SD card or generated.
#pragma once
#include "library.h"

// Largest label and box per system. Real art is converted to fit inside these
// by tools/convert_art.py, keeping its own proportions.
#define NES_LABEL_W 106
#define NES_LABEL_H 88
#define GB_LABEL_W  90
#define GB_LABEL_H  78
#define NES_BOX_W   112
#define NES_BOX_H   160
#define GB_BOX_W    128
#define GB_BOX_H    128

image_t *art_load(const char *path);                // .img files written by convert_art.py
image_t *art_cart(game_t *g, bool may_build);       // cached; NULL if not built yet
image_t *art_box(game_t *g);                        // cached
image_t *art_thumb(game_t *g);                      // the save's screenshot, cached
image_t *art_slot_thumb(game_t *g, int slot);       // one save slot's screenshot, cached
void art_forget_slot_thumbs(game_t *g);
void art_forget(game_t *g);
void art_forget_system(sys_t s);
void art_trim(void);                                // keeps memory use bounded
void img_dmgify(image_t *im, float kf);
uint16_t dmg_color(int k);
void draw_heart(int x, int y, uint16_t c);
void draw_sticker(int x, int y, uint16_t c);
int cart_w(sys_t s);
int cart_h(sys_t s);

typedef struct { int lx, ly, lw, lh; } label_box_t;
label_box_t cart_label_box(sys_t s);
