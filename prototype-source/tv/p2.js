<script>
'use strict';
/* ================= basics ================= */
const W = 1920, H = 1080;
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

function fit() {
  const vw = innerWidth, vh = innerHeight, s = Math.min(vw / W, vh / H);
  stage.style.transform = `translate(${(vw - W * s) / 2}px, ${(vh - H * s) / 2}px) scale(${s})`;
  const dpr = Math.min(devicePixelRatio || 1, 2);
  const px = Math.max(640, Math.min(W, Math.round(W * s * dpr)));   // never more than the Pi's 1080p
  if (cv.width !== px) { cv.width = px; cv.height = Math.round(px * H / W); }
}
addEventListener('resize', fit);

/* ================= consoles ================= */
// Colors: the planet for each console (light, mid, dark), its nebula, and the accent used in text.
const SYS = {
  nes:     { name: 'NES', long: 'Nintendo Entertainment System', planet: ['#ffc2cf', '#d9587f', '#5c1a3a'], neb: ['#3d1236', '#140c2c'], accent: '#ff9db6', res: [256, 240], aspect: 4 / 3, hand: false },
  snes:    { name: 'SNES', long: 'Super Nintendo', planet: ['#e4dcff', '#8f7ee8', '#2f2470'], neb: ['#271a5a', '#0e0b2a'], accent: '#c2b6ff', res: [256, 224], aspect: 4 / 3, hand: false },
  genesis: { name: 'Genesis', long: 'Sega Genesis', planet: ['#bcd6ff', '#3f74e6', '#0f2463'], neb: ['#0f2556', '#070c26'], accent: '#86b0ff', res: [320, 224], aspect: 4 / 3, hand: false },
  gb:      { name: 'Game Boy', long: 'Game Boy', planet: ['#e6f2b8', '#8fb34a', '#2c4818'], neb: ['#1d3324', '#08121a'], accent: '#b9db74', res: [160, 144], aspect: 10 / 9, hand: true },
  gbc:     { name: 'Game Boy Color', long: 'Game Boy Color', planet: ['#bdf7ee', '#2fb8ae', '#0b4651'], neb: ['#0b3a44', '#061424'], accent: '#5fe0d4', res: [160, 144], aspect: 10 / 9, hand: true },
  gba:     { name: 'Game Boy Advance', long: 'Game Boy Advance', planet: ['#d9ccff', '#7258ea', '#21175e'], neb: ['#22185a', '#0b0a26'], accent: '#ab98ff', res: [240, 160], aspect: 3 / 2, hand: true },
};
const SYS_ORDER = ['nes', 'snes', 'genesis', 'gb', 'gbc', 'gba'];

