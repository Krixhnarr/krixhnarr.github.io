package io.github.krixhnarr.wilddex.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krixhnarr.wilddex.GameModel
import io.github.krixhnarr.wilddex.Sheet
import io.github.krixhnarr.wilddex.core.Dex
import io.github.krixhnarr.wilddex.core.Entry
import io.github.krixhnarr.wilddex.core.Game
import io.github.krixhnarr.wilddex.core.PlayerState
import io.github.krixhnarr.wilddex.live
import io.github.krixhnarr.wilddex.ui.AnimalArt
import io.github.krixhnarr.wilddex.ui.ArtLook
import io.github.krixhnarr.wilddex.ui.Bar
import io.github.krixhnarr.wilddex.ui.Btn
import io.github.krixhnarr.wilddex.ui.ChunkyButton
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.Stars
import io.github.krixhnarr.wilddex.ui.TypeGlyph
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.hex
import io.github.krixhnarr.wilddex.ui.mono
import io.github.krixhnarr.wilddex.ui.typeColor

val RARITY_COLOR = mapOf(1 to Color(0xFF7D8C99), 2 to Color(0xFF2E9B45), 3 to Color(0xFF2F9BEA), 4 to Color(0xFFE0A100))
fun pad3(n: Int) = n.toString().padStart(3, '0')

private fun sorted(s: PlayerState, list: List<Entry>, sort: String): List<Entry> {
    fun lv(e: Entry) = s.caught[e.k]?.let { Game.cardLevel(it) } ?: 0
    fun pwr(e: Entry) = if (s.caught[e.k] != null) Game.statsFor(e, lv(e)).pwr else -1
    return when (sort) {
        "rarity" -> list.sortedWith(compareByDescending<Entry> { it.r }.thenBy { it.no })
        "level" -> list.sortedWith(compareByDescending<Entry> { lv(it) }.thenBy { it.no })
        "pwr" -> list.sortedWith(compareByDescending<Entry> { pwr(it) }.thenBy { it.no })
        "recent" -> list.sortedWith(compareByDescending<Entry> { s.caught[it.k]?.last ?: 0 }.thenBy { it.no })
        else -> list.sortedBy { it.no }
    }
}

@Composable
fun CardsScreen(model: GameModel) {
    val c = LocalWd.current
    val s = model.live
    val f = model.binder
    val n = s.ownedKeys().size
    val sectors = Dex.sets.count { set -> set.keys.all { s.caught.containsKey(it) } }

    var list = if (f.set == "all") Dex.entries else Dex.entries.filter { it.set == f.set }
    f.type?.let { t -> list = list.filter { Dex.typesOf(it).contains(t) } }
    if (f.owned) list = list.filter { s.caught.containsKey(it.k) }
    list = sorted(s, list, f.sort)
    if (list.none { it.k == f.selected }) f.selected = (list.firstOrNull { s.caught.containsKey(it.k) } ?: list.firstOrNull())?.k

    LazyVerticalGrid(
        GridCells.Adaptive(74.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenTitle("Cards", "$n/${Dex.total} · $sectors SECTORS")
                // sectors
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("All", "$n/${Dex.total}", f.set == "all") { model.fx.click(); f.set = "all" }
                    for (set in Dex.sets) {
                        val got = set.keys.count { s.caught.containsKey(it) }
                        FilterChip("${set.icon} ${set.name}", "$got/${set.keys.size}", f.set == set.id, done = got == set.keys.size) { model.fx.click(); f.set = set.id }
                    }
                }
                // affinity filter
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeFilter(null, f.type == null) { model.fx.click(); f.type = null }
                    for (t in Dex.typeIds) TypeFilter(t, f.type == t) {
                        model.fx.click()
                        f.type = if (f.type == t) null else t
                        if (f.type != null) Dex.types.getValue(t).let { model.say("${it.name.uppercase()} · ${it.desc}") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SORT", style = mono(10.sp, c.dim, FontWeight.Bold, 0.1f))
                    for ((id, label) in listOf("no" to "No.", "rarity" to "Rarity", "level" to "Level", "pwr" to "Power", "recent" to "Recent")) {
                        SmallToggle(label, f.sort == id) { model.fx.click(); f.sort = id }
                    }
                }
                Row(Modifier.padding(vertical = 4.dp)) {
                    SmallToggle(if (f.owned) "✓ Owned only" else "Owned only", f.owned) { model.fx.click(); f.owned = !f.owned }
                }
                f.selected?.let { Spacer(Modifier.height(6.dp)); InvDetail(model, s, Dex.byKey.getValue(it)) }
                if (list.isEmpty()) Text("No cards match these filters yet.", style = mono(12.sp, c.dim), modifier = Modifier.padding(24.dp))
            }
        }
        items(list, key = { it.k }) { e ->
            Slot(model, s, e, selected = f.selected == e.k) {
                if (f.selected == e.k) { model.fx.click(); model.open(Sheet.Card(e.k)) } // second tap opens the card
                else { model.fx.click(); f.selected = e.k }
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, sub: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = LocalWd.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = display(28.sp, c.hi))
            if (sub != null) Text(sub.uppercase(), style = mono(10.sp, c.dim, FontWeight.Bold, 0.12f))
        }
        trailing()
    }
}

