package io.github.krixhnarr.wilddex.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Entry

// 3D animal art (Microsoft Fluent Emoji, MIT) and the affinity-type glyphs.

object ArtCache {
    private val cache = HashMap<String, ImageBitmap>()
    fun get(context: android.content.Context, name: String): ImageBitmap? = cache[name] ?: try {
        context.assets.open("art/$name").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()?.also { cache[name] = it }
    } catch (e: Exception) { null }
}

enum class ArtLook { Normal, Silhouette, Gray }

@Composable
fun AnimalArt(entry: Entry, modifier: Modifier = Modifier, look: ArtLook = ArtLook.Normal, silhouette: Color = Color.Black) {
    val ctx = LocalContext.current
    val bmp = remember(entry.art) { ArtCache.get(ctx, entry.art) } ?: return
    val filter = when (look) {
        ArtLook.Normal -> null
        ArtLook.Silhouette -> ColorFilter.tint(silhouette)
        ArtLook.Gray -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    Image(bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit, colorFilter = filter)
}

// ---- affinity glyphs: the web app's 24x24 SVG snippets, drawn with Canvas
private class GlyphShape(val path: Path, val fill: Boolean)

private val glyphCache = HashMap<String, List<GlyphShape>>()

private fun parseGlyph(svg: String): List<GlyphShape> {
    val out = mutableListOf<GlyphShape>()
    val tag = Regex("<(path|circle)([^>]*)/>")
    fun attr(a: String, name: String) = Regex("""\b$name="([^"]*)"""").find(a)?.groupValues?.get(1)
    for (m in tag.findAll(svg)) {
        val (kind, attrs) = m.destructured
        val fill = attr(attrs, "class") == "fill"
        val path = if (kind == "path") PathParser().parsePathString(attr(attrs, "d") ?: continue).toPath() else Path().apply {
            val cx = attr(attrs, "cx")!!.toFloat(); val cy = attr(attrs, "cy")!!.toFloat(); val r = attr(attrs, "r")!!.toFloat()
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r))
        }
        out += GlyphShape(path, fill)
    }
    return out
}

@Composable
fun TypeGlyph(type: String, modifier: Modifier, color: Color? = null) {
    val c = LocalWd.current
    val t = Dex.types.getValue(type)
    val col = color ?: c.readable(hex(t.color))
    val shapes = glyphCache.getOrPut(type) { parseGlyph(t.svg) }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, pivot = Offset.Zero) {
            for (sh in shapes) {
                if (sh.fill) drawPath(sh.path, col, style = Fill)
                else drawPath(sh.path, col, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
