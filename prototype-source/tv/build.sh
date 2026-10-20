#!/bin/sh
# Rebuilds shelf-tv.html from its parts (the order matters).
cd "$(dirname "$0")" && cat head.html p1b.html p2.js p3.js p4.js p5.js p6.js > shelf-tv.html && echo "built shelf-tv.html"
