/* ================= state ================= */
const S = {
  screen: 'profiles', pSel: 0, profile: -1, tab: 'home',
  top: { on: false, i: 0 },
  home: { i: 0, f: 0, zone: 'row', card: 0 },
  lib: { zone: 'grid', rail: 0, filter: 0, i: 0, sort: 0, show: 0, rowF: 0, list: [] },
  game: { id: null, zone: 'actions', i: 0, view: 0, from: 'home' },
  ach: { id: null, filter: 0, i: 0 },
  set: { cat: 0, zone: 'nav', i: 0, back: 'home' },
  pmenu: { open: false, i: 0 }, sleep: null, dlg: { open: false }, kb: { open: false },
  seq: null, play: null, time: 0,
};
const SYSP = { upload: true, ntp: true, art: { source: 'Free sources', ss: null, auto: true, backs: true, shots: true, videos: true, manuals: true, info: true, region: 'USA', running: false, done: 0 } };
const curProfile = () => S.profile >= 0 ? PROFILES[S.profile] : null;
const rec = id => curProfile().played[id];
const TOP = ['home', 'library', 'settings', 'profile'];
const SORTS = ['Recently played', 'A to Z', 'Release year'];
const SHOWS = ['All games', 'Saved games', 'Not played yet'];
const HOME_MAX = 10;
const anim = new Map();
function pose(key, init) { if (!anim.has(key)) anim.set(key, Object.assign({ x: 0, y: 0, z: 0, rx: 0, ry: 0, s: 0, alpha: 0, bright: 1, glow: 0 }, init)); return anim.get(key); }
function approach(o, t, dt, rate = 9) { const k = 1 - Math.exp(-dt * rate); for (const n in t) o[n] += (t[n] - o[n]) * k; }
const motion = () => { const p = curProfile(); return p ? p.prefs.motion : !reduceMotion; };
function cartScale(sys, px) { const c = CART[sys]; return px / PX / Math.max(c.w, c.h) * (SYS[sys].hand ? .86 : 1); }
function ensureRec(g) { const p = curProfile(); if (!p.played[g.id]) p.played[g.id] = { last: 0, minutes: 0, earned: 0, fav: false, slots: [null, null, null, null], lastSlot: 0 }; return p.played[g.id]; }

