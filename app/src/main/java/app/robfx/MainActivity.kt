package app.robfx

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import app.robfx.ui.ConverterScreen
import app.robfx.ui.HomeActions
import app.robfx.ui.HomeContent
import app.robfx.ui.HomeUi
import app.robfx.ui.Sheets
import app.robfx.data.WatchPair
import app.robfx.ui.AppRoot
import app.robfx.update.UpdateState
import app.robfx.ui.theme.RobFxTheme
import app.robfx.ui.theme.resolveDark
import app.robfx.work.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val vm: ConverterViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // One synchronous DataStore read so the very first frame has the right theme and pair, not a flash of defaults.
                val initial = runBlocking { container.settings.settings.first() }
                return ConverterViewModel(application, initial) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsState()
            val dark = resolveDark(settings.theme)
            DisposableEffect(dark) {
                // Transparent bars with icons that match the *app's* theme, which may differ from the system's.
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale(); container.updater.recheckPermission() }
            LifecycleEventEffect(Lifecycle.Event.ON_START) { container.updater.visible = true }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { container.updater.visible = false }

            RobFxTheme(settings.theme, settings.dynamicColor) {
                val screen by vm.screen.collectAsState()
                val notify = {
                    if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val editing by vm.editing.collectAsState()
                AppRoot(
                    screen = screen,
                    editing = editing,
                    onBackToMarkets = vm::goHome,
                    onStopEditing = { vm.setEditing(false) },
                    markets = {
                        val rows by vm.rows.collectAsState()
                        val refreshing by vm.refreshing.collectAsState()
                        val status = rows.firstNotNullOfOrNull { it.rate }
                        val update by vm.updateState.collectAsState()
                        val pending = (update as? UpdateState.Available)?.info?.version ?: (update as? UpdateState.NeedsPermission)?.info?.version
                        HomeContent(
                            HomeUi(rows, status, editing, refreshing, pending),
                            object : HomeActions {
                                override fun open(w: WatchPair) = vm.openPair(w)
                                override fun remove(w: WatchPair) = vm.removeWatch(w)
                                override fun toggleEditing() = vm.toggleEditing()
                                override fun startEditing() = vm.setEditing(true)
                                override fun add() = vm.startAdd()
                                override fun refresh() = vm.refresh()
                                override fun sheet(s: Sheet) = vm.open(s)
                            },
                        )
                    },
                    converter = { ConverterScreen(vm = vm, showSheets = false, requestNotifications = notify) },
                )
                // Sheets live above both screens so settings and alerts open from either.
                Sheets(vm, notify)
            }
        }
    }
}
