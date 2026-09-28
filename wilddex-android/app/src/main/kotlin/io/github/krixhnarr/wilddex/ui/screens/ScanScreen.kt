package io.github.krixhnarr.wilddex.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.scan.ScanController
import io.github.krixhnarr.wilddex.ui.AnimalArt
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.PillButton
import io.github.krixhnarr.wilddex.ui.HelpButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.body
import io.github.krixhnarr.wilddex.ui.caption
import io.github.krixhnarr.wilddex.ui.label
import io.github.krixhnarr.wilddex.ui.headline
import io.github.krixhnarr.wilddex.ui.rarityBlock
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val VF_SHAPE = RoundedCornerShape(28.dp)
private val HUD = Color.White

@Composable
fun ScanScreen(model: GameModel) {
    val c = LocalWd.current
    val ctx = LocalContext.current
    val ctl = remember { ScanController(model, ctx) }
    val scope = rememberCoroutineScope()
    val preview = LocalInspectionMode.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) model.update { settings.camera = true }
    }
    // Camera turns on by itself once the player has allowed it before.
    val wantCamera = granted && model.live.settings.camera
    LaunchedEffect(wantCamera) {
        if (wantCamera) {
            ctl.say("optics online · lens=${if (ctl.front) "front" else "rear"}", ScanController.Kind.Ok)
            ctl.idle()
            ctl.warm()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        ScreenTitle("Scan", "Real, live animals only") { HelpButton { model.fx.click(); model.open(Sheet.Help("scan")) } }

        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(VF_SHAPE).background(Color.Black)
                .border(3.dp, if (ctl.locked) c.success else c.hairline, VF_SHAPE),
        ) {
            if (wantCamera && !preview) CameraPreview(ctl)
            ctl.frozen?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Hud(ctl)
            if (!wantCamera) CameraOff(denied) {
                if (granted) model.update { settings.camera = true } else ask.launch(Manifest.permission.CAMERA)
            }
        }

        Terminal(ctl)

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SideButton(Icon.Torch, "Light", ctl.torch, enabled = ctl.camera?.cameraInfo?.hasFlashUnit() == true) {
                ctl.torch = !ctl.torch
                ctl.camera?.cameraControl?.enableTorch(ctl.torch)
            }
            ScanButton(busy = ctl.phase != ScanController.Phase.Idle) {
                if (!wantCamera) { if (granted) model.update { settings.camera = true } else ask.launch(Manifest.permission.CAMERA) }
                else scope.launch { ctl.scan() }
            }
            SideButton(Icon.Flip, "Flip", false, enabled = wantCamera && ctl.phase == ScanController.Phase.Idle) {
                model.fx.click()
                ctl.front = !ctl.front
            }
        }

        RecentPulls(model)
    }
}

@Composable
private fun CameraPreview(ctl: ScanController) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(ctl.front) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get().also { provider = it }
            val prev = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { ctl.onFrame(it) }
            val selector = if (ctl.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                p.unbindAll()
                ctl.camera = p.bindToLifecycle(owner, selector, prev, analysis)
                ctl.live = true
            } catch (e: Exception) {
                ctl.say("optics not found", ScanController.Kind.Err)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            ctl.cameraStopped()
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView({ view }, Modifier.fillMaxSize())
}

@Composable
private fun CameraOff(denied: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Camera off", style = headline(23.sp, Color.White))
        Text(
            if (denied) "Camera permission blocked. Allow camera access in Settings to scan animals."
            else "Point it at a real, live animal.",
            style = body(16.sp, Color.White), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp),
        )
        PillButton("Turn on camera", onBlock = true, onClick = onClick)
    }
}

