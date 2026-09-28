package io.github.krixhnarr.wilddex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** Cards level up only by spending credits; re-scanning the same animal earns nothing. */
class LevelingTest {
    @Before fun clock() {
        Clock.zone = ZoneOffset.UTC
        Clock.now = { Instant.parse("2026-09-28T10:00:00Z").toEpochMilli() }
    }

    private val dog = Dex.byKey.getValue("dog")
    private val never = { 1.0 } // no holo

    @Test fun rescanDoesNotLevelOrPay() {
        val s = PlayerState().also { it.migrate() }
        val first = Progress.register(s, dog, dog.c[0], Game.Grade("S", 0.9, 20, 15, "Perfect sync"), never)
        assertEquals(1, Game.cardLevel(s.caught.getValue("dog")))
        val credits = s.shards; val xp = s.xpNow()
        // streak and daily orders are once-a-day, so later re-scans pay nothing at all
        repeat(20) { Progress.register(s, dog, dog.c[0], Game.Grade("S", 0.9, 20, 15, "Perfect sync"), never) }
        assertEquals(1, Game.cardLevel(s.caught.getValue("dog")))
        assertEquals(21, s.caught.getValue("dog").count)
        assert(first.credits > 0)
        // (a daily order such as "re-scan a card you own" may complete, but it pays only when claimed in Ops)
        assertEquals(credits, s.shards)
        assertEquals(xp, s.xpNow())
    }

    @Test fun costs() {
        assertEquals(20, Game.upgradeCost(dog, 1))
        assertEquals(180, Game.upgradeCost(dog, 9))
        assertNull(Game.upgradeCost(dog, 10))
        assertEquals(900, (1..9).sumOf { Game.upgradeCost(dog, it)!! })
        val legend = Dex.entries.first { it.r == 4 }
        assertEquals(60, Game.upgradeCost(legend, 1))
    }

    @Test fun upgradeSpendsCredits() {
        val s = PlayerState(shards = 50).also { it.caught["dog"] = CardRecord(count = 1, level = 1) }
        val up = Progress.upgradeCard(s, "dog")
        assertNotNull(up)
        assertEquals(2, Game.cardLevel(s.caught.getValue("dog")))
        assertEquals(30, s.shards)
        assertNull(Progress.upgradeCard(s, "dog")) // needs 40, has 30
        assertNull(Progress.upgradeCard(s, "cat")) // not owned
        // stats grow with the bought level
        assertEquals(Game.statsFor(dog, 1).hp + 3, Game.statsFor(dog, Game.cardLevel(s.caught.getValue("dog"))).hp)
    }

    @Test fun oldSavesKeepTheirLevels() {
        val s = PlayerState.fromJson("""{"caught":{"dog":{"count":9},"cat":{"count":1}}}""")
        assertEquals(4, Game.cardLevel(s.caught.getValue("dog")))
        assertEquals(1, Game.cardLevel(s.caught.getValue("cat")))
        s.caught.getValue("dog").count = 100 // more sightings no longer change it
        assertEquals(4, Game.cardLevel(s.caught.getValue("dog")))
    }
}
