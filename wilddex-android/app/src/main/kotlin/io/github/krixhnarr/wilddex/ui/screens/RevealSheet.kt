package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.AnimalArt
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardShape
import io.github.krixhnarr.wilddex.ui.CardView
import io.github.krixhnarr.wilddex.ui.Chip
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.DotText
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.Stars
import io.github.krixhnarr.wilddex.ui.XpBar
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.ui.typeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GRADE_COLOR = mapOf("S" to Color(0xFFF2B51D), "A" to Color(0xFF4CC764), "B" to Color(0xFF2F9BEA), "C" to Color(0xFF8DA0B0))
private val PULL = mapOf(1 to "▲ New card", 2 to "▲ Uncommon pull", 3 to "◆ Rare pull", 4 to "★ Legendary pull")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealSheet(model: GameModel, sheet: Sheet.Reveal) {
    val c = LocalWd.current
    val r = sheet.result
    val e = r.entry
    val s = model.live
    val scope = rememberCoroutineScope()
    val flip = remember(sheet) { Animatable(if (r.isNew) 180f else 0f) }
    val shake = remember(sheet) { Animatable(0f) }
    val burst = remember(sheet) { Animatable(0f) }
    val stamp = remember(sheet) { Animatable(0f) }
    var started by remember(sheet) { mutableStateOf(false) }
    var revealing by remember(sheet) { mutableStateOf(false) }

    fun startDetail() {
        if (started) return
        started = true
        scope.launch {
            if (r.holoNew || (r.isNew && e.r == 4) || r.setDone) { delay(350); model.confetti++ }
        }
        scope.launch {
            if (r.grade != null) { delay(250); model.fx.grade(r.grade!!.grade); stamp.animateTo(1f, spring(0.4f, Spring.StiffnessMediumLow)) }
        }
        if (r.holoNew) scope.launch { delay(500); model.fx.holo(); model.fx.buzz(20, 30, 20, 30, 20, 30, 160) }
        if (r.isNew && s.settings.voice) scope.launch { delay(400); model.fx.speak("${e.n}. ${e.t}") }
        scope.launch {
            delay(900)
            when {
                r.missionsDone.isNotEmpty() || r.eventDone -> model.say(if (r.eventDone) "WEEKLY EVENT COMPLETE · CLAIM IN OPS" else "MISSION COMPLETE · CLAIM IN OPS")
                r.setDone -> model.say("SECTOR COMPLETE")
            }
        }
    }

    fun reveal() {
        if (revealing || !r.isNew) return
        revealing = true
        scope.launch {
            // rarer cards hold the tension a little longer before flipping
            val hold = listOf(0L, 0L, 120L, 450L, 900L)[e.r]
            if (hold > 0) {
                model.fx.charge()
                val until = System.currentTimeMillis() + hold
                while (System.currentTimeMillis() < until) { shake.animateTo(1f, tween(40)); shake.animateTo(-1f, tween(40)) }
                shake.snapTo(0f)
            }
            flip.animateTo(0f, tween(520))
            model.fx.reveal(e.r)
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(1200)) }
            delay(if (e.r >= 3) 300 else 0)
            startDetail()
        }
    }

    LaunchedEffect(sheet) {
        if (r.isNew) model.fx.charge()
        else {
            model.fx.again()
            if (r.lvAfter > r.lvBefore) launch { burst.animateTo(1f, tween(1200)) }
            startDetail()
        }
    }

    // ---- stage
    val tc = typeColor(e)
    val inf = rememberInfiniteTransition(label = "stage")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    val glow by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    Box(
        Modifier.fillMaxWidth().height(330.dp)
            .clickable(remember { MutableInteractionSource() }, null, enabled = r.isNew && !revealing) { reveal() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(340.dp).rotate(spin)) {
            val a = if (flip.value > 90f) 0.25f * glow else 0.45f
            for (i in 0 until 14) rotate(i * 360f / 14) {
                drawArc(Brush.radialGradient(listOf(tc.copy(alpha = a), Color.Transparent), radius = size.minDimension / 2), -5f, 10f, true)
            }
        }
        if (burst.value in 0.001f..0.999f) Canvas(Modifier.size(320.dp)) {
            val t = burst.value
            val col = if (r.holoNew) Color(0xFFFF8AD8) else if (e.r >= 3) Color(0xFFFFC83D) else tc
            for (i in 0 until 28) {
                val ang = i * 2 * PI / 28
                val rad = size.minDimension / 2 * (0.25f + t * 0.75f)
                drawCircle(col.copy(alpha = 1 - t), 6f * (1 - t) + 1.5f, center + Offset((cos(ang) * rad).toFloat(), (sin(ang) * rad).toFloat()))
            }
            drawCircle(Color.White.copy(alpha = (1 - t * 3).coerceAtLeast(0f) * 0.8f), size.minDimension / 2 * t)
        }
        Box(
            Modifier.width(200.dp).graphicsLayer {
                rotationY = flip.value
                rotationZ = shake.value * 2.5f
                translationX = shake.value * 4f
                cameraDistance = 16f * density
            },
        ) {
            if (flip.value > 90f) CardBack(Modifier.fillMaxWidth().graphicsLayer { rotationY = 180f }, glow)
            else CardView(e, s.caught[e.k], frame = model.frame())
        }
        if (r.grade != null && stamp.value > 0f) {
            val g = r.grade!!
            val col = GRADE_COLOR.getValue(g.grade)
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 8.dp)
                    .graphicsLayer { val k = 2.4f - 1.4f * stamp.value; scaleX = k; scaleY = k; alpha = stamp.value.coerceIn(0f, 1f); rotationZ = -12f }
                    .size(68.dp).clip(CircleShape).background(col.copy(alpha = 0.18f)).border(3.dp, col, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("SYNC", style = mono(8.sp, col, FontWeight.Bold, 0.2f))
                Text(g.grade, style = display(30.sp, col))
            }
        }
    }

    if (r.isNew && !started) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DotText(if (revealing) "DECRYPTING" else "SEALED CARD", 16.dp, c.accentDeep)
            Actions { ChunkyButton("Decrypt card", kind = Btn.Primary) { reveal() } }
        }
        return
    }

    AnimatedVisibility(started, enter = fadeIn(tween(300)) + expandVertically()) {
        Column {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.holoNew) Chip("✦ Holo variant", holo = true)
                if (r.isNew) Chip(PULL.getValue(e.r), highlight = true) else Chip("● Sighting logged ×${r.count}")
            }
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (g in r.gains) Chip(g.text, highlight = g.highlight && !g.holo, holo = g.holo)
            }
            XpBar(r.xpBefore, r.xpAfter)
            if (!r.isNew && r.lvAfter > r.lvBefore) PowerUp(r.entry, r.lvBefore, r.lvAfter)
            CardDetail(model, e, typing = true)
            Actions {
                ChunkyButton("Play audio", icon = { LineIcon(Icon.Speaker, Modifier.size(18.dp), c.hi) }) { model.fx.speak("${e.n}. ${e.t}") }
                ChunkyButton("Continue", kind = Btn.Primary) { model.close() }
            }
            Text(
                "wrong animal? not a ${e.n.lowercase()} · undo",
                style = mono(11.sp, c.dim).copy(textDecoration = TextDecoration.Underline),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { model.rollback(sheet) }.padding(8.dp),
            )
        }
    }
}

