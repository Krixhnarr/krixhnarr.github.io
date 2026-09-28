package io.github.krixhnarr.wilddex.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Btn { Primary, Neutral }

/** Chunky game button: a solid bottom edge that it sinks into when pressed. */
@Composable
fun ChunkyButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: Btn = Btn.Neutral,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalWd.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val edge = if (small) 3.dp else 4.dp
    val sink by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(70), label = "press")
    val radius = if (small) 10.dp else 12.dp
    val (face, faceBottom, edgeColor, ink) = when (kind) {
        Btn.Primary -> listOf(Color(0xFFFFDE6E), c.sun2, c.sunPress, c.onSun)
        Btn.Neutral -> listOf(c.surface, c.surface, c.press, c.hi)
    }
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(bottom = edge)
            .semantics { contentDescription = text }
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        // the edge underneath
        Box(Modifier.matchParentSize().offset(y = edge).clip(RoundedCornerShape(radius)).background(edgeColor))
        Row(
            Modifier
                .matchParentSize()
                .offset(y = edge * sink * 0.8f)
                .clip(RoundedCornerShape(radius))
                .background(Brush.verticalGradient(listOf(face, faceBottom)))
                .border(1.dp, if (kind == Btn.Neutral) c.line2 else c.sunPress.copy(alpha = 0.6f), RoundedCornerShape(radius)),
        ) {}
        Row(
            Modifier.offset(y = edge * sink * 0.8f).padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 8.dp else 12.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.invoke()
            Text(text.uppercase(), style = display(if (small) 12.sp else 14.sp, ink, 0.05f), textAlign = TextAlign.Center)
        }
    }
}

/** Rounded white panel with a soft edge — the building block of most screens. */
@Composable
fun Panel(modifier: Modifier = Modifier, tint: Color? = null, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalWd.current
    Column(
        modifier
            .padding(bottom = 5.dp)
            .drawBehind {
                drawRoundRect(c.press, topLeft = Offset(0f, 5.dp.toPx()), size = size, cornerRadius = CornerRadius(18.dp.toPx()))
            }
            .clip(RoundedCornerShape(18.dp))
            .background(if (tint != null) Brush.linearGradient(listOf(tint.copy(alpha = 0.2f).compositeOver(c.surface), c.surface)) else Brush.linearGradient(listOf(c.surface, c.surface)))
            .border(1.dp, border ?: c.line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        content = content,
    )
}

fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
fun PanelHead(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = LocalWd.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = display(15.sp, c.hi, 0.05f), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Small(text: String, color: Color? = null) {
    val c = LocalWd.current
    Text(text.uppercase(), style = mono(9.5.sp, color ?: c.dim, spacing = 0.12f))
}

@Composable
fun HelpButton(onClick: () -> Unit) {
    val c = LocalWd.current
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line2, CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = "Help" },
        contentAlignment = Alignment.Center,
    ) { Text("?", style = display(15.sp, c.accentDeep)) }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, highlight: Boolean = false, holo: Boolean = false) {
    val c = LocalWd.current
    val bg: Brush = when {
        holo -> Brush.horizontalGradient(HOLO)
        highlight -> Brush.linearGradient(listOf(c.accent, c.accent))
        else -> Brush.linearGradient(listOf(c.surface2, c.surface2))
    }
    Text(
        text, modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        style = mono(11.sp, if (highlight && !holo) Color.White else if (holo) Color(0xFF1E1E22) else c.ink, androidx.compose.ui.text.font.FontWeight.Bold),
    )
}

val HOLO = listOf(Color(0xFFFF8AD8), Color(0xFFFFE08A), Color(0xFF8AFFD0), Color(0xFF8AD8FF), Color(0xFFC48AFF))

