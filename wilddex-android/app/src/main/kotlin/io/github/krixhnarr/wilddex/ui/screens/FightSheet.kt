package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Battle
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Progress
import io.github.krixhnarr.wilddex.ui.AnimalArt
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.Chip
import io.github.krixhnarr.wilddex.ui.PillButton
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.HOLO
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.TypeGlyph
import io.github.krixhnarr.wilddex.ui.XpBar
import io.github.krixhnarr.wilddex.ui.compositeOver
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.hex
import io.github.krixhnarr.wilddex.ui.body
import io.github.krixhnarr.wilddex.ui.caption
import io.github.krixhnarr.wilddex.ui.label
import io.github.krixhnarr.wilddex.ui.headline
import io.github.krixhnarr.wilddex.ui.typeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Per-side animation state for one fighter on the field. */
private class SideFx {
    val lunge = Animatable(0f)
    val hurt = Animatable(0f)
    val faint = Animatable(0f)
    val enter = Animatable(1f)
    var guarding by mutableStateOf(false)
    val pops = mutableStateListOf<Pop>()
    val bursts = mutableStateListOf<Burst>()
}

private data class Pop(val text: String, val color: Color, val id: Long = System.nanoTime())
private data class Burst(val type: String, val big: Boolean, val id: Long = System.nanoTime())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FightSheet(model: GameModel, sheet: Sheet.Fight) {
    val c = LocalWd.current
    val kind = sheet.kind
    val s = model.state
    val scope = rememberCoroutineScope()
    val (fight, foeName) = remember(sheet) {
        val you = Battle.squadTeam(s)
        if (kind == "rival") {
            val r = Battle.dailyRival(s)
            Battle.Fight(Battle.Side(you), Battle.Side(Battle.rivalTeam(r)), kind) to r.name
        } else Battle.Fight(Battle.Side(you), Battle.Side(Battle.wildTeam(s, you.size)), kind) to "Wild signal"
    }
    var tick by remember(sheet) { mutableIntStateOf(0) }
    var busy by remember(sheet) { mutableStateOf(false) }
    val log = remember(sheet) { mutableStateListOf<androidx.compose.ui.text.AnnotatedString>() }
    val fx = remember(sheet) { mapOf("you" to SideFx(), "foe" to SideFx()) }
    var banner by remember(sheet) { mutableStateOf<String?>(null) }
    val bannerAnim = remember(sheet) { Animatable(0f) }
    val shake = remember(sheet) { Animatable(0f) }
    val flash = remember(sheet) { Animatable(0f) }
    var outcome by remember(sheet) { mutableStateOf<Progress.BattleOutcome?>(null) }

    fun line(vararg parts: Pair<String, Color?>) {
        log += buildAnnotatedString {
            for ((t, col) in parts) if (col != null) withStyle(SpanStyle(color = col, fontWeight = FontWeight.Bold)) { append(t) } else append(t)
        }
        while (log.size > 3) log.removeAt(0)
    }

    LaunchedEffect(sheet) {
        model.fx.scene("battle"); model.fx.charge(); model.fx.buzz(20, 30, 20)
        line(foeName to c.ink, " sends out " to null, fight.foe.active.entry.n to c.ink, "!" to null)
        line("Go, " to null, fight.you.active.entry.n to c.ink, "!" to null)
    }
    DisposableEffect(sheet) { onDispose { model.fx.scene(if (model.night) "night" else "day") } }

    fun showBanner(text: String) {
        banner = text
        scope.launch { bannerAnim.snapTo(0f); bannerAnim.animateTo(1f, tween(900)) }
    }

