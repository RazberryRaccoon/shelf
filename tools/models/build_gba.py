"""Game Boy Advance console and cartridge, modeled from reference photos (front, back, cartridge front and back).
The outline was traced from the back photo; sizes are the real ones (144.5 x 82 x 24.5 mm, cartridge 57.5 x 33.5 x
8 mm). Details are stylized, with no logos or printed text.
Writes prototype-source/models/gba_console.json and gba_cart.json. With --preview, also renders comparison images."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from shelf_blender import *

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'prototype-source', 'models')
PREVIEW = '--preview' in sys.argv

# The outline seen from the front: top corners rounded where the shoulder buttons wrap them, the ends swelling out
# a little below the middle, and softer bottom corners.
W2, H2 = .7225, .41
OUTLINE = rounded([(-.69, -H2, .17), (.69, -H2, .17), (W2, -.15, .6), (.69, H2, .27), (-.69, H2, .27), (-W2, -.15, .6)], 14)
# The purple face has big diagonal cuts at its top corners; the gray shoulder buttons fill them, stepped back.
FACE = rounded([(-.69, -H2, .17), (.69, -H2, .17), (W2, -.15, .6), (.703, .16, .07), (.455, H2, .06), (-.455, H2, .06), (-.703, .16, .07), (-W2, -.15, .6)], 14)

def console():
    reset()
    FACE_M = mat('face', '#5a4eb6', .35, .2, {'grain': .25})
    BACK = mat('back', '#4b40a0', .3, .15, {'grain': .25})
    GRAY = mat('gray', '#c6c6cc', .3, .15, {'grain': .2})
    KEY = mat('key', '#cfcfd4', .4, .25)
    BEZEL = mat('bezel', '#28282c', .6, .55)
    LCD = mat('lcd', '#7c7e7a', .15, .35, {'screen': True})
    BORDER = mat('border', '#1a1b1c', .3, .4)
    HOLE = mat('hole', '#121216', .05)
    METAL = mat('metal', '#a2a4ab', .9, .35)
    LED = mat('led', '#2f3a30', .4, .2, {'led': True})

    zf, zb = .1225, -.1225
    # two shell halves with a seam between them; the back edge is softer than the front
    front = solid('front', FACE, .002, zf, [.008, .002], FACE_M)
    back = solid('back', FACE, zb, -.002, [.002, .024], BACK)
    for s in (-1, 1):
        # the shoulder button fills the cut corner, a step behind the face, following the round outline outside it
        sh = soften(clip(clip(offset(OUTLINE, .002), 0, 1, .1), s, 0, .36), .02)
        solid('shoulder', sh, -.105, .062, [.012, .01], GRAY)
        # below it, a gray band runs down the end, on the back half
        solid('side', band(OUTLINE, .003, .028, lambda x, y: s * x > .55 and y < .09), -.108, -.006, .007, GRAY)
    # ---- the front ----
    bez = rounded([(-.405, -.3, .13), (.405, -.3, .13), (.405, .345, .055), (-.405, .345, .055)], 10)
    bezel = solid('bezel', bez, zf - .004, zf + .004, .0025, BEZEL)
    cut(bezel, box('window', .61, .412, zf - .001, zf + .02, 0, .09, BORDER, .012, .001))
    flat('border', rrect(.612, .414, .012, 4, 0, .09), zf + .0008, BORDER)
    flat('lcd', rrect(.58, .39, .004, 4, 0, .09), zf + .0016, LCD)
    # the D-pad in a cross-shaped opening, with a dimple in the middle
    cx, cy = -.535, .125
    arm, aw = .105, .034
    cross = lambda g: rounded([(-arm - g, -aw - g, .012), (-aw - g, -aw - g, .006), (-aw - g, -arm - g, .012), (aw + g, -arm - g, .012), (aw + g, -aw - g, .006),
                               (arm + g, -aw - g, .012), (arm + g, aw + g, .012), (aw + g, aw + g, .006), (aw + g, arm + g, .012), (-aw - g, arm + g, .012),
                               (-aw - g, aw + g, .006), (-arm - g, aw + g, .012)], 4)
    cut(front, move(solid('dpadhole', cross(.007), zf - .014, zf + .02, .002, HOLE), cx, cy))
    dpad = move(solid('dpad', cross(0), zf - .012, zf + .016, .008, KEY), cx, cy)
    cut(dpad, move(cylinder('dimple', .022, zf + .012, zf + .03, 0, 0, KEY, bevel=.004), cx, cy))
    # A and B, each sitting in a shallow round dish
    for x, y in ((.455, -.04), (.6, .005)):
        cut(front, cylinder('dish', .07, zf - .006, zf + .02, x, y, FACE_M, bevel=.003))
        cylinder('ab', .057, zf - .006, zf + .017, x, y, KEY, bevel=.016)
    # Start and Select, each with a recessed pad beside it
    for x, y in ((-.455, -.105), (-.495, -.195)):
        pad = rotated(rrect(.115, .044, .022, 6, x - .06, y + .012), 20, x - .06, y + .012)
        cut(front, solid('pad', pad, zf - .004, zf + .02, .002, FACE_M))
        cylinder('ss', .022, zf - .004, zf + .011, x, y, KEY, bevel=.008)
    # the power light
    cut(front, cylinder('ledhole', .012, zf - .004, zf + .02, .555, .195, HOLE, bevel=.001))
    cylinder('led', .009, zf - .004, zf - .0005, .555, .195, LED, bevel=.002)
    # six speaker slots, their right ends following the rounded corner
    for i in range(6):
        y = -.165 - i * .029; L = .19 - max(0, i - 2) * .025
        cut(front, box('slot', L, .012, zf - .012, zf + .02, .405 + L / 2 - i * .005, y, HOLE, .006, .001))

    # ---- the edges: cartridge slot on top, headphones, volume and power on the bottom ----
    cut(back, box('cartslot', .6, .4, -.104, -.014, 0, H2 + .1 - .35 + .2 - .01, HOLE, .01, .002))
    jack = rotate(cylinder('jack', .024, -.04, .04, 0, 0, HOLE), rx=90); cut(front, move(jack, .17, -H2, .05))
    cut(front, move(box('volslot', .07, .1, .02, .07, 0, 0, HOLE, .004), .33, -H2))
    wheel = rotate(cylinder('wheel', .03, -.012, .012, 0, 0, GRAY, bevel=.004), ry=90); move(wheel, .33, -H2 + .012, .045)
    cut(back, move(box('pwrslot', .1, .1, -.08, -.03, 0, 0, HOLE, .004), -.42, -H2))
    move(box('pwr', .04, .03, -.07, -.04, 0, 0, GRAY, .006, .003), -.44, -H2 - .006)

    # ---- the back: screws in their holes, the battery door, and a recess for the sticker ----
    for x, y in ((-.36, .3), (.36, .3), (-.49, .17), (.49, .17), (-.63, -.27), (.63, -.27)):
        cut(back, cylinder('screwhole', .024, zb - .02, zb + .012, x, y, HOLE, bevel=.002))
        screw = cylinder('screw', .017, zb + .004, zb + .009, x, y, METAL, bevel=.002)
        for ang in (0, 90):   # a cross in the screw head
            cut(screw, move(rotate(box('cross', .022, .0045, zb + .001, zb + .0055, 0, 0, HOLE, .001, .0005), rz=ang), x, y))
    cut(back, solid('doorgap', rrect(.652, .372, .034, 8, 0, -.212), zb - .02, zb + .006, .001, HOLE))
    door = solid('door', rrect(.64, .36, .028, 8, 0, -.212), zb + .0005, zb + .006, [.001, .003], BACK)
    cut(door, box('latch', .08, .03, zb - .02, zb + .003, 0, -.04, HOLE, .01, .001))
    for i in range(4): box('grip', .05, .004, zb - .0015, zb + .001, 0, -.075 - i * .01, BACK, .002, .001)
    cut(back, box('sticker', .42, .13, zb - .02, zb + .0015, 0, .245, BACK, .01, .001))
    ob = finish('gba_console', OUT)
    if PREVIEW:
        for v in ('front', 'back', 'three-quarter', 'top', 'bottom'): preview(ob, os.path.join(PREV, f'gba_console_{v}.png'), v)

# ---------------- the cartridge ----------------
def cartridge():
    reset()
    SHELL = mat('shell', '#303236', .3, .15, {'grain': .2})
    HOLE = mat('hole', '#121216', .05)
    GOLD = mat('gold', '#d6b25a', .9, .3)
    LABEL = mat('label', '#ffffff', .22, 0, {'label': True})
    METAL = mat('metal', '#9a9ca4', .9, .3)
    w, top, bot, d = .2875, .1675, -.1675, .08
    # wider at the top where you grip it (a step 7 mm down each side), chamfered top corners, and the tab at the bottom
    step, sx = top - .07, .014
    outline = rounded([(-w + sx, bot, .012), (-.11, bot, .004), (-.09, bot - .012, .004), (.09, bot - .012, .004), (.11, bot, .004), (w - sx, bot, .012),
                       (w - sx, step - .008, .003), (w, step, .003), (w, top - .016, .003), (w - .016, top, .003), (-w + .016, top, .003), (-w, top - .016, .003),
                       (-w, step, .003), (-w + sx, step - .008, .003)], 6)
    body = solid('cart', outline, -d / 2, d / 2, [.006, .006], SHELL)
    # front: the label sits in a recess; the grip at the top is a slightly raised band
    cut(body, box('recess', .45, .222, d / 2 - .0025, d / 2 + .02, 0, -.045, SHELL, .012, .001))
    flat('label', rrect(.44, .212, .01, 4, 0, -.045), d / 2 - .002, LABEL)
    cut(body, box('band', .47, .042, d / 2 - .0012, d / 2 + .02, 0, .128, SHELL, .01, .001))   # the grip band, slightly sunk
    # back: two screws in their holes, and a shallow panel between them
    for y in (.125, -.02):
        cut(body, cylinder('screwhole', .014, -d / 2 - .02, -d / 2 + .008, 0, y, HOLE))
        cylinder('screw', .0105, -d / 2 + .003, -d / 2 + .007, 0, y, METAL, bevel=.002)
    cut(body, box('panel', .32, .16, -d / 2 - .02, -d / 2 + .0015, 0, -.06, SHELL, .02, .001))
    # the contacts: an opening along the bottom edge with the board's gold fingers inside
    cut(body, box('mouth', .44, .06, -.026, .02, 0, bot - .01, HOLE, .004, .001))
    for i in range(32):
        box('pin', .009, .02, -.012, -.004, -.195 + i * .0126, bot + .013, GOLD, .002, .001)
    ob = finish('gba_cart', OUT)
    if PREVIEW:
        for v in ('front', 'back', 'bottom', 'three-quarter'): preview(ob, os.path.join(PREV, f'gba_cart_{v}.png'), v, ortho=.7)

PREV = os.environ.get('SHELF_PREVIEW_DIR', '/tmp/shelf-previews'); os.makedirs(PREV, exist_ok=True)
if __name__ == '__main__':
    which = [a for a in sys.argv[1:] if not a.startswith('--')] or ['console', 'cart']
    if 'console' in which: console()
    if 'cart' in which: cartridge()
