package io.github.krixhnarr.wilddex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.Tab
import io.github.krixhnarr.wilddex.core.Battle
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.Loot
import io.github.krixhnarr.wilddex.core.Progress
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.screens.ArenaScreen
import io.github.krixhnarr.wilddex.ui.screens.CardsScreen
import io.github.krixhnarr.wilddex.ui.screens.HomeScreen
import io.github.krixhnarr.wilddex.ui.screens.OpsScreen
import io.github.krixhnarr.wilddex.ui.screens.ProfileScreen
import io.github.krixhnarr.wilddex.ui.screens.ScanScreen
import io.github.krixhnarr.wilddex.ui.screens.SheetContent
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The whole app: sky, header, the current tab, bottom nav and overlays. */
@Composable
fun WildDexRoot(model: GameModel, animateSky: Boolean = true) {
    WildDexTheme(night = model.night) {
        val c = LocalWd.current
        Box(Modifier.fillMaxSize().background(c.skyBottom)) {
            SkyBackground(Modifier.fillMaxSize(), animate = animateSky)
            Column(Modifier.fillMaxSize()) {
                Header(model)
                AnimatedContent(
                    targetState = model.tab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(200)) { -it / 8 * dir } + fadeOut(tween(160)))
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

// ---------------------------------------------------------------- header
private val HexShape = GenericShape { s, _ ->
    val w = s.width; val h = s.height
    moveTo(w * 0.5f, 0f); lineTo(w, h * 0.25f); lineTo(w, h * 0.75f); lineTo(w * 0.5f, h); lineTo(0f, h * 0.75f); lineTo(0f, h * 0.25f); close()
}

@Composable
private fun Header(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val lv = Progress.opLevel(s)
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Raised(Modifier.weight(1f), shape = RoundedCornerShape(24.dp), edge = 3.dp, onClick = { model.go(Tab.Id) }) {
            Row(Modifier.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(HexShape).background(Brush.verticalGradient(listOf(Color(0xFFFFDE6E), c.sun2))),
                    contentAlignment = Alignment.Center,
                ) { Text("$lv", style = display(15.sp, c.onSun)) }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(s.name.ifBlank { "Operator" }, style = display(13.sp, c.hi), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Bar(Progress.xpProgress(s).toFloat(), c.accent, Modifier.fillMaxWidth(), height = 6.dp)
                }
            }
        }
        val crates = s.locker?.crates ?: 0
        Pill(Icon.Crate, "$crates", c.gold, hot = crates > 0, label = "Supply crates") { model.go(Tab.Id) }
        Pill(Icon.Credits, "${s.shards}", c.accent, label = "Credits") { model.go(Tab.Id) }
    }
}

@Composable
private fun Pill(icon: Icon, value: String, tint: Color, hot: Boolean = false, label: String, onClick: () -> Unit) {
    val c = LocalWd.current
    val p = if (hot) pulse() else 0f
    Raised(shape = RoundedCornerShape(20.dp), edge = 3.dp, onClick = onClick, modifier = Modifier.semantics { contentDescription = "$label $value" }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            LineIcon(icon, Modifier.size(18.dp).scale(1f + p * 0.12f), tint)
            Text(value, style = mono(14.sp, c.hi, FontWeight.Bold), modifier = Modifier.padding(start = 5.dp))
        }
    }
}

