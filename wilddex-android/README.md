# WildDex (Android)

A real-world collectible card game: scan real, live animals with your phone
camera and each new species drops a sealed card to decrypt and keep in your
binder. Re-scan animals to level their cards, battle a daily rival with your
squad, clear daily orders and keep a streak.

Native Android app in Kotlin + Jetpack Compose. It replaces the web version
that used to live at https://krixhnarr.github.io/wilddex/ (that page now only
says the game moved, and lets old players download their progress).

## Modules

- **`core/`** — pure Kotlin, no Android: the dex (261 cards, 20 sectors, 13
  affinity types), levels, stats, sync grades, holo odds, missions, streaks,
  weekly events, badges, battles, crates, the save format, and the anti-cheat
  maths (`Parallax.kt` depth sweep, `FrameDetect.kt` bezel/print detector,
  `Recognition.kt` screen/print classes). Kept Android-free so an iPhone
  version can share it later through Kotlin Multiplatform.
  `ParityTest` checks it against fixtures made by the original JavaScript
  (`core/src/test/resources/parity.mjs`), so both versions follow the same rules.
- **`app/`** — the Android app: Compose UI (`ui/`), CameraX + LiteRT scanning
  (`scan/`), the generated soundtrack and effects (`audio/`), and the save
  file (`data/`).

## Real animals only

Scanning is point-and-hold: the player holds the phone on the animal for
~1.5 s, so it works for pets and for a zoo elephant 20 m away. Before a card
is registered the scan must pass the live check:

- **Devices & print:** the recogniser looks for screens, monitors, phones,
  books and packets, both in the aimed square and in the whole frame.
- **Bezels & borders:** `FrameDetect.kt` looks for a device bezel or a
  paper margin around the picture.
- **Display pixels:** `ScreenDetect.kt` looks at the centre of the frame at
  full camera resolution for a display's pixel grid or moiré — a few very
  sharp peaks in the frequency spectrum that fur, feathers and skin don't have.

`Parallax.kt` still tracks points through the hold; when the hand's natural
wobble shows real depth, the scan earns a better sync grade (S needs it).

Limits: this catches screens and prints rather than proving 3D, so a
carefully made print or a screen seen from far away can sometimes pass;
tune the thresholds with real-phone reports (the scanner log prints the
numbers behind every decision).

## Recognition — no second opinions

Recognition runs on the phone with EfficientNet-Lite4 (int8, 300×300,
`app/src/main/assets/efficientnet_lite4_int8.tflite`). Each scan looks twice —
the middle and the end of the hold, each as full + mirrored + centre-crop
views — and averages them. A card is only registered when it is clearly one
animal (`Recognition.decide`): score ≥ 0.6, ≥ 0.4 ahead of the next card, and
both looks agree. Otherwise the player is told to get closer or try another
angle; there is no "pick the animal" list, so nobody can choose their way to
a card. Classes under 1% don't count towards a card, so 118 dog breeds can't
out-vote a single horse class.

On 756 independent wildlife photos (lucabaggi/animal-wildlife, 63 kinds with a
matching card), single-look top-1 card accuracy was 93.4% vs 87.4% for the
previous MobileNet v2; with the acceptance rule, 84% of photos were accepted
and 2.3% of those were wrong (before the two-look agreement check).
Nothing is uploaded.

## Levelling

Card levels (1–10, +3 to every stat per level) are bought with credits in the
binder or the card view — `Game.upgradeCost`: 20◆ × current level × rarity
(Common ×1, Uncommon ×1.5, Rare ×2, Legendary ×3). Re-scanning an animal you
already have only logs a sighting (and can turn up a holo); it pays no credits
or XP, so it can't be farmed. Credits come from new discoveries and their sync
grade, completed sectors, daily orders, weekly events, the streak, battles and
operator level-ups. Older saves keep the levels their cards had reached.

## Build

Needs JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew :core:test                 # game rules, live-check and backup tests
./gradlew :app:testDebugUnitTest     # Robolectric screenshots + synth test
./gradlew :app:assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Screenshots of every screen: `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true`
writes them to `app/build/shots/`.

Release builds are minified and, for now, signed with the shared
`debug.keystore` in this folder so each new APK installs over the last one.
**Before publishing on Google Play**, create a private upload key (never
commit it), sign release builds with it, and enrol in Play App Signing.

## Moving progress from the web version

Open https://krixhnarr.github.io/wilddex/ in the same browser that was used
to play, tap **Download my progress**, then in the app go to
**Operator → Backup → Import** and pick the file. Photos come along too.
Backups exported from the app use the same format.

## Credits

3D animal art: Microsoft Fluent Emoji (MIT, `app/src/main/assets/art/LICENSE.md`).
Fonts: Russo One, JetBrains Mono (OFL). Model: EfficientNet-Lite4 (Apache 2.0).
