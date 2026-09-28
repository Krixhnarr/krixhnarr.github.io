package io.github.krixhnarr.wilddex.core

import kotlin.math.floor

// Supply crates: bought with credits (or earned by levelling up) and full of
// cosmetics only — card frames and operator titles. Animals never come out of
// a crate; those still have to be scanned for real.

object Loot {
    data class Tier(val name: String, val color: Long)
    val TIERS = mapOf(
        1 to Tier("Common", 0xFFB4B4BC),
        2 to Tier("Rare", 0xFFB9A0F0),
        3 to Tier("Epic", 0xFF5FB6DA),
        4 to Tier("Legendary", 0xFFE9C46A),
    )

    data class Item(val id: String, val name: String, val r: Int, val desc: String = "")

    val FRAMES = listOf(
        Item("standard", "Standard", 0, "Factory issue"),
        Item("circuit", "Circuit", 1, "Printed traces"),
        Item("topo", "Topo", 1, "Contour lines"),
        Item("neon", "Neon", 2, "Glow tube"),
        Item("sakura", "Sakura", 2, "Butterfly pink"),
        Item("aurora", "Aurora", 3, "Shifting sky light"),
        Item("glitch", "Glitch", 3, "RGB split signal"),
        Item("obsidian", "Obsidian", 4, "Black glass, gold edge"),
    )

    val TITLES = listOf(
        Item("rookie", "Field Rookie", 0),
        Item("puddle", "Puddle Scout", 1),
        Item("bugmag", "Bug Magnet", 1),
        Item("moth", "Moth Mechanic", 1),
        Item("fox", "Fox Friend", 1),
        Item("signal", "Signal Chaser", 1),
        Item("owl", "Owl Whisperer", 2),
        Item("night", "Night Stalker", 2),
        Item("beetle", "Beetle Baron", 2),
        Item("tide", "Tide Reader", 2),
        Item("storm", "Storm Caller", 3),
        Item("apex", "Apex Operator", 3),
        Item("wild", "Wild Card", 3),
        Item("relic", "Relic Keeper", 4),
    )

    const val CRATE_COST = 60
    const val DUPE_REFUND = 20
    private val WEIGHTS = listOf(1 to 60.0, 2 to 28.0, 3 to 10.0, 4 to 2.0)

    fun frameId(state: PlayerState): String = state.locker?.frame?.takeIf { f -> FRAMES.any { it.id == f } } ?: "standard"
    fun titleName(state: PlayerState): String = TITLES.firstOrNull { it.id == state.locker?.title }?.name ?: TITLES[0].name

    data class Drop(val kind: String, val item: Item, val dupe: Boolean)

    /** Opens one crate. `free` uses an earned crate, otherwise it costs credits. */
    fun openCrate(state: PlayerState, free: Boolean, rnd: () -> Double = { Math.random() }): Drop? {
        val l = state.locker()
        if (free) {
            if (l.crates < 1) return null
            l.crates--
        } else {
            if (state.shards < CRATE_COST) return null
            state.shards -= CRATE_COST
        }
        l.opened++
        var roll = rnd() * 100
        var tier = 1
        for ((t, w) in WEIGHTS) { if (roll < w) { tier = t; break }; roll -= w }
        val pool = FRAMES.filter { it.r == tier }.map { "frame" to it } + TITLES.filter { it.r == tier }.map { "title" to it }
        val (kind, item) = pool[floor(rnd() * pool.size).toInt()]
        val list = if (kind == "frame") l.frames else l.titles
        val dupe = list.contains(item.id)
        if (dupe) state.shards += DUPE_REFUND else list += item.id
        return Drop(kind, item, dupe)
    }
}
