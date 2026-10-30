// Consoles for the studio: Shelf's own code-built consoles (prototype-source/android/r4c.js), in Shelf's units
// (1 = 10 cm). Silhouettes and proportions follow the real consoles; details stay stylized, with no logos (see CLAUDE.md,
// "Hard constraints"). Front faces +z, the top faces +y.
//
// finish   starting values for the tuning page (the main shell; other plastic parts share its roughness and clearcoat).
//          Light shells are a little darker than the real plastic: under a bright studio they read true.
// lid      consoles with a part on a hinge: where it rests (0 closed, 1 open).
// build    the parts, through the kit in console.js: on/face/up place a shape; shapes are solid, slabY, bar, disc, ring,
//          dome, cross, tri, lathe, plane; materials are M.shell, M.plastic(color), M.dark, M.screen, M.lcd, M.led, M.metal.

const plastic = (color, roughness = .45, clearcoat = .35) => ({ color, roughness, clearcoat, clearcoatRoughness: .75 });

// The parts a console's colors can change, by role (see console.js), in the order the app lists them.
export const PART_NAMES = { shell: 'Shell', lower: 'Lower half', band: 'Front band', door: 'Door', lid: 'Lid', bezel: 'Screen bezel',
  dpad: 'D-pad', buttons: 'Buttons', small: 'Start and Select', shoulders: 'Shoulder buttons', stick: 'Stick', switches: 'Switches' };

