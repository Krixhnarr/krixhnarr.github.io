// Procedural soundtrack, synthesised live with Web Audio — no audio files.
// Three looping themes (day adventure, night lullaby, battle), victory and
// defeat stingers, and quiet nature ambience (birdsong by day, crickets by
// night). A small look-ahead scheduler keeps notes sample-accurate.

let ctx = null;
let master = null;   // everything the soundtrack makes
let musicBus = null; // themes + stingers (ducked while scanning / speaking)
let ambBus = null;   // birds & crickets
let delay = null;    // echo send for leads and bells
let noise = null;
let enabled = true;
let volume = 0.6;
let ducked = false;
let unlocked = false;
let scene = null;    // desired theme: 'day' | 'night' | 'battle'
let track = null;    // theme currently playing
let step = 0;
let nextTime = 0;
let timer = null;
let ambTimer = null;
let stingerUntil = 0;

const mtof = (m) => 440 * 2 ** ((m - 69) / 12);

export function context() {
  if (!ctx) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
  }
  return ctx;
}

function build() {
  if (master) return;
  const c = context();
  master = c.createGain();
  const comp = c.createDynamicsCompressor();
  comp.threshold.value = -18;
  comp.ratio.value = 3;
  master.connect(comp).connect(c.destination);
  musicBus = c.createGain();
  musicBus.gain.value = 0;
  musicBus.connect(master);
  ambBus = c.createGain();
  ambBus.gain.value = 0;
  ambBus.connect(master);
  // echo: delay -> feedback -> back into the music bus
  delay = c.createDelay(1);
  delay.delayTime.value = 0.28;
  const fb = c.createGain();
  fb.gain.value = 0.32;
  const wet = c.createGain();
  wet.gain.value = 0.35;
  const tone = c.createBiquadFilter();
  tone.type = 'lowpass';
  tone.frequency.value = 2600;
  delay.connect(tone).connect(fb).connect(delay);
  tone.connect(wet).connect(musicBus);
  // one second of white noise for drums, wind and crickets
  noise = c.createBuffer(1, c.sampleRate, c.sampleRate);
  const d = noise.getChannelData(0);
  for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
}

// ---------------------------------------------------------------- instruments
function env(g, t, peak, attack, decay, hold = 0) {
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(peak, t + attack);
  if (hold) g.gain.setValueAtTime(peak, t + attack + hold);
  g.gain.exponentialRampToValueAtTime(0.0001, t + attack + hold + decay);
}
function osc(type, freq, t, dur, dest) {
  const o = ctx.createOscillator();
  o.type = type;
  o.frequency.setValueAtTime(freq, t);
  o.connect(dest);
  o.start(t);
  o.stop(t + dur + 0.05);
  return o;
}
function voice(peak, attack, decay, hold, t, send = 0) {
  const g = ctx.createGain();
  env(g, t, peak, attack, decay, hold);
  g.connect(musicBus);
  if (send) { const s = ctx.createGain(); s.gain.value = send; g.connect(s).connect(delay); }
  return g;
}

