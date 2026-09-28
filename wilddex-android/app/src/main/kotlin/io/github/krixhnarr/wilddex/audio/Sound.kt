package io.github.krixhnarr.wilddex.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.krixhnarr.wilddex.Fx
import io.github.krixhnarr.wilddex.core.Settings
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

// The soundtrack (three looping themes, stingers, nature ambience), the
// game's sound effects, phone vibration and the narrator voice. A port of
// the web app's music.js and sfx.

private val N: Int? = null

private val DAY_CHORDS = listOf(listOf(48, 60, 64, 67), listOf(45, 57, 60, 64), listOf(41, 53, 57, 60), listOf(43, 55, 59, 62))
private val DAY_MEL = listOf(
    listOf(76, 79, 84, 79, 81, 79, 76, N), listOf(72, 76, 81, 76, 79, 76, 72, N), listOf(81, 84, 77, 81, 79, 77, 76, 74), listOf(74, 79, 83, 79, 86, N, 83, N),
    listOf(79, N, 76, 79, 84, N, 83, 81), listOf(81, N, 76, 81, 84, 83, 81, 79), listOf(77, 81, 84, 86, 84, 81, 77, 81), listOf(79, 83, 86, 83, 79, N, N, N),
)
private val NIGHT_CHORDS = listOf(listOf(45, 57, 60, 64), listOf(41, 53, 57, 60), listOf(48, 55, 60, 64), listOf(43, 55, 59, 62))
private val NIGHT_MEL = listOf(listOf(76, N, N, 81, N, 79, N, N), listOf(N, N, 72, N, 74, N, 76, N), listOf(79, N, N, 76, N, 74, N, 72), listOf(74, N, N, N, 76, N, N, N))
private val BATTLE_ROOTS = listOf(40, 40, 36, 38)
private val BATTLE_MEL = listOf(
    listOf(76, 76, 79, 76, 83, 81, 79, 78), listOf(76, N, 79, 81, 83, N, 86, 83), listOf(84, 83, 81, 79, 76, 79, 81, N), listOf(86, 84, 83, 81, 78, N, 74, N),
)

private class Track(val bpm: Int, val steps: Int, val play: Instruments.(step: Int, t: Long, sd: Double) -> Unit)

/** The web app's instruments, as [Note] recipes. */
private class Instruments(val synth: Synth) {
    fun mtof(m: Int) = Synth.mtof(m)
    fun marimba(m: Int, t: Long, v: Double = 0.16) {
        synth.play(Note(t, Wave.Sine, mtof(m), v, 0.004, 0.45, send = 0.35))
        synth.play(Note(t, Wave.Sine, mtof(m) * 4, v * 0.35, 0.002, 0.08))
    }
    fun pluck(m: Int, t: Long, v: Double = 0.05) =
        synth.play(Note(t, Wave.Square, mtof(m), v, 0.003, 0.2, filter = FilterType.Low, cutoff = 3200.0, cutoffTo = 500.0, cutoffTime = 0.18))
    fun bass(m: Int, t: Long, len: Double, v: Double = 0.18, wave: Wave = Wave.Triangle) =
        synth.play(Note(t, wave, mtof(m), v, 0.01, 0.12, hold = max(0.02, len - 0.15), filter = if (wave == Wave.Saw) FilterType.Low else null, cutoff = 700.0))
    fun bell(m: Int, t: Long, v: Double = 0.09) {
        synth.play(Note(t, Wave.Sine, mtof(m), v, 0.005, 1.8, send = 0.6))
        synth.play(Note(t, Wave.Triangle, mtof(m) * 2, v * 0.25, 0.005, 0.9))
    }
    fun pad(ms: List<Int>, t: Long, len: Double, v: Double = 0.035) {
        for (m in ms) for (cents in listOf(-6.0, 6.0)) {
            synth.play(Note(t, Wave.Saw, mtof(m), v, len * 0.35, len * 0.5, hold = len * 0.2, filter = FilterType.Low, cutoff = 900.0, detuneCents = cents))
        }
    }
    fun lead(m: Int, t: Long, len: Double, v: Double = 0.045) =
        synth.play(Note(t, Wave.Square, mtof(m), v, 0.005, 0.1, hold = max(0.01, len - 0.1), send = 0.25, filter = FilterType.Low, cutoff = 2400.0))
    fun kick(t: Long, v: Double = 0.5) = synth.play(Note(t, Wave.Sine, 140.0, v, 0.002, 0.28, glideTo = 42.0, glideTime = 0.22))
    fun snare(t: Long, v: Double = 0.16) = synth.play(Note(t, Wave.Noise, 0.0, v, 0.002, 0.16, filter = FilterType.Band, cutoff = 1800.0, q = 1.0))
    fun hat(t: Long, v: Double = 0.05, len: Double = 0.04) = synth.play(Note(t, Wave.Noise, 0.0, v, 0.001, len, filter = FilterType.High, cutoff = 7000.0))
}

