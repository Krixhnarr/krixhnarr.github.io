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
import androidx.compose.runtime.mutableStateOf
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
import io.github.krixhnarr.wilddex.ui.PillButton
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
import io.github.krixhnarr.wilddex.ui.body
import io.github.krixhnarr.wilddex.ui.caption
import io.github.krixhnarr.wilddex.ui.label
import io.github.krixhnarr.wilddex.ui.headline
import io.github.krixhnarr.wilddex.ui.typeColor
import kotlinx.coroutines.launch

@Composable
fun SheetContent(model: GameModel, sheet: Sheet) {
    when (sheet) {
        is Sheet.Card -> CardSheet(model, sheet.key)
        is Sheet.Help -> HelpSheet(model, sheet.topic)
        Sheet.Intro -> IntroSheet(model)
        is Sheet.Crate -> CrateSheet(model, sheet.free)
        is Sheet.Reveal -> RevealSheet(model, sheet)
        is Sheet.Squad -> SquadSheet(model, sheet.slot)
        is Sheet.Fight -> FightSheet(model, sheet)
    }
}

// ---------------------------------------------------------------- shared bits
@Composable
fun SheetHead(eyebrow: String, title: String, sub: String? = null) {
    val c = LocalWd.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eyebrow.uppercase(), style = caption(10.sp, c.ink))
        Text(title, style = display(32.sp, c.ink), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        if (sub != null) Text(sub, style = body(13.sp, c.ink).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), textAlign = TextAlign.Center)
    }
}

