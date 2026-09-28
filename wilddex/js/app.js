import { SETS, ENTRIES, BY_KEY, RARITY } from './dex-data.js';
import { LABELS } from './labels.js';
import * as store from './store.js';
import { loadModel, classify, interpret, isReady, spoofCheck } from './classifier.js';
import { TYPES, TYPE_IDS, AFFINITY, glyph } from './affinity.js';
import * as game from './game.js';
import { detectFrame } from './liveness.js';
import { recordSweep, analyseSweep } from './parallax.js';
import { renderDots, startTwinkle, dotSVG } from './dotmatrix.js';
import * as battle from './battle.js';
import * as loot from './loot.js';
import * as music from './music.js';
import * as FX from './fx.js';
import * as account from './account.js';

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pad = (n, w = 3) => String(n).padStart(w, '0');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const title = (s) => s.replace(/(^|[\s-])([a-z])/g, (m, a, b) => a + b.toUpperCase());
const snake = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
const isoDate = (ts) => game.today(new Date(ts));
const state = () => store.getState();
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const artURL = (e) => `art/${[...e.e].map((c) => c.codePointAt(0).toString(16).padStart(4, '0')).filter((c) => c !== 'fe0f').join('-')}.webp`;
const shortSize = (e) => e.z.split(/ at | with | body| tall| long|;/)[0];
const shortDiet = (e) => e.d.split(/ \(|,| ·| and | & /)[0];

const RANKS = [
  [0, 'Rookie'], [1, 'Novice Collector'], [10, 'Explorer'], [25, 'Field Researcher'],
  [50, 'Naturalist'], [100, 'Wildlife Expert'], [175, 'Master Collector'], [ENTRIES.length, 'WildDex Champion'],
];

// ---------------------------------------------------------------- sound & voice
let audio;
function tone(freq, at, dur, type = 'square', vol = 0.035) {
  if (!state().settings.sound) return;
  try {
    audio = audio || music.context();
    if (!audio) return;
    if (audio.state !== 'running') audio.resume().catch(() => {});
    const t = audio.currentTime + at;
    const o = audio.createOscillator();
    const g = audio.createGain();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g).connect(audio.destination);
    o.start(t);
    o.stop(t + dur + 0.05);
  } catch { /* no audio */ }
}
const sfx = {
  scan: () => { for (let i = 0; i < 10; i++) tone(1200 + (i % 3) * 400, i * 0.08, 0.03, 'square', 0.02); },
  charge: () => { for (let i = 0; i < 8; i++) tone(300 + i * 90, i * 0.06, 0.05, 'sawtooth', 0.018); },
  reveal: (r) => {
    const notes = [523.25, 659.25, 783.99, 1046.5, 1318.5, 1568];
    notes.slice(0, 3 + r).forEach((f, i) => tone(f, i * 0.07, 0.2, 'square', 0.03));
    tone(notes[2 + r] * 1.5, 0.12 + r * 0.08, 0.6, 'triangle', 0.05);
    if (r >= 3) [1568, 1760, 2093, 2349, 2637].forEach((f, i) => tone(f, 0.5 + i * 0.06, 0.25, 'triangle', 0.035));
    if (r === 4) [523.25, 659.25, 783.99].forEach((f) => tone(f, 0.9, 1.2, 'sine', 0.05));
  },
  again: () => { tone(880, 0, 0.08); tone(1320, 0.09, 0.14); },
  coin: () => { tone(988, 0, 0.06, 'square', 0.03); tone(1319, 0.06, 0.18, 'square', 0.03); },
  fail: () => { tone(220, 0, 0.14, 'sawtooth', 0.03); tone(165, 0.13, 0.26, 'sawtooth', 0.03); },
  click: () => tone(1800, 0, 0.02, 'square', 0.015),
  lock: () => { tone(1568, 0, 0.05, 'square', 0.03); tone(2093, 0.06, 0.12, 'square', 0.03); },
  grade: (g) => { const n = { S: 4, A: 3, B: 2, C: 1 }[g]; for (let i = 0; i < n; i++) tone(660 * 1.26 ** i, i * 0.07, 0.12, 'square', 0.03); tone(180, 0, 0.18, 'sine', 0.08); },
  holo: () => [1319, 1568, 1976, 2349, 2637, 3136].forEach((f, i) => tone(f, i * 0.05, 0.3, 'triangle', 0.03)),
  hit: (m = 1) => { tone(m > 1 ? 140 : 110, 0, 0.12, 'sawtooth', 0.05); tone(m > 1 ? 420 : 300, 0.02, 0.06, 'square', 0.03); },
  guard: () => { tone(520, 0, 0.08, 'triangle', 0.04); tone(780, 0.06, 0.1, 'triangle', 0.03); },
  overdrive: () => { for (let i = 0; i < 12; i++) tone(200 + i * 110, i * 0.03, 0.05, 'sawtooth', 0.025); },
  faint: () => { for (let i = 0; i < 5; i++) tone(500 - i * 70, i * 0.07, 0.09, 'square', 0.03); },
  win: () => [523.25, 659.25, 783.99, 1046.5, 783.99, 1046.5].forEach((f, i) => tone(f, i * 0.11, 0.22, 'square', 0.035)),
  lose: () => [392, 349.23, 311.13, 261.63].forEach((f, i) => tone(f, i * 0.16, 0.3, 'triangle', 0.04)),
  levelup: () => { [523.25, 659.25, 783.99, 1046.5, 1318.5].forEach((f, i) => tone(f, i * 0.09, 0.25, 'square', 0.035)); [1046.5, 1318.5, 1568].forEach((f) => tone(f, 0.55, 0.9, 'triangle', 0.04)); },
  crack: (n = 1) => { tone(90 + n * 30, 0, 0.1, 'sawtooth', 0.06); tone(900 + n * 200, 0.02, 0.05, 'square', 0.025); },
};

// Phone vibration, when the phone supports it and the player hasn't turned it off.
function buzz(pattern) {
  if (state().settings.haptics === false || !navigator.vibrate) return;
  try { navigator.vibrate(pattern); } catch { /* ignore */ }
}

function speak(text) {
  if (!state().settings.voice || !('speechSynthesis' in window)) return;
  try {
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    const voices = speechSynthesis.getVoices();
    u.voice = voices.find((v) => /en-GB/i.test(v.lang)) || voices.find((v) => /^en/i.test(v.lang)) || null;
    u.rate = 1.04;
    u.pitch = 0.8;
    music.duck(true);
    u.onend = u.onerror = () => music.duck(false);
    speechSynthesis.speak(u);
  } catch { /* ignore */ }
}
const stopSpeaking = () => { try { speechSynthesis.cancel(); } catch { /* ignore */ } music.duck(false); };

// ---------------------------------------------------------------- text effects
const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&*<>/\\=+';
function decode(el, text = el.dataset.text || el.textContent, dur = 600) {
  el.dataset.text = text;
  if (reducedMotion()) { el.textContent = text; return; }
  const start = performance.now();
  const step = (now) => {
    const p = Math.min(1, (now - start) / dur);
    const n = Math.floor(p * text.length);
    let out = text.slice(0, n);
    for (let i = n; i < text.length; i++) out += /\s/.test(text[i]) ? text[i] : GLYPHS[(Math.random() * GLYPHS.length) | 0];
    el.textContent = out;
    if (p < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

function typeOut(el, text, cps = 70) {
  const wrap = el.closest('.dex-text');
  if (reducedMotion()) { el.textContent = text; wrap?.classList.add('done'); return; }
  let i = 0;
  const tick = () => {
    if (!el.isConnected) return;
    i = Math.min(text.length, i + 2);
    el.textContent = text.slice(0, i);
    if (i < text.length) setTimeout(tick, 2000 / cps);
    else wrap?.classList.add('done');
  };
  tick();
}

// ---------------------------------------------------------------- terminal log
const term = $('#term');
// The scanner status line shows only the newest message, without log prefixes.
const clean = (html) => html.replace(/^(\[(ok|err|warn|\.\.)\]|&gt;)\s*/, '');
function log(html, cls = '') {
  const line = document.createElement('div');
  if (cls) line.className = cls;
  line.innerHTML = clean(html);
  term.appendChild(line);
  while (term.children.length > 6) term.firstChild.remove();
  return line;
}
const idle = () => log('Ready · point at a real animal');
const asciiBar = (p, n = 12) => '█'.repeat(Math.round(p * n)) + '░'.repeat(n - Math.round(p * n));

// ---------------------------------------------------------------- UI helpers
let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 2800);
}

function flash(rarity = 0) {
  const f = document.createElement('div');
  f.className = `flash${rarity === 4 ? ' gold' : rarity === 5 ? ' holo' : ''}`;
  document.body.appendChild(f);
  setTimeout(() => f.remove(), 450);
}

// Floating reward numbers ("+25◆") that rise from where they were earned.
function popGain(text, from, { cls = '', delay = 0 } = {}) {
  if (reducedMotion()) return;
  setTimeout(() => {
    const r = (from && from.getBoundingClientRect && from.getBoundingClientRect()) || { left: innerWidth / 2 - 20, top: innerHeight / 2, width: 40, height: 0 };
    const el = document.createElement('div');
    el.className = `pop ${cls}`;
    el.textContent = text;
    el.style.left = `${r.left + r.width / 2}px`;
    el.style.top = `${r.top + r.height / 2}px`;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 1300);
  }, delay);
}

