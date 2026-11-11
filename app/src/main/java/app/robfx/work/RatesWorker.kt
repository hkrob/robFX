package app.robfx.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.robfx.container
import app.robfx.data.AlertChecker
import app.robfx.widget.RobFxWidget
import kotlinx.coroutines.flow.first

class RatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val ok = c.rates.refresh()

        val alerts = c.settings.alerts.first()
        val hits = AlertChecker.triggered(alerts) { f, t -> c.rates.pair(f, t)?.rate }
        for ((alert, rate) in hits) Notifier.alert(applicationContext, alert, rate)
        // One-shot: a tripped alert goes quiet until the user re-arms it, instead of nagging every 30 minutes.
        if (hits.isNotEmpty()) {
            val ids = hits.map { it.first.id }.toSet()
            c.settings.updateAlerts { list -> list.map { if (it.id in ids) it.copy(active = false) else it } }
        }

        RobFxWidget().updateAll(applicationContext)
        return if (ok) Result.success() else Result.retry()
    }
}
