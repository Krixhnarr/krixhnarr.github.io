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
import io.github.krixhnarr.wilddex.ui.Block
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardShape
import io.github.krixhnarr.wilddex.ui.PillButton
import io.github.krixhnarr.wilddex.ui.HelpButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PanelHead
import io.github.krixhnarr.wilddex.ui.Small
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.body
import io.github.krixhnarr.wilddex.ui.caption
import io.github.krixhnarr.wilddex.ui.label
import io.github.krixhnarr.wilddex.ui.headline

/** Loot tiers use the pastel blocks: Common cream, Rare mint, Epic lilac, Legendary lime. */
fun tierColor(r: Int): Color = when (r) { 1 -> Color(0xFFF4ECD6); 2 -> Color(0xFFC8E6CD); 3 -> Color(0xFFC5B0F4); 4 -> Color(0xFFDCEEB1); else -> Color(0xFFE6E6E6) }

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
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(c.ink, c.ink))).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("OPERATOR", style = caption(11.sp, Color.White), modifier = Modifier.weight(1f))
                Text("OP-${Progress.operatorId(s)}", style = label(13.sp, Color.White))
            }
            Text("CALLSIGN", style = caption(10.sp, c.ink), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            var name by remember { mutableStateOf(s.name) }
            BasicTextField(
                name, { v -> name = v.take(18); model.update { this.name = name.trim() } },
                singleLine = true, textStyle = headline(21.sp, c.ink), cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceSoft).border(1.dp, c.hairline, RoundedCornerShape(12.dp)).padding(12.dp),
                decorationBox = { inner -> if (name.isEmpty()) Text("Enter name", style = headline(21.sp, c.muted)); inner() },
            )
            Text(Loot.titleName(s), style = label(14.sp, c.ink), modifier = Modifier.padding(top = 8.dp))
            Text("Rank $rank · Lv ${Progress.opLevel(s)}", style = headline(16.sp, c.ink), modifier = Modifier.padding(top = 4.dp))
            Text(next?.let { "${it.first - n} more cards to ${it.second}" } ?: "Every card collected. Legendary.", style = body(13.sp, c.ink))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IdStat(Modifier.weight(1f), pad3(n), "Cards")
                IdStat(Modifier.weight(1f), pad3(forms), "Forms")
                IdStat(Modifier.weight(1f), "${s.xpNow()}", "XP")
                IdStat(Modifier.weight(1f), "${s.scans}", "Scans")
            }
            val byR = (1..4).map { r -> Dex.entries.filter { it.r == r }.let { all -> "${all.count { s.caught.containsKey(it.k) }}/${all.size}" } }
            Text("Common ${byR[0]} · Uncommon ${byR[1]} · Rare ${byR[2]} · Legendary ${byR[3]}", style = body(12.sp, c.ink), modifier = Modifier.padding(top = 10.dp))
        }

        SupplyPanel(model, s)
        LockerPanel(model, s)
        ConfigPanel(model, s)
        BackupPanel(model)

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            PillButton("How to play", small = true) { model.fx.click(); model.open(Sheet.Intro) }
            PillButton("About", small = true) { model.fx.click(); model.open(Sheet.Help("about")) }
        }
    }
}

@Composable
private fun IdStat(modifier: Modifier, value: String, label: String) {
    val c = LocalWd.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceSoft).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = headline(18.sp, c.ink))
        Text(label.uppercase(), style = caption(9.sp, c.ink))
    }
}

