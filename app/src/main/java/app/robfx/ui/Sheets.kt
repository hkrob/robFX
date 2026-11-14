package app.robfx.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.robfx.ConverterViewModel
import app.robfx.Sheet
import app.robfx.calc.Format
import app.robfx.data.Alert
import app.robfx.data.Currencies
import app.robfx.data.Currency
import app.robfx.data.ThemeMode
import app.robfx.update.UpdateState
import app.robfx.ui.theme.Pal

/** The app's bottom sheet: full height, the theme's background, rounded top. Shared by every screen's sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val pal = Pal.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = pal.bg,
        contentColor = pal.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) { content() }
}

@Composable
fun Sheets(vm: ConverterViewModel, requestNotifications: () -> Unit) {
    val sheet by vm.sheet.collectAsState()
    if (sheet == Sheet.None) return
    val recents = vm.settings.collectAsState().value.recents
    AppSheet(onDismiss = vm::closeSheet) {
        when (sheet) {
            Sheet.FromPicker -> CurrencyPicker(recents, "Convert from", vm.settings.collectAsState().value.from) { vm.pick(it, true) }
            Sheet.ToPicker -> CurrencyPicker(recents, "Convert to", vm.settings.collectAsState().value.to) { vm.pick(it, false) }
            Sheet.AddFrom -> CurrencyPicker(recents, "Watch a pair \u00B7 1 of 2", null) { vm.pickForWatch(it) }
            Sheet.AddTo -> CurrencyPicker(recents, "Watch a pair \u00B7 2 of 2", null) { vm.pickForWatch(it) }
            Sheet.Alerts -> Alerts(vm, requestNotifications)
            Sheet.Settings -> SettingsSheet(vm, onAlerts = { vm.open(Sheet.Alerts) })
            Sheet.None -> {}
        }
    }
}

@Composable
internal fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Pal.current.text, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
fun CurrencyPicker(recentCodes: List<String>, title: String, selected: String?, onPick: (String) -> Unit) {
    val pal = Pal.current
    var query by remember { mutableStateOf("") }

    val q = query.trim()
    val list = remember(q) {
        if (q.isEmpty()) Currencies.all
        else Currencies.all.filter { it.code.contains(q, true) || it.name.contains(q, true) }
    }
    val recents = remember(recentCodes, q) {
        if (q.isEmpty()) recentCodes.mapNotNull { c -> Currencies.all.firstOrNull { it.code == c } }.take(4) else emptyList()
    }

    Column(Modifier.fillMaxHeight(0.92f)) {
        Title(title)
        Row(
            Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(pal.key)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = pal.subtext)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search currency or code", color = pal.subtext, fontSize = 16.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = pal.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(pal.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) Icon(Icons.Outlined.Close, "Clear search", tint = pal.subtext, modifier = Modifier.size(20.dp).clickable { query = "" })
        }
        LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
            if (recents.isNotEmpty()) {
                item { Header("Recent") }
                items(recents, key = { "r" + it.code }) { CurrencyRow(it, it.code == selected) { onPick(it.code) } }
                item { Header("All currencies") }
            }
            items(list, key = { it.code }) { CurrencyRow(it, it.code == selected) { onPick(it.code) } }
        }
    }
}

@Composable
internal fun Header(text: String) {
    Text(text.uppercase(), color = Pal.current.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun CurrencyRow(c: Currency, selected: Boolean, onClick: () -> Unit) {
    val pal = Pal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.flag, fontSize = 26.sp, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(c.code, color = if (selected) pal.accent else pal.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(c.name, color = pal.subtext, fontSize = 13.sp)
        }
        if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(pal.accent))
    }
}

@Composable
private fun SettingsSheet(vm: ConverterViewModel, onAlerts: () -> Unit) {
    val pal = Pal.current
    val s by vm.settings.collectAsState()
    val sources by vm.sources.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val pair by vm.pair.collectAsState()

    Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
        Title("Settings")
        LazyColumn(Modifier.weight(1f)) {
            item { Header("Theme") }
            item {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.key).padding(4.dp)) {
                    ThemeMode.entries.forEach { m ->
                        val sel = m == s.theme
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) pal.accent else Color.Transparent)
                                .clickable { vm.setTheme(m) }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(m.label, color = if (sel) pal.onAccent else pal.keyText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) item { ToggleRow("Material You colours", "Take the accent from your wallpaper", s.dynamicColor, vm::setDynamic) }
            item { ToggleRow("Haptics", "Key presses and chart scrubbing", s.haptics, vm::setHaptics) }

            item { Header("Rate alerts") }
            item { NavRow("Manage alerts", "Get notified when a rate crosses a target", onAlerts) }

            item { Header("Updates") }
            item { UpdateRow(vm) }
            item { ToggleRow("Update automatically", "Check every few hours and download new versions. Android still asks you to confirm the install.", s.autoUpdate, vm::setAutoUpdate) }

            item { Header("Data sources") }
            items(sources, key = { it.name }) { src ->
                val perSource = pair?.perSource?.get(src.name)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (src.ok) pal.positive else if (src.updatedAt != null) pal.warning else pal.negative))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(src.name, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append(if (src.ok) "OK" else "Failed")
                                src.latencyMs?.let { append(" · ${it} ms") }
                                src.updatedAt?.let { append(" · ${ago(it)}") }
                            },
                            color = pal.subtext, fontSize = 13.sp,
                        )
                    }
                    if (perSource != null) Text(Format.rate(perSource), color = pal.subtext, fontSize = 15.sp)
                }
            }
            item {
                Text(
                    "Rates are the median of every source that answered. If they disagree by more than 0.5% the status pill turns amber.",
                    color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Box(
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(50)).background(pal.key)
                        .clickable(enabled = !refreshing) { vm.refresh() }.padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text(if (refreshing) "Refreshing…" else "Refresh now", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Header("Diagnostics")
                NavRow("Copy diagnostic report", "If the app froze or crashed, copy this and send it with your report") {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("robFX diagnostics", app.robfx.diag.Diagnostics.report(ctx)))
                    android.widget.Toast.makeText(ctx, "Report copied", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            item {
                Text("robFX 1.2.1 · Informational rates, not for trading.", color = pal.subtext, fontSize = 12.sp, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun UpdateRow(vm: ConverterViewModel) {
    val pal = Pal.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state by vm.updateState.collectAsState()

    val (status, label, enabled, action) = when (val u = state) {
        UpdateState.Idle -> Quad("Not checked yet", "Check for updates", true) { vm.checkForUpdate() }
        UpdateState.Checking -> Quad("Checking\u2026", "Checking\u2026", false) {}
        UpdateState.UpToDate -> Quad("You're on the latest version", "Check again", true) { vm.checkForUpdate() }
        is UpdateState.Available -> Quad("Version ${u.info.version} is available", "Update to ${u.info.version}", true) { vm.installUpdate(u.info) }
        is UpdateState.NeedsPermission -> Quad("Allow robFX to install updates, then come back", "Allow installs", true) {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        is UpdateState.Downloading -> Quad("Downloading ${(u.progress * 100).toInt()}%", "Downloading\u2026", false) {}
        is UpdateState.Installing -> Quad("Installing ${u.info.version}\u2026", "Installing\u2026", false) {}
        is UpdateState.Failed -> Quad(u.message, if (u.info != null) "Try again" else "Check again", true) {
            u.info?.let { vm.installUpdate(it) } ?: vm.checkForUpdate()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("robFX ${vm.currentVersion}", color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(status, color = if (state is UpdateState.Failed) pal.negative else pal.subtext, fontSize = 13.sp)
        (state as? UpdateState.Available)?.info?.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it.lines().take(6).joinToString("\n"), color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        (state as? UpdateState.Downloading)?.let {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(pal.key)) {
                Box(Modifier.fillMaxWidth(it.progress.coerceIn(0f, 1f)).height(4.dp).background(pal.accent))
            }
        }
        val primary = state is UpdateState.Available || state is UpdateState.NeedsPermission || (state as? UpdateState.Failed)?.info != null
        Box(
            Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).background(if (primary) pal.accent else pal.key)
                .clickable(enabled = enabled, onClick = action).padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text(label, color = if (primary) pal.onAccent else if (enabled) pal.accent else pal.subtext, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

private data class Quad(val status: String, val label: String, val enabled: Boolean, val action: () -> Unit)

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = Pal.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = pal.subtext, fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = pal.accent, checkedThumbColor = pal.onAccent, uncheckedTrackColor = pal.key, uncheckedBorderColor = pal.subtext, uncheckedThumbColor = pal.subtext),
        )
    }
}

@Composable
private fun NavRow(title: String, sub: String, onClick: () -> Unit) {
    val pal = Pal.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = pal.subtext, fontSize = 13.sp)
    }
}

@Composable
private fun Alerts(vm: ConverterViewModel, requestNotifications: () -> Unit) {
    val pal = Pal.current
    val s by vm.settings.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val pair by vm.pair.collectAsState()

    var above by remember { mutableStateOf(true) }
    var target by remember(s.from, s.to) { mutableStateOf(pair?.let { Format.rate(it.rate).replace(",", "") } ?: "") }
    val value = target.toDoubleOrNull()

    Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
        Title("Rate alerts")
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.key).padding(16.dp)) {
                    Text("New alert for ${s.from}/${s.to}", color = pal.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.bg).padding(4.dp)) {
                        listOf(true to "Rises to", false to "Falls to").forEach { (isAbove, label) ->
                            val sel = isAbove == above
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sel) pal.accent else Color.Transparent)
                                    .clickable { above = isAbove }.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (sel) pal.onAccent else pal.keyText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(color = pal.accent, fontSize = 30.sp, fontWeight = FontWeight.Light),
                            cursorBrush = SolidColor(pal.accent),
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(if (value != null && value > 0) pal.accent else pal.bg)
                                .clickable(enabled = value != null && value > 0) {
                                    requestNotifications()
                                    vm.addAlert(above, value!!)
                                }.padding(horizontal = 20.dp, vertical = 12.dp),
                        ) { Text("Add", color = if (value != null && value > 0) pal.onAccent else pal.subtext, fontWeight = FontWeight.Bold) }
                    }
                    pair?.let { Text("Now ${Format.rate(it.rate)}", color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            if (alerts.isEmpty()) {
                item { Text("No alerts yet. Checked about every 30 minutes in the background.", color = pal.subtext, fontSize = 14.sp, modifier = Modifier.padding(24.dp)) }
            } else {
                item { Header("Your alerts") }
                items(alerts, key = { it.id }) { a -> AlertRow(a, onToggle = { vm.setAlertActive(a.id, it) }, onDelete = { vm.deleteAlert(a.id) }) }
            }
        }
    }
}

@Composable
private fun AlertRow(a: Alert, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val pal = Pal.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${a.from}/${a.to}", color = if (a.active) pal.text else pal.subtext, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (a.above) "Rises to " else "Falls to ") + Format.rate(a.target) + if (a.active) "" else " · triggered",
                color = pal.subtext, fontSize = 13.sp,
            )
        }
        Switch(
            checked = a.active, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = pal.accent, checkedThumbColor = pal.onAccent, uncheckedTrackColor = pal.key, uncheckedBorderColor = pal.subtext, uncheckedThumbColor = pal.subtext),
        )
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Delete, "Delete alert", tint = pal.subtext)
        }
    }
}
