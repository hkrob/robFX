package app.robfx.ui

import androidx.compose.foundation.Canvas
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
import app.robfx.Sheet
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
)

interface HomeActions {
    fun open(w: WatchPair)
    fun remove(w: WatchPair)
    fun toggleEditing()
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

        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Markets", color = pal.text, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(
                (ui.status?.let { "Updated ${timeOf(it.updatedAt)} · " } ?: "") + "Past week · tap a pair to convert",
                color = pal.subtext, fontSize = 14.sp,
            )
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp)) {
            if (ui.rows.isEmpty()) {
                item { Text("Nothing watched yet. Add a pair to follow it here.", color = pal.subtext, fontSize = 15.sp, modifier = Modifier.padding(24.dp)) }
            }
            items(ui.rows, key = { it.pair.key }) { row -> WatchRowItem(row, ui.editing, { actions.open(row.pair) }, { actions.remove(row.pair) }) }
        }

        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(pal.accent).clickable(onClick = actions::add).padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, tint = pal.onAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add pair", color = pal.onAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun WatchRowItem(row: WatchRow, editing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val pal = Pal.current
    val from = Currencies.get(row.pair.from)
    val to = Currencies.get(row.pair.to)
    val change = row.change
    val tone = when {
        change == null -> pal.subtext
        change >= 0 -> pal.positive
        else -> pal.negative
    }

    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = if (editing) onRemove else onOpen).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Icon(Icons.Outlined.RemoveCircle, "Remove ${row.pair.key}", tint = pal.negative, modifier = Modifier.padding(end = 14.dp).size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(from.flag + to.flag, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("${from.code}/${to.code}", color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text("${from.name} → ${to.name}", color = pal.subtext, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Sparkline(row.points?.map { it.value }.orEmpty(), tone, Modifier.width(72.dp).height(30.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(104.dp)) {
                // Drop significant digits before the type shrinks, and shrink before anything clips.
                FitText(
                    candidates = row.rate?.let { r -> listOf(6, 5, 4, 3).map { Format.rate(r.rate, it) }.distinct() } ?: listOf("—"),
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
