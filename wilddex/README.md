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
- **Capture feel**: the liveness sweep doubles as a lock-on mini-game — a
  ring fills as sync builds, and the result earns a sync grade (S/A/B/C) from
  confidence, depth and motion, with bonus credits and XP. Any capture has a
  1-in-40 chance (1-in-20 on an S) of a rainbow **holo** variant. Re-scans
  power cards up with half-star steps to five stars at Lv 10.
- **Battles** (`js/battle.js`): 3-v-3 turn-based fights with the cards you own —
  type strikes, Guard and a charged Overdrive, a 13-type matchup wheel
  (each type beats two), a daily rival and paid practice battles. Battles
  never give or take cards.
- **Rewards**: operator XP from scans, grades, battles and orders; full-screen
  level-ups pay credits and a supply crate. Crates (`js/loot.js`) hold only
  cosmetics — card frames and operator titles for the Locker. Haptics,
  count-up credits and floating reward numbers throughout.
- **Social & world**: compare collections with friends via a QR code / link
  (`#c=` bitset of owned cards — no server, never grants cards); opt-in
  location tags (rounded to ~1 km, on-device) with a self-drawn field map;
  rotating weekly events.
- **Storage**: progress in `localStorage`, capture photos in IndexedDB, with
  backup/restore on the ID screen.
- **Music & life**: a soundtrack synthesised live with Web Audio
  (`js/music.js`, no audio files) — a bright day adventure theme, a calm
  night lullaby, a driving battle theme, win/lose jingles and quiet
  birdsong or crickets. It starts on the first tap, ducks under scans and
  narration, pauses in the background, and has a toggle and volume in
  Config. Effects (`js/fx.js`): birds, butterflies, swaying grass and
  shooting stars in the sky; new cards fly into the Cards tab; each of the
  13 types has its own attack effect; confetti for big moments; tabs slide
  and tiles pop in.
- **Look — Field Expedition theme**: a bright outdoor palette that reads
  well in sunlight — sky-blue scene with drifting clouds, sun and rolling
  hills, white rounded panels, sunny-yellow press-down buttons, coral and
  grass accents, bright type-tinted cards (Legendary = gold) and a
  sky-and-grass battle arena. After 7pm it switches to a night sky with a
  moon, stars and fireflies (Config → Sky: Auto / Day / Night). Game shell:
  Home hub, bottom icon tab bar with a raised Scan button, level/XP chip and
  currency pills, Russo One + JetBrains Mono, LED dot-matrix titles.

Plain static files, no build step. When changing app files, bump `VERSION`
in `sw.js` so installed copies pick up the update.