    suspend fun play(e: Battle.Event) {
        when (e) {
            is Battle.Event.Guard -> {
                fx.getValue(e.side).guarding = true
                model.fx.guardSfx()
                line(e.name to c.ink, " braces behind a guard." to null)
                tick++
                delay(600)
            }
            is Battle.Event.Attack -> {
                val att = fx.getValue(e.side)
                val tgt = fx.getValue(e.target)
                scope.launch { att.lunge.snapTo(0f); att.lunge.animateTo(1f, tween(140)); att.lunge.animateTo(0f, tween(220)) }
                if (e.move == "overdrive") { model.fx.overdrive(); scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(500)) } }
                delay(220)
                val big = e.crit || e.move == "overdrive" || e.mult > 1
                tgt.bursts += Burst(e.type, big)
                if (big) scope.launch { repeat(4) { shake.animateTo(if (it % 2 == 0) 1f else -1f, tween(45)) }; shake.animateTo(0f, tween(45)) }
                scope.launch { tgt.hurt.snapTo(1f); tgt.hurt.animateTo(0f, tween(450)) }
                model.fx.hit(e.mult)
                model.fx.buzz(*(if (e.crit || e.move == "overdrive") longArrayOf(30, 30, 70) else longArrayOf(if (e.mult > 1) 40 else 20)))
                tgt.pops += Pop("-${e.dmg}", if (e.mult > 1) c.magenta else if (e.mult < 1) c.muted else c.blockInk)
                tgt.guarding = false
                tick++
                val note = listOfNotNull(if (e.crit) "Critical!" else null, if (e.mult > 1) "Super effective!" else if (e.mult < 1) "Resisted…" else null).joinToString(" ")
                val ty = Dex.types.getValue(e.type)
                line(
                    e.name to c.ink, " used " to null,
                    (if (e.move == "overdrive") "Overdrive" else "${ty.name} strike") to c.readable(hex(ty.color)),
                    " · ${e.dmg} dmg" to null, (if (note.isNotEmpty()) " · $note" else "") to if (note.isNotEmpty()) c.ink else null,
                )
                if (big) showBanner(if (e.move == "overdrive") "OVERDRIVE" else if (e.crit) "CRITICAL" else "SUPER")
                delay(750)
            }
            is Battle.Event.Faint -> {
                model.fx.faint()
                scope.launch { fx.getValue(e.side).faint.animateTo(1f, tween(600)) }
                line(e.name to c.ink, " is out of the fight!" to null)
                tick++
                delay(800)
            }
            is Battle.Event.Enter -> {
                val f = fx.getValue(e.side)
                f.faint.snapTo(0f); f.guarding = false
                tick++
                scope.launch { f.enter.snapTo(0f); f.enter.animateTo(1f, spring(0.5f, Spring.StiffnessMediumLow)) }
                line((if (e.side == "you") "Go, " else "Next up, ") to null, e.name to c.ink, "!" to null)
                delay(550)
            }
            is Battle.Event.End -> {}
        }
    }

    fun finish(won: Boolean) {
        val o = Progress.finishBattle(model.state, kind, won)
        outcome = o
        model.queueLevelUps(o.levelUps)
        model.commit()
        model.fx.stinger(won)
        if (won) model.confetti++
        model.fx.buzz(*(if (won) longArrayOf(40, 60, 40, 60, 160) else longArrayOf(120)))
        if (!won) {
            val t = fight.foe.active.types[0]
            val counters = Battle.countersOf(t).joinToString(" / ") { Dex.types.getValue(it).name.uppercase() }
            scope.launch { delay(900); model.say("TIP: $counters BEAT ${Dex.types.getValue(t).name.uppercase()}") }
        }
    }

    fun move(m: String) {
        if (busy || fight.over) return
        busy = true
        model.fx.click()
        scope.launch {
            for (e in Battle.playTurn(fight, m)) play(e)
            busy = false
            tick++
            if (fight.over) finish(fight.winner == "you")
        }
    }

    // ---------------------------------------------------------------- layout
    tick // re-read fighters whenever an event plays
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp, end = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.name.ifBlank { "Operator" }, style = headline(15.sp, c.ink), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("vs", style = headline(19.sp, c.ink), modifier = Modifier.padding(horizontal = 10.dp))
        Text(foeName, style = headline(15.sp, c.ink), modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
    }
    Box(
        Modifier.fillMaxWidth().height(330.dp).graphicsLayer { translationX = shake.value * 8f * density }
            .clip(RoundedCornerShape(22.dp))
            .background(c.mint)
            .border(1.dp, c.hairline, RoundedCornerShape(22.dp)),
    ) {
        // ground ellipses
        Canvas(Modifier.fillMaxSize()) {
            drawOval(Color.Black.copy(alpha = 0.12f), Offset(size.width * 0.52f, size.height * 0.33f), androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.09f))
            drawOval(Color.Black.copy(alpha = 0.14f), Offset(size.width * 0.06f, size.height * 0.84f), androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.1f))
        }
        FieldSide(fight.foe, fx.getValue("foe"), foe = true, tick)
        FieldSide(fight.you, fx.getValue("you"), foe = false, tick)
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(c.lime.copy(alpha = flash.value * 0.8f)))
        banner?.let {
            val t = bannerAnim.value
            if (t in 0.001f..0.999f) Text(
                it, style = display(40.sp, Color.White),
                modifier = Modifier.align(Alignment.Center)
                    .graphicsLayer { val k = if (t < 0.2f) 0.6f + t * 2 else 1f; scaleX = k; scaleY = k; alpha = if (t > 0.7f) (1 - t) / 0.3f else 1f; rotationZ = -6f }
                    .clip(RoundedCornerShape(10.dp)).background(c.coral).padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 70.dp).clip(RoundedCornerShape(14.dp)).background(c.canvas).padding(10.dp),
    ) { for (l in log) Text(l, style = body(14.sp, c.ink), modifier = Modifier.padding(vertical = 1.dp)) }

    val o = outcome
    if (o == null) {
        val me = fight.you.active
        val them = fight.foe.active
        val ready = me.charge >= Battle.CHARGE_MAX
        FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
            for (t in me.types) {
                val m = Battle.mult(t, them.types)
                MoveButton(Modifier.weight(1f), "${Dex.types.getValue(t).name} strike", if (m > 1) "super" else if (m < 1) "weak" else null, hex(Dex.types.getValue(t).color), !busy, glyph = t) { move("strike:$t") }
            }
            MoveButton(Modifier.weight(1f), "Guard", "+1 charge", c.ink, !busy) { move("guard") }
            MoveButton(Modifier.weight(1f), "Overdrive", if (ready) "ready!" else "${me.charge}/${Battle.CHARGE_MAX}", c.lime, !busy && ready, hot = ready) { move("overdrive") }
        }
    } else {
        val won = fight.winner == "you"
        Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (won) "Victory" else "Defeat", style = display(56.sp, c.ink))
            FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (o.reward.credits > 0) Chip("+${o.reward.credits}◆", highlight = true)
                Chip("+${o.reward.xp} XP", highlight = won)
                if (kind == "rival" && won && o.reward.credits > 0) Chip("rival defeated", highlight = true)
                if (won && o.reward.credits == 0) Chip(if (kind == "rival") "rival already beaten today" else "daily paid wins used")
            }
            XpBar(o.xpBefore, o.xpAfter)
            Actions {
                if (kind == "wild" || !won) PillButton(if (kind == "rival") "Try again" else "Battle again") { model.open(Sheet.Fight(kind)) }
                PillButton("Done", kind = Btn.Primary) { model.close() }
            }
        }
    }
}

