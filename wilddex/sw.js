// WildDex has moved to a native Android app. This replaces the old offline
// worker: it deletes the cached web app, unregisters itself and reloads any
// open WildDex tabs so they show the notice page from the network.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter((k) => k.startsWith('wilddex-')).map((k) => caches.delete(k)));
    await self.registration.unregister();
    const tabs = await self.clients.matchAll({ type: 'window' });
    tabs.forEach((t) => t.navigate(t.url).catch(() => {}));
  })());
});
