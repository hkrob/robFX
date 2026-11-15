package app.robfx

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    @org.junit.Before fun cleanSlate() = TestStorage.reset()

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

    protected fun seed(c: Comparison) = TestStorage.seed(c)

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

    @Test fun tappingAFieldBringsBackAFoldedKeypad() {
        launch(); openUnitTab(); assertKeypad()
        // Fold the keypad away, as a stray tap on the chevron would.
        tap("Hide keypad")
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithContentDescription("Show keypad").assertIsDisplayed()
        rule.onNodeWithContentDescription("7").assertDoesNotExist()
        // Tapping any of the three fields opens it again.
        for (label in listOf("PRICE", "SIZE", "QTY")) {
            rule.onAllNodesWithText(label)[0].performClick()
            rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
            assertKeypad()
            tap("Hide keypad"); rule.mainClock.advanceTimeBy(1000)
            rule.onNodeWithContentDescription("7").assertDoesNotExist()
        }
        // Adding an item is also "about to type".
        rule.onNodeWithText("Add item").performClick()
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        assertKeypad()
    }

    @Test fun namingItemsFlowsThroughToTheVerdict() {
        launch(); openUnitTab()
        // A: 5 for 100, B: 7 for 150, so B is the better value.
        for (k in listOf("5")) tap(k)
        tap("Next field"); for (k in listOf("1", "0", "0")) tap(k)
        tap("Next field"); tap("Next field")
        tap("7"); tap("Next field"); for (k in listOf("1", "5", "0")) tap(k)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Best value: Item B").assertIsDisplayed()

        // Name both shops through the real sheet and keyboard field.
        rule.onNodeWithText("Item A").performClick(); rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Coles")
        rule.onNodeWithText("Save").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Item B").performClick(); rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Woolworths")
        rule.onNodeWithText("Save").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)

        rule.onNodeWithText("Best value: Woolworths").assertIsDisplayed()
        rule.onNodeWithText("Coles").assertIsDisplayed()
        shot("flow_named")
    }

    @Test fun namesYouJustTypedAreOfferedStraightAway() {
        launch(); openUnitTab()
        // Name Item A; nothing has been saved to history yet.
        rule.onNodeWithText("Item A").performClick(); rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Coles")
        rule.onNodeWithText("Save").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        // Naming Item B offers it as a recent, without leaving the tab.
        rule.onNodeWithText("Item B").performClick(); rule.waitForIdle()
        // The list comes from storage on background threads, so wait for it rather than for the fake clock.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("RECENTLY USED").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("RECENTLY USED").assertIsDisplayed()
        // One chip in the sheet plus the name already on Item A's card.
        org.junit.Assert.assertEquals(2, rule.onAllNodesWithText("Coles").fetchSemanticsNodes().size)
        shot("flow_recent_names")
    }

    @Test fun namesAlreadyOnTheSavedComparisonAreRecentAndOfferedToTheOtherItems() {
        // As if typed under an earlier version: stored on the comparison, but never put in the recents list.
        seed(
            Comparison(
                group = UnitGroup.Weight, basis = "100g", currency = "HKD",
                items = listOf(
                    UnitItem("5", "100", "", "g", "HKD", label = "Coles"),
                    UnitItem("7", "150", "", "g", "HKD", label = "Woolworths"),
                    UnitItem("", "", "", "g", "HKD"),
                ),
            ),
        )
        launch(); openUnitTab()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Coles").fetchSemanticsNodes().isNotEmpty() }
        // Naming the third item offers the other two, even though nothing has been saved to history.
        rule.onNodeWithText("Item C").performClick(); rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("RECENTLY USED").fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertEquals(2, rule.onAllNodesWithText("Coles").fetchSemanticsNodes().size)
        org.junit.Assert.assertEquals(2, rule.onAllNodesWithText("Woolworths").fetchSemanticsNodes().size)
        // ...and they were carried into the recents list itself, for the next comparison.
        rule.waitUntil(5_000) { TestStorage.recentLabels().size >= 2 }
        org.junit.Assert.assertEquals(setOf("Coles", "Woolworths"), TestStorage.recentLabels().toSet())
        shot("flow_recent_carried")
    }

    @Test fun tagsYouJustTypedAreOfferedStraightAway() {
        launch(); openUnitTab()
        rule.onNodeWithText("Tag").performClick(); rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Pasta night")
        rule.onNodeWithText("Save").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        // The chip now carries the tag; open it again and the same tag is offered in the list of recents.
        rule.onNodeWithText("Pasta night").performClick(); rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("RECENTLY USED").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("RECENTLY USED").assertIsDisplayed()
    }

    @Test fun converterAmountBringsBackAFoldedKeypad() {
        launch()
        rule.onAllNodesWithText("USD", substring = true)[0].performClick()
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        tap("Hide keypad"); rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithContentDescription("7").assertDoesNotExist()
        // The amount being converted is "1" on a fresh start.
        rule.onNodeWithText("1").performClick()
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithContentDescription("7").assertIsDisplayed()
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
    // Held until the test ends, then released at once, so the saturation can't bleed into whichever test runs next.
    private val gate = java.util.concurrent.CountDownLatch(1)

    @org.junit.After fun releaseThreads() = gate.countDown()

    @Test fun tappingUnitPriceDoesNotWaitForBusyIoThreads() {
        launch()
        // 100 jobs that each hold an IO thread: more than the pool has, like a burst of slow requests on a bad connection.
        repeat(100) { kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { gate.await(8, java.util.concurrent.TimeUnit.SECONDS) } }
        Thread.sleep(500)
        val t0 = System.nanoTime()
        openUnitTab()
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("UNIT TAB OPEN WITH BUSY IO: $ms ms")
        assertKeypad()
        org.junit.Assert.assertTrue("opening the tab took $ms ms with the IO pool busy", ms < 1_500)
    }
}
