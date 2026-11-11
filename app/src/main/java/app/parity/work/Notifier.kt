package app.parity.work

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
import app.parity.MainActivity
import app.parity.R
import app.parity.calc.Format
import app.parity.data.Alert

object Notifier {
    private const val CHANNEL = "alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
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
}
