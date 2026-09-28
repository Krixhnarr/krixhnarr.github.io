package io.github.krixhnarr.wilddex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web-app backups (and their quirks, like nulls and extra keys) must load in the Android app. */
class BackupTest {
    private val web = """
        {"app":"wilddex","version":1,"exported":1790000000000,
         "state":{"caught":{"dog":{"first":1,"last":2,"count":5,"forms":[208],"photo":true,"holo":null,"loc":[9.93,76.26]},
                            "cat":{"first":3,"last":4,"count":1,"forms":[281],"photo":false}},
                  "scans":12,"shards":90,"xp":null,"intel":{"tiger":true},"daily":null,"streak":{"last":"2026-09-27","days":2,"best":4},
                  "battle":{"squad":["dog"],"rival":null,"wins":1,"losses":0,"rivals":0},
                  "name":"Krish","settings":{"voice":false,"sound":true,"music":true,"musicVol":0.4,"tilt":true,"location":false,"haptics":true,"theme":"auto"},
                  "someFutureField":{"x":1}},
         "photos":{"dog":"data:image/jpeg;base64,AAAA"}}
    """.trimIndent()

    @Test fun loadsWebBackup() {
        val b = Backup.parse(web)
        val s = b.state
        assertEquals(5, s.caught.getValue("dog").count)
        assertNull(s.caught.getValue("dog").holo)
        assertEquals(listOf(9.93, 76.26), s.caught.getValue("dog").loc)
        assertEquals(90, s.shards)
        assertEquals("Krish", s.name)
        assertEquals(0.4, s.settings.musicVol, 1e-9)
        assertTrue(s.xpNow() > 0) // missing XP is rebuilt from the cards
        assertEquals(setOf("dog"), b.photos.keys)
    }

    @Test fun roundTrip() {
        val s = Backup.parse(web).state
        val again = Backup.parse(Backup.write(s, mapOf("dog" to "data:image/jpeg;base64,AAAA"), 5))
        assertEquals(s.toJson(), again.state.toJson())
        assertEquals(1, again.photos.size)
    }

    @Test fun loadsBareSave() {
        val s = Backup.parse("""{"caught":{"cat":{"count":2}},"shards":7}""").state
        assertEquals(2, s.caught.getValue("cat").count)
        assertEquals(7, s.shards)
    }
}