// Counts a number up (or down) instead of snapping to it.
function countTo(el, to) {
  const from = Number(el.dataset.v ?? el.textContent) || 0;
  el.dataset.v = to;
  if (from === to || reducedMotion()) { el.textContent = to; return; }
  const start = performance.now();
  const dur = Math.min(900, 250 + Math.abs(to - from) * 12);
  const step = (now) => {
    const p = Math.min(1, (now - start) / dur);
    el.textContent = Math.round(from + (to - from) * (1 - (1 - p) ** 3));
    if (p < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

// ---- operator XP & level-ups
const levelUps = [];
// Grants XP; each level crossed pays credits + a free supply crate, shown once the current sheet closes.
function earnXP(amount) {
  if (!amount) return null;
  const s = state();
  const r = game.grantXP(s, amount);
  for (let L = r.before + 1; L <= r.after; L++) {
    const credits = game.levelUpCredits(L);
    s.shards = (s.shards || 0) + credits;
    loot.locker(s).crates++;
    levelUps.push({ level: L, credits });
  }
  return r;
}
function flushLevelUps() {
  if (!levelUps.length || !$('#sheet').hidden || !$('#levelup').hidden) return;
  const ups = levelUps.splice(0);
  const last = ups[ups.length - 1];
  const credits = ups.reduce((a, u) => a + u.credits, 0);
  const box = $('#levelup');
  box.innerHTML = `<div class="lu-rays" aria-hidden="true"></div>
    <div class="lu-body">
      <span class="dot-text lu-title"><span class="sr">Level up</span>${dotSVG('LEVEL UP')}</span>
      <div class="lu-level"><small>operator level</small><b>${last.level}</b></div>
      <div class="lu-rewards">
        <span class="gain hl">+${credits}◆ credits</span>
        <span class="gain hl">+${ups.length} supply crate${ups.length > 1 ? 's' : ''}</span>
      </div>
      <p class="lu-note">Open crates in <b>ID → Supply</b> for new card frames and titles.</p>
      <button type="button" class="btn primary" data-lu-close>Continue</button>
    </div>`;
  box.hidden = false;
  sfx.levelup();
  FX.confetti(110);
  buzz([60, 50, 60, 50, 220]);
  const stage = $('.lu-body', box);
  burst(stage, 3);
  setTimeout(() => burst(stage, 4), 280);
  $('[data-lu-close]', box).focus({ preventScroll: true });
  refreshAll(true);
}
$('#levelup').addEventListener('click', (ev) => {
  if (!ev.target.closest('[data-lu-close]')) return;
  $('#levelup').hidden = true;
  $('#levelup').innerHTML = '';
  refreshAll();
});

// XP bar for result screens: fills from the old total to the new one.
function xpBarHTML(before, after) {
  const lv = game.levelForXP(after);
  const lo = game.xpForLevel(lv);
  const hi = game.xpForLevel(lv + 1);
  const fromPct = game.levelForXP(before) < lv ? 0 : ((before - lo) / (hi - lo)) * 100;
  const toPct = ((after - lo) / (hi - lo)) * 100;
  return `<div class="xpbar" style="--from:${fromPct.toFixed(1)}%;--to:${toPct.toFixed(1)}%">
    <div class="xp-top"><span>Operator LV ${lv}</span><b>+${after - before} XP</b><span>${after - lo}/${hi - lo}</span></div>
    <div class="xp-track"><span></span></div></div>`;
}

let lastFocus = null;
let onSheetClose = null;
function openSheet(html, { onClose } = {}) {
  const sheet = $('#sheet');
  onSheetClose = onClose || null;
  // Fresh element each time so click handlers from the previous sheet don't linger.
  const old = $('#sheet-body');
  const body = old.cloneNode(false);
  old.replaceWith(body);
  body.innerHTML = html;
  if (sheet.hidden) lastFocus = document.activeElement;
  sheet.hidden = false;
  $('.sheet-panel', sheet).scrollTop = 0;
  $('.sheet-panel', sheet).focus({ preventScroll: true });
  hydratePhotos(body);
  $$('[data-decode]', body).forEach((el) => decode(el));
  $$('.tilt', body).forEach(enableTilt);
  return body;
}
function closeSheet() {
  const sheet = $('#sheet');
  if (sheet.hidden) return;
  sheet.hidden = true;
  $('#sheet-body').innerHTML = '';
  stopSpeaking();
  gyroTarget = null;
  const cb = onSheetClose;
  onSheetClose = null;
  if (cb) cb();
  if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
  setTimeout(flushLevelUps, $('.fly-card') ? 950 : 150); // let a collect flight land first
}
$('#sheet').addEventListener('click', (e) => { if (e.target.closest('[data-close]')) closeSheet(); });
document.addEventListener('keydown', (e) => {
  if (e.key !== 'Escape') return;
  if (!$('#levelup').hidden) $('[data-lu-close]')?.click();
  else if (!battleLocked) closeSheet();
});

function hydratePhotos(root) {
  $$('img[data-photo]', root).forEach(async (img) => {
    const url = await store.photoURL(img.dataset.photo);
    if (url) img.src = url;
  });
}

// Cards follow the phone's tilt (gyroscope), with finger/mouse as fallback.
let gyroTarget = null;
let gyroListening = false;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
function onOrientation(ev) {
  if (!gyroTarget || ev.beta == null || ev.gamma == null) return;
  const g = gyroTarget;
  if (!g.base) g.base = [ev.beta, ev.gamma];
  const rx = clamp((g.base[0] - ev.beta) * 0.7, -16, 16);
  const ry = clamp((ev.gamma - g.base[1]) * 0.9, -20, 20);
  g.rx += (rx - g.rx) * 0.25;
  g.ry += (ry - g.ry) * 0.25;
  g.el.style.setProperty('--rx', `${g.rx.toFixed(2)}deg`);
  g.el.style.setProperty('--ry', `${g.ry.toFixed(2)}deg`);
  g.el.style.setProperty('--mx', `${50 + g.ry * 2.4}%`);
  g.el.style.setProperty('--my', `${50 - g.rx * 2.4}%`);
}
async function startGyro(el) {
  gyroTarget = { el, base: null, rx: 0, ry: 0 };
  if (gyroListening || !state().settings.tilt || typeof DeviceOrientationEvent === 'undefined') return;
  try {
    // iOS asks once, and only from a tap.
    if (typeof DeviceOrientationEvent.requestPermission === 'function' && (await DeviceOrientationEvent.requestPermission()) !== 'granted') return;
    window.addEventListener('deviceorientation', onOrientation);
    gyroListening = true;
  } catch { /* no permission from this context — finger tilt still works */ }
}

function enableTilt(el) {
  if (reducedMotion()) return;
  startGyro(el);
  const move = (ev) => {
    const r = el.getBoundingClientRect();
    const x = (ev.clientX - r.left) / r.width;
    const y = (ev.clientY - r.top) / r.height;
    el.style.setProperty('--ry', `${(x - 0.5) * 22}deg`);
    el.style.setProperty('--rx', `${(0.5 - y) * 18}deg`);
    el.style.setProperty('--mx', `${x * 100}%`);
    el.style.setProperty('--my', `${y * 100}%`);
  };
  el.addEventListener('pointermove', move);
  el.addEventListener('pointerleave', () => { el.style.setProperty('--rx', '0deg'); el.style.setProperty('--ry', '0deg'); });
}

function burst(stage, rarity) {
  if (reducedMotion()) return;
  const colors = {
    1: ['#A6A6B0', '#DCDCE2', '#F1F1F4'],
    2: ['#CDBDF0', '#F1F1F4', '#9B7CD8'],
    3: ['#B9A0F0', '#F1F1F4', '#5FB6DA', '#D98DDB'],
    4: ['#F2D27A', '#FFF1C2', '#E8B84A', '#CDBDF0'],
    5: ['#FF8AD8', '#FFE08A', '#8AFFD0', '#8AD8FF', '#C48AFF', '#FFFFFF'],
  }[rarity] || ['#9B7CD8', '#CDBDF0', '#F1F1F4'];
  const b = document.createElement('div');
  b.className = 'burst';
  const n = 10 + Math.min(4, rarity) * Math.min(4, rarity) * 5;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + Math.random() * 0.3;
    const d = 90 + Math.random() * (60 + Math.min(4, rarity) * 30);
    const p = document.createElement('i');
    p.style.setProperty('--x', `${Math.cos(a) * d}px`);
    p.style.setProperty('--y', `${Math.sin(a) * d}px`);
    p.style.setProperty('--c', colors[i % colors.length]);
    b.appendChild(p);
  }
  const ring = document.createElement('div');
  ring.className = 'ring-pulse';
  stage.append(b, ring);
  setTimeout(() => { b.remove(); ring.remove(); }, 1000);
}

// ---------------------------------------------------------------- cards
const typeColor = (e) => TYPES[AFFINITY[e.k][0]].color;
const pips = (r) => [1, 2, 3, 4].map((i) => `<i class="${i <= r ? 'on' : ''}"></i>`).join('');

function cardHTML(e, { tag = 'button' } = {}) {
  const s = state();
  const rec = s.caught[e.k];
  const intel = !rec && s.intel[e.k];
  const known = rec || intel;
  const types = AFFINITY[e.k];
  const cls = `card r${e.r}${rec ? '' : ' locked'}${intel ? ' intel' : ''}${rec?.holo ? ' holo' : ''} skin-${loot.frameId(s)}`;
  const lv = rec ? game.levelFor(rec.count) : 0;
  const attrs = tag === 'button' ? `type="button" data-key="${e.k}" aria-label="#${pad(e.no)} ${known ? esc(e.n) : 'unknown card'}${rec ? '' : ', not captured'}"` : '';
  return `<${tag} class="${cls}"${known ? ` style="--tc:${typeColor(e)}"` : ''} ${attrs}>
    <div class="card-top"><span>#${pad(e.no)}</span><span class="tdot"></span></div>
    ${rec ? `<span class="card-lv">LV${lv}</span>` : ''}${rec?.holo ? '<span class="holo-tag">HOLO</span>' : ''}
    <div class="card-art"><div class="disc"></div><img src="${artURL(e)}" alt="" loading="lazy" draggable="false">
      ${rec ? `<span class="card-stars" role="img" aria-label="Level ${lv} of ${game.MAX_LEVEL}">${game.starsHTML(lv)}</span>` : ''}</div>
    ${!known ? '<span class="lock">ENCRYPTED</span>' : ''}${intel ? '<span class="lock">NOT CAPTURED</span>' : ''}
    <div class="card-name">${known ? esc(e.n) : '? ? ?'}</div>
    <div class="card-facts">
      <div><small>Size</small><b>${known ? esc(shortSize(e)) : '???'}</b></div>
      <div><small>Diet</small><b>${known ? esc(shortDiet(e)) : '???'}</b></div>
    </div>
    <div class="card-type">Type: ${types.map((t) => `${glyph(t)}<b>${TYPES[t].name}</b>`).join(' / ')}</div>
    <div class="card-pips" aria-label="${RARITY[e.r].name}">${pips(e.r)}</div>
    ${tag === 'div' ? '<span class="shine"></span>' : ''}
  </${tag}>`;
}

function cardBackHTML() {
  return `<div class="card-back">
    <svg viewBox="0 0 100 100" aria-hidden="true"><circle cx="50" cy="50" r="48" stroke-dasharray="2 3"/><circle cx="50" cy="50" r="38"/><circle cx="50" cy="50" r="28" stroke-dasharray="1 2"/></svg>
    <div class="logo">WILD<br>DEX<small>DATA CARD</small></div>
    <div class="tap">TAP TO DECRYPT</div>
  </div>`;
}

const typeChips = (e) => `<div class="types">${AFFINITY[e.k].map((t) => `<span class="tchip" style="--tc:${TYPES[t].color}">${glyph(t)}${TYPES[t].name}</span>`).join('')}</div>`;

function statsPanel(e, rec) {
  const lv = rec ? game.levelFor(rec.count) : 1;
  const st = game.statsFor(e, lv);
  const next = rec ? game.nextLevelAt(lv) : null;
  const prevAt = 2 ** (lv - 1);
  return `<section class="panel" style="--tc:${typeColor(e)}">
    <div class="panel-head"><span>Battle stats · Lv ${rec ? lv : '—'}${rec ? ` <em class="stars-inline">${game.starsHTML(lv)}</em>` : ''}</span><svg class="shape" viewBox="0 0 20 12"><path d="M1 12V4M5 12V1M9 12V6M13 12V2M17 12V7"/></svg></div>
    <div class="panel-body">
      <div class="stats">${game.STAT_KEYS.map((k) => `<div class="stat"><span>${k.toUpperCase()}</span><span class="bar"><span style="width:${st[k]}%"></span></span><b>${rec ? st[k] : '??'}</b></div>`).join('')}</div>
      <div class="pwr"><span>POWER</span><b>${rec ? st.pwr : '???'}</b></div>
      ${rec ? `<div class="lvbar">${next ? `${rec.count}/${next} sightings to Lv ${lv + 1}` : 'Max level reached'}
        <div class="track"><span style="width:${next ? ((rec.count - prevAt) / (next - prevAt)) * 100 : 100}%"></span></div></div>` : ''}
    </div>
  </section>`;
}

function detailHTML(e, { banner = '', gains = '', extra = '', hideUntilFlip = false, typing = false } = {}) {
  const s = state();
  const rec = s.caught[e.k];
  const intel = !rec && s.intel[e.k];
  const set = SETS.find((x) => x.id === e.set);
  const known = rec || intel;
  const forms = rec && e.c.length > 1 ? `<div class="forms">${rec.forms.map((i) => `<span>${esc(title(LABELS[i]))}</span>`).join('')}</div>` : '';
  return `<div class="detail${hideUntilFlip ? ' pending' : ''}">
    <div class="detail-head">
      ${banner}
      <p class="eyebrow">#${pad(e.no)} · ${esc(set.name)}</p>
      <h2 data-decode>${known ? esc(e.n) : 'Unknown'}</h2>
      <span class="sci">${known ? esc(e.s) : 'species incognita'}</span>
      ${typeChips(e)}
    </div>
    ${gains ? `<div class="gains">${gains}</div>` : ''}${extra}
    ${rec ? `
      <section class="panel"><div class="panel-head"><span>Dex entry</span><span>${RARITY[e.r].name}</span></div>
        <div class="panel-body"><p class="dex-text${typing ? '' : ' done'}"><span class="sr">${esc(e.t)}</span><span class="typed" aria-hidden="true">${typing ? '' : esc(e.t)}</span></p>
        <p class="fact"><b>// did you know?</b> ${esc(e.f)}</p></div></section>
      ${statsPanel(e, rec)}
      <section class="panel"><div class="panel-head"><span>Field data</span><span>${RARITY[e.r].value} XP</span></div>
        <div class="panel-body facts"><div><small>Habitat</small>${esc(e.h)}</div><div><small>Diet</small>${esc(e.d)}</div><div><small>Size</small>${esc(e.z)}</div></div></section>
      <section class="panel"><div class="panel-head"><span>Capture log</span><span>${rec.count}× seen</span></div>
        <div class="panel-body capture">
          ${rec.photo ? `<img data-photo="${e.k}" alt="Your capture photo of a ${esc(e.n)}">` : '<span></span>'}
          <ul><li><span>first</span> ${isoDate(rec.first)}</li><li><span>last</span> ${isoDate(rec.last)}</li>
          <li><span>forms</span> ${rec.forms.length}/${e.c.length}</li>
          ${rec.loc ? `<li><span>near</span> ${fmtLoc(rec.loc)} · <a href="${osmURL(rec.loc)}" target="_blank" rel="noopener">map ↗</a></li>` : ''}</ul>
        </div>${forms ? `<div class="panel-body" style="padding-top:0">${forms}</div>` : ''}</section>` : `
      <section class="panel"><div class="panel-head"><span>Dex entry</span><span>${RARITY[e.r].name}</span></div>
        <div class="panel-body"><p class="dex-text done">${intel
          ? `Intel decrypted. Find a real ${esc(e.n.toLowerCase())} — look in <b>${esc(e.h.toLowerCase())}</b> — and scan it to capture this card.`
          : `Encrypted. This ${RARITY[e.r].name.toLowerCase()} signal was last traced to <b>${esc(e.h.toLowerCase())}</b>.`}</p></div></section>
      ${statsPanel(e, null)}`}
  </div>`;
}

function openEntry(key) {
  const e = BY_KEY[key];
  const s = state();
  const rec = s.caught[e.k];
  sfx.click();
  const canDecrypt = !rec && !s.intel[e.k];
  const body = openSheet(`
    <div class="stage" style="--aura:${typeColor(e)}"><div class="tilt">${cardHTML(e, { tag: 'div' })}</div></div>
    ${detailHTML(e)}
    <div class="actions">
      ${rec ? `<button type="button" class="btn" data-act="speak"><svg viewBox="0 0 24 24"><path d="M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6"/></svg>Play audio</button>` : ''}
      ${canDecrypt ? `<button type="button" class="btn" data-act="decrypt" ${s.shards < game.DECRYPT_COST ? 'disabled' : ''}>Decrypt intel · ${game.DECRYPT_COST}◆</button>` : ''}
      <button type="button" class="btn primary" data-close>Close</button>
    </div>`);
  body.addEventListener('click', (ev) => {
    const act = ev.target.closest('[data-act]');
    if (!act) return;
    if (act.dataset.act === 'speak') speak(`${e.n}. ${e.t}`);
    if (act.dataset.act === 'decrypt' && s.shards >= game.DECRYPT_COST) {
      s.shards -= game.DECRYPT_COST;
      s.intel[e.k] = true;
      store.save();
      sfx.coin();
      refreshAll();
      openEntry(e.k);
    }
  });
}

// ---------------------------------------------------------------- camera
const video = $('#video');
const freeze = $('#freeze');
const vf = $('#viewfinder');
const freezeCtx = freeze.getContext('2d', { willReadFrequently: true });
// Whole camera frame, letterboxed — used only for the screen/print check.
const wide = document.createElement('canvas');
wide.width = wide.height = 448;
const wideCtx = wide.getContext('2d', { willReadFrequently: true });
// Uncropped camera frame (downscaled) for the bezel / print-margin detector.
const raw = document.createElement('canvas');
let stream = null;
let facing = 'environment';
let wantCamera = false;
let busy = false;

async function startCamera() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    $('#vf-text').textContent = 'This browser has no camera access. Open WildDex on a phone with a camera to scan animals.';
    $('#btn-camera').hidden = true;
    log('[err] optics unavailable', 'err');
    return;
  }
  stopCamera();
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: facing }, width: { ideal: 1280 }, height: { ideal: 1280 } },
      audio: false,
    });
    video.srcObject = stream;
    await video.play();
    wantCamera = true;
    try { localStorage.setItem('wilddex.cam', '1'); } catch { /* ignore */ }
    const front = stream.getVideoTracks()[0].getSettings().facingMode === 'user';
    vf.classList.toggle('mirror', front);
    vf.classList.add('live');
    const caps = stream.getVideoTracks()[0].getCapabilities?.() || {};
    $('#btn-torch').disabled = !caps.torch;
    $('#rec').textContent = '● LIVE';
    $('#lens').textContent = `LENS:${front ? 'FRONT' : 'REAR'}`;
    log(`[ok] optics online · lens=${front ? 'front' : 'rear'}`, 'ok');
    idle();
    warmModel();
  } catch (err) {
    vf.classList.remove('live');
    const denied = err && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
    $('#vf-text').textContent = denied
      ? 'Camera permission blocked. Allow camera access in your browser settings to scan animals.'
      : 'No camera found. WildDex needs a camera to scan real animals.';
    log(`[err] optics ${denied ? 'permission denied' : 'not found'}`, 'err');
  }
}

function stopCamera() {
  if (stream) stream.getTracks().forEach((t) => t.stop());
  stream = null;
  video.srcObject = null;
  vf.classList.remove('live');
  $('#btn-torch').disabled = true;
  $('#btn-torch').setAttribute('aria-pressed', 'false');
  $('#rec').textContent = '○ STBY';
}

function captureFrame() {
  const vw = video.videoWidth;
  const vh = video.videoHeight;
  const side = Math.min(vw, vh);
  freezeCtx.save();
  if (vf.classList.contains('mirror')) { freezeCtx.translate(freeze.width, 0); freezeCtx.scale(-1, 1); }
  freezeCtx.drawImage(video, (vw - side) / 2, (vh - side) / 2, side, side, 0, 0, freeze.width, freeze.height);
  freezeCtx.restore();

  const scale = wide.width / Math.max(vw, vh);
  const dw = vw * scale;
  const dh = vh * scale;
  wideCtx.fillStyle = '#000';
  wideCtx.fillRect(0, 0, wide.width, wide.height);
  wideCtx.drawImage(video, (wide.width - dw) / 2, (wide.height - dh) / 2, dw, dh);

  const rs = Math.min(1, 640 / Math.max(vw, vh));
  raw.width = Math.round(vw * rs);
  raw.height = Math.round(vh * rs);
  raw.getContext('2d').drawImage(video, 0, 0, raw.width, raw.height);
}

const canvasToBlob = (canvas, size = 360) => new Promise((resolve) => {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  c.getContext('2d').drawImage(canvas, 0, 0, size, size);
  c.toBlob((b) => resolve(b), 'image/jpeg', 0.84);
});

function unfreeze() { vf.classList.remove('frozen', 'scanning'); }
const resetScanner = () => { unfreeze(); idle(); };

// ---------------------------------------------------------------- model
let modelPromise = null;
function warmModel() {
  if (!modelPromise) {
    let line = null;
    modelPromise = loadModel((p) => {
      if (isReady() || p >= 1) return;
      const html = `[..] loading scanner ${asciiBar(p, 8)} ${Math.round(p * 100)}%`;
      if (line && line.isConnected) line.innerHTML = clean(html); else line = log(html);
    }).then((m) => {
      const msg = `[ok] scanner ready · ${ENTRIES.length} animals known`;
      if (line && line.isConnected) { line.className = 'ok'; line.innerHTML = clean(msg); } else log(msg, 'ok');
      return m;
    }).catch((err) => {
      modelPromise = null;
      log('[err] neural core failed to load — check connection', 'err');
      throw err;
    });
  }
  return modelPromise;
}

// ---------------------------------------------------------------- scanning
let pendingGrade = null; // sync grade of the last verified sweep, used by register()
async function runScan(source) {
  if (busy) return;
  busy = true;
  $('#btn-scan').disabled = true;
  music.duck(true);
  try {
    if (!stream) { await startCamera(); if (!stream) return; }
    warmModel().catch(() => {});
    log(`&gt; wilddex.scan --source=${source} --live`, 'cmd');
    log('[..] lock-on sweep · slide phone left, then right');
    pendingGrade = null;

    // 1) Lock-on sweep: record ~1.9 s while the player slides the phone. The
    //    ring drifts the way the phone should move and fills as sync builds.
    vf.classList.remove('locked');
    vf.classList.add('sweeping');
    $('#sweep-bar').style.width = '0%';
    $('#lock-arc').style.strokeDashoffset = 100;
    let tick = 0;
    let quarter = 0;
    const frames = await recordSweep(video, {
      onProgress: (p) => {
        $('#sweep-bar').style.width = `${p * 100}%`;
        $('#sweep-text').textContent = p < 0.5 ? 'Slide phone left' : 'Now slide right';
        $('#lock-arc').style.strokeDashoffset = 100 - p * 100;
        $('#lock-pct').textContent = `${Math.round(p * 100)}%`;
        const lx = p < 0.5 ? -p * 2 : -1 + (p - 0.5) * 4;
        $('#lockon').style.setProperty('--lx', `${(lx * 12).toFixed(1)}%`);
        if (tick++ % 3 === 0) tone(p < 0.5 ? 900 + p * 400 : 1300 - (p - 0.5) * 400, 0, 0.03, 'square', 0.015);
        if (Math.floor(p * 4) > quarter) { quarter = Math.floor(p * 4); buzz(12); }
      },
    });
    vf.classList.remove('sweeping');
    if (!stream) return;
    vf.classList.add('locked');
    sfx.lock();
    buzz([20, 40, 20]);
    setTimeout(() => vf.classList.remove('locked'), 900);

    // 2) Freeze the last frame and identify the animal.
    captureFrame();
    vf.classList.add('frozen', 'scanning');
    flash();
    sfx.scan();
    const started = performance.now();
    await warmModel();
    log('[..] inference ×3 (full · mirror · crop)');
    const probs = await classify(freeze);
    let v = interpret(probs);

    // 3) Only a real, live, 3D animal counts.
    if (v.kind === 'match' || v.kind === 'unsure') {
      log('[..] liveness · depth, screen & print checks');
      const depth = analyseSweep(frames);
      const spoof = spoofCheck(probs, await classify(wide));
      const frame = detectFrame(raw, raw.width, raw.height);
      if (spoof.blocked || frame.found) v = { kind: 'spoof', spoof, frame };
      else if (depth.verdict !== 'live') v = { kind: 'depth', depth };
      else pendingGrade = game.syncGrade(v.top.score, depth);
    }
    const wait = 1200 - (performance.now() - started);
    if (wait > 0) await sleep(wait);
    vf.classList.remove('scanning');
    state().scans++;
    store.save();
    await handleVerdict(v);
  } catch (err) {
    console.error(err);
    sfx.fail();
    log('[err] scan aborted · please retry', 'err');
    unfreeze();
  } finally {
    busy = false;
    $('#btn-scan').disabled = false;
    music.duck(false);
  }
}

