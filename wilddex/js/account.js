// Accounts & cloud save (Firebase Auth + Firestore). The SDK is loaded from
// Google's CDN only when accounts are configured and used, so guest play
// stays light and fully offline.

import { firebaseConfig, providers } from './firebase-config.js';

const SDK = 'https://www.gstatic.com/firebasejs/12.19.0';
// Tests can point the app at the local Firebase emulators.
const TEST = window.__firebaseTest || null;
const config = TEST?.config || firebaseConfig;

let app = null;
let fa = null; // firebase-auth module
let auth = null;
let fs = null; // firebase-firestore module
let db = null;

export const enabled = () => !!config;
export const providerList = () => ({ google: !!providers.google, apple: !!providers.apple });

async function init() {
  if (auth) return;
  const { initializeApp } = await import(`${SDK}/firebase-app.js`);
  fa = await import(`${SDK}/firebase-auth.js`);
  app = initializeApp(config);
  auth = fa.getAuth(app);
  auth.useDeviceLanguage();
  if (TEST?.auth) fa.connectAuthEmulator(auth, TEST.auth, { disableWarnings: true });
  await fa.setPersistence(auth, fa.browserLocalPersistence);
}

async function initDb() {
  await init();
  if (db) return;
  fs = await import(`${SDK}/firebase-firestore.js`);
  db = fs.getFirestore(app);
  if (TEST?.firestore) fs.connectFirestoreEmulator(db, TEST.firestore[0], TEST.firestore[1]);
}

// ---------------------------------------------------------------- auth
export async function watch(cb) {
  await init();
  // Finish a redirect sign-in (used when popups aren't allowed).
  fa.getRedirectResult(auth).catch(() => {});
  return fa.onAuthStateChanged(auth, cb);
}
export const currentUser = () => auth?.currentUser || null;

export async function signIn(email, password) {
  await init();
  return (await fa.signInWithEmailAndPassword(auth, email, password)).user;
}

export async function signUp(email, password, name) {
  await init();
  const { user } = await fa.createUserWithEmailAndPassword(auth, email, password);
  if (name) await fa.updateProfile(user, { displayName: name }).catch(() => {});
  fa.sendEmailVerification(user).catch(() => {});
  return user;
}

export async function resetPassword(email) {
  await init();
  await fa.sendPasswordResetEmail(auth, email);
}

async function withProvider(provider) {
  await init();
  try {
    return (await fa.signInWithPopup(auth, provider)).user;
  } catch (err) {
    // Installed apps and some browsers block popups: fall back to a redirect.
    if (['auth/popup-blocked', 'auth/operation-not-supported-in-this-environment', 'auth/cancelled-popup-request'].includes(err.code)) {
      await fa.signInWithRedirect(auth, provider);
      return null;
    }
    throw err;
  }
}
export async function signInGoogle() {
  await init();
  const p = new fa.GoogleAuthProvider();
  p.setCustomParameters({ prompt: 'select_account' });
  return withProvider(p);
}
export async function signInApple() {
  await init();
  const p = new fa.OAuthProvider('apple.com');
  p.addScope('email');
  p.addScope('name');
  return withProvider(p);
}

export async function signOut() {
  await init();
  await fa.signOut(auth);
}

export async function resendVerification() {
  if (auth?.currentUser) await fa.sendEmailVerification(auth.currentUser);
}

// Deletes the cloud save and the account itself.
export async function deleteAccount() {
  await initDb();
  const user = auth.currentUser;
  if (!user) return;
  await fs.deleteDoc(fs.doc(db, 'players', user.uid));
  await fa.deleteUser(user);
}

// ---------------------------------------------------------------- cloud save
export async function loadSave(uid) {
  await initDb();
  const snap = await fs.getDoc(fs.doc(db, 'players', uid));
  if (!snap.exists()) return null;
  const d = snap.data();
  try { return { state: JSON.parse(d.state), savedAt: d.savedAt || 0 }; } catch { return null; }
}

export async function writeSave(uid, state) {
  await initDb();
  await fs.setDoc(fs.doc(db, 'players', uid), {
    state: JSON.stringify(state), savedAt: state.savedAt || Date.now(), updatedAt: fs.serverTimestamp(), v: 1,
  });
}

// ---------------------------------------------------------------- messages
const MESSAGES = {
  'auth/invalid-email': 'That email address doesn\'t look right.',
  'auth/missing-email': 'Enter your email address.',
  'auth/missing-password': 'Enter your password.',
  'auth/invalid-credential': 'Wrong email or password.',
  'auth/wrong-password': 'Wrong email or password.',
  'auth/user-not-found': 'No account with that email yet — create one?',
  'auth/email-already-in-use': 'That email already has an account. Sign in instead.',
  'auth/weak-password': 'Use at least 8 characters for your password.',
  'auth/too-many-requests': 'Too many tries. Wait a minute and try again.',
  'auth/network-request-failed': 'No connection. Check your internet and try again.',
  'auth/popup-closed-by-user': 'Sign-in window was closed.',
  'auth/account-exists-with-different-credential': 'That email already signs in another way — try email or Google.',
  'auth/requires-recent-login': 'For safety, sign in again and then delete your account.',
  'auth/unauthorized-domain': 'This site isn\'t allowed to sign in yet (add it to Authorized domains in Firebase).',
  'auth/operation-not-allowed': 'That sign-in method isn\'t switched on yet.',
  'permission-denied': 'Cloud save was refused. Sign in again.',
  'auth/internal-error': 'Couldn\'t reach the sign-in service. Check your connection and try again.',
  'auth/popup-blocked': 'Your browser blocked the sign-in window. Allow pop-ups and try again.',
};
export const friendlyError = (err) => MESSAGES[err?.code] || (err?.message || 'Something went wrong. Try again.').replace(/^Firebase: /, '');
