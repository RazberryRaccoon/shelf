# Contact sheet: python3 sheet.py frames_dir out.png [cols] [scale] [label_start label_step]
import sys, os
from PIL import Image, ImageDraw
d, out = sys.argv[1], sys.argv[2]; cols = int(sys.argv[3]) if len(sys.argv) > 3 else 4; sc = float(sys.argv[4]) if len(sys.argv) > 4 else .3
t0 = float(sys.argv[5]) if len(sys.argv) > 5 else 0; st = float(sys.argv[6]) if len(sys.argv) > 6 else .1
fs = sorted(f for f in os.listdir(d) if f.endswith('.png'))
ims = [Image.open(os.path.join(d, f)).convert('RGB') for f in fs]
w, h = int(ims[0].width * sc), int(ims[0].height * sc); rows = (len(ims) + cols - 1) // cols
S = Image.new('RGB', (cols * w, rows * h), 'black'); D = ImageDraw.Draw(S)
for i, im in enumerate(ims):
    x, y = (i % cols) * w, (i // cols) * h; S.paste(im.resize((w, h)), (x, y)); D.text((x + 6, y + 4), f't={t0 + i * st:.2f}', fill='white')
S.save(out)
