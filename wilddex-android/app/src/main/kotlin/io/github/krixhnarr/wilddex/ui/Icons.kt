package io.github.krixhnarr.wilddex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

// Line icons from the web app (24x24 viewBox, round strokes).

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 -$r ${r}h${-(w - 2 * r)}a$r $r 0 0 1 -$r -${r}v${-(h - 2 * r)}a$r $r 0 0 1 $r -${r}Z"

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

enum class Icon(vararg val d: String) {
    Home("M3 11 12 3l9 8", "M5 9.5V21h5v-6h4v6h5V9.5"),
    Cards(rect(7f, 3f, 13f, 17f, 2f), "M4 7v12a2 2 0 0 0 2 2h10", "m13.5 8.5 1.2 2.4 2.6.4-1.9 1.8.5 2.6-2.4-1.3-2.4 1.3.5-2.6-1.9-1.8 2.6-.4Z"),
    Scan("M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4", circle(12f, 12f, 3.5f)),
    Arena("M14.5 17.5 3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2", "M9.5 17.5 21 6V3h-3L6.5 14.5M11 19l-6-6M8 16l-4 4M5 21l-2-2"),
    Orders(rect(5f, 4f, 14f, 17f, 2f), "M9 4V3h6v1M9 10l1.5 1.5L13 9M9 16h6"),
    Crate("M3 8.5 12 4l9 4.5v9L12 22l-9-4.5Z", "M3 8.5 12 13l9-4.5M12 13v9"),
    Credits("M12 2 21 9l-9 13L3 9Z", "M3 9h18M9 9l3 13 3-13M8 2.8 9 9l3-7 3 7 1-6.2"),
    Streak("M12 22c4.4 0 7-2.9 7-6.6 0-4.2-3.6-6.6-4.6-10.9-2 1.6-3 3.6-3 5.8-1-.8-1.6-1.9-1.9-3.3C7.4 9 5 11.6 5 15.4 5 19.1 7.6 22 12 22Z", "M12 22c-1.8 0-3-1.3-3-3 0-2 1.6-3 3-5 1.4 2 3 3 3 5 0 1.7-1.2 3-3 3Z"),
    Event(rect(3f, 5f, 18f, 16f, 2f), "M3 10h18M8 3v4M16 3v4", "m12 12.5.9 1.9 2.1.3-1.5 1.4.4 2.1-1.9-1-1.9 1 .4-2.1-1.5-1.4 2.1-.3Z"),
    Collection(rect(7f, 3f, 13f, 17f, 2f), "M4 7v12a2 2 0 0 0 2 2h10"),
    Music("M9 18V5l11-2v13", circle(6f, 18f, 3f), circle(17f, 16f, 3f)),
    Mute("M9 18V5l11-2v13", circle(6f, 18f, 3f), circle(17f, 16f, 3f), "M3 3l18 18"),
    Close("M6 6l12 12M18 6 6 18"),
    Speaker("M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 010 6"),
    Torch("M8 3h8v4l-2 3v11h-4V10L8 7Z", "M8 7h8M12 13v2"),
    Flip("M4 8h11l-3-3M20 16H9l3 3"),
    Badge("M12 2 20.5 7v10L12 22l-8.5-5V7Z", "m8.5 12 2.5 2.5 4.5-5"),
    Pin("M12 22s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12Z", circle(12f, 10f, 2.5f)),
}

private val iconCache = HashMap<Icon, List<Path>>()

@Composable
fun LineIcon(icon: Icon, modifier: Modifier, color: Color, strokeWidth: Float = 2f) {
    val paths = iconCache.getOrPut(icon) { icon.d.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            for (p in paths) drawPath(p, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