/** Progress bar with rounded ends. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = LocalWd.current
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(c.barBg)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** Five stars; each is 0/50/100% filled (half a star per card level). */
@Composable
fun Stars(fills: List<Int>, starSize: Dp = 10.dp, empty: Color = Color(0x2E1E3346)) {
    val gold = LocalWd.current.gold
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        fills.forEach { f ->
            Canvas(Modifier.size(starSize)) {
                val p = starPath(size.width)
                drawPath(p, empty)
                if (f > 0) clipRectLeft(size.width * f / 100f) { drawPath(p, gold) }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectLeft(w: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    clipRect(right = w) { block() }
}

fun starPath(s: Float): Path = Path().apply {
    val pts = listOf(0.5f to 0f, 0.61f to 0.35f, 0.98f to 0.35f, 0.68f to 0.57f, 0.79f to 0.91f, 0.5f to 0.7f, 0.21f to 0.91f, 0.32f to 0.57f, 0.02f to 0.35f, 0.39f to 0.35f)
    moveTo(pts[0].first * s, pts[0].second * s)
    pts.drop(1).forEach { lineTo(it.first * s, it.second * s) }
    close()
}

// ---------------------------------------------------------------- LED dot-matrix text
private val DOTS: Map<Char, String> = mapOf(
    'A' to "01110100011000111111100011000110001", 'B' to "11110100011000111110100011000111110",
    'C' to "01110100011000010000100001000101110", 'D' to "11110100011000110001100011000111110",
    'E' to "11111100001000011110100001000011111", 'F' to "11111100001000011110100001000010000",
    'G' to "01110100011000010111100011000101111", 'H' to "10001100011000111111100011000110001",
    'I' to "01110001000010000100001000010001110", 'J' to "00111000100001000010000101001001100",
    'K' to "10001100101010011000101001001010001", 'L' to "10000100001000010000100001000011111",
    'M' to "10001110111010110101100011000110001", 'N' to "10001100011100110101100111000110001",
    'O' to "01110100011000110001100011000101110", 'P' to "11110100011000111110100001000010000",
    'Q' to "01110100011000110001101011001001101", 'R' to "11110100011000111110101001001010001",
    'S' to "01111100001000001110000010000111110", 'T' to "11111001000010000100001000010000100",
    'U' to "10001100011000110001100011000101110", 'V' to "10001100011000110001100010101000100",
    'W' to "10001100011000110101101011010101010", 'X' to "10001100010101000100010101000110001",
    'Y' to "10001100010101000100001000010000100", 'Z' to "11111000010001000100010001000011111",
    '0' to "01110100011001110101110011000101110", '1' to "00100011000010000100001000010001110",
    '2' to "01110100010000100010001000100011111", '3' to "11111000100010000010000011000101110",
    '4' to "00010001100101010010111110001000010", '5' to "11111100001111000001000011000101110",
    '6' to "00110010001000011110100011000101110", '7' to "11111000010001000100010000100001000",
    '8' to "01110100011000101110100011000101110", '9' to "01110100011000101111000010001001100",
    '-' to "00000000000000011111000000000000000", '.' to "00000000000000000000000000110001100",
)

private class DotLayout(val cols: Int, val on: List<Pair<Int, Int>>, val off: List<Pair<Int, Int>>)

private fun layoutDots(text: String): DotLayout {
    var x = 0
    val on = mutableListOf<Pair<Int, Int>>(); val off = mutableListOf<Pair<Int, Int>>()
    for (ch in text.uppercase()) {
        if (ch == ' ') { x += 3; continue }
        val g = DOTS[ch] ?: DOTS.getValue('-')
        for (r in 0 until 7) for (col in 0 until 5) (if (g[r * 5 + col] == '1') on else off) += (x + col) to r
        x += 6
    }
    return DotLayout(maxOf(1, x - 1), on, off)
}

/** Headings drawn as an LED board: 5x7 dots per letter, with a few random dots blinking. */
@Composable
fun DotText(text: String, height: Dp, color: Color, modifier: Modifier = Modifier, blink: Boolean = true) {
    val lay = remember(text) { layoutDots(text) }
    val blinking = remember(text) { mutableStateMapOf<Int, Float>() }
    if (blink) LaunchedEffect(text) {
        while (true) {
            delay(420)
            val n = 1 + Random.nextInt(3)
            repeat(n) {
                val i = Random.nextInt(lay.on.size)
                launchBlink(blinking, i)
            }
        }
    }
    Canvas(modifier.height(height).width(height * lay.cols / 7f).semantics { contentDescription = text }) {
        val u = size.height / 7f
        lay.off.forEach { (x, y) -> drawCircle(color.copy(alpha = 0.12f), u * 0.3f, Offset((x + 0.5f) * u, (y + 0.5f) * u)) }
        lay.on.forEachIndexed { i, (x, y) ->
            drawCircle(color.copy(alpha = blinking[i] ?: 1f), u * 0.4f, Offset((x + 0.5f) * u, (y + 0.5f) * u))
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchBlink(map: MutableMap<Int, Float>, i: Int) {
    launch {
        val dur = 250 + Random.nextInt(900)
        map[i] = 0.08f; delay((dur * 0.45).toLong())
        map[i] = 1f; delay((dur * 0.15).toLong())
        map[i] = 0.08f; delay((dur * 0.4).toLong())
        map.remove(i)
    }
}

/** A gently pulsing highlight used on claimable rewards. */
@Composable
fun pulse(periodMs: Int = 1300): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "p")
    return v
}

@Composable
fun XpBar(xpBefore: Int, xpAfter: Int, modifier: Modifier = Modifier) {
    val c = LocalWd.current
    val lv = io.github.krixhnarr.wilddex.core.Game.levelForXP(xpAfter)
    val lo = io.github.krixhnarr.wilddex.core.Game.xpForLevel(lv)
    val hi = io.github.krixhnarr.wilddex.core.Game.xpForLevel(lv + 1)
    val from = if (io.github.krixhnarr.wilddex.core.Game.levelForXP(xpBefore) < lv) 0f else (xpBefore - lo).toFloat() / (hi - lo)
    val to = (xpAfter - lo).toFloat() / (hi - lo)
    val anim = remember { Animatable(from) }
    LaunchedEffect(xpAfter) { delay(300); anim.animateTo(to, tween(1100)) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("OPERATOR LV $lv", style = mono(10.sp, c.dim, spacing = 0.14f), modifier = Modifier.weight(1f))
            Text("+${xpAfter - xpBefore} XP", style = mono(10.sp, c.accentDeep, androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("${xpAfter - lo}/${hi - lo}", style = mono(10.sp, c.dim), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Bar(anim.value, c.accent)
    }
}

/** Box that sits 4dp above a solid edge — tiles, slots and choice rows. */
@Composable
fun Raised(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = LocalWd.current.surface,
    edge: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalWd.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 1f else 0f, tween(70), label = "sink")
    // propagateMinConstraints: when the caller fixes the size (weight, fillMaxWidth)
    // the face fills it; otherwise it wraps its content.
    Box(modifier.padding(bottom = edge), propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().offset(y = edge).clip(shape).background(c.press))
        Box(
            Modifier.offset(y = edge * sink * 0.75f).clip(shape).background(color).border(1.dp, c.line, shape)
                .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier),
            content = content,
        )
    }
}

