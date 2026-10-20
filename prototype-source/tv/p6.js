/* ================= input ================= */
function act(a) {
  audio();
  if (S.sleep) { if (S.sleep !== 'restart') wake(); return; }
  if (a === 'home') return goHome();
  if (S.kb.open) {
    const k = S.kb; if (a !== 'a') k.typed = false;
    if (a === 'up' || a === 'down') { const r = clamp(k.r + (a === 'up' ? -1 : 1), 0, KB_ROWS.length - 1); if (r !== k.r) { k.c = Math.min(Math.round(k.c * (KB_ROWS[r].length - 1) / Math.max(1, KB_ROWS[k.r].length - 1)), KB_ROWS[r].length - 1); k.r = r; sfx.move(); renderKb(); } }
    else if (a === 'left' || a === 'right') { k.c = (k.c + (a === 'left' ? -1 : 1) + KB_ROWS[k.r].length) % KB_ROWS[k.r].length; sfx.move(); renderKb(); }
    else if (a === 'a') kbPress(); else if (a === 'y') { k.value = k.value.slice(0, -1); renderKb(); }
    else if (a === 'b') { k.open = false; renderKb(); sfx.back(); }
    return;
  }
  if (S.dlg.open) {
    const d = S.dlg;
    if (a === 'up' || a === 'down') { d.i = clamp(d.i + (a === 'up' ? -1 : 1), 0, d.items.length - 1); sfx.move(); renderDlg(); }
    else if (a === 'a') d.onPick(d.i); else if (a === 'b') { closeDlg(); sfx.back(); }
    return;
  }
  if (S.pmenu.open) {
    if (a === 'up' || a === 'down') { S.pmenu.i = clamp(S.pmenu.i + (a === 'up' ? -1 : 1), 0, PMENU.length - 1); sfx.move(); renderPmenu(); }
    else if (a === 'a') pmenuAct(); else if (a === 'b') { closePmenu(); sfx.back(); }
    return;
  }
  const sc = S.screen;
  if (sc === 'profiles') {
    const n = PROFILES.length + 1;
    if (a === 'left' || a === 'right') { const k = clamp(S.pSel + (a === 'left' ? -1 : 1), 0, n - 1); if (k !== S.pSel) { S.pSel = k; sfx.move(); updateProfiles(); } }
    else if (a === 'a') chooseProfile();
    return;
  }
  if (sc === 'play' || sc === 'launch') return;
  if (sc === 'home' || sc === 'library') {
    if (a === 'lb' || a === 'rb') { sfx.tab(); S.top.on = false; return enterTab(sc === 'home' ? 'library' : 'home'); }
    if (S.top.on) return topInput(a);
    return sc === 'home' ? homeInput(a) : libInput(a);
  }
  if (sc === 'game') return gameInput(a);
  if (sc === 'ach') return achInput(a);
  if (sc === 'media') return mediaInput(a);
  if (sc === 'manual') return manualInput(a);
  if (sc === 'settings') return setInput(a);
}
function refreshTab() { S.tab === 'home' ? refreshHome() : refreshLibrary(false); }
function topInput(a) {
  if (a === 'left' || a === 'right') { const k = clamp(S.top.i + (a === 'left' ? -1 : 1), 0, TOP.length - 1); if (k !== S.top.i) { S.top.i = k; sfx.move(); renderTop(); } }
  else if (a === 'down' || a === 'b') { S.top.on = false; sfx.move(); refreshTab(); }
  else if (a === 'a') {
    const k = TOP[S.top.i];
    if (k === 'home' || k === 'library') { if (k !== S.tab) { sfx.tab(); enterTab(k); S.top.on = true; renderTop(); refreshTab(); } }
    else if (k === 'settings') openSettings(0); else openPmenu();
  }
}
function homeInput(a) {
  const H0 = S.home, items = homeItems(), id = items[H0.i];
  if (H0.zone === 'row') {
    if (a === 'left' || a === 'right') { const k = clamp(H0.i + (a === 'left' ? -1 : 1), 0, items.length - 1); if (k !== H0.i) { H0.i = k; H0.card = 0; sfx.move(); refreshHome(); } }
    else if (a === 'up') { S.top.on = true; S.top.i = 0; sfx.move(); refreshHome(); }
    else if (a === 'down') { H0.zone = 'cards'; H0.card = 0; sfx.move(); refreshHome(); }
    else if (a === 'a') { if (id === '__all') { sfx.select(); enterTab('library'); } else openGame(id, 'home:' + id, 'home'); }
    else if (a === 'y' && id !== '__all') toggleFav(byId[id]);
  } else {
    const n = homeCards(id).length;
    if (a === 'left' || a === 'right') { const k = clamp(H0.card + (a === 'left' ? -1 : 1), 0, n - 1); if (k !== H0.card) { H0.card = k; sfx.move(); refreshHome(); } }
    else if (a === 'up' || a === 'b') { H0.zone = 'row'; sfx.move(); refreshHome(); }
    else if (a === 'a') cardAction();
    else if (a === 'y' && id !== '__all') toggleFav(byId[id]);
  }
}
function libInput(a) {
  const L = S.lib, n = L.list.length, cols = 5;
  if (L.zone === 'grid') {
    const col = L.i % cols;
    if (a === 'left') { if (col === 0 || !n) L.zone = 'rail'; else L.i--; sfx.move(); }
    else if (a === 'right') { if (col < cols - 1 && L.i + 1 < n) { L.i++; sfx.move(); } }
    else if (a === 'up') { if (L.i < cols) L.zone = 'filters'; else L.i -= cols; sfx.move(); }
    else if (a === 'down') { if (L.i + cols < n) { L.i += cols; sfx.move(); } else if (Math.floor(L.i / cols) < Math.floor((n - 1) / cols)) { L.i = n - 1; sfx.move(); } }
    else if (a === 'a' && n) return openGame(L.list[L.i].id, 'lib:' + L.list[L.i].id, 'library');
    else if (a === 'y' && n) return toggleFav(L.list[L.i]);
    else if (a === 'b') { L.zone = 'rail'; sfx.back(); }
  } else if (L.zone === 'rail') {
    const stops = railStops(), at = stops.indexOf(L.rail);
    const item = railItems(curProfile())[L.rail];
    if (a === 'up' && at === 0) { S.top.on = true; S.top.i = 1; sfx.move(); }
    else if (a === 'up' || a === 'down') { const k = clamp(at + (a === 'up' ? -1 : 1), 0, stops.length - 1); if (stops[k] !== L.rail) { L.rail = stops[k]; L.i = 0; L.rowF = 0; sfx.move(); return refreshLibrary(true); } }
    else if (a === 'a' && item.add) return newCollection();
    else if (a === 'y' && item.custom) return collectionOptions(item.custom);
    else if ((a === 'right' || a === 'a') && n) { L.zone = 'grid'; sfx.move(); }
    else if (a === 'b') { sfx.tab(); return enterTab('home'); }
  } else {
    if (a === 'left') { if (L.filter === 0) L.zone = 'rail'; else L.filter = 0; sfx.move(); }
    else if (a === 'right') { L.filter = 1; sfx.move(); }
    else if (a === 'up') { S.top.on = true; S.top.i = 1; sfx.move(); }
    else if (a === 'down' || a === 'b') { if (n) L.zone = 'grid'; sfx.move(); }
    else if (a === 'a') { if (L.filter === 0) L.sort = (L.sort + 1) % SORTS.length; else L.show = (L.show + 1) % SHOWS.length; L.i = 0; L.rowF = 0; sfx.select(); return refreshLibrary(true); }
  }
  refreshLibrary(false);
}
function gameInput(a) {
  const G0 = S.game, acts = gameActions(), hc = curProfile().prefs.hardcore, g = byId[G0.id], nMedia = mediaFor(g).length;
  if (a === 'b') { if (G0.zone !== 'actions') { G0.zone = 'actions'; G0.i = 0; fillGame(); return sfx.back(); } sfx.back(); return enterTab(G0.from); }
  if (a === 'lb' || a === 'rb') { G0.view = (G0.view + (a === 'rb' ? 1 : 3)) % 4; return sfx.move(); }
  if (a === 'y') return toggleFav(g);
  const go = (zone, i = 0) => { G0.zone = zone; G0.i = i; sfx.move(); };
  const toManual = () => { G0.back = { zone: G0.zone, i: G0.i }; go('manual'); };
  const slotsOr = up => hc ? (up ? 'actions' : 'media') : 'slots';
  if (G0.zone === 'manual') {   // the button under the box
    if (a === 'right') { const b = G0.back || { zone: 'actions', i: 0 }; go(b.zone, b.i); }
    else if (a === 'a') { if (hasManual(g)) return openManual(); toast(usingSS() ? 'ScreenScraper doesn’t have a manual for this game' : 'Manuals come from ScreenScraper. You can switch in Settings, Art and info.'); }
  } else if (G0.zone === 'actions') {
    if (a === 'left') { if (G0.i === 0) toManual(); else { G0.i--; sfx.move(); } }
    else if (a === 'right') { if (G0.i < acts.length - 1) { G0.i++; sfx.move(); } }
    else if (a === 'down') { const r = rec(G0.id); hc ? go('media') : go('slots', r ? r.lastSlot : 0); }
    else if (a === 'a') return gameAction();
  } else if (G0.zone === 'slots') {
    if (a === 'left') { if (G0.i === 0) toManual(); else { G0.i--; sfx.move(); } }
    else if (a === 'right') { if (G0.i < 3) { G0.i++; sfx.move(); } }
    else if (a === 'up') go('actions'); else if (a === 'down') go('media');
    else if (a === 'a') return gameAction();
  } else if (G0.zone === 'media') {
    if (a === 'left') { if (G0.i === 0) toManual(); else { G0.i--; sfx.move(); } }
    else if (a === 'right') { if (G0.i < nMedia - 1) { G0.i++; sfx.move(); } }
    else if (a === 'up') { const r = rec(G0.id); hc ? go('actions') : go('slots', r ? r.lastSlot : 0); }
    else if (a === 'down') go('ach');
    else if (a === 'a') return openMedia(G0.i);
  } else {
    if (a === 'left') toManual(); else if (a === 'up') go('media');
    else if (a === 'a') return gameAction();
  }
  fillGame();
}
function mediaInput(a) {
  const M = S.mv, n = mediaFor(byId[M.gid]).length;
  if (a === 'left' || a === 'right') { const k = clamp(M.i + (a === 'left' ? -1 : 1), 0, n - 1); if (k !== M.i) { M.i = k; M.playing = false; M.t = 0; sfx.move(); fillMedia(); } }
  else if (a === 'a') { const m = mediaFor(byId[M.gid])[M.i]; if (m.kind === 'video') { M.playing = !M.playing; sfx.select(); fillMedia(); } }
  else if (a === 'b') { M.playing = false; sfx.back(); S.screen = 'game'; show('game'); S.game.zone = 'media'; S.game.i = M.i; fillGame(); }
}
function achInput(a) {
  const n = achRows().length;
  if (a === 'up' || a === 'down') { const k = clamp(S.ach.i + (a === 'up' ? -1 : 1), 0, Math.max(0, n - 1)); if (k !== S.ach.i) { S.ach.i = k; sfx.move(); fillAch(false); } }
  else if (a === 'left' || a === 'right') { const k = clamp(S.ach.filter + (a === 'left' ? -1 : 1), 0, 2); if (k !== S.ach.filter) { S.ach.filter = k; S.ach.i = 0; sfx.tab(); fillAch(false); } }
  else if (a === 'b') {   // back to wherever the page was opened from
    sfx.back();
    if (S.ach.from === 'game') { S.screen = 'game'; show('game'); S.game.zone = 'ach'; fillGame(); }
    else enterTab('home');
  }
}
function setInput(a) {
  const M = settingsModel(), Z = S.set;
  if (Z.zone === 'nav') {
    if (a === 'up' || a === 'down') { const k = clamp(Z.cat + (a === 'up' ? -1 : 1), 0, M.length - 1); if (k !== Z.cat) { Z.cat = k; sfx.move(); } }
    else if (a === 'right' || a === 'a') { Z.zone = 'items'; Z.i = 0; sfx.move(); }
    else if (a === 'b') return closeSettings();
  } else {
    const n = M[Z.cat].items.length;
    if (a === 'up' || a === 'down') { const k = clamp(Z.i + (a === 'up' ? -1 : 1), 0, n - 1); if (k !== Z.i) { Z.i = k; sfx.move(); } }
    else if (a === 'left' || a === 'b') { Z.zone = 'nav'; sfx.move(); }
    else if (a === 'a') return settingsAct();
  }
  fillSettings();
}
const KEYS = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', Enter: 'a', ' ': 'a', Escape: 'b', Backspace: 'b',
  f: 'y', F: 'y', q: 'lb', Q: 'lb', e: 'rb', E: 'rb', h: 'home', H: 'home' };
