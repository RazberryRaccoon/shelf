#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>
#include "esp_heap_caps.h"
#include "gfx.h"
#include "font.h"

const uint8_t BAYER[16] = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

static image_t screen;
static image_t *T = &screen;
static int cx0, cy0, cx1, cy1;
#define STACK 6
static struct { image_t *t; int c[4]; } stack[STACK];
static int depth;

static inline int imin(int a, int b) { return a < b ? a : b; }
static inline int imax(int a, int b) { return a > b ? a : b; }

uint32_t mixh(uint32_t a, uint32_t b, float t)
{
    int r = ((a >> 16) & 255) + (int)((((int)(b >> 16) & 255) - (int)((a >> 16) & 255)) * t);
    int g = ((a >> 8) & 255) + (int)((((int)(b >> 8) & 255) - (int)((a >> 8) & 255)) * t);
    int bl = (a & 255) + (int)((((int)b & 255) - (int)(a & 255)) * t);
    r = imax(0, imin(255, r)); g = imax(0, imin(255, g)); bl = imax(0, imin(255, bl));
    return ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)bl;
}

void unpack565(uint16_t c, int *r, int *g, int *b)
{
    *r = ((c >> 11) & 31) * 255 / 31;
    *g = ((c >> 5) & 63) * 255 / 63;
    *b = (c & 31) * 255 / 31;
}

image_t *img_new(int w, int h)
{
    image_t *im = malloc(sizeof(image_t));
    if (!im) return NULL;
    im->w = w; im->h = h;
    im->px = heap_caps_malloc(w * h * 2, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!im->px) im->px = malloc(w * h * 2);
    if (!im->px) { free(im); return NULL; }
    return im;
}
void img_free(image_t *im) { if (im) { free(im->px); free(im); } }
void img_clear(image_t *im, uint16_t c) { for (int i = 0; i < im->w * im->h; i++) im->px[i] = c; }

void gfx_init(uint16_t *fb) { screen.w = 320; screen.h = 240; screen.px = fb; T = &screen; depth = 0; gfx_noclip(); }
void gfx_set_screen(uint16_t *fb) { gfx_init(fb); }
image_t *gfx_screen(void) { return &screen; }

void gfx_push_target(image_t *im)
{
    if (depth < STACK) { stack[depth].t = T; stack[depth].c[0] = cx0; stack[depth].c[1] = cy0; stack[depth].c[2] = cx1; stack[depth].c[3] = cy1; depth++; }
    T = im ? im : &screen;
    gfx_noclip();
}
void gfx_pop_target(void)
{
    if (depth == 0) { T = &screen; gfx_noclip(); return; }
    depth--;
    T = stack[depth].t; cx0 = stack[depth].c[0]; cy0 = stack[depth].c[1]; cx1 = stack[depth].c[2]; cy1 = stack[depth].c[3];
}
void gfx_clip(int x0, int y0, int x1, int y1) { cx0 = imax(0, x0); cy0 = imax(0, y0); cx1 = imin(T->w, x1); cy1 = imin(T->h, y1); }
void gfx_noclip(void) { cx0 = 0; cy0 = 0; cx1 = T->w; cy1 = T->h; }

void gfx_fill(int x, int y, int w, int h, uint16_t c)
{
    int x0 = imax(x, cx0), y0 = imax(y, cy0), x1 = imin(x + w, cx1), y1 = imin(y + h, cy1);
    for (int yy = y0; yy < y1; yy++) {
        uint16_t *p = T->px + yy * T->w + x0;
        int n = x1 - x0;
        if (n > 8) {
            if ((uintptr_t)p & 2) { *p++ = c; n--; }
            uint32_t cc = c | ((uint32_t)c << 16), *q = (uint32_t *)p;
            for (; n >= 2; n -= 2) *q++ = cc;
            p = (uint16_t *)q;
        }
        while (n-- > 0) *p++ = c;
    }
}
void gfx_fill_a(int x, int y, int w, int h, uint16_t c, int a)
{
    if (a <= 0) return;
    if (a >= 256) { gfx_fill(x, y, w, h, c); return; }
    int x0 = imax(x, cx0), y0 = imax(y, cy0), x1 = imin(x + w, cx1), y1 = imin(y + h, cy1);
    for (int yy = y0; yy < y1; yy++) {
        uint16_t *p = T->px + yy * T->w + x0;
        for (int i = x0; i < x1; i++, p++) *p = blend565(*p, c, a);
    }
}
void gfx_px(int x, int y, uint16_t c) { if (x >= cx0 && x < cx1 && y >= cy0 && y < cy1) T->px[y * T->w + x] = c; }
void gfx_px_a(int x, int y, uint16_t c, int a)
{
    if (x >= cx0 && x < cx1 && y >= cy0 && y < cy1) { uint16_t *p = &T->px[y * T->w + x]; *p = blend565(*p, c, a); }
}

