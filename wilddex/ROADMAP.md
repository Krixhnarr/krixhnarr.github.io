# WildDex roadmap

Everything that's done, in progress, and still to do. ✅ done · 🔧 in progress ·
⬜ to do. Items near the top of each list matter most.

## 1. Accounts & cloud save
- ✅ Sign in / create account with email + password
- ✅ Forgot password (reset email) and email verification
- ✅ "Continue with Google" button (popup, falls back to redirect)
- ✅ Cloud save: progress syncs to your account; a new phone loads it; if both
  the phone and the account have progress you choose which to keep
- ✅ Sign out (progress stays on the phone) and delete account
- ✅ Firestore security rules (`firestore.rules`): players can only touch their own save
- 🔧 **Create the Firebase project and paste its config** into `js/firebase-config.js`
  (accounts stay hidden until this is done — see README → Accounts setup)
- ⬜ Test Google sign-in on real iPhone + Android (can't be tested in the sandbox)
- ⬜ "Continue with Apple" — needs an Apple Developer account ($99/yr); required
  by Apple if the app goes to the App Store with Google sign-in
- ⬜ Branded password-reset / verification emails (sender name, logo, custom domain)
- ⬜ Sync card photos too (Firebase Storage) — today they stay on the phone
- ⬜ Firebase App Check to block scripted abuse of the backend

## 2. Real-device testing & tuning
- ⬜ Tune the anti-cheat liveness thresholds on real phones (photos, prints,
  screens, videos vs. real pets, birds, bugs) — iPhone and Android
- ⬜ Check camera permissions, torch, front/back lens, gyro tilt, geolocation
- ⬜ Music balance and loudness on phone speakers; haptics on Android
- ⬜ Outdoor readability of the day theme in bright sun
- ⬜ Battery and heat during long scanning sessions

## 3. Recognition quality
- ⬜ Animals the model can't see yet: pigeons, crows, deer, giraffes, many
  Indian species → move to a wildlife-trained model (e.g. iNaturalist-based)
- ⬜ Regional sets (Kerala / India: Malabar squirrel, hornbill, peacock varieties…)
- ⬜ Smaller/faster model (quantised) so first load is quicker than ~15 MB

## 4. Game features
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
- ⬜ Google Play: wrap the PWA as a Trusted Web Activity (the site already
  serves `.well-known/assetlinks.json`)
- ⬜ Apple App Store: wrap with Capacitor; needs Sign in with Apple
- ⬜ Store listings: screenshots, icon variants, splash screens, description
- ⬜ Custom domain (e.g. wilddex.app) instead of github.io

## 7. Privacy, legal & safety
- ⬜ Privacy policy (camera, location, accounts, cloud save) and terms of use
- ⬜ Data export + delete in-app (delete ✅, export of cloud data ⬜)
- ⬜ Age rating / children's privacy rules if kids are the audience
- ⬜ Safety tips: don't approach wild animals, respect wildlife and property

## 8. Quality & code health
- ⬜ Split `app.js` (~2,500 lines) into modules (scanner, binder, arena, ops, profile)
- ⬜ Move the browser test scripts into the repo + run them on GitHub Actions
- ⬜ Accessibility pass: screen reader labels, focus order, contrast in both themes
- ⬜ Translations (Malayalam, Hindi, …)
- ⬜ Privacy-friendly crash reporting / analytics

## Done so far (highlights)
Live-animal scanning with anti-spoof liveness · 261 cards in 20 sectors ·
13 affinity types · levels, stars, holo variants, sync grades · battles with a
daily rival · supply crates, frames & titles · daily orders, streaks, weekly
events, badges · compare with friends via QR · field map · home hub, bottom
tabs · Field Expedition day/night theme · generated music & animations ·
accounts & cloud save (awaiting Firebase config)
