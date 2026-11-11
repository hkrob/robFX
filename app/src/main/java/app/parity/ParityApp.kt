package app.parity

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.parity.work.Notifier
import app.parity.work.RatesWorker
import java.util.concurrent.TimeUnit

class ParityApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannel(this)
        // Powers both the widget and the alerts. KEEP so a relaunch doesn't reset the period.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rates",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RatesWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as ParityApp).container
