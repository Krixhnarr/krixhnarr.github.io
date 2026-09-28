// Runs the web app's JavaScript rules on fixed inputs so the Kotlin port can be checked against them.
const FIXED = Date.UTC(2026, 8, 28, 6, 30); // 2026-09-28 06:30 UTC
const RealDate = Date;
globalThis.Date = class extends RealDate { constructor(...a) { super(...(a.length ? a : [FIXED])); } static now() { return FIXED; } };
process.env.TZ = 'UTC';
const W = '/home/user/krixhnarr.github.io/wilddex/js/';
const { ENTRIES } = await import(W + 'dex-data.js');
const game = await import(W + 'game.js');
const battle = await import(W + 'battle.js');
const par = await import(W + 'parallax.js');
const cls = await import(W + 'classifier.js');
import fs from 'fs';

const out = {};
out.hash = ['dog', 'rival:2026-09-28:A1B2C3', 'target:2026-09-28', 'WildDex ✦'].map((s) => [s, game.hash(s)]);
out.stats = ENTRIES.map((e) => [e.k, game.statsFor(e, 1), game.statsFor(e, 7)]);
out.levels = [0, 1, 2, 3, 4, 7, 8, 15, 16, 255, 256, 511, 512, 1000].map((c) => [c, game.levelFor(c)]);
out.missions = ['2026-01-01', '2026-02-14', '2026-09-28', '2027-06-30', '2025-12-31'].map((d) => [d, (() => {
  // makeMissions isn't exported; dailyState uses today(), so compute via a state per date
  return null;
})()]);
out.grades = [[0.9, { tracks: 60, parallax: 20, range: 40 }], [0.5, { tracks: 40, parallax: 4, range: 10 }], [0.3, null], [0.7, { tracks: 30, parallax: 8, range: 120 }]]
  .map(([c, d]) => [c, d, game.syncGrade(c, d)]);
out.mult = [];
const T = Object.keys(battle.STRONG);
for (const a of T) for (const b of T) for (const c of [null, ...T]) if (c !== b) out.mult.push([a, c ? [b, c] : [b], battle.mult(a, c ? [b, c] : [b])]);
const state = { caught: {}, opId: 'A1B2C3', battle: null };
['dog', 'cat', 'red-fox', 'lion', 'koala', 'orca', 'ladybug', 'squirrel'].forEach((k, i) => { state.caught[k] = { count: 1 + i * 3, forms: [], holo: i === 2 ? 1 : undefined }; });
const rival = battle.dailyRival(state);
out.rival = { name: rival.name, keys: rival.keys, lvs: rival.lvs, date: rival.date, squad: battle.squad(state) };
const ev = game.weeklyEvent({});
out.event = { id: ev.id, key: ev.key, daysLeft: ev.daysLeft, goal: ev.goal };
const ds = {}; out.today = game.dailyState(ds).date; out.todayMissions = game.dailyState(ds).missions.map((m) => m.id);

// recognition: pseudo-random probability vectors
let seed = 7;
const rnd = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
out.interpret = [];
for (let t = 0; t < 30; t++) {
  const p = new Float32Array(1000);
  let sum = 0;
  const hot = Math.floor(rnd() * 1000);
  for (let i = 0; i < 1000; i++) { const r = rnd(); p[i] = r * r * r * r * r * r * r * r; sum += p[i]; }
  p[hot] += sum * (t % 3); // sometimes one class dominates
  let s2 = 0; for (let i = 0; i < 1000; i++) s2 += p[i];
  for (let i = 0; i < 1000; i++) p[i] /= s2;
  const v = cls.interpret(p);
  out.interpret.push({ hot, kind: v.kind, top: v.top.entry.k, score: v.top.score, form: v.top.form, alts: v.alternatives.map((a) => a.entry.k), object: v.object });
}

// parallax: synthetic sweeps built from integer value noise (bit-identical in both languages)
function lattice(ix, iy, salt) {
  let h = Math.imul(ix, 374761393) + Math.imul(iy, 668265263) + Math.imul(salt, 1274126177);
  h = Math.imul(h ^ (h >>> 13), 1103515245);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296 * 255;
}
function noise(x, y, scale, salt) {
  const fx = x / scale; const fy = y / scale;
  const x0 = Math.floor(fx); const y0 = Math.floor(fy);
  const tx = fx - x0; const ty = fy - y0;
  const a = lattice(x0, y0, salt); const b = lattice(x0 + 1, y0, salt); const c = lattice(x0, y0 + 1, salt); const d = lattice(x0 + 1, y0 + 1, salt);
  return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
}
function sweep(kind) {
  const w = 256; const h = 192; const frames = [];
  for (let f = 0; f < 20; f++) {
    const phase = f < 10 ? f / 9 : (19 - f) / 9; // slide left then back
    const cam = phase * 24; // background shift in px
    const g = new Float32Array(w * h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const inFg = (x - 128) ** 2 / 3600 + (y - 96) ** 2 / 2500 < 1;
      let v;
      if (kind === 'live' && inFg) v = noise(x + cam * 2.2 + 1000, y, 5, 2); // nearer: moves more
      else v = noise(x + cam, y, 6, 1);
      g[y * w + x] = v;
    }
    frames.push({ g, w, h });
  }
  return frames;
}
out.sweeps = {};
for (const k of ['live', 'flat']) out.sweeps[k] = par.analyseSweep(sweep(k));
fs.writeFileSync('/home/user/krixhnarr.github.io/wilddex-android/core/src/test/resources/parity.json', JSON.stringify(out));
console.log(JSON.stringify({ rival: out.rival, event: out.event, today: out.today, missions: out.todayMissions, sweeps: out.sweeps, interpretKinds: out.interpret.map((i) => i.kind).join(',') }, null, 1));
