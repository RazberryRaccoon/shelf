/* ================= state ================= */
const S = {
  screen: 'setup', tab: 'home', top: { on: false, i: 0 },
  setup: { step: 0, i: 0 },
  home: { i: 0, f: 0, zone: 'row', card: 0 },
  lib: { zone: 'grid', rail: 0, filter: 0, i: 0, sort: 0, rowF: .6, list: [] },
  game: { id: null, zone: 'actions', i: 0, from: 'library', turn: false, rx: 0, ry: 0 },
  apps: { i: 0 }, set: { cat: 0, zone: 'nav', i: 0, back: 'home', sub: null, game: null },
  mu: { i: 0, ex: 0, zone: 'rail', yaw: -.55, pitch: .45, yawT: -.55, pitchT: .45, open: 0, fade: 1, prev: null },
  ach: { id: null, filter: 0, i: 0 }, dlg: { open: false }, kb: { open: false },
  seq: null, play: null, time: 0,
};
const TABS = ['home', 'library', 'apps', 'museum'];
const TOP = [...TABS, 'settings', 'profile'];
const SORTS = ['Recently played', 'A to Z', 'Release year'];
const SHOWS = ['All', 'Saved', 'Not played'];
const HOME_MAX = 10;
const ANDROID_GAMES = new Set(['Moonlight Dash', 'Tiny Orchard']);
const inLibrary = g => g.sys !== 'android' || ANDROID_GAMES.has(g.title);
const anim = new Map();
function pose(key, init) { if (!anim.has(key)) anim.set(key, Object.assign({ x: 0, y: 0, z: 0, rx: 0, ry: 0, s: 0, alpha: 0, bright: 1, glow: 0, open: 0, lift: 0 }, init)); return anim.get(key); }
function approach(o, t, dt, rate = 9) { const k = 1 - Math.exp(-dt * rate); for (const n in t) o[n] += (t[n] - o[n]) * k; }
const motion = () => P.prefs.motion;
const rec = id => P.played[id];
function ensureRec(g) { if (!P.played[g.id]) P.played[g.id] = { last: 0, minutes: 0, earned: 0, fav: false, slots: [null, null, null, null], lastSlot: 0 }; return P.played[g.id]; }
const BOXK = .76;   // boxes in the Library: 1 unit (100 mm) = .76 x PX pixels, so every box keeps its true size relative to the others
function mediaScale(sys, px) { const c = CART[sys]; return px / PX / Math.max(c.w, c.h); }

