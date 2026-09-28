package io.github.krixhnarr.wilddex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The scanner registers a card only when it's clearly one animal; otherwise the player rescans. */
class RecognitionTest {
    private fun probs(vararg pairs: Pair<Int, Double>): FloatArray {
        val p = FloatArray(1000) { 0.0005f }
        for ((i, v) in pairs) p[i] = v.toFloat()
        val s = p.sum()
        return FloatArray(1000) { p[it] / s }
    }
    private fun cls(key: String, n: Int = 0) = Dex.byKey.getValue(key).c[n]

    @Test fun clearAnimalIsAMatch() {
        val v = Recognition.decide(listOf(probs(cls("red-fox") to 0.85)))
        assertEquals(Recognition.Kind.MATCH, v.kind)
        assertEquals("red-fox", v.top.entry.k)
    }

    @Test fun breedsOfOneCardAddUp() {
        // 3 dog breeds at 30% each → one confident "dog" card
        val v = Recognition.decide(listOf(probs(cls("dog", 0) to 0.3, cls("dog", 1) to 0.3, cls("dog", 2) to 0.3)))
        assertEquals(Recognition.Kind.MATCH, v.kind)
        assertEquals("dog", v.top.entry.k)
    }

    @Test fun lookAlikesMeanRescanNotChoose() {
        // grey wolf vs husky-like dog, close call → UNSURE (player rescans; no pick list)
        val v = Recognition.decide(listOf(probs(cls("grey-wolf") to 0.5, cls("dog") to 0.35)))
        assertEquals(Recognition.Kind.UNSURE, v.kind)
    }

    @Test fun confidentButNotFarEnoughAhead() {
        val v = Recognition.decide(listOf(probs(cls("lion") to 0.62, cls("cougar") to 0.3)))
        assertEquals(Recognition.Kind.UNSURE, v.kind)
    }

    @Test fun framesMustAgree() {
        val a = probs(cls("cheetah") to 0.95)
        val b = probs(cls("leopard") to 0.55, cls("cheetah") to 0.3)
        val v = Recognition.decide(listOf(a, b))
        assertFalse(v.agree)
        assertEquals(Recognition.Kind.UNSURE, v.kind)
        val w = Recognition.decide(listOf(a, probs(cls("cheetah") to 0.8)))
        assertEquals(Recognition.Kind.MATCH, w.kind)
    }

    @Test fun diffuseBreedNoiseDoesNotBeatOneStrongClass() {
        // a horse photo: sorrel 60%, plus 0.4% spread over 100 dog breeds (40% total)
        val p = FloatArray(1000) { 0f }
        p[cls("horse")] = 0.6f
        for (i in Dex.byKey.getValue("dog").c.take(100)) p[i] = 0.004f
        val v = Recognition.decide(listOf(p))
        assertEquals("horse", v.top.entry.k)
        assertEquals(Recognition.Kind.MATCH, v.kind)
    }

    @Test fun objectsAndNothing() {
        assertEquals(Recognition.Kind.OBJECT, Recognition.decide(listOf(probs(700 to 0.8))).kind)
        assertEquals(Recognition.Kind.NOTHING, Recognition.decide(listOf(probs())).kind)
    }
}
