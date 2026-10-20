// What Shelf needs from the system: a GL ES 2 screen, buttons, a clock and a little sound.
// platform_sdl.c provides it on the Pi; test/platform_egl.c provides an offscreen version for testing.
#pragma once
#include <stdbool.h>
#include <stdint.h>

enum { BTN_UP, BTN_DOWN, BTN_LEFT, BTN_RIGHT, BTN_A, BTN_B, BTN_X, BTN_Y, BTN_LB, BTN_RB, BTN_START, BTN_SELECT, BTN_HOME, BTN_COUNT };

bool plat_init(int *width, int *height);       // opens the screen; returns its size in pixels
bool plat_poll(bool held[BTN_COUNT]);           // which buttons are down right now; false means quit
void plat_swap(void);
double plat_time(void);                         // seconds
void plat_audio(const int16_t *samples, int count);   // mono, 22050 Hz
void plat_shutdown(void);