/* ================= small DOM helpers ================= */
let toastTimer = 0;
function toast(msg) { const t = $('#toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2400); }
function show(screen, under) {   // under: a tab left visible behind, faded back (behind an open box)
  document.querySelectorAll('.screen').forEach(s => { s.classList.toggle('on', s.id === 's-' + screen); s.classList.toggle('under', s.id === 's-' + under); s.classList.remove('rise'); });
  $('#topbar').classList.toggle('on', TABS.includes(screen)); $('#topbar').classList.toggle('under', TABS.includes(under || '')); $('#topbar').classList.remove('rise');
  if (screen !== 'game') S.skipId = null;
}
function riseUnder(tab) {   // bring a faded-back tab forward again, slowly, while the box closes
  const el = $('#s-' + tab); if (!el) return;
  document.querySelectorAll('.screen.under').forEach(s => s.classList.remove('under'));
  el.classList.add('rise', 'on'); $('#topbar').classList.remove('under'); $('#topbar').classList.add('rise', 'on');
}
function clockText() { return new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }); }
function avatarCss(cols) { return `radial-gradient(circle at 35% 30%, ${cols[0]}, ${cols[1]} 55%, ${cols[2]})`; }
function dateText(ts) { return new Date(ts).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' }); }
function renderTop() { document.querySelectorAll('#topbar [data-k]').forEach(e => { const k = e.dataset.k; e.classList.toggle('sel', k === S.tab); e.classList.toggle('focus', S.top.on && TOP[S.top.i] === k); }); }
function paintProfile() { $('#meName').textContent = P.name; $('#avatar').style.background = avatarCss(P.colors); }
function applyTheme(t) {
  P.prefs.theme = t; stage.dataset.t = t;
  stage.style.setProperty('--fadec', getComputedStyle(stage).getPropertyValue('--bg'));
  applyAccent();
}
const PROFILE_COLORS = [['#ffd38a', '#e8834a', '#6a2a1e'], ['#bff3ff', '#3fa4d9', '#13355e'], ['#f6c8ff', '#b05ad8', '#401a5e'], ['#d6ffc8', '#5ac46a', '#1a4a2a'], ['#ffe0f0', '#e0607e', '#5a1a33']];

/* ================= first-run setup ================= */
const SETUP_STEPS = 6;
function setupOpts() {
  const s = S.setup.step;
  const back = { k: 'back', label: 'Back', back: true };
  if (s === 0) return [{ k: 'next', label: 'Get started', primary: true }, { k: 'quick', label: 'Quick setup with test data (for testing)', debug: true }];
  if (s === 1) return [{ k: 'name', label: 'Your name', value: P.name || 'Choose' }, ...PROFILE_COLORS.map((c, i) => ({ k: 'color', i, label: ['Amber', 'Sky', 'Orchid', 'Fern', 'Rose'][i], color: c })), { k: 'next', label: 'Continue', primary: true }, back];
  if (s === 2) return [...Object.keys(THEMES).map(t => ({ k: 'theme', t, label: THEMES[t].name })), { k: 'next', label: 'Continue', primary: true }, back];
  if (s === 3) return [{ k: 'folder', label: 'Add another folder' }, { k: 'next', label: 'Continue', primary: true }, back];
  if (s === 4) return [{ k: 'ss', label: 'ScreenScraper', value: P.art.ss ? 'Signed in' : 'Sign in' }, { k: 'ra', label: 'RetroAchievements', value: P.ra ? 'Signed in' : 'Sign in' }, { k: 'next', label: 'Continue', primary: true }, back];
  return [{ k: 'launcher', label: 'Use Shelf as my home screen', primary: true }, { k: 'skip', label: 'Not now' }, back];
}
function fillSetup() {
  const s = S.setup.step, opts = setupOpts(); S.setup.i = clamp(S.setup.i, 0, opts.length - 1);
  const counts = SYS_ORDER.filter(k => k !== 'android').map(k => [PLAT[k].name, GAMES.filter(g => g.sys === k).length]);
  const head = [
    ['Welcome', 'Shelf', 'A home for your games. Let’s set up this handheld; it takes about a minute.'],
    ['Step 1 of 5', 'Who’s playing?', 'Shelf keeps one profile. Your name and color show at the top of the screen.'],
    ['Step 2 of 5', 'Pick a look', 'You can change it any time in Settings.'],
    ['Step 3 of 5', `Found ${GAMES.filter(g => g.sys !== 'android').length} games`, 'In Internal storage › ROMs and SD card › ROMs, sorted into consoles by folder and file type.'],
    ['Step 4 of 5', 'Art, manuals and achievements', 'Sign in to download box art, manuals and videos, and to earn achievements. Both are free, and both can wait.'],
    ['Step 5 of 5', 'Make Shelf your home screen?', 'Then the Home button always brings you back here, and Shelf can save a game in RetroArch before you leave it. Your apps stay one tab away.'],
  ][s];
  let extra = '';
  if (s === 3) extra = `<div class="found">${counts.map(([n, c]) => `<div class="ok">${n} <b>${c}</b></div>`).join('')}</div>
    <div class="found"><div class="ok">RetroArch <b>with 9 cores</b></div><div class="ok">DuckStation</div><div class="ok">melonDS</div><div class="ok">PPSSPP</div><div class="ok">Mupen64Plus FZ</div></div>`;
  $('#setup').innerHTML = `<div class="step">${head[0]}</div><h1>${head[1]}</h1><p class="lead">${head[2]}</p>${extra}<div class="opts">` + opts.map((o, i) => {
    const cur = (o.k === 'theme' && P.prefs.theme === o.t) || (o.k === 'color' && P.colors === o.color);
    const T = o.t && THEMES[o.t], sw = o.color ? `<span style="width:20px;height:20px;border-radius:50%;background:${avatarCss(o.color)}"></span>`
      : T ? `<span style="width:34px;height:20px;border-radius:6px;border:1px solid var(--line);background:${T.space ? 'radial-gradient(circle at 70% 130%, #8b78e6 0, #2a1a5e 45%, #070b1e 80%)' : `linear-gradient(135deg, ${T.top}, ${T.c[0]} 55%, ${T.c[1]})`}"></span>` : '';
    return `<div class="opt ${o.primary ? 'primary' : ''} ${o.debug ? 'debug' : ''} ${o.back ? 'back' : ''} ${i === S.setup.i ? 'focus' : ''}"><span style="display:flex;gap:10px;align-items:center">${sw}${o.label}${cur ? ' ✓' : ''}</span>${o.value ? `<small>${esc(o.value)}</small>` : ''}</div>`;
  }).join('') + `</div><div class="dots">${Array.from({ length: SETUP_STEPS }, (_, i) => `<i class="${i <= s ? 'on' : ''}"></i>`).join('')}</div>`;
}
function setupAct() {
  const o = setupOpts()[S.setup.i]; sfx.select();
  if (o.k === 'back') { S.setup.step--; S.setup.i = 0; return fillSetup(); }
  if (o.k === 'quick') return quickSetup();
  if (o.k === 'name') return openKb('Your name', P.name, v => { P.name = v; fillSetup(); });
  if (o.k === 'color') { P.colors = o.color; return fillSetup(); }
  if (o.k === 'theme') { applyTheme(o.t); return fillSetup(); }
  if (o.k === 'folder') return toast('Opens Android’s folder picker');
  if (o.k === 'ss') { P.art.ss = P.art.ss ? null : 'player_one'; P.art.source = P.art.ss ? 'ScreenScraper' : 'Free sources'; return fillSetup(); }
  if (o.k === 'ra') { P.ra = P.ra ? null : 'player_one'; return fillSetup(); }
  if (o.k === 'next') { if (S.setup.step === 1 && !P.name) return openKb('Your name', '', v => { P.name = v; S.setup.step++; S.setup.i = 0; fillSetup(); }); S.setup.step++; S.setup.i = 0; return fillSetup(); }
  P.prefs.homeLauncher = o.k === 'launcher'; finishSetup();
}
function quickSetup() {   // for testing: goes through every step with test data, instead of skipping setup
  const fill = [() => { P.name = 'Tester'; P.colors = PROFILE_COLORS[1]; }, () => applyTheme(P.prefs.theme), () => { }, () => { P.art.ss = 'tester'; P.art.source = 'ScreenScraper'; P.ra = 'tester'; }, () => { P.prefs.homeLauncher = true; }];
  let k = 0; S.setup.quick = true;
  const next = () => {
    if (k < fill.length) { fill[k](); S.setup.step = k + 1; S.setup.i = setupOpts().findIndex(o => o.primary); fillSetup(); k++; setTimeout(next, 260); }
    else { S.setup.quick = false; finishSetup(); }
  };
  next();
}
function finishSetup() { P.setup = true; paintProfile(); enterTab('home'); toast(`Welcome, ${P.name}`); }
function enterSetup() { S.screen = 'setup'; show('setup'); setMood('home'); fillSetup(); }

/* ================= tabs ================= */
function enterTab(tab) {
  S.tab = tab; S.screen = tab; S.top.on = false; show(tab); renderTop();
  if (tab === 'home') refreshHome(); else if (tab === 'library') refreshLibrary(true); else if (tab === 'museum') enterMuseum(); else refreshApps();
}
function refreshTab() { S.tab === 'home' ? refreshHome() : S.tab === 'library' ? refreshLibrary(false) : S.tab === 'museum' ? refreshMuseum() : refreshApps(); renderTop(); }
const homeItems = () => P.recent.filter(id => inLibrary(byId[id]) && !(P.hidden && P.hidden.has(id))).slice(0, HOME_MAX).concat(['__all']);
function homeCards(id) { return id === '__all' ? [] : [{ k: 'box' }, { k: 'ach' }, { k: 'saves' }]; }
function cardHtml(c, g) {
  const r = rec(g.id);
  if (c.k === 'box') return `<h4>Open the box</h4><p>Manual, saves, media and details</p><div class="big">⧉</div>`;
  if (c.k === 'ach') {
    if (g.sys === 'android') return `<h4>Achievements</h4><p>Android games keep their own.</p>`;
    if (!P.ra) return `<h4>Achievements</h4><p>Sign in to RetroAchievements in Settings to earn them.</p>`;
    const A = achievements(g), e = achFor(P, g), got = A.list.filter(a => e[a.id]).sort((a, b) => e[b.id].date - e[a.id].date), pct = Math.round(got.length / A.list.length * 100);
    return `<h4>Achievements</h4><div class="big">${got.length}<small>of ${A.list.length}</small></div><i class="bar"><b style="width:${pct}%"></b></i><div class="minib">${got.slice(0, 5).map(a => `<img src="${badge(g, a, false)}" alt="">`).join('')}</div>`;
  }
  const emu = emuFor(g);
  if (!isRA(emu)) return `<h4>Saves</h4><p class="note-small">${g.sys === 'android' ? 'The app keeps its own saves.' : `Kept by ${emu}. Shelf’s save slots work with RetroArch.`}</p>`;
  const used = r ? r.slots.filter(Boolean).length : 0;
  return `<h4>Saves</h4><p>${used} of 4 slots used</p><div class="slotdots">${[0, 1, 2, 3].map(i => `<i class="${r && r.slots[i] ? 'on' : ''}"></i>`).join('')}</div>`;
}
function refreshHome() {
  const items = homeItems(), H0 = S.home; H0.i = clamp(H0.i, 0, items.length - 1);
  const id = items[H0.i], cards = homeCards(id); H0.card = clamp(H0.card, 0, Math.max(0, cards.length - 1)); if (!cards.length) H0.zone = 'row';
  if (id === '__all') { setMood('home'); $('#hTitle').textContent = 'Your library'; $('#hMeta').innerHTML = `<span>${GAMES.filter(inLibrary).length} games on ${SYS_ORDER.length - 1} consoles, plus Android</span>`; }
  else {
    const g = byId[id], r = rec(id), emu = emuFor(g); setMood(g.sys); $('#hTitle').textContent = g.title;
    $('#hMeta').innerHTML = `<span class="chip"><i></i>${PLAT[g.sys].name}</span><span>${agoText(r.last)}</span><span>${playedText(r.minutes)}</span><span>${isRA(emu) && r.slots.some(Boolean) ? 'A to resume' : 'A to play'}</span>`;
  }
  const g = byId[id];
  $('#hCards').innerHTML = cards.map((c, i) => `<div class="card panel ${H0.zone === 'cards' && H0.card === i ? 'focus' : ''}">${cardHtml(c, g)}</div>`).join('');
  $('#hTileN').textContent = `${GAMES.filter(inLibrary).length} games`;
  renderTop();
}

/* ================= library ================= */
const railStops = () => railItems().map((r, i) => r.head ? -1 : i).filter(i => i >= 0);
function libList() {
  const RI = railItems(); S.lib.rail = clamp(S.lib.rail, 0, RI.length - 1);
  const k = RI[S.lib.rail].k, sort = S.lib.sort;
  const list = GAMES.filter(inLibrary).filter(g => railMatch(k, g));
  list.sort((a, b) => sort === 1 ? a.title.localeCompare(b.title) : sort === 2 ? a.year - b.year || a.title.localeCompare(b.title)
    : ((P.played[b.id] || { last: 0 }).last - (P.played[a.id] || { last: 0 }).last) || a.title.localeCompare(b.title));
  return list;
}
function refreshLibrary(rebuild) {
  const L = S.lib; if (rebuild) { L.list = libList(); L.i = clamp(L.i, 0, Math.max(0, L.list.length - 1)); }
  const RI = railItems();
  $('#rail').innerHTML = '<nav class="railin">' + RI.map((r, i) => r.head ? `<h5>${r.head}</h5>` :
    `<div class="${i === L.rail ? 'cur' : ''} ${!S.top.on && L.zone === 'rail' && i === L.rail ? 'focus' : ''}">${esc(r.label)}<span>${GAMES.filter(inLibrary).filter(g => railMatch(r.k, g)).length}</span></div>`).join('')
    + (!S.top.on && L.zone === 'rail' && RI[L.rail].custom ? '<p class="railhint"><i class="btn">Y</i> Rename or delete</p>' : '') + '</nav>';
  const rin = $('#rail .railin'), cur = rin.querySelector('.cur');
  if (cur) { const view = 584, off = clamp(cur.offsetTop + cur.offsetHeight / 2 - view / 2, 0, Math.max(0, rin.scrollHeight - view)); rin.style.transform = `translateY(${-off}px)`; }
  $('#filters').innerHTML = `<div class="filter ${!S.top.on && L.zone === 'filters' ? 'focus' : ''}">Sort<em>${SORTS[L.sort]}</em></div>`;
  $('#libHead').textContent = `${L.list.length} ${L.list.length === 1 ? 'game' : 'games'}`;
  const empty = !L.list.length; $('#libEmpty').style.display = empty ? 'block' : 'none';
  $('#libEmpty').textContent = RI[L.rail].custom ? 'No games here yet. Open any game’s box and choose Add to collection.' : 'Nothing here yet.';
  const onGrid = L.zone === 'grid' && !S.top.on && !empty; $('#lFocus').classList.toggle('on', onGrid);
  if (onGrid) { const g = L.list[L.i], r = P.played[g.id]; $('#lFocus b').textContent = g.title; $('#lFocus span').textContent = `${PLAT[g.sys].name}, ${g.year}` + (r && r.last ? `, ${agoText(r.last).toLowerCase()}` : ''); setMood(g.sys); }
  else setMood(PLAT[RI[L.rail].k] ? RI[L.rail].k : 'home');
  renderTop();
}

/* ================= the game page: the open box ================= */
function openGame(id, fromKey, from) {
  const g = byId[id]; S.game = { id, zone: 'actions', i: 0, from: from || S.tab, turn: false, rx: 0, ry: 0 };
  const f = anim.get(fromKey), bp = pose('case', {});
  Object.assign(bp, f ? { x: f.x, y: f.y, z: f.z || 0, rx: f.rx || 0, ry: f.ry || 0, s: f.s, alpha: 1, open: 0, lift: 0 } : { x: 0, y: 0, z: 0, s: .5, alpha: 0, open: 0 });
  if (fromKey && fromKey.startsWith('home:')) {   // from Home: the open box slides in from the left, and the cartridge is put into it
    const m = caseModel(g.sys), s = Math.min(620 / (2 * m.W * PX), 520 / (m.H * PX)), [cx, cy] = pw(345, 392);
    Object.assign(bp, { x: cx + m.W / 2 * s - 9, y: cy, z: 0, rx: -.18, ry: 0, s, alpha: 1, open: 1 });
    S.game.fly = { t: 0, from: f ? { x: f.x, y: f.y, z: 0, rx: f.rx, ry: f.ry, s: f.s } : null };
  }
  S.screen = 'game'; show('game', S.game.from); S.game.dim = 0; $('#s-game').classList.remove('closing'); setMood(g.sys); sfx.select(); fillGame(); $('#gpin').classList.add('enter');
}
function closeGame(then) {   // the box closes, goes back where it came from, and that screen comes back around it
  const G0 = S.game; if (G0.closing) return;
  G0.closing = { t: 0 }; G0.then = then || G0.from; G0.turn = false; G0.fly = null; sfx.back(); $('#gpin').classList.remove('enter');
  $('#s-game').classList.add('closing'); $('#objLabel').classList.remove('on'); $('#turnHint').classList.remove('on');
  if (TABS.includes(G0.then)) { if (G0.then === 'home') refreshHome(); else if (G0.then === 'library') refreshLibrary(false); setTimeout(() => { if (S.screen === 'game' && S.game.closing) riseUnder(G0.then); }, 320); }   // once the box is out of the way
}
function closeGameTarget(g) {   // the pose the closed box shrinks back to
  const G0 = S.game, c = CART[g.sys], [bw, bh] = BOX[g.sys];
  if (G0.then !== G0.from) { const o = anim.get('case'); return { y: o.y - .5, s: o.s * .9, alpha: 0 }; }   // Home pressed: the closed box sinks away
  if (G0.from === 'library') { const l = anim.get('lib:' + g.id); if (l) return { x: l.x, y: l.y, z: l.z, rx: l.rx, ry: l.ry, s: l.s, alpha: 1 }; }
  return null;
}
function gameActions() {
  const g = byId[S.game.id], r = rec(g.id), ra = isRA(emuFor(g)), saved = ra && r && r.slots.some(Boolean) && !P.prefs.hardcore;
  return [saved ? 'Resume' : 'Play', ...(saved ? ['New game'] : []), ...(g.sys === 'android' ? [] : ['emu']), 'Close the box', 'Game settings', 'col', 'fav'];
}
function fillGame() {
  const g = byId[S.game.id], s = PLAT[g.sys], r = rec(g.id), G0 = S.game, emu = emuFor(g), ra = isRA(emu);
  const acts = gameActions(), slots = r ? r.slots : [null, null, null, null], last = r ? r.lastSlot : -1, media = mediaFor(g);
  let h = `<div class="gp-sys">${s.long}</div><h2>${esc(g.title)}</h2>
    <div class="facts">${[['Released', g.year], ['Developer', g.dev], ['Genre', g.genre], ['Players', g.players]].map(([a, b]) => `<div>${a}<b>${b}</b></div>`).join('')}</div>
    <div class="actions" data-z="actions">${acts.map((a, i) => `<div class="act ${i === 0 ? 'primary' : ''} ${G0.zone === 'actions' && G0.i === i ? 'focus' : ''}">${a === 'fav' ? `${r && r.fav ? '★' : '☆'} <i class="btn">Y</i>` : a === 'col' ? 'Add to collection' : a === 'emu' ? `Plays with ${esc(emuName(emu))}` : a}</div>`).join('')}</div>`;
  h += `<h3 data-z="saves">Saves${ra ? '<small>Slots are kept per game</small>' : ''}</h3>`;
  h += ra ? `<div class="slots">${slots.map((sl, i) => `<div class="slot ${G0.zone === 'saves' && G0.i === i ? 'focus' : ''}">${sl ? '<canvas></canvas>' : '<div class="none">New game</div>'}${sl && i === last ? '<span class="tag">Last played</span>' : ''}<p>${sl ? agoText(sl.ts) : `Slot ${i + 1}`}</p></div>`).join('')}</div>`
    : `<div class="managed ${G0.zone === 'saves' ? 'focus' : ''}"><span>${g.sys === 'android' ? 'This app keeps its own saves.' : `<b>${esc(emuName(emu))}</b> keeps this game’s saves. Shelf’s save slots work with RetroArch.`}</span><span class="act ${G0.zone === 'saves' ? 'focus' : ''}">Open ${esc(g.sys === 'android' ? g.title : emuApp(emu))}</span></div>`;
  h += `<h3 data-z="media">Media<small>${media.length} items</small></h3><div class="mrow"><div class="mstrip" id="gMedia">${mediaStrip(g, media, G0.zone === 'media' ? G0.i : -1, 'mthumb')}</div></div>`;
  if (g.sys !== 'android') {
    const A = achievements(g), e = P.ra ? achFor(P, g) : {}, got = A.list.filter(a => e[a.id]), pts = got.reduce((t, a) => t + a.pts, 0), pct = Math.round(got.length / A.list.length * 100);
    h += `<h3 data-z="ach">Achievements</h3><div class="achcard ${G0.zone === 'ach' ? 'focus' : ''}">${P.ra ? `<span>${got.length} of ${A.list.length}</span><i class="bar"><b style="width:${pct}%"></b></i><span>${pts} of ${A.points} points</span><div class="minib">${got.slice(-3).map(a => `<img src="${badge(g, a, false)}" alt="">`).join('')}</div><span class="go">View all</span>` : 'Sign in to RetroAchievements in Settings to earn these.'}</div>`;
  }
  h += `<p class="desc">${esc(g.desc)}</p><p class="credit">${artCredit()}</p>`;
  $('#gpin').innerHTML = h;
  $('#gpin').querySelectorAll('.slot').forEach((el, i) => { const sl = slots[i], c = el.querySelector('canvas'); if (sl && c) gameFrame(g, sl.seed, sl.scroll || 100, c); });
  $('#gMedia').style.transform = `translateX(${-Math.max(0, Math.min((G0.zone === 'media' ? G0.i : 0) - 2, media.length - 4.3)) * 137}px)`;
  // keep the focused section in view
  const zoneEl = $('#gpin').querySelector(`[data-z="${G0.zone}"]`), off = zoneEl && G0.zone !== 'actions' ? clamp(zoneEl.offsetTop - 150, 0, 400) : 0;
  $('#gpin').style.transform = `translateY(${-off}px)`;
  $('#turnHint').classList.toggle('on', G0.turn);
}
function artCredit() { return usingSS() ? 'Art, manuals and details from ScreenScraper.' : 'Art from RetroArch’s thumbnail database. Details from Wikipedia.'; }
function gameAction() {
  const g = byId[S.game.id], r = rec(g.id), G0 = S.game;
  if (G0.zone === 'ach') return P.ra ? openAch(g.id) : openSettings(CATS.indexOf('Achievements'));
  if (G0.zone === 'media') return openMedia(G0.i);
  if (G0.zone === 'saves') { const e = emuFor(g); if (!isRA(e)) { sfx.select(); return toast(`Opens ${g.sys === 'android' ? g.title : emuApp(e)}, where this game’s saves are`); } return launchGame(g, G0.i, !!(r && r.slots[G0.i])); }
  if (G0.zone === 'manual') { if (hasManual(g)) return openManual(); return toast(usingSS() ? 'ScreenScraper doesn’t have a manual for this game' : 'Manuals come from ScreenScraper. Sign in under Settings, Art and info.'); }
  if (G0.zone === 'cart') return launchGame(g, r ? r.lastSlot : 0, !!(r && r.slots.some(Boolean)));
  const a = gameActions()[G0.i];
  if (a === 'Resume') return launchGame(g, r.lastSlot, true);
  if (a === 'Play') return launchGame(g, r ? Math.max(0, r.slots.findIndex(x => !x)) : 0, false);
  if (a === 'New game') { const free = r.slots.findIndex(x => !x); G0.zone = 'saves'; G0.i = free < 0 ? 0 : free; fillGame(); return toast(free < 0 ? 'All four slots are used. Choose one to replace.' : 'Choose a slot for the new game'); }
  if (a === 'Close the box') { G0.turn = true; G0.rx = .12; G0.ry = .5; sfx.select(); return fillGame(); }
  if (a === 'Game settings') return openGameSettings(g.id);
  if (a === 'emu') return pickEmulator(g.sys, P.gameEmu[g.id] || null, v => { if (v) P.gameEmu[g.id] = v; else delete P.gameEmu[g.id]; fillGame(); toast(`${g.title} plays with ${emuName(emuFor(g))}`); }, { withDefault: true, title: `${g.title}: play with` });
  if (a === 'col') return addToCollection(g);
  toggleFav(g);
}
function toggleFav(g) {
  const r = ensureRec(g); r.fav = !r.fav; sfx.select(); toast(r.fav ? `Added ${g.title} to Favorites` : `Removed ${g.title} from Favorites`);
  if (S.screen === 'game') fillGame(); else if (S.screen === 'home') refreshHome(); else if (S.screen === 'library') refreshLibrary(false);
}