// Each console's real color editions (North American unless noted), as colors per part; parts left out keep the
// model's own. A [color, finish] pair gives a part a finish from the app's Studio (the translucent editions are clear,
// or smoke for the smoky black, so their boards show through). From the manufacturers' release histories;
// see docs/RESEARCH.md. Names describe the color, never a brand.
export const CONSOLE_COLORS = {
  nes: [['Standard gray', 'The NES (1985)', {}],
    ['Red and white', 'The Famicom’s colors (Japan, 1983)', { shell: '#e8e2d6', band: '#9c2a33', door: '#e8e2d6', buttons: '#9c2a33' }]],
  snes: [['Standard gray', 'North America (1991): two grays, purple switches', {}],
    ['Light gray', 'Super Famicom and Europe (1990–92): pale gray, gray switches', { shell: '#d6d4d8', lower: '#cfcdd2', switches: '#9c9aa3' }]],
  n64: [['Charcoal', 'The original (1996)', {}],
    ['Jungle green', 'Funtastic series (1999), translucent', { shell: ['#2f8f55', 'clear'] }],
    ['Ice blue', 'Funtastic series (1999), translucent', { shell: ['#86bfe6', 'clear'] }],
    ['Grape purple', 'Funtastic series (1999), translucent', { shell: ['#6e4fa3', 'clear'] }],
    ['Fire orange', 'Funtastic series (1999), translucent', { shell: ['#f07a24', 'clear'] }],
    ['Watermelon red', 'Funtastic series (1999), translucent', { shell: ['#e1454f', 'clear'] }],
    ['Smoke black', 'Funtastic series (1999), translucent', { shell: ['#3a3c44', 'smoke'] }],
    ['Gold', 'A limited gold edition (1999)', { shell: ['#c9a24a', 'metal'] }]],
  genesis: [['Black', 'Every model was black (1989)', {}]],
  gb: [['Gray', 'The original (1989)', {}],
    ['Red', 'Play It Loud series (1995)', { shell: '#d22a32' }], ['Yellow', 'Play It Loud series (1995)', { shell: '#f1cf34' }],
    ['Green', 'Play It Loud series (1995)', { shell: '#3a9f52' }], ['Blue', 'Play It Loud series (1995)', { shell: '#2e5ec2' }],
    ['Black', 'Play It Loud series (1995)', { shell: '#232328' }], ['White', 'Play It Loud series (1995)', { shell: '#ecebe8' }],
    ['Clear', 'Play It Loud series (1995), see-through', { shell: ['#d9e3ea', 'clear'] }]],
  gbc: [['Teal', 'Launch colors (1998)', {}], ['Berry', 'Launch colors (1998)', { shell: '#cf2d63' }],
    ['Grape', 'Launch colors (1998)', { shell: '#6a3fa0' }], ['Kiwi', 'Launch colors (1998)', { shell: '#a6c93c' }],
    ['Dandelion', 'Launch colors (1998)', { shell: '#f2c230' }], ['Atomic purple', 'Launch colors (1998), translucent', { shell: ['#6a4c9c', 'clear'] }]],
  gba: [['Indigo', 'Launch color (2001)', {}], ['Arctic', 'Launch color (2001)', { shell: '#e8e9ee', dpad: '#c9cbd4', shoulders: '#c9cbd4' }],
    ['Black', '2002', { shell: '#1d1d22', dpad: '#3a3a42', shoulders: '#3a3a42' }], ['Fuchsia', 'Launch color (2001)', { shell: '#d93b8f', dpad: '#a8256a', shoulders: '#a8256a' }],
    ['Glacier', 'Launch color (2001), translucent', { shell: ['#8ec5e6', 'clear'], dpad: '#5f95bf', shoulders: '#5f95bf' }],
    ['Platinum', '2002', { shell: ['#c8cbd1', 'metal'], dpad: '#8f939b', shoulders: '#8f939b' }], ['Orange', 'Japan (2001)', { shell: '#f08a2a' }]],
  ps1: [['Gray', 'The original (1995)', {}], ['White', 'The later slim model’s white (2000)', { shell: '#e9e9e6', lid: '#f0f0ee', buttons: '#dcdcd8' }]],
  ds: [['Platinum', 'Launch color (2004)', {}], ['Graphite black', '2005', { shell: '#2a2b30', buttons: '#4a4c54' }],
    ['Electric blue', '2005', { shell: '#2f6fd0' }], ['Titanium', '2005: dark silver', { shell: ['#5d6068', 'metal'] }],
    ['Candy pink', '2005', { shell: '#f2a6c4' }], ['Red', '2005', { shell: '#c4182c' }]],
  psp: [['Piano black', 'Launch color (2005)', {}], ['Ceramic white', '2005', { shell: '#eeeeeb', buttons: '#dcdcd8', shoulders: '#dcdcd8', bezel: '#eeeeeb' }],
    ['Ice silver', '2006', { shell: ['#c7cad0', 'metal'] }], ['Rose pink', '2006', { shell: '#f0b3c5' }],
    ['Felicia blue', '2007', { shell: '#4d6db2' }], ['Radiant red', '2008', { shell: '#c01d2a' }]],
  android: [['Midnight', 'Today’s handhelds', {}], ['Snow', 'Today’s handhelds', { shell: '#eceef2', buttons: '#c9ccd6' }],
    ['Indigo', 'Today’s handhelds', { shell: '#3d3fa8' }], ['Retro', 'Gray with purple buttons, a nod to the 16-bit era', { shell: '#c3c3c8', buttons: '#6c5ea6', dpad: '#4a4a52' }]],
};

