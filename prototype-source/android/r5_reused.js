/* ================= achievements page ================= */
const ACH_FILTERS = ['All', 'Earned', 'Locked'];
function openAch(id) { S.ach = { id, filter: 0, i: 0, from: S.screen }; S.screen = 'ach'; show('ach'); sfx.select(); fillAch(true); }
function achRows() {
  const g = byId[S.ach.id], e = achFor(P, g);
  return achievements(g).list.map(a => ({ a, e: e[a.id] })).filter(x => S.ach.filter === 0 || (S.ach.filter === 1 ? x.e : !x.e));
}
function fillAch(full) {
  const g = byId[S.ach.id], A = achievements(g), e = achFor(P, g), rows = achRows();
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
  const rowH = 77, viewH = 750 - 28 - 20 - 44, total = rows.length * rowH + 20;
  const off = clamp(S.ach.i * rowH + rowH / 2 - viewH / 2, 0, Math.max(0, total - viewH));
  $('#achList').style.transform = `translateY(${-off}px)`;
}

/* ================= dialogs, the on-screen keyboard and collections ================= */
const esc = t => String(t).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
function openDlg(title, items, onPick, i = 0) { S.dlg = { open: true, title, items, i, onPick }; renderDlg(); sfx.select(); }
function closeDlg() { S.dlg.open = false; renderDlg(); }
function renderDlg() {
  const d = S.dlg, el = $('#dlg'); el.classList.toggle('open', !!d.open); if (!d.open) return;
  el.innerHTML = `<h3>${esc(d.title)}</h3><div class="dlist">` + d.items.map((it, i) => `<div class="ditem ${i === d.i ? 'focus' : ''}">${it.check != null ? `<span class="chk ${it.check ? 'on' : ''}"></span>` : ''}${it.img != null ? (it.img ? `<img class="dthumb" src="${it.img}" alt="">` : '<i class="dthumb"></i>') : ''}<span>${esc(it.label)}</span>${it.sub ? `<small>${esc(it.sub)}</small>` : ''}</div>`).join('') + '</div>';
  const L = el.querySelector('.dlist'), f = L.querySelector('.focus'); if (f) L.scrollTop = clamp(f.offsetTop - L.clientHeight / 2 + f.offsetHeight / 2, 0, L.scrollHeight);   // long lists keep the choice in view
}
const KB_ROWS = [[...'1234567890'], [...'QWERTYUIOP'], [...'ASDFGHJKL'], [...'ZXCVBNM'], ['&', "'", '-', '!', 'Space', 'Delete', 'Done']];
const KB_OFF = [0, 0, .5, 1.5, 0], kbW = key => key === 'Space' ? 3 : key.length > 1 ? 1.5 : 1;   // rows staggered like a real keyboard, in key widths
function kbCenter(r, c) { let x = KB_OFF[r]; for (let i = 0; i < c; i++) x += kbW(KB_ROWS[r][i]); return x + kbW(KB_ROWS[r][c]) / 2; }
function kbNearest(r, x) { let best = 0, d = 1e9; KB_ROWS[r].forEach((_, c) => { const e = Math.abs(kbCenter(r, c) - x); if (e < d) { d = e; best = c; } }); return best; }
function openKb(title, value, onDone) { S.kb = { open: true, title, value, r: 1, c: 0, onDone }; renderKb(); sfx.select(); }
function renderKb() {
  const k = S.kb, el = $('#kb'); el.classList.toggle('open', !!k.open); if (!k.open) return;
  el.innerHTML = `<h3>${esc(k.title)}</h3><div class="kbfield">${esc(k.value)}<i class="caret"></i></div>` + KB_ROWS.map((row, r) =>
    `<div class="kbrow" style="padding-left:${KB_OFF[r] * 93}px">${row.map((key, c) => `<span class="key ${key.length > 1 ? 'wide' : ''} ${r === k.r && c === k.c ? 'focus' : ''}" style="width:${kbW(key) * 93 - 10.5}px">${key}</span>`).join('')}</div>`).join('');
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
    const p = P;
    if (p.collections.some(c => c.name.toLowerCase() === name.toLowerCase())) return toast(`You already have a collection called ${name}`);
    const c = { id: Date.now().toString(36), name, games: addId ? [addId] : [] };
    p.collections.push(c); sfx.select(); toast(addId ? `Added to ${name}` : `Made ${name}`);
    if (S.screen === 'library') { S.lib.rail = railItems().findIndex(x => x.k === 'col:' + c.id); S.lib.i = 0; refreshLibrary(true); }
    else if (S.screen === 'game') fillGame();
  });
}
function collectionOptions(c) {
  openDlg(c.name, [{ label: 'Rename' }, { label: 'Delete collection' }, { label: 'Cancel' }], i => {
    closeDlg();
    if (i === 0) openKb('Rename collection', c.name, name => { c.name = name; toast(`Renamed to ${name}`); refreshLibrary(false); });
    else if (i === 1) openDlg(`Delete “${c.name}”?`, [{ label: 'Delete', sub: 'The games stay in your library' }, { label: 'Keep it' }], j => {
      closeDlg();
      if (j === 0) { P.collections = P.collections.filter(x => x !== c); S.lib.rail = 0; S.lib.i = 0; S.lib.zone = 'rail'; toast(`Deleted ${c.name}`); refreshLibrary(true); }
    }, 1);
  });
}
function addToCollection(g) {
  const p = P, items = () => [...p.collections.map(c => ({ label: c.name, check: c.games.includes(g.id) })), { label: 'New collection…' }];
  openDlg(`Add ${g.title} to`, items(), i => {
    if (i === p.collections.length) { closeDlg(); return newCollection(g.id); }
    const c = p.collections[i], had = c.games.includes(g.id);
    c.games = had ? c.games.filter(x => x !== g.id) : [...c.games, g.id];
    sfx.select(); toast(had ? `Removed from ${c.name}` : `Added to ${c.name}`); S.dlg.items = items(); renderDlg();
  });
}