private val TRACKS = mapOf(
    "day" to Track(104, 8) { s, t, sd ->
        val bar = (s / 8) % 8
        val i = s % 8
        val ch = DAY_CHORDS[bar % 4]
        if (i == 0) bass(ch[0], t, sd * 3, 0.16)
        if (i == 3) bass(ch[0] + 12, t, sd * 0.9, 0.1)
        if (i == 4) bass(ch[0] + 7, t, sd * 3, 0.12)
        pluck(ch[1 + (i % 3)], t, if (i % 2 == 1) 0.03 else 0.045)
        DAY_MEL[bar][i]?.let { marimba(it - 12, t) }
        if (i % 4 == 0) kick(t, 0.28)
        if (i % 2 == 1) hat(t, 0.035)
        if (i == 4) snare(t, 0.07)
    },
    "night" to Track(70, 8) { s, t, sd ->
        val bar = (s / 8) % 4
        val i = s % 8
        val ch = NIGHT_CHORDS[bar]
        if (i == 0) { pad(ch.drop(1), t, sd * 8.2); bass(ch[0], t, sd * 7, 0.12, Wave.Sine) }
        NIGHT_MEL[bar][i]?.let { bell(it, t) }
        if (i == 6 && bar % 2 == 1) bell(ch[3] + 12, t, 0.03)
    },
    "battle" to Track(144, 16) { s, t, sd ->
        val bar = (s / 16) % 4
        val i = s % 16
        val root = BATTLE_ROOTS[bar]
        if (i % 2 == 0) bass(root + if (i % 8 == 6) 12 else 0, t, sd * 1.6, 0.15, Wave.Saw)
        if (i % 4 == 0) kick(t, 0.45)
        if (i == 4 || i == 12) snare(t, 0.14)
        hat(t, if (i % 4 == 2) 0.05 else 0.025)
        if (i % 2 == 0) BATTLE_MEL[bar][i / 2]?.let { lead(it - 12, t, sd * 1.7) }
    },
)

/** Music, ambience and effects on one [Synth]. */
class Sound(private val settings: () -> Settings) {
    private val synth = Synth()
    private val inst = Instruments(synth)
    @Volatile private var scene = "day"
    @Volatile private var track: Track? = null
    private var step = 0
    private var nextStep = 0L
    private var stingerUntil = 0L
    private var nextAmb = 0L
    private var ducked = false
    @Volatile private var running = false
    /** Changes to the playing theme, applied on the audio thread. */
    private val actions = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

    init {
        synth.onBlock = { _, to ->
            while (true) { val a = actions.poll() ?: break; a() }
            schedule(to)
        }
    }

    private fun schedule(until: Long) {
        val tr = track ?: return
        val sd = 60.0 / tr.bpm / (tr.steps / 4.0)
        val look = until + (0.1 * synth.sr).toLong()
        while (nextStep < look) {
            if (nextStep >= stingerUntil) tr.play(inst, step, nextStep, sd)
            nextStep += (sd * synth.sr).toLong()
            step++
        }
        // birdsong by day, crickets by night
        if (scene != "battle" && synth.now >= nextAmb) {
            val night = scene == "night"
            if (night) cricket(synth.now) else chirp(synth.now)
            nextAmb = synth.now + ((if (night) 0.9 + Random.nextDouble() * 1.8 else 2.5 + Random.nextDouble() * 5.0) * synth.sr).toLong()
        }
    }

    private fun chirp(t: Long) {
        val n = 2 + Random.nextInt(4)
        val base = 2600 + Random.nextDouble() * 1600
        var s = t
        repeat(n) {
            s += ((0.09 + Random.nextDouble() * 0.05) * synth.sr).toLong()
            synth.play(Note(s, Wave.Sine, base, 0.05, 0.005, 0.07, bus = Bus.Amb, glideTo = base * (1.3 + Random.nextDouble() * 0.4), glideTime = 0.05))
        }
    }

    private fun cricket(t: Long) {
        repeat(3 + Random.nextInt(3)) { k ->
            synth.play(Note(t + (k * 0.055 * synth.sr).toLong(), Wave.Sine, 4400 + Random.nextDouble() * 200, 0.018, 0.004, 0.03, bus = Bus.Amb))
        }
    }

