package io.github.krixhnarr.wilddex.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Clock
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Entry
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.Loot
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.Bar
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.CardView
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.Icon
import io.github.krixhnarr.wilddex.ui.LineIcon
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PanelHead
import io.github.krixhnarr.wilddex.ui.Small
import io.github.krixhnarr.wilddex.ui.Stars
import io.github.krixhnarr.wilddex.ui.TypeGlyph
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.hex
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.ui.typeColor
import kotlinx.coroutines.launch

@Composable
fun SheetContent(model: GameModel, sheet: Sheet) {
    when (sheet) {
        is Sheet.Card -> CardSheet(model, sheet.key)
        is Sheet.Help -> HelpSheet(model, sheet.topic)
        Sheet.Intro -> IntroSheet(model)
        is Sheet.Crate -> CrateSheet(model, sheet.free)
        is Sheet.Reveal -> RevealSheet(model, sheet.result)
        is Sheet.Choices -> ChoicesSheet(model, sheet.options, sheet.text)
        is Sheet.Squad -> SquadSheet(model, sheet.slot)
        is Sheet.Fight -> FightSheet(model, sheet.kind)
    }
}

// ---------------------------------------------------------------- shared bits
@Composable
fun SheetHead(eyebrow: String, title: String, sub: String? = null) {
    val c = LocalWd.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eyebrow.uppercase(), style = mono(10.sp, c.accentDeep, FontWeight.Bold, 0.16f))
        Text(title, style = display(26.sp, c.hi), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        if (sub != null) Text(sub, style = mono(11.sp, c.dim).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), textAlign = TextAlign.Center)
    }
}

@Composable
fun TypeChips(e: Entry) {
    val c = LocalWd.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in Dex.typesOf(e)) {
            val ty = Dex.types.getValue(t)
            val col = hex(ty.color)
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeGlyph(t, Modifier.size(15.dp))
                Text(" ${ty.name.uppercase()}", style = mono(11.sp, c.readable(col), FontWeight.Bold, 0.06f))
            }
        }
    }
}

/** A card you can drag to tilt; springs back when let go. */
@Composable
fun TiltCard(model: GameModel, e: Entry, width: Int = 210) {
    val s = model.live
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val tc = typeColor(e)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((width * 1.25f).dp).background(Brush.radialGradient(listOf(tc.copy(alpha = 0.35f), Color.Transparent))))
        CardView(
            e, s.caught[e.k], intel = s.intel[e.k] == true, frame = model.frame(),
            modifier = Modifier.width(width.dp)
                .graphicsLayer { rotationX = rx.value; rotationY = ry.value; cameraDistance = 14f * density }
                .pointerInput(e.k) {
                    detectDragGestures(
                        onDragEnd = { scope.launch { rx.animateTo(0f, spring(0.35f, Spring.StiffnessLow)) }; scope.launch { ry.animateTo(0f, spring(0.35f, Spring.StiffnessLow)) } },
                    ) { ch, d ->
                        ch.consume()
                        scope.launch { rx.snapTo((rx.value - d.y / 6f).coerceIn(-22f, 22f)) }
                        scope.launch { ry.snapTo((ry.value + d.x / 6f).coerceIn(-26f, 26f)) }
                    }
                },
        )
    }
}

