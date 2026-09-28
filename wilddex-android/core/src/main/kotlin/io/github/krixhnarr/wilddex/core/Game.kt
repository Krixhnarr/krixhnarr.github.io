package io.github.krixhnarr.wilddex.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Collectible-game rules: card levels, battle stats, credits, operator XP,
// sync grades, holo odds, daily missions, streaks, weekly events and badges.
// Stats are game values (not biology) derived deterministically from rarity +
// affinity so every player sees the same card. Ported from the web app's game.js.

/** Wall clock and time zone, swappable in tests. */
object Clock {
    var now: () -> Long = { System.currentTimeMillis() }
    var zone: ZoneId = ZoneId.systemDefault()
    fun date(ms: Long = now()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun hour(): Int = Instant.ofEpochMilli(now()).atZone(zone).hour
}

object Game {
    const val SHARD = "◆"
    const val DECRYPT_COST = 40
    const val MAX_LEVEL = 10

    // ---- card level: sightings 1,2,4,8,16... -> Lv 1,2,3,4,5 ... max 10
    fun levelFor(count: Int): Int = min(MAX_LEVEL, 1 + (31 - Integer.numberOfLeadingZeros(max(1, count))))
    fun nextLevelAt(level: Int): Int? = if (level >= MAX_LEVEL) null else 1 shl level

    // ---- stats
    private val BASE = mapOf(1 to 34, 2 to 44, 3 to 54, 4 to 68)
    private val MOD: Map<String, Map<String, Int>> = mapOf(
        "terra" to mapOf("hp" to 10, "def" to 10),
        "aqua" to mapOf("hp" to 10, "spd" to 4),
        "aero" to mapOf("spd" to 16),
        "feral" to mapOf("atk" to 16),
        "toxin" to mapOf("atk" to 10, "def" to 4),
        "swarm" to mapOf("spd" to 10, "hp" to -8),
        "frost" to mapOf("def" to 10, "hp" to 4),
        "solar" to mapOf("atk" to 6, "hp" to 6),
        "umbra" to mapOf("spd" to 6, "atk" to 6),
        "psi" to mapOf("def" to 6, "spd" to 6),
        "verdant" to mapOf("hp" to 12),
        "volt" to mapOf("spd" to 16, "atk" to 4),
        "ancient" to mapOf("def" to 16, "hp" to 8),
    )

    /** FNV-1a 32-bit hash, identical to the web version (unsigned result). */
    fun hash(str: String): Long {
        var h = 2166136261L.toInt()
        for (ch in str) { h = h xor ch.code; h *= 16777619 }
        return h.toLong() and 0xFFFFFFFFL
    }

    val STAT_KEYS = listOf("hp", "atk", "def", "spd")

    data class Stats(val hp: Int, val atk: Int, val def: Int, val spd: Int) {
        val pwr: Int get() = hp + atk + def + spd
        operator fun get(key: String): Int = when (key) { "hp" -> hp; "atk" -> atk; "def" -> def; else -> spd }
    }

    fun statsFor(e: Entry, level: Int = 1): Stats {
        val h = hash(e.k)
        val v = STAT_KEYS.mapIndexed { i, s ->
            var x = BASE.getValue(e.r) + (((h ushr (i * 8)) and 255) % 17).toInt() - 8
            Dex.typesOf(e).forEachIndexed { j, t -> x += jsRound((MOD.getValue(t)[s] ?: 0) * (if (j == 0) 1.0 else 0.6)) }
            x += (level - 1) * 3
            max(8, min(99, x))
        }
        return Stats(v[0], v[1], v[2], v[3])
    }

    // ---- operator XP: level L starts at 20·(L-1)² XP
    fun levelForXP(xp: Int): Int = 1 + floor(sqrt(max(0, xp) / 20.0)).toInt()
    fun xpForLevel(level: Int): Int = 20 * (level - 1) * (level - 1)
    fun levelUpCredits(level: Int): Int = 10 * level
    const val SIGHTING_XP = 5
    const val MISSION_XP = 10
    const val EVENT_XP = 40

