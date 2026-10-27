import sys
from PIL import Image
import numpy as np
im=np.asarray(Image.open(sys.argv[1]).convert('RGB')).astype(float)
shells=[0xe6,0x9a,0x5a,0x2a,0x12]; labels=[255,192,128,64,32]
def patch(x0,x1,y0,y1): return im[y0:y1,x0:x1].mean()
out=[]
for i in range(5):
    x=140+i*262
    s=patch(x+52,x+82,140,152)   # shell, top right between the ridges and the corner
    l=patch(x-40,x+40,520,580)  # label (bottom row), centre
    out.append((shells[i],round(s),labels[i],round(l)))
print(' '.join(f'shell {a}->{b}  label {c}->{d} |' for a,b,c,d in out))
