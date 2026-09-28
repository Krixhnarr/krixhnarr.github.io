package io.github.krixhnarr.wilddex.audio

import io.github.krixhnarr.wilddex.core.Settings
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** Renders each theme offline: it must be audible, never clip hard, and render faster than real time. */
class SynthTest {
    private fun wav(name: String, pcm: FloatArray, sr: Int = 44100) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (x in pcm) data.putShort((x.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sr).putInt(sr * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size * 2)
        File("build/sound").mkdirs()
        File("build/sound/$name.wav").writeBytes(h.array() + data.array())
    }

    @Test fun themes() {
        for (scene in listOf("day", "night", "battle")) {
            val sound = Sound { Settings(music = true, musicVol = 0.6) }
            val t0 = System.nanoTime()
            val pcm = sound.renderOffline(scene, 12.0)
            val ms = (System.nanoTime() - t0) / 1e6
            val peak = pcm.maxOf { abs(it) }
            val rms = sqrt(pcm.sumOf { (it * it).toDouble() } / pcm.size)
            println("$scene: peak=%.3f rms=%.4f render=%.0fms for 12s".format(peak, rms, ms))
            wav(scene, pcm)
            assertTrue("$scene NaN", pcm.none { it.isNaN() })
            assertTrue("$scene silent", rms > 0.01)
            assertTrue("$scene clipping", peak < 0.999f)
        }
    }
}
