import { SETS, ENTRIES, BY_KEY, RARITY } from './dex-data.js';
import { LABELS } from './labels.js';
import * as store from './store.js';
import { loadModel, classify, interpret, isReady } from './classifier.js';

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pad = (n) => String(n).padStart(3, '0');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const title = (s) => s.replace(/(^|[\s-])([a-z])/g, (m, a, b) => a + b.toUpperCase());
const snake = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
const isoDate = (ts) => new Date(ts).toISOString().slice(0, 10);
const state = () => store.getState();
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const RANKS = [
  [0, 'Rookie'],
  [1, 'Novice Collector'],
  [10, 'Explorer'],
  [25, 'Field Researcher'],
  [50, 'Naturalist'],
  [100, 'Wildlife Expert'],
  [175, 'Master Collector'],
  [ENTRIES.length, 'WildDex Champion'],
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
  success: () => {
    [523.25, 659.25, 783.99, 1046.5, 1318.5].forEach((f, i) => tone(f, i * 0.07, 0.18, 'square', 0.03));
    tone(1568, 0.4, 0.5, 'triangle', 0.05);
  },
  again: () => { tone(880, 0, 0.08); tone(1320, 0.09, 0.14); },
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
function decode(el, text = el.dataset.text || el.textContent, dur = 650) {
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

// Types text into el; the full text is available to screen readers at once.
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
  toastTimer = setTimeout(() => t.classList.remove('show'), 2600);
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

// ---------------------------------------------------------------- stamps & entries
function regmark(ts) {
  const d = new Date(ts);
  const date = `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getFullYear()).slice(2)}`;
  return `<svg class="regmark" viewBox="0 0 120 64" aria-hidden="true">
    <rect x="3" y="3" width="114" height="58" stroke-width="3"/><rect x="9" y="9" width="102" height="46" stroke-width="1"/>
    <text x="60" y="27" font-size="13" text-anchor="middle" letter-spacing="1.2">REGISTERED</text>
    <text x="60" y="45" font-size="12" text-anchor="middle" letter-spacing="1.5">✓ ${date}</text>
  </svg>`;
}

// Deterministic "encrypted" hex noise for locked stamps.
function noise(no) {
  let x = (no * 2654435761) >>> 0;
  let out = '';
  for (let i = 0; i < 90; i++) {
    x = (x * 1103515245 + 12345) >>> 0;
    out += '0123456789ABCDEF'[(x >>> 16) & 15];
  }
  return out;
}

function stampHTML(e, { big = false } = {}) {
  const rec = state().caught[e.k];
  const cls = `stamp-wrap r${e.r}${rec ? '' : ' locked'}`;
  const img = rec && rec.photo
    ? `<img data-photo="${e.k}" alt="Your photo of a ${esc(e.n)}">`
    : `<span class="noise" aria-hidden="true">${noise(e.no)}</span><span class="ghost" aria-hidden="true">${e.e}</span><span class="enc">ENCRYPTED</span>`;
  const open = big ? '<div' : '<button type="button"';
  const close = big ? '</div>' : '</button>';
  return `${open} class="${cls}" data-key="${e.k}" ${big ? '' : `aria-label="No. ${e.no} ${rec ? esc(e.n) : 'not yet registered'}"`}>
      <div class="stamp"><div class="stamp-inner">
        <div class="stamp-img">${img}</div>
        <div class="stamp-meta"><span>#${pad(e.no)}</span><span class="val">${RARITY[e.r].value}XP</span></div>
        <div class="stamp-name">${rec ? esc(e.n) : '■■■■■■'}</div>
        <div class="rbar"></div>
      </div></div>
      ${rec ? regmark(rec.first) : ''}
    ${close}`;
}

function setOf(e) { return SETS.find((s) => s.id === e.set); }
const setIndex = (set) => String(SETS.indexOf(set) + 1).padStart(2, '0');

function codeBlock(obj) {
  const lines = Object.entries(obj).map(([k, v]) => {
    const val = typeof v === 'number'
      ? `<span class="n">${v}</span>`
      : Array.isArray(v)
        ? `<span class="p">[</span>${v.map((x) => `<span class="s">"${esc(x)}"</span>`).join('<span class="p">, </span>')}<span class="p">]</span>`
        : `<span class="s">"${esc(v)}"</span>`;
    return `  <span class="k">"${k}"</span><span class="p">:</span> ${val}`;
  });
  return `<pre class="code"><span class="p">{</span>\n${lines.join('<span class="p">,</span>\n')}\n<span class="p">}</span></pre>`;
}

function plateHTML(e, { banner = '', note = '', slam = false, typing = false } = {}) {
  const rec = state().caught[e.k];
  const set = setOf(e);
  const known = !!rec;
  const palette = (rec && rec.palette) || ['#F3F8F5', '#F5B7B2', '#FF7972', '#434448'];
  const data = { habitat: e.h, diet: e.d, size: e.z, rarity: RARITY[e.r].name.toLowerCase() };
  if (known && e.c.length > 1) data[`forms[${rec.forms.length}/${e.c.length}]`] = rec.forms.map((i) => snake(LABELS[i]));
  if (known) { data.sightings = rec.count; data.first_seen = isoDate(rec.first); }
  return `<article class="plate">
    <div class="plate-caption"><span>SECTOR <b>${setIndex(set)}·${esc(set.name)}</b></span><span>ID <b>#${pad(e.no)}</b></span></div>
    ${banner}
    <h2 data-decode>${known ? esc(e.n) : 'UNKNOWN'}</h2>
    <div class="plate-sci"><i>${known ? esc(e.s) : 'species incognita'}</i><span>+${RARITY[e.r].value} XP</span></div>
    <div class="plate-figure${slam ? ' slam' : ''}">${stampHTML(e, { big: true })}</div>
    <p class="plate-note">${note || (known
      ? `&gt; record ${isoDate(rec.first)} · sightings <b>×${rec.count}</b>`
      : `&gt; no record · signal detected in <b>${esc(e.h.toLowerCase())}</b>`)}</p>
    ${known ? `
      <section class="panel">
        <div class="panel-head"><span>DEX_ENTRY_${pad(e.no)}.TXT</span><span class="dots"></span></div>
        <div class="panel-body"><p class="dex-text${typing ? '' : ' done'}"><span class="sr">${esc(e.t)}</span><span class="typed" aria-hidden="true">${typing ? '' : esc(e.t)}</span></p></div>
      </section>
      <p class="comment">/* <b>did you know?</b> ${esc(e.f)} */</p>
      ${codeBlock(data)}` : `
      <section class="panel"><div class="panel-head"><span>DEX_ENTRY_${pad(e.no)}.TXT</span><span class="dots"></span></div>
        <div class="panel-body"><p class="dex-text done">ACCESS DENIED — scan a live specimen of this ${RARITY[e.r].name.toLowerCase()} animal to decrypt its entry.</p></div></section>
      ${codeBlock({ sector: set.name, habitat: e.h, rarity: RARITY[e.r].name.toLowerCase(), status: 'encrypted' })}`}
    <div class="plate-foot">
      <div class="who">${known ? 'Spectral analysis' : 'Awaiting sample'}<span>op:${esc(snake(state().name || 'operator'))}</span></div>
      <div class="swatches">${palette.map((c) => `<div class="swatch"><i style="background:${c}"></i><small>${c.toUpperCase()}</small></div>`).join('')}</div>
      <div class="plate-no">${pad(e.no)}</div>
    </div>
  </article>`;
}

const speakBtn = `<button type="button" class="btn" data-act="speak"><svg viewBox="0 0 24 24"><path d="M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6M18.5 6.5a8 8 0 010 11"/></svg>Play audio</button>`;

function openEntry(key) {
  const e = BY_KEY[key];
  const rec = state().caught[e.k];
  sfx.click();
  const body = openSheet(`${plateHTML(e)}
    <div class="actions">${rec ? speakBtn : ''}<button type="button" class="btn primary" data-close>Close</button></div>`);
  body.addEventListener('click', (ev) => {
    if (ev.target.closest('[data-act="speak"]')) speak(`${e.n}. ${e.t}`);
  });
}

// ---------------------------------------------------------------- spectral analysis
function extractPalette(canvas) {
  const N = 48;
  const c = document.createElement('canvas');
  c.width = c.height = N;
  const x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(canvas, 0, 0, N, N);
  const d = x.getImageData(0, 0, N, N).data;
  const buckets = new Map();
  for (let i = 0; i < d.length; i += 4) {
    const key = ((d[i] >> 4) << 8) | ((d[i + 1] >> 4) << 4) | (d[i + 2] >> 4);
    const b = buckets.get(key) || [0, 0, 0, 0];
    b[0] += d[i]; b[1] += d[i + 1]; b[2] += d[i + 2]; b[3]++;
    buckets.set(key, b);
  }
  const colours = [...buckets.values()].sort((a, b) => b[3] - a[3]).map((b) => [b[0] / b[3], b[1] / b[3], b[2] / b[3]]);
  const picked = [];
  for (const min of [70, 45, 25, 0]) {
    for (const col of colours) {
      if (picked.length === 4) break;
      if (picked.every((p) => Math.hypot(p[0] - col[0], p[1] - col[1], p[2] - col[2]) > min)) picked.push(col);
    }
  }
  const lum = (p) => 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2];
  return picked.sort((a, b) => lum(b) - lum(a))
    .map((p) => '#' + p.map((v) => Math.round(v).toString(16).padStart(2, '0')).join(''));
}

const canvasToBlob = (canvas, size = 360) => new Promise((resolve) => {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  c.getContext('2d').drawImage(canvas, 0, 0, size, size);
  c.toBlob((b) => resolve(b), 'image/jpeg', 0.84);
});

// ---------------------------------------------------------------- camera
const video = $('#video');
const freeze = $('#freeze');
const vf = $('#viewfinder');
const freezeCtx = freeze.getContext('2d', { willReadFrequently: true });
let stream = null;
let facing = 'environment';
let wantCamera = false;
let busy = false;

async function startCamera() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    $('#vf-text').textContent = 'No optics available in this browser — feed the scanner a photo instead.';
    $('#btn-camera').hidden = true;
    log('[err] optics unavailable · fallback=image_upload', 'err');
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
    const settings = stream.getVideoTracks()[0].getSettings();
    const front = settings.facingMode === 'user';
    vf.classList.toggle('mirror', front);
    vf.classList.add('live');
    $('#rec').textContent = '● LIVE';
    $('#lens').textContent = `LENS:${front ? 'FRONT' : 'REAR'}`;
    log(`[ok] optics online · lens=${front ? 'front' : 'rear'} · ${video.videoWidth}×${video.videoHeight}`, 'ok');
    idle();
    warmModel();
  } catch (err) {
    vf.classList.remove('live');
    const denied = err && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
    $('#vf-text').textContent = denied
      ? 'Camera permission blocked. Allow it in browser settings, or feed the scanner a photo.'
      : 'No camera found — feed the scanner a photo instead.';
    log(`[err] optics ${denied ? 'permission denied' : 'not found'}`, 'err');
  }
}

