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
const fmtDate = (ts) => new Date(ts).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const state = () => store.getState();

const RANKS = [
  [0, 'Rookie', 'Collector novus'],
  [1, 'Novice Collector', 'Collector tiro'],
  [10, 'Explorer', 'Explorator curiosus'],
  [25, 'Field Researcher', 'Investigator agrestis'],
  [50, 'Naturalist', 'Naturalista peritus'],
  [100, 'Wildlife Expert', 'Peritus faunae'],
  [175, 'Master Collector', 'Magister collectionis'],
  [ENTRIES.length, 'WildDex Champion', 'Campio maximus'],
];

// ---------------------------------------------------------------- sound & voice
let audio;
function tone(freq, at, dur, type = 'sine', vol = 0.07) {
  if (!state().settings.sound) return;
  try {
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    const t = audio.currentTime + at;
    const o = audio.createOscillator();
    const g = audio.createGain();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(vol, t + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g).connect(audio.destination);
    o.start(t);
    o.stop(t + dur + 0.05);
  } catch { /* no audio */ }
}
const sfx = {
  scan: () => [0, 0.14, 0.28, 0.42, 0.56, 0.7].forEach((t, i) => tone(880 + i * 90, t, 0.06, 'triangle', 0.035)),
  success: () => [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(f, i * 0.09, 0.35, 'triangle', 0.07)),
  again: () => { tone(659.25, 0, 0.2, 'triangle'); tone(880, 0.1, 0.3, 'triangle'); },
  fail: () => { tone(330, 0, 0.18, 'sine', 0.06); tone(247, 0.14, 0.28, 'sine', 0.06); },
};

function speak(text) {
  if (!state().settings.voice || !('speechSynthesis' in window)) return;
  try {
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    const voices = speechSynthesis.getVoices();
    u.voice = voices.find((v) => /en-GB/i.test(v.lang)) || voices.find((v) => /^en/i.test(v.lang)) || null;
    u.rate = 1;
    u.pitch = 0.9;
    speechSynthesis.speak(u);
  } catch { /* ignore */ }
}
const stopSpeaking = () => { try { speechSynthesis.cancel(); } catch { /* ignore */ } };

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
  setTimeout(() => f.remove(), 500);
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

// ---------------------------------------------------------------- stamps & plates
function postmark(ts) {
  const d = new Date(ts);
  const month = 'JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC'.split(' ')[d.getMonth()];
  const day = `${String(d.getDate()).padStart(2, '0')} ${month}`;
  const year = d.getFullYear();
  return `<svg class="postmark" viewBox="0 0 120 120" aria-hidden="true">
    <circle cx="60" cy="60" r="54" stroke-width="3"/><circle cx="60" cy="60" r="44" stroke-width="1.5"/>
    <defs><path id="pm-arc" d="M60 60 m-49 0 a49 49 0 1 1 98 0"/></defs>
    <text font-size="9.5"><textPath href="#pm-arc" startOffset="50%" text-anchor="middle">WILDDEX · REGISTERED</textPath></text>
    <text x="60" y="58" font-size="15" text-anchor="middle">${day}</text>
    <text x="60" y="76" font-size="13" text-anchor="middle">${year}</text>
    <path d="M-2 92 q10-6 20 0 t20 0 t20 0 t20 0 t20 0 t20 0 M-2 102 q10-6 20 0 t20 0 t20 0 t20 0 t20 0 t20 0" stroke-width="2.5"/>
  </svg>`;
}