/* ================= small DOM helpers ================= */
let toastTimer = 0;
function toast(msg) { const t = $('#toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2600); }
function show(screen) {
  document.querySelectorAll('.screen').forEach(s => s.classList.toggle('on', s.id === 's-' + screen));
  $('#topbar').classList.toggle('on', screen === 'home' || screen === 'library');
}
function clockText() { const d = new Date(), p = curProfile(); return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', hour12: !(p && p.prefs.clock24) }); }
function avatarCss(cols) { return `radial-gradient(circle at 35% 30%, ${cols[0]}, ${cols[1]} 55%, ${cols[2]})`; }
function dateText(ts) { return new Date(ts).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' }); }
function renderTop() {
  document.querySelectorAll('#topbar [data-k]').forEach(e => { const k = e.dataset.k;
    e.classList.toggle('sel', k === S.tab); e.classList.toggle('focus', S.top.on && TOP[S.top.i] === k); });
}
function paintProfile() { const p = curProfile(); $('#meName').textContent = p.name; $('#avatar').style.background = avatarCss(p.colors); $('#setName').textContent = p.name; $('#setAvatar').style.background = avatarCss(p.colors); }

/* ================= who's playing ================= */
const profileX = (i, n) => 960 + (i - (n - 1) / 2) * 340;
function enterProfiles() {
  S.screen = 'profiles'; S.profile = -1; S.top.on = false; show('profiles'); setMood('home'); closePmenu();
  const n = PROFILES.length + 1;
  $('#plist').innerHTML = PROFILES.map((p, i) => `<div class="pname" style="left:${profileX(i, n)}px">${p.name}<small>${p.recent.length ? `${p.recent.length} games played` : 'New profile'}${p.ra ? `<br>${profilePoints(p).toLocaleString()} achievement points` : ''}</small></div>`).join('')
    + `<div class="padd" style="left:${profileX(n - 1, n)}px">+</div><div class="pname" style="left:${profileX(n - 1, n)}px">Add profile</div>`;
  updateProfiles();
}
function updateProfiles() {
  document.querySelectorAll('#plist .pname').forEach((e, i) => e.classList.toggle('sel', i === S.pSel));
  $('#plist .padd').classList.toggle('sel', S.pSel === PROFILES.length);
}
function chooseProfile() {
  if (S.pSel === PROFILES.length) return toast('Adding a profile isn’t part of this prototype yet');
  S.profile = S.pSel; paintProfile(); sfx.select();
  S.home = { i: 0, f: 0, zone: 'row', card: 0 }; S.lib = { zone: 'grid', rail: 0, filter: 0, i: 0, sort: 0, show: 0, rowF: 0, list: [] }; anim.clear();
  enterTab(curProfile().recent.length ? 'home' : 'library');
}

/* ================= tabs ================= */
function enterTab(tab) {
  S.tab = tab; S.screen = tab; show(tab); renderTop();
  if (tab === 'home') refreshHome(); else refreshLibrary(true);
}
const homeItems = () => curProfile().recent.slice(0, HOME_MAX).concat(['__all']);
function homeCards(id) {
  if (id === '__all') return ['fav', 'two', 'prog'].map(k => ({ k: 'col', col: k }));
  const r = rec(id), saved = r && r.slots.some(Boolean), p = curProfile();
  return [{ k: saved && !p.prefs.hardcore ? 'continue' : 'play' }, { k: 'ach' }, { k: 'saves' }];
}
function cardHtml(c, g) {
  const p = curProfile();
  if (c.k === 'col') { const item = BASE_RAIL.find(x => x.k === c.col), n = GAMES.filter(x => railMatch(c.col, x, p)).length;
    return `<h4>${item.label}</h4><div class="big">${n}<small>${n === 1 ? 'game' : 'games'}</small></div><p>Open in Library</p>`; }
  const r = rec(g.id);
  if (c.k === 'continue') { const sl = r.slots[r.lastSlot] || r.slots.find(Boolean); return `<h4>Continue</h4><p>Saved ${agoText(sl.ts).toLowerCase()}</p><canvas data-slot="${r.slots.indexOf(sl)}"></canvas>`; }
  if (c.k === 'play') return `<h4>${p.prefs.hardcore ? 'Play in hardcore' : 'Start playing'}</h4><p>${p.prefs.hardcore ? 'Saves are off in hardcore mode' : 'No saves yet'}</p><div class="big">▶</div>`;
  if (c.k === 'ach') {
    if (!p.ra) return `<h4>Achievements</h4><p>Link a RetroAchievements account in Settings to start earning them.</p>`;
    const A = achievements(g), e = achFor(p, g), got = A.list.filter(a => e[a.id]).sort((a, b) => e[b.id].date - e[a.id].date), pct = Math.round(got.length / A.list.length * 100);
    return `<h4>Achievements</h4><div class="big">${got.length}<small>of ${A.list.length}</small></div><i class="bar"><b style="width:${pct}%"></b></i>
      <div class="minib">${got.slice(0, 5).map(a => `<img src="${badge(g, a, false)}" alt="">`).join('')}</div>`;
  }
  const used = r ? r.slots.filter(Boolean).length : 0;
  return `<h4>Saves</h4><p>${used} of 4 slots used</p><div class="slotdots">${[0, 1, 2, 3].map(i => `<i class="${r && r.slots[i] ? 'on' : ''}"></i>`).join('')}</div>`;
}
function refreshHome() {
  const p = curProfile(), items = homeItems(), H0 = S.home;
  H0.i = clamp(H0.i, 0, items.length - 1);
  const id = items[H0.i], cards = homeCards(id);
  H0.card = clamp(H0.card, 0, cards.length - 1);
  $('#homeEmpty').style.display = p.recent.length ? 'none' : 'block';
  if (id === '__all') {
    setMood('home'); $('#hTitle').textContent = 'Your library';
    $('#hMeta').innerHTML = `<span>${GAMES.length} games on ${SYS_ORDER.length} consoles</span>`;
  } else {
    const g = byId[id], r = rec(id); setMood(g.sys); $('#hTitle').textContent = g.title;
    $('#hMeta').innerHTML = `<span class="chip"><i></i>${SYS[g.sys].name}</span><span>${agoText(r.last)}</span><span>${playedText(r.minutes)}</span>${r.fav ? '<span>★ Favorite</span>' : ''}`;
  }
  const g = byId[id];
  $('#hCards').innerHTML = cards.map((c, i) => `<div class="card panel ${H0.zone === 'cards' && H0.card === i ? 'focus' : ''}">${cardHtml(c, g)}</div>`).join('');
  $('#hCards').querySelectorAll('canvas').forEach(c => { const r = rec(id), sl = r.slots[+c.dataset.slot]; gameFrame(g, sl.seed, sl.scroll || 100, c); });
  $('#allCount').textContent = `${GAMES.length} games`;
  renderTop();
}
function cardAction() {
  const id = homeItems()[S.home.i], c = homeCards(id)[S.home.card];
  if (c.k === 'col') { S.lib.rail = railItems(curProfile()).findIndex(x => x.k === c.col); S.lib.zone = 'grid'; S.lib.i = 0; sfx.select(); return enterTab('library'); }
  const g = byId[id], r = rec(id);
  if (c.k === 'continue') return startLaunch(g, r.lastSlot, true, 'home');
  if (c.k === 'play') return startLaunch(g, Math.max(0, r.slots.findIndex(x => !x)), false, 'home');
  if (c.k === 'ach') return curProfile().ra ? openAch(id) : openSettings(5);
  openGame(id, 'home:' + id, 'home'); S.game.zone = 'slots'; S.game.i = r.lastSlot; fillGame();
}

/* ================= library ================= */
const railStops = () => railItems(curProfile()).map((r, i) => r.head ? -1 : i).filter(i => i >= 0);
function libList() {
  const p = curProfile(), RI = railItems(p); S.lib.rail = clamp(S.lib.rail, 0, RI.length - 1);
  const k = RI[S.lib.rail].k, show = S.lib.show, sort = S.lib.sort;
  let list = GAMES.filter(g => railMatch(k, g, p)).filter(g => { const r = p.played[g.id], saved = r && r.slots.some(Boolean);
    return show === 0 || (show === 1 && saved) || (show === 2 && !(r && r.last)); });
  list.sort((a, b) => sort === 1 ? a.title.localeCompare(b.title) : sort === 2 ? a.year - b.year || a.title.localeCompare(b.title)
    : ((p.played[b.id] || { last: 0 }).last - (p.played[a.id] || { last: 0 }).last) || a.title.localeCompare(b.title));
  return list;
}
function refreshLibrary(rebuild) {
  const p = curProfile(), L = S.lib;
  if (rebuild) { L.list = libList(); L.i = clamp(L.i, 0, Math.max(0, L.list.length - 1)); }
  const RI = railItems(p); L.rail = clamp(L.rail, 0, RI.length - 1);
  $('#rail').innerHTML = '<nav class="railin">' + RI.map((r, i) => r.head ? `<h5>${r.head}</h5>` :
    `<div class="${i === L.rail ? 'cur' : ''} ${!S.top.on && L.zone === 'rail' && i === L.rail ? 'focus' : ''}">${esc(r.label)}<span>${r.add ? '' : GAMES.filter(g => railMatch(r.k, g, p)).length}</span></div>`).join('')
    + (!S.top.on && L.zone === 'rail' && RI[L.rail].custom ? '<p class="railhint"><i class="btn">Y</i> Rename or delete</p>' : '') + '</nav>';
  // with many collections the list scrolls, keeping the current one in view
  const rin = $('#rail .railin'), cur = rin.querySelector('.cur');
  if (cur) { const view = 738 - 28, off = clamp(cur.offsetTop + cur.offsetHeight / 2 - view / 2, 0, Math.max(0, rin.scrollHeight - view)); rin.style.transform = `translateY(${-off}px)`; }
  $('#filters').innerHTML = [['Sort', SORTS[L.sort]], ['Show', SHOWS[L.show]]].map(([a, b], i) =>
    `<div class="filter ${!S.top.on && L.zone === 'filters' && L.filter === i ? 'focus' : ''}">${a}<em>${b}</em></div>`).join('');
  $('#libHead').textContent = `${L.list.length} ${L.list.length === 1 ? 'game' : 'games'}`;
  const empty = !L.list.length, item = RI[L.rail];
  $('#libEmpty').style.display = empty ? 'block' : 'none';
  $('#libEmpty').textContent = item.add ? 'Press A to make a new collection, then add games to it from any game’s page.'
    : item.custom && !item.custom.games.length ? 'No games here yet. Open any game and choose Add to collection.'
    : item.k === 'fav' && !GAMES.some(g => railMatch('fav', g, p)) ? 'No favorites yet. Press Y on any game to add it here.' : `Nothing here with “${SHOWS[L.show]}”. Change the Show filter to see more.`;
  const onGrid = L.zone === 'grid' && !S.top.on && !empty;
  $('#lFocus').classList.toggle('on', onGrid);
  if (onGrid) { const g = L.list[L.i], r = p.played[g.id];
    $('#lFocus b').textContent = g.title; $('#lFocus span').textContent = `${SYS[g.sys].name}, ${g.year}` + (r && r.last ? `, played ${agoText(r.last).toLowerCase()}` : ''); setMood(g.sys); }
  else setMood(SYS[item.k] ? item.k : 'home');
  renderTop();
}

/* ================= game page ================= */
function openGame(id, fromKey, from) {
  const g = byId[id]; S.game = { id, zone: 'actions', i: 0, view: 0, from: from || S.tab };
  const f = anim.get(fromKey), bp = pose('box', {}), [bw, bh] = BOX[g.sys];
  if (f) Object.assign(bp, { x: f.x, y: f.y, z: f.z, rx: 0, ry: f.ry, s: f.s * Math.max(CART[g.sys].w, CART[g.sys].h) / Math.max(bw, bh), alpha: 0 });
  S.screen = 'game'; show('game'); setMood(g.sys); sfx.select(); fillGame();
}
function gameActions() {
  const g = byId[S.game.id], r = rec(g.id), saved = r && r.slots.some(Boolean), hc = curProfile().prefs.hardcore;
  return [saved && !hc ? 'Resume' : 'Play', ...(saved && !hc ? ['New game'] : []), 'col', 'fav'];
}
function fillGame() {
  const g = byId[S.game.id], s = SYS[g.sys], p = curProfile(), r = rec(g.id), G0 = S.game, hc = p.prefs.hardcore;
  $('#gSys').textContent = s.long; $('#gTitle').textContent = g.title;
  $('#gFacts').innerHTML = [['Released', g.year], ['Developer', g.dev], ['Genre', g.genre], ['Players', g.players]].map(([a, b]) => `<div>${a}<b>${b}</b></div>`).join('');
  $('#gActions').innerHTML = gameActions().map((a, i) => `<div class="act ${G0.zone === 'actions' && G0.i === i ? 'focus' : ''}">${a === 'fav' ? `${r && r.fav ? '★ Favorite' : 'Favorite'} <i class="btn">Y</i>` : a === 'col' ? 'Add to collection' : a}</div>`).join('');
  const slots = r ? r.slots : [null, null, null, null], last = r ? r.lastSlot : -1;
  $('#gSlots').style.opacity = hc ? .35 : 1;
  $('#gSlots').innerHTML = slots.map((sl, i) => `<div class="slot ${G0.zone === 'slots' && G0.i === i ? 'focus' : ''}">
    ${sl ? '<canvas></canvas>' : '<div class="none">New game</div>'}${sl && i === last ? '<span class="tag">Last played</span>' : ''}<p>${sl ? agoText(sl.ts) : `Slot ${i + 1}`}</p></div>`).join('');
  $('#gSlots').querySelectorAll('.slot').forEach((el, i) => { const sl = slots[i], c = el.querySelector('canvas'); if (sl && c) gameFrame(g, sl.seed, sl.scroll || 100, c); });
  const A = achievements(g), e = p.ra ? achFor(p, g) : {}, got = A.list.filter(a => e[a.id]), pts = got.reduce((t, a) => t + a.pts, 0), pct = Math.round(got.length / A.list.length * 100);
  $('#gAch').className = `achcard ${G0.zone === 'ach' ? 'focus' : ''}`;
  $('#gAch').innerHTML = p.ra ? `<span>${got.length} of ${A.list.length}</span><i class="bar"><b style="width:${pct}%"></b></i><span>${pts} of ${A.points} points</span>
      <div class="minib">${got.slice(-4).map(a => `<img src="${badge(g, a, false)}" alt="">`).join('')}</div><span class="go">View all</span>`
    : `<span>Link a RetroAchievements account in Settings to earn the ${A.list.length} achievements in this game.</span>`;
  $('#gDesc').textContent = g.desc;
  $('#gCredit').textContent = artCredit();
  const media = mediaFor(g), mi = G0.zone === 'media' ? G0.i : -1;
  $('#gMedia').innerHTML = mediaStrip(g, media, mi, 'mthumb');
  $('#gMedia').style.transform = `translateX(${-Math.max(0, Math.min((G0.zone === 'media' ? G0.i : 0) - 2, media.length - 4.4)) * 174}px)`;
  const man = hasManual(g), mb = $('#gManual');
  mb.className = `manualbtn ${G0.zone === 'manual' ? 'focus' : ''} ${man ? '' : 'none'}`;
  mb.innerHTML = man ? `<b>Read the manual</b><small>${manualPages(g)} pages${(rec(g.id) || {}).manual && rec(g.id).manual.marks.length ? `, ${rec(g.id).manual.marks.length} bookmarked` : ''}</small>` : `<b>No manual found</b><small>${usingSS() ? 'ScreenScraper doesn’t have one' : 'Manuals come from ScreenScraper'}</small>`;
}
function gameAction() {
  const g = byId[S.game.id], r = rec(g.id), G0 = S.game;
  if (G0.zone === 'ach') return curProfile().ra ? openAch(g.id) : openSettings(5);
  if (G0.zone === 'slots') return startLaunch(g, G0.i, !!(r && r.slots[G0.i]), 'game');
  const a = gameActions()[G0.i];
  if (a === 'Resume') return startLaunch(g, r.lastSlot, true, 'game');
  if (a === 'Play') return startLaunch(g, r ? Math.max(0, r.slots.findIndex(x => !x)) : 0, false, 'game');
  if (a === 'col') return addToCollection(g);
  if (a === 'New game') { const free = r.slots.findIndex(x => !x); G0.zone = 'slots'; G0.i = free < 0 ? 0 : free; fillGame(); return toast(free < 0 ? 'All four slots are used. Choose one to replace.' : 'Choose a slot for the new game'); }
  toggleFav(g);
}
function toggleFav(g) {
  const r = ensureRec(g); r.fav = !r.fav; sfx.select(); toast(r.fav ? `Added ${g.title} to Favorites` : `Removed ${g.title} from Favorites`);
  if (S.screen === 'game') fillGame(); else if (S.screen === 'home') refreshHome(); else if (S.screen === 'library') refreshLibrary(false);
}

/* ================= achievements page ================= */
const ACH_FILTERS = ['All', 'Earned', 'Locked'];
function openAch(id) { S.ach = { id, filter: 0, i: 0, from: S.screen }; S.screen = 'ach'; show('ach'); sfx.select(); fillAch(true); }
function achRows() {
  const g = byId[S.ach.id], e = achFor(curProfile(), g);
  return achievements(g).list.map(a => ({ a, e: e[a.id] })).filter(x => S.ach.filter === 0 || (S.ach.filter === 1 ? x.e : !x.e));
}
function fillAch(full) {
  const g = byId[S.ach.id], A = achievements(g), p = curProfile(), e = achFor(p, g), rows = achRows();
  S.ach.i = clamp(S.ach.i, 0, Math.max(0, rows.length - 1));
  if (full) {
    const got = A.list.filter(a => e[a.id]), pct = Math.round(got.length / A.list.length * 100), pts = got.reduce((t, a) => t + a.pts, 0), hc = got.filter(a => e[a.id].hc).length;
    const win = A.list.find(a => a.type === 'Win condition'), beaten = win && e[win.id];
    $('#achSide').innerHTML = `<div class="gp-sys">${SYS[g.sys].long}</div><h2>${g.title}</h2>
      <div class="ring" style="background: conic-gradient(var(--accent) ${pct * 3.6}deg, rgba(238,241,255,.12) 0)"><span>${pct}%<small>complete</small></span></div>
      <div class="stats"><div>Achievements<b>${got.length} of ${A.list.length}</b></div><div>Points<b>${pts} of ${A.points}</b></div>
        <div>Hardcore unlocks<b>${hc}</b></div><div>Players<b>${A.players.toLocaleString()}</b></div></div>
      <div class="award">${got.length === A.list.length ? '<b>Mastered.</b> You’ve earned every achievement.' : beaten ? '<b>Beaten.</b> Earn the rest to master it.' : 'Finish the game to earn the <b>Beaten</b> award. Earn every achievement to master it.'}</div>`;
  }
  $('#achChips').innerHTML = ACH_FILTERS.map((f, i) => `<div class="${i === S.ach.filter ? 'sel' : ''}">${f}</div>`).join('');
  $('#achList').innerHTML = rows.length ? rows.map((x, i) => `<div class="arow ${x.e ? '' : 'locked'} ${i === S.ach.i ? 'focus' : ''}"><img src="${badge(g, x.a, !x.e)}" alt="">
    <div class="t"><b>${x.a.title}</b><p>${x.a.desc}</p><div class="tags">${x.a.type ? `<i>${x.a.type}</i>` : ''}${x.e && x.e.hc ? '<i class="hc">Hardcore</i>' : ''}</div></div>
    <div class="r"><b>${x.a.pts} ${x.a.pts === 1 ? 'point' : 'points'}</b>${x.e ? `Earned ${dateText(x.e.date)}` : 'Locked'}<br>${Math.max(1, Math.round(x.a.rate * 100))}% of players</div></div>`).join('')
    : `<div class="arow"><div class="t"><b>Nothing here yet</b><p>Earned achievements show up here as you unlock them.</p></div></div>`;
  const rowH = 132, viewH = 1080 - 96 - 60 - 70, total = rows.length * rowH + 40;
  const off = clamp(S.ach.i * rowH + rowH / 2 - viewH / 2, 0, Math.max(0, total - viewH));
  $('#achList').style.transform = `translateY(${-off}px)`;
}

/* ================= settings page ================= */
function settingsModel() {
  const p = curProfile(), P = p.prefs;
  const tog = (label, key, desc, obj = P) => ({ label, desc, value: obj[key] ? 'On' : 'Off', act: () => { obj[key] = !obj[key]; } });
  const cyc = (label, key, vals, desc, names) => ({ label, desc, value: names ? names[P[key]] : P[key], act: () => { const v = vals.indexOf(P[key]); P[key] = vals[(v + 1) % vals.length]; } });
  const info = (label, value, desc, bar) => ({ label, desc, value, info: true, bar });
  const action = (label, value, desc, act) => ({ label, desc, value, act, action: true });
  return [
    { cat: 'Profile', intro: 'Who you are on this console.', items: [
      action('Name', p.name, 'Shown on the Who’s playing screen.', () => toast('Editing names needs an on-screen keyboard, which isn’t in this prototype yet')),
      action('Planet color', 'Change', 'The color of your profile planet.', () => { const i = PALETTES.findIndex(c => c[1] === p.colors[1]); p.colors = PALETTES[(i + 1) % PALETTES.length]; paintProfile(); }),
      action('RetroAchievements', p.ra || 'Link account', p.ra ? 'Linked. Achievements you earn are added to this account.' : 'Link a free account to earn achievements in your games.', () => { p.ra = p.ra ? null : p.name.toLowerCase() + '_plays'; toast(p.ra ? 'Account linked' : 'Account unlinked'); }),
      action('Switch profile', '', 'Go back to the Who’s playing screen.', () => { anim.clear(); enterProfiles(); return true; })] },
    { cat: 'Display', intro: 'How menus and games look on your TV.', items: [
      cyc('Menu resolution', 'res', ['1080p', '720p'], 'The resolution of these menus. 720p keeps animations smoother on a Raspberry Pi 3.'),
      cyc('Game borders', 'border', ['space', 'minimal', 'off'], 'What surrounds the game picture while you play.', { space: 'Space', minimal: 'Minimal', off: 'Off' }),
      cyc('Screen filter', 'filter', ['None', 'Scanlines', 'LCD grid'], 'A filter over the game picture. With borders on too, it runs best at 720p on a Pi 3.'),
      tog('Background motion', 'motion', 'Slowly drifting stars and turning planets.'),
      cyc('Clock', 'clock24', [false, true], 'How the time is shown.', { false: '12-hour', true: '24-hour' })] },
    { cat: 'Sound', intro: 'Sounds in the menus. Game volume is set while you play.', items: [
      tog('Interface sounds', 'sound', 'Soft clicks when you move and select.'),
      cyc('Music on Home', 'music', ['Off', 'Quiet'], 'Gentle ambient music on Home and in the Library.'),
      cyc('Volume', 'volume', ['Low', 'Medium', 'High'], 'Overall volume of the menus.')] },
    { cat: 'Controllers', intro: 'Pair controllers and choose how buttons work.', items: [
      info('Controller 1', 'Connected, 80%', 'Bluetooth controller, battery 80%.'),
      action('Pair a controller', 'Start', 'Put your controller in pairing mode, then choose Start.', () => toast('Looking for controllers…')),
      cyc('Button layout', 'layout', ['Nintendo', 'Xbox'], 'Which button confirms and which goes back.'),
      cyc('Home button', 'combo', ['Select + Start', 'Hold Select'], 'For controllers without a Home button.')] },
    { cat: 'Games', intro: 'How saving and playing work.', items: [
      tog('Save when you quit', 'autosave', 'Pressing Home while playing saves your place first.'),
      cyc('Resume uses', 'resume', ['Last save', 'Ask each time'], 'What Resume loads.'),
      tog('Rewind', 'rewind', 'Undo mistakes. It uses extra processing, so some games may slow down on a Raspberry Pi 3.'),
      cyc('Fast-forward speed', 'ff', ['2×', '3×', '4×'], 'How fast fast-forward runs.')] },
    { cat: 'Achievements', intro: p.ra ? `Linked to ${p.ra} on RetroAchievements.` : 'No RetroAchievements account is linked to this profile.', items: [
      action('Account', p.ra || 'Link account', p.ra ? 'Choose to unlink this account.' : 'Link a free account to earn achievements.', () => { p.ra = p.ra ? null : p.name.toLowerCase() + '_plays'; toast(p.ra ? 'Account linked' : 'Account unlinked'); }),
      tog('Hardcore mode', 'hardcore', 'Achievements earn double points, but saves, rewind and fast-forward are off while you play.'),
      tog('Unlock pop-ups', 'achPop', 'A short message at the top of the screen when you earn one.'),
      tog('Progress on Home', 'achHome', 'Show achievement progress for each game on Home.')] },
    artSettings(),
    { cat: 'Network', intro: 'Connected to HomeNet.', items: [
      info('Wi-Fi', 'HomeNet', 'Strong signal.'),
      tog('Add games from a computer', 'upload', 'Open http://shelf.local on a computer on the same network to add games and art.', SYSP),
      tog('Set the time automatically', 'ntp', 'Uses the internet to keep the clock right.', SYSP)] },
    { cat: 'System', intro: 'This console and its storage.', items: [
      info('Storage', '23.4 of 59.5 GB', 'Games, saves, screenshots and art on the SD card.', 39),
      info('Version', 'Shelf 0.2', 'Prototype build.'),
      action('Check for updates', 'Check', 'Looks for a newer version of Shelf.', () => toast('Shelf is up to date')),
      action('Restart', '', 'Restart the console.', () => { powerOverlay('restart'); return true; }),
      action('Turn off', '', 'Shut down safely. Unplug it after the light goes out.', () => { powerOverlay('off'); return true; })] },
  ];
}
// art and info for every game, from free sources or a ScreenScraper account; cartridge labels come from the label cropper
function artSettings() {
  const A = SYSP.art, ss = A.source === 'ScreenScraper', tog = (label, key, desc, value) => ({ label, desc, value: value || (A[key] ? 'On' : 'Off'), act: () => { A[key] = !A[key]; } });
  return { cat: 'Art and info', intro: 'Box art, screenshots and game details are downloaded for you. If a picture is missing, Shelf tries your RetroAchievements account next. Cartridge labels come from the label cropper on your computer.', items: [
    { label: 'Source', value: A.source, desc: ss ? 'The most complete art: box fronts, backs and 3D boxes, title screens, screenshots, logos, release dates by region and descriptions.'
        : 'RetroArch’s thumbnail and game databases, plus Wikipedia. No account needed. Box backs and spines are made from the front art.',
      act: () => { A.source = ss ? 'Free sources' : 'ScreenScraper'; if (!ss && !A.ss) toast('Sign in to ScreenScraper below to use it'); } },
    ...(ss ? [{ label: 'ScreenScraper account', value: A.ss || 'Sign in', action: true, desc: A.ss ? 'Signed in. Choose to sign out.' : 'A free account from screenscraper.fr. It also raises how many games you can look up each day.',
      act: () => { A.ss = A.ss ? null : 'jordan_ss'; toast(A.ss ? 'Signed in to ScreenScraper' : 'Signed out of ScreenScraper'); } }] : []),
    tog('Get art for new games', 'auto', 'When you add games, Shelf downloads their art and details automatically.'),
    tog('Box backs and spines', 'backs', ss ? 'Real scans where ScreenScraper has them.' : 'Not available from free sources, so Shelf builds them from the front art.', ss ? null : 'Built by Shelf'),
    tog('Screenshots and title screens', 'shots', 'Shown in each game’s media and on the backs of boxes.'),
    tog('Gameplay videos', 'videos', ss ? 'Short clips for each game’s media. Shelf downloads the smaller versions, which play smoothly on a Raspberry Pi 3.' : 'Only available from ScreenScraper.', ss ? null : 'Not available'),
    tog('Manuals', 'manuals', ss ? 'Scans of the original manuals, where ScreenScraper has them.' : 'Only available from ScreenScraper.', ss ? null : 'Not available'),
    tog('Details and descriptions', 'info', 'Release year, developer, genre, players and a short description.'),
    { label: 'Region', value: A.region, desc: 'Which region’s box art to prefer when a game was released in several.', act: () => { const R = ['USA', 'Europe', 'Japan', 'World']; A.region = R[(R.indexOf(A.region) + 1) % R.length]; } },
    { label: 'Update art now', action: true, value: A.running ? `${A.done} of ${GAMES.length}` : 'Update', desc: A.running ? 'Downloading. You can keep using Shelf while this runs.' : `Look up art and details for all ${GAMES.length} games again.`,
      act: () => { if (!A.running) runArtUpdate(); } },
  ] };
}
function runArtUpdate() {
  const A = SYSP.art; A.running = true; A.done = 0;
  const timer = setInterval(() => {
    A.done++;
    if (A.done >= GAMES.length) { clearInterval(timer); A.running = false; toast(`Art and details updated for ${GAMES.length} games`); }
    if (S.screen === 'settings') fillSettings();
  }, 110);
}
function artCredit() { const A = SYSP.art; return A.source === 'ScreenScraper' && A.ss ? 'Art and details from ScreenScraper.' : 'Art from RetroArch’s thumbnail database. Details from Wikipedia.'; }
function openSettings(cat = 0) { closePmenu(); S.set = { cat, zone: cat ? 'items' : 'nav', i: 0, back: S.screen }; S.screen = 'settings'; show('settings'); sfx.select(); fillSettings(); }
function fillSettings() {
  const M = settingsModel(), C = M[S.set.cat];
  $('#setNav').innerHTML = M.map((c, i) => `<div class="${i === S.set.cat ? 'cur' : ''} ${S.set.zone === 'nav' && i === S.set.cat ? 'focus' : ''}">${c.cat}</div>`).join('');
  $('#setBody').innerHTML = `<h2>${C.cat}</h2><p class="intro">${C.intro}</p><div class="sview"><div class="srows">` + C.items.map((it, i) => `<div class="srow ${S.set.zone === 'items' && i === S.set.i ? 'focus' : ''}">
    <div><b>${it.label}</b><p>${it.desc}</p>${it.bar ? `<i class="bar"><b style="width:${it.bar}%"></b></i>` : ''}</div>${it.value !== '' ? `<em class="${it.action ? 'act' : ''}">${it.value}</em>` : ''}</div>`).join('') + '</div></div>';
  // long sections scroll so the selected row stays in view
  const rows = $('#setBody .srows'), f = rows.querySelector('.srow.focus');
  if (f) { const view = $('#setBody .sview').clientHeight, off = clamp(f.offsetTop + f.offsetHeight / 2 - view / 2, 0, Math.max(0, rows.scrollHeight - view)); rows.style.transform = `translateY(${-off}px)`; }
}
function settingsAct() {
  const it = settingsModel()[S.set.cat].items[S.set.i];
  if (it.info) return;
  const left = it.act(); sfx.select();
  if (!left) fillSettings();
}
function closeSettings() {
  sfx.back(); const b = S.set.back;
  if (b === 'game') { S.screen = 'game'; show('game'); fillGame(); } else enterTab(b === 'library' ? 'library' : 'home');
}

/* ================= profile menu and power ================= */
const PMENU = ['Switch profile', 'Sleep', 'Turn off'];
function openPmenu() { S.pmenu = { open: true, i: 0 }; renderPmenu(); sfx.select(); }
function closePmenu() { S.pmenu.open = false; $('#pmenu').classList.remove('open'); }
function renderPmenu() { const m = $('#pmenu'); m.classList.toggle('open', S.pmenu.open); m.innerHTML = PMENU.map((t, i) => `<div class="${i === S.pmenu.i ? 'focus' : ''}">${t}</div>`).join(''); }
function pmenuAct() {
  const k = PMENU[S.pmenu.i]; closePmenu();
  if (k === 'Switch profile') { anim.clear(); return enterProfiles(); }
  powerOverlay(k === 'Sleep' ? 'sleep' : 'off');
}
function powerOverlay(kind) {
  S.sleep = kind;
  $('#sleepMsg').textContent = { sleep: 'Sleeping. Press any button to wake.', off: 'Shelf is off. Press any button to turn it on.', restart: 'Restarting' }[kind];
  $('#sleep').classList.add('on');
  if (kind === 'restart') setTimeout(() => wake(), 1800);
}
function wake() {
  const k = S.sleep; S.sleep = null; $('#sleep').classList.remove('on');
  if (k !== 'sleep') { anim.clear(); enterProfiles(); }
}
function goHome() {
  if (S.screen === 'play') return homeButton();
  if (S.screen === 'launch' || S.screen === 'profiles') return;
  closePmenu(); closeDlg(); S.kb.open = false; renderKb(); S.top.on = false; S.home.zone = 'row'; S.home.i = 0; sfx.tab(); enterTab('home');
}

/* ================= launching and coming back ================= */
function startLaunch(g, slot, resume, from) {
  S.seq = { kind: 'launch', g, slot, resume, t: 0, flags: {}, start: from === 'home' ? [-2.2, 1.0, 0] : [-2.6, .4, 0] };
  S.screen = 'launch'; show('none'); sfx.select();
  pose('console', {}).y = -7;
}
function startReturn() {
  const P = S.play; S.play = null;
  S.seq = { kind: 'return', g: P.g, slot: P.slot, saved: P.saved, t: 0, flags: {} };
  S.screen = 'launch'; show('none');
  const fl = $('#flash'); fl.style.transition = 'none'; fl.style.background = '#070b1e'; fl.style.opacity = 1;
  requestAnimationFrame(() => { fl.style.transition = 'opacity .7s ease'; fl.style.opacity = 0; });
}
function apply(M, p) { return [M[0] * p[0] + M[4] * p[1] + M[8] * p[2] + M[12], M[1] * p[0] + M[5] * p[1] + M[9] * p[2] + M[13], M[2] * p[0] + M[6] * p[1] + M[10] * p[2] + M[14]]; }
function project(p) { const v = [VP[0] * p[0] + VP[4] * p[1] + VP[8] * p[2] + VP[12], VP[1] * p[0] + VP[5] * p[1] + VP[9] * p[2] + VP[13], VP[3] * p[0] + VP[7] * p[1] + VP[11] * p[2] + VP[15]];
  return [(v[0] / v[2] + 1) / 2 * W, (1 - v[1] / v[2]) / 2 * H]; }
function consolePose(sys) { return SYS[sys].hand ? { x: 0, y: -.25, z: 0, ry: -.3, s: sys === 'gba' ? 1.55 : 1.75 } : { x: 0, y: -.55, z: 0, ry: -.38, s: 1.3 }; }
function cartPath(g, cp, M) {
  const slot = consoleModel(g.sys).slot, c = CART[g.sys], s = cp.s, at = apply(M, [slot.x, slot.y, slot.z]);
  const up = [0, 1, 0], fwd = [Math.sin(cp.ry), 0, Math.cos(cp.ry)], len = c.h * s;
  if (slot.kind === 'front') {
    const out = at.map((v, i) => v + fwd[i] * (len / 2 + 1.1)), inn = at.map((v, i) => v + fwd[i] * (len / 2 - len * .78));
    return { out, inn, rx: -Math.PI / 2, ry: cp.ry, press: -.05 * s, s };
  }
  const deep = slot.kind === 'hand' ? .62 : .58;
  return { out: at.map((v, i) => v + up[i] * (len / 2 + 1.0)), inn: at.map((v, i) => v + up[i] * (len / 2 - len * deep)), rx: 0, ry: cp.ry, turn: slot.kind === 'hand', press: 0, s };
}
function seqUpdate(dt) {
  const q = S.seq; q.t += dt; const t = q.t, g = q.g, F = q.flags, cp = pose('console', {}), target = consolePose(g.sys), box = anim.get('box');
  if (q.kind === 'launch') {
    if (box) approach(box, { x: -9, alpha: 0 }, dt, 4);
    Object.assign(cp, target, { y: lerp(-7, target.y, easeOut((t - .15) / .8)), alpha: 1 });
    if (t > 1.45 && !F.click) { F.click = 1; sfx.insert(); }
    if (t > 1.7 && !F.power) { F.power = 1; sfx.power(); }
    if (t > 2.35 && !F.flash) { F.flash = 1; const fl = $('#flash'); fl.style.background = 'radial-gradient(circle at 50% 50%, color-mix(in srgb, var(--accent) 55%, #070b1e) 0%, #070b1e 70%)'; fl.style.transition = 'opacity .45s ease-in'; fl.style.opacity = 1; }
    if (t > 2.85) { S.seq = null; startPlay(g, q.slot, q.resume); }
  } else {
    if (t > .45 && !F.click) { F.click = 1; sfx.insert(); }
    Object.assign(cp, target, { y: lerp(target.y, -7, ease((t - .6) / .45)), alpha: 1 });
    if (t > 1.05 && q.now) {   // hand the cartridge over to the Home row, from wherever it is on screen
      const [px, py] = project(q.now.pos), [x, y] = pw(px, py), d = Math.hypot(q.now.pos[0] - EYE[0], q.now.pos[1] - EYE[1], q.now.pos[2] - EYE[2]);
      S.seq = null; S.home = { i: 0, f: 0, zone: 'row', card: 0 }; S.top.on = false;
      enterTab('home');
      const was = NODRAW; NODRAW = true; drawHome(10, S.time); NODRAW = was;   // everything else is already in place
      Object.assign(pose('home:' + g.id, {}), { x, y, z: 0, rx: 0, ry: q.now.ry, s: q.now.s * CAMZ / d, alpha: 1, bright: 1 });
      sfx.saved();
      toast(q.saved ? `Saved to slot ${q.slot + 1}` : 'Hardcore mode is on, so no save was made');
    }
  }
}
function seqDraw() {
  const q = S.seq, t = q.t, g = q.g, cp = anim.get('console'), hand = SYS[g.sys].hand;
  let power = 0, camZ = CAMZ;
  if (q.kind === 'launch') { power = clamp((t - 1.7) / .25, 0, 1); camZ = lerp(CAMZ, hand ? 5.2 : 6.2, ease((t - 1.75) / 1.1)); }
  else power = clamp(1 - t / .3, 0, 1);
  setCamera(hand ? .7 : 2.3, camZ, 0); beginMeshes();
  const M = drawConsole(g.sys, cp, power), path = cartPath(g, cp, M);
  if (q.kind === 'launch') {
    const fly = easeOut((t - .25) / .75), ins = ease((t - 1.05) / .4);
    const pos = fly < 1 ? q.start.map((v, i) => lerp(v, path.out[i], fly)) : path.out.map((v, i) => lerp(v, path.inn[i], ins));
    if (ins >= 1) pos[1] += path.press * ease((t - 1.45) / .12);
    const turn = path.turn ? ease((t - .55) / .5) * Math.PI : 0;
    drawCart(g, { x: pos[0], y: pos[1], z: pos[2], rx: path.rx, ry: path.ry + turn, s: path.s * lerp(.55, 1, fly), alpha: clamp((t - .2) / .2, 0, 1) });
  } else {
    const up = ease((t - .3) / .4), stand = ease((t - .55) / .45);
    const pos = path.inn.map((v, i) => lerp(v, path.out[i], up)); pos[1] += stand * .6;
    const turn = path.turn ? (1 - ease((t - .45) / .45)) * Math.PI : 0;
    const st = { x: pos[0], y: pos[1], z: pos[2], rx: lerp(path.rx, 0, stand), ry: lerp(path.ry + turn, -.1, stand), s: path.s, alpha: 1 };
    q.now = { pos, ry: st.ry, s: st.s };
    drawCart(g, st);
  }
  endMeshes();
}

/* ================= playing ================= */
function startPlay(g, slot, resume) {
  const p = curProfile(), r = ensureRec(g);
  S.play = { g, slot, scroll: resume && r.slots[slot] ? (r.slots[slot].scroll || 120) : 0, t: 0, achShown: false, seed: hash(g.id + 'play') };
  S.screen = 'play'; show('play');
  const pl = $('#s-play'), border = p.prefs.border; pl.classList.toggle('minimal', border === 'minimal'); pl.classList.toggle('off', border === 'off');
  const gc = $('#gamecv'), [w, h] = SYS[g.sys].res; gc.width = w; gc.height = h;
  const dh = border === 'off' ? 1080 : 900, dw = Math.round(dh * SYS[g.sys].aspect), box = { width: dw + 'px', height: dh + 'px', left: (W - dw) / 2 + 'px', top: (H - dh) / 2 + 'px' };
  Object.assign(gc.style, box);
  const sc = $('#scan'); Object.assign(sc.style, box); sc.width = dw; sc.height = dh; const x = sc.getContext('2d'); x.clearRect(0, 0, dw, dh);
  if (p.prefs.filter === 'Scanlines') { x.fillStyle = 'rgba(0,0,0,.28)'; for (let y = 0; y < dh; y += 4) x.fillRect(0, y + 2, dw, 2); }
  if (p.prefs.filter === 'LCD grid') { x.fillStyle = 'rgba(0,0,0,.22)'; const k = dh / h; for (let y = 0; y < dh; y += k) x.fillRect(0, Math.floor(y), dw, 1); for (let xx = 0; xx < dw; xx += k) x.fillRect(Math.floor(xx), 0, 1, dh); }
  const fl = $('#flash'); fl.style.transition = 'opacity .6s ease-out'; fl.style.opacity = 0;
  $('#playHint').classList.add('show'); setTimeout(() => $('#playHint').classList.remove('show'), 5000);
  $('#achPop').classList.remove('show');
}
function playUpdate(dt) {
  const P = S.play; P.t += dt; P.scroll += dt * 40;
  gameFrame(P.g, P.seed, Math.floor(P.scroll), $('#gamecv'));
  const p = curProfile();
  if (P.t > 4 && !P.achShown && p.ra && p.prefs.achPop) {
    P.achShown = true; const r = rec(P.g.id), A = achievements(P.g), e = achFor(p, P.g), next = [...A.list].sort((a, b) => b.rate - a.rate).find(a => !e[a.id]);
    if (next) { $('#achName').textContent = next.title; $('#achImg').src = badge(P.g, next, false); $('#achPop').classList.add('show'); sfx.saved(); r.earned++;
      setTimeout(() => $('#achPop').classList.remove('show'), 4200); }
  }
}
function homeButton() {
  const P = S.play; if (!P || P.saving) return; P.saving = true;
  const p = curProfile(), r = p.played[P.g.id], hc = p.prefs.hardcore;
  $('#saving').textContent = hc ? 'Quitting' : 'Saving'; $('#saving').classList.add('show');
  if (!hc) { r.slots[P.slot] = { ts: Date.now(), seed: P.seed, scroll: Math.floor(P.scroll) }; r.lastSlot = P.slot; }
  P.saved = !hc; r.last = Date.now(); r.minutes += Math.max(1, Math.round(P.t / 60));
  p.recent = [P.g.id, ...p.recent.filter(x => x !== P.g.id)];
  setTimeout(() => { $('#saving').classList.remove('show'); startReturn(); }, 800);
}

/* ================= dialogs, the on-screen keyboard and collections ================= */
const esc = t => String(t).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
function openDlg(title, items, onPick, i = 0) { S.dlg = { open: true, title, items, i, onPick }; renderDlg(); sfx.select(); }
function closeDlg() { S.dlg.open = false; renderDlg(); }
function renderDlg() {
  const d = S.dlg, el = $('#dlg'); el.classList.toggle('open', !!d.open); if (!d.open) return;
  el.innerHTML = `<h3>${esc(d.title)}</h3>` + d.items.map((it, i) => `<div class="ditem ${i === d.i ? 'focus' : ''}">${it.check != null ? `<span class="chk ${it.check ? 'on' : ''}"></span>` : ''}<span>${esc(it.label)}</span>${it.sub ? `<small>${esc(it.sub)}</small>` : ''}</div>`).join('');
}
const KB_ROWS = [[...'ABCDEFGHIJ'], [...'KLMNOPQRST'], [...'UVWXYZ', "'", '&', '-', '!'], [...'1234567890'], ['Space', 'Delete', 'Done']];
function openKb(title, value, onDone) { S.kb = { open: true, title, value, r: 0, c: 0, onDone }; renderKb(); sfx.select(); }
function renderKb() {
  const k = S.kb, el = $('#kb'); el.classList.toggle('open', !!k.open); if (!k.open) return;
  el.innerHTML = `<h3>${esc(k.title)}</h3><div class="kbfield">${esc(k.value)}<i class="caret"></i></div>` + KB_ROWS.map((row, r) =>
    `<div class="kbrow">${row.map((key, c) => `<span class="key ${key.length > 1 ? 'wide' : ''} ${r === k.r && c === k.c ? 'focus' : ''}">${key}</span>`).join('')}</div>`).join('');
}
function kbType(ch) {   // capitalizes the start of each word, like a title
  const k = S.kb; if (k.value.length >= 28) return;
  if (ch === ' ') { if (k.value && !k.value.endsWith(' ')) k.value += ' '; }
  else { const start = !k.value || k.value.endsWith(' '); k.value += start ? ch.toUpperCase() : ch.toLowerCase(); }
  renderKb(); sfx.move();
}
function kbDone() { const k = S.kb, v = k.value.trim(); if (!v) return toast('Type a name first'); k.open = false; renderKb(); k.onDone(v); }
function kbPress() {
  const key = KB_ROWS[S.kb.r][S.kb.c];
  if (key === 'Space') kbType(' '); else if (key === 'Delete') { S.kb.value = S.kb.value.slice(0, -1); renderKb(); sfx.back(); }
  else if (key === 'Done') kbDone(); else kbType(key);
}
function newCollection(addId) {
  openKb('Name your collection', '', name => {
    const p = curProfile();
    if (p.collections.some(c => c.name.toLowerCase() === name.toLowerCase())) return toast(`You already have a collection called ${name}`);
    const c = { id: Date.now().toString(36), name, games: addId ? [addId] : [] };
    p.collections.push(c); sfx.select(); toast(addId ? `Added to ${name}` : `Made ${name}`);
    if (S.screen === 'library') { S.lib.rail = railItems(p).findIndex(x => x.k === 'col:' + c.id); S.lib.i = 0; refreshLibrary(true); }
    else if (S.screen === 'game') fillGame();
  });
}
function collectionOptions(c) {
  openDlg(c.name, [{ label: 'Rename' }, { label: 'Delete collection' }, { label: 'Cancel' }], i => {
    closeDlg();
    if (i === 0) openKb('Rename collection', c.name, name => { c.name = name; toast(`Renamed to ${name}`); refreshLibrary(false); });
    else if (i === 1) openDlg(`Delete “${c.name}”?`, [{ label: 'Delete', sub: 'The games stay in your library' }, { label: 'Keep it' }], j => {
      closeDlg();
      if (j === 0) { const p = curProfile(); p.collections = p.collections.filter(x => x !== c); S.lib.rail = 0; S.lib.i = 0; S.lib.zone = 'rail'; toast(`Deleted ${c.name}`); refreshLibrary(true); }
    }, 1);
  });
}
function addToCollection(g) {
  const p = curProfile(), items = () => [...p.collections.map(c => ({ label: c.name, check: c.games.includes(g.id) })), { label: 'New collection…' }];
  openDlg(`Add ${g.title} to`, items(), i => {
    if (i === p.collections.length) { closeDlg(); return newCollection(g.id); }
    const c = p.collections[i], had = c.games.includes(g.id);
    c.games = had ? c.games.filter(x => x !== g.id) : [...c.games, g.id];
    sfx.select(); toast(had ? `Removed from ${c.name}` : `Added to ${c.name}`); S.dlg.items = items(); renderDlg();
  });
}

/* ================= media and manuals for a game ================= */
const usingSS = () => SYSP.art.source === 'ScreenScraper' && !!SYSP.art.ss;
function mediaFor(g) {
  const A = SYSP.art, ss = usingSS(), r = rng(hash(g.id + 'media')), out = [];
  if (ss && A.videos) out.push({ kind: 'video', label: 'Gameplay video', seed: hash(g.id + 'vid'), dur: 30 + Math.floor(r() * 40), scroll: 60 });
  if (A.shots) { out.push({ kind: 'title', label: 'Title screen' });
    const n = ss ? 4 : 2; for (let i = 0; i < n; i++) out.push({ kind: 'shot', label: 'Screenshot', seed: hash(g.id + 'shot' + i), scroll: 30 + i * 140 }); }
  out.push({ kind: 'box', label: 'Box, front' });
  if (ss && A.backs) out.push({ kind: 'back', label: 'Box, back' });
  return out;
}
const hasManual = g => usingSS() && SYSP.art.manuals && hash(g.id + 'hasman') % 7 !== 0;   // most games, not all
const manualPages = g => 16 + (hash(g.id + 'pages') % 3) * 4;
const fmtTime = s => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
function mediaStrip(g, items, focus, cls) {
  return items.map((m, i) => `<div class="${cls} ${i === focus ? 'focus' : ''}"><img src="${mediaThumb(g, m)}" alt="${m.label}">${m.kind === 'video' ? `<i class="playmark"></i><span class="dur">${fmtTime(m.dur)}</span>` : ''}</div>`).join('');
}

/* the media viewer: one picture large, the whole sequence below it */
function openMedia(i) { const g = byId[S.game.id]; S.mv = { gid: g.id, i, playing: false, t: 0 }; S.screen = 'media'; show('media'); sfx.select(); fillMedia(); }
function fillMedia() {
  const g = byId[S.mv.gid], items = mediaFor(g), m = items[S.mv.i], c = $('#mvCanvas'), src = mediaCanvas(g, m);
  const pixel = m.kind !== 'box' && m.kind !== 'back', boxH = 720, boxW = 1500;
  const dispW = pixel ? src.height * SYS[g.sys].aspect : src.width, sc = Math.min(boxW / dispW, boxH / src.height);   // game pictures use the console's real screen shape
  c.width = src.width; c.height = src.height; c.getContext('2d').drawImage(src, 0, 0);
  Object.assign(c.style, { width: Math.round(dispW * sc) + 'px', height: Math.round(src.height * sc) + 'px', imageRendering: pixel ? 'pixelated' : 'auto' });
  $('#mvTitle').textContent = g.title; $('#mvCount').textContent = `${S.mv.i + 1} of ${items.length}`;
  $('#mvCap').textContent = m.kind === 'video' ? `${m.label}, ${fmtTime(m.dur)}` : m.label;
  const vid = m.kind === 'video';
  $('#mvPlay').style.display = vid && !S.mv.playing ? 'grid' : 'none';
  $('#mvBar').style.display = vid ? 'flex' : 'none'; updateMvBar();
  $('#mvStrip').innerHTML = mediaStrip(g, items, S.mv.i, 'mvthumb');
  $('#mvStrip').style.transform = `translateX(${960 - (S.mv.i * 180 + 80)}px)`;   // keep the current one centered (160 wide, 20 apart)
}
function updateMvBar() {
  const g = byId[S.mv.gid], m = mediaFor(g)[S.mv.i]; if (m.kind !== 'video') return;
  $('#mvBar i').style.width = (S.mv.t / m.dur * 100) + '%'; $('#mvBar span').textContent = `${fmtTime(S.mv.t)} / ${fmtTime(m.dur)}`;
}
function mvUpdate(dt) {   // videos only move while playing
  const M = S.mv; if (!M || !M.playing) return; const g = byId[M.gid], m = mediaFor(g)[M.i];
  M.t += dt; if (M.t >= m.dur) { M.playing = false; M.t = 0; fillMedia(); return; }
  gameFrame(g, m.seed, Math.floor(m.scroll + M.t * 40), $('#mvCanvas')); updateMvBar();
}

/* the manual reader: two-page spreads, your place remembered, bookmarks */
function manualState(g) { const r = ensureRec(g); if (!r.manual) r.manual = { spread: 0, marks: [] }; return r.manual; }
const spreads = g => 1 + Math.ceil((manualPages(g) - 1) / 2);
function openManual() {
  const g = byId[S.game.id], st = manualState(g);
  S.man = { gid: g.id }; S.screen = 'manual'; show('manual'); sfx.select(); fillManual();
  if (st.spread > 0) toast(`Picked up where you left off, page ${st.spread * 2 - 1}`);
}
function spreadPages(g, s) { const n = manualPages(g); return s === 0 ? [0] : [2 * s - 1, 2 * s].filter(p => p < n); }
function fillManual() {
  const g = byId[S.man.gid], st = manualState(g), n = manualPages(g), pages = spreadPages(g, st.spread), marked = st.marks.includes(st.spread);
  $('#mnTitle').textContent = `${g.title} manual`;
  $('#mnPage').textContent = st.spread === 0 ? `Cover, ${n} pages` : pages.length > 1 ? `Pages ${pages[0]} and ${pages[1]} of ${n - 1}` : `Page ${pages[0]} of ${n - 1}`;
  const book = $('#mnBook'); book.innerHTML = ''; book.classList.toggle('single', pages.length === 1);
  pages.forEach(pg => { const c = manualPage(g, pg, n), el = document.createElement('canvas'); el.width = c.width; el.height = c.height; el.getContext('2d').drawImage(c, 0, 0); book.appendChild(el); });
  if (marked) book.insertAdjacentHTML('beforeend', '<i class="ribbon"></i>');
  book.classList.remove('turn'); void book.offsetWidth; book.classList.add('turn');
  const total = spreads(g);
  $('#mnTrack').innerHTML = Array.from({ length: total }, (_, s) => `<i class="${s === st.spread ? 'cur' : ''} ${st.marks.includes(s) ? 'mark' : ''}"></i>`).join('');
  $('#mnMarks').textContent = st.marks.length ? `${st.marks.length} bookmark${st.marks.length > 1 ? 's' : ''}` : 'No bookmarks yet';
}
function manualInput(a) {
  const g = byId[S.man.gid], st = manualState(g), total = spreads(g);
  if (a === 'left' || a === 'right') { const k = clamp(st.spread + (a === 'left' ? -1 : 1), 0, total - 1); if (k !== st.spread) { st.spread = k; sfx.move(); fillManual(); } }
  else if (a === 'y') { const i = st.marks.indexOf(st.spread); if (i < 0) { st.marks.push(st.spread); st.marks.sort((x, y) => x - y); toast('Bookmarked'); } else { st.marks.splice(i, 1); toast('Bookmark removed'); } sfx.select(); fillManual(); }
  else if (a === 'lb' || a === 'rb') {
    const next = a === 'rb' ? st.marks.find(s => s > st.spread) : [...st.marks].reverse().find(s => s < st.spread);
    if (next == null) toast(st.marks.length ? `No more bookmarks ${a === 'rb' ? 'ahead' : 'before this page'}` : 'Press Y to bookmark a page'); else { st.spread = next; sfx.tab(); fillManual(); }
  }
  else if (a === 'b') { sfx.back(); S.screen = 'game'; show('game'); S.game.zone = 'manual'; fillGame(); }
}
