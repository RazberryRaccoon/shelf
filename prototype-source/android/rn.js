/* ================= the Android app: real games, apps and emulators ================= */
// Inside Shelf's Android app, ShelfNative (Java, in android/) finds your ROMs and apps, launches emulators and keeps
// Shelf's state between runs. Everything here is skipped in the browser prototype. The invented library stays on as
// the Museum's examples.
const DEMO_GAMES = NATIVE ? GAMES.slice() : [];
let NATIVE_EMUS = [];   // emulator apps found on the handheld, by the names Shelf uses
const CORE_FILES = {   // RetroArch cores: Shelf's names → the core files RetroArch installs (as in ES-DE's Android setup)
  'Mesen': 'mesen', 'FCEUmm': 'fceumm', 'Nestopia UE': 'nestopia', 'QuickNES': 'quicknes', 'Snes9x': 'snes9x', 'bsnes': 'bsnes', 'bsnes-hd': 'bsnes_hd_beta',
  'Snes9x 2010': 'snes9x2010', 'Mesen-S': 'mesen-s', 'Beetle Supafaust': 'mednafen_supafaust', 'Mupen64Plus-Next': 'mupen64plus_next_gles3', 'ParaLLEl N64': 'parallel_n64',
  'Genesis Plus GX': 'genesis_plus_gx', 'PicoDrive': 'picodrive', 'BlastEm': 'blastem', 'Gambatte': 'gambatte', 'SameBoy': 'sameboy', 'Gearboy': 'gearboy', 'mGBA': 'mgba',
  'TGB Dual': 'tgbdual', 'gpSP': 'gpsp', 'VBA-M': 'vbam', 'VBA Next': 'vba_next', 'SwanStation': 'swanstation', 'Beetle PSX HW': 'mednafen_psx_hw', 'Beetle PSX': 'mednafen_psx',
  'PCSX ReARMed': 'pcsx_rearmed', 'melonDS DS': 'melondsds', 'melonDS': 'melonds', 'DeSmuME': 'desmume', 'DeSmuME 2015': 'desmume2015', 'NooDS': 'noods', 'PPSSPP': 'ppsspp',
};
function nativeAndroidGame(name, pkg) {   // an app you've marked as a game gets a box in the Library
  let g = GAMES.find(x => x.sys === 'android' && x.title === name);
  if (!g) { g = G('android', name, '', '', '', '', 0, ''); GAMES.push(g); byId[g.id] = g; }
  g.pkg = pkg; ANDROID_GAMES.add(name); return g;
}
function nativeScan() {
  let d; try { d = JSON.parse(ShelfNative.scan(nativeFolders())); } catch (e) { console.error(e); return; }
  const marked = GAMES.filter(g => g.sys === 'android');
  GAMES.length = 0;
  for (const r of d.games) GAMES.push({ id: hash(r.path).toString(36), sys: r.sys, title: r.title, year: '', dev: '', genre: '', players: '', ach: 0, desc: '', path: r.path });
  GAMES.forEach(applyMeta); GAMES.sort((a, b) => a.title.localeCompare(b.title)); GAMES.push(...marked);
  for (const k in byId) delete byId[k];
  for (const g of [...DEMO_GAMES, ...GAMES]) byId[g.id] = g;
  APPS.length = 0; d.apps.forEach(a => APPS.push({ name: a.name, pkg: a.pkg, iconData: a.icon, icon: 'ring', hue: hash(a.pkg) % 360 }));
  NATIVE_EMUS = d.emus; INSTALLED_APPS.clear(); d.emus.forEach(e => INSTALLED_APPS.add(e));
  INSTALLED_CORES.clear(); if (d.emus.includes('RetroArch')) P.cores.forEach(c => INSTALLED_CORES.add(c));   // RetroArch's cores can't be listed from outside it, so Shelf goes by the ones you've told it about
  for (const k of SYS_ORDER) if (k !== 'android' && !emuInstalled(P.emu[k])) P.emu[k] = PLAT[k].emus.find(emuInstalled) || P.emu[k];
}
// Games folders: by default ROMs/<console> on internal storage and SD cards; each console can also have a folder you choose
const romDir = k => ({ ps1: 'psx', ds: 'nds' })[k] || k;
const prettyPath = p => p.replace(/^\/storage\/emulated\/0\/?/, 'Internal storage/').replace(/^\/storage\/[^/]+\/?/, 'SD card/').replace(/\/$/, '').split('/').join(' › ');
function nativeFolders() { const o = {}; for (const k of SYS_ORDER) if (P.pf[k] && P.pf[k].folder) o[k] = P.pf[k].folder; return JSON.stringify(o); }
function nativePickFolder(k) {
  if (!ShelfNative.hasStorage()) { toast('First, allow Shelf to see your files'); return ShelfNative.requestStorage(); }
  ShelfNative.pickFolder(k);   // Android's folder picker; the answer comes back through __nativeFolder
}
function chooseFolders() {   // from setup: which console's folder to choose
  const ks = SYS_ORDER.filter(k => k !== 'android');
  openDlg('Choose a games folder for', ks.map(k => ({ label: PLAT[k].long, sub: P.pf[k].folder ? prettyPath(P.pf[k].folder) : `ROMs › ${romDir(k)}` })), i => { closeDlg(); nativePickFolder(ks[i]); });
}
function nativeSave() {
  try {
    ShelfNative.saveState(JSON.stringify({ v: 1, name: P.name, colors: P.colors, setup: P.setup, recent: P.recent, played: P.played, collections: P.collections, prefs: P.prefs,
      emu: P.emu, gameEmu: P.gameEmu, pf: P.pf, playing: P.playing || null, meta: P.meta, cores: P.cores, keys: P.keys, hidden: [...(P.hidden || [])], androidGames: GAMES.filter(g => g.sys === 'android').map(g => ({ name: g.title, pkg: g.pkg })) }));
  } catch (e) { console.error(e); }
}
function nativeLoad() {
  let s = null; try { s = JSON.parse(ShelfNative.loadState() || 'null'); } catch (e) { console.error(e); }
  if (!s) return;
  for (const k of ['name', 'colors', 'setup', 'recent', 'played', 'collections', 'gameEmu', 'playing', 'meta', 'cores', 'keys']) if (s[k] != null) P[k] = s[k];
  Object.assign(P.prefs, s.prefs || {}); Object.assign(P.emu, s.emu || {}); Object.assign(P.pf, s.pf || {});
  P.hidden = new Set(s.hidden || []);
  (s.androidGames || []).forEach(a => nativeAndroidGame(a.name, a.pkg));
}
function nativeLaunch(g, emu) {   // '' when the emulator opened, otherwise what went wrong, in plain words
  nativeSave();
  if (g.sys === 'android') return ShelfNative.openApp(g.pkg || '') ? '' : `Couldn’t open ${g.title}`;
  const ra = emu.startsWith('RetroArch: ');
  return ShelfNative.launch(JSON.stringify({ emu: ra ? 'RetroArch' : emuApp(emu), core: ra ? CORE_FILES[emu.slice(11)] || '' : '', path: g.path, sys: g.sys })) || '';
}
const WEBVIEW_OK = 100;   // older engines get a suggestion to update Android System WebView
function nativeInfo() { try { return JSON.parse(ShelfNative.info()); } catch (e) { return { model: 'Android device', android: '', version: '' }; } }
// Back from a game Shelf no longer remembers launching (Android closed Shelf's page while the game ran): rebuild
// what's needed from the saved state and play the return animation anyway.
function nativeBackFromGame() {
  const pl = P.playing, g = pl && byId[pl.id]; if (!g || S.seq) return false;
  if (S.screen === 'setup') { P.playing = null; return false; }
  S.game.id = g.id; S.play = { g, slot: 0, from: pl.from === 'game' ? 'home' : pl.from, t: 0, native: true, start: pl.start || Date.now() }; S.screen = 'play';
  homeFromPlay(); return true;
}
// RetroArch cores: Shelf can't see which ones RetroArch has downloaded (Android keeps that folder private), so it
// asks the first time you play a console, and keeps the list you can change in Settings › Emulators.
const coresFor = k => (EMU_LIST[k] || { cores: [] }).cores;
function knowCore(c) { if (!P.cores.includes(c)) P.cores.push(c); if (INSTALLED_APPS.has('RetroArch')) INSTALLED_CORES.add(c); }
function askCore(sys, then) {
  const cs = coresFor(sys), items = [...cs.map(c => ({ label: c, sub: P.cores.includes(c) ? 'You have this one' : '' })), { label: 'How do I check?', sub: '' }];
  openDlg(`Which RetroArch core do you use for ${PLAT[sys].name}?`, items, i => {
    if (i === cs.length) return toast('In RetroArch: Main Menu › Load Core lists your cores. Download a Core adds more.');
    closeDlg(); knowCore(cs[i]); P.emu[sys] = 'RetroArch: ' + cs[i]; nativeSave(); toast(`${PLAT[sys].name} games use RetroArch, ${cs[i]}. Change it in Settings › Platforms.`); then('RetroArch: ' + cs[i]);
  }, Math.max(0, cs.findIndex(c => P.cores.includes(c))));
}
function editCores() {   // tick the cores you have
  const all = [...new Set(SYS_ORDER.flatMap(coresFor))];
  const items = () => [...all.map(c => ({ label: c, sub: SYS_ORDER.filter(k => coresFor(k).includes(c)).map(k => PLAT[k].name).join(', '), check: P.cores.includes(c) })), { label: 'Done', sub: '' }];
  openDlg('RetroArch cores you have', items(), i => {
    if (i === all.length) { closeDlg(); nativeScan(); nativeSave(); return fillSettings(); }
    const c = all[i]; if (P.cores.includes(c)) { P.cores = P.cores.filter(x => x !== c); INSTALLED_CORES.delete(c); } else knowCore(c);
    S.dlg.items = items(); renderDlg();
  });
}
function nativeOpenEmu(g, e) { if (!ShelfNative.openEmulator(emuApp(e))) toast(`${emuApp(e)} isn’t installed`); }

