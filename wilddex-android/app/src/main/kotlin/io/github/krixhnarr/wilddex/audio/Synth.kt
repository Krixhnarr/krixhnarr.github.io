package io.github.krixhnarr.wilddex.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tan
import kotlin.random.Random

// A tiny real-time synthesiser: oscillators with exponential envelopes and
// optional filters, three buses (music, ambience, effects), an echo on the
// music bus and a soft limiter. Everything WildDex plays is generated here —
// the app ships no audio files.

enum class Wave { Sine, Square, Triangle, Saw, Noise }
enum class Bus { Music, Amb, Sfx }
enum class FilterType { Low, High, Band }

/** One note. Times are in seconds, relative to [start] (in samples). */
class Note(
    val start: Long,
    val wave: Wave,
    val freq: Double,
    val peak: Double,
    val attack: Double,
    val decay: Double,
    val hold: Double = 0.0,
    val bus: Bus = Bus.Music,
    val send: Double = 0.0,
    val glideTo: Double? = null,
    val glideTime: Double = 0.0,
    val filter: FilterType? = null,
    val cutoff: Double = 1000.0,
    val cutoffTo: Double? = null,
    val cutoffTime: Double = 0.0,
    val q: Double = 0.707,
    val linearAttack: Boolean = false,
    val detuneCents: Double = 0.0,
)

private const val FLOOR = 0.0001

/** Running state of a [Note] inside the render thread. */
private class Voice(val n: Note, val sr: Int) {
    var phase = if (n.detuneCents != 0.0) Random.nextDouble() else 0.0
    var freq = n.freq * 2.0.pow(n.detuneCents / 1200.0)
    val freqMul = if (n.glideTo != null && n.glideTime > 0) (n.glideTo / n.freq).pow(1.0 / (n.glideTime * sr)) else 1.0
    var glideLeft = (n.glideTime * sr).toLong()
    var t = 0L
    val aN = maxOf(1L, (n.attack * sr).toLong())
    val hN = (n.hold * sr).toLong()
    val dN = maxOf(1L, (n.decay * sr).toLong())
    var env = if (n.linearAttack) 0.0 else FLOOR
    val aMul = (n.peak / FLOOR).pow(1.0 / aN)
    val dMul = (FLOOR / n.peak).pow(1.0 / dN)
    val done get() = t >= aN + hN + dN

    // TPT state-variable filter
    var cutoff = n.cutoff
    val cutMul = if (n.cutoffTo != null && n.cutoffTime > 0) (n.cutoffTo / n.cutoff).pow(1.0 / (n.cutoffTime * sr)) else 1.0
    var cutLeft = (n.cutoffTime * sr).toLong()
    var ic1 = 0.0; var ic2 = 0.0
    var a1 = 0.0; var a2 = 0.0; var a3 = 0.0; val k = 1.0 / n.q
    init { coeffs() }
    fun coeffs() {
        val g = tan(PI * minOf(cutoff, sr * 0.45) / sr)
        a1 = 1.0 / (1.0 + g * (g + k)); a2 = g * a1; a3 = g * a2
    }

    fun sample(): Double {
        // envelope
        when {
            t < aN -> env = if (n.linearAttack) n.peak * (t + 1).toDouble() / aN else env * aMul
            t < aN + hN -> env = n.peak
            else -> env *= dMul
        }
        t++
        // oscillator
        val x = when (n.wave) {
            Wave.Sine -> Synth.sin(phase)
            Wave.Square -> if (phase < 0.5) 1.0 else -1.0
            Wave.Triangle -> 1.0 - 4.0 * abs(phase - 0.5)
            Wave.Saw -> 2.0 * phase - 1.0
            Wave.Noise -> Random.nextDouble() * 2 - 1
        }
        phase += freq / sr
        if (phase >= 1.0) phase -= phase.toLong()
        if (glideLeft > 0) { freq *= freqMul; glideLeft-- }
        var y = x
        if (n.filter != null) {
            if (cutLeft > 0) { cutoff *= cutMul; cutLeft--; if (cutLeft % 16 == 0L) coeffs() }
            val v3 = x - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2 * v1 - ic1; ic2 = 2 * v2 - ic2
            y = when (n.filter) { FilterType.Low -> v2; FilterType.Band -> v1 * k; FilterType.High -> x - k * v1 - v2 }
        }
        return y * env
    }
}

