#!/usr/bin/env python3
"""
Writes sky.mat's theme code from the theme viewer (prototypes/shelf-themes.html), so the app draws exactly what the
viewer shows: everything from the city's buildings to the end of stormy() is copied over, with the viewer's uniforms
renamed to the material's parameters, integer steps for the texture hash, and a few early exits (work skipped where it
can't show) that a handheld's GPU needs. The material's header and its main() stay as they are in sky.mat.
    python3 sky-from-viewer.py && MATC=/path/to/matc sh build.sh
"""
import os
here = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(here, '..', '..', '..', 'prototypes', 'shelf-themes.html')).read()
mat = open(os.path.join(here, 'sky.mat')).read()
body = src[src.index('// a layer of buildings'):src.index('void main() {')]
body = body.replace('uT', 'T').replace('uMood', 'MOOD').replace('uFlash', 'FLASH')
for a, b in [
    ("hash(wc + cell + (slot - 1.) * .37)", "hash(wc + cell + vec2(7., 3.) * (slot - 1.))"),
    ("hash(wc + cell + slot * .37)", "hash(wc + cell + vec2(7., 3.) * slot)"),
    # early exits: the forest's drifting fog only near its valley, the desert's drifting sand only near its crest
    ("    float f = fbm(vec2(p.x * .0028", "    if (abs(p.y - base - 55.) > 230.) continue;\n    float f = fbm(vec2(p.x * .0028"),
    ("    float drift = fbm(", "    if (abs(p.y - ridge + 8.) > 50.) continue;\n    float drift = fbm("),
]:
    assert body.count(a) == 1, a
    body = body.replace(a, b)
body = '\n'.join(('    ' + l if l.strip() else l) for l in body.splitlines())
head = mat[:mat.index('    // a layer of buildings')]
tail = mat[mat.index('    void material(inout MaterialInputs material)'):]
open(os.path.join(here, 'sky.mat'), 'w').write(head + body.rstrip() + '\n\n' + tail)
print('sky.mat written')