function stopCamera() {
  if (stream) stream.getTracks().forEach((t) => t.stop());
  stream = null;
  video.srcObject = null;
  vf.classList.remove('live');
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
}

async function drawFileToFreeze(file) {
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.src = url;
    await img.decode();
    const side = Math.min(img.naturalWidth, img.naturalHeight);
    freezeCtx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, freeze.width, freeze.height);
  } finally {
    URL.revokeObjectURL(url);
  }
}

function unfreeze() {
  vf.classList.remove('frozen', 'scanning');
}

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
      if (line && line.isConnected) { line.className = 'ok'; line.innerHTML = '[ok] neural core online · mobilenet_v2 · 261 signatures'; }
      else log('[ok] neural core online · mobilenet_v2 · 261 signatures', 'ok');
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
    if (source === 'camera') {
      if (!stream) { await startCamera(); if (!stream) return; }
      captureFrame();
    }
    vf.classList.add('frozen', 'scanning');
    flash();
    sfx.scan();
    const started = performance.now();
    log(`&gt; wilddex.scan --source=${source}`, 'cmd');
    log('[..] frame captured · 448×448 · rgb');
    await warmModel();
    log('[..] inference ×3 (full · mirror · crop)');
    const probs = await classify(freeze);
    const v = interpret(probs);
    const wait = 1300 - (performance.now() - started);
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
  } else if (v.kind === 'unsure') {
    sfx.again();
    log(`[warn] low confidence · top=${conf(v.top.score)} · manual id required`, 'warn');
    showChoices(v.alternatives, 'Signal inconclusive. Select the correct specimen, or rescan closer and in better light.');
  } else if (v.kind === 'object') {
    sfx.fail();
    log(`[err] non-fauna object: <b>${esc(snake(LABELS[v.object]))}</b> · conf=${conf(v.objectScore)}`, 'err');
    setTimeout(() => { unfreeze(); idle(); }, 1800);
  } else {
    sfx.fail();
    log('[err] no fauna signature · move closer, hold steady', 'err');
    setTimeout(() => { unfreeze(); idle(); }, 1800);
  }
}

