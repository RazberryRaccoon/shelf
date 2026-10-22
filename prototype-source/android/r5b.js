/* ================= Apps ================= */
const APP_COLS = 5;
const iconUrls = new Map();
const iconUrl = app => { if (app.iconData) return app.iconData; if (!iconUrls.has(app.name)) iconUrls.set(app.name, appIcon(app).toDataURL()); return iconUrls.get(app.name); };
function refreshApps() {
  S.apps.i = clamp(S.apps.i, 0, APPS.length - 1); setMood('home');
  $('#appCount').textContent = `${APPS.length} apps on this handheld`;
  $('#appGrid').innerHTML = APPS.map((a, i) => `<div class="app ${!S.top.on && i === S.apps.i ? 'focus' : ''}"><img src="${iconUrl(a)}" alt=""><span>${esc(a.name)}</span>${ANDROID_GAMES.has(a.name) ? '<small>In Library</small>' : ''}</div>`).join('');
  const first = $('#appGrid .app'), rowH = first ? first.offsetHeight + 30 : 190, r = Math.floor(S.apps.i / APP_COLS), view = 750 - 196;
  const rows = Math.ceil(APPS.length / APP_COLS), off = clamp(r * rowH - (view - rowH) / 2, 0, Math.max(0, rows * rowH - view + 24));
  $('#appGrid').style.transform = `translateY(${-off}px)`;
  renderTop();
}
function appOptions() {
  const app = APPS[S.apps.i], inLib = ANDROID_GAMES.has(app.name);
  openDlg(app.name, [{ label: inLib ? 'Move back to Apps' : 'Mark as game', sub: inLib ? '' : 'Gives it a box in your Library' }, { label: 'App info' }, { label: 'Cancel' }], i => {
    closeDlg();
    if (i === 0) inLib ? unmarkGame(app.name) : markAsGame(app);
    else if (i === 1) toast('Opens Android’s app info');
    refreshApps();
  });
}
function markAsGame(app) {
  let g = GAMES.find(x => x.sys === 'android' && x.title === app.name);
  if (!g) { g = G('android', app.name, 2024, 'From the app store', 'Game', '1 player', 0, `${app.name} on your handheld. Shelf found its art in a game database, or made a box from its icon.`); GAMES.push(g); byId[g.id] = g; }
  g.pkg = app.pkg; ANDROID_GAMES.add(app.name); if (NATIVE) nativeSave(); sfx.saved(); toast(`${app.name} is now in your Library, under Android`);
}
function unmarkGame(name) { ANDROID_GAMES.delete(name); if (NATIVE) nativeSave(); sfx.back(); toast(`${name} is back in Apps`); }

