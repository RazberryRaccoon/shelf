<script>
'use strict';
/* ================= basics ================= */
const W = 1334, H = 750;
const $ = s => document.querySelector(s);
const stage = $('#stage'), cv = $('#gl');
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const ease = t => t < 0 ? 0 : t > 1 ? 1 : t * t * (3 - 2 * t);
const easeOut = t => 1 - Math.pow(1 - clamp(t, 0, 1), 3);
function hash(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
function rng(seed) { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function hexRgb(h) { const n = parseInt(h.slice(1), 16); return [(n >> 16 & 255) / 255, (n >> 8 & 255) / 255, (n & 255) / 255]; }
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const NATIVE = typeof ShelfNative !== 'undefined';   // running inside the Android app, with real games, apps and emulators
const joinKnown = (...xs) => xs.filter(Boolean).join(', ');   // real games may not have every detail yet

function fit() {
  const vw = innerWidth, vh = innerHeight - (NATIVE ? 0 : 30), s = Math.min(vw / W, vh / H);
  stage.style.transform = `translate(${(vw - W * s) / 2}px, ${(vh - H * s) / 2}px) scale(${s})`;
  const dpr = Math.min(devicePixelRatio || 1, 2);
  const px = Math.max(640, Math.min(NATIVE ? W : W * 1.5, Math.round(W * s * dpr)));   // on the handheld, one canvas pixel per screen pixel
  if (cv.width !== px) { cv.width = px; cv.height = Math.round(px * H / W); }
}
addEventListener('resize', fit);

/* ================= platforms ================= */
// Box sizes are the originals in decimetres (1 = 100 mm). Cardboard boxes become modern clamshell cases at the same size.
function shades(hex) {   // planet and nebula colors derived from a console's accent
  const [r, g, b] = hexRgb(hex), mix = (k, t) => '#' + [r, g, b].map(v => Math.round(clamp(k < 0 ? v * (1 + k) : v + (1 - v) * k, 0, 1) * 255).toString(16).padStart(2, '0')).join('');
  return { planet: [mix(.55), hex, mix(-.62)], neb: [mix(-.72), mix(-.88)] };
}
const PLAT = {
  nes:     { name: 'NES', long: 'Nintendo Entertainment System', box: [1.27, 1.82, .25], media: 'cart', accent: '#ff9db6', res: [256, 240], aspect: 4 / 3, emus: ['RetroArch: Mesen', 'RetroArch: FCEUmm', 'RetroArch: Nestopia'] },
  snes:    { name: 'SNES', long: 'Super Nintendo', box: [1.74, 1.21, .28], media: 'cart', accent: '#c2b6ff', res: [256, 224], aspect: 4 / 3, emus: ['RetroArch: Snes9x', 'RetroArch: bsnes', 'Snes9x EX+'] },
  n64:     { name: 'N64', long: 'Nintendo 64', box: [1.74, 1.21, .28], media: 'cart', accent: '#ffd06b', res: [320, 240], aspect: 4 / 3, emus: ['Mupen64Plus FZ', 'RetroArch: Mupen64Plus-Next', 'RetroArch: ParaLLEl N64'] },
  genesis: { name: 'Genesis', long: 'Sega Genesis', box: [1.28, 1.97, .25], media: 'cart', accent: '#86b0ff', res: [320, 224], aspect: 4 / 3, real: true, emus: ['RetroArch: Genesis Plus GX', 'RetroArch: PicoDrive', 'MD.emu'] },
  gb:      { name: 'Game Boy', long: 'Game Boy', box: [1.23, 1.23, .2], media: 'cart', accent: '#b9db74', res: [160, 144], aspect: 10 / 9, hand: true, emus: ['RetroArch: Gambatte', 'RetroArch: SameBoy', 'Pizza Boy GBC'] },
  gbc:     { name: 'Game Boy Color', long: 'Game Boy Color', box: [1.23, 1.23, .2], media: 'cart', accent: '#5fe0d4', res: [160, 144], aspect: 10 / 9, hand: true, emus: ['RetroArch: Gambatte', 'RetroArch: SameBoy', 'Pizza Boy GBC'] },
  gba:     { name: 'Game Boy Advance', long: 'Game Boy Advance', box: [1.23, 1.23, .2], media: 'cart', accent: '#ab98ff', res: [240, 160], aspect: 3 / 2, hand: true, emus: ['RetroArch: mGBA', 'RetroArch: gpSP', 'Pizza Boy GBA'] },
  ps1:     { name: 'PlayStation', long: 'PlayStation', box: [1.42, 1.25, .099], media: 'disc', accent: '#9fb2d8', res: [320, 240], aspect: 4 / 3, real: true, emus: ['DuckStation', 'RetroArch: SwanStation', 'RetroArch: Beetle PSX'] },
  ds:      { name: 'DS', long: 'Nintendo DS', box: [1.25, 1.37, .14], media: 'card', accent: '#ff9a82', res: [256, 384], aspect: 2 / 3, hand: true, real: true, emus: ['melonDS', 'DraStic', 'RetroArch: melonDS DS'] },
  psp:     { name: 'PSP', long: 'PlayStation Portable', box: [1.04, 1.75, .14], media: 'umd', accent: '#a7e8ff', res: [480, 272], aspect: 16 / 9, hand: true, real: true, emus: ['PPSSPP', 'RetroArch: PPSSPP'] },
  android: { name: 'Android', long: 'Android games', box: [1.35, 1.71, .12], media: 'acard', accent: '#8ff0b4', res: [320, 180], aspect: 16 / 9, real: true, emus: ['Opens the app'] },
};
const SYS_ORDER = ['nes', 'snes', 'n64', 'genesis', 'gb', 'gbc', 'gba', 'ps1', 'ds', 'psp', 'android'];
for (const k of SYS_ORDER) Object.assign(PLAT[k], shades(PLAT[k].accent));
// Every emulator Shelf can launch for each console: RetroArch cores and standalone apps. The list follows the
// Android configuration of ES-DE, another frontend, which documents how to launch each one. The first is the default.
const EMU_LIST = {
  nes:     { cores: ['Mesen', 'FCEUmm', 'Nestopia UE', 'QuickNES'], apps: ['NES.emu', 'Nesoid'] },
  snes:    { cores: ['Snes9x', 'bsnes', 'bsnes-hd', 'Snes9x 2010', 'Mesen-S', 'Beetle Supafaust'], apps: ['Snes9x EX+'] },
  n64:     { first: 'Mupen64Plus FZ', cores: ['Mupen64Plus-Next', 'ParaLLEl N64'], apps: ['Mupen64Plus FZ', 'Mupen64Plus AE'] },
  genesis: { cores: ['Genesis Plus GX', 'PicoDrive', 'BlastEm'], apps: ['MD.emu', 'Pizza Boy SC'] },
  gb:      { cores: ['Gambatte', 'SameBoy', 'Gearboy', 'mGBA', 'TGB Dual'], apps: ['Pizza Boy GBC', 'My OldBoy!', 'GBC.emu', 'Linkboy', 'SkyEmu'] },
  gbc:     { cores: ['Gambatte', 'SameBoy', 'Gearboy', 'mGBA', 'TGB Dual'], apps: ['Pizza Boy GBC', 'My OldBoy!', 'GBC.emu', 'Linkboy', 'SkyEmu'] },
  gba:     { cores: ['mGBA', 'gpSP', 'VBA-M', 'VBA Next'], apps: ['Pizza Boy GBA', 'My Boy!', 'GBA.emu', 'Linkboy', 'NooDS', 'SkyEmu'] },
  ps1:     { first: 'DuckStation', cores: ['SwanStation', 'Beetle PSX HW', 'Beetle PSX', 'PCSX ReARMed'], apps: ['DuckStation', 'ePSXe', 'FPseNG', 'FPse'] },
  ds:      { first: 'melonDS', cores: ['melonDS DS', 'melonDS', 'DeSmuME', 'DeSmuME 2015', 'NooDS'], apps: ['melonDS', 'DraStic', 'NooDS', 'SkyEmu'] },
  psp:     { first: 'PPSSPP', cores: ['PPSSPP'], apps: ['PPSSPP', 'PPSSPP Gold'] },
};
for (const k in EMU_LIST) { const e = EMU_LIST[k], all = [...e.cores.map(c => 'RetroArch: ' + c), ...e.apps]; PLAT[k].emus = e.first ? [e.first, ...all.filter(x => x !== e.first)] : all; }
// what this (pretend) handheld has installed
const INSTALLED_CORES = new Set(['Mesen', 'Snes9x', 'Mupen64Plus-Next', 'Genesis Plus GX', 'Gambatte', 'mGBA', 'SwanStation', 'melonDS DS', 'PPSSPP']);
const INSTALLED_APPS = new Set(['RetroArch', 'DuckStation', 'melonDS', 'PPSSPP', 'Mupen64Plus FZ']);
const emuInstalled = e => e.startsWith('RetroArch: ') ? INSTALLED_CORES.has(e.slice(11)) : INSTALLED_APPS.has(e) || e.startsWith('Other app');
const emuName = e => e.replace('RetroArch: ', 'RetroArch, ').replace('Other app: ', '');
const emuApp = e => e.startsWith('RetroArch') ? 'RetroArch' : e.replace('Other app: ', '');
const SYS = PLAT;   // the art generators use SYS
const isRA = emu => !NATIVE && emu.startsWith('RetroArch');   // Shelf's own save slots (not in the Android app yet: RetroArch keeps its saves)

/* ================= the library (all invented) ================= */
const G = (sys, title, year, dev, genre, players, ach, desc) => ({ id: hash(sys + title).toString(36), sys, title, year, dev, genre, players, ach, desc });
const GAMES = [
  G('nes', 'Lantern Knight', 1987, 'Hearthside Games', 'Action', '1 player', 32, 'A lone knight climbs a burning fortress to bring back the lantern that keeps the valley warm. Every floor adds a new trick, from crumbling bridges to gargoyles that breathe embers.'),
  G('nes', 'Comet Kid', 1989, 'Bluefin Soft', 'Platformer', '1 player', 40, 'Ride a comet across eight planets, bounce off moons and collect stardust before the sun sets on each world.'),
  G('nes', 'Hollow Peak', 1988, 'Granite House', 'Adventure', '1 player', 45, 'Explore a mountain that is hollow all the way down. Map it room by room, and find out who carved the stairs.'),
  G('nes', 'Starship Courier', 1991, 'Orbit Nine', 'Shooter', '1–2 players', 28, 'Deliver the mail across a hostile sector. Upgrade your little ship one stamp at a time.'),
  G('snes', 'Aurora Quest', 1994, 'Northlight', 'Role-playing', '1 player', 60, 'Four travelers follow the northern lights to a city that only appears in winter. A long journey with a gentle heart.'),
  G('snes', 'Skyline Racer', 1992, 'Velocity Lab', 'Racing', '1–2 players', 36, 'Neon highways stacked a hundred stories high. Drift between towers and race the night itself.'),
  G('snes', 'Garden of Gears', 1993, 'Brass Bird', 'Puzzle', '1–2 players', 30, 'Grow clockwork flowers and wind them up to solve two hundred puzzles, each one a little stranger than the last.'),
  G('snes', 'Tidewalker', 1995, 'Coral Studio', 'Action', '1 player', 44, 'Walk the sea floor when the tide goes out, and get back to shore before it comes in again.'),
  G('n64', 'Kite Kingdom', 1997, 'Windmill', 'Platformer', '1 player', 70, 'Glide between floating islands on a patchwork kite, collecting the wind stones that keep the kingdom aloft.'),
  G('n64', 'Orbit Golf', 1998, 'Brass Bird', 'Sports', '1–4 players', 40, 'Mini golf where every hole is a tiny planet. Mind the gravity, and never putt toward the sun.'),
  G('n64', 'Ember Wings', 1999, 'Redline', 'Shooter', '1–2 players', 36, 'Pilot a dragonfly fighter through canyons of glass in a race to relight the last beacon.'),
  G('genesis', 'Blast Velocity', 1991, 'Redline', 'Platformer', '1 player', 38, 'Run fast, run faster, then run through a loop the size of a moon. Speed is the whole idea.'),
  G('genesis', 'Neon Coast', 1993, 'Palmwave', 'Beat ’em up', '1–2 players', 34, 'Clean up a seaside city one boardwalk at a time, with a soundtrack built for summer nights.'),
  G('genesis', 'Iron Comet', 1990, 'Redline', 'Shooter', '1 player', 26, 'A side-scrolling shooter across a collapsing star system, with eight weapons you can swap mid-flight.'),
  G('gb', 'Pocket Golem', 1991, 'Small Wonder', 'Role-playing', '1 player', 35, 'Raise a tiny stone golem from pebble to mountain. Feed it moss, teach it tricks, and climb the tower.'),
  G('gb', 'Moss Kingdom', 1993, 'Fernleaf', 'Adventure', '1 player', 40, 'A kingdom so small it fits in a garden. Talk to beetles, cross puddle seas and find the lost crown.'),
  G('gb', 'Snow Valley Sled', 1990, 'Frostbit', 'Sports', '1 player', 18, 'One hill, one sled, one perfect run. Shave seconds off every course in the valley.'),
  G('gbc', 'Crystal Critters', 1999, 'Prism Garden', 'Role-playing', '1 player', 50, 'Catch creatures that grow crystals, trade them with friends and polish them into legends.'),
  G('gbc', 'Harbor Days', 2000, 'Gull & Co', 'Simulation', '1 player', 32, 'Run a seaside harbor through four seasons. Fish, trade and repaint the lighthouse.'),
  G('gbc', 'Pixie Patrol', 1999, 'Prism Garden', 'Platformer', '1 player', 28, 'A pixie with a lantern guards a forest at night. Light every path before morning.'),
  G('gba', 'Starfall Tactics', 2003, 'Northlight', 'Strategy', '1 player', 64, 'A grid-based tactics saga about soldiers who fell from the stars and the farmers who took them in.'),
  G('gba', 'Echo Island', 2002, 'Coral Studio', 'Adventure', '1 player', 46, 'Every sound on this island leaves a trace. Follow the echoes to the island’s oldest song.'),
  G('gba', 'Gear Golf', 2002, 'Brass Bird', 'Sports', '1–4 players', 30, 'Mini golf on clockwork courses that move, spin and occasionally fight back.'),
  G('ps1', 'Glass Cathedral', 1998, 'Vesper', 'Adventure', '1 player', 48, 'A thief and a bell-ringer explore a cathedral made of stained glass, where every window is a door to somewhere else.'),
  G('ps1', 'Vector Rally', 1997, 'Velocity Lab', 'Racing', '1–2 players', 42, 'Wireframe cars, impossible tracks and a soundtrack that speeds up with you.'),
  G('ps1', 'Hollow Moon', 1999, 'Northlight', 'Role-playing', '1 player', 66, 'The moon is hollow, and someone has been living inside it. A four-disc journey into the dark side.'),
  G('ds', 'Paper Planets', 2007, 'Folded Sky', 'Puzzle', '1 player', 40, 'Fold paper into planets, launch them into orbit, and build a solar system one crease at a time.'),
  G('ds', 'Pocket Atlas', 2008, 'Small Wonder', 'Adventure', '1 player', 44, 'Draw your own map as you explore an island that changes every time you close the lid.'),
  G('ds', 'Lantern Lab', 2009, 'Hearthside Games', 'Puzzle', '1–2 players', 35, 'Bend light through prisms and mirrors to wake up a sleeping laboratory.'),
  G('psp', 'Neon Drift', 2006, 'Velocity Lab', 'Racing', '1–4 players', 38, 'Night racing through a city that rearranges its streets every lap.'),
  G('psp', 'Signal Tower', 2008, 'Orbit Nine', 'Strategy', '1 player', 50, 'Defend the last radio tower on a quiet planet by building relays, decoys and a lot of antennas.'),
  G('psp', 'Tidal Knights', 2007, 'Coral Studio', 'Action', '1–2 players', 46, 'Knights who ride the tide into battle against a kingdom that lives underwater.'),
  G('android', 'Moonlight Dash', 2021, 'Pale Studio', 'Runner', '1 player', 0, 'Run along the rings of a sleeping planet, collecting moonlight before sunrise.'),
  G('android', 'Tiny Orchard', 2023, 'Leaf & Lantern', 'Simulation', '1 player', 0, 'Grow a pocket orchard, trade fruit with neighbors and decorate a cottage one pear at a time.'),
];
const byId = Object.fromEntries(GAMES.map(g => [g.id, g]));
const theme = g => ({ 'Action': 'peaks', 'Platformer': 'planet', 'Racing': 'city', 'Adventure': 'forest', 'Shooter': 'space', 'Role-playing': 'peaks',
  'Puzzle': 'geo', 'Strategy': 'space', 'Beat ’em up': 'city', 'Sports': 'sea', 'Simulation': 'sea', 'Runner': 'planet' })[g.genre] || 'planet';

/* ================= Android apps (invented) ================= */
const APPS = [
  { name: 'Browser', icon: 'globe', hue: 210 }, { name: 'Files', icon: 'folder', hue: 40 }, { name: 'Music', icon: 'note', hue: 330 }, { name: 'Photos', icon: 'sun', hue: 20 },
  { name: 'App Store', icon: 'bag', hue: 150 }, { name: 'RetroArch', icon: 'ring', hue: 260 }, { name: 'PPSSPP', icon: 'bolt', hue: 200 }, { name: 'DuckStation', icon: 'diamond', hue: 0 },
  { name: 'melonDS', icon: 'crown', hue: 15 }, { name: 'Mupen64Plus FZ', icon: 'star', hue: 45 }, { name: 'Pixel Pond', icon: 'heart', hue: 185, game: true }, { name: 'Sky Courier', icon: 'flag', hue: 280, game: true },
  { name: 'Moonlight Dash', icon: 'moon', hue: 240, game: true, marked: true }, { name: 'Tiny Orchard', icon: 'leaf', hue: 110, game: true, marked: true }, { name: 'Clock', icon: 'clock', hue: 220 }, { name: 'Settings', icon: 'gear', hue: 230 },
];

/* ================= the one profile ================= */
const NOW = Date.now(), HOUR = 3600e3, DAY = 86400e3;
const P = { name: '', colors: ['#ffd38a', '#e8834a', '#6a2a1e'], setup: false, recent: [], played: {}, collections: [], ra: 'player_one',
  prefs: { homeArt: 'shot', sound: true, motion: !reduceMotion, theme: 'space', accentFollows: true, caseTint: false, hardcore: false, achPop: true, autosave: true, rewind: false, res: 'native',
    filter: 'None', layout: 'Nintendo', homeLauncher: true, showApps: 'All apps' },
  emu: {}, gameEmu: {}, art: { source: 'ScreenScraper', ss: 'player_one', videos: true, manuals: true, backs: true, region: 'USA' } };
for (const k of SYS_ORDER) P.emu[k] = PLAT[k].emus[0];
P.pf = {}; SYS_ORDER.forEach(k => P.pf[k] = { filter: 'None', ask: false });
[['Aurora Quest', 2 * HOUR, 1312, 38, 3], ['Glass Cathedral', 22 * HOUR, 640, 20, 2], ['Kite Kingdom', 2 * DAY, 905, 41, 2], ['Lantern Knight', 3 * DAY, 248, 14, 1],
 ['Paper Planets', 4 * DAY, 300, 12, 1], ['Neon Drift', 6 * DAY, 210, 9, 1], ['Starfall Tactics', 8 * DAY, 422, 20, 2], ['Moonlight Dash', 9 * DAY, 120, 0, 0], ['Pocket Golem', 12 * DAY, 75, 6, 1], ['Crystal Critters', 15 * DAY, 640, 27, 2]]
  .forEach(([title, ago, mins, earned, slots], i) => {
    const g = GAMES.find(x => x.title === title); P.recent.push(g.id);
    P.played[g.id] = { last: NOW - ago, minutes: mins, earned, fav: i % 3 === 0, slots: [0, 1, 2, 3].map(k => k < slots ? { ts: NOW - ago - k * DAY * 2.3, seed: hash(g.id + k) } : null), lastSlot: 0 };
  });
P.collections.push({ id: 'couch', name: 'Couch co-op', games: ['Orbit Golf', 'Neon Coast', 'Skyline Racer', 'Vector Rally'].map(t => GAMES.find(g => g.title === t).id) });
const emuFor = g => (S.onceEmu && S.onceEmu.id === g.id && S.onceEmu.e) || P.gameEmu[g.id] || P.emu[g.sys];
function agoText(ts) {
  const d = Date.now() - ts;
  if (d < HOUR) return 'Just now';
  if (d < DAY) { const h = Math.round(d / HOUR); return `${h} hour${h > 1 ? 's' : ''} ago`; }
  const n = Math.round(d / DAY);
  return n === 1 ? 'Yesterday' : n < 14 ? `${n} days ago` : `${Math.round(n / 7)} weeks ago`;
}
function playedText(m) { if (!m) return 'Not played yet'; if (m < 60) return `${m} minute${m > 1 ? 's' : ''} played`; const h = Math.floor(m / 60); return `${h} hour${h > 1 ? 's' : ''} played`; }

/* ================= sound (soft, optional) ================= */
let AC = null;
function audio() { if (!AC) { try { AC = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { } } if (AC && AC.state === 'suspended') AC.resume(); return AC; }
function tone(freq, dur, gain, type = 'sine', when = 0, glide = 0) {
  if (!P.prefs.sound) return;
  const a = AC; if (!a) return;
  const t = a.currentTime + when, o = a.createOscillator(), g = a.createGain();
  o.type = type; o.frequency.setValueAtTime(freq, t); if (glide) o.frequency.exponentialRampToValueAtTime(freq * glide, t + dur);
  g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(gain, t + 0.012); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g).connect(a.destination); o.start(t); o.stop(t + dur + 0.05);
}
function thud(when = 0, gain = 0.12) {
  if (!P.prefs.sound || !AC) return;
  const a = AC, t = a.currentTime + when, len = 0.12, buf = a.createBuffer(1, a.sampleRate * len, a.sampleRate), d = buf.getChannelData(0);
  for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, 4);
  const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain();
  f.type = 'lowpass'; f.frequency.value = 900; g.gain.value = gain; s.buffer = buf; s.connect(f).connect(g).connect(a.destination); s.start(t);
}
const sfx = {
  move: () => tone(1320, 0.05, 0.018),
  select: () => { tone(660, 0.12, 0.04); tone(990, 0.16, 0.035, 'sine', 0.06); },
  back: () => tone(700, 0.12, 0.03, 'sine', 0, 0.7),
  tab: () => tone(880, 0.08, 0.025, 'triangle'),
  insert: () => { thud(0, 0.16); tone(220, 0.08, 0.04, 'triangle', 0.02); },
  power: () => [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(f, 1.4, 0.022, 'sine', i * 0.07)),
  off: () => [783.99, 587.33, 392].forEach((f, i) => tone(f, .5, 0.018, 'sine', i * 0.08)),
  saved: () => { tone(784, 0.14, 0.03); tone(1175, 0.24, 0.028, 'sine', 0.09); },
};

/* ================= achievements (generated; on the Pi these come from the RetroAchievements API) ================= */
const ITEMS = { peaks: 'embers', planet: 'stardust', city: 'tokens', forest: 'acorns', space: 'star shards', sea: 'shells', geo: 'gears' };
const PLACES = ['the first village', 'the old bridge', 'the crystal caves', 'the clock tower', 'the sunken garden', 'the sky harbor', 'the frozen pass', 'the last fortress'];
const CHALLENGES = [
  ['Untouchable', 'Beat a boss without taking a hit'], ['Speed Runner', 'Finish any chapter in under three minutes'], ['Sharp Eyes', 'Find a hidden room'],
  ['No Shortcuts', 'Finish a chapter without skipping a single area'], ['Steady Hands', 'Clear a stage without falling once'], ['Night Owl', 'Play a full chapter at night in the game'],
  ['Collector', 'Collect 100 {item}'], ['Treasure Hunter', 'Open every chest in {place}'], ['High Flyer', 'Reach the highest point in {place}'],
  ['Second Wind', 'Recover from one hit point and win'], ['Iron Will', 'Finish the game without a continue'], ['Patient One', 'Wait out the storm in {place}'],
  ['Quick Draw', 'Win a battle in the first ten seconds'], ['Lucky Break', 'Find the rare drop in {place}'], ['Big Spender', 'Spend 5,000 coins in one visit'],
  ['Explorer', 'Visit every area on the map'], ['Perfectionist', 'Get a perfect rating on any stage'], ['Pacifist', 'Clear {place} without defeating anyone'],
  ['Hidden Path', 'Take the secret route out of {place}'], ['Full Pockets', 'Carry the maximum number of {item}'], ['Old Friend', 'Talk to every villager in {place}'],
  ['Long Way Round', 'Reach {place} by the scenic route'], ['Bright Idea', 'Solve the lantern puzzle in {place}'], ['Echo', 'Find all six sound stones'] ];
const ACH_ICONS = ['star', 'diamond', 'ring', 'bolt', 'crown', 'flag', 'heart', 'moon'];
const achCache = {};
function achievements(g) {
  if (achCache[g.id]) return achCache[g.id];
  const r = rng(hash(g.id + 'ach')), n = Math.max(1, g.ach), item = ITEMS[theme(g)], list = [], used = {};
  const players = 400 + (hash(g.id) % 14000), prog = Math.min(8, Math.max(3, Math.round(n * .3)));
  const deck = [...CHALLENGES].sort(() => r() - .5);   // each name once before any repeats
  const name = t => { used[t] = (used[t] || 0) + 1; return used[t] > 1 ? `${t} ${['', '', 'II', 'III', 'IV', 'V', 'VI'][used[t]] || used[t]}` : t; };
  for (let i = 0; i < n; i++) {
    let a;
    if (i === n - 1) a = { title: 'The Long Road Home', desc: `Finish ${g.title}`, type: 'Win condition', pts: 25, rate: .14 + r() * .1 };
    else if (i < prog) { const place = PLACES[i % PLACES.length]; a = { title: name(['Setting Out', 'Onward', 'Deeper Still', 'Halfway There', 'Over the Ridge', 'Almost Home', 'Final Approach', 'Last Light'][i % 8]),
      desc: `Reach ${place}`, type: 'Progression', pts: [3, 5, 5, 10][i % 4], rate: Math.max(.2, .9 - i * .07) }; }
    else { const [t, d] = deck[(i - prog) % deck.length];
      const tier = (used[t] || 0) + 1, title = name(t);
      a = { title, desc: d.replace('100', [100, 250, 500, 999][Math.min(3, tier - 1)]).replace('{item}', item).replace('{place}', PLACES[Math.floor(r() * PLACES.length)]), type: r() < .12 ? 'Missable' : '', pts: [1, 2, 3, 5, 5, 10, 10, 25][Math.floor(r() * 8)], rate: (.03 + r() * .45) / tier }; }
    a.icon = ACH_ICONS[Math.floor(r() * ACH_ICONS.length)]; a.id = g.id + ':' + i; a.order = i;
    list.push(a);
  }
  return (achCache[g.id] = { list, players, points: list.reduce((s, a) => s + a.pts, 0) });
}
// which of a game's achievements this profile has earned: the most common ones first, with dates and hardcore flags
function achFor(p, g) {
  const A = achievements(g), r = (p || P).played[g.id], k = r ? r.earned : 0, rr = rng(hash(p.name + g.id));
  const byEase = [...A.list].sort((a, b) => b.rate - a.rate), earned = {};
  byEase.slice(0, k).forEach((a, i) => { earned[a.id] = { date: (r.last || NOW) - (k - i) * (DAY * (.3 + rr() * 2)), hc: rr() < .6 }; });
  return earned;
}
function profilePoints(p) { let t = 0; for (const id in p.played) { const g = byId[id], e = achFor(p, g); achievements(g).list.forEach(a => { if (e[a.id]) t += a.pts; }); } return t; }

/* ================= library rail ================= */
const BASE_RAIL = [{ k: 'all', label: 'All games' }, ...SYS_ORDER.map(s => ({ k: s, label: PLAT[s].name })), { head: 'Collections' },
  { k: 'fav', label: 'Favorites' }, { k: 'prog', label: 'In progress' }];
function railItems() { return [...BASE_RAIL, ...P.collections.map(c => ({ k: 'col:' + c.id, label: c.name, custom: c }))]; }
function railMatch(k, g) {
  const r = P.played[g.id];
  if (k === 'all') return true; if (PLAT[k]) return g.sys === k;
  if (k === 'fav') return !!(r && r.fav);
  if (k === 'prog') return !!(r && r.earned > 0 && r.earned < g.ach);
  if (k.startsWith('col:')) { const c = P.collections.find(x => 'col:' + x.id === k); return !!c && c.games.includes(g.id); }
  return true;
}