async function handleVerdict(v) {
  const conf = (x) => x.toFixed(2);
  if (v.kind === 'match') {
    log(`[ok] match <b>${snake(v.top.entry.s)}</b> · conf=${conf(v.top.score)}`, 'ok');
    await register(v.top.entry, v.top.form, v.alternatives.filter((a) => a.entry !== v.top.entry));
  } else if (v.kind === 'depth') {
    const d = v.depth;
    if (d.verdict === 'flat') {
      sfx.fail();
      log(`[err] liveness failed · flat surface · ${d.parallax || 0}/${d.tracks} depth points`, 'err');
      log('[err] photos, prints & screens can\'t be registered', 'err');
      toast('FLAT IMAGE DETECTED · SCAN A LIVE ANIMAL');
    } else if (d.verdict === 'video') {
      sfx.fail();
      log(`[err] liveness failed · moving image on a flat screen · ${d.indep} pts`, 'err');
      log('[err] videos on screens can\'t be registered', 'err');
      toast('VIDEO ON A SCREEN DETECTED · SCAN A LIVE ANIMAL');
    } else if (d.verdict === 'still') {
      sfx.again();
      log('[warn] no depth signal · slide the phone left, then right, while scanning', 'warn');
      toast('SLIDE YOUR PHONE LEFT, THEN RIGHT');
    } else if (d.verdict === 'oneway') {
      sfx.again();
      log('[warn] one-way motion · slide left AND back right to verify depth', 'warn');
      toast('SLIDE LEFT, THEN BACK RIGHT');
    } else {
      sfx.again();
      log('[warn] too little detail to verify · move closer or add light', 'warn');
      toast('CAN\'T VERIFY · MOVE CLOSER OR ADD LIGHT');
    }
    setTimeout(resetScanner, 2200);
  } else if (v.kind === 'spoof') {
    sfx.fail();
    log(v.spoof.blocked
      ? `[err] liveness failed · <b>${esc(snake(LABELS[v.spoof.label]))}</b> · conf=${conf(v.spoof.score)}`
      : `[err] liveness failed · <b>display_or_print_frame</b> detected`, 'err');
    log('[err] screens & prints can\'t be registered', 'err');
    toast('SCREEN OR PRINT DETECTED · SCAN A REAL ANIMAL');
    setTimeout(resetScanner, 2200);
  } else if (v.kind === 'unsure') {
    sfx.again();
    log(`[warn] low confidence · top=${conf(v.top.score)} · manual id`, 'warn');
    showChoices(v.alternatives, 'Signal inconclusive — pick the right animal, or rescan closer and in better light.');
  } else if (v.kind === 'object') {
    sfx.fail();
    log(`[err] not fauna: <b>${esc(snake(LABELS[v.object]))}</b> · conf=${conf(v.objectScore)}`, 'err');
    setTimeout(resetScanner, 1800);
  } else {
    sfx.fail();
    log('[err] no fauna signature · move closer, hold steady', 'err');
    setTimeout(resetScanner, 1800);
  }
}

function showChoices(options, text) {
  const body = openSheet(`<div class="detail-head">
      <span class="banner warn">⚠ Signal unclear</span>
      <p class="eyebrow">Manual identification</p>
      <h2 data-decode>Select match</h2>
    </div>
    <p class="fact">${esc(text)}</p>
    <div class="choices">${options.map((o, i) => {
      const known = state().caught[o.entry.k];
      return `<button type="button" class="choice" data-i="${i}">
        <span class="disc"><img src="${artURL(o.entry)}" alt=""></span>
        <span><b>${esc(o.entry.n)}</b><i>${known ? 'in your binder' : 'new card'}</i></span>
        <span class="pct">${Math.max(1, Math.round(o.score * 100))}%</span></button>`;
    }).join('')}</div>
    <div class="actions"><button type="button" class="btn" data-act="none">None of these · rescan</button></div>`,
  { onClose: resetScanner });
  body.addEventListener('click', async (ev) => {
    const btn = ev.target.closest('.choice');
    if (btn) {
      const o = options[Number(btn.dataset.i)];
      onSheetClose = null;
      log(`[ok] manual id <b>${snake(o.entry.s)}</b>`, 'ok');
      await register(o.entry, o.form, options.filter((a) => a !== o));
    } else if (ev.target.closest('[data-act="none"]')) {
      closeSheet();
    }
  });
}

async function register(e, form, alternatives = []) {
  const s = state();
  const snapshot = structuredClone(s);
  const grade = pendingGrade;
  pendingGrade = null;
  const now = Date.now();
  const isNew = !s.caught[e.k];
  const rec = s.caught[e.k] || { first: now, count: 0, forms: [], photo: false };
  const lvBefore = isNew ? 0 : game.levelFor(rec.count);
  const newForm = e.c.length > 1 && !rec.forms.includes(form);
  if (!rec.forms.includes(form)) rec.forms.push(form);
  rec.count++;
  rec.last = now;
  const blob = await canvasToBlob(freeze);
  if (isNew) {
    rec.photo = true;
    await store.putPhoto(e.k, blob);
  }
  s.caught[e.k] = rec;
  delete s.intel[e.k];
  const lvAfter = game.levelFor(rec.count);

  // rewards
  const gains = [];
  let shards = 0;
  if (isNew) { shards += RARITY[e.r].value; gains.push([`+${RARITY[e.r].value}◆ new card`, true]); }
  else { shards += 3; gains.push(['+3◆ sighting', false]); }
  if (!isNew && lvAfter > lvBefore) { shards += 10; gains.push([`LV ${lvBefore} → ${lvAfter} · +10◆`, true]); }
  if (newForm && !isNew) gains.push([`new form: ${title(LABELS[form])}`, true]);
  const streak = game.touchStreak(s);
  if (streak.bonus) { shards += streak.bonus; gains.push([`day ${streak.days} streak +${streak.bonus}◆`, false]); }
  const missionsDone = game.progressMissions(s, { entry: e, isNew });
  missionsDone.forEach((m) => gains.push([`mission complete: ${m.text}`, true]));
  const eventDone = game.progressEvent(s, e);
  if (eventDone) gains.push([`event complete: ${game.weeklyEvent(s).name}`, true]);
  const setDone = isNew && SETS.find((x) => x.id === e.set).entries.every((x) => s.caught[x.k]);
  if (setDone) { shards += 50; gains.push([`sector complete +50◆`, true]); }
  if (grade) {
    shards += grade.credits;
    if (grade.credits) gains.push([`sync ${grade.grade} +${grade.credits}◆`, grade.grade === 'S']);
    if (!s.bestGrade || 'SABC'.indexOf(grade.grade) < 'SABC'.indexOf(s.bestGrade)) s.bestGrade = grade.grade;
  }
  // Holo roll: a rare foil variant of this card.
  let holoNew = false;
  if (Math.random() < game.holoChance(grade?.grade)) {
    if (!rec.holo) { rec.holo = now; holoNew = true; gains.unshift(['✦ holo variant', true]); } else { shards += game.HOLO_DUPE_CREDITS; gains.push([`holo echo +${game.HOLO_DUPE_CREDITS}◆`, true]); }
  }
  s.shards = (s.shards || 0) + shards;
  const xpBefore = s.xp;
  const xpGain = (isNew ? RARITY[e.r].value : game.SIGHTING_XP) + (grade ? grade.xp : 0) + (holoNew ? 25 : 0);
  earnXP(xpGain);
  store.save();
  refreshAll(true);
  tagLocation(e.k);

  log(isNew
    ? `[ok] new card #${pad(e.no)} ${esc(e.n)} · +${shards}◆`
    : `[ok] sighting #${pad(e.no)} ×${rec.count} · +${shards}◆`, 'ok');
  buzz(!isNew ? 30 : e.r === 4 ? [40, 60, 40, 60, 200] : e.r === 3 ? [30, 40, 30, 40, 120] : [30, 40, 80]);

  const gainsHTML = gains.map(([t, hl], i) => `<span class="gain${hl ? ' hl' : ''}${t.startsWith('✦') ? ' holo' : ''}" style="animation-delay:${i * 0.08}s">${esc(t)}</span>`).join('');
  const PULL = { 1: '▲ New card', 2: '▲ Uncommon pull', 3: '◆ Rare pull', 4: '★ Legendary pull' };
  const banner = (holoNew ? '<span class="banner holo">✦ Holo variant</span> ' : '')
    + (isNew ? `<span class="banner r${e.r}">${PULL[e.r]}</span>` : `<span class="banner soft">● Sighting logged ×${rec.count}</span>`);
  const aura = typeColor(e);
  // Power-up panel when a re-scan raises the card's level.
  let powerHTML = '';
  if (!isNew && lvAfter > lvBefore) {
    const a = game.statsFor(e, lvBefore);
    const b = game.statsFor(e, lvAfter);
    powerHTML = `<section class="powerup chamfer">
      <p class="pu-title">Power up <b>LV ${lvBefore} → ${lvAfter}</b></p>
      <div class="pu-stars"><span class="stars-inline">${game.starsHTML(lvBefore)}</span><i>▸</i><span class="stars-inline new">${game.starsHTML(lvAfter)}</span></div>
      <div class="pu-stats">${game.STAT_KEYS.map((k) => `<span>${k.toUpperCase()} <b>${b[k]}</b><em>+${b[k] - a[k]}</em></span>`).join('')}</div>
    </section>`;
  }
  const extra = xpBarHTML(xpBefore, s.xp) + powerHTML;
  const gradeStamp = grade ? `<div class="grade-stamp g-${grade.grade}" aria-label="Sync grade ${grade.grade}"><small>sync</small><b>${grade.grade}</b></div>` : '';

  const body = openSheet(`
    <div class="stage${isNew ? ' charging' : ''}${holoNew ? ' is-holo' : ''}" style="--aura:${aura}" ${isNew ? 'role="button" tabindex="0" aria-label="Reveal card"' : ''}>
      <div class="rays" aria-hidden="true"></div>${gradeStamp}
      <div class="flip${isNew ? ' down' : ''}">
        <div class="face"><div class="tilt">${cardHTML(e, { tag: 'div' })}</div></div>
        ${isNew ? `<div class="back face">${cardBackHTML()}</div>` : ''}
      </div>
    </div>
    <div class="after"${isNew ? ' hidden' : ''}>
      ${detailHTML(e, { banner, gains: gainsHTML, extra, typing: true })}
      <div class="actions">
        <button type="button" class="btn" data-act="speak"><svg viewBox="0 0 24 24"><path d="M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6"/></svg>Play audio</button>
        <button type="button" class="btn primary" data-close>Continue</button>
      </div>
      <div class="subtle-row"><button type="button" class="link" data-act="wrong">misidentified? not a ${esc(e.n.toLowerCase())}</button></div>
    </div>
    ${isNew ? `<div class="actions pre"><button type="button" class="btn primary" data-act="reveal">Decrypt card</button></div>` : ''}`,
  { onClose: () => {
    resetScanner();
    if (isNew) FX.flyToBinder(artURL(e), flyFrom, $('.tabbar [data-tab="binder"]'));
  } });
  if (isNew) freshKeys.add(e.k);
  let flyFrom = null;

  const after = $('.after', body);
  const celebrate = () => {
    const stage = $('.stage', body);
    if (grade) { stage.classList.add('stamped'); setTimeout(() => sfx.grade(grade.grade), 250); }
    if (holoNew) { stage.classList.add('revealed', 'holo-on'); setTimeout(() => { sfx.holo(); burst(stage, 5); flash(5); buzz([20, 30, 20, 30, 20, 30, 160]); }, 500); }
    popGain(`+${shards}◆`, stage, { delay: 350 });
    popGain(`+${xpGain} XP`, stage, { cls: 'xp', delay: 600 });
    setTimeout(() => $('.xpbar', body)?.classList.add('go'), 300);
  };
  const startDetail = () => {
    flyFrom = $('.stage .card', body)?.getBoundingClientRect() || null;
    if (holoNew || (isNew && e.r === 4) || setDone) setTimeout(() => FX.confetti(holoNew || e.r === 4 ? 140 : 90), 350);
    after.hidden = false;
    celebrate();
    $$('[data-decode]', after).forEach((el) => decode(el));
    typeOut($('.dex-text .typed', after), e.t);
    if (isNew) setTimeout(() => speak(`${e.n}. ${e.t}`), 400);
    if (missionsDone.length || eventDone) setTimeout(() => toast(eventDone ? 'WEEKLY EVENT COMPLETE · CLAIM IN OPS' : 'MISSION COMPLETE · CLAIM IN OPS'), 900);
    else if (setDone) setTimeout(() => toast('SECTOR COMPLETE'), 900);
  };

  if (isNew) {
    sfx.charge();
    const stage = $('.stage', body);
    let revealed = false;
    const reveal = () => {
      if (revealed) return;
      revealed = true;
      stage.classList.remove('charging');
      // Rarer cards hold the tension a little longer before flipping.
      const hold = [0, 0, 120, 450, 900][e.r];
      if (hold) { stage.classList.add('tease', `r${e.r}`); sfx.charge(); }
      setTimeout(() => {
        stage.classList.remove('tease');
        $('.flip', stage).classList.remove('down');
        $('.actions.pre', body)?.remove();
        setTimeout(() => {
          stage.classList.add('revealed', `r${e.r}`);
          burst(stage, e.r);
          if (e.r >= 3) setTimeout(() => burst(stage, e.r), 260);
          sfx.reveal(e.r);
          flash(e.r);
        }, 350);
        setTimeout(startDetail, 700 + (e.r >= 3 ? 300 : 0));
      }, hold);
    };
    stage.addEventListener('click', reveal);
    stage.addEventListener('keydown', (ev) => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); reveal(); } });
    $('[data-act="reveal"]', body).addEventListener('click', reveal);
  } else {
    sfx.again();
    if (lvAfter > lvBefore) setTimeout(() => burst($('.stage', body), 1), 200);
    startDetail();
  }

  body.addEventListener('click', async (ev) => {
    const act = ev.target.closest('[data-act]');
    if (!act) return;
    if (act.dataset.act === 'speak') speak(`${e.n}. ${e.t}`);
    if (act.dataset.act === 'wrong') {
      // Roll back everything this registration did, then offer the other candidates.
      store.replaceState(snapshot);
      levelUps.length = 0;
      if (isNew) await store.deletePhoto(e.k);
      refreshAll();
      stopSpeaking();
      log(`[warn] registration #${pad(e.no)} rolled back`, 'warn');
      if (alternatives.length) showChoices(alternatives, `Rolled back ${e.n}. Was it one of these?`);
      else { closeSheet(); toast('REGISTRATION ROLLED BACK'); }
    }
  });
}

// ---------------------------------------------------------------- views
function caughtKeys() { return Object.keys(state().caught).filter((k) => BY_KEY[k]); }
// Older saves had no XP total: start them at what their cards were worth.
function migrate() {
  const s = state();
  if (!Number.isFinite(s.xp)) s.xp = caughtKeys().reduce((sum, k) => sum + RARITY[BY_KEY[k].r].value, 0);
}
const xp = () => state().xp || 0;
const opLevel = () => game.levelForXP(xp());
function rankFor(n) { let r = RANKS[0]; for (const x of RANKS) if (n >= x[0]) r = x; return r; }
function unclaimedMissions() {
  const d = game.dailyState(state());
  return d.missions.filter((m) => (d.progress[m.id] || 0) >= m.goal && !d.claimed[m.id]).length;
}

let currentTab = 'home';
function refreshAll(bumpWallet = false) {
  migrate();
  const s = state();
  const n = caughtKeys().length;
  countTo($('#shards'), s.shards || 0);
  const lvl = opLevel();
  $('#op-level').textContent = lvl;
  $('#op-name').textContent = s.name || 'Operator';
  const lo = game.xpForLevel(lvl);
  const hi = game.xpForLevel(lvl + 1);
  $('#op-xp-bar').style.width = `${Math.max(4, ((xp() - lo) / (hi - lo)) * 100)}%`;
  const crates = s.locker?.crates || 0;
  $('#crates').textContent = crates;
  $('.pill.crates').classList.toggle('hot', crates > 0);
  if (bumpWallet) { const w = $('#meters'); w.classList.remove('bump'); void w.offsetWidth; w.classList.add('bump'); }
  const ev = game.weeklyEvent(s);
  $('#ops-badge').hidden = !(unclaimedMissions() || (ev.progress >= ev.goal && !ev.claimed));
  $('#arena-badge').hidden = !(n && !(s.battle?.rival?.date === game.today() && s.battle.rival.won));
  $('#id-badge').hidden = !crates;
  renderRecent();
  if (currentTab === 'home') renderHome();
  if (currentTab === 'binder') renderBinder();
  if (currentTab === 'arena') renderArena();
  if (currentTab === 'ops') renderOps();
  if (currentTab === 'id') renderProfile();
}

function renderRecent() {
  const recent = Object.entries(state().caught)
    .filter(([k]) => BY_KEY[k])
    .sort((a, b) => b[1].last - a[1].last)
    .slice(0, 8)
    .map(([k]) => BY_KEY[k]);
  $('#recent-wrap').hidden = !recent.length;
  $('#recent').innerHTML = recent.map((e) => cardHTML(e)).join('');
}

