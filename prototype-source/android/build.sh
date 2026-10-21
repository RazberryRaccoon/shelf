#!/bin/sh
# Rebuilds shelf-android.html from its parts (the order matters). The Blender models in ../models are joined in
# after r2.js, which opens the script.
cd "$(dirname "$0")" && {
  cat r1.html r2.js
  printf "const MODEL_DATA = {\n"
  for f in ../models/*.json; do [ -f "$f" ] && { printf "%s: " "$(basename "$f" .json)"; cat "$f"; printf ",\n"; }; done
  printf "};\n"
  cat r2m.js r3.js r4.js r4g.js r4m.js r4c.js r5a.js r5b.js r5_reused.js r5m.js rn.js r6.js
} > shelf-android.html && echo "built shelf-android.html"