addEventListener('keydown', e => {
  if (S.kb.open) {   // a real keyboard types straight into the on-screen one
    if (e.key.length === 1 && /[A-Za-z0-9 '&!-]/.test(e.key)) { e.preventDefault(); audio(); S.kb.typed = true; return kbType(e.key); }
    if (e.key === 'Backspace') { e.preventDefault(); S.kb.value = S.kb.value.slice(0, -1); return renderKb(); }
    if (e.key === 'Enter' && S.kb.typed) { e.preventDefault(); return kbDone(); }   // after typing, Enter finishes; otherwise it presses the selected key
  }
  const a = KEYS[e.key]; if (!a) return; e.preventDefault(); act(a);
});
const PAD = { prev: {}, hold: {} };
function pollPad(dt) {
  const pads = navigator.getGamepads ? navigator.getGamepads() : [], p = [...pads].find(Boolean); if (!p) return;
  const press = i => !!(p.buttons[i] && p.buttons[i].pressed), ax = p.axes[0] || 0, ay = p.axes[1] || 0;
  const now = { a: press(0), b: press(1), y: press(3), lb: press(4), rb: press(5), home: press(16) || (press(8) && press(9)),
    up: press(12) || ay < -.55, down: press(13) || ay > .55, left: press(14) || ax < -.55, right: press(15) || ax > .55 };
  for (const k in now) {
    if (now[k] && !PAD.prev[k]) { act(k); PAD.hold[k] = 0; }
    else if (now[k] && ['up', 'down', 'left', 'right'].includes(k)) { PAD.hold[k] += dt; if (PAD.hold[k] > .38) { PAD.hold[k] -= .11; act(k); } }
  }
  PAD.prev = now;
}

/* ================= drawing each screen ================= */
const PL = { x: 960, y: 1500, r: 700, a: 1 };
function planetTarget() {
  switch (S.screen) {
    case 'profiles': return { x: 960, y: 1560, r: 760, a: 1 };
    case 'home': return { x: 1760, y: 1300, r: 600, a: 1 };
    case 'library': return { x: 1880, y: 1320, r: 520, a: .85 };
    case 'game': return { x: 330, y: 1270, r: 720, a: 1 };
    case 'ach': return { x: 1800, y: 1340, r: 560, a: .7 };
    case 'media': case 'manual': return { x: 960, y: 1700, r: 700, a: 0 };
    case 'settings': return { x: 1780, y: 1360, r: 600, a: .55 };
    case 'launch': return { x: 1560, y: 1350, r: 820, a: .7 };
    default: return { x: 960, y: 1600, r: 700, a: 0 };
  }
}
function drawProfiles(dt, t) {
  const n = PROFILES.length + 1;
  PROFILES.forEach((p, i) => { const o = pose('prof' + i, { s: 80 }), sel = i === S.pSel;
    approach(o, { s: sel ? 112 : 80, y: sel ? -10 : 0 }, dt, 10); drawPlanet('profile' + i + p.colors[1], profileX(i, n), 505 + o.y, o.s, t, sel ? 1 : .75, p.colors); });
}
function drawHome(dt, t) {
  const items = homeItems(), H0 = S.home;
  approach(H0, { f: H0.i }, dt, 8);
  const order = items.map((id, i) => i).sort((a, b) => Math.abs(b - H0.i) - Math.abs(a - H0.i));
  const rowFocus = H0.zone === 'row' && !S.top.on;
  setCamera(); beginMeshes();
  let tileX = null, tileA = 0;
  for (const i of order) {
    const id = items[i], off = i - H0.f, foc = i === H0.i;
    const xpx = 620 + off * 320 + Math.sign(off) * Math.min(Math.abs(off), 1) * 110, ypx = foc ? 400 : 445;
    const alpha = off < 0 ? (off > -1 ? 1 + .55 * off : .45 * (1 - clamp(-off - 1, 0, 1))) : 1 - clamp((off - 4.2) / 1.2, 0, 1);
    if (id === '__all') {
      const o = pose('home:__all', { s: 0 }); approach(o, { x: xpx, y: ypx, s: foc ? 330 : 250, alpha }, dt, 10);
      drawPlate('tile', tileArt, o.x, o.y, o.s, o.s, 0, foc && rowFocus ? [1, 1, 1] : [.75, .78, .95], o.alpha);
      tileX = o; tileA = o.alpha; continue;
    }
    const g = byId[id], o = pose('home:' + id, { s: 0 }), [x, y] = pw(xpx, ypx);
    const sway = foc && rowFocus && motion() ? Math.sin(t * .9) * .12 : 0;
    approach(o, { x, y, z: foc ? .6 : 0, ry: clamp(-off * .32, -.62, .62) + sway, rx: foc ? -.06 : 0, s: cartScale(g.sys, foc ? 450 : 260),
      alpha, bright: foc ? 1 : .7, glow: foc && rowFocus ? 1 : 0 }, dt, 10);
    if (o.alpha > .01) drawCart(g, o);
  }
  endMeshes();
  if (tileX && !NODRAW) { const L = $('#allLabel'); L.style.left = tileX.x + 'px'; L.style.top = (tileX.y - 70) + 'px'; L.style.opacity = tileA; }
}
function drawLibrary(dt, t) {
  const L = S.lib, cols = 5, row = Math.floor(L.i / cols), onGrid = L.zone === 'grid' && !S.top.on;
  // the first row starts near the top of the frame and the last row ends near the bottom, leaving room for the title
  const lastRow = Math.max(0, Math.floor((L.list.length - 1) / cols)), top = .52, bottom = .4;
  approach(L, { rowF: clamp(row, top, Math.max(top, lastRow - bottom)) }, dt, 9);
  const CY = 641, ROW = 345, cx = c => 620 + c * 263.5, still = !motion();
  setFlat(); beginMeshes();   // a flat camera: no perspective, so every cartridge sits exactly in its column
  drawPlate('gridpanel', panelArt, 1147, 641, 1354, 738, 0, [.045, .06, .16], .62);
  clipRect(471, 273, 1823, 1009);
  const items = [];
  L.list.forEach((g, i) => {
    const r = Math.floor(i / cols), c = i % cols, foc = onGrid && i === L.i, o = pose('lib:' + g.id, { s: 0 });
    const ypx = CY + (r - L.rowF) * ROW, lift = foc ? 22 : 0, bob = still ? 0 : Math.sin(t * .8 + i * 1.7) * (foc ? 4 : 3);   // a slow drift, out of step with its neighbors
    const [x, y] = pw(cx(c), ypx - lift + bob);
    const vis = 1 - clamp((Math.abs(ypx - CY) - ROW) / 120, 0, 1), cut = Math.max(0, 392 - ypx, ypx - 890) / 120;
    approach(o, { x, z: foc ? .45 : 0, ry: -.2, rx: -.12, s: cartScale(g.sys, foc ? 222 : 190), alpha: clamp(vis, 0, 1) * (1 - .45 * clamp(cut, 0, 1)), bright: foc ? 1.02 : onGrid ? .62 : .8, glow: foc ? 1 : 0 }, dt, 11);
    approach(o, { y }, dt, foc ? 7 : 11);
    items.push({ g, o, foc, cxp: cx(c), base: ypx });
  });
  for (const it of items) {   // soft shadows on the panel; the selected cartridge's sits further below it
    const c = CART[it.g.sys], h = (c.h / 2 + (it.g.sys === 'gba' ? .1 : 0)) * it.o.s * PX, w = Math.max(c.w, c.h * .6) * it.o.s * PX;
    if (it.o.alpha > .01) drawPlate('shadow', shadowArt, it.cxp, it.base + h + (it.foc ? 30 : 16), w * (it.foc ? .8 : .95), it.foc ? 30 : 24, -.6, [0, 0, .02], it.o.alpha * (it.foc ? .5 : .55));
  }
  let focus = null;
  for (const it of items) { if (it.foc) focus = it; else if (it.o.alpha > .01) drawCart(it.g, it.o); }
  if (focus) {
    const { g, o, base } = focus, m = moodColors(MOOD.cur);
    drawCartGlow(g, o, hexRgb(m.accent), hexRgb(m.planet[0])); drawCart(g, o);
    if (!NODRAW) { const F = $('#lFocus'); F.style.left = focus.cxp + 'px'; F.style.top = (base - 22 + (CART[g.sys].h / 2 + (g.sys === 'gba' ? .1 : 0)) * o.s * PX + 34) + 'px'; }
  }
  unclip(); endMeshes();
}
const VIEWS = [-.42, Math.PI / 2 - .3, -Math.PI + .38, -Math.PI / 2 + .3];
function drawGamePage(dt, t) {
  const g = byId[S.game.id], o = pose('box', {}), [bw, bh] = BOX[g.sys], [x, y] = pw(470, 520);
  let ry = VIEWS[S.game.view]; while (ry - o.ry > Math.PI) ry -= Math.PI * 2; while (ry - o.ry < -Math.PI) ry += Math.PI * 2;
  approach(o, { x, y, z: 0, rx: -.05, ry: ry + (motion() ? Math.sin(t * .7) * .05 : 0), s: 600 / PX / Math.max(bw, bh), alpha: 1, bright: 1 }, dt, 7);
  setCamera(); beginMeshes(); drawBox(g, o); endMeshes();
}

/* ================= main loop ================= */
let last = performance.now(), clockTimer = 0, MANUAL = false, NODRAW = false;
window.__step = (seconds, fps = 30) => { MANUAL = true; const n = Math.max(1, Math.round(seconds * fps)); for (let i = 0; i < n; i++) { NODRAW = i < n - 1; tick(1 / fps); } NODRAW = false; };
function loop(now) {
  requestAnimationFrame(loop);   // keep running even if one frame hits an error
  const dt = Math.min(.05, (now - last) / 1000); last = now;
  if (!MANUAL) { try { tick(dt); } catch (e) { console.error(e); } }
}
function tick(dt) {
  pollPad(dt);
  if (motion()) S.time += dt;
  MOOD.t = Math.min(1, MOOD.t + dt / .8);
  for (let i = 0; i < 3; i++) RIM[i] += (RIM_T[i] - RIM[i]) * Math.min(1, dt * 4);
  if (S.seq) seqUpdate(dt);
  if (S.play) playUpdate(dt);
  if (S.screen === 'media') mvUpdate(dt);
  approach(PL, planetTarget(), dt, 3);
  clockTimer -= dt; if (clockTimer <= 0) { clockTimer = 1; const c = clockText(); $('#clock').textContent = c; $('#clockBig').textContent = c; }
  if (!NODRAW) { gl.viewport(0, 0, cv.width, cv.height); gl.clearColor(.027, .043, .118, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT); }
  const pan = S.screen === 'home' ? S.home.f * .006 : S.screen === 'library' ? S.lib.rowF * .02 : 0;
  drawBackdrop(S.time, { bright: S.screen === 'play' ? .75 : S.screen === 'media' || S.screen === 'manual' ? .45 : 1, pan, planet: PL.a > .01 ? PL : null });
  if (S.screen === 'profiles') drawProfiles(dt, S.time);
  else if (S.screen === 'home') drawHome(dt, S.time);
  else if (S.screen === 'library') drawLibrary(dt, S.time);
  else if (S.screen === 'game') drawGamePage(dt, S.time);
  else if (S.screen === 'launch' && S.seq) seqDraw();
}
async function boot() {
  fit();
  if (!gl) { $('#nogl').style.display = 'grid'; return; }
  initGL(); initMeshes();
  try { await Promise.race([Promise.all([document.fonts.load('600 40px Unbounded'), document.fonts.load('600 20px Manrope')]), new Promise(r => setTimeout(r, 2000))]); } catch (e) { }
  enterProfiles();
  requestAnimationFrame(loop);
}
boot();
</script>
</body>
</html>