void gfx_blit_ex(const image_t *im, int dx, int dy, int dw, int dh, uint16_t tint, int tint_a)
{
    if (!im || dw <= 0 || dh <= 0) return;
    int x0 = imax(dx, cx0), y0 = imax(dy, cy0), x1 = imin(dx + dw, cx1), y1 = imin(dy + dh, cy1);
    if (x0 >= x1 || y0 >= y1) return;
    if (dw == im->w && dh == im->h) {   // unscaled: no stepping math
        for (int y = y0; y < y1; y++) {
            const uint16_t *src = im->px + (y - dy) * im->w + (x0 - dx);
            uint16_t *dst = T->px + y * T->w + x0;
            if (tint_a) { for (int x = x0; x < x1; x++, src++, dst++) if (*src != KEY) *dst = blend565(*src, tint, tint_a); }
            else for (int x = x0; x < x1; x++, src++, dst++) if (*src != KEY) *dst = *src;
        }
        return;
    }
    int stepx = (im->w << 16) / dw, stepy = (im->h << 16) / dh;
    for (int y = y0; y < y1; y++) {
        int sy = ((y - dy) * stepy) >> 16;
        if (sy >= im->h) sy = im->h - 1;
        const uint16_t *src = im->px + sy * im->w;
        uint16_t *dst = T->px + y * T->w;
        int sx = (x0 - dx) * stepx;
        for (int x = x0; x < x1; x++, sx += stepx) {
            int ix = sx >> 16;
            if (ix >= im->w) ix = im->w - 1;
            uint16_t c = src[ix];
            if (c != KEY) dst[x] = tint_a ? blend565(c, tint, tint_a) : c;
        }
    }
}
void gfx_blit(const image_t *im, int x, int y) { if (im) gfx_blit_ex(im, x, y, im->w, im->h, 0, 0); }
void gfx_blit_opaque(const image_t *im, int x, int y)
{
    if (!im) return;
    int x0 = imax(x, cx0), y0 = imax(y, cy0), x1 = imin(x + im->w, cx1), y1 = imin(y + im->h, cy1);
    if (x0 >= x1) return;
    for (int yy = y0; yy < y1; yy++) memcpy(T->px + yy * T->w + x0, im->px + (yy - y) * im->w + (x0 - x), (x1 - x0) * 2);
}
void gfx_blit_part(const image_t *im, int sx, int sy, int w, int h, int dx, int dy)
{
    for (int y = 0; y < h; y++) {
        int ty = dy + y, fy = sy + y;
        if (ty < cy0 || ty >= cy1 || fy < 0 || fy >= im->h) continue;
        for (int x = 0; x < w; x++) {
            int tx = dx + x, fx = sx + x;
            if (tx < cx0 || tx >= cx1 || fx < 0 || fx >= im->w) continue;
            uint16_t c = im->px[fy * im->w + fx];
            if (c != KEY) T->px[ty * T->w + tx] = c;
        }
    }
}

void gfx_disc(int cx, int cy, int r, uint16_t c)
{
    for (int y = -r; y <= r; y++) {
        int hw = (int)floorf(sqrtf((float)(r * r - y * y)) + 0.35f);
        gfx_fill(cx - hw, cy + y, hw * 2 + 1, 1, c);
    }
}

void gfx_line(int x0, int y0, int x1, int y1, uint16_t c)
{
    int dx = abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -abs(y1 - y0), sy = y0 < y1 ? 1 : -1, e = dx + dy;
    for (int n = 0; n < 1000; n++) {
        gfx_px(x0, y0, c);
        if (x0 == x1 && y0 == y1) break;
        int e2 = 2 * e;
        if (e2 >= dy) { e += dy; x0 += sx; }
        if (e2 <= dx) { e += dx; y0 += sy; }
    }
}

void gfx_iris(int cx, int cy, int r)
{
    if (r <= 0) { gfx_fill(0, 0, T->w, T->h, 0); return; }
    for (int y = 0; y < T->h; y++) {
        int dy = y - cy;
        if (abs(dy) >= r) { gfx_fill(0, y, T->w, 1, 0); continue; }
        int hw = (int)sqrtf((float)(r * r - dy * dy));
        int l = cx - hw, rr = cx + hw + 1;
        if (l > 0) gfx_fill(0, y, l, 1, 0);
        if (rr < T->w) gfx_fill(rr, y, T->w - rr, 1, 0);
    }
}

