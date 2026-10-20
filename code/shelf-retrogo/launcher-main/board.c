#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"
#include "esp_heap_caps.h"
#include <rg_system.h>
#include "board.h"

static uint16_t *frames[2];
static int drawing;
static volatile int showing = -1;
static SemaphoreHandle_t frame_ready, frame_sent, bus;

static void display_task(void *arg)
{
    while (1) {
        xSemaphoreTake(frame_ready, portMAX_DELAY);
        xSemaphoreTake(bus, portMAX_DELAY);
        rg_display_write_rect(0, 0, SCREEN_W, SCREEN_H, SCREEN_W * 2, frames[showing], 0);
        xSemaphoreGive(bus);
        xSemaphoreGive(frame_sent);
    }
}

void board_init(void)
{
    for (int i = 0; i < 2; i++) {
        frames[i] = heap_caps_malloc(SCREEN_W * SCREEN_H * 2, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
        if (!frames[i]) frames[i] = heap_caps_malloc(SCREEN_W * SCREEN_H * 2, MALLOC_CAP_8BIT);
        if (!frames[i]) RG_PANIC("Not enough memory for the frame buffers");
        memset(frames[i], 0, SCREEN_W * SCREEN_H * 2);
    }
    frame_ready = xSemaphoreCreateBinary();
    frame_sent = xSemaphoreCreateBinary();
    bus = xSemaphoreCreateMutex();
    xSemaphoreGive(frame_sent);
    xTaskCreatePinnedToCore(display_task, "shelf_disp", 3072, NULL, 4, NULL, 1);
}

uint16_t *board_frame(void) { return frames[drawing]; }

void board_present(void)
{
    xSemaphoreTake(frame_sent, portMAX_DELAY);   // the previous frame is on screen
    showing = drawing;
    drawing ^= 1;
    xSemaphoreGive(frame_ready);
}

void board_sync(void)
{
    xSemaphoreTake(frame_sent, portMAX_DELAY);
    xSemaphoreGive(frame_sent);
}

void board_bus_lock(void) { if (bus) xSemaphoreTake(bus, portMAX_DELAY); }
void board_bus_unlock(void) { if (bus) xSemaphoreGive(bus); }

uint32_t board_input_read(void)
{
    uint32_t k = rg_input_read_gamepad(), s = 0;
    if (k & RG_KEY_UP) s |= BTN_UP;
    if (k & RG_KEY_DOWN) s |= BTN_DOWN;
    if (k & RG_KEY_LEFT) s |= BTN_LEFT;
    if (k & RG_KEY_RIGHT) s |= BTN_RIGHT;
    if (k & RG_KEY_A) s |= BTN_A;
    if (k & RG_KEY_B) s |= BTN_B;
    if (k & RG_KEY_SELECT) s |= BTN_SELECT;
    if (k & RG_KEY_START) s |= BTN_START;
    if (k & RG_KEY_MENU) s |= BTN_MENU;
    if (k & RG_KEY_OPTION) s |= BTN_VOLUME;   // the Volume button on the Odroid GO
    return s;
}

int board_battery_percent(void)
{
    static double last = -10;
    static int pct = -1;
    double now = board_seconds();
    if (now - last >= 2.0) {
        last = now;
        rg_battery_t b = rg_input_read_battery();
        pct = b.present ? (int)(b.level + 0.5f) : -1;
        if (pct > 100) pct = 100;
    }
    return pct;
}

double board_seconds(void) { return rg_system_timer() / 1000000.0; }
