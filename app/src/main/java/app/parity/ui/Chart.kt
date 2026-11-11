package app.parity.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parity.calc.Format
import app.parity.data.HistoryPoint
import app.parity.data.Range
import app.parity.ui.theme.Pal
import java.time.format.DateTimeFormatter
import java.util.Locale

// Built per use so a locale change while the app is open is honoured.
private fun dateFmt(): DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Composable
fun RateChart(
    points: List<HistoryPoint>,
    loading: Boolean,
    error: Boolean,
    range: Range,
    onRange: (Range) -> Unit,
    onRetry: () -> Unit,
    onScrub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = Pal.current
    var scrub by remember(points) { mutableStateOf<Int?>(null) }
    val reveal = remember { Animatable(1f) }
    // Draw-in on every new series: it reads as the data arriving rather than the screen jumping.
    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
    }

    val shown = scrub?.let { points.getOrNull(it) } ?: points.lastOrNull()
    val first = points.firstOrNull()
    val change = if (shown != null && first != null && first.value != 0.0) shown.value / first.value - 1 else null

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = shown?.let { Format.rate(it.value) } ?: "—",
                    color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (change != null) {
                        Text(
                            Format.percent(change),
                            color = if (change >= 0) pal.positive else pal.negative,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                        Text("  ·  ", color = pal.subtext, fontSize = 13.sp)
                    }
                    Text(
                        text = if (scrub != null && shown != null) shown.date.format(dateFmt()) else "Past ${range.label}",
                        color = pal.subtext, fontSize = 13.sp, maxLines = 1,
                    )
                }
            }
            if (points.size > 1) {
                val hi = points.maxOf { it.value }
                val lo = points.minOf { it.value }
                Column(horizontalAlignment = Alignment.End) {
                    Text("H  ${Format.rate(hi)}", color = pal.subtext, fontSize = 12.sp)
                    Text("L  ${Format.rate(lo)}", color = pal.subtext, fontSize = 12.sp)
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                points.size >= 2 -> ChartCanvas(points, scrub, reveal.value, { scrub = it }, onScrub)
                error -> Text("Chart unavailable · Retry", color = pal.subtext, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onRetry).padding(16.dp))
                loading -> Text("Loading…", color = pal.subtext, fontSize = 14.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (r in Range.entries) {
                val sel = r == range
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) pal.key else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onRange(r) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(r.label, color = if (sel) pal.accent else pal.subtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChartCanvas(
    points: List<HistoryPoint>,
    scrub: Int?,
    reveal: Float,
    setScrub: (Int?) -> Unit,
    onScrub: () -> Unit,
) {
    val pal = Pal.current
    val lo = points.minOf { it.value }
    val hi = points.maxOf { it.value }
    // A flat series (a peg) would divide by zero; give it a sliver of height instead.
    val span = (hi - lo).takeIf { it > 0 } ?: (hi * 0.001).coerceAtLeast(1e-9)
    val accent = pal.accent
    val grid = pal.key

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(points) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun at(x: Float) = (((x - 14.dp.toPx()) / (size.width - 28.dp.toPx())) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
                    setScrub(at(down.position.x))
                    onScrub()
                    horizontalDrag(down.id) { ch ->
                        ch.consume()
                        val i = at(ch.position.x)
                        setScrub(i)
                    }
                    setScrub(null)
                }
            },
    ) {
        val padV = 10.dp.toPx()
        // Inset the series so the end-point marker isn't sliced by the screen edge; grid lines still run edge to edge.
        val padH = 14.dp.toPx()
        val w = size.width - padH * 2
        val h = size.height - padV * 2
        fun x(i: Int) = padH + if (points.size == 1) 0f else i.toFloat() / (points.size - 1) * w
        fun y(v: Double) = padV + (1 - ((v - lo) / span)).toFloat() * h

        // Hairline grid at min / mid / max: structure without chart-junk.
        for (f in listOf(0f, 0.5f, 1f)) {
            val gy = padV + f * h
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }

        val line = Path().apply {
            moveTo(x(0), y(points[0].value))
            // Midpoint-controlled cubics: smooth without overshooting past the real highs and lows.
            for (i in 1 until points.size) {
                val x0 = x(i - 1); val y0 = y(points[i - 1].value)
                val x1 = x(i); val y1 = y(points[i].value)
                val mx = (x0 + x1) / 2
                cubicTo(mx, y0, mx, y1, x1, y1)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(padH + w, size.height); lineTo(padH, size.height); close()
        }

        clipRect(right = padH + w * reveal + if (reveal >= 1f) padH else 0f) {
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0f))))
            drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val idx = scrub ?: (points.size - 1)
        if (scrub != null || reveal >= 1f) {
            val cx = x(idx); val cy = y(points[idx].value)
            if (scrub != null) drawLine(accent.copy(alpha = 0.45f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(accent.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = Offset(cx, cy))
            drawCircle(accent, radius = 4.5.dp.toPx(), center = Offset(cx, cy))
            drawCircle(pal.bg, radius = 1.8.dp.toPx(), center = Offset(cx, cy))
        }
    }
}
