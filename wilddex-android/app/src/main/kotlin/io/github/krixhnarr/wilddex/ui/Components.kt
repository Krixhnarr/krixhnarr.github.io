package io.github.krixhnarr.wilddex.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.core.Game
import kotlinx.coroutines.delay

/** Primary = black pill (one per section), Secondary = white pill, Promo = the single magenta pill. */
enum class Btn { Primary, Secondary, Promo }

/** Pill button. Pressing scales it down slightly — no darkened fill, per the guide. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: Btn = Btn.Secondary,
    small: Boolean = false,
    enabled: Boolean = true,
    onBlock: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalWd.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, tween(90), label = "press")
    val (bg, fg) = when (kind) {
        Btn.Primary -> if (onBlock) Color.Black to Color.White else c.ink to c.onInk
        Btn.Promo -> c.magenta to Color.White
        Btn.Secondary -> (if (onBlock) Color.White else c.canvas) to (if (onBlock) c.blockInk else c.ink)
    }
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .heightIn(min = if (small) 36.dp else 48.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (kind == Btn.Secondary && !onBlock) Modifier.border(1.dp, c.hairline, RoundedCornerShape(50)) else Modifier)
            .semantics { contentDescription = text }
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.invoke(fg)
        Text(text, style = label(if (small) 14.sp else 17.sp, fg), textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** 40–44dp circular icon button (surface-soft on canvas, translucent white on dark blocks). */
@Composable
fun IconCircle(icon: Icon, description: String, modifier: Modifier = Modifier, inverse: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val c = LocalWd.current
    val bg = when { active -> c.ink; inverse -> Color.White.copy(alpha = 0.16f); else -> c.surfaceSoft }
    val fg = when { active -> c.onInk; inverse -> Color.White; else -> c.ink }
    Box(
        modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, Modifier.size(20.dp), fg, 1.8f) }
}

/** Hairline card on the canvas (pricing-card): radius 24, no shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = LocalWd.current
    Column(
        modifier.clip(RoundedCornerShape(R8.lg)).background(c.canvas).border(1.dp, c.hairline, RoundedCornerShape(R8.lg)).padding(padding),
        content = content,
    )
}

/** Pastel colour-block section: the story surface. Text on it is always black (white on navy). */
@Composable
fun Block(color: Color, modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(24.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(R8.lg)).background(color).padding(padding), content = content)
}

/** Ink colour to use on a block. */
fun blockInk(block: Color): Color = if (block.red + block.green + block.blue < 1.0f) Color.White else Color.Black

/** Mono uppercase eyebrow above a title. */
@Composable
fun Eyebrow(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = caption(11.sp, color ?: LocalWd.current.ink), modifier = modifier)
}

/** Card header: headline title + optional trailing caption / control. */
@Composable
fun PanelHead(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = LocalWd.current
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = headline(20.sp, c.ink), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Small(text: String, color: Color? = null) {
    Text(text.uppercase(), style = caption(10.sp, color ?: LocalWd.current.ink))
}

@Composable
fun HelpButton(inverse: Boolean = false, onClick: () -> Unit) {
    val c = LocalWd.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (inverse) Color.White.copy(alpha = 0.16f) else c.surfaceSoft)
            .clickable(onClick = onClick).semantics { contentDescription = "Help" },
        contentAlignment = Alignment.Center,
    ) { Text("?", style = label(17.sp, if (inverse) Color.White else c.ink)) }
}

/** Small pill tag. `highlight` = the selected/primary black pill. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, highlight: Boolean = false, holo: Boolean = false, fill: Color? = null) {
    val c = LocalWd.current
    val bg: Brush = when {
        holo -> Brush.horizontalGradient(HOLO)
        highlight -> Brush.linearGradient(listOf(c.ink, c.ink))
        fill != null -> Brush.linearGradient(listOf(fill, fill))
        else -> Brush.linearGradient(listOf(c.surfaceSoft, c.surfaceSoft))
    }
    val fg = when { holo -> Color.Black; highlight -> c.onInk; fill != null -> blockInk(fill); else -> c.ink }
    Text(
        text, modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        style = label(13.sp, fg), maxLines = 1,
    )
}

val HOLO = listOf(Color(0xFFFF8AD8), Color(0xFFFFE08A), Color(0xFF8AFFD0), Color(0xFF8AD8FF), Color(0xFFC48AFF))

/** Thin progress bar: hairline track, ink fill. */
@Composable
fun Bar(fraction: Float, color: Color? = null, modifier: Modifier = Modifier, height: Dp = 6.dp, track: Color? = null) {
    val c = LocalWd.current
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track ?: c.hairlineSoft)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color ?: c.ink))
    }
}

/** Five stars; each is 0/50/100% filled (half a star per card level). */
@Composable
fun Stars(fills: List<Int>, starSize: Dp = 10.dp, color: Color? = null, empty: Color? = null) {
    val c = LocalWd.current
    val on = color ?: c.ink
    val off = empty ?: on.copy(alpha = 0.18f)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        fills.forEach { f ->
            Canvas(Modifier.size(starSize)) {
                val p = starPath(size.width)
                drawPath(p, off)
                if (f > 0) clipRect(right = size.width * f / 100f) { drawPath(p, on) }
            }
        }
    }
}

fun starPath(s: Float): Path = Path().apply {
    val pts = listOf(0.5f to 0f, 0.61f to 0.35f, 0.98f to 0.35f, 0.68f to 0.57f, 0.79f to 0.91f, 0.5f to 0.7f, 0.21f to 0.91f, 0.32f to 0.57f, 0.02f to 0.35f, 0.39f to 0.35f)
    moveTo(pts[0].first * s, pts[0].second * s)
    pts.drop(1).forEach { lineTo(it.first * s, it.second * s) }
    close()
}

/** A gently pulsing value used on claimable rewards. */
@Composable
fun pulse(periodMs: Int = 1300): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs), RepeatMode.Reverse), label = "p")
    return v
}

/** Operator XP gained, animated along a thin bar. */
@Composable
fun XpBar(xpBefore: Int, xpAfter: Int, modifier: Modifier = Modifier) {
    val c = LocalWd.current
    val lv = Game.levelForXP(xpAfter)
    val lo = Game.xpForLevel(lv)
    val hi = Game.xpForLevel(lv + 1)
    val from = if (Game.levelForXP(xpBefore) < lv) 0f else (xpBefore - lo).toFloat() / (hi - lo)
    val to = (xpAfter - lo).toFloat() / (hi - lo)
    val anim = remember { Animatable(from) }
    LaunchedEffect(xpAfter) { delay(300); anim.animateTo(to, tween(1100)) }
    Panel(modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Operator Lv $lv", modifier = Modifier.weight(1f))
            Text("+${xpAfter - xpBefore} XP", style = label(14.sp, c.ink))
            Text("  ${xpAfter - lo}/${hi - lo}", style = caption(11.sp, c.ink))
        }
        Bar(anim.value)
    }
}

/**
 * Flat pressable surface (template-card / tile): scales slightly when pressed.
 * On the canvas it gets a hairline; on a colour it is a plain block.
 */
@Composable
fun Raised(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(R8.lg),
    color: Color = LocalWd.current.canvas,
    border: Boolean = color == LocalWd.current.canvas,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalWd.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "sink")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(color)
            .then(if (border) Modifier.border(1.dp, c.hairline, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier),
        propagateMinConstraints = true,
        content = content,
    )
}

/** Flattens a translucent colour onto a background. */
fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