/* ---------- art and details: found with libretro's free database and art (android/…/Art.java) ---------- */
// P.meta[game id] = { name (libretro's), releaseyear, developer, publisher, genre, description, art: {box, title, shot}, t }
const ART = new Map();   // "id:kind" → { img, ready }
function artImg(g, kind) {   // a downloaded picture for this game, once it has loaded (null otherwise, and always in the browser)
  if (!NATIVE || !g) return null;
  const m = P.meta[g.id]; if (!m || !m.art || !m.art[kind]) return null;
  const key = g.id + ':' + kind + ':' + m.t; let a = ART.get(key);
  if (!a) {
    a = { img: new Image(), ready: false }; ART.set(key, a);
    a.img.onload = () => { a.ready = true; artLoaded(g); };
    a.img.src = ShelfNative.artData(g.id, kind);
  }
  return a.ready ? a.img : null;
}
function artStamp(g) {   // changes whenever a game's art changes or finishes loading, so textures and pictures are made again
  if (!NATIVE || !g || !P.meta[g.id]) return '';
  const m = P.meta[g.id]; let n = 0; for (const k of ART_KINDS.map(a => a.k)) { const a = ART.get(g.id + ':' + k + ':' + m.t); if (a && a.ready) n++; }
  return ':' + m.t + '.' + n;
}
let artRefresh = 0;
function artLoaded(g) {   // throw away what was drawn with the old art, and redraw what's on screen
  for (const k of [...texCache.keys()]) if (k.includes(g.id) && !k.endsWith(artStamp(g))) { gl.deleteTexture(texCache.get(k)); texCache.delete(k); }
  clearTimeout(artRefresh); artRefresh = setTimeout(() => { if (S.screen === 'game') fillGame(); else if (S.screen === 'home') refreshHome(); }, 120);
}
const cleanDetail = s => (s || '').replace(/\s*\?+$/, '').trim();
function applyMeta(g) {   // libretro's details onto the game
  const m = P.meta[g.id]; if (!m) return;
  if (m.releaseyear) g.year = m.releaseyear; if (m.developer) g.dev = cleanDetail(m.developer);
  if (m.genre) g.genre = m.genre.replace(/\s*\(.*\)$/, ''); if (m.description) g.desc = m.description;
}
const ART_REQ = {}; let artSeq = 0;
function artCall(fn, ...args) { return new Promise(res => { const id = 'a' + (++artSeq); ART_REQ[id] = res; ShelfNative[fn](id, ...args); }); }
window.__nativeArt = (id, body) => { const f = ART_REQ[id]; delete ART_REQ[id]; if (f) f(body); };
function artSaved(g, res) {   // a download finished: remember it, use it
  if (!res || res.error) return false;
  const old = P.meta[g.id] || {}, src = {}; for (const k in res.art || {}) src[k] = res.src || 'libretro';
  P.meta[g.id] = { name: res.name, releaseyear: res.releaseyear, developer: res.developer, publisher: res.publisher, genre: res.genre, description: res.description, art: res.art || {}, src: Object.assign({}, old.src || {}, src), t: res.t };
  applyMeta(g); nativeSave(); return true;
}
const romQuery = g => (g.path || g.title).split('/').pop().replace(/\.[^.]+$/, '');   // the file's name, tags and all: the closest to libretro's names