    // ---- sync grade: how cleanly the lock-on sweep went (confidence, depth, motion)
    data class Grade(val grade: String, val q: Double, val credits: Int, val xp: Int, val name: String)
    private val GRADES = mapOf(
        "S" to Triple(20, 15, "Perfect sync"),
        "A" to Triple(12, 10, "Strong sync"),
        "B" to Triple(6, 5, "Good sync"),
        "C" to Triple(0, 0, "Weak sync"),
    )

    fun syncGrade(conf: Double, depth: SweepResult?): Grade {
        val c = min(1.0, conf / 0.8)
        val d = if (depth != null && depth.tracks > 0) min(1.0, depth.parallax.toDouble() / depth.tracks / 0.25) else 0.4
        val range = depth?.range ?: 0.0
        val m = when {
            depth == null -> 0.5
            range < 12 -> range / 12
            range > 90 -> max(0.4, 1 - (range - 90) / 90)
            else -> 1.0
        }
        val q = 0.45 * c + 0.35 * d + 0.2 * m
        val g = when { q >= 0.85 -> "S"; q >= 0.7 -> "A"; q >= 0.5 -> "B"; else -> "C" }
        val (credits, xp, name) = GRADES.getValue(g)
        return Grade(g, (q * 100).roundToInt() / 100.0, credits, xp, name)
    }

    // ---- holo (shiny) variants: rare foil pulls, better odds on a perfect sync
    const val HOLO_ODDS = 40
    fun holoChance(grade: String?): Double = (if (grade == "S") 2.0 else 1.0) / HOLO_ODDS
    const val HOLO_DUPE_CREDITS = 30

    /** Card stars: half a star per level, five stars at Lv 10. Each value is 0, 50 or 100 (% filled). */
    fun stars(level: Int): List<Int> = (0 until 5).map { i -> max(0, min(2, level - i * 2)) * 50 }

    // ---- dates
    fun today(date: LocalDate = Clock.date()): String = date.toString()
    private fun yesterday(): String = today(Clock.date().minusDays(1))

    // ---- streak: returns bonus credits when today's first sighting extends it
    data class StreakTouch(val bonus: Int, val days: Int, val extended: Boolean)

    fun touchStreak(state: PlayerState): StreakTouch {
        val s = state.streak ?: Streak().also { state.streak = it }
        val t = today()
        if (s.last == t) return StreakTouch(0, s.days, false)
        s.days = if (s.last == yesterday()) s.days + 1 else 1
        s.last = t
        s.best = max(s.best, s.days)
        return StreakTouch(min(50, 5 * s.days), s.days, true)
    }

    fun liveStreak(state: PlayerState): Int {
        val s = state.streak ?: return 0
        return if (s.last == today() || s.last == yesterday()) s.days else 0
    }

    // ---- daily missions (same three for everyone on a given day)
    data class Mission(val id: String, val text: String, val goal: Int, val reward: Int, val type: String? = null, val set: String? = null)

    private val EASY_TYPES = listOf("terra", "aero", "swarm", "aqua", "verdant", "feral")
    private val EASY_SETS = listOf("home", "farm", "backyard", "bugs")

    fun makeMissions(dateStr: String): List<Mission> {
        val h = hash(dateStr)
        val type = EASY_TYPES[(h % EASY_TYPES.size).toInt()]
        val set = Dex.sets.first { it.id == EASY_SETS[((h ushr 5) % EASY_SETS.size).toInt()] }
        val pool = listOf(
            Mission("log3", "Log 3 sightings", 3, 20),
            Mission("new1", "Register a new species", 1, 40),
            Mission("type:$type", "Scan a ${Dex.types.getValue(type).name}-type animal", 1, 25, type = type),
            Mission("rare", "Scan an Uncommon or rarer animal", 1, 30),
            Mission("repeat", "Re-scan a card you already own", 1, 15),
            Mission("set:${set.id}", "Scan something from ${set.name}", 1, 20, set = set.id),
        )
        // Always one "new species" order, plus two others picked by the date.
        val others = pool.filter { it.id != "new1" }
        val a = others[((h ushr 9) % others.size).toInt()]
        val rest = others.filter { it != a }
        val b = rest[((h ushr 13) % rest.size).toInt()]
        return listOf(pool[1], a, b)
    }

