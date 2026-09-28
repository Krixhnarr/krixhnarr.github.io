package io.github.krixhnarr.wilddex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// The living background behind every screen. Day: sky gradient, a pulsing
// sun, drifting clouds, a flock of birds, butterflies and swaying grass on
// rolling hills. Night: moon, twinkling stars, fireflies and shooting stars.

private val STARS = List(40) { Random(it * 7 + 3).let { r -> Triple(r.nextFloat(), r.nextFloat() * 0.62f, 0.8f + r.nextFloat() * 1.2f) } }

@Composable
fun SkyBackground(modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = LocalWd.current
    val t by produceState(0f, animate) {
        if (!animate) return@produceState
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000f }
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(c.skyTop, c.skyBottom), endY = size.height * 0.72f))
        val w = size.width; val h = size.height
        if (!c.night) {
            // sun
            val sunR = 46.dp.toPx() * (1f + 0.03f * sin(t * 1.05f))
            val sc = Offset(w * 0.82f, h * 0.14f)
            drawCircle(Brush.radialGradient(listOf(Color(0x73FFDE6E), Color(0x00FFDE6E)), sc, sunR * 2.4f), sunR * 2.4f, sc)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFBD6), Color(0xFFFFD84D)), sc, sunR), sunR, sc)
            // clouds
            listOf(Triple(0.16f, 80f, 1f), Triple(0.34f, 110f, 0.7f), Triple(0.07f, 95f, 0.55f)).forEachIndexed { i, (y, period, scale) ->
                val span = w + 260.dp.toPx()
                val x = ((t + i * 37f) % period) / period * span - 200.dp.toPx()
                cloud(Offset(x, h * y), scale * 1.dp.toPx(), Color.White.copy(alpha = 0.92f))
            }
            // birds
            val flock = (t % 34f) / 34f
            val fx = -140.dp.toPx() + flock * (w + 180.dp.toPx())
            val fy = h * 0.19f + sin(flock * PI.toFloat() * 4) * 12.dp.toPx()
            listOf(Offset(0f, 14f), Offset(30f, 0f), Offset(58f, 20f)).forEachIndexed { i, o ->
                bird(Offset(fx + o.x.dp.toPx(), fy + o.y.dp.toPx()), (1f - i * 0.15f) * 1.dp.toPx(), sin(t * 15f + i))
            }
        } else {
            // moon, stars, shooting star
            val mc = Offset(w * 0.8f, h * 0.13f)
            val mr = 34.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(Color(0x4DAABEFF), Color(0x00AABEFF)), mc, mr * 2.4f), mr * 2.4f, mc)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFDCE5FF)), mc, mr), mr, mc)
            STARS.forEachIndexed { i, (x, y, r) ->
                val a = 0.55f + 0.45f * abs(sin(t * 0.8f + i))
                drawCircle(Color.White.copy(alpha = a), r * 1.dp.toPx(), Offset(x * w, y * h))
            }
            val sp = (t % 11f) / 11f
            if (sp > 0.88f) {
                val k = (sp - 0.88f) / 0.12f
                val start = Offset(w * 0.1f + k * w * 0.6f, h * 0.12f + k * h * 0.26f)
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White), start - Offset(110f, 50f), start), start - Offset(110f, 50f), start, 2.dp.toPx(), StrokeCap.Round, alpha = 1f - k)
            }
        }
        // hills
        val hillTop = h * 0.62f
        drawOval(c.hill3, Offset(-w * 0.4f, hillTop + h * 0.02f), Size(w * 1.2f, h * 0.55f))
        drawOval(c.hill2, Offset(w * 0.25f, hillTop - h * 0.02f), Size(w * 1.2f, h * 0.6f))
        drawOval(c.hill1, Offset(-w * 0.3f, hillTop + h * 0.13f), Size(w * 1.6f, h * 0.5f))
        // grass tufts
        listOf(0.06f to 0.86f, 0.28f to 0.8f, 0.52f to 0.89f, 0.74f to 0.82f, 0.9f to 0.88f).forEachIndexed { i, (x, y) ->
            tuft(Offset(w * x, h * y), 1.dp.toPx(), sin(t * 1.9f + i * 1.3f) * 7f, c.hill3)
        }
        if (!c.night) {
            // butterflies
            butterfly(Offset(w * 0.14f + sin(t * 0.33f) * 70.dp.toPx(), h * 0.72f + sin(t * 0.5f) * 30.dp.toPx()), 1.dp.toPx(), t * 28f, Color(0xFFFF7A59), Color(0xFFFFC83D))
            butterfly(Offset(w * 0.7f + sin(t * 0.27f + 2f) * 60.dp.toPx(), h * 0.77f + sin(t * 0.41f) * 26.dp.toPx()), 0.8f.dp.toPx(), t * 31f, Color(0xFFFF8AD8), Color.White)
        } else {
            // fireflies
            repeat(9) { i ->
                val x = w * (0.1f + (i * 0.37f) % 0.85f) + sin(t * 0.6f + i) * 30.dp.toPx()
                val y = h * (0.62f + (i * 0.13f) % 0.25f) + sin(t * 0.45f + i * 2) * 24.dp.toPx()
                val a = 0.3f + 0.7f * abs(sin(t * 1.3f + i * 1.7f))
                drawCircle(Color(0x66FFF078), 9.dp.toPx(), Offset(x, y), alpha = a)
                drawCircle(Color(0xFFFFF59E), 2.5f.dp.toPx(), Offset(x, y), alpha = a)
            }
        }
    }
}

