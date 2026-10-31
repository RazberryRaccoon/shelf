#!/usr/bin/env python3
"""
Shelf's menu music and ambient sounds, written out note by note and played on small synthesized instruments (no
samples, no recordings), so every sound is Shelf's own. One track per theme, each a seamless loop, and one ambient
loop per theme (two for Forest: birds by day, crickets by night), plus three far-off thunder rolls for Stormy.

    python3 tools/music/compose.py            # everything, into android/native/src/main/assets/sound/
    python3 tools/music/compose.py city rain  # just these (track or ambient names)

Needs numpy and ffmpeg (with libvorbis). Each piece is rendered a few seconds longer than its loop, and the tail (the
last notes ringing on and the reverb) is folded back onto the start, so the loop has no seam.
"""
import os, subprocess, sys, tempfile, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'android', 'native', 'src', 'main', 'assets', 'sound')
rng = np.random.default_rng(7)

# ---------- small helpers ----------
NAMES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
def midi(n):
    """'C#4', 'Bb2' or a number, to a MIDI note."""
    if isinstance(n, (int, float)): return n
    k = NAMES[n[0]]; i = 1
    while n[i] in '#b': k += 1 if n[i] == '#' else -1; i += 1
    return k + 12 * (int(n[i:]) + 1)
def hz(n): return 440.0 * 2 ** ((midi(n) - 69) / 12)
def T(sec): return np.arange(int(sec * SR)) / SR

def fade(n, a, r):
    """An envelope n samples long: up over a seconds, down over the last r."""
    e = np.ones(n); a = min(int(a * SR), n); r = min(int(r * SR), n)
    if a: e[:a] = np.linspace(0, 1, a) ** 1.5
    if r: e[n - r:] *= np.linspace(1, 0, r) ** 1.5
    return e

def shape(x, f):
    """Filters x by a gain per frequency (f: a function of frequencies in Hz), through the FFT."""
    X = np.fft.rfft(x); fr = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * f(fr), len(x))
def lowpass(x, fc, order=2): return shape(x, lambda f: 1 / (1 + (f / fc) ** (2 * order)) ** .5)
def highpass(x, fc, order=2): return shape(x, lambda f: 1 - 1 / (1 + (f / max(fc, 1)) ** (2 * order)) ** .5)
def band(x, lo, hi): return highpass(lowpass(x, hi), lo)
def noise(sec): return rng.standard_normal(int(sec * SR))

# ---------- instruments: each returns a mono note, ringing on past its written length where it would ----------
def pad(f, dur, vel=1., bright=1., attack=.9, release=1.6):
    """Warm and soft: a few detuned voices of a rounded saw (partials falling off quickly), swelling in."""
    t = T(dur + release); out = np.zeros_like(t)
    for cents, ph in ((-5, 0.), (0, 1.3), (6, 2.1)):
        fr = f * 2 ** (cents / 1200)
        for n in range(1, 9):
            if fr * n > 9000: break
            out += np.sin(2 * np.pi * fr * n * t + ph * n) / n ** (2.1 - .5 * bright)
    return out * fade(len(t), attack, release) * vel * .22

def epiano(f, dur, vel=1.):
    """An electric piano's tine: FM, bright at the strike, mellowing; rings, then damps when let go."""
    t = T(dur + .35); idx = (1.1 + 1.2 * vel) * np.exp(-t / .35)
    s = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t)) + .08 * np.sin(2 * np.pi * f * 7.1 * t) * np.exp(-t / .04)
    decay = np.exp(-t / (2.4 * (261 / f) ** .4))
    return s * decay * fade(len(t), .003, .3) * vel * .5

def bell(f, dur, vel=1., ring=3.):
    """A glass bell: FM at an inharmonic ratio, ringing long."""
    t = T(dur + ring); idx = 1.6 * np.exp(-t / .5)
    s = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * 3.5 * t)) + .25 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / .7)
    return s * np.exp(-t / ring * 2.2) * fade(len(t), .004, .4) * vel * .32

def kalimba(f, dur, vel=1.):
    t = T(1.6)
    s = np.sin(2 * np.pi * f * t) + .35 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t / .03) + .12 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t / .2)
    return s * np.exp(-t / .55) * fade(len(t), .002, .1) * vel * .42

