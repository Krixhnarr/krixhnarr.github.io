package io.github.krixhnarr.wilddex.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Card battles: 3 v 3, turn based. Cards fight with the stats and affinity
// types they already have. Pure rules — the arena screens live in the app.
// Battles never give or take cards. Ported from the web app's battle.js.

object Battle {
    /** Each type is strong against two others (×1.5); the reverse is resisted (×0.67). */
    val STRONG: Map<String, List<String>> = mapOf(
        "terra" to listOf("volt", "toxin"),
        "aqua" to listOf("solar", "terra"),
        "aero" to listOf("swarm", "verdant"),
        "feral" to listOf("aero", "swarm"),
        "toxin" to listOf("verdant", "aqua"),
        "swarm" to listOf("verdant", "psi"),
        "frost" to listOf("aero", "ancient"),
        "solar" to listOf("frost", "swarm"),
        "umbra" to listOf("psi", "feral"),
        "psi" to listOf("feral", "toxin"),
        "verdant" to listOf("aqua", "terra"),
        "volt" to listOf("aqua", "aero"),
        "ancient" to listOf("umbra", "volt"),
    )

    fun mult(type: String, defTypes: List<String>): Double {
        var m = 1.0
        for (d in defTypes) {
            if (STRONG.getValue(type).contains(d)) m *= 1.5
            else if (STRONG.getValue(d).contains(type)) m *= 0.67
        }
        return max(0.5, min(2.25, m))
    }

    /** Types that beat the given type. */
    fun countersOf(type: String): List<String> = Dex.typeIds.filter { STRONG.getValue(it).contains(type) }

    const val CHARGE_MAX = 3
    const val STRIKE_POWER = 42
    const val OVERDRIVE_POWER = 78
    const val SQUAD_SIZE = 3

    class Fighter(val entry: Entry, val lv: Int, val holo: Boolean) {
        val types: List<String> = Dex.typesOf(entry)
        private val st = Game.statsFor(entry, lv)
        private val boost = if (holo) 1.1 else 1.0 // holo cards hit a little harder
        val maxHp: Int = ((st.hp * 2 + 30) * boost).roundToInt()
        var hp: Int = maxHp
        val atk: Int = (st.atk * boost).roundToInt()
        val def: Int = (st.def * boost).roundToInt()
        val spd: Int = st.spd
        var charge = 0
        var guard = false
    }

    class Side(val team: List<Fighter>, var i: Int = 0) {
        val active: Fighter get() = team[i]
        fun alive() = team.any { it.hp > 0 }
    }

    class Fight(val you: Side, val foe: Side, val kind: String) {
        var turn = 0
        var over = false
        var winner: String? = null
        fun side(s: String) = if (s == "you") you else foe
    }

    sealed class Event {
        data class Guard(val side: String, val name: String) : Event()
        data class Attack(
            val side: String, val name: String, val move: String, val dmg: Int, val mult: Double, val crit: Boolean,
            val type: String, val target: String, val hp: Int, val maxHp: Int,
        ) : Event()
        data class Faint(val side: String, val name: String) : Event()
        data class Enter(val side: String, val name: String, val index: Int) : Event()
        data class End(val winner: String) : Event()
    }

    private fun bestType(att: Fighter, def: Fighter): String = att.types.sortedByDescending { mult(it, def.types) }.first()

    private data class Hit(val dmg: Int, val mult: Double, val crit: Boolean, val type: String)

    private fun hit(att: Fighter, def: Fighter, move: String, rnd: () -> Double): Hit {
        val type = if (move == "overdrive") bestType(att, def) else move.substringAfter(':')
        val m = mult(type, def.types)
        val crit = rnd() < 1.0 / 16
        val power = if (move == "overdrive") OVERDRIVE_POWER else STRIKE_POWER
        var dmg = (att.atk * power).toDouble() / (def.def + 30) * m * (0.88 + rnd() * 0.12) * (if (crit) 1.5 else 1.0)
        if (def.guard) dmg *= 0.4
        return Hit(max(1, dmg.roundToInt()), m, crit, type)
    }