@Composable
private fun SupplyPanel(model: GameModel, s: PlayerState) {
    val c = LocalWd.current
    val l = s.locker()
    val canBuy = s.shards >= Loot.CRATE_COST
    Block(c.cream, Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Supply", style = headline(22.sp, c.blockInk), modifier = Modifier.weight(1f))
            HelpButton { model.fx.click(); model.open(Sheet.Help("supply")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                LineIcon(Icon.Crate, Modifier.size(52.dp), c.blockInk, 1.6f)
                if (l.crates > 0) Text(
                    "${l.crates}", style = label(13.sp, Color.White),
                    modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(c.magenta).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Text("Card frames & titles. One free every level-up.", style = body(16.sp, c.blockInk), modifier = Modifier.padding(start = 12.dp).weight(1f))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (l.crates > 0) PillButton("Open crate (${l.crates})", kind = Btn.Primary, small = true) { model.fx.click(); model.open(Sheet.Crate(true)) }
            PillButton("Buy · ${Loot.CRATE_COST}◆", kind = if (l.crates == 0 && canBuy) Btn.Primary else Btn.Secondary, small = true, enabled = canBuy, onBlock = true) {
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
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) c.surfaceSoft else c.surfaceSoft)
                        .border(if (on) 2.dp else 1.dp, if (on) c.ink else c.hairline, RoundedCornerShape(12.dp))
                        .then(if (owned) Modifier.clickable { model.fx.click(); model.fx.buzz(10); model.update { locker().frame = f.id } } else Modifier)
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(30.dp).height(40.dp).clip(CardShape)
                            .background(if (owned) FrameSwatch(f.id) else Brush.linearGradient(listOf(c.surfaceSoft, c.surfaceSoft)))
                            .border(2.dp, if (owned) tierColor(f.r) else c.hairline, CardShape),
                    )
                    Text(if (owned) f.name else "???", style = label(11.sp, c.ink), modifier = Modifier.padding(top = 3.dp), maxLines = 1)
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
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) c.ink else c.surfaceSoft)
                        .border(1.5.dp, if (owned) tierColor(t.r) else c.hairline, RoundedCornerShape(10.dp))
                        .then(if (owned) Modifier.clickable { model.fx.click(); model.fx.buzz(10); model.update { locker().title = t.id } } else Modifier)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    style = label(13.sp, if (on) Color.White else if (owned) c.ink else c.muted),
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
                Text("Theme", style = headline(15.sp, c.ink))
                Text("Dark after 7pm on Auto", style = body(12.sp, c.ink))
            }
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceSoft).padding(3.dp)) {
                for (m in listOf("auto", "day", "night")) {
                    val on = s.settings.theme == m
                    Text(
                        when (m) { "day" -> "Light"; "night" -> "Dark"; else -> "Auto" },
                        Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) c.ink else Color.Transparent)
                            .clickable { model.fx.click(); model.update { settings.theme = m }; model.applySky() }.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = label(13.sp, if (on) Color.White else c.ink),
                    )
                }
            }
        }
        Toggle("Voice", "Read new cards aloud", s.settings.voice) { v -> model.update { settings.voice = v } }
        Toggle("Music", "Day, night & battle themes", s.settings.music) { v -> model.update { settings.music = v } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Music volume", style = headline(15.sp, c.ink), modifier = Modifier.width(120.dp))
            var vol by remember { mutableStateOf(s.settings.musicVol.toFloat()) }
            Slider(
                vol, { vol = it }, Modifier.weight(1f), steps = 19,
                onValueChangeFinished = { model.update { settings.musicVol = (vol * 20).toInt() / 20.0 } },
                colors = SliderDefaults.colors(thumbColor = c.lime, activeTrackColor = c.ink, inactiveTrackColor = c.hairlineSoft),
            )
        }
        Toggle("Sound FX", null, s.settings.sound) { v -> model.update { settings.sound = v }; if (v) model.fx.click() }
        Toggle("Haptics", null, s.settings.haptics) { v -> model.update { settings.haptics = v }; if (v) model.fx.buzz(20) }
        Toggle("Card tilt", "Cards lean as you move your phone", s.settings.tilt) { v -> model.update { settings.tilt = v } }
        Toggle("Location tags", "Map your finds (~1 km, on this phone)", s.settings.location) { v ->
            if (v) model.enableLocation?.invoke() ?: model.update { settings.location = true } else model.update { settings.location = false }
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalWd.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = headline(15.sp, c.ink))
            if (sub != null) Text(sub, style = body(12.sp, c.ink))
        }
        Switch(
            value, onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.success, uncheckedTrackColor = c.surfaceSoft, uncheckedBorderColor = c.hairline, uncheckedThumbColor = c.muted),
        )
    }
}

@Composable
private fun BackupPanel(model: GameModel) {
    val c = LocalWd.current
    var confirm by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PanelHead("Backup")
        Text("Saved on this phone only.", style = body(14.sp, c.ink))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Export", small = true) { model.exportBackup?.invoke() }
            PillButton("Import", small = true) { model.importBackup?.invoke() }
            PillButton("Wipe binder", small = true) { confirm = true }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Wipe binder?", style = headline(20.sp, Color.Black)) },
        text = { Text("Every card, credit and streak will be deleted. This can't be undone unless you exported a backup.", style = body(15.sp, Color.Black)) },
        confirmButton = {
            TextButton({ confirm = false; model.wipe() }) { Text("Wipe", style = label(15.sp, Color(0xFFFF3D8B))) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", style = label(15.sp, Color.Black)) } },
    )
}