// One game: search (starting from its file name), show the closest matches with their box art, and use the one you pick.
function openArtPicker(g, query = romQuery(g)) {
  const head = { label: `Search: “${query}”`, sub: 'Change' };
  openDlg(`Art and details for ${g.title}`, [head, { label: 'Searching…', sub: '' }], i => { if (i === 0) editQuery(); });
  const editQuery = () => { closeDlg(); openKb('Search for', query, v => openArtPicker(g, v || romQuery(g))); };
  artCall('artSearch', g.sys, query, g.path || '').then(res => {
    if (!S.dlg.open || !S.dlg.items[0] || S.dlg.items[0].label !== head.label) return;   // closed, or a newer search
    if (res.error) { S.dlg.items = [head, { label: res.error, sub: '' }]; return renderDlg(); }
    const list = res.results || [];
    S.dlg.items = [head, ...(list.length ? list.map(r => ({ label: r.name, img: r.thumb || '', sub: r.exact ? 'Exact match' : r.thumb ? '' : 'No box art', cls: 'art' })) : [{ label: 'Nothing found. Try fewer or different words.', sub: '' }])];
    S.dlg.onPick = i => {
      if (i === 0) return editQuery();
      const r = list[i - 1]; if (!r) return;
      closeDlg(); toast(`Downloading art for ${g.title}…`);
      artCall('artApply', g.id, g.sys, r.name).then(res => { if (artSaved(g, res)) toast(`Art and details updated for ${g.title}`); else toast(res.error || 'Couldn’t download the art'); refreshAfterArt(); });
    };
    S.dlg.i = list.length ? 1 : 0; renderDlg();
  });
}
function refreshAfterArt() { if (S.screen === 'settings') fillSettings(); else if (S.screen === 'game') fillGame(); else if (S.screen === 'home') refreshHome(); else if (S.screen === 'library') refreshLibrary(false); }