/* ================= the library (all invented) ================= */
const G = (sys, title, year, dev, genre, players, ach, desc) => ({ id: hash(sys + title).toString(36), sys, title, year, dev, genre, players, ach, desc });
const GAMES = [
  G('nes', 'Lantern Knight', 1987, 'Hearthside Games', 'Action', '1 player', 32, 'A lone knight climbs a burning fortress to bring back the lantern that keeps the valley warm. Every floor adds a new trick, from crumbling bridges to gargoyles that breathe embers.'),
  G('nes', 'Comet Kid', 1989, 'Bluefin Soft', 'Platformer', '1 player', 40, 'Ride a comet across eight planets, bounce off moons and collect stardust before the sun sets on each world.'),
  G('nes', 'Turbo Tides', 1990, 'Marlin Works', 'Racing', '1–2 players', 24, 'Jet-ski racing through harbors, canals and one very angry storm. Shortcuts hide behind every wave.'),
  G('nes', 'Hollow Peak', 1988, 'Granite House', 'Adventure', '1 player', 45, 'Explore a mountain that is hollow all the way down. Map it room by room, and find out who carved the stairs.'),
  G('nes', 'Starship Courier', 1991, 'Orbit Nine', 'Shooter', '1–2 players', 28, 'Deliver the mail across a hostile sector. Upgrade your little ship one stamp at a time.'),
  G('snes', 'Aurora Quest', 1994, 'Northlight', 'Role-playing', '1 player', 60, 'Four travelers follow the northern lights to a city that only appears in winter. A long journey with a gentle heart.'),
  G('snes', 'Skyline Racer', 1992, 'Velocity Lab', 'Racing', '1–2 players', 36, 'Neon highways stacked a hundred stories high. Drift between towers and race the night itself.'),
  G('snes', 'Tidewalker', 1995, 'Coral Studio', 'Action', '1 player', 44, 'Walk the sea floor when the tide goes out, and get back to shore before it comes in again.'),
  G('snes', 'Garden of Gears', 1993, 'Brass Bird', 'Puzzle', '1–2 players', 30, 'Grow clockwork flowers and wind them up to solve two hundred puzzles, each one a little stranger than the last.'),
  G('snes', 'Nebula Knights', 1996, 'Orbit Nine', 'Strategy', '1 player', 52, 'Command a fleet of knightly starships in turn-based battles across a glittering nebula.'),
  G('genesis', 'Blast Velocity', 1991, 'Redline', 'Platformer', '1 player', 38, 'Run fast, run faster, then run through a loop the size of a moon. Speed is the whole idea.'),
  G('genesis', 'Neon Coast', 1993, 'Palmwave', 'Beat ’em up', '1–2 players', 34, 'Clean up a seaside city one boardwalk at a time, with a soundtrack built for summer nights.'),
  G('genesis', 'Iron Comet', 1990, 'Redline', 'Shooter', '1 player', 26, 'A side-scrolling shooter across a collapsing star system, with eight weapons you can swap mid-flight.'),
  G('genesis', 'Thunder Valley', 1992, 'Stormcraft', 'Racing', '1–2 players', 22, 'Off-road racing through canyons where the weather changes every lap.'),
  G('genesis', 'Quasar Drift', 1994, 'Palmwave', 'Puzzle', '1–2 players', 25, 'Steer falling stars into constellations before the sky fills up.'),
  G('gb', 'Pocket Golem', 1991, 'Small Wonder', 'Role-playing', '1 player', 35, 'Raise a tiny stone golem from pebble to mountain. Feed it moss, teach it tricks, and climb the tower.'),
  G('gb', 'Snow Valley Sled', 1990, 'Frostbit', 'Sports', '1 player', 18, 'One hill, one sled, one perfect run. Shave seconds off every course in the valley.'),
  G('gb', 'Tiny Tower Wizard', 1992, 'Small Wonder', 'Puzzle', '1 player', 30, 'A wizard the size of a thimble rebuilds a toppled tower one enchanted block at a time.'),
  G('gb', 'Moss Kingdom', 1993, 'Fernleaf', 'Adventure', '1 player', 40, 'A kingdom so small it fits in a garden. Talk to beetles, cross puddle seas and find the lost crown.'),
  G('gbc', 'Crystal Critters', 1999, 'Prism Garden', 'Role-playing', '1 player', 50, 'Catch creatures that grow crystals, trade them with friends and polish them into legends.'),
  G('gbc', 'Harbor Days', 2000, 'Gull & Co', 'Simulation', '1 player', 32, 'Run a seaside harbor through four seasons. Fish, trade and repaint the lighthouse.'),
  G('gbc', 'Pixie Patrol', 1999, 'Prism Garden', 'Platformer', '1 player', 28, 'A pixie with a lantern guards a forest at night. Light every path before morning.'),
  G('gbc', 'Sunset Skaters', 2001, 'Halfpipe', 'Sports', '1–2 players', 24, 'Skate a beach town as the sun goes down and chain tricks into a skyline of color.'),
  G('gba', 'Starfall Tactics', 2003, 'Northlight', 'Strategy', '1 player', 64, 'A grid-based tactics saga about soldiers who fell from the stars and the farmers who took them in.'),
  G('gba', 'Echo Island', 2002, 'Coral Studio', 'Adventure', '1 player', 46, 'Every sound on this island leaves a trace. Follow the echoes to the island’s oldest song.'),
  G('gba', 'Velvet Voyage', 2004, 'Lumen', 'Role-playing', '1 player', 55, 'A traveling theater troupe sails between floating islands, putting on shows and solving mysteries.'),
  G('gba', 'Gear Golf', 2002, 'Brass Bird', 'Sports', '1–4 players', 30, 'Mini golf on clockwork courses that move, spin and occasionally fight back.'),
  G('gba', 'Lumen Legends', 2005, 'Lumen', 'Action', '1 player', 48, 'A lighthouse keeper takes up a sword when the lights across the sea start going out.'),
];
const byId = Object.fromEntries(GAMES.map(g => [g.id, g]));
const theme = g => ({ 'Action': 'peaks', 'Platformer': 'planet', 'Racing': 'city', 'Adventure': 'forest', 'Shooter': 'space', 'Role-playing': 'peaks',
  'Puzzle': 'geo', 'Strategy': 'space', 'Beat ’em up': 'city', 'Sports': 'sea', 'Simulation': 'sea' })[g.genre] || 'planet';

/* ================= profiles ================= */
const NOW = Date.now(), HOUR = 3600e3, DAY = 86400e3;
function makeProfile(name, colors, plays) {
  const p = { name, colors, recent: [], played: {}, collections: [], ra: name === 'Riley' ? null : name.toLowerCase() + '_plays',
    prefs: { sound: true, motion: !reduceMotion, border: 'space', clock24: false, filter: 'None', music: 'Off', res: '1080p', hardcore: false,
      achPop: true, achHome: true, autosave: true, resume: 'Last save', rewind: false, ff: '2×', layout: 'Nintendo', combo: 'Select + Start', volume: 'Medium' } };
  plays.forEach(([title, ago, mins, earned, slots], i) => {
    const g = GAMES.find(x => x.title === title);
    p.recent.push(g.id);
    p.played[g.id] = { last: NOW - ago, minutes: mins, earned, fav: i % 3 === 0,
      slots: [0, 1, 2, 3].map(k => k < slots ? { ts: NOW - ago - k * DAY * 2.3, seed: hash(g.id + k) } : null), lastSlot: 0 };
  });
  return p;
}
const PROFILES = [
  makeProfile('Jordan', ['#ffd38a', '#e8834a', '#6a2a1e'], [['Aurora Quest', 2 * HOUR, 1312, 38, 3], ['Lantern Knight', 26 * HOUR, 248, 14, 1], ['Starfall Tactics', 3 * DAY, 905, 41, 2],
    ['Blast Velocity', 5 * DAY, 96, 9, 1], ['Pocket Golem', 8 * DAY, 422, 20, 2], ['Crystal Critters', 12 * DAY, 640, 27, 4], ['Comet Kid', 20 * DAY, 75, 6, 1]]),
  makeProfile('Sam', ['#bff3ff', '#3fa4d9', '#13355e'], [['Neon Coast', 5 * HOUR, 180, 12, 1], ['Gear Golf', 2 * DAY, 66, 8, 1], ['Skyline Racer', 4 * DAY, 214, 15, 2], ['Sunset Skaters', 9 * DAY, 120, 10, 1]]),
  makeProfile('Riley', ['#f6c8ff', '#b05ad8', '#401a5e'], []),
];

