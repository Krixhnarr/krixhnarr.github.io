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
    private fun sweep(kind: String): List<GrayFrame> = (0 until 20).map { f ->
        val phase = if (f < 10) f / 9.0 else (19 - f) / 9.0
        val cam = phase * 24
        val w = 256; val h = 192
        val g = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val inFg = ((x - 128) * (x - 128)).toDouble() / 3600 + ((y - 96) * (y - 96)).toDouble() / 2500 < 1
            g[y * w + x] = (if (kind == "live" && inFg) noise(x + cam * 2.2 + 1000, y.toDouble(), 5, 2) else noise(x + cam, y.toDouble(), 6, 1)).toFloat()
        }
        GrayFrame(g, w, h)
    }

    @Test fun parallaxSweeps() {
        for (kind in listOf("live", "flat")) {
            val js = p["sweeps"]!!.jsonObject[kind]!!.jsonObject
            val r = Parallax.analyse(sweep(kind))
            assertEquals("$kind verdict", js["verdict"]!!.jsonPrimitive.content, r.verdict.name.lowercase())
            assertEquals("$kind tracks", js["tracks"]!!.jsonPrimitive.int, r.tracks)
            assertEquals("$kind parallax", js["parallax"]!!.jsonPrimitive.int, r.parallax)
            assertEquals("$kind indep", js["indep"]!!.jsonPrimitive.int, r.indep)
            assertEquals("$kind range", js["range"]!!.jsonPrimitive.double, r.range, 0.051)
        }
    }

}
