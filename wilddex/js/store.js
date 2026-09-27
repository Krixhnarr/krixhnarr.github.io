// Persistence: collection progress in localStorage, stamp photos in
// IndexedDB (they are too big for localStorage's ~5 MB). Every access is
// guarded - private windows or blocked storage fall back to memory.

const KEY = 'wilddex.v1';
const DB_NAME = 'wilddex';
const STORE = 'photos';

const fresh = () => ({
  caught: {}, // key -> { first, last, count, forms: [classIdx], photo: bool, holo?: ts }
  scans: 0,
  shards: 0,
  intel: {}, // key -> true once a locked card's intel is decrypted with shards
  daily: null,
  streak: null,
  name: '',
  onboarded: false,
  settings: { voice: true, sound: true, music: true, musicVol: 0.6, tilt: true, location: false, haptics: true, theme: 'auto' },
});

let state = fresh();
try {
  const raw = localStorage.getItem(KEY);
  if (raw) state = { ...fresh(), ...JSON.parse(raw) };
  state.settings = { ...fresh().settings, ...state.settings };
} catch { /* storage unavailable - keep in-memory state */ }

export function getState() { return state; }

export function save() {
  try { localStorage.setItem(KEY, JSON.stringify(state)); } catch { /* ignore */ }
}

export function replaceState(next) {
  state = { ...fresh(), ...next, settings: { ...fresh().settings, ...(next.settings || {}) } };
  save();
}

// ---- photos (IndexedDB) ----
const memPhotos = new Map();
let dbPromise;
function db() {
  if (!dbPromise) {
    dbPromise = new Promise((resolve) => {
      try {
        const req = indexedDB.open(DB_NAME, 1);
        req.onupgradeneeded = () => req.result.createObjectStore(STORE);
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => resolve(null);
      } catch { resolve(null); }
    });
  }
  return dbPromise;
}

function tx(mode, fn) {
  return db().then((d) => new Promise((resolve) => {
    if (!d) return resolve(undefined);
    try {
      const t = d.transaction(STORE, mode);
      const req = fn(t.objectStore(STORE));
      t.oncomplete = () => resolve(req && req.result);
      t.onerror = t.onabort = () => resolve(undefined);
    } catch { resolve(undefined); }
  }));
}

export async function putPhoto(key, blob) {
  memPhotos.set(key, blob);
  urlCache.delete(key);
  await tx('readwrite', (s) => s.put(blob, key));
}

export async function getPhoto(key) {
  if (memPhotos.has(key)) return memPhotos.get(key);
  const blob = await tx('readonly', (s) => s.get(key));
  if (blob) memPhotos.set(key, blob);
  return blob;
}

export async function deletePhoto(key) {
  memPhotos.delete(key);
  urlCache.delete(key);
  await tx('readwrite', (s) => s.delete(key));
}

export async function clearPhotos() {
  memPhotos.clear();
  urlCache.clear();
  await tx('readwrite', (s) => s.clear());
}

// Object URLs for <img> tags, created once per photo.
const urlCache = new Map();
export async function photoURL(key) {
  if (urlCache.has(key)) return urlCache.get(key);
  const blob = await getPhoto(key);
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  urlCache.set(key, url);
  return url;
}

// ---- backup ----
const blobToDataURL = (blob) => new Promise((resolve) => {
  const r = new FileReader();
  r.onload = () => resolve(r.result);
  r.onerror = () => resolve(null);
  r.readAsDataURL(blob);
});

export async function exportBackup() {
  const photos = {};
  for (const key of Object.keys(state.caught)) {
    const blob = await getPhoto(key);
    if (blob) photos[key] = await blobToDataURL(blob);
  }
  return JSON.stringify({ app: 'wilddex', version: 1, exported: Date.now(), state, photos });
}

export async function importBackup(text) {
  const data = JSON.parse(text);
  if (data.app !== 'wilddex' || !data.state) throw new Error('Not a WildDex backup file');
  await clearPhotos();
  replaceState(data.state);
  for (const [key, url] of Object.entries(data.photos || {})) {
    const blob = await (await fetch(url)).blob();
    await putPhoto(key, blob);
  }
}

export async function resetAll() {
  const keep = { name: state.name, opId: state.opId, settings: state.settings, onboarded: true };
  await clearPhotos();
  replaceState(keep);
}