PROFILES[0].collections.push({ id: 'comfort', name: 'Comfort games', games: ['Pocket Golem', 'Harbor Days', 'Garden of Gears', 'Moss Kingdom'].map(t => GAMES.find(g => g.title === t).id) });
function agoText(ts) {
  const d = NOW - ts;
  if (d < HOUR) return 'Just now';
  if (d < DAY) { const h = Math.round(d / HOUR); return `${h} hour${h > 1 ? 's' : ''} ago`; }
  const n = Math.round(d / DAY);
  return n === 1 ? 'Yesterday' : n < 14 ? `${n} days ago` : `${Math.round(n / 7)} weeks ago`;
}
function playedText(m) { if (!m) return 'Not played yet'; if (m < 60) return `${m} minutes played`; const h = Math.floor(m / 60); return `${h} hour${h > 1 ? 's' : ''} played`; }

/* ================= sound (soft, optional) ================= */
let AC = null;
function audio() { if (!AC) { try { AC = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { } } if (AC && AC.state === 'suspended') AC.resume(); return AC; }
function tone(freq, dur, gain, type = 'sine', when = 0, glide = 0) {
  const p = curProfile(); if (p && !p.prefs.sound) return;
  const a = AC; if (!a) return;
  const t = a.currentTime + when, o = a.createOscillator(), g = a.createGain();
  o.type = type; o.frequency.setValueAtTime(freq, t); if (glide) o.frequency.exponentialRampToValueAtTime(freq * glide, t + dur);
  g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(gain, t + 0.012); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g).connect(a.destination); o.start(t); o.stop(t + dur + 0.05);
}
function thud(when = 0, gain = 0.12) {
  const p = curProfile(); if ((p && !p.prefs.sound) || !AC) return;
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
  const r = rng(hash(g.id + 'ach')), n = g.ach, item = ITEMS[theme(g)], list = [], used = {};
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
  const A = achievements(g), r = p.played[g.id], k = r ? r.earned : 0, rr = rng(hash(p.name + g.id));
  const byEase = [...A.list].sort((a, b) => b.rate - a.rate), earned = {};
  byEase.slice(0, k).forEach((a, i) => { earned[a.id] = { date: (r.last || NOW) - (k - i) * (DAY * (.3 + rr() * 2)), hc: rr() < .6 }; });
  return earned;
}
function profilePoints(p) { let t = 0; for (const id in p.played) { const g = byId[id], e = achFor(p, g); achievements(g).list.forEach(a => { if (e[a.id]) t += a.pts; }); } return t; }

/* ================= library rail: consoles and collections ================= */
const BASE_RAIL = [{ k: 'all', label: 'All games' }, ...SYS_ORDER.map(s => ({ k: s, label: SYS[s].name })), { head: 'Collections' },
  { k: 'fav', label: 'Favorites' }, { k: 'two', label: 'Two players' }, { k: 'prog', label: 'In progress' }];
// the rail: consoles, the automatic collections, then the profile's own collections and a way to make a new one
function railItems(p) { return [...BASE_RAIL, ...(p ? p.collections.map(c => ({ k: 'col:' + c.id, label: c.name, custom: c })) : []), { k: 'new', label: '+ New collection', add: true }]; }
function railMatch(k, g, p) {
  const r = p.played[g.id];
  if (k === 'all') return true; if (SYS[k]) return g.sys === k;
  if (k === 'fav') return !!(r && r.fav); if (k === 'two') return !g.players.startsWith('1 player');
  if (k === 'prog') return !!(r && r.earned > 0 && r.earned < g.ach);
  if (k.startsWith('col:')) { const c = p.collections.find(x => 'col:' + x.id === k); return !!c && c.games.includes(g.id); }
  if (k === 'new') return false;
  return true;
}
const PALETTES = [['#ffd38a', '#e8834a', '#6a2a1e'], ['#bff3ff', '#3fa4d9', '#13355e'], ['#f6c8ff', '#b05ad8', '#401a5e'], ['#d6ffc8', '#5ac46a', '#1a4a2a'], ['#ffe0f0', '#e0607e', '#5a1a33']];
