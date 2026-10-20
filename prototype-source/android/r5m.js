/* ================= Museum: every console, its media and its box, to turn over and read about ================= */
const MUSEUM = {
  nes: { maker: 'Nintendo', year: 1985, released: 'October 1985', sold: 'About 62 million', cpu: 'Ricoh 2A03, 1.79 MHz', plays: 'Cartridges', facts: [
    'After the video game crash of 1983, stores didn’t want game consoles, so the NES was styled like a VCR, with a door on the front, and sold alongside a toy robot.',
    'Blowing into cartridges mostly worked because it made people put them back in. The moisture actually corroded the contacts over time.',
    'In Japan it was the Famicom, a red-and-white console that loaded from the top. Its second controller had a built-in microphone.'] },
  snes: { maker: 'Nintendo', year: 1991, released: 'August 1991', sold: 'About 49 million', cpu: 'Ricoh 5A22, up to 3.58 MHz', plays: 'Cartridges', facts: [
    'Its sound chip was designed by Ken Kutaragi at Sony. That partnership is how Sony ended up making the PlayStation.',
    '“Mode 7” could rotate and scale a background layer, which is how racing games like F-Zero faked a 3D track.',
    'Some cartridges carried extra chips. The Super FX chip in Star Fox let it draw 3D polygons.',
    'North America got a boxy purple-gray console. Japan and Europe got this rounder design, and rounder cartridges to match.'] },
  n64: { maker: 'Nintendo', year: 1996, released: 'September 1996', sold: 'About 33 million', cpu: 'NEC VR4300, 93.75 MHz', plays: 'Cartridges', facts: [
    'Its controller made the analog stick standard for home consoles.',
    'The graphics chip was co-designed with Silicon Graphics, the company behind the computers used for movie effects at the time.',
    'Four controller ports came built in, which made four-player split screen a living-room staple.',
    'The Expansion Pak doubled its memory from 4 MB to 8 MB. Majora’s Mask wouldn’t run without it.'] },
  genesis: { maker: 'Sega', year: 1989, released: 'August 1989', sold: 'About 31 million', cpu: 'Motorola 68000, 7.67 MHz', plays: 'Cartridges', facts: [
    'Everywhere else it was called the Mega Drive. A trademark got in the way in North America.',
    'Its main processor, the Motorola 68000, also powered the first Macintosh computers.',
    'Its music comes from a Yamaha FM synthesis chip, which gives it its distinctive punchy sound.',
    'A second processor inside lets it play Master System games with an adapter.'] },
  gb: { maker: 'Nintendo', year: 1989, released: 'July 1989', sold: 'About 119 million, with the Game Boy Color', cpu: 'Sharp SM83, 4.19 MHz', plays: 'Cartridges', facts: [
    'It was designed by Gunpei Yokoi’s team, who chose a simple screen and long battery life over color.',
    'One survived a bombing during the Gulf War. Its case is melted, but it still plays Tetris, on display in New York.',
    'Its screen shows four shades of green-gray.',
    'In North America it came with Tetris, which became one of the best-selling games ever.'] },
  gbc: { maker: 'Nintendo', year: 1998, released: 'November 1998', sold: 'About 119 million, with the Game Boy', cpu: 'Sharp SM83, up to 8.39 MHz', plays: 'Cartridges', facts: [
    'It can show 56 colors at once, out of 32,768.',
    'It plays original Game Boy games too. Holding buttons on the startup screen picks a color palette for them.',
    'Most games made only for the Game Boy Color came on clear cartridges, which won’t run on the original. Black ones work on both.',
    'An infrared port on top let two players swap data without a cable.'] },
  gba: { maker: 'Nintendo', year: 2001, released: 'June 2001', sold: 'About 82 million', cpu: 'ARM7TDMI, 16.78 MHz', plays: 'Cartridges', facts: [
    'The original model had no screen light, so people played under lamps. The folding SP model added one in 2003.',
    'A second processor inside lets it play Game Boy and Game Boy Color cartridges.',
    'Its wide layout put the screen between your hands for the first time on a Game Boy.'] },
  ps1: { maker: 'Sony', year: 1995, released: 'September 1995', sold: 'About 102 million', cpu: 'MIPS R3000A, 33.87 MHz', plays: 'CDs', facts: [
    'It began as a CD add-on for the Super Nintendo. When that deal fell apart, Sony made its own console.',
    'Its discs have a black underside, partly to stand out and partly to make copying harder.',
    'It was the first console to sell more than 100 million.',
    'A memory card holds 15 save blocks, 128 KB in all.'] },
  ds: { maker: 'Nintendo', year: 2004, released: 'November 2004', sold: 'About 154 million', cpu: 'ARM9 at 67 MHz and ARM7 at 33 MHz', plays: 'Game cards', facts: [
    'Two screens, the lower one touch-sensitive, and a microphone you could blow into.',
    'It’s the best-selling handheld of all time.',
    'It came out in North America before Japan, which was unusual for Nintendo.',
    'A second slot on the bottom plays Game Boy Advance cartridges.'] },
  psp: { maker: 'Sony', year: 2005, released: 'March 2005', sold: 'About 80 million', cpu: 'MIPS Allegrex, up to 333 MHz', plays: 'UMDs', facts: [
    'The first handheld to use optical discs. A UMD holds 1.8 GB.',
    'Movies were sold on UMD too.',
    'It could connect to a PlayStation 3 and stream it over Wi-Fi.'] },
  android: { maker: 'Many makers', year: 2008, released: 'September 2008 (Android 1.0)', sold: 'Billions of devices', cpu: 'Varies', plays: 'Apps', facts: [
    'Android started out as an operating system for digital cameras.',
    'The first Android phone, the T-Mobile G1, had a slide-out keyboard.',
    'In 2011 the Xperia Play put a slide-out game controller on an Android phone.',
    'Handhelds like the Retroid Pocket run Android and play games from every console in this museum.'] },
};
const MU_ORDER = Object.keys(MUSEUM).sort((a, b) => MUSEUM[a].year - MUSEUM[b].year || SYS_ORDER.indexOf(a) - SYS_ORDER.indexOf(b));   // a timeline, oldest first
const MEDIA_NAME = { cart: 'Cartridge', disc: 'Disc', card: 'Game card', umd: 'UMD', acard: 'Game card' };
const EXHIBITS = sys => ['Console', MEDIA_NAME[PLAT[sys].media], 'Box', 'Open box'];
const EX_VIEW = [{ yaw: -.55, pitch: .45 }, { yaw: -.4, pitch: .12 }, { yaw: .5, pitch: .12 }, { yaw: 0, pitch: .12 }];
const sampleGame = sys => GAMES.find(g => g.sys === sys);
function enterMuseum() { const U = S.mu; U.zone = U.zone || 'rail'; refreshMuseum(); setMood(MU_ORDER[U.i]); }
function pickExhibit(i, ex) {
  const U = S.mu; if (i === U.i && ex === U.ex) return;
  const sameBox = i === U.i && ex >= 2 && U.ex >= 2;
  if (!sameBox) { U.prev = { sys: MU_ORDER[U.i], ex: U.ex, yaw: U.yaw, pitch: U.pitch, open: U.open }; U.fade = 0; }
  if (i !== U.i || !sameBox) { const v = EX_VIEW[ex]; U.yawT = v.yaw; U.pitchT = v.pitch; if (!sameBox) { U.yaw = v.yaw + (i !== U.i ? .5 : .3); U.pitch = v.pitch; } }
  if (!sameBox) U.open = ex === 3 ? .6 : 0;
  U.i = i; U.ex = ex; setMood(MU_ORDER[i]); refreshMuseum();
}
function refreshMuseum() {
  const U = S.mu, sys = MU_ORDER[U.i], d = MUSEUM[sys], g = sampleGame(sys), stage0 = !S.top.on && U.zone === 'stage';
  $('#muRail').innerHTML = '<nav class="railin"><h5>Consoles, oldest first</h5>' + MU_ORDER.map((k, i) => `<div class="${i === U.i ? 'cur' : ''} ${!S.top.on && U.zone === 'rail' && i === U.i ? 'focus' : ''}">${esc(PLAT[k].name)}<span>${MUSEUM[k].year}</span></div>`).join('') + '</nav>';
  $('#muChips').innerHTML = EXHIBITS(sys).map((e, i) => `<div class="filter ${i === U.ex ? (stage0 ? 'focus' : 'cur') : ''}">${e}</div>`).join('');
  $('#muInfo').innerHTML = `<div class="gp-sys">${esc(d.maker)} · ${d.year}</div><h2>${esc(PLAT[sys].long)}</h2>
    <div class="mu-facts"><div>Released in North America<b>${esc(d.released)}</b></div><div>Sold<b>${esc(d.sold)}</b></div><div>Processor<b>${esc(d.cpu)}</b></div><div>Plays<b>${esc(d.plays)}</b></div></div>
    <h3>Did you know?</h3><ul>${d.facts.map(f => `<li>${esc(f)}</li>`).join('')}</ul>
    <p class="mu-note">Shelf’s models are stylized: the real shapes and sizes, with no logos. Shown with ${esc(g.title)}, an invented game.</p>`;
  $('#muHint').innerHTML = stage0 ? '<i class="btn">◀▶</i><i class="btn">▲▼</i> Turn it &nbsp; <i class="btn">A</i> Next exhibit &nbsp; <i class="btn">Y</i> Reset the view &nbsp; <i class="btn">B</i> Back to the list'
    : '<i class="btn">▲▼</i> Choose a console &nbsp; <i class="btn">A</i> Take a closer look';
  renderTop();
}
function museumInput(a) {
  const U = S.mu, n = MU_ORDER.length;
  if (U.zone === 'rail') {
    if (a === 'up' && U.i === 0) { S.top.on = true; S.top.i = TOP.indexOf('museum'); sfx.move(); return refreshMuseum(); }
    if (a === 'up' || a === 'down') { const k = clamp(U.i + (a === 'up' ? -1 : 1), 0, n - 1); if (k !== U.i) { pickExhibit(k, U.ex); sfx.move(); } }
    else if (a === 'right' || a === 'a') { U.zone = 'stage'; sfx.move(); refreshMuseum(); }
    return;
  }
  if (a === 'left' || a === 'right') U.yawT += (a === 'left' ? -1 : 1) * .4;
  else if (a === 'up' || a === 'down') U.pitchT = clamp(U.pitchT + (a === 'up' ? 1 : -1) * .22, -1.1, 1.3);
  else if (a === 'a') { pickExhibit(U.i, (U.ex + 1) % 4); sfx.select(); }
  else if (a === 'y') { const v = EX_VIEW[U.ex]; U.yawT = v.yaw + Math.round((U.yawT - v.yaw) / (Math.PI * 2)) * Math.PI * 2; U.pitchT = v.pitch; sfx.move(); }
  else if (a === 'b') { U.zone = 'rail'; sfx.back(); refreshMuseum(); }
}
// fit any exhibit into the stage: its size from the model's bounds, centered on its middle
function exhibitBounds(sys, ex, open) {
  if (ex === 0) { const m = consoleModel(sys); return [m.lo, m.hi]; }
  if (ex === 1) { const m = cartModel(sys); return [m.lo, m.hi]; }
  const [W, H, D] = BOX[sys], k = clamp(open, 0, 1); return [[-W / 2 - W * k, -H / 2, -D / 2], [W / 2, H / 2, D / 2]];
}
function drawExhibit(sys, ex, k, yaw, pitch, open) {
  const g = sampleGame(sys), [lo, hi] = exhibitBounds(sys, ex, ex >= 2 ? open : 0), c = lo.map((v, i) => (v + hi[i]) / 2);
  const span = Math.max(hi[0] - lo[0], (hi[1] - lo[1]) * 1.25, hi[2] - lo[2]), fit = (ex === 0 ? 3.3 : ex === 3 ? 3.9 : 3.0) / span;
  const s = fit * (.94 + .06 * k), bob = motion() ? Math.sin(S.time * .8) * .025 : 0, [x, y] = pw(560, 392);
  const R = M4.mul(M4.trs(x, y + bob, 0, pitch, 0, 0, 1), M4.trs(0, 0, 0, 0, yaw, 0, s)), st = { alpha: k, bright: 1, s, detail: 1 };
  if (ex >= 2) {   // cases draw from their own pose, so fold the turn and the centering into it
    drawCaseAt(g, M4.mul(R, M4.trs(-c[0], -c[1], -c[2], 0, 0, 0, 1)), st, open);
  } else {
    const M = M4.mul(R, M4.trs(-c[0], -c[1], -c[2], 0, 0, 0, 1));
    ex === 0 ? drawConsole(sys, M, st) : drawCartM(g, M, st);
  }
}
function drawCaseAt(g, M, st, open) { drawCase(g, { M, s: st.s, alpha: st.alpha, bright: st.bright, detail: 1 }, open); }
function drawMuseum(dt) {
  const U = S.mu, sys = MU_ORDER[U.i];
  approach(U, { yaw: U.yawT, pitch: U.pitchT, open: U.ex === 3 ? 1 : 0 }, dt, 5);
  U.fade = Math.min(1, U.fade + dt / .5);
  setCamera(0, CAMZ, 0);
  shadowUnder(560, 600, 430, .8, true);
  beginMeshes();
  if (U.prev && U.fade < 1) drawExhibit(U.prev.sys, U.prev.ex, 1 - easeOut(U.fade * 1.6), U.prev.yaw, U.prev.pitch, U.prev.open);
  if (!NODRAW) gl.clear(gl.DEPTH_BUFFER_BIT);   // the outgoing exhibit never hides the incoming one
  drawExhibit(sys, U.ex, easeOut(U.fade), U.yaw, U.pitch, U.open);
  endMeshes();
}
(function museumDrag() {   // drag on the stage to turn the exhibit (mouse or touch)
  let drag = null;
  addEventListener('pointerdown', e => { if (S.screen !== 'museum' || !e.target.closest || !e.target.closest('#muDrag')) return; drag = [e.clientX, e.clientY]; S.mu.zone = 'stage'; refreshMuseum(); });
  addEventListener('pointermove', e => { if (!drag) return; const k = 1 / (stage.getBoundingClientRect().width / W); const U = S.mu;
    U.yawT += (e.clientX - drag[0]) * k * .01; U.pitchT = clamp(U.pitchT + (e.clientY - drag[1]) * k * .008, -1.1, 1.3); U.yaw = U.yawT; U.pitch = U.pitchT; drag = [e.clientX, e.clientY]; });
  addEventListener('pointerup', () => { drag = null; });
})();
