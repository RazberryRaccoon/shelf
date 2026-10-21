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
  let d; try { d = JSON.parse(ShelfNative.scan()); } catch (e) { console.error(e); return; }
  const marked = GAMES.filter(g => g.sys === 'android');
  GAMES.length = 0;
  for (const r of d.games) GAMES.push({ id: hash(r.path).toString(36), sys: r.sys, title: r.title, year: '', dev: '', genre: '', players: '', ach: 0, desc: '', path: r.path });
  GAMES.sort((a, b) => a.title.localeCompare(b.title)); GAMES.push(...marked);
  for (const k in byId) delete byId[k];
  for (const g of [...DEMO_GAMES, ...GAMES]) byId[g.id] = g;
  APPS.length = 0; d.apps.forEach(a => APPS.push({ name: a.name, pkg: a.pkg, iconData: a.icon, icon: 'ring', hue: hash(a.pkg) % 360 }));
  NATIVE_EMUS = d.emus; INSTALLED_APPS.clear(); d.emus.forEach(e => INSTALLED_APPS.add(e));
  INSTALLED_CORES.clear(); if (d.emus.includes('RetroArch')) Object.keys(CORE_FILES).forEach(c => INSTALLED_CORES.add(c));   // RetroArch's cores can't be listed from outside it
  for (const k of SYS_ORDER) if (k !== 'android' && !emuInstalled(P.emu[k])) P.emu[k] = PLAT[k].emus.find(emuInstalled) || P.emu[k];
}
function nativeSave() {
  try {
    ShelfNative.saveState(JSON.stringify({ v: 1, name: P.name, colors: P.colors, setup: P.setup, recent: P.recent, played: P.played, collections: P.collections, prefs: P.prefs,
      emu: P.emu, gameEmu: P.gameEmu, pf: P.pf, hidden: [...(P.hidden || [])], androidGames: GAMES.filter(g => g.sys === 'android').map(g => ({ name: g.title, pkg: g.pkg })) }));
  } catch (e) { console.error(e); }
}
function nativeLoad() {
  let s = null; try { s = JSON.parse(ShelfNative.loadState() || 'null'); } catch (e) { console.error(e); }
  if (!s) return;
  for (const k of ['name', 'colors', 'setup', 'recent', 'played', 'collections', 'gameEmu']) if (s[k] != null) P[k] = s[k];
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
function nativeOpenEmu(g, e) { if (!ShelfNative.openEmulator(emuApp(e))) toast(`${emuApp(e)} isn’t installed`); }
if (NATIVE) {
  // start from nothing but your own games: no invented history, collections or sign-ins
  GAMES.length = 0; P.recent = []; P.played = {}; P.collections = []; P.ra = null; ANDROID_GAMES.clear();
  P.art = { source: 'Free sources', ss: null, videos: false, manuals: false, backs: false, region: 'USA' };
  P.prefs.homeArt = 'cart';   // Shelf's own box art is mostly title text; the label art makes a calmer background
  nativeLoad(); nativeScan();
  $('#keysHelp').style.display = 'none'; document.body.style.background = '#000';
  stage.style.borderRadius = '0';   // edge to edge on the handheld
  for (const el of [document.documentElement, document.body]) Object.assign(el.style, { touchAction: 'none', overscrollBehavior: 'none' });   // no panning or zooming the page
  fit();
  setInterval(nativeSave, 15000);
  window.__nativeKey = a => { try { if (P.prefs.layout === 'Xbox' && (a === 'a' || a === 'b')) a = a === 'a' ? 'b' : 'a'; act(a); } catch (e) { console.error(e); } };
  window.__nativePause = () => nativeSave();
  window.__nativeResume = () => {   // back from an emulator or an app, or from Android's settings
    if (S.screen === 'play' && S.play) return homeFromPlay();
    if (S.seq || !['setup', 'home', 'library', 'apps'].includes(S.screen)) return;
    nativeScan();
    if (S.screen === 'setup') fillSetup(); else if (S.screen === 'library') refreshLibrary(true); else if (S.screen === 'apps') refreshApps(); else refreshHome();
  };
}
