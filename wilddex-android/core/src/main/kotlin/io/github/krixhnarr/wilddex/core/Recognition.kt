package io.github.krixhnarr.wilddex.core

// Turns MobileNet's 1000 ImageNet probabilities into a WildDex verdict, and
// spots screens and printed pictures. Ported from the web app's classifier.js.

object Recognition {
    /** ImageNet classes the net reports when a "live" scan is really a phone, monitor, TV, laptop, book or magazine. */
    val SPOOF_CLASSES = intArrayOf(
        782, 664, 851, 620, 681, 487, 605, 590, 527, 916, 548, 598, 781, // screens & devices
        921, 917, 922, 611, 918, 692, // book jackets, comics, menus, puzzles, packets
    )

    private fun spoofMass(p: FloatArray) = SPOOF_CLASSES.sumOf { p[it].toDouble() }
    private fun topSpoof(p: FloatArray) = SPOOF_CLASSES.maxBy { p[it] }

    data class Spoof(val blocked: Boolean, val score: Double, val label: Int)

    /** `centre` is the square the player aimed at; `wide` is the whole camera frame zoomed out. */
    fun spoofCheck(centre: FloatArray, wide: FloatArray): Spoof {
        val c = spoofMass(centre)
        val w = spoofMass(wide)
        val blocked = c >= 0.12 || w >= 0.2 || wide[topSpoof(wide)] >= 0.15
        val p = if (w >= c) wide else centre
        return Spoof(blocked, maxOf(c, w), topSpoof(p))
    }

    data class Candidate(val entry: Entry, val score: Double, val form: Int)

    enum class Kind { MATCH, UNSURE, OBJECT, NOTHING }

    data class Verdict(
        val kind: Kind, val top: Candidate, val alternatives: List<Candidate>,
        val animal: Double, val obj: Int, val objectScore: Double,
    )

    fun interpret(p: FloatArray): Verdict {
        var animal = 0.0
        for (i in 0 until Dex.animalClassLimit) animal += p[i]
        val ranked = Dex.entries.map { e ->
            var s = 0.0
            var best = e.c[0]
            for (i in e.c) { s += p[i]; if (p[i] > p[best]) best = i }
            Candidate(e, s, best)
        }.sortedByDescending { it.score }
        var obj = Dex.animalClassLimit
        for (i in Dex.animalClassLimit until 1000) if (p[i] > p[obj]) obj = i
        val top = ranked[0]
        val alternatives = ranked.take(4).filter { it.score >= 0.04 }
        val kind = when {
            top.score >= 0.45 -> Kind.MATCH
            animal >= 0.35 && top.score >= 0.12 -> Kind.UNSURE
            p[obj] >= 0.3 -> Kind.OBJECT
            else -> Kind.NOTHING
        }
        return Verdict(kind, top, alternatives, animal, obj, p[obj].toDouble())
    }
}
