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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import io.github.krixhnarr.wilddex.ui.Eyebrow
import io.github.krixhnarr.wilddex.ui.HOLO
import io.github.krixhnarr.wilddex.ui.LocalWd
import io.github.krixhnarr.wilddex.ui.Panel
import io.github.krixhnarr.wilddex.ui.PillButton
import io.github.krixhnarr.wilddex.ui.Raised
import io.github.krixhnarr.wilddex.ui.Stars
import io.github.krixhnarr.wilddex.ui.TypeGlyph
import io.github.krixhnarr.wilddex.ui.blockInk
import io.github.krixhnarr.wilddex.ui.body
import io.github.krixhnarr.wilddex.ui.caption
import io.github.krixhnarr.wilddex.ui.display
import io.github.krixhnarr.wilddex.ui.headline
import io.github.krixhnarr.wilddex.ui.label
import io.github.krixhnarr.wilddex.ui.rarityBlock
import io.github.krixhnarr.wilddex.ui.title

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
        GridCells.Adaptive(76.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ScreenTitle("Cards", "$n of ${Dex.total} · $sectors sectors")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterPill("All", "$n", f.set == "all") { model.fx.click(); f.set = "all" }
                    for (set in Dex.sets) {
                        val got = set.keys.count { s.caught.containsKey(it) }
                        FilterPill("${set.icon} ${set.name}", "$got/${set.keys.size}", f.set == set.id) { model.fx.click(); f.set = set.id }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeFilter(null, f.type == null) { model.fx.click(); f.type = null }
                    for (t in Dex.typeIds) TypeFilter(t, f.type == t) {
                        model.fx.click()
                        f.type = if (f.type == t) null else t
                        if (f.type != null) Dex.types.getValue(t).let { model.say("${it.name} · ${it.desc}") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Eyebrow("Sort", modifier = Modifier.padding(end = 2.dp))
                    for ((id, lbl) in listOf("no" to "No.", "rarity" to "Rarity", "level" to "Level", "pwr" to "Power", "recent" to "Recent")) {
                        SmallToggle(lbl, f.sort == id) { model.fx.click(); f.sort = id }
                    }
                    SmallToggle(if (f.owned) "✓ Owned" else "Owned", f.owned) { model.fx.click(); f.owned = !f.owned }
                }
                f.selected?.let { Spacer(Modifier.height(10.dp)); InvDetail(model, s, Dex.byKey.getValue(it)) }
                if (list.isEmpty()) Text("No cards match these filters yet.", style = body(16.sp, c.ink), modifier = Modifier.padding(24.dp))
                Spacer(Modifier.height(4.dp))
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

/** Screen header: mono eyebrow over a display title. */
@Composable
fun ScreenTitle(title: String, sub: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = LocalWd.current
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (sub != null) Eyebrow(sub, modifier = Modifier.padding(bottom = 6.dp))
            Text(title, style = display(44.sp, c.ink))
        }
        trailing()
    }
}

/** pricing-tab style: selected = black pill. */
@Composable
private fun FilterPill(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.ink else c.canvas)
            .border(1.dp, if (selected) c.ink else c.hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = label(14.sp, if (selected) c.onInk else c.ink))
        Text("  $count", style = caption(11.sp, if (selected) c.onInk else c.ink))
    }
}

@Composable
private fun TypeFilter(type: String?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (selected) c.ink else c.surfaceSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (type == null) Text("All", style = label(12.sp, if (selected) c.onInk else c.ink))
        else TypeGlyph(type, Modifier.size(20.dp), if (selected) c.onInk else null)
    }
}

@Composable
fun SmallToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    Text(
        label,
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.ink else c.surfaceSoft).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = label(13.sp, if (selected) c.onInk else c.ink),
    )
}

@Composable
private fun Slot(model: GameModel, s: PlayerState, e: Entry, selected: Boolean, onClick: () -> Unit) {
    val c = LocalWd.current
    val rec = s.caught[e.k]
    val intel = rec == null && s.intel[e.k] == true
    val fresh = e.k in model.fresh
    val shape = RoundedCornerShape(14.dp)
    val bg = if (rec != null) rarityBlock(c, e.r) else c.surfaceSoft
    val ink = if (rec != null) blockInk(bg) else c.ink
    Raised(Modifier.aspectRatio(0.84f), shape = shape, color = bg, border = false, onClick = onClick) {
        Box(
            Modifier.fillMaxSize()
                .then(if (selected) Modifier.border(2.dp, c.ink, shape) else Modifier)
                .then(if (rec?.holo != null) Modifier.border(2.dp, Brush.sweepGradient(HOLO + HOLO.first()), shape) else Modifier),
        ) {
            Text(pad3(e.no), style = caption(9.sp, ink), modifier = Modifier.padding(start = 8.dp, top = 6.dp))
            AnimalArt(
                e, Modifier.align(Alignment.Center).padding(top = 8.dp).size(44.dp),
                look = when { rec != null -> ArtLook.Normal; intel -> ArtLook.Gray; else -> ArtLook.Silhouette },
                silhouette = c.hairline,
            )
            if (rec != null) Text(
                "Lv ${Game.cardLevel(rec)}", style = label(10.sp, bg),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(50)).background(ink).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            if (fresh) {
                val inf = rememberInfiniteTransition(label = "fresh")
                val a by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
                Box(Modifier.matchParentSize().border(3.dp, c.magenta.copy(alpha = 1f - a), shape))
            }
        }
    }
}

@Composable
private fun InvDetail(model: GameModel, s: PlayerState, e: Entry) {
    val c = LocalWd.current
    val rec = s.caught[e.k]
    val intel = rec == null && s.intel[e.k] == true
    val known = rec != null || intel
    val lv = rec?.let { Game.cardLevel(it) } ?: 1
    val st = Game.statsFor(e, lv)
    Panel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(if (rec != null) rarityBlock(c, e.r) else c.surfaceSoft), contentAlignment = Alignment.Center) {
                AnimalArt(e, Modifier.size(60.dp), look = when { rec != null -> ArtLook.Normal; intel -> ArtLook.Gray; else -> ArtLook.Silhouette }, silhouette = c.hairline)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Eyebrow("#${pad3(e.no)} · ${Dex.rarity.getValue(e.r).name}")
                Text(if (known) e.n else "Unknown", style = headline(20.sp, c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(if (known) e.s else "Not discovered yet", style = body(14.sp, c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rec != null) { Text("Lv $lv", style = label(13.sp, c.ink)); Stars(Game.stars(lv), 10.dp) }
                    for (t in Dex.typesOf(e)) TypeGlyph(t, Modifier.size(14.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in Game.STAT_KEYS) {
                Column(Modifier.weight(1f)) {
                    Text(if (rec != null) "${st[k]}" else "—", style = title(16.sp, c.ink))
                    Text(k.uppercase(), style = caption(9.sp, c.ink))
                    Bar(if (rec != null) st[k] / 100f else 0f, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), height = 3.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when { rec != null -> "Power ${st.pwr}"; intel -> "Look in ${e.h.lowercase()}"; else -> "Not found yet" },
                style = body(14.sp, c.ink, 480), modifier = Modifier.weight(1f), maxLines = 2,
            )
            val cost = if (rec != null) Game.upgradeCost(e, lv) else null
            if (cost != null) {
                PillButton("Lv ${lv + 1} · $cost◆", small = true, enabled = s.shards >= cost) { model.upgradeCard(e.k) }
                Spacer(Modifier.width(8.dp))
            }
            PillButton("Open", kind = Btn.Primary, small = true) { model.fx.click(); model.open(Sheet.Card(e.k)) }
        }
    }
}

