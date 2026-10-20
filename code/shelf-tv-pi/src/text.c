// Text with FreeType. Variable fonts (Unbounded, Manrope) get their weight set per face.
#include "text.h"
#include <ft2build.h>
#include FT_FREETYPE_H
#include FT_MULTIPLE_MASTERS_H
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

static FT_Library ft;
typedef struct { int font, weight; FT_Face face; } face_t;
static face_t faces[8]; static int nfaces;
static char paths[2][512];

static FT_Face get_face(int font, int weight) {
    for (int i = 0; i < nfaces; i++) if (faces[i].font == font && faces[i].weight == weight) return faces[i].face;
    if (nfaces == 8) return faces[0].face;
    FT_Face f; if (FT_New_Face(ft, paths[font], 0, &f)) return NULL;
    if (FT_HAS_MULTIPLE_MASTERS(f)) {   // a variable font: pick the weight
        FT_MM_Var *mm; if (!FT_Get_MM_Var(f, &mm)) {
            FT_Fixed coords[16];
            for (unsigned a = 0; a < mm->num_axis && a < 16; a++) coords[a] = mm->axis[a].tag == FT_MAKE_TAG('w', 'g', 'h', 't') ? weight * 65536 : mm->axis[a].def;
            FT_Set_Var_Design_Coordinates(f, mm->num_axis < 16 ? mm->num_axis : 16, coords);
            FT_Done_MM_Var(ft, mm);
        }
    }
    faces[nfaces++] = (face_t){ font, weight, f };
    return f;
}
bool text_init(const char *dir) {
    if (FT_Init_FreeType(&ft)) return false;
    const char *want[2][4] = { { "Unbounded.ttf", "Unbounded[wght].ttf", NULL }, { "Manrope.ttf", "Manrope[wght].ttf", NULL } };
    const char *fallback[] = { "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/TTF/DejaVuSans.ttf", NULL };
    for (int f = 0; f < 2; f++) {
        paths[f][0] = 0;
        for (int i = 0; want[f][i] && !paths[f][0]; i++) { snprintf(paths[f], 512, "%s/%s", dir, want[f][i]); FILE *t = fopen(paths[f], "rb"); if (t) fclose(t); else paths[f][0] = 0; }
        for (int i = 0; fallback[i] && !paths[f][0]; i++) { FILE *t = fopen(fallback[i], "rb"); if (t) { fclose(t); snprintf(paths[f], 512, "%s", fallback[i]); } }
        if (!paths[f][0]) { fprintf(stderr, "Shelf: no font found\n"); return false; }
    }
    return true;
}
// decode one UTF-8 character
static unsigned utf8(const char **s) {
    const unsigned char *p = (const unsigned char *)*s; unsigned c = *p++;
    if (c >= 0xF0) { c = (c & 7) << 18 | (p[0] & 63) << 12 | (p[1] & 63) << 6 | (p[2] & 63); p += 3; }
    else if (c >= 0xE0) { c = (c & 15) << 12 | (p[0] & 63) << 6 | (p[1] & 63); p += 2; }
    else if (c >= 0xC0) { c = (c & 31) << 6 | (p[0] & 63); p += 1; }
    *s = (const char *)p; return c;
}
float text_width(const char *s, int font, int weight, float size) {
    FT_Face f = get_face(font, weight); if (!f) return 0;
    FT_Set_Pixel_Sizes(f, 0, (FT_UInt)lroundf(size));
    float w = 0; FT_UInt prev = 0;
    while (*s) {
        FT_UInt g = FT_Get_Char_Index(f, utf8(&s));
        if (prev && FT_HAS_KERNING(f)) { FT_Vector k; FT_Get_Kerning(f, prev, g, FT_KERNING_DEFAULT, &k); w += k.x / 64.f; }
        if (!FT_Load_Glyph(f, g, FT_LOAD_DEFAULT)) w += f->glyph->advance.x / 64.f;
        prev = g;
    }
    return w;
}
uint8_t *text_raster(const char *s, int font, int weight, float size, int *ow, int *oh) {
    FT_Face f = get_face(font, weight); if (!f) return NULL;
    FT_Set_Pixel_Sizes(f, 0, (FT_UInt)lroundf(size));
    int asc = (int)ceilf(f->size->metrics.ascender / 64.f), desc = (int)ceilf(-f->size->metrics.descender / 64.f);
    int w = (int)ceilf(text_width(s, font, weight, size)) + 4, h = asc + desc + 2;
    uint8_t *a = calloc((size_t)w * h, 1);
    float pen = 1; FT_UInt prev = 0;
    while (*s) {
        FT_UInt g = FT_Get_Char_Index(f, utf8(&s));
        if (prev && FT_HAS_KERNING(f)) { FT_Vector k; FT_Get_Kerning(f, prev, g, FT_KERNING_DEFAULT, &k); pen += k.x / 64.f; }
        if (FT_Load_Glyph(f, g, FT_LOAD_RENDER)) continue;
        FT_Bitmap *b = &f->glyph->bitmap; int x0 = (int)lroundf(pen) + f->glyph->bitmap_left, y0 = asc - f->glyph->bitmap_top + 1;
        for (unsigned y = 0; y < b->rows; y++) for (unsigned x = 0; x < b->width; x++) {
            int X = x0 + (int)x, Y = y0 + (int)y; if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
            int v = a[Y * w + X] + b->buffer[y * b->pitch + x]; a[Y * w + X] = v > 255 ? 255 : (uint8_t)v;
        }
        pen += f->glyph->advance.x / 64.f; prev = g;
    }
    *ow = w; *oh = h; return a;
}

// a small cache of text textures, so unchanged text isn't rendered again every frame
typedef struct { char s[160]; int font, weight; float size; GLuint tex; int w, h; int age; } cached_t;
static cached_t cache[96];
float text_draw(const char *s, int font, int weight, float size, float x, float y, const float c[4], int align) {
    if (!s || !*s) return 0;
    cached_t *e = NULL, *oldest = &cache[0];
    for (int i = 0; i < 96; i++) {
        if (cache[i].tex && cache[i].font == font && cache[i].weight == weight && cache[i].size == size && !strcmp(cache[i].s, s)) { e = &cache[i]; break; }
        if (!cache[i].tex || cache[i].age > oldest->age) oldest = &cache[i];
    }
    if (!e) {
        e = oldest; if (e->tex) glDeleteTextures(1, &e->tex);
        uint8_t *a = text_raster(s, font, weight, size, &e->w, &e->h);
        e->tex = tex_alpha(e->w, e->h, a); free(a);
        snprintf(e->s, sizeof e->s, "%s", s); e->font = font; e->weight = weight; e->size = size;
    }
    e->age = 0;
    float px = align == 1 ? x - e->w / 2.f : align == 2 ? x - e->w : x;
    ui_image(e->tex, roundf(px), roundf(y), (float)e->w, (float)e->h, c, true);
    return (float)e->w;
}
void text_frame_end(void) { for (int i = 0; i < 96; i++) if (cache[i].tex) cache[i].age++; }
