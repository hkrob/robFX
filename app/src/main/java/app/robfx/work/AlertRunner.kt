package app.robfx.work

import android.content.Context
import app.robfx.container
import app.robfx.data.Alert
import app.robfx.data.AlertChecker

object AlertRunner {
    /**
     * Fires every active alert whose target the current consensus has reached, and disarms it in the same settings
     * edit, so the background worker and the open app checking at the same moment can't both notify.
     */
    suspend fun run(context: Context) {
        val c = context.container
        var fired = emptyList<Pair<Alert, Double>>()
        c.settings.updateAlerts { list ->
            val hits = AlertChecker.triggered(list) { f, t -> c.rates.pair(f, t)?.rate }
            fired = hits
            if (hits.isEmpty()) return@updateAlerts list
            val now = System.currentTimeMillis()
            val byId = hits.associate { it.first.id to it.second }
            list.map { a -> byId[a.id]?.let { r -> a.copy(active = false, triggeredAt = now, triggeredRate = r) } ?: a }
        }
        for ((alert, rate) in fired) Notifier.alert(context, alert, rate)
    }
}
