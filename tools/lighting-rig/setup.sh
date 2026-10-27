#!/bin/sh
# Fetches Filament 1.75.1 (the app's version): its web build for the rig, and cmgen to make the studio light from the
# app's HDR. Run once from this folder: sh setup.sh
set -e
cd "$(dirname "$0")"
V=v1.75.1
curl -sSL -o web.tgz https://github.com/google/filament/releases/download/$V/filament-$V-web.tgz && tar xzf web.tgz && rm web.tgz
curl -sSL -o linux.tgz https://github.com/google/filament/releases/download/$V/filament-$V-linux.tgz && tar xzf linux.tgz filament/bin/cmgen && rm linux.tgz
filament/bin/cmgen -q -x ibl --format=ktx --size=256 ../../android/native/src/main/assets/env/studio.hdr
cp ../../android/native/src/main/assets/materials/outline.filamat .
ln -sfn ../../android/native/src/main/assets/models models
