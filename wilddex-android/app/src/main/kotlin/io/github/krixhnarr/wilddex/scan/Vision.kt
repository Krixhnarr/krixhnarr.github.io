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

// On-device recognition with EfficientNet-Lite4 (ImageNet, 1000 classes,
// int8, 300×300, 81.5% top-1), plus the image helpers the scanner needs.

class Classifier private constructor(context: Context) {
    private val interpreter: Interpreter
    private val inScale: Float
    private val inZero: Int
    private val outScale: Float
    private val outZero: Int

    init {
        val fd = context.assets.openFd(MODEL)
        val buf = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buf, Interpreter.Options().setNumThreads(4))
        interpreter.getInputTensor(0).quantizationParams().let { inScale = it.scale; inZero = it.zeroPoint }
        interpreter.getOutputTensor(0).quantizationParams().let { outScale = it.scale; outZero = it.zeroPoint }
    }

    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { ByteArray(1000) }
    private val pixels = IntArray(SIZE * SIZE)
    /** Pixel value 0..255 → the model's quantised input, for EfficientNet's (x − 127) / 128 normalisation. */
    private val lut = ByteArray(256) { v -> (((v - 127) / 128f) / inScale + inZero).roundToInt().coerceIn(0, 255).toByte() }

    /** Probabilities for the 1000 ImageNet classes, for one [SIZE]×[SIZE] bitmap. */
    @Synchronized
    private fun run(bmp: Bitmap): FloatArray {
        bmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        for (p in pixels) {
            input.put(lut[(p shr 16) and 255]); input.put(lut[(p shr 8) and 255]); input.put(lut[p and 255])
        }
        interpreter.run(input, output)
        val out = FloatArray(1000)
        var sum = 0.0
        for (i in 0 until 1000) { out[i] = ((output[0][i].toInt() and 255) - outZero) * outScale; sum += out[i] }
        if (sum > 0) for (i in 0 until 1000) out[i] = (out[i] / sum).toFloat()
        return out
    }

    /**
     * Averages the full square, its mirror image and a tighter centre crop
     * (helps with small or off-centre animals).
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

    /** One quick look (no extra views) — used for the whole-frame screen/print check. */
    fun classifyOnce(square: Bitmap): FloatArray = run(Bitmap.createScaledBitmap(square, SIZE, SIZE, true))

    companion object {
        const val MODEL = "efficientnet_lite4_int8.tflite"
        const val SIZE = 300
        @Volatile private var instance: Classifier? = null
        fun get(context: Context): Classifier = instance ?: synchronized(this) {
            instance ?: Classifier(context.applicationContext).also { instance = it }
        }
    }
}

object Frames {
    /** The centre square of the camera frame (what the round viewfinder shows), scaled to [size]. */
    fun centerSquare(src: Bitmap, size: Int = 480): Bitmap {
        val side = min(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src, Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** The whole camera frame letterboxed on black — used only for the screen/print check. */
    fun letterbox(src: Bitmap, size: Int = 480): Bitmap {
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
