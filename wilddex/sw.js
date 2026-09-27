// Offline support. The app shell is refreshed in the background
// (stale-while-revalidate); the ~15 MB model and TF.js bundle are cached on
// first use and served from cache afterwards. Scope is /wilddex/ only.
const VERSION = 'wilddex-v2';
const SHELL = [
  './',
  'index.html',
  'css/style.css',
  'js/app.js',
  'js/classifier.js',
  'js/dex-data.js',
  'js/labels.js',
  'js/store.js',
  'manifest.webmanifest',
  'icons/icon.svg',
  'icons/icon-192.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('wilddex-') && k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

const isHeavy = (url) => url.pathname.includes('/model/') || url.pathname.includes('/vendor/');
const isFont = (url) => url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com';

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const sameOrigin = url.origin === self.location.origin;
  if (!sameOrigin && !isFont(url)) return;

  if (isHeavy(url) || isFont(url)) {
    // cache-first
    event.respondWith(caches.open(VERSION).then(async (cache) => {
      const hit = await cache.match(req);
      if (hit) return hit;
      const res = await fetch(req);
      if (res.ok || res.type === 'opaque') cache.put(req, res.clone());
      return res;
    }));
    return;
  }

  // stale-while-revalidate for the app shell
  event.respondWith(caches.open(VERSION).then(async (cache) => {
    const hit = await cache.match(req, { ignoreSearch: true });
    const network = fetch(req).then((res) => {
      if (res.ok) cache.put(req, res.clone());
      return res;
    }).catch(() => hit);
    return hit || network;
  }));
});
