// Builds a game's case for the app: the prototype's clamshell cases (prototype-source/android/r4m.js, caseModel) at the
// real boxes' sizes (docs/RESEARCH.md), in the Studio's beveled, physically based style. Units are millimetres.
//
// The case is a hollow base (a floor, a thin wall, and a molded tray with a well the cartridge sits in) and a cover on
// a hinge along the spine. Nodes the app uses:
//   cover          the cover, its origin on the hinge: turn it around y to open (0 closed, -π lying open to the left)
//   front / back / spine   the art: flat planes with their own matte material and a texture slot
//   media          where the cartridge, disc or card sits in the tray (its centre)
//   manual         the game's manual, its origin at its centre: a booklet held inside the cover (its cover art on
//                  'manual-art', facing the tray when closed and you when open); a jewel case's front booklet
//   body / inner   the case's plastic, which the app colors
import * as THREE from 'three';
import { outlinePoints, slab, plasticMaterial, labelMaterial } from './cartridge.js';

// Each console's case: width, height, depth in mm; 'jewel' for the PlayStation's jewel case with its clear cover.
export const CASES = {
  nes: { size: [127, 182, 25] }, snes: { size: [174, 121, 28] }, n64: { size: [174, 121, 28] }, genesis: { size: [128, 197, 25] },
  gb: { size: [123, 123, 20] }, gbc: { size: [123, 123, 20] }, gba: { size: [123, 123, 20] },
  ps1: { size: [142, 125, 9.9], jewel: true }, ds: { size: [135, 122, 15] }, psp: { size: [104, 177, 14] }, android: { size: [135, 171, 12] },
};

const rrect = (w, h, r, x = 0, y = 0) => outlinePoints([[x - w / 2, y - h / 2, r], [x + w / 2, y - h / 2, r], [x + w / 2, y + h / 2, r], [x - w / 2, y + h / 2, r]]);

// A flat piece of art with rounded corners and UVs across its whole area.
function artPlane(w, h, r, material, name) {
  const g = new THREE.ShapeGeometry(new THREE.Shape(rrect(w, h, r).map(p => new THREE.Vector2(p[0], p[1]))), 8);
  const uv = g.attributes.uv, pos = g.attributes.position;
  for (let i = 0; i < uv.count; i++) uv.setXY(i, pos.getX(i) / w + .5, pos.getY(i) / h + .5);
  const m = new THREE.Mesh(g, material); m.name = name; return m;
}