const inst = {
  marimba(m, t, v = 0.16) {
    const g = voice(v, 0.004, 0.45, 0, t, 0.35);
    osc('sine', mtof(m), t, 0.5, g);
    const g2 = voice(v * 0.35, 0.002, 0.08, 0, t);
    osc('sine', mtof(m) * 4, t, 0.1, g2);
  },
  pluck(m, t, v = 0.05) {
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.setValueAtTime(3200, t);
    f.frequency.exponentialRampToValueAtTime(500, t + 0.18);
    const g = voice(v, 0.003, 0.2, 0, t);
    f.connect(g);
    osc('square', mtof(m), t, 0.25, f);
  },
  bass(m, t, len, v = 0.18, type = 'triangle') {
    const g = voice(v, 0.01, 0.12, Math.max(0.02, len - 0.15), t);
    if (type === 'sawtooth') {
      const f = ctx.createBiquadFilter();
      f.type = 'lowpass';
      f.frequency.value = 700;
      f.connect(g);
      osc(type, mtof(m), t, len, f);
    } else osc(type, mtof(m), t, len, g);
  },
  bell(m, t, v = 0.09) {
    const g = voice(v, 0.005, 1.8, 0, t, 0.6);
    osc('sine', mtof(m), t, 2, g);
    const g2 = voice(v * 0.25, 0.005, 0.9, 0, t);
    osc('triangle', mtof(m) * 2, t, 1, g2);
  },
  pad(ms, t, len, v = 0.035) {
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.value = 900;
    const g = voice(v, len * 0.35, len * 0.5, len * 0.2, t);
    f.connect(g);
    ms.forEach((m) => [-6, 6].forEach((cents) => { const o = osc('sawtooth', mtof(m), t, len + 0.2, f); o.detune.value = cents; }));
  },
  lead(m, t, len, v = 0.045) {
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.value = 2400;
    const g = voice(v, 0.005, 0.1, Math.max(0.01, len - 0.1), t, 0.25);
    f.connect(g);
    osc('square', mtof(m), t, len, f);
  },
  kick(t, v = 0.5) {
    const g = voice(v, 0.002, 0.28, 0, t);
    const o = osc('sine', 140, t, 0.3, g);
    o.frequency.exponentialRampToValueAtTime(42, t + 0.22);
  },
  snare(t, v = 0.16) {
    const src = ctx.createBufferSource();
    src.buffer = noise;
    const f = ctx.createBiquadFilter();
    f.type = 'bandpass';
    f.frequency.value = 1800;
    const g = voice(v, 0.002, 0.16, 0, t);
    src.connect(f).connect(g);
    src.start(t, Math.random() * 0.5, 0.2);
  },
  hat(t, v = 0.05, len = 0.04) {
    const src = ctx.createBufferSource();
    src.buffer = noise;
    const f = ctx.createBiquadFilter();
    f.type = 'highpass';
    f.frequency.value = 7000;
    const g = voice(v, 0.001, len, 0, t);
    src.connect(f).connect(g);
    src.start(t, Math.random() * 0.5, len + 0.02);
  },
};

// ---------------------------------------------------------------- themes
const _ = null;
// Day: bright C-major adventure, 104 bpm, 8th-note grid, 8 bars (A + B).
const DAY_CHORDS = [[48, 60, 64, 67], [45, 57, 60, 64], [41, 53, 57, 60], [43, 55, 59, 62]];
const DAY_MEL = [
  [76, 79, 84, 79, 81, 79, 76, _], [72, 76, 81, 76, 79, 76, 72, _], [81, 84, 77, 81, 79, 77, 76, 74], [74, 79, 83, 79, 86, _, 83, _],
  [79, _, 76, 79, 84, _, 83, 81], [81, _, 76, 81, 84, 83, 81, 79], [77, 81, 84, 86, 84, 81, 77, 81], [79, 83, 86, 83, 79, _, _, _],
];
// Night: calm A-minor pentatonic lullaby, 70 bpm, pads + bells.
const NIGHT_CHORDS = [[45, 57, 60, 64], [41, 53, 57, 60], [48, 55, 60, 64], [43, 55, 59, 62]];
const NIGHT_MEL = [[76, _, _, 81, _, 79, _, _], [_, _, 72, _, 74, _, 76, _], [79, _, _, 76, _, 74, _, 72], [74, _, _, _, 76, _, _, _]];
// Battle: driving E-minor, 144 bpm, 16th-note grid.
const BATTLE_ROOTS = [40, 40, 36, 38];
const BATTLE_MEL = [
  [76, 76, 79, 76, 83, 81, 79, 78], [76, _, 79, 81, 83, _, 86, 83], [84, 83, 81, 79, 76, 79, 81, _], [86, 84, 83, 81, 78, _, 74, _],
];

const TRACKS = {
  day: {
    bpm: 104, steps: 8, bars: 8,
    play(s, t, sd) {
      const bar = Math.floor(s / 8) % 8;
      const i = s % 8;
      const ch = DAY_CHORDS[bar % 4];
      if (i === 0) inst.bass(ch[0], t, sd * 3, 0.16);
      if (i === 3) inst.bass(ch[0] + 12, t, sd * 0.9, 0.1);
      if (i === 4) inst.bass(ch[0] + 7, t, sd * 3, 0.12);
      inst.pluck(ch[1 + (i % 3)], t, i % 2 ? 0.03 : 0.045);
      const n = DAY_MEL[bar][i];
      if (n) inst.marimba(n - 12, t);
      if (i % 4 === 0) inst.kick(t, 0.28);
      if (i % 2 === 1) inst.hat(t, 0.035);
      if (i === 4) inst.snare(t, 0.07);
    },
  },
  night: {
    bpm: 70, steps: 8, bars: 4,
    play(s, t, sd) {
      const bar = Math.floor(s / 8) % 4;
      const i = s % 8;
      const ch = NIGHT_CHORDS[bar];
      if (i === 0) { inst.pad(ch.slice(1), t, sd * 8.2); inst.bass(ch[0], t, sd * 7, 0.12, 'sine'); }
      const n = NIGHT_MEL[bar][i];
      if (n) inst.bell(n, t);
      if (i === 6 && bar % 2) inst.bell(ch[3] + 12, t, 0.03);
    },
  },
  battle: {
    bpm: 144, steps: 16, bars: 4,
    play(s, t, sd) {
      const bar = Math.floor(s / 16) % 4;
      const i = s % 16;
      const root = BATTLE_ROOTS[bar];
      if (i % 2 === 0) inst.bass(root + (i % 8 === 6 ? 12 : 0), t, sd * 1.6, 0.15, 'sawtooth');
      if (i % 4 === 0) inst.kick(t, 0.45);
      if (i === 4 || i === 12) inst.snare(t, 0.14);
      inst.hat(t, i % 4 === 2 ? 0.05 : 0.025);
      if (i % 2 === 0) { const n = BATTLE_MEL[bar][i / 2]; if (n) inst.lead(n - 12, t, sd * 1.7); }
    },
  },
};

