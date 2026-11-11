package app.robfx.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.robfx.container

/** Receives the system installer's verdict for a session this app committed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.container.updater.onInstallResult(intent)
    }
}
