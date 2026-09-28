package io.github.krixhnarr.wilddex.core

// Turns the recogniser's 1000 ImageNet probabilities into a WildDex verdict, and
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

    /**
     * MATCH: sure enough to register the card. UNSURE: an animal, but not
     * clearly one card — the player rescans (there is no "pick one" option,
     * so nobody can talk the scanner into a card). OBJECT / NOTHING: no animal.
     */
    enum class Kind { MATCH, UNSURE, OBJECT, NOTHING }

    data class Verdict(
        val kind: Kind, val top: Candidate, val alternatives: List<Candidate>,
        val animal: Double, val obj: Int, val objectScore: Double,
        /** How far ahead of the runner-up the top card is. */
        val margin: Double = 0.0,
        /** Did every frame of the scan pick the same card? */
        val agree: Boolean = true,
    )

    /** Classes under this probability don't count towards a card (keeps 120 dog breeds from out-voting one horse class). */
    const val CLASS_FLOOR = 0.01
    /** A card needs at least this score... */
    const val MATCH_TOP = 0.6
    /** ...and must lead the next card by this much. Tuned on 756 independent wildlife photos:
     *  84% of scans accepted from a single frame, 2.2% of those wrong, before the frame-agreement check. */
    const val MATCH_MARGIN = 0.4

    private fun rank(p: FloatArray): List<Candidate> = Dex.entries.map { e ->
        var s = 0.0
        var best = e.c[0]
        for (i in e.c) { if (p[i] >= CLASS_FLOOR) s += p[i]; if (p[i] > p[best]) best = i }
        Candidate(e, s, best)
    }.sortedByDescending { it.score }

    /** One set of probabilities (one frame, or several averaged). */
    fun interpret(p: FloatArray): Verdict = decide(listOf(p))

    /**
     * Decides from one or more frames of the same scan: their probabilities
     * are averaged, and a MATCH also needs every frame to agree on the card.
     */
    fun decide(frames: List<FloatArray>): Verdict {
        require(frames.isNotEmpty())
        val p = FloatArray(1000) { i -> frames.sumOf { it[i].toDouble() }.toFloat() / frames.size }
        var animal = 0.0
        for (i in 0 until Dex.animalClassLimit) animal += p[i]
        val ranked = rank(p)
        var obj = Dex.animalClassLimit
        for (i in Dex.animalClassLimit until 1000) if (p[i] > p[obj]) obj = i
        val top = ranked[0]
        val margin = top.score - ranked[1].score
        val agree = frames.size < 2 || frames.all { rank(it)[0].entry == top.entry }
        val alternatives = ranked.take(4).filter { it.score >= 0.04 }
        val kind = when {
            top.score >= MATCH_TOP && margin >= MATCH_MARGIN && agree -> Kind.MATCH
            animal >= 0.35 && top.score >= 0.12 -> Kind.UNSURE
            p[obj] >= 0.3 -> Kind.OBJECT
            top.score >= 0.12 -> Kind.UNSURE
            else -> Kind.NOTHING
        }
        return Verdict(kind, top, alternatives, animal, obj, p[obj].toDouble(), margin, agree)
    }
}