/** Smoothly ramped gain. */
private class Gain(var value: Double) {
    private var target = value
    private var step = 0.0
    private var left = 0L
    fun ramp(to: Double, samples: Long) {
        target = to
        left = maxOf(1L, samples)
        step = (to - value) / left
    }
    fun next(): Double {
        if (left > 0) { value += step; left--; if (left == 0L) value = target }
        return value
    }
}

class Synth(val sr: Int = 44100) {
    private val incoming = ConcurrentLinkedQueue<Note>()
    private val voices = ArrayList<Voice>(64)
    private val gains = mapOf(Bus.Music to Gain(0.0), Bus.Amb to Gain(0.0), Bus.Sfx to Gain(1.0))
    private val fades = ConcurrentLinkedQueue<Triple<Bus, Double, Double>>()

    // echo on the music bus: delay -> lowpass -> feedback, and -> wet
    private val echo = DoubleArray((0.28 * sr).toInt())
    private var echoI = 0
    private var echoLp = 0.0
    private val echoA = 1 - exp(-2 * PI * 2600 / sr)

    @Volatile var now = 0L
        private set
    /** Called on the audio thread before each block; schedule notes for the block here. */
    var onBlock: ((from: Long, to: Long) -> Unit)? = null

    fun play(n: Note) { incoming += n }
    fun fade(bus: Bus, to: Double, secs: Double) { fades += Triple(bus, to, secs) }
    fun at(secsFromNow: Double): Long = now + (secsFromNow * sr).toLong()

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        val block = 512
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(min, block * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        thread = Thread({
            val buf = FloatArray(block)
            while (running) {
                render(buf)
                t.write(buf, 0, block, AudioTrack.WRITE_BLOCKING)
            }
        }, "wilddex-synth").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track?.run { try { stop() } catch (_: Exception) {}; release() }
        track = null
    }

    /** Renders the next block without a device — used by tests. */
    internal fun renderOffline(out: FloatArray) = render(out)

    private fun render(out: FloatArray) {
        onBlock?.invoke(now, now + out.size)
        while (true) { val n = incoming.poll() ?: break; voices += Voice(n, sr) }
        while (true) { val (b, to, secs) = fades.poll() ?: break; gains.getValue(b).ramp(to, (secs * sr).toLong()) }
        val gm = gains.getValue(Bus.Music); val ga = gains.getValue(Bus.Amb); val gs = gains.getValue(Bus.Sfx)
        for (i in out.indices) {
            val s = now + i
            var music = 0.0; var amb = 0.0; var sfx = 0.0; var send = 0.0
            var j = 0
            while (j < voices.size) {
                val v = voices[j]
                if (v.n.start > s) { j++; continue }
                val y = v.sample()
                when (v.n.bus) { Bus.Music -> music += y; Bus.Amb -> amb += y; Bus.Sfx -> sfx += y }
                if (v.n.send > 0) send += y * v.n.send
                if (v.done) { voices[j] = voices[voices.size - 1]; voices.removeAt(voices.size - 1) } else j++
            }
            val d = echo[echoI]
            echoLp += echoA * (d - echoLp)
            echo[echoI] = (send + echoLp * 0.32)
            echoI = (echoI + 1) % echo.size
            music += echoLp * 0.35
            val mix = music * gm.next() + amb * ga.next() + sfx * gs.next()
            out[i] = soft(mix * 1.6).toFloat()
        }
        now += out.size
    }

    private fun soft(x: Double): Double = if (x > 1.5) 1.0 else if (x < -1.5) -1.0 else x - x * x * x / 6.75

    companion object {
        private val SIN = DoubleArray(4097) { kotlin.math.sin(2 * PI * it / 4096) }
        fun sin(phase: Double): Double { val p = phase * 4096; val i = p.toInt(); val f = p - i; return SIN[i] + (SIN[i + 1] - SIN[i]) * f }
        fun mtof(m: Int): Double = 440.0 * 2.0.pow((m - 69) / 12.0)
    }
}