@Composable
fun TypeChips(e: Entry) {
    val c = LocalWd.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in Dex.typesOf(e)) {
            val ty = Dex.types.getValue(t)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.surfaceSoft).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeGlyph(t, Modifier.size(15.dp))
                Text("  ${ty.name}", style = label(13.sp, c.ink))
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
    // lean with the phone (Card tilt setting): gravity relative to how it was held at first
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var gx by remember { mutableStateOf(0f) }
    var gy by remember { mutableStateOf(0f) }
    if (s.settings.tilt) androidx.compose.runtime.DisposableEffect(Unit) {
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var base: FloatArray? = null
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(ev: android.hardware.SensorEvent) {
                val b = base ?: ev.values.copyOf().also { base = it }
                gx += (((ev.values[0] - b[0]) * -3.2f).coerceIn(-18f, 18f) - gx) * 0.2f
                gy += (((ev.values[1] - b[1]) * 3.2f).coerceIn(-15f, 15f) - gy) * 0.2f
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm?.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(l) }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        CardView(
            e, s.caught[e.k], intel = s.intel[e.k] == true, frame = model.frame(),
            modifier = Modifier.width(width.dp)
                .graphicsLayer { rotationX = rx.value + gy; rotationY = ry.value + gx; cameraDistance = 14f * density }
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
        if (rec != null) PillButton("Play audio", icon = { col -> LineIcon(Icon.Speaker, Modifier.size(18.dp), col) }) { model.fx.speak("${e.n}. ${e.t}") }
        if (rec == null && !intel) PillButton("Decrypt · ${Game.DECRYPT_COST}◆", enabled = s.shards >= Game.DECRYPT_COST) { model.decrypt(key) }
        PillButton("Close", kind = Btn.Primary) { model.close() }
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
        Text("#${pad3(e.no)} · ${set.name}".uppercase(), style = caption(10.sp, c.ink))
        Text(if (known) e.n else "Unknown", style = display(34.sp, c.ink), textAlign = TextAlign.Center)
        Text(if (known) e.s else "species incognita", style = body(14.sp, c.ink).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        Spacer(Modifier.height(8.dp))
        TypeChips(e)
    }
    if (rec != null) {
        Section("Dex entry", rarity.name) {
            TypedText(e.t, typing)
            Spacer(Modifier.height(8.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append("// did you know? ") }
                append(e.f)
            }, style = body(14.sp, c.ink))
        }
        StatsSection(model, e, rec)
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
                        title(Dex.labels[f]), style = label(12.sp, c.ink),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.surfaceSoft).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    } else {
        Section("Dex entry", rarity.name) {
            Text(
                if (intel) "Intel decrypted. Find a real ${e.n.lowercase()} — look in ${e.h.lowercase()} — and scan it to capture this card."
                else "Encrypted. This ${rarity.name.lowercase()} signal was last traced to ${e.h.lowercase()}.",
                style = body(14.sp, c.ink),
            )
        }
        StatsSection(model, e, null)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = LocalWd.current
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label.uppercase(), style = caption(10.sp, c.ink), modifier = Modifier.width(72.dp).padding(top = 1.dp))
        Text(value, style = body(14.sp, c.ink), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TypedText(text: String, typing: Boolean) {
    val c = LocalWd.current
    var shown by remember(text) { mutableIntStateOf(if (typing) 0 else text.length) }
    LaunchedEffect(text, typing) {
        while (shown < text.length) { kotlinx.coroutines.delay(14); shown = minOf(text.length, shown + 1) }
    }
    Text(text.take(shown) + if (shown < text.length) "▌" else "", style = body(15.sp, c.ink))
}

@Composable
fun StatsSection(model: GameModel, e: Entry, rec: io.github.krixhnarr.wilddex.core.CardRecord?) {
    val c = LocalWd.current
    val s = model.live
    val owned = rec != null
    val lv = rec?.let { Game.cardLevel(it) } ?: 1
    val st = Game.statsFor(e, lv)
    val tc = typeColor(e)
    var boosted by remember(e.k) { mutableStateOf<io.github.krixhnarr.wilddex.core.Progress.Upgrade?>(null) }
    val glow = remember(e.k) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(boosted) { if (boosted != null) { glow.snapTo(1f); glow.animateTo(0f, androidx.compose.animation.core.tween(1200)) } }
    Section("Battle stats · Lv ${if (owned) lv else "—"}") {
        if (owned) Row(Modifier.padding(bottom = 8.dp)) { Stars(Game.stars(lv), 13.dp) }
        val before = boosted?.let { Game.statsFor(e, it.before) }
        for (k in Game.STAT_KEYS) {
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k.uppercase(), style = label(13.sp, c.ink), modifier = Modifier.width(40.dp))
                Bar(if (owned) st[k] / 100f else 0f, androidx.compose.ui.graphics.lerp(c.ink, c.magenta, glow.value), Modifier.weight(1f), height = 6.dp)
                Text(if (owned) "${st[k]}" else "??", style = label(14.sp, c.ink), modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                if (before != null) Text("+${st[k] - before[k]}", style = label(13.sp, c.success), modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("POWER", style = caption(11.sp, c.ink), modifier = Modifier.weight(1f))
            Text(if (owned) "${st.pwr}" else "???", style = headline(19.sp, c.ink))
        }
        if (owned) {
            val cost = Game.upgradeCost(e, lv)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        cost == null -> "Max level reached"
                        s.shards >= cost -> "Spend credits to power it up"
                        else -> "Need ${cost - s.shards}◆ more · earn ◆ from new animals, orders and events"
                    },
                    style = body(13.sp, c.ink), modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                if (cost != null) PillButton("Lv ${lv + 1} · $cost◆", kind = if (s.shards >= cost) Btn.Primary else Btn.Secondary, small = true, enabled = s.shards >= cost) {
                    model.upgradeCard(e.k)?.let { boosted = it }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- help & intro
private val HELP = mapOf(
    "scan" to ("How scanning works" to listOf(
        "Aim" to "Point at one real, live animal — near or far, big or small. Fill the ring if you can.",
        "Hold" to "Tap scan and hold the phone steady for a moment while the ring fills.",
        "Grade" to "Sharp, steady scans earn S, A, B or C — on a new animal, better grades pay more ◆ and XP. S needs real depth: keep a bit of the background in view. S doubles holo odds.",
        "Again?" to "Scanning an animal you already have logs a sighting and can turn up a holo, but pays no ◆ and doesn't level the card — use ◆ in your binder for that.",
        "Real only" to "Screens, phones, printed photos and books are rejected: the scanner looks for display pixels, device edges and paper borders.",
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
        "Private" to "Recognition runs on your phone (EfficientNet-Lite4). Photos never leave your device, and scanning works offline.",
        "Dex" to "${Dex.total} cards · ${Dex.sets.size} sectors · ${Dex.typeIds.size} types.",
        "Credits" to "3D animal art: Microsoft Fluent Emoji (MIT). Recogniser: EfficientNet-Lite4 (Apache 2.0). Fonts: Inter, JetBrains Mono (OFL).",
    )),
)

@Composable
fun Steps(steps: List<Pair<String, String>>) {
    val c = LocalWd.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, (b, t) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.canvas).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(c.lime), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = headline(15.sp, c.blockInk))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(b, style = headline(16.sp, c.ink))
                    Text(t, style = body(14.sp, c.ink), modifier = Modifier.padding(top = 2.dp))
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
    Actions { PillButton("Got it", kind = Btn.Primary) { model.close() } }
}

@Composable
private fun IntroSheet(model: GameModel) {
    val c = LocalWd.current
    SheetHead("How to play", "WildDex", "real-world animal card collector")
    Steps(listOf(
        "Scan" to "Point the camera at a real, living animal — a pet, a park bird, a garden bug, a zoo lion — press scan and hold steady for a moment. Cards only drop for live 3D animals — never photos, screens or videos.",
        "Pull the card" to "New species drop a sealed card. Tap to decrypt it and add it to your binder.",
        "Level up" to "Spend credits (◆) to power up a card — more stars, better stats. Earn ◆ by discovering new animals and completing daily orders, weekly events and battles. Any scan can drop a rare holo card.",
        "Battle" to "Build a squad of three in the Arena and beat today's rival. Use type matchups: every affinity beats two others.",
        "Complete" to "${Dex.total} cards · ${Dex.typeIds.size} affinities · ${Dex.sets.size} sectors. Clear daily orders, keep your streak, level up for supply crates, and spend credits on intel and crates.",
    ))
    Text("All recognition happens on your device. Nothing is uploaded.", style = body(13.sp, c.ink), modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), textAlign = TextAlign.Center)
    Actions {
        PillButton("Start collecting", kind = Btn.Primary) {
            model.update { onboarded = true }
            model.close()
        }
    }
}
