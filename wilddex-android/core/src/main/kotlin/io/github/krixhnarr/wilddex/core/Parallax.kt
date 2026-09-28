package io.github.krixhnarr.wilddex.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// Live-animal check: is the thing in front of the camera a real 3D scene, or
// a picture on a flat surface — a print, a photo, a screen, a video?
//
// While the player slides the phone sideways, we record a short burst of
// frames and track feature points through it. Every point on a flat surface
// moves according to one homography, however the camera moves. A real scene
// breaks that: the animal and its background sit at different depths
// (parallax), and a living animal moves by itself. So:
//   - points that fit one homography        -> flat picture -> rejected
//   - points off that plane whose offset follows the player's left-right
//     slide (parallax)                       -> real 3D      -> accepted
//   - off-plane motion that ignores the slide -> a video on a flat screen -> rejected
//   - hardly any motion / one way only       -> ask the player to slide
// Ported from the web app's parallax.js; numbers match it.

/** A grayscale frame (0..255) at analysis size. */
class GrayFrame(val g: FloatArray, val w: Int, val h: Int)

enum class Verdict { LIVE, VIDEO, FLAT, STILL, ONEWAY, TEXTURE }

data class SweepResult(
    val verdict: Verdict, val tracks: Int, val range: Double = 0.0,
    val parallax: Int = 0, val pGrouped: Int = 0, val indep: Int = 0, val iGrouped: Int = 0,
)

object Parallax {
    const val LONG = 256 // analysis width (long side), px
    private const val PATCH = 4 // 9x9 patches for tracking
    private const val SEARCH = 7 // search radius around the predicted position
    private const val MAX_POINTS = 200
    const val RESID = 2.2 // px at LONG=256: beyond tracking noise
    const val MIN_TRACKS = 14
    private const val PARALLAX_CORR = 0.8 // off-plane motion must follow the hand this closely

    private class P(val x: Double, val y: Double)

