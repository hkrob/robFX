package app.robfx

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.robfx.data.Currencies
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import app.robfx.ui.ConverterScreen
import app.robfx.ui.HomeActions
import app.robfx.ui.HomeContent
import app.robfx.ui.HomeUi
import app.robfx.ui.Sheets
import app.robfx.ui.UnitPriceScreen
import app.robfx.data.WatchPair
import app.robfx.ui.AppRoot
import app.robfx.update.UpdateState
import app.robfx.ui.theme.RobFxTheme
import app.robfx.ui.theme.resolveDark
import app.robfx.work.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FROM = "pair_from"
        const val EXTRA_TO = "pair_to"
        const val EXTRA_UPDATE = "open_update"
    }

    /** The update notification opens the update itself. A fresh process hasn't checked yet, so check on the way in. */
    private fun openRequestedUpdate(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_UPDATE, false) != true) return
        intent.removeExtra(EXTRA_UPDATE)
        if (vm.updateState.value !is UpdateState.Available) vm.checkForUpdate()
        vm.goHome()
        vm.open(Sheet.SettingsUpdate)
    }

    /** A widget tap can ask for a specific pair's converter. */
    private fun openRequestedPair(intent: Intent?) {
        val from = intent?.getStringExtra(EXTRA_FROM) ?: return
        val to = intent.getStringExtra(EXTRA_TO) ?: return
        if (from in Currencies.codes && to in Currencies.codes && from != to) vm.openPair(WatchPair(from, to))
        // Consumed: a rotation must not reopen it.
        intent.removeExtra(EXTRA_FROM)
        intent.removeExtra(EXTRA_TO)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedPair(intent)
        openRequestedUpdate(intent)
    }

    private val unitVm: UnitPriceViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UnitPriceViewModel(application) as T
            }
        }
    }

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
        openRequestedPair(intent)
        openRequestedUpdate(intent)
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
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { container.updater.visible = false; unitVm.leave()
                // Whatever changed in the app (pair, Markets list, fresh rates) shows on the home screen straight away.
                lifecycleScope.launch { app.robfx.widget.Widgets.updateAll(applicationContext) }
            }

            RobFxTheme(settings.theme, settings.dynamicColor) {
                val screen by vm.screen.collectAsState()
                val notify = {
                    if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val editing by vm.editing.collectAsState()
                val tab by vm.homeTab.collectAsState()
                // Leaving the unit price tab is the moment a comparison counts as done, so it goes into the recents then.
                val selectTab: (HomeTab) -> Unit = { t ->
                    if (tab == HomeTab.UnitPrice && t != HomeTab.UnitPrice) unitVm.leave()
                    vm.selectTab(t)
                }
                // Wide windows (tablets, unfolded foldables, landscape) get a centred column instead of a stretched phone UI.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier.fillMaxSize().background(app.robfx.ui.theme.Pal.current.bg),
                    contentAlignment = androidx.compose.ui.Alignment.TopCenter,
                ) {
                    val landscape = maxWidth > maxHeight
                    Box(Modifier.fillMaxHeight().widthIn(max = if (landscape) 1000.dp else 560.dp).fillMaxWidth()) {
                AppRoot(
                        screen = screen,
                        editing = editing,
                        unitTabOpen = tab != HomeTab.Markets,
                        onCloseUnitTab = { selectTab(HomeTab.Markets) },
                        onBackToMarkets = vm::goHome,
                        onStopEditing = { vm.setEditing(false) },
                        markets = {
                            if (tab == HomeTab.About) {
                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                val releases = remember { app.robfx.about.Changelog.load(ctx) }
                                app.robfx.ui.AboutContent(
                                    app.robfx.ui.AboutUi(vm.currentVersion, releases),
                                    object : app.robfx.ui.AboutActions {
                                        override fun selectTab(t: HomeTab) = selectTab(t)
                                        override fun openSettings() = vm.open(Sheet.Settings)
                                        override fun openSource() {
                                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(app.robfx.ui.SOURCE_URL))) }
                                        }
                                        override fun email() {
                                            val mail = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${app.robfx.ui.CONTACT_EMAIL}?subject=" + android.net.Uri.encode("robFX ${vm.currentVersion}")))
                                            // No mail app set up: put the address on the clipboard instead of doing nothing.
                                            if (runCatching { startActivity(mail) }.isFailure) {
                                                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText("robFX contact", app.robfx.ui.CONTACT_EMAIL))
                                                android.widget.Toast.makeText(this@MainActivity, "Email address copied", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                            } else if (tab == HomeTab.UnitPrice) {
                                UnitPriceScreen(unitVm, settings.haptics, settings.unitDecimals, onSelectTab = selectTab, onOpenSettings = { vm.open(Sheet.Settings) })
                            } else {
                                val rows by vm.rows.collectAsState()
                                val refreshing by vm.refreshing.collectAsState()
                                val status = rows.firstNotNullOfOrNull { it.rate }
                                val update by vm.updateState.collectAsState()
                                val sources by vm.sources.collectAsState()
                                val pending = (update as? UpdateState.Available)?.info?.version ?: (update as? UpdateState.NeedsPermission)?.info?.version
                                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                                    Box(Modifier.widthIn(max = 640.dp).fillMaxHeight()) {
                                        HomeContent(
                                            HomeUi(rows, status, editing, refreshing, pending, settings.sort, settings.sortDescending, settings.rateDecimals, app.robfx.ui.offlineText(sources)),
                                            object : HomeActions {
                                                override fun open(w: WatchPair) = vm.openPair(w)
                                                override fun remove(w: WatchPair) = vm.removeWatch(w)
                                                override fun toggleEditing() = vm.toggleEditing()
                                                override fun startEditing() = vm.setEditing(true)
                                                override fun tapSort(s: WatchSort) = vm.tapSort(s)
                                                override fun selectTab(t: HomeTab) = selectTab(t)
                                                override fun add() = vm.startAdd()
                                                override fun refresh() = vm.refresh()
                                                override fun sheet(s: Sheet) = vm.open(s)
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        converter = { ConverterScreen(vm = vm, showSheets = false, requestNotifications = notify) },
                    )
                    }
                }
                // Sheets live above both screens so settings and alerts open from either.
                Sheets(vm, notify)
            }
        }
    }
}
