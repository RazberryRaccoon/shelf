/* ================= menu music: four original tracks, played live (the real app ships them as audio files) ================= */
P.prefs.music = { on: true, track: 'orbit', vol: 'Medium' };
const mtof = m => 440 * Math.pow(2, (m - 69) / 12);
let NOISE = null;
function voice(t, f, dur, o) {   // one note: an oscillator (or two, detuned), an envelope and an optional filter
  const a = AC, g = a.createGain(), end = t + dur + (o.rel || .3);
  let dest = g;
  if (o.cut) { const fl = a.createBiquadFilter(); fl.type = 'lowpass'; fl.frequency.value = o.cut; fl.Q.value = .3; fl.connect(g); dest = fl; }
  g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(o.gain, t + (o.att || .01));
  if (o.decay) g.gain.exponentialRampToValueAtTime(.0001, t + o.decay); else { g.gain.setValueAtTime(o.gain, t + dur); g.gain.linearRampToValueAtTime(0, end); }
  g.connect(MUS.out);
  (o.detune ? [-o.detune, o.detune] : [0]).forEach(dc => {
    const os = a.createOscillator(); os.type = o.type || 'sine'; os.frequency.value = f; os.detune.value = dc;
    if (o.fm) { const m = a.createOscillator(), mg = a.createGain(); m.frequency.value = f * o.fm; mg.gain.setValueAtTime(f * 1.2, t); mg.gain.exponentialRampToValueAtTime(f * .05, t + .6); m.connect(mg).connect(os.frequency); m.start(t); m.stop(end); }
    os.connect(dest); os.start(t); os.stop(end + .05);
  });
}
const V = {   // the instruments
  pad: (t, n, d) => n.forEach(m => voice(t, mtof(m), d, { type: 'sawtooth', gain: .006, att: 1.2, rel: 1.6, cut: 820, detune: 7 })),
  bell: (t, m) => { voice(t, mtof(m), 0, { gain: .018, decay: 2.4 }); voice(t, mtof(m) * 2.76, 0, { gain: .004, decay: 1.1 }); },
  keys: (t, n, d) => n.forEach(m => voice(t, mtof(m), d, { gain: .011, fm: 1, decay: 1.6 + d * .3 })),
  pluck: (t, m, cut = 2200) => voice(t, mtof(m), 0, { type: 'triangle', gain: .02, decay: .55, cut }),
  bass: (t, m, d) => voice(t, mtof(m), d, { gain: .045, att: .03, rel: .35 }),
  kick: t => { const a = AC, o = a.createOscillator(), g = a.createGain(); o.frequency.setValueAtTime(110, t); o.frequency.exponentialRampToValueAtTime(45, t + .12);
    g.gain.setValueAtTime(.06, t); g.gain.exponentialRampToValueAtTime(.0001, t + .25); o.connect(g).connect(MUS.out); o.start(t); o.stop(t + .3); },
  hat: (t, k = 1) => { const a = AC; if (!NOISE) { NOISE = a.createBuffer(1, a.sampleRate * .05, a.sampleRate); const d = NOISE.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1; }
    const s = a.createBufferSource(), f = a.createBiquadFilter(), g = a.createGain(); f.type = 'highpass'; f.frequency.value = 7000;
    g.gain.setValueAtTime(.008 * k, t); g.gain.exponentialRampToValueAtTime(.0001, t + .045); s.buffer = NOISE; s.connect(f).connect(g).connect(MUS.out); s.start(t); },
};
// each track plays one bar at a time: bar(n, start time, seconds per beat)
const TRACKS = {
  orbit: { name: 'Orbit', bpm: 64, mood: 'Slow pads and gentle bells, for the space theme.', bar(n, t, b) {
    const C = [[50, 57, 62, 66, 69], [52, 59, 64, 68, 71], [47, 54, 59, 62, 66], [43, 50, 55, 59, 62]][n % 4];
    V.pad(t, C.slice(1), b * 4); V.bass(t, C[0] - 12, b * 3.6);
    (n % 2 ? [[0, 4], [1.5, 2], [3, 3]] : [[.5, 3], [2, 4], [2.75, 1]]).forEach(([at, i]) => V.bell(t + at * b, C[i] + 12)); } },
  afternoon: { name: 'Afternoon', bpm: 84, mood: 'Soft electric piano and a light beat.', bar(n, t, b) {
    const C = [[53, 57, 60, 64], [52, 55, 59, 62], [50, 53, 57, 60], [55, 59, 62, 65, 69]][n % 4], root = [41, 40, 38, 43][n % 4];
    V.keys(t, C, b * 1.5); V.keys(t + b * 2.5, C.slice(1), b);
    V.bass(t, root, b * 1.4); V.bass(t + b * 2, root, b * .9); V.bass(t + b * 3, root + 7, b * .8);
    V.kick(t); V.kick(t + b * 2); for (let i = 0; i < 8; i++) V.hat(t + i * b / 2, i % 2 ? .6 : 1);
    if (n % 4 === 1 || n % 4 === 3) [[0, 76], [.5, 77], [1, 79], [2.5, 76]].forEach(([at, m]) => V.pluck(t + at * b, m, 3000)); } },
  tidepool: { name: 'Tidepool', bpm: 56, mood: 'Warm, slow and minimal.', bar(n, t, b) {
    const C = [[57, 60, 64, 71], [53, 57, 60, 64], [48, 55, 60, 64], [55, 59, 62, 64]][n % 4];
    V.pad(t, C, b * 4); V.bass(t, C[0] - 24, b * 3.8);
    [0, 1, 2, 3].forEach(i => V.pluck(t + i * b, C[(i + n) % C.length] + 12, 1300)); } },
  lanterns: { name: 'Lanterns', bpm: 92, mood: 'A little more melodic, with plucked strings.', bar(n, t, b) {
    const C = [[48, 52, 55, 60], [45, 48, 52, 57], [41, 45, 48, 53], [43, 47, 50, 55]][n % 4];
    [0, 1, 2, 3, 2, 1, 2, 3].forEach((k, i) => V.pluck(t + i * b / 2, C[k] + 12, 2600));
    V.bass(t, C[0] - 12, b * 2); V.bass(t + b * 2.5, C[0] - 12, b * 1.2);
    const mel = [[76, 74, 72, 74], [76, 79, 76, 74], [72, 69, 72, 74], [74, 71, 67, 71]][n % 4];
    if (n % 8 >= 4) mel.forEach((m, i) => V.bell(t + i * b, m)); } },
};
const TRACK_KEYS = Object.keys(TRACKS), MUSIC_VOL = { Low: .35, Medium: .6, High: .9 };
const MUS = { out: null, timer: 0, playing: null, bar: 0, next: 0, duck: 1, bars: 0 };
function musicEnsure() {   // starts after the first button press, since browsers only allow sound after one
  const a = AC; if (!a || MUS.timer) return;
  MUS.out = a.createGain(); MUS.out.gain.value = 0; MUS.out.connect(a.destination);
  MUS.timer = setInterval(musicTick, 120); musicTick();
}
function musicTrack() { const k = P.prefs.music.track; return k === 'shuffle' ? (MUS.shuffled || (MUS.shuffled = TRACK_KEYS[Math.floor(Math.random() * TRACK_KEYS.length)])) : k; }
function musicTick() {
  const a = AC, M = P.prefs.music; if (!a || !MUS.out) return;
  const want = M.on ? MUSIC_VOL[M.vol] * MUS.duck : 0;
  MUS.out.gain.setTargetAtTime(want, a.currentTime, MUS.duck < 1 ? .25 : .8);
  if (!M.on && MUS.out.gain.value < .001) { MUS.playing = null; return; }
  const k = musicTrack();
  if (k !== MUS.playing) { MUS.playing = k; MUS.bar = 0; MUS.next = Math.max(MUS.next, a.currentTime + .15); }
  const T = TRACKS[k], b = 60 / T.bpm;
  while (MUS.next < a.currentTime + .6) {
    T.bar(MUS.bar, MUS.next, b); MUS.next += b * 4; MUS.bar++;
    if (M.track === 'shuffle' && MUS.bar % 16 === 0) MUS.shuffled = TRACK_KEYS[(TRACK_KEYS.indexOf(k) + 1 + Math.floor(Math.random() * (TRACK_KEYS.length - 1))) % TRACK_KEYS.length];
  }
}
function musicDuck() { MUS.duck = S.screen === 'launch' || S.screen === 'play' ? 0 : S.screen === 'media' ? .2 : 1; }   // quiet while a game runs or a video plays
