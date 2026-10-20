// The Raspberry Pi side: SDL2 gives Shelf a fullscreen OpenGL ES 2 screen, controllers, the keyboard and sound.
#include "platform.h"
#include <SDL2/SDL.h>
#include <stdio.h>

static SDL_Window *win; static SDL_GLContext ctx; static SDL_AudioDeviceID audio;
static bool keys[BTN_COUNT];

static void open_controllers(void) {
    for (int i = 0; i < SDL_NumJoysticks(); i++) if (SDL_IsGameController(i) && !SDL_GameControllerFromInstanceID(SDL_JoystickGetDeviceInstanceID(i))) SDL_GameControllerOpen(i);
}
bool plat_init(int *w, int *h) {
    SDL_SetHint(SDL_HINT_VIDEO_DOUBLE_BUFFER, "1");
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_GAMECONTROLLER | SDL_INIT_AUDIO) != 0) { fprintf(stderr, "SDL: %s\n", SDL_GetError()); return false; }
    // extra controller mappings, if the installer left any
    const char *home = SDL_getenv("HOME"); char db[512];
    if (home) { SDL_snprintf(db, sizeof db, "%s/shelf/gamecontrollerdb.txt", home); SDL_GameControllerAddMappingsFromFile(db); }
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 2); SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0);
    SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, 16);
    SDL_DisplayMode dm; SDL_GetDesktopDisplayMode(0, &dm);
    win = SDL_CreateWindow("Shelf", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, dm.w, dm.h, SDL_WINDOW_OPENGL | SDL_WINDOW_FULLSCREEN_DESKTOP);
    if (!win) { fprintf(stderr, "SDL window: %s\n", SDL_GetError()); return false; }
    ctx = SDL_GL_CreateContext(win);
    if (!ctx) { fprintf(stderr, "SDL GL: %s\n", SDL_GetError()); return false; }
    SDL_GL_SetSwapInterval(1);
    SDL_ShowCursor(SDL_DISABLE);
    SDL_GL_GetDrawableSize(win, w, h);
    open_controllers();
    SDL_AudioSpec want = { 0 }, got; want.freq = 22050; want.format = AUDIO_S16SYS; want.channels = 1; want.samples = 1024;
    audio = SDL_OpenAudioDevice(NULL, 0, &want, &got, 0); if (audio) SDL_PauseAudioDevice(audio, 0);
    return true;
}
static int key_button(SDL_Keycode k) {
    switch (k) {
        case SDLK_UP: return BTN_UP; case SDLK_DOWN: return BTN_DOWN; case SDLK_LEFT: return BTN_LEFT; case SDLK_RIGHT: return BTN_RIGHT;
        case SDLK_RETURN: case SDLK_SPACE: return BTN_A; case SDLK_ESCAPE: case SDLK_BACKSPACE: return BTN_B;
        case SDLK_f: return BTN_Y; case SDLK_q: return BTN_LB; case SDLK_e: return BTN_RB; case SDLK_TAB: return BTN_START; case SDLK_h: return BTN_HOME;
        default: return -1;
    }
}
bool plat_poll(bool held[BTN_COUNT]) {
    SDL_Event e;
    while (SDL_PollEvent(&e)) {
        if (e.type == SDL_QUIT) return false;
        if (e.type == SDL_CONTROLLERDEVICEADDED) open_controllers();
        if ((e.type == SDL_KEYDOWN || e.type == SDL_KEYUP) && !e.key.repeat) { int b = key_button(e.key.keysym.sym); if (b >= 0) keys[b] = e.type == SDL_KEYDOWN; }
    }
    for (int b = 0; b < BTN_COUNT; b++) held[b] = keys[b];
    static const int map[][2] = { { SDL_CONTROLLER_BUTTON_DPAD_UP, BTN_UP }, { SDL_CONTROLLER_BUTTON_DPAD_DOWN, BTN_DOWN }, { SDL_CONTROLLER_BUTTON_DPAD_LEFT, BTN_LEFT },
        { SDL_CONTROLLER_BUTTON_DPAD_RIGHT, BTN_RIGHT }, { SDL_CONTROLLER_BUTTON_A, BTN_A }, { SDL_CONTROLLER_BUTTON_B, BTN_B }, { SDL_CONTROLLER_BUTTON_X, BTN_X },
        { SDL_CONTROLLER_BUTTON_Y, BTN_Y }, { SDL_CONTROLLER_BUTTON_LEFTSHOULDER, BTN_LB }, { SDL_CONTROLLER_BUTTON_RIGHTSHOULDER, BTN_RB },
        { SDL_CONTROLLER_BUTTON_START, BTN_START }, { SDL_CONTROLLER_BUTTON_BACK, BTN_SELECT }, { SDL_CONTROLLER_BUTTON_GUIDE, BTN_HOME } };
    for (int i = 0; i < SDL_NumJoysticks(); i++) {
        SDL_GameController *c = SDL_GameControllerFromInstanceID(SDL_JoystickGetDeviceInstanceID(i)); if (!c) continue;
        for (unsigned k = 0; k < sizeof map / sizeof map[0]; k++) if (SDL_GameControllerGetButton(c, map[k][0])) held[map[k][1]] = true;
        int ax = SDL_GameControllerGetAxis(c, SDL_CONTROLLER_AXIS_LEFTX), ay = SDL_GameControllerGetAxis(c, SDL_CONTROLLER_AXIS_LEFTY);
        if (ax < -18000) held[BTN_LEFT] = true;
        if (ax > 18000) held[BTN_RIGHT] = true;
        if (ay < -18000) held[BTN_UP] = true;
        if (ay > 18000) held[BTN_DOWN] = true;
    }
    return true;
}
void plat_swap(void) { SDL_GL_SwapWindow(win); }
double plat_time(void) { return SDL_GetPerformanceCounter() / (double)SDL_GetPerformanceFrequency(); }
void plat_audio(const int16_t *s, int n) { if (audio && SDL_GetQueuedAudioSize(audio) < 22050 * 2) SDL_QueueAudio(audio, s, n * 2); }
void plat_shutdown(void) {
    // release the screen completely so RetroArch can take it over
    if (audio) SDL_CloseAudioDevice(audio);
    SDL_GL_DeleteContext(ctx); SDL_DestroyWindow(win); SDL_Quit();
}
