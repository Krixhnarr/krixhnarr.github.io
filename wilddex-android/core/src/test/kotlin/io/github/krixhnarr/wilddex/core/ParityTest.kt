package io.github.krixhnarr.wilddex.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.floor

/**
 * Checks the Kotlin port against the original JavaScript rules. The fixtures in
 * parity.json come from parity.mjs, which runs the web app's code on the same
 * inputs (at 2026-09-28 06:30 UTC).
 */
class ParityTest {
    companion object {
        lateinit var p: JsonObject

        @BeforeClass @JvmStatic
        fun load() {
            p = Json.parseToJsonElement(ParityTest::class.java.getResource("/parity.json")!!.readText()).jsonObject
            Clock.zone = ZoneOffset.UTC
            Clock.now = { Instant.parse("2026-09-28T06:30:00Z").toEpochMilli() }
        }
    }

    private fun arr(key: String): JsonArray = p[key]!!.jsonArray

    @Test fun hashes() {
        for (row in arr("hash")) {
            val (s, h) = row.jsonArray
            assertEquals(s.jsonPrimitive.content, h.jsonPrimitive.long, Game.hash(s.jsonPrimitive.content))
        }
    }

    @Test fun stats() {
        for (row in arr("stats")) {
            val r = row.jsonArray
            val e = Dex.byKey.getValue(r[0].jsonPrimitive.content)
            for ((i, lv) in listOf(1, 7).withIndex()) {
                val js = r[i + 1].jsonObject
                val kt = Game.statsFor(e, lv)
                for (k in Game.STAT_KEYS) assertEquals("${e.k} $k lv$lv", js[k]!!.jsonPrimitive.int, kt[k])
                assertEquals(js["pwr"]!!.jsonPrimitive.int, kt.pwr)
            }
        }
    }

    @Test fun levels() {
        for (row in arr("levels")) {
            val (c, lv) = row.jsonArray
            assertEquals(lv.jsonPrimitive.int, Game.levelFor(c.jsonPrimitive.int))
        }
    }

    @Test fun grades() {
        for (row in arr("grades")) {
            val r = row.jsonArray
            val d = r[1].takeUnless { it is JsonNull }?.jsonObject?.let {
                SweepResult(Verdict.LIVE, it["tracks"]!!.jsonPrimitive.int, it["range"]!!.jsonPrimitive.double, it["parallax"]!!.jsonPrimitive.int)
            }
            val g = Game.syncGrade(r[0].jsonPrimitive.double, d)
            assertEquals(r[2].jsonObject["grade"]!!.jsonPrimitive.content, g.grade)
            assertEquals(r[2].jsonObject["credits"]!!.jsonPrimitive.int, g.credits)
        }
    }

    @Test fun typeMatchups() {
        for (row in arr("mult")) {
            val r = row.jsonArray
            val defs = r[1].jsonArray.map { it.jsonPrimitive.content }
            assertEquals(r[2].jsonPrimitive.double, Battle.mult(r[0].jsonPrimitive.content, defs), 1e-12)
        }
    }

    @Test fun dailyRivalAndSquad() {
        val state = PlayerState(opId = "A1B2C3")
        listOf("dog", "cat", "red-fox", "lion", "koala", "orca", "ladybug", "squirrel").forEachIndexed { i, k ->
            state.caught[k] = CardRecord(count = 1 + i * 3, holo = if (i == 2) 1L else null)
        }
        val js = p["rival"]!!.jsonObject
        val r = Battle.dailyRival(state)
        assertEquals(js["name"]!!.jsonPrimitive.content, r.name)
        assertEquals(js["keys"]!!.jsonArray.map { it.jsonPrimitive.content }, r.keys)
        assertEquals(js["lvs"]!!.jsonArray.map { it.jsonPrimitive.int }, r.lvs)
        assertEquals(js["squad"]!!.jsonArray.map { it.jsonPrimitive.content }, Battle.squad(state))
    }

    @Test fun calendar() {
        val ev = Game.weeklyEvent(PlayerState())
        val js = p["event"]!!.jsonObject
        assertEquals(js["id"]!!.jsonPrimitive.content, ev.def.id)
        assertEquals(js["key"]!!.jsonPrimitive.content, ev.key)
        assertEquals(js["daysLeft"]!!.jsonPrimitive.int, ev.daysLeft)
        assertEquals(p["today"]!!.jsonPrimitive.content, Game.today())
        assertEquals(arr("todayMissions").map { it.jsonPrimitive.content }, Game.missions().map { it.id })
    }

    @Test fun recognition() {
        var seed = 7L
        fun rnd(): Double { seed = ((seed.toInt() * 1664525 + 1013904223).toLong() and 0xFFFFFFFFL); return seed / 4294967296.0 }
        for ((t, row) in arr("interpret").withIndex()) {
            val js = row.jsonObject
            val prob = FloatArray(1000)
            var sum = 0.0
            val hot = floor(rnd() * 1000).toInt()
            for (i in 0 until 1000) { val r = rnd(); prob[i] = (r * r * r * r * r * r * r * r).toFloat(); sum += prob[i] }
            prob[hot] = (prob[hot] + sum * (t % 3)).toFloat()
            var s2 = 0.0
            for (i in 0 until 1000) s2 += prob[i]
            for (i in 0 until 1000) prob[i] = (prob[i] / s2).toFloat()
            assertEquals(js["hot"]!!.jsonPrimitive.int, hot)
            val v = Recognition.interpret(prob)
            assertEquals("case $t kind", js["kind"]!!.jsonPrimitive.content, v.kind.name.lowercase())
            assertEquals("case $t top", js["top"]!!.jsonPrimitive.content, v.top.entry.k)
            assertEquals(js["score"]!!.jsonPrimitive.double, v.top.score, 1e-9)
            assertEquals(js["form"]!!.jsonPrimitive.int, v.top.form)
            assertEquals(js["alts"]!!.jsonArray.map { it.jsonPrimitive.content }, v.alternatives.map { it.entry.k })
            assertEquals(js["object"]!!.jsonPrimitive.int, v.obj)
        }
    }