    const val ALL_CLEAR_BONUS = 30

    fun daily(state: PlayerState): Daily {
        val t = today()
        val d = state.daily
        if (d == null || d.date != t) return Daily(date = t).also { state.daily = it }
        return d
    }

    fun missions(): List<Mission> = makeMissions(today())

    /** Called after each successful registration; returns missions that just completed. */
    fun progressMissions(state: PlayerState, entry: Entry, isNew: Boolean): List<Mission> {
        val d = daily(state)
        val done = mutableListOf<Mission>()
        for (m in missions()) {
            val hit = when {
                m.id == "log3" -> true
                m.id == "new1" -> isNew
                m.id == "rare" -> entry.r >= 2
                m.id == "repeat" -> !isNew
                m.type != null -> Dex.typesOf(entry).contains(m.type)
                m.set != null -> entry.set == m.set
                else -> false
            }
            if (!hit) continue
            val before = d.progress[m.id] ?: 0
            if (before >= m.goal) continue
            d.progress[m.id] = before + 1
            if (before + 1 >= m.goal) done += m
        }
        return done
    }

    fun unclaimedMissions(state: PlayerState): Int {
        val d = daily(state)
        return missions().count { (d.progress[it.id] ?: 0) >= it.goal && d.claimed[it.id] != true }
    }

    /** Claims a finished mission; returns the credits gained (0 if not claimable). */
    fun claimMission(state: PlayerState, id: String): Int {
        val d = daily(state)
        val list = missions()
        val m = list.firstOrNull { it.id == id } ?: return 0
        if (d.claimed[id] == true || (d.progress[id] ?: 0) < m.goal) return 0
        d.claimed[id] = true
        var gained = m.reward
        if (!d.bonus && list.all { d.claimed[it.id] == true }) {
            d.bonus = true
            gained += ALL_CLEAR_BONUS
        }
        state.shards += gained
        return gained
    }

    // ---- weekly events: a themed challenge that rotates every ISO week
    data class EventDef(val id: String, val name: String, val text: String, val type: String? = null, val set: String? = null, val goal: Int = 5)
    data class WeeklyEvent(val def: EventDef, val key: String, val daysLeft: Int, val progress: Int, val claimed: Boolean) {
        val goal get() = def.goal
        val reward get() = EVENT_REWARD
    }

    private val EVENTS = listOf(
        EventDef("monsoon", "Monsoon Week", "Log 5 Aqua-type sightings", type = "aqua"),
        EventDef("bugs", "Bug Week", "Log 5 Swarm-type sightings", type = "swarm"),
        EventDef("sky", "Sky Watch", "Log 5 Aero-type sightings", type = "aero"),
        EventDef("green", "Green Week", "Log 4 Verdant-type sightings", type = "verdant", goal = 4),
        EventDef("night", "Night Watch", "Log 3 Umbra-type sightings", type = "umbra", goal = 3),
        EventDef("hunt", "Predator Week", "Log 4 Feral-type sightings", type = "feral", goal = 4),
        EventDef("farm", "Farm Fair", "Log 5 sightings from Farm & Country", set = "farm"),
        EventDef("yard", "Backyard Blitz", "Log 5 sightings from Backyard & Park", set = "backyard"),
        EventDef("pets", "Pet Parade", "Log 5 sightings from Home & Pets", set = "home"),
    )
    const val EVENT_REWARD = 80

