package app.robfx.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.robfx.HomeTab
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import app.robfx.Sheet
import app.robfx.WatchSort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import app.robfx.WatchRow
import app.robfx.calc.Format
import app.robfx.data.Currencies
import app.robfx.data.PairRate
import app.robfx.data.WatchPair
import app.robfx.ui.theme.Pal

data class HomeUi(
    val rows: List<WatchRow>,
    val status: PairRate?,
    val editing: Boolean,
    val refreshing: Boolean,
    val updateVersion: String? = null,
    val sort: WatchSort = WatchSort.Custom,
    val sortDescending: Boolean = false,
    val rateDecimals: Int = Format.AUTO,
    /** Set when every rate source failed its last attempt (or the newest rates are over 12 hours old). */
    val offline: String? = null,
)

/** The banner line for [HomeUi.offline], or null while rates are fresh. */
fun offlineText(sources: List<app.robfx.data.SourceStatus>, now: Long = System.currentTimeMillis()): String? {
    val newest = sources.mapNotNull { it.updatedAt }.maxOrNull()
    val allFailed = sources.isNotEmpty() && sources.all { !it.ok && it.error != null }
    val old = newest != null && now - newest > 12 * 3_600_000L
    return when {
        !allFailed && !old -> null
        newest == null -> "Can't reach any rate source"
        allFailed -> "Offline \u00B7 showing rates from ${ago(newest)}"
        else -> "Rates are from ${ago(newest)}"
    }
}

interface HomeActions {
    fun open(w: WatchPair)
    fun remove(w: WatchPair)
    fun toggleEditing()
    fun startEditing()
    fun tapSort(s: WatchSort)
    fun selectTab(t: HomeTab)
    fun add()
    fun refresh()
    fun sheet(s: Sheet)
}

