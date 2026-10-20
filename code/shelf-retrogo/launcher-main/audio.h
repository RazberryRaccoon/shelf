// Little chiptune blips through the speaker.
#pragma once
#include <stdbool.h>

enum { WAVE_SQUARE, WAVE_TRIANGLE };

void sound_init(void);
void sound_set_enabled(bool on);                    // loudness itself follows the system volume
void sound_tone(float freq, float dur, int wave, float at, float vol, float slide_to);
void sound_noise(float dur, float at, float vol, float f0, float f1);

void sfx_tick(int dir);
void sfx_bump(void);
void sfx_shelf(void);
void sfx_open(void);
void sfx_back(void);
void sfx_fav(bool on);
void sfx_sort(void);
void sfx_clunk(void);
void sfx_jingle(void);
void sfx_unjingle(void);
void sfx_pop(void);
void sfx_whoosh(void);
void sfx_saved(void);
void sfx_letter(int i);
void sfx_vol(void);