// ---- binder
const freshKeys = new Set(); // cards caught this session; their slots sparkle once
const binder = { set: 'all', type: null, sort: 'no', owned: false, selected: null };
function renderBinder() {
  const s = state();
  const n = caughtKeys().length;
  $('#binder-sub').textContent = `${n}/${ENTRIES.length} · ${SETS.filter((x) => x.entries.every((e) => s.caught[e.k])).length} SECTORS`;

  const chip = (id, label, got, total) => `<button type="button" class="chip${got === total && id !== 'all' ? ' done' : ''}" role="tab" data-set="${id}" aria-selected="${binder.set === id}">${label} <small>${got}/${total}</small></button>`;
  $('#set-chips').innerHTML = chip('all', 'all', n, ENTRIES.length) + SETS.map((set) => {
    const got = set.entries.filter((e) => s.caught[e.k]).length;
    return chip(set.id, `${set.icon} ${esc(set.name.toLowerCase())}`, got, set.entries.length);
  }).join('');
  $('#type-chips').innerHTML = `<button type="button" class="all" data-type="" aria-pressed="${!binder.type}">ALL</button>` + TYPE_IDS.map((t) =>
    `<button type="button" data-type="${t}" style="--tc:${TYPES[t].color}" aria-pressed="${binder.type === t}" title="${TYPES[t].name} — ${TYPES[t].desc}">${glyph(t)}</button>`).join('');

  let list = binder.set === 'all' ? ENTRIES.slice() : ENTRIES.filter((e) => e.set === binder.set);
  if (binder.type) list = list.filter((e) => AFFINITY[e.k].includes(binder.type));
  if (binder.owned) list = list.filter((e) => s.caught[e.k]);
  const lv = (e) => (s.caught[e.k] ? game.levelFor(s.caught[e.k].count) : 0);
  const sorters = {
    no: (a, b) => a.no - b.no,
    rarity: (a, b) => b.r - a.r || a.no - b.no,
    level: (a, b) => lv(b) - lv(a) || a.no - b.no,
    pwr: (a, b) => (s.caught[b.k] ? game.statsFor(b, lv(b)).pwr : -1) - (s.caught[a.k] ? game.statsFor(a, lv(a)).pwr : -1) || a.no - b.no,
    recent: (a, b) => (s.caught[b.k]?.last || 0) - (s.caught[a.k]?.last || 0) || a.no - b.no,
  };
  list.sort(sorters[binder.sort]);
  if (!list.some((e) => e.k === binder.selected)) binder.selected = (list.find((e) => s.caught[e.k]) || list[0])?.k || null;
  const rc = { 1: 'var(--r1)', 2: 'var(--r2)', 3: 'var(--r3)', 4: 'var(--r4)' };
  $('#grid').innerHTML = list.map((e) => {
    const rec = s.caught[e.k];
    const intel = !rec && s.intel[e.k];
    const cls = `slot r${e.r}${rec ? ' owned' : ' locked'}${intel ? ' intel' : ''}${rec?.holo ? ' holo' : ''}${freshKeys.has(e.k) ? ' fresh' : ''}`;
    return `<button type="button" class="${cls}" data-slot="${e.k}" style="--rc:${rc[e.r]};--tc:${typeColor(e)}" aria-pressed="${binder.selected === e.k}"
      aria-label="#${pad(e.no)} ${rec || intel ? esc(e.n) : 'unknown'}${rec ? `, seen ${rec.count} times` : ', not captured'}">
      <span class="no">${pad(e.no)}</span><i class="rar"></i>
      <img src="${artURL(e)}" alt="" loading="lazy" draggable="false">
      ${rec ? `<span class="qty">x${rec.count}</span>` : ''}
    </button>`;
  }).join('');
  $('#grid-empty').hidden = list.length > 0;
  freshKeys.clear();
  $('#inv-detail').hidden = !binder.selected;
  if (binder.selected) renderInvDetail(BY_KEY[binder.selected]);
}

