// Live-animal check: is the thing in front of the camera 3D (or moving on its
// own), or a flat picture — a print, a photo, a screen?
//
// While the player slides the phone sideways, we record a short burst of
// frames and track ~100 feature points through it. Every point on a flat
// surface moves according to one homography, however the camera moves. A
// real scene breaks that: the animal and its background sit at different
// depths (parallax), and a living animal moves by itself. So:
//   - points that fit one homography        -> flat picture -> rejected
//   - a solid group of points that don't    -> 3D / alive   -> accepted
//   - hardly any motion at all              -> ask the player to move

const LONG = 256;        // analysis width (long side), px
const PATCH = 4;         // 9x9 patches for tracking
const SEARCH = 7;        // search radius around the predicted position
const MAX_POINTS = 200;

// ---------------------------------------------------------------- recording
export function grabGray(source, sw, sh, canvas) {
  const s = LONG / Math.max(sw, sh);
  const w = Math.round(sw * s);
  const h = Math.round(sh * s);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(source, 0, 0, w, h);
  const d = ctx.getImageData(0, 0, w, h).data;
  const g = new Float32Array(w * h);
  for (let i = 0, j = 0; j < g.length; i += 4, j++) g[j] = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];
  return { g, w, h };
}

// Records `count` frames about `interval` ms apart from a playing <video>.
export async function recordSweep(video, { count = 14, interval = 95, onProgress = () => {} } = {}) {
  const canvas = document.createElement('canvas');
  const frames = [];
  for (let i = 0; i < count; i++) {
    frames.push(grabGray(video, video.videoWidth, video.videoHeight, canvas));
    onProgress((i + 1) / count);
    if (i < count - 1) await new Promise((r) => setTimeout(r, interval));
  }
  return frames;
}

// ---------------------------------------------------------------- features
function corners(g, w, h) {
  const score = new Float32Array(w * h);
  const m = 10;
  for (let y = m; y < h - m; y++) {
    for (let x = m; x < w - m; x++) {
      let sxx = 0; let syy = 0; let sxy = 0;
      for (let dy = -2; dy <= 2; dy++) {
        for (let dx = -2; dx <= 2; dx++) {
          const i = (y + dy) * w + (x + dx);
          const gx = g[i + 1] - g[i - 1];
          const gy = g[i + w] - g[i - w];
          sxx += gx * gx; syy += gy * gy; sxy += gx * gy;
        }
      }
      // Shi-Tomasi: smaller eigenvalue of the structure tensor
      const tr = sxx + syy;
      const det = sxx * syy - sxy * sxy;
      score[y * w + x] = tr / 2 - Math.sqrt(Math.max(0, (tr * tr) / 4 - det));
    }
  }
  // Spread points over a grid so a busy background (grass, stripes) can't
  // take them all; the centre cells — where the animal is — get more.
  const GX = 8; const GY = 6;
  const cells = Array.from({ length: GX * GY }, () => []);
  for (let y = m; y < h - m; y++) {
    for (let x = m; x < w - m; x++) {
      const v = score[y * w + x];
      if (v > 250) cells[Math.min(GY - 1, Math.floor((y / h) * GY)) * GX + Math.min(GX - 1, Math.floor((x / w) * GX))].push([v, x, y]);
    }
  }
  const taken = [];
  const minD2 = 6 * 6;
  cells.forEach((cand, ci) => {
    const cxI = ci % GX; const cyI = Math.floor(ci / GX);
    const central = cxI >= 2 && cxI <= 5 && cyI >= 1 && cyI <= 4;
    const quota = central ? 6 : 3;
    cand.sort((a, b) => b[0] - a[0]);
    let n = 0;
    for (const [, x, y] of cand) {
      if (n >= quota) break;
      if (taken.every(([tx, ty]) => (tx - x) ** 2 + (ty - y) ** 2 >= minD2)) { taken.push([x, y]); n++; }
    }
  });
  return taken.slice(0, MAX_POINTS);
}

// ---------------------------------------------------------------- tracking
function bilinear(g, w, x, y) {
  const x0 = Math.floor(x); const y0 = Math.floor(y);
  const fx = x - x0; const fy = y - y0;
  const i = y0 * w + x0;
  return g[i] * (1 - fx) * (1 - fy) + g[i + 1] * fx * (1 - fy) + g[i + w] * (1 - fx) * fy + g[i + w + 1] * fx * fy;
}

