package app.robfx

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.robfx.calc.Format
import app.robfx.data.HistoryPoint
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.Range
import app.robfx.data.Settings
import app.robfx.data.ThemeMode
import app.robfx.data.WatchPair
import app.robfx.ui.ConverterActions
import app.robfx.ui.ConverterContent
import app.robfx.ui.ConverterUi
import app.robfx.ui.DecimalsRow
import app.robfx.ui.HomeActions
import app.robfx.ui.HomeContent
import app.robfx.ui.HomeUi
import app.robfx.ui.UnitPriceActions
import app.robfx.ui.UnitPriceContent
import app.robfx.ui.UnitPriceUi
import app.robfx.ui.theme.RobFxTheme
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import app.robfx.unitprice.UnitPrice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

class DecimalsFormatTest {
    private val auto: (Double) -> String = { "auto" }

    @Test fun autoDefersAndFixedIsExact() {
        assertEquals("auto", Format.number(7.8449, Format.AUTO, auto))
        assertEquals("7.84", Format.number(7.8449, 2, auto))
        assertEquals("8", Format.number(7.8449, 0, auto))
        assertEquals("1,234.500", Format.number(1234.5, 3, auto))
    }

    @Test fun fittingStepsDownOneDecimalAtATimeSoNothingClips() {
        assertEquals(listOf("7.8449", "7.845", "7.84", "7.8", "8"), Format.fitting(7.8449, 4, listOf("x")))
        assertEquals(listOf("x", "y"), Format.fitting(7.8449, Format.AUTO, listOf("x", "y")))
        // Rounded forms that coincide are offered once.
        assertEquals(listOf("2.00", "2.0", "2"), Format.fitting(2.0, 2, emptyList()))
    }

    @Test fun settingsDefaultToAuto() {
        val s = Settings("USD", "HKD")
        assertEquals(Format.AUTO, s.rateDecimals)
        assertEquals(Format.AUTO, s.amountDecimals)
        assertEquals(Format.AUTO, s.unitDecimals)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
class DecimalsScreenTest {
    @get:Rule val rule = createComposeRule()

    private object NoConv : ConverterActions {
        override fun back() {}
        override fun toggleWatch() {}
        override fun key(k: String) {}
        override fun swap() {}
        override fun refresh() {}
        override fun setRange(r: Range) {}
        override fun retryHistory() {}
        override fun toggleKeypad() {}
        override fun showKeypad() {}
        override fun open(sheet: Sheet) {}
    }

    private object NoHome : HomeActions {
        override fun open(w: WatchPair) {}
        override fun remove(w: WatchPair) {}
        override fun toggleEditing() {}
        override fun startEditing() {}
        override fun tapSort(s: WatchSort) {}
        override fun selectTab(t: HomeTab) {}
        override fun add() {}
        override fun refresh() {}
        override fun sheet(s: Sheet) {}
    }

    private object NoUnit : UnitPriceActions {
        override fun focusOn(item: Int, field: Field) {}
        override fun key(k: String) {}
        override fun next() {}
        override fun setGroup(g: UnitGroup) {}
        override fun setBasis(id: String) {}
        override fun addItem() {}
        override fun removeItem(i: Int) {}
        override fun clearAll() {}
        override fun toggleKeypad() {}
        override fun pickUnit(item: Int) {}
        override fun pickCurrency(item: Int) {}
        override fun pickHomeCurrency() {}
        override fun openRecents() {}
        override fun pickTag() {}
        override fun pickLabel(item: Int) {}
        override fun openSettings() {}
        override fun selectTab(t: HomeTab) {}
    }

    private val rate = PairRate(7.8449, mapOf("a" to 7.8449), 0.0, Quality.Consensus, 1_794_355_352_000L)
    private val points = (0..6).map { HistoryPoint(LocalDate.of(2026, 11, 1).plusDays(it.toLong()), 7.80 + it * 0.0071) }

    private fun converter(settings: Settings) = rule.setContent {
        RobFxTheme(ThemeMode.Amoled, false) {
            ConverterContent(ConverterUi(settings, "1000", rate, 7844.9, HistoryState(points, loading = false), keypadOpen = true, refreshing = false), NoConv)
        }
    }

    @Test fun converterFollowsRateAndAmountSettings() {
        converter(Settings("USD", "HKD", expr = "1000", rateDecimals = 2, amountDecimals = 0))
        rule.onNodeWithText("1 USD = 7.84 HKD").assertExists()
        rule.onNodeWithText("7,845").assertExists()
        // The chart's own readout follows the rate setting too.
        rule.onNodeWithText("7.84").assertExists()
    }

    @Test fun converterOnAutoIsUnchanged() {
        converter(Settings("USD", "HKD", expr = "1000"))
        rule.onNodeWithText("1 USD = 7.8449 HKD").assertExists()
        rule.onNodeWithText("7,844.9").assertExists()
    }

    @Test fun marketsFollowTheRateSetting() {
        val rows = listOf(WatchRow(WatchPair("USD", "HKD"), rate, points), WatchRow(WatchPair("AUD", "USD"), rate.copy(rate = 0.678102), points))
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { HomeContent(HomeUi(rows, rate, false, false, rateDecimals = 3), NoHome) } }
        rule.onNodeWithText("7.845").assertExists()
        rule.onNodeWithText("0.678").assertExists()
    }

    @Test fun unitPricesFollowTheirSetting() {
        val c = Comparison(group = UnitGroup.Weight, basis = "100g", currency = "HKD", items = listOf(UnitItem("5", "100", "", "g", "HKD"), UnitItem("7", "150", "", "g", "HKD")))
        val ev = UnitPrice.evaluate(c) { _, _ -> null }
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { UnitPriceContent(UnitPriceUi(c, ev, null, true, false, null, decimals = 3), NoUnit) } }
        rule.onNodeWithText("HK$4.667").assertExists()
        rule.onNodeWithText("HK$5.000").assertExists()
    }

    @Test fun theSettingsRowReportsTheChoice() {
        var picked = Int.MIN_VALUE
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { DecimalsRow("Rates", "sub", Format.AUTO) { picked = it } } }
        rule.onNodeWithContentDescription("Rates: 2 decimals").performClick()
        assertEquals(2, picked)
        rule.onNodeWithContentDescription("Rates: automatic").performClick()
        assertEquals(Format.AUTO, picked)
    }
}
