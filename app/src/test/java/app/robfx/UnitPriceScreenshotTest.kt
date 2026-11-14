package app.robfx

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.ThemeMode
import app.robfx.ui.UnitPriceActions
import app.robfx.ui.UnitPriceContent
import app.robfx.ui.UnitPriceUi
import app.robfx.ui.theme.RobFxTheme
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import app.robfx.unitprice.UnitPrice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

abstract class UnitShots {
    @get:Rule val rule = createComposeRule()

    private object Noop : UnitPriceActions {
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
        override fun openSettings() {}
        override fun selectTab(t: HomeTab) {}
    }

    private val usdToHkd = PairRate(7.8449, mapOf("a" to 7.8449), 0.0, Quality.Consensus, 1_794_355_352_000L)
    private val rate: (String, String) -> PairRate? = { f, t -> if (f == "USD" && t == "HKD") usdToHkd else null }

    protected val example = Comparison(
        group = UnitGroup.Weight, basis = "100g", currency = "HKD",
        items = listOf(UnitItem("5", "100", "", "g", "HKD"), UnitItem("7", "150", "", "g", "HKD")),
    )

    protected val multipack = Comparison(
        group = UnitGroup.Volume, basis = "l", currency = "HKD",
        items = listOf(
            UnitItem("34", "330", "6", "ml", "HKD"),
            UnitItem("5.49", "1", "", "l", "USD"),
            UnitItem("", "", "", "ml", "HKD"),
        ),
    )

    protected fun shot(name: String, mode: ThemeMode, c: Comparison, focus: FocusTarget?, keypad: Boolean = true) {
        val ev = UnitPrice.evaluate(c, rate)
        rule.setContent {
            RobFxTheme(mode, dynamic = false) { UnitPriceContent(UnitPriceUi(c, ev, focus, keypad, true, usdToHkd), Noop) }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class UnitPhoneShots : UnitShots() {
    @Test fun example() = shot("unit_example", ThemeMode.Amoled, example, FocusTarget(1, Field.Size))
    @Test fun firstRun() = shot("unit_first_run", ThemeMode.Amoled, Comparison.fresh(UnitGroup.Weight, "HKD", us = false), FocusTarget(0, Field.Price))
    @Test fun countGroup() = shot(
        "unit_count", ThemeMode.Amoled,
        Comparison(group = UnitGroup.Count, basis = "each", currency = "HKD", items = listOf(UnitItem("38", "12", "", "each", "HKD"), UnitItem("22", "6", "", "each", "HKD"))),
        FocusTarget(0, Field.Qty),
    )
    @Test fun multipackLight() = shot("unit_multipack_light", ThemeMode.Light, multipack, FocusTarget(0, Field.Price))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w1024dp-h640dp-land-xhdpi")
class UnitTabletShot : UnitShots() {
    @Test fun wide() = shot("unit_tablet", ThemeMode.Amoled, multipack, FocusTarget(1, Field.Price))
}
