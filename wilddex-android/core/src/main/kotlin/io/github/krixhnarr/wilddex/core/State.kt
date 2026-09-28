package io.github.krixhnarr.wilddex.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
    /** Card level, raised only by spending credits (older saves: derived from sightings once). */
    var level: Int? = null,
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
    /** Turn the camera on by itself when opening Scan (the player allowed it before). */
    var camera: Boolean = false,
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
        // levels used to come from re-scans; keep whatever level a card had reached
        for (rec in caught.values) if (rec.level == null) rec.level = Game.levelFor(rec.count)
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

/**
 * A backup file, in the same format as the web version's export:
 * `{ app: "wilddex", version: 1, exported, state, photos: { key: dataURL } }`.
 * A bare save (just the state object) is accepted too.
 */
object Backup {
    class Parsed(val state: PlayerState, val photos: Map<String, String>)

    fun parse(text: String): Parsed {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["app"]?.jsonPrimitive?.contentOrNull == "wilddex" && root["state"] != null) {
            val photos = root["photos"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            return Parsed(json.decodeFromJsonElement(PlayerState.serializer(), root.getValue("state")).also { it.migrate() }, photos)
        }
        require(root.containsKey("caught")) { "Not a WildDex backup file" }
        return Parsed(PlayerState.fromJson(text), emptyMap())
    }

    fun write(state: PlayerState, photos: Map<String, String>, exported: Long): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("app", "wilddex"); put("version", 1); put("exported", exported)
            put("state", json.encodeToJsonElement(PlayerState.serializer(), state))
            put("photos", buildJsonObject { photos.forEach { (k, v) -> put(k, v) } })
        },
    )
}