    // --- synthetic sweeps, generated exactly like parity.mjs
    private fun lattice(ix: Int, iy: Int, salt: Int): Double {
        var h = ix * 374761393 + iy * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296.0 * 255
    }
    private fun noise(x: Double, y: Double, scale: Int, salt: Int): Double {
        val fx = x / scale; val fy = y / scale
        val x0 = floor(fx); val y0 = floor(fy)
        val tx = fx - x0; val ty = fy - y0
        val a = lattice(x0.toInt(), y0.toInt(), salt); val b = lattice(x0.toInt() + 1, y0.toInt(), salt)
        val c = lattice(x0.toInt(), y0.toInt() + 1, salt); val d = lattice(x0.toInt() + 1, y0.toInt() + 1, salt)
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    }
    /**
     * A synthetic sweep: the camera slides right then back. "live" has a
     * foreground ellipse at a different depth (it shifts 2.2× as far as the
     * background); "flat" is one plane; "video" is a plane whose centre moves
     * on its own, up and down, ignoring the hand.
     */
    private fun sweep(
        kind: String, amp: Double = 24.0, exposure: Double = 0.0, repeat: Boolean = false, n: Int = 20,
    ): List<GrayFrame> = (0 until n).map { i ->
        val f = if (repeat) i / 2 * 2 else i // every frame shown twice, like a laggy camera
        val phase = if (f < n / 2) f / (n / 2 - 1.0) else (n - 1 - f) / (n / 2 - 1.0)
        val cam = phase * amp
        val w = 256; val h = 192
        // auto-exposure drifting during the sweep: brightness and contrast change
        val gain = 1 + exposure * kotlin.math.sin(f * 0.7)
        val offset = exposure * 60 * kotlin.math.cos(f * 0.4)
        val g = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val inFg = ((x - 128) * (x - 128)).toDouble() / 3600 + ((y - 96) * (y - 96)).toDouble() / 2500 < 1
            val v = when {
                kind == "live" && inFg -> noise(x + cam * 2.2 + 1000, y.toDouble(), 5, 2)
                kind == "video" && inFg -> noise(x + cam + 1000, y + 14 * kotlin.math.sin(f * 1.3), 5, 2)
                else -> noise(x + cam, y.toDouble(), 6, 1)
            }
            g[y * w + x] = (v * gain + offset).coerceIn(0.0, 255.0).toFloat()
        }
        GrayFrame(g, w, h)
    }

    private fun check(expect: Verdict, frames: List<GrayFrame>, label: String) = checkAny(setOf(expect), frames, label)

    private fun checkAny(expect: Set<Verdict>, frames: List<GrayFrame>, label: String) {
        val r = Parallax.analyse(frames)
        println("$label: $r")
        org.junit.Assert.assertTrue("$label verdict ($r)", r.verdict in expect)
    }

    @Test fun parallaxSweeps() {
        // same scenes the web version was tested on
        val frames = sweep("live")
        repeat(3) { Parallax.analyse(frames) } // warm up the JIT
        val t0 = System.nanoTime(); Parallax.analyse(frames)
        println("analyse took ${(System.nanoTime() - t0) / 1_000_000} ms")
        check(Verdict.LIVE, frames, "live")
        check(Verdict.FLAT, sweep("flat"), "flat")
        check(Verdict.STILL, sweep("live", amp = 0.0), "still")
    }

    /** Things a real phone camera does that used to lose the tracks ("can't verify"). */
    @Test fun realCameraConditions() {
        check(Verdict.LIVE, sweep("live", exposure = 0.25), "live + exposure drift")
        check(Verdict.LIVE, sweep("live", amp = 48.0), "live + fast slide")
        check(Verdict.LIVE, sweep("live", repeat = true), "live + repeated frames")
        check(Verdict.LIVE, sweep("live", amp = 40.0, exposure = 0.2), "live + fast + exposure")
        // repeated frames double the jumps; the scanner waits for fresh frames, so this is the worst it sees
        check(Verdict.LIVE, sweep("live", amp = 32.0, exposure = 0.2, repeat = true), "live + all three")
    }

    /** The same conditions must not let a picture through. */
    @Test fun picturesStillRejected() {
        check(Verdict.FLAT, sweep("flat", exposure = 0.25), "flat + exposure drift")
        check(Verdict.FLAT, sweep("flat", amp = 48.0), "flat + fast slide")
        check(Verdict.FLAT, sweep("flat", amp = 40.0, exposure = 0.2, repeat = true), "flat + all three")
        // a video on a screen is rejected either as a video or as a flat picture
        val rejected = setOf(Verdict.VIDEO, Verdict.FLAT)
        checkAny(rejected, sweep("video"), "video on a screen")
        checkAny(rejected, sweep("video", exposure = 0.2), "video + exposure drift")
        checkAny(rejected, sweep("video", amp = 40.0, repeat = true), "video + fast + repeated")
    }
}
