/* ---------- consoles: stylized after each real console's shape and proportions, no logos ---------- */
// Front faces +z and the top faces +y. `slot` is where the media goes in, for the launch animation.
const consoleMeshes = {};
const BLENDER_SLOTS = { gba: { kind: 'hand', x: 0, y: .41, z: -.046 } };   // where the media goes in each Blender-built console
function consoleModel(sys) {
  if (consoleMeshes[sys]) return consoleMeshes[sys];
  const bm = BLENDER_SLOTS[sys] && blenderModel(sys + '_console', { slot: BLENDER_SLOTS[sys] });
  if (bm) return (consoleMeshes[sys] = bm);
  const B = Builder(), at = (x, y, z, rx = 0, ry = 0, rz = 0) => M4.trs(x, y, z, rx, ry, rz, 1);
  const on = (g, M, m) => B.add(g, M, m);
  const face = (g, x, y, z, m, rz = 0) => on(g, at(x, y, z, 0, 0, rz), m);                  // on a front face
  const up = (g, x, y, z, m, ry = 0) => on(g, at(x, y, z, -Math.PI / 2, ry, 0), m);         // on a top face (local +y points to the back)
  const scr = (w, h) => planeGeo(w, h), SCREEN = { col: '#0c0f14', spec: .18, gloss: .55, screen: true };   // not too shiny: a flat screen facing the light would turn white
  const led = col => ({ col, led: true, spec: .4 });
  const dpad = (x, y, z, s, m) => { face(discG(s * .62, .004), x, y, z + .001, shellM(shadeHex(m.col, .15), { spec: .1 })); face(crossG(s, s * .33, .035), x, y, z + .016, m); };
  const grille = (x, y, z, cols, rows, gap, m) => { for (let i = 0; i < cols; i++) for (let j = 0; j < rows; j++) face(discG(.011, .002), x + (i - (cols - 1) / 2) * gap, y + (j - (rows - 1) / 2) * gap, z + .0005, m); };
  let slot, extra = {};
  switch (sys) {
    case 'nes': {   // a wide, flat deck with a darker band along the front and a door on top of it
      const S0 = shellM('#bdb9b2'), D0 = shellM('#4b4946'), z = 1.015;
      on(slabG(rrect(2.56, 2.03, .05), .9, .035), IDENT, S0);
      face(barG(2.42, .36, .03, .02, .008), 0, -.235, z + .012, D0);
      face(barG(1.62, .32, .02, .015, .006), 0, .17, z + .006, shellM('#aaa69f'));
      face(planeGeo(1.5, .012), 0, .31, z + .017, DARK);
      for (let i = 0; i < 9; i++) up(barG(2.3, .032, .014, .01, .004), 0, .452, -.15 - i * .085, shellM('#a9a59e'));
      [-.95, -.62].forEach(x => { face(barG(.24, .15, .006, .03), x, -.235, z + .027, DARK); face(planeGeo(.15, .05), x, -.235, z + .031, shellM('#6f6c68')); });
      [.55, .86].forEach(x => face(barG(.24, .1, .035, .015, .006), x, -.235, z + .04, shellM('#d0ccc5')));
      face(barG(.05, .03, .008, .006), .32, -.235, z + .028, led('#ff3b3b'));
      slot = { kind: 'front', x: 0, y: .17, z: z + .02 }; break;
    }
    case 'snes': {  // rounded corners, a raised middle section with the slot, two sliders and an eject button
      const S0 = shellM('#cac8d1'), D0 = shellM('#77757f');
      on(slabG(rrect(2.0, 2.42, .34, 8), .56, .11), at(0, -.08, 0), S0);
      on(slabG(rrect(1.28, 1.5, .24, 8), .2, .08), at(0, .26, -.18), shellM('#c0bec8'));
      up(barG(1.0, .17, .01, .02), 0, .36, -.18, DARK);
      up(barG(1.06, .022, .01, .005), 0, .362, -.06, D0); up(barG(1.06, .022, .01, .005), 0, .362, -.3, D0);
      [-.8, .8].forEach(x => { up(barG(.3, .14, .02, .06), x, .21, .45, D0); up(barG(.13, .12, .05, .04), x - .06, .235, .45, shellM('#9a98a4')); });
      up(barG(.32, .17, .05, .06, .015), 0, .21, .7, D0);
      up(discG(.022, .006), -.3, .201, .86, led('#ff4a4a'));
      [-.36, .36].forEach(x => { face(barG(.3, .17, .008, .05), x, -.16, 1.21, DARK); face(planeGeo(.2, .06), x, -.16, 1.215, shellM('#5e5c66')); });
      slot = { kind: 'top', x: 0, y: .36, z: -.18 }; break;
    }
    case 'n64': {   // a broad body with a rounded front, a raised center with the slot, and four controller ports
      const S0 = shellM('#3a3d45', { spec: .35 }), D0 = shellM('#2a2c32');
      on(slabG(rrect(2.6, 1.9, [.18, .18, .5, .5], 8), .5, .09), at(0, -.115, 0), S0);
      on(slabG(rrect(1.36, 1.15, .32, 8), .22, .1), at(0, .2, -.05), S0);
      up(barG(1.12, .14, .01, .02), 0, .312, -.08, DARK);
      up(barG(1.18, .03, .012, .01), 0, .314, .03, D0);
      up(barG(.62, .26, .012, .03), 0, .137, .74, D0);
      [-.85, .85].forEach(x => { up(barG(.3, .12, .02, .05), x, .14, .6, D0); up(barG(.12, .1, .05, .03), x + .06, .16, .6, shellM('#55585f')); });
      up(discG(.022, .006), 0, .136, .9, led('#ff3b3b'));
      [-.75, -.25, .25, .75].forEach(x => { face(barG(.24, .17, .008, .03), x, -.17, .955, DARK); face(planeGeo(.15, .07), x, -.17, .96, shellM('#55585f')); });
      slot = { kind: 'top', x: 0, y: .31, z: -.08 }; break;
    }
    case 'genesis': {   // a black wedge with a big raised circle around the slot
      const S0 = shellM('#1b1c21', { spec: .6, gloss: .25 }), D0 = shellM('#111216', { spec: .4 });
      on(slabG(rrect(2.8, 2.12, .14, 8), .52, .07), at(0, -.06, 0), S0);
      up(latheGeo([[0, .05], [.74, .05], [.8, .02], [.82, 0], [0, 0]], 64), -.2, .2, -.05, S0);
      [.46, .58, .7].forEach(r => up(ringG(r, r + .014, .004, 64), -.2, .252, -.05, D0));
      up(barG(1.02, .2, .01, .02), -.2, .254, -.05, DARK);
      up(barG(1.12, .26, .006, .03), -.2, .252, -.05, shellM('#2a2b31'));
      up(barG(.3, .12, .02, .05), -1.0, .2, .82, D0); up(barG(.1, .1, .05, .03), -1.05, .22, .82, shellM('#3a3b42'));
      up(domeG(.08, .03), 1.02, .2, .8, shellM('#2c2d34'));
      up(discG(.02, .006), -.72, .201, .86, led('#ff3030'));
      face(barG(.22, .05, .02, .02), -1.05, -.05, 1.06, D0); face(barG(.04, .08, .03, .015), -1.12, -.05, 1.08, shellM('#3a3b42'));
      [-.45, -.12].forEach(x => face(barG(.22, .13, .008, .03), x, -.13, 1.062, DARK));
      slot = { kind: 'top', x: -.2, y: .26, z: -.05 }; break;
    }
    case 'gb': {   // tall and gray, with the rounded lower corner, a framed screen and slanted speaker slots
      const S0 = shellM('#c9c8ce'), z = .16, B0 = shellM('#2a2a31', { spec: .4 });
      on(solidGeo(rounded([[-.45, -.74, .04], [.45, -.74, .3], [.45, .74, .03], [-.45, .74, .03]]), .32, .04), IDENT, S0);
      face(solidGeo(rounded([[-.39, .04, .02], [.39, .04, .13], [.39, .66, .02], [-.39, .66, .02]]), .006, .002, 2), 0, 0, z + .002, shellM('#585b6c', { spec: .3 }));
      face(scr(.47, .43), 0, .345, z + .0055, { col: '#7f8e5c', spec: .6, gloss: .5 });
      face(planeGeo(.66, .008), 0, .628, z + .0055, { col: '#8b2c51', spec: .2 }); face(planeGeo(.66, .008), 0, .614, z + .0055, { col: '#3b3e90', spec: .2 });
      face(discG(.016, .004), -.33, .43, z + .006, led('#ff2a2a'));
      dpad(-.21, -.2, z, .27, B0);
      [[.3, -.15], [.13, -.23]].forEach(([x, y]) => face(domeG(.066, .03), x, y, z, shellM('#9c2a5c', { spec: .5 })));
      [-.06, .1].forEach(x => face(barG(.13, .036, .02, .017), x, -.46, z + .008, shellM('#8d8c97'), .45));
      for (let i = 0; i < 6; i++) { const k = (i - 2.5) * .04; face(barG(.022, .15, .006, .011), .27 + k * Math.cos(.5), -.54 + k * Math.sin(.5), z + .001, shellM('#9a99a2'), .5); }   // speaker slots, kept inside the rounded corner
      up(barG(.18, .05, .03, .012), -.22, .74, .02, shellM('#9a99a2'));
      up(barG(.62, .12, .004, .01), 0, .741, -.08, DARK);
      slot = { kind: 'hand', x: 0, y: .74, z: -.08 }; break;
    }
    case 'gbc': {   // smaller, see-through color, the screen centered high
      const S0 = shellM('#2fb5a9', { spec: .55, gloss: .3 }), z = .135, B0 = shellM('#26323a', { spec: .4 });
      on(solidGeo(rrect(.78, 1.33, [.13, .13, .05, .05], 8), .27, .04), IDENT, S0);
      face(solidGeo(rrect(.66, .56, .05, 6), .006, .002, 2), 0, .31, z + .002, shellM('#2a2c33', { spec: .4 }));
      face(scr(.45, .41), 0, .31, z + .0055, SCREEN);
      face(discG(.014, .004), -.29, .38, z + .006, led('#ff3a3a'));
      dpad(-.2, -.22, z, .24, B0);
      [[.25, -.17], [.1, -.25]].forEach(([x, y]) => face(domeG(.058, .028), x, y, z, shellM('#4a3f93', { spec: .5 })));
      [-.07, .08].forEach(x => face(barG(.11, .032, .018, .015), x, -.45, z + .007, shellM('#1f2a30')));
      grille(.24, -.5, z, 3, 2, .04, DARK);
      up(barG(.5, .1, .004, .01), 0, .666, -.07, DARK);
      slot = { kind: 'hand', x: 0, y: .665, z: -.07 }; break;
    }
    case 'gba': {   // wide, with firm corners, the screen between the controls, shoulder buttons along the top edge
      const S0 = shellM('#4e46a8', { spec: .45 }), z = .1225, B0 = shellM('#c5c4d2', { spec: .5 });
      on(solidGeo(rrect(1.445, .82, [.2, .2, .1, .1], 8), .245, .03), IDENT, S0);
      face(solidGeo(rrect(.84, .6, .06, 6), .006, .002, 2), 0, .03, z + .002, shellM('#18181f', { spec: .5 }));
      face(scr(.62, .415), 0, .045, z + .0055, SCREEN);
      dpad(-.53, .07, z, .2, shellM('#3b3488'));
      [[.6, .13], [.46, .03]].forEach(([x, y]) => face(domeG(.055, .028), x, y, z, B0));
      [[-.53, -.21], [-.53, -.29]].forEach(([x, y]) => face(domeG(.024, .014), x, y, z, B0));
      grille(.52, -.24, z, 3, 3, .035, DARK);
      face(discG(.012, .004), -.36, -.26, z + .005, led('#5bff8a'));
      [-1, 1].forEach(s => on(slabG(rrect(.26, .11, .025), .035, .012), at(s * .47, .41 + .0165, -.06), shellM('#3b3488')));
      up(barG(.62, .1, .004, .01), 0, .411, -.05, DARK);
      slot = { kind: 'hand', x: 0, y: .41, z: -.05 }; break;
    }
    case 'ps1': {   // a low gray box with a round lid on top and three round buttons
      const S0 = shellM('#c6c4bf'), D0 = shellM('#a9a7a1');
      on(slabG(rrect(2.7, 1.88, .08), .6, .05), IDENT, S0);
      up(ringG(.722, .745, .004, 72), -.25, .3, -.05, shellM('#8e8c86'));
      up(discG(.7, .01, 64), -.25, .296, -.05, shellM('#2a2a2e', { spec: .2 }));   // the well under the lid, with its spindle
      up(latheGeo([[0, .03], [.05, .03], [.06, 0], [0, 0]], 24), -.25, .3, -.05, shellM('#d9dbe0', { spec: .7 }));
      up(domeG(.15, .035), .92, .3, -.36, D0);
      [[.72, .42], [1.06, .42]].forEach(([x, z]) => up(domeG(.09, .03), x, .3, z, D0));
      up(discG(.02, .006), .89, .301, .2, led('#3dff6a'));
      [-.6, -.2].forEach(x => { face(barG(.3, .12, .008, .03), x, -.15, .94, DARK); face(barG(.28, .05, .008, .01), x, .05, .94, DARK); });
      slot = { kind: 'lid', x: -.25, y: .32, z: -.05 };
      const L = Builder(); L.add(latheGeo([[0, .035], [.66, .035], [.71, .015], [.72, 0], [0, 0]], 72), at(0, 0, .72, -Math.PI / 2), shellM('#cbc9c4'));
      extra.lid = { model: L.build(), hinge: [-.25, .3, -.77], closed: 0, open: -1.7, rest: 0 }; break;
    }
    case 'ds': {    // the original two-screen clamshell, shown open
      const S0 = shellM('#c4c7cf', { spec: .5, gloss: .2 }), P0 = shellM('#2c2e35', { spec: .3 }), K = shellM('#d3d5dc', { spec: .5 });
      on(slabG(rrect(1.49, .85, .12, 8), .14, .03), IDENT, S0);
      up(barG(1.42, .78, .004, .1), 0, .071, 0, P0);
      up(scr(.62, .46), 0, .074, 0, SCREEN);
      up(crossG(.17, .055, .03), -.52, .071, -.03, shellM('#3a3c44'));
      [[.52, -.11], [.52, .05], [.44, -.03], [.6, -.03]].forEach(([x, z]) => up(domeG(.033, .02), x, .071, z, K));
      [[.48, .26], [.56, .26]].forEach(([x, z]) => up(domeG(.018, .01), x, .071, z, K));
      on(discG(.06, 1.3, 24), at(0, .135, -.43, 0, Math.PI / 2, 0), S0);
      on(planeGeo(.39, .05), at(0, 0, -.4255, 0, Math.PI, 0), DARK);   // the card slot in the back edge
      // the top half folds on the hinge: built standing up, closed it lies on the bottom half
      const L = Builder(), T = at(0, .44, 0);
      L.add(solidGeo(rrect(1.49, .85, .12, 8), .13, .03), T, S0);
      L.add(barG(1.42, .78, .004, .1), M4.mul(T, at(0, 0, .066)), P0);
      L.add(scr(.62, .46), M4.mul(T, at(0, 0, .069)), SCREEN);
      [-1, 1].forEach(s => { for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) L.add(discG(.01, .002), M4.mul(T, at(s * .55 + (i - 1) * .03, .05 + (j - 1) * .03, .069)), DARK); });
      extra.lid = { model: L.build(), hinge: [0, .135, -.43], closed: Math.PI / 2, open: -.35, rest: 1 };
      extra.bounds = [[-.75, -.07, -.75], [.75, .95, .43]];
      slot = { kind: 'edge', x: 0, y: 0, z: -.425 }; break;
    }
    case 'psp': {   // a glossy black oval with a wide screen in the middle
      const S0 = shellM('#16171b', { spec: .8, gloss: .55 }), z = .115, K = shellM('#2c2d33', { spec: .5 });
      on(solidGeo(rrect(1.7, .74, .33, 10), .23, .05), IDENT, S0);
      face(scr(.96, .545), 0, .02, z + .001, SCREEN);
      [[-.075, 0, Math.PI / 2], [.075, 0, -Math.PI / 2], [0, .075, Math.PI], [0, -.075, 0]].forEach(([dx, dy, r]) => face(triG(.04, .02), -.64 + dx, .12 + dy, z + .008, K, r));
      face(latheGeo([[0, .035], [.045, .033], [.06, .02], [.062, 0], [0, 0]], 24), -.64, -.17, z, shellM('#5e6068'));
      [[.085, 0], [-.085, 0], [0, .085], [0, -.085]].forEach(([dx, dy]) => face(domeG(.042, .022), .64 + dx, .12 + dy, z, K));
      for (let i = 0; i < 6; i++) face(barG(.07, .024, .012, .012), -.3 + i * .12, -.31, z + .004, K);
      [-1, 1].forEach(s => on(solidGeo(rrect(.4, .05, .02), .12, .02), at(s * .55, .37, -.02, 0, 0, -s * .1), K));
      // the UMD sits on the back under a raised door, hinged along its top edge, that swings up past the hinge to open
      on(planeGeo(.66, .6), at(0, 0, -z - .0008, 0, Math.PI, 0), DARK);
      const L = Builder(); L.add(solidGeo(rrect(.7, .64, [.06, .06, .24, .24], 8), .056, .02), at(0, -.32, -.029), shellM('#1f2026', { spec: .6, gloss: .3 }));
      L.add(barG(.2, .03, .004, .01), at(0, -.58, -.058), shellM('#3a3b42'));
      extra.lid = { model: L.build(), hinge: [0, .32, -z], closed: 0, open: 1.95, rest: 0 };
      slot = { kind: 'back', x: 0, y: 0, z: -z, outside: 1.04 }; break;   // it sits on the back, under the raised door
    }
    default: {      // Android games: a generic modern handheld
      const S0 = shellM('#2b2e3a', { spec: .45 }), z = .1, K = shellM('#d9dbe3', { spec: .5 });
      on(solidGeo(rrect(1.9, .82, .14, 8), .2, .04), IDENT, S0);
      face(solidGeo(rrect(1.14, .68, .05, 6), .005, .002, 2), 0, .02, z + .001, shellM('#0e1015', { spec: .5 }));
      face(scr(1.06, .6), 0, .02, z + .0045, SCREEN);
      dpad(-.72, .13, z, .2, shellM('#3b3f4d'));
      face(latheGeo([[0, .04], [.05, .038], [.07, .025], [.075, 0], [0, 0]], 24), -.72, -.2, z, shellM('#4a4e5c'));
      face(ringG(.08, .1, .004, 32), -.72, -.2, z + .001, DARK);
      [[.085, 0], [-.085, 0], [0, .085], [0, -.085]].forEach(([dx, dy]) => face(domeG(.04, .02), .72 + dx, .13 + dy, z, K));
      face(latheGeo([[0, .04], [.05, .038], [.07, .025], [.075, 0], [0, 0]], 24), .72, -.2, z, shellM('#4a4e5c'));
      [-1, 1].forEach(s => on(solidGeo(rrect(.42, .05, .02), .14, .02), at(s * .62, .41, -.02), shellM('#3b3f4d')));
      up(barG(.5, .06, .004, .01), 0, .411, -.04, DARK);
      slot = { kind: 'hand', x: 0, y: .41, z: -.04 };
    }
  }
  const built = B.build(Object.assign({ slot }, extra));
  if (extra.bounds) [built.lo, built.hi] = extra.bounds;   // parts on hinges aren't in the measured size
  return (consoleMeshes[sys] = built);
}
function drawConsole(sys, M, st) {
  const m = consoleModel(sys); drawParts(m, M, st);
  if (m.lid) {   // a part on a hinge: the PlayStation's lid, the DS's top half, the PSP's disc door
    const L = m.lid, [hx, hy, hz] = L.hinge, v = st.lid == null ? L.rest : st.lid;
    drawParts(L.model, M4.mul(M, M4.trs(hx, hy, hz, lerp(L.closed, L.open, v), 0, 0, 1)), st);
  }
}
