package app.robfx

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import app.robfx.data.HistoryPoint
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.Range
import app.robfx.data.Settings
import app.robfx.data.ThemeMode
import app.robfx.ui.ConverterActions
import app.robfx.ui.ConverterContent
import app.robfx.ui.ConverterUi
import app.robfx.ui.HomeActions
import app.robfx.ui.HomeContent
import app.robfx.ui.HomeUi
import app.robfx.data.WatchPair
import app.robfx.ui.theme.RobFxTheme
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
        override fun back() {}
        override fun toggleWatch() {}
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
        rule.setContent { RobFxTheme(mode, dynamic = false) { ConverterContent(ui, Noop) } }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private object HomeNoop : HomeActions {
        override fun open(w: WatchPair) {}
        override fun remove(w: WatchPair) {}
        override fun toggleEditing() {}
        override fun startEditing() {}
        override fun tapSort(s: WatchSort) {}
        override fun add() {}
        override fun refresh() {}
        override fun sheet(s: Sheet) {}
    }

    private fun spark(seed: Double, drift: Double) = (0..6).map { i ->
        HistoryPoint(LocalDate.of(2026, 11, 4).plusDays(i.toLong()), 1.0 + drift * i + 0.004 * sin(i * 1.3 + seed))
    }

    private fun home(editing: Boolean, sort: WatchSort = WatchSort.Custom, desc: Boolean = false) = HomeUi(
        rows = listOf(
            WatchRow(WatchPair("HKD", "USD"), PairRate(0.12747, emptyMap(), 0.0, Quality.Consensus, 0), spark(0.2, 0.0006).map { HistoryPoint(it.date, it.value * 0.1275) }),
            WatchRow(WatchPair("USD", "EUR"), PairRate(0.89174, emptyMap(), 0.0, Quality.Consensus, 0), spark(1.0, -0.002).map { HistoryPoint(it.date, it.value * 0.89) }),
            WatchRow(WatchPair("USD", "JPY"), PairRate(154.832, emptyMap(), 0.0, Quality.Consensus, 0), spark(2.0, 0.004).map { HistoryPoint(it.date, it.value * 154) }),
            WatchRow(WatchPair("EUR", "GBP"), PairRate(0.8412, emptyMap(), 0.0, Quality.Consensus, 0), spark(3.0, -0.0015).map { HistoryPoint(it.date, it.value * 0.84) }),
            WatchRow(WatchPair("USD", "CNY"), PairRate(7.1893, emptyMap(), 0.0, Quality.Consensus, 0), spark(4.0, 0.001).map { HistoryPoint(it.date, it.value * 7.18) }),
            WatchRow(WatchPair("AUD", "USD"), PairRate(0.6512, emptyMap(), 0.0, Quality.Consensus, 0), null),
            WatchRow(WatchPair("USD", "IDR"), PairRate(16123.456, emptyMap(), 0.0, Quality.Consensus, 0), spark(5.0, 0.002).map { HistoryPoint(it.date, it.value * 16000) }),
            WatchRow(WatchPair("EUR", "KRW"), PairRate(1523.4567, emptyMap(), 0.0, Quality.Consensus, 0), spark(6.0, -0.001).map { HistoryPoint(it.date, it.value * 1500) }),
        ),
        status = PairRate(1.0, mapOf("a" to 1.0, "b" to 1.0, "c" to 1.0), 0.0, Quality.Consensus, 1_794_355_352_000L),
        editing = editing, refreshing = false, sort = sort, sortDescending = desc,
    )

    private fun shotHome(name: String, mode: ThemeMode, editing: Boolean = false, sort: WatchSort = WatchSort.Custom, desc: Boolean = false) {
        rule.setContent { RobFxTheme(mode, dynamic = false) { HomeContent(home(editing, sort, desc), HomeNoop) } }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun homeSortedByChange() = shotHome("home_sorted_change", ThemeMode.Amoled, sort = WatchSort.Change, desc = true)

    @Test fun homeAmoled() = shotHome("home_amoled", ThemeMode.Amoled)
    @Test fun homeLightEditing() = shotHome("home_light_edit", ThemeMode.Light, editing = true)

    @Test fun longResultFits() = shot(
        "long_result", ThemeMode.Amoled,
        ui(expr = "1000").copy(
            settings = Settings("HKD", "CNY", expr = "1000", range = Range.M3),
            pair = PairRate(0.85047, mapOf("a" to 0.85047), 0.0, Quality.Consensus, 1_794_355_352_000L),
            converted = 1000 * 0.850470123,
        ),
    )

    @Test fun amoled() = shot("amoled", ThemeMode.Amoled, ui())
    @Test fun light() = shot("light", ThemeMode.Light, ui())
    @Test fun dark() = shot("dark", ThemeMode.Dark, ui(quality = Quality.Divergent))
    @Test fun keypadFolded() = shot("folded", ThemeMode.Amoled, ui(keypad = false))
}
