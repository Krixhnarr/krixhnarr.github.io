package io.github.krixhnarr.wilddex.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.FrameDetect
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.GrayFrame
import io.github.krixhnarr.wilddex.core.Parallax
import io.github.krixhnarr.wilddex.core.Recognition
import io.github.krixhnarr.wilddex.core.SweepResult
import io.github.krixhnarr.wilddex.core.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs a scan: a lock-on sweep that records frames while the player slides
 * the phone, then recognition and the live-animal checks. Only a real, live,
 * 3D animal is registered — prints, photos, screens and videos are rejected.
 */
class ScanController(private val model: GameModel, private val context: Context) {
    enum class Phase { Idle, Sweeping, Analyzing }
    enum class Kind { Cmd, Ok, Err, Warn, Info }
    data class Line(val text: String, val kind: Kind = Kind.Info, val id: Long = System.nanoTime())

    var phase by mutableStateOf(Phase.Idle)
    var progress by mutableFloatStateOf(0f)
    var locked by mutableStateOf(false)
    var frozen by mutableStateOf<ImageBitmap?>(null)
    var front by mutableStateOf(false)
    var torch by mutableStateOf(false)
    var live by mutableStateOf(false)
    var modelReady by mutableStateOf(false)
    var camera: Camera? = null
    val log = mutableStateListOf<Line>()

    private val latest = AtomicReference<Bitmap?>(null)
    private var lastGrab = 0L
    /** Counts camera frames, so the sweep never records the same frame twice. */
    @Volatile private var seq = 0L

    fun say(text: String, kind: Kind = Kind.Info) {
        log += Line(text, kind)
        while (log.size > 6) log.removeAt(0)
    }

    fun idle() = say("Ready · point at a real animal")

