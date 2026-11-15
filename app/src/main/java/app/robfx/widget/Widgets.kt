package app.robfx.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.robfx.MainActivity
import app.robfx.WatchRow
import app.robfx.WatchSorting
import app.robfx.calc.Format
import app.robfx.container
import app.robfx.data.Currencies
import app.robfx.data.HistoryPoint
import app.robfx.data.Quality
import app.robfx.data.WatchPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

// ---------- what each widget draws, as plain values (so the layouts can be tested without Android's widget host)

data class PairModel(
    val from: String, val to: String, val amount: Double, val rate: Double?, val change: Double?,
    val series: List<Double>?, val updatedAt: Long?, val stale: Boolean,
)

data class ListRow(val from: String, val to: String, val rate: Double?, val change: Double?)

data class ListModel(val rows: List<ListRow>, val updatedAt: Long?, val stale: Boolean)

data class ConvertRow(val code: String, val value: Double?)

data class ConvertModel(val from: String, val amount: Double, val rows: List<ConvertRow>, val updatedAt: Long?, val stale: Boolean)

// ---------- colours

class WidgetColors(val bg: ColorProvider, val text: ColorProvider, val sub: ColorProvider, val accent: ColorProvider)

private val Up = Color(0xFF2FBF71)
private val Down = Color(0xFFF0605D)

@Composable
fun widgetColors(cfg: WidgetConfig): WidgetColors {
    val a = 1f - cfg.transparency.coerceIn(0, 80) / 100f
    fun fixed(bg: Long, text: Long, sub: Long, accent: Long) =
        WidgetColors(ColorProvider(Color(bg).copy(alpha = a)), ColorProvider(Color(text)), ColorProvider(Color(sub)), ColorProvider(Color(accent)))
    return when (cfg.theme) {
        WidgetTheme.Black -> fixed(0xFF000000, 0xFFF2F2F4, 0xFF9A9AA0, 0xFF6B93F5)
        WidgetTheme.Dark -> fixed(0xFF1E2024, 0xFFF1F2F5, 0xFF9A9EA8, 0xFF6B93F5)
        WidgetTheme.Light -> fixed(0xFFF6F7FA, 0xFF0E0F12, 0xFF62666E, 0xFF2F63E0)
        WidgetTheme.System -> WidgetColors(
            androidx.glance.color.ColorProvider(Color(0xFFF6F7FA).copy(alpha = a), Color(0xFF000000).copy(alpha = a)),
            androidx.glance.color.ColorProvider(Color(0xFF0E0F12), Color(0xFFF2F2F4)),
            androidx.glance.color.ColorProvider(Color(0xFF62666E), Color(0xFF9A9AA0)),
            androidx.glance.color.ColorProvider(Color(0xFF2F63E0), Color(0xFF6B93F5)),
        )
        // Wallpaper colours come from the system as-is, so transparency doesn't apply to this theme.
        WidgetTheme.Dynamic -> WidgetColors(GlanceTheme.colors.widgetBackground, GlanceTheme.colors.onSurface, GlanceTheme.colors.onSurfaceVariant, GlanceTheme.colors.primary)
    }
}

// ---------- shared pieces

val FromKey = ActionParameters.Key<String>(MainActivity.EXTRA_FROM)
val ToKey = ActionParameters.Key<String>(MainActivity.EXTRA_TO)

fun tapAction(cfg: WidgetConfig, from: String?, to: String?): Action = when {
    cfg.tap == WidgetTap.Refresh -> actionRunCallback<RefreshAction>()
    cfg.tap == WidgetTap.Converter && from != null && to != null -> actionStartActivity<MainActivity>(actionParametersOf(FromKey to from, ToKey to to))
    else -> actionStartActivity<MainActivity>()
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.rates.refresh()
        Widgets.updateAll(context)
    }
}

