package io.github.krixhnarr.wilddex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.Tab
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.Progress
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.screens.ArenaScreen
import io.github.krixhnarr.wilddex.ui.screens.CardsScreen
import io.github.krixhnarr.wilddex.ui.screens.HomeScreen
import io.github.krixhnarr.wilddex.ui.screens.OpsScreen
import io.github.krixhnarr.wilddex.ui.screens.ProfileScreen
import io.github.krixhnarr.wilddex.ui.screens.ScanScreen
import io.github.krixhnarr.wilddex.ui.screens.SheetContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The whole app: top bar, the current tab, bottom nav and overlays, on a plain canvas. */
@Composable
fun WildDexRoot(model: GameModel, @Suppress("UNUSED_PARAMETER") animateSky: Boolean = true) {
    WildDexTheme(night = model.night) {
        val c = LocalWd.current
        Box(Modifier.fillMaxSize().background(c.canvas)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(model)
                AnimatedContent(
                    targetState = model.tab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(240)) { it / 10 * dir } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(tween(180)) { -it / 12 * dir } + fadeOut(tween(140)))
                    },
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        Tab.Home -> HomeScreen(model)
                        Tab.Cards -> CardsScreen(model)
                        Tab.Scan -> ScanScreen(model)
                        Tab.Arena -> ArenaScreen(model)
                        Tab.Ops -> OpsScreen(model)
                        Tab.Id -> ProfileScreen(model)
                    }
                }
                BottomNav(model)
            }
            SheetHost(model)
            ToastHost(model)
            LevelUpHost(model)
            Confetti(model.confetti)
        }
    }
}

// ---------------------------------------------------------------- top bar (56dp, white, hairline)
@Composable
private fun TopBar(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val lv = Progress.opLevel(s)
    Column(Modifier.fillMaxWidth().background(c.canvas).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { model.go(Tab.Id) }.padding(end = 8.dp)
                    .semantics { contentDescription = "Operator level $lv" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                    Text("$lv", style = label(15.sp, c.onInk))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(s.name.ifBlank { "Operator" }, style = label(15.sp, c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Bar(Progress.xpProgress(s).toFloat(), modifier = Modifier.fillMaxWidth(0.8f), height = 3.dp)
                }
            }
            val crates = s.locker?.crates ?: 0
            Pill(Icon.Crate, "$crates", hot = crates > 0, label = "Supply crates") { model.go(Tab.Id) }
            Pill(Icon.Credits, "${s.shards}", label = "Credits") { model.go(Tab.Id) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
    }
}

@Composable
private fun Pill(icon: Icon, value: String, hot: Boolean = false, label: String, onClick: () -> Unit) {
    val c = LocalWd.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (hot) c.lime else c.surfaceSoft).clickable(onClick = onClick)
            .semantics { contentDescription = "$label $value" }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(icon, Modifier.size(16.dp), if (hot) c.blockInk else c.ink, 1.8f)
        Text(value, style = label(14.sp, if (hot) c.blockInk else c.ink), modifier = Modifier.padding(start = 6.dp))
    }
}

// ---------------------------------------------------------------- bottom nav
@Composable
private fun BottomNav(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val arenaBadge = s.ownedKeys().isNotEmpty() && s.battle?.rival?.let { it.date == Game.today() && it.won } != true
    val opsBadge = Game.unclaimedMissions(s) > 0 || Game.weeklyEvent(s).let { it.progress >= it.goal && !it.claimed }
    Column(Modifier.fillMaxWidth().background(c.canvas).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(model, Tab.Home, Icon.Home, "Home")
            NavItem(model, Tab.Cards, Icon.Cards, "Cards")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(c.ink).clickable { model.go(Tab.Scan) }
                        .semantics { contentDescription = "Scan" },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icon.Scan, Modifier.size(24.dp), c.onInk, 2f) }
            }
            NavItem(model, Tab.Arena, Icon.Arena, "Arena", badge = arenaBadge)
            NavItem(model, Tab.Ops, Icon.Orders, "Ops", badge = opsBadge)
        }
    }
}

