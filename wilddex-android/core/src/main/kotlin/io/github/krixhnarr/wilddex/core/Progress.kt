package io.github.krixhnarr.wilddex.core

import kotlin.math.max

// Everything that changes the player's progress: captures, XP and level-ups,
// claiming orders and events, decrypting intel and battle rewards. The UI
// calls these and just displays the results.

data class LevelUp(val level: Int, val credits: Int)

/** One reward line shown after a capture ("+25◆ new card", "LV 2 → 3 · +10◆"…). */
data class Gain(val text: String, val highlight: Boolean = false, val holo: Boolean = false)

data class CaptureResult(
    val entry: Entry,
    val form: Int,
    val isNew: Boolean,
    val count: Int,
    val lvBefore: Int,
    val lvAfter: Int,
    val newForm: Boolean,
    val credits: Int,
    val xpBefore: Int,
    val xpAfter: Int,
    val xpGain: Int,
    val grade: Game.Grade?,
    val holoNew: Boolean,
    val gains: List<Gain>,
    val missionsDone: List<Game.Mission>,
    val eventDone: Boolean,
    val setDone: Boolean,
    val levelUps: List<LevelUp>,
)

object Progress {
    /** Grants XP; each level crossed pays credits + a free supply crate. */
    fun earnXP(state: PlayerState, amount: Int): List<LevelUp> {
        if (amount <= 0) return emptyList()
        val before = Game.levelForXP(state.xpNow())
        state.xp = state.xpNow() + amount
        val after = Game.levelForXP(state.xpNow())
        return (before + 1..after).map { lv ->
            val credits = Game.levelUpCredits(lv)
            state.shards += credits
            state.locker().crates++
            LevelUp(lv, credits)
        }
    }

    fun opLevel(state: PlayerState): Int = Game.levelForXP(state.xpNow())

    private fun title(s: String) = Regex("(^|[\\s-])([a-z])").replace(s) { it.groupValues[1] + it.groupValues[2].uppercase() }

    /**
     * Registers a verified capture of [e] (with ImageNet [form]) and hands out
     * every reward. [rnd] decides the holo roll.
     */
    fun register(state: PlayerState, e: Entry, form: Int, grade: Game.Grade?, rnd: () -> Double = { Math.random() }): CaptureResult {
        val now = Clock.now()
        val isNew = !state.caught.containsKey(e.k)
        val rec = state.caught[e.k] ?: CardRecord(first = now)
        val lvBefore = if (isNew) 0 else Game.levelFor(rec.count)
        val newForm = e.c.size > 1 && !rec.forms.contains(form)
        if (!rec.forms.contains(form)) rec.forms += form
        rec.count++
        rec.last = now
        if (isNew) rec.photo = true
        state.caught[e.k] = rec
        state.intel.remove(e.k)
        val lvAfter = Game.levelFor(rec.count)

        val gains = mutableListOf<Gain>()
        var credits = 0
        val value = Dex.rarity.getValue(e.r).value
        if (isNew) { credits += value; gains += Gain("+$value◆ new card", true) } else { credits += 3; gains += Gain("+3◆ sighting") }
        if (!isNew && lvAfter > lvBefore) { credits += 10; gains += Gain("LV $lvBefore → $lvAfter · +10◆", true) }
        if (newForm && !isNew) gains += Gain("new form: ${title(Dex.labels[form])}", true)
        val streak = Game.touchStreak(state)
        if (streak.bonus > 0) { credits += streak.bonus; gains += Gain("day ${streak.days} streak +${streak.bonus}◆") }
        val missionsDone = Game.progressMissions(state, e, isNew)
        missionsDone.forEach { gains += Gain("mission complete: ${it.text}", true) }
        val eventDone = Game.progressEvent(state, e)
        if (eventDone) gains += Gain("event complete: ${Game.weeklyEvent(state).def.name}", true)
        val setDone = isNew && Dex.setOf(e).keys.all { state.caught.containsKey(it) }
        if (setDone) { credits += 50; gains += Gain("sector complete +50◆", true) }
        if (grade != null) {
            credits += grade.credits
            if (grade.credits > 0) gains += Gain("sync ${grade.grade} +${grade.credits}◆", grade.grade == "S")
            val order = "SABC"
            val best = state.bestGrade
            if (best == null || order.indexOf(grade.grade) < order.indexOf(best)) state.bestGrade = grade.grade
        }
        // Holo roll: a rare foil variant of this card.
        var holoNew = false
        if (rnd() < Game.holoChance(grade?.grade)) {
            if (rec.holo == null) { rec.holo = now; holoNew = true; gains.add(0, Gain("✦ holo variant", true, holo = true)) } else {
                credits += Game.HOLO_DUPE_CREDITS; gains += Gain("holo echo +${Game.HOLO_DUPE_CREDITS}◆", true)
            }
        }
        state.shards += credits
        val xpBefore = state.xpNow()
        val xpGain = (if (isNew) value else Game.SIGHTING_XP) + (grade?.xp ?: 0) + (if (holoNew) 25 else 0)
        val ups = earnXP(state, xpGain)
        return CaptureResult(
            e, form, isNew, rec.count, lvBefore, lvAfter, newForm, credits, xpBefore, state.xpNow(), xpGain,
            grade, holoNew, gains, missionsDone, eventDone, setDone, ups,
        )
    }

    data class Claim(val credits: Int, val xp: Int, val levelUps: List<LevelUp>)

    fun claimMission(state: PlayerState, id: String): Claim? {
        val gained = Game.claimMission(state, id)
        if (gained == 0) return null
        return Claim(gained, Game.MISSION_XP, earnXP(state, Game.MISSION_XP))
    }

    fun claimEvent(state: PlayerState): Claim? {
        val gained = Game.claimEvent(state)
        if (gained == 0) return null
        return Claim(gained, Game.EVENT_XP, earnXP(state, Game.EVENT_XP))
    }

    fun decryptIntel(state: PlayerState, key: String): Boolean {
        if (state.caught.containsKey(key) || state.intel[key] == true || state.shards < Game.DECRYPT_COST) return false
        state.shards -= Game.DECRYPT_COST
        state.intel[key] = true
        return true
    }

    data class BattleOutcome(val reward: Battle.Reward, val xpBefore: Int, val xpAfter: Int, val levelUps: List<LevelUp>)

    fun finishBattle(state: PlayerState, kind: String, won: Boolean): BattleOutcome {
        val reward = Battle.settle(state, kind, won)
        state.shards += reward.credits
        val xpBefore = state.xpNow()
        val ups = earnXP(state, reward.xp)
        return BattleOutcome(reward, xpBefore, state.xpNow(), ups)
    }

    /** A random 6-hex-digit operator id, created once. */
    fun operatorId(state: PlayerState): String =
        state.opId ?: "%06X".format((Math.random() * 0xFFFFFF).toInt()).also { state.opId = it }

    fun xpProgress(state: PlayerState): Double {
        val lv = opLevel(state)
        val lo = Game.xpForLevel(lv); val hi = Game.xpForLevel(lv + 1)
        return max(0.0, (state.xpNow() - lo).toDouble() / (hi - lo))
    }
}
