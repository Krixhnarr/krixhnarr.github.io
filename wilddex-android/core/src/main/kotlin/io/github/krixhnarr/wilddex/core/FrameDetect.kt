package io.github.krixhnarr.wilddex.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Screen / print frame detector.
//
// A photo of an animal shown on a phone, monitor, laptop or TV — or printed
// on paper — almost always has a thin, evenly coloured frame around the
// picture: the device bezel (dark) or the print margin (light). Real scenes
// rarely have one. We look for such a ring enclosing the centre of the camera
// frame, trying a few small rotations for a tilted phone or photo. Uses
// integral images so every candidate rectangle is scored in constant time.
// Ported from the web app's liveness.js.

object FrameDetect {
    const val N = 320 // long side of the analysis image
    private val ANGLES = intArrayOf(0, -5, 5, -10, 10)
    private const val UNIFORM_SD = 17.0 // a bezel / margin strip is nearly flat
    private const val CONTRAST = 22.0 // ...and clearly different from what's next to it
    private const val BUSY_SD = 24.0 // or the neighbour is busy (picture content)
    private const val OUTER_CONTRAST = 26.0 // bezel/margin vs the table, wall or hand around the device

    data class Hit(val found: Boolean, val angle: Int = 0, val score: Double = 0.0, val sides: Int = 0, val thickness: Int = 0)

    private class Stat(val m: Double, val sd: Double)

    private class Integral(y: DoubleArray, w: Int, h: Int) {
        private val ww = w + 1
        private val s = DoubleArray(ww * (h + 1))
        private val q = DoubleArray(ww * (h + 1))
        init {
            for (r in 1..h) {
                var rs = 0.0; var rq = 0.0
                for (c in 1..w) {
                    val v = y[(r - 1) * w + (c - 1)]
                    rs += v; rq += v * v
                    s[r * ww + c] = s[(r - 1) * ww + c] + rs
                    q[r * ww + c] = q[(r - 1) * ww + c] + rq
                }
            }
        }
        /** stats of rect [x0,x1) x [y0,y1) */
        fun st(x0: Int, y0: Int, x1: Int, y1: Int): Stat {
            val a = y0 * ww + x0; val b = y0 * ww + x1; val c = y1 * ww + x0; val d = y1 * ww + x1
            val n = (x1 - x0) * (y1 - y0)
            if (n <= 0) return Stat(0.0, 999.0)
            val m = (s[d] - s[b] - s[c] + s[a]) / n
            val v = (q[d] - q[b] - q[c] + q[a]) / n - m * m
            return Stat(m, sqrt(max(0.0, v)))
        }
    }

    private fun sideOk(ring: Stat, inner: Stat, outer: Stat): Boolean {
        if (ring.sd > UNIFORM_SD) return false
        val inOk = abs(inner.m - ring.m) > CONTRAST || inner.sd > BUSY_SD
        val outOk = abs(outer.m - ring.m) > OUTER_CONTRAST
        return inOk && outOk
    }

    private class Peaks(val left: List<Int>, val right: List<Int>, val top: List<Int>, val bottom: List<Int>)

    /** Columns / rows where long straight vertical / horizontal edges sit. */
    private fun edgePeaks(y: DoubleArray, w: Int, h: Int): Peaks {
        val col = DoubleArray(w); val row = DoubleArray(h)
        for (r in 1 until h - 1) for (c in 1 until w - 1) {
            val i = r * w + c
            val ax = abs(y[i + 1] - y[i - 1]); val ay = abs(y[i + w] - y[i - w])
            if (ax > 18 && ax > 2 * ay) col[c] += 1.0
            if (ay > 18 && ay > 2 * ax) row[r] += 1.0
        }
        fun pick(arr: DoubleArray, from: Int, to: Int, len: Int, k: Int): List<Int> {
            val idx = mutableListOf<Int>()
            for (i in from until to) {
                val prev = if (i - 1 >= 0) arr[i - 1] else 0.0
                val next = if (i + 1 < arr.size) arr[i + 1] else 0.0
                if (arr[i] >= 0.15 * len && arr[i] >= prev && arr[i] >= next) idx += i
            }
            return idx.sortedByDescending { arr[it] }.take(k)
        }
        return Peaks(
            pick(col, 2, floor(w * 0.45).toInt(), h, 16), pick(col, kotlin.math.ceil(w * 0.55).toInt(), w - 2, h, 16),
            pick(row, 2, floor(h * 0.45).toInt(), w, 8), pick(row, kotlin.math.ceil(h * 0.55).toInt(), h - 2, w, 8),
        )
    }

