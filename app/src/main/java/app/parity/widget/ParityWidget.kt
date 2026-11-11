package app.parity.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.parity.MainActivity
import app.parity.calc.Calc
import app.parity.calc.Format
import app.parity.container
import app.parity.data.Currencies
import app.parity.data.Quality
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date

private val Ink = Color(0xFF000000)
private val Accent = Color(0xFF5584F0)
private val Subtle = Color(0xFF8E8E93)
private val Light = Color(0xFFF2F2F4)

/** A glance at the pair and amount from the app. Always dark: it matches the reference and reads on any wallpaper. */
class ParityWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val s = c.settings.settings.first()
        val pair = c.rates.pair(s.from, s.to)
        val amount = Calc.evaluate(s.expr)?.takeIf { it.signum() > 0 }?.toDouble() ?: 1.0
        val from = Currencies.get(s.from)
        val to = Currencies.get(s.to)

        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Ink).cornerRadius(28.dp).padding(16.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("${from.flag} ${from.code}  →  ${to.flag} ${to.code}", style = TextStyle(color = ColorProvider(Subtle), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(4.dp))
                if (pair == null) {
                    Text("Open Parity to load rates", style = TextStyle(color = ColorProvider(Light), fontSize = 15.sp))
                } else {
                    Text(Format.rate(pair.rate), style = TextStyle(color = ColorProvider(Accent), fontSize = 34.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        "${Format.amount(amount)} ${from.code} = ${Format.amount(amount * pair.rate)} ${to.code}",
                        style = TextStyle(color = ColorProvider(Light), fontSize = 14.sp),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    val note = if (pair.quality == Quality.Stale) "Offline · " else ""
                    Text(
                        note + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pair.updatedAt)),
                        style = TextStyle(color = ColorProvider(Subtle), fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

class ParityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParityWidget()
}