function tick() {
  if (!track || !ctx) return;
  const sd = 60 / track.bpm / (track.steps / 4);
  while (nextTime < ctx.currentTime + 0.15) {
    if (nextTime >= stingerUntil) track.play(step, nextTime, sd);
    nextTime += sd;
    step++;
  }
}

function level() { return enabled ? volume * (ducked ? 0.25 : 1) : 0; }
function fadeBus(bus, to, secs = 0.6) {
  if (!bus) return;
  const now = ctx.currentTime;
  bus.gain.cancelScheduledValues(now);
  bus.gain.setValueAtTime(bus.gain.value, now);
  bus.gain.linearRampToValueAtTime(to, now + secs);
}

function startTrack(name) {
  if (track === TRACKS[name]) return;
  const go = () => {
    track = TRACKS[name];
    step = 0;
    nextTime = Math.max(ctx.currentTime + 0.05, stingerUntil);
    if (!timer) timer = setInterval(tick, 25);
    fadeBus(musicBus, level() * 0.85, 0.8);
  };
  if (track) { fadeBus(musicBus, 0, 0.35); track = null; setTimeout(go, 380); } else go();
}

// ---------------------------------------------------------------- ambience
function chirp(t) {
  const n = 2 + Math.floor(Math.random() * 4);
  const base = 2600 + Math.random() * 1600;
  for (let k = 0; k < n; k++) {
    const s = t + k * (0.09 + Math.random() * 0.05);
    const g = ctx.createGain();
    env(g, s, 0.05, 0.005, 0.07);
    g.connect(ambBus);
    const o = ctx.createOscillator();
    o.type = 'sine';
    o.frequency.setValueAtTime(base, s);
    o.frequency.exponentialRampToValueAtTime(base * (1.3 + Math.random() * 0.4), s + 0.05);
    o.connect(g);
    o.start(s);
    o.stop(s + 0.1);
  }
}
function cricket(t) {
  const pulses = 3 + Math.floor(Math.random() * 3);
  for (let k = 0; k < pulses; k++) {
    const s = t + k * 0.055;
    const g = ctx.createGain();
    env(g, s, 0.018, 0.004, 0.03);
    g.connect(ambBus);
    const o = ctx.createOscillator();
    o.type = 'sine';
    o.frequency.value = 4400 + Math.random() * 200;
    o.connect(g);
    o.start(s);
    o.stop(s + 0.05);
  }
}
function ambience() {
  clearTimeout(ambTimer);
  if (!ctx || !enabled || !unlocked) return;
  const night = scene === 'night' || (scene === 'battle' && document.documentElement.dataset.time === 'night');
  if (scene !== 'battle') (night ? cricket : chirp)(ctx.currentTime + 0.05);
  ambTimer = setTimeout(ambience, night ? 900 + Math.random() * 1800 : 2500 + Math.random() * 5000);
}