private fun DrawScope.cloud(o: Offset, s: Float, col: Color) {
    drawRoundRect(col, o, Size(150 * s, 44 * s), androidx.compose.ui.geometry.CornerRadius(22 * s))
    drawCircle(col, 32 * s, o + Offset(54 * s, 2 * s))
    drawCircle(col, 24 * s, o + Offset(98 * s, 4 * s))
}

private fun DrawScope.bird(o: Offset, s: Float, flap: Float) {
    val wing = 7 * s * (0.45f + 0.55f * flap)
    val p = Path().apply {
        moveTo(o.x, o.y)
        quadraticTo(o.x + 5 * s, o.y - wing, o.x + 10 * s, o.y)
        quadraticTo(o.x + 15 * s, o.y - wing, o.x + 20 * s, o.y)
    }
    drawPath(p, Color(0xFF2B4A63), style = Stroke(2 * s, cap = StrokeCap.Round))
}

private fun DrawScope.butterfly(o: Offset, s: Float, phase: Float, wingA: Color, wingB: Color) {
    val flap = 0.2f + 0.8f * abs(sin(phase))
    translate(o.x, o.y) {
        rotate(sin(phase / 9f) * 12f, Offset.Zero) {
            drawOval(wingA, Offset(-11 * s * flap, -7 * s), Size(11 * s * flap, 10 * s))
            drawOval(wingA, Offset(0f, -7 * s), Size(11 * s * flap, 10 * s))
            drawOval(wingB, Offset(-8 * s * flap, 1 * s), Size(8 * s * flap, 7 * s))
            drawOval(wingB, Offset(0f, 1 * s), Size(8 * s * flap, 7 * s))
            drawRect(Color(0xFF2B3440), Offset(-0.7f * s, -6 * s), Size(1.4f * s, 13 * s))
        }
    }
}

private fun DrawScope.tuft(o: Offset, s: Float, sway: Float, col: Color) {
    rotate(sway, o) {
        val p = Path().apply {
            moveTo(o.x - 13 * s, o.y)
            lineTo(o.x - 8 * s, o.y - 18 * s); lineTo(o.x - 5 * s, o.y)
            lineTo(o.x - 3 * s, o.y); lineTo(o.x, o.y - 22 * s); lineTo(o.x + 3 * s, o.y)
            lineTo(o.x + 5 * s, o.y); lineTo(o.x + 9 * s, o.y - 16 * s); lineTo(o.x + 13 * s, o.y)
            close()
        }
        drawPath(p, col)
    }
}