    // ---------------------------------------------------------------- features
    private fun corners(g: FloatArray, w: Int, h: Int): List<P> {
        val score = FloatArray(w * h)
        val m = 10
        for (y in m until h - m) {
            for (x in m until w - m) {
                var sxx = 0.0; var syy = 0.0; var sxy = 0.0
                for (dy in -2..2) for (dx in -2..2) {
                    val i = (y + dy) * w + (x + dx)
                    val gx = g[i + 1].toDouble() - g[i - 1]
                    val gy = g[i + w].toDouble() - g[i - w]
                    sxx += gx * gx; syy += gy * gy; sxy += gx * gy
                }
                // Shi-Tomasi: smaller eigenvalue of the structure tensor
                val tr = sxx + syy
                val det = sxx * syy - sxy * sxy
                score[y * w + x] = (tr / 2 - sqrt(max(0.0, tr * tr / 4 - det))).toFloat()
            }
        }
        // Spread points over a grid so a busy background can't take them all;
        // the centre cells — where the animal is — get more.
        val gx = 8; val gy = 6
        val cells = List(gx * gy) { mutableListOf<Triple<Float, Int, Int>>() }
        for (y in m until h - m) for (x in m until w - m) {
            val v = score[y * w + x]
            if (v > 250) {
                val cy = minOf(gy - 1, floor(y.toDouble() / h * gy).toInt())
                val cx = minOf(gx - 1, floor(x.toDouble() / w * gx).toInt())
                cells[cy * gx + cx] += Triple(v, x, y)
            }
        }
        val taken = mutableListOf<P>()
        val minD2 = 36.0
        cells.forEachIndexed { ci, cand ->
            val cxI = ci % gx; val cyI = ci / gx
            val central = cxI in 2..5 && cyI in 1..4
            val quota = if (central) 6 else 3
            val sorted = cand.sortedByDescending { it.first }
            var n = 0
            for ((_, x, y) in sorted) {
                if (n >= quota) break
                if (taken.all { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) >= minD2 }) { taken += P(x.toDouble(), y.toDouble()); n++ }
            }
        }
        return taken.take(MAX_POINTS)
    }

    // ---------------------------------------------------------------- tracking
    private fun bilinear(g: FloatArray, w: Int, x: Double, y: Double): Double {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val i = y0 * w + x0
        return g[i] * (1 - fx) * (1 - fy) + g[i + 1] * fx * (1 - fy) + g[i + w] * (1 - fx) * fy + g[i + w + 1] * fx * fy
    }

    /** Best match of the patch around (px,py) in `a` near (qx,qy) in `b`, with sub-pixel refinement. */
    private fun match(a: FloatArray, b: FloatArray, w: Int, h: Int, px: Double, py: Double, qx: Double, qy: Double): P? {
        val lim = PATCH + SEARCH + 2
        if (qx < lim || qy < lim || qx > w - lim || qy > h - lim || px < PATCH + 1 || py < PATCH + 1 || px > w - PATCH - 2 || py > h - PATCH - 2) return null
        val n = (2 * PATCH + 1) * (2 * PATCH + 1)
        val tpl = DoubleArray(n)
        var mean = 0.0
        var k = 0
        for (dy in -PATCH..PATCH) for (dx in -PATCH..PATCH) { val v = bilinear(a, w, px + dx, py + dy); tpl[k++] = v; mean += v }
        mean /= n
        var varT = 0.0
        for (v in tpl) varT += (v - mean) * (v - mean)
        if (varT / n < 60) return null // too flat to track reliably
        val cx = jsRound(qx); val cy = jsRound(qy)
        val size = 2 * SEARCH + 1
        val sad = DoubleArray(size * size)
        var best = Double.POSITIVE_INFINITY; var bi = -1
        for (sy in -SEARCH..SEARCH) for (sx in -SEARCH..SEARCH) {
            var s = 0.0; var kk = 0
            for (dy in -PATCH..PATCH) {
                val row = (cy + sy + dy) * w + cx + sx
                for (dx in -PATCH..PATCH) { s += abs(b[row + dx] - tpl[kk]); kk++ }
            }
            val idx = (sy + SEARCH) * size + (sx + SEARCH)
            sad[idx] = s
            if (s < best) { best = s; bi = idx }
        }
        val bx = (bi % size) - SEARCH; val by = bi / size - SEARCH
        if (abs(bx) == SEARCH || abs(by) == SEARCH) return null // hit the search edge
        if (best / n > 26) return null // poor match
        fun at(x: Int, y: Int) = sad[(y + SEARCH) * size + (x + SEARCH)]
        fun sub(l: Double, c: Double, r: Double): Double { val d = l - 2 * c + r; return if (d > 0) 0.5 * (l - r) / d else 0.0 }
        return P(cx + bx + sub(at(bx - 1, by), best, at(bx + 1, by)), cy + by + sub(at(bx, by - 1), best, at(bx, by + 1)))
    }

    /** Each point keeps its frame-0 patch as the template, so tracking error doesn't pile up. */
    private fun track(frames: List<GrayFrame>): List<List<P>> {
        val w = frames[0].w; val h = frames[0].h
        val pts = corners(frames[0].g, w, h)
        val tracks = pts.map { mutableListOf(it) }
        val vel = pts.map { doubleArrayOf(0.0, 0.0) }
        val alive = BooleanArray(pts.size) { true }
        for (f in 1 until frames.size) {
            for (i in tracks.indices) {
                if (!alive[i]) continue
                val p0 = tracks[i][0]
                val prev = tracks[i][f - 1]
                val m = match(frames[0].g, frames[f].g, w, h, p0.x, p0.y, prev.x + vel[i][0], prev.y + vel[i][1])
                if (m == null) { alive[i] = false; continue }
                vel[i][0] = m.x - prev.x; vel[i][1] = m.y - prev.y
                tracks[i] += m
            }
        }
        return tracks.filterIndexed { i, _ -> alive[i] }
    }

    // ---------------------------------------------------------------- homography
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
            if (abs(m[p][c]) < 1e-10) return null
            val t = m[c]; m[c] = m[p]; m[p] = t
            for (r in 0 until n) {
                if (r == c) continue
                val f = m[r][c] / m[c][c]
                for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { i -> m[i][n] / m[i][i] }
    }

    /** Least-squares homography (h33 = 1) from point pairs, via the normal equations. */
    private fun fitH(src: List<P>, dst: List<P>): DoubleArray? {
        val ata = Array(8) { DoubleArray(8) }
        val atb = DoubleArray(8)
        for (i in src.indices) {
            val x = src[i].x; val y = src[i].y; val u = dst[i].x; val v = dst[i].y
            val rows = arrayOf(
                doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u),
                doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v),
            )
            for (r in rows) for (aa in 0 until 8) {
                atb[aa] += r[aa] * r[8]
                for (bb in 0 until 8) ata[aa][bb] += r[aa] * r[bb]
            }
        }
        val hv = solve(ata, atb) ?: return null
        return hv + 1.0
    }

    private fun project(h: DoubleArray, p: P): P {
        val z = h[6] * p.x + h[7] * p.y + h[8]
        return P((h[0] * p.x + h[1] * p.y + h[2]) / z, (h[3] * p.x + h[4] * p.y + h[5]) / z)
    }

    private fun ransacResiduals(src: List<P>, dst: List<P>, thresh: Double, iters: Int = 300): List<P>? {
        val n = src.size
        var best: DoubleArray? = null; var bestIn = -1
        // Normalise coordinates for numerical stability.
        fun norm(pts: List<P>): Triple<Double, Double, Double> {
            val mx = pts.sumOf { it.x } / pts.size
            val my = pts.sumOf { it.y } / pts.size
            val meanD = pts.sumOf { hypot(it.x - mx, it.y - my) } / pts.size
            val sc = sqrt(2.0) / (if (meanD == 0.0) 1.0 else meanD)
            return Triple(mx, my, sc)
        }
        val (smx, smy, ssc) = norm(src); val (dmx, dmy, dsc) = norm(dst)
        val s = src.map { P((it.x - smx) * ssc, (it.y - smy) * ssc) }
        val d = dst.map { P((it.x - dmx) * dsc, (it.y - dmy) * dsc) }
        val t = thresh * dsc
        // Same LCG as the web version, including JavaScript's double-precision
        // overflow before `>>> 0`, so both pick identical samples.
        var seed = 12345.0
        fun rnd(): Double {
            val d = seed * 1103515245.0 + 12345.0
            seed = d.rem(4294967296.0).let { if (it < 0) it + 4294967296.0 else it }
            return seed / 4294967296.0
        }
        repeat(iters) {
            val idx = LinkedHashSet<Int>()
            while (idx.size < 4) idx += floor(rnd() * n).toInt()
            val ii = idx.toList()
            val hm = fitH(ii.map { s[it] }, ii.map { d[it] }) ?: return@repeat
            var cnt = 0
            for (i in 0 until n) { val p = project(hm, s[i]); if (hypot(p.x - d[i].x, p.y - d[i].y) < t) cnt++ }
            if (cnt > bestIn) { bestIn = cnt; best = hm }
        }
        val b0 = best ?: return null
        // Refit on inliers, then measure residual vectors in pixels.
        val inl = (0 until n).filter { val p = project(b0, s[it]); hypot(p.x - d[it].x, p.y - d[it].y) < t }
        val hm = fitH(inl.map { s[it] }, inl.map { d[it] }) ?: b0
        return (0 until n).map { i -> val p = project(hm, s[i]); P((d[i].x - p.x) / dsc, (d[i].y - p.y) / dsc) }
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val n = a.size
        val ma = a.average(); val mb = b.average()
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in 0 until n) { val x = a[i] - ma; val y = b[i] - mb; sab += x * y; saa += x * x; sbb += y * y }
        return if (saa != 0.0 && sbb != 0.0) sab / sqrt(saa * sbb) else 0.0
    }

    private fun groupedCount(pts: List<P>): Int =
        pts.count { p -> pts.count { q -> q !== p && hypot(q.x - p.x, q.y - p.y) < 40 } >= 2 }

    fun analyse(frames: List<GrayFrame>): SweepResult {
        val tracks = track(frames)
        if (tracks.size < MIN_TRACKS) return SweepResult(Verdict.TEXTURE, tracks.size)
        val tn = tracks[0].size
        val first = tracks.map { it[0] }

        // Residual of every point from the dominant plane motion, frame by frame.
        val res = tracks.map { mutableListOf(P(0.0, 0.0)) }
        for (f in 1 until tn) {
            val r = ransacResiduals(first, tracks.map { it[f] }, 1.2, 140)
            if (r == null) res.forEach { it += P(0.0, 0.0) } else r.forEachIndexed { i, v -> res[i] += v }
        }

        // Main direction of the hand's motion and the scene's median motion profile along it.
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (t in tracks) for (p in t) { val dx = p.x - t[0].x; val dy = p.y - t[0].y; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val ang = 0.5 * atan2(2 * sxy, sxx - syy)
        val ux = cos(ang); val uy = sin(ang)
        val g = DoubleArray(tn) { f ->
            val dd = tracks.map { (it[f].x - it[0].x) * ux + (it[f].y - it[0].y) * uy }.sorted()
            dd[dd.size / 2]
        }
        val gMax = g.max(); val gMin = g.min()
        // How far things moved: 80th percentile of per-point travel.
        val travel = tracks.map { t ->
            val a = t.map { (it.x - t[0].x) * ux + (it.y - t[0].y) * uy }
            a.max() - a.min()
        }.sorted()
        val range = travel[floor(travel.size * 0.8).toInt()]
        val rangeR = round(range * 10) / 10
        if (range < 8) return SweepResult(Verdict.STILL, tracks.size, rangeR)
        // Did the motion reverse? (distance from the far end back towards the start)
        val far = if (abs(gMax - g[0]) > abs(gMin - g[0])) gMax else gMin
        val back = abs(far - g[tn - 1])
        if (back < max(1.5, 0.25 * (gMax - gMin))) return SweepResult(Verdict.ONEWAY, tracks.size, rangeR)

        val parallax = mutableListOf<P>(); val indep = mutableListOf<P>()
        res.forEachIndexed { i, e ->
            val amp = e.maxOf { hypot(it.x, it.y) }
            if (amp <= RESID) return@forEachIndexed
            val along = DoubleArray(e.size) { e[it].x * ux + e[it].y * uy }
            val alongAmp = along.maxOf { abs(it) }
            val r = pearson(along, g)
            if (abs(r) >= PARALLAX_CORR && alongAmp >= 0.6 * amp) parallax += first[i] else indep += first[i]
        }
        val pg = groupedCount(parallax); val ig = groupedCount(indep)
        val verdict = when {
            parallax.size >= 6 && pg >= 5 && parallax.size.toDouble() / tracks.size >= 0.08 -> Verdict.LIVE
            indep.size >= 10 && ig >= 8 && indep.size.toDouble() / tracks.size >= 0.08 -> Verdict.VIDEO
            else -> Verdict.FLAT
        }
        return SweepResult(verdict, tracks.size, rangeR, parallax.size, pg, indep.size, ig)
    }
}