function stampHTML(e, { big = false } = {}) {
  const rec = state().caught[e.k];
  const cls = `stamp-wrap r${e.r}${rec ? '' : ' locked'}`;
  const img = rec && rec.photo
    ? `<img data-photo="${e.k}" alt="Your photo of a ${esc(e.n)}">`
    : `<span class="ghost-rings"></span><span class="ghost" aria-hidden="true">${e.e}</span>`;
  const tag = big ? 'div' : 'button type="button"';
  const endTag = big ? 'div' : 'button';
  return `<${tag} class="${cls}" data-key="${e.k}" ${big ? '' : `aria-label="No. ${e.no} ${rec ? esc(e.n) : 'not yet registered'}"`}>
      <div class="stamp"><div class="stamp-inner">
        <div class="stamp-img">${img}</div>
        <div class="stamp-meta"><span>No.${pad(e.no)}</span><span class="val">${RARITY[e.r].value}</span></div>
        <div class="stamp-name">${rec ? esc(e.n) : '???'}</div>
        <div class="rbar"></div>
      </div></div>
      ${rec ? postmark(rec.first) : ''}
    </${endTag}>`;
}

function setOf(e) { return SETS.find((s) => s.id === e.set); }

function formsHTML(e, rec) {
  if (e.c.length < 2 || !rec) return '';
  const names = rec.forms.map((i) => title(LABELS[i]));
  return `<div class="forms"><p>Forms registered: <b>${rec.forms.length}</b> of ${e.c.length}</p>
    <ul>${names.map((n) => `<li>${esc(n)}</li>`).join('')}</ul></div>`;
}

function plateHTML(e, { banner = '', note = '', slam = false } = {}) {
  const rec = state().caught[e.k];
  const set = setOf(e);
  const known = !!rec;
  const palette = (rec && rec.palette) || ['#F3F8F5', '#F5B7B2', '#FF7972', '#434448'];
  const who = esc(state().name || 'WildDex Collector');
  return `<article class="plate">
    <div class="plate-caption"><span>${set.icon} ${esc(set.name)}</span><span>${RARITY[e.r].name}</span><span>No. ${pad(e.no)}</span></div>
    ${banner}
    <h2>${known ? esc(e.n) : 'Unknown'}</h2>
    <div class="plate-sci"><i>${known ? esc(e.s) : 'species incognita'}</i><span>${RARITY[e.r].value} pts</span></div>
    <div class="plate-figure${slam ? ' slam' : ''}">${stampHTML(e, { big: true })}</div>
    <p class="plate-note">${note || (known
      ? `Registered ${fmtDate(rec.first)} · seen <b>×${rec.count}</b>`
      : `Not yet registered · found in <b>${esc(e.h.toLowerCase())}</b>`)}</p>
    ${known ? `
      <p class="plate-text">${esc(e.t)}</p>
      <p class="fact"><b>Did you know?</b>${esc(e.f)}</p>
      <dl class="data">
        <div><dt>Habitat</dt><dd>${esc(e.h)}</dd></div>
        <div><dt>Diet</dt><dd>${esc(e.d)}</dd></div>
        <div><dt>Size</dt><dd>${esc(e.z)}</dd></div>
      </dl>
      ${formsHTML(e, rec)}` : `
      <p class="plate-text">This page of your album is still empty. Find a ${RARITY[e.r].name.toLowerCase()} animal of the <i>${esc(set.name)}</i> sheet and scan it to reveal its entry.</p>`}
    <div class="plate-foot">
      <div class="who">${known ? 'Colour study' : 'Awaiting specimen'}<span>${who}</span></div>
      <div class="swatches">${palette.map((c) => `<div class="swatch"><i style="background:${c}"></i><small>${c.toUpperCase()}</small></div>`).join('')}</div>
      <div class="plate-no">${e.no}</div>
    </div>
  </article>`;
}

function openEntry(key) {
  const e = BY_KEY[key];
  const rec = state().caught[e.k];
  const body = openSheet(`${plateHTML(e)}
    <div class="actions">${rec ? `<button type="button" class="btn" data-act="speak">
      <svg viewBox="0 0 24 24"><path d="M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6M18.5 6.5a8 8 0 010 11"/></svg>Read aloud</button>` : ''}
      <button type="button" class="btn dark" data-close>Close</button></div>`);
  body.addEventListener('click', (ev) => {
    if (ev.target.closest('[data-act="speak"]')) speak(`${e.n}. ${e.t}`);
  });
}

