package io.github.krixhnarr.wilddex.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Screen detector: a phone camera pointed at a display picks up the display's
// pixel grid, which shows as a moiré or fine grid — a few very sharp, strong
// peaks in the image's frequency spectrum. Fur, feathers, skin, leaves and
// grass have broad, smooth spectra instead. Works on native-resolution
// grayscale (no downscaling, which would blur the grid away).

object ScreenDetect {
    const val SIZE = 256 // analysed square, native camera pixels
    private const val R_MIN = 12 // ignore coarse structure (stripes on a tabby, fences far away)
    private const val R_MAX = 124
    /** A peak must stand this far above the typical power at its frequency. */
    const val PEAK_RATIO = 80.0
    /** ...and the sharpest few peaks must carry this share of the fine detail. */
    const val PEAK_SHARE = 0.06

    data class Result(val found: Boolean, val ratio: Double, val share: Double)

    /** [g] is a [SIZE]×[SIZE] grayscale crop (0..255). */
    fun detect(g: FloatArray): Result {
        val n = SIZE
        val re = DoubleArray(n * n); val im = DoubleArray(n * n)
        var mean = 0.0
        for (v in g) mean += v
        mean /= g.size
        // Hann window stops the crop's edges from smearing energy across the spectrum
        val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
        for (y in 0 until n) for (x in 0 until n) re[y * n + x] = (g[y * n + x] - mean) * win[x] * win[y]
        fft2(re, im, n)

        // power per radius band: find the typical (median) level of each band
        val bands = Array(R_MAX + 1) { ArrayList<Double>() }
        val power = DoubleArray(n * n)
        for (v in 0 until n) for (u in 0 until n) {
            val fu = if (u <= n / 2) u else u - n
            val fv = if (v <= n / 2) v else v - n
            val r = hypot(fu.toDouble(), fv.toDouble()).toInt()
            if (r < R_MIN || r > R_MAX) continue
            val p = re[v * n + u] * re[v * n + u] + im[v * n + u] * im[v * n + u]
            power[v * n + u] = p
            bands[r] += p
        }
        val median = DoubleArray(R_MAX + 1) { r -> bands[r].sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] } }
        var total = 0.0
        val ratios = ArrayList<Pair<Double, Double>>() // (ratio, power)
        for (v in 0 until n) for (u in 0 until n) {
            val p = power[v * n + u]
            if (p == 0.0) continue
            total += p
            val fu = if (u <= n / 2) u else u - n
            val fv = if (v <= n / 2) v else v - n
            val r = hypot(fu.toDouble(), fv.toDouble()).toInt()
            val m = median[r]
            if (m > 0) ratios += (p / m) to p
        }
        if (total <= 0 || ratios.isEmpty()) return Result(false, 0.0, 0.0)
        ratios.sortByDescending { it.first }
        val best = ratios[0].first
        // the 8 sharpest bins (a grid gives symmetric pairs and harmonics)
        val share = ratios.take(8).sumOf { it.second } / total
        return Result(best >= PEAK_RATIO && share >= PEAK_SHARE, best, share)
    }

    // ---------------------------------------------------------------- FFT
    private fun fft2(re: DoubleArray, im: DoubleArray, n: Int) {
        val rr = DoubleArray(n); val ii = DoubleArray(n)
        for (y in 0 until n) {
            for (x in 0 until n) { rr[x] = re[y * n + x]; ii[x] = im[y * n + x] }
            fft(rr, ii)
            for (x in 0 until n) { re[y * n + x] = rr[x]; im[y * n + x] = ii[x] }
        }
        for (x in 0 until n) {
            for (y in 0 until n) { rr[y] = re[y * n + x]; ii[y] = im[y * n + x] }
            fft(rr, ii)
            for (y in 0 until n) { re[y * n + x] = rr[y]; im[y * n + x] = ii[y] }
        }
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

}