// insert: where its game sits once it's in (the start animation), in the console's millimetres: the cartridge's centre
// (at), how it's turned (rot: x, y, z in radians, turned about x first, then y), the straight path it slides in along
// (from: where that path starts, from the seated spot), and turn: how far the console turns round to take it.
export const CONSOLES = {
  nes: {
    // flat through the front door, label up, then pressed down into the connector as on the real one (press: where it
    // goes in, above its seat)
    insert: { at: [0, 6, 32], rot: [-Math.PI / 2, Math.PI, 0], from: [0, 0, 150], press: [0, 11, 0] },
    name: 'NES', finish: plastic('#a7a39d', .5, .25), seam: { axis: 'y', at: -.356 },
    build(k) {   // a wide, flat deck with a darker band along the front and a door on top of it
      const { M } = k, D0 = M.plastic('#4b4946', 'band'), z = 1.015;
      k.bodyY(k.rrect(2.56, 2.03, .05), .9, .035, -.356, k.at(0, 0, 0), M.shell);   // a low base and the top half, meeting just under the board
      k.face(k.bar(2.42, .36, .03, .02, .008), 0, -.235, z + .012, D0);
      // the door: hinged along its top edge, it swings in as the cartridge pushes past it and drops shut behind it; the
      // dark opening shows behind it while it's open
      k.face(k.bar(1.56, .28, .004, .012, .002), 0, .17, z + .002, M.dark);
      k.part('door', { at: [0, .33, z + .005], hinge: [0, 1.45] }, D => {
        D.face(D.bar(1.62, .32, .014, .015, .005), 0, -.16, .007, M.plastic('#aaa69f', 'door'));
        D.face(D.plane(1.5, .012), 0, -.02, .0145, M.dark);
      });
      for (let i = 0; i < 9; i++) k.up(k.bar(2.3, .032, .014, .01, .004), 0, .452, -.15 - i * .085, M.plastic('#a9a59e'));
      [-.95, -.62].forEach(x => { k.face(k.bar(.24, .15, .006, .03), x, -.235, z + .027, M.dark); k.face(k.plane(.15, .05), x, -.235, z + .031, M.plastic('#6f6c68', 'band')); });
      k.part('power', { at: [.55, -.235, z + .04], slide: [0, 0, -.022] }, P => P.face(P.bar(.24, .1, .035, .015, .006), 0, 0, 0, M.plastic('#d0ccc5', 'buttons')));   // pressed in to turn it on
      k.face(k.bar(.24, .1, .035, .015, .006), .86, -.235, z + .04, M.plastic('#d0ccc5', 'buttons'));
      k.face(k.bar(.05, .03, .008, .006), .32, -.235, z + .028, M.led('#ff3b3b'));
    },
  },
  snes: {
    insert: { at: [0, 28.1, -56], rot: [0, 0, 0], from: [0, 110, 0] },   // down into the slot on the raised block
    name: 'Super Nintendo', finish: plastic('#c3c1ca', .45, .3), seam: { axis: 'y', at: -.05 },   // the two-tone line is its seam; the board sits on the lower half
    build(k) {   // the North American console: boxy and two-tone, a raised block around the slot, purple switches
      const { M } = k, LO = M.plastic('#76747f', 'lower'), P = M.plastic('#6c5ea6', 'switches');
      k.cupY(k.rrect(2.0, 2.45, .05), -.35, -.05, .03, LO, 'lower');                                   // the darker lower half, hollow
      k.cupY(k.rrect(1.98, 2.43, .05), .12, -.05, .04, M.shell, 'upper');                               // the lighter upper half
      k.on(k.slabY(k.rrect(1.44, 1.2, .06), .14, .05), k.at(0, .17, -.5), M.shell);                    // the raised block with the slot
      k.up(k.bar(1.0, .17, .01, .02), 0, .241, -.56, M.dark);                                          // the cartridge slot
      k.up(k.bar(1.1, .3, .006, .03), 0, .2405, -.56, M.plastic('#b3b1ba'));                           // its dust flap surround
      [-.62, .62].forEach(x => k.up(k.bar(.14, .9, .004, .02), x, .2405, -.5, M.plastic('#b3b1ba')));   // grooves along the block
      k.up(k.bar(.34, .12, .05, .05, .015), 0, .13, .2, P);                                            // the eject lever, in front of the block
      [-.62, .62].forEach(x => k.up(k.bar(.34, .18, .012, .04), x, .121, .38, M.dark));   // power and reset
      k.part('power', { at: [-.69, .14, .38], slide: [.14, 0, 0] }, S => S.up(S.bar(.15, .15, .06, .04, .02), 0, 0, 0, P));
      k.up(k.bar(.15, .15, .06, .04, .02), .55, .14, .38, P);
      k.up(k.disc(.02, .006), -.3, .121, .52, M.led('#ff4a4a'));
      [-.36, .36].forEach(x => { k.face(k.bar(.3, .17, .008, .05), x, -.2, 1.225, M.dark); k.face(k.plane(.2, .06), x, -.2, 1.23, M.plastic('#5e5c66')); });
      for (let i = 0; i < 3; i++) k.face(k.bar(1.2, .012, .004, .005), 0, .055 - i * .035, 1.2165, M.plastic('#a9a7b1'));   // lines across the front of the upper half
    },
  },
  n64: {
    insert: { at: [0, 29.2, -8], rot: [0, 0, 0], from: [0, 110, 0] },
    name: 'Nintendo 64', finish: plastic('#3a3d45', .42, .4), seam: { axis: 'y', at: -.266 },
    build(k) {   // a broad body with a rounded front, a raised center with the slot, and four controller ports
      const { M } = k, D0 = M.plastic('#2a2c32');
      k.bodyY(k.rrect(2.6, 1.9, [.18, .18, .5, .5], 8), .5, .09, -.151, k.at(0, -.115, 0), M.shell);
      k.on(k.slabY(k.rrect(1.36, 1.15, .32, 8), .22, .1), k.at(0, .2, -.05), M.shell);
      k.up(k.bar(1.12, .14, .01, .02), 0, .312, -.08, M.dark);
      k.up(k.bar(1.18, .03, .012, .01), 0, .314, .03, D0);
      k.up(k.bar(.62, .26, .012, .03), 0, .137, .74, D0);
      [-.85, .85].forEach(x => k.up(k.bar(.3, .12, .02, .05), x, .14, .6, D0));
      k.part('power', { at: [-.79, .16, .6], slide: [-.12, 0, 0] }, S => S.up(S.bar(.12, .1, .05, .03), 0, 0, 0, M.plastic('#55585f', 'switches')));
      k.up(k.bar(.12, .1, .05, .03), .91, .16, .6, M.plastic('#55585f', 'switches'));
      k.up(k.disc(.022, .006), 0, .137, .8, M.led('#ff3b3b'));   // clear of the rounded front edge
      [-.75, -.25, .25, .75].forEach(x => { k.face(k.bar(.24, .17, .008, .03), x, -.17, .955, M.dark); k.face(k.plane(.15, .07), x, -.17, .96, M.plastic('#55585f')); });
    },
  },
  genesis: {
    insert: { at: [-20, 35.4, -5], rot: [0, 0, 0], from: [0, 140, 0] },
    name: 'Sega Genesis', finish: plastic('#1b1c21', .32, .6), seam: { axis: 'y', at: -.241 },
    build(k) {   // a black wedge with a big raised circle around the slot
      const { M } = k, D0 = M.plastic('#111216');
      k.bodyY(k.rrect(2.8, 2.12, .14, 8), .52, .07, -.181, k.at(0, -.06, 0), M.shell);
      k.up(k.lathe([[0, .05], [.74, .05], [.8, .02], [.82, 0], [0, 0]], 64), -.2, .2, -.05, M.shell);
      [.46, .58, .7].forEach(r => k.up(k.ring(r, r + .014, .004, 64), -.2, .252, -.05, D0));
      k.up(k.bar(1.02, .2, .01, .02), -.2, .254, -.05, M.dark);
      k.up(k.bar(1.12, .26, .006, .03), -.2, .252, -.05, M.plastic('#2a2b31'));
      k.up(k.bar(.3, .12, .02, .05), -1.0, .2, .82, D0);
      k.part('power', { at: [-1.05, .22, .82], slide: [.1, 0, 0] }, S => S.up(S.bar(.1, .1, .05, .03), 0, 0, 0, M.plastic('#3a3b42', 'switches')));
      k.up(k.dome(.08, .03), 1.02, .2, .8, M.plastic('#2c2d34', 'switches'));
      k.up(k.disc(.02, .006), -.72, .201, .86, M.led('#ff3030'));
      k.face(k.bar(.22, .05, .02, .02), -1.05, -.05, 1.06, D0); k.face(k.bar(.04, .08, .03, .015), -1.12, -.05, 1.08, M.plastic('#3a3b42'));
      [-.45, -.12].forEach(x => k.face(k.bar(.22, .13, .008, .03), x, -.13, 1.062, M.dark));
    },
  },
  gb: {
    insert: { at: [0, 41.5, -11], rot: [0, Math.PI, 0], from: [0, 76, 0], turn: Math.PI },   // down into the pocket in the back, label out
    name: 'Game Boy', finish: plastic('#b9b8bf', .48, .3), seam: { axis: 'z', at: -.07 },
    build(k) {   // tall and gray, with the rounded lower corner, a framed screen and slanted speaker slots
      const { M } = k, z = .16, B0 = M.plastic('#2a2a31', 'dpad'), K = M.plastic('#9a99a2');
      // front and back halves; the back's top is open where the cartridge slides down into it, its connector's mouth on the floor
      const out = k.rounded([[-.45, -.74, .04], [.45, -.74, .3], [.45, .74, .03], [-.45, .74, .03]]);
      k.body(out, .32, .04, -.07, k.at(0, 0, 0), M.shell, k.notch(out, .295, .44));   // the top 30 mm of the cartridge shows; the rest is behind the back
      k.up(k.bar(.56, .075, .003, .005), 0, .4415, -.115, M.dark);
      k.half('lower', () => k.face(k.bar(.59, .295, .015, .004, .002), 0, .5925, -.0775, M.shell));   // the pocket's floor
      k.window(k.rounded([[-.39, .04, .02], [.39, .04, .13], [.39, .66, .02], [-.39, .66, .02]]), k.at(0, 0, z), .009, .47, .43, 0, .345, M.plastic('#585b6c', 'bezel'), M.picture('#7f8e5c', .55));
      k.face(k.plane(.66, .008), 0, .628, z + .0095, M.lcd('#8b2c51')); k.face(k.plane(.66, .008), 0, .614, z + .0095, M.lcd('#3b3e90'));
      k.face(k.disc(.016, .004), -.33, .43, z + .01, M.led('#ff2a2a'));
      k.dpad(-.21, -.2, z, .27, B0);
      [[.3, -.15], [.13, -.23]].forEach(([x, y]) => k.face(k.dome(.066, .03), x, y, z, M.plastic('#9c2a5c', 'buttons')));
      [-.06, .1].forEach(x => k.face(k.bar(.13, .036, .02, .017), x, -.46, z + .008, M.plastic('#8d8c97', 'small'), .45));
      for (let i = 0; i < 6; i++) { const d = (i - 2.5) * .04; k.face(k.bar(.022, .15, .006, .011), .27 + d * Math.cos(.5), -.54 + d * Math.sin(.5), z + .001, K, .5); }
      k.part('power', { at: [-.22, .74, .02], slide: [.09, 0, 0] }, S => S.up(S.bar(.18, .05, .03, .012), 0, 0, 0, K));   // the power switch on top, sliding toward the cartridge
    },
  },
  gbc: {
    insert: { at: [0, 34, -8.5], rot: [0, Math.PI, 0], from: [0, 76, 0], turn: Math.PI },
    name: 'Game Boy Color', finish: plastic('#2fb5a9', .35, .55), seam: { axis: 'z', at: -.045 },
    build(k) {   // smaller, the screen centered high
      const { M } = k, z = .135, B0 = M.plastic('#26323a', 'dpad');
      const out = k.rrect(.78, 1.33, [.13, .13, .05, .05], 8);
      k.body(out, .27, .04, -.045, k.at(0, 0, 0), M.shell, k.notch(out, .295, .365));   // the cartridge pocket at the top of the back: its top 30 mm shows
      k.up(k.bar(.56, .075, .003, .005), 0, .3665, -.09, M.dark);
      k.half('lower', () => k.face(k.bar(.59, .295, .015, .004, .002), 0, .5125, -.0525, M.shell));
      k.window(k.rrect(.66, .56, .05, 6), k.at(0, .31, z), .009, .45, .41, 0, 0, M.plastic('#2a2c33', 'bezel'), M.picture('#0c0f14'));
      k.face(k.disc(.014, .004), -.29, .38, z + .01, M.led('#ff3a3a'));
      k.dpad(-.2, -.22, z, .24, B0);
      [[.25, -.17], [.1, -.25]].forEach(([x, y]) => k.face(k.dome(.058, .028), x, y, z, M.plastic('#4a3f93', 'buttons')));
      [-.07, .08].forEach(x => k.face(k.bar(.11, .032, .018, .015), x, -.45, z + .007, M.plastic('#1f2a30', 'small')));
      k.grille(.24, -.5, z, 3, 2, .04);
      k.part('power', { at: [-.2, .665, .02], slide: [.07, 0, 0] }, S => S.up(S.bar(.14, .04, .025, .01), 0, 0, 0, M.plastic('#1f2a30', 'small')));   // its switch on top
    },
  },
  gba: {
    insert: { at: [0, 24.25, -7.75], rot: [0, Math.PI, 0], from: [0, 56, 0], turn: Math.PI },   // all the way in, flush with the top
    name: 'Game Boy Advance', finish: plastic('#4e46a8', .4, .45), seam: { axis: 'z', at: -.0375 },
    build(k) {   // wide, with firm corners, the screen between the controls, shoulder buttons along the top edge
      const { M } = k, z = .1225, B0 = M.plastic('#c5c4d2', 'buttons'), SS = M.plastic('#c5c4d2', 'small'), D = M.plastic('#3b3488', 'dpad'), SH = M.plastic('#3b3488', 'shoulders');
      const out = k.rrect(1.445, .82, [.2, .2, .1, .1], 8);
      k.body(out, .245, .03, -.0375, k.at(0, 0, 0), M.shell, k.notch(out, .295, .24));   // the cartridge goes all the way in; its top half shows
      k.up(k.bar(.56, .07, .003, .005), 0, .2415, -.08, M.dark);
      k.half('lower', () => k.face(k.bar(.59, .165, .015, .004, .002), 0, .3275, -.045, M.shell));
      k.window(k.rrect(.84, .6, .06, 6), k.at(0, .03, z), .009, .62, .415, 0, .015, M.plastic('#18181f', 'bezel'), M.picture('#0c0f14'));
      k.dpad(-.565, .07, z, .2, D);                                                                     // kept clear of the screen's bezel
      [[.625, .13], [.495, .03]].forEach(([x, y]) => k.face(k.dome(.055, .028), x, y, z, B0));
      [[-.565, -.21], [-.565, -.29]].forEach(([x, y]) => k.face(k.dome(.024, .014), x, y, z, SS));
      k.grille(.55, -.24, z, 3, 3, .035);
      k.face(k.disc(.012, .004), -.36, -.215, z + .0095, M.led('#5bff8a'));                              // on the bezel, under the screen's corner
      [-1, 1].forEach(s => k.on(k.slabY(k.rrect(.26, .11, .025), .035, .012), k.at(s * .47, .41 + .0165, -.06), SH));
    },
  },
  ps1: {
    insert: { at: [-25, 30.5, -5], rot: [-Math.PI / 2, 0, 0], from: [0, 90, 0] },   // down into the well under the lid
    name: 'PlayStation', finish: plastic('#afada8', .5, .25), lid: { rest: 0 }, seam: { axis: 'y', at: -.241 },
    build(k) {   // a low gray box with a round lid on top and three round buttons
      const { M } = k, D0 = M.plastic('#a9a7a1', 'buttons');
      k.bodyY(k.rrect(2.7, 1.88, .08), .6, .05, -.241, k.at(0, 0, 0), M.shell);
      k.up(k.ring(.722, .745, .004, 72), -.25, .3, -.05, M.plastic('#8e8c86'));
      k.up(k.disc(.7, .01, 64), -.25, .296, -.05, M.plastic('#2a2a2e'));   // the well under the lid, with its spindle
      k.up(k.lathe([[0, .03], [.05, .03], [.06, 0], [0, 0]], 24), -.25, .3, -.05, M.metal);
      k.part('open', { at: [.92, .3, -.36], slide: [0, -.016, 0] }, B => B.up(B.dome(.15, .035), 0, 0, 0, D0));   // the big round button that opens the lid
      k.up(k.dome(.09, .03), .72, .3, .42, D0);
      k.part('power', { at: [1.06, .3, .42], slide: [0, -.014, 0] }, B => B.up(B.dome(.09, .03), 0, 0, 0, D0));
      k.up(k.disc(.02, .006), .89, .301, .2, M.led('#3dff6a'));
      [-.6, -.2].forEach(x => { k.face(k.bar(.3, .12, .008, .03), x, -.15, .94, M.dark); k.face(k.bar(.28, .05, .008, .01), x, .05, .94, M.dark); });
      k.lid({ hinge: [-.25, .3, -.77], closed: 0, open: -1.7 }, L => L.on(L.lathe([[0, .035], [.66, .035], [.71, .015], [.72, 0], [0, 0]], 72), L.at(0, 0, .72, -Math.PI / 2), L.M.plastic('#cbc9c4', 'lid')));
    },
  },
  ds: {
    insert: { at: [0, -1, -25], rot: [-Math.PI / 2, 0, 0], from: [0, 0, -90], turn: Math.PI },   // into the back edge, label up
    name: 'Nintendo DS', finish: plastic('#b0b3bc', .38, .5), lid: { rest: 1 }, seam: { axis: 'y', at: -.032 },
    build(k) {   // the original two-screen clamshell
      const { M } = k, P0 = M.plastic('#2c2e35', 'bezel'), K = M.plastic('#d3d5dc', 'buttons');
      k.bodyY(k.rrect(1.49, .85, .12, 8), .14, .03, -.032, k.at(0, 0, 0), M.shell);
      k.window(k.rrect(1.42, .78, .1, 8), k.at(0, .07, 0, -Math.PI / 2, 0, 0), .007, .62, .46, 0, 0, P0, M.screen);
      k.up(k.cross(.17, .055, .03), -.52, .071, -.03, M.plastic('#3a3c44', 'dpad'));
      [[.52, -.11], [.52, .05], [.44, -.03], [.6, -.03]].forEach(([x, z]) => k.up(k.dome(.033, .02), x, .071, z, K));
      [[.48, .26], [.56, .26]].forEach(([x, z]) => k.up(k.dome(.018, .01), x, .071, z, K));
      k.on(k.disc(.075, 1.3, 32), k.at(0, .135, -.43, 0, Math.PI / 2, 0), M.shell);   // the hinge, around the axis the top half turns on
      k.on(k.slabY(k.rrect(1.3, .1, .02), .1, .02), k.at(0, .085, -.395), M.shell);   // joining it to the bottom half
      k.on(k.plane(.39, .05), k.at(0, 0, -.4255, 0, Math.PI, 0), M.dark);   // the card slot in the back edge
      // the top half, built standing up from the hinge; closed, it lies on the bottom half
      k.lid({ hinge: [0, .135, -.43], closed: Math.PI / 2, open: -.35 }, L => {
        L.on(L.solid(L.rrect(1.49, .85, .12, 8), .13, .03), L.at(0, .44, 0), M.shell);
        L.window(L.rrect(1.42, .78, .1, 8), L.at(0, .44, .065), .007, .62, .46, 0, 0, P0, M.picture('#0c0f14'));
        [-1, 1].forEach(s => { for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) L.on(L.disc(.01, .002), L.at(s * .55 + (i - 1) * .03, .49 + (j - 1) * .03, .0722), M.dark); });
      });
    },
  },
  psp: {
    insert: { at: [0, 0, -13.7], rot: [0, Math.PI, 0], from: [0, 0, -80], turn: Math.PI },   // in the hollow under the back door, label out (seen while the door is open)
    name: 'PSP', finish: plastic('#16171b', .22, .85), lid: { rest: 0 }, seam: { axis: 'z', at: 0 },
    build(k) {   // a glossy black oval with a wide screen in the middle
      const { M } = k, z = .115, K = M.plastic('#2c2d33', 'buttons'), SH = M.plastic('#2c2d33', 'shoulders');
      k.body(k.rrect(1.7, .74, .33, 10), .23, .05, 0, k.at(0, 0, 0), M.shell);
      k.window(k.rrect(1.02, .6, .03, 6), k.at(0, .02, z), .007, .96, .545, 0, 0, M.plastic('#0b0c0f', 'bezel'), M.picture('#0c0f14'));
      [[-.075, 0, Math.PI / 2], [.075, 0, -Math.PI / 2], [0, .075, 0], [0, -.075, Math.PI]]   // all pointing inward
        .forEach(([dx, dy, r]) => k.face(k.tri(.04, .02), -.64 + dx, .12 + dy, z + .008, K, r));
      k.face(k.lathe([[0, .035], [.045, .033], [.06, .02], [.062, 0], [0, 0]], 24), -.64, -.17, z, M.plastic('#5e6068', 'stick'));
      [[.085, 0], [-.085, 0], [0, .085], [0, -.085]].forEach(([dx, dy]) => k.face(k.dome(.042, .022), .64 + dx, .12 + dy, z, K));
      for (let i = 0; i < 6; i++) k.face(k.bar(.07, .024, .012, .012), -.3 + i * .12, -.31, z + .004, K);
      [-1, 1].forEach(s => {   // shoulder buttons that follow the curve of the top corners
        const cx = s * .52, cy = .04, pts = [], A = s > 0 ? [28, 98] : [82, 152];
        for (let i = 0; i <= 16; i++) { const a = (A[0] + (A[1] - A[0]) * i / 16) * Math.PI / 180; pts.push([cx + .35 * Math.cos(a), cy + .35 * Math.sin(a), i === 0 || i === 16 ? .012 : 0]); }
        for (let i = 16; i >= 0; i--) { const a = (A[0] + (A[1] - A[0]) * i / 16) * Math.PI / 180; pts.push([cx + .29 * Math.cos(a), cy + .29 * Math.sin(a), 0]); }
        k.on(k.solid(k.rounded(pts), .11, .015), k.at(0, 0, -.03), SH);
      });
      // the disc sits on the back under a raised door, hinged along its top edge
      k.on(k.plane(.66, .6), k.at(0, 0, -z - .0008, 0, Math.PI, 0), M.dark);
      k.lid({ hinge: [0, .32, -z], closed: 0, open: 1.95 }, L => {
        L.on(L.solid(L.rrect(.7, .64, [.06, .06, .24, .24], 8), .056, .02), L.at(0, -.32, -.029), M.plastic('#1f2026'));
        L.on(L.bar(.2, .03, .004, .01), L.at(0, -.58, -.058), M.plastic('#3a3b42'));
      });
    },
  },
  android: {
    insert: { at: [0, 26.1, -4], rot: [0, 0, 0], from: [0, 70, 0] },   // down into the slot on top
    name: 'Android handheld', finish: plastic('#2b2e3a', .4, .45), seam: { axis: 'z', at: -.02 },
    build(k) {   // Android games: a generic modern handheld
      const { M } = k, z = .1, K = M.plastic('#d9dbe3', 'buttons'), S = M.plastic('#4a4e5c', 'stick'), D = M.plastic('#3b3f4d', 'dpad'), SH = M.plastic('#3b3f4d', 'shoulders');
      const stick = x => k.face(k.lathe([[0, .04], [.05, .038], [.07, .025], [.075, 0], [0, 0]], 24), x, -.2, z, S);
      k.body(k.rrect(1.9, .82, .14, 8), .2, .04, -.02, k.at(0, 0, 0), M.shell);
      k.window(k.rrect(1.14, .68, .05, 6), k.at(0, .02, z), .008, 1.06, .6, 0, 0, M.plastic('#0e1015', 'bezel'), M.picture('#0c0f14'));
      k.dpad(-.72, .13, z, .2, D);
      stick(-.72); k.face(k.ring(.08, .1, .004, 32), -.72, -.2, z + .001, M.dark);
      [[.085, 0], [-.085, 0], [0, .085], [0, -.085]].forEach(([dx, dy]) => k.face(k.dome(.04, .02), .72 + dx, .13 + dy, z, K));
      stick(.72);
      [-1, 1].forEach(s => k.on(k.solid(k.rrect(.42, .05, .02), .14, .02), k.at(s * .62, .41, -.02), SH));
      k.up(k.bar(.5, .06, .004, .01), 0, .411, -.04, M.dark);
    },
  },
};