function renderInvDetail(e) {
  const s = state();
  const rec = s.caught[e.k];
  const intel = !rec && s.intel[e.k];
  const known = rec || intel;
  const lv = rec ? game.levelFor(rec.count) : 1;
  const st = game.statsFor(e, lv);
  const types = AFFINITY[e.k].map((t) => `<b class="tcx" style="--tc:${TYPES[t].color}">${TYPES[t].name}</b>`).join(' / ');
  const rarCol = { 1: '#7D8C99', 2: '#2E9B45', 3: 'var(--accent-deep)', 4: '#B07F00' }[e.r];
  $('#inv-detail').innerHTML = `
    <p class="inv-name">${known ? `${esc(e.n)} <small>${esc(e.s)}</small>` : `? ? ? <small>#${pad(e.no)} · undiscovered</small>`}</p>
    <div class="inv-art${rec ? '' : ' locked'}${intel ? ' intel' : ''}">
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <g class="spin"><circle cx="50" cy="50" r="47" stroke-dasharray="3 5"/></g>
        <circle cx="50" cy="50" r="40"/><circle cx="50" cy="50" r="33" class="accent" stroke-dasharray="40 170" transform="rotate(-60 50 50)"/>
        <circle cx="50" cy="50" r="26"/>
      </svg>
      <img src="${artURL(e)}" alt="">
    </div>
    <div class="inv-info">
      <p class="inv-kind"><em style="color:${rarCol};font-style:normal">${RARITY[e.r].name}</em>${rec ? ` · LV ${lv} <em class="stars-inline">${game.starsHTML(lv)}</em>` : ''}${rec?.holo ? ' <b class="holo-txt">HOLO</b>' : ''}<span>${types}</span></p>
      <div class="inv-stats">${game.STAT_KEYS.map((k) => `<div class="inv-stat"><b>${rec ? `+${st[k]}` : '??'}</b>${k}<i class="seg"><em style="width:${rec ? st[k] : 0}%"></em></i></div>`).join('')}</div>
    </div>
    <div class="inv-use">
      <p>${rec ? `PWR ${st.pwr}` : intel ? `Find it in ${esc(e.h.toLowerCase())}` : 'Not found yet'}</p>
      <button type="button" class="btn small primary" data-open="${e.k}">Open</button>
    </div>`;
}

$('#grid').addEventListener('click', (ev) => {
  const b = ev.target.closest('[data-slot]');
  if (!b) return;
  const key = b.dataset.slot;
  if (binder.selected === key) { openEntry(key); return; } // second tap opens the full card
  binder.selected = key;
  sfx.click();
  $$('#grid .slot').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.slot === key)));
  renderInvDetail(BY_KEY[key]);
});
$('#inv-detail').addEventListener('click', (ev) => {
  const b = ev.target.closest('[data-open]');
  if (b) openEntry(b.dataset.open);
});

$('#set-chips').addEventListener('click', (ev) => {
  const c = ev.target.closest('.chip');
  if (!c) return;
  sfx.click();
  binder.set = c.dataset.set;
  renderBinder();
  $(`.chip[data-set="${binder.set}"]`)?.scrollIntoView?.({ inline: 'center', block: 'nearest' });
});
$('#type-chips').addEventListener('click', (ev) => {
  const b = ev.target.closest('button');
  if (!b) return;
  sfx.click();
  binder.type = b.dataset.type || (null);
  if (binder.type && b.getAttribute('aria-pressed') === 'true') binder.type = null;
  renderBinder();
  if (binder.type) toast(`${TYPES[binder.type].name.toUpperCase()} · ${TYPES[binder.type].desc}`);
});
$('#sort').addEventListener('change', (ev) => { binder.sort = ev.target.value; renderBinder(); });
$('#owned-only').addEventListener('change', (ev) => { binder.owned = ev.target.checked; renderBinder(); });

// ---- arena (battles)
const glyphs = (k) => AFFINITY[k].map((t) => glyph(t)).join('');
function fighterChip(e, lv, { holo = false } = {}) {
  return `<div class="fchip${holo ? ' holo' : ''}" style="--tc:${typeColor(e)}">
    <img src="${artURL(e)}" alt="" loading="lazy"><b>LV${lv}</b><span class="fglyphs">${glyphs(e.k)}</span></div>`;
}

function renderArena() {
  const s = state();
  const b = battle.battleState(s);
  $('#arena-sub').textContent = `W ${b.wins} · L ${b.losses}`;
  if (!battle.owned(s).length) {
    $('#arena').innerHTML = `<section class="card-box chamfer"><div class="box-head"><h3>No squad yet</h3></div>
      <p class="about">Catch your first card to unlock battles.</p>
      <button type="button" class="btn primary" data-goto-scan>Go scan</button></section>`;
    return;
  }
  const r = battle.dailyRival(s);
  store.save();
  const keys = battle.squad(s);
  const sims = battle.simsToday(s);
  const pwr = Math.round(keys.reduce((a, k) => a + battle.pwrOf(s, k), 0));
  const rivalLv = Math.round(r.lvs.reduce((a, x) => a + x, 0) / r.lvs.length);
  $('#arena').innerHTML = `
    <section class="card-box chamfer rival${r.won ? ' won' : ''}">
      <div class="box-head"><h3>Daily rival</h3><small>${r.won ? 'defeated ✓ · new rival tomorrow' : `+${battle.RIVAL_REWARD.credits}◆ · +${battle.RIVAL_REWARD.xp} XP`}</small></div>
      <div class="rival-id"><span class="dot-text">${dotSVG(r.name)}</span><span class="rival-lv">LV ${rivalLv}</span></div>
      <div class="mini-team">${r.keys.map((k, i) => fighterChip(BY_KEY[k], r.lvs[i])).join('')}</div>
      <button type="button" class="btn ${r.won ? '' : 'primary '}wide" data-fight="rival">${r.won ? 'Rematch for XP' : r.tries ? 'Try again' : 'Challenge'}</button>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Your squad</h3><small>PWR ${pwr}</small></div>
      <div class="squad">${[0, 1, 2].map((i) => {
        const k = keys[i];
        if (!k) return `<div class="sq-slot empty"><span>+</span><small>scan more animals</small></div>`;
        const e = BY_KEY[k];
        const rec = s.caught[k];
        const lv = game.levelFor(rec.count);
        return `<button type="button" class="sq-slot${rec.holo ? ' holo' : ''}" data-squad="${i}" style="--tc:${typeColor(e)}" aria-label="Squad slot ${i + 1}: ${esc(e.n)}, tap to change">
          <span class="sq-no">${i + 1}</span><img src="${artURL(e)}" alt="">
          <b>${esc(e.n)}</b><span class="fglyphs">${glyphs(k)}</span><small>LV ${lv} · PWR ${Math.round(battle.pwrOf(s, k))}</small></button>`;
      }).join('')}</div>
      <div class="btn-row"><button type="button" class="btn small" data-auto-squad>Auto-pick</button><span class="about">Slot 1 fights first</span></div>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Wild signals</h3><small>${sims.paid}/${battle.SIM_PAID} today</small></div>
      <div class="reward-row"><span class="gain">+${battle.SIM_REWARD.credits}◆</span><span class="gain">+${battle.SIM_REWARD.xp} XP</span><span class="about">per win</span></div>
      <button type="button" class="btn wide" data-fight="wild">Find a battle</button>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Type matchups</h3><button type="button" class="help-btn" data-help="types" aria-label="How type matchups work">?</button></div>
      <div class="typechart">${TYPE_IDS.map((t) => `<div style="--tc:${TYPES[t].color}">${glyph(t)}<b>${TYPES[t].name}</b><i>beats</i>${battle.STRONG[t].map((x) => `<span style="--tc:${TYPES[x].color}" title="${TYPES[x].name}">${glyph(x)}</span>`).join('')}</div>`).join('')}</div>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Record</h3></div>
      <div class="idstats three"><div><b>${pad(b.wins)}</b><span>Wins</span></div><div><b>${pad(b.losses)}</b><span>Losses</span></div><div><b>${pad(b.rivals)}</b><span>Rivals beaten</span></div></div>
    </section>`;
}

$('#arena').addEventListener('click', (ev) => {
  const s = state();
  if (ev.target.closest('[data-goto-scan]')) { showTab('scan'); return; }
  const f = ev.target.closest('[data-fight]');
  if (f) { startBattle(f.dataset.fight); return; }
  if (ev.target.closest('[data-auto-squad]')) {
    battle.battleState(s).squad = battle.bestSquad(s);
    store.save();
    sfx.click();
    renderArena();
    return;
  }
  const slot = ev.target.closest('[data-squad]');
  if (slot) pickSquad(Number(slot.dataset.squad));
});

function pickSquad(i) {
  const s = state();
  const keys = battle.squad(s);
  const list = battle.owned(s).sort((a, b) => battle.pwrOf(s, b) - battle.pwrOf(s, a));
  const body = openSheet(`<div class="detail-head"><p class="eyebrow">Squad slot ${i + 1}</p><h2 data-decode>Pick a card</h2><span class="sci">sorted by power</span></div>
    <div class="choices">${list.map((k) => {
      const e = BY_KEY[k];
      const lv = game.levelFor(s.caught[k].count);
      const at = keys.indexOf(k);
      return `<button type="button" class="choice${at === i ? ' current' : ''}" data-k="${k}">
        <span class="disc"><img src="${artURL(e)}" alt=""></span>
        <span><b>${esc(e.n)}${s.caught[k].holo ? ' <em class="holo-txt">HOLO</em>' : ''}</b><i>LV ${lv} · ${AFFINITY[k].map((t) => TYPES[t].name).join(' / ')}${at >= 0 ? ` · in slot ${at + 1}` : ''}</i></span>
        <span class="pct">${Math.round(battle.pwrOf(s, k))}</span></button>`;
    }).join('')}</div>`);
  body.addEventListener('click', (ev) => {
    const c = ev.target.closest('[data-k]');
    if (!c) return;
    const k = c.dataset.k;
    const next = keys.slice();
    const at = next.indexOf(k);
    if (at >= 0) next[at] = next[i];
    next[i] = k;
    battle.battleState(s).squad = next.filter(Boolean);
    store.save();
    sfx.click();
    closeSheet();
    renderArena();
  });
}

// ---- battle screen
let battleLocked = false;
let inBattle = false;
const MOVE_NAME = (m) => (m === 'guard' ? 'Guard' : m === 'overdrive' ? 'Overdrive' : `${TYPES[m.split(':')[1]].name} strike`);

function startBattle(kind) {
  const s = state();
  const you = battle.squadTeam(s);
  if (!you.length) return;
  let foe;
  let foeName;
  if (kind === 'rival') { const r = battle.dailyRival(s); foe = battle.rivalTeam(r); foeName = r.name; } else { foe = battle.wildTeam(s, you.length); foeName = 'Wild signal'; }
  const b = battle.newBattle(you, foe, { kind });
  const me = s.name || 'Operator';
  inBattle = true;
  music.setScene('battle');
  sfx.charge();
  buzz([20, 30, 20]);
  const sideHTML = (side, team) => `<div class="side ${side}" data-side="${side}">
      <div class="plate"><p class="pl-name"></p><div class="hp"><span></span></div><div class="pl-row"><span class="hp-num"></span><span class="charge"></span></div></div>
      <div class="fighter"><div class="disc"></div><img alt=""><div class="shield"></div></div>
      <div class="bench">${team.map((_, i) => `<i data-b="${i}"></i>`).join('')}</div>
    </div>`;
  const body = openSheet(`<div class="battle${kind === 'rival' ? ' is-rival' : ''}">
      <div class="bt-head"><span>${esc(me)}</span><b>VS</b><span>${esc(foeName)}</span></div>
      <div class="field">${sideHTML('foe', foe)}${sideHTML('you', you)}<div class="bt-banner" aria-hidden="true"></div></div>
      <div class="bt-log" aria-live="polite"></div>
      <div class="moves"></div>
    </div>`, { onClose: () => { battleLocked = false; inBattle = false; music.setScene(timeScene()); renderArena(); } });
  const root = $('.battle', body);
  const sideEl = (side) => $(`.side.${side}`, root);
  const logLine = (html) => {
    const l = $('.bt-log', root);
    l.insertAdjacentHTML('beforeend', `<div>${html}</div>`);
    while (l.children.length > 3) l.firstChild.remove();
  };

  function drawSide(side, { enter = false } = {}) {
    const f = battle.active(b[side]);
    const el = sideEl(side);
    el.style.setProperty('--tc', typeColor(f.e));
    $('.pl-name', el).innerHTML = `${esc(f.e.n)} <small>LV${f.lv}</small> <span class="fglyphs">${glyphs(f.k)}</span>${f.holo ? ' <em class="holo-txt">HOLO</em>' : ''}`;
    const img = $('.fighter img', el);
    img.src = artURL(f.e);
    $('.fighter', el).classList.toggle('holo', f.holo);
    $('.fighter', el).classList.remove('faint', 'hurt', 'lunge', 'guarding');
    if (enter) { $('.fighter', el).classList.remove('enter'); void el.offsetWidth; $('.fighter', el).classList.add('enter'); }
    drawHP(side);
    $$('.bench i', el).forEach((pip, i) => { pip.className = b[side].team[i].hp <= 0 ? 'out' : i === b[side].i ? 'on' : ''; });
  }
  function drawHP(side) {
    const f = battle.active(b[side]);
    const el = sideEl(side);
    const pct = (f.hp / f.maxHp) * 100;
    const bar = $('.hp span', el);
    bar.style.width = `${pct}%`;
    bar.className = pct < 25 ? 'low' : pct < 55 ? 'mid' : '';
    $('.hp-num', el).textContent = `${f.hp}/${f.maxHp}`;
    $('.charge', el).innerHTML = Array.from({ length: battle.CHARGE_MAX }, (_, i) => `<i class="${i < f.charge ? 'on' : ''}"></i>`).join('');
  }
  function drawMoves(enabled = true) {
    const f = battle.active(b.you);
    const foeF = battle.active(b.foe);
    const hint = (t) => { const m = battle.mult(t, foeF.types); return m > 1 ? '<em class="sup">super</em>' : m < 1 ? '<em class="weak">weak</em>' : ''; };
    const ready = f.charge >= battle.CHARGE_MAX;
    $('.moves', root).innerHTML = `${f.types.map((t) => `<button type="button" class="move" data-move="strike:${t}" style="--tc:${TYPES[t].color}" ${enabled ? '' : 'disabled'}>${glyph(t)}<span>${TYPES[t].name} strike</span>${hint(t)}</button>`).join('')}
      <button type="button" class="move guard" data-move="guard" ${enabled ? '' : 'disabled'}><svg viewBox="0 0 24 24" class="glyph"><path d="M12 3 4 6v6c0 4.4 3.4 8 8 9 4.6-1 8-4.6 8-9V6Z"/></svg><span>Guard</span><em>+1 charge</em></button>
      <button type="button" class="move od${ready ? ' ready' : ''}" data-move="overdrive" ${enabled && ready ? '' : 'disabled'}><svg viewBox="0 0 24 24" class="glyph"><path d="M13.5 2 5 13.5h6.2L10 22l9-12h-6.2Z"/></svg><span>Overdrive</span><em>${ready ? 'ready!' : `${f.charge}/${battle.CHARGE_MAX}`}</em></button>`;
  }

  drawSide('foe', { enter: true });
  drawSide('you', { enter: true });
  drawMoves();
  logLine(`<b>${esc(foeName)}</b> sends out <b>${esc(battle.active(b.foe).e.n)}</b>!`);
  logLine(`Go, <b>${esc(battle.active(b.you).e.n)}</b>!`);

  root.addEventListener('click', async (ev) => {
    const btn = ev.target.closest('[data-move]');
    if (btn && !btn.disabled && !b.over && !battleLocked) {
      battleLocked = true;
      drawMoves(false);
      const events = battle.playTurn(b, btn.dataset.move);
      for (const e of events) { await playEvent(e); if (!body.isConnected) return; }
      battleLocked = false;
      if (b.over) finish(b.winner === 'you');
      else drawMoves();
      return;
    }
    const act = ev.target.closest('[data-bt]');
    if (act?.dataset.bt === 'again') { closeSheet(); startBattle(kind); }
  });

  async function playEvent(e) {
    const fx = reducedMotion() ? 0.3 : 1;
    if (e.t === 'guard') {
      const el = sideEl(e.side);
      $('.fighter', el).classList.add('guarding');
      sfx.guard();
      logLine(`<b>${esc(e.name)}</b> braces behind a guard.`);
      drawHP(e.side);
      await sleep(600 * fx);
    } else if (e.t === 'attack') {
      const att = $('.fighter', sideEl(e.side));
      const tgt = $('.fighter', sideEl(e.target));
      att.classList.remove('lunge'); void att.offsetWidth; att.classList.add('lunge');
      if (e.move === 'overdrive') { sfx.overdrive(); root.classList.add('od-flash'); setTimeout(() => root.classList.remove('od-flash'), 500); }
      await sleep(220 * fx);
      const big = e.crit || e.move === 'overdrive' || e.mult > 1;
      FX.typeFX(e.type, tgt, { big });
      if (big) FX.shake($('.field', root));
      tgt.classList.remove('hurt'); void tgt.offsetWidth; tgt.classList.add('hurt');
      sfx.hit(e.mult);
      buzz(e.crit || e.move === 'overdrive' ? [30, 30, 70] : e.mult > 1 ? 40 : 20);
      drawHP(e.target);
      drawHP(e.side);
      popGain(`-${e.dmg}`, tgt, { cls: e.mult > 1 ? 'dmg super' : e.mult < 1 ? 'dmg weak' : 'dmg' });
      const note = [e.crit ? 'Critical!' : '', e.mult > 1 ? 'Super effective!' : e.mult < 1 ? 'Resisted…' : ''].filter(Boolean).join(' ');
      logLine(`<b>${esc(e.name)}</b> used <span class="tcx" style="--tc:${TYPES[e.type].color}">${MOVE_NAME(e.move === 'overdrive' ? 'overdrive' : e.move)}</span> · ${e.dmg} dmg${note ? ` · <em>${note}</em>` : ''}`);
      if (e.mult > 1 || e.crit || e.move === 'overdrive') banner(e.move === 'overdrive' ? 'OVERDRIVE' : e.crit ? 'CRITICAL' : 'SUPER');
      $('.fighter', sideEl(e.target)).classList.remove('guarding');
      await sleep(750 * fx);
    } else if (e.t === 'faint') {
      const el = sideEl(e.side);
      FX.koStars($('.fighter', el));
      $('.fighter', el).classList.add('faint');
      sfx.faint();
      logLine(`<b>${esc(e.name)}</b> is out of the fight!`);
      $$('.bench i', el).forEach((pip, i) => { if (b[e.side].team[i].hp <= 0) pip.className = 'out'; });
      await sleep(800 * fx);
    } else if (e.t === 'enter') {
      drawSide(e.side, { enter: true });
      logLine(`${e.side === 'you' ? 'Go' : 'Next up'}, <b>${esc(e.name)}</b>!`);
      await sleep(550 * fx);
    }
  }
  function banner(text) {
    const el = $('.bt-banner', root);
    el.textContent = text;
    el.classList.remove('show'); void el.offsetWidth; el.classList.add('show');
  }

  function finish(won) {
    const xpBefore = s.xp;
    const reward = battle.settle(s, kind, won);
    s.shards = (s.shards || 0) + reward.credits;
    earnXP(reward.xp);
    store.save();
    refreshAll(true);
    music.stinger(won ? 'win' : 'lose');
    if (won) FX.confetti(kind === 'rival' && reward.credits ? 130 : 70);
    buzz(won ? [40, 60, 40, 60, 160] : [120]);
    const more = kind === 'wild' || !won;
    $('.moves', root).innerHTML = '';
    $('.bt-log', root).insertAdjacentHTML('afterend', `<div class="bt-result ${won ? 'win' : 'lose'}">
      <span class="dot-text bt-word"><span class="sr">${won ? 'Victory' : 'Defeat'}</span>${dotSVG(won ? 'VICTORY' : 'DEFEAT')}</span>
      <div class="gains">${reward.credits ? `<span class="gain hl">+${reward.credits}◆</span>` : ''}<span class="gain${won ? ' hl' : ''}">+${reward.xp} XP</span>
        ${kind === 'rival' && won && reward.credits ? '<span class="gain hl">rival defeated</span>' : ''}${!reward.credits && won ? `<span class="gain">${kind === 'rival' ? 'rival already beaten today' : 'daily paid wins used'}</span>` : ''}</div>
      ${xpBarHTML(xpBefore, s.xp)}
      <div class="actions">${more ? `<button type="button" class="btn" data-bt="again">${kind === 'rival' ? 'Try again' : 'Battle again'}</button>` : ''}<button type="button" class="btn primary" data-close>Done</button></div>
    </div>`);
    const res = $('.bt-result', root);
    if (won) { burst(res, 3); popGain(reward.credits ? `+${reward.credits}◆` : `+${reward.xp} XP`, res, { delay: 200 }); }
    const lastFoe = battle.active(b.foe);
    const counters = TYPE_IDS.filter((x) => battle.STRONG[x].includes(lastFoe.types[0])).map((x) => TYPES[x].name);
    if (!won) setTimeout(() => toast(`TIP: ${counters.join(' / ').toUpperCase()} BEAT ${TYPES[lastFoe.types[0]].name.toUpperCase()}`), 900);
    setTimeout(() => $('.xpbar', res)?.classList.add('go'), 250);
    res.scrollIntoView({ block: 'nearest', behavior: reducedMotion() ? 'auto' : 'smooth' });
  }
}

// ---- ops
const ACHV_ICON = '<svg viewBox="0 0 24 24"><path d="m12 3 2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7Z"/></svg>';
function renderOps() {
  const s = state();
  const d = game.dailyState(s);
  const streak = game.liveStreak(s);
  const { list, byType } = game.achievements(s);
  const allClaimed = d.missions.every((m) => d.claimed[m.id]);
  const weekDots = Array.from({ length: 7 }, (_, i) => `<i class="${i < Math.min(streak, 7) ? 'on' : ''}"></i>`).join('');

  const ev = game.weeklyEvent(s);
  const evTc = ev.type ? TYPES[ev.type].color : 'var(--accent)';
  const evP = Math.min(ev.goal, ev.progress);
  $('#ops').innerHTML = `
    <section class="card-box chamfer event" style="--tc:${evTc}">
      <div class="box-head"><h3>Weekly event</h3><small>${ev.daysLeft} day${ev.daysLeft === 1 ? '' : 's'} left</small></div>
      <div class="event-body">
        <div class="event-icon">${ev.type ? glyph(ev.type) : `<span>${SETS.find((x) => x.id === ev.set).icon}</span>`}</div>
        <div><p class="event-name">${esc(ev.name)}</p><p class="event-text">${esc(ev.text)} · ${evP}/${ev.goal}</p>
          <div class="prog"><span style="width:${(evP / ev.goal) * 100}%"></span></div></div>
        <div>${ev.claimed ? '<span class="tchip" style="--tc:var(--dim)">done</span>'
          : `<button type="button" class="btn small${evP >= ev.goal ? ' primary' : ''}" data-claim-event ${evP >= ev.goal ? '' : 'disabled'}>+${ev.reward}◆</button>`}</div>
      </div>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Daily orders</h3><small>resets daily</small></div>
      ${d.missions.map((m) => {
        const p = Math.min(m.goal, d.progress[m.id] || 0);
        const claimed = d.claimed[m.id];
        return `<div class="mission${claimed ? ' claimed' : ''}">
          <p>${esc(m.text)} <span style="color:var(--dim)">· ${p}/${m.goal}</span></p>
          <div class="reward">${claimed ? '<span class="tchip" style="--tc:var(--dim)">done</span>'
            : `<button type="button" class="btn small${p >= m.goal ? ' primary' : ''}" data-claim="${m.id}" ${p >= m.goal ? '' : 'disabled'}>+${m.reward}◆</button>`}</div>
          <div class="prog"><span style="width:${(p / m.goal) * 100}%"></span></div>
        </div>`;
      }).join('')}
      <p class="allclear">${allClaimed ? 'All clear ✓' : `All three: +${game.ALL_CLEAR_BONUS}◆ bonus`}</p>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Streak</h3><small>best ${s.streak?.best || 0} days</small></div>
      <div class="streak"><div class="big">${pad(streak, 2)}</div>
        <div><p><b>+5◆ × streak</b> each day</p><div class="days">${weekDots}</div></div></div>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Affinities</h3><small>owned / total</small></div>
      <div class="typegrid">${TYPE_IDS.map((t) => {
        const total = ENTRIES.filter((e) => AFFINITY[e.k].includes(t)).length;
        return `<div style="--tc:${TYPES[t].color}">${glyph(t)}<span><b>${TYPES[t].name}</b></span><em>${byType[t] || 0}<small style="display:inline">/${total}</small></em></div>`;
      }).join('')}</div>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Field map</h3><small>${tagged().length} tagged</small></div>
      ${fieldMapHTML()}
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Badges</h3><small>${list.filter((a) => a.done).length}/${list.length}</small></div>
      <div class="achv">${list.map((a) => `<div class="${a.done ? 'done' : ''}" title="${esc(a.desc)}"><div class="hex">${ACHV_ICON}</div><b>${a.name}</b><span>${a.desc}</span></div>`).join('')}</div>
    </section>`;
}
$('#ops').addEventListener('click', (ev) => {
  if (ev.target.closest('[data-claim-event]')) {
    const btn = ev.target.closest('[data-claim-event]');
    const gained = game.claimEvent(state());
    if (gained) {
      earnXP(game.EVENT_XP);
      store.save(); sfx.reveal(2); buzz([30, 40, 90]);
      popGain(`+${gained}◆`, btn); popGain(`+${game.EVENT_XP} XP`, btn, { cls: 'xp', delay: 200 });
      toast(`EVENT CLEARED · +${gained}◆`); refreshAll(true); flushLevelUps();
    }
    return;
  }
  const pin = ev.target.closest('[data-map-key]');
  if (pin) { openEntry(pin.dataset.mapKey); return; }
  if (ev.target.closest('[data-goto-id]')) { showTab('id'); return; }
  const b = ev.target.closest('[data-claim]');
  if (!b) return;
  const gained = game.claimMission(state(), b.dataset.claim);
  if (gained) {
    earnXP(game.MISSION_XP);
    store.save();
    sfx.coin();
    buzz(25);
    popGain(`+${gained}◆`, b);
    popGain(`+${game.MISSION_XP} XP`, b, { cls: 'xp', delay: 200 });
    toast(`+${gained}◆ CREDITS`);
    refreshAll(true);
    flushLevelUps();
  }
});

// ---- profile
function operatorId() {
  const s = state();
  if (!s.opId) {
    s.opId = Math.floor(Math.random() * 0xffffff).toString(16).toUpperCase().padStart(6, '0');
    store.save();
  }
  return s.opId;
}
function renderProfile() {
  queueMicrotask(renderQR);
  const s = state();
  const n = caughtKeys().length;
  const [, rankName] = rankFor(n);
  const next = RANKS.find((r) => r[0] > n);
  $('#rank-sub').textContent = `CLEARANCE: ${rankName.toUpperCase()}`;
  const forms = Object.values(s.caught).reduce((a, rec) => a + (rec.forms ? rec.forms.length : 0), 0);
  const byRarity = [1, 2, 3, 4].map((r) => {
    const all = ENTRIES.filter((e) => e.r === r);
    return `${all.filter((e) => s.caught[e.k]).length}/${all.length}`;
  });

  $('#profile').innerHTML = `
    <section class="card-box chamfer idcard">
      <div class="id-top"><span>OPERATOR</span><b>OP-${operatorId()}</b></div>
      <label for="collector-name">Callsign</label>
      <input id="collector-name" maxlength="18" placeholder="Enter name" value="${esc(s.name)}" autocomplete="nickname" spellcheck="false">
      <p class="op-title">${esc(loot.titleName(s))}</p>
      <div class="rank">Rank <b>${esc(rankName)}</b> · Lv ${opLevel()}
        <small>${next ? `${next[0] - n} more cards to ${next[1]}` : 'Every card collected. Legendary.'}</small></div>
      <div class="idstats">
        <div><b>${pad(n)}</b><span>Cards</span></div>
        <div><b>${pad(forms)}</b><span>Forms</span></div>
        <div><b>${xp()}</b><span>XP</span></div>
        <div><b>${s.scans}</b><span>Scans</span></div>
      </div>
      <p class="about" style="margin:10px 0 0">Common ${byRarity[0]} · Uncommon ${byRarity[1]} · Rare ${byRarity[2]} · Legendary ${byRarity[3]}</p>
      <div class="barcode" aria-hidden="true"></div>
    </section>

    ${accountHTML()}
    ${supplyHTML()}
    ${lockerHTML()}

    <section class="card-box chamfer">
      <div class="box-head"><h3>Config</h3></div>
      <div class="toggle"><span>Sky<small>Night sky from 7pm to 6am</small></span>
        <span class="seg-pick" role="group" aria-label="Sky">${['auto', 'day', 'night'].map((m) => `<button type="button" data-theme-mode="${m}" aria-pressed="${(s.settings.theme || 'auto') === m}">${m[0].toUpperCase() + m.slice(1)}</button>`).join('')}</span></div>
      <label class="toggle"><span>Voice<small>Read new cards aloud</small></span>
        <input type="checkbox" class="switch" data-setting="voice" ${s.settings.voice ? 'checked' : ''}></label>
      <label class="toggle"><span>Music<small>Day, night & battle themes</small></span>
        <input type="checkbox" class="switch" data-setting="music" ${s.settings.music !== false ? 'checked' : ''}></label>
      <label class="toggle vol"><span>Music volume</span>
        <input type="range" id="music-vol" min="0" max="100" step="5" value="${Math.round((s.settings.musicVol ?? 0.6) * 100)}" aria-label="Music volume"></label>
      <label class="toggle"><span>Sound FX</span>
        <input type="checkbox" class="switch" data-setting="sound" ${s.settings.sound ? 'checked' : ''}></label>
      <label class="toggle"><span>Haptics</span>
        <input type="checkbox" class="switch" data-setting="haptics" ${s.settings.haptics !== false ? 'checked' : ''}></label>
      <label class="toggle"><span>Card tilt</span>
        <input type="checkbox" class="switch" data-setting="tilt" ${s.settings.tilt ? 'checked' : ''}></label>
      <label class="toggle"><span>Location tags<small>Map your finds (~1 km, on this phone)</small></span>
        <input type="checkbox" class="switch" data-setting="location" ${s.settings.location ? 'checked' : ''}></label>
    </section>

    <section class="card-box chamfer compare">
      <div class="box-head"><h3>Compare</h3><button type="button" class="help-btn" data-help="compare" aria-label="How compare works">?</button></div>
      <div class="qr" id="qr" aria-label="QR code with your collection link"></div>
      <div class="btn-row" style="justify-content:center">
        <button type="button" class="btn small primary" data-act="share">Share link</button>
        <button type="button" class="btn small" data-act="copy">Copy link</button>
      </div>
      <form class="paste">
        <input type="text" id="friend-code" placeholder="Paste a friend's link or code" autocomplete="off" spellcheck="false">
        <button type="submit" class="btn small">Compare</button>
      </form>
    </section>

    <section class="card-box chamfer">
      <div class="box-head"><h3>Backup</h3></div>
      <p class="about">Saved on this phone only.</p>
      <div class="btn-row">
        <button type="button" class="btn small" data-act="export">Export</button>
        <label class="btn small">Import<input type="file" accept="application/json,.json" data-act="import" hidden></label>
        <button type="button" class="btn small" data-act="reset">Wipe binder</button>
      </div>
    </section>

    <p class="center-row"><button type="button" class="btn small" data-help="intro">How to play</button> <button type="button" class="btn small" data-help="about">About</button></p>`;
}
$('#profile').addEventListener('input', (ev) => {
  if (ev.target.id === 'collector-name') { state().name = ev.target.value.trim(); store.save(); }
  if (ev.target.id === 'music-vol') { state().settings.musicVol = Number(ev.target.value) / 100; music.setVolume(state().settings.musicVol); store.save(); }
});
$('#profile').addEventListener('change', async (ev) => {
  const t = ev.target;
  if (t.dataset.setting) {
    state().settings[t.dataset.setting] = t.checked;
    store.save();
    if (t.dataset.setting === 'sound' && t.checked) sfx.again();
    if (t.dataset.setting === 'music') { music.setEnabled(t.checked); if (t.checked) { music.setScene(timeScene()); music.unlock().then(updateMusicBtn); } updateMusicBtn(); }
    if (t.dataset.setting === 'location' && t.checked) {
      const pos = await getPosition();
      if (!pos) {
        t.checked = false;
        state().settings.location = false;
        store.save();
        toast('LOCATION PERMISSION NEEDED');
      } else toast('LOCATION TAGS ON');
    }
  }
  if (t.dataset.act === 'import' && t.files[0]) {
    try {
      await store.importBackup(await t.files[0].text());
      toast('BINDER RESTORED');
      refreshAll();
    } catch (err) {
      toast(err.message || 'Could not read that file');
    }
    t.value = '';
  }
});
$('#profile').addEventListener('submit', (ev) => {
  ev.preventDefault();
  const friend = parseCode($('#friend-code').value);
  if (!friend) { toast('THAT CODE ISN\'T A WILDDEX LINK'); return; }
  openCompare(friend);
});
$('#profile').addEventListener('click', async (ev) => {
  const s = state();
  const tm = ev.target.closest('[data-theme-mode]');
  if (tm) { s.settings.theme = tm.dataset.themeMode; store.save(); applyTime(); sfx.click(); renderProfile(); return; }
  const tb = ev.target.closest('[data-title]');
  if (tb) { loot.locker(s).title = tb.dataset.title; store.save(); sfx.click(); buzz(10); refreshAll(); renderProfile(); return; }
  const fb = ev.target.closest('[data-frame]');
  if (fb) { loot.locker(s).frame = fb.dataset.frame; store.save(); sfx.click(); buzz(10); refreshAll(); renderProfile(); return; }
  const cb = ev.target.closest('[data-crate]');
  if (cb) { crateFlow(cb.dataset.crate === 'free'); return; }
  const act = ev.target.closest('[data-act]');
  if (!act) return;
  if (act.dataset.act === 'share') {
    const url = shareURL();
    try {
      if (navigator.share) await navigator.share({ title: 'My WildDex', text: `Compare WildDex collections with ${state().name || 'me'}!`, url });
      else { await navigator.clipboard.writeText(url); toast('LINK COPIED'); }
    } catch { /* share sheet dismissed */ }
  }
  if (act.dataset.act === 'copy') {
    try { await navigator.clipboard.writeText(shareURL()); toast('LINK COPIED'); } catch { $('#friend-code').value = shareURL(); toast('COPY THE LINK FROM THE BOX'); }
  }
  if (act.dataset.act === 'export') {
    const json = await store.exportBackup();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([json], { type: 'application/json' }));
    a.download = `wilddex-backup-${game.today()}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }
  if (act.dataset.act === 'reset' && confirm('Wipe every card, shard and streak? This can\'t be undone unless you exported a backup.')) {
    await store.resetAll();
    toast('BINDER WIPED');
    refreshAll();
  }
  if (act.dataset.act === 'intro') showIntro();
});