@Composable
private fun Hud(ctl: ScanController) {
    val inf = rememberInfiniteTransition(label = "hud")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "spin")
    val breathe by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
    val scanLine by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    val sweeping = ctl.phase == ScanController.Phase.Sweeping
    val analyzing = ctl.phase == ScanController.Phase.Analyzing
    Box(Modifier.fillMaxSize()) {
        // static HUD: ticks, rings, crosshair and corner brackets
        Canvas(Modifier.fillMaxSize()) {
            val u = size.minDimension / 100f
            val ctr = center
            val a = if (sweeping) 0.35f else 0.8f
            drawCircle(HUD.copy(alpha = 0.35f * a), 46 * u, ctr, style = Stroke(1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.8f * u, 2.2f * u))))
            for ((i, r) in listOf(36f, 26f, 16f).withIndex()) drawCircle(HUD.copy(alpha = (0.22f + i * 0.08f) * a * breathe), r * u, ctr, style = Stroke(0.5f * u))
            drawCircle(HUD.copy(alpha = a), 1.3f * u, Offset(44.5f * u, 50 * u))
            drawCircle(HUD.copy(alpha = a), 1.3f * u, Offset(55.5f * u, 50 * u))
            val cross = Path().apply {
                moveTo(50 * u, 8 * u); lineTo(50 * u, 14 * u); moveTo(50 * u, 86 * u); lineTo(50 * u, 92 * u)
                moveTo(8 * u, 50 * u); lineTo(14 * u, 50 * u); moveTo(86 * u, 50 * u); lineTo(92 * u, 50 * u)
            }
            drawPath(cross, HUD.copy(alpha = 0.7f * a), style = Stroke(0.6f * u))
            val br = Path().apply {
                moveTo(4 * u, 14 * u); lineTo(4 * u, 4 * u); lineTo(14 * u, 4 * u)
                moveTo(86 * u, 4 * u); lineTo(96 * u, 4 * u); lineTo(96 * u, 14 * u)
                moveTo(96 * u, 86 * u); lineTo(96 * u, 96 * u); lineTo(86 * u, 96 * u)
                moveTo(14 * u, 96 * u); lineTo(4 * u, 96 * u); lineTo(4 * u, 86 * u)
            }
            drawPath(br, HUD.copy(alpha = 0.9f), style = Stroke(1f * u, cap = StrokeCap.Round))
            if (analyzing) {
                val y = size.height * scanLine
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99DCEEB1), Color.Transparent), startY = y - 30 * u, endY = y + 2 * u), Offset(0f, y - 30 * u), GSize(size.width, 32 * u))
            }
        }
        Text(
            if (ctl.live) "● LIVE" else "○ STBY", style = caption(11.sp, if (ctl.live) Color(0xFFFF3D8B) else HUD, bold = true),
            modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
        )
        Text(
            "LENS:${if (ctl.front) "FRONT" else "REAR"}", style = caption(10.sp, HUD.copy(alpha = 0.8f)),
            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
        )
        // lock-on ring: fills as sync builds while the player holds steady
        AnimatedVisibility(sweeping || ctl.locked, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 1.3f), exit = fadeOut()) {
            val p = ctl.progress
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(spin)) {
                    val u = size.minDimension / 100f
                    drawCircle(HUD.copy(alpha = 0.5f), 36 * u, style = Stroke(1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * u, 3f * u))))
                }
                Canvas(Modifier.fillMaxSize()) {
                    val u = size.minDimension / 100f
                    val col = if (ctl.locked) Color(0xFFDCEEB1) else Color.White
                    drawCircle(HUD.copy(alpha = 0.18f), 30 * u, style = Stroke(4f * u))
                    drawArc(col, -90f, 360f * p, false, Offset(20 * u, 20 * u), GSize(60 * u, 60 * u), style = Stroke(4f * u, cap = StrokeCap.Round))
                    drawCircle(col.copy(alpha = 0.7f), 21 * u, style = Stroke(0.8f * u))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SYNC", style = caption(9.sp, HUD))
                    Text("${(p * 100).toInt()}%", style = headline(21.sp, Color.White))
                }
                if (ctl.locked) Text(
                    "LOCKED", style = headline(16.sp, Color.White),
                    modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Color(0xFFDCEEB1)).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        AnimatedVisibility(sweeping, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Hold steady…",
                    style = headline(16.sp, Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Box(Modifier.padding(top = 8.dp).width(180.dp).height(6.dp).clip(CircleShape).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(ctl.progress).height(6.dp).background(Color.White))
                }
            }
        }
    }
}

@Composable
private fun Terminal(ctl: ScanController) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 96.dp).clip(shape).background(Color.Black).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (ctl.log.isEmpty()) Text("> camera off", style = caption(11.sp, Color.White.copy(alpha = 0.6f)))
        ctl.log.forEachIndexed { i, l ->
            val (prefix, col) = when (l.kind) {
                ScanController.Kind.Ok -> "[ok] " to Color(0xFFDCEEB1)
                ScanController.Kind.Err -> "[err] " to Color(0xFFFF8AB8)
                ScanController.Kind.Warn -> "[warn] " to Color(0xFFF4ECD6)
                ScanController.Kind.Cmd -> "" to Color(0xFFC5B0F4)
                ScanController.Kind.Info -> "" to Color.White
            }
            Text(
                prefix + l.text + if (i == ctl.log.lastIndex) " ▌" else "",
                style = caption(11.sp, col.copy(alpha = if (i == ctl.log.lastIndex) 1f else 0.7f)),
            )
        }
    }
}

@Composable
private fun SideButton(icon: Icon, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }) {
        Raised(
            Modifier.size(56.dp).semantics { contentDescription = label }, shape = CircleShape,
            color = if (active) c.lime else c.canvas, onClick = if (enabled) onClick else null,
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { LineIcon(icon, Modifier.size(24.dp), if (active) c.blockInk else c.ink) }
        }
        Text(label.uppercase(), style = caption(10.sp, c.ink))
    }
}

@Composable
private fun ScanButton(busy: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    val inf = rememberInfiniteTransition(label = "scanbtn")
    val orbit by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 900 else 4000, easing = LinearEasing)), label = "orbit")
    Box(
        Modifier.size(104.dp).clickable(remember { MutableInteractionSource() }, null, enabled = !busy, onClick = onClick)
            .semantics { contentDescription = "Scan" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().rotate(orbit)) {
            drawCircle(c.ink.copy(alpha = 0.5f), size.minDimension / 2 - 3f, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
        }
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(c.ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (busy) "…" else "Scan", style = label(18.sp, c.onInk))
        }
    }
}

@Composable
private fun RecentPulls(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val recent = s.ownedKeys().sortedByDescending { s.caught.getValue(it).last }.take(10)
    if (recent.isEmpty()) return
    Text("Recent pulls", style = headline(20.sp, c.ink), modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (k in recent) {
            val e = Dex.byKey.getValue(k)
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(rarityBlock(c, e.r))
                    .clickable { model.fx.click(); model.open(Sheet.Card(k)) },
                contentAlignment = Alignment.Center,
            ) { AnimalArt(e, Modifier.size(42.dp)) }
        }
    }
}
