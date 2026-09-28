package io.github.krixhnarr.wilddex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.R

// "Field Expedition" palette: bright outdoor colours by day, a navy night sky
// after 7pm. Every screen reads colours from here so both skies stay in sync.

@Immutable
data class WdColors(
    val night: Boolean,
    val skyTop: Color, val skyBottom: Color,
    val hill1: Color, val hill2: Color, val hill3: Color, val dirt: Color,
    val surface: Color, val surface2: Color, val slot: Color, val slotHi: Color,
    val ink: Color, val hi: Color, val dim: Color, val dim2: Color,
    val line: Color, val line2: Color, val press: Color, val barBg: Color,
    val accent: Color, val accentDeep: Color, val accentSoft: Color,
    val sun: Color = Color(0xFFFFC83D), val sun2: Color = Color(0xFFFFB01F), val sunPress: Color = Color(0xFFD18A00), val onSun: Color = Color(0xFF3F2B00),
    val coral: Color = Color(0xFFFF7A59), val grass: Color = Color(0xFF4CC764), val gold: Color = Color(0xFFF2B51D),
    val good: Color = Color(0xFF1E9B48), val bad: Color = Color(0xFFC8432A),
) {
    /** Type colours are mixed toward the ink so they stay readable on light surfaces. */
    fun readable(c: Color): Color = if (night) lerp(c, Color.White, 0.12f) else lerp(c, Color(0xFF1E3346), 0.28f)
}

val DayColors = WdColors(
    night = false,
    skyTop = Color(0xFF62C4FF), skyBottom = Color(0xFFD4F1FF),
    hill1 = Color(0xFF9BE37A), hill2 = Color(0xFF5DCB63), hill3 = Color(0xFF3FAE55), dirt = Color(0xFF2E8A42),
    surface = Color.White, surface2 = Color(0xFFEEF5FA), slot = Color(0xFFE3ECF3), slotHi = Color(0xFFD6E3EC),
    ink = Color(0xFF1E3346), hi = Color(0xFF13283A), dim = Color(0xFF5B7083), dim2 = Color(0xFF8DA0B0),
    line = Color(0x1F1E3346), line2 = Color(0x3D1E3346), press = Color(0xFFC3D3DF), barBg = Color(0xFFE2ECF3),
    accent = Color(0xFF2F9BEA), accentDeep = Color(0xFF1766A8), accentSoft = Color(0xFFBFE3FB),
)

val NightColors = WdColors(
    night = true,
    skyTop = Color(0xFF0B1633), skyBottom = Color(0xFF26306A),
    hill1 = Color(0xFF1F4B45), hill2 = Color(0xFF173B38), hill3 = Color(0xFF112E2D), dirt = Color(0xFF0B201F),
    surface = Color(0xFF1B274B), surface2 = Color(0xFF243259), slot = Color(0xFF2A3963), slotHi = Color(0xFF33436F),
    ink = Color(0xFFE6EEFB), hi = Color.White, dim = Color(0xFFA5B4D0), dim2 = Color(0xFF7486A8),
    line = Color(0x24C8DCFF), line2 = Color(0x47C8DCFF), press = Color(0xFF0C1430), barBg = Color(0x59000000),
    accent = Color(0xFF4FB3FF), accentDeep = Color(0xFF7CC8FF), accentSoft = Color(0xFF2B4E7E),
)

val LocalWd = staticCompositionLocalOf { DayColors }

val Display = FontFamily(Font(R.font.russo_one))
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

fun display(size: TextUnit, color: Color, spacing: Float = 0.03f) =
    TextStyle(fontFamily = Display, fontSize = size, color = color, letterSpacing = (size.value * spacing).sp)

fun mono(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontFamily = Mono, fontSize = size, color = color, fontWeight = weight, letterSpacing = (size.value * spacing).sp)

fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))

@Composable
fun WildDexTheme(night: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWd provides if (night) NightColors else DayColors, content = content)
}
