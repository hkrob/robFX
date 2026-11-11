package app.robfx

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.robfx.data.HistoryPoint
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.Range
import app.robfx.data.Settings
import app.robfx.data.ThemeMode
import app.robfx.ui.ConverterActions
import app.robfx.ui.ConverterContent
import app.robfx.ui.ConverterUi
import app.robfx.ui.theme.RobFxTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.sin

/** Window shapes beyond a portrait phone: Android 17 no longer lets apps opt out of these on large screens. */
abstract class LayoutShots {
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

    private fun ui() = ConverterUi(
        settings = Settings("USD", "HKD", expr = "1000", range = Range.M1),
        expr = "1000",
        pair = PairRate(7.8449, mapOf("a" to 7.8449, "b" to 7.8461, "c" to 7.8440), 0.0027, Quality.Consensus, 1_794_355_352_000L),
        converted = 7844.9,
        history = HistoryState(
            (0..30).map { i -> HistoryPoint(LocalDate.of(2026, 10, 12).plusDays(i.toLong()), 7.835 + 0.012 * sin(i / 4.0) + i * 0.0004) },
            loading = false,
        ),
        keypadOpen = true, refreshing = false,
    )

    protected fun shot(name: String) {
        rule.setContent { RobFxTheme(ThemeMode.Amoled, dynamic = false) { ConverterContent(ui(), Noop) } }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w1024dp-h640dp-land-xhdpi")
class TabletLandscapeShot : LayoutShots() { @Test fun render() = shot("tablet_landscape") }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w800dp-h360dp-land-xhdpi")
class PhoneLandscapeShot : LayoutShots() { @Test fun render() = shot("phone_landscape") }
