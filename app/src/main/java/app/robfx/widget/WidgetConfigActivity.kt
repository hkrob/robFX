package app.robfx.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import app.robfx.container
import app.robfx.data.Currencies
import app.robfx.data.Settings
import app.robfx.ui.AppSheet
import app.robfx.ui.CurrencyPicker
import app.robfx.ui.Header
import app.robfx.ui.ToggleRow
import app.robfx.ui.theme.Pal
import app.robfx.ui.theme.RobFxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Opens when a widget is added, and again from the launcher's "Reconfigure" on Android 12 and later. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must leave no half-added widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val type = Widgets.typeOf(runCatching { AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className }.getOrNull())

        setContent {
            // Loaded off the main thread; the screen appears once the saved settings (or defaults) are known.
            val loaded by produceState<Pair<Settings, WidgetConfig>?>(null) {
                val s = container.settings.settings.first()
                value = s to ((container.settings.widgetConfig(id) ?: WidgetConfig.default(type, s.from, s.to)).copy(type = type))
            }
            val settings by container.settings.settings.collectAsState(initial = null)
            val (appSettings, initial) = loaded ?: return@setContent
            RobFxTheme(appSettings.theme, appSettings.dynamicColor) {
                var cfg by remember { mutableStateOf(initial) }
                WidgetConfigScreen(cfg, (settings ?: appSettings).recents, onChange = { cfg = it }, onCancel = ::finish) {
                    lifecycleScope.launch {
                        container.settings.setWidgetConfig(id, cfg)
                        runCatching {
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(id)
                            when (type) {
                                WidgetType.Single -> PairWidget().update(this@WidgetConfigActivity, glanceId)
                                WidgetType.Watchlist -> WatchlistWidget().update(this@WidgetConfigActivity, glanceId)
                                WidgetType.Convert -> ConvertWidget().update(this@WidgetConfigActivity, glanceId)
                            }
                        }
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }
                }
            }
        }
    }
}

