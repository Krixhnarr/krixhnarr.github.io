package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import io.github.krixhnarr.wilddex.ui.Block
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardView
import io.github.krixhnarr.wilddex.ui.Eyebrow
import io.github.krixhnarr.wilddex.ui.GhostCard
import io.github.krixhnarr.wilddex.ui.HelpButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.IconCircle
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PillButton
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.body
import io.github.krixhnarr.wilddex.ui.caption
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.headline
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("WildDex · field log", modifier = Modifier.weight(1f))
            MusicButton(model)
            Spacer(Modifier.width(8.dp))
            HelpButton { model.fx.click(); model.open(Sheet.Intro) }
        }
        Spacer(Modifier.height(16.dp))

        // hero: the one colour block on this screen
        Block(c.lime, Modifier.fillMaxWidth(), PaddingValues(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(if (n == 0) "Scan your first animal." else "Find something wild today.", style = display(38.sp, c.blockInk))
                    Text(
                        "$n of ${Dex.total} animals found",
                        style = body(16.sp, c.blockInk), modifier = Modifier.padding(top = 14.dp),
                    )
                }
                val mod = Modifier.width(118.dp)
                if (recent != null) CardView(
                    Dex.byKey.getValue(recent), s.caught[recent], frame = model.frame(),
                    modifier = mod.clickable { model.fx.click(); model.open(Sheet.Card(recent)) },
                ) else GhostCard(mod)
            }
            Spacer(Modifier.height(20.dp))
            PillButton(
                "Scan an animal", Modifier.fillMaxWidth(), kind = Btn.Primary, onBlock = true,
                icon = { col -> LineIcon(Icon.Scan, Modifier.size(20.dp), col, 2f) },
            ) { model.go(Tab.Scan) }
        }

        Text("Today", style = headline(22.sp, c.ink), modifier = Modifier.padding(top = 32.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rival != null) Tile(Modifier.weight(1f), "Rival", rival.name, if (rival.won) "Defeated ✓" else "Win +${Battle.RIVAL_REWARD.credits}◆", hot = !rival.won) { model.go(Tab.Arena) }
            else Tile(Modifier.weight(1f), "Arena", "Locked", "Catch a card first") { model.go(Tab.Arena) }
            Tile(Modifier.weight(1f), "Orders", "$done/${missions.size}", if (ready > 0) "$ready reward${if (ready > 1) "s" else ""} ready" else "Daily missions", hot = ready > 0) { model.go(Tab.Ops) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (crates > 0) Tile(Modifier.weight(1f), "Crates", "$crates", "Tap to open", hot = true) { model.fx.click(); model.open(Sheet.Crate(true)) }
            else Tile(Modifier.weight(1f), "Crates", "0", "${Loot.CRATE_COST}◆ each") { model.go(Tab.Id) }
            Tile(Modifier.weight(1f), "Streak", "${streak} day${if (streak == 1) "" else "s"}", "Best ${s.streak?.best ?: 0}", hot = streak > 0 && s.streak?.last != Game.today()) { model.go(Tab.Ops) }
        }
        Spacer(Modifier.height(10.dp))
        Raised(Modifier.fillMaxWidth(), onClick = { model.go(Tab.Ops) }) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Weekly event · ${ev.daysLeft}d left", modifier = Modifier.weight(1f))
                    if (evP >= ev.goal && !ev.claimed) HotDot()
                }
                Text(ev.def.name, style = headline(20.sp, c.ink), modifier = Modifier.padding(top = 8.dp))
                Text(if (ev.claimed) "Complete ✓" else "${ev.def.text} · $evP/${ev.goal}", style = body(15.sp, c.ink), modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                Bar(evP.toFloat() / ev.goal)
            }
        }
        Spacer(Modifier.height(10.dp))
        Raised(Modifier.fillMaxWidth(), onClick = { model.go(Tab.Cards) }) {
            Column(Modifier.padding(20.dp)) {
                Eyebrow("Collection")
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("$n", style = display(44.sp, c.ink))
                    Text(" / ${Dex.total}", style = body(18.sp, c.ink), modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("$holos holo · $sectors sectors complete", style = body(15.sp, c.ink), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                Bar(n.toFloat() / Dex.total)
            }
        }
    }
}

@Composable
private fun HotDot() {
    val c = LocalWd.current
    val p = pulse()
    Box(Modifier.size(9.dp).clip(CircleShape).background(c.magenta.copy(alpha = 0.55f + p * 0.45f)))
}

/** template-card: surface-soft tile with an eyebrow, a big value and a line of body. */
@Composable
private fun Tile(modifier: Modifier, label: String, value: String, sub: String, hot: Boolean = false, onClick: () -> Unit) {
    val c = LocalWd.current
    Raised(modifier, shape = RoundedCornerShape(16.dp), color = c.surfaceSoft, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(label, modifier = Modifier.weight(1f))
                if (hot) HotDot()
            }
            Text(value, style = headline(22.sp, c.ink), modifier = Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = body(14.sp, c.ink, if (hot) 480 else 330), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MusicButton(model: GameModel) {
    val on = model.live.settings.music
    IconCircle(if (on) Icon.Music else Icon.Mute, if (on) "Music on" else "Music off") {
        model.update { settings.music = !settings.music }
        model.say(if (model.state.settings.music) "MUSIC ON" else "MUSIC OFF")
    }
}

