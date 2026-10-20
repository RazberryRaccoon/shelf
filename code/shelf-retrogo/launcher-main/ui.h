// The launcher's screens and animations, ported from the browser prototype.
#pragma once
#include <stdint.h>
#include <stdbool.h>

void ui_init(void);
void ui_message(const char *text, bool dismissable);   // full-screen message; \n starts a new paragraph
void ui_frame(float dt, uint32_t buttons);                        // update, then draw into the screen
