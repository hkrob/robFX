package app.robfx.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.robfx.ConverterViewModel
import app.robfx.HistoryState
import app.robfx.data.Range
import app.robfx.data.Settings
import app.robfx.Sheet
import app.robfx.calc.Format
import app.robfx.data.Currencies
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.ui.theme.Pal
import java.text.DateFormat
import java.util.Date

/** Everything the converter draws, as plain values, so it can be rendered without a ViewModel (previews, screenshot tests). */
data class ConverterUi(
    val settings: Settings,
    val expr: String,
    val pair: PairRate?,
    val converted: Double?,
    val history: HistoryState,
    val keypadOpen: Boolean,
    val refreshing: Boolean,
    val watched: Boolean = false,
)

interface ConverterActions {
    fun back()
    fun toggleWatch()
    fun key(k: String)
    fun swap()
    fun refresh()
    fun setRange(r: Range)
    fun retryHistory()
    fun toggleKeypad()
    fun open(sheet: Sheet)
}

@Composable
fun ConverterScreen(vm: ConverterViewModel, showSheets: Boolean = true, requestNotifications: () -> Unit) {
    val ui = ConverterUi(
        settings = vm.settings.collectAsState().value,
        expr = vm.expr.collectAsState().value,
        pair = vm.pair.collectAsState().value,
        converted = vm.converted.collectAsState().value,
        history = vm.history.collectAsState().value,
        keypadOpen = vm.keypadOpen.collectAsState().value,
        refreshing = vm.refreshing.collectAsState().value,
        watched = vm.watchlist.collectAsState().value.any { it.from == vm.settings.value.from && it.to == vm.settings.value.to },
    )
    val actions = remember(vm) {
        object : ConverterActions {
            override fun back() = vm.goHome()
            override fun toggleWatch() = vm.toggleWatch()
            override fun key(k: String) = vm.key(k)
            override fun swap() = vm.swap()
            override fun refresh() = vm.refresh()
            override fun setRange(r: Range) = vm.setRange(r)
            override fun retryHistory() = vm.retryHistory()
            override fun toggleKeypad() = vm.toggleKeypad()
            override fun open(sheet: Sheet) = vm.open(sheet)
        }
    }
    ConverterContent(ui, actions)
    if (showSheets) Sheets(vm, requestNotifications)
}

