// The renderer: OpenGL ES 2.0, the same API the Raspberry Pi 3 has.
#pragma once
#include "shelf.h"
#include <GLES2/gl2.h>

typedef struct { float m[16]; } mat4;
mat4 m4_mul(mat4 a, mat4 b);
mat4 m4_persp(float fovy, float aspect, float n, float f);
mat4 m4_trs(float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz);
void m4_apply(const mat4 *m, const float p[3], float out[3]);

typedef struct { GLuint pos, nor, uv, idx; int count; } mesh_t;
mesh_t mesh_make(const float *pos, const float *nor, const float *uv, int nverts, const uint16_t *idx, int nidx);

// textures: RGBA (flip = true for textures used on 3D models), or alpha only (for text)
GLuint tex_rgba(int w, int h, const uint8_t *rgba, bool flip);
GLuint tex_alpha(int w, int h, const uint8_t *a);

// camera: world units at z = 0 map to PX pixels on the 1920x1080 canvas
#define CAMZ 10.0f
extern float PX;
void px_to_world(float px, float py, float *x, float *y);
void cam_perspective(float camy, float camz, float looky);
void cam_flat(void);
extern mat4 VP; extern float EYE[3]; extern float RIM[3];
extern float GTIME;     // seconds, for the gentle animations in shaders

enum { MODE_LIT, MODE_MASK, MODE_GLOW };
typedef struct { const float *col; float alpha, spec, bright; const float *emit; GLuint tex; int mode; float grow; } draw_opts;
void gfx_init(int screen_w, int screen_h);
void gfx_begin_frame(void);
void meshes_begin(void);
void meshes_end(void);
void draw_mesh(const mesh_t *m, mat4 model, const draw_opts *o);
void draw_backdrop(GLuint neb_a, GLuint neb_b, float mix, float bright, float pan);
void draw_planet(GLuint surface, const float atm[3], float cx, float cy, float r, float rot, float alpha);
void clip_rect(float x0, float y0, float x1, float y1);
void unclip(void);

// 2D, in canvas pixels (origin top left)
void ui_begin(void);
void ui_rect(float x, float y, float w, float h, float radius, const float rgba[4]);
void ui_image(GLuint tex, float x, float y, float w, float h, const float rgba[4], bool alpha_tex);
extern mesh_t PLANE;