const resetScanner = () => { unfreeze(); idle(); };

function showChoices(options, text) {
  const body = openSheet(`<article class="plate">
      <div class="plate-caption"><span>MANUAL <b>IDENTIFICATION</b></span></div>
      <span class="banner warn">⚠ SIGNAL UNCLEAR</span>
      <h2 data-decode>SELECT MATCH</h2>
      <p class="comment" style="margin-top:12px">/* ${esc(text)} */</p>
      <div class="choices">${options.map((o, i) => {
        const known = state().caught[o.entry.k];
        return `<button type="button" class="choice" data-i="${i}">
          <span class="em">${o.entry.e}</span>
          <span><b>${esc(o.entry.n)}</b><i>${known ? 'record exists' : 'new signature'}</i></span>
          <span class="pct">${Math.max(1, Math.round(o.score * 100))}%</span></button>`;
      }).join('')}</div>
      <div class="actions"><button type="button" class="btn ghost" data-act="none">None of these · rescan</button></div>
    </article>`, { onClose: resetScanner });
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
  const prev = s.caught[e.k] ? structuredClone(s.caught[e.k]) : null;
  const now = Date.now();
  const isNew = !prev;
  const rec = s.caught[e.k] || { first: now, count: 0, forms: [], photo: false };
  const newForm = e.c.length > 1 && !rec.forms.includes(form);
  if (!rec.forms.includes(form)) rec.forms.push(form);
  rec.count++;
  rec.last = now;
  const blob = await canvasToBlob(freeze);
  const palette = extractPalette(freeze);
  if (isNew) {
    rec.photo = true;
    rec.palette = palette;
    await store.putPhoto(e.k, blob);
  }
  s.caught[e.k] = rec;
  store.save();
  refreshCounts();

  const setDone = isNew && setOf(e).entries.every((x) => s.caught[x.k]);
  if (isNew) {
    sfx.success();
    if (navigator.vibrate) navigator.vibrate([30, 40, 60]);
    log(`[ok] registered #${pad(e.no)} ${esc(e.n)} · +${RARITY[e.r].value}xp`, 'ok');
  } else {
    sfx.again();
    log(`[ok] record exists #${pad(e.no)} · sightings=${rec.count}`, 'ok');
  }

  const banner = isNew
    ? '<span class="banner new">▲ NEW SPECIMEN REGISTERED</span>'
    : `<span class="banner soft">● RECORD EXISTS · SIGHTING ×${rec.count}</span>`;
  const extra = [];
  if (newForm && !isNew) extra.push(`&gt; new form unlocked: <b>${esc(snake(LABELS[form]))}</b>`);
  if (setDone) extra.push(`&gt; sector secured: <b>${esc(setOf(e).name)}</b>`);

  const body = openSheet(`${plateHTML(e, { banner, note: extra.join('<br>'), slam: isNew, typing: true })}
    <div class="actions">
      ${speakBtn}
      ${isNew ? '' : '<button type="button" class="btn" data-act="photo">Overwrite image</button>'}
      <button type="button" class="btn primary" data-close>Continue</button>
    </div>
    <div class="subtle-row"><button type="button" class="link" data-act="wrong">misidentified? not a ${esc(e.n.toLowerCase())}</button></div>`,
  { onClose: resetScanner });

  typeOut($('.dex-text .typed', body), e.t);
  if (isNew) setTimeout(() => speak(`${e.n}. ${e.t}`), 700);
  if (setDone) setTimeout(() => toast(`SECTOR SECURED · ${setOf(e).name.toUpperCase()}`), 1200);

  body.addEventListener('click', async (ev) => {
    const act = ev.target.closest('[data-act]');
    if (!act) return;
    if (act.dataset.act === 'speak') speak(`${e.n}. ${e.t}`);
    if (act.dataset.act === 'photo') {
      rec.palette = palette;
      rec.photo = true;
      await store.putPhoto(e.k, blob);
      store.save();
      act.disabled = true;
      act.textContent = 'Image updated';
      hydratePhotos(body);
      refreshCounts();
    }
    if (act.dataset.act === 'wrong') {
      // Undo this registration, then offer the other candidates.
      if (prev) s.caught[e.k] = prev; else { delete s.caught[e.k]; await store.deletePhoto(e.k); }
      store.save();
      refreshCounts();
      stopSpeaking();
      log(`[warn] registration #${pad(e.no)} rolled back`, 'warn');
      if (alternatives.length) {
        showChoices(alternatives, `Registration of ${e.n} rolled back. Was it one of these?`);
      } else {
        closeSheet();
        toast('Registration rolled back');
      }
    }
  });
}