@Composable
private fun PowerUp(e: io.github.krixhnarr.wilddex.core.Entry, before: Int, after: Int) {
    val c = LocalWd.current
    val a = Game.statsFor(e, before)
    val b = Game.statsFor(e, after)
    Panel(Modifier.fillMaxWidth().padding(top = 10.dp), tint = c.gold, border = c.gold) {
        Text("POWER UP  LV $before → $after", style = display(16.sp, c.hi), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Stars(Game.stars(before), 12.dp, c.line2)
            Text("  ▸  ", style = mono(12.sp, c.dim))
            Stars(Game.stars(after), 14.dp, c.line2)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in Game.STAT_KEYS) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(k.uppercase(), style = mono(9.sp, c.dim, FontWeight.Bold))
                Text("${b[k]}", style = display(16.sp, c.hi))
                Text("+${b[k] - a[k]}", style = mono(10.sp, c.good, FontWeight.Bold))
            }
        }
    }
}

/** The sealed back of a card, shown before it is decrypted. */
@Composable
fun CardBack(modifier: Modifier, glow: Float = 1f) {
    Box(
        modifier.aspectRatio(0.7f).clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B3A6B), Color(0xFF0E1F3F), Color(0xFF1B3A6B))))
            .border(3.dp, Color(0xFFFFC83D).copy(alpha = 0.5f + glow * 0.5f), CardShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 8
            var y = 0f
            while (y < size.height) {
                var x = if (((y / step).toInt()) % 2 == 0) 0f else step / 2
                while (x < size.width) { drawCircle(Color.White.copy(alpha = 0.06f), step * 0.14f, Offset(x, y)); x += step }
                y += step * 0.6f
            }
            drawCircle(Color(0xFFFFC83D).copy(alpha = 0.18f * glow), size.width * 0.34f)
            drawCircle(Color(0xFFFFC83D).copy(alpha = 0.8f), size.width * 0.3f, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LineIcon(Icon.Scan, Modifier.size(54.dp), Color(0xFFFFC83D), 2.2f)
            Text("WILDDEX", style = display(16.sp, Color.White, 0.2f), modifier = Modifier.padding(top = 8.dp))
            Text("TAP TO DECRYPT", style = mono(9.sp, Color(0xFFFFE08A), FontWeight.Bold, 0.2f))
        }
    }
}
