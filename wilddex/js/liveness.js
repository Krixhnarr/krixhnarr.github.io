// Screen / print frame detector.
//
// A photo of an animal shown on a phone, monitor, laptop or TV — or printed
// on paper — almost always has a thin, evenly coloured frame around the
// picture: the device bezel (dark) or the print margin (light). Real scenes
// rarely have one. We look for such a ring enclosing the centre of the camera
// frame, trying a few small rotations for a tilted phone or photo.
//
// Works on a tiny luminance image, using integral images so every candidate
// rectangle is scored in constant time.

const N = 320; // long side of the analysis image
const ANGLES = [0, -5, 5, -10, 10];

function luminance(ctx, w, h) {
  const d = ctx.getImageData(0, 0, w, h).data;
  const y = new Float64Array(w * h);
  for (let i = 0, j = 0; j < y.length; i += 4, j++) y[j] = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];
  return y;
}

// Integral images of value and value² (size (w+1)*(h+1)).
function integrals(y, w, h) {
  const W = w + 1;
  const s = new Float64Array(W * (h + 1));
  const q = new Float64Array(W * (h + 1));
  for (let r = 1; r <= h; r++) {
    let rs = 0;
    let rq = 0;
    for (let c = 1; c <= w; c++) {
      const v = y[(r - 1) * w + (c - 1)];
      rs += v;
      rq += v * v;
      s[r * W + c] = s[(r - 1) * W + c] + rs;
      q[r * W + c] = q[(r - 1) * W + c] + rq;
    }
  }
  // stats of rect [x0,x1) x [y0,y1)
  return (x0, y0, x1, y1) => {
    const a = y0 * W + x0; const b = y0 * W + x1; const c = y1 * W + x0; const d = y1 * W + x1;
    const n = (x1 - x0) * (y1 - y0);
    if (n <= 0) return { m: 0, sd: 999, n: 0 };
    const m = (s[d] - s[b] - s[c] + s[a]) / n;
    const v = (q[d] - q[b] - q[c] + q[a]) / n - m * m;
    return { m, sd: Math.sqrt(Math.max(0, v)), n };
  };
}

const UNIFORM_SD = 17;   // a bezel / margin strip is nearly flat
const CONTRAST = 22;     // ...and clearly different from what's next to it
const BUSY_SD = 24;      // or the neighbour is busy (picture content)

const OUTER_CONTRAST = 26; // bezel/margin vs the table, wall or hand around the device

function sideOk(ring, inner, outer) {
  if (ring.sd > UNIFORM_SD) return false;
  const inOk = Math.abs(inner.m - ring.m) > CONTRAST || inner.sd > BUSY_SD;
  const outOk = Math.abs(outer.m - ring.m) > OUTER_CONTRAST;
  return inOk && outOk;
}

// Columns / rows where long straight vertical / horizontal edges sit.
function edgePeaks(y, w, h) {
  const col = new Float64Array(w);
  const row = new Float64Array(h);
  for (let r = 1; r < h - 1; r++) {
    for (let c = 1; c < w - 1; c++) {
      const i = r * w + c;
      const gx = y[i + 1] - y[i - 1];
      const gy = y[i + w] - y[i - w];
      const ax = Math.abs(gx);
      const ay = Math.abs(gy);
      if (ax > 18 && ax > 2 * ay) col[c] += 1; // vertical edge pixel
      if (ay > 18 && ay > 2 * ax) row[r] += 1; // horizontal edge pixel
    }
  }
  const pick = (arr, from, to, len, k) => {
    const idx = [];
    for (let i = from; i < to; i++) if (arr[i] >= 0.15 * len && arr[i] >= (arr[i - 1] || 0) && arr[i] >= (arr[i + 1] || 0)) idx.push(i);
    return idx.sort((a, b) => arr[b] - arr[a]).slice(0, k);
  };
  return {
    left: pick(col, 2, Math.floor(w * 0.45), h, 16),
    right: pick(col, Math.ceil(w * 0.55), w - 2, h, 16),
    top: pick(row, 2, Math.floor(h * 0.45), w, 8),
    bottom: pick(row, Math.ceil(h * 0.55), h - 2, w, 8),
  };
}

