package app.parity

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import app.parity.data.HistoryPoint
import app.parity.data.PairRate
import app.parity.data.Quality
import app.parity.data.Range
import app.parity.data.Settings
import app.parity.data.ThemeMode
import app.parity.ui.ConverterActions
import app.parity.ui.ConverterContent
import app.parity.ui.ConverterUi
import app.parity.ui.theme.ParityTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.sin

/** Renders the real screen headlessly to build/screens as PNGs so the design can be looked at without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private object Noop : ConverterActions {
        override fun key(k: String) {}
        override fun swap() {}
        override fun refresh() {}
        override fun setRange(r: Range) {}
        override fun retryHistory() {}
        override fun toggleKeypad() {}
        override fun open(sheet: Sheet) {}
    }

    private fun series(): List<HistoryPoint> {
        val start = LocalDate.of(2026, 10, 12)
        return (0..30).map { i -> HistoryPoint(start.plusDays(i.toLong()), 7.835 + 0.012 * sin(i / 4.0) + i * 0.0004 + 0.003 * sin(i * 1.7)) }
    }

    private fun ui(keypad: Boolean = true, quality: Quality = Quality.Consensus, expr: String = "45÷12") = ConverterUi(
        settings = Settings("USD", "HKD", expr = expr, range = Range.M1),
        expr = expr,
        pair = PairRate(7.8449, mapOf("a" to 7.8449, "b" to 7.8461, "c" to 7.8440), if (quality == Quality.Divergent) 0.012 else 0.0027, quality, 1_794_355_352_000L),
        converted = 45.0 / 12 * 7.8449,
        history = HistoryState(series(), loading = false),
        keypadOpen = keypad,
        refreshing = false,
    )

    private fun shot(name: String, mode: ThemeMode, ui: ConverterUi) {
        rule.setContent { ParityTheme(mode, dynamic = false) { ConverterContent(ui, Noop) } }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun amoled() = shot("amoled", ThemeMode.Amoled, ui())
    @Test fun light() = shot("light", ThemeMode.Light, ui())
    @Test fun dark() = shot("dark", ThemeMode.Dark, ui(quality = Quality.Divergent))
    @Test fun keypadFolded() = shot("folded", ThemeMode.Amoled, ui(keypad = false))
}
