package io.github.krixhnarr.wilddex.core

import kotlinx.serialization.Serializable

// The player's saved progress. Field names match the web app's save format so
// a web backup or cloud save can be loaded as-is.

@Serializable
data class CardRecord(
    var first: Long = 0,
    var last: Long = 0,
    var count: Int = 0,
    var forms: MutableList<Int> = mutableListOf(),
    var photo: Boolean = false,
    var holo: Long? = null,
    var loc: List<Double>? = null,
    var lastLoc: List<Double>? = null,
)

@Serializable
data class Daily(
    var date: String = "",
    var progress: MutableMap<String, Int> = mutableMapOf(),
    var claimed: MutableMap<String, Boolean> = mutableMapOf(),
    var bonus: Boolean = false,
)

@Serializable
data class Streak(var last: String? = null, var days: Int = 0, var best: Int = 0)

@Serializable
data class WeekEvent(var key: String = "", var progress: Int = 0, var claimed: Boolean = false)

@Serializable
data class Rival(
    var date: String = "",
    var name: String = "",
    var keys: List<String> = emptyList(),
    var lvs: List<Int> = emptyList(),
    var won: Boolean = false,
    var tries: Int = 0,
)

@Serializable
data class Sims(var date: String = "", var paid: Int = 0)

@Serializable
data class BattleState(
    var squad: MutableList<String> = mutableListOf(),
    var rival: Rival? = null,
    var sims: Sims? = null,
    var wins: Int = 0,
    var losses: Int = 0,
    var rivals: Int = 0,
)

@Serializable
data class Locker(
    var frames: MutableList<String> = mutableListOf("standard"),
    var titles: MutableList<String> = mutableListOf("rookie"),
    var frame: String = "standard",
    var title: String = "rookie",
    var crates: Int = 0,
    var opened: Int = 0,
)

@Serializable
data class Settings(
    var voice: Boolean = true,
    var sound: Boolean = true,
    var music: Boolean = true,
    var musicVol: Double = 0.6,
    var tilt: Boolean = true,
    var location: Boolean = false,
    var haptics: Boolean = true,
    var theme: String = "auto",
)

@Serializable
data class PlayerState(
    var caught: MutableMap<String, CardRecord> = mutableMapOf(),
    var scans: Int = 0,
    var shards: Int = 0,
    var xp: Int? = null,
    var intel: MutableMap<String, Boolean> = mutableMapOf(),
    var daily: Daily? = null,
    var streak: Streak? = null,
    var event: WeekEvent? = null,
    var eventsWon: Int = 0,
    var battle: BattleState? = null,
    var locker: Locker? = null,
    var name: String = "",
    var opId: String? = null,
    var onboarded: Boolean = false,
    var bestGrade: String? = null,
    var compared: Int = 0,
    var savedAt: Long = 0,
    var settings: Settings = Settings(),
) {
    /** Cards the player owns that exist in this version of the dex. */
    fun ownedKeys(): List<String> = caught.keys.filter { Dex.byKey.containsKey(it) }

    /** Older saves had no XP total: start them at what their cards were worth. */
    fun migrate() {
        if (xp == null) xp = ownedKeys().sumOf { Dex.rarity.getValue(Dex.byKey.getValue(it).r).value }
    }

    fun xpNow(): Int = xp ?: 0
    fun locker(): Locker = locker ?: Locker().also { locker = it }
    fun battle(): BattleState = battle ?: BattleState().also { battle = it }

    fun copyDeep(): PlayerState = json.decodeFromString(serializer(), json.encodeToString(serializer(), this))
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): PlayerState = json.decodeFromString(serializer(), text).also { it.migrate() }
    }
}