    /** The rival's choice: overdrive when charged, otherwise usually its best matchup. */
    fun aiMove(b: Fight, rnd: () -> Double): String {
        val me = b.foe.active
        val them = b.you.active
        if (me.charge >= CHARGE_MAX) return "overdrive"
        if (me.hp < me.maxHp * 0.3 && rnd() < 0.2) return "guard"
        val opts = me.types.sortedByDescending { mult(it, them.types) }
        return "strike:" + if (rnd() < 0.8) opts.first() else opts.last()
    }

    /** Plays one turn and returns the events in order, for the UI to animate. */
    fun playTurn(b: Fight, youMove: String, rnd: () -> Double = { Math.random() }): List<Event> {
        if (b.over) return emptyList()
        b.turn++
        val events = mutableListOf<Event>()
        val foeMove = aiMove(b, rnd)
        fun speed(side: String, move: String) = if (move == "guard") 1000.0 else b.side(side).active.spd + rnd() * 0.5
        val acts = listOf("you" to youMove, "foe" to foeMove)
            .map { Triple(it.first, it.second, speed(it.first, it.second)) }
            .sortedByDescending { it.third }
        val actors = acts.map { b.side(it.first).active }

        for ((n, act) in acts.withIndex()) {
            val (side, move) = act
            val other = if (side == "you") "foe" else "you"
            val me = actors[n]
            if (me.hp <= 0 || b.side(side).active !== me) continue // fainted before acting
            val target = b.side(other).active
            if (move == "guard") {
                me.guard = true
                me.charge = min(CHARGE_MAX, me.charge + 1)
                events += Event.Guard(side, me.entry.n)
                continue
            }
            if (move == "overdrive" && me.charge < CHARGE_MAX) continue
            val h = hit(me, target, move, rnd)
            me.charge = if (move == "overdrive") 0 else min(CHARGE_MAX, me.charge + 1)
            if (!target.guard) target.charge = min(CHARGE_MAX, target.charge + 1)
            target.hp = max(0, target.hp - h.dmg)
            events += Event.Attack(side, me.entry.n, move, h.dmg, h.mult, h.crit, h.type, other, target.hp, target.maxHp)
            if (target.hp <= 0) {
                events += Event.Faint(other, target.entry.n)
                if (!b.side(other).alive()) {
                    b.over = true
                    b.winner = side
                    events += Event.End(side)
                    break
                }
            }
        }
        for (s in listOf("you", "foe")) {
            val sd = b.side(s)
            sd.active.guard = false
            if (!b.over && sd.active.hp <= 0) {
                sd.i = sd.team.indexOfFirst { it.hp > 0 }
                events += Event.Enter(s, sd.active.entry.n, sd.i)
            }
        }
        return events
    }

    // ---------------------------------------------------------------- squads & opponents
    private fun lvOf(state: PlayerState, k: String) = Game.levelFor(state.caught.getValue(k).count)
    fun pwrOf(state: PlayerState, k: String): Double =
        Game.statsFor(Dex.byKey.getValue(k), lvOf(state, k)).pwr * (if (state.caught.getValue(k).holo != null) 1.1 else 1.0)

    /** The saved squad (cards still owned), topped up with the strongest cards. */
    fun squad(state: PlayerState): List<String> {
        val b = state.battle()
        val mine = state.ownedKeys()
        val keys = b.squad.filter { it in mine }.take(SQUAD_SIZE).toMutableList()
        val rest = mine.filter { it !in keys }.sortedByDescending { pwrOf(state, it) }.toMutableList()
        while (keys.size < min(SQUAD_SIZE, mine.size)) keys += rest.removeAt(0)
        return keys
    }

    fun bestSquad(state: PlayerState): List<String> = state.ownedKeys().sortedByDescending { pwrOf(state, it) }.take(SQUAD_SIZE)
    fun squadTeam(state: PlayerState, keys: List<String> = squad(state)): List<Fighter> =
        keys.map { Fighter(Dex.byKey.getValue(it), lvOf(state, it), state.caught.getValue(it).holo != null) }

