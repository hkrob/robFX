package app.parity

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
import app.parity.ui.ConverterScreen
import app.parity.ui.theme.ParityTheme
import app.parity.ui.theme.resolveDark
import app.parity.work.Notifier
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
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale() }

            ParityTheme(settings.theme, settings.dynamicColor) {
                ConverterScreen(
                    vm = vm,
                    requestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }
}