// ---- accounts & cloud save (optional; only when Firebase is configured)
const SYNC_KEY = 'wilddex.sync'; // which account this phone last synced with
const syncMeta = () => { try { return JSON.parse(localStorage.getItem(SYNC_KEY)) || {}; } catch { return {}; } };
const setSyncMeta = (m) => { try { localStorage.setItem(SYNC_KEY, JSON.stringify(m)); } catch { /* ignore */ } };
let user = null;
let syncState = 'idle'; // idle | syncing | synced | error | offline | choosing
let syncTimer = null;
const hasProgress = (st) => Object.keys(st?.caught || {}).length > 0 || (st?.xp || 0) > 0;
const GOOGLE_G = '<svg viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.9 6.2C12.5 13.6 17.8 9.5 24 9.5z"/><path fill="#4285F4" d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.4c-.5 2.9-2.2 5.3-4.6 6.9l7.4 5.8c4.3-4 6.9-9.9 6.9-17.2z"/><path fill="#FBBC05" d="M10.6 28.5c-.5-1.4-.8-2.9-.8-4.5s.3-3.1.8-4.5l-7.9-6.2C1 16.6 0 20.2 0 24s1 7.4 2.7 10.7l7.9-6.2z"/><path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.4-5.8c-2.1 1.4-4.8 2.3-8.5 2.3-6.2 0-11.5-4.1-13.4-9.8l-7.9 6.2C6.6 42.6 14.6 48 24 48z"/></svg>';
const APPLE_LOGO = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M16.4 12.6c0-2.4 2-3.6 2.1-3.7-1.1-1.7-2.9-1.9-3.5-1.9-1.5-.2-2.9.9-3.7.9-.8 0-1.9-.9-3.2-.8-1.6 0-3.1 1-4 2.4-1.7 3-.4 7.3 1.2 9.7.8 1.2 1.8 2.5 3 2.4 1.2 0 1.7-.8 3.1-.8 1.5 0 1.9.8 3.2.8 1.3 0 2.2-1.2 3-2.4.9-1.4 1.3-2.7 1.3-2.8 0 0-2.5-1-2.5-3.8zM14 5.5c.7-.8 1.1-1.9 1-3-1 0-2.1.7-2.8 1.5-.6.7-1.2 1.8-1 2.9 1.1.1 2.1-.6 2.8-1.4z"/></svg>';

function accountHTML() {
  if (!account.enabled()) return '';
  if (!user) {
    return `<section class="card-box chamfer account">
      <div class="box-head"><h3>Account</h3><small>optional</small></div>
      <p class="about">Sign in to back up your cards and carry them to any phone.</p>
      <div class="btn-row"><button type="button" class="btn small primary" data-auth="signin">Sign in</button><button type="button" class="btn small" data-auth="signup">Create account</button></div>
    </section>`;
  }
  const name = user.displayName || (user.email || 'Operator').split('@')[0];
  const needsVerify = user.email && !user.emailVerified && user.providerData.some((p) => p.providerId === 'password');
  return `<section class="card-box chamfer account">
    <div class="box-head"><h3>Account</h3><small>signed in</small></div>
    <div class="acct-row"><span class="avatar">${user.photoURL ? `<img src="${esc(user.photoURL)}" alt="" referrerpolicy="no-referrer">` : esc(name[0].toUpperCase())}</span>
      <div><b>${esc(name)}</b><small>${esc(user.email || 'signed in')}</small></div></div>
    <p class="sync-line" id="sync-line">${syncLine()}</p>
    ${needsVerify ? '<p class="about verify">Check your inbox to verify your email. <button type="button" class="link" data-acct="verify">Resend</button></p>' : ''}
    <div class="btn-row"><button type="button" class="btn small" data-acct="sync">Sync now</button><button type="button" class="btn small" data-acct="signout">Sign out</button></div>
    <p class="about fine">Card photos stay on this phone. <button type="button" class="link" data-acct="delete">Delete account</button></p>
  </section>`;
}
function syncLine() {
  const at = syncMeta().at;
  const ago = at ? Math.max(0, Math.round((Date.now() - at) / 60000)) : null;
  return {
    idle: '<i class="dot"></i>Cloud save ready',
    syncing: '<i class="dot busy"></i>Saving to cloud…',
    synced: `<i class="dot ok"></i>Saved to cloud${ago != null ? ` · ${ago < 1 ? 'just now' : `${ago} min ago`}` : ''}`,
    error: '<i class="dot bad"></i>Couldn\'t save — will retry',
    offline: '<i class="dot bad"></i>Offline — saves when you\'re back online',
    choosing: '<i class="dot busy"></i>Choose which save to keep',
  }[syncState];
}
function renderAccountStatus() {
  const el = $('#sync-line');
  if (el) el.innerHTML = syncLine();
}

async function pushNow() {
  if (!user) return;
  clearTimeout(syncTimer);
  syncTimer = null;
  syncState = 'syncing';
  renderAccountStatus();
  try {
    await account.writeSave(user.uid, state());
    setSyncMeta({ uid: user.uid, at: Date.now() });
    syncState = 'synced';
  } catch (err) {
    syncState = navigator.onLine ? 'error' : 'offline';
    if (syncState === 'error') syncTimer = setTimeout(pushNow, 30000);
  }
  renderAccountStatus();
}
store.onSave(() => {
  if (!user || syncState === 'choosing') return;
  clearTimeout(syncTimer);
  syncTimer = setTimeout(pushNow, 4000);
});
window.addEventListener('online', () => { if (user && syncState === 'offline') pushNow(); });
document.addEventListener('visibilitychange', () => { if (document.hidden && syncTimer && user) pushNow(); });

// Load a cloud save into this phone (keeping this phone's own settings).
function adopt(cloudState) {
  store.replaceState({ ...cloudState, settings: state().settings }, { quiet: true });
  setSyncMeta({ uid: user.uid, at: Date.now() });
  syncState = 'synced';
  applyTime();
  refreshAll();
}

async function reconcile(u) {
  syncState = 'syncing';
  renderAccountStatus();
  let cloud;
  try { cloud = await account.loadSave(u.uid); } catch { syncState = navigator.onLine ? 'error' : 'offline'; renderAccountStatus(); return; }
  if (user !== u) return;
  const local = state();
  const meta = syncMeta();
  if (!cloud || !hasProgress(cloud.state)) { await pushNow(); return; }
  if (!hasProgress(local)) { adopt(cloud.state); toast('PROGRESS LOADED FROM CLOUD'); return; }
  if (meta.uid === u.uid) {
    // This phone already belongs to this account: newest save wins.
    if ((cloud.savedAt || 0) > (local.savedAt || 0)) { adopt(cloud.state); toast('PROGRESS UPDATED FROM CLOUD'); } else await pushNow();
    return;
  }
  chooseSave(local, cloud.state);
}

function saveSummary(st) {
  const n = Object.keys(st.caught || {}).filter((k) => BY_KEY[k]).length;
  return `<b>${n}</b> cards · LV ${game.levelForXP(st.xp || 0)} · ${st.shards || 0}◆`;
}
function chooseSave(local, cloud) {
  syncState = 'choosing';
  renderAccountStatus();
  const body = openSheet(`<div class="detail-head"><p class="eyebrow">Cloud save</p><h2 data-decode>Which save?</h2><span class="sci">both have progress</span></div>
    <p class="fact">This phone and your account have different progress. Pick one to keep — the other will be replaced.</p>
    <div class="save-pick">
      <button type="button" class="save-opt" data-keep="cloud"><span class="so-icon">☁️</span><span><b>Cloud save</b><small>${saveSummary(cloud)}</small></span></button>
      <button type="button" class="save-opt" data-keep="local"><span class="so-icon">📱</span><span><b>This phone</b><small>${saveSummary(local)}</small></span></button>
    </div>`, { onClose: () => { if (syncState === 'choosing') { syncState = 'idle'; renderAccountStatus(); } } });
  body.addEventListener('click', async (ev) => {
    const b = ev.target.closest('[data-keep]');
    if (!b) return;
    if (b.dataset.keep === 'cloud') { adopt(cloud); closeSheet(); toast('CLOUD SAVE LOADED'); } else { syncState = 'idle'; closeSheet(); await pushNow(); toast('THIS PHONE\'S PROGRESS SAVED'); }
  });
}

// ---- sign-in sheet: sign in / create account / forgot password
function openAuth(mode = 'signin', note = '', email = '') {
  const p = account.providerList();
  const titles = { signin: 'Sign in', signup: 'Create account', reset: 'Reset password' };
  const social = mode === 'reset' ? '' : `
    ${p.google ? `<button type="button" class="btn social google" data-social="google">${GOOGLE_G}<span>Continue with Google</span></button>` : ''}
    ${p.apple ? `<button type="button" class="btn social apple" data-social="apple">${APPLE_LOGO}<span>Continue with Apple</span></button>` : ''}
    ${p.google || p.apple ? '<div class="or"><span>or</span></div>' : ''}`;
  const pw = (name, label, auto) => `<label class="fld"><span>${label}</span><span class="pw"><input type="password" name="${name}" autocomplete="${auto}" minlength="8" required><button type="button" class="show" data-show aria-label="Show password">Show</button></span></label>`;
  const body = openSheet(`<div class="detail-head"><p class="eyebrow">Account</p><h2>${titles[mode]}</h2>
      <span class="sci">${mode === 'reset' ? 'we\'ll email you a reset link' : 'back up your cards · play on any phone'}</span></div>
    <div class="auth">
      ${social}
      <form class="auth-form" novalidate>
        ${mode === 'signup' ? `<label class="fld"><span>Callsign</span><input name="name" maxlength="18" autocomplete="nickname" value="${esc(state().name || '')}"></label>` : ''}
        <label class="fld"><span>Email</span><input type="email" name="email" autocomplete="email" inputmode="email" required value="${esc(email)}"></label>
        ${mode === 'signin' ? pw('password', 'Password', 'current-password') : ''}
        ${mode === 'signup' ? pw('password', 'Password · 8+ characters', 'new-password') + pw('confirm', 'Confirm password', 'new-password') : ''}
        <p class="auth-msg${note ? ' ok' : ''}" role="alert">${esc(note)}</p>
        <button type="submit" class="btn primary wide">${{ signin: 'Sign in', signup: 'Create account', reset: 'Send reset link' }[mode]}</button>
      </form>
      ${mode === 'signin' ? '<p class="auth-links"><button type="button" class="link" data-auth="reset">Forgot password?</button></p>' : ''}
      <p class="auth-switch">${mode === 'signin' ? 'New here? <button type="button" class="link" data-auth="signup">Create an account</button>'
        : 'Have an account? <button type="button" class="link" data-auth="signin">Sign in</button>'}</p>
      <p class="about fine">Your cards sync to your account. Card photos stay on this phone.</p>
    </div>`);
  const form = $('.auth-form', body);
  const msg = $('.auth-msg', body);
  const busy = (on) => $$('button, input', body).forEach((el) => { if (!el.matches('[data-close]')) el.disabled = on; });
  const fail = (err) => { msg.className = 'auth-msg'; msg.textContent = typeof err === 'string' ? err : account.friendlyError(err); busy(false); sfx.fail(); };
  const done = (text) => { closeSheet(); if (text) toast(text); };
  body.addEventListener('click', async (ev) => {
    const show = ev.target.closest('[data-show]');
    if (show) { const input = show.previousElementSibling; input.type = input.type === 'password' ? 'text' : 'password'; show.textContent = input.type === 'password' ? 'Show' : 'Hide'; return; }
    const soc = ev.target.closest('[data-social]');
    if (soc) {
      busy(true);
      try { const u = await (soc.dataset.social === 'google' ? account.signInGoogle() : account.signInApple()); if (u) done('SIGNED IN'); } catch (err) { fail(err); }
    }
  });
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = new FormData(form);
    const email = String(f.get('email') || '').trim();
    const password = String(f.get('password') || '');
    if (!email) { fail('Enter your email address.'); return; }
    busy(true);
    try {
      if (mode === 'signin') { await account.signIn(email, password); done('SIGNED IN'); }
      if (mode === 'reset') {
        await account.resetPassword(email);
        closeSheet();
        openAuth('signin', `Reset link sent to ${email}. Check your inbox (and spam).`, email);
      }
      if (mode === 'signup') {
        if (password.length < 8) { fail('Use at least 8 characters for your password.'); return; }
        if (password !== String(f.get('confirm') || '')) { fail('Passwords don\'t match.'); return; }
        const name = String(f.get('name') || '').trim();
        if (name) { state().name = name.slice(0, 18); store.save(); }
        await account.signUp(email, password, name);
        done('ACCOUNT CREATED · CHECK YOUR EMAIL');
      }
    } catch (err) { fail(err); }
  });
  setTimeout(() => (email ? $('input[name=password]', form) : $('input', form))?.focus({ preventScroll: true }), 350);
}

document.addEventListener('click', async (ev) => {
  const a = ev.target.closest('[data-auth]');
  if (a) { sfx.click(); openAuth(a.dataset.auth); return; }
  const act = ev.target.closest('[data-acct]');
  if (!act || !user) return;
  const what = act.dataset.acct;
  if (what === 'sync') { await pushNow(); toast(syncState === 'synced' ? 'SAVED TO CLOUD' : 'COULDN\'T SAVE — TRY AGAIN'); }
  if (what === 'verify') { try { await account.resendVerification(); toast('VERIFICATION EMAIL SENT'); } catch (err) { toast(account.friendlyError(err)); } }
  if (what === 'signout') {
    if (syncTimer) await pushNow();
    await account.signOut();
    toast('SIGNED OUT · PROGRESS KEPT ON THIS PHONE');
  }
  if (what === 'delete' && confirm('Delete your account and its cloud save? Your cards stay on this phone. This can\'t be undone.')) {
    try { await account.deleteAccount(); setSyncMeta({}); toast('ACCOUNT DELETED'); } catch (err) { toast(account.friendlyError(err)); }
  }
});

function startAccounts() {
  if (!account.enabled()) return;
  account.watch((u) => {
    user = u;
    syncState = 'idle';
    if (u) reconcile(u);
    if (currentTab === 'id') renderProfile();
    if (currentTab === 'home') renderHome();
  }).catch(() => { /* offline or SDK blocked: stay in guest mode */ });
}

// ---- supply crates & locker (cosmetics only)
const CRATE_SVG = `<svg viewBox="0 0 120 100" aria-hidden="true">
  <g class="lid"><path class="c-body" d="M14 30 24 16h72l10 14Z"/><path class="c-seam" d="M24 16h72"/></g>
  <path class="c-body" d="M14 30h92v54l-10 10H24L14 84Z"/>
  <path class="c-seam" d="M14 30h92M60 30v64M34 30v64M86 30v64"/>
  <path class="c-glow" d="M14 30h92"/>
  <text x="60" y="68" text-anchor="middle">WD</text></svg>`;