// ---------------------------------------------------------------- colour study
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
    $('#vf-text').textContent = 'Camera isn\'t available in this browser — upload a photo instead.';
    $('#btn-camera').hidden = true;
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
    vf.classList.toggle('mirror', settings.facingMode === 'user');
    vf.classList.add('live');
    setStatus('Point at an animal and press <em>scan</em>.');
    warmModel();
  } catch (err) {
    vf.classList.remove('live');
    const denied = err && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
    $('#vf-text').textContent = denied
      ? 'Camera permission was blocked. Allow it in your browser settings, or upload a photo instead.'
      : 'No camera found — upload a photo instead.';
  }
}

function stopCamera() {
  if (stream) stream.getTracks().forEach((t) => t.stop());
  stream = null;
  video.srcObject = null;
  vf.classList.remove('live');
}

function captureFrame() {
  const vw = video.videoWidth;
  const vh = video.videoHeight;
  const side = Math.min(vw, vh);
  const ctx = freezeCtx;
  ctx.save();
  if (vf.classList.contains('mirror')) { ctx.translate(freeze.width, 0); ctx.scale(-1, 1); }
  ctx.drawImage(video, (vw - side) / 2, (vh - side) / 2, side, side, 0, 0, freeze.width, freeze.height);
  ctx.restore();
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
function setStatus(html) { $('#status').innerHTML = html; }
function showMeter(p) {
  const m = $('#meter');
  m.hidden = p == null || p >= 1;
  $('#meter-bar').style.width = `${Math.round((p || 0) * 100)}%`;
}

let modelPromise = null;
function warmModel() {
  if (!modelPromise) {
    modelPromise = loadModel((p) => {
      if (p < 1 && !isReady()) showMeter(p);
    }).then((m) => { showMeter(null); return m; })
      .catch((err) => { modelPromise = null; showMeter(null); throw err; });
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
    if (!isReady()) setStatus('Loading the recognition model… <em>first time only</em>');
    await warmModel();
    setStatus('Analysing specimen…');
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
    setStatus('Something went wrong while scanning. Please try again.');
    unfreeze();
  } finally {
    busy = false;
    $('#btn-scan').disabled = false;
  }
}

async function handleVerdict(v) {
  if (v.kind === 'match') {
    await register(v.top.entry, v.top.form, v.alternatives.filter((a) => a.entry !== v.top.entry));
  } else if (v.kind === 'unsure') {
    sfx.again();
    setStatus('Specimen unclear — <em>which one is it?</em>');
    showChoices(v.alternatives, 'The scan was inconclusive. Pick the right animal, or try again closer and in better light.');
  } else if (v.kind === 'object') {
    sfx.fail();
    setStatus(`That looks like a <em>${esc(LABELS[v.object])}</em>, not an animal.`);
    setTimeout(unfreeze, 1800);
  } else {
    sfx.fail();
    setStatus('No animal detected. Move closer and hold steady.');
    setTimeout(unfreeze, 1800);
  }
}

function showChoices(options, text, { onNone } = {}) {
  const body = openSheet(`<article class="plate">
      <div class="plate-caption"><span>Field identification</span></div>
      <span class="banner blush">Specimen unclear</span>
      <h2>Which one is it?</h2>
      <p class="plate-text" style="margin-top:12px">${esc(text)}</p>
      <div class="choices">${options.map((o, i) => {
        const known = state().caught[o.entry.k];
        return `<button type="button" class="choice" data-i="${i}">
          <span class="em">${o.entry.e}</span>
          <span><b>${esc(o.entry.n)}</b><i>${known ? 'Already in your album' : 'New for your album'}</i></span>
          <span class="pct">${Math.max(1, Math.round(o.score * 100))}%</span></button>`;
      }).join('')}</div>
      <div class="actions"><button type="button" class="btn ghost" data-act="none">None of these — scan again</button></div>
    </article>`, { onClose: () => { unfreeze(); setStatus('Point at an animal and press <em>scan</em>.'); } });
  body.addEventListener('click', async (ev) => {
    const btn = ev.target.closest('.choice');
    if (btn) {
      const o = options[Number(btn.dataset.i)];
      onSheetClose = null;
      await register(o.entry, o.form, options.filter((a) => a !== o));
    } else if (ev.target.closest('[data-act="none"]')) {
      if (onNone) onNone();
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
    setStatus(`<em>${esc(e.n)}</em> registered in your album!`);
  } else {
    sfx.again();
    setStatus(`${esc(e.n)} — already registered.`);
  }

  const banner = isNew
    ? `<span class="banner">New specimen registered</span>`
    : `<span class="banner soft">Already in album · seen ×${rec.count}</span>`;
  const extra = [];
  if (newForm && !isNew) extra.push(`New form: <b>${esc(title(LABELS[form]))}</b>`);
  if (setDone) extra.push(`Sheet complete: <b>${esc(setOf(e).name)}</b>!`);
  const note = extra.length ? extra.join(' · ') : '';

  const body = openSheet(`${plateHTML(e, { banner, note, slam: isNew })}
    <div class="actions">
      <button type="button" class="btn" data-act="speak"><svg viewBox="0 0 24 24"><path d="M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6M18.5 6.5a8 8 0 010 11"/></svg>Read aloud</button>
      ${isNew ? '' : '<button type="button" class="btn" data-act="photo">Use this photo</button>'}
      <button type="button" class="btn primary" data-close>Continue</button>
    </div>
    <div class="subtle-row"><button type="button" class="link" data-act="wrong">Not a ${esc(e.n.toLowerCase())}?</button></div>`,
  { onClose: () => { unfreeze(); setStatus('Point at an animal and press <em>scan</em>.'); } });

  if (isNew) setTimeout(() => speak(`${e.n}. ${e.t}`), 700);
  if (setDone) setTimeout(() => toast(`🎉 ${setOf(e).name} sheet complete!`), 1200);

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
      act.textContent = 'Stamp updated';
      hydratePhotos(body);
      refreshCounts();
    }
    if (act.dataset.act === 'wrong') {
      // Undo this registration, then offer the other candidates.
      if (prev) s.caught[e.k] = prev; else { delete s.caught[e.k]; await store.deletePhoto(e.k); }
      store.save();
      refreshCounts();
      stopSpeaking();
      if (alternatives.length) {
        showChoices(alternatives, `Registration of ${e.n} undone. Was it one of these?`);
      } else {
        closeSheet();
        toast(`Registration undone`);
      }
    }
  });
}

