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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.core.CardRecord
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Entry
import io.github.krixhnarr.wilddex.core.Game

fun shortSize(e: Entry): String = e.z.split(Regex(" at | with | body| tall| long|;"))[0]
fun shortDiet(e: Entry): String = e.d.split(Regex(" \\(|,| ·| and | & "))[0]
fun typeColor(e: Entry): Color = hex(Dex.primaryType(e).color)

/** Corner shape of a card at its natural size (sleeves, card backs). */
val CardShape = RoundedCornerShape(14.dp)

/** The colour block a card of this rarity sits on. Legendary is the one dark block. */
fun rarityBlock(c: WdColors, r: Int): Color = when (r) { 1 -> c.cream; 2 -> c.mint; 3 -> c.lilac; else -> c.navy }

/**
 * A collectible card: a colour block by rarity, the animal on a white disc,
 * and editorial type. [rec] = the player's record (null = not captured),
 * [intel] = decrypted but not captured, [frame] = the locker card frame.
 */
@Composable
fun CardView(e: Entry, rec: CardRecord?, modifier: Modifier = Modifier, intel: Boolean = false, frame: String = "standard") {
    val c = LocalWd.current
    val known = rec != null || intel
    val locked = rec == null
    val holo = rec?.holo != null
    val lv = rec?.let { Game.cardLevel(it) } ?: 0
    val obsidian = frame == "obsidian" && !locked
    val bg = when {
        locked -> c.surfaceSoft
        obsidian -> Color(0xFF0E0E10)
        else -> rarityBlock(c, e.r)
    }
    val ink = if (locked) c.ink else blockInk(bg)
    val inf = rememberInfiniteTransition(label = "card")
    val sheen by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "sheen")

    BoxWithConstraints(modifier.aspectRatio(0.7f)) {
        val u = maxWidth.value / 150f // design unit: 1 at 150dp wide
        val cardShape = RoundedCornerShape((14 * u).dp)
        Box(
            Modifier.fillMaxSize().clip(cardShape).background(bg)
                .drawWithContent {
                    drawContent()
                    if (!locked) drawSkin(frame, sheen)
                    if (holo) {
                        val shift = sheen * size.width * 2
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color(0x8CFF6EC8), Color(0x80FFDC6E), Color(0x806EFFBE), Color(0x8C6EC8FF), Color(0x8CBE6EFF), Color.Transparent),
                                start = Offset(-size.width + shift, 0f), end = Offset(shift, size.height),
                            ),
                            blendMode = BlendMode.Multiply, alpha = 0.45f,
                        )
                    }
                }
                .then(
                    when {
                        holo -> Modifier.border((2 * u).dp, Brush.linearGradient(HOLO), cardShape)
                        locked -> Modifier.border(1.dp, c.hairline, cardShape)
                        frame != "standard" -> Modifier.border((2 * u).dp, FRAME_BORDER[frame] ?: ink, cardShape)
                        else -> Modifier
                    },
                ),
        ) {
            Column(Modifier.fillMaxSize().padding((10 * u).dp), horizontalAlignment = Alignment.Start) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("#%03d".format(e.no), style = caption((8.5 * u).sp, ink), modifier = Modifier.weight(1f))
                    if (holo) Text(
                        "HOLO", style = caption((7 * u).sp, Color.Black, bold = true),
                        modifier = Modifier.padding(end = (4 * u).dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(HOLO))
                            .padding(horizontal = (5 * u).dp, vertical = (1.5 * u).dp),
                    )
                    if (rec != null) Text(
                        "Lv $lv", style = label((8.5 * u).sp, bg),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(ink).padding(horizontal = (6 * u).dp, vertical = (1.5 * u).dp),
                    )
                }
                Box(Modifier.fillMaxWidth().padding(top = (6 * u).dp).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize(0.94f).aspectRatio(1f).clip(CircleShape)
                            .background(if (locked) c.canvas else if (ink == Color.White) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.7f)),
                    )
                    AnimalArt(
                        e, Modifier.fillMaxSize(0.78f),
                        look = when { rec != null -> ArtLook.Normal; intel -> ArtLook.Gray; else -> ArtLook.Silhouette },
                        silhouette = c.hairline,
                    )
                    if (!known) LockLabel("Encrypted", u)
                    if (intel) LockLabel("Not captured", u)
                }
                Spacer(Modifier.height((6 * u).dp))
                Text(
                    if (known) e.n else "Unknown", style = headline((12.5 * u).sp, ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth().padding(top = (3 * u).dp)) {
                    Fact("Size", if (known) shortSize(e) else "—", u, ink, Modifier.weight(1f))
                    Fact("Diet", if (known) shortDiet(e) else "—", u, ink, Modifier.weight(1f))
                }
                Row(Modifier.padding(top = (4 * u).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * u).dp)) {
                    Dex.typesOf(e).forEachIndexed { i, t ->
                        if (i > 0) Text("·", style = body((8 * u).sp, ink))
                        TypeGlyph(t, Modifier.size((10 * u).dp), color = ink)
                        Text(Dex.types.getValue(t).name, style = body((8 * u).sp, ink, 480), maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (rec != null) Stars(Game.stars(lv), (7.5 * u).dp, color = ink) else Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    Text(Dex.rarity.getValue(e.r).name.uppercase(), style = caption((7 * u).sp, ink))
                }
            }
        }
    }
}

