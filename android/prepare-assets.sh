#!/usr/bin/env bash
# Builds the Shelf interface from prototype-source/android and puts it in the app as assets/index.html,
# pointing at the bundled fonts instead of Google Fonts so it works offline. Run from anywhere.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../prototype-source/android"
bash "$src/build.sh"
python3 - "$src/shelf-android.html" "$here/app/src/main/assets/index.html" <<'PY'
import re, sys
html = open(sys.argv[1], encoding='utf-8').read()
html = re.sub(r'<link rel="preconnect"[^>]*>\n', '', html)
html, n = re.subn(r'<link href="https://fonts\.googleapis\.com/[^"]*" rel="stylesheet">', '<link href="fonts/fonts.css" rel="stylesheet">', html)
assert n == 1, 'font link not found'
html = html.replace('<title>Shelf for Android, prototype</title>', '<title>Shelf</title>')
open(sys.argv[2], 'w', encoding='utf-8').write(html)
PY
rm "$src/shelf-android.html"
echo "wrote app/src/main/assets/index.html"
