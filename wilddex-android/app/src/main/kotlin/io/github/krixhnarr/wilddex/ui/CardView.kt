package io.github.krixhnarr.wilddex.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.core.CardRecord
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Entry
import io.github.krixhnarr.wilddex.core.Game

val CardShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)

private val INK = Color(0xFF1E3346)
private val HI = Color(0xFF13283A)
private val DIM = Color(0xFF5B7083)
private val BLUE = Color(0xFF2F9BEA)
private val GOLD = Color(0xFFF2B51D)
private val GOLD_INK = Color(0xFF946600)

fun shortSize(e: Entry): String = e.z.split(Regex(" at | with | body| tall| long|;"))[0]
fun shortDiet(e: Entry): String = e.d.split(Regex(" \\(|,| ·| and | & "))[0]
fun typeColor(e: Entry): Color = hex(Dex.primaryType(e).color)

/**
 * A collectible card. [rec] is the player's record (null = not captured),
 * [intel] = decrypted but not captured, [frame] = the locker card frame.
 */
@Composable
fun CardView(e: Entry, rec: CardRecord?, modifier: Modifier = Modifier, intel: Boolean = false, frame: String = "standard") {
    val known = rec != null || intel
    val tc = if (known) typeColor(e) else Color(0xFF8DA0B0)
    val locked = rec == null
    val holo = rec?.holo != null
    val lv = rec?.let { Game.levelFor(it.count) } ?: 0
    val obsidian = frame == "obsidian" && !locked
    val ink = if (obsidian) Color(0xFFEDE3C4) else INK
    val hi = if (obsidian) Color(0xFFFFF1C9) else HI
    val dim = if (obsidian) Color(0xFFB5AA8A) else DIM
    val border = when {
        e.r == 4 && !locked -> GOLD
        frame != "standard" && !locked -> FRAME_BORDER[frame] ?: tc
        else -> lerp(tc, Color.White, 0.15f)
    }
    val bg: Brush = when {
        locked -> Brush.verticalGradient(listOf(Color(0xFFEDF2F6), Color(0xFFE2E9EF)))
        obsidian -> Brush.verticalGradient(listOf(Color(0xFF2A2A30), Color(0xFF16161A), Color(0xFF0E0E10)))
        e.r == 4 -> Brush.verticalGradient(0f to Color(0xFFFFE59A), 0.45f to Color(0xFFFFF6DA), 1f to Color.White)
        frame == "sakura" -> Brush.verticalGradient(0f to Color(0xFFFFD9DF), 0.45f to Color(0xFFFFF4F6), 1f to Color.White)
        else -> Brush.verticalGradient(0f to lerp(Color.White, tc, 0.26f), 0.42f to Color.White, 1f to Color(0xFFF3F7FA))
    }
    val inf = rememberInfiniteTransition(label = "card")
    val sheen by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "sheen")

    BoxWithConstraints(modifier.aspectRatio(0.7f)) {
        val u = maxWidth.value / 150f // design unit: 1 at 150dp wide
        Box(
            Modifier.fillMaxSize().clip(CardShape).background(bg)
                .drawWithContent {
                    drawContent()
                    // skins (frame cosmetics) under the rarity sheen
                    if (!locked) drawSkin(frame, sheen)
                    if (holo) {
                        val shift = sheen * size.width * 2
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color(0x8CFF6EC8), Color(0x80FFDC6E), Color(0x806EFFBE), Color(0x8C6EC8FF), Color(0x8CBE6EFF), Color.Transparent),
                                start = Offset(-size.width + shift, 0f), end = Offset(shift, size.height),
                            ),
                            blendMode = BlendMode.Multiply, alpha = 0.5f,
                        )
                    } else if (!locked && e.r >= 3) {
                        val shift = sheen * size.width * 2
                        val streak = if (e.r == 4) Color(0x38F2B51D) else Color(0x292F9BEA)
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, streak, Color(0x99FFFFFF), streak, Color.Transparent),
                            start = Offset(-size.width + shift, 0f), end = Offset(shift, size.height)))
                    }
                }
                .border(
                    width = 2.dp,
                    brush = if (holo) Brush.linearGradient(HOLO) else Brush.linearGradient(listOf(border, border)),
                    shape = CardShape,
                ),
        ) {
            // rarity inner frames
            if (!locked && e.r >= 2) {
                val inner = when (e.r) { 2 -> tc.copy(alpha = 0.55f); 3 -> BLUE; else -> GOLD }
                Box(Modifier.fillMaxSize().padding((4 * u).dp).border((1 * u).dp, inner, CutCornerShape(topStart = (9 * u).dp, bottomEnd = (9 * u).dp)))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = (8 * u).dp, vertical = (8 * u).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth()) {
                    Text("#%03d".format(e.no), style = mono((9.5 * u).sp, ink, FontWeight.Bold), modifier = Modifier.align(Alignment.CenterStart))
                    if (!locked && e.r >= 3) Text(if (e.r == 4) "LEGENDARY" else "RARE", style = mono((7 * u).sp, if (e.r == 4) GOLD_INK else BLUE, FontWeight.Bold, 0.3f), modifier = Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.CenterEnd).size((8 * u).dp).rotate(45f).background(tc))
                }
                Box(Modifier.fillMaxWidth().padding(top = (6 * u).dp).aspectRatio(1.28f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize(0.92f).aspectRatio(1f).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(if (obsidian) Color(0xFF4A4A52) else Color.White, if (obsidian) Color(0xFF1E1E22) else lerp(Color(0xFFEAF2F7), tc, 0.22f))))
                            .border((1 * u).dp, Color(0x241E3346), CircleShape),
                    )
                    AnimalArt(
                        e, Modifier.fillMaxSize(0.78f),
                        look = when { rec != null -> ArtLook.Normal; intel -> ArtLook.Gray; else -> ArtLook.Silhouette },
                        silhouette = Color(0x281E3346),
                    )
                    if (rec != null) Box(Modifier.align(Alignment.BottomCenter).offset(y = (4 * u).dp).background(Color(0xE6FFFFFF)).padding(horizontal = (4 * u).dp, vertical = (2 * u).dp)) {
                        Stars(Game.stars(lv), starSize = (8 * u).dp)
                    }
                    if (!known) LockLabel("ENCRYPTED", u)
                    if (intel) LockLabel("NOT CAPTURED", u)
                }
                Spacer(Modifier.height((6 * u).dp))
                Text(
                    if (known) e.n.uppercase() else "? ? ?", style = display((10.5 * u).sp, if (!locked && e.r == 4) GOLD_INK else hi),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth().padding(top = (5 * u).dp)) {
                    Fact("Size", if (known) shortSize(e) else "???", u, dim, ink, Modifier.weight(1f))
                    Fact("Diet", if (known) shortDiet(e) else "???", u, dim, ink, Modifier.weight(1f))
                }
                Row(Modifier.padding(top = (5 * u).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * u).dp)) {
                    Text("Type:", style = mono((8 * u).sp, dim))
                    Dex.typesOf(e).forEachIndexed { i, t ->
                        if (i > 0) Text("/", style = mono((8 * u).sp, dim))
                        TypeGlyph(t, Modifier.size((11 * u).dp), color = if (known) null else Color(0xFF8DA0B0))
                        Text(Dex.types.getValue(t).name, style = mono((8 * u).sp, ink, FontWeight.Bold), maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy((3 * u).dp)) {
                    (1..4).forEach { i ->
                        Box(Modifier.size((6 * u).dp).rotate(45f).background(if (i <= e.r) (if (e.r == 4 && !locked) GOLD else tc) else Color(0xFFCBD6DF)))
                    }
                }
            }
            if (rec != null) {
                Text(
                    "LV$lv", style = display((9 * u).sp, if (e.r == 4) Color(0xFF3F2B00) else Color.White),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = (23 * u).dp, end = (7 * u).dp)
                        .background(if (e.r == 4) GOLD else BLUE).padding(horizontal = (4 * u).dp, vertical = (2 * u).dp),
                )
            }
            if (holo) {
                Text(
                    "HOLO", style = mono((7.5 * u).sp, Color(0xFF1E1E22), FontWeight.Bold, 0.2f),
                    modifier = Modifier.align(Alignment.TopStart).padding(top = (23 * u).dp, start = (7 * u).dp)
                        .background(Brush.horizontalGradient(HOLO)).padding(horizontal = (4 * u).dp, vertical = (2 * u).dp),
                )
            }
        }
    }
}