def marimba(f, dur, vel=1.):
    t = T(1.2)
    s = np.sin(2 * np.pi * f * t) + .3 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t / .05) + .1 * np.sin(2 * np.pi * f * 9.8 * t) * np.exp(-t / .015)
    return s * np.exp(-t / .38) * fade(len(t), .002, .1) * vel * .5

def harp(f, dur, vel=1.):
    t = T(2.6); s = np.zeros_like(t)
    for n in range(1, 8): s += np.sin(2 * np.pi * f * n * t) / n ** 1.4 * np.exp(-t * (.9 + .9 * n))
    return s * fade(len(t), .003, .3) * vel * .4

def oud(f, dur, vel=1.):
    """A plucked, woody string: strong low partials, a tiny slide into the note."""
    t = T(min(dur, 1.4) + .5); fr = f * (1 - .015 * np.exp(-t / .03)); ph = 2 * np.pi * np.cumsum(fr) / SR; s = np.zeros_like(t)
    for n in range(1, 11): s += np.sin(ph * n) * (1 / n) * (1.5 if n in (2, 3) else 1) * np.exp(-t * (2.2 + 1.3 * n))
    return s * fade(len(t), .002, .25) * vel * .45

def piano(f, dur, vel=1.):
    """A soft piano: slightly stretched partials, the high ones dying first; damped when let go."""
    t = T(dur + .4); s = np.zeros_like(t); B = .0004
    for n in range(1, 11):
        fn = f * n * np.sqrt(1 + B * n * n)
        if fn > 12000: break
        s += np.sin(2 * np.pi * fn * t) / n ** (1.6 - .4 * vel) * (.7 * np.exp(-t * (.5 + .45 * n)) + .3 * np.exp(-t * (.12 + .1 * n)))
    s *= (261 / f) ** .15
    return s * fade(len(t), .002, .35) * vel * .38

def flute(f, dur, vel=1.):
    t = T(dur + .2); vib = 1 + .004 * np.sin(2 * np.pi * 5.1 * t) * np.clip((t - .3) / .4, 0, 1)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    s = np.sin(ph) + .12 * np.sin(2 * ph) + .04 * np.sin(3 * ph)
    breath = band(rng.standard_normal(len(t)), 1500, 5000) * .05
    return (s + breath) * fade(len(t), .09, .22) * vel * .3

def bass(f, dur, vel=1.):
    t = T(dur + .15); s = np.sin(2 * np.pi * f * t) + .22 * np.sin(4 * np.pi * f * t) * np.exp(-t / .3)
    return s * np.exp(-t / 3.) * fade(len(t), .012, .12) * vel * .55

def cello(f, dur, vel=1.): return pad(f, dur, vel, bright=1.6, attack=1.4, release=1.8) * 1.2

# drums: all soft
def kick(vel=1.):
    t = T(.5); fr = 48 + 70 * np.exp(-t / .035); ph = 2 * np.pi * np.cumsum(fr) / SR
    return np.sin(ph) * np.exp(-t / .22) * fade(len(t), .002, .05) * vel * .7
def brush(vel=1.):
    t = T(.35); return band(noise(.35), 600, 6000) * np.exp(-t / .1) * fade(len(t), .012, .05) * vel * .2
def hat(vel=1.):
    t = T(.12); return highpass(noise(.12), 7000) * np.exp(-t / .025) * vel * .16
def shaker(vel=1.):
    t = T(.15); return band(noise(.15), 4000, 11000) * np.exp(-((t - .03) / .025) ** 2) * vel * .14
def dum(vel=1.):
    t = T(.7); fr = 70 + 30 * np.exp(-t / .05); ph = 2 * np.pi * np.cumsum(fr) / SR
    return (np.sin(ph) * np.exp(-t / .3) + band(noise(.7), 100, 500) * .2 * np.exp(-t / .04)) * fade(len(t), .003, .05) * vel * .6
def tek(vel=1.):
    t = T(.2); return (band(noise(.2), 900, 4000) * np.exp(-t / .03) + np.sin(2 * np.pi * 420 * t) * .25 * np.exp(-t / .03)) * vel * .3

# ---------- the mix: notes placed in time, a reverb, and the loop's tail folded back on its start ----------
def impulse(sec=3.2, damp=5000):
    t = T(sec); out = []
    for ch in range(2):
        n = rng.standard_normal(len(t)) * np.exp(-t / (sec / 6.9)); n = lowpass(n, damp, 1)
        n[:int(.02 * SR)] = 0; out.append(n / np.sqrt(np.sum(n ** 2)))
    return np.array(out)