@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions) {
    val pal = Pal.current
    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(ui.status) { actions.sheet(Sheet.Settings) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !ui.refreshing, onClick = actions::refresh), contentAlignment = Alignment.Center) {
                if (ui.refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = pal.accent, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, "Refresh rates", tint = pal.keyText)
            }
            IconBtn(if (ui.editing) Icons.Outlined.Check else Icons.Outlined.Edit, if (ui.editing) "Done editing" else "Edit list", actions::toggleEditing, if (ui.editing) pal.accent else pal.keyText)
            IconBtn(Icons.Outlined.Notifications, "Rate alerts", { actions.sheet(Sheet.Alerts) }, pal.keyText)
            IconBtn(Icons.Outlined.Settings, "Settings", { actions.sheet(Sheet.Settings) }, pal.keyText)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Short windows (landscape phones, split screen) can't spare a pinned button: it joins the end of the list instead.
            val short = maxHeight < 420.dp
            Column(Modifier.fillMaxSize()) {
                // The titles, status line and sort bar scroll away with the list, so the pairs get the whole screen.
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    item(key = "#tabs") { HomeTabs(HomeTab.Markets, actions::selectTab) }
                    item(key = "#status") {
                        Text(
                            (ui.status?.let { "Updated ${timeOf(it.updatedAt)} \u00B7 " } ?: "") + "Past week",
                            color = pal.subtext, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    ui.offline?.let { msg ->
                        item(key = "#offline") {
                            Row(
                                Modifier.padding(horizontal = 24.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(pal.key).clickable(enabled = !ui.refreshing, onClick = actions::refresh)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(pal.negative))
                                Spacer(Modifier.width(10.dp))
                                Text(msg, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(if (ui.refreshing) "Trying\u2026" else "Retry", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                    ui.updateVersion?.let { v ->
                        item(key = "#update") {
                            Row(
                                Modifier.padding(horizontal = 24.dp, vertical = 6.dp).clip(RoundedCornerShape(50)).background(pal.key)
                                    .clickable { actions.sheet(Sheet.SettingsUpdate) }.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("robFX $v is available", color = pal.text, fontSize = 14.sp)
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.clip(RoundedCornerShape(50)).background(pal.accent).padding(horizontal = 14.dp, vertical = 6.dp)) {
                                    Text("Update", color = pal.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (ui.rows.size > 1) item(key = "#sort") { SortBar(ui.sort, ui.sortDescending, actions::tapSort) }
                    if (ui.rows.isEmpty()) {
                        item(key = "#empty") { Text("Nothing watched yet. Add a pair to follow it here.", color = pal.subtext, fontSize = 15.sp, modifier = Modifier.padding(24.dp)) }
                    }
                    items(ui.rows, key = { it.pair.key }) { row -> WatchRowItem(row, ui.editing, ui.rateDecimals, { actions.open(row.pair) }, { actions.remove(row.pair) }, actions::startEditing) }
                    if (short) item(key = "#add") { AddPairButton(actions::add, Modifier.padding(top = 12.dp)) }
                }
                if (!short) AddPairButton(actions::add, Modifier.navigationBarsPadding().padding(vertical = 16.dp))
            }
        }
    }
}

@Composable
private fun AddPairButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pal = Pal.current
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(pal.accent).clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = pal.onAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add pair", color = pal.onAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchRowItem(row: WatchRow, editing: Boolean, decimals: Int, onOpen: () -> Unit, onRemove: () -> Unit, onLongPress: () -> Unit) {
    val view = LocalView.current
    val pal = Pal.current
    val from = Currencies.get(row.pair.from)
    val to = Currencies.get(row.pair.to)
    val change = row.change
    val tone = when {
        change == null -> pal.subtext
        change >= 0 -> pal.positive
        else -> pal.negative
    }

    // The name and the rate are the content; the sparkline is a nicety and is the first thing to go when space is short
    // (narrow phones, large fonts, edit mode's extra button).
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp.coerceAtMost(640.dp)
    val narrow = screenWidth < 380.dp
    val side = if (narrow) 20.dp else 24.dp
    val rateWidth = (if (narrow) 96.dp else 104.dp) * fontScale.coerceIn(1f, 1.3f)
    val rowWidth = screenWidth - side * 2 - (if (editing) 50.dp else 0.dp)
    // 150dp is two flags plus a six-letter pair code at 18sp bold: the part that must never be cut.
    val showSpark = rowWidth >= 150.dp * fontScale + rateWidth + 72.dp

    Column {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(
                    // In edit mode a stray tap on a row does nothing; only the red minus removes, so a slip can't delete a pair.
                    onClick = { if (!editing) onOpen() },
                    onLongClick = {
                        if (!editing) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress()
                        }
                    },
                )
                .padding(horizontal = side, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Box(Modifier.padding(end = 6.dp).size(44.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.RemoveCircle, "Remove ${row.pair.key}", tint = pal.negative, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(from.flag + to.flag, fontSize = 18.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(10.dp))
                    Text("${from.code}/${to.code}", color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Text("${from.name} \u2192 ${to.name}", color = pal.subtext, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showSpark) Sparkline(row.points?.map { it.value }.orEmpty(), tone, Modifier.padding(horizontal = 6.dp).width(60.dp).height(30.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(rateWidth)) {
                // Drop significant digits before the type shrinks, and shrink before anything clips.
                FitText(
                    candidates = row.rate?.let { r -> Format.fitting(r.rate, decimals, listOf(6, 5, 4, 3).map { Format.rate(r.rate, it) }.distinct()) } ?: listOf("—"),
                    color = pal.text, maxSize = 22, preferredMin = 18, hardMin = 13,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(change?.let { Format.percent(it) } ?: "···", color = tone, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp).background(pal.key))
    }
}

/** A line and nothing else: no axes, no fill, direction carried by colour. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: (hi * 0.001).coerceAtLeast(1e-9)
        val pad = 2.dp.toPx()
        fun x(i: Int) = pad + i.toFloat() / (values.size - 1) * (size.width - pad * 2)
        fun y(v: Double) = pad + (1 - ((v - lo) / span)).toFloat() * (size.height - pad * 2)
        val path = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) {
                val mx = (x(i - 1) + x(i)) / 2
                cubicTo(mx, y(values[i - 1]), mx, y(values[i]), x(i), y(values[i]))
            }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.8.dp.toPx(), center = Offset(x(values.lastIndex), y(values.last())))
    }
}

@Composable
private fun SortBar(sort: WatchSort, descending: Boolean, onTap: (WatchSort) -> Unit) {
    val pal = Pal.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SORT", color = pal.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
        WatchSort.entries.forEach { s ->
            val selected = s == sort
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) pal.key else Color.Transparent)
                    .clickable { onTap(s) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.label, color = if (selected) pal.accent else pal.subtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                // The arrow says which way: up is low to high (or A to Z), down is high to low.
                if (selected && s != WatchSort.Custom) {
                    Icon(
                        if (descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        contentDescription = if (descending) "Descending" else "Ascending",
                        tint = pal.accent, modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            }
        }
    }
}

/** The home screen's sections as big, light titles: the chosen one is full strength, the others wait quietly. */
@Composable
fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val pal = Pal.current
    // Scrollable, so three titles still work on a narrow phone or at a large font size.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        HomeTab.entries.forEach { t ->
            Text(
                t.title,
                color = if (t == selected) pal.text else pal.subtext.copy(alpha = 0.7f),
                fontSize = if (t == selected) 34.sp else 24.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 18.dp).clip(RoundedCornerShape(8.dp)).clickable { onSelect(t) },
            )
        }
    }
}
