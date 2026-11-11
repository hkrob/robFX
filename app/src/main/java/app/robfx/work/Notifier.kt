package app.robfx.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.robfx.MainActivity
import app.robfx.R
import app.robfx.calc.Format
import app.robfx.data.Alert

object Notifier {
    private const val CHANNEL = "alerts"
    private const val UPDATES = "updates"
    private const val UPDATE_ID = 7001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
            )
            nm.createNotificationChannel(
                NotificationChannel(UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // canPost() is the permission check; lint can't see through it, and runCatching covers a revoke racing the call.
    @android.annotation.SuppressLint("MissingPermission")
    fun alert(context: Context, alert: Alert, current: Double) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, alert.id.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val verb = if (alert.above) "rose to" else "fell to"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("${alert.from}/${alert.to} ${Format.rate(current)}")
            .setContentText("$verb your target of ${Format.rate(alert.target)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(alert.id.toInt(), n) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun updateAvailable(context: Context, version: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(context, UPDATE_ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, UPDATES)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("robFX $version is available")
            .setContentText("Open Settings to install it")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, n) }
    }

    /** The system's own install dialog, reached from a notification when we aren't on screen to show it. */
    @android.annotation.SuppressLint("MissingPermission")
    fun confirmInstall(context: Context, confirm: Intent, version: String?) {
        if (!canPost(context)) return
        val go = PendingIntent.getActivity(context, UPDATE_ID + 1, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, UPDATES)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("robFX ${version ?: "update"} is ready")
            .setContentText("Tap to install")
            .setContentIntent(go)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, n) }
    }
}