// ---------------------------------------------------------------- views
function caughtCount() { return Object.keys(state().caught).filter((k) => BY_KEY[k]).length; }
function points() { return Object.keys(state().caught).reduce((sum, k) => sum + (BY_KEY[k] ? RARITY[BY_KEY[k].r].value : 0), 0); }
function rankFor(n) { let r = RANKS[0]; for (const x of RANKS) if (n >= x[0]) r = x; return r; }

function refreshCounts() {
  const n = caughtCount();
  $('#count-num').textContent = n;
  $('#count-total').textContent = `/ ${ENTRIES.length}`;
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
  $('#album-pct').textContent = Math.floor((n / ENTRIES.length) * 100);
  $('#album-bar').style.width = `${(n / ENTRIES.length) * 100}%`;
  $('#album-sub').textContent = `${n} of ${ENTRIES.length} stamps collected · ${done} of ${SETS.length} sheets complete`;

  const chip = (id, label, got, total) => `<button type="button" class="chip${got === total && id !== 'all' ? ' done' : ''}" role="tab" data-set="${id}" aria-selected="${albumFilter === id}">${label} <small>${got}/${total}</small></button>`;
  $('#chips').innerHTML = chip('all', 'All sheets', n, ENTRIES.length) + SETS.map((set) => {
    const got = set.entries.filter((e) => s.caught[e.k]).length;
    return chip(set.id, `${set.icon} ${esc(set.name)}`, got, set.entries.length);
  }).join('');

  const sets = albumFilter === 'all' ? SETS : SETS.filter((x) => x.id === albumFilter);
  $('#sheets').innerHTML = sets.map((set) => {
    const got = set.entries.filter((e) => s.caught[e.k]).length;
    const complete = got === set.entries.length;
    return `<section class="sheet-section">
      <div class="sheet-head">
        <div><h2>${set.icon} ${esc(set.name)}</h2><p>${complete ? 'Every stamp collected' : `${set.entries.length - got} still to find`}</p></div>
        ${complete ? '<span class="seal">Sheet complete</span>' : `<span class="frac">${got}<small style="font-size:.6em;color:var(--ink-2)">/${set.entries.length}</small></span>`}
      </div>
      <div class="stamp-grid">${set.entries.map((e) => stampHTML(e)).join('')}</div>
    </section>`;
  }).join('');
  hydratePhotos($('#sheets'));
}

