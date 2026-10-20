// Software drawing into RGB565 images (the screen is one of them).
#pragma once
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

// Copies a string, cutting it short if needed; always leaves it terminated.
static inline void str_copy(char *dst, size_t size, const char *src)
{
    if (!size) return;
    size_t n = strlen(src);
    if (n >= size) n = size - 1;
    memcpy(dst, src, n);
    dst[n] = 0;
}

#define KEY 0xF81F                      // transparent pixel (pure magenta)

typedef struct { int w, h; uint16_t *px; } image_t;

enum { AL_LEFT, AL_CENTER, AL_RIGHT };

static inline uint16_t RGB(uint32_t hex)
{
    return (uint16_t)(((hex >> 8) & 0xF800) | ((hex >> 5) & 0x07E0) | ((hex >> 3) & 0x001F));
}
uint32_t mixh(uint32_t a, uint32_t b, float t);        // blend two 0xRRGGBB colors
// Move color a toward color b by t/256, blending all three channels at once.
static inline uint16_t blend565(uint16_t a, uint16_t b, int t256)
{
    uint32_t x = (a | ((uint32_t)a << 16)) & 0x07E0F81Fu;
    uint32_t y = (b | ((uint32_t)b << 16)) & 0x07E0F81Fu;
    uint32_t t = (uint32_t)(t256 + 4) >> 3;   // 0..32
    uint32_t r = ((((y - x) * t) >> 5) + x) & 0x07E0F81Fu;
    return (uint16_t)(r | (r >> 16));
}
void unpack565(uint16_t c, int *r, int *g, int *b);    // to 0..255

extern const uint8_t BAYER[16];
static inline bool dither(int x, int y, float v)       // ordered dither test
{
    return (BAYER[(y & 3) * 4 + (x & 3)] + 0.5f) / 16.0f < v;
}

image_t *img_new(int w, int h);          // in PSRAM when available
void img_free(image_t *im);
void img_clear(image_t *im, uint16_t c);

void gfx_init(uint16_t *fb);
void gfx_set_screen(uint16_t *fb);       // switch to another frame buffer
image_t *gfx_screen(void);
void gfx_push_target(image_t *im);       // draw into an image...
void gfx_pop_target(void);               // ...then back to whatever was before
void gfx_clip(int x0, int y0, int x1, int y1);
void gfx_noclip(void);

void gfx_fill(int x, int y, int w, int h, uint16_t c);
void gfx_fill_a(int x, int y, int w, int h, uint16_t c, int a256);
void gfx_px(int x, int y, uint16_t c);
void gfx_px_a(int x, int y, uint16_t c, int a256);
void gfx_blit(const image_t *im, int x, int y);
void gfx_blit_opaque(const image_t *im, int x, int y);   // no transparency: a fast row copy
void gfx_blit_ex(const image_t *im, int x, int y, int dw, int dh, uint16_t tint, int tint_a);
void gfx_blit_part(const image_t *im, int sx, int sy, int w, int h, int dx, int dy);
void gfx_disc(int cx, int cy, int r, uint16_t c);
void gfx_line(int x0, int y0, int x1, int y1, uint16_t c);
void gfx_iris(int cx, int cy, int r);    // black everywhere outside a circle

int gfx_text_width(const char *s, int scale);
int gfx_text(const char *s, int x, int y, uint16_t c, int scale, int align);
int gfx_text_a(const char *s, int x, int y, uint16_t c, int scale, int align, int a256);
int gfx_text_sh(const char *s, int x, int y, uint16_t c, uint16_t shadow, int scale, int align, int a256);
int gfx_wrap(const char *s, int max_w, int scale, char lines[][64], int max_lines);
void gfx_ellipsize(const char *s, int max_w, int scale, char *out, int out_len);