@Composable
private fun BoxScope.FieldSide(side: Battle.Side, f: SideFx, foe: Boolean, tick: Int) {
    val c = LocalWd.current
    val a = side.active
    val tc = typeColor(a.entry)
    val dir = if (foe) -1f else 1f
    // plate: name, HP and charge
    Column(
        Modifier.align(if (foe) Alignment.TopStart else Alignment.BottomEnd).padding(12.dp).width(170.dp)
            .clip(RoundedCornerShape(14.dp)).background(c.canvas.copy(alpha = 0.94f)).border(1.dp, c.hairline, RoundedCornerShape(14.dp)).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.entry.n, style = headline(14.sp, c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(" LV${a.lv}", style = label(12.sp, c.ink))
            Spacer(Modifier.weight(1f))
            for (t in a.types) TypeGlyph(t, Modifier.padding(start = 2.dp).size(13.dp))
        }
        val pct = a.hp.toFloat() / a.maxHp
        val hpCol = if (pct < 0.25f) c.magenta else Color.Black
        val shown = remember(a) { Animatable(pct) }
        LaunchedEffect(a, a.hp) { shown.animateTo(pct, tween(400)) }
        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(c.hairlineSoft)) {
            Box(Modifier.fillMaxWidth(shown.value.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(hpCol))
        }
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${a.hp}/${a.maxHp}", style = label(12.sp, c.ink), modifier = Modifier.weight(1f))
            repeat(Battle.CHARGE_MAX) { i -> Box(Modifier.padding(start = 3.dp).size(8.dp).clip(CircleShape).background(if (i < a.charge) c.lime else c.surfaceSoft)) }
        }
        Row(Modifier.padding(top = 4.dp)) {
            side.team.forEachIndexed { i, t ->
                Box(Modifier.padding(end = 4.dp).size(7.dp).clip(CircleShape).background(when { t.hp <= 0 -> c.muted.copy(alpha = 0.4f); i == side.i -> c.ink; else -> c.ink }))
            }
        }
    }
    // the fighter itself
    Box(
        Modifier.align(if (foe) Alignment.TopEnd else Alignment.BottomStart).padding(if (foe) androidx.compose.foundation.layout.PaddingValues(top = 20.dp, end = 26.dp) else androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp, start = 22.dp))
            .size(if (foe) 120.dp else 140.dp)
            .graphicsLayer {
                val lunge = f.lunge.value * 40f * density
                translationX = lunge * dir + f.hurt.value * 10f * density * kotlin.math.sin(f.hurt.value * 20f)
                translationY = -lunge * dir * 0.6f + f.faint.value * 40f * density
                alpha = (1f - f.faint.value) * f.enter.value.coerceIn(0f, 1f)
                val k = 0.6f + 0.4f * f.enter.value
                scaleX = k * (if (foe) 1f else -1f); scaleY = k
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(0.92f).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
        if (a.holo) Box(Modifier.fillMaxSize(0.85f).clip(CircleShape).border(3.dp, Brush.sweepGradient(HOLO + HOLO.first()), CircleShape))
        AnimalArt(a.entry, Modifier.fillMaxSize(0.72f).graphicsLayer { alpha = 1f - f.hurt.value * 0.5f })
        if (f.guarding) Box(Modifier.fillMaxSize().clip(CircleShape).border(4.dp, Color.Black, CircleShape).background(Color.White.copy(alpha = 0.3f)))
        for (b in f.bursts) key(b.id) { TypeBurst(b) { f.bursts.remove(b) } }
        for (p in f.pops) key(p.id) { DamagePop(p) { f.pops.remove(p) } }
    }
}

