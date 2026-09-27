import { SETS, ENTRIES, BY_KEY, RARITY } from './dex-data.js';
import { LABELS } from './labels.js';
import * as store from './store.js';
import { loadModel, classify, interpret, isReady, spoofCheck } from './classifier.js';
import { TYPES, TYPE_IDS, AFFINITY, glyph } from './affinity.js';
import * as game from './game.js';
import { detectFrame } from './liveness.js';
import { recordSweep, analyseSweep } from './parallax.js';

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
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
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
  },
  again: () => { tone(880, 0, 0.08); tone(1320, 0.09, 0.14); },
  coin: () => { tone(988, 0, 0.06, 'square', 0.03); tone(1319, 0.06, 0.18, 'square', 0.03); },
  fail: () => { tone(220, 0, 0.14, 'sawtooth', 0.03); tone(165, 0.13, 0.26, 'sawtooth', 0.03); },
  click: () => tone(1800, 0, 0.02, 'square', 0.015),
};

function speak(text) {
  if (!state().settings.voice || !('speechSynthesis' in window)) return;
  try {
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    const voices = speechSynthesis.getVoices();
    u.voice = voices.find((v) => /en-GB/i.test(v.lang)) || voices.find((v) => /^en/i.test(v.lang)) || null;
    u.rate = 1.04;
    u.pitch = 0.8;
    speechSynthesis.speak(u);
  } catch { /* ignore */ }
}
const stopSpeaking = () => { try { speechSynthesis.cancel(); } catch { /* ignore */ } };

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
function log(html, cls = '') {
  const line = document.createElement('div');
  if (cls) line.className = cls;
  line.innerHTML = html;
  term.appendChild(line);
  while (term.children.length > 6) term.firstChild.remove();
  return line;
}
const idle = () => log('&gt; awaiting target');
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

