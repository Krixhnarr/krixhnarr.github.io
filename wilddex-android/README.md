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

Each scan records a ~1.9 s sweep (20 frames) while the player slides the
phone left, then right. Tracked points on a flat surface all move by one
homography; a real scene shows parallax that follows the player's hand. So
photos, prints and screens (flat) and videos on screens (motion that ignores
the hand) are rejected. On top of that, MobileNet v2 checks for screens,
monitors, books and packets in the aimed square and in the whole frame, and
a detector looks for a device bezel or print margin around the picture.

Recognition runs on the phone (`app/src/main/assets/mobilenet_v2.tflite`,
full + mirrored + centre-crop views averaged). Nothing is uploaded.

## Build

Needs JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew :core:test                 # game-rule parity + backup tests
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
Fonts: Russo One, JetBrains Mono (OFL). Model: MobileNet v2 (Apache 2.0).
