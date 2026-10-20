/* ================= input ================= */
function act(a) {
  audio(); musicEnsure();
  if (a === 'home') { closeDlg(); S.kb.open = false; renderKb(); if (S.screen === 'play') return homeFromPlay(); if (S.screen === 'setup' || S.seq) return;
    if (S.screen === 'game') { if (!S.game.closing) { S.game.zone = 'actions'; closeGame('home'); } return; } S.home.i = 0; S.home.zone = 'row'; sfx.tab(); return enterTab('home'); }
  if (S.kb.open) {
    const k = S.kb; if (a !== 'a') k.typed = false;
    if (a === 'up' || a === 'down') { const r = clamp(k.r + (a === 'up' ? -1 : 1), 0, KB_ROWS.length - 1); if (r !== k.r) { k.c = kbNearest(r, kbCenter(k.r, k.c)); k.r = r; sfx.move(); renderKb(); } }
    else if (a === 'left' || a === 'right') { k.c = (k.c + (a === 'left' ? -1 : 1) + KB_ROWS[k.r].length) % KB_ROWS[k.r].length; sfx.move(); renderKb(); }
    else if (a === 'a') kbPress(); else if (a === 'y') { k.value = k.value.slice(0, -1); renderKb(); } else if (a === 'b') { k.open = false; renderKb(); sfx.back(); }
    return;
  }
  if (S.dlg.open) { const d = S.dlg;
    if (a === 'up' || a === 'down') { d.i = clamp(d.i + (a === 'up' ? -1 : 1), 0, d.items.length - 1); sfx.move(); renderDlg(); }
    else if (a === 'a') d.onPick(d.i); else if (a === 'b') { closeDlg(); sfx.back(); }
    return; }
  if (S.seq) return;
  const sc = S.screen;
  if (sc === 'setup') {
    if (S.setup.quick) return;
    const n = setupOpts().length;
    if (['up', 'down', 'left', 'right'].includes(a)) { const k = clamp(S.setup.i + (a === 'up' || a === 'left' ? -1 : 1), 0, n - 1); if (k !== S.setup.i) { S.setup.i = k; sfx.move(); fillSetup(); } }
    else if (a === 'a') setupAct(); else if (a === 'b' && S.setup.step > 0) { S.setup.step--; S.setup.i = 0; sfx.back(); fillSetup(); }
    return;
  }
  if (sc === 'play') return;
  if (TABS.includes(sc)) {
    if ((a === 'lb' || a === 'rb') && !(sc === 'museum' && S.mu.zone === 'stage' && !S.top.on)) { const k = (TABS.indexOf(sc) + (a === 'rb' ? 1 : TABS.length - 1)) % TABS.length; sfx.tab(); return enterTab(TABS[k]); }
    if (S.top.on) return topInput(a);
    return sc === 'home' ? homeInput(a) : sc === 'library' ? libInput(a) : sc === 'museum' ? museumInput(a) : appsInput(a);
  }
  if (sc === 'game') return gameInput(a);
  if (sc === 'ach') return achInput(a);
  if (sc === 'media') return mediaInput(a);
  if (sc === 'manual') return manualInput(a);
  if (sc === 'settings') return setInput(a);
}
function topInput(a) {
  if (a === 'left' || a === 'right') { const k = clamp(S.top.i + (a === 'left' ? -1 : 1), 0, TOP.length - 1); if (k !== S.top.i) { S.top.i = k; sfx.move(); renderTop(); } }
  else if (a === 'down' || a === 'b') { S.top.on = false; sfx.move(); refreshTab(); }
  else if (a === 'a') { const k = TOP[S.top.i];
    if (k === 'settings') return openSettings(0); if (k === 'profile') return openSettings(0);
    if (k !== S.tab) { sfx.tab(); enterTab(k); S.top.on = true; S.top.i = TOP.indexOf(k); refreshTab(); } }
}
function homeInput(a) {
  const H0 = S.home, items = homeItems(), id = items[H0.i];
  if (H0.zone === 'row') {
    if (a === 'left' || a === 'right') { const k = clamp(H0.i + (a === 'left' ? -1 : 1), 0, items.length - 1); if (k !== H0.i) { H0.i = k; H0.card = 0; sfx.move(); refreshHome(); } }
    else if (a === 'up') { S.top.on = true; S.top.i = 0; sfx.move(); refreshHome(); }
    else if (a === 'down' && id !== '__all') { H0.zone = 'cards'; H0.card = 0; sfx.move(); refreshHome(); }
    else if (a === 'a') { if (id === '__all') { sfx.select(); enterTab('library'); } else { const g = byId[id], r = rec(id); launchGame(g, r.lastSlot, isRA(emuFor(g)) && r.slots.some(Boolean)); } }
    else if (a === 'y' && id !== '__all') toggleFav(byId[id]);
  } else {
    if (a === 'left' || a === 'right') { const k = clamp(H0.card + (a === 'left' ? -1 : 1), 0, 2); if (k !== H0.card) { H0.card = k; sfx.move(); refreshHome(); } }
    else if (a === 'up' || a === 'b') { H0.zone = 'row'; sfx.move(); refreshHome(); }
    else if (a === 'a') { const c = homeCards(id)[H0.card].k, g = byId[id];
      if (c === 'box') openGame(id, 'home:' + id, 'home'); else if (c === 'ach') { if (g.sys !== 'android') P.ra ? openAch(id) : openSettings(CATS.indexOf('Achievements')); } else { openGame(id, 'home:' + id, 'home'); S.game.zone = isRA(emuFor(g)) ? 'saves' : 'actions'; S.game.i = 0; fillGame(); } }
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
    const stops = railStops(), at = stops.indexOf(L.rail), item = railItems()[L.rail];
    if (a === 'up' && at === 0) { S.top.on = true; S.top.i = 1; sfx.move(); }
    else if (a === 'up' || a === 'down') { const k = clamp(at + (a === 'up' ? -1 : 1), 0, stops.length - 1); if (stops[k] !== L.rail) { L.rail = stops[k]; L.i = 0; L.rowF = .6; sfx.move(); return refreshLibrary(true); } }
    else if (a === 'y' && item.custom) return collectionOptions(item.custom);
    else if ((a === 'right' || a === 'a') && n) { L.zone = 'grid'; sfx.move(); }
  } else {
    if (a === 'left') { L.zone = 'rail'; sfx.move(); } else if (a === 'up') { S.top.on = true; S.top.i = 1; sfx.move(); }
    else if (a === 'down' || a === 'b') { if (n) L.zone = 'grid'; sfx.move(); }
    else if (a === 'a') { L.sort = (L.sort + 1) % SORTS.length; L.i = 0; sfx.select(); return refreshLibrary(true); }
  }
  refreshLibrary(false);
}
function appsInput(a) {
  const cols = 7, n = APPS.length, i = S.apps.i;
  if (a === 'left' && i % cols > 0) { S.apps.i--; sfx.move(); } else if (a === 'right' && i % cols < cols - 1 && i + 1 < n) { S.apps.i++; sfx.move(); }
  else if (a === 'up') { if (i < cols) { S.top.on = true; S.top.i = 2; } else S.apps.i -= cols; sfx.move(); }
  else if (a === 'down' && i + cols < n) { S.apps.i += cols; sfx.move(); }
  else if (a === 'a') { const app = APPS[i]; if (ANDROID_GAMES.has(app.name)) { const g = GAMES.find(x => x.title === app.name); return startPlayDirect(g); } toast(`Opens ${app.name}`); }
  else if (a === 'y') return appOptions();
  refreshApps();
}
function startPlayDirect(g) { startLaunch(g, 0, false, 'apps'); }
function actRowMove(dir) {   // the buttons wrap onto rows: up and down move to the nearest button in the next row
  const els = [...$('#gpin .actions').children], cur = els[S.game.i]; if (!cur) return false;
  const tops = [...new Set(els.map(e => e.offsetTop))].sort((a, b) => a - b), to = tops[tops.indexOf(cur.offsetTop) + dir];
  if (to == null) return false;
  const cx = cur.offsetLeft + cur.offsetWidth / 2; let best = S.game.i, bd = 1e9;
  els.forEach((e, i) => { if (e.offsetTop !== to) return; const d = Math.abs(e.offsetLeft + e.offsetWidth / 2 - cx); if (d < bd) { bd = d; best = i; } });
  S.game.i = best; return true;
}
function gameInput(a) {
  if (S.game.closing) return;
  $('#gpin').classList.remove('enter');
  const G0 = S.game, g = byId[G0.id], acts = gameActions(), ra = isRA(emuFor(g)), nMedia = mediaFor(g).length;
  if (G0.turn) {   // turning the closed box
    if (a === 'left' || a === 'right') { G0.ry += (a === 'left' ? -1 : 1) * .45; sfx.move(); }
    else if (a === 'up' || a === 'down') { G0.rx = clamp(G0.rx + (a === 'up' ? -1 : 1) * .3, -.9, .9); sfx.move(); }
    else if (a === 'lb' || a === 'rb') { const views = [0, Math.PI / 2, Math.PI], cur = views.reduce((b, v, i) => Math.abs(((G0.ry - v) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI) < Math.abs(((G0.ry - views[b]) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI) ? i : b, 0);
      const next = views[(cur + (a === 'rb' ? 1 : 2)) % 3]; let d = next - G0.ry; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; G0.ry += d; G0.rx = 0; sfx.move(); }
    else if (a === 'b' || a === 'a') { G0.turn = false; G0.zone = 'actions'; G0.i = 0; sfx.select(); fillGame(); }
    return;
  }
  if (a === 'y') return toggleFav(g);
  if (a === 'b') { if (G0.zone !== 'actions') { G0.zone = 'actions'; G0.i = 0; fillGame(); return sfx.back(); } return closeGame(); }
  const go = (zone, i = 0) => { G0.zone = zone; G0.i = i; sfx.move(); };
  const z = G0.zone;
  if (z === 'manual') { if (a === 'right') go('cart'); else if (a === 'a') return gameAction(); }
  else if (z === 'cart') { if (a === 'left') go('manual'); else if (a === 'right') go('actions'); else if (a === 'a') return gameAction(); }
  else if (z === 'actions') {
    if (a === 'left') { if (G0.i === 0) go('cart'); else { G0.i--; sfx.move(); } } else if (a === 'right') { if (G0.i < acts.length - 1) { G0.i++; sfx.move(); } }
    else if (a === 'up') { if (actRowMove(-1)) sfx.move(); }
    else if (a === 'down') { if (actRowMove(1)) sfx.move(); else go('saves', ra ? (rec(G0.id) || { lastSlot: 0 }).lastSlot : 0); } else if (a === 'a') return gameAction();
  } else if (z === 'saves') {
    if (a === 'left') { if (!ra || G0.i === 0) go('cart'); else { G0.i--; sfx.move(); } } else if (a === 'right' && ra && G0.i < 3) { G0.i++; sfx.move(); }
    else if (a === 'up') go('actions'); else if (a === 'down') go('media'); else if (a === 'a') return gameAction();
  } else if (z === 'media') {
    if (a === 'left') { if (G0.i === 0) go('cart'); else { G0.i--; sfx.move(); } } else if (a === 'right' && G0.i < nMedia - 1) { G0.i++; sfx.move(); }
    else if (a === 'up') go('saves', ra ? (rec(G0.id) || { lastSlot: 0 }).lastSlot : 0); else if (a === 'down' && g.sys !== 'android') go('ach'); else if (a === 'a') return gameAction();
  } else { if (a === 'up') go('media'); else if (a === 'left') go('cart'); else if (a === 'a') return gameAction(); }
  fillGame();
}
function achInput(a) {
  const n = achRows().length;
  if (a === 'up' || a === 'down') { const k = clamp(S.ach.i + (a === 'up' ? -1 : 1), 0, Math.max(0, n - 1)); if (k !== S.ach.i) { S.ach.i = k; sfx.move(); fillAch(false); } }
  else if (a === 'left' || a === 'right') { const k = clamp(S.ach.filter + (a === 'left' ? -1 : 1), 0, 2); if (k !== S.ach.filter) { S.ach.filter = k; S.ach.i = 0; sfx.tab(); fillAch(false); } }
  else if (a === 'b') { sfx.back(); if (S.ach.from === 'game') { S.screen = 'game'; show('game'); S.game.zone = 'ach'; fillGame(); } else enterTab('home'); }
}
function mediaInput(a) {
  const M = S.mv, n = mediaFor(byId[M.gid]).length;
  if (a === 'left' || a === 'right') { const k = clamp(M.i + (a === 'left' ? -1 : 1), 0, n - 1); if (k !== M.i) { M.i = k; M.playing = false; M.t = 0; sfx.move(); fillMedia(); } }
  else if (a === 'a') { const m = mediaFor(byId[M.gid])[M.i]; if (m.kind === 'video') { M.playing = !M.playing; sfx.select(); fillMedia(); } }
  else if (a === 'b') { M.playing = false; sfx.back(); S.screen = 'game'; show('game'); S.game.zone = 'media'; S.game.i = M.i; fillGame(); }
}
function setInput(a) {
  const Z = S.set, M = settingsModel(), cat = Z.game ? 0 : Z.cat, items = M[cat].items, it = items[Z.i];
  if (Z.zone === 'nav') {
    if ((a === 'up' || a === 'down') && !Z.game) { const k = clamp(Z.cat + (a === 'up' ? -1 : 1), 0, M.length - 1); if (k !== Z.cat) { Z.cat = k; Z.sub = null; sfx.move(); } }
    else if (a === 'right' || a === 'a') { Z.zone = 'items'; Z.i = 0; sfx.move(); } else if (a === 'b') return closeSettings();
  } else {
    if (it && it.themes && (a === 'left' || a === 'right')) { Z.ti = clamp(Z.ti + (a === 'left' ? -1 : 1), 0, Object.keys(THEMES).length - 1); sfx.move(); }
    else if (it && it.themes && ((a === 'up' && Z.ti >= 4) || (a === 'down' && Math.floor(Z.ti / 4) < Math.floor((Object.keys(THEMES).length - 1) / 4)))) {   // up and down move between rows of themes
      Z.ti = a === 'up' ? Z.ti - 4 : Math.min(Z.ti + 4, Object.keys(THEMES).length - 1); sfx.move(); }
    else if (a === 'up' || a === 'down') { const k = clamp(Z.i + (a === 'up' ? -1 : 1), 0, items.length - 1); if (k !== Z.i) { Z.i = k; sfx.move(); } }
    else if (a === 'left' && !Z.game) { Z.zone = 'nav'; sfx.move(); }
    else if (a === 'b') { if (Z.sub) { const k = Z.sub; Z.sub = null; Z.i = SYS_ORDER.indexOf(k); sfx.back(); } else if (Z.game) return closeSettings(); else { Z.zone = 'nav'; sfx.move(); } }
    else if (a === 'a') return settingsAct();
  }
  fillSettings();
}
const KEYS = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', Enter: 'a', ' ': 'a', Escape: 'b', Backspace: 'b', f: 'y', F: 'y', q: 'lb', Q: 'lb', e: 'rb', E: 'rb', h: 'home', H: 'home' };
addEventListener('keydown', e => {
  if (S.kb.open) {
    if (e.key.length === 1 && /[A-Za-z0-9 '&!-]/.test(e.key)) { e.preventDefault(); audio(); S.kb.typed = true; return kbType(e.key); }
    if (e.key === 'Backspace') { e.preventDefault(); S.kb.value = S.kb.value.slice(0, -1); return renderKb(); }
    if (e.key === 'Enter' && S.kb.typed) { e.preventDefault(); return kbDone(); }
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

/* ================= drawing ================= */
function project(p) { const v = [VP[0] * p[0] + VP[4] * p[1] + VP[8] * p[2] + VP[12], VP[1] * p[0] + VP[5] * p[1] + VP[9] * p[2] + VP[13], VP[3] * p[0] + VP[7] * p[1] + VP[11] * p[2] + VP[15]];
  return [(v[0] / v[2] + 1) / 2 * W, (1 - v[1] / v[2]) / 2 * H]; }
function apply(M, p) { return [M[0] * p[0] + M[4] * p[1] + M[8] * p[2] + M[12], M[1] * p[0] + M[5] * p[1] + M[9] * p[2] + M[13], M[2] * p[0] + M[6] * p[1] + M[10] * p[2] + M[14]]; }
const lightTheme = () => ['light', 'dawn', 'bloom'].includes(P.prefs.theme);
function shadowUnder(xpx, ypx, wpx, alpha, far) { drawPlate('shadow', shadowArt, xpx, ypx, wpx, far ? 24 : 18, -.8, lightTheme() ? [.25, .2, .35] : [0, 0, .02], alpha * (lightTheme() ? .35 : .55)); }
const PL = { x: 667, y: 1100, r: 520, a: 1 };
function planetTarget() {
  const sc = S.screen === 'game' && S.game.closing ? S.game.then : S.screen === 'game' && S.game.fly ? 'game' : S.screen;
  switch (sc) {
    case 'setup': return { x: 667, y: 1080, r: 560, a: 1 };
    case 'home': return { x: 1220, y: 900, r: 420, a: 1 };
    case 'library': case 'apps': return { x: 1300, y: 920, r: 380, a: .85 };
    case 'museum': return { x: 560, y: 1150, r: 470, a: .55 };
    case 'game': return { x: 260, y: 940, r: 480, a: .9 };
    case 'settings': case 'ach': return { x: 1260, y: 940, r: 400, a: .5 };
    default: return { x: 667, y: 1200, r: 500, a: 0 };
  }
}
function drawHome(dt, t) {
  const items = homeItems(), H0 = S.home, rowFocus = H0.zone === 'row' && !S.top.on, q = S.seq;
  approach(H0, { f: H0.i }, dt, 8);
  setFlat(); beginMeshes();
  const drawn = [];
  items.forEach((id, i) => {
    const off = i - H0.f, foc = i === H0.i, xpx = 270 + off * 196 + Math.sign(off) * Math.min(Math.abs(off), 1) * 70;
    const alpha = off < 0 ? (off > -1 ? 1 + .6 * off : .4 * (1 - clamp(-off - 1, 0, 1))) : 1 - clamp((off - 5) / 1.2, 0, 1);
    const bob = motion() ? Math.sin(t * .8 + i * 1.7) * (foc ? 4 : 3) : 0, ypx = (foc ? 238 : 262) + bob;
    if (id === '__all') { const o = pose('home:__all', { s: 0 }); approach(o, { x: xpx, y: 262, s: foc ? 190 : 140, alpha }, dt, 10);
      drawPlate('tile', tileArt, o.x, o.y, o.s, o.s, 0, foc && rowFocus ? [1, 1, 1] : lightTheme() ? [.3, .32, .4] : [.75, .78, .95], o.alpha);
      if (!NODRAW) { const L = $('#hTile'); L.style.left = o.x + 'px'; L.style.top = (o.y - 22) + 'px'; L.style.opacity = o.alpha; L.classList.add('on'); }
      return; }
    const g = byId[id], o = pose('home:' + id, { s: 0, y: 3 }), [x, y] = pw(xpx, ypx);
    approach(o, { x, z: foc ? .5 : 0, ry: -.2, rx: -.1, s: mediaScale(g.sys, foc ? 236 : 150), alpha, bright: foc ? 1.02 : .72, glow: foc && rowFocus ? 1 : 0 }, dt, 10);
    approach(o, { y }, dt, foc ? 7 : 10);
    const M = M4.trs(o.x, o.y, o.z, o.rx, o.ry, 0, o.s);
    if ((q && q.g.id === id) || S.skipId === id) return;   // it's drawn elsewhere: in the launch scene, or flying to its box
    const hpx = CART[g.sys].h / 2 * o.s * PX;
    if (o.alpha > .01) { shadowUnder(xpx, ypx - bob + hpx + (foc ? 24 : 12), Math.max(CART[g.sys].w, .5) * o.s * PX * .9, o.alpha, foc); drawn.push([g, M, o, foc]); }
  });
  for (const [g, M, o, foc] of drawn) { if (foc && rowFocus) { const m = moodColors(MOOD.cur); drawCartGlowM(g, M, o.s, hexRgb(m.accent), hexRgb(m.planet[0]), o.alpha); } drawCartM(g, M, o); }
  endMeshes();
  if (!NODRAW && items[H0.i] !== '__all') $('#hTile').classList.toggle('on', true);
}
function drawLibrary(dt, t) {
  const L = S.lib, cols = 5, row = Math.floor(L.i / cols), onGrid = L.zone === 'grid' && !S.top.on;
  const lastRow = Math.max(0, Math.floor((L.list.length - 1) / cols)), top = .58, bottom = .49;
  approach(L, { rowF: clamp(row, top, Math.max(top, lastRow - bottom)) }, dt, 9);
  const CY = 420, ROW = 300, cx = c => 363 + c * 206, s0 = BOXK, still = !motion();
  setFlat(); beginMeshes();
  drawPlate('gridpanel', panelArt, 775, 420, 1062, 600, 0, lightTheme() ? [1, 1, 1] : [.035, .045, .11], lightTheme() ? .4 : .55);
  clipRect(245, 121, 1305, 719);
  let focus = null;
  L.list.forEach((g, i) => {
    const r = Math.floor(i / cols), c = i % cols, foc = onGrid && i === L.i, o = pose('lib:' + g.id, { s: 0 });
    const ypx = CY + (r - L.rowF) * ROW, lift = foc ? 14 : 0, bob = still ? 0 : Math.sin(t * .8 + i * 1.7) * (foc ? 4 : 3), [x, y] = pw(cx(c), ypx - lift + bob);
    const cut = Math.max(0, 260 - ypx, ypx - 600) / 90, vis = 1 - clamp((Math.abs(ypx - CY) - ROW) / 90, 0, 1);
    approach(o, { x, z: foc ? .4 : 0, ry: .3, rx: -.08, s: s0 * (foc ? 1.08 : 1), alpha: clamp(vis, 0, 1) * (1 - .45 * clamp(cut, 0, 1)), bright: foc ? 1.03 : onGrid ? .66 : .82 }, dt, 11);
    approach(o, { y }, dt, foc ? 7 : 11);
    const [bw, bh] = BOX[g.sys];
    if (S.skipId === g.id) return;   // it's open in front
    if (o.alpha > .01) shadowUnder(cx(c), ypx + bh / 2 * o.s * PX + (foc ? 22 : 12), bw * o.s * PX * .95, o.alpha, foc);
    if (foc) focus = { g, o, c, ypx: ypx - lift, bh }; else if (o.alpha > .01) drawCase(g, o, 0);
  });
  if (focus) { const m = moodColors(MOOD.cur); drawCaseGlow(focus.g, focus.o, hexRgb(m.accent), hexRgb(m.planet[0])); drawCase(focus.g, focus.o, 0);
    if (!NODRAW) { const F = $('#lFocus'); F.style.left = cx(focus.c) + 'px'; F.style.top = (focus.ypx + focus.bh / 2 * focus.o.s * PX + 26) + 'px'; } }
  unclip(); endMeshes();
}
function lerpPose(a, b, k) { let d = b.ry - a.ry; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI;
  return { x: lerp(a.x, b.x, k), y: lerp(a.y, b.y, k), z: lerp(a.z, b.z, k), rx: lerp(a.rx, b.rx, k), ry: a.ry + d * k, s: lerp(a.s, b.s, k) }; }
function drawScrim(alpha) {   // the theme's background over everything drawn so far, so a screen can fade out behind another
  if (NODRAW) return; if (alpha <= .001) return gl.clear(gl.DEPTH_BUFFER_BIT); setFlat(); beginMeshes(); const b = hexRgb(getComputedStyle(stage).getPropertyValue('--bg').trim() || '#070b1e');
  drawPlate('scrim', panelArt, 667, 375, 1500, 900, 3, b, alpha * .94); endMeshes(); gl.clear(gl.DEPTH_BUFFER_BIT);
}
function drawShade(alpha) {   // darkens everything drawn so far
  if (NODRAW) return; if (alpha <= .001) return gl.clear(gl.DEPTH_BUFFER_BIT);
  setFlat(); beginMeshes(); drawPlate('scrim', panelArt, 667, 375, 1500, 900, 3, [0, 0, .01], alpha); endMeshes(); gl.clear(gl.DEPTH_BUFFER_BIT);
}
function drawGamePage(dt, t) {
  const G0 = S.game, g = byId[G0.id], o = pose('case', {}), m = caseModel(g.sys), [W0, H0] = [m.W, m.H];
  const sOpen = Math.min(620 / (2 * W0 * PX), 520 / (H0 * PX)), s = G0.turn ? Math.min(sOpen * 1.35, 430 / (Math.max(W0, H0) * PX)) : sOpen;
  const [cxw, cyw] = pw(345, 392), openT = G0.turn ? 0 : 1;
  // Opening from Home: the open box slides in from the left while the cartridge lifts off Home and hovers over the
  // tray, then it lowers straight in. Closing to Home is the reverse: up out of the tray, box out to the left, back to Home.
  const home = G0.from === 'home' && (G0.fly || G0.closing), tx = cxw + m.W / 2 * s, offLeft = tx - 9;
  const trayAt = M4.mul(M4.trs(tx, cyw, 0, -.18, 0, 0, s), M4.trs(0, m.by, m.mediaZ, 0, 0, 0, 1)), at0 = M => { const p = apply(M, [0, 0, 0]); return { x: p[0], y: p[1], z: p[2], rx: -.18, ry: 0, s }; };
  const restPose = at0(trayAt), hoverPose = at0(M4.mul(trayAt, M4.trs(0, 0, .6, 0, 0, 0, 1)));
  if (G0.closing && G0.from === 'home') {
    const C = G0.closing; C.t += dt; o.x = lerp(tx, offLeft, Math.pow(clamp((C.t - .3) / .5, 0, 1), 2));
    if (C.t > .85) { G0.closing = null; S.skipId = null; $('#s-game').classList.remove('closing'); return enterTab(G0.then); }
  } else if (G0.closing) {   // close the cover first, then glide back to where the box came from
    const C = G0.closing; C.t += dt; approach(o, { open: 0 }, dt, 8);
    const T = C.t > .25 ? closeGameTarget(g) : null;
    if (T) approach(o, T, dt, 7); else approach(o, { x: cxw, y: cyw, rx: -.08, ry: 0 }, dt, 6);
    if (C.t > .95 || (!closeGameTarget(g) && C.t > .5)) {
      G0.closing = null; $('#s-game').classList.remove('closing');
      if (G0.from === 'library' && G0.then === 'library') Object.assign(pose('lib:' + g.id, {}), { x: o.x, y: o.y, z: o.z, rx: o.rx, ry: o.ry, s: o.s });
      return enterTab(TABS.includes(G0.then) ? G0.then : 'home');
    }
  } else {
    approach(o, { open: openT }, dt, 5.5);
    approach(o, { x: cxw + (m.W / 2) * s * o.open, y: cyw, z: 0, s, alpha: 1, bright: 1, rx: G0.turn ? G0.rx : -.18, ry: G0.turn ? G0.ry : 0 }, dt, 6);
    if (G0.fly) o.x = lerp(offLeft, tx, easeOut(G0.fly.t / .6));
  }
  // The screen the box came from stays behind it, faded far back, and comes forward again as the box closes
  if (G0.fly) G0.fly.t += dt;
  approach(G0, { dim: G0.closing ? 0 : 1 }, dt, G0.closing ? 4.5 : 5);
  const under = G0.closing ? G0.then : G0.from;
  if (under === 'home' || under === 'library') {
    S.skipId = g.id; UNDER = 1 - .55 * G0.dim;
    under === 'home' ? drawHome(dt, t) : drawLibrary(dt, t);
    UNDER = 1;
  }
  drawShade(G0.dim * (lightTheme() ? .32 : .58));   // and a dark veil over the whole background, planet and all
  setCamera(0, CAMZ, 0); beginMeshes();
  const q = S.seq && S.seq.g === g ? S.seq : null;
  drawCase(g, o, o.open, 0, !!q || home);   // while launching, coming back or flying to and from Home, the media is drawn on its own
  const M = M4.trs(o.x, o.y, o.z, o.rx, o.ry, 0, o.s), top = m.D / 2 - m.ct, d = CART[g.sys].d, trayM = M4.mul(M, M4.trs(0, m.by, m.mediaZ, 0, 0, 0, 1));
  if (home && !q) {   // the cartridge between its spot on Home, the air over the tray, and the tray
    const h = anim.get('home:' + g.id), there = h ? { x: h.x, y: h.y, z: 0, rx: h.rx, ry: h.ry, s: h.s } : hoverPose;
    let P0;
    if (G0.fly) { const t1 = G0.fly.t, from = G0.fly.from || there;
      P0 = t1 < .6 ? lerpPose(from, hoverPose, ease(t1 / .6)) : lerpPose(hoverPose, restPose, ease((t1 - .6) / .3));
      if (t1 > .88 && !G0.fly.click) { G0.fly.click = 1; thud(0, .09); }
    } else { const t1 = G0.closing.t; P0 = t1 < .3 ? lerpPose(restPose, hoverPose, ease(t1 / .3)) : lerpPose(hoverPose, there, ease((t1 - .3) / .5)); }
    drawCartM(g, M4.trs(P0.x, P0.y, P0.z, P0.rx, P0.ry, 0, P0.s), { alpha: 1, bright: 1, s: P0.s });
    if (G0.fly && G0.fly.t > .95) G0.fly = null;
  }
  const mc = moodColors(MOOD.cur), c1 = hexRgb(mc.accent), c2 = hexRgb(mc.planet[0]);
  if (!G0.turn && !G0.closing && o.open > .9 && !q) {
    if (G0.zone === 'cart') drawCartGlowM(g, trayM, o.s, c1, c2);
    if (G0.zone === 'manual') { const CM = M4.mul(M, coverMatrix(m, o.open)), [mw, mh] = m.manualSize;
      if (!m.manRing) m.manRing = ringMesh(shape(mw, mh, [.012, .012, .012, .012]));
      gl.disable(gl.CULL_FACE); gl.depthMask(false); drawPart(m.manRing, M4.mul(CM, M4.trs(0, m.by, top - .016, 0, Math.PI, 0, 1)), { col: c1, emit: c2, alpha: .95, hull: true, grow: 20 / (PX * o.s) }); gl.enable(gl.CULL_FACE); gl.depthMask(true); }
  }
  endMeshes();
  if (!NODRAW) {   // a label over the object you're on
    const L = $('#objLabel'), on = !G0.turn && !G0.closing && o.open > .9 && (G0.zone === 'cart' || G0.zone === 'manual') && !q;
    L.classList.toggle('on', on);
    if (on) {
      const CM = M4.mul(M, coverMatrix(m, o.open)), pt = G0.zone === 'cart' ? apply(trayM, [0, CART[g.sys].h / 2 + .08, 0]) : apply(CM, [0, m.by + m.manualSize[1] / 2 + .08, top]);
      const [px, py] = project(pt); L.style.left = px + 'px'; L.style.top = (py - 34) + 'px';
      const ra = isRA(emuFor(g)), r = rec(g.id);
      L.textContent = G0.zone === 'cart' ? (ra && r && r.slots.some(Boolean) ? 'Resume' : 'Play') : hasManual(g) ? 'Read the manual' : 'No manual';
    }
  }
}

/* ================= main loop ================= */
let last = performance.now(), clockTimer = 0, MANUAL = false, NODRAW = false, UNDER = 1;   // UNDER fades everything drawn behind an open box
window.__step = (seconds, fps = 30) => { MANUAL = true; const n = Math.max(1, Math.round(seconds * fps)); for (let i = 0; i < n; i++) { NODRAW = i < n - 1; tick(1 / fps); } NODRAW = false; };
function loop(now) { requestAnimationFrame(loop); const dt = Math.min(.05, (now - last) / 1000); last = now; if (!MANUAL) { try { tick(dt); } catch (e) { console.error(e); } } }
function tick(dt) {
  pollPad(dt);
  if (motion()) S.time += dt;
  MOOD.t = Math.min(1, MOOD.t + dt / .8);
  for (let i = 0; i < 3; i++) RIM[i] += (RIM_T[i] - RIM[i]) * Math.min(1, dt * 4);
  if (S.seq) seqUpdate(dt);
  musicDuck();
  if (S.play) playUpdate(dt);
  if (S.screen === 'media') mvUpdate(dt);
  approach(PL, planetTarget(), dt, 3);
  clockTimer -= dt; if (clockTimer <= 0) { clockTimer = 1; $('#clock').textContent = clockText(); }
  if (!NODRAW) { gl.viewport(0, 0, cv.width, cv.height); const b = hexRgb(getComputedStyle(stage).getPropertyValue('--bg').trim() || '#070b1e'); gl.clearColor(b[0], b[1], b[2], 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT); }
  const dim = ['media', 'manual', 'play'].includes(S.screen) ? .45 : 1;
  drawBackdrop(S.time, { bright: dim, pan: S.screen === 'home' ? S.home.f * .006 : S.screen === 'library' ? S.lib.rowF * .02 : 0, planet: PL.a > .01 ? PL : null });
  if (S.screen === 'home') drawHome(dt, S.time);
  else if (S.screen === 'library') drawLibrary(dt, S.time);
  else if (S.screen === 'game') drawGamePage(dt, S.time);
  else if (S.screen === 'museum') drawMuseum(dt);
  else if (S.screen === 'launch' && S.seq) drawLaunch(dt);
}
async function boot() {
  fit();
  if (!gl) { document.body.insertAdjacentHTML('beforeend', '<p style="padding:40px">This prototype needs WebGL.</p>'); return; }
  initGL(); initMeshes(); applyTheme(P.prefs.theme);
  try { await Promise.race([Promise.all([document.fonts.load('600 40px Unbounded'), document.fonts.load('600 20px Manrope')]), new Promise(r => setTimeout(r, 2000))]); } catch (e) { }
  enterSetup();
  requestAnimationFrame(loop);
}
boot();
</script>
</body>
</html>
