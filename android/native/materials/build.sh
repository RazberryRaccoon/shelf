#!/bin/sh
# Compiles Shelf Native's own materials into the app's assets. Needs matc from the Filament release the app uses
# (1.75.1: https://github.com/google/filament/releases, filament-v1.75.1-linux.tgz, bin/matc):
#   MATC=/path/to/matc sh build.sh
cd "$(dirname "$0")"
for m in backdrop shadow glow sky; do
    "${MATC:-matc}" -p mobile -a opengl -a vulkan -o ../src/main/assets/materials/$m.filamat $m.mat || exit 1
done