    private fun squadLevel(state: PlayerState): Int {
        val keys = squad(state)
        if (keys.isEmpty()) return 1
        return max(1, jsRound(keys.sumOf { lvOf(state, it) }.toDouble() / keys.size))
    }

    private fun rarityCap(n: Int) = when { n < 6 -> 1; n < 15 -> 2; n < 40 -> 3; else -> 4 }

    /** Picks `count` distinct cards, preferring different primary types. */
    private fun pickTeam(count: Int, cap: Int, rnd: () -> Double): List<Entry> {
        val pool = Dex.entries.filter { it.r <= cap }
        val out = mutableListOf<Entry>()
        var guard = 0
        while (out.size < count && guard++ < 200) {
            val e = pool[floor(rnd() * pool.size).toInt()]
            if (e in out) continue
            if (guard < 100 && out.any { Dex.typesOf(it)[0] == Dex.typesOf(e)[0] }) continue
            out += e
        }
        return out
    }

    /** xorshift32, identical to the web version so rivals match across platforms. */
    fun seeded(seed: Long): () -> Double {
        var x = (seed and 0xFFFFFFFFL).let { if (it == 0L) 1L else it }
        return {
            x = x xor ((x shl 13) and 0xFFFFFFFFL)
            x = x xor (x ushr 17)
            x = x xor ((x shl 5) and 0xFFFFFFFFL)
            x.toDouble() / 4294967296.0
        }
    }

    private val CALLSIGNS = listOf("VEX", "NOVA", "KODA", "RAZE", "ONYX", "LYRA", "JUNO", "HEX", "MIRA", "ZED", "ASH", "RUNE", "SABLE", "ORIN")
    data class Reward(val credits: Int, val xp: Int)
    val RIVAL_REWARD = Reward(60, 50)
    val SIM_REWARD = Reward(12, 15)
    const val SIM_PAID = 5
    const val LOSS_XP = 5

    /** Today's rival: fixed for the day once generated. */
    fun dailyRival(state: PlayerState): Rival {
        val b = state.battle()
        val t = Game.today()
        val cur = b.rival
        if (cur != null && cur.date == t) return cur
        val rnd = seeded(Game.hash("rival:$t:${state.opId ?: ""}"))
        val lv = squadLevel(state)
        val team = pickTeam(SQUAD_SIZE, rarityCap(state.ownedKeys().size), rnd)
        val name = "${CALLSIGNS[floor(rnd() * CALLSIGNS.size).toInt()]}-${10 + floor(rnd() * 90).toInt()}"
        return Rival(t, name, team.map { it.k }, team.indices.map { min(10, lv + if (it == 2) 1 else 0) }).also { b.rival = it }
    }

    fun rivalTeam(r: Rival): List<Fighter> = r.keys.mapIndexed { i, k -> Fighter(Dex.byKey.getValue(k), r.lvs[i], false) }

    fun simsToday(state: PlayerState): Sims {
        val b = state.battle()
        val cur = b.sims
        if (cur != null && cur.date == Game.today()) return cur
        return Sims(Game.today(), 0).also { b.sims = it }
    }

    fun wildTeam(state: PlayerState, size: Int, rnd: () -> Double = { Math.random() }): List<Fighter> {
        val lv = squadLevel(state)
        return pickTeam(size, rarityCap(state.ownedKeys().size), rnd).map {
            Fighter(it, max(1, min(10, lv + floor(rnd() * 3).toInt() - 1)), false)
        }
    }

    /** Rewards for a finished battle; updates battle stats and returns what was earned. */
    fun settle(state: PlayerState, kind: String, won: Boolean): Reward {
        val b = state.battle()
        if (won) b.wins++ else b.losses++
        if (kind == "rival") {
            val r = dailyRival(state)
            r.tries++
            if (won && !r.won) { r.won = true; b.rivals++; return RIVAL_REWARD }
            return Reward(0, if (won) 10 else LOSS_XP)
        }
        val s = simsToday(state)
        if (won && s.paid < SIM_PAID) { s.paid++; return SIM_REWARD }
        return Reward(0, if (won) 5 else LOSS_XP)
    }
}