@Composable
private fun FilterChip(label: String, count: String, selected: Boolean, done: Boolean = false, onClick: () -> Unit) {
    val c = LocalWd.current
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) c.accent else c.surface)
            .border(1.dp, if (done && !selected) c.grass else c.line2, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = display(12.sp, if (selected) Color.White else c.hi))
        Text(" $count", style = mono(10.sp, if (selected) Color.White.copy(alpha = 0.8f) else c.dim))
    }
}

@Composable
private fun TypeFilter(type: String?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    val tc = type?.let { hex(Dex.types.getValue(it).color) } ?: c.accent
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (selected) tc else c.surface)
            .border(1.5.dp, if (selected) tc else c.line2, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (type == null) Text("ALL", style = mono(10.sp, if (selected) Color.White else c.ink, FontWeight.Bold))
        else TypeGlyph(type, Modifier.size(20.dp), if (selected) Color.White else null)
    }
}

@Composable
fun SmallToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    Text(
        label,
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) c.accentSoft else c.surface)
            .border(1.dp, if (selected) c.accent else c.line, RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        style = mono(11.sp, if (selected) c.accentDeep else c.ink, FontWeight.Bold),
    )
}

@Composable
private fun Slot(model: GameModel, s: PlayerState, e: Entry, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    val rec = s.caught[e.k]
    val intel = rec == null && s.intel[e.k] == true
    val tc = typeColor(e)
    val rc = RARITY_COLOR.getValue(e.r)
    val fresh = e.k in model.fresh
    val shape = RoundedCornerShape(14.dp)
    Raised(
        Modifier.aspectRatio(0.86f),
        shape = shape, edge = 3.dp,
        color = if (rec != null) lerpC(c.surface, tc, 0.14f) else c.slot,
        onClick = onClick,
    ) {
        Box(
            Modifier.fillMaxSize().then(if (selected) Modifier.border(2.5.dp, c.accent, shape) else Modifier)
                .then(if (rec?.holo != null) Modifier.border(2.dp, Brush.sweepGradient(io.github.krixhnarr.wilddex.ui.HOLO + io.github.krixhnarr.wilddex.ui.HOLO.first()), shape) else Modifier),
        ) {
            Text(pad3(e.no), style = mono(9.sp, c.dim, FontWeight.Bold), modifier = Modifier.padding(start = 7.dp, top = 5.dp))
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).clip(CircleShape).background(rc))
            AnimalArt(
                e, Modifier.align(Alignment.Center).padding(top = 8.dp).size(44.dp),
                look = when { rec != null -> ArtLook.Normal; intel -> ArtLook.Gray; else -> ArtLook.Silhouette },
                silhouette = c.slotHi.copy(alpha = 1f).let { if (c.night) Color(0xFF1B2748) else Color(0xFFBFCEDA) },
            )
            if (rec != null) Text(
                "x${rec.count}", style = mono(9.sp, c.hi, FontWeight.Bold),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(c.surface.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
            if (fresh) {
                val inf = rememberInfiniteTransition(label = "fresh")
                val a by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
                Box(Modifier.matchParentSize().border(3.dp, c.sun.copy(alpha = 1f - a), shape))
            }
        }
    }
}

