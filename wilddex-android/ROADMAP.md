# WildDex roadmap

Everything that's done, in progress, and still to do. ✅ done · 🔧 in progress ·
⬜ to do. Items near the top of each list matter most.

## 0. Native Android app (replaces the web version)
- ✅ Kotlin + Jetpack Compose app in `wilddex-android/`; game rules in a
  pure-Kotlin `core` module (ready to share with an iPhone app via Kotlin Multiplatform)
- ✅ Parity tests: the Kotlin rules give the same results as the old JavaScript
- ✅ Home, Cards, card detail, Scan, Arena, Ops, Operator screens; day/night sky
- ✅ Scanning: CameraX + on-device recogniser (LiteRT) + depth sweep, screen/print
  and bezel checks; reveal, sync grades, holo, manual pick, rollback
- ✅ Battles, crates, generated music & sound effects, vibration, narrator voice,
  card tilt, optional location tags, backup export/import
- ✅ Web version taken down; its page lets old players download their progress,
  which the app imports (photos included)
- 🔧 **Install the APK on a real phone and try every screen** (no emulator in the
  build sandbox, so the camera, sound and sensors are untested on hardware)
- ⬜ Not ported yet from the web version: Compare with friends (QR/link) and the
  field map of tagged finds
- ⬜ Accounts on Android (Firebase Auth + Firestore): needs `google-services.json`
  and the app's SHA-1 added in the Firebase console
- ⬜ App icon set (adaptive icon, all densities) and a splash screen

## 1. Accounts & cloud save
- ✅ Sign in / create account with email + password
- ✅ Forgot password (reset email) and email verification
- ✅ "Continue with Google" button (popup, falls back to redirect)
- ✅ Cloud save: progress syncs to your account; a new phone loads it; if both
  the phone and the account have progress you choose which to keep
- ✅ Sign out (progress stays on the phone) and delete account
- ✅ Firestore security rules (`firestore.rules`): players can only touch their own save
- ℹ️ Built for the web version (now closed). The Android app still needs its own
  Firebase setup: create the project, add an Android app `io.github.krixhnarr.wilddex`,
  download `google-services.json`, add the signing key's SHA-1 for Google sign-in,
  and publish `firestore.rules` (in this folder)
- ⬜ Test Google sign-in on a real Android phone
- ⬜ "Continue with Apple" — needs an Apple Developer account ($99/yr); required
  by Apple if the app goes to the App Store with Google sign-in
- ⬜ Branded password-reset / verification emails (sender name, logo, custom domain)
- ⬜ Sync card photos too (Firebase Storage) — today they stay on the phone
- ⬜ Firebase App Check to block scripted abuse of the backend

## 2. Real-device testing & tuning
- ✅ Point-and-hold scanning (no left-right sweep) so big and far animals work;
  screen pixel-grid/moiré detector added; depth from hand wobble is a grade bonus
- ⬜ Tune the live-check thresholds on real phones: screens (phones, laptops,
  TVs at different distances), printed photos, vs. real pets, birds, bugs.
  Watch for false alarms on patterned fabric, blinds and tiles
- ⬜ Stronger print detection (paper texture, halftone dots, glare)
- ⬜ Check camera permissions, torch, front/back lens, gyro tilt, geolocation
- ⬜ Performance on low-end phones (scan time, battery during long sessions)
- ⬜ Music balance and loudness on phone speakers; haptics on Android
- ⬜ Outdoor readability of the day theme in bright sun
- ⬜ Battery and heat during long scanning sessions

## 3. Recognition quality
- ✅ EfficientNet-Lite4 replaces MobileNet v2 (93% vs 87% top-1 card accuracy on
  independent wildlife photos); two looks per scan must agree; no manual pick list
- ⬜ Measure accept/reject rates on real scans and tune `MATCH_TOP` / `MATCH_MARGIN`
- ⬜ Animals the model can't see yet: pigeons, crows, deer, giraffes, many
  Indian species → move to a wildlife-trained model (e.g. iNaturalist-based)
- ⬜ Regional sets (Kerala / India: Malabar squirrel, hornbill, peacock varieties…)
- ✅ Quantised int8 model (15 MB)

## 4. Game features
- ✅ Card levels are bought with credits (◆) — re-scanning an animal no longer
  levels it or pays anything, so it can't be farmed. Cost 20◆ × level × rarity
  (Common ×1 … Legendary ×3); a Common to Lv 10 is 900◆
- ⬜ Balance the credit economy with real play data (upgrade costs vs. income
  from discoveries, orders, events and battles)
- ⬜ Friends list and async PvP (battle a friend's squad from their code)
- ⬜ Leaderboards (needs server-side checks so they can't be faked)
- ⬜ More battle depth: status effects, card abilities, balance pass with real data
- ⬜ Seasonal events and limited-time frames/titles
- ⬜ Achievements for sets, types and holo collections
- ⬜ Onboarding tutorial that walks through the first scan and first battle

## 5. Anti-cheat on the server
- ⬜ Today all checks run on the phone; anyone technical could edit their save.
  For leaderboards/PvP, validate captures and rewards server-side
  (Cloud Functions) and keep a scan log per account

## 6. Publishing
- ⬜ Google Play developer account ($25 one-time)
- ⬜ Private upload key + Play App Signing (today's APKs use a shared debug key)
- ⬜ Build an Android App Bundle (`./gradlew :app:bundleRelease`), internal testing
  track first, then closed/open testing, then production
- ⬜ Data safety form (camera and location stay on the phone), content rating,
  target audience
- ⬜ iPhone later: reuse `core/` via Kotlin Multiplatform, UI in SwiftUI or
  Compose Multiplatform; needs an Apple Developer account and Sign in with Apple
- ⬜ Store listings: screenshots, icon variants, splash screens, description
- ⬜ Custom domain (e.g. wilddex.app) instead of github.io

## 7. Privacy, legal & safety
- ⬜ Privacy policy (camera, location, accounts, cloud save) and terms of use
- ⬜ Data export + delete in-app (delete ✅, export of cloud data ⬜)
- ⬜ Age rating / children's privacy rules if kids are the audience
- ⬜ Safety tips: don't approach wild animals, respect wildlife and property

## 8. Quality & code health
- ⬜ Run `:core:test` and the screenshot tests on GitHub Actions for every change
- ⬜ Instrumented tests on a real device / Firebase Test Lab
- ⬜ Accessibility pass: screen reader labels, focus order, contrast in both themes
- ⬜ Translations (Malayalam, Hindi, …)
- ⬜ Privacy-friendly crash reporting / analytics

## Done so far (highlights)
Live-animal scanning with anti-spoof liveness · 261 cards in 20 sectors ·
13 affinity types · levels, stars, holo variants, sync grades · battles with a
daily rival · supply crates, frames & titles · daily orders, streaks, weekly
events, badges · compare with friends via QR · field map · home hub, bottom
tabs · Field Expedition day/night theme · generated music & animations ·
accounts & cloud save on the web (awaiting Firebase config) · native Android app