@Composable
private fun RowScope.NavItem(model: GameModel, tab: Tab, icon: Icon, label: String, badge: Boolean = false) {
    val c = LocalWd.current
    val active = model.tab == tab
    Box(
        Modifier.weight(1f).height(68.dp)
            .clickable(remember { MutableInteractionSource() }, null) { model.go(tab) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                // selected = the primary surface, per the guide
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (active) c.ink else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                ) { LineIcon(icon, Modifier.size(22.dp), if (active) c.onInk else c.ink, 1.8f) }
                if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 2.dp).size(8.dp).clip(CircleShape).background(c.magenta))
            }
            Text(label, style = caption(10.sp, c.ink), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------------------------------------------------------------- sheets
@Composable
private fun SheetHost(model: GameModel) {
    val c = LocalWd.current
    val sheet = model.sheet
    var shown by remember { mutableStateOf<Sheet?>(null) }
    if (sheet != null) shown = sheet
    BackHandler(enabled = sheet != null) { model.close() }
    AnimatedVisibility(sheet != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null) { model.close() })
    }
    AnimatedVisibility(
        sheet != null,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val cur = shown ?: return@Box
            Box(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars).padding(top = 24.dp)
                    .clip(RoundedCornerShape(topStart = R8.lg, topEnd = R8.lg))
                    .background(c.canvas)
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 28.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(CircleShape).background(c.hairline))
                    Spacer(Modifier.height(12.dp))
                    SheetContent(model, cur)
                }
                IconCircle(Icon.Close, "Close", Modifier.align(Alignment.TopEnd).padding(12.dp)) { model.close() }
            }
        }
    }
}

// ---------------------------------------------------------------- toast: a black pill, like the marquee strip
@Composable
private fun ToastHost(model: GameModel) {
    val c = LocalWd.current
    val t = model.toast
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(t) {
        if (t == null) return@LaunchedEffect
        text = t.text; visible = true
        kotlinx.coroutines.delay(2300)
        visible = false
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { -it } + fadeIn(), exit = fadeOut(tween(300))) {
            Text(
                text.lowercase().replaceFirstChar(Char::uppercaseChar),
                Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(50)).background(c.ink).padding(horizontal = 18.dp, vertical = 11.dp),
                style = label(14.sp, c.onInk), textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- level up: a full navy colour block
@Composable
private fun LevelUpHost(model: GameModel) {
    val c = LocalWd.current
    val up = model.levelUps.firstOrNull()
    // hold level-ups back while a reveal is on screen; they follow it
    val show = up != null && model.sheet !is Sheet.Reveal
    AnimatedVisibility(show, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
        val cur = up ?: return@AnimatedVisibility
        LaunchedEffect(cur) { model.fx.levelUp(); model.fx.buzz(40, 50, 40, 50, 160); model.confetti++ }
        val pop = remember(cur) { Animatable(0.85f) }
        LaunchedEffect(cur) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        Box(
            Modifier.fillMaxSize().background(c.navy).clickable(remember { MutableInteractionSource() }, null) { model.levelUps.removeAt(0) }
                .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(28.dp),
        ) {
            Column(Modifier.align(Alignment.CenterStart).graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }) {
                Eyebrow("Operator level up", Color.White)
                Text("Level\n${cur.level}", style = display(96.sp, Color.White), modifier = Modifier.padding(top = 12.dp))
                Text("You earned a supply crate and credits.", style = body(18.sp, Color.White), modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("+${cur.credits}◆", fill = c.lime)
                    Chip("+1 supply crate", fill = c.lilac)
                }
            }
            PillButton("Continue", Modifier.align(Alignment.BottomStart), onBlock = true) { model.levelUps.removeAt(0) }
        }
    }
}

// ---------------------------------------------------------------- confetti (pastel paper)
private class Bit(val x: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float)

@Composable
private fun Confetti(trigger: Int) {
    if (trigger == 0) return
    val c = LocalWd.current
    val colors = c.blocks + c.magenta + c.ink
    val bits = remember(trigger) {
        List(90) {
            val a = (Random.nextFloat() * 0.8f + 0.1f) * PI.toFloat()
            val sp = 0.7f + Random.nextFloat() * 0.9f
            Bit(0.5f + (Random.nextFloat() - 0.5f) * 0.3f, cos(a) * sp * 0.6f, -sin(a) * sp * 1.4f, Random.nextFloat() * 720 - 360, colors.random(), 6f + Random.nextFloat() * 6f)
        }
    }
    val t = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(2200, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val time = t.value * 2.2f
        for (b in bits) {
            val x = (b.x + b.vx * time) * size.width
            val y = size.height * 0.55f + (b.vy * time + 0.9f * time * time) * size.height * 0.5f
            rotate(b.spin * time, Offset(x, y)) {
                drawRect(b.color.copy(alpha = (1f - t.value).coerceIn(0f, 1f)), Offset(x, y), Size(b.w * density / 2, b.w * density / 4))
            }
        }
    }
}