// Best match of the patch around (px,py) in `a` near (qx,qy) in `b`, with sub-pixel refinement.
function match(a, b, w, h, px, py, qx, qy) {
  const lim = PATCH + SEARCH + 2;
  if (qx < lim || qy < lim || qx > w - lim || qy > h - lim || px < PATCH + 1 || py < PATCH + 1 || px > w - PATCH - 2 || py > h - PATCH - 2) return null;
  const tpl = [];
  let mean = 0;
  for (let dy = -PATCH; dy <= PATCH; dy++) for (let dx = -PATCH; dx <= PATCH; dx++) { const v = bilinear(a, w, px + dx, py + dy); tpl.push(v); mean += v; }
  mean /= tpl.length;
  let varT = 0;
  for (const v of tpl) varT += (v - mean) ** 2;
  if (varT / tpl.length < 60) return null; // too flat to track reliably
  const cx = Math.round(qx); const cy = Math.round(qy);
  const size = 2 * SEARCH + 1;
  const sad = new Float32Array(size * size);
  let best = Infinity; let bi = -1;
  for (let sy = -SEARCH; sy <= SEARCH; sy++) {
    for (let sx = -SEARCH; sx <= SEARCH; sx++) {
      let s = 0; let k = 0;
      for (let dy = -PATCH; dy <= PATCH; dy++) {
        const row = (cy + sy + dy) * w + cx + sx;
        for (let dx = -PATCH; dx <= PATCH; dx++, k++) s += Math.abs(b[row + dx] - tpl[k]);
      }
      const idx = (sy + SEARCH) * size + (sx + SEARCH);
      sad[idx] = s;
      if (s < best) { best = s; bi = idx; }
    }
  }
  const bx = (bi % size) - SEARCH; const by = Math.floor(bi / size) - SEARCH;
  if (Math.abs(bx) === SEARCH || Math.abs(by) === SEARCH) return null; // hit the search edge
  if (best / tpl.length > 26) return null; // poor match
  const at = (x, y) => sad[(y + SEARCH) * size + (x + SEARCH)];
  const sub = (l, c, r) => { const d = l - 2 * c + r; return d > 0 ? (0.5 * (l - r)) / d : 0; };
  return [cx + bx + sub(at(bx - 1, by), best, at(bx + 1, by)), cy + by + sub(at(bx, by - 1), best, at(bx, by + 1))];
}

// Each point keeps its frame-0 patch as the template, so tracking error
// doesn't pile up from frame to frame (which would look like fake parallax).
function track(frames) {
  const { w, h } = frames[0];
  const pts = corners(frames[0].g, w, h);
  const tracks = pts.map(([x, y]) => [[x, y]]);
  const vel = pts.map(() => [0, 0]);
  const alive = pts.map(() => true);
  for (let f = 1; f < frames.length; f++) {
    for (let i = 0; i < tracks.length; i++) {
      if (!alive[i]) continue;
      const [x0, y0] = tracks[i][0];
      const [px, py] = tracks[i][f - 1];
      const m = match(frames[0].g, frames[f].g, w, h, x0, y0, px + vel[i][0], py + vel[i][1]);
      if (!m) { alive[i] = false; continue; }
      vel[i] = [m[0] - px, m[1] - py];
      tracks[i].push(m);
    }
  }
  return tracks.filter((t, i) => alive[i]);
}

// ---------------------------------------------------------------- homography
function solve(A, bvec) {
  const n = bvec.length;
  const M = A.map((row, i) => [...row, bvec[i]]);
  for (let c = 0; c < n; c++) {
    let p = c;
    for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
    if (Math.abs(M[p][c]) < 1e-10) return null;
    [M[c], M[p]] = [M[p], M[c]];
    for (let r = 0; r < n; r++) {
      if (r === c) continue;
      const f = M[r][c] / M[c][c];
      for (let k = c; k <= n; k++) M[r][k] -= f * M[c][k];
    }
  }
  return M.map((row, i) => row[n] / row[i]);
}

// Least-squares homography (h33 = 1) from point pairs, via the normal equations.
function fitH(src, dst) {
  const AtA = Array.from({ length: 8 }, () => new Array(8).fill(0));
  const Atb = new Array(8).fill(0);
  for (let i = 0; i < src.length; i++) {
    const [x, y] = src[i]; const [u, v] = dst[i];
    const rows = [[x, y, 1, 0, 0, 0, -u * x, -u * y, u], [0, 0, 0, x, y, 1, -v * x, -v * y, v]];
    for (const r of rows) {
      for (let a = 0; a < 8; a++) { Atb[a] += r[a] * r[8]; for (let b = 0; b < 8; b++) AtA[a][b] += r[a] * r[b]; }
    }
  }
  const hv = solve(AtA, Atb);
  return hv ? [...hv, 1] : null;
}