class Mix:
    def __init__(self, seconds, tail=10):
        self.loop = int(seconds * SR); n = self.loop + int(tail * SR)
        self.dry = np.zeros((2, n)); self.wet = np.zeros(n); self.sec = seconds
    def add(self, t, sig, pan=0., gain=1., send=.3):
        i = int(round(t * SR)) % self.loop; j = min(i + len(sig), self.dry.shape[1]); s = sig[:j - i] * gain
        l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
        self.dry[0, i:j] += s * l * 1.414; self.dry[1, i:j] += s * r * 1.414; self.wet[i:j] += s * send
    def render(self, rev=3.2, damp=5000, wet=1.):
        ir = impulse(rev, damp); n = self.dry.shape[1]; m = 1 << int(np.ceil(np.log2(n + ir.shape[1])))
        W = np.fft.rfft(self.wet, m); out = self.dry.copy()
        for ch in range(2): out[ch] += np.fft.irfft(W * np.fft.rfft(ir[ch], m), m)[:n] * wet
        L = self.loop; tail = out[:, L:]
        while tail.shape[1]: k = min(L, tail.shape[1]); out[:, :k] += tail[:, :k]; tail = tail[:, k:]
        return out[:, :L]

def human(t, amt=.008): return t + rng.uniform(-amt, amt)

def master(x, rms_db):
    """Brings a loop to a set loudness (RMS), with a gentle limit on peaks."""
    x = x - x.mean(axis=1, keepdims=True)
    rms = np.sqrt(np.mean(x ** 2)); x = x * (10 ** (rms_db / 20) / rms)
    pk = np.max(np.abs(x))
    if pk > .89: x = np.tanh(x / .89) * .89   # a soft limit on the few loudest peaks
    return np.clip(x, -.98, .98)