/* ---------- text ---------- */
static uint32_t utf8_next(const char **ps)
{
    const unsigned char *s = (const unsigned char *)*ps;
    uint32_t c = *s;
    int n = 0;
    if (c < 0x80) n = 1;
    else if ((c & 0xE0) == 0xC0) { c &= 0x1F; n = 2; }
    else if ((c & 0xF0) == 0xE0) { c &= 0x0F; n = 3; }
    else if ((c & 0xF8) == 0xF0) { c &= 0x07; n = 4; }
    else { *ps += 1; return '?'; }
    for (int i = 1; i < n; i++) {
        if ((s[i] & 0xC0) != 0x80) { *ps += i; return '?'; }
        c = (c << 6) | (s[i] & 0x3F);
    }
    *ps += n;
    return c;
}
static const uint8_t *glyph_rows(uint32_t cp)
{
    if (cp == 0x2018 || cp == 0x2019) cp = '\'';
    if (cp == 0x201C || cp == 0x201D) cp = '"';
    if (cp == 0x2013 || cp == 0x2014) cp = '-';
    if (cp >= 32 && cp <= 126) return FONT_ASCII[cp - 32];
    for (int i = 0; i < FONT_SPECIAL_N; i++) if (FONT_SPECIAL[i].cp == cp) return FONT_SPECIAL[i].rows;
    return FONT_ASCII['?' - 32];
}
static void glyph_extent(uint32_t cp, const uint8_t *rows, int *mn, int *mx)
{
    if (cp == ' ') { *mn = 0; *mx = 1; return; }
    if (cp >= '0' && cp <= '9') { *mn = 0; *mx = 4; return; }
    int a = 5, b = -1;
    for (int r = 0; r < 8; r++) for (int c = 0; c < 5; c++) if (rows[r] & (1 << (4 - c))) { if (c < a) a = c; if (c > b) b = c; }
    if (b < 0) { a = 0; b = 1; }
    *mn = a; *mx = b;
}
int gfx_text_width(const char *s, int sc)
{
    int w = 0;
    while (*s) {
        uint32_t cp = utf8_next(&s);
        int mn, mx;
        glyph_extent(cp, glyph_rows(cp), &mn, &mx);
        w += mx - mn + 2;
    }
    return w > 0 ? (w - 1) * sc : 0;
}
int gfx_text_a(const char *s, int x, int y, uint16_t c, int sc, int align, int a)
{
    int w = gfx_text_width(s, sc);
    if (align == AL_CENTER) x -= w / 2; else if (align == AL_RIGHT) x -= w;
    while (*s) {
        uint32_t cp = utf8_next(&s);
        const uint8_t *rows = glyph_rows(cp);
        int mn, mx;
        glyph_extent(cp, rows, &mn, &mx);
        for (int r = 0; r < 8; r++)
            for (int k = mn; k <= mx; k++)
                if (rows[r] & (1 << (4 - k))) gfx_fill_a(x + (k - mn) * sc, y + r * sc, sc, sc, c, a);
        x += (mx - mn + 2) * sc;
    }
    return w;
}
int gfx_text(const char *s, int x, int y, uint16_t c, int sc, int align) { return gfx_text_a(s, x, y, c, sc, align, 256); }
int gfx_text_sh(const char *s, int x, int y, uint16_t c, uint16_t sh, int sc, int align, int a)
{
    gfx_text_a(s, x + sc, y + sc, sh, sc, align, a);
    return gfx_text_a(s, x, y, c, sc, align, a);
}
int gfx_wrap(const char *s, int max_w, int sc, char lines[][64], int max_lines)
{
    int n = 0;
    char cur[64] = "";
    char word[64];
    const char *p = s;
    while (*p && n < max_lines) {
        while (*p == ' ') p++;
        int wl = 0;
        while (*p && *p != ' ' && wl < 63) word[wl++] = *p++;
        word[wl] = 0;
        if (!wl) break;
        char test[130];
        snprintf(test, sizeof(test), "%s%s%s", cur, cur[0] ? " " : "", word);
        if (gfx_text_width(test, sc) <= max_w || !cur[0]) str_copy(cur, sizeof(cur), test);
        else { str_copy(lines[n++], 64, cur); str_copy(cur, sizeof(cur), word); }
    }
    if (cur[0] && n < max_lines) str_copy(lines[n++], 64, cur);
    return n;
}
void gfx_ellipsize(const char *s, int max_w, int sc, char *out, int len)
{
    str_copy(out, len, s);
    if (gfx_text_width(out, sc) <= max_w) return;
    int n = strlen(out);
    while (n > 1) {
        n--;
        while (n > 0 && (out[n] & 0xC0) == 0x80) n--;   // keep UTF-8 whole
        out[n] = 0;
        while (n > 0 && out[n - 1] == ' ') out[--n] = 0;
        char t[160];
        snprintf(t, sizeof(t), "%s…", out);
        if (gfx_text_width(t, sc) <= max_w) { str_copy(out, len, t); return; }
    }
}