    private fun search(y: DoubleArray, w: Int, h: Int, deadline: Long): Hit? {
        val st = Integral(y, w, h)
        val pk = edgePeaks(y, w, h)
        var best: Hit? = null
        // An edge peak can be either side of the bezel line; try the strip on both sides of it.
        fun variants(arr: List<Int>, sign: Int) = arr.flatMap { listOf(it, it + sign) }
        for (l0 in variants(pk.left, 1)) {
            if (System.currentTimeMillis() > deadline) break
            for (r0 in variants(pk.right, 0)) {
                val rw = r0 - l0
                if (rw < 0.22 * w) continue
                for (t0 in variants(pk.top, 1)) for (b0 in variants(pk.bottom, 0)) {
                    val rh = b0 - t0
                    if (rh < 0.22 * h) continue
                    val maxT = max(2, floor(0.08 * min(rw, rh)).toInt())
                    for (t in 2..min(12, maxT)) for (inward in booleanArrayOf(true, false)) {
                        // ring strip on the inside (edge = outer bezel boundary) or outside (edge = screen boundary)
                        val l = if (inward) l0 else l0 - t
                        val r = if (inward) r0 else r0 + t
                        val tp = if (inward) t0 else t0 - t
                        val bt = if (inward) b0 else b0 + t
                        if (l < 1 || tp < 1 || r > w - 1 || bt > h - 1) continue
                        // Middle 70% of each side: skips rounded phone corners and fingers.
                        val iy = jsRound((bt - tp) * 0.15); val ix = jsRound((r - l) * 0.15)
                        val y0 = tp + iy; val y1 = bt - iy; val x0 = l + ix; val x1 = r - ix
                        val sides = listOf(
                            Triple(st.st(l, y0, l + t, y1), st.st(l + t, y0, l + 2 * t, y1), st.st(max(0, l - t), y0, l, y1)),
                            Triple(st.st(r - t, y0, r, y1), st.st(r - 2 * t, y0, r - t, y1), st.st(r, y0, min(w, r + t), y1)),
                            Triple(st.st(x0, tp, x1, tp + t), st.st(x0, tp + t, x1, tp + 2 * t), st.st(x0, max(0, tp - t), x1, tp)),
                            Triple(st.st(x0, bt - t, x1, bt), st.st(x0, bt - 2 * t, x1, bt - t), st.st(x0, bt, x1, min(h, bt + t))),
                        )
                        val good = sides.filter { sideOk(it.first, it.second, it.third) }
                        if (good.size < 3) continue
                        val means = good.map { it.first.m }
                        if (means.max() - means.min() > 30) continue
                        val score = good.size + rw.toDouble() * rh / (w * h)
                        if (best == null || score > best.score) best = Hit(true, 0, score, good.size, t)
                    }
                }
            }
        }
        return best
    }

    /** Rotates a luminance image about its centre, filling uncovered pixels with mid grey. */
    private fun rotate(src: DoubleArray, w: Int, h: Int, deg: Int): DoubleArray {
        if (deg == 0) return src
        val a = Math.toRadians(deg.toDouble())
        val ca = cos(a); val sa = sin(a)
        val cx = w / 2.0; val cy = h / 2.0
        val out = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            // inverse-map the destination pixel into the source
            val dx = x + 0.5 - cx; val dy = y + 0.5 - cy
            val sx = ca * dx + sa * dy + cx - 0.5
            val sy = -sa * dx + ca * dy + cy - 0.5
            val x0 = floor(sx).toInt(); val y0 = floor(sy).toInt()
            out[y * w + x] = if (x0 < 0 || y0 < 0 || x0 >= w - 1 || y0 >= h - 1) 128.0 else {
                val fx = sx - x0; val fy = sy - y0; val i = y0 * w + x0
                src[i] * (1 - fx) * (1 - fy) + src[i + 1] * fx * (1 - fy) + src[i + w] * (1 - fx) * fy + src[i + w + 1] * fx * fy
            }
        }
        return out
    }

    /** `lum` is the whole camera frame as luminance, already scaled so its long side is [N]. */
    fun detect(lum: DoubleArray, w: Int, h: Int, budgetMs: Long = 700): Hit {
        val deadline = System.currentTimeMillis() + budgetMs
        for (a in ANGLES) {
            if (System.currentTimeMillis() > deadline) break
            val hit = search(rotate(lum, w, h, a), w, h, deadline)
            if (hit != null) return hit.copy(angle = a)
        }
        return Hit(false)
    }

}