    /** Called from the camera analyzer thread for every frame. */
    fun onFrame(img: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastGrab < 45) return
            lastGrab = now
            var b = img.toBitmap()
            val rot = img.imageInfo.rotationDegrees
            if (rot != 0 || front) {
                val m = Matrix().apply { postRotate(rot.toFloat()); if (front) postScale(-1f, 1f) }
                b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            }
            latest.set(b)
            seq++
        } finally {
            img.close()
        }
    }

    /**
     * Holds exposure and white balance still during the sweep, so the frames
     * stay comparable while the phone moves (auto-exposure otherwise keeps
     * changing the brightness mid-sweep).
     */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun lockExposure(on: Boolean) {
        val cam = camera ?: return
        try {
            val ctl = androidx.camera.camera2.interop.Camera2CameraControl.from(cam.cameraControl)
            if (on) ctl.setCaptureRequestOptions(
                androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_LOCK, true)
                    .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_LOCK, true)
                    .build(),
            ) else ctl.clearCaptureRequestOptions()
        } catch (_: Exception) {
            // not supported on this camera: the tracker copes with exposure changes anyway
        }
    }

    fun cameraStopped() { live = false; latest.set(null); camera = null; torch = false }

    suspend fun warm() {
        if (modelReady) return
        say("[..] loading scanner")
        withContext(Dispatchers.Default) { Classifier.get(context) }
        modelReady = true
        say("scanner ready · ${Dex.total} animals known", Kind.Ok)
    }

    suspend fun scan() {
        if (phase != Phase.Idle) return
        val fx = model.fx
        if (latest.get() == null) { say("camera not ready", Kind.Err); return }
        frozen = null
        phase = Phase.Sweeping
        fx.duck(true)
        try {
            say("> wilddex.scan --live", Kind.Cmd)
            say("[..] lock-on sweep · slide phone left, then right")
            // 1) record ~1.9 s while the player slides the phone
            val frames = ArrayList<GrayFrame>(SWEEP_FRAMES)
            var quarter = 0
            var used = -1L
            lockExposure(true)
            val start = System.currentTimeMillis()
            for (i in 0 until SWEEP_FRAMES) {
                // wait for a frame we haven't used yet (a slow camera can repeat frames)
                val waitUntil = System.currentTimeMillis() + 200
                while (seq == used && System.currentTimeMillis() < waitUntil) delay(5)
                used = seq
                val f = latest.get() ?: return
                frames += withContext(Dispatchers.Default) { Frames.gray(f) }
                progress = (i + 1f) / SWEEP_FRAMES
                if (i % 3 == 0) fx.tick(progress)
                if ((progress * 4).toInt() > quarter) { quarter = (progress * 4).toInt(); fx.buzz(12) }
                // keep an even pace however long each step took
                val next = start + (i + 1) * SWEEP_INTERVAL
                if (i < SWEEP_FRAMES - 1) delay(maxOf(0L, next - System.currentTimeMillis()))
            }
            lockExposure(false)
            locked = true
            fx.lock(); fx.buzz(20, 40, 20)

            // 2) freeze the last frame and identify the animal
            val shot = latest.get() ?: return
            val square = Frames.centerSquare(shot)
            frozen = square.asImageBitmap()
            phase = Phase.Analyzing
            fx.scan()
            val started = System.currentTimeMillis()
            if (!modelReady) warm()
            say("[..] inference ×3 (full · mirror · crop)")
            val outcome = withContext(Dispatchers.Default) {
                val classifier = Classifier.get(context)
                val probs = classifier.classify(square)
                val v = Recognition.interpret(probs)
                if (v.kind == Recognition.Kind.MATCH || v.kind == Recognition.Kind.UNSURE) {
                    // 3) only a real, live, 3D animal counts
                    val depth = Parallax.analyse(frames)
                    val spoof = Recognition.spoofCheck(probs, classifier.classify(Frames.letterbox(shot)))
                    val frame = Frames.detectFrame(shot)
                    when {
                        spoof.blocked || frame.found -> Outcome.Spoof(spoof, frame)
                        depth.verdict != Verdict.LIVE -> Outcome.Depth(depth)
                        else -> Outcome.Animal(v, Game.syncGrade(v.top.score, depth))
                    }
                } else Outcome.Plain(v)
            }
            if (outcome !is Outcome.Plain) say("[..] liveness · depth, screen & print checks")
            val wait = 1200 - (System.currentTimeMillis() - started)
            if (wait > 0) delay(wait)
            model.update { scans++ }
            handle(outcome, square)
        } catch (e: Exception) {
            fx.fail()
            say("scan aborted · please retry", Kind.Err)
            frozen = null
        } finally {
            lockExposure(false)
            fx.duck(false)
            locked = false
            progress = 0f
            phase = Phase.Idle
        }
    }

    private sealed interface Outcome {
        data class Animal(val v: Recognition.Verdict, val grade: Game.Grade) : Outcome
        data class Spoof(val spoof: Recognition.Spoof, val frame: FrameDetect.Hit) : Outcome
        data class Depth(val depth: SweepResult) : Outcome
        data class Plain(val v: Recognition.Verdict) : Outcome
    }

    private fun snake(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    private fun conf(x: Double) = "%.2f".format(x)

    private suspend fun handle(o: Outcome, square: Bitmap) {
        val fx = model.fx
        when (o) {
            is Outcome.Animal -> {
                val v = o.v
                model.pending = GameModel.PendingScan(o.grade, Bitmap.createScaledBitmap(square, 360, 360, true))
                if (v.kind == Recognition.Kind.MATCH) {
                    say("match ${snake(v.top.entry.s)} · conf=${conf(v.top.score)}", Kind.Ok)
                    model.register(v.top.entry, v.top.form, v.alternatives)
                } else {
                    fx.again()
                    say("low confidence · top=${conf(v.top.score)} · manual id", Kind.Warn)
                    model.open(Sheet.Choices(v.alternatives, "Signal inconclusive — pick the right animal, or rescan closer and in better light."))
                }
                frozen = null
                idle()
            }
            is Outcome.Depth -> {
                val d = o.depth
                when (d.verdict) {
                    Verdict.FLAT -> { fx.fail(); say("liveness failed · flat surface · ${d.parallax}/${d.tracks} depth points", Kind.Err); say("photos, prints & screens can't be registered", Kind.Err); model.say("FLAT IMAGE DETECTED · SCAN A LIVE ANIMAL") }
                    Verdict.VIDEO -> { fx.fail(); say("liveness failed · moving image on a flat screen · ${d.indep} pts", Kind.Err); say("videos on screens can't be registered", Kind.Err); model.say("VIDEO ON A SCREEN DETECTED · SCAN A LIVE ANIMAL") }
                    Verdict.STILL -> { fx.again(); say("no depth signal · slide the phone left, then right, while scanning", Kind.Warn); model.say("SLIDE YOUR PHONE LEFT, THEN RIGHT") }
                    Verdict.ONEWAY -> { fx.again(); say("one-way motion · slide left AND back right to verify depth", Kind.Warn); model.say("SLIDE LEFT, THEN BACK RIGHT") }
                    else -> {
                        fx.again()
                        say("could only follow ${d.tracks}/${Parallax.MIN_TRACKS} detail points · fill the ring with the animal, slide slowly", Kind.Warn)
                        model.say("CAN'T VERIFY · GET CLOSER, SLIDE SLOWLY")
                    }
                }
                delay(2200); frozen = null; idle()
            }
            is Outcome.Spoof -> {
                fx.fail()
                say(
                    if (o.spoof.blocked) "liveness failed · ${snake(Dex.labels[o.spoof.label])} · conf=${conf(o.spoof.score)}"
                    else "liveness failed · display_or_print_frame detected", Kind.Err,
                )
                say("screens & prints can't be registered", Kind.Err)
                model.say("SCREEN OR PRINT DETECTED · SCAN A REAL ANIMAL")
                delay(2200); frozen = null; idle()
            }
            is Outcome.Plain -> {
                fx.fail()
                if (o.v.kind == Recognition.Kind.OBJECT) say("not fauna: ${snake(Dex.labels[o.v.obj])} · conf=${conf(o.v.objectScore)}", Kind.Err)
                else say("no fauna signature · move closer, hold steady", Kind.Err)
                delay(1800); frozen = null; idle()
            }
        }
    }

    companion object {
        const val SWEEP_FRAMES = 20
        const val SWEEP_INTERVAL = 95L
    }
}