function search(y, w, h, deadline) {
  const st = integrals(y, w, h);
  const pk = edgePeaks(y, w, h);
  let best = null;
  // An edge peak can be either side of the bezel line; try the strip on both sides of it.
  const variants = (arr, sign) => arr.flatMap((p) => [p, p + sign]);
  for (const L0 of variants(pk.left, 1)) {
    if (performance.now() > deadline) break;
    for (const R0 of variants(pk.right, 0)) {
      const rw = R0 - L0;
      if (rw < 0.22 * w) continue;
      for (const T0 of variants(pk.top, 1)) {
        for (const B0 of variants(pk.bottom, 0)) {
          const rh = B0 - T0;
          if (rh < 0.22 * h) continue;
          const maxT = Math.max(2, Math.floor(0.08 * Math.min(rw, rh)));
          for (let t = 2; t <= Math.min(12, maxT); t++) {
            for (const inward of [true, false]) {
              // ring strip on the inside (edge = outer bezel boundary) or outside (edge = screen boundary)
              const L = inward ? L0 : L0 - t;
              const R = inward ? R0 : R0 + t;
              const T = inward ? T0 : T0 - t;
              const B = inward ? B0 : B0 + t;
              if (L < 1 || T < 1 || R > w - 1 || B > h - 1) continue;
              // Use the middle 70% of each side: skips rounded phone corners and
              // most of a finger or thumb holding the device.
              const iy = Math.round((B - T) * 0.15);
              const ix = Math.round((R - L) * 0.15);
              const y0 = T + iy; const y1 = B - iy; const x0 = L + ix; const x1 = R - ix;
              const sides = [
                [st(L, y0, L + t, y1), st(L + t, y0, L + 2 * t, y1), st(Math.max(0, L - t), y0, L, y1)],
                [st(R - t, y0, R, y1), st(R - 2 * t, y0, R - t, y1), st(R, y0, Math.min(w, R + t), y1)],
                [st(x0, T, x1, T + t), st(x0, T + t, x1, T + 2 * t), st(x0, Math.max(0, T - t), x1, T)],
                [st(x0, B - t, x1, B), st(x0, B - 2 * t, x1, B - t), st(x0, B, x1, Math.min(h, B + t))],
              ];
              const good = sides.filter(([ring, inner, outer]) => sideOk(ring, inner, outer));
              if (good.length < 3) continue;
              const means = good.map(([ring]) => ring.m);
              if (Math.max(...means) - Math.min(...means) > 30) continue;
              const score = good.length + (rw * rh) / (w * h);
              if (!best || score > best.score) best = { score, rect: [L / w, T / h, R / w, B / h], sides: good.length, t };
            }
          }
        }
      }
    }
  }
  return best;
}

// `source` is anything drawImage accepts (video, canvas, image) with natural size sw x sh.
export function detectFrame(source, sw, sh) {
  const scale = N / Math.max(sw, sh);
  const w = Math.max(8, Math.round(sw * scale));
  const h = Math.max(8, Math.round(sh * scale));
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const ctx = c.getContext('2d', { willReadFrequently: true });
  const deadline = performance.now() + 700;
  for (const a of ANGLES) {
    if (performance.now() > deadline) break;
    ctx.save();
    ctx.fillStyle = '#808080';
    ctx.fillRect(0, 0, w, h);
    ctx.translate(w / 2, h / 2);
    ctx.rotate((a * Math.PI) / 180);
    ctx.drawImage(source, -w / 2, -h / 2, w, h);
    ctx.restore();
    const hit = search(luminance(ctx, w, h), w, h, deadline);
    if (hit) return { found: true, angle: a, ...hit };
  }
  return { found: false };
}
