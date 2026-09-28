package io.github.krixhnarr.wilddex

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.krixhnarr.wilddex.core.Battle
import io.github.krixhnarr.wilddex.core.CaptureResult
import io.github.krixhnarr.wilddex.core.Clock
import io.github.krixhnarr.wilddex.core.Entry
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.Progress
import io.github.krixhnarr.wilddex.core.LevelUp
import io.github.krixhnarr.wilddex.core.Loot
import io.github.krixhnarr.wilddex.core.PlayerState
import io.github.krixhnarr.wilddex.core.Recognition
import io.github.krixhnarr.wilddex.data.SaveStore

enum class Tab { Home, Cards, Scan, Arena, Ops, Id }

/** Things that slide up over the current screen. */
sealed interface Sheet {
    data class Card(val key: String) : Sheet
    data class Reveal(val result: CaptureResult, val alternatives: List<Recognition.Candidate>, val snapshot: PlayerState) : Sheet
    data class Choices(val options: List<Recognition.Candidate>, val text: String) : Sheet
    data class Squad(val slot: Int) : Sheet
    data class Fight(val kind: String, val nonce: Long = System.nanoTime()) : Sheet
    data class Crate(val free: Boolean, val nonce: Long = System.nanoTime()) : Sheet
    data class Help(val topic: String) : Sheet
    data object Intro : Sheet
}

/** Cards-tab filters; kept here so they survive switching tabs. */
class BinderFilter {
    var set by mutableStateOf("all")
    var type by mutableStateOf<String?>(null)
    var sort by mutableStateOf("no")
    var owned by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
}

/** Sound, haptics and voice. The real one lives in the audio package; tests use [Fx.Silent]. */
interface Fx {
    fun click() {}
    fun coin() {}
    fun reveal(rarity: Int) {}
    fun crack(hit: Int) {}
    fun buzz(vararg pattern: Long) {}
    fun tick(p: Float) {}
    fun lock() {}
    fun scan() {}
    fun fail() {}
    fun again() {}
    fun charge() {}
    fun holo() {}
    fun grade(g: String) {}
    fun speak(text: String) {}
    fun stopSpeaking() {}
    object Silent : Fx
}

/**
 * The single source of truth for the running game. [state] is a mutable save
 * object; anything that changes it calls [commit], which bumps [rev] so
 * Compose redraws and writes the save to disk.
 */
class GameModel(private val store: SaveStore?, initial: PlayerState? = null, var fx: Fx = Fx.Silent) {
    var state: PlayerState = initial ?: store?.load() ?: PlayerState().also { it.migrate() }
        private set
    var rev by mutableIntStateOf(0)
        private set

    var tab by mutableStateOf(Tab.Home)
    var sheet by mutableStateOf<Sheet?>(null)
    var toast by mutableStateOf<Toast?>(null)
    val levelUps = mutableStateListOf<LevelUp>()
    var night by mutableStateOf(false)
    /** Keys caught this session: their binder slots sparkle once. */
    val fresh = mutableStateListOf<String>()
    /** Set when a new card should fly into the Cards tab. */
    var collectFlight by mutableStateOf<String?>(null)
    var confetti by mutableIntStateOf(0)
    val binder = BinderFilter()

    data class Toast(val text: String, val id: Long = System.nanoTime())

    init { state.migrate() }

    fun commit(save: Boolean = true) {
        rev++
        if (save) store?.save(state)
    }

    inline fun update(block: PlayerState.() -> Unit) { state.block(); commit() }

    fun replace(next: PlayerState) { state = next.also { it.migrate() }; commit() }

    fun say(text: String) { toast = Toast(text) }

    fun open(s: Sheet) { sheet = s }
    fun close() { sheet = null }

    fun queueLevelUps(ups: List<LevelUp>) { levelUps += ups }

    fun store(): SaveStore? = store

    fun applySky() {
        val mode = state.settings.theme
        val h = Clock.hour()
        night = mode == "night" || (mode == "auto" && (h >= 19 || h < 6))
    }

    // helpers used across screens
    fun frame(): String = Loot.frameId(state)
    fun squad(): List<String> = Battle.squad(state)

    /** Set by the activity: open the system file picker to save / load a backup. */
    var exportBackup: (() -> Unit)? = null
    var importBackup: (() -> Unit)? = null

    fun wipe() {
        val keep = state.settings
        state = PlayerState(settings = keep).also { it.migrate() }
        store?.wipePhotos()
        fresh.clear()
        say("BINDER WIPED")
        commit()
    }

    /** The last verified scan, waiting to be registered (directly or via manual choice). */
    class PendingScan(val grade: Game.Grade?, val photo: Bitmap?)
    var pending: PendingScan? = null
    /** Set by the activity: tags a capture with the phone's rough location if enabled. */
    var tagLocation: ((String) -> Unit)? = null
    /** Set by the activity: asks for location permission, then turns location tags on. */
    var enableLocation: (() -> Unit)? = null

    /** Registers a verified capture and opens the reveal. */
    fun register(e: Entry, form: Int, alternatives: List<Recognition.Candidate>) {
        val p = pending
        pending = null
        val snapshot = state.copyDeep()
        val r = Progress.register(state, e, form, p?.grade)
        if (r.isNew) p?.photo?.let { store?.savePhoto(e.k, it) }
        if (r.isNew && e.k !in fresh) fresh += e.k
        tagLocation?.invoke(e.k)
        commit()
        queueLevelUps(r.levelUps)
        fx.buzz(*(if (!r.isNew) longArrayOf(30) else when (e.r) { 4 -> longArrayOf(40, 60, 40, 60, 200); 3 -> longArrayOf(30, 40, 30, 40, 120); else -> longArrayOf(30, 40, 80) }))
        open(Sheet.Reveal(r, alternatives.filter { it.entry != e }, snapshot))
    }

    /** "Misidentified?" — undo everything a registration did, then offer the other candidates. */
    fun rollback(r: Sheet.Reveal) {
        val e = r.result.entry
        replace(r.snapshot)
        levelUps.clear()
        if (r.result.isNew) { store?.deletePhoto(e.k); fresh.remove(e.k) }
        fx.stopSpeaking()
        if (r.alternatives.isNotEmpty()) open(Sheet.Choices(r.alternatives, "Rolled back ${e.n}. Was it one of these?"))
        else { close(); say("REGISTRATION ROLLED BACK") }
    }

    fun go(t: Tab) { if (t != tab) fx.click(); tab = t }

    fun claimMission(id: String) {
        val c = Progress.claimMission(state, id) ?: return
        fx.coin(); fx.buzz(25)
        say("+${c.credits}◆ CREDITS · +${c.xp} XP")
        queueLevelUps(c.levelUps)
        commit()
    }

    fun claimEvent() {
        val c = Progress.claimEvent(state) ?: return
        fx.reveal(2); fx.buzz(30, 40, 90)
        say("EVENT CLEARED · +${c.credits}◆")
        confetti++
        queueLevelUps(c.levelUps)
        commit()
    }

    fun decrypt(key: String) {
        if (!Progress.decryptIntel(state, key)) { say("NEED ${Game.DECRYPT_COST}◆"); return }
        fx.coin()
        say("INTEL DECRYPTED")
        commit()
    }
}

/** Reads the save and subscribes the calling composable to changes. */
val GameModel.live: PlayerState
    @androidx.compose.runtime.Composable get() { rev; return state }
