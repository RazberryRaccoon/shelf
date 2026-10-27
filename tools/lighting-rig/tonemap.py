# Applies a tone curve to a LINEAR rig render (exposure / 4, transparent around the models) and composites it over the
# backdrop: tonemap.py in.png bg.png out.png curve [k]
import sys
import numpy as np
from PIL import Image
src, bgp, out, curve = sys.argv[1:5]; K = float(sys.argv[5]) if len(sys.argv) > 5 else 4.0
im = np.asarray(Image.open(src).convert('RGBA')).astype(np.float64) / 255
bg = np.asarray(Image.open(bgp).convert('RGB')).astype(np.float64) / 255
a = im[..., 3:4]; c = np.where(a > 0, im[..., :3] / np.maximum(a, 1e-6), 0)   # unpremultiplied (the page's canvas is premultiplied)
def dec(v): return np.where(v <= .04045, v / 12.92, ((v + .055) / 1.055) ** 2.4)
def enc(v): v = np.clip(v, 0, 1); return np.where(v <= .0031308, v * 12.92, 1.055 * v ** (1 / 2.4) - .055)
x = dec(c) * K
if curve == 'neutral':
    start = .8 - .04; desat = .15
    m = x.min(axis=2, keepdims=True); off = np.where(m < .08, m - 6.25 * m * m, .04); x = x - off
    peak = x.max(axis=2, keepdims=True); d = 1 - start
    newp = 1 - d * d / (peak + d - start)
    y = x * np.where(peak > start, newp / np.maximum(peak, 1e-6), 1)
    g = 1 - 1 / (desat * (peak - newp) + 1)
    y = np.where(peak > start, y * (1 - g) + newp * g, y)
elif curve == 'aces':   # Narkowicz's fit of ACES (for comparison only)
    x = x / .6 * .6; y = (x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14)
else: y = x
o = enc(y) * a + bg * (1 - a)
Image.fromarray((np.clip(o, 0, 1) * 255 + .5).astype(np.uint8)).save(out)
