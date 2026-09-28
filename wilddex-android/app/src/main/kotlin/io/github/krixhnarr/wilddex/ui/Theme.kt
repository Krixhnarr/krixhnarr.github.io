package io.github.krixhnarr.wilddex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.R

// Design system after DESIGN.md (Figma-inspired): a monochrome frame — white
// canvas, black ink, pill buttons, hairline cards, no shadows — interrupted by
// oversized pastel colour blocks that carry the story. Dark mode is the same
// system on the inverse canvas (black frame, white ink); the pastel blocks keep
// black ink in both.

@Immutable
data class WdColors(
    val night: Boolean,
    /** Page background. */
    val canvas: Color,
    /** All text and the primary (selected / main action) surface. */
    val ink: Color,
    /** Text on an [ink] surface. */
    val onInk: Color,
    /** Off-white tiles, icon buttons, slots. */
    val surfaceSoft: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    /** Disabled text and empty states only — never body copy. */
    val muted: Color,
    val scrim: Color = Color(0x99000000),
) {
    // pastel colour blocks — identical in light and dark
    val lime = Color(0xFFDCEEB1)
    val lilac = Color(0xFFC5B0F4)
    val cream = Color(0xFFF4ECD6)
    val pink = Color(0xFFEFD4D4)
    val mint = Color(0xFFC8E6CD)
    val coral = Color(0xFFF3C9B6)
    val navy = Color(0xFF1F1D3D)
    /** Ink on a pastel block. */
    val blockInk = Color(0xFF000000)
    /** Single-shot promo accent — one claimable reward / promo per screen. */
    val magenta = Color(0xFFFF3D8B)
    val success = Color(0xFF1EA64A)

    /** Affinity-type colours are game data; keep them readable against the canvas. */
    fun readable(c: Color): Color = if (night) lerp(c, Color.White, 0.18f) else lerp(c, Color.Black, 0.35f)
}

val LightColors = WdColors(
    night = false,
    canvas = Color(0xFFFFFFFF), ink = Color(0xFF000000), onInk = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF7F7F5), hairline = Color(0xFFE6E6E6), hairlineSoft = Color(0xFFF1F1F1), muted = Color(0xFF9E9E9E),
)

val DarkColors = WdColors(
    night = true,
    canvas = Color(0xFF000000), ink = Color(0xFFFFFFFF), onInk = Color(0xFF000000),
    surfaceSoft = Color(0xFF161616), hairline = Color(0xFF2B2B2B), hairlineSoft = Color(0xFF1C1C1C), muted = Color(0xFF6E6E6E),
)

val LocalWd = staticCompositionLocalOf { LightColors }

/** Pastel blocks in rotation, for screens that need "the next" block. */
val WdColors.blocks: List<Color> get() = listOf(lime, lilac, cream, mint, pink, coral)

// ---------------------------------------------------------------- type
// Inter (variable) stands in for figmaSans, JetBrains Mono for figmaMono,
// as DESIGN.md suggests. Weights follow the guide: 320 330 340 480 540 700.

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Sans = FontFamily(inter(320), inter(330), inter(340), inter(400), inter(480), inter(540), inter(700))
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Display / hero headline: light weight, tight tracking (-2% of size). */
fun display(size: TextUnit, color: Color) = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight(340), fontSize = size, color = color,
    letterSpacing = (-0.02).em, lineHeight = size * 1.04, lineHeightStyle = tight,
)

/** Titles inside colour blocks and cards. */
fun headline(size: TextUnit, color: Color) = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight(540), fontSize = size, color = color,
    letterSpacing = (-0.01).em, lineHeight = size * 1.3,
)

/** Bold card title (the collectible card name, stat numbers). */
fun title(size: TextUnit, color: Color) = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight(700), fontSize = size, color = color, lineHeight = size * 1.3,
)

/** Body copy: hierarchy comes from weight, never from grey. */
fun body(size: TextUnit, color: Color, weight: Int = 330) = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight(weight), fontSize = size, color = color,
    letterSpacing = (-0.008).em, lineHeight = size * 1.42,
)

/** Pill buttons and emphasised links. */
fun label(size: TextUnit, color: Color) = TextStyle(
    fontFamily = Sans, fontWeight = FontWeight(480), fontSize = size, color = color, letterSpacing = (-0.005).em,
)

/** Mono eyebrow / caption — taxonomy only, always uppercase with positive tracking. */
fun caption(size: TextUnit, color: Color, bold: Boolean = false) = TextStyle(
    fontFamily = Mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size, color = color,
    letterSpacing = 0.05.em,
)

fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))

// shape scale
object R8 { val xs = 2.dp; val sm = 6.dp; val md = 8.dp; val lg = 24.dp; val xl = 32.dp }

@Composable
fun WildDexTheme(night: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWd provides if (night) DarkColors else LightColors, content = content)
}

