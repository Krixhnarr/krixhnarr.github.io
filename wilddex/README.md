# WildDex

A real-world collectible card game: scan real animals with your phone camera and
each new species drops a sealed card to decrypt and keep in your
binder. Re-scan animals to level their cards, clear daily orders, keep a
streak, and spend data shards to decrypt intel on cards you haven't found.

Live at https://krixhnarr.github.io/wilddex/ — installable as an app
(Add to Home Screen) and works offline after the first scan.

- **Liveness — real animals only**: each scan records a ~1.9 s sweep while
  the player slides the phone left, then right. `js/parallax.js` tracks ~150
  points and removes the single flat-surface motion; what's left must be
  parallax that follows the player's hand. Photos, prints and screens (all
  flat) and videos on screens (their motion ignores the hand) are rejected.
  Backed up by a device/print classifier check and a bezel/print-margin
  detector (`js/liveness.js`).
- **Recognition** runs on-device with MobileNet v2 (`model/`) via
  TensorFlow.js (`vendor/tf.min.js`). Nothing is uploaded.
- **Collection**: 261 animal cards across 20 sectors (`js/dex-data.js`). Every
  one of the model's 398 animal classes maps to exactly one entry; entries
  with several classes (dog breeds, cat types, crabs…) record each as a
  "form".
- **Cards**: 3D animal art from Microsoft Fluent Emoji (`art/`, MIT), 13
  colour-coded affinity types with HUD glyphs, rarity frames (Legendary = gold),
  gyroscope card tilt (`js/affinity.js`), game stats, levels,
  missions and streaks (`js/game.js`).
- **Social & world**: compare collections with friends via a QR code / link
  (`#c=` bitset of owned cards — no server, never grants cards); opt-in
  location tags (rounded to ~1 km, on-device) with a self-drawn field map;
  rotating weekly events.
- **Storage**: progress in `localStorage`, capture photos in IndexedDB, with
  backup/restore on the ID screen.
- **Look**: game-inventory HUD — charcoal panels, lavender accent, wide
  Michroma headings (self-hosted with JetBrains Mono), level ring, stat bars,
  waveform decorations and chamfered frames; the Inventory tab is a slot grid
  with a selected-item detail panel.

Plain static files, no build step. When changing app files, bump `VERSION`
in `sw.js` so installed copies pick up the update.
