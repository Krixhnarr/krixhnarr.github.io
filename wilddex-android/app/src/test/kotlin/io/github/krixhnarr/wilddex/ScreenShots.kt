package io.github.krixhnarr.wilddex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.krixhnarr.wilddex.core.CardRecord
import io.github.krixhnarr.wilddex.core.Clock
import io.github.krixhnarr.wilddex.core.Locker
import io.github.krixhnarr.wilddex.core.PlayerState
import io.github.krixhnarr.wilddex.core.Streak
import io.github.krixhnarr.wilddex.ui.WildDexRoot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/** Renders each screen with a sample save so the UI can be checked without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenShots {
    @get:Rule val compose = createComposeRule()

    @Before fun clock() {
        Clock.zone = ZoneOffset.UTC
        Clock.now = { Instant.parse("2026-09-28T10:30:00Z").toEpochMilli() }
    }

    private fun sample(): PlayerState {
        val t = Clock.now()
        val s = PlayerState(name = "Krish", opId = "A1B2C3", shards = 145, scans = 31)
        listOf("dog" to 9, "cat" to 4, "red-fox" to 2, "lion" to 1, "koala" to 3, "orca" to 1, "ladybug" to 5, "squirrel" to 2, "goldfish" to 1, "monarch" to 1)
            .forEachIndexed { i, (k, n) -> s.caught[k] = CardRecord(first = t - i * 86_400_000L, last = t - i * 3_600_000L, count = n, forms = mutableListOf(), holo = if (k == "red-fox") t else null) }
        s.intel["tiger"] = true
        s.streak = Streak(last = "2026-09-27", days = 3, best = 5)
        s.locker = Locker(frames = mutableListOf("standard", "neon", "topo"), titles = mutableListOf("rookie", "fox"), frame = "standard", crates = 1)
        s.migrate()
        s.xp = 260
        s.onboarded = true
        return s
    }

    private fun shot(name: String, night: Boolean = false, setup: GameModel.() -> Unit = {}) {
        val m = GameModel(null, sample())
        m.night = night
        m.setup()
        compose.setContent { WildDexRoot(m, animateSky = false) }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun home() = shot("home")
    @Test fun homeNight() = shot("home-night", night = true)
    @Test fun cards() = shot("cards") { tab = Tab.Cards }
    @Test fun ops() = shot("ops") { tab = Tab.Ops }
    @Test fun profile() = shot("profile") { tab = Tab.Id }
    @Test fun cardSheet() = shot("card-sheet") { open(Sheet.Card("red-fox")) }
    @Test fun cardStats() = shot("card-stats") { open(Sheet.Card("dog")) }
    @Test fun intro() = shot("intro") { open(Sheet.Intro) }
    @Test fun scan() = shot("scan") { tab = Tab.Scan }
    @Test fun revealNew() = shot("reveal-new") {
        pending = GameModel.PendingScan(io.github.krixhnarr.wilddex.core.Game.Grade("A", 0.75, 8, 10, "Good"), null)
        register(io.github.krixhnarr.wilddex.core.Dex.byKey.getValue("tiger"), io.github.krixhnarr.wilddex.core.Dex.byKey.getValue("tiger").c[0])
    }
    @Test fun revealRescan() = shot("reveal-rescan") {
        pending = GameModel.PendingScan(io.github.krixhnarr.wilddex.core.Game.Grade("S", 0.9, 15, 20, "Perfect"), null)
        register(io.github.krixhnarr.wilddex.core.Dex.byKey.getValue("koala"), io.github.krixhnarr.wilddex.core.Dex.byKey.getValue("koala").c[0])
    }
    @Test fun arena() = shot("arena") { tab = Tab.Arena }
    @Test fun fight() = shot("fight") { open(Sheet.Fight("rival")) }
    @Test fun squad() = shot("squad") { open(Sheet.Squad(0)) }
    @Test fun crate() = shot("crate") { open(Sheet.Crate(true)) }
    @Test fun levelUp() = shot("levelup") { queueLevelUps(listOf(io.github.krixhnarr.wilddex.core.LevelUp(5, 50))) }
}