// Every game: matched by the ROM's checksum, or by a close match of its file name, in the background.
const ART_RUN = { on: false, i: 0, n: 0, found: 0 };
function artAll(again) {
  if (ART_RUN.on) { ShelfNative.artStop(); return; }
  const list = GAMES.filter(g => g.sys !== 'android' && g.path && (again || !P.meta[g.id]));
  if (!list.length) return toast('Every game already has art. Use Find art again to replace it.');
  Object.assign(ART_RUN, { on: true, i: 0, n: list.length, found: 0 }); toast(`Finding art for ${list.length} games…`); refreshAfterArt();
  const id = 'r' + (++artSeq);
  window.__nativeArtProgress = (rid, p) => {
    if (rid !== id) return;
    if (p.done || p.error) { ART_RUN.on = false; toast(p.error ? p.error : `Found art for ${p.found} of ${ART_RUN.n} games${p.cancelled ? ' (stopped)' : ''}`); return refreshAfterArt(); }
    ART_RUN.i = p.i + 1; const g = byId[p.key];
    if (g && p.result && artSaved(g, p.result)) ART_RUN.found++;
    if (S.screen === 'settings') fillSettings();
  };
  ShelfNative.artAuto(id, JSON.stringify(list.map(g => ({ key: g.id, sys: g.sys, path: g.path, query: romQuery(g) }))), JSON.stringify(P.keys));
}


/* ---------- art from several services, one kind at a time ---------- */
const ART_KINDS = [{ k: 'box', name: 'Box front' }, { k: 'back', name: 'Box back' }, { k: 'spine', name: 'Spine' },
  { k: 'label', name: 'Label' }, { k: 'title', name: 'Title screen' }, { k: 'shot', name: 'Screenshot' }];
