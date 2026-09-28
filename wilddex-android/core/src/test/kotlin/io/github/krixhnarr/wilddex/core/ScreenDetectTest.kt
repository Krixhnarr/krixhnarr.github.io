package io.github.krixhnarr.wilddex.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Real textures must pass; a display's pixel grid / moiré must be caught. */
class ScreenDetectTest {
    private val n = ScreenDetect.SIZE

    private fun lattice(ix: Int, iy: Int, salt: Int): Double {
        var h = ix * 374761393 + iy * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
    private fun noise(x: Double, y: Double, scale: Double, salt: Int): Double {
        val fx = x / scale; val fy = y / scale
        val x0 = floor(fx); val y0 = floor(fy); val tx = fx - x0; val ty = fy - y0
        val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
        val a = lattice(x0.toInt(), y0.toInt(), salt); val b = lattice(x0.toInt() + 1, y0.toInt(), salt)
        val c = lattice(x0.toInt(), y0.toInt() + 1, salt); val d = lattice(x0.toInt() + 1, y0.toInt() + 1, salt)
        return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy
    }
    /** Multi-scale noise: something like fur, grass or a busy room. */
    private fun natural(seed: Int, fine: Double = 1.0): (Int, Int) -> Double = { x, y ->
        120 + 60 * noise(x.toDouble(), y.toDouble(), 40.0, seed) + 40 * noise(x.toDouble(), y.toDouble(), 9.0, seed + 1) +
            25 * fine * noise(x.toDouble(), y.toDouble(), 2.5, seed + 2) + 12 * fine * noise(x.toDouble(), y.toDouble(), 1.2, seed + 3) - 60
    }

    private fun image(f: (Int, Int) -> Double, noiseAmp: Double = 3.0, seed: Int = 1): FloatArray {
        val r = Random(seed)
        return FloatArray(n * n) { i -> (f(i % n, i / n) + (r.nextDouble() - 0.5) * 2 * noiseAmp).coerceIn(0.0, 255.0).toFloat() }
    }

    /** What a camera sees of a display: the picture times the display's pixel grid, sampled by the camera. */
    private fun screen(picture: (Int, Int) -> Double, pitch: Double, depth: Double = 0.25, angle: Double = 0.0): (Int, Int) -> Double = { x, y ->
        val ca = kotlin.math.cos(angle); val sa = kotlin.math.sin(angle)
        val u = x * ca - y * sa; val v = x * sa + y * ca
        val grid = 1 - depth * (0.5 + 0.5 * sin(2 * PI * u / pitch)) * (0.5 + 0.5 * sin(2 * PI * v / pitch))
        picture(x, y) * grid
    }

    private fun check(expect: Boolean, g: FloatArray, label: String) {
        val r = ScreenDetect.detect(g)
        println("$label: $r")
        if (expect) assertTrue("$label should be a screen: $r", r.found) else assertFalse("$label should be real: $r", r.found)
    }

    @Test fun realTexturesPass() {
        for (s in 1..5) check(false, image(natural(s * 10)), "fur/foliage $s")
        check(false, image(natural(7, fine = 2.0), noiseAmp = 8.0), "coarse grain + sensor noise")
        check(false, image({ x, y -> natural(3)(x, y) + 40 * sin(2 * PI * (x * 0.8 + y * 0.3) / 40) }), "tabby stripes")
        check(false, image({ x, y -> 128 + 50 * sin(2 * PI * x / 60) }), "smooth gradient")
        check(false, image({ _, _ -> 128.0 }, noiseAmp = 6.0), "blank wall, noisy")
    }

    @Test fun screensCaught() {
        check(true, image(screen(natural(11), pitch = 3.0)), "display grid, 3 px")
        check(true, image(screen(natural(12), pitch = 4.3, angle = 0.2)), "display grid, 4.3 px, tilted")
        check(true, image(screen(natural(13), pitch = 1.07, depth = 0.3)), "moiré (grid finer than camera pixels)")
        check(true, image(screen(natural(14), pitch = 2.4, depth = 0.15), noiseAmp = 5.0), "faint grid + noise")
    }
}