def write(name, x, q=3):
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f: path = f.name
    pcm = (np.clip(x.T, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w: w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    dst = os.path.join(OUT, name + '.ogg')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', path, '-c:a', 'libvorbis', '-q:a', str(q), dst], check=True)
    os.remove(path); print(f'{name}.ogg  {x.shape[1] / SR:5.1f} s  {os.path.getsize(dst) // 1024} KB')

def chord_notes(ch): return [hz(n) for n in ch]

# ---------- the tracks ----------
def space():
    """Drift: slow Lydian pads in D, glass bells arpeggiating up above, a soft sub. 68 bpm, 24 bars."""
    beat = 60 / 68; bar = 4 * beat
    chords = [['D3', 'A3', 'C#4', 'E4', 'F#4'], ['D3', 'B3', 'E4', 'G#4'], ['B2', 'F#3', 'A3', 'C#4', 'D4'], ['G2', 'D3', 'F#3', 'B3', 'C#4']]
    m = Mix(24 * bar)
    for b in range(0, 24, 2):
        ch = chords[(b // 2) % 4]; t0 = b * bar
        for k, n in enumerate(ch): m.add(t0, pad(hz(n), 2 * bar - .3, .8), pan=-.5 + k / (len(ch) - 1), send=.5)
        m.add(t0, bass(hz(midi(ch[0]) - 12), 2 * bar - .2, .6), send=.1)
        m.add(t0, pad(hz(midi(ch[-1]) + 12), 2 * bar - .3, .25, bright=.5, attack=2.), pan=.3, send=.8)
        # bells: up through the chord an octave or two higher, sparser in the first pass, fuller in the second
        cyc = b // 8; tones = [midi(n) + 12 for n in ch[1:]] + [midi(n) + 24 for n in ch[1:3]]
        density = [.35, .8, .55][cyc]
        for s in range(16):
            if rng.random() < density and (s % 2 == 0 or cyc == 1):
                n = tones[(s * 2 + cyc) % len(tones)]
                m.add(human(t0 + s * beat / 2), bell(hz(n), .5, .5 + .3 * rng.random()), pan=rng.uniform(-.7, .7), send=.7)
    return m.render(4.5, 4000)

def city():
    """After Hours: a late-night electric piano, swung, with soft brushes and a round bass. 80 bpm, 24 bars."""
    beat = 60 / 80; bar = 4 * beat; sw = beat * 2 / 3
    chords = [['F3', 'A3', 'C4', 'E4', 'G4'], ['E3', 'G3', 'B3', 'D4', 'A4'], ['D3', 'F3', 'A3', 'C4', 'E4'], ['C3', 'E3', 'G3', 'B3', 'D4']]
    roots = ['F2', 'E2', 'D2', 'C2']
    m = Mix(24 * bar)
    mel = [('E5', 0, 1), ('G5', 1.66, .5), ('A5', 2, 1), ('G5', 3, 1), ('E5', 4, 1.5), ('D5', 5.66, .5), ('C5', 6, 1.5), ('D5', 7.66, .34),
           ('E5', 8, 1), ('G5', 9, .66), ('E5', 9.66, .34), ('D5', 10, 2), ('C5', 12, 1), ('A4', 13, 1), ('G4', 14, 2)]
    for b in range(24):
        t0 = b * bar; ch = chords[b % 4]; r = hz(roots[b % 4]); brk = 16 <= b < 20
        v = .55 + .1 * rng.random()
        for n in ch: m.add(human(t0, .012), epiano(hz(n), 1.4 * beat, v), pan=-.25 + .5 * rng.random(), send=.3)
        for n in ch[1:4]: m.add(human(t0 + beat + sw, .01), epiano(hz(n), .4 * beat, v * .7), pan=.1, send=.3)
        for n in ch[:3]: m.add(t0, pad(hz(n), bar - .2, .22 if brk else .14, bright=.4, attack=.6, release=1.), pan=-.2, send=.5)   # a soft bed
        if brk:
            for n in ch[1:]: m.add(human(t0 + 2 * beat + sw, .01), epiano(hz(n), 1.2 * beat, v * .75), pan=.15, send=.4)
        if not brk:
            m.add(t0, bass(r, 1.4 * beat, .8), send=.05); m.add(t0 + 2 * beat + sw, bass(r * 1.5, .5 * beat, .6), send=.05)
            m.add(t0 + 3 * beat + sw, bass(hz(midi(roots[(b + 1) % 4]) + 1), .3 * beat, .5), send=.05)
            m.add(t0, kick(.8), send=.05); m.add(t0 + beat + sw, kick(.35), send=.05); m.add(t0 + 2 * beat, kick(.7), send=.05)
            m.add(human(t0 + beat), brush(.8), pan=.15, send=.2); m.add(human(t0 + 3 * beat), brush(.85), pan=.15, send=.2)
            for k in range(4):
                m.add(human(t0 + k * beat, .004), hat(.6), pan=-.3, send=.1); m.add(human(t0 + k * beat + sw, .004), hat(.35), pan=-.3, send=.1)
        # the melody, over the second and fourth four-bar passes and after the breakdown
        if b in range(4, 8) or b in range(12, 16) or b in range(20, 24):
            for n, at, d in mel:
                if int(at // 4) == b % 4: m.add(human(t0 + (at % 4) * beat), epiano(hz(n), d * beat, .62), pan=.2, send=.4)
    return m.render(2.6, 5000)

def forest():
    """Canopy: a kalimba pattern in G, a wooden flute melody, a little shaker. 96 bpm, 32 bars."""
    beat = 60 / 96; bar = 4 * beat
    chords = [['G3', 'B3', 'D4', 'G4'], ['E3', 'G3', 'B3', 'D4'], ['C3', 'G3', 'B3', 'E4'], ['D3', 'A3', 'D4', 'F#4']]
    lows = ['G2', 'E2', 'C2', 'D2']
    pattern = [0, 2, 1, 3, 2, 1, 3, 2]
    mel = [('B4', 0, 1.5), ('A4', 1.5, .5), ('G4', 2, 1), ('D5', 3, 1), ('E5', 4, 2), ('D5', 6, 1), ('B4', 7, 1),
           ('E5', 8, 1.5), ('D5', 9.5, .5), ('B4', 10, 2), ('A4', 12, 3),
           ('B4', 16, 1), ('D5', 17, 1), ('E5', 18, 1), ('G5', 19, 1), ('E5', 20, 2), ('D5', 22, 1), ('B4', 23, .5), ('A4', 23.5, .5),
           ('G4', 24, 1.5), ('A4', 25.5, .5), ('B4', 26, 2), ('A4', 28, 2), ('G4', 30, 2)]
    m = Mix(32 * bar)
    for b in range(32):
        t0 = b * bar; ch = chords[b % 4]
        if b % 4 == 0:
            for n in ch[:3]: m.add(t0, pad(hz(n), 4 * bar - .4, .35, bright=.6, attack=1.5), pan=-.3, send=.5)
        for s, k in enumerate(pattern):
            n = midi(ch[k]) + 12; v = .55 + (.15 if s % 2 == 0 else 0) + .1 * rng.random()
            m.add(human(t0 + s * beat / 2), kalimba(hz(n), .3, v), pan=.35 * np.sin(s), send=.35)
        m.add(t0, marimba(hz(lows[b % 4]), .5, .7), send=.15); m.add(t0 + 2.5 * beat, marimba(hz(midi(lows[b % 4]) + 7), .5, .45), send=.15)
        if 8 <= b < 24:
            for s in range(8): m.add(human(t0 + s * beat / 2, .006), shaker(.9 if s % 2 else .5), pan=.45, send=.1)
            for n, at, d in mel:
                if int(at // 4) == (b - 8) % 8:
                    m.add(human(t0 + (at % 4) * beat), flute(hz(n), d * beat, .75 if b < 16 else .85), pan=-.15, send=.45)
    return m.render(3.0, 5500)

def ocean():
    """Tide: rolling harp arpeggios in A, a slow swelling pad, a few long glass notes. 6/8, 32 bars."""
    e8 = 60 / 52 / 3; bar = 6 * e8
    chords = [['A2', 'E3', 'B3', 'C#4', 'G#4'], ['F#2', 'C#3', 'A3', 'E4', 'G#4'], ['D3', 'A3', 'C#4', 'F#4', 'A4'], ['E3', 'B3', 'E4', 'A4', 'B4']]
    m = Mix(32 * bar)
    mel = [('C#5', 0, 4), ('E5', 4, 2), ('B4', 6, 6), ('A4', 12, 4), ('C#5', 16, 2), ('F#5', 18, 3), ('E5', 21, 3), ('B4', 24, 8)]
    for b in range(32):
        t0 = b * bar; ch = chords[(b // 2) % 4]
        if b % 2 == 0:
            for k, n in enumerate(ch[:4]): m.add(t0, pad(hz(n), 2 * bar - .3, .45, bright=.7, attack=2.2, release=2.), pan=-.4 + k * .27, send=.6)
            m.add(t0, bass(hz(midi(ch[0]) - 12), 2 * bar - .3, .45), send=.1)
        ns = [midi(n) for n in ch] + [midi(n) + 12 for n in ch[1:3]]
        up = b % 2 == 0; seq = ns[:6] if up else ns[1:7][::-1]
        for s in range(6):
            m.add(human(t0 + s * e8, .01), harp(hz(seq[s] + 12), .3, .5 + .15 * (s == 0) + .1 * rng.random()), pan=-.5 + s / 5, send=.45)
        if 8 <= b < 24:
            for n, at, d in mel:
                if int(at // 6) == (b - 8) % 8:
                    m.add(t0 + (at % 6) * e8, bell(hz(n), d * e8, .45, ring=4.), pan=.2, send=.7)
    return m.render(4.0, 4500)

def aurora():
    """Northern Lights: glass bells drifting over a wide, slow pad in E minor. 54 bpm, 20 bars."""
    beat = 60 / 54; bar = 4 * beat
    chords = [['E2', 'B2', 'G3', 'F#4', 'B4'], ['C3', 'G3', 'B3', 'E4', 'F#4'], ['G2', 'D3', 'B3', 'E4'], ['F#2', 'D3', 'A3', 'E4']]
    scale = ['E5', 'F#5', 'G5', 'B5', 'D6', 'E6', 'B4', 'D5']
    m = Mix(20 * bar)
    for b in range(20):
        t0 = b * bar; ch = chords[b % 4]
        for k, n in enumerate(ch): m.add(t0, pad(hz(n), bar - .1, .7, bright=.5, attack=1.6, release=2.2), pan=-.6 + 1.2 * k / (len(ch) - 1), send=.65)
        m.add(t0, pad(hz(midi(ch[2]) + 24), bar, .12, bright=.3, attack=2.5, release=2.5), pan=.5, send=.9)   # shimmer
        for _ in range(rng.integers(1, 4)):
            at = rng.integers(0, 8) * beat / 2; n = scale[rng.integers(0, len(scale))]
            m.add(human(t0 + at), bell(hz(n), 1., .35 + .25 * rng.random(), ring=5.), pan=rng.uniform(-.8, .8), send=.85)
    return m.render(6.0, 3500)

def desert():
    """Dunes: a plucked string over a drone in D Dorian, a hand drum keeping a slow, steady walk. 88 bpm, 32 bars."""
    beat = 60 / 88; bar = 4 * beat
    mel = [('D4', 0, .5), ('E4', .5, .5), ('F4', 1, 1), ('A4', 2, 1), ('G4', 3, 1), ('F4', 4, 1), ('E4', 5, .5), ('D4', 5.5, .5), ('E4', 6, 2),
           ('A4', 8, .5), ('B4', 8.5, .5), ('C5', 9, 1), ('B4', 10, .5), ('A4', 10.5, .5), ('G4', 11, 1), ('A4', 12, 4),
           ('D5', 16, 1), ('C5', 17, .5), ('A4', 17.5, .5), ('G4', 18, 1), ('F4', 19, 1), ('G4', 20, .5), ('A4', 20.5, .5), ('G4', 21, .5), ('F4', 21.5, .5), ('E4', 22, 2),
           ('F4', 24, 1), ('G4', 25, 1), ('A4', 26, 1), ('C5', 27, 1), ('D5', 28, 3)]
    m = Mix(32 * bar)
    for b in range(32):
        t0 = b * bar
        if b % 4 == 0:
            m.add(t0, pad(hz('D2'), 4 * bar - .2, .5, bright=.9, attack=1.2), pan=-.2, send=.4); m.add(t0, pad(hz('A2'), 4 * bar - .2, .35, bright=.9, attack=1.2), pan=.2, send=.4)
        # the low string's figure: D on one and three, the fifth and the octave between
        for at, n, v in ((0, 'D3', .7), (1.5, 'A2', .45), (2, 'D3', .55), (3, 'C3' if b % 4 == 3 else 'A2', .45), (3.5, 'D3', .4)):
            m.add(human(t0 + at * beat), oud(hz(n), .5 * beat, v), pan=-.2, send=.25)
        if 4 <= b < 28:
            for at, f, v in ((0, dum, .9), (1.5, tek, .5), (2, dum, .7), (3, tek, .6), (3.5, tek, .35)): m.add(human(t0 + at * beat, .006), f(v), pan=.25, send=.2)
        if 8 <= b < 24:
            lift = 12 if b >= 16 else 0
            for n, at, d in mel:
                if int(at // 4) == (b - 8) % 8: m.add(human(t0 + (at % 4) * beat), oud(hz(midi(n) + lift), d * beat, .75), pan=.15, send=.35)
    return m.render(2.8, 4500)

def stormy():
    """Rain on Glass: a soft piano in C minor, a low cello under it from the second pass. 64 bpm, 24 bars."""
    beat = 60 / 64; bar = 4 * beat
    chords = [['C3', 'G3', 'Eb4'], ['Ab2', 'Eb3', 'C4'], ['Eb3', 'Bb3', 'G4'], ['Bb2', 'F3', 'D4'], ['C3', 'G3', 'Eb4'], ['Ab2', 'Eb3', 'C4'], ['F2', 'C3', 'Ab3'], ['G2', 'D3', 'B3']]
    mel = [('G4', 0, 2), ('Eb5', 2, 1), ('D5', 3, 1), ('C5', 4, 3), ('Bb4', 8, 1.5), ('C5', 9.5, .5), ('D5', 10, 1), ('Eb5', 11, 1), ('D5', 12, 4),
           ('G5', 16, 1.5), ('F5', 17.5, .5), ('Eb5', 18, 1), ('D5', 19, 1), ('C5', 20, 2), ('Eb5', 22, 1), ('G4', 23, 1), ('Ab4', 24, 2), ('C5', 26, 1), ('F5', 27, 1), ('D5', 28, 3)]
    m = Mix(24 * bar)
    for b in range(24):
        t0 = b * bar; ch = chords[b % 8]; cyc = b // 8
        for k, at in enumerate((0, 1, 2, 3)):
            n = ch[[0, 1, 2, 1][k]]; m.add(human(t0 + at * beat), piano(hz(n), 1.1 * beat, .45 + .1 * (k == 0)), pan=-.25, send=.4)
        low = midi(ch[0]) - (12 if midi(ch[0]) > 45 else 0)
        if cyc >= 1: m.add(t0, cello(hz(low), bar - .2, .55), pan=.1, send=.4)
        if cyc >= 1:
            for n, at, d in mel:
                if int(at // 4) == b % 8: m.add(human(t0 + (at % 4) * beat), piano(hz(n), d * beat, .55 + .1 * rng.random()), pan=.2, send=.5)
    return m.render(3.4, 4000)

TRACKS = {'space': space, 'city': city, 'forest': forest, 'ocean': ocean, 'aurora': aurora, 'desert': desert, 'stormy': stormy}

# ---------- ambient loops ----------
def seamless(make, sec, xf=3.):
    """Renders something 'sec + xf' long (stereo) and crossfades its last xf seconds over its first."""
    x = make(sec + xf); L = int(sec * SR); X = int(xf * SR); k = np.linspace(0, np.pi / 2, X)
    out = x[:, :L].copy(); out[:, :X] = x[:, :X] * np.sin(k) + x[:, L:L + X] * np.cos(k); return out
def slow(sec, period, ph=0.):
    """A slow wobble 0..1 that repeats exactly over the loop (its period divides the loop length)."""
    t = T(sec); return .5 + .5 * np.sin(2 * np.pi * t / period + ph)
def stereo(fn, sec): return np.array([fn(sec), fn(sec)])

def amb_space(sec):
    t = T(sec); hum = np.zeros_like(t)
    for f, a in ((55, 1), (55.4, .8), (82.5, .4), (110.3, .2)): hum += a * np.sin(2 * np.pi * f * t)
    air = stereo(lambda s: lowpass(noise(s), 300, 2), sec) * .6
    return (hum * .1 * (.7 + .3 * slow(sec, 20)) + air * (.6 + .4 * slow(sec, 13.3))) * .5

def amb_city(sec):
    bed = stereo(lambda s: lowpass(noise(s), 220, 2) + .25 * band(noise(s), 300, 1500), sec)
    bed *= .6 + .4 * slow(sec, 20.)
    t = T(sec)
    for at, dur, pan in ((4, 5, -1), (16, 6, 1), (29, 4.5, -1)):   # cars passing far off, one side to the other
        g = np.exp(-((t - at - dur / 2) / (dur / 4)) ** 2); car = band(noise(sec), 120, 1400) * g * .9
        p = np.clip((t - at) / dur, 0, 1) if pan > 0 else 1 - np.clip((t - at) / dur, 0, 1)
        bed[0] += car * np.cos(p * np.pi / 2); bed[1] += car * np.sin(p * np.pi / 2)
    return bed

def chirp(f0, f1, dur, vib=0.):
    t = T(dur); f = f0 + (f1 - f0) * (t / dur); f = f * (1 + vib * np.sin(2 * np.pi * 28 * t))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / dur) ** 2

def amb_forest_day(sec):
    t = T(sec); out = stereo(lambda s: lowpass(noise(s), 700, 2), sec) * .5 * (.5 + .5 * slow(sec, 13.3, 1))   # breeze in the leaves
    out += stereo(lambda s: band(noise(s), 2000, 6000), sec) * .04 * slow(sec, 10, 2)
    songs = [  # little phrases: each a list of (start, from Hz, to Hz, length)
        [(0, 3800, 3200, .12), (.16, 3800, 3200, .12), (.32, 4200, 2800, .25)],
        [(0, 2900, 3500, .18), (.25, 3500, 2600, .3)],
        [(i * .07, 5200, 4800, .05) for i in range(9)],
        [(0, 2400, 2400, .2), (.24, 3000, 3000, .2), (.5, 2700, 2000, .35)]]
    at = .8
    while at < sec - 2:
        s = songs[rng.integers(0, len(songs))]; pan = rng.uniform(-.8, .8); g = rng.uniform(.04, .1)
        for st, f0, f1, d in s:
            c = chirp(f0, f1, d, vib=.01); i = int((at + st) * SR)
            if i + len(c) < len(t): out[0, i:i + len(c)] += c * g * (1 - pan) / 2; out[1, i:i + len(c)] += c * g * (1 + pan) / 2
        at += rng.uniform(1.4, 4.)
    return out

def amb_forest_night(sec):
    t = T(sec); out = stereo(lambda s: lowpass(noise(s), 500, 2), sec) * .35 * (.5 + .5 * slow(sec, 20.))
    for f, rate, pan, g, ph in ((4400, 1.6, -.6, .05, 0), (4700, 1.25, .5, .04, .3), (4150, 2.1, .1, .025, .7)):   # crickets
        pulse = (np.sin(2 * np.pi * 30 * t) > .3).astype(float)                       # 30 pulses a second within a chirp
        gate = ((t * rate + ph) % 1 < .14).astype(float); gate = lowpass(gate, 60, 1)  # chirps, a few times a second
        c = np.sin(2 * np.pi * f * t) * lowpass(pulse, 400, 1) * gate * g
        out[0] += c * (1 - pan) / 2; out[1] += c * (1 + pan) / 2
    return out

def amb_ocean(sec):
    t = T(sec); out = stereo(lambda s: lowpass(noise(s), 500, 1), sec) * .25
    n = int(round(sec / 8.)); per = sec / n
    for k in range(n + 1):   # waves, each rising, breaking and washing out
        at = k * per + rng.uniform(-.8, .8); x = t - at
        env = np.where(x < 0, np.exp(-(x / 1.8) ** 2), np.exp(-x / 2.6)) * (.7 + .3 * rng.random())
        hiss = band(noise(sec), 400, 5000) * np.where(x < 0, 0, np.exp(-x / 1.4)) * .5
        w = lowpass(noise(sec), 700, 1) * env + hiss * env
        pan = rng.uniform(-.4, .4); out[0] += w * (1 - pan) / 2; out[1] += w * (1 + pan) / 2
    return out

def amb_wind(sec, lo, hi, gust, hiss):
    out = np.zeros((2, int(sec * SR)))
    for ch in range(2):
        a = band(noise(sec), lo, hi * .6); b = band(noise(sec), lo * 1.6, hi)
        s = slow(sec, gust, ch * .9); out[ch] = a * (1 - s) + b * s * 1.2
        out[ch] *= .45 + .55 * slow(sec, gust * 1.5, 1 + ch)
        out[ch] += highpass(noise(sec), 5000) * hiss * slow(sec, gust, ch * .9 + .5)
    return out

def amb_aurora(sec): return amb_wind(sec, 180, 900, 10., .02)
def amb_desert(sec): return amb_wind(sec, 120, 1300, 6.67, .08)

def amb_rain(sec):
    out = stereo(lambda s: highpass(lowpass(noise(s), 7000, 1), 500, 1), sec) * .35
    out += stereo(lambda s: lowpass(noise(s), 250, 2), sec) * .3
    t = T(sec); n = int(sec * 40)
    for _ in range(n):   # drops close by
        i = rng.integers(0, len(t) - 600); d = band(rng.standard_normal(600), 1500, 7000) * np.exp(-np.arange(600) / 60) * rng.uniform(.05, .25)
        ch = rng.integers(0, 2); out[ch, i:i + 600] += d
    return out

def thunder(k):
    sec = 7.; t = T(sec); env = np.zeros_like(t)
    for _ in range(4 + k): at = rng.uniform(.2, 2.5); env += rng.uniform(.4, 1) * np.exp(-((t - at) / rng.uniform(.25, .6)) ** 2)
    env += .6 * np.exp(-np.clip(t - 1, 0, None) / 1.8) * (t > .3)
    env *= fade(len(t), .25, 1.5)
    x = np.array([lowpass(noise(sec), 160, 2), lowpass(noise(sec), 160, 2)]) * env + np.array([band(noise(sec), 150, 600)] * 2) * env * .15
    return x

AMBIENT = {'amb-space': amb_space, 'amb-city': amb_city, 'amb-forest-day': amb_forest_day, 'amb-forest-night': amb_forest_night,
           'amb-ocean': amb_ocean, 'amb-aurora': amb_aurora, 'amb-desert': amb_desert, 'amb-stormy': amb_rain}

if __name__ == '__main__':
    want = set(sys.argv[1:])
    for name, f in TRACKS.items():
        if not want or name in want: write('music-' + name, master(f(), -19), q=3)
    for name, f in AMBIENT.items():
        if not want or name in want or name[4:] in want: write(name, master(seamless(f, 40.), -27), q=2)
    if not want or 'thunder' in want:
        for k in range(3): write(f'thunder-{k + 1}', master(thunder(k), -24), q=2)
