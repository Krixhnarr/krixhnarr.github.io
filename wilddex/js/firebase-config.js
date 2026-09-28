// Firebase project settings for accounts & cloud save.
// Paste the "firebaseConfig" object from Firebase console → Project settings →
// Your apps → Web app. These values are public by design; access is protected
// by Firestore security rules (see firestore.rules) and Authorized domains.
// While this is null, WildDex runs in guest mode and the account UI is hidden.
export const firebaseConfig = null;

// Which "Sign in with…" buttons to show. Each must also be enabled in
// Firebase console → Authentication → Sign-in method.
export const providers = {
  google: true,
  apple: false, // needs an Apple Developer account
};
