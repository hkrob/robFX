package app.robfx.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import app.robfx.data.SettingsStore
import app.robfx.data.source.Http
import app.robfx.data.source.HttpStatusException
import app.robfx.work.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Self-update from this repo's GitHub releases. Android never allows a fully silent *first* install, so the flow is:
 * check -> download -> verify -> hand to the system installer. After the first manual confirmation, Android 12+ may
 * apply later updates without a prompt; otherwise a notification takes you straight to the system's install dialog.
 */
class Updater(private val context: Context, private val settings: SettingsStore) {
    private val api = Http()
    private val download = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val mutex = Mutex()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Set by the activity: a confirm dialog can only be launched straight away while we are on screen. */
    @Volatile var visible = false

    val currentVersion: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    private fun canInstall() = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Back from the "allow installs" settings page: step out of the permission-needed state if it's now granted. */
    fun recheckPermission() {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && canInstall()) _state.value = UpdateState.Available(s.info)
    }

    suspend fun check(): UpdateInfo? {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing) return null
        _state.value = UpdateState.Checking
        return try {
            val info = Releases.parse(api.get(LATEST))
            settings.setLastUpdateCheck(System.currentTimeMillis())
            if (info != null && Releases.isNewer(info.version, currentVersion)) {
                _state.value = UpdateState.Available(info)
                info
            } else {
                _state.value = UpdateState.UpToDate
                null
            }
        } catch (e: HttpStatusException) {
            // 404 just means "no release published yet".
            _state.value = if (e.code == 404) UpdateState.UpToDate else UpdateState.Failed("GitHub answered ${e.code}")
            null
        } catch (e: IOException) {
            _state.value = UpdateState.Failed("Couldn't reach GitHub")
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = UpdateState.Failed("Unexpected release format")
            null
        }
    }

    suspend fun downloadAndInstall(info: UpdateInfo) = mutex.withLock {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(info)
            return@withLock
        }
        try {
            val file = File(context.cacheDir, "update/robFX-${info.version}.apk")
            file.parentFile?.apply { listFiles()?.forEach { it.delete() }; mkdirs() }
            _state.value = UpdateState.Downloading(info, 0f)
            val sha = withContext(app.robfx.data.source.NetworkDispatcher) { fetch(info, file) }
            if (info.sha256 != null && info.sha256 != sha) throw IOException("Download failed its checksum")
            verifyArchive(file)
            _state.value = UpdateState.Installing(info)
            withContext(Dispatchers.IO) { commit(file) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.value = UpdateState.Failed(e.message ?: "Update failed", info)
        }
    }

    private fun fetch(info: UpdateInfo, out: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        download.newCall(Request.Builder().url(info.apkUrl).header("User-Agent", "robFX/$currentVersion").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
            val body = r.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            var lastPct = -1
            out.outputStream().use { sink ->
                body.byteStream().use { src ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; _state.value = UpdateState.Downloading(info, pct / 100f) }
                        }
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The file must really be this app, and newer. (Android also refuses a different signing key at install time.) */
    private fun verifyArchive(file: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, 0) ?: throw IOException("Downloaded file isn't a valid APK")
        if (archive.packageName != context.packageName) throw IOException("Downloaded APK is for a different app")
        val installed = pm.getPackageInfo(context.packageName, 0)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) throw IOException("Downloaded APK isn't newer than this one")
    }

    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("robfx.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable: the installer adds the status extras to this intent.
            val pending = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    fun onInstallResult(intent: Intent) {
        val info = (_state.value as? UpdateState.Installing)?.info
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                if (visible) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                } else {
                    Notifier.confirmInstall(context, confirm, info?.version)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> _state.value = UpdateState.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed (code $status)", info,
            )
        }
    }

    companion object {
        const val LATEST = "https://api.github.com/repos/hkrob/robFX/releases/latest"
    }
}
