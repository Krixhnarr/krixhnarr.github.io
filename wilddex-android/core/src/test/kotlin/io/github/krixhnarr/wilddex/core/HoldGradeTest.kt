package io.github.krixhnarr.wilddex.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldGradeTest {
    private fun g(conf: Double, steady: Double, depth: Boolean) = Game.holdGrade(conf, steady, depth).grade

    @Test fun grades() {
        assertEquals("S", g(0.9, 1.0, true))   // confident, steady, depth seen
        assertEquals("A", g(0.9, 1.0, false))  // no depth: best is A
        assertEquals("S", g(0.8, 0.7, true))   // a little shake is fine
        assertEquals("A", g(0.5, 0.7, true))
        assertEquals("B", g(0.6, 0.6, false))
        assertEquals("C", g(0.3, 0.2, false))
    }
}
