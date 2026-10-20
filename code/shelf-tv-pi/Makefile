# Shelf for TV
#   make              builds ./shelf for the Raspberry Pi (needs: libsdl2-dev libgles-dev libfreetype-dev libpng-dev)
#   make shelf-test   builds an offscreen test version (development only)
CFLAGS ?= -O2 -Wall -Wextra -Wno-unused-parameter -std=gnu11
CORE = src/main.c src/gfx.c src/models.c src/art.c src/text.c src/library.c
DEPS = $(shell pkg-config --cflags freetype2 libpng)
LIBS = $(shell pkg-config --libs freetype2 libpng) -lm

shelf: $(CORE) src/platform_sdl.c src/*.h
	$(CC) $(CFLAGS) $(DEPS) $(shell pkg-config --cflags sdl2) -o $@ $(CORE) src/platform_sdl.c $(shell pkg-config --libs sdl2) $(shell pkg-config --libs glesv2 2>/dev/null || echo -lGLESv2) $(LIBS)

shelf-test: $(CORE) test/platform_egl.c src/*.h
	$(CC) $(CFLAGS) $(DEPS) -Itest/include -o $@ $(CORE) test/platform_egl.c /usr/lib/x86_64-linux-gnu/libGLESv2.so.2 /usr/lib/x86_64-linux-gnu/libEGL.so.1 $(LIBS)

clean:
	rm -f shelf shelf-test
.PHONY: clean