// ---------------------------------------------------------------- views
function caughtCount() { return Object.keys(state().caught).filter((k) => BY_KEY[k]).length; }
function points() { return Object.keys(state().caught).reduce((sum, k) => sum + (BY_KEY[k] ? RARITY[BY_KEY[k].r].value : 0), 0); }
function rankFor(n) { let r = RANKS[0]; for (const x of RANKS) if (n >= x[0]) r = x; return r; }

function refreshCounts() {
  $('#count-num').textContent = pad(caughtCount());
  $('#count-total').textContent = `/ ${ENTRIES.length} SPECIMENS`;
  renderRecent();
  if (currentTab === 'album') renderAlbum();
  if (currentTab === 'passport') renderPassport();
}

function renderRecent() {
  const recent = Object.entries(state().caught)
    .filter(([k]) => BY_KEY[k])
    .sort((a, b) => b[1].last - a[1].last)
    .slice(0, 8)
    .map(([k]) => BY_KEY[k]);
  $('#recent-wrap').hidden = !recent.length;
  $('#recent').innerHTML = recent.map((e) => stampHTML(e)).join('');
  hydratePhotos($('#recent'));
}

let albumFilter = 'all';
function renderAlbum() {
  const s = state();
  const n = caughtCount();
  const done = SETS.filter((set) => set.entries.every((e) => s.caught[e.k])).length;
  const pct = (n / ENTRIES.length) * 100;
  $('#album-pct').textContent = pct > 0 && pct < 10 ? pct.toFixed(1) : Math.floor(pct);
  $('#album-bar').style.width = `${(n / ENTRIES.length) * 100}%`;
  $('#album-sub').textContent = `> ${n}/${ENTRIES.length} specimens indexed · ${done}/${SETS.length} sectors secured`;

  const chip = (id, label, got, total) => `<button type="button" class="chip${got === total && id !== 'all' ? ' done' : ''}" role="tab" data-set="${id}" aria-selected="${albumFilter === id}">${label} <small>${got}/${total}</small></button>`;
  $('#chips').innerHTML = chip('all', 'ALL', n, ENTRIES.length) + SETS.map((set) => {
    const got = set.entries.filter((e) => s.caught[e.k]).length;
    return chip(set.id, `${set.icon} ${esc(set.name)}`, got, set.entries.length);
  }).join('');

  const sets = albumFilter === 'all' ? SETS : SETS.filter((x) => x.id === albumFilter);
  $('#sheets').innerHTML = sets.map((set) => {
    const got = set.entries.filter((e) => s.caught[e.k]).length;
    const complete = got === set.entries.length;
    return `<section class="sheet-section">
      <div class="sheet-head">
        <div><small>SECTOR ${setIndex(set)}</small><h2>${set.icon} ${esc(set.name)}</h2><p>${complete ? 'all signatures captured' : `${set.entries.length - got} signatures remaining`}</p></div>
        ${complete ? '<span class="seal">✓ SECURED</span>' : `<span class="frac">${String(got).padStart(2, '0')}<small>/${set.entries.length}</small></span>`}
      </div>
      <div class="stamp-grid">${set.entries.map((e) => stampHTML(e)).join('')}</div>
    </section>`;
  }).join('');
  hydratePhotos($('#sheets'));
}

