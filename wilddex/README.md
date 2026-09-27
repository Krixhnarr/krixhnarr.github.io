# WildDex

A real-world collectible card game: scan real animals with your phone camera and
each new species drops a sealed card to decrypt and keep in your
binder. Re-scan animals to level their cards, clear daily orders, keep a
streak, and spend data shards to decrypt intel on cards you haven't found.

Live at https://krixhnarr.github.io/wilddex/ — installable as an app
(Add to Home Screen) and works offline after the first scan.

- **Recognition** runs on-device with MobileNet v2 (`model/`) via
  TensorFlow.js (`vendor/tf.min.js`). Nothing is uploaded.
- **Collection**: 261 animal cards across 20 sectors (`js/dex-data.js`). Every
  one of the model's 398 animal classes maps to exactly one entry; entries
  with several classes (dog breeds, cat types, crabs…) record each as a
  "form".
- **Cards**: 3D animal art from Microsoft Fluent Emoji (`art/`, MIT), 13
  affinity types with HUD glyphs (`js/affinity.js`), game stats, levels,
  missions and streaks (`js/game.js`).
- **Storage**: progress in `localStorage`, capture photos in IndexedDB, with
  backup/restore on the ID screen.
- **Look**: cyber-Y2K dot-matrix UI (self-hosted Doto + JetBrains Mono) with accents from
  the *Diaethria neglecta* butterfly palette — `#F3F8F5`, `#F5B7B2`,
  `#FF7972`, `#434448`.

Plain static files, no build step. When changing app files, bump `VERSION`
in `sw.js` so installed copies pick up the update.