function supplyHTML() {
  const s = state();
  const l = loot.locker(s);
  const canBuy = (s.shards || 0) >= loot.CRATE_COST;
  return `<section class="card-box chamfer supply">
    <div class="box-head"><h3>Supply</h3><button type="button" class="help-btn" data-help="supply" aria-label="About supply crates">?</button></div>
    <div class="crate-row"><div class="crate mini">${CRATE_SVG}${l.crates ? `<i class="crate-count">${l.crates}</i>` : ''}</div>
      <p class="about"><b>Card frames</b> & <b>titles</b>. One free every level-up.</p></div>
    <div class="btn-row">
      ${l.crates ? `<button type="button" class="btn small primary" data-crate="free">Open crate (${l.crates})</button>` : ''}
      <button type="button" class="btn small${!l.crates && canBuy ? ' primary' : ''}" data-crate="buy" ${canBuy ? '' : 'disabled'}>Buy · ${loot.CRATE_COST}◆</button>
    </div>
  </section>`;
}

function lockerHTML() {
  const s = state();
  const l = loot.locker(s);
  const fCount = Object.keys(loot.FRAMES).length;
  const tCount = Object.keys(loot.TITLES).length;
  return `<section class="card-box chamfer locker">
    <div class="box-head"><h3>Locker</h3><small>${l.frames.length}/${fCount} frames · ${l.titles.length}/${tCount} titles</small></div>
    <p class="lbl">Card frame</p>
    <div class="frame-grid">${Object.entries(loot.FRAMES).map(([id, f]) => (l.frames.includes(id)
      ? `<button type="button" class="frame-pick" data-frame="${id}" aria-pressed="${l.frame === id}" style="--tier:${loot.TIERS[f.r]?.color || 'var(--steel)'}"><span class="swatch card skin-${id}"></span><b>${f.name}</b></button>`
      : `<div class="frame-pick locked"><span class="swatch"></span><b>???</b></div>`)).join('')}</div>
    <p class="lbl">Title</p>
    <div class="title-chips">${Object.entries(loot.TITLES).map(([id, t]) => (l.titles.includes(id)
      ? `<button type="button" data-title="${id}" aria-pressed="${l.title === id}" style="--tier:${loot.TIERS[t.r]?.color || 'var(--steel)'}">${esc(t.name)}</button>`
      : '<span class="locked">? ? ?</span>')).join('')}</div>
  </section>`;
}

function crateFlow(free) {
  const s = state();
  const res = loot.openCrate(s, { free });
  if (!res) { toast(free ? 'NO CRATES LEFT' : `NEED ${loot.CRATE_COST}◆`); return; }
  store.save();
  refreshAll(true);
  const tier = loot.TIERS[res.r];
  const body = openSheet(`<div class="detail-head"><p class="eyebrow">Supply crate</p><h2 data-decode>Crack it open</h2><span class="sci">tap the crate</span></div>
    <div class="crate-stage" style="--tier:${tier.color}">
      <div class="crate big" role="button" tabindex="0" aria-label="Tap to open the crate">${CRATE_SVG}</div>
      <p class="crate-hint">TAP ×3</p>
    </div>
    <div class="crate-result" hidden></div>`);
  const crate = $('.crate.big', body);
  const stage = $('.crate-stage', body);
  let hits = 0;
  const tap = () => {
    if (hits >= 3) return;
    hits++;
    crate.classList.remove('hit'); void crate.offsetWidth; crate.classList.add('hit', `h${hits}`);
    sfx.crack(hits);
    buzz(15 * hits);
    $('.crate-hint', body).textContent = hits < 3 ? `TAP ×${3 - hits}` : '';
    if (hits === 3) setTimeout(openIt, 250);
  };
  const openIt = () => {
    stage.classList.add('open', `t${res.r}`);
    burst(stage, res.r >= 4 ? 4 : res.r + 1);
    if (res.r >= 3) setTimeout(() => burst(stage, res.r >= 4 ? 4 : 3), 250);
    flash(res.r >= 4 ? 4 : 0);
    sfx.reveal(res.r);
    buzz(res.r >= 3 ? [40, 50, 40, 50, 160] : [30, 40, 80]);
    const s2 = state();
    const sample = BY_KEY[battle.owned(s2)[0]] || ENTRIES[0];
    const preview = res.kind === 'frame'
      ? `<div class="loot-card"><div class="card r1 skin-${res.id}" style="--tc:${typeColor(sample)}"><div class="card-top"><span>#${pad(sample.no)}</span><span class="tdot"></span></div><div class="card-art"><div class="disc"></div><img src="${artURL(sample)}" alt=""></div><div class="card-name">${esc(sample.n)}</div></div></div>`
      : `<div class="loot-title"><small>operator title</small><b>${esc(res.name)}</b></div>`;
    const r = $('.crate-result', body);
    r.innerHTML = `<p class="loot-tier" style="color:${tier.color}">${tier.name} ${res.kind}</p>
      <p class="loot-name">${esc(res.name)}</p>
      ${preview}
      ${res.dupe ? `<p class="about center">Already in your locker — refunded <b>+${loot.DUPE_REFUND}◆</b>.</p>` : `<p class="about center">${res.kind === 'frame' ? esc(res.desc) : 'Shown on your operator ID.'}</p>`}
      <div class="actions">
        ${!res.dupe ? '<button type="button" class="btn primary" data-equip>Equip</button>' : ''}
        ${loot.locker(s2).crates || (s2.shards || 0) >= loot.CRATE_COST ? `<button type="button" class="btn" data-again>Open another${loot.locker(s2).crates ? '' : ` · ${loot.CRATE_COST}◆`}</button>` : ''}
        <button type="button" class="btn${res.dupe ? ' primary' : ''}" data-close>Done</button>
      </div>`;
    r.hidden = false;
    if (res.dupe) popGain(`+${loot.DUPE_REFUND}◆`, r, { delay: 300 });
    refreshAll(true);
  };
  crate.addEventListener('click', tap);
  crate.addEventListener('keydown', (ev) => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); tap(); } });
  body.addEventListener('click', (ev) => {
    if (ev.target.closest('[data-equip]')) {
      const l = loot.locker(state());
      if (res.kind === 'frame') l.frame = res.id; else l.title = res.id;
      store.save();
      sfx.coin();
      toast(`${res.name.toUpperCase()} EQUIPPED`);
      closeSheet();
      refreshAll();
    }
    if (ev.target.closest('[data-again]')) { closeSheet(); crateFlow(loot.locker(state()).crates > 0); }
  });
}

// ---------------------------------------------------------------- location tags (opt-in)
const fmtLoc = ([lat, lon]) => `${Math.abs(lat).toFixed(2)}°${lat >= 0 ? 'N' : 'S'} ${Math.abs(lon).toFixed(2)}°${lon >= 0 ? 'E' : 'W'}`;
const osmURL = ([lat, lon]) => `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=13/${lat}/${lon}`;

function getPosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      (p) => resolve(p),
      () => resolve(null),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 10 * 60 * 1000 },
    );
  });
}

// Saves a rough (2-decimal ≈ 1 km) position for a capture, without delaying the reveal.
async function tagLocation(key) {
  if (!state().settings.location) return;
  const pos = await getPosition();
  const rec = state().caught[key];
  if (!pos || !rec) return;
  const loc = [Math.round(pos.coords.latitude * 100) / 100, Math.round(pos.coords.longitude * 100) / 100];
  if (!rec.loc) rec.loc = loc;
  rec.lastLoc = loc;
  store.save();
}

const tagged = () => caughtKeys().filter((k) => state().caught[k].loc).map((k) => ({ e: BY_KEY[k], loc: state().caught[k].loc }));

// A self-drawn map of capture spots — no map tiles, nothing leaves the phone.
function fieldMapHTML() {
  const pts = tagged();
  if (!pts.length) {
    return `<p class="about">${state().settings.location
      ? 'Your next finds will appear here.'
      : 'Turn on <b>Location tags</b> to map your finds.'}</p>
      ${state().settings.location ? '' : '<button type="button" class="btn small" data-goto-id>Open config</button>'}`;
  }
  const lats = pts.map((p) => p.loc[0]);
  const lons = pts.map((p) => p.loc[1]);
  const midLat = (Math.min(...lats) + Math.max(...lats)) / 2;
  const k = Math.cos((midLat * Math.PI) / 180);
  let spanX = (Math.max(...lons) - Math.min(...lons)) * k;
  let spanY = Math.max(...lats) - Math.min(...lats);
  const span = Math.max(spanX, spanY * 1.6, 0.04);
  spanX = span; spanY = span / 1.6;
  const cx = ((Math.min(...lons) + Math.max(...lons)) / 2) * k;
  const W = 320; const H = 200; const pad = 22;
  const px = (lon) => W / 2 + ((lon * k - cx) / spanX) * (W - 2 * pad);
  const py = (lat) => H / 2 - ((lat - midLat) / spanY) * (H - 2 * pad);
  // group captures at the same spot
  const spots = new Map();
  for (const p of pts) {
    const id = p.loc.join(',');
    if (!spots.has(id)) spots.set(id, []);
    spots.get(id).push(p);
  }
  const kmAcross = Math.round(span * 111);
  const marks = [...spots.values()].map((group) => {
    const [lat, lon] = group[0].loc;
    const x = px(lon); const y = py(lat);
    const lead = group[0].e;
    return `<g class="pin" data-map-key="${lead.k}" transform="translate(${x.toFixed(1)} ${y.toFixed(1)})">
      <circle r="13" style="stroke:${typeColor(lead)}"/>
      <image href="${artURL(lead)}" x="-10" y="-10" width="20" height="20"/>
      ${group.length > 1 ? `<text x="11" y="-9">${group.length}</text>` : ''}
      <title>${esc(group.map((g) => g.e.n).join(', '))} · ${fmtLoc(group[0].loc)}</title></g>`;
  }).join('');
  return `<svg class="fieldmap" viewBox="0 0 ${W} ${H}" role="img" aria-label="Map of ${pts.length} tagged captures">
      <defs><pattern id="fm-dots" width="10" height="10" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r=".8"/></pattern></defs>
      <rect class="bg" width="${W}" height="${H}"/><rect width="${W}" height="${H}" fill="url(#fm-dots)"/>
      <path class="cross" d="M${W / 2} 0V${H}M0 ${H / 2}H${W}"/>
      ${marks}
      <text class="scale" x="8" y="${H - 8}">≈ ${kmAcross < 1 ? '<1' : kmAcross} km across · ${spots.size} spot${spots.size === 1 ? '' : 's'}</text>
    </svg>
    <p class="about" style="margin-top:8px">Tap a marker · ~1 km · stays on this phone</p>`;
}

// ---------------------------------------------------------------- compare with friends
const b64u = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const unb64u = (str) => Uint8Array.from(atob(str.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((str.length + 3) % 4)), (c) => c.charCodeAt(0));

// v1.<name>.<level>.<bitset over ENTRIES order> — just which cards you own.
function collectionCode() {
  const bits = new Uint8Array(Math.ceil(ENTRIES.length / 8));
  ENTRIES.forEach((e, i) => { if (state().caught[e.k]) bits[i >> 3] |= 1 << (i & 7); });
  const name = (state().name || 'operator').slice(0, 18);
  return `1.${b64u(new TextEncoder().encode(name))}.${opLevel()}.${b64u(bits)}`;
}
const shareURL = () => `${location.origin}${location.pathname}#c=${collectionCode()}`;

function parseCode(text) {
  try {
    const raw = String(text || '').trim();
    const code = raw.includes('#c=') ? decodeURIComponent(raw.split('#c=')[1]) : raw;
    const [v, name, level, bitsStr] = code.split('.');
    if (v !== '1' || !bitsStr) return null;
    const bits = unb64u(bitsStr);
    if (bits.length < Math.ceil(ENTRIES.length / 8)) return null;
    const owned = new Set(ENTRIES.filter((e, i) => bits[i >> 3] & (1 << (i & 7))).map((e) => e.k));
    return { name: new TextDecoder().decode(unb64u(name)).slice(0, 18) || 'operator', level: Math.max(1, Math.min(99, Number(level) || 1)), owned };
  } catch { return null; }
}

async function renderQR() {
  const box = $('#qr');
  if (!box) return;
  try {
    const { default: qrcode } = await import('../vendor/qrcode.mjs');
    const qr = qrcode(0, 'M');
    qr.addData(shareURL());
    qr.make();
    const n = qr.getModuleCount();
    let d = '';
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (qr.isDark(r, c)) d += `M${c} ${r}h1v1h-1z`;
    box.innerHTML = `<svg viewBox="-2 -2 ${n + 4} ${n + 4}" shape-rendering="crispEdges"><rect x="-2" y="-2" width="${n + 4}" height="${n + 4}" fill="#F1F1F4"/><path d="${d}" fill="#242427"/></svg>`;
  } catch { box.textContent = 'QR unavailable — use Share link.'; }
}

function openCompare(friend) {
  const s = state();
  s.compared = (s.compared || 0) + 1;
  s.onboarded = true;
  store.save();
  const mine = new Set(caughtKeys());
  const theirsOnly = ENTRIES.filter((e) => friend.owned.has(e.k) && !mine.has(e.k));
  const mineOnly = ENTRIES.filter((e) => mine.has(e.k) && !friend.owned.has(e.k));
  const both = ENTRIES.filter((e) => mine.has(e.k) && friend.owned.has(e.k));
  const slots = (list, hint) => (list.length
    ? `<div class="slot-grid mini">${list.map((e) => `<button type="button" class="slot${mine.has(e.k) ? '' : ' seen'}" data-key="${e.k}" title="${esc(e.n)}">
        <span class="no">${pad(e.no)}</span><img src="${artURL(e)}" alt="${esc(e.n)}" loading="lazy"></button>`).join('')}</div>`
    : `<p class="about">${hint}</p>`);
  const body = openSheet(`<div class="detail-head">
      <p class="eyebrow">Compare // field partner</p>
      <h2 data-decode>${esc(friend.name)}</h2>
      <span class="sci">LV ${friend.level} · ${friend.owned.size} cards</span>
    </div>
    <div class="compare-stats">
      <div><b>${theirsOnly.length}</b><span>they have · you don't</span></div>
      <div><b>${both.length}</b><span>both</span></div>
      <div><b>${mineOnly.length}</b><span>you have · they don't</span></div>
    </div>
    <section class="panel"><div class="panel-head"><span>Targets — they have, you don't</span><span>${theirsOnly.length}</span></div>
      <div class="panel-body">${slots(theirsOnly, 'Nothing new here — you have every card they do!')}</div></section>
    <section class="panel"><div class="panel-head"><span>Your exclusives</span><span>${mineOnly.length}</span></div>
      <div class="panel-body">${slots(mineOnly, 'They have every card you do.')}</div></section>
    <section class="panel"><div class="panel-head"><span>Both collected</span><span>${both.length}</span></div>
      <div class="panel-body">${slots(both, 'No cards in common yet.')}</div></section>
    <p class="fact">Comparing never adds cards — go find the animals yourself!</p>
    <div class="actions"><button type="button" class="btn primary" data-close>Done</button></div>`);
  body.addEventListener('click', (ev) => {
    const b = ev.target.closest('.slot[data-key]');
    if (b) openEntry(b.dataset.key);
  });
}

// cards anywhere open their detail
document.addEventListener('click', (ev) => {
  const c = ev.target.closest('button.card');
  if (c) openEntry(c.dataset.key);
});

// ---------------------------------------------------------------- home hub
const ICON = {
  rival: '<svg viewBox="0 0 24 24"><path d="M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2"/><path d="M9.5 17.5 21 6V3h-3L6.5 14.5M11 19l-6-6M8 16l-4 4M5 21l-2-2"/></svg>',
  orders: '<svg viewBox="0 0 24 24"><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1M9 10l1.5 1.5L13 9M9 16h6"/></svg>',
  crate: '<svg viewBox="0 0 24 24"><path d="M3 8.5 12 4l9 4.5v9L12 22l-9-4.5Z"/><path d="M3 8.5 12 13l9-4.5M12 13v9"/></svg>',
  streak: '<svg viewBox="0 0 24 24"><path d="M12 22c4.4 0 7-2.9 7-6.6 0-4.2-3.6-6.6-4.6-10.9-2 1.6-3 3.6-3 5.8-1-.8-1.6-1.9-1.9-3.3C7.4 9 5 11.6 5 15.4 5 19.1 7.6 22 12 22Z"/><path d="M12 22c-1.8 0-3-1.3-3-3 0-2 1.6-3 3-5 1.4 2 3 3 3 5 0 1.7-1.2 3-3 3Z"/></svg>',
  event: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/><path d="m12 12.5.9 1.9 2.1.3-1.5 1.4.4 2.1-1.9-1-1.9 1 .4-2.1-1.5-1.4 2.1-.3Z"/></svg>',
  cards: '<svg viewBox="0 0 24 24"><rect x="7" y="3" width="13" height="17" rx="2"/><path d="M4 7v12a2 2 0 0 0 2 2h10"/></svg>',
  scan: '<svg viewBox="0 0 24 24"><path d="M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4"/><circle cx="12" cy="12" r="3.5"/></svg>',
  cloud: '<svg viewBox="0 0 24 24"><path d="M7 18h10a4 4 0 0 0 .6-8A6 6 0 0 0 6 9.5 4.3 4.3 0 0 0 7 18Z"/><path d="m9.5 13.5 2 2 3.5-3.5"/></svg>',
};

