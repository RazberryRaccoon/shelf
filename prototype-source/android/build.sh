#!/bin/sh
# Rebuilds shelf-android.html from its parts (the order matters).
cd "$(dirname "$0")" && cat r1.html r2.js r3.js r4.js r5a.js r5b.js r5_reused.js r6.js > shelf-android.html && echo "built shelf-android.html"
