#!/bin/bash
# Rebuilds the files in downloads/ from the source folders.
# Run from the repository root:   bash scripts/make-downloads.sh
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$PWD/downloads"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"
rm -f "$OUT"/*.zip "$OUT"/*.tar.gz

# Odroid GO: unzip it to C:\shelf-retrogo and the paths in its README work as written.
mkdir -p "$TMP/odroid/prototype"
cp -r code/shelf-retrogo/. "$TMP/odroid/"
cp code/shelf-firmware-standalone.zip "$TMP/odroid/old-standalone-firmware.zip"
cp prototypes/shelf-odroid-launcher.html "$TMP/odroid/prototype/"
(cd "$TMP/odroid" && zip -qrX "$OUT/shelf-odroid-go.zip" .)

# TV / Raspberry Pi: the installer package its README expects (unpacks to shelf-tv/).
cp -r code/shelf-tv-pi "$TMP/shelf-tv"
(cd "$TMP" && tar --sort=name --owner=0 --group=0 --mtime='2026-01-01' -czf "$OUT/shelf-tv.tar.gz" shelf-tv)
(cd prototypes && zip -qX "$OUT/shelf-tv-prototype.zip" shelf-tv.html)

# Android: the browser prototype.
(cd prototypes && zip -qX "$OUT/shelf-android-prototype.zip" shelf-android.html)

ls -l "$OUT"