private fun lerpC(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

@Composable
private fun InvDetail(model: GameModel, s: PlayerState, e: Entry) {
    val c = LocalWd.current
    val rec = s.caught[e.k]
    val intel = rec == null && s.intel[e.k] == true
    val known = rec != null || intel
    val lv = rec?.let { Game.cardLevel(it) } ?: 1
    val st = Game.statsFor(e, lv)
    val tc = typeColor(e)
    Panel(Modifier.fillMaxWidth().padding(bottom = 6.dp), tint = if (known) tc else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                val inf = rememberInfiniteTransition(label = "ring")
                val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "spin")
                Box(Modifier.matchParentSize().rotate(spin).drawBehind {
                    drawCircle(tc.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 7f))))
                })
                Box(Modifier.size(78.dp).clip(CircleShape).background(Brush.radialGradient(listOf(tc.copy(alpha = if (known) 0.3f else 0.08f), Color.Transparent))))
                AnimalArt(e, Modifier.size(64.dp), look = when { rec != null -> ArtLook.Normal; intel -> ArtLook.Gray; else -> ArtLook.Silhouette }, silhouette = if (c.night) Color(0xFF0E1630) else Color(0xFFB8C7D3))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (known) e.n else "? ? ?", style = display(19.sp, c.hi), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (known) e.s else "#${pad3(e.no)} · undiscovered", style = mono(10.sp, c.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Dex.rarity.getValue(e.r).name.uppercase(), style = mono(10.sp, RARITY_COLOR.getValue(e.r), FontWeight.Bold, 0.08f))
                    if (rec != null) {
                        Text("  LV $lv  ", style = mono(10.sp, c.ink, FontWeight.Bold))
                        Stars(Game.stars(lv), 9.dp, c.line2)
                    }
                }
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (t in Dex.typesOf(e)) {
                        val ty = Dex.types.getValue(t)
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(hex(ty.color).copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeGlyph(t, Modifier.size(12.dp))
                            Text(" ${ty.name}", style = mono(10.sp, c.readable(hex(ty.color)), FontWeight.Bold))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in Game.STAT_KEYS) {
                Column(Modifier.weight(1f)) {
                    Text(if (rec != null) "+${st[k]}" else "??", style = display(14.sp, c.hi))
                    Text(k.uppercase(), style = mono(9.sp, c.dim, FontWeight.Bold))
                    Bar(if (rec != null) st[k] / 100f else 0f, tc, Modifier.fillMaxWidth().padding(top = 3.dp), height = 5.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when { rec != null -> "PWR ${st.pwr}"; intel -> "Find it in ${e.h.lowercase()}"; else -> "Not found yet" },
                style = mono(11.sp, c.ink, FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 2,
            )
            val cost = if (rec != null) Game.upgradeCost(e, lv) else null
            if (cost != null) {
                ChunkyButton("Lv ${lv + 1} · $cost◆", small = true, enabled = s.shards >= cost) { model.upgradeCard(e.k) }
                Spacer(Modifier.width(8.dp))
            }
            ChunkyButton("Open", kind = Btn.Primary, small = true) { model.fx.click(); model.open(Sheet.Card(e.k)) }
        }
    }
}