    fun weeklyEvent(state: PlayerState): WeeklyEvent {
        val d = Clock.date()
        val year = d.get(IsoFields.WEEK_BASED_YEAR)
        val week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val daysLeft = 8 - d.dayOfWeek.value // Monday=1 .. Sunday=7
        val key = "$year-W$week"
        val def = EVENTS[((year * 53 + week) % EVENTS.size)]
        val ev = state.event
        val cur = if (ev == null || ev.key != key) WeekEvent(key).also { state.event = it } else ev
        return WeeklyEvent(def, key, daysLeft, cur.progress, cur.claimed)
    }

    /** Returns true when this sighting completes the week's event. */
    fun progressEvent(state: PlayerState, entry: Entry): Boolean {
        val ev = weeklyEvent(state)
        if (ev.claimed || ev.progress >= ev.goal) return false
        val hit = if (ev.def.type != null) Dex.typesOf(entry).contains(ev.def.type) else entry.set == ev.def.set
        if (!hit) return false
        val e = state.event!!
        e.progress++
        return e.progress >= ev.goal
    }

    fun claimEvent(state: PlayerState): Int {
        val ev = weeklyEvent(state)
        if (ev.claimed || ev.progress < ev.goal) return 0
        state.event!!.claimed = true
        state.eventsWon++
        state.shards += EVENT_REWARD
        return EVENT_REWARD
    }

    // ---- achievements (computed, never stored)
    data class Badge(val id: String, val name: String, val desc: String, val done: Boolean)

    fun achievements(state: PlayerState): Pair<List<Badge>, Map<String, Int>> {
        val caught = state.ownedKeys()
        val byType = mutableMapOf<String, Int>()
        for (k in caught) for (t in Dex.affinity.getValue(k)) byType[t] = (byType[t] ?: 0) + 1
        val maxLevel = caught.maxOfOrNull { levelFor(state.caught.getValue(it).count) } ?: 0
        val list = listOf(
            Badge("first", "First Contact", "Register your first animal", caught.isNotEmpty()),
            Badge("ten", "Field Agent", "Register 10 species", caught.size >= 10),
            Badge("fifty", "Archivist", "Register 50 species", caught.size >= 50),
            Badge("rare", "Rare Signal", "Register a Rare animal", caught.any { Dex.byKey.getValue(it).r >= 3 }),
            Badge("legend", "Relic Hunter", "Register a Legendary", caught.any { Dex.byKey.getValue(it).r == 4 }),
            Badge("level5", "Bonded", "Raise a card to Lv 5", maxLevel >= 5),
            Badge("types", "Spectrum", "Own every affinity type", Dex.typeIds.all { (byType[it] ?: 0) > 0 }),
            Badge("streak7", "Dedicated", "Reach a 7-day streak", (state.streak?.best ?: 0) >= 7),
            Badge("sector", "Sector Secured", "Complete any sector", Dex.sets.any { s -> s.keys.all { state.caught.containsKey(it) } }),
            Badge("event", "Event Champion", "Complete a weekly event", state.eventsWon >= 1),
            Badge("social", "Field Partner", "Compare with a friend", state.compared >= 1),
            Badge("mapper", "Cartographer", "Tag 10 capture locations", caught.count { state.caught.getValue(it).loc != null } >= 10),
            Badge("holo", "Holo Hunter", "Pull a holo card", caught.any { state.caught.getValue(it).holo != null }),
            Badge("victory", "First Victory", "Win a battle", (state.battle?.wins ?: 0) >= 1),
            Badge("rivals", "Rival Slayer", "Defeat 5 daily rivals", (state.battle?.rivals ?: 0) >= 5),
            Badge("perfect", "Perfect Sync", "Get an S grade on a capture", state.bestGrade == "S"),
            Badge("stylist", "Stylist", "Unlock a new card frame", (state.locker?.frames?.size ?: 0) > 1),
        )
        return list to byType
    }
}

/** JavaScript Math.round: halves round up (towards +infinity). */
internal fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