@Composable
fun Actions(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@Composable
fun Section(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Panel(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        PanelHead(title) { if (trailing != null) Small(trailing) }
        content()
    }
}

// ---------------------------------------------------------------- card detail
private fun title(s: String) = s.split(' ', '-').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

@Composable
fun CardSheet(model: GameModel, key: String) {
    val c = LocalWd.current
    val s = model.live
    val e = Dex.byKey.getValue(key)
    val rec = s.caught[key]
    val intel = rec == null && s.intel[key] == true
    TiltCard(model, e)
    CardDetail(model, e)
    Actions {
        if (rec != null) ChunkyButton("Play audio", icon = { LineIcon(Icon.Speaker, Modifier.size(18.dp), c.hi) }) { model.fx.speak("${e.n}. ${e.t}") }
        if (rec == null && !intel) ChunkyButton("Decrypt · ${Game.DECRYPT_COST}◆", enabled = s.shards >= Game.DECRYPT_COST) { model.decrypt(key) }
        ChunkyButton("Close", kind = Btn.Primary) { model.close() }
    }
}

@Composable
fun CardDetail(model: GameModel, e: Entry, typing: Boolean = false) {
    val c = LocalWd.current
    val s = model.live
    val rec = s.caught[e.k]
    val intel = rec == null && s.intel[e.k] == true
    val known = rec != null || intel
    val set = Dex.setOf(e)
    val rarity = Dex.rarity.getValue(e.r)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("#${pad3(e.no)} · ${set.name}".uppercase(), style = mono(10.sp, c.accentDeep, FontWeight.Bold, 0.14f))
        Text(if (known) e.n else "Unknown", style = display(28.sp, c.hi), textAlign = TextAlign.Center)
        Text(if (known) e.s else "species incognita", style = mono(12.sp, c.dim).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        Spacer(Modifier.height(8.dp))
        TypeChips(e)
    }
    if (rec != null) {
        Section("Dex entry", rarity.name) {
            TypedText(e.t, typing)
            Spacer(Modifier.height(8.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = c.accentDeep, fontWeight = FontWeight.Bold)) { append("// did you know? ") }
                append(e.f)
            }, style = mono(12.sp, c.ink))
        }
        StatsSection(e, rec.count)
        Section("Field data", "${rarity.value} XP") {
            Fact("Habitat", e.h); Fact("Diet", e.d); Fact("Size", e.z)
        }
        Section("Capture log", "${rec.count}× seen") {
            Row {
                if (rec.photo) {
                    val file = model.store()?.photo(e.k)
                    val bmp = remember(file?.lastModified()) { file?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
                    if (bmp != null) Image(bmp, "Your capture photo of a ${e.n}", Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Fact("First", Clock.date(rec.first).toString())
                    Fact("Last", Clock.date(rec.last).toString())
                    Fact("Forms", "${rec.forms.size}/${e.c.size}")
                    rec.loc?.let { (lat, lon) ->
                        Fact("Near", "%.2f°%s %.2f°%s".format(kotlin.math.abs(lat), if (lat >= 0) "N" else "S", kotlin.math.abs(lon), if (lon >= 0) "E" else "W"))
                    }
                }
            }
            if (e.c.size > 1 && rec.forms.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in rec.forms) Text(
                        title(Dex.labels[f]), style = mono(10.sp, c.ink, FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface2).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    } else {
        Section("Dex entry", rarity.name) {
            Text(
                if (intel) "Intel decrypted. Find a real ${e.n.lowercase()} — look in ${e.h.lowercase()} — and scan it to capture this card."
                else "Encrypted. This ${rarity.name.lowercase()} signal was last traced to ${e.h.lowercase()}.",
                style = mono(12.sp, c.ink),
            )
        }
        StatsSection(e, null)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = LocalWd.current
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label.uppercase(), style = mono(10.sp, c.dim, FontWeight.Bold, 0.1f), modifier = Modifier.width(72.dp).padding(top = 1.dp))
        Text(value, style = mono(12.sp, c.ink), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TypedText(text: String, typing: Boolean) {
    val c = LocalWd.current
    var shown by remember(text) { mutableIntStateOf(if (typing) 0 else text.length) }
    LaunchedEffect(text, typing) {
        while (shown < text.length) { kotlinx.coroutines.delay(14); shown = minOf(text.length, shown + 1) }
    }
    Text(text.take(shown) + if (shown < text.length) "▌" else "", style = mono(13.sp, c.hi))
}

@Composable
fun StatsSection(e: Entry, count: Int?) {
    val c = LocalWd.current
    val lv = count?.let { Game.levelFor(it) } ?: 1
    val st = Game.statsFor(e, lv)
    val tc = typeColor(e)
    Section("Battle stats · Lv ${if (count != null) lv else "—"}") {
        if (count != null) Row(Modifier.padding(bottom = 8.dp)) { Stars(Game.stars(lv), 13.dp, c.line2) }
        for (k in Game.STAT_KEYS) {
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k.uppercase(), style = mono(11.sp, c.dim, FontWeight.Bold), modifier = Modifier.width(40.dp))
                Bar(if (count != null) st[k] / 100f else 0f, tc, Modifier.weight(1f), height = 9.dp)
                Text(if (count != null) "${st[k]}" else "??", style = mono(12.sp, c.hi, FontWeight.Bold), modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("POWER", style = mono(11.sp, c.dim, FontWeight.Bold, 0.1f), modifier = Modifier.weight(1f))
            Text(if (count != null) "${st.pwr}" else "???", style = display(18.sp, c.hi))
        }
        if (count != null) {
            val next = Game.nextLevelAt(lv)
            val prev = 1 shl (lv - 1)
            Text(
                if (next != null) "$count/$next sightings to Lv ${lv + 1}" else "Max level reached",
                style = mono(11.sp, c.dim), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Bar(if (next != null) (count - prev).toFloat() / (next - prev) else 1f, c.gold, Modifier.fillMaxWidth(), height = 7.dp)
        }
    }
}

// ---------------------------------------------------------------- help & intro
private val HELP = mapOf(
    "scan" to ("How scanning works" to listOf(
        "Aim" to "Point at one real, live animal — close and well lit.",
        "Sweep" to "Tap scan and slide your phone left, then right. Keep the animal in the ring.",
        "Grade" to "A steady sweep earns S, A, B or C — better grades pay more ◆ and XP. S doubles holo odds.",
        "Real only" to "Photos, prints, screens and videos are rejected: the sweep checks for real 3D depth.",
    )),
    "types" to ("Type matchups" to listOf(
        "×1.5" to "Each type beats two others for ×1.5 damage. Hitting a type that beats yours is resisted (×0.67).",
        "Guard" to "Guard takes less damage and charges Overdrive — a big hit with your best type.",
        "Holo" to "Holo cards get +10% HP, ATK and DEF.",
    )),
    "supply" to ("Supply crates" to listOf(
        "Cosmetics" to "Crates hold card frames and operator titles. You get one every level-up, or buy one for ${Loot.CRATE_COST}◆.",
        "Dupes" to "Duplicates refund ${Loot.DUPE_REFUND}◆.",
        "No animals" to "No animals inside — those you scan for real.",
    )),
    "about" to ("About WildDex" to listOf(
        "Private" to "Recognition runs on your phone (MobileNet v2). Photos never leave your device, and scanning works offline.",
        "Dex" to "${Dex.total} cards · ${Dex.sets.size} sectors · ${Dex.typeIds.size} types.",
        "Credits" to "3D animal art: Microsoft Fluent Emoji (MIT). Fonts: Russo One, JetBrains Mono (OFL).",
    )),
)

@Composable
fun Steps(steps: List<Pair<String, String>>) {
    val c = LocalWd.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, (b, t) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(c.sun), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = display(14.sp, c.onSun))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(b, style = display(15.sp, c.hi))
                    Text(t, style = mono(12.sp, c.ink), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun HelpSheet(model: GameModel, topic: String) {
    val (t, steps) = HELP[topic] ?: return
    SheetHead("Help", t)
    Steps(steps)
    Actions { ChunkyButton("Got it", kind = Btn.Primary) { model.close() } }
}

@Composable
private fun IntroSheet(model: GameModel) {
    val c = LocalWd.current
    SheetHead("How to play", "WildDex", "real-world animal card collector")
    Steps(listOf(
        "Scan" to "Point the camera at a real, living animal — a pet, a park bird, a garden bug, a zoo lion — press scan and slowly slide your phone left, then right. Cards only drop for live 3D animals — never photos, screens or videos.",
        "Pull the card" to "New species drop a sealed card. Tap to decrypt it and add it to your binder.",
        "Level up" to "Scan the same animal again to power up its card — more stars, better stats. A smooth sweep earns a higher sync grade, and any scan can drop a rare holo card.",
        "Battle" to "Build a squad of three in the Arena and beat today's rival. Use type matchups: every affinity beats two others.",
        "Complete" to "${Dex.total} cards · ${Dex.typeIds.size} affinities · ${Dex.sets.size} sectors. Clear daily orders, keep your streak, level up for supply crates, and spend credits on intel and crates.",
    ))
    Text("All recognition happens on your device. Nothing is uploaded.", style = mono(11.sp, c.dim), modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), textAlign = TextAlign.Center)
    Actions {
        ChunkyButton("Start collecting", kind = Btn.Primary) {
            model.update { onboarded = true }
            model.close()
        }
    }
}