@Composable
private fun LockLabel(text: String, u: Float) {
    val c = LocalWd.current
    Text(
        text.uppercase(), style = caption((7.5 * u).sp, c.onInk),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = (8 * u).dp, vertical = (3 * u).dp),
    )
}

@Composable
private fun Fact(label: String, value: String, u: Float, ink: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = caption((6.5 * u).sp, ink))
        Text(value, style = body((8.5 * u).sp, ink, 480), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val FRAME_BORDER = mapOf(
    "circuit" to Color(0xFF5FB6DA), "topo" to Color(0xFFCDA66F), "neon" to Color(0xFFFF3D8B),
    "sakura" to Color(0xFFF5B7B2), "aurora" to Color(0xFF9ED8CF), "glitch" to Color(0xFF3CDCFF), "obsidian" to Color(0xFFE9C46A),
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkin(frame: String, t: Float) {
    val w = size.width; val h = size.height
    when (frame) {
        "circuit" -> {
            val step = 22.dp.toPx(); val col = Color(0x2E000000)
            var x = 0f
            while (x < w) { drawLine(col, Offset(x, 0f), Offset(x, h), 0.7.dp.toPx()); x += step }
            var y = step / 2
            while (y < h) { drawLine(col, Offset(0f, y), Offset(w, y), 0.7.dp.toPx()); var xx = 0f; while (xx < w) { drawCircle(col, 1.6.dp.toPx(), Offset(xx, y)); xx += step }; y += step * 2 }
        }
        "topo" -> {
            val col = Color(0x33000000)
            for (i in 1..12) drawCircle(col, i * 10.dp.toPx(), Offset(w * 0.18f, h * 0.14f), style = Stroke(0.8.dp.toPx()))
            for (i in 1..10) drawCircle(col, i * 12.dp.toPx(), Offset(w * 0.92f, h * 0.88f), style = Stroke(0.8.dp.toPx()))
        }
        "neon" -> {
            for (k in 1..4) drawRoundRect(Color(0xFFFF3D8B).copy(alpha = 0.14f / k), Offset(k * 2f, k * 2f), Size(w - k * 4f, h - k * 4f), CornerRadius(12f), style = Stroke((k * 3).dp.toPx()))
        }
        "sakura" -> listOf(0.14f to 0.18f, 0.82f to 0.1f, 0.9f to 0.58f, 0.08f to 0.72f, 0.62f to 0.94f, 0.4f to 0.06f).forEachIndexed { i, (x, y) ->
            drawOval(if (i % 2 == 0) Color(0x99F5B7B2) else Color(0x99FF7972), Offset(w * x, h * y), Size(10.dp.toPx(), 6.dp.toPx()))
        }
        "aurora" -> drawRect(
            Brush.linearGradient(listOf(Color(0x4471C189), Color(0x335FB6DA), Color(0x44D98DDB), Color(0x4471C189)), start = Offset(0f, -h + t * h * 2), end = Offset(w, t * h * 2)),
            blendMode = BlendMode.Multiply,
        )
        "glitch" -> {
            drawRect(Color(0xBFFF3C78), size = Size(3.dp.toPx(), h))
            drawRect(Color(0xBF3CDCFF), topLeft = Offset(w - 3.dp.toPx(), 0f), size = Size(3.dp.toPx(), h))
            var y = 0f
            while (y < h) { drawRect(Color(0x0F000000), Offset(0f, y), Size(w, 1.dp.toPx())); y += 3.dp.toPx() }
        }
        "obsidian" -> drawRoundRect(Color(0x8CE9C46A), topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = Size(w - 12.dp.toPx(), h - 12.dp.toPx()), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
    }
}

/** An empty "your first card" placeholder: dashed hairline on the canvas. */
@Composable
fun GhostCard(modifier: Modifier = Modifier) {
    val c = LocalWd.current
    Box(modifier.aspectRatio(0.7f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(c.ink.copy(alpha = 0.25f), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("?", style = display(64.sp, c.ink))
            Eyebrow("Your first card")
        }
    }
}

