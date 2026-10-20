// Testing without a screen: Mesa renders offscreen, a script presses buttons on a fixed clock,
// and screenshots are saved as PNG. Not used on the Pi.
//   SHELF_SCRIPT="wait 2; press A; wait 1; shot home.png; press RIGHT; ..." ./shelf-test
#include "../src/platform.h"
#include "../src/art.h"
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TW 1920
#define TH 1080
static EGLDisplay dpy; static double now; static char script[8192]; static char *cursor; static double wait_until; static int press_frames[BTN_COUNT];
static int hold_btn = -1; static double hold_until;
static const char *NAMES[BTN_COUNT] = { "UP", "DOWN", "LEFT", "RIGHT", "A", "B", "X", "Y", "LB", "RB", "START", "SELECT", "HOME" };

bool plat_init(int *w, int *h) {
    PFNEGLGETPLATFORMDISPLAYEXTPROC get = (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress("eglGetPlatformDisplayEXT");
    dpy = get ? get(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL) : EGL_NO_DISPLAY;
    if (dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, NULL, NULL)) { fprintf(stderr, "EGL: no display\n"); return false; }
    EGLint cfg_attr[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE }, n; EGLConfig cfg;   // offscreen, so no window surfaces
    if (!eglChooseConfig(dpy, cfg_attr, &cfg, 1, &n) || !n) { fprintf(stderr, "EGL: no config\n"); return false; }
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    if (ctx == EGL_NO_CONTEXT || !eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) { fprintf(stderr, "EGL: no context\n"); return false; }
    // an offscreen framebuffer to draw into
    GLuint fb, color, depth; glGenFramebuffers(1, &fb); glBindFramebuffer(GL_FRAMEBUFFER, fb);
    glGenTextures(1, &color); glBindTexture(GL_TEXTURE_2D, color); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, TW, TH, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
    glGenRenderbuffers(1, &depth); glBindRenderbuffer(GL_RENDERBUFFER, depth); glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, TW, TH);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { fprintf(stderr, "framebuffer incomplete\n"); return false; }
    fprintf(stderr, "renderer: %s\n", (const char *)glGetString(GL_RENDERER));
    const char *s = getenv("SHELF_SCRIPT"); snprintf(script, sizeof script, "%s", s ? s : "wait 1; shot shot.png"); cursor = script;
    *w = TW; *h = TH; return true;
}
static void shot(const char *name) {
    uint8_t *px = malloc(TW * TH * 4), *flip = malloc(TW * TH * 4);
    glReadPixels(0, 0, TW, TH, GL_RGBA, GL_UNSIGNED_BYTE, px);
    for (int y = 0; y < TH; y++) memcpy(flip + y * TW * 4, px + (TH - 1 - y) * TW * 4, TW * 4);
    for (int i = 0; i < TW * TH; i++) flip[i * 4 + 3] = 255;
    png_save(name, flip, TW, TH); free(px); free(flip); fprintf(stderr, "saved %s at %.2fs\n", name, now);
}
// the next script step, run when the previous wait has passed
static bool step(void) {
    while (now >= wait_until && hold_btn < 0) {
        while (*cursor == ' ' || *cursor == ';') cursor++;
        if (!*cursor) return false;
        char cmd[32], arg[128] = ""; int used = 0;
        if (sscanf(cursor, "%31s %127[^;]%n", cmd, arg, &used) < 1) return false;
        if (!used) used = (int)strlen(cursor);
        cursor += used;
        if (!strcmp(cmd, "wait")) wait_until = now + atof(arg);
        else if (!strcmp(cmd, "shot")) shot(arg);
        else if (!strcmp(cmd, "press") || !strcmp(cmd, "hold")) {
            for (int b = 0; b < BTN_COUNT; b++) if (!strcmp(arg, NAMES[b])) {
                if (cmd[0] == 'p') { press_frames[b] = 2; wait_until = now + .15; } else { hold_btn = b; hold_until = now + 2.5; }
            }
        }
    }
    return true;
}
bool plat_poll(bool held[BTN_COUNT]) {
    if (!step() && now >= wait_until) return false;
    for (int b = 0; b < BTN_COUNT; b++) { held[b] = press_frames[b] > 0; if (press_frames[b] > 0) press_frames[b]--; }
    if (hold_btn >= 0) { held[hold_btn] = true; if (now > hold_until) hold_btn = -1; }
    return true;
}
void plat_swap(void) { glFinish(); now += 1 / 30.0; }
double plat_time(void) { return now; }
void plat_audio(const int16_t *s, int n) { (void)s; (void)n; }
void plat_shutdown(void) { eglTerminate(dpy); }
