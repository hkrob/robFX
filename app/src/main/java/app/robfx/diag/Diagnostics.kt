package app.robfx.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What to hand over when "it froze" or "it crashed" can't be reproduced from here: why Android last ended the
 * app (including the main-thread trace of an ANR, when the system kept one) and any uncaught exception we saw.
 */
object Diagnostics {
    private const val CRASH_FILE = "last_crash.txt"

    /** Names for the system's process-exit reason codes (ApplicationExitInfo.REASON_*). */
    fun reasonName(code: Int): String = when (code) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR (app not responding)"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($code)"
    }

    fun installCrashLog(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, thread: Thread, error: Throwable) {
        File(context.filesDir, CRASH_FILE).writeText("${stamp(System.currentTimeMillis())}\nthread: ${thread.name}\n${error.stackTraceToString()}".take(8_000))
    }

    fun lastCrash(context: Context): String? = runCatching { File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun stamp(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    private fun exits(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "(needs Android 11 or newer)"
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val list = am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            if (list.isEmpty()) return@runCatching "(none recorded)"
            list.joinToString("\n") { e ->
                buildString {
                    append("- ${stamp(e.timestamp)}  ${reasonName(e.reason)}  pid ${e.pid}")
                    if (!e.description.isNullOrBlank()) append("\n    ${e.description}")
                    // For an ANR the system keeps the thread dump; the main thread's stack names what it was stuck on.
                    if (e.reason == 6) {
                        val trace = runCatching { e.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        if (!trace.isNullOrBlank()) append("\n    --- ANR trace (start) ---\n").append(trace.take(3_500)).append("\n    --- (end) ---")
                    }
                }
            }
        }.getOrElse { "(unavailable: ${it.javaClass.simpleName})" }
    }

    fun report(context: Context): String {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
        val cfg = context.resources.configuration
        return buildString {
            appendLine("robFX diagnostics")
            appendLine("Version ${info?.versionName} (${info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) }})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Locale ${Locale.getDefault()} · font scale ${cfg.fontScale} · ${cfg.screenWidthDp}x${cfg.screenHeightDp} dp · ${cfg.densityDpi} dpi")
            appendLine()
            appendLine("Recent process exits (newest first):")
            appendLine(exits(context))
            appendLine()
            appendLine("Last uncaught exception:")
            appendLine(lastCrash(context) ?: "(none)")
        }
    }
}