function flash() {
  const f = document.createElement('div');
  f.className = 'flash';
  document.body.appendChild(f);
  setTimeout(() => f.remove(), 450);
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
  const cb = onSheetClose;
  onSheetClose = null;
  if (cb) cb();
  if (lastFocus && lastFocus.focus) lastFocus.focus({ preventScroll: true });
}
$('#sheet').addEventListener('click', (e) => { if (e.target.closest('[data-close]')) closeSheet(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeSheet(); });

function hydratePhotos(root) {
  $$('img[data-photo]', root).forEach(async (img) => {
    const url = await store.photoURL(img.dataset.photo);
    if (url) img.src = url;
  });
}

function enableTilt(el) {
  if (reducedMotion()) return;
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
  const colors = ['#FF7972', '#F5B7B2', '#F3F8F5'];
  const b = document.createElement('div');
  b.className = 'burst';
  const n = 14 + rarity * 8;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + Math.random() * 0.3;
    const d = 90 + Math.random() * (60 + rarity * 30);
    const p = document.createElement('i');
    p.style.setProperty('--x', `${Math.cos(a) * d}px`);
    p.style.setProperty('--y', `${Math.sin(a) * d}px`);
    p.style.setProperty('--c', colors[i % 3]);
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
  const cls = `card r${e.r}${rec ? '' : ' locked'}${intel ? ' intel' : ''}`;
  const attrs = tag === 'button' ? `type="button" data-key="${e.k}" aria-label="#${pad(e.no)} ${known ? esc(e.n) : 'unknown card'}${rec ? '' : ', not captured'}"` : '';
  return `<${tag} class="${cls}"${known ? ` style="--tc:${typeColor(e)}"` : ''} ${attrs}>
    <div class="card-top"><span>#${pad(e.no)}</span><span class="tdot"></span></div>
    ${rec ? `<span class="card-lv">LV${game.levelFor(rec.count)}</span>` : ''}
    <div class="card-art"><div class="disc"></div><img src="${artURL(e)}" alt="" loading="lazy" draggable="false"></div>
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
    <div class="panel-head"><span>Battle stats · Lv ${rec ? lv : '—'}</span><svg class="shape" viewBox="0 0 20 12"><path d="M1 12V4M5 12V1M9 12V6M13 12V2M17 12V7"/></svg></div>
    <div class="panel-body">
      <div class="stats">${game.STAT_KEYS.map((k) => `<div class="stat"><span>${k.toUpperCase()}</span><span class="bar"><span style="width:${st[k]}%"></span></span><b>${rec ? st[k] : '??'}</b></div>`).join('')}</div>
      <div class="pwr"><span>POWER</span><b>${rec ? st.pwr : '???'}</b></div>
      ${rec ? `<div class="lvbar">${next ? `${rec.count}/${next} sightings to Lv ${lv + 1}` : 'Max level reached'}
        <div class="track"><span style="width:${next ? ((rec.count - prevAt) / (next - prevAt)) * 100 : 100}%"></span></div></div>` : ''}
    </div>
  </section>`;
}

function detailHTML(e, { banner = '', gains = '', hideUntilFlip = false, typing = false } = {}) {
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
    ${gains ? `<div class="gains">${gains}</div>` : ''}
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
          <li><span>forms</span> ${rec.forms.length}/${e.c.length}</li></ul>
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
      const html = `[..] loading neural core ${asciiBar(p)} ${Math.round(p * 100)}%`;
      if (line && line.isConnected) line.innerHTML = html; else line = log(html);
    }).then((m) => {
      const msg = `[ok] neural core online · ${ENTRIES.length} signatures`;
      if (line && line.isConnected) { line.className = 'ok'; line.innerHTML = msg; } else log(msg, 'ok');
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
async function runScan(source) {
  if (busy) return;
  busy = true;
  $('#btn-scan').disabled = true;
  try {
    if (!stream) { await startCamera(); if (!stream) return; }
    warmModel().catch(() => {});
    log(`&gt; wilddex.scan --source=${source} --live`, 'cmd');
    log('[..] depth sweep · slide phone sideways');

    // 1) Depth sweep: record ~1.3 s while the player slides the phone.
    vf.classList.add('sweeping');
    $('#sweep-bar').style.width = '0%';
    let tick = 0;
    const frames = await recordSweep(video, {
      onProgress: (p) => { $('#sweep-bar').style.width = `${p * 100}%`; if (tick++ % 3 === 0) tone(900 + p * 700, 0, 0.03, 'square', 0.015); },
    });
    vf.classList.remove('sweeping');
    if (!stream) return;

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
      log(`[err] liveness failed · flat surface · ${d.off}/${d.tracks} depth points`, 'err');
      log('[err] photos, prints & screens can\'t be registered', 'err');
      toast('FLAT IMAGE DETECTED · SCAN A LIVE ANIMAL');
    } else if (d.verdict === 'still') {
      sfx.again();
      log('[warn] no depth signal · slide the phone sideways while scanning', 'warn');
      toast('SLIDE YOUR PHONE SIDEWAYS DURING THE SCAN');
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
  const setDone = isNew && SETS.find((x) => x.id === e.set).entries.every((x) => s.caught[x.k]);
  if (setDone) { shards += 50; gains.push([`sector complete +50◆`, true]); }
  s.shards = (s.shards || 0) + shards;
  store.save();
  refreshAll(true);

  log(isNew
    ? `[ok] new card #${pad(e.no)} ${esc(e.n)} · +${shards}◆`
    : `[ok] sighting #${pad(e.no)} ×${rec.count} · +${shards}◆`, 'ok');
  if (navigator.vibrate) navigator.vibrate(isNew ? [30, 40, 80] : 30);

  const gainsHTML = gains.map(([t, hl], i) => `<span class="gain${hl ? ' hl' : ''}" style="animation-delay:${i * 0.08}s">${esc(t)}</span>`).join('');
  const banner = isNew ? '<span class="banner">▲ New card</span>' : `<span class="banner soft">● Sighting logged ×${rec.count}</span>`;
  const aura = typeColor(e);

  const body = openSheet(`
    <div class="stage${isNew ? ' charging' : ''}" style="--aura:${aura}" ${isNew ? 'role="button" tabindex="0" aria-label="Reveal card"' : ''}>
      <div class="flip${isNew ? ' down' : ''}">
        <div class="face"><div class="tilt">${cardHTML(e, { tag: 'div' })}</div></div>
        ${isNew ? `<div class="back face">${cardBackHTML()}</div>` : ''}
      </div>
    </div>
    <div class="after"${isNew ? ' hidden' : ''}>
      ${detailHTML(e, { banner, gains: gainsHTML, typing: true })}
      <div class="actions">
        <button type="button" class="btn" data-act="speak"><svg viewBox="0 0 24 24"><path d="M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6"/></svg>Play audio</button>
        <button type="button" class="btn primary" data-close>Continue</button>
      </div>
      <div class="subtle-row"><button type="button" class="link" data-act="wrong">misidentified? not a ${esc(e.n.toLowerCase())}</button></div>
    </div>
    ${isNew ? `<div class="actions pre"><button type="button" class="btn primary" data-act="reveal">Decrypt card</button></div>` : ''}`,
  { onClose: resetScanner });

  const after = $('.after', body);
  const startDetail = () => {
    after.hidden = false;
    $$('[data-decode]', after).forEach((el) => decode(el));
    typeOut($('.dex-text .typed', after), e.t);
    if (isNew) setTimeout(() => speak(`${e.n}. ${e.t}`), 400);
    if (missionsDone.length) setTimeout(() => toast('MISSION COMPLETE · CLAIM IN OPS'), 900);
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
      $('.flip', stage).classList.remove('down');
      $('.actions.pre', body)?.remove();
      setTimeout(() => { burst(stage, e.r); sfx.reveal(e.r); flash(); }, 350);
      setTimeout(startDetail, 700);
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
function xp() { return caughtKeys().reduce((sum, k) => sum + RARITY[BY_KEY[k].r].value, 0); }
const opLevel = () => 1 + Math.floor(Math.sqrt(xp() / 20));
function rankFor(n) { let r = RANKS[0]; for (const x of RANKS) if (n >= x[0]) r = x; return r; }
function unclaimedMissions() {
  const d = game.dailyState(state());
  return d.missions.filter((m) => (d.progress[m.id] || 0) >= m.goal && !d.claimed[m.id]).length;
}

let currentTab = 'scan';
function refreshAll(bumpWallet = false) {
  const s = state();
  $('#count-num').textContent = pad(caughtKeys().length);
  $('#count-total').textContent = `/${ENTRIES.length}`;
  $('#streak-num').textContent = game.liveStreak(s);
  $('#op-level').textContent = pad(opLevel(), 2);
  $('#shards').textContent = s.shards || 0;
  if (bumpWallet) { const w = $('.wallet'); w.classList.remove('bump'); void w.offsetWidth; w.classList.add('bump'); }
  $('#ops-badge').hidden = !unclaimedMissions();
  renderRecent();
  if (currentTab === 'binder') renderBinder();
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
const binder = { set: 'all', type: null, sort: 'no', owned: false };
function renderBinder() {
  const s = state();
  const n = caughtKeys().length;
  $('#binder-sub').innerHTML = `${n}<i>/</i>${ENTRIES.length} cards <i>.</i> ${SETS.filter((x) => x.entries.every((e) => s.caught[e.k])).length} sectors`;
  $('#binder-bar').style.width = `${(n / ENTRIES.length) * 100}%`;

  const chip = (id, label, got, total) => `<button type="button" class="chip${got === total && id !== 'all' ? ' done' : ''}" role="tab" data-set="${id}" aria-selected="${binder.set === id}">${label} <small>${got}/${total}</small></button>`;
  $('#set-chips').innerHTML = chip('all', 'All', n, ENTRIES.length) + SETS.map((set) => {
    const got = set.entries.filter((e) => s.caught[e.k]).length;
    return chip(set.id, `${set.icon} ${esc(set.name)}`, got, set.entries.length);
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
  $('#grid').innerHTML = list.map((e) => cardHTML(e)).join('');
  $('#grid-empty').hidden = list.length > 0;
}
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

// ---- ops
const ACHV_ICON = '<svg viewBox="0 0 24 24"><path d="m12 3 2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7Z"/></svg>';
function renderOps() {
  const s = state();
  const d = game.dailyState(s);
  const streak = game.liveStreak(s);
  const { list, byType } = game.achievements(s);
  const allClaimed = d.missions.every((m) => d.claimed[m.id]);
  const weekDots = Array.from({ length: 7 }, (_, i) => `<i class="${i < Math.min(streak, 7) ? 'on' : ''}"></i>`).join('');

  $('#ops').innerHTML = `
    <section class="card-box">
      <div class="box-head"><h3>Daily orders</h3><small>resets at midnight</small></div>
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
      <p class="allclear">${allClaimed ? 'All orders cleared today ✓' : `Clear all three for a +${game.ALL_CLEAR_BONUS}◆ bonus`}</p>
    </section>

    <section class="card-box">
      <div class="box-head"><h3>Streak</h3><small>best ${s.streak?.best || 0} days</small></div>
      <div class="streak"><div class="big">${pad(streak, 2)}</div>
        <div><p>Log a sighting every day to grow your streak. Each day pays <b>+5◆ × streak</b> (max 50).</p><div class="days">${weekDots}</div></div></div>
    </section>

    <section class="card-box">
      <div class="box-head"><h3>Affinities</h3><small>owned / total</small></div>
      <div class="typegrid">${TYPE_IDS.map((t) => {
        const total = ENTRIES.filter((e) => AFFINITY[e.k].includes(t)).length;
        return `<div style="--tc:${TYPES[t].color}">${glyph(t)}<span><b>${TYPES[t].name}</b><small>${TYPES[t].desc}</small></span><em>${byType[t] || 0}<small style="display:inline">/${total}</small></em></div>`;
      }).join('')}</div>
    </section>

    <section class="card-box">
      <div class="box-head"><h3>Badges</h3><small>${list.filter((a) => a.done).length}/${list.length}</small></div>
      <div class="achv">${list.map((a) => `<div class="${a.done ? 'done' : ''}"><div class="hex">${ACHV_ICON}</div><b>${a.name}</b>${a.desc}</div>`).join('')}</div>
    </section>`;
}
$('#ops').addEventListener('click', (ev) => {
  const b = ev.target.closest('[data-claim]');
  if (!b) return;
  const gained = game.claimMission(state(), b.dataset.claim);
  if (gained) {
    store.save();
    sfx.coin();
    toast(`+${gained}◆ DATA SHARDS`);
    refreshAll(true);
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
  const s = state();
  const n = caughtKeys().length;
  const [, rankName] = rankFor(n);
  const next = RANKS.find((r) => r[0] > n);
  $('#rank-sub').innerHTML = `Clearance <i>.</i> ${esc(rankName)}`;
  const forms = Object.values(s.caught).reduce((a, rec) => a + (rec.forms ? rec.forms.length : 0), 0);
  const byRarity = [1, 2, 3, 4].map((r) => {
    const all = ENTRIES.filter((e) => e.r === r);
    return `${all.filter((e) => s.caught[e.k]).length}/${all.length}`;
  });

  $('#profile').innerHTML = `
    <section class="card-box idcard">
      <div class="id-top"><span>OPERATOR</span><b>OP-${operatorId()}</b></div>
      <label for="collector-name">Callsign</label>
      <input id="collector-name" maxlength="18" placeholder="Enter name" value="${esc(s.name)}" autocomplete="nickname" spellcheck="false">
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

    <section class="card-box">
      <div class="box-head"><h3>Config</h3></div>
      <label class="toggle"><span>Voice<small>Narrate new cards aloud</small></span>
        <input type="checkbox" class="switch" data-setting="voice" ${s.settings.voice ? 'checked' : ''}></label>
      <label class="toggle"><span>Sound FX<small>Scanner blips and reveal chimes</small></span>
        <input type="checkbox" class="switch" data-setting="sound" ${s.settings.sound ? 'checked' : ''}></label>
    </section>

    <section class="card-box">
      <div class="box-head"><h3>Backup</h3></div>
      <p class="about">Your binder lives only on this device. Export a backup to move it to a new phone.</p>
      <div class="btn-row">
        <button type="button" class="btn small" data-act="export">Export</button>
        <label class="btn small">Import<input type="file" accept="application/json,.json" data-act="import" hidden></label>
        <button type="button" class="btn small" data-act="reset">Wipe binder</button>
      </div>
    </section>

    <section class="card-box about">
      <div class="box-head"><h3>About</h3></div>
      <p>Recognition runs entirely on your phone with a MobileNet v2 neural net — photos never leave your device, and scanning works offline once loaded.</p>
      <p>${ENTRIES.length} cards across ${SETS.length} sectors and ${TYPE_IDS.length} affinities. Works best with one animal, close and well lit. Each scan checks for real depth (parallax) or movement, so photos, prints and screens are rejected. Pigeons, crows, deer and giraffes aren't in the net's vocabulary yet.</p>
      <p>Card stats are game values. 3D animal art: Microsoft Fluent Emoji (MIT).</p>
      <p><button type="button" class="link" data-act="intro">How to play</button></p>
    </section>`;
}
$('#profile').addEventListener('input', (ev) => {
  if (ev.target.id === 'collector-name') { state().name = ev.target.value.trim(); store.save(); }
});
$('#profile').addEventListener('change', async (ev) => {
  const t = ev.target;
  if (t.dataset.setting) {
    state().settings[t.dataset.setting] = t.checked;
    store.save();
    if (t.dataset.setting === 'sound' && t.checked) sfx.again();
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
$('#profile').addEventListener('click', async (ev) => {
  const act = ev.target.closest('[data-act]');
  if (!act) return;
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

// cards anywhere open their detail
document.addEventListener('click', (ev) => {
  const c = ev.target.closest('button.card');
  if (c) openEntry(c.dataset.key);
});

// ---------------------------------------------------------------- tabs
function showTab(tab) {
  const changed = tab !== currentTab;
  currentTab = tab;
  $$('.tabs button').forEach((b) => {
    if (b.dataset.tab === tab) b.setAttribute('aria-current', 'page');
    else b.removeAttribute('aria-current');
  });
  $$('.view').forEach((v) => { v.hidden = v.dataset.view !== tab; });
  if (tab === 'scan') { if (wantCamera && !stream) startCamera(); } else stopCamera();
  if (tab === 'binder') renderBinder();
  if (tab === 'ops') renderOps();
  if (tab === 'id') renderProfile();
  if (changed) window.scrollTo({ top: 0 });
  try { sessionStorage.setItem('wilddex.tab', tab); } catch { /* ignore */ }
}
$('.tabs').addEventListener('click', (ev) => {
  const b = ev.target.closest('button[data-tab]');
  if (b) { sfx.click(); showTab(b.dataset.tab); }
});
document.addEventListener('visibilitychange', () => {
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
      <li><div><b>Scan</b><span>Point the camera at a real, living animal — a pet, a park bird, a garden bug, a zoo lion — press scan and slowly slide your phone sideways. Cards only drop for live 3D animals, never photos or screens.</span></div></li>
      <li><div><b>Pull the card</b><span>New species drop a sealed card. Tap to decrypt it and add it to your binder.</span></div></li>
      <li><div><b>Level up</b><span>Scan the same animal again to level its card and boost its stats. Every sighting earns ◆ data shards.</span></div></li>
      <li><div><b>Complete</b><span>${ENTRIES.length} cards · ${TYPE_IDS.length} affinities · ${SETS.length} sectors. Clear daily orders, keep your streak, and spend shards to decrypt intel on cards you haven't found.</span></div></li>
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
    '<span class="hl">WILD≋DEX</span>',
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

async function boot() {
  let tab = 'scan';
  try { tab = sessionStorage.getItem('wilddex.tab') || 'scan'; } catch { /* ignore */ }
  if (!['scan', 'binder', 'ops', 'id'].includes(tab)) tab = 'scan';
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
  if ('serviceWorker' in navigator && location.protocol !== 'file:') navigator.serviceWorker.register('sw.js').catch(() => {});
  if ('speechSynthesis' in window) speechSynthesis.getVoices();
}

boot();