$('#chips').addEventListener('click', (ev) => {
  const c = ev.target.closest('.chip');
  if (!c) return;
  sfx.click();
  albumFilter = c.dataset.set;
  renderAlbum();
  const sel = $(`.chip[data-set="${albumFilter}"]`);
  sel?.scrollIntoView?.({ inline: 'center', block: 'nearest' });
});

function operatorId() {
  const s = state();
  if (!s.opId) {
    s.opId = Math.floor(Math.random() * 0xffffff).toString(16).toUpperCase().padStart(6, '0');
    store.save();
  }
  return s.opId;
}

function renderPassport() {
  const s = state();
  const n = caughtCount();
  const [floor, rankName] = rankFor(n);
  const next = RANKS.find((r) => r[0] > n);
  $('#pts').textContent = points();
  $('#rank-sub').textContent = `// clearance: ${rankName.toLowerCase()}`;
  const byRarity = [1, 2, 3, 4].map((r) => {
    const all = ENTRIES.filter((e) => e.r === r);
    return { r, total: all.length, got: all.filter((e) => s.caught[e.k]).length };
  });
  const forms = Object.values(s.caught).reduce((a, rec) => a + (rec.forms ? rec.forms.length : 0), 0);
  const rColor = { 1: 'var(--r1)', 2: 'var(--r2)', 3: 'var(--r3)', 4: 'linear-gradient(90deg, var(--blush), var(--mint), var(--coral))' };

  $('#passport').innerHTML = `
    <section class="card idcard">
      <div class="id-top"><span>OPERATOR ID</span><b>OP-${operatorId()}</b></div>
      <label for="collector-name">CALLSIGN</label>
      <input id="collector-name" maxlength="24" placeholder="enter callsign" value="${esc(s.name)}" autocomplete="nickname" spellcheck="false">
      <div class="rank">CLEARANCE: <b>${rankName.toUpperCase()}</b>
        <small>${next ? `&gt; ${next[0] - n} more specimens to ${next[1].toLowerCase()}` : '&gt; database complete. legendary.'}</small></div>
      <div class="next-rank"><span style="width:${next ? Math.round(((n - floor) / (next[0] - floor)) * 100) : 100}%"></span></div>
      <div class="stats">
        <div><b>${pad(n)}</b><span>Species</span></div>
        <div><b>${pad(forms)}</b><span>Forms</span></div>
        <div><b>${pad(s.scans)}</b><span>Scans</span></div>
      </div>
      <div class="barcode" aria-hidden="true"></div>
    </section>

    <section class="card">
      <h3>Sector badges</h3>
      <div class="medals">${SETS.map((set) => {
        const got = set.entries.filter((e) => s.caught[e.k]).length;
        const pct = Math.round((got / set.entries.length) * 100);
        return `<div class="medal${got === set.entries.length ? ' done' : ''}"><div class="hex" style="--p:${pct}"><span>${set.icon}</span></div>${esc(set.name)}</div>`;
      }).join('')}</div>
    </section>

    <section class="card">
      <h3>Rarity index</h3>
      <div class="rarity-rows">${byRarity.map((x) => `<div class="rarity-row"><span>${RARITY[x.r].name.toUpperCase()}</span>
        <span class="bar"><span style="width:${(x.got / x.total) * 100}%;background:${rColor[x.r]}"></span></span>
        <span class="n">${pad(x.got)}/${pad(x.total)}</span></div>`).join('')}</div>
    </section>

    <section class="card">
      <h3>System config</h3>
      <label class="toggle"><span>voice_synthesis<small>Narrate new registrations aloud</small></span>
        <input type="checkbox" class="switch" data-setting="voice" ${s.settings.voice ? 'checked' : ''}></label>
      <label class="toggle"><span>audio_fx<small>Scanner blips and registration tones</small></span>
        <input type="checkbox" class="switch" data-setting="sound" ${s.settings.sound ? 'checked' : ''}></label>
    </section>

    <section class="card">
      <h3>Data backup</h3>
      <p class="about">Your database lives only on this device. Export a backup file to move it to a new phone.</p>
      <div class="btn-row">
        <button type="button" class="btn small" data-act="export">Export</button>
        <label class="btn small">Import<input type="file" accept="application/json,.json" data-act="import" hidden></label>
        <button type="button" class="btn small ghost" data-act="reset">Wipe database</button>
      </div>
    </section>

    <section class="card about">
      <h3>System info</h3>
      <p><span class="prompt">$</span> cat /sys/scanner.txt</p>
      <p>Recognition runs entirely on this device with a MobileNet v2 neural net — images never leave your phone, and the scanner works offline once the core has loaded.</p>
      <p>${ENTRIES.length} species signatures across ${SETS.length} sectors. Best results: one animal, close, well lit. Some common animals — pigeons, crows, deer, giraffes — aren't in the net's vocabulary yet.</p>
      <p><button type="button" class="link" data-act="intro">run wilddex --help</button></p>
    </section>`;
}