    private fun musicLevel(): Double { val s = settings(); return if (s.music) s.musicVol * (if (ducked) 0.25 else 1.0) * 0.85 else 0.0 }
    private fun ambLevel(): Double { val s = settings(); return if (s.music) s.musicVol * 0.8 else 0.0 }

    /** Renders [seconds] of a theme with no audio device — used by tests. */
    internal fun renderOffline(sceneName: String, seconds: Double): FloatArray {
        running = true
        scene = sceneName
        applyLevels(0.01)
        startTrack(sceneName)
        val out = FloatArray((seconds * synth.sr).toInt() / 512 * 512)
        val block = FloatArray(512)
        for (i in 0 until out.size / 512) { synth.renderOffline(block); block.copyInto(out, i * 512) }
        return out
    }

    /** App came to the foreground. */
    fun resume() {
        if (running) return
        running = true
        synth.start()
        applyLevels(1.0)
        startTrack(scene)
    }

    /** App went to the background: silence and free the audio device. */
    fun pause() {
        if (!running) return
        running = false
        synth.stop()
        track = null
    }

    fun applyLevels(secs: Double = 0.3) {
        synth.fade(Bus.Music, musicLevel(), secs)
        synth.fade(Bus.Amb, ambLevel(), secs)
        synth.fade(Bus.Sfx, if (settings().sound) 1.0 else 0.0, 0.05)
        if (settings().music && running) startTrack(scene)
    }

    fun setScene(name: String) {
        if (name == scene && track != null) return
        scene = name
        if (running) startTrack(name)
    }

    private fun startTrack(name: String) = actions.add { startTrackNow(name) }

    private fun startTrackNow(name: String) {
        val next = TRACKS.getValue(name)
        if (track === next) return
        synth.fade(Bus.Music, 0.0, 0.3)
        step = 0
        nextStep = maxOf(synth.at(0.38), stingerUntil)
        track = next
        synth.fade(Bus.Music, musicLevel(), 0.8)
    }

    fun duck(on: Boolean) { ducked = on; synth.fade(Bus.Music, musicLevel(), if (on) 0.25 else 0.8) }

    fun stinger(win: Boolean) {
        if (!settings().music || !running) return
        actions.add { stingerNow(win) }
    }

    private fun stingerNow(win: Boolean) {
        val t = synth.at(0.05)
        val sec = { x: Double -> t + (x * synth.sr).toLong() }
        if (win) {
            listOf(72, 76, 79, 84).forEachIndexed { i, m -> inst.lead(m, sec(i * 0.12), 0.11, 0.06) }
            listOf(72, 76, 79, 84).forEach { inst.lead(it, sec(0.5), 0.9, 0.035) }
            inst.bass(48, sec(0.5), 0.9, 0.18)
            inst.kick(sec(0.5), 0.4)
            stingerUntil = sec(1.6)
        } else {
            listOf(67, 63, 60, 55).forEachIndexed { i, m -> inst.bell(m, sec(i * 0.28), 0.08) }
            inst.bass(43, sec(0.84), 1.2, 0.12, Wave.Sine)
            stingerUntil = sec(2.2)
        }
        nextStep = stingerUntil
    }

    // ---------------------------------------------------------------- effects
    fun tone(freq: Double, at: Double, dur: Double, wave: Wave = Wave.Square, vol: Double = 0.035) {
        if (!running || !settings().sound) return
        synth.play(Note(synth.at(at), wave, freq, vol * 1.6, 0.008, max(0.01, dur - 0.008), bus = Bus.Sfx, linearAttack = true))
    }
}