// mediaSize: the cartridge's [width, height, depth] (a disc's diameter twice), so the well fits it.
export function buildCase(sys, mediaSize, opts) {
  const spec = CASES[sys], [W, H, D] = spec.size, jewel = !!spec.jewel;
  const r = jewel ? 1.2 : 4, ct = Math.min(3, D * .22), top = D / 2 - ct, bevel = Math.min(opts.bevel ?? .8, .8), seg = opts.segments ?? 4;
  const [mw, mh, md] = mediaSize, disc = mw === mh && sys === 'ps1';
  const tz = top - 2, floor = Math.max(-D / 2 + 1.2, tz - md - .7);
  const outline = rrect(W, H, r), innerLine = rrect(W - 4, H - 4, Math.max(.4, r - 2));   // a 2 mm wall
  // the well fits the media with a little play, leaving at least a 1 mm rim of tray (an NES cartridge nearly fills its box)
  const well = disc ? rrect(mw + 2, mh + 2, (mw + 2) / 2, 0, 0) : rrect(Math.min(mw + 3, W - 6), Math.min(mh + 3, H - 6), 2);

  const body = plasticMaterial({ color: opts.color ?? '#2b2f3c', roughness: .42, clearcoat: .35, clearcoatRoughness: .6 }); body.name = 'case-body';
  const inner = plasticMaterial({ color: opts.inner ?? '#1d2029', roughness: .6, clearcoat: .1, clearcoatRoughness: .7 }); inner.name = 'case-inner';
  const tray = jewel ? plasticMaterial({ color: '#15161b', roughness: .35, clearcoat: .5, clearcoatRoughness: .3 }) : inner;
  const clear = new THREE.MeshPhysicalMaterial({ color: '#dfe8f2', roughness: .05, clearcoat: 1, clearcoatRoughness: .03, transparent: true, opacity: .25 });
  const art = name => { const m = labelMaterial(opts.artTexture, { labelRoughness: .5, labelGloss: .15 }); m.name = name; return m; };

  const group = new THREE.Group(); group.name = 'case';
  // the base: a floor, the outer wall up to the hinge line, and the tray a little lower with its well
  const base = new THREE.Group(); base.name = 'base';
  const floorM = new THREE.Mesh(slab(outline, [], -D / 2, floor, bevel, seg), jewel ? tray : body); floorM.name = 'body';
  // a thin wall takes a small bevel: a bevel wider than half the wall closes its opening
  const wall = new THREE.Mesh(slab(outline, [innerLine], floor - .5, top, .3, 2), jewel ? tray : body); wall.name = 'body';
  const trayM = new THREE.Mesh(slab(innerLine, [well], floor - .5, tz, .3, 2), tray); trayM.name = 'inner';
  const wellFloor = new THREE.Mesh(new THREE.ShapeGeometry(new THREE.Shape(well.map(p => new THREE.Vector2(p[0], p[1]))), 8).translate(0, 0, floor + .02), inner); wellFloor.name = 'inner';
  base.add(floorM, wall, trayM, wellFloor);
  if (disc) {   // a hub with teeth that grips the disc
    const hub = new THREE.Mesh(new THREE.CylinderGeometry(9, 9, 1.4, 40).rotateX(Math.PI / 2).translate(0, 0, floor + .7), tray); hub.name = 'inner'; base.add(hub);
  } else for (const s of [-1, 1]) {   // two clips at the edge of the well
    // beside it where there's room inside the wall; where the cartridge nearly fills the box's width (the NES), above
    // and below it instead, lying along the edge, so they never poke out through the sides
    const side = mw / 2 + 3 + 5 <= W / 2 - 2.5;
    const clip = new THREE.Mesh(slab(side ? rrect(10, 2.8, 1) : rrect(10, 2.4, 1), [], tz - .2, tz + .8, .3, 2), tray);
    if (side) clip.position.set(s * (mw / 2 + 3), s * mh * .2, 0);
    else clip.position.set(s * mw * .22, s * Math.min(mh / 2 + 2.6, H / 2 - 3.8), 0);
    clip.name = 'inner'; base.add(clip);
  }
  // the back and the spine carry art
  const back = artPlane(W - 3, H - 3, Math.max(.5, r - 1.5), art('case-back'), 'back'); back.rotation.y = Math.PI; back.position.z = -D / 2 - .3; base.add(back);
  const spine = artPlane(D - 2, H - 4, .8, art('case-spine'), 'spine'); spine.rotation.y = -Math.PI / 2; spine.position.set(-W / 2 - .3, 0, 0); base.add(spine);
  group.add(base);

  // the cover, on the hinge along the spine (the left edge, at the cover's middle depth)
  const hx = -W / 2, hz = D / 2 - ct / 2;
  const cover = new THREE.Group(); cover.name = 'cover'; cover.position.set(hx, 0, hz);
  const shell = new THREE.Group(); shell.position.set(-hx, 0, -hz);
  const lid = new THREE.Mesh(slab(outline, [], top, D / 2, Math.min(bevel, ct * .4), seg), jewel ? clear : body); lid.name = jewel ? 'clear' : 'body';
  shell.add(lid);
  if (!jewel) {   // a raised rim around the art
    const rim = new THREE.Mesh(slab(rrect(W - 2, H - 2, r - 1), [rrect(W - 5.2, H - 5.2, Math.max(.4, r - 2.6))], D / 2 - .1, D / 2 + .7, .15, 2), body); rim.name = 'body'; shell.add(rim);
  }
  const front = artPlane(W - (jewel ? 4 : 5.6), H - (jewel ? 4 : 5.6), Math.max(.4, r - 2.8), art('case-front'), 'front');
  // the art sits clear of the plastic (or the two flicker against each other); a jewel case's art is behind its clear cover
  front.position.z = jewel ? top + .3 : D / 2 + .3;
  if (jewel) {   // a jewel case's front art is its booklet: the manual slides out from behind the clear cover
    const book = new THREE.Group(); book.name = 'manual'; book.position.z = front.position.z; front.position.z = 0; book.add(front); shell.add(book);
    // its inside, seen through the lid when the case lies open (the art is single-sided, so the front alone vanished)
    const inside = artPlane(W - 4, H - 4, Math.max(.4, r - 2.8), art('manual-art'), 'manual-art'); inside.rotation.y = Math.PI; inside.position.z = -.25; book.add(inside);
  } else shell.add(front);
  const lining = new THREE.Mesh(new THREE.ShapeGeometry(new THREE.Shape(rrect(W - 5, H - 5, Math.max(.4, r - 2.5)).map(p => new THREE.Vector2(p[0], p[1]))), 8).rotateY(Math.PI).translate(0, 0, top - .3), inner);
  lining.name = 'inner'; if (!jewel) shell.add(lining);
  if (!jewel) {
    // the manual: a booklet a little smaller than the cover, lying against its inside under two clips; its own cover
    // (the art) faces the tray when the case is shut, and you when it lies open
    const mw = W - 16, mh = H - 18, mt = 1.4, mz = top - .4 - mt / 2;
    const book = new THREE.Group(); book.name = 'manual'; book.position.set(0, 0, mz);
    const paper = plasticMaterial({ color: '#efece4', roughness: .85, clearcoat: 0, clearcoatRoughness: .9 }); paper.name = 'manual-paper';
    const sheet = new THREE.Mesh(slab(rrect(mw, mh, 1.2), [], -mt / 2, mt / 2, .2, 2), paper); sheet.name = 'manual-paper'; book.add(sheet);
    const face = artPlane(mw - 1, mh - 1, .8, art('manual-art'), 'manual-art'); face.rotation.y = Math.PI; face.position.z = -mt / 2 - .15; book.add(face);
    shell.add(book);
    for (const s of [-1, 1]) {   // the clips that hold it, at the cover's top and bottom edges
      const clip = new THREE.Mesh(slab(rrect(14, 3, 1), [], top - .4 - mt - .7, top - .2, .3, 2), inner); clip.position.set(0, s * (mh / 2 + .6), 0); clip.name = 'inner'; shell.add(clip);
    }
  }
  cover.add(shell); group.add(cover);

  // where the media sits: centred in the well, resting on its floor
  const media = new THREE.Object3D(); media.name = 'media'; media.position.set(0, 0, floor + .05 + md / 2); group.add(media);
  group.userData.materials = { body, inner };
  return group;
}