/* ================= settings ================= */
const CATS = ['Profile', 'Appearance', 'Sound', 'Platforms', 'Emulators', 'Art and info', 'Achievements', 'Controls', 'Home screen and apps', 'About'];
const ALL_EMUS = ['RetroArch', ...new Set(Object.values(EMU_LIST).flatMap(e => e.apps))];
P.gf = {}; P.hidden = new Set();
// choosing an emulator: every one Shelf knows for this console, installed or not, plus any other app
function pickEmulator(sys, current, onPick, o = {}) {
  const items = [];
  if (o.withDefault) items.push({ label: `Default (${emuName(P.emu[sys])})`, v: null });
  PLAT[sys].emus.forEach(e => items.push({ label: emuName(e), v: e, sub: e.startsWith('RetroArch') ? (emuInstalled(e) ? (NATIVE ? 'You have this core' : 'RetroArch core') : NATIVE ? 'RetroArch core' : 'Core downloads on first use') : emuInstalled(e) ? 'Installed' : 'Not installed' }));
  if (!o.once) items.push({ label: 'Other app…', v: 'other', sub: 'Any installed app' });
  const at = Math.max(0, items.findIndex(it => it.v === current));
  openDlg(o.title || `${PLAT[sys].name}: play with`, items, i => {
    const it = items[i]; closeDlg();
    if (it.v === 'other') { toast('Opens Android’s app picker. Shown here: Lemuroid.'); return onPick('Other app: Lemuroid'); }
    if (it.v && !it.v.startsWith('RetroArch') && !emuInstalled(it.v)) return toast(`Opens ${it.v} in the app store`);
    if (NATIVE && it.v && it.v.startsWith('RetroArch: ')) knowCore(it.v.slice(11));   // choosing a core means you have it
    onPick(it.v);
  }, at);
}
function launchGame(g, slot, resume) {   // asks which emulator first, if that console is set to ask every time
  if (NATIVE && g.sys !== 'android') {   // a RetroArch core Shelf doesn't know you have: ask which one you use, first
    const e = emuFor(g);
    if (e.startsWith('RetroArch: ') && !P.cores.includes(e.slice(11))) return askCore(g.sys, ne => { if (P.gameEmu[g.id]) P.gameEmu[g.id] = ne; launchGame(g, slot, resume); });
  }
  if (g.sys !== 'android' && P.pf[g.sys].ask && !P.gameEmu[g.id]) return pickEmulator(g.sys, P.emu[g.sys], e => { S.onceEmu = { id: g.id, e }; startLaunch(g, slot, resume && isRA(e)); }, { once: true, title: `Play ${g.title} with` });
  startLaunch(g, slot, resume);
}
function settingsModel() {
  const Q = P.prefs, tog = (label, key, desc, obj = Q) => ({ label, desc, value: obj[key] ? 'On' : 'Off', act: () => { obj[key] = !obj[key]; } });
  const cyc = (label, obj, key, vals, desc, names) => ({ label, desc, value: names ? names[obj[key]] : obj[key], act: () => { obj[key] = vals[(vals.indexOf(obj[key]) + 1) % vals.length]; } });
  const info = (label, value, desc, bar) => ({ label, desc, value, info: true, bar });
  const action = (label, value, desc, act) => ({ label, desc, value, act, action: true });
  const nav = (label, value, desc, go) => ({ label, desc, value, act: go, nav: true });
  if (S.set.game) {   // one game's own settings
    const g = byId[S.set.game], own = P.gameEmu[g.id], opts = [null, ...PLAT[g.sys].emus], emu = emuFor(g), gf = P.gf[g.id] || (P.gf[g.id] = { filter: null });
    return [{ cat: g.title, intro: `${PLAT[g.sys].long}. These override the platform’s settings for this game only.`, items: g.sys === 'android' ? [
      action('Move back to Apps', '', 'Takes it out of the Library. It stays installed.', () => { unmarkGame(g.title); S.set.game = null; enterTab('apps'); return true; }),
      ...(NATIVE ? [] : [action('Box art', 'Choose', 'Pick other art from the game databases, or make a box from the icon.', () => toast('Shows other art to choose from'))]),
      tog('Show on Home', 'show', 'Include it in your recently played cartridges.', { get show() { return !P.hidden.has(g.id); }, set show(v) { v ? P.hidden.delete(g.id) : P.hidden.add(g.id); } })] : [
      { label: 'Emulator', desc: own ? 'Chosen for this game.' : `Using the ${PLAT[g.sys].name} default. Choose to override it.`, value: own ? emuName(own) : `Default (${emuName(P.emu[g.sys])})`, act: () => { pickEmulator(g.sys, own || null, v => { if (v) P.gameEmu[g.id] = v; else delete P.gameEmu[g.id]; fillSettings(); }, { withDefault: true, title: `${g.title}: play with` }); return true; } },
      info('Saves', isRA(emu) ? 'Shelf’s slots' : `Kept by ${emu}`, isRA(emu) ? 'Four save slots with screenshots, per game.' : 'Standalone emulators keep their own saves.'),
      { label: 'Screen filter', desc: 'Overrides the platform’s filter.', value: gf.filter || `Default (${P.pf[g.sys].filter})`, act: () => { const v = [null, 'None', 'Scanlines', 'LCD grid'], i = v.indexOf(gf.filter); gf.filter = v[(i + 1) % v.length]; } },
      ...(NATIVE ? [] : [action('Box art', 'Choose', 'Pick other box art from ScreenScraper.', () => toast('Shows other box art to choose from'))]),
      ...(NATIVE ? [] : [info('Manual', hasManual(g) ? `${manualPages(g)} pages` : 'Not found', hasManual(g) ? 'Downloaded from ScreenScraper.' : 'ScreenScraper doesn’t have one for this game.')]),
      ...(NATIVE ? [
        action('Details', P.meta[g.id] && P.meta[g.id].name ? P.meta[g.id].name : 'Not found yet', 'Year, developer, genre and description, plus matching box art, title screen and screenshot. From libretro (no account).', () => { openArtPicker(g); return true; }),
        ...ART_KINDS.map(({ k }) => { const m = P.meta[g.id] || {}, has = m.art && m.art[k];
          return action(kindName(g, k), has ? (m.src && m.src[k]) || 'Downloaded' : 'Shelf’s own', kindTip(k), () => { openKindPicker(g, k); return true; }); }),
        ...(P.meta[g.id] ? [action('Remove all art and details', '', 'Go back to the box Shelf makes for it.', () => { ShelfNative.artRemove(g.id); delete P.meta[g.id]; nativeScan(); nativeSave(); toast('Art removed'); })] : [])] : []),
      tog('Show on Home', 'show', 'Include it in your recently played cartridges.', { get show() { return !P.hidden.has(g.id); }, set show(v) { v ? P.hidden.delete(g.id) : P.hidden.add(g.id); } })] }];
  }
  if (S.set.sub) {      // one platform's page
    const k = S.set.sub, pl = PLAT[k], emu = P.emu[k];
    return CATS.map(c => c === 'Platforms' ? { cat: pl.long, intro: `${GAMES.filter(g => g.sys === k && inLibrary(g)).length} games. Press B to go back to all platforms.`, items: k === 'android' ? [
      info('Launching', 'Opens the app', 'Android games open in their own app.'), info('Box', 'Keep case', 'A generic keep case at Blu-ray size.')] : [
      { label: 'Default emulator', desc: `Used by every game on this platform unless a game chooses its own. ${pl.emus.length} to choose from.` + (NATIVE ? ' A RetroArch core has to be downloaded in RetroArch first (Main Menu › Load Core › Download a Core); if a game opens to a black screen, pick a core you have.' : ''), value: emuName(emu), act: () => { pickEmulator(k, emu, v => { P.emu[k] = v; fillSettings(); }); return true; } },
      tog('Ask every time', 'ask', 'Choose the emulator each time you play, unless a game has its own.', P.pf[k]),
      info('Saves', isRA(emu) ? 'Shelf’s slots' : `Kept by ${emuApp(emu)}`, isRA(emu) ? 'RetroArch saves into Shelf’s four slots per game.' : NATIVE ? 'The emulator keeps its own saves. Shelf’s save slots come in a later version.' : 'Standalone emulators keep their own saves.'),
      cyc('Screen filter', P.pf[k], 'filter', ['None', 'Scanlines', 'LCD grid'], 'A filter over the game picture, when the emulator supports one.'),
      ...(NATIVE ? [action('Games folder', P.pf[k].folder ? prettyPath(P.pf[k].folder) : `ROMs › ${romDir(k)}`,
          P.pf[k].folder ? 'Shelf looks here, and in any ROMs folder. Press A to choose a different one.' : 'Shelf looks in ROMs folders on internal storage and SD cards. Press A to choose a different folder.',
          () => { nativePickFolder(k); return true; }),
        ...(P.pf[k].folder ? [action('Stop using this folder', '', 'Go back to the ROMs folders only.', () => { P.pf[k].folder = ''; nativeSave(); nativeScan(); })] : [])]
        : [info('Game folder', `ROMs/${k}`, 'Where Shelf looks for this platform’s games.')]),
      info('BIOS', k === 'ps1' ? 'Found' : 'Not needed', k === 'ps1' ? 'DuckStation found a PlayStation BIOS.' : 'This platform runs without one.'),
      info('Box', pl.real ? 'Original case' : 'Modern case', pl.real ? 'The original case, opening as it really does.' : 'A modern clamshell case at the original box’s size.')] } : null).map((c, i) => c || modelFor(i));
  }
  return CATS.map((c, i) => modelFor(i));
  function modelFor(i) {
    switch (CATS[i]) {
      case 'Profile': return { cat: 'Profile', intro: 'This handheld has one profile.', items: [
        action('Name', P.name, 'Shown at the top of the screen.', () => openKb('Your name', P.name, v => { P.name = v; paintProfile(); fillSettings(); })),
        action('Color', 'Change', 'Your profile’s color.', () => { const i = PROFILE_COLORS.indexOf(P.colors); P.colors = PROFILE_COLORS[(i + 1) % PROFILE_COLORS.length]; paintProfile(); }),
        action('Run setup again', '', 'Go through the first-run steps again.', () => { S.setup = { step: 0, i: 0 }; enterSetup(); return true; })] };
      case 'Appearance': return { cat: 'Appearance', intro: 'Themes change the background, panels, lighting and accent.', items: [
        { label: 'themes', themes: true },
        tog('Accent follows the console', 'accentFollows', 'The accent color and one background field take on each console’s color.'),
        cyc('Case color', Q, 'caseTint', [false, true], 'The plastic of the modern cases.', { false: 'Neutral', true: 'Console color' }),
        cyc('Home background', Q, 'homeArt', ['box', 'title', 'shot', 'cart', 'off'], 'A picture of the selected game under the row on Home, darkened and fading out toward the top.', { box: 'Box art', title: 'Title screen', shot: 'Screenshot', cart: 'Cartridge art', off: 'Off' }),
        tog('Background motion', 'motion', 'Drifting backgrounds, twinkling stars and the slow float of boxes and cartridges.')] };
      case 'Sound': { const M = P.prefs.music, names = Object.fromEntries([...TRACK_KEYS.map(k => [k, TRACKS[k].name]), ['shuffle', 'Shuffle']]); return { cat: 'Sound', intro: 'Music plays quietly in the menus and stops while you play.', items: [
        tog('Menu music', 'on', 'Original tracks written for Shelf.', M),
        cyc('Track', M, 'track', [...TRACK_KEYS, 'shuffle'], M.track === 'shuffle' ? 'A different track every so often.' : TRACKS[M.track].mood, names),
        cyc('Music volume', M, 'vol', ['Low', 'Medium', 'High'], 'Interface sounds stay at their own level.'),
        tog('Interface sounds', 'sound', 'Soft clicks when you move and choose.')] }; }
      case 'Platforms': return { cat: 'Platforms', intro: 'Choose a platform for its default emulator, filters and folders.', items: SYS_ORDER.map(k =>
        nav(PLAT[k].long, k === 'android' ? 'Apps' : P.pf[k].ask ? 'Ask every time' : emuName(P.emu[k]), `${GAMES.filter(g => g.sys === k && inLibrary(g)).length} games`, () => { S.set.sub = k; S.set.i = 0; })) };
      case 'Emulators': return { cat: 'Emulators', intro: 'Emulators found on this handheld. Shelf launches them for you.', items: [
        ...(NATIVE && INSTALLED_APPS.has('RetroArch') ? [action('RetroArch cores', `${P.cores.length} marked`, 'Tick the cores you’ve downloaded in RetroArch, so Shelf only uses ones you have. Otherwise it asks the first time you play each console.', () => { editCores(); return true; })] : []),
        ...ALL_EMUS.map(e => INSTALLED_APPS.has(e) ? info(e, e === 'RetroArch' ? `Installed, ${INSTALLED_CORES.size} core${INSTALLED_CORES.size === 1 ? '' : 's'}` : 'Installed', e === 'RetroArch' ? 'Shelf manages save slots and achievements through RetroArch. Other cores download when first used.' : `A standalone emulator for ${SYS_ORDER.filter(k => (PLAT[k].emus || []).includes(e)).map(k => PLAT[k].name).join(', ')}. It keeps its own saves.`)
          : action(e, 'Get it', `Not installed. Plays ${SYS_ORDER.filter(k => (PLAT[k].emus || []).includes(e)).map(k => PLAT[k].name).join(', ')}.`, () => toast(`Opens ${e} in the app store`))),
        action('Look for emulators again', '', 'After installing or removing one.', () => toast(`Found ${INSTALLED_APPS.size} emulators`))] };
      case 'Art and info': if (NATIVE) { const have = GAMES.filter(g => P.meta[g.id]).length, all = GAMES.filter(g => g.sys !== 'android').length; return { cat: 'Art and info',
        intro: 'Find art for every game uses libretro (free), and ScreenScraper too when it’s connected. To choose each kind of art for one game from every connected service, open its box, then Game settings.', items: [
        action(ART_RUN.on ? 'Stop finding art' : 'Find art for every game', ART_RUN.on ? `${ART_RUN.i} of ${ART_RUN.n}` : `${have} of ${all} have art`, ART_RUN.on ? 'Finding art in the background. You can keep using Shelf.' : 'Looks up each game by its file. Games it can’t match for certain are left for you to choose.', () => { artAll(false); }),
        ...(ART_RUN.on ? [] : [action('Find art again for every game', '', 'Replaces the art and details already found.', () => { artAll(true); })]),
        ...ART_SOURCES.map(s => action(s.name, !s.fields.length ? 'No account needed' : srcConnected(s) ? 'Connected' : 'Connect',
          `Gives ${s.kinds.map(k => ART_KINDS.find(a => a.k === k).name.toLowerCase()).join(', ')}. ${s.needs}.`, () => { sourceOptions(s); return true; }))] }; }
        { const A = P.art, ss = A.source === 'ScreenScraper'; return { cat: 'Art and info', intro: 'Box art, manuals, media and details are downloaded for you. Cartridge labels too, where the source has them.', items: [
        { label: 'Source', value: A.source, desc: ss ? 'The most complete: boxes, manuals, videos and details.' : 'RetroArch’s databases and Wikipedia. No account needed.', act: () => { A.source = ss ? 'Free sources' : 'ScreenScraper'; } },
        ...(ss ? [action('ScreenScraper account', A.ss || 'Sign in', A.ss ? 'Signed in.' : 'A free account from screenscraper.fr.', () => { A.ss = A.ss ? null : 'player_one'; })] : []),
        tog('Gameplay videos', 'videos', 'Short clips for each game’s media.', A), tog('Manuals', 'manuals', 'Scans of the original manuals.', A),
        cyc('Region', A, 'region', ['USA', 'Europe', 'Japan', 'World'], 'Which region’s box art to prefer.'),
        action('Update everything now', 'Update', `Look up art and details for all ${GAMES.length} games again.`, () => toast('Updating in the background'))] }; }
      case 'Achievements': return { cat: 'Achievements', intro: P.ra ? `Signed in as ${P.ra}.` : 'Not signed in.', items: [
        action('RetroAchievements account', P.ra || 'Sign in', 'Achievements work with RetroArch.', () => { P.ra = P.ra ? null : 'player_one'; }),
        tog('Hardcore mode', 'hardcore', 'Double points, but saves and rewind are off while you play.'),
        tog('Unlock pop-ups', 'achPop', 'A short message when you earn one.')] };
      case 'Controls': return { cat: 'Controls', intro: 'How Shelf reads your buttons.', items: [
        cyc('Button layout', Q, 'layout', ['Nintendo', 'Xbox'], 'Which of A and B confirms.'),
        info('Home button', 'Saves, then returns', 'In RetroArch games, Shelf saves before coming back.'),
        tog('Vibration', 'vibe', 'A light tap when you open a box or start a game.', Object.assign(Q, { vibe: Q.vibe ?? true }))] };
      case 'Home screen and apps': return { cat: 'Home screen and apps', intro: 'Shelf as this handheld’s launcher.', items: [
        tog('Use Shelf as the home screen', 'homeLauncher', 'The Home button always brings you here.'),
        cyc('Apps tab shows', Q, 'showApps', ['All apps', 'Hide emulators'], 'Emulators can stay out of the way, since Shelf launches them.'),
        info('Android games', `${ANDROID_GAMES.size} marked`, 'In Apps, press Y on a game and choose Mark as game.')] };
      default: if (NATIVE) { const d = nativeInfo(); return { cat: 'About', intro: `Shelf for Android, version ${d.version}.`, items: [
        info('Device', d.model, `Android ${d.android}.`), info('Games found', `${GAMES.filter(g => g.sys !== 'android').length}`, 'In the ROMs folders on internal storage and SD cards.'),
        action('Web engine', ENGINE ? `Chrome ${ENGINE}` : 'Unknown', ENGINE && ENGINE < WEBVIEW_OK ? 'Android System WebView, which draws Shelf, is out of date. Update it for smoother menus.' : 'Android System WebView, which draws Shelf. Opens it in the Play Store.', () => ShelfNative.openStore('com.google.android.webview'))] }; }
        return { cat: 'About', intro: 'Shelf for Android, prototype.', items: [info('Device', 'Retroid Pocket 3+', 'Android 11, Unisoc T618.'), info('Storage', '61.2 of 116 GB', 'Games, art, manuals and media.', 53), info('Version', '0.1 prototype', '')] };
    }
  }
}
function openSettings(cat = 0) { S.set = { cat, zone: cat ? 'items' : 'nav', i: 0, back: S.screen, sub: null, game: null, ti: Object.keys(THEMES).indexOf(P.prefs.theme) }; S.screen = 'settings'; show('settings'); sfx.select(); fillSettings(); }
function openGameSettings(id) { S.set = { cat: 0, zone: 'items', i: 0, back: 'game', sub: null, game: id }; S.screen = 'settings'; show('settings'); sfx.select(); fillSettings(); }
function fillSettings() {
  const M = settingsModel(), cat = S.set.game ? 0 : S.set.cat, C = M[cat];
  $('#setNav').innerHTML = S.set.game ? `<div class="cur ${S.set.zone === 'nav' ? 'focus' : ''}">This game</div><div style="font-size:12px;font-weight:600;color:var(--fg2)">B goes back to the box.</div>`
    : M.map((c, i) => `<div class="${i === cat ? 'cur' : ''} ${S.set.zone === 'nav' && i === cat ? 'focus' : ''}">${CATS[i]}</div>`).join('');
  const keys = Object.keys(THEMES);
  $('#setBody').innerHTML = `<h2>${esc(C.cat)}</h2><p class="intro">${C.intro}</p><div class="sview"><div class="srows">` + C.items.map((it, i) => {
    const f = S.set.zone === 'items' && i === S.set.i;
    if (it.themes) return `<div class="themes">${keys.map((k, j) => { const T = THEMES[k], bg = T.space ? 'radial-gradient(circle at 70% 120%, #8b78e6 0, #2a1a5e 40%, #070b1e 75%)' : `linear-gradient(160deg, ${T.top}, ${T.c[0]} 55%, ${T.c[1]})`;
      const fg = ['light', 'dawn', 'bloom'].includes(k) ? '#14161c' : '#eef1ff';
      return `<div class="th ${P.prefs.theme === k ? 'cur' : ''} ${f && S.set.ti === j ? 'focus' : ''}" style="background:${bg};color:${fg}">${T.name}</div>`; }).join('')}</div>`;
    return `<div class="srow ${f ? 'focus' : ''}"><div><b>${esc(it.label)}</b><p>${it.desc || ''}</p>${it.bar ? `<i class="bar"><b style="width:${it.bar}%"></b></i>` : ''}</div>${it.value !== '' && it.value != null ? `<em class="${it.action ? 'act' : ''} ${it.nav ? 'nav' : ''}">${esc(String(it.value))}</em>` : ''}</div>`;
  }).join('') + '</div></div>';
  const rows = $('#setBody .srows'), fr = rows.querySelector('.srow.focus, .th.focus');
  if (fr) { const view = $('#setBody .sview').clientHeight, el = fr.classList.contains('th') ? fr.parentElement : fr, off = clamp(el.offsetTop + el.offsetHeight / 2 - view / 2, 0, Math.max(0, rows.scrollHeight - view)); rows.style.transform = `translateY(${-off}px)`; }
}
function settingsAct() {
  const it = settingsModel()[S.set.game ? 0 : S.set.cat].items[S.set.i];
  if (it.themes) { applyTheme(Object.keys(THEMES)[S.set.ti]); sfx.select(); return fillSettings(); }
  if (it.info) return;
  const left = it.act(); sfx.select(); if (!left) fillSettings();
}
function closeSettings() {
  sfx.back();
  if (S.set.game) { const id = S.set.game; S.set.game = null; S.screen = 'game'; show('game'); return fillGame(); }
  const b = S.set.back; if (b === 'game') { S.screen = 'game'; show('game'); fillGame(); } else enterTab(TABS.includes(b) ? b : 'home');
}