@Composable
private fun BoxScope.DamagePop(p: Pop, done: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(900)); done() }
    Text(
        p.text, style = display(32.sp, p.color).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 3f), 4f)),
        modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = -t.value * 50f * density; alpha = 1f - t.value * t.value; val k = 1.3f - 0.3f * t.value; scaleX = k; scaleY = k },
    )
}

/** Type-coloured particle burst on the target (bigger for super hits). */
@Composable
private fun TypeBurst(b: Burst, done: () -> Unit) {
    val t = remember { Animatable(0f) }
    val col = hex(Dex.types.getValue(b.type).color)
    val n = if (b.big) 22 else 12
    val parts = remember { List(n) { Triple(Random.nextFloat() * 2 * PI.toFloat(), 0.5f + Random.nextFloat() * 0.6f, 3f + Random.nextFloat() * 5f) } }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(if (b.big) 700 else 500)); done() }
    Canvas(Modifier.fillMaxSize()) {
        val r0 = size.minDimension / 2
        drawCircle(col.copy(alpha = (1 - t.value) * 0.5f), r0 * (0.3f + t.value * (if (b.big) 0.9f else 0.6f)), style = androidx.compose.ui.graphics.drawscope.Stroke(6f * (1 - t.value) + 1f))
        for ((ang, sp, sz) in parts) {
            val d = r0 * sp * t.value * (if (b.big) 1.2f else 0.9f)
            drawCircle(col.copy(alpha = 1 - t.value), sz * density * (1 - t.value * 0.6f), center + Offset(cos(ang) * d, sin(ang) * d))
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TypeGlyph(b.type, Modifier.size(48.dp).graphicsLayer { alpha = 1 - t.value; val k = 0.6f + t.value; scaleX = k; scaleY = k }, Color.White)
    }
}

@Composable
private fun MoveButton(modifier: Modifier, label: String, hint: String?, tint: Color, enabled: Boolean, glyph: String? = null, hot: Boolean = false, onClick: () -> Unit) {
    val c = LocalWd.current
    io.github.krixhnarr.wilddex.ui.Raised(
        modifier.graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        shape = RoundedCornerShape(50), color = if (hot) c.lime else c.canvas,
        onClick = if (enabled) onClick else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) TypeGlyph(glyph, Modifier.size(20.dp))
            Text(label, style = io.github.krixhnarr.wilddex.ui.label(15.sp, if (hot) c.blockInk else c.ink), modifier = Modifier.padding(start = if (glyph != null) 8.dp else 0.dp).weight(1f), maxLines = 1)
            if (hint != null) Text(
                hint, style = caption(10.sp, when (hint) { "super" -> c.magenta; "weak" -> c.ink; else -> if (hot) c.blockInk else c.ink }),
            )
        }
    }
}
