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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.Tab
import io.github.krixhnarr.wilddex.core.Battle
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Entry
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.AnimalArt
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.Chip
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.compositeOver
import io.github.krixhnarr.wilddex.ui.DotText
import io.github.krixhnarr.wilddex.ui.HOLO
import io.github.krixhnarr.wilddex.ui.HelpButton
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PanelHead
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.Small
import io.github.krixhnarr.wilddex.ui.TypeGlyph
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.hex
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.ui.typeColor
import kotlin.math.roundToInt

@Composable
fun ArenaScreen(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val b = s.battle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        ScreenTitle("Arena", "W ${b.wins} · L ${b.losses}")
        if (s.ownedKeys().isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                PanelHead("No squad yet")
                Text("Catch your first card to unlock battles.", style = mono(12.sp, c.ink))
                Spacer(Modifier.height(12.dp))
                ChunkyButton("Go scan", kind = Btn.Primary) { model.go(Tab.Scan) }
            }
            return@Column
        }
        val r = Battle.dailyRival(s)
        val keys = Battle.squad(s)
        val sims = Battle.simsToday(s)
        val pwr = keys.sumOf { Battle.pwrOf(s, it) }.roundToInt()
        val rivalLv = kotlin.math.floor(r.lvs.sum().toDouble() / r.lvs.size + 0.5).toInt()

        Panel(Modifier.fillMaxWidth(), tint = c.coral, border = if (r.won) c.line else c.coral.copy(alpha = 0.6f)) {
            PanelHead("Daily rival") { Small(if (r.won) "defeated ✓ · new rival tomorrow" else "+${Battle.RIVAL_REWARD.credits}◆ · +${Battle.RIVAL_REWARD.xp} XP") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotText(r.name, 20.dp, c.hi, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Chip("LV $rivalLv", highlight = true)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.keys.forEachIndexed { i, k -> FighterChip(Modifier.weight(1f), Dex.byKey.getValue(k), r.lvs[i], false) }
            }
            ChunkyButton(
                if (r.won) "Rematch for XP" else if (r.tries > 0) "Try again" else "Challenge", Modifier.fillMaxWidth(),
                kind = if (r.won) Btn.Neutral else Btn.Primary,
            ) { model.open(Sheet.Fight("rival")) }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Your squad") { Small("PWR $pwr") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until Battle.SQUAD_SIZE) {
                    val k = keys.getOrNull(i)
                    if (k == null) {
                        Column(
                            Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2).border(1.5.dp, c.line2, RoundedCornerShape(16.dp)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("+", style = display(26.sp, c.dim2))
                            Text("scan more animals", style = mono(9.sp, c.dim), textAlign = TextAlign.Center)
                        }
                    } else SquadSlot(model, Modifier.weight(1f), i, k)
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                ChunkyButton("Auto-pick", small = true) { model.fx.click(); model.update { battle().squad = Battle.bestSquad(this).toMutableList() } }
                Text("Slot 1 fights first", style = mono(11.sp, c.dim), modifier = Modifier.padding(start = 10.dp))
            }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Wild signals") { Small("${sims.paid}/${Battle.SIM_PAID} today") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("+${Battle.SIM_REWARD.credits}◆", highlight = true)
                Chip("+${Battle.SIM_REWARD.xp} XP", highlight = true)
                Text("per win", style = mono(11.sp, c.dim))
            }
            Spacer(Modifier.height(10.dp))
            ChunkyButton("Find a battle", Modifier.fillMaxWidth()) { model.open(Sheet.Fight("wild")) }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Type matchups") { HelpButton { model.fx.click(); model.open(Sheet.Help("types")) } }
            for (t in Dex.typeIds) {
                val ty = Dex.types.getValue(t)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeGlyph(t, Modifier.size(18.dp))
                    Text(ty.name, style = mono(12.sp, c.readable(hex(ty.color)), FontWeight.Bold), modifier = Modifier.padding(start = 6.dp).weight(1f))
                    Text("beats", style = mono(10.sp, c.dim), modifier = Modifier.padding(end = 6.dp))
                    for (x in Battle.STRONG.getValue(t)) {
                        Box(Modifier.padding(start = 4.dp).size(26.dp).clip(CircleShape).background(hex(Dex.types.getValue(x).color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            TypeGlyph(x, Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PanelHead("Record")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((v, l) in listOf(b.wins to "Wins", b.losses to "Losses", b.rivals to "Rivals beaten")) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pad3(v), style = display(18.sp, c.hi))
                        Text(l.uppercase(), style = mono(9.sp, c.dim, FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun FighterChip(modifier: Modifier, e: Entry, lv: Int, holo: Boolean) {
    val c = LocalWd.current
    val tc = typeColor(e)
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(tc.copy(alpha = 0.15f))
            .border(if (holo) 2.dp else 1.dp, if (holo) Brush.sweepGradient(HOLO + HOLO.first()) else Brush.linearGradient(listOf(c.line, c.line)), RoundedCornerShape(14.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimalArt(e, Modifier.size(44.dp))
        Text("LV$lv", style = mono(10.sp, c.hi, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { for (t in Dex.typesOf(e)) TypeGlyph(t, Modifier.size(13.dp)) }
    }
}

@Composable
private fun SquadSlot(model: GameModel, modifier: Modifier, i: Int, k: String) {
    val c = LocalWd.current
    val s = model.live
    val e = Dex.byKey.getValue(k)
    val rec = s.caught.getValue(k)
    val lv = Game.levelFor(rec.count)
    Raised(modifier, color = typeColor(e).copy(alpha = 0.16f).compositeOver(c.surface), onClick = { model.fx.click(); model.open(Sheet.Squad(i)) }) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                Text("${i + 1}", style = display(12.sp, Color.White), modifier = Modifier.clip(CircleShape).background(c.accent).padding(horizontal = 7.dp, vertical = 1.dp))
                Spacer(Modifier.weight(1f))
                if (rec.holo != null) Text("HOLO", style = mono(8.sp, Color(0xFF1E1E22), FontWeight.Bold), modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(HOLO)).padding(horizontal = 3.dp))
            }
            AnimalArt(e, Modifier.size(56.dp).padding(top = 2.dp))
            Text(e.n, style = display(12.sp, c.hi), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 2.dp)) { for (t in Dex.typesOf(e)) TypeGlyph(t, Modifier.size(13.dp)) }
            Text("LV $lv · PWR ${Battle.pwrOf(s, k).roundToInt()}", style = mono(9.sp, c.dim, FontWeight.Bold))
        }
    }
}

@Composable
fun SquadSheet(model: GameModel, slot: Int) {
    val c = LocalWd.current
    val s = model.live
    val keys = Battle.squad(s)
    val list = s.ownedKeys().sortedByDescending { Battle.pwrOf(s, it) }
    SheetHead("Squad slot ${slot + 1}", "Pick a card", "sorted by power")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in list) {
            val e = Dex.byKey.getValue(k)
            val rec = s.caught.getValue(k)
            val at = keys.indexOf(k)
            Raised(
                Modifier.fillMaxWidth(), color = if (at == slot) c.accentSoft else c.surface,
                onClick = {
                    val next = keys.toMutableList()
                    if (at >= 0) next[at] = next.getOrElse(slot) { k }
                    if (slot < next.size) next[slot] = k else next += k
                    model.fx.click()
                    model.update { battle().squad = next.distinct().toMutableList() }
                    model.close()
                },
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(typeColor(e).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { AnimalArt(e, Modifier.size(36.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(e.n + if (rec.holo != null) "  ✦" else "", style = display(15.sp, c.hi))
                        Text(
                            "LV ${Game.levelFor(rec.count)} · ${Dex.typesOf(e).joinToString(" / ") { Dex.types.getValue(it).name }}${if (at >= 0) " · in slot ${at + 1}" else ""}",
                            style = mono(10.sp, c.dim),
                        )
                    }
                    Text("${Battle.pwrOf(s, k).roundToInt()}", style = display(16.sp, c.accentDeep))
                }
            }
        }
    }
}