function project(H, [x, y]) {
  const z = H[6] * x + H[7] * y + H[8];
  return [(H[0] * x + H[1] * y + H[2]) / z, (H[3] * x + H[4] * y + H[5]) / z];
}

function ransacH(src, dst, thresh, iters = 300) {
  const n = src.length;
  let best = null; let bestIn = -1;
  // Normalise coordinates for numerical stability.
  const norm = (pts) => {
    const mx = pts.reduce((s, p) => s + p[0], 0) / pts.length;
    const my = pts.reduce((s, p) => s + p[1], 0) / pts.length;
    const sc = Math.SQRT2 / (pts.reduce((s, p) => s + Math.hypot(p[0] - mx, p[1] - my), 0) / pts.length || 1);
    return { f: (p) => [(p[0] - mx) * sc, (p[1] - my) * sc], sc };
  };
  const ns = norm(src); const nd = norm(dst);
  const S = src.map(ns.f); const D = dst.map(nd.f);
  const t = thresh * nd.sc;
  let seed = 12345;
  const rnd = () => { seed = (seed * 1103515245 + 12345) >>> 0; return seed / 4294967296; };
  for (let it = 0; it < iters; it++) {
    const idx = new Set();
    while (idx.size < 4) idx.add(Math.floor(rnd() * n));
    const I = [...idx];
    const H = fitH(I.map((i) => S[i]), I.map((i) => D[i]));
    if (!H) continue;
    let cnt = 0;
    for (let i = 0; i < n; i++) { const p = project(H, S[i]); if (Math.hypot(p[0] - D[i][0], p[1] - D[i][1]) < t) cnt++; }
    if (cnt > bestIn) { bestIn = cnt; best = H; }
  }
  if (!best) return null;
  // Refit on inliers, then measure residuals in pixels.
  const inl = [];
  for (let i = 0; i < n; i++) { const p = project(best, S[i]); if (Math.hypot(p[0] - D[i][0], p[1] - D[i][1]) < t) inl.push(i); }
  const H = fitH(inl.map((i) => S[i]), inl.map((i) => D[i])) || best;
  return src.map((_, i) => { const p = project(H, S[i]); return Math.hypot(p[0] - D[i][0], p[1] - D[i][1]) / nd.sc; });
}

// ---------------------------------------------------------------- verdict
export const RESID = 2.2;        // px at LONG=256: beyond tracking noise
export const MIN_TRACKS = 14;

export function analyseSweep(frames, { debug = false } = {}) {
  const tracks = track(frames);
  const all = debug ? corners(frames[0].g, frames[0].w, frames[0].h) : null;
  if (tracks.length < MIN_TRACKS) return { verdict: 'texture', tracks: tracks.length };
  const first = tracks.map((t) => t[0]);
  const disp = tracks.map((t) => Math.hypot(t[t.length - 1][0] - t[0][0], t[t.length - 1][1] - t[0][1])).sort((a, b) => a - b);
  const median = disp[Math.floor(disp.length / 2)];

  // Worst residual of each track against the best single-plane motion from
  // frame 0 to every later frame.
  const resid = tracks.map(() => 0);
  for (let f = 2; f < tracks[0].length; f += 1) {
    const r = ransacH(first, tracks.map((t) => t[f]), 1.2, 160);
    if (r) r.forEach((v, i) => { if (v > resid[i]) resid[i] = v; });
  }
  const off = resid.filter((r) => r > RESID).length;
  const offShare = off / tracks.length;

  // Points that don't fit the dominant flat motion must also be spatially
  // grouped (an animal, a nearer object), not scattered tracking glitches.
  const offPts = first.filter((_, i) => resid[i] > RESID);
  let grouped = 0;
  for (const p of offPts) if (offPts.filter((q) => q !== p && Math.hypot(q[0] - p[0], q[1] - p[1]) < 40).length >= 2) grouped++;

  const live = off >= 6 && offShare >= 0.15 && grouped >= 5;
  let verdict;
  if (live) verdict = 'live';
  else if (median < 4) verdict = 'still';   // nothing moved: can't tell yet
  else verdict = 'flat';
  const out = { verdict, tracks: tracks.length, median: +median.toFixed(1), offShare: +offShare.toFixed(2), off, grouped };
  if (debug) { out.first = first; out.resid = resid; out.corners = all; }
  return out;
}
