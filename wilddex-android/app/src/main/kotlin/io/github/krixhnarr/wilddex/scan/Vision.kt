package io.github.krixhnarr.wilddex.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.github.krixhnarr.wilddex.core.FrameDetect
import io.github.krixhnarr.wilddex.core.GrayFrame
import io.github.krixhnarr.wilddex.core.Parallax
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// On-device recognition with MobileNet v2 (ImageNet, 1001 outputs including
// "background" at 0), plus the image helpers the scanner needs.

class Classifier private constructor(context: Context) {
    private val interpreter: Interpreter

    init {
        val fd = context.assets.openFd("mobilenet_v2.tflite")
        val buf = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buf, Interpreter.Options().setNumThreads(4))
    }

    private val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(1001) }
    private val pixels = IntArray(SIZE * SIZE)

    /** Probabilities for the 1000 ImageNet classes, for one 224×224 bitmap. */
    @Synchronized
    private fun run(bmp: Bitmap): FloatArray {
        bmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 255) / 127.5f - 1f)
            input.putFloat(((p shr 8) and 255) / 127.5f - 1f)
            input.putFloat((p and 255) / 127.5f - 1f)
        }
        interpreter.run(input, output)
        // drop "background" and renormalise: the same as a softmax over logits 1..1000
        val out = FloatArray(1000)
        var sum = 0.0
        for (i in 0 until 1000) { out[i] = output[0][i + 1]; sum += out[i] }
        if (sum > 0) for (i in 0 until 1000) out[i] = (out[i] / sum).toFloat()
        return out
    }

    /**
     * Averages the full square, its mirror image and a tighter centre crop
     * (helps with small or off-centre animals), like the web version.
     */
    fun classify(square: Bitmap): FloatArray {
        val full = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val mirror = Bitmap.createBitmap(full, 0, 0, SIZE, SIZE, Matrix().apply { preScale(-1f, 1f) }, false)
        val s = square.width
        val c0 = (s * 0.14f).roundToInt(); val c1 = (s * 0.86f).roundToInt()
        val crop = Bitmap.createScaledBitmap(Bitmap.createBitmap(square, c0, c0, c1 - c0, c1 - c0), SIZE, SIZE, true)
        val views = listOf(run(full), run(mirror), run(crop))
        return FloatArray(1000) { i -> (views[0][i] + views[1][i] + views[2][i]) / 3f }
    }

    companion object {
        const val SIZE = 224
        @Volatile private var instance: Classifier? = null
        fun get(context: Context): Classifier = instance ?: synchronized(this) {
            instance ?: Classifier(context.applicationContext).also { instance = it }
        }
    }
}

object Frames {
    /** The centre square of the camera frame (what the round viewfinder shows), scaled to [size]. */
    fun centerSquare(src: Bitmap, size: Int = 448): Bitmap {
        val side = min(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src, Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** The whole camera frame letterboxed on black — used only for the screen/print check. */
    fun letterbox(src: Bitmap, size: Int = 448): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val s = size.toFloat() / max(src.width, src.height)
        val dw = src.width * s; val dh = src.height * s
        c.drawBitmap(src, null, RectF((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun scaled(src: Bitmap, long: Int): Bitmap {
        val s = long.toFloat() / max(src.width, src.height)
        return Bitmap.createScaledBitmap(src, max(8, (src.width * s).roundToInt()), max(8, (src.height * s).roundToInt()), true)
    }

    /** Grayscale frame for the parallax depth sweep (long side 256). */
    fun gray(src: Bitmap): GrayFrame {
        val b = scaled(src, Parallax.LONG)
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val g = FloatArray(px.size) { i -> val p = px[i]; (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) }
        return GrayFrame(g, b.width, b.height)
    }

    /** The centre [ScreenDetect.SIZE]² of the frame at native resolution, as grayscale, for the screen-grid check. */
    fun screenCrop(src: Bitmap): FloatArray {
        val n = io.github.krixhnarr.wilddex.core.ScreenDetect.SIZE
        val sz = minOf(n, src.width, src.height)
        val px = IntArray(sz * sz)
        src.getPixels(px, 0, sz, (src.width - sz) / 2, (src.height - sz) / 2, sz, sz)
        val out = FloatArray(n * n) { 128f }
        for (y in 0 until sz) for (x in 0 until sz) {
            val p = px[y * sz + x]
            out[y * n + x] = 0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)
        }
        return out
    }

    /** How steady the phone was: 1 = rock still, 0 = shaking (mean change between frames). */
    fun steadiness(frames: List<GrayFrame>): Double {
        if (frames.size < 2) return 1.0
        var sum = 0.0; var n = 0
        for (i in 1 until frames.size) {
            val a = frames[i - 1].g; val b = frames[i].g
            var d = 0.0
            var k = 0
            while (k < a.size) { d += kotlin.math.abs(a[k] - b[k]); k += 7 }
            sum += d / (a.size / 7); n++
        }
        return (1 - (sum / n) / 25.0).coerceIn(0.0, 1.0)
    }

    /** Luminance at long side 320 for the bezel / print-margin detector. */
    fun detectFrame(src: Bitmap): FrameDetect.Hit {
        val b = scaled(src, FrameDetect.N)
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val y = DoubleArray(px.size) { i -> val p = px[i]; 0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255) }
        return FrameDetect.detect(y, b.width, b.height)
    }
}
