package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Loot
import io.github.krixhnarr.wilddex.core.PlayerState
import io.github.krixhnarr.wilddex.core.Progress
import io.github.krixhnarr.wilddex.core.nextRank
import io.github.krixhnarr.wilddex.core.rankFor
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardShape
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.HelpButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PanelHead
import io.github.krixhnarr.wilddex.ui.Small
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.mono

fun tierColor(r: Int): Color = Loot.TIERS[r]?.let { Color(it.color) } ?: Color(0xFF8DA0B0)

@Composable
fun ProfileScreen(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val n = s.ownedKeys().size
    val rank = rankFor(n).second
    val next = nextRank(n)
    val forms = s.caught.values.sumOf { it.forms.size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        ScreenTitle("Operator", "Clearance: $rank")

        // ---- ID card
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(c.accent, c.accentDeep))).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("OPERATOR", style = mono(11.sp, Color.White, FontWeight.Bold, 0.2f), modifier = Modifier.weight(1f))
                Text("OP-${Progress.operatorId(s)}", style = mono(11.sp, Color.White, FontWeight.Bold))
            }
            Text("CALLSIGN", style = mono(10.sp, c.dim, FontWeight.Bold, 0.12f), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            var name by remember { mutableStateOf(s.name) }
            BasicTextField(
                name, { v -> name = v.take(18); model.update { this.name = name.trim() } },
                singleLine = true, textStyle = display(20.sp, c.hi), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).border(1.dp, c.line2, RoundedCornerShape(12.dp)).padding(12.dp),
                decorationBox = { inner -> if (name.isEmpty()) Text("Enter name", style = display(20.sp, c.dim2)); inner() },
            )
            Text(Loot.titleName(s), style = mono(12.sp, c.accentDeep, FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
            Text("Rank $rank · Lv ${Progress.opLevel(s)}", style = display(15.sp, c.hi), modifier = Modifier.padding(top = 4.dp))
            Text(next?.let { "${it.first - n} more cards to ${it.second}" } ?: "Every card collected. Legendary.", style = mono(11.sp, c.dim))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdStat(Modifier.weight(1f), pad3(n), "Cards")
                IdStat(Modifier.weight(1f), pad3(forms), "Forms")
                IdStat(Modifier.weight(1f), "${s.xpNow()}", "XP")
                IdStat(Modifier.weight(1f), "${s.scans}", "Scans")
            }
            val byR = (1..4).map { r -> Dex.entries.filter { it.r == r }.let { all -> "${all.count { s.caught.containsKey(it.k) }}/${all.size}" } }
            Text("Common ${byR[0]} · Uncommon ${byR[1]} · Rare ${byR[2]} · Legendary ${byR[3]}", style = mono(10.sp, c.dim), modifier = Modifier.padding(top = 10.dp))
        }

        SupplyPanel(model, s)
        LockerPanel(model, s)
        ConfigPanel(model, s)
        BackupPanel(model)

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            ChunkyButton("How to play", small = true) { model.fx.click(); model.open(Sheet.Intro) }
            ChunkyButton("About", small = true) { model.fx.click(); model.open(Sheet.Help("about")) }
        }
    }
}

@Composable
private fun IdStat(modifier: Modifier, value: String, label: String) {
    val c = LocalWd.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = display(17.sp, c.hi))
        Text(label.uppercase(), style = mono(9.sp, c.dim, FontWeight.Bold, 0.1f))
    }
}

@Composable
private fun SupplyPanel(model: GameModel, s: PlayerState) {
    val c = LocalWd.current
    val l = s.locker()
    val canBuy = s.shards >= Loot.CRATE_COST
    Panel(Modifier.fillMaxWidth().padding(top = 12.dp), tint = c.gold) {
        PanelHead("Supply") { HelpButton { model.fx.click(); model.open(Sheet.Help("supply")) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                LineIcon(Icon.Crate, Modifier.size(52.dp), Color(0xFFB07F00), 1.8f)
                if (l.crates > 0) Text(
                    "${l.crates}", style = mono(11.sp, Color.White, FontWeight.Bold),
                    modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(c.coral).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Text("Card frames & titles. One free every level-up.", style = mono(12.sp, c.ink), modifier = Modifier.padding(start = 12.dp).weight(1f))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.crates > 0) ChunkyButton("Open crate (${l.crates})", kind = Btn.Primary, small = true) { model.fx.click(); model.open(Sheet.Crate(true)) }
            ChunkyButton("Buy · ${Loot.CRATE_COST}◆", kind = if (l.crates == 0 && canBuy) Btn.Primary else Btn.Neutral, small = true, enabled = canBuy) {
                model.fx.click(); model.open(Sheet.Crate(false))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockerPanel(model: GameModel, s: PlayerState) {
    val c = LocalWd.current
    val l = s.locker()
    Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PanelHead("Locker") { Small("${l.frames.size}/${Loot.FRAMES.size} frames · ${l.titles.size}/${Loot.TITLES.size} titles") }
        Small("Card frame")
        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
            for (f in Loot.FRAMES) {
                val owned = f.id in l.frames
                val on = l.frame == f.id
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) c.accentSoft else c.surface2)
                        .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp))
                        .then(if (owned) Modifier.clickable { model.fx.click(); model.fx.buzz(10); model.update { locker().frame = f.id } } else Modifier)
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(30.dp).height(40.dp).clip(CardShape)
                            .background(if (owned) FrameSwatch(f.id) else Brush.linearGradient(listOf(c.slot, c.slot)))
                            .border(2.dp, if (owned) tierColor(f.r) else c.line2, CardShape),
                    )
                    Text(if (owned) f.name else "???", style = mono(9.sp, c.hi, FontWeight.Bold), modifier = Modifier.padding(top = 3.dp), maxLines = 1)
                }
            }
        }
        Small("Title")
        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in Loot.TITLES) {
                val owned = t.id in l.titles
                val on = l.title == t.id
                Text(
                    if (owned) t.name else "? ? ?",
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) c.accent else c.surface2)
                        .border(1.5.dp, if (owned) tierColor(t.r) else c.line, RoundedCornerShape(10.dp))
                        .then(if (owned) Modifier.clickable { model.fx.click(); model.fx.buzz(10); model.update { locker().title = t.id } } else Modifier)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    style = mono(11.sp, if (on) Color.White else if (owned) c.ink else c.dim2, FontWeight.Bold),
                )
            }
        }
    }
}

