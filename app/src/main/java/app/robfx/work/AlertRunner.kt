package app.robfx.work

import android.content.Context
import app.robfx.container
import app.robfx.data.Alert
import app.robfx.data.AlertChecker

object AlertRunner {
    /**
     * Checks every active alert against the current consensus and saves the outcome in the same settings edit, so the
     * background worker and the open app checking at the same moment can't both notify.
     */
    suspend fun run(context: Context) {
        val c = context.container
        var fired = emptyList<Pair<Alert, Double>>()
        c.settings.updateAlerts { list ->
            val e = AlertChecker.evaluate(list, System.currentTimeMillis()) { f, t -> c.rates.pair(f, t)?.rate }
            fired = e.fired
            e.alerts
        }
        for ((alert, rate) in fired) Notifier.alert(context, alert, rate)
    }
}