// ---------------------------------------------------------------- bottom nav
@Composable
private fun BottomNav(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val arenaBadge = s.ownedKeys().isNotEmpty() && s.battle?.rival?.let { it.date == Game.today() && it.won } != true
    val opsBadge = Game.unclaimedMissions(s) > 0 || Game.weeklyEvent(s).let { it.progress >= it.goal && !it.claimed }
    Box(
        Modifier.fillMaxWidth().background(c.surface).border(1.dp, c.line)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            NavItem(model, Tab.Home, Icon.Home, "Home")
            NavItem(model, Tab.Cards, Icon.Cards, "Cards")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val active = model.tab == Tab.Scan
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-14).dp)) {
                    Raised(
                        Modifier.size(64.dp, 62.dp).semantics { contentDescription = "Scan" },
                        shape = CircleShape, color = if (active) c.sun2 else c.sun, edge = 4.dp,
                        onClick = { model.go(Tab.Scan) },
                    ) {
                        Box(Modifier.size(58.dp).background(Brush.verticalGradient(listOf(Color(0xFFFFDE6E), c.sun2))), contentAlignment = Alignment.Center) {
                            LineIcon(Icon.Scan, Modifier.size(28.dp), c.onSun, 2.4f)
                        }
                    }
                    Text("SCAN", style = mono(9.sp, if (active) c.accentDeep else c.dim, FontWeight.Bold, 0.1f))
                }
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
    val col = if (active) c.accentDeep else c.dim
    Box(
        Modifier.weight(1f).height(64.dp)
            .clickable(remember { MutableInteractionSource() }, null) { model.go(tab) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) c.accentSoft else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                ) { LineIcon(icon, Modifier.size(24.dp), col) }
                if (badge) Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 1.dp).size(9.dp).clip(CircleShape).background(c.coral).border(1.5.dp, c.surface, CircleShape))
            }
            Text(label.uppercase(), style = mono(9.sp, col, FontWeight.Bold, 0.1f), modifier = Modifier.padding(top = 2.dp))
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
        Box(Modifier.fillMaxSize().background(Color(0x8C0A1428)).clickable(remember { MutableInteractionSource() }, null) { model.close() })
    }
    AnimatedVisibility(
        sheet != null,
        enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it },
        exit = slideOutVertically(tween(220)) { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val cur = shown ?: return@Box
            Box(
                Modifier.fillMaxWidth().heightIn(max = 10000.dp)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(top = 24.dp)
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(c.surface2)
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 22.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).clip(CircleShape).background(c.line2))
                    Spacer(Modifier.height(8.dp))
                    SheetContent(model, cur)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(c.surface)
                        .border(1.dp, c.line2, CircleShape).clickable { model.close() }.semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icon.Close, Modifier.size(16.dp), c.ink, 2.4f) }
            }
        }
    }
}

// ---------------------------------------------------------------- toast
@Composable
private fun ToastHost(model: GameModel) {
    val c = LocalWd.current
    val t = model.toast
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(t) {
        if (t == null) return@LaunchedEffect
        text = t.text; visible = true
        delay(2300)
        visible = false
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { -it } + fadeIn(), exit = fadeOut(tween(300))) {
            Text(
                text,
                Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(14.dp)).background(c.hi.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = mono(12.sp, c.surface, FontWeight.Bold, 0.06f), textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- level up
@Composable
private fun LevelUpHost(model: GameModel) {
    val c = LocalWd.current
    val up = model.levelUps.firstOrNull()
    // hold level-ups back while a reveal is on screen; they follow it
    val show = up != null && model.sheet !is Sheet.Reveal
    AnimatedVisibility(show, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        val cur = up ?: return@AnimatedVisibility
        LaunchedEffect(cur) { model.fx.levelUp(); model.fx.buzz(40, 50, 40, 50, 160); model.confetti++ }
        Box(
            Modifier.fillMaxSize().background(Color(0xB30A1428)).clickable(remember { MutableInteractionSource() }, null) {
                model.levelUps.removeAt(0)
            },
            contentAlignment = Alignment.Center,
        ) {
            val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "rays")
            val spin by inf.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
            Canvas(Modifier.size(360.dp).rotate(spin)) {
                for (i in 0 until 12) rotate(i * 30f) {
                    drawArc(Brush.radialGradient(listOf(c.sun.copy(alpha = 0.45f), Color.Transparent), radius = size.minDimension / 2), -4f, 8f, true)
                }
            }
            val pop = remember(cur) { Animatable(0.4f) }
            LaunchedEffect(cur) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
            Column(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                DotText("LEVEL UP", 26.dp, c.sun)
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.size(110.dp).clip(HexShape).background(Brush.verticalGradient(listOf(Color(0xFFFFE89A), c.sun2))),
                    contentAlignment = Alignment.Center,
                ) { Text("${cur.level}", style = display(52.sp, c.onSun)) }
                Spacer(Modifier.height(10.dp))
                Text("OPERATOR LEVEL ${cur.level}", style = mono(12.sp, Color.White, FontWeight.Bold, 0.16f))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("+${cur.credits}◆")
                    Chip("+1 SUPPLY CRATE", highlight = true)
                }
                Spacer(Modifier.height(26.dp))
                ChunkyButton("Nice!", kind = Btn.Primary) { model.levelUps.removeAt(0) }
            }
        }
    }
}

// ---------------------------------------------------------------- confetti
private class Bit(val x: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float)

@Composable
private fun Confetti(trigger: Int) {
    if (trigger == 0) return
    val colors = listOf(Color(0xFFFFC83D), Color(0xFFFF7A59), Color(0xFF4CC764), Color(0xFF2F9BEA), Color(0xFFC48AFF), Color(0xFFFF8AD8))
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
                drawRect(b.color.copy(alpha = (1f - t.value).coerceIn(0f, 1f) * 1.4f.coerceAtMost(1f)), Offset(x, y), Size(b.w * density / 2, b.w * density / 4))
            }
        }
    }
}
