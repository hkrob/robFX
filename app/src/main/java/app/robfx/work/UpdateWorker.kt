package app.robfx.work

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.robfx.container
import kotlinx.coroutines.flow.first

/** Looks for a new release every few hours; downloads it right away if automatic updates are on. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val info = c.updater.check() ?: return Result.success()
        val s = c.settings.settings.first()
        val canInstall = Build.VERSION.SDK_INT < 26 || applicationContext.packageManager.canRequestPackageInstalls()
        if (s.autoUpdate && canInstall) {
            c.updater.downloadAndInstall(info)
        } else if (c.settings.lastNotifiedVersion() != info.version) {
            Notifier.updateAvailable(applicationContext, info.version)
            c.settings.setLastNotifiedVersion(info.version)
        }
        return Result.success()
    }
}
