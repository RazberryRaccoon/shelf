"""Game Boy Advance console and cartridge, modeled from reference photos. Real outline and proportions, stylized
details, no logos or printed text. Writes prototype-source/models/gba_console.json and gba_cart.json."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from shelf_blender import *

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'prototype-source', 'models')

# ---------------- the console: 144.5 x 82 x 24.5 mm ----------------
def console():
    reset()
    BODY = mat('body', '#5048b0', .35, .15, {'grain': 1})
    GRAY = mat('gray', '#c8c8d0', .3, .1, {'grain': 1})
    BEZEL = mat('bezel', '#2a2a30', .5, .45)
    SCREEN = mat('screen', '#767a76', .2, .5, {'screen': True})
    HOLE = mat('hole', '#141419', .05)
    METAL = mat('metal', '#9a9ca4', .9, .3)

    W, H = 1.445, .82
    outline = rounded([(-W / 2, -H / 2, .27), (W / 2, -H / 2, .27), (W / 2, H / 2, .15), (-W / 2, H / 2, .15)], 12)
    zf, zb = .1, -.085                        # the front face, and the back of the thin middle
    body = solid('body', outline, zb, zf, .035, BODY)
    # the grips: the back swells out under each hand
    for s in (-1, 1):
        g = soften(clip(offset(outline, -.006), s, 0, .42), .07)
        body2 = solid('grip', g, -.145, zb + .04, .04, BODY)
    # the battery cover on the back, with a small latch
    solid('battery', rrect(.5, .27, .03, 6, 0, -.19), -.098, -.08, .006, BODY)
    box('latch', .09, .03, -.104, -.09, 0, -.045, BODY, .01, .003)
    for x, y in ((-.4, .3), (.4, .3), (-.55, -.2), (.55, -.2), (-.28, .08), (.28, .08)):   # screws
        cylinder('screw', .016, -.094 if abs(x) < .3 else -.15, -.084 if abs(x) < .3 else -.14, x, y, METAL)
    # the gray shoulder buttons wrap the top corners, at the back
    for s in (-1, 1):
        sh = soften(clip(clip(offset(outline, .008), 0, 1, .25), s, 0, .42), .03)
        solid('shoulder', sh, -.13, -.015, .014, GRAY)
    # the cartridge slot: open at the top and the back, so an inserted cartridge's back shows, as on the real one
    cut(body, box('slot', .6, .4, -.25, -.006, 0, .27, HOLE, .01, .002))
    # front: the screen in its glossy bezel
    bez = rounded([(-.43, -.27, .1), (.43, -.27, .1), (.43, .33, .05), (-.43, .33, .05)], 8)
    solid('bezel', bez, zf - .006, zf + .006, .004, BEZEL)
    flat('screen', rrect(.6, .41, .006, 4, 0, .085), zf + .0065, SCREEN)
    # the D-pad sits in a round well
    cut(body, cylinder('dwell', .125, zf - .012, zf + .02, -.535, .13, HOLE))
    cross = rounded([(-.105, -.035, .012), (-.035, -.035, .004), (-.035, -.105, .012), (.035, -.105, .012), (.035, -.035, .004), (.105, -.035, .012),
                     (.105, .035, .012), (.035, .035, .004), (.035, .105, .012), (-.035, .105, .012), (-.035, .035, .004), (-.105, .035, .012)], 4)
    d = solid('dpad', cross, zf - .01, zf + .014, .008, GRAY)
    d.location = (-.535, 0, .13); bpy.context.view_layer.objects.active = d; bpy.ops.object.transform_apply(location=True)
    # A and B, each in a shallow ring
    for x, y in ((.44, -.02), (.6, .02)):
        cut(body, cylinder('ring', .066, zf - .008, zf + .02, x, y, HOLE))
        cylinder('button', .053, zf - .006, zf + .016, x, y, GRAY, bevel=.012)
    # Start and Select, each with a small recessed pad beside it
    for x, y in ((-.45, -.11), (-.49, -.2)):
        cut(body, box('pad', .1, .035, zf - .004, zf + .02, x - .07, y, HOLE, .017, .002))
        cylinder('small', .023, zf - .004, zf + .01, x, y, GRAY, bevel=.008)
    # the speaker slots and the power light
    for i in range(6):
        cut(body, box('grille', .17 - i * .005, .011, zf - .008, zf + .02, .55 + i * .0025, -.19 - i * .028, HOLE, .005, .001))
    cylinder('led', .009, zf - .002, zf + .004, .55, .2, mat('led', '#3a3f3a', .4, 0, {'led': True}))
    finish('gba_console', OUT)

# ---------------- the cartridge: 57.5 x 35 x 8 mm ----------------
def cartridge():
    reset()
    SHELL = mat('shell', '#3b3b42', .3, .1, {'grain': 1})
    HOLE = mat('hole', '#121216', .05)
    GOLD = mat('gold', '#d6b25a', .9, .3)
    LABEL = mat('label', '#ffffff', .22, 0, {'label': True})
    METAL = mat('metal', '#9a9ca4', .9, .3)
    w, h, d = .575 / 2, .345 / 2, .08
    # the outline: chamfered top corners, and the tab at the bottom that carries the contacts
    outline = rounded([(-w, -h, .012), (-.11, -h, .004), (-.09, -h - .014, .004), (.09, -h - .014, .004), (.11, -h, .004), (w, -h, .012),
                       (w, h - .028, .004), (w - .028, h, .004), (-w + .028, h, .004), (-w, h - .028, .004)], 6)
    body = solid('cart', outline, -d / 2, d / 2, .01, SHELL)
    # the label sits in a shallow recess with a raised lip around it; a raised band runs across the top
    cut(body, box('recess', .45, .225, d / 2 - .002, d / 2 + .02, 0, -.02, SHELL, .012, .001))
    flat('label', rrect(.44, .215, .01, 4, 0, -.02), d / 2 - .0015, LABEL)
    box('band', .44, .045, d / 2 - .004, d / 2 + .0025, 0, .125, SHELL, .01, .002)
    # the back: two screws, and a raised panel down the middle
    for y in (.09, -.06):
        cut(body, cylinder('screwhole', .02, -d / 2 - .02, -d / 2 + .004, 0, y, HOLE))
        cylinder('screw', .014, -d / 2 + .001, -d / 2 + .004, 0, y, METAL)
    box('ridge', .2, .26, -d / 2 - .003, -d / 2 + .004, 0, -.01, SHELL, .02, .002)
    # the contacts: an opening along the bottom edge with the board's gold fingers inside
    cut(body, box('mouth', .44, .06, -.022, .022, 0, -h - .01, HOLE, .004, .001))
    for i in range(32):
        box('pin', .009, .03, -.006, .006, -.195 + i * .0126, -h + .006, GOLD, .002, .001)
    finish('gba_cart', OUT)

if __name__ == '__main__':
    which = sys.argv[1:] or ['console', 'cart']
    if 'console' in which: console()
    if 'cart' in which: cartridge()
