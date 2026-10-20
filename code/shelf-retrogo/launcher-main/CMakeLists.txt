# Shelf replaces retro-go's launcher app. This file replaces launcher/main/CMakeLists.txt.
idf_component_register(
    SRCS "main.c" "board.c" "gfx.c" "font.c" "audio.c" "library.c" "shelf_system.c" "art.c" "backdrop.c" "ui.c"
    INCLUDE_DIRS "."
    REQUIRES retro-go json)
rg_setup_compile_options(-O2)