@Composable
fun ConverterContent(ui: ConverterUi, actions: ConverterActions) {
    val pal = Pal.current
    val settings = ui.settings
    val view = LocalView.current

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bg)) {
        // Android 17 ignores orientation and resizability limits on big screens, so landscape and tablets are a given:
        // when the window is wider than it is tall, the keypad moves beside the display instead of under it.
        val screenW = maxWidth
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val compact = maxHeight < 520.dp
        val onScrub = { if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

        @Composable
        fun ColumnScope.Main() {
            TopBar(
                pair = ui.pair,
                watched = ui.watched,
                onBack = actions::back,
                onWatch = actions::toggleWatch,
                onStatus = { actions.open(Sheet.Settings) },
                onAlerts = { actions.open(Sheet.Alerts) },
                onSettings = { actions.open(Sheet.Settings) },
            )
            Display(
                fromCode = settings.from,
                toCode = settings.to,
                expr = ui.expr,
                converted = ui.converted,
                onFrom = { actions.open(Sheet.FromPicker) },
                onTo = { actions.open(Sheet.ToPicker) },
                compact = compact,
            )
            RateLine(settings.from, settings.to, ui.pair, ui.refreshing, onRefresh = actions::refresh)
            RateChart(
                points = ui.history.points,
                loading = ui.history.loading,
                error = ui.history.error,
                range = settings.range,
                onRange = actions::setRange,
                onRetry = actions::retryHistory,
                onScrub = onScrub,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.weight(1f).fillMaxHeight()) { Main() }
                BoxWithConstraints(Modifier.width(minOf(screenW * 0.4f, 380.dp)).fillMaxHeight().navigationBarsPadding()) {
                    val keyH = ((maxHeight - 4.dp) / 5).coerceIn(44.dp, 150.dp)
                    Keypad(
                        keyHeight = keyH,
                        haptics = settings.haptics,
                        onKey = actions::key,
                        onSwap = actions::swap,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Main()

                // The chevron from the reference: fold the keypad away to give the chart the screen.
                val rot by animateFloatAsState(if (ui.keypadOpen) 0f else 180f, label = "chev")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable(onClick = actions::toggleKeypad),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ExpandMore, contentDescription = if (ui.keypadOpen) "Hide keypad" else "Show keypad", tint = pal.subtext, modifier = Modifier.rotate(rot))
                }

                AnimatedVisibility(
                    visible = ui.keypadOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    BoxWithConstraints {
                        // Keys scale down on short screens so the chart never collapses to nothing.
                        val keyH: Dp = (maxHeight.coerceAtLeast(600.dp) * 0.082f).coerceIn(52.dp, 76.dp)
                        Keypad(
                            keyHeight = keyH,
                            haptics = settings.haptics,
                            onKey = actions::key,
                            onSwap = actions::swap,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                }
                if (!ui.keypadOpen) Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun TopBar(pair: PairRate?, watched: Boolean, onBack: () -> Unit, onWatch: () -> Unit, onStatus: () -> Unit, onAlerts: () -> Unit, onSettings: () -> Unit) {
    val pal = Pal.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.AutoMirrored.Outlined.ArrowBack, "Back to markets", onBack, pal.keyText)
        StatusPill(pair, onStatus)
        Spacer(Modifier.weight(1f))
        IconBtn(if (watched) Icons.Filled.Star else Icons.Outlined.StarBorder, if (watched) "Remove from markets" else "Add to markets", onWatch, if (watched) pal.accent else pal.keyText)
        IconBtn(Icons.Outlined.Notifications, "Rate alerts", onAlerts, pal.keyText)
        IconBtn(Icons.Outlined.Settings, "Settings", onSettings, pal.keyText)
    }
}

@Composable
internal fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

@Composable
internal fun StatusPill(pair: PairRate?, onClick: () -> Unit) {
    val pal = Pal.current
    val (color, text) = when (pair?.quality) {
        null -> pal.subtext to "No data"
        Quality.Consensus -> pal.positive to "${pair.perSource.size} sources agree"
        Quality.Single -> pal.warning to "1 source"
        Quality.Divergent -> pal.warning to "Sources differ ${"%.1f".format(pair.spread * 100)}%"
        Quality.Stale -> pal.negative to "Offline · ${ago(pair.updatedAt)}"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = pal.subtext, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Display(
    fromCode: String,
    toCode: String,
    expr: String,
    converted: Double?,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    compact: Boolean = false,
) {
    val pal = Pal.current
    val ctx = LocalContext.current
    val from = Currencies.get(fromCode)
    val to = Currencies.get(toCode)
    val result = converted?.let { Format.amount(it) } ?: "—"
    val resultCandidates = converted?.let { listOf(Format.amount(it), Format.amountCompact(it)).distinct() } ?: listOf(result)

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        AmountRow(from.code, from.name, from.flag, listOf(Format.pretty(expr)), pal.text, onFrom, null, compact)
        Spacer(Modifier.height(4.dp))
        AmountRow(to.code, to.name, to.flag, resultCandidates, pal.accent, onTo, onLongAmount = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Converted amount", result.replace(",", "")))
            Toast.makeText(ctx, "Copied $result", Toast.LENGTH_SHORT).show()
        }, compact = compact)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmountRow(code: String, name: String, flag: String, amount: List<String>, color: androidx.compose.ui.graphics.Color, onCurrency: () -> Unit, onLongAmount: (() -> Unit)?, compact: Boolean = false) {
    val pal = Pal.current
    Row(Modifier.fillMaxWidth().height(if (compact) 60.dp else 84.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onCurrency)
                .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flag, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(code, color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.ExpandMore, null, tint = pal.subtext, modifier = Modifier.size(20.dp))
            }
            Text(name, color = pal.subtext, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
        }
        // Fewer decimals before smaller type, smaller type before clipping: see FitText.
        FitText(
            candidates = amount,
            color = color,
            maxSize = if (compact) 38 else 52,
            preferredMin = if (compact) 28 else 36,
            hardMin = 18,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = {}, onLongClick = onLongAmount),
        )
    }
}

@Composable
private fun RateLine(from: String, to: String, pair: PairRate?, refreshing: Boolean, onRefresh: () -> Unit) {
    val pal = Pal.current
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pair?.let { "1 $from = ${Format.rate(it.rate)} $to" } ?: "Waiting for rates…",
            color = pal.subtext, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1,
        )
        pair?.let { Text(timeOf(it.updatedAt), color = pal.subtext, fontSize = 12.sp) }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !refreshing, onClick = onRefresh), contentAlignment = Alignment.Center) {
            if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), color = pal.accent, strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, "Refresh rates", tint = pal.subtext, modifier = Modifier.size(18.dp))
        }
    }
}

internal fun timeOf(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

internal fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 48 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}