/* ================= launching, playing, coming back ================= */
// Launching: the game's console rises into view, the media flies in and clicks into its slot, the power light comes
// on with a jingle, and the screen fades into the game. Coming back plays the same thing in reverse.
// On Android, Shelf plays the launch before handing off to the emulator, and the return when it comes back to the front.
const KIND = {   // how media goes into each kind of slot: direction (console space), turn, and how much of it is left sticking out
  top:   { dir: [0, -1, 0], rx: 0, turn: 0, outside: .55 },
  hand:  { dir: [0, -1, 0], rx: 0, turn: Math.PI, outside: .02 },
  front: { dir: [0, 0, -1], rx: -Math.PI / 2, turn: Math.PI, outside: .04 },
  edge:  { dir: [0, 0, 1], rx: -Math.PI / 2, turn: 0, outside: .03 },
  back:  { dir: [0, 0, 1], rx: 0, turn: Math.PI, outside: 0, thin: true },
  lid:   { dir: [0, -1, 0], rx: -Math.PI / 2, turn: 0, outside: 1, thin: true },
};
// Each kind's launch, in seconds: when the media flies to the slot and goes in, when a hinged part opens and
// closes, when the console turns its back to you, when the power comes on, and when the screen starts to fade.
// Coming back plays the same timeline backwards, a little faster.
const TIMELINE = {
  top:   { fly: [.15, .85], ins: [1.0, .35], click: 1.35, power: 1.7, end: 2.35 },
  hand:  { fly: [.15, .85], ins: [1.0, .4], click: 1.4, power: 1.75, end: 2.4 },
  front: { fly: [.15, .85], ins: [1.0, .35], press: [1.38, .1], click: 1.38, power: 1.75, end: 2.4 },
  lid:   { fly: [.15, .85], ins: [1.0, .3], lid: [[.2, .4], [1.3, .22]], click: 1.5, power: 1.75, end: 2.4 },
  edge:  { turn: [[.1, .6], [1.45, .55]], fly: [.2, .8], ins: [1.0, .4], click: 1.4, lid: [[2.0, .5]], power: 2.5, end: 3.1 },
  back:  { turn: [[.1, .6], [1.75, .6]], lid: [[.55, .35], [1.4, .25]], fly: [.3, .75], ins: [1.05, .3], click: 1.62, power: 2.45, end: 3.05 },
};
function consolePose(sys) {
  const m = consoleModel(sys), dx = m.hi[0] - m.lo[0], dy = m.hi[1] - m.lo[1], hand = !!PLAT[sys].hand;
  const s = Math.min(3.3 / dx, (hand ? 2.6 : 1.9) / dy);
  return { x: 0, y: hand ? -.3 : -.55, z: 0, ry: hand ? -.28 : -.38, s };
}
function mediaPath(sys, cp) {   // the media's pose just outside the slot, and fully in
  const m = consoleModel(sys), sl = m.slot, K = KIND[sl.kind], c = CART[sys], CM = M4.trs(cp.x, cp.y, cp.z, 0, cp.ry, 0, cp.s);
  const at = apply(CM, [sl.x, sl.y, sl.z]), cy = Math.cos(cp.ry), sy = Math.sin(cp.ry), outside = sl.outside != null ? sl.outside : K.outside;
  const d = [K.dir[0] * cy + K.dir[2] * sy, K.dir[1], -K.dir[0] * sy + K.dir[2] * cy];
  const len = (K.thin ? c.d : c.h) * cp.s, inn = at.map((v, i) => v + d[i] * (len / 2 - len * outside)), out = at.map((v, i) => v - d[i] * (len / 2 + .9 + (K.thin ? .3 : 0)));
  return { out, inn, rx: K.rx, ry: cp.ry + K.turn, s: cp.s, kind: sl.kind };
}
function mediaPoseNow(from, g) {   // where the media is on screen right now, so it can fly from there
  if (from === 'home') { const o = anim.get('home:' + g.id); if (o) return { x: o.x, y: o.y, z: o.z, rx: o.rx, ry: o.ry, s: o.s }; }
  if (from === 'game') { const o = anim.get('case'), m = caseModel(g.sys); if (o) {
    const p = apply(M4.trs(o.x, o.y, o.z, o.rx, o.ry, 0, o.s), [0, m.by, m.mediaZ]); return { x: p[0], y: p[1], z: p[2], rx: o.rx, ry: o.ry, s: o.s }; } }
  return { x: 0, y: -6, z: 0, rx: 0, ry: 0, s: 1 };
}
function homeSlotPose(g) { const [x, y] = pw(270, HOME_Y); return { x, y, z: .5, rx: -.1, ry: -.2, s: mediaScale(g.sys, 240) }; }
function startLaunch(g, slot, resume, from = S.screen) {
  const cp = consolePose(g.sys);
  S.seq = { kind: 'launch', g, slot, resume, t: 0, from, under: from === 'apps' ? null : from, flags: {}, cp, start: mediaPoseNow(from, g) };
  S.screen = 'launch'; show('none'); S.top.on = false; sfx.select();
  $('#nowLoading b').textContent = g.title; setTimeout(() => { if (S.seq && S.seq.kind === 'launch') $('#nowLoading').classList.add('on'); }, 350);
}
const seqTL = q => TIMELINE[consoleModel(q.g.sys).slot.kind];
const seqClock = q => q.kind === 'launch' ? q.t : Math.max(0, seqTL(q).end - q.t * 1.3);   // the return runs the launch backwards
function seqUpdate(dt) {
  const q = S.seq, L = seqTL(q), F = q.flags, t0 = seqClock(q); q.t += dt; const t = seqClock(q);
  const crossed = at => q.kind === 'launch' ? t0 < at && t >= at : t0 > at && t <= at;
  if (crossed(L.click)) sfx.insert();
  if (q.kind === 'launch') {
    if (crossed(L.power)) sfx.power();
    if (t > L.end && !F.fade) { F.fade = 1; const fl = $('#flash'); fl.style.transition = 'opacity .45s ease-in'; fl.style.opacity = 1; }
    if (t > L.end + .5) { S.seq = null; $('#nowLoading').classList.remove('on'); startPlay(q.g, q.slot, q.resume, q.from); }
  } else {
    if (crossed(L.power + .15)) sfx.off();
    if (t <= 0) finishReturn(q);
  }
}
function seqTimes(q) {   // every moving part of the sequence, as 0..1 progress values on the launch clock
  const t = seqClock(q), L = seqTL(q), span = (r, k = ease) => k((t - r[0]) / r[1]);
  const twice = R => R ? (R.length > 1 ? span(R[0]) * (1 - span(R[1])) : span(R[0])) : 0;   // opens, and closes again if there's a second range
  return { rise: easeOut((t - .1) / .8), fly: span(L.fly), ins: span(L.ins), press: L.press ? span(L.press) : 0, lid: twice(L.lid), turn: twice(L.turn),
    power: clamp((t - L.power) / .15, 0, 1), cam: ease((t - .05) / .85), close: ease((t - L.power - .05) / .9), scrim: ease(t / .45) };
}
function drawLaunch(dt) {
  const q = S.seq, g = q.g, T = seqTimes(q), hand = !!PLAT[g.sys].hand, cp = q.cp, path = mediaPath(g.sys, { ...cp, ry: cp.ry + Math.PI * T.turn });
  if (q.under && T.scrim < .999) { if (q.under === 'home') drawHome(dt, S.time); else if (q.under === 'game') drawGamePage(dt, S.time); }
  drawScrim(T.scrim);
  // the camera starts where the screen's own camera is, rises to look down at the console, then moves in close
  const camY = lerp(0, hand ? .8 : 2.3, T.cam), look = lerp(0, -.3, T.cam), camZ = lerp(CAMZ, hand ? 6.2 : 7.2, T.close);
  setCamera(camY, camZ, look); beginMeshes();
  const C = { ...cp, y: lerp(-7, cp.y, T.rise), ry: cp.ry + Math.PI * T.turn }, M = M4.trs(C.x, C.y, C.z, 0, C.ry, 0, C.s);
  drawConsole(g.sys, M, { alpha: 1, bright: 1, s: C.s, detail: .6, power: T.power, screenOn: T.power, lid: T.lid });
  const st = q.kind === 'launch' ? q.start : q.dest, aim = { x: path.out[0], y: path.out[1] + (C.y - cp.y), z: path.out[2] };
  let pos = [lerp(st.x, aim.x, T.fly), lerp(st.y, aim.y, T.fly), lerp(st.z, aim.z, T.fly)];
  if (T.fly >= 1) pos = path.out.map((v, i) => lerp(v, path.inn[i], T.ins) + (i === 1 ? C.y - cp.y : 0));
  if (path.kind === 'front') pos[1] -= .05 * cp.s * T.press;
  let dry = path.ry - st.ry; while (dry > Math.PI) dry -= 2 * Math.PI; while (dry < -Math.PI) dry += 2 * Math.PI;
  const rx = lerp(st.rx, path.rx, T.fly), ry = st.ry + dry * T.fly, s = lerp(st.s, path.s, T.fly);
  q.now = { x: pos[0], y: pos[1], z: pos[2], rx, ry, s };
  drawCartM(g, M4.trs(pos[0], pos[1], pos[2], rx, ry, 0, s), { alpha: 1, bright: 1, s, detail: .6 });
  endMeshes();
}
function startPlay(g, slot, resume, from) {
  const r = ensureRec(g), emu = emuFor(g), before = P.recent.slice();
  // a game moves to the front of Home the moment it's launched, not when you come back from it
  r.last = Date.now(); P.recent = [g.id, ...P.recent.filter(x => x !== g.id)];
  if (NATIVE) {   // hand the game to the emulator; Shelf waits in the dark until you come back (__nativeResume)
    S.play = { g, slot, from, t: 0, native: true, start: Date.now() }; S.screen = 'play'; show('play');
    P.playing = { id: g.id, from, start: S.play.start };   // saved, so coming back works even if Android restarted Shelf meanwhile
    const fl = $('#flash'); fl.style.transition = 'opacity .5s ease-out'; fl.style.opacity = 0;
    const err = nativeLaunch(g, emu);
    if (err) { S.launchErr = err; P.recent = before; P.playing = null; setTimeout(homeFromPlay, 60); }
    return;
  }
  S.play = { g, slot, from, t: 0, seed: hash(g.id + 'play'), scroll: resume && r.slots[slot] ? (r.slots[slot].scroll || 120) : 0 };
  S.screen = 'play'; show('play');
  const gc = $('#gamecv'), [w, h] = PLAT[g.sys].res; gc.width = w; gc.height = h;
  const asp = PLAT[g.sys].aspect, dh = Math.min(H, W / asp), dw = dh * asp;
  Object.assign(gc.style, { width: dw + 'px', height: dh + 'px', left: (W - dw) / 2 + 'px', top: (H - dh) / 2 + 'px' });
  const tag = $('#emuTag'); tag.textContent = g.sys === 'android' ? `${g.title} is open. Press H, the Home button, to come back.` : `Playing in ${emu.replace('RetroArch: ', 'RetroArch, ')}. Press H, the Home button, to come back.`;
  tag.classList.add('show'); setTimeout(() => tag.classList.remove('show'), 3500);
  const fl = $('#flash'); fl.style.transition = 'opacity .5s ease-out'; fl.style.opacity = 0;
}
function playUpdate(dt) { const p = S.play; if (p.native) return; p.t += dt; p.scroll += dt * 40; gameFrame(p.g, p.seed, Math.floor(p.scroll), $('#gamecv')); }
function homeFromPlay() {
  const p = S.play; if (!p) return; S.play = null;
  const g = p.g, r = ensureRec(g), emu = emuFor(g), ra = isRA(emu) && P.prefs.autosave && !P.prefs.hardcore;
  if (ra) { r.slots[p.slot] = { ts: Date.now(), seed: p.seed, scroll: Math.floor(p.scroll) }; r.lastSlot = p.slot; }
  if (!S.launchErr) r.minutes += Math.max(1, Math.round((p.native ? (Date.now() - p.start) / 1000 : p.t) / 60));
  P.playing = null; if (NATIVE) nativeSave();
  const to = p.from === 'game' ? 'game' : 'home';
  if (to === 'home') { S.tab = 'home'; S.home.i = 0; S.home.zone = 'row'; refreshHome(); } else fillGame();
  S.seq = { kind: 'return', g, t: 0, flags: {}, under: to, cp: consolePose(g.sys), saved: ra, emu };
  S.seq.dest = to === 'home' ? homeSlotPose(g) : mediaPoseNow('game', g);
  S.screen = 'launch'; show('none');
  const fl = $('#flash'); fl.style.transition = 'none'; fl.style.opacity = 1;
  requestAnimationFrame(() => { fl.style.transition = 'opacity .5s ease'; fl.style.opacity = 0; });
}
function finishReturn(q) {
  const g = q.g; S.seq = null; S.onceEmu = null;
  if (q.under === 'home') { S.screen = 'home'; show('home'); Object.assign(pose('home:' + g.id, {}), q.now || homeSlotPose(g), { alpha: 1, bright: 1 }); refreshHome(); }
  else { S.screen = 'game'; show('game'); fillGame(); }
  renderTop();
  if (S.launchErr) { toast(S.launchErr); S.launchErr = null; return; }
  sfx.saved(); toast(q.saved ? `Saved to slot ${(rec(g.id).lastSlot) + 1}` : g.sys === 'android' ? `Back from ${g.title}` : `Back from ${q.emu}`);
}