fun FrameSwatch(id: String): Brush = when (id) {
    "circuit" -> Brush.linearGradient(listOf(Color(0xFFD9F7EA), Color(0xFF7AD9B0)))
    "topo" -> Brush.linearGradient(listOf(Color(0xFFF4EAD5), Color(0xFFD8B98A)))
    "neon" -> Brush.linearGradient(listOf(Color(0xFF2B1B55), Color(0xFFFF4FD8)))
    "sakura" -> Brush.linearGradient(listOf(Color(0xFFFFD9DF), Color(0xFFFF9EB6)))
    "aurora" -> Brush.linearGradient(listOf(Color(0xFF7CF5C8), Color(0xFF7C9CF5), Color(0xFFC87CF5)))
    "glitch" -> Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFF3DFFF2)))
    "obsidian" -> Brush.linearGradient(listOf(Color(0xFF2A2A30), Color(0xFF0E0E10)))
    else -> Brush.linearGradient(listOf(Color(0xFFE8F4FF), Color.White))
}

@Composable
private fun ConfigPanel(model: GameModel, s: PlayerState) {
    val c = LocalWd.current
    Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PanelHead("Config")
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sky", style = display(14.sp, c.hi))
                Text("Night sky from 7pm to 6am", style = mono(10.sp, c.dim))
            }
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(3.dp)) {
                for (m in listOf("auto", "day", "night")) {
                    val on = s.settings.theme == m
                    Text(
                        m.replaceFirstChar(Char::uppercaseChar),
                        Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) c.accent else Color.Transparent)
                            .clickable { model.fx.click(); model.update { settings.theme = m }; model.applySky() }.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = mono(11.sp, if (on) Color.White else c.ink, FontWeight.Bold),
                    )
                }
            }
        }
        Toggle("Voice", "Read new cards aloud", s.settings.voice) { v -> model.update { settings.voice = v } }
        Toggle("Music", "Day, night & battle themes", s.settings.music) { v -> model.update { settings.music = v } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Music volume", style = display(14.sp, c.hi), modifier = Modifier.width(120.dp))
            var vol by remember { mutableStateOf(s.settings.musicVol.toFloat()) }
            Slider(
                vol, { vol = it }, Modifier.weight(1f), steps = 19,
                onValueChangeFinished = { model.update { settings.musicVol = (vol * 20).toInt() / 20.0 } },
                colors = SliderDefaults.colors(thumbColor = c.sun2, activeTrackColor = c.accent, inactiveTrackColor = c.barBg),
            )
        }
        Toggle("Sound FX", null, s.settings.sound) { v -> model.update { settings.sound = v }; if (v) model.fx.click() }
        Toggle("Haptics", null, s.settings.haptics) { v -> model.update { settings.haptics = v }; if (v) model.fx.buzz(20) }
        Toggle("Card tilt", "Cards lean as you move your phone", s.settings.tilt) { v -> model.update { settings.tilt = v } }
        Toggle("Location tags", "Map your finds (~1 km, on this phone)", s.settings.location) { v -> model.update { settings.location = v } }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalWd.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = display(14.sp, c.hi))
            if (sub != null) Text(sub, style = mono(10.sp, c.dim))
        }
        Switch(
            value, onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.grass, uncheckedTrackColor = c.slot, uncheckedBorderColor = c.line2, uncheckedThumbColor = c.dim2),
        )
    }
}

@Composable
private fun BackupPanel(model: GameModel) {
    val c = LocalWd.current
    var confirm by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PanelHead("Backup")
        Text("Saved on this phone only.", style = mono(12.sp, c.ink))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChunkyButton("Export", small = true) { model.exportBackup?.invoke() }
            ChunkyButton("Import", small = true) { model.importBackup?.invoke() }
            ChunkyButton("Wipe binder", small = true) { confirm = true }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Wipe binder?", style = display(18.sp, Color(0xFF13283A))) },
        text = { Text("Every card, credit and streak will be deleted. This can't be undone unless you exported a backup.", style = mono(12.sp, Color(0xFF1E3346))) },
        confirmButton = {
            TextButton({ confirm = false; model.wipe() }) { Text("WIPE", style = mono(13.sp, Color(0xFFC8432A), FontWeight.Bold)) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("CANCEL", style = mono(13.sp, Color(0xFF1E3346), FontWeight.Bold)) } },
    )
}
