/* ================= Apps ================= */
const iconUrls = new Map();
const iconUrl = app => { if (!iconUrls.has(app.name)) iconUrls.set(app.name, appIcon(app).toDataURL()); return iconUrls.get(app.name); };
function refreshApps() {
  S.apps.i = clamp(S.apps.i, 0, APPS.length - 1); setMood('home');
  $('#appCount').textContent = `${APPS.length} apps on this handheld`;
  $('#appGrid').innerHTML = APPS.map((a, i) => `<div class="app ${!S.top.on && i === S.apps.i ? 'focus' : ''}"><img src="${iconUrl(a)}" alt=""><span>${esc(a.name)}</span>${ANDROID_GAMES.has(a.name) ? '<small>In Library</small>' : ''}</div>`).join('');
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
  ANDROID_GAMES.add(app.name); sfx.saved(); toast(`${app.name} is now in your Library, under Android`);
}
function unmarkGame(name) { ANDROID_GAMES.delete(name); sfx.back(); toast(`${name} is back in Apps`); }

/* ================= settings ================= */
const CATS = ['Profile', 'Appearance', 'Platforms', 'Emulators', 'Art and info', 'Achievements', 'Controls', 'Home screen and apps', 'About'];
const INSTALLED = { 'RetroArch': 'Installed, 9 cores', 'DuckStation': 'Installed', 'melonDS': 'Installed', 'PPSSPP': 'Installed', 'Mupen64Plus FZ': 'Installed' };
const ALL_EMUS = ['RetroArch', 'DuckStation', 'melonDS', 'PPSSPP', 'Mupen64Plus FZ', 'DraStic', 'Snes9x EX+', 'MD.emu', 'Pizza Boy GBC', 'Pizza Boy GBA'];
P.pf = {}; SYS_ORDER.forEach(k => P.pf[k] = { filter: 'None' }); P.gf = {}; P.hidden = new Set();
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
      action('Box art', 'Choose', 'Pick other art from the game databases, or make a box from the icon.', () => toast('Shows other art to choose from')),
      tog('Show on Home', 'show', 'Include it in your recently played cartridges.', { get show() { return !P.hidden.has(g.id); }, set show(v) { v ? P.hidden.delete(g.id) : P.hidden.add(g.id); } })] : [
      { label: 'Emulator', desc: own ? 'Chosen for this game.' : `Using the ${PLAT[g.sys].name} default. Choose to override it.`, value: own ? own : `Default (${P.emu[g.sys]})`, act: () => { const i = opts.indexOf(own || null); const n = opts[(i + 1) % opts.length]; if (n) P.gameEmu[g.id] = n; else delete P.gameEmu[g.id]; } },
      info('Saves', isRA(emu) ? 'Shelf’s slots' : `Kept by ${emu}`, isRA(emu) ? 'Four save slots with screenshots, per game.' : 'Standalone emulators keep their own saves.'),
      { label: 'Screen filter', desc: 'Overrides the platform’s filter.', value: gf.filter || `Default (${P.pf[g.sys].filter})`, act: () => { const v = [null, 'None', 'Scanlines', 'LCD grid'], i = v.indexOf(gf.filter); gf.filter = v[(i + 1) % v.length]; } },
      action('Box art', 'Choose', 'Pick other box art from ScreenScraper.', () => toast('Shows other box art to choose from')),
      info('Manual', hasManual(g) ? `${manualPages(g)} pages` : 'Not found', hasManual(g) ? 'Downloaded from ScreenScraper.' : 'ScreenScraper doesn’t have one for this game.'),
      tog('Show on Home', 'show', 'Include it in your recently played cartridges.', { get show() { return !P.hidden.has(g.id); }, set show(v) { v ? P.hidden.delete(g.id) : P.hidden.add(g.id); } })] }];
  }
  if (S.set.sub) {      // one platform's page
    const k = S.set.sub, pl = PLAT[k], emu = P.emu[k];
    return CATS.map(c => c === 'Platforms' ? { cat: pl.long, intro: `${GAMES.filter(g => g.sys === k && inLibrary(g)).length} games. Press B to go back to all platforms.`, items: k === 'android' ? [
      info('Launching', 'Opens the app', 'Android games open in their own app.'), info('Box', 'Keep case', 'A generic keep case at Blu-ray size.')] : [
      { label: 'Default emulator', desc: 'Used by every game on this platform unless a game chooses its own.', value: emu.replace('RetroArch: ', 'RetroArch, '), act: () => { const e = pl.emus; P.emu[k] = e[(e.indexOf(emu) + 1) % e.length]; } },
      info('Saves', isRA(emu) ? 'Shelf’s slots' : `Kept by ${emu}`, isRA(emu) ? 'RetroArch saves into Shelf’s four slots per game.' : 'Standalone emulators keep their own saves.'),
      cyc('Screen filter', P.pf[k], 'filter', ['None', 'Scanlines', 'LCD grid'], 'A filter over the game picture, when the emulator supports one.'),
      info('Game folder', `ROMs/${k}`, 'Where Shelf looks for this platform’s games.'),
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
        tog('Background motion', 'motion', 'Drifting backgrounds, twinkling stars and the slow float of boxes and cartridges.'),
        tog('Interface sounds', 'sound', 'Soft clicks when you move and choose.')] };
      case 'Platforms': return { cat: 'Platforms', intro: 'Choose a platform for its default emulator, filters and folders.', items: SYS_ORDER.map(k =>
        nav(PLAT[k].long, k === 'android' ? 'Apps' : P.emu[k].replace('RetroArch: ', 'RetroArch, '), `${GAMES.filter(g => g.sys === k && inLibrary(g)).length} games`, () => { S.set.sub = k; S.set.i = 0; })) };
      case 'Emulators': return { cat: 'Emulators', intro: 'Emulators found on this handheld. Shelf launches them for you.', items: [
        ...ALL_EMUS.map(e => INSTALLED[e] ? info(e, INSTALLED[e], e === 'RetroArch' ? 'Shelf manages save slots and achievements through RetroArch.' : 'A standalone emulator. It keeps its own saves.')
          : action(e, 'Get it', 'Not installed.', () => toast(`Opens ${e} in the app store`))),
        action('Look for emulators again', '', 'After installing or removing one.', () => toast('Found 5 emulators'))] };
      case 'Art and info': { const A = P.art, ss = A.source === 'ScreenScraper'; return { cat: 'Art and info', intro: 'Box art, manuals, media and details are downloaded for you. Cartridge labels too, where the source has them.', items: [
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
      default: return { cat: 'About', intro: 'Shelf for Android, prototype.', items: [info('Device', 'Retroid Pocket 3+', 'Android 11, Unisoc T618.'), info('Storage', '61.2 of 116 GB', 'Games, art, manuals and media.', 53), info('Version', '0.1 prototype', '')] };
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
  const b = S.set.back; if (b === 'game') { S.screen = 'game'; show('game'); fillGame(); } else enterTab(['home', 'library', 'apps'].includes(b) ? b : 'home');
}

/* ================= launching, playing, coming back ================= */
function startLaunch(g, slot, resume) {
  S.seq = { kind: 'launch', g, slot, resume, t: 0, from: S.screen, flags: {} };
  sfx.select();
}
function seqUpdate(dt) {
  const q = S.seq; q.t += dt;
  if (q.kind === 'launch') {
    if (q.t > .55 && !q.flags.fade) { q.flags.fade = 1; const fl = $('#flash'); fl.style.transition = 'opacity .35s ease-in'; fl.style.opacity = 1; sfx.insert(); }
    if (q.t > .95) { S.seq = null; startPlay(q.g, q.slot, q.resume, q.from); }
  } else if (q.t > .9) { S.seq = null; }
}
function startPlay(g, slot, resume, from) {
  const r = ensureRec(g), emu = emuFor(g);
  S.play = { g, slot, from, t: 0, seed: hash(g.id + 'play'), scroll: resume && r.slots[slot] ? (r.slots[slot].scroll || 120) : 0 };
  S.screen = 'play'; show('play');
  const gc = $('#gamecv'), [w, h] = PLAT[g.sys].res; gc.width = w; gc.height = h;
  const asp = PLAT[g.sys].aspect, dh = Math.min(H, W / asp), dw = dh * asp;
  Object.assign(gc.style, { width: dw + 'px', height: dh + 'px', left: (W - dw) / 2 + 'px', top: (H - dh) / 2 + 'px' });
  const tag = $('#emuTag'); tag.textContent = g.sys === 'android' ? `${g.title} is open. Press H, the Home button, to come back.` : `Playing in ${emu.replace('RetroArch: ', 'RetroArch, ')}. Press H, the Home button, to come back.`;
  tag.classList.add('show'); setTimeout(() => tag.classList.remove('show'), 3500);
  const fl = $('#flash'); fl.style.transition = 'opacity .5s ease-out'; fl.style.opacity = 0;
}
function playUpdate(dt) { const p = S.play; p.t += dt; p.scroll += dt * 40; gameFrame(p.g, p.seed, Math.floor(p.scroll), $('#gamecv')); }
function homeFromPlay() {
  const p = S.play; if (!p) return; S.play = null;
  const g = p.g, r = ensureRec(g), emu = emuFor(g), ra = isRA(emu) && P.prefs.autosave && !P.prefs.hardcore;
  if (ra) { r.slots[p.slot] = { ts: Date.now(), seed: p.seed, scroll: Math.floor(p.scroll) }; r.lastSlot = p.slot; }
  r.last = Date.now(); r.minutes += Math.max(1, Math.round(p.t / 60));
  P.recent = [g.id, ...P.recent.filter(x => x !== g.id)];
  const fl = $('#flash'); fl.style.transition = 'none'; fl.style.opacity = 1;
  requestAnimationFrame(() => { fl.style.transition = 'opacity .5s ease'; fl.style.opacity = 0; });
  S.seq = { kind: 'return', g, t: 0, flags: {} };
  if (p.from === 'home') { S.home.i = 0; S.home.zone = 'row'; enterTab('home'); }
  else { S.screen = 'game'; show('game'); fillGame(); }
  sfx.saved(); toast(ra ? `Saved to slot ${p.slot + 1}` : g.sys === 'android' ? `Back from ${g.title}` : `Back from ${emu}`);
}