object Widgets {
    suspend fun updateAll(context: Context) {
        runCatching { PairWidget().updateAll(context) }
        runCatching { WatchlistWidget().updateAll(context) }
        runCatching { ConvertWidget().updateAll(context) }
    }

    fun typeOf(providerClass: String?): WidgetType = when (providerClass) {
        WatchlistWidgetReceiver::class.java.name -> WidgetType.Watchlist
        ConvertWidgetReceiver::class.java.name -> WidgetType.Convert
        else -> WidgetType.Single
    }

    suspend fun configFor(context: Context, id: GlanceId, type: WidgetType): WidgetConfig {
        val c = context.container
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val saved = appWidgetId?.let { c.settings.widgetConfig(it) }
        if (saved != null) return saved.copy(type = type)
        val s = c.settings.settings.first()
        return WidgetConfig.default(type, s.from, s.to)
    }
}

internal fun timeText(ms: Long?, stale: Boolean): String =
    if (ms == null) "" else (if (stale) "Offline · " else "") + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

internal fun label(code: String, flags: Boolean): String = if (flags) "${Currencies.get(code).flag} $code".trim() else code

/** Widgets can't draw freely, so the sparkline is rendered to a small picture. Colour carries the direction. */
fun sparkBitmap(values: List<Double>, widthPx: Int, heightPx: Int, up: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(2), heightPx.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
    if (values.size < 2) return bmp
    val lo = values.min()
    val hi = values.max()
    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    val stroke = heightPx / 12f
    val pad = stroke * 1.5f
    fun x(i: Int) = pad + i.toFloat() / (values.size - 1) * (widthPx - pad * 2)
    fun y(v: Double) = pad + (1 - ((v - lo) / span)).toFloat() * (heightPx - pad * 2)
    val path = Path().apply {
        moveTo(x(0), y(values[0]))
        for (i in 1 until values.size) {
            val mx = (x(i - 1) + x(i)) / 2
            cubicTo(mx, y(values[i - 1]), mx, y(values[i]), x(i), y(values[i]))
        }
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = (if (up) Up else Down).let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
    }
    Canvas(bmp).drawPath(path, paint)
    return bmp
}

@Composable
private fun Frame(cfg: WidgetConfig, action: Action, content: @Composable (WidgetColors) -> Unit) {
    GlanceTheme {
        val colors = widgetColors(cfg)
        Box(GlanceModifier.fillMaxSize().background(colors.bg).cornerRadius(24.dp).clickable(action).padding(horizontal = 14.dp, vertical = 12.dp)) {
            content(colors)
        }
    }
}