/* ================= media and manuals for a game ================= */
const usingSS = () => P.art.source === 'ScreenScraper' && !!P.art.ss;
function mediaFor(g) {
  if (NATIVE) { const a = (P.meta[g.id] || {}).art || {};   // what was downloaded for it, or Shelf's own box art
    return [{ kind: 'box', label: a.box ? 'Box art' : 'Box art (made by Shelf)' }, ...(a.title ? [{ kind: 'title', label: 'Title screen', real: 1 }] : []), ...(a.shot ? [{ kind: 'shot', label: 'Screenshot', real: 1 }] : [])]; }
  const A = P.art, ss = usingSS(), r = rng(hash(g.id + 'media')), out = [];
  if (g.sys === 'android') return [{ kind: 'box', label: 'Store art' }, { kind: 'shot', label: 'Screenshot', seed: hash(g.id + 'shot0'), scroll: 40 }, { kind: 'shot', label: 'Screenshot', seed: hash(g.id + 'shot1'), scroll: 160 }];
  if (ss && A.videos) out.push({ kind: 'video', label: 'Gameplay video', seed: hash(g.id + 'vid'), dur: 30 + Math.floor(r() * 40), scroll: 60 });
  if (true) { out.push({ kind: 'title', label: 'Title screen' });
    const n = ss ? 4 : 2; for (let i = 0; i < n; i++) out.push({ kind: 'shot', label: 'Screenshot', seed: hash(g.id + 'shot' + i), scroll: 30 + i * 140 }); }
  out.push({ kind: 'box', label: 'Box, front' });
  if (ss && A.backs) out.push({ kind: 'back', label: 'Box, back' });
  return out;
}
const hasManual = g => g.sys !== 'android' && usingSS() && P.art.manuals && hash(g.id + 'hasman') % 7 !== 0;   // most games, not all
const manualPages = g => 16 + (hash(g.id + 'pages') % 3) * 4;
const fmtTime = s => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
function mediaStrip(g, items, focus, cls) {
  return items.map((m, i) => `<div class="${cls} ${i === focus ? 'focus' : ''}"><img src="${mediaThumb(g, m)}" alt="${m.label}">${m.kind === 'video' ? `<i class="playmark"></i><span class="dur">${fmtTime(m.dur)}</span>` : ''}</div>`).join('');
}

/* the media viewer: one picture large, the whole sequence below it */
function openMedia(i) { const g = byId[S.game.id]; S.mv = { gid: g.id, i, playing: false, t: 0 }; S.screen = 'media'; show('media'); sfx.select(); fillMedia(); }
function fillMedia() {
  const g = byId[S.mv.gid], items = mediaFor(g), m = items[S.mv.i], c = $('#mvCanvas'), src = mediaCanvas(g, m);
  const pixel = m.kind !== 'box' && m.kind !== 'back', boxH = 490, boxW = 1100;
  const dispW = pixel ? src.height * SYS[g.sys].aspect : src.width, sc = Math.min(boxW / dispW, boxH / src.height);   // game pictures use the console's real screen shape
  c.width = src.width; c.height = src.height; c.getContext('2d').drawImage(src, 0, 0);
  Object.assign(c.style, { width: Math.round(dispW * sc) + 'px', height: Math.round(src.height * sc) + 'px', imageRendering: pixel ? 'pixelated' : 'auto' });
  $('#mvTitle').textContent = g.title; $('#mvCount').textContent = `${S.mv.i + 1} of ${items.length}`;
  $('#mvCap').textContent = m.kind === 'video' ? `${m.label}, ${fmtTime(m.dur)}` : m.label;
  const vid = m.kind === 'video';
  $('#mvPlay').style.display = vid && !S.mv.playing ? 'grid' : 'none';
  $('#mvBar').style.display = vid ? 'flex' : 'none'; updateMvBar();
  $('#mvStrip').innerHTML = mediaStrip(g, items, S.mv.i, 'mvthumb');
  $('#mvStrip').style.transform = `translateX(${W / 2 - (S.mv.i * 140 + 64)}px)`;   // keep the current one centered (128 wide, 12 apart)
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
