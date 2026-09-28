package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.Tab
import io.github.krixhnarr.wilddex.core.Battle
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.Loot
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.Bar
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardView
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.DotText
import io.github.krixhnarr.wilddex.ui.GhostCard
import io.github.krixhnarr.wilddex.ui.HelpButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.hex
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.ui.pulse

@Composable
fun HomeScreen(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val keys = s.ownedKeys()
    val n = keys.size
    val recent = keys.maxByOrNull { s.caught.getValue(it).last }
    val missions = Game.missions()
    val d = Game.daily(s)
    val done = missions.count { (d.progress[it.id] ?: 0) >= it.goal }
    val ready = Game.unclaimedMissions(s)
    val rival = if (n > 0) Battle.dailyRival(s) else null
    val ev = Game.weeklyEvent(s)
    val evP = minOf(ev.goal, ev.progress)
    val streak = Game.liveStreak(s)
    val crates = s.locker?.crates ?: 0
    val holos = keys.count { s.caught.getValue(it).holo != null }
    val sectors = Dex.sets.count { set -> set.keys.all { s.caught.containsKey(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DotText("WILDDEX", 22.dp, c.hi, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            MusicButton(model)
            Spacer(Modifier.width(8.dp))
            HelpButton { model.fx.click(); model.open(Sheet.Intro) }
        }

        // hero: newest card on spinning sun rays
        Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
            val inf = rememberInfiniteTransition(label = "hero")
            val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "spin")
            val bob by inf.animateFloat(-5f, 5f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "bob")
            val rays = c.sun.copy(alpha = if (c.night) 0.16f else 0.34f)
            Canvas(Modifier.size(300.dp).rotate(spin)) {
                for (i in 0 until 16) rotate(i * 22.5f) {
                    drawArc(Brush.radialGradient(listOf(rays, Color.Transparent), radius = size.minDimension / 2), -5f, 10f, true)
                }
            }
            val mod = Modifier.width(160.dp).graphicsLayer { translationY = bob * density; rotationZ = bob * 0.4f }
            if (recent != null) {
                CardView(
                    Dex.byKey.getValue(recent), s.caught[recent], frame = model.frame(),
                    modifier = mod.clickable { model.fx.click(); model.open(Sheet.Card(recent)) },
                )
            } else GhostCard(mod)
        }

        ChunkyButton("Scan an animal", Modifier.fillMaxWidth(), kind = Btn.Primary, icon = { LineIcon(Icon.Scan, Modifier.size(22.dp), c.onSun, 2.4f) }) {
            model.go(Tab.Scan)
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rival != null) Tile(Modifier.weight(1f), Icon.Arena, "Rival", rival.name, if (rival.won) "Defeated ✓" else "Win +${Battle.RIVAL_REWARD.credits}◆", c.coral, hot = !rival.won) { model.go(Tab.Arena) }
            else Tile(Modifier.weight(1f), Icon.Arena, "Arena", "Locked", "Catch a card first", c.coral) { model.go(Tab.Arena) }
            Tile(Modifier.weight(1f), Icon.Orders, "Orders", "$done/${missions.size}", if (ready > 0) "$ready reward${if (ready > 1) "s" else ""} ready!" else "Daily missions", c.accent, hot = ready > 0) { model.go(Tab.Ops) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (crates > 0) Tile(Modifier.weight(1f), Icon.Crate, "Crates", "$crates", "Tap to open!", c.gold, hot = true) { model.fx.click(); model.open(Sheet.Crate(true)) }
            else Tile(Modifier.weight(1f), Icon.Crate, "Crates", "0", "${Loot.CRATE_COST}◆ each", c.gold) { model.go(Tab.Id) }
            Tile(Modifier.weight(1f), Icon.Streak, "Streak", "${streak}d", "best ${s.streak?.best ?: 0}", Color(0xFFF0A05B), hot = streak > 0 && s.streak?.last != Game.today()) { model.go(Tab.Ops) }
        }
        Spacer(Modifier.height(10.dp))
        val evColor = ev.def.type?.let { hex(Dex.types.getValue(it).color) } ?: c.accent
        Tile(Modifier.fillMaxWidth(), Icon.Event, ev.def.name, "$evP/${ev.goal}", if (ev.claimed) "Complete ✓" else "${ev.daysLeft}d left", evColor, hot = evP >= ev.goal && !ev.claimed, bar = evP.toFloat() / ev.goal) { model.go(Tab.Ops) }
        Spacer(Modifier.height(10.dp))
        Tile(Modifier.fillMaxWidth(), Icon.Collection, "Collection", "$n/${Dex.total}", "$holos holo · $sectors sectors", Color(0xFF2BB5A0), bar = n.toFloat() / Dex.total) { model.go(Tab.Cards) }
    }
}

@Composable
private fun Tile(
    modifier: Modifier, icon: Icon, label: String, value: String, sub: String, tint: Color,
    hot: Boolean = false, bar: Float? = null, onClick: () -> Unit,
) {
    val c = LocalWd.current
    val p = if (hot) pulse() else 0f
    Raised(modifier.graphicsLayer { scaleX = 1f + p * 0.015f; scaleY = 1f + p * 0.015f }, shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    LineIcon(icon, Modifier.size(19.dp), c.readable(tint))
                }
                Text(label.uppercase(), style = mono(10.sp, c.dim, FontWeight.Bold, 0.1f), modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hot) Box(Modifier.size(9.dp).graphicsLayer { alpha = 0.6f + p * 0.4f }.clip(CircleShape).background(c.coral))
            }
            Text(value, style = display(22.sp, c.hi), modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (bar != null) Bar(bar, tint, Modifier.fillMaxWidth().padding(vertical = 5.dp), height = 7.dp)
            Text(sub, style = mono(11.sp, if (hot) c.readable(tint) else c.dim, if (hot) FontWeight.Bold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MusicButton(model: GameModel) {
    val c = LocalWd.current
    val on = model.live.settings.music
    Raised(Modifier.size(34.dp), shape = RoundedCornerShape(50), edge = 2.dp, onClick = {
        model.update { settings.music = !settings.music }
        model.say(if (model.state.settings.music) "MUSIC ON" else "MUSIC OFF")
    }) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            LineIcon(if (on) Icon.Music else Icon.Mute, Modifier.size(17.dp), if (on) c.accentDeep else c.dim2)
        }
    }
}
