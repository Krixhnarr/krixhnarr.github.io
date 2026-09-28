package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.Bar
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.Chip
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PanelHead
import io.github.krixhnarr.wilddex.ui.Small
import io.github.krixhnarr.wilddex.ui.TypeGlyph
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.hex
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.ui.pulse

@Composable
fun OpsScreen(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val d = Game.daily(s)
    val missions = Game.missions()
    val streak = Game.liveStreak(s)
    val (badges, byType) = Game.achievements(s)
    val ev = Game.weeklyEvent(s)
    val evP = minOf(ev.goal, ev.progress)
    val evColor = ev.def.type?.let { hex(Dex.types.getValue(it).color) } ?: c.accent

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        ScreenTitle("Ops", "Orders · events · badges")

        Panel(Modifier.fillMaxWidth(), tint = evColor) {
            PanelHead("Weekly event") { Small("${ev.daysLeft} day${if (ev.daysLeft == 1) "" else "s"} left") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(evColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    if (ev.def.type != null) TypeGlyph(ev.def.type!!, Modifier.size(28.dp))
                    else Text(Dex.sets.first { it.id == ev.def.set }.icon, style = display(24.sp, c.hi))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(ev.def.name, style = display(17.sp, c.hi))
                    Text("${ev.def.text} · $evP/${ev.goal}", style = mono(11.sp, c.dim))
                    Bar(evP.toFloat() / ev.goal, evColor, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                ClaimButton(ev.claimed, evP >= ev.goal, "+${ev.reward}◆") { model.claimEvent() }
            }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Daily orders") { Small("resets daily") }
            for (m in missions) {
                val p = minOf(m.goal, d.progress[m.id] ?: 0)
                val claimed = d.claimed[m.id] == true
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).graphicsLayer { alpha = if (claimed) 0.55f else 1f }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("${m.text} · $p/${m.goal}", style = mono(12.sp, c.ink, FontWeight.Bold))
                        Bar(p.toFloat() / m.goal, c.accent, Modifier.fillMaxWidth().padding(top = 6.dp), height = 6.dp)
                    }
                    ClaimButton(claimed, p >= m.goal, "+${m.reward}◆") { model.claimMission(m.id) }
                }
            }
            val allClaimed = missions.all { d.claimed[it.id] == true }
            Text(
                if (allClaimed) "All clear ✓" else "All three: +${Game.ALL_CLEAR_BONUS}◆ bonus",
                style = mono(11.sp, if (allClaimed) c.good else c.dim, FontWeight.Bold), modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
            )
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Streak") { Small("best ${s.streak?.best ?: 0} days") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lit = streak > 0
                LineIcon(Icon.Streak, Modifier.size(38.dp), if (lit) Color(0xFFF0A05B) else c.dim2, 2.2f)
                Text(streak.toString().padStart(2, '0'), style = display(40.sp, if (lit) Color(0xFFE0801F) else c.dim2), modifier = Modifier.padding(horizontal = 10.dp))
                Column {
                    Text("+5◆ × streak each day", style = mono(12.sp, c.ink, FontWeight.Bold))
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in 0 until 7) Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < minOf(streak, 7)) Color(0xFFF0A05B) else c.slot))
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Affinities") { Small("owned / total") }
            for (row in Dex.typeIds.chunked(3)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in row) {
                        val ty = Dex.types.getValue(t)
                        val col = hex(ty.color)
                        val total = Dex.entries.count { Dex.typesOf(it).contains(t) }
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.13f)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeGlyph(t, Modifier.size(18.dp))
                            Column(Modifier.padding(start = 6.dp)) {
                                Text(ty.name, style = mono(11.sp, c.readable(col), FontWeight.Bold))
                                Text("${byType[t] ?: 0}/$total", style = mono(10.sp, c.dim))
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Badges") { Small("${badges.count { it.done }}/${badges.size}") }
            for (row in badges.chunked(3)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (b in row) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (b.done) c.sun.copy(alpha = 0.18f) else c.surface2)
                                .border(1.dp, if (b.done) c.gold else c.line, RoundedCornerShape(14.dp)).padding(8.dp)
                                .graphicsLayer { alpha = if (b.done) 1f else 0.6f },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LineIcon(Icon.Badge, Modifier.size(30.dp), if (b.done) Color(0xFFD99A00) else c.dim2)
                            Text(b.name, style = mono(10.sp, c.hi, FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            Text(b.desc, style = mono(9.sp, c.dim), textAlign = TextAlign.Center)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun ClaimButton(claimed: Boolean, ready: Boolean, label: String, onClick: () -> Unit) {
    val c = LocalWd.current
    if (claimed) { Chip("done"); return }
    val p = if (ready) pulse() else 0f
    ChunkyButton(label, Modifier.graphicsLayer { scaleX = 1f + p * 0.05f; scaleY = 1f + p * 0.05f }, kind = if (ready) Btn.Primary else Btn.Neutral, small = true, enabled = ready, onClick = onClick)
}
