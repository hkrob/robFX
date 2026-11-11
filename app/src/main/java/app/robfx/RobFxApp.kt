package app.robfx

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.robfx.work.Notifier
import app.robfx.work.RatesWorker
import app.robfx.work.UpdateWorker
import java.util.concurrent.TimeUnit

class RobFxApp : Application() {
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
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as RobFxApp).container