// ---------------------------------------------------------------- iPhone audio
// iOS mutes Web Audio when the ring/silent switch is on. Asking for a
// "playback" audio session (iOS 17+), or playing a looping silent <audio>
// tag on older iOS, lets the soundtrack play like a music app does.
let silentTag = null;
function silentWavURL() {
  const n = 4000;
  const buf = new ArrayBuffer(44 + n);
  const v = new DataView(buf);
  const w = (o, str) => [...str].forEach((ch, i) => v.setUint8(o + i, ch.charCodeAt(0)));
  w(0, 'RIFF'); v.setUint32(4, 36 + n, true); w(8, 'WAVE'); w(12, 'fmt ');
  v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, 8000, true); v.setUint32(28, 8000, true); v.setUint16(32, 1, true); v.setUint16(34, 8, true);
  w(36, 'data'); v.setUint32(40, n, true);
  for (let i = 0; i < n; i++) v.setUint8(44 + i, 128);
  return URL.createObjectURL(new Blob([buf], { type: 'audio/wav' }));
}
function iosPlayback() {
  try { if (navigator.audioSession) { navigator.audioSession.type = 'playback'; return; } } catch { /* ignore */ }
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  if (!ios || silentTag) return;
  silentTag = document.createElement('audio');
  silentTag.setAttribute('x-webkit-airplay', 'deny');
  silentTag.setAttribute('playsinline', '');
  silentTag.loop = true;
  silentTag.src = silentWavURL();
  silentTag.play().catch(() => { silentTag = null; });
}

// ---------------------------------------------------------------- public API
// Browsers only start audio from a finished tap or click; call this from
// those events (it's cheap to call again). Resolves true once sound is live.
export function unlock() {
  const c = context();
  if (!c) return Promise.resolve(false);
  build();
  if (unlocked && c.state === 'running') return Promise.resolve(true);
  iosPlayback();
  try { // a 1-sample silent sound started inside the gesture wakes older iOS
    const src = c.createBufferSource();
    src.buffer = c.createBuffer(1, 1, 22050);
    src.connect(c.destination);
    src.start(0);
  } catch { /* ignore */ }
  const ready = c.state === 'running' ? Promise.resolve() : c.resume();
  return Promise.resolve(ready).then(() => {
    if (c.state !== 'running') return false;
    if (!unlocked) {
      unlocked = true;
      liveAt = performance.now();
      fadeBus(ambBus, enabled ? volume * 0.8 : 0, 1);
      if (scene && enabled) startTrack(scene);
      ambience();
    }
    return true;
  }).catch(() => false);
}
let liveAt = 0;
// When sound first came alive (so the tap that started it isn't also read as a toggle).
export const liveSince = () => liveAt;
export const isPlaying = () => !!(unlocked && enabled && ctx && ctx.state === 'running');

export function setScene(name) {
  scene = name;
  if (unlocked && enabled) startTrack(name);
}

export function setEnabled(on) {
  enabled = on;
  if (!unlocked) return;
  if (on) { track = null; startTrack(scene || 'day'); fadeBus(ambBus, volume * 0.8); ambience(); } else {
    fadeBus(musicBus, 0, 0.3);
    fadeBus(ambBus, 0, 0.3);
    setTimeout(() => { if (!enabled) { track = null; clearTimeout(ambTimer); } }, 350);
  }
}

export function setVolume(v) {
  volume = Math.max(0, Math.min(1, v));
  if (!unlocked || !enabled) return;
  fadeBus(musicBus, level() * 0.85, 0.15);
  fadeBus(ambBus, volume * 0.8, 0.15);
}

// Softens the music under scans and narration.
export function duck(on) {
  ducked = on;
  if (unlocked && enabled && track) fadeBus(musicBus, level() * 0.85, on ? 0.25 : 0.8);
}

// A short jingle that replaces the current theme; the next setScene() resumes music.
export function stinger(kind) {
  if (!unlocked || !enabled) return;
  const t = ctx.currentTime + 0.05;
  fadeBus(musicBus, level() * 0.6, 0.05);
  if (kind === 'win') {
    [72, 76, 79, 84].forEach((m, i) => inst.lead(m, t + i * 0.12, 0.11, 0.06));
    [72, 76, 79, 84].forEach((m) => inst.lead(m, t + 0.5, 0.9, 0.035));
    inst.bass(48, t + 0.5, 0.9, 0.18);
    inst.kick(t + 0.5, 0.4);
    stingerUntil = t + 1.6;
  } else {
    [67, 63, 60, 55].forEach((m, i) => inst.bell(m, t + i * 0.28, 0.08));
    inst.bass(43, t + 0.84, 1.2, 0.12, 'sine');
    stingerUntil = t + 2.2;
  }
  track = null; // the theme stops under the jingle
}

export function suspend(on) {
  if (!ctx) return;
  if (on) { ctx.suspend(); if (silentTag) silentTag.pause(); } else if (unlocked) { ctx.resume().catch(() => {}); if (silentTag) silentTag.play().catch(() => {}); }
}