$('#chips').addEventListener('click', (ev) => {
  const c = ev.target.closest('.chip');
  if (!c) return;
  albumFilter = c.dataset.set;
  renderAlbum();
  c.scrollIntoView?.({ inline: 'center', block: 'nearest' });
});

function renderPassport() {
  const s = state();
  const n = caughtCount();
  const [, rankName, rankSci] = rankFor(n);
  const next = RANKS.find((r) => r[0] > n);
  $('#pts').textContent = points();
  $('#rank-sci').textContent = rankSci;
  const byRarity = [1, 2, 3, 4].map((r) => {
    const all = ENTRIES.filter((e) => e.r === r);
    return { r, total: all.length, got: all.filter((e) => s.caught[e.k]).length };
  });
  const forms = Object.values(s.caught).reduce((a, rec) => a + (rec.forms ? rec.forms.length : 0), 0);
  const rColor = { 1: 'var(--r1)', 2: 'var(--r2)', 3: 'var(--r3)', 4: 'var(--r4)' };

  $('#passport').innerHTML = `
    <section class="card idcard">
      <label for="collector-name">Collector</label>
      <input id="collector-name" maxlength="28" placeholder="Your name" value="${esc(s.name)}" autocomplete="nickname">
      <div class="rank">${rankName}<small>${next ? `${next[0] - n} more to become ${next[1]}` : 'Every stamp collected. Legendary.'}</small></div>
      <div class="next-rank"><span style="width:${next ? Math.round((n - rankFor(n)[0]) / (next[0] - rankFor(n)[0]) * 100) : 100}%"></span></div>
      <div class="stats">
        <div><b>${n}</b><span>Species</span></div>
        <div><b>${forms}</b><span>Forms seen</span></div>
        <div><b>${s.scans}</b><span>Scans</span></div>
      </div>
    </section>

    <section class="card">
      <h3>Sheet medals</h3>
      <div class="medals">${SETS.map((set) => {
        const got = set.entries.filter((e) => s.caught[e.k]).length;
        const pct = Math.round((got / set.entries.length) * 100);
        return `<div class="medal${got === set.entries.length ? ' done' : ''}"><div class="disc" style="--p:${pct}"><span>${set.icon}</span></div>${esc(set.name)}</div>`;
      }).join('')}</div>
    </section>

    <section class="card">
      <h3>By rarity</h3>
      <div class="rarity-rows">${byRarity.map((x) => `<div class="rarity-row"><span>${RARITY[x.r].name}</span>
        <span class="bar"><span style="width:${(x.got / x.total) * 100}%;background:${rColor[x.r]}"></span></span>
        <span class="n">${x.got}/${x.total}</span></div>`).join('')}</div>
    </section>

    <section class="card">
      <h3>Settings</h3>
      <label class="toggle"><span>Read entries aloud<small>The WildDex voice narrates new discoveries</small></span>
        <input type="checkbox" class="switch" data-setting="voice" ${s.settings.voice ? 'checked' : ''}></label>
      <label class="toggle"><span>Sound effects<small>Scanner blips and registration chimes</small></span>
        <input type="checkbox" class="switch" data-setting="sound" ${s.settings.sound ? 'checked' : ''}></label>
    </section>

    <section class="card">
      <h3>Backup</h3>
      <p class="about">Your album lives only on this device. Save a backup file to move it to a new phone.</p>
      <div class="btn-row">
        <button type="button" class="btn small" data-act="export">Save backup</button>
        <label class="btn small">Restore backup<input type="file" accept="application/json,.json" data-act="import" hidden></label>
        <button type="button" class="btn small ghost" data-act="reset">Reset album</button>
      </div>
    </section>

    <section class="card about">
      <h3>About the scanner</h3>
      <p>Recognition runs entirely on your phone with a MobileNet image model — photos never leave your device, and scanning works offline once the model has loaded.</p>
      <p>It knows ${ENTRIES.length} kinds of animals. It works best with a clear, close, well-lit view of one animal. Some everyday animals — like pigeons, crows, deer and giraffes — aren't in the model's vocabulary yet.</p>
      <p><button type="button" class="link" data-act="intro">Show the welcome guide again</button></p>
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
      toast('Album restored');
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
    a.download = `wilddex-backup-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }
  if (act.dataset.act === 'reset') {
    if (confirm('Remove every stamp from your album? This can\'t be undone (unless you saved a backup).')) {
      await store.resetAll();
      toast('Album reset');
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
  window.scrollTo({ top: 0 });
  try { sessionStorage.setItem('wilddex.tab', tab); } catch { /* ignore */ }
}
$('.tabs').addEventListener('click', (ev) => {
  const b = ev.target.closest('button[data-tab]');
  if (b) showTab(b.dataset.tab);
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
  if (stream) startCamera();
});
$('#file').addEventListener('change', async (ev) => {
  const file = ev.target.files[0];
  ev.target.value = '';
  if (!file || busy) return;
  try {
    await drawFileToFreeze(file);
  } catch {
    toast('Could not open that image');
    return;
  }
  runScan('file');
});

// ---------------------------------------------------------------- onboarding
function showIntro() {
  openSheet(`<article class="plate">
      <div class="plate-caption"><span>Field guide</span><span>No. 000</span></div>
      <h2>WildDex</h2>
      <div class="plate-sci"><i>Fauna collectoria</i></div>
      <ol class="intro-steps">
        <li><div><b>Scan real animals</b><span>Point your camera at a pet, a bird in the park, a bug in the garden or a lion at the zoo — or upload a photo.</span></div></li>
        <li><div><b>Unlock their entry</b><span>Each new species is stamped into your album with your own photo, its description and a fun fact.</span></div></li>
        <li><div><b>Complete the sheets</b><span>${ENTRIES.length} stamps across ${SETS.length} sheets, from backyard birds to legendary relics. Rarer finds are worth more points.</span></div></li>
      </ol>
      <p class="about">Everything runs on your device. Nothing is uploaded.</p>
      <div class="actions"><button type="button" class="btn primary" data-close>Start collecting</button></div>
    </article>`, {
    onClose: () => { state().onboarded = true; store.save(); },
  });
}

// ---------------------------------------------------------------- boot
async function boot() {
  let tab = 'scan';
  try { tab = sessionStorage.getItem('wilddex.tab') || 'scan'; } catch { /* ignore */ }
  refreshCounts();
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
