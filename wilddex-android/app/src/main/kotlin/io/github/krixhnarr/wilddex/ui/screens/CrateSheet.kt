package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.core.Battle
import io.github.krixhnarr.wilddex.core.CardRecord
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Loot
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardView
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.Sheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CrateSheet(model: GameModel, free: Boolean) {
    val c = LocalWd.current
    // open the crate once, when the sheet appears
    var drop by remember { mutableStateOf<Loot.Drop?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val d = Loot.openCrate(model.state, free)
        if (d == null) failed = true else { drop = d; model.commit() }
    }
    if (failed) {
        SheetHead("Supply crate", if (free) "No crates left" else "Need ${Loot.CRATE_COST}◆")
        Actions { ChunkyButton("OK", kind = Btn.Primary) { model.close() } }
        return
    }
    val res = drop ?: return
    val tier = tierColor(res.item.r)
    var hits by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val open = hits >= 3

    SheetHead("Supply crate", "Crack it open", if (open) null else "tap the crate")
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        if (open) Canvas(Modifier.size(260.dp)) {
            val t = burst.value
            for (i in 0 until 14) rotate(i * 360f / 14 + t * 40f) {
                drawArc(Brush.radialGradient(listOf(tier.copy(alpha = 0.6f * (1 - t * 0.5f)), Color.Transparent), radius = size.minDimension / 2), -5f, 10f, true)
            }
            for (i in 0 until 24) {
                val a = i * 2 * PI / 24
                val r = size.minDimension / 2 * (0.2f + t * 0.8f)
                drawCircle(tier.copy(alpha = 1 - t), 5f * (1 - t) + 1f, center + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()))
            }
        }
        if (!open) {
            Box(
                Modifier.size(150.dp).graphicsLayer { rotationZ = shake.value * 8f; scaleX = 1f + hits * 0.05f; scaleY = 1f + hits * 0.05f }
                    .clickable(remember { MutableInteractionSource() }, null) {
                        if (hits >= 3) return@clickable
                        hits++
                        model.fx.crack(hits); model.fx.buzz(15L * hits)
                        scope.launch {
                            shake.snapTo(1f); shake.animateTo(0f, spring(0.2f, Spring.StiffnessHigh))
                        }
                        if (hits == 3) scope.launch {
                            model.fx.reveal(res.item.r); model.fx.buzz(30, 40, 80)
                            if (res.item.r >= 3) model.confetti++
                            burst.animateTo(1f, tween(1400))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFE0A100)))))
                LineIcon(Icon.Crate, Modifier.size(96.dp), Color(0xFF6B4A00), 1.6f)
            }
        } else {
            AnimatedVisibility(true, enter = scaleIn(spring(0.45f)) + fadeIn()) {
                if (res.kind == "frame") {
                    val s = model.live
                    val sample = Battle.squad(s).firstOrNull()?.let { Dex.byKey[it] } ?: Dex.entries[0]
                    CardView(sample, s.caught[sample.k] ?: CardRecord(count = 1), Modifier.width(130.dp), frame = res.item.id)
                } else Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).padding(horizontal = 26.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("OPERATOR TITLE", style = mono(10.sp, c.dim, FontWeight.Bold, 0.16f))
                    Text(res.item.name, style = display(24.sp, c.hi))
                }
            }
        }
    }
    if (!open) {
        Text("TAP ×${3 - hits}", style = mono(12.sp, c.accentDeep, FontWeight.Bold, 0.2f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        return
    }
    val t = Loot.TIERS.getValue(res.item.r)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${t.name} ${res.kind}".uppercase(), style = mono(12.sp, androidx.compose.ui.graphics.lerp(tier, c.ink, 0.25f), FontWeight.Bold, 0.16f))
        Text(res.item.name, style = display(26.sp, c.hi))
        Text(
            if (res.dupe) "Already in your locker — refunded +${Loot.DUPE_REFUND}◆." else if (res.kind == "frame") res.item.desc else "Shown on your operator ID.",
            style = mono(12.sp, c.dim), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
    }
    val s = model.live
    Actions {
        if (!res.dupe) ChunkyButton("Equip", kind = Btn.Primary) {
            model.update { if (res.kind == "frame") locker().frame = res.item.id else locker().title = res.item.id }
            model.fx.coin()
            model.say("${res.item.name.uppercase()} EQUIPPED")
            model.close()
        }
        val more = s.locker().crates > 0
        if (more || s.shards >= Loot.CRATE_COST) ChunkyButton(if (more) "Open another" else "Again · ${Loot.CRATE_COST}◆") {
            model.open(Sheet.Crate(more))
        }
        ChunkyButton("Done", kind = if (res.dupe) Btn.Primary else Btn.Neutral) { model.close() }
    }
}
