package app.robfx.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.robfx.container
import app.robfx.widget.Widgets

class RatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val ok = c.rates.refresh()

        // One-shot: a tripped alert goes quiet until the user re-arms it, instead of nagging every 30 minutes.
        AlertRunner.run(applicationContext)

        Widgets.updateAll(applicationContext)
        return if (ok) Result.success() else Result.retry()
    }
}
