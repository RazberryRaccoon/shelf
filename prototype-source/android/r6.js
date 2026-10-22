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
    if ((a === 'lb' || a === 'rb') && !(sc === 'museum' && S.mu.zone === 'stage' && !S.top.on)) {   // L and R step through Home, Library, Apps and Museum
      return cycleScreen(sc, a === 'rb' ? 1 : -1); }
    if (S.top.on) return topInput(a);
    return sc === 'home' ? homeInput(a) : sc === 'library' ? libInput(a) : sc === 'museum' ? museumInput(a) : appsInput(a);
  }
  if (sc === 'game') return gameInput(a);
  if (sc === 'ach') return achInput(a);
  if (sc === 'media') return mediaInput(a);
  if (sc === 'manual') return manualInput(a);
  if (sc === 'settings') { if ((a === 'lb' || a === 'rb') && !S.set.game && S.set.back !== 'game') return cycleScreen('settings', a === 'rb' ? 1 : -1); return setInput(a); }
}
const CYCLE = [...TABS, 'settings'];   // L and R step through these, Home first, wrapping round
function cycleScreen(from, dir) {
  const k = CYCLE[(CYCLE.indexOf(from) + dir + CYCLE.length) % CYCLE.length]; sfx.tab(); S.top.on = false;
  if (k === 'settings') return openSettings(0);
  if (from === 'settings') S.set.back = k;
  enterTab(k);
}
function topInput(a) {
  if (a === 'left' || a === 'right') { const k = clamp(S.top.i + (a === 'left' ? -1 : 1), 0, TOP.length - 1); if (k !== S.top.i) { S.top.i = k; sfx.move(); renderTop(); } }
  else if (a === 'down') { S.top.on = false; sfx.move(); refreshTab(); }
  else if (a === 'b') { S.top.on = false; if (S.screen !== 'home') { sfx.back(); return enterTab('home'); } sfx.move(); refreshTab(); }
  else if (a === 'a') { const k = TOP[S.top.i];
    if (k === 'settings') return openSettings(0); if (k === 'profile') return openSettings(CATS.indexOf('Profile'));
    sfx.tab(); if (k !== S.tab) enterTab(k); else { S.top.on = false; refreshTab(); } }
}
function homeInput(a) {
  const H0 = S.home, items = homeItems(), id = items[H0.i];
  if (a === 'up') { S.top.on = true; S.top.i = TOP.indexOf('library'); sfx.move(); return refreshHome(); }
  if (!id) return;
  if (a === 'left' || a === 'right') { const k = clamp(H0.i + (a === 'left' ? -1 : 1), 0, items.length - 1); if (k !== H0.i) { H0.i = k; sfx.move(); refreshHome(); } }
  else if (a === 'down') openGame(id, 'home:' + id, 'home');   // its page opens right here: the box slides in and the cartridge goes into it
  else if (a === 'a') { const g = byId[id], r = ensureRec(g); launchGame(g, r.lastSlot, isRA(emuFor(g)) && r.slots.some(Boolean)); }
  else if (a === 'y') toggleFav(byId[id]);
}
function libInput(a) {
  const L = S.lib, n = L.list.length, cols = LIB_COLS;
  if (L.zone === 'grid') {
    const col = L.i % cols;
    if (a === 'left') { if (col === 0 || !n) L.zone = 'rail'; else L.i--; sfx.move(); }
    else if (a === 'right') { if (col < cols - 1 && L.i + 1 < n) { L.i++; sfx.move(); } }
    else if (a === 'up') { if (L.i < cols) L.zone = 'filters'; else L.i -= cols; sfx.move(); }
    else if (a === 'down') { if (L.i + cols < n) { L.i += cols; sfx.move(); } else if (Math.floor(L.i / cols) < Math.floor((n - 1) / cols)) { L.i = n - 1; sfx.move(); } }
    else if (a === 'a' && n) return openGame(L.list[L.i].id, 'lib:' + L.list[L.i].id, 'library');
    else if (a === 'y' && n) return toggleFav(L.list[L.i]);
    else if (a === 'b') { sfx.back(); return enterTab('home'); }
  } else if (L.zone === 'rail') {
    const stops = railStops(), at = stops.indexOf(L.rail), item = railItems()[L.rail];
    if (a === 'up' && at === 0) { S.top.on = true; S.top.i = TOP.indexOf('library'); sfx.move(); }
    else if (a === 'b') { sfx.back(); return enterTab('home'); }
    else if (a === 'up' || a === 'down') { const k = clamp(at + (a === 'up' ? -1 : 1), 0, stops.length - 1); if (stops[k] !== L.rail) { L.rail = stops[k]; L.i = 0; L.off = null; sfx.move(); return refreshLibrary(true); } }
    else if (a === 'y' && item.custom) return collectionOptions(item.custom);
    else if ((a === 'right' || a === 'a') && n) { L.zone = 'grid'; sfx.move(); }
  } else {
    if (a === 'left') { L.zone = 'rail'; sfx.move(); } else if (a === 'up') { S.top.on = true; S.top.i = TOP.indexOf('library'); sfx.move(); }
    else if (a === 'b') { sfx.back(); return enterTab('home'); }
    else if (a === 'down') { if (n) L.zone = 'grid'; sfx.move(); }
    else if (a === 'a') { L.sort = (L.sort + 1) % SORTS.length; L.i = 0; sfx.select(); return refreshLibrary(true); }
  }
  refreshLibrary(false);
}
function appsInput(a) {
  const cols = APP_COLS, n = APPS.length, i = S.apps.i;
  if (a === 'left' && i % cols > 0) { S.apps.i--; sfx.move(); } else if (a === 'right' && i % cols < cols - 1 && i + 1 < n) { S.apps.i++; sfx.move(); }
  else if (a === 'up') { if (i < cols) { S.top.on = true; S.top.i = TOP.indexOf('apps'); } else S.apps.i -= cols; sfx.move(); }
  else if (a === 'b') { sfx.back(); return enterTab('home'); }
  else if (a === 'down' && i + cols < n) { S.apps.i += cols; sfx.move(); }
  else if (a === 'a') { const app = APPS[i]; if (ANDROID_GAMES.has(app.name)) { const g = GAMES.find(x => x.title === app.name); return startPlayDirect(g); } if (NATIVE) return ShelfNative.openApp(app.pkg); toast(`Opens ${app.name}`); }
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
  if (z === 'manual') { if (a === 'right') go('cart'); else if (a === 'up') return closeGame(); else if (a === 'a') return gameAction(); }
  else if (z === 'cart') { if (a === 'left') go('manual'); else if (a === 'right') go('actions'); else if (a === 'up') return closeGame(); else if (a === 'a') return gameAction(); }
  else if (z === 'actions') {
    if (a === 'left') { if (G0.i === 0) go('cart'); else { G0.i--; sfx.move(); } } else if (a === 'right') { if (G0.i < acts.length - 1) { G0.i++; sfx.move(); } }
    else if (a === 'up') { if (actRowMove(-1)) sfx.move(); else return closeGame(); }   // up from the top row closes the page, as down opened it
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
  if (NATIVE) return;   // the Android app passes controller buttons in directly (__nativeKey)
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
    case 'home': case 'library': case 'apps': case 'museum': case 'game': return { x: 1190, y: -175, r: 390, a: 1 };   // up in the top half, clear of Home's picture
    case 'settings': case 'ach': return { x: 1260, y: 940, r: 400, a: .5 };
    default: return { x: 667, y: 1200, r: 500, a: 0 };
  }
}
// Home's picture: the selected game's chosen media across the lower part of the screen, darkened, fading out toward
// the top so its edge meets the bottom of the row. Crossfades when the selection changes; hidden while a page is open.
const HB = { key: null, prev: null, a: 1, vis: 1 };
function homeArtCanvas(g, kind) {
  const w = 1024, h = 300, c = canvas(w, h), x = c.getContext('2d');
  let src = null;
  if (kind === 'cart') { src = canvas(w, h); drawScene(src.getContext('2d'), w, h, g); }
  else if (kind === 'box') src = mediaCanvas(g, { kind: 'box' });
  else if (kind === 'title' && g.sys !== 'android') src = mediaCanvas(g, { kind: 'title' });
  else src = mediaCanvas(g, { kind: 'shot', seed: hash(g.id + 'shot0'), scroll: 30 });
  const k = Math.max(w / src.width, h / src.height), sw = w / k, sh = h / k;
  x.drawImage(src, (src.width - sw) / 2, (src.height - sh) * (kind === 'box' ? .35 : .5), sw, sh, 0, 0, w, h);
  const f = x.createLinearGradient(0, 0, 0, h); f.addColorStop(0, 'rgba(0,0,0,0)'); f.addColorStop(.55, 'rgba(0,0,0,.85)'); f.addColorStop(1, 'rgba(0,0,0,1)');
  x.globalCompositeOperation = 'destination-in'; x.fillStyle = f; x.fillRect(0, 0, w, h);
  return c;
}
function drawHomeArt(dt, id, show) {
  const kind = P.prefs.homeArt, key = kind !== 'off' && id ? 'homeart:' + kind + ':' + id : null;
  if (key !== HB.key) { HB.prev = HB.a > .5 ? HB.key : HB.prev; HB.key = key; HB.a = 0; }
  HB.a = Math.min(1, HB.a + dt / .45); approach(HB, { vis: show ? 1 : 0 }, dt, 6);
  const dim = lightTheme() ? [.62, .62, .66] : [.36, .37, .44], top = HOME_Y + 122, hgt = 750 - top;   // the fade's edge sits at the bottom of the row
  const plate = (k, a) => { if (!k || a < .005 || NODRAW) return; const [, kd, gid] = k.split(':');
    const [x, y] = pw(667, top + hgt / 2); gl.depthMask(false);
    drawPart(PLANE, M4.trs(x, y, -1, 0, 0, 0, [1334 / PX, hgt / PX, 1]), { tex: tex(k, () => homeArtCanvas(byId[gid], kd), true), flat: true, col: dim, alpha: a * HB.vis });
    gl.depthMask(true); };
  plate(HB.prev, 1 - ease(HB.a)); plate(HB.key, ease(HB.a));
}
const HOME_Y = 302;   // the selected cartridge's center on Home, under the top bar
function drawHome(dt, t, strip) {   // strip: a game's page is open here, so the rest of the row waits in a small strip up top
  const items = homeItems(), H0 = S.home, rowFocus = H0.zone === 'row' && !S.top.on && S.screen === 'home', q = S.seq;
  approach(H0, { f: H0.i }, dt, 8);
  setFlat(); beginMeshes();
  drawHomeArt(dt, items[H0.i], !strip);
  const drawn = [], others = strip ? items.filter(id => id !== S.game.id) : [];
  items.forEach((id, i) => {
    const g = byId[id], o = pose('home:' + id, { s: 0, y: 3 }), foc = !strip && i === H0.i;
    let xpx, ypx, alpha, bob = 0;
    if (strip) { const j = others.indexOf(id); xpx = 667 + (j - (others.length - 1) / 2) * 80; ypx = 156; alpha = .8; }
    else {
      const off = i - H0.f; xpx = 270 + off * 196 + Math.sign(off) * Math.min(Math.abs(off), 1) * 70;
      alpha = off < 0 ? (off > -1 ? 1 + .6 * off : .4 * (1 - clamp(-off - 1, 0, 1))) : 1 - clamp((off - 5) / 1.2, 0, 1);
      bob = motion() ? Math.sin(t * .8 + i * 1.7) * (foc ? 4 : 3) : 0; ypx = (foc ? HOME_Y : HOME_Y + 24) + bob;
    }
    const [x, y] = pw(xpx, ypx);
    approach(o, { x, z: foc ? .5 : 0, ry: strip ? -.12 : -.2, rx: -.1, s: mediaScale(g.sys, strip ? 54 : foc ? 240 : 156), alpha, bright: foc ? 1.02 : strip ? .62 : .72, glow: foc && rowFocus ? 1 : 0 }, dt, strip ? 7 : 10);
    approach(o, { y }, dt, foc ? 7 : strip ? 6 : 10);
    const M = M4.trs(o.x, o.y, o.z, o.rx, o.ry, 0, o.s);
    if ((q && q.g.id === id) || S.skipId === id) return;   // it's drawn elsewhere: in the launch scene, or in its box
    const hpx = CART[g.sys].h / 2 * o.s * PX, sh = 1 - clamp((HOME_Y + 24 - (H / 2 - o.y * PX)) / 80, 0, 1);   // shadows fade as they rise into the strip
    if (o.alpha > .01) { if (sh > .01) shadowUnder(xpx, ypx - bob + hpx + (foc ? 24 : 12), Math.max(CART[g.sys].w, .5) * o.s * PX * .9, o.alpha * sh, foc); drawn.push([g, M, o, foc]); }
  });
  for (const [g, M, o, foc] of drawn) { if (foc && rowFocus) { const m = moodColors(MOOD.cur); drawCartGlowM(g, M, o.s, hexRgb(m.accent), hexRgb(m.planet[0]), o.alpha); } drawCartM(g, M, o); }
  endMeshes();
}
// The sheet: Library, Apps or the Museum on a panel that slides up from the bottom and covers Home completely.
// Everything on it is drawn with the view shifted down by SHEETOFF pixels, so it all moves together.
function sheetTarget() {
  const sc = S.screen, G0 = S.game;
  if (SHEETS.includes(sc)) return 1;
  if (sc === 'game') return SHEETS.includes(G0.from) && G0.closing && G0.then === G0.from ? 1 : 0;   // a box opened from the Library: the sheet slides away, then back
  if (['settings', 'ach', 'media', 'manual'].includes(sc)) return S.sheet.p >= .5 ? 1 : 0;
  return 0;
}
function sheetUpdate(dt) {
  const sh = S.sheet, tgt = sheetTarget(); sh.p = clamp(sh.p + Math.sign(tgt - sh.p) * dt / .55, 0, 1); sh.k = ease(sh.p);
  SHEETOFF = (1 - sh.k) * 790;
  // Home stays hidden while any sheet is up, or a box from the Library is open, and only comes back once the sheet has fully gone
  const homeOk = sh.k < .002 && !(sc => sc === 'game' && S.game.from !== 'home')(S.screen);
  S.homeA = homeOk ? Math.min(1, S.homeA + dt / .4) : Math.min(S.homeA, 1 - sh.k, tgt ? 1 - sh.k : 0);
  if (NODRAW) return;
  $('#homeInfo').style.opacity = S.homeA;
  for (const n of SHEETS) { const el = $('#s-' + n), mine = n === sh.name;   // the sheet's own panels ride along with it, and stay below the screen once it's down
    el.style.transform = mine ? `translateY(${SHEETOFF}px)` : ''; el.classList.toggle('sheetdom', mine && sh.k > .002 && S.screen === 'home'); }
}
function drawSheet(dt, t) {
  if (S.sheet.k < .002 || !S.sheet.name) return;
  if (!NODRAW) { gl.clear(gl.DEPTH_BUFFER_BIT); gl.viewport(0, -Math.round(SHEETOFF * cv.height / H), cv.width, cv.height); }
  setFlat(); beginMeshes();
  const b = hexRgb(getComputedStyle(stage).getPropertyValue('--bg').trim() || '#070b1e'), f = lightTheme() ? [1, 1, 1] : [.6, .65, 1];
  drawPlate('sheet', panelArt, 667, 524, 1310, 800, 2, b.map((v, i) => v * .9 + f[i] * .06), .95);
  endMeshes(); if (!NODRAW) gl.clear(gl.DEPTH_BUFFER_BIT);
  S.sheet.cf = Math.min(1, (S.sheet.cf == null ? 1 : S.sheet.cf) + dt / .3); UNDER = ease(S.sheet.cf);   // switching sheets fades the new one in
  if (S.sheet.name === 'library') drawLibrary(dt, t); else if (S.sheet.name === 'museum') drawMuseum(dt);
  UNDER = 1;
  if (!NODRAW) gl.viewport(0, 0, cv.width, cv.height);
}
// The Library's grid: four columns in a framed panel. Each row is as tall as its tallest box (raised, as when
// it's selected) plus room for the title under it, so tall boxes never crowd the rows above and below.
const LIB_COLS = 4, LIB_VIEW = { x: 352, y: 196, w: 950, h: 530 };
function libRows() {   // each row's top and height, in pixels from the top of the grid
  const L = S.lib, out = []; let y = 12;
  for (let r = 0; r * LIB_COLS < L.list.length; r++) {
    let tall = 0; for (let c = 0; c < LIB_COLS; c++) { const g = L.list[r * LIB_COLS + c]; if (g) tall = Math.max(tall, BOX[g.sys][1]); }
    const box = tall * BOXK * 1.08 * PX + 14, h = 26 + box + 76;   // breathing room, the box, its title and the gap to the next row
    out.push({ top: y, h, box }); y += h;
  }
  return { rows: out, total: y + 12 };
}
function drawLibrary(dt, t) {
  const L = S.lib, cols = LIB_COLS, V = LIB_VIEW, row = Math.floor(L.i / cols), onGrid = L.zone === 'grid' && !S.top.on;
  const { rows, total } = libRows(), R = rows[row] || { top: 0, h: 0 };
  const want = clamp(R.top + R.h / 2 - V.h / 2, 0, Math.max(0, total - V.h));   // keep the selected row in the middle when there's room
  if (L.off == null) L.off = want;
  approach(L, { off: want }, dt, 9);
  const cx = c => V.x + V.w / (2 * cols) + c * V.w / cols, s0 = BOXK, still = !motion();
  setFlat(); beginMeshes();
  drawPlate('gridpanel', panelArt, V.x + V.w / 2, V.y + V.h / 2, V.w, V.h, 0, lightTheme() ? [1, 1, 1] : [.035, .045, .11], lightTheme() ? .4 : .55);
  clipRect(V.x + 1, V.y + 1, V.x + V.w - 1, V.y + V.h - 1);
  let focus = null;
  L.list.forEach((g, i) => {
    const r = Math.floor(i / cols), c = i % cols, foc = onGrid && i === L.i, o = pose('lib:' + g.id, { s: 0 }), RW = rows[r];
    const [bw, bh] = BOX[g.sys], hpx = bh * s0 * PX;
    const ypx = V.y + RW.top - L.off + 26 + RW.box / 2 + 7, lift = foc ? 14 : 0, bob = still ? 0 : Math.sin(t * .8 + i * 1.7) * (foc ? 4 : 3), [x, y] = pw(cx(c), ypx - lift + bob);
    const out = Math.max(V.y - (ypx + hpx / 2), (ypx - hpx / 2) - (V.y + V.h)), edge = Math.max(V.y + 30 - (ypx - hpx / 2), (ypx + hpx / 2) - (V.y + V.h - 30));
    const vis = 1 - clamp(out / 60, 0, 1), cut = clamp(edge / 90, 0, 1);
    approach(o, { x, z: foc ? .4 : 0, ry: .3, rx: -.08, s: s0 * (foc ? 1.08 : 1), alpha: vis * (1 - .45 * cut), bright: foc ? 1.03 : onGrid ? .66 : .82 }, dt, 11);
    approach(o, { y }, dt, foc ? 7 : 11);
    if (S.skipId === g.id) return;   // it's open in front
    if (o.alpha > .01) shadowUnder(cx(c), ypx + bh / 2 * o.s * PX + (foc ? 22 : 12), bw * o.s * PX * .95, o.alpha, foc);
    if (foc) focus = { g, o, c, ypx: ypx - lift, bh }; else if (o.alpha > .01) drawCase(g, o, 0);
  });
  if (focus) { const m = moodColors(MOOD.cur); drawCaseGlow(focus.g, focus.o, hexRgb(m.accent), hexRgb(m.planet[0])); drawCase(focus.g, focus.o, 0);
    if (!NODRAW) { const F = $('#lFocus'); F.style.left = cx(focus.c) + 'px'; F.style.top = (H / 2 - focus.o.y * PX + focus.bh / 2 * focus.o.s * PX + 30) + 'px'; } }
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
const CASE_AT = [326, 462], CASE_FIT = [580, 500];   // the open case's center and the room it fits in, left of the details
function drawGamePage(dt, t) {
  const G0 = S.game, g = byId[G0.id], o = pose('case', {}), m = caseModel(g.sys), [W0, H0] = [m.W, m.H];
  const sOpen = Math.min(CASE_FIT[0] / (2 * W0 * PX), CASE_FIT[1] / (H0 * PX)), s = G0.turn ? Math.min(sOpen * 1.35, 430 / (Math.max(W0, H0) * PX)) : sOpen;
  const [cxw, cyw] = pw(CASE_AT[0], CASE_AT[1]), openT = G0.turn ? 0 : 1;
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
  // From Home, the rest of the row waits in a strip up top; from the Library, the sheet slides away below, and back as the box closes
  S.skipId = g.id;
  if (G0.from === 'home') { if (G0.closing ? G0.then === 'home' : true) drawHome(dt, t, !G0.closing); }
  else if (SHEETS.includes(G0.from)) drawSheet(dt, t);
  if (!NODRAW) gl.clear(gl.DEPTH_BUFFER_BIT);
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
let last = performance.now(), clockTimer = 0, MANUAL = false, NODRAW = false, UNDER = 1, SHEETOFF = 0;   // UNDER fades everything drawn behind an open box
window.__step = (seconds, fps = 30) => { MANUAL = true; const n = Math.max(1, Math.round(seconds * fps)); for (let i = 0; i < n; i++) { NODRAW = i < n - 1; tick(1 / fps); } NODRAW = false; };
function loop(now) { requestAnimationFrame(loop); const dt = Math.min(.05, (now - last) / 1000); last = now; if (!MANUAL) { try { tick(dt); } catch (e) { console.error(e); } } }
function tick(dt) {
  pollPad(dt);
  if (motion()) S.time += dt;
  MOOD.t = Math.min(1, MOOD.t + dt / .8);
  for (let i = 0; i < 3; i++) RIM[i] += (RIM_T[i] - RIM[i]) * Math.min(1, dt * 4);
  if (S.seq) seqUpdate(dt);
  sheetUpdate(dt);
  musicDuck();
  if (S.play) playUpdate(dt);
  if (S.screen === 'media') mvUpdate(dt);
  approach(PL, planetTarget(), dt, 3);
  clockTimer -= dt; if (clockTimer <= 0) { clockTimer = 1; $('#clock').textContent = clockText(); }
  if (!NODRAW) { gl.viewport(0, 0, cv.width, cv.height); const b = hexRgb(getComputedStyle(stage).getPropertyValue('--bg').trim() || '#070b1e'); gl.clearColor(b[0], b[1], b[2], 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT); }
  const dim = ['media', 'manual', 'play'].includes(S.screen) ? .45 : 1;
  drawBackdrop(S.time, { bright: dim, pan: S.screen === 'home' ? S.home.f * .006 : S.screen === 'library' ? (S.lib.off || 0) * .00007 : 0, planet: PL.a > .01 ? PL : null });
  if (S.screen === 'home' || SHEETS.includes(S.screen)) {
    if (S.homeA > .005) { UNDER = S.homeA; drawHome(dt, S.time); UNDER = 1; }
    drawSheet(dt, S.time);
  }
  else if (S.screen === 'game') drawGamePage(dt, S.time);
  else if (S.screen === 'launch' && S.seq) drawLaunch(dt);
}
async function boot() {
  fit();
  if (!gl) { document.body.insertAdjacentHTML('beforeend', '<p style="padding:40px">This prototype needs WebGL.</p>'); return; }
  initGL(); initMeshes(); applyTheme(P.prefs.theme);
  try { await Promise.race([Promise.all([document.fonts.load('600 40px Unbounded'), document.fonts.load('600 20px Manrope')]), new Promise(r => setTimeout(r, 2000))]); } catch (e) { }
  if (NATIVE && P.setup) { paintProfile(); enterTab('home'); } else enterSetup();
  requestAnimationFrame(loop);
}
boot();
</script>
</body>
</html>
