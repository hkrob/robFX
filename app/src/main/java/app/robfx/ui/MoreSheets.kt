package app.robfx.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.robfx.ConverterViewModel
import app.robfx.ExportState
import app.robfx.Sheet
import app.robfx.calc.Format
import app.robfx.data.Currencies
import app.robfx.data.ExportPeriod
import app.robfx.ui.theme.Pal
import java.time.LocalDate

/** The converter's amount in every currency on the user's list. Tap one to make it the converter's target. */
@Composable
internal fun MultiSheet(vm: ConverterViewModel) {
    val pal = Pal.current
    val s by vm.settings.collectAsState()
    val list by vm.multi.collectAsState()
    val amount by vm.amount.collectAsState()
    vm.snapshot.collectAsState().value // recompose when rates arrive
    val a = amount?.toDouble() ?: 1.0
    val shown = list.filter { it != s.from }

    Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
        Title("In other currencies")
        Text(
            "${Format.number(a, s.amountDecimals) { Format.amount(it) }} ${s.from} · ${Currencies.get(s.from).name}",
            color = pal.subtext, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(shown, key = { it }) { code ->
                val r = vm.rate(s.from, code)
                Row(
                    Modifier.fillMaxWidth().clickable { vm.pickMulti(code) }.padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Currencies.get(code).flag, fontSize = 24.sp, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(code, color = if (code == s.to) pal.accent else pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            r?.let { "1 ${s.from} = ${Format.number(it, s.rateDecimals) { v -> Format.rate(v) }}" } ?: Currencies.get(code).name,
                            color = pal.subtext, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FitText(
                        candidates = listOf(r?.let { Format.number(a * it, s.amountDecimals) { v -> Format.amount(v) } } ?: "\u2014"),
                        color = if (code == s.to) pal.accent else pal.text,
                        maxSize = 24, preferredMin = 16, hardMin = 11,
                        modifier = Modifier.weight(1.3f),
                    )
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { vm.removeMulti(code) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Remove $code", tint = pal.subtext, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(50)).background(pal.key)
                        .clickable { vm.open(Sheet.MultiAdd) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = pal.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add currency", color = pal.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            item {
                Text(
                    "Tap a currency to convert into it. Uses the same consensus rates as the converter.",
                    color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Daily rates for the converter's pair, saved as CSV wherever the user picks (Files, Drive, …). */
@Composable
internal fun ExportSheet(vm: ConverterViewModel) {
    val pal = Pal.current
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val state by vm.export.collectAsState()
    val periods = remember { ExportPeriod.all(LocalDate.now()) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val ready = vm.export.value as? ExportState.Ready
        if (uri != null && ready != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(ready.csv.toByteArray()) } ?: error("no stream") }.isSuccess
            Toast.makeText(ctx, if (ok) "Saved ${ready.fileName}" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
        vm.exportDone()
    }
    LaunchedEffect(state) { (state as? ExportState.Ready)?.let { save.launch(it.fileName) } }

    Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
        Title("Export rate history")
        Text(
            "Daily ECB reference rates for ${s.from}/${s.to} as a CSV file. Weekends and holidays use the last published rate, " +
                "with the date it was published.",
            color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp),
        )
        (state as? ExportState.Failed)?.let {
            Text(it.message, color = pal.negative, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(periods, key = { it.label }) { p ->
                val loading = (state as? ExportState.Loading)?.period == p
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = state !is ExportState.Loading) { vm.prepareExport(p) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${dateText(p.start)} – ${dateText(p.end)}", color = pal.subtext, fontSize = 13.sp)
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = pal.accent, strokeWidth = 2.dp)
                }
            }
        }
    }
}

private fun dateText(d: LocalDate) = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
