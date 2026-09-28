package io.github.krixhnarr.wilddex.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The WildDex card list, affinity types and ImageNet labels. The data lives in
// JSON resources (exported from the original web app) so it can be shared by
// the Android app today and an iPhone app later.

@Serializable
data class Rarity(val r: Int, val name: String, val value: Int)

@Serializable
data class DexSet(val id: String, val name: String, val icon: String, val color: String, val keys: List<String>)

/** One collectible card. `c` are the ImageNet class indices that count as this animal. */
@Serializable
data class Entry(
    val k: String, val no: Int, val n: String, val s: String, val e: String, val art: String,
    val set: String, val r: Int, val c: List<Int>, val h: String, val d: String, val z: String,
    val t: String, val f: String,
)

@Serializable
data class AffinityType(val id: String, val name: String, val color: String, val desc: String, val svg: String)

@Serializable
private data class DexFile(val animalClassLimit: Int, val rarity: List<Rarity>, val sets: List<DexSet>, val entries: List<Entry>)

@Serializable
private data class TypesFile(val types: List<AffinityType>, val affinity: Map<String, List<String>>)

internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; coerceInputValues = true; isLenient = true }

private fun resource(name: String): String =
    Dex::class.java.getResourceAsStream("/$name")?.bufferedReader()?.use { it.readText() }
        ?: error("Missing resource $name")

object Dex {
    private val file: DexFile = json.decodeFromString(resource("dex.json"))
    private val typesFile: TypesFile = json.decodeFromString(resource("types.json"))

    /** ImageNet classes below this index are animals (plus two fossils); the rest are objects. */
    val animalClassLimit: Int = file.animalClassLimit
    val entries: List<Entry> = file.entries
    val sets: List<DexSet> = file.sets
    val byKey: Map<String, Entry> = entries.associateBy { it.k }
    val rarity: Map<Int, Rarity> = file.rarity.associateBy { it.r }
    val types: Map<String, AffinityType> = typesFile.types.associateBy { it.id }
    val typeIds: List<String> = typesFile.types.map { it.id }
    val affinity: Map<String, List<String>> = typesFile.affinity
    val labels: List<String> = json.decodeFromString(resource("labels.json"))
    val total: Int get() = entries.size

    fun setOf(e: Entry): DexSet = sets.first { it.id == e.set }
    fun entriesOf(set: DexSet): List<Entry> = set.keys.map { byKey.getValue(it) }
    fun typesOf(e: Entry): List<String> = affinity.getValue(e.k)
    fun primaryType(e: Entry): AffinityType = types.getValue(typesOf(e).first())
}

val RANKS: List<Pair<Int, String>> = listOf(
    0 to "Rookie", 1 to "Novice Collector", 10 to "Explorer", 25 to "Field Researcher",
    50 to "Naturalist", 100 to "Wildlife Expert", 175 to "Master Collector", 261 to "WildDex Champion",
)

fun rankFor(cards: Int): Pair<Int, String> = RANKS.last { cards >= it.first }
fun nextRank(cards: Int): Pair<Int, String>? = RANKS.firstOrNull { it.first > cards }