@Composable
private fun ChangeText(change: Double?, size: Int = 12) {
    if (change == null) return
    Text(Format.percent(change), style = TextStyle(color = ColorProvider(if (change >= 0) Up else Down), fontSize = size.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

// ---------- single pair

@Composable
fun PairContent(cfg: WidgetConfig, m: PairModel, size: DpSize, density: Float, action: Action) = Frame(cfg, action) { c ->
    val value = when {
        m.rate == null -> "—"
        m.amount == 1.0 -> Format.rate(m.rate)
        else -> Format.amount(m.amount * m.rate)
    }
    val title = "${label(m.from, cfg.showFlags)} → ${label(m.to, cfg.showFlags)}"
    val change = if (cfg.showChange) m.change else null
    if (size.height < 90.dp) {
        // One row tall: everything on a single line.
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text("${m.from}/${m.to}", style = TextStyle(color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            Text(value, style = TextStyle(color = c.accent, fontSize = 20.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            ChangeText(change)
        }
    } else {
        Column(GlanceModifier.fillMaxSize()) {
            Text(title, style = TextStyle(color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (m.amount != 1.0 && m.rate != null) {
                Text("${Format.amount(m.amount)} ${m.from} =", style = TextStyle(color = c.sub, fontSize = 11.sp), maxLines = 1)
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = TextStyle(color = c.accent, fontSize = (if (value.length > 9) 22 else 28).sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                val series = m.series
                if (cfg.showSpark && series != null && series.size >= 2 && size.width >= 170.dp) {
                    val w = 64
                    val h = 28
                    Image(
                        provider = ImageProvider(sparkBitmap(series, (w * density).toInt(), (h * density).toInt(), series.last() >= series.first())),
                        contentDescription = null,
                        modifier = GlanceModifier.width(w.dp).height(h.dp),
                    )
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ChangeText(change)
                if (change != null) Text("  ${cfg.period.label.lowercase()}", style = TextStyle(color = c.sub, fontSize = 11.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                if (cfg.showUpdated) Text(timeText(m.updatedAt, m.stale), style = TextStyle(color = c.sub, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class PairWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val cfg = Widgets.configFor(context, id, WidgetType.Single)
        val pair = c.rates.pair(cfg.from, cfg.to)
        val series = if (cfg.showSpark || cfg.showChange) c.widgetSeries.get(cfg.from, cfg.to, cfg.period) else null
        val shown = if (cfg.period == WidgetPeriod.Day) series?.takeLast(5) else series
        val model = PairModel(cfg.from, cfg.to, cfg.amountValue, pair?.rate, WidgetMath.change(series, cfg.period), shown, pair?.updatedAt, pair?.quality == Quality.Stale)
        val density = context.resources.displayMetrics.density
        provideContent { PairContent(cfg, model, LocalSize.current, density, tapAction(cfg, cfg.from, cfg.to)) }
    }
}

// ---------- watchlist

/** How many list rows fit in a widget of this height. Plain rows rather than a scrolling list: taller widget, more rows. */
internal fun rowsThatFit(height: androidx.compose.ui.unit.Dp, chrome: Int, rowHeight: Int, max: Int): Int =
    ((height.value - chrome) / rowHeight).toInt().coerceIn(1, max)

@Composable
fun WatchlistContent(cfg: WidgetConfig, m: ListModel, size: DpSize, action: Action) = Frame(cfg, action) { c ->
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Markets", style = TextStyle(color = c.sub, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            if (cfg.showUpdated) Text(timeText(m.updatedAt, m.stale), style = TextStyle(color = c.sub, fontSize = 11.sp), maxLines = 1)
        }
        if (m.rows.isEmpty()) {
            Text("No pairs yet. Long-press to set up.", style = TextStyle(color = c.sub, fontSize = 13.sp))
        } else {
            // Narrow widgets drop the flags before anything gets cut off.
            val flagsFit = cfg.showFlags && size.width >= 230.dp
            val changeFits = cfg.showChange && size.width >= 215.dp
            Column(GlanceModifier.fillMaxWidth()) {
                m.rows.take(rowsThatFit(size.height, chrome = 50, rowHeight = 29, max = 9)).forEach { r ->
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(tapAction(cfg, r.from, r.to)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val flags = if (flagsFit) Currencies.get(r.from).flag + Currencies.get(r.to).flag + " " else ""
                        Text("$flags${r.from}/${r.to}", style = TextStyle(color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(r.rate?.let { Format.rate(it, 5) } ?: "\u2014", style = TextStyle(color = c.text, fontSize = 14.sp), maxLines = 1)
                        if (changeFits) {
                            Box(GlanceModifier.width(62.dp), contentAlignment = Alignment.CenterEnd) { ChangeText(r.change) }
                        }
                    }
                }
            }
        }
    }
}

class WatchlistWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val cfg = Widgets.configFor(context, id, WidgetType.Watchlist)
        val settings = c.settings.settings.first()
        val pairs: List<WatchPair> = (if (cfg.followMarkets) c.settings.watchlist.first() else cfg.ownPairs).take(12)
        val series: List<List<Double>?> = if (!cfg.showChange && !cfg.followMarkets) pairs.map { null } else {
            withTimeoutOrNull(9_000) { coroutineScope { pairs.map { p -> async { c.widgetSeries.get(p.from, p.to, cfg.period) } }.awaitAll() } } ?: pairs.map { null }
        }
        var rows = pairs.mapIndexed { i, p -> ListRow(p.from, p.to, c.rates.pair(p.from, p.to)?.rate, WidgetMath.change(series[i], cfg.period)) }
        if (cfg.followMarkets) {
            // Same order as the Markets screen, whatever sort is chosen there.
            val asWatch = pairs.mapIndexed { i, p ->
                WatchRow(p, c.rates.pair(p.from, p.to), series[i]?.mapIndexed { k, v -> HistoryPoint(LocalDate.ofEpochDay(k.toLong()), v) })
            }
            val order = WatchSorting.sort(asWatch, settings.sort, settings.sortDescending).map { it.pair }
            rows = order.mapNotNull { p -> rows.firstOrNull { it.from == p.from && it.to == p.to } }
        }
        val any = pairs.firstNotNullOfOrNull { c.rates.pair(it.from, it.to) }
        val model = ListModel(rows, any?.updatedAt, any?.quality == Quality.Stale)
        provideContent { WatchlistContent(cfg, model, LocalSize.current, tapAction(cfg, null, null)) }
    }
}

// ---------- quick convert

@Composable
fun ConvertContent(cfg: WidgetConfig, m: ConvertModel, size: DpSize, action: Action) = Frame(cfg, action) { c ->
    Column(GlanceModifier.fillMaxSize()) {
        Text("${Format.amount(m.amount)} ${label(m.from, cfg.showFlags)}", style = TextStyle(color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        if (m.rows.isEmpty()) {
            Text("No currencies yet. Long-press to set up.", style = TextStyle(color = c.sub, fontSize = 13.sp))
        } else {
            Column(GlanceModifier.fillMaxWidth()) {
                m.rows.take(rowsThatFit(size.height, chrome = if (cfg.showUpdated) 66 else 50, rowHeight = 27, max = WidgetConfig.MAX_TARGETS)).forEach { r ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(tapAction(cfg, m.from, r.code)), verticalAlignment = Alignment.CenterVertically) {
                        Text(label(r.code, cfg.showFlags), style = TextStyle(color = c.sub, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(r.value?.let { Format.amountCompact(it) } ?: "\u2014", style = TextStyle(color = c.text, fontSize = 16.sp, textAlign = TextAlign.End), maxLines = 1)
                    }
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (cfg.showUpdated) {
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(timeText(m.updatedAt, m.stale), style = TextStyle(color = c.sub, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class ConvertWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val cfg = Widgets.configFor(context, id, WidgetType.Convert)
        val amount = cfg.amountValue
        val rows = cfg.validTargets.map { code -> ConvertRow(code, c.rates.pair(cfg.from, code)?.rate?.let { it * amount }) }
        val any = cfg.validTargets.firstNotNullOfOrNull { c.rates.pair(cfg.from, it) }
        val model = ConvertModel(cfg.from, amount, rows, any?.updatedAt, any?.quality == Quality.Stale)
        provideContent { ConvertContent(cfg, model, LocalSize.current, tapAction(cfg, cfg.from, cfg.validTargets.firstOrNull())) }
    }
}

// ---------- receivers (one per entry in the launcher's widget picker)

abstract class ConfiguredReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // A removed widget's settings go with it.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { appWidgetIds.forEach { context.container.settings.removeWidgetConfig(it) } } finally { pending.finish() }
        }
    }
}

// Keeps the original class name, so a widget placed before this version carries on as a single-pair widget.
class RobFxWidgetReceiver : ConfiguredReceiver() { override val glanceAppWidget: GlanceAppWidget = PairWidget() }
class WatchlistWidgetReceiver : ConfiguredReceiver() { override val glanceAppWidget: GlanceAppWidget = WatchlistWidget() }
class ConvertWidgetReceiver : ConfiguredReceiver() { override val glanceAppWidget: GlanceAppWidget = ConvertWidget() }