/** The real [Fx]: sounds, vibration and the narrator voice. */
class GameFx(context: Context, private val settings: () -> Settings) : Fx {
    val sound = Sound(settings)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { t ->
                    t.language = if (t.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE) Locale.UK else Locale.US
                    t.setSpeechRate(1.04f)
                    t.setPitch(0.8f)
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { sound.duck(false) }
                        @Deprecated("Deprecated in Java") override fun onError(id: String?) { sound.duck(false) }
                    })
                    ttsReady = true
                }
            }
        }
    }

    private fun t(f: Double, at: Double, d: Double, w: Wave = Wave.Square, v: Double = 0.035) = sound.tone(f, at, d, w, v)

    override fun click() = t(1800.0, 0.0, 0.02, Wave.Square, 0.015)
    override fun coin() { t(988.0, 0.0, 0.06, v = 0.03); t(1319.0, 0.06, 0.18, v = 0.03) }
    override fun scan() { for (i in 0 until 10) t(1200.0 + (i % 3) * 400, i * 0.08, 0.03, v = 0.02) }
    override fun charge() { for (i in 0 until 8) t(300.0 + i * 90, i * 0.06, 0.05, Wave.Saw, 0.018) }
    override fun reveal(rarity: Int) {
        val r = rarity.coerceIn(0, 4)
        val notes = listOf(523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0)
        notes.take(3 + r).forEachIndexed { i, f -> t(f, i * 0.07, 0.2, v = 0.03) }
        t(notes[minOf(5, 2 + r)] * 1.5, 0.12 + r * 0.08, 0.6, Wave.Triangle, 0.05)
        if (r >= 3) listOf(1568.0, 1760.0, 2093.0, 2349.0, 2637.0).forEachIndexed { i, f -> t(f, 0.5 + i * 0.06, 0.25, Wave.Triangle, 0.035) }
        if (r == 4) listOf(523.25, 659.25, 783.99).forEach { t(it, 0.9, 1.2, Wave.Sine, 0.05) }
    }
    override fun again() { t(880.0, 0.0, 0.08); t(1320.0, 0.09, 0.14) }
    override fun fail() { t(220.0, 0.0, 0.14, Wave.Saw, 0.03); t(165.0, 0.13, 0.26, Wave.Saw, 0.03) }
    override fun lock() { t(1568.0, 0.0, 0.05, v = 0.03); t(2093.0, 0.06, 0.12, v = 0.03) }
    override fun tick(p: Float) = t(if (p < 0.5f) 900.0 + p * 400 else 1300.0 - (p - 0.5) * 400, 0.0, 0.03, v = 0.015)
    override fun grade(g: String) {
        val n = mapOf("S" to 4, "A" to 3, "B" to 2, "C" to 1)[g] ?: 1
        for (i in 0 until n) t(660.0 * Math.pow(1.26, i.toDouble()), i * 0.07, 0.12, v = 0.03)
        t(180.0, 0.0, 0.18, Wave.Sine, 0.08)
    }
    override fun holo() = listOf(1319.0, 1568.0, 1976.0, 2349.0, 2637.0, 3136.0).forEachIndexed { i, f -> t(f, i * 0.05, 0.3, Wave.Triangle, 0.03) }
    override fun hit(mult: Double) { t(if (mult > 1) 140.0 else 110.0, 0.0, 0.12, Wave.Saw, 0.05); t(if (mult > 1) 420.0 else 300.0, 0.02, 0.06, v = 0.03) }
    override fun guardSfx() { t(520.0, 0.0, 0.08, Wave.Triangle, 0.04); t(780.0, 0.06, 0.1, Wave.Triangle, 0.03) }
    override fun overdrive() { for (i in 0 until 12) t(200.0 + i * 110, i * 0.03, 0.05, Wave.Saw, 0.025) }
    override fun faint() { for (i in 0 until 5) t(500.0 - i * 70, i * 0.07, 0.09, v = 0.03) }
    override fun crack(hit: Int) { t(90.0 + hit * 30, 0.0, 0.1, Wave.Saw, 0.06); t(900.0 + hit * 200, 0.02, 0.05, v = 0.025) }
    override fun duck(on: Boolean) = sound.duck(on)
    override fun settingsChanged() = sound.applyLevels()
    override fun levelUp() {
        listOf(523.25, 659.25, 783.99, 1046.5, 1318.5).forEachIndexed { i, f -> t(f, i * 0.09, 0.25, v = 0.035) }
        listOf(1046.5, 1318.5, 1568.0).forEach { t(it, 0.55, 0.9, Wave.Triangle, 0.04) }
    }

    override fun scene(name: String) = sound.setScene(name)
    override fun stinger(win: Boolean) {
        if (settings().music) sound.stinger(win)
        else if (win) listOf(523.25, 659.25, 783.99, 1046.5, 783.99, 1046.5).forEachIndexed { i, f -> t(f, i * 0.11, 0.22, v = 0.035) }
        else listOf(392.0, 349.23, 311.13, 261.63).forEachIndexed { i, f -> t(f, i * 0.16, 0.3, Wave.Triangle, 0.04) }
    }

    override fun buzz(vararg pattern: Long) {
        if (!settings().haptics || pattern.isEmpty()) return
        val v = vibrator ?: return
        try {
            if (pattern.size == 1) v.vibrate(VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L) + pattern, -1))
        } catch (_: Exception) {}
    }

    override fun speak(text: String) {
        if (!settings().voice || !ttsReady) return
        sound.duck(true)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wilddex")
    }

    override fun stopSpeaking() { tts?.stop(); sound.duck(false) }

    fun shutdown() { tts?.shutdown(); sound.pause() }
}