$('#passport').addEventListener('input', (ev) => {
  if (ev.target.id === 'collector-name') {
    state().name = ev.target.value.trim();
    store.save();
  }
});
$('#passport').addEventListener('change', async (ev) => {
  const t = ev.target;
  if (t.dataset.setting) {
    state().settings[t.dataset.setting] = t.checked;
    store.save();
    if (t.dataset.setting === 'sound' && t.checked) sfx.again();
  }
  if (t.dataset.act === 'import' && t.files[0]) {
    try {
      await store.importBackup(await t.files[0].text());
      toast('DATABASE RESTORED');
      refreshCounts();
      renderPassport();
    } catch (err) {
      toast(err.message || 'Could not read that file');
    }
    t.value = '';
  }
});
$('#passport').addEventListener('click', async (ev) => {
  const act = ev.target.closest('[data-act]');
  if (!act) return;
  if (act.dataset.act === 'export') {
    const json = await store.exportBackup();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([json], { type: 'application/json' }));
    a.download = `wilddex-backup-${isoDate(Date.now())}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }
  if (act.dataset.act === 'reset') {
    if (confirm('Wipe every specimen from your database? This can\'t be undone (unless you exported a backup).')) {
      await store.resetAll();
      toast('DATABASE WIPED');
      refreshCounts();
      renderPassport();
    }
  }
  if (act.dataset.act === 'intro') showIntro();
});

// stamps anywhere open their entry
document.addEventListener('click', (ev) => {
  const st = ev.target.closest('button.stamp-wrap');
  if (st) openEntry(st.dataset.key);
});

// ---------------------------------------------------------------- tabs
let currentTab = 'scan';
function showTab(tab) {
  const changed = tab !== currentTab;
  currentTab = tab;
  $$('.tabs button').forEach((b) => {
    if (b.dataset.tab === tab) b.setAttribute('aria-current', 'page');
    else b.removeAttribute('aria-current');
  });
  $$('.view').forEach((v) => { v.hidden = v.dataset.view !== tab; });
  if (tab === 'scan') {
    if (wantCamera && !stream) startCamera();
  } else {
    stopCamera();
  }
  if (tab === 'album') renderAlbum();
  if (tab === 'passport') renderPassport();
  const view = $(`.view[data-view="${tab}"]`);
  $$('[data-decode]', view).forEach((el) => decode(el));
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
$('#file').addEventListener('change', async (ev) => {
  const file = ev.target.files[0];
  ev.target.value = '';
  if (!file || busy) return;
  try {
    await drawFileToFreeze(file);
  } catch {
    log('[err] could not decode image', 'err');
    return;
  }
  runScan('image');
});

// ---------------------------------------------------------------- clock
function tickClock() {
  const d = new Date();
  $('#clock').textContent = [d.getHours(), d.getMinutes(), d.getSeconds()].map((x) => String(x).padStart(2, '0')).join(':');
}
tickClock();
setInterval(tickClock, 1000);

// ---------------------------------------------------------------- onboarding & boot
function showIntro() {
  openSheet(`<article class="plate">
      <div class="plate-caption"><span>$ <b>wilddex --help</b></span></div>
      <h2 data-decode>WILDDEX</h2>
      <div class="plate-sci"><i>field scanner for real-world fauna</i><span>v2.6</span></div>
      <ol class="intro-steps">
        <li><div><b>Scan</b><span>Aim the optics at a pet, a bird in the park, a bug in the garden or a lion at the zoo — or feed it a photo.</span></div></li>
        <li><div><b>Register</b><span>Each new species is decrypted and stamped into your database with your image, its dex entry and a data file.</span></div></li>
        <li><div><b>Collect</b><span>${ENTRIES.length} specimen stamps across ${SETS.length} sectors — from backyard birds to legendary relics. Rarer finds earn more XP.</span></div></li>
      </ol>
      <p class="comment">/* all processing is on-device. nothing is uploaded. */</p>
      <div class="actions"><button type="button" class="btn primary" data-close>&gt; Start collecting</button></div>
    </article>`, {
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
  const n = caughtCount();
  const lines = [
    '<span class="hl">WILDDEX_OS v2.6</span> — fauna recognition unit',
    `<span class="ok">[ok]</span> specimen db ...... ${ENTRIES.length} signatures`,
    `<span class="ok">[ok]</span> operator album ... ${n} registered`,
    `<span class="ok">[ok]</span> optics ........... calibrated`,
    `<span class="ok">[ok]</span> neural core ...... standby`,
    `<span class="t">&gt; welcome${state().name ? ` back, ${esc(snake(state().name))}` : ', operator'}</span>`,
  ];
  for (const l of lines) {
    if (skip) break;
    pre.innerHTML += `${l}\n`;
    tone(1400, 0, 0.02, 'square', 0.012);
    await sleep(170);
  }
  if (!skip) await sleep(350);
  boot.classList.add('out');
  await sleep(350);
  boot.hidden = true;
}

// ---------------------------------------------------------------- boot
async function boot() {
  let tab = 'scan';
  try { tab = sessionStorage.getItem('wilddex.tab') || 'scan'; } catch { /* ignore */ }
  refreshCounts();
  log('WILDDEX_OS v2.6 · on-device mode', 'cmd');
  idle();
  await bootSequence();
  showTab(tab);

  // Re-open the camera automatically if the user already granted it.
  let camBefore = false;
  try { camBefore = localStorage.getItem('wilddex.cam') === '1'; } catch { /* ignore */ }
  if (camBefore && navigator.permissions) {
    try {
      const p = await navigator.permissions.query({ name: 'camera' });
      if (p.state === 'granted') { wantCamera = true; if (currentTab === 'scan') startCamera(); }
    } catch { /* permissions API doesn't know "camera" in some browsers */ }
  }

  if (!state().onboarded) showIntro();

  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
  if ('speechSynthesis' in window) speechSynthesis.getVoices();
}

boot();