const ANDROID_KINDS = ['box', 'back', 'spine', 'title', 'shot'];   // an Android game's keep case has no cartridge or disc label
const kindsFor = g => ART_KINDS.filter(a => g.sys !== 'android' || ANDROID_KINDS.includes(a.k));
const kindName = (g, k) => k === 'label' ? (CART[g.sys] && (CART[g.sys].disc || CART[g.sys].umd) ? 'Disc art' : 'Cartridge label') : ART_KINDS.find(a => a.k === k).name;
// each service: what it gives, and what you need to connect it (fields are asked for with Android's own text box)
const ART_SOURCES = [   // noAndroid: has no Android games
  { id: 'libretro', name: 'libretro', kinds: ['box', 'title', 'shot'], needs: 'No account needed', fields: [], noAndroid: true },
  { id: 'ss', name: 'ScreenScraper', kinds: ['box', 'back', 'spine', 'label', 'title', 'shot'], needs: 'A free screenscraper.fr account, plus developer credentials you request once on their forum',
    fields: [['devid', 'Developer ID'], ['devpassword', 'Developer password', 1], ['user', 'Your ScreenScraper username', 0, 1], ['password', 'Your ScreenScraper password', 1, 1]] },
  { id: 'tgdb', name: 'TheGamesDB', kinds: ['box', 'back', 'title', 'shot'], needs: 'An API key from thegamesdb.net (request it under API Access)', fields: [['key', 'TheGamesDB API key', 1]] },
  { id: 'ra', name: 'RetroAchievements', kinds: ['box', 'title', 'shot'], noAndroid: true, needs: 'Your RetroAchievements username and web API key (Settings › Keys on the site)', fields: [['user', 'RetroAchievements username'], ['key', 'Web API key', 1]] },
  { id: 'sgdb', name: 'SteamGridDB', kinds: ['box'], needs: 'An API key from steamgriddb.com (Preferences › API)', fields: [['key', 'SteamGridDB API key', 1]] },
  { id: 'igdb', name: 'IGDB', kinds: ['box', 'shot'], needs: 'A Twitch developer app’s client ID and secret (dev.twitch.tv › Your Console)', fields: [['id', 'Twitch client ID'], ['secret', 'Twitch client secret', 1]] },
];
const srcConnected = s => s.fields.filter(f => !f[3]).every(f => (P.keys[s.id] || {})[f[0]]);   // [field, label, secret, optional]
function kindTip(k, g) {   // which services give this kind of art (for this game's platform), and which of those you've connected
  const all = ART_SOURCES.filter(s => s.kinds.includes(k) && !(g && g.sys === 'android' && s.noAndroid)), on = all.filter(srcConnected);
  if (!all.length) return 'No service has this for Android games.';
  return `From ${all.map(s => s.name + (s.fields.length ? '' : ' (no account)')).join(', ')}. ` + (on.length ? `Connected: ${on.map(s => s.name).join(', ')}.` : 'Connect one in Settings › Art and info.');
}
const askText = (title, value, secret) => new Promise(res => { const id = 't' + (++artSeq); ART_REQ[id] = res; ShelfNative.askText(id, title, value || '', !!secret); });
window.__nativeText = (id, v) => { const f = ART_REQ[id]; delete ART_REQ[id]; if (f) f(v); };
async function connectSource(s) {   // fill in an account, field by field, then try it
  const k = Object.assign({}, P.keys[s.id] || {});
  for (const [f, label, secret, optional] of s.fields) { const v = await askText(label + (optional ? ' (optional)' : ''), k[f], secret); if (v == null) return; k[f] = v; }
  P.keys[s.id] = k; nativeSave(); toast(`Checking ${s.name}…`); fillSettings();
  const r = await artCall('artTest', s.id, JSON.stringify(P.keys));
  toast(r.error ? `${s.name}: ${r.error}` : `${s.name}: ${r.message}`);
}
function sourceOptions(s) {
  if (!s.fields.length) return toast('libretro needs no account');
  if (!srcConnected(s)) return connectSource(s);
  openDlg(s.name, [{ label: 'Check the connection' }, { label: 'Change the account details' }, { label: 'Disconnect' }], async i => {
    closeDlg();
    if (i === 0) { toast(`Checking ${s.name}…`); const r = await artCall('artTest', s.id, JSON.stringify(P.keys)); toast(r.error ? `${s.name}: ${r.error}` : `${s.name}: ${r.message}`); }
    else if (i === 1) connectSource(s);
    else { delete P.keys[s.id]; nativeSave(); fillSettings(); toast(`${s.name} disconnected`); }
  });
}
// One kind of art for one game: every connected service at once, from a search you can change
function openKindPicker(g, k, query = romQuery(g)) {
  const head = { label: `Search: “${query}”`, sub: 'Change' }, title = `${kindName(g, k)} for ${g.title}`;
  openDlg(title, [head, { label: 'Searching…', sub: '' }], i => { if (i === 0) editQuery(); });
  const editQuery = () => { closeDlg(); openKb('Search for', query, v => openKindPicker(g, k, v || romQuery(g))); };
  artCall('artFind', k, g.sys, query, g.path || '', JSON.stringify(P.keys)).then(res => {
    if (!S.dlg.open || S.dlg.title !== title || S.dlg.items[0].label !== head.label) return;
    const list = res.results || [], probs = (res.problems || []).map(p => ({ label: p.replace(/^(\w+):/, (m, id) => (ART_SOURCES.find(s => s.id === id) || { name: id }).name + ':'), sub: '' }));
    S.dlg.items = [head, ...(res.error ? [{ label: res.error, sub: '' }] : list.length ? list.map(r => ({ label: r.game, img: r.thumb, sub: r.source })) : [{ label: 'Nothing found. Try fewer or different words, or connect more services.', sub: '' }]), ...probs];
    S.dlg.onPick = i => {
      if (i === 0) return editQuery();
      const r = list[i - 1]; if (!r) return;
      closeDlg(); toast(`Downloading ${kindName(g, k).toLowerCase()}…`);
      artCall('artTake', g.id, k, r.url).then(t => {
        if (t.error) return toast(t.error);
        const m = P.meta[g.id] || (P.meta[g.id] = { art: {}, src: {} }); m.art = Object.assign({}, m.art, { [k]: 1 }); m.src = Object.assign({}, m.src, { [k]: r.source }); m.t = t.t;
        nativeSave(); toast(`${kindName(g, k)} updated, from ${r.source}`); refreshAfterArt();
      });
    };
    S.dlg.i = list.length ? 1 : 0; renderDlg();
  });
}
function removeKind(g, k) { const m = P.meta[g.id]; if (!m || !m.art) return; delete m.art[k]; if (m.src) delete m.src[k]; m.t = Date.now(); nativeSave(); refreshAfterArt(); }

