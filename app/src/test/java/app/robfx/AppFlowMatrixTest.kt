package app.robfx

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** What could differ on a real phone: locale, screen, font size, rotation, backgrounding, saved data, quick tapping. */
abstract class FlowBase {
    @get:Rule val rule = createEmptyComposeRule()

    protected fun launch(): ActivityScenario<MainActivity> {
        val s = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle()
        return s
    }

    protected fun openUnitTab() {
        rule.onNodeWithText("Unit price").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
    }

    protected fun assertKeypad() {
        rule.onNodeWithContentDescription("Hide keypad").assertIsDisplayed()
        rule.onNodeWithContentDescription("7").assertIsDisplayed()
    }

    protected fun shot(name: String) {
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    protected fun seed(c: Comparison) {
        val app = ApplicationProvider.getApplicationContext<Context>().container
        runBlocking { app.settings.setUnitComparison(c) }
    }

    protected fun tap(desc: String) { rule.onNodeWithContentDescription(desc).performClick(); rule.waitForIdle() }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "zh-rHK-w412dp-h892dp-port-xxhdpi")
class HongKongLocaleFlow : FlowBase() {
    @Test fun unitTab() { launch(); openUnitTab(); assertKeypad(); shot("flow_hk") }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w320dp-h568dp-port-xhdpi")
class SmallScreenFlow : FlowBase() {
    @Test fun unitTab() { launch(); openUnitTab(); assertKeypad(); shot("flow_small") }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class BigFontFlow : FlowBase() {
    @Test fun unitTab() {
        RuntimeEnvironment.setFontScale(1.6f)
        launch(); openUnitTab(); assertKeypad(); shot("flow_bigfont")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class SessionFlow : FlowBase() {
    @Test fun savedStateFromEarlierUseStillOpens() {
        seed(
            Comparison(
                group = UnitGroup.Volume, basis = "floz_us", currency = "HKD", tag = "Pasta night, big shop",
                items = List(6) { UnitItem("12${it}.5+3", "3${it}0", "${it + 1}", "ml", if (it % 2 == 0) "HKD" else "USD") },
            ),
        )
        launch(); openUnitTab(); assertKeypad()
        // The saved comparison is loaded off the main thread and shows up once it arrives.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Pasta night, big shop").fetchSemanticsNodes().isNotEmpty() }
        shot("flow_seeded")
    }

    @Test fun typingAddingTaggingAndSwitchingTabsKeepsWorking() {
        launch(); openUnitTab()
        for (k in listOf("5", "0", "0")) tap(k)
        tap("Next field")
        for (k in listOf("1", "0", "0")) tap(k)
        tap("Next field"); tap("Next field")
        for (k in listOf("7", "5")) tap(k)
        rule.onNodeWithText("Add item").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Tag").performClick(); rule.waitForIdle()
        shot("flow_session")
    }

    @Test fun quickTabSwitchingAndBackgrounding() {
        val s = launch()
        repeat(4) {
            openUnitTab(); assertKeypad()
            rule.onNodeWithText("Markets").performClick(); rule.waitForIdle()
            rule.mainClock.advanceTimeBy(500)
        }
        openUnitTab()
        s.moveToState(Lifecycle.State.CREATED); rule.waitForIdle()
        s.moveToState(Lifecycle.State.RESUMED); rule.waitForIdle()
        assertKeypad()
    }

    @Test fun rotationKeepsTheTabAndTheKeypad() {
        val s = launch(); openUnitTab()
        s.recreate(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        assertKeypad()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w800dp-h360dp-land-xhdpi")
class LandscapeFlow : FlowBase() {
    @Test fun unitTab() {
        launch(); openUnitTab()
        rule.onNodeWithContentDescription("7").assertIsDisplayed()
        shot("flow_landscape")
    }
}

/** The first tap builds the Unit price ViewModel; it must not wait on background threads that are busy with the network. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class BusyBackgroundFlow : FlowBase() {
    @Test fun tappingUnitPriceDoesNotWaitForBusyIoThreads() {
        launch()
        // 100 jobs that each hold an IO thread for 4s: more than the pool has, like a burst of slow requests on a bad connection.
        repeat(100) { kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { Thread.sleep(4_000) } }
        Thread.sleep(500)
        val t0 = System.nanoTime()
        openUnitTab()
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("UNIT TAB OPEN WITH BUSY IO: $ms ms")
        assertKeypad()
        org.junit.Assert.assertTrue("opening the tab took $ms ms with the IO pool busy", ms < 1_500)
    }
}
