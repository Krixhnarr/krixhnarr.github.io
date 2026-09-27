// On-device recognition with MobileNet v2 (ImageNet, 1001 outputs incl.
// background). TensorFlow.js and the model weights are self-hosted, so after
// the first load the service worker lets scanning work fully offline and no
// photo ever leaves the phone.

import { ENTRIES, ANIMAL_CLASS_LIMIT } from './dex-data.js';

const TF_URL = 'vendor/tf.min.js';
const MODEL_URL = 'model/model.json';
const SIZE = 224;

let model = null;
let loading = null;

function loadScript(src) {
  return new Promise((resolve, reject) => {
    if (window.tf) return resolve();
    const s = document.createElement('script');
    s.src = src;
    s.onload = resolve;
    s.onerror = () => reject(new Error('Could not load TensorFlow.js'));
    document.head.appendChild(s);
  });
}

export function isReady() { return !!model; }

export function loadModel(onProgress = () => {}) {
  if (model) return Promise.resolve(model);
  if (!loading) {
    loading = (async () => {
      onProgress(0.02);
      await loadScript(TF_URL);
      const tf = window.tf;
      try { await tf.setBackend('webgl'); } catch { /* falls back to cpu */ }
      await tf.ready();
      onProgress(0.08);
      const m = await tf.loadGraphModel(MODEL_URL, {
        onProgress: (p) => onProgress(0.08 + p * 0.87),
      });
      // Warm-up run compiles the WebGL shaders so the first real scan is fast.
      tf.tidy(() => m.predict(tf.zeros([1, SIZE, SIZE, 3])));
      onProgress(1);
      model = m;
      return m;
    })().catch((err) => { loading = null; throw err; });
  }
  return loading;
}

// Returns 1000 ImageNet probabilities for a square canvas, averaged over the
// full frame, its mirror image and a tighter centre crop (helps with small or
// off-centre animals).
export async function classify(canvas) {
  const tf = window.tf;
  const probs = tf.tidy(() => {
    const img = tf.browser.fromPixels(canvas).toFloat().div(255);
    const batch1 = img.expandDims(0);
    const full = tf.image.resizeBilinear(batch1, [SIZE, SIZE]);
    const crop = tf.image.cropAndResize(batch1, [[0.14, 0.14, 0.86, 0.86]], [0], [SIZE, SIZE]);
    const batch = tf.concat([full, tf.image.flipLeftRight(full), crop]);
    const logits = model.predict(batch).slice([0, 1], [-1, 1000]);
    return tf.softmax(logits).mean(0);
  });
  const data = await probs.data();
  probs.dispose();
  return data;
}

// Screens and printed pictures: ImageNet classes the net reports when a
// "live" scan is really a phone, monitor, TV, laptop, book or magazine page.
export const SPOOF_CLASSES = [
  782, 664, 851, 620, 681, 487, 605, 590, 527, 916, 548, 598, 781, // screens & devices
  921, 917, 922, 611, 918, 692, // book jackets, comics, menus, puzzles, packets
];
const spoofMass = (p) => SPOOF_CLASSES.reduce((sum, i) => sum + p[i], 0);
const topSpoof = (p) => SPOOF_CLASSES.reduce((best, i) => (p[i] > p[best] ? i : best), SPOOF_CLASSES[0]);

// `centre` is the probabilities for the square the player aimed at; `wide`
// is the whole camera frame zoomed out, where a phone's bezel, a monitor's
// edge or a page border usually shows up even when the animal fills the
// centre.
export function spoofCheck(centre, wide) {
  const c = spoofMass(centre);
  const w = spoofMass(wide);
  const blocked = c >= 0.12 || w >= 0.2 || wide[topSpoof(wide)] >= 0.15;
  const p = w >= c ? wide : centre;
  return { blocked, score: Math.max(c, w), label: topSpoof(p) };
}

// Turns raw probabilities into a WildDex verdict.
export function interpret(p) {
  let animal = 0;
  for (let i = 0; i < ANIMAL_CLASS_LIMIT; i++) animal += p[i];

  const ranked = ENTRIES.map((e) => {
    let s = 0;
    let best = e.c[0];
    for (const i of e.c) {
      s += p[i];
      if (p[i] > p[best]) best = i;
    }
    return { entry: e, score: s, form: best };
  }).sort((a, b) => b.score - a.score);

  let object = ANIMAL_CLASS_LIMIT;
  for (let i = ANIMAL_CLASS_LIMIT; i < 1000; i++) if (p[i] > p[object]) object = i;

  const top = ranked[0];
  const alternatives = ranked.slice(0, 4).filter((r) => r.score >= 0.04);

  let kind;
  if (top.score >= 0.45) kind = 'match';
  else if (animal >= 0.35 && top.score >= 0.12) kind = 'unsure';
  else if (p[object] >= 0.3) kind = 'object';
  else kind = 'nothing';

  return { kind, top, alternatives, animal, object, objectScore: p[object] };
}