if (NATIVE) {   // start-up in the app; runs last, so everything it uses is defined
  // start from nothing but your own games: no invented history, collections or sign-ins
  GAMES.length = 0; P.recent = []; P.played = {}; P.collections = []; P.ra = null; P.meta = {}; P.cores = []; P.keys = {}; ANDROID_GAMES.clear();
  P.art = { source: 'Free sources', ss: null, videos: false, manuals: false, backs: false, region: 'USA' };
  P.prefs.homeArt = 'cart';   // Shelf's own box art is mostly title text; the label art makes a calmer background
  try { nativeLoad(); } catch (e) { console.error('Shelf couldn’t read its saved state', e); }
  try { nativeScan(); } catch (e) { console.error('Shelf couldn’t list games', e); }
  $('#keysHelp').style.display = 'none'; document.body.style.background = '#000';
  stage.style.borderRadius = '0';   // edge to edge on the handheld
  for (const el of [document.documentElement, document.body]) Object.assign(el.style, { touchAction: 'none', overscrollBehavior: 'none' });   // no panning or zooming the page
  fit();
  setInterval(nativeSave, 15000);
  window.__nativeKey = a => { try { if (P.prefs.layout === 'Xbox' && (a === 'a' || a === 'b')) a = a === 'a' ? 'b' : 'a'; act(a); } catch (e) { console.error(e); } };
  window.__nativePause = () => nativeSave();
  window.__nativeFolder = (k, path) => {
    if (!path) return toast('That folder can’t be used. Choose one on internal storage or an SD card.');
    P.pf[k].folder = path; nativeSave(); nativeScan();
    const n = GAMES.filter(g => g.sys === k).length; toast(`${PLAT[k].name}: found ${n} game${n === 1 ? '' : 's'}`);
    if (S.screen === 'settings') fillSettings(); else if (S.screen === 'setup') fillSetup(); else if (S.screen === 'library') refreshLibrary(true); else if (S.screen === 'home') refreshHome();
  };
  window.__nativeResume = () => {   // back from an emulator or an app, or from Android's settings
    if (S.screen === 'play' && S.play) return homeFromPlay();
    if (nativeBackFromGame()) return;
    if (S.seq || !['setup', 'home', 'library', 'apps'].includes(S.screen)) return;
    nativeScan();
    if (S.screen === 'setup') fillSetup(); else if (S.screen === 'library') refreshLibrary(true); else if (S.screen === 'apps') refreshApps(); else refreshHome();
  };
}
