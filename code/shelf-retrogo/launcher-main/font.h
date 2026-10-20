#pragma once
#include <stdint.h>

typedef struct { uint32_t cp; uint8_t rows[8]; } font_special_t;
extern const uint8_t FONT_ASCII[95][8];
extern const font_special_t FONT_SPECIAL[];
extern const int FONT_SPECIAL_N;