@Composable
private fun LockLabel(text: String, u: Float) {
    Text(text, style = mono((7.5 * u).sp, INK, FontWeight.Bold, 0.24f), modifier = Modifier.background(Color.White).padding(horizontal = (7 * u).dp, vertical = (3 * u).dp))
}

@Composable
private fun Fact(label: String, value: String, u: Float, dim: Color, ink: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = mono((8 * u).sp, dim, spacing = 0.08f))
        Text(value, style = mono((8.5 * u).sp, ink, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val FRAME_BORDER = mapOf(
    "circuit" to Color(0xFF5FB6DA), "topo" to Color(0xFFCDA66F), "neon" to Color(0xFFE4D9FA),
    "sakura" to Color(0xFFF5B7B2), "aurora" to Color(0xFF9ED8CF), "glitch" to Color(0xFF9AA9B6), "obsidian" to GOLD,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkin(frame: String, t: Float) {
    val w = size.width; val h = size.height
    when (frame) {
        "circuit" -> {
            val step = 22.dp.toPx(); val col = Color(0x335FB6DA)
            var x = 0f
            while (x < w) { drawLine(col, Offset(x, 0f), Offset(x, h), 1.dp.toPx()); x += step }
            var y = step / 2
            while (y < h) { drawLine(col, Offset(0f, y), Offset(w, y), 1.dp.toPx()); var xx = 0f; while (xx < w) { drawCircle(col, 2.dp.toPx(), Offset(xx, y)); xx += step }; y += step * 2 }
        }
        "topo" -> {
            val col = Color(0x38CDA66F)
            for (i in 1..12) drawCircle(col, i * 10.dp.toPx(), Offset(w * 0.18f, h * 0.14f), style = Stroke(1.dp.toPx()))
            for (i in 1..10) drawCircle(col, i * 12.dp.toPx(), Offset(w * 0.92f, h * 0.88f), style = Stroke(1.dp.toPx()))
        }
        "neon" -> {
            for (k in 1..5) drawRoundRect(Color(0xFF2F9BEA).copy(alpha = 0.16f / k), Offset(k * 2f, k * 2f), Size(w - k * 4f, h - k * 4f), CornerRadius(4f), style = Stroke((k * 3).dp.toPx()))
            drawRect(Color.White.copy(alpha = if (t in 0.46f..0.5f) 0.2f else 0.7f), style = Stroke(1.dp.toPx()))
        }
        "sakura" -> listOf(0.14f to 0.18f, 0.82f to 0.1f, 0.9f to 0.58f, 0.08f to 0.72f, 0.62f to 0.94f, 0.4f to 0.06f).forEachIndexed { i, (x, y) ->
            drawOval(if (i % 2 == 0) Color(0x99F5B7B2) else Color(0x99FF7972), Offset(w * x, h * y), Size(10.dp.toPx(), 6.dp.toPx()))
        }
        "aurora" -> drawRect(
            Brush.linearGradient(listOf(Color(0x5571C189), Color(0x455FB6DA), Color(0x55D98DDB), Color(0x5571C189)), start = Offset(0f, -h + t * h * 2), end = Offset(w, t * h * 2)),
            blendMode = BlendMode.Screen,
        )
        "glitch" -> {
            drawRect(Color(0xBFFF3C78), size = Size(3.dp.toPx(), h))
            drawRect(Color(0xBF3CDCFF), topLeft = Offset(w - 3.dp.toPx(), 0f), size = Size(3.dp.toPx(), h))
            var y = 0f
            while (y < h) { drawRect(Color(0x0F000000), Offset(0f, y), Size(w, 1.dp.toPx())); y += 3.dp.toPx() }
        }
        "obsidian" -> drawRect(Color(0x8CE9C46A), topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = Size(w - 12.dp.toPx(), h - 12.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

/** A dashed "?" card for a player with no captures yet. */
@Composable
fun GhostCard(modifier: Modifier = Modifier) {
    val c = LocalWd.current
    Box(modifier.aspectRatio(0.7f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(c.line2, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("?", style = display(52.sp, c.accent))
            Text("your first card", style = mono(11.sp, c.dim))
        }
    }
}