private sealed interface Pick {
    data object From : Pick
    data object To : Pick
    data object PairFrom : Pick
    data class PairTo(val from: String) : Pick
    data object Target : Pick
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigScreen(cfg: WidgetConfig, recents: List<String>, onChange: (WidgetConfig) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    val pal = Pal.current
    var pick by remember { mutableStateOf<Pick?>(null) }

    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${cfg.type.title} widget", color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Text("These settings apply to this widget only", color = pal.subtext, fontSize = 13.sp)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Cancel", tint = pal.keyText)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (cfg.type) {
                WidgetType.Single -> {
                    Header("Pair")
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CurrencyChip(cfg.from) { pick = Pick.From }
                        Box(Modifier.padding(horizontal = 6.dp).size(44.dp).clip(CircleShape).clickable { onChange(cfg.flipped()) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.SwapHoriz, "Flip direction", tint = pal.accent)
                        }
                        CurrencyChip(cfg.to) { pick = Pick.To }
                    }
                    Header("Amount")
                    AmountField(cfg.amount, "Rate shown for this many ${cfg.from}") { onChange(cfg.copy(amount = it)) }
                }
                WidgetType.Watchlist -> {
                    Header("Pairs")
                    Segmented(listOf("Follow Markets" to true, "Choose pairs" to false), cfg.followMarkets) { onChange(cfg.copy(followMarkets = it)) }
                    if (cfg.followMarkets) {
                        Note("Shows the pairs on your Markets screen, in the same order.")
                    } else {
                        FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cfg.pairs.forEach { p -> RemovableChip(p) { onChange(cfg.copy(pairs = cfg.pairs - p)) } }
                            if (cfg.pairs.size < WidgetConfig.MAX_PAIRS) AddChip("Add pair") { pick = Pick.PairFrom }
                        }
                    }
                }
                WidgetType.Convert -> {
                    Header("From")
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) { CurrencyChip(cfg.from) { pick = Pick.From } }
                    Header("Amount")
                    AmountField(cfg.amount, "How many ${cfg.from} to convert") { onChange(cfg.copy(amount = it)) }
                    Header("Into")
                    FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cfg.targets.forEach { t -> RemovableChip("${Currencies.get(t).flag} $t") { onChange(cfg.copy(targets = cfg.targets - t)) } }
                        if (cfg.targets.size < WidgetConfig.MAX_TARGETS) AddChip("Add currency") { pick = Pick.Target }
                    }
                }
            }

            Header("Theme")
            FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetTheme.entries.forEach { t -> Choice(t.label, t == cfg.theme) { onChange(cfg.copy(theme = t)) } }
            }
            Header("Background transparency")
            FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetConfig.TRANSPARENCY_STEPS.forEach { t -> Choice(if (t == 0) "Solid" else "$t%", t == cfg.transparency) { onChange(cfg.copy(transparency = t)) } }
            }
            if (cfg.theme == WidgetTheme.Dynamic) Note("Material You takes its colours from your wallpaper and is always solid.")

            Header("Decimals")
            FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetConfig.DECIMAL_CHOICES.forEach { d ->
                    Choice(if (d == WidgetConfig.AUTO_DECIMALS) "Auto" else "$d", d == cfg.decimals, if (d == WidgetConfig.AUTO_DECIMALS) "Automatic decimals" else "$d decimals") { onChange(cfg.copy(decimals = d)) }
                }
            }
            Note(
                if (cfg.decimals == WidgetConfig.AUTO_DECIMALS) "Auto adapts to the number: more places for small rates, fewer for large amounts."
                else "Small rates lose detail at few decimals, e.g. 0.12747 shows as ${app.robfx.calc.Format.fixed(0.12747, cfg.decimals)}.",
            )

            Header("Show")
            if (cfg.type == WidgetType.Single) ToggleRow("Sparkline", "A small chart of the period", cfg.showSpark) { onChange(cfg.copy(showSpark = it)) }
            if (cfg.type != WidgetType.Convert) ToggleRow("Change", "Percent change over the period", cfg.showChange) { onChange(cfg.copy(showChange = it)) }
            ToggleRow("Flags", "Country flags beside the codes", cfg.showFlags) { onChange(cfg.copy(showFlags = it)) }
            ToggleRow("Updated time", "When the rates were last fetched", cfg.showUpdated) { onChange(cfg.copy(showUpdated = it)) }

            if (cfg.type != WidgetType.Convert) {
                Header("Change period")
                FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetPeriod.entries.forEach { p -> Choice(p.label, p == cfg.period) { onChange(cfg.copy(period = p)) } }
                }
            }

            Header("When tapped")
            FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetTap.entries.forEach { t -> Choice(t.label, t == cfg.tap) { onChange(cfg.copy(tap = t)) } }
            }
            Note("Widgets refresh about every 30 minutes, and whenever you open robFX.")
            Spacer(Modifier.padding(8.dp))
        }

        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(pal.accent).clickable(onClick = onSave).padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save widget", color = pal.onAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }

    val close = { pick = null }
    when (val p = pick) {
        null -> {}
        Pick.From -> AppSheet(close) {
            CurrencyPicker(recents, "From", cfg.from) { c -> onChange(if (c == cfg.to) cfg.flipped() else cfg.copy(from = c, targets = cfg.targets - c)); close() }
        }
        Pick.To -> AppSheet(close) { CurrencyPicker(recents, "To", cfg.to) { c -> onChange(if (c == cfg.from) cfg.flipped() else cfg.copy(to = c)); close() } }
        Pick.PairFrom -> AppSheet(close) { CurrencyPicker(recents, "Add a pair · 1 of 2", null) { c -> pick = Pick.PairTo(c) } }
        is Pick.PairTo -> AppSheet(close) { CurrencyPicker(recents, "Add a pair · 2 of 2", null) { c -> if (c != p.from) { onChange(cfg.withPairAdded(p.from, c)); close() } } }
        Pick.Target -> AppSheet(close) { CurrencyPicker(recents, "Convert into", null) { c -> onChange(cfg.withTargetAdded(c)); close() } }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Pal.current.subtext, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    val pal = Pal.current
    Box(Modifier.semantics { if (description != null) contentDescription = description }.clip(RoundedCornerShape(50)).background(if (selected) pal.accent else pal.key).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = if (selected) pal.onAccent else pal.keyText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val pal = Pal.current
    Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.key).padding(4.dp)) {
        options.forEach { (label, value) ->
            val sel = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) pal.accent else Color.Transparent).clickable { onSelect(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) pal.onAccent else pal.keyText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CurrencyChip(code: String, onClick: () -> Unit) {
    val pal = Pal.current
    val c = Currencies.get(code)
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(pal.key).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(c.flag, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(code, color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("  ▾", color = pal.subtext, fontSize = 12.sp)
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val pal = Pal.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(pal.key).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Remove $label", tint = pal.subtext, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    val pal = Pal.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(pal.key).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Add, null, tint = pal.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = pal.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AmountField(value: String, hint: String, onChange: (String) -> Unit) {
    val pal = Pal.current
    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
        Box(Modifier.widthIn(max = 220.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.key).padding(horizontal = 16.dp, vertical = 12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(12)) },
                singleLine = true,
                textStyle = TextStyle(color = pal.text, fontSize = 20.sp),
                cursorBrush = SolidColor(pal.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(hint, color = pal.subtext, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