function renderHome() {
  const s = state();
  const keys = caughtKeys();
  const n = keys.length;
  const recent = keys.slice().sort((a, b) => s.caught[b].last - s.caught[a].last)[0];
  const d = game.dailyState(s);
  const done = d.missions.filter((m) => (d.progress[m.id] || 0) >= m.goal).length;
  const ready = unclaimedMissions();
  const r = n ? battle.dailyRival(s) : null;
  const ev = game.weeklyEvent(s);
  const evP = Math.min(ev.goal, ev.progress);
  const streak = game.liveStreak(s);
  const crates = s.locker?.crates || 0;
  const holos = keys.filter((k) => s.caught[k].holo).length;
  const sectors = SETS.filter((x) => x.entries.every((e) => s.caught[e.k])).length;
  const tile = ({ tab, goto, act, icon, label, value, sub, hot, tc, bar, cls }) => `<button type="button" class="tile${hot ? ' hot' : ''}${bar != null ? ' wide' : ''}${cls ? ` ${cls}` : ''}"
      ${tab ? `data-tab="${tab}"` : ''} ${goto ? `data-goto="${goto}"` : ''} ${act ? `data-home="${act}"` : ''} style="--tc:${tc}">
      <span class="t-icon">${icon}</span><span class="t-label">${label}</span><b class="t-value">${value}</b>
      ${bar != null ? `<i class="t-bar"><em style="width:${Math.max(2, bar * 100)}%"></em></i>` : ''}<small class="t-sub">${sub}</small></button>`;
  $('#home').innerHTML = `
    <div class="home-top"><span class="dot-text home-logo"><span class="sr">WildDex</span>${dotSVG('WILDDEX')}</span>
      <span class="home-btns">${musicBtnHTML()}<button type="button" class="help-btn" data-help="intro" aria-label="How to play">?</button></span></div>
    <section class="hero">
      <div class="hero-rays" aria-hidden="true"></div>
      <div class="hero-card">${recent ? cardHTML(BY_KEY[recent]) : '<div class="card ghost"><b>?</b><small>your first card</small></div>'}</div>
    </section>
    <button type="button" class="scan-cta" data-tab="scan" data-autostart>${ICON.scan}<span>Scan an animal</span></button>
    <div class="tiles">
      ${tile(r ? { tab: 'arena', icon: ICON.rival, label: 'Rival', value: esc(r.name), sub: r.won ? 'Defeated ✓' : `Win +${battle.RIVAL_REWARD.credits}◆`, hot: !r.won, tc: '#FF7A59' }
        : { tab: 'arena', icon: ICON.rival, label: 'Arena', value: 'Locked', sub: 'Catch a card first', tc: '#FF7A59' })}
      ${tile({ tab: 'ops', icon: ICON.orders, label: 'Orders', value: `${done}/${d.missions.length}`, sub: ready ? `${ready} reward${ready > 1 ? 's' : ''} ready!` : 'Daily missions', hot: ready > 0, tc: '#2F9BEA' })}
      ${tile(crates ? { act: 'crate', icon: ICON.crate, label: 'Crates', value: crates, sub: 'Tap to open!', hot: true, tc: '#F2B51D' }
        : { tab: 'id', goto: 'supply', icon: ICON.crate, label: 'Crates', value: 0, sub: `${loot.CRATE_COST}◆ each`, tc: '#F2B51D' })}
      ${tile({ tab: 'ops', icon: ICON.streak, label: 'Streak', value: `${streak}<small>d</small>`, sub: `best ${s.streak?.best || 0}`, hot: streak > 0 && s.streak.last !== game.today(), tc: '#F0A05B', cls: streak > 0 ? 'lit' : '' })}
      ${tile({ tab: 'ops', icon: ICON.event, label: esc(ev.name), value: `${evP}/${ev.goal}`, bar: evP / ev.goal, sub: ev.claimed ? 'Complete ✓' : `${ev.daysLeft}d left`, hot: evP >= ev.goal && !ev.claimed, tc: ev.type ? TYPES[ev.type].color : '#2F9BEA' })}
      ${account.enabled() && !user && n >= 3 ? tile({ tab: 'id', goto: 'account', icon: ICON.cloud, label: 'Back up', value: 'Sign in', sub: 'Keep your cards safe', tc: '#2F9BEA', bar: null }) : ''}
      ${tile({ tab: 'binder', icon: ICON.cards, label: 'Collection', value: `${n}<small>/${ENTRIES.length}</small>`, bar: n / ENTRIES.length, sub: `${holos} holo · ${sectors} sectors`, tc: '#2BB5A0' })}
    </div>`;
}
$('#home').addEventListener('click', (ev) => {
  if (ev.target.closest('[data-home="crate"]')) { sfx.click(); crateFlow(true); return; }
});

// ---------------------------------------------------------------- music button
const NOTE_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/><path class="mute" d="M3 3l18 18"/></svg>';
function musicBtnHTML() {
  const on = state().settings.music !== false;
  return `<button type="button" class="help-btn music-btn${on ? '' : ' off'}${on && music.isPlaying() ? ' live' : ''}" data-music aria-pressed="${on}" aria-label="Music ${on ? 'on' : 'off'}">${NOTE_SVG}</button>`;
}
function updateMusicBtn() {
  $$('[data-music]').forEach((b) => {
    const on = state().settings.music !== false;
    b.classList.toggle('off', !on);
    b.classList.toggle('live', on && music.isPlaying());
    b.setAttribute('aria-pressed', String(on));
    b.setAttribute('aria-label', `Music ${on ? 'on' : 'off'}`);
  });
  const sw = $('[data-setting="music"]');
  if (sw) sw.checked = state().settings.music !== false;
}
document.addEventListener('click', async (ev) => {
  if (!ev.target.closest('[data-music]')) return;
  const s = state();
  if (s.settings.music !== false && (!music.isPlaying() || performance.now() - music.liveSince() < 1200)) {
    // Music is on but the browser hadn't let it start yet: this tap starts it.
    music.setScene(inBattle ? 'battle' : timeScene());
    await music.unlock();
    toast(music.isPlaying() ? 'MUSIC ON' : 'TURN UP THE VOLUME');
    updateMusicBtn();
    return;
  }
  s.settings.music = s.settings.music === false;
  store.save();
  music.setEnabled(s.settings.music);
  if (s.settings.music) { music.setScene(inBattle ? 'battle' : timeScene()); await music.unlock(); }
  toast(s.settings.music ? 'MUSIC ON' : 'MUSIC OFF');
  updateMusicBtn();
});

// ---------------------------------------------------------------- help (the "?" buttons)
const HELP = {
  scan: ['How scanning works', `<ol class="intro-steps">
    <li><div><b>Aim</b><span>Point at one real, live animal — close and well lit.</span></div></li>
    <li><div><b>Sweep</b><span>Tap scan and slide your phone left, then right. Keep the animal in the ring.</span></div></li>
    <li><div><b>Grade</b><span>A steady sweep earns S, A, B or C — better grades pay more ◆ and XP. S doubles holo odds.</span></div></li>
    <li><div><b>Real only</b><span>Photos, prints, screens and videos are rejected: the sweep checks for real 3D depth.</span></div></li></ol>`],
  types: ['Type matchups', `<p class="fact">Each type beats two others for <b>×1.5</b> damage. Hitting a type that beats yours is resisted (<b>×0.67</b>).</p>
    <p class="fact"><b>Guard</b> takes less damage and charges <b>Overdrive</b> — a big hit with your best type. Holo cards get +10% HP, ATK and DEF.</p>`],
  supply: ['Supply crates', `<p class="fact">Crates hold <b>card frames</b> and <b>operator titles</b>. You get one every level-up, or buy one for ${loot.CRATE_COST}◆. Duplicates refund ${loot.DUPE_REFUND}◆.</p>
    <p class="fact">No animals inside — those you scan for real.</p>`],
  compare: ['Compare', '<p class="fact">Show a friend your QR code or send your link. Their WildDex shows which cards you each have. No server, and comparing never gives cards.</p>'],
  about: ['About WildDex', `<p class="fact">Recognition runs on your phone (MobileNet v2) — photos never leave your device, and scanning works offline once loaded.</p>
    <p class="fact">${ENTRIES.length} cards · ${SETS.length} sectors · ${TYPE_IDS.length} types. Pigeons, crows, deer and giraffes aren't in the scanner's vocabulary yet.</p>
    <p class="fact">3D animal art: Microsoft Fluent Emoji (MIT). QR codes: qrcode-generator (MIT). Fonts: Russo One, JetBrains Mono (OFL).</p>`],
};
document.addEventListener('click', (ev) => {
  const h = ev.target.closest('[data-help]');
  if (!h) return;
  sfx.click();
  if (h.dataset.help === 'intro') { showIntro(); return; }
  const [t, html] = HELP[h.dataset.help];
  openSheet(`<div class="detail-head"><p class="eyebrow">Help</p><h2 data-decode>${t}</h2></div>${html}
    <div class="actions"><button type="button" class="btn primary" data-close>Got it</button></div>`);
});

// ---------------------------------------------------------------- tabs
const TAB_ORDER = ['home', 'binder', 'scan', 'arena', 'ops', 'id'];
function showTab(tab) {
  const changed = tab !== currentTab;
  const dir = TAB_ORDER.indexOf(tab) >= TAB_ORDER.indexOf(currentTab) ? 'from-right' : 'from-left';
  currentTab = tab;
  $$('.tabbar [data-tab]').forEach((b) => {
    if (b.dataset.tab === tab) b.setAttribute('aria-current', 'page');
    else b.removeAttribute('aria-current');
  });
  $$('.view').forEach((v) => {
    v.hidden = v.dataset.view !== tab;
    if (!v.hidden && changed) {
      v.classList.remove('enter', 'from-right', 'from-left'); void v.offsetWidth; v.classList.add('enter', dir);
      clearTimeout(v.enterTimer);
      v.enterTimer = setTimeout(() => v.classList.remove('enter', 'from-right', 'from-left'), 900);
    }
  });
  if (tab === 'home') renderHome();
  if (tab === 'scan') { if (wantCamera && !stream) startCamera(); } else stopCamera();
  if (tab === 'binder') renderBinder();
  if (tab === 'arena') renderArena();
  if (tab === 'ops') renderOps();
  if (tab === 'id') renderProfile();
  if (changed) window.scrollTo({ top: 0 });
  try { sessionStorage.setItem('wilddex.tab', tab); } catch { /* ignore */ }
}
// Anything with data-tab navigates: the tab bar, header chips and home tiles.
document.addEventListener('click', (ev) => {
  const b = ev.target.closest('[data-tab]');
  if (!b || b.closest('#sheet')) return;
  sfx.click();
  buzz(8);
  showTab(b.dataset.tab);
  if (b.dataset.goto) setTimeout(() => $(`.${b.dataset.goto}`)?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' }), 80);
  if (b.hasAttribute('data-autostart') && !stream) startCamera();
});
document.addEventListener('visibilitychange', () => {
  music.suspend(document.hidden);
  if (document.hidden) stopCamera();
  else if (currentTab === 'scan' && wantCamera) startCamera();
});

// ---------------------------------------------------------------- scan controls
$('#btn-camera').addEventListener('click', startCamera);
$('#btn-scan').addEventListener('click', () => runScan('camera'));
$('#btn-flip').addEventListener('click', () => {
  facing = facing === 'environment' ? 'user' : 'environment';
  sfx.click();
  if (stream) startCamera();
});
$('#btn-torch').addEventListener('click', async () => {
  const track = stream && stream.getVideoTracks()[0];
  if (!track) return;
  const on = $('#btn-torch').getAttribute('aria-pressed') !== 'true';
  try {
    await track.applyConstraints({ advanced: [{ torch: on }] });
    $('#btn-torch').setAttribute('aria-pressed', String(on));
    sfx.click();
  } catch { log('[err] flashlight unavailable', 'err'); }
});

// ---------------------------------------------------------------- onboarding & boot
function showIntro() {
  openSheet(`<div class="detail-head">
      <p class="eyebrow">How to play</p>
      <h2 data-decode>WildDex</h2>
      <span class="sci">real-world animal card collector</span>
    </div>
    <ol class="intro-steps">
      <li><div><b>Scan</b><span>Point the camera at a real, living animal — a pet, a park bird, a garden bug, a zoo lion — press scan and slowly slide your phone left, then right. Cards only drop for live 3D animals — never photos, screens or videos.</span></div></li>
      <li><div><b>Pull the card</b><span>New species drop a sealed card. Tap to decrypt it and add it to your binder.</span></div></li>
      <li><div><b>Level up</b><span>Scan the same animal again to power up its card — more stars, better stats. A smooth sweep earns a higher sync grade, and any scan can drop a rare <b>holo</b> card.</span></div></li>
      <li><div><b>Battle</b><span>Build a squad of three in the Arena and beat today's rival. Use type matchups: every affinity beats two others.</span></div></li>
      <li><div><b>Complete</b><span>${ENTRIES.length} cards · ${TYPE_IDS.length} affinities · ${SETS.length} sectors. Clear daily orders, keep your streak, level up for supply crates, and spend credits on intel and crates.</span></div></li>
    </ol>
    <p class="fact">All recognition happens on your device. Nothing is uploaded.</p>
    <div class="actions"><button type="button" class="btn primary" data-close>Start collecting</button></div>`, {
    onClose: () => { state().onboarded = true; store.save(); },
  });
}

async function bootSequence() {
  let seen = false;
  try { seen = sessionStorage.getItem('wilddex.booted') === '1'; sessionStorage.setItem('wilddex.booted', '1'); } catch { /* ignore */ }
  if (seen || reducedMotion()) return;
  const boot = $('#boot');
  const pre = $('#boot-log');
  boot.hidden = false;
  let skip = false;
  boot.addEventListener('click', () => { skip = true; }, { once: true });
  const lines = [
    `<span class="hl dot-text"><span class="sr">WILDDEX</span>${dotSVG('WILDDEX')}</span>`,
    `<span class="ok">●</span> card database ... ${ENTRIES.length} signatures`,
    `<span class="ok">●</span> operator binder .. ${caughtKeys().length} captured`,
    `<span class="ok">●</span> affinity matrix .. ${TYPE_IDS.length} types`,
    `<span class="ok">●</span> neural core ...... standby`,
    `<span class="t">&gt; welcome${state().name ? ` back, ${esc(state().name)}` : ', operator'}</span>`,
  ];
  for (const l of lines) {
    if (skip) break;
    pre.innerHTML += `${l}\n`;
    tone(1400, 0, 0.02, 'square', 0.012);
    await sleep(160);
  }
  if (!skip) await sleep(350);
  boot.classList.add('out');
  await sleep(350);
  boot.hidden = true;
}

// Decorative waveform bars (fixed pattern per element so they don't jitter).
function drawWaves() {
  $$('.wave').forEach((el, n) => {
    const count = Number(el.dataset.bars) || 24;
    let x = 97 + n * 31;
    const bars = [];
    for (let i = 0; i < count; i++) {
      x = (x * 1103515245 + 12345) >>> 0;
      const h = 25 + ((x >>> 16) % 75) * (0.55 + 0.45 * Math.sin((i / count) * Math.PI));
      bars.push(`<i style="height:${Math.round(Math.min(100, h))}%"></i>`);
    }
    el.innerHTML = bars.join('');
  });
}

// A friend's link: …/wilddex/#c=<code>
function handleCompareLink() {
  const m = location.hash.match(/^#c=(.+)$/);
  if (!m) return;
  history.replaceState(null, '', location.pathname);
  const friend = parseCode(decodeURIComponent(m[1]));
  if (friend) setTimeout(() => openCompare(friend), state().onboarded ? 0 : 400);
  else toast('THAT LINK ISN\'T A VALID WILDDEX CODE');
}

// Day or night sky: automatic by local time (night 7pm–6am), or fixed in Config.
function applyTime() {
  const mode = state().settings.theme || 'auto';
  const h = new Date().getHours();
  const night = mode === 'night' || (mode === 'auto' && (h >= 19 || h < 6));
  document.documentElement.dataset.time = night ? 'night' : 'day';
  $('meta[name="theme-color"]').setAttribute('content', night ? '#0B1633' : '#62C4FF');
  if (!inBattle) music.setScene(night ? 'night' : 'day');
}
const timeScene = () => (document.documentElement.dataset.time === 'night' ? 'night' : 'day');

async function boot() {
  music.setEnabled(state().settings.music !== false);
  music.setVolume(state().settings.musicVol ?? 0.6);
  applyTime();
  // Browsers (iPhones especially) only start audio from a finished tap, so try
  // on every tap until sound is live — and again after the app is backgrounded.
  const wake = () => { music.unlock().then(updateMusicBtn); };
  ['pointerup', 'touchend', 'click', 'keydown'].forEach((t) => document.addEventListener(t, wake, { capture: true, passive: true }));
  setInterval(applyTime, 5 * 60 * 1000);
  drawWaves();
  $$('.screen-title').forEach(renderDots);
  startTwinkle();
  let tab = 'home';
  try { tab = sessionStorage.getItem('wilddex.tab') || 'home'; } catch { /* ignore */ }
  if (!['home', 'scan', 'binder', 'arena', 'ops', 'id'].includes(tab)) tab = 'home';
  refreshAll();
  log('wilddex_os · on-device mode', 'cmd');
  idle();
  await bootSequence();
  showTab(tab);

  let camBefore = false;
  try { camBefore = localStorage.getItem('wilddex.cam') === '1'; } catch { /* ignore */ }
  if (camBefore && navigator.permissions) {
    try {
      const p = await navigator.permissions.query({ name: 'camera' });
      if (p.state === 'granted') { wantCamera = true; if (currentTab === 'scan') startCamera(); }
    } catch { /* permissions API doesn't know "camera" in some browsers */ }
  }
  if (!state().onboarded) showIntro();
  startAccounts();
  handleCompareLink();
  window.addEventListener('hashchange', handleCompareLink);
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    // When a new version installs, reload once so the update shows straight away.
    const hadController = !!navigator.serviceWorker.controller;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (!hadController || window.__reloading) return;
      window.__reloading = true;
      location.reload();
    });
    navigator.serviceWorker.register('sw.js').then((reg) => reg.update()).catch(() => {});
  }
  if ('speechSynthesis' in window) speechSynthesis.getVoices();
}

boot();
