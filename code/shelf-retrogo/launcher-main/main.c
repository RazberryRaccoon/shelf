// Shelf: a launcher for the Odroid GO, running as retro-go's launcher app.
#include <stdlib.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_heap_caps.h"
#include <rg_system.h>
#include "board.h"
#include "gfx.h"
#include "audio.h"
#include "library.h"
#include "shelf_system.h"
#include "backdrop.h"
#include "ui.h"

static void event_handler(int event, void *arg)
{
    (void)event; (void)arg;   // Shelf redraws every frame anyway
}

void app_main(void)
{
    const rg_handlers_t handlers = {.event = &event_handler};
    rg_app_t *app = rg_system_init(32000, &handlers, NULL);   // 32000 must match RATE in audio.c
    app->configNs = "launcher";
    app->isLauncher = true;

    // Same workaround as retro-go's own launcher: keep many small allocations out of
    // internal RAM so the SD card driver always has room.
    heap_caps_malloc_extmem_enable(1024);

    board_init();
    gfx_init(board_frame());
    prefs_load();
    sound_init();
    sound_set_enabled(PREFS.sound);

    if (!rg_storage_ready()) {
        ui_message("Can’t read the SD card.\nCheck that it’s inserted and formatted as FAT32, then restart.", false);
    } else {
        board_bus_lock();
        rg_storage_mkdir(RG_STORAGE_ROOT "/shelf");
        board_bus_unlock();
        install_theme();
        lib_scan();
        lib_load_meta();
        backdrops_build_async();
        ui_init();
    }

    double last = board_seconds();
    while (1) {
        double now = board_seconds();
        float dt = now - last;
        last = now;
        int64_t start = rg_system_timer();
        gfx_set_screen(board_frame());
        ui_frame(dt, board_input_read());
        rg_system_tick(rg_system_timer() - start);   // tells retro-go Shelf is alive
        board_present();
        vTaskDelay(1);
    }
}
