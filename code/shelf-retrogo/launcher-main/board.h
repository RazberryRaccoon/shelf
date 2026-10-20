// Shelf's view of the hardware. Everything here sits on top of retro-go.
#pragma once
#include <stdint.h>
#include <stdbool.h>

#define SCREEN_W 320
#define SCREEN_H 240

enum {
    BTN_UP = 1 << 0, BTN_DOWN = 1 << 1, BTN_LEFT = 1 << 2, BTN_RIGHT = 1 << 3,
    BTN_A = 1 << 4, BTN_B = 1 << 5, BTN_SELECT = 1 << 6, BTN_START = 1 << 7,
    BTN_MENU = 1 << 8, BTN_VOLUME = 1 << 9,
};

void board_init(void);

// Two frame buffers: draw into board_frame(), then board_present() hands it to the
// screen. The transfer runs on the other core while the next frame is drawn.
uint16_t *board_frame(void);
void board_present(void);
void board_sync(void);          // wait until nothing is being sent (before retro-go draws its own dialogs)

// The SD card shares the screen's SPI bus; wrap SD card access in these.
void board_bus_lock(void);
void board_bus_unlock(void);

uint32_t board_input_read(void);
int board_battery_percent(void);  // -1 when there's no battery reading
double board_seconds(void);
