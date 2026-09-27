# WildDex

A pocket field-guide game: scan real animals with your phone camera (or a
photo) and each new species is stamped into your collection album with your
photo, a dex entry, a fun fact and a colour study of the picture.

Live at https://krixhnarr.github.io/wilddex/ — installable as an app
(Add to Home Screen) and works offline after the first scan.

- **Recognition** runs on-device with MobileNet v2 (`model/`) via
  TensorFlow.js (`vendor/tf.min.js`). Nothing is uploaded.
- **Collection**: 261 animals on 20 album sheets (`js/dex-data.js`). Every
  one of the model's 398 animal classes maps to exactly one entry; entries
  with several classes (dog breeds, cat types, crabs…) record each as a
  "form".
- **Storage**: progress in `localStorage`, stamp photos in IndexedDB, with
  backup/restore on the Passport screen.
- **Palette**: from the *Diaethria neglecta* plate — `#F3F8F5`, `#F5B7B2`,
  `#FF7972`, `#434448`.

Plain static files, no build step. When changing app files, bump `VERSION`
in `sw.js` so installed copies pick up the update.
