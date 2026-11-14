package app.robfx

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import app.robfx.ui.AppRoot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The system back gesture arrives at the activity's dispatcher; pressing it there is what a swipe does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var screen by mutableStateOf(Screen.Converter)
    private var editing by mutableStateOf(false)
    private var unitTab by mutableStateOf(false)

    private fun content() = rule.setContent {
        AppRoot(
            screen, editing,
            unitTabOpen = unitTab,
            onCloseUnitTab = { unitTab = false },
            onBackToMarkets = { screen = Screen.Home },
            onStopEditing = { editing = false },
            markets = { Text("MARKETS") },
            converter = { Text("CONVERTER") },
        )
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun backFromConverterReturnsToMarkets() {
        content()
        rule.onNodeWithText("CONVERTER").assertExists()
        back()
        assertEquals(Screen.Home, screen)
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("MARKETS").assertExists()
    }

    @Test fun backWhileEditingLeavesEditModeBeforeAnythingElse() {
        screen = Screen.Home
        editing = true
        content()
        back()
        assertEquals(false, editing)
        assertEquals(Screen.Home, screen)
    }

    @Test fun backOnTheUnitPriceTabReturnsToMarketsBeforeAnythingElse() {
        screen = Screen.Home
        unitTab = true
        content()
        back()
        assertEquals(false, unitTab)
        assertEquals(Screen.Home, screen)
        // And now Markets is plain again, so the next back belongs to the system.
        assertEquals(false, rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test fun backOnPlainMarketsIsLeftToTheSystem() {
        screen = Screen.Home
        content()
        // No handler is enabled, so the dispatcher would hand the event to the activity (and close the app).
        assertEquals(false, rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class MarketsGesturesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Recorder : app.robfx.ui.HomeActions {
        val log = mutableListOf<String>()
        override fun open(w: app.robfx.data.WatchPair) { log += "open ${w.key}" }
        override fun remove(w: app.robfx.data.WatchPair) { log += "remove ${w.key}" }
        override fun toggleEditing() { log += "toggle" }
        override fun startEditing() { log += "startEditing" }
        override fun tapSort(s: WatchSort) { log += "sort $s" }
        override fun selectTab(t: HomeTab) { log += "tab $t" }
        override fun add() { log += "add" }
        override fun refresh() { log += "refresh" }
        override fun sheet(s: Sheet) { log += "sheet" }
    }

    private fun ui(editing: Boolean) = app.robfx.ui.HomeUi(
        rows = listOf(WatchRow(app.robfx.data.WatchPair("USD", "HKD"), null, null)),
        status = null, editing = editing, refreshing = false,
    )

    @Test fun longPressStartsEditingAndTapStillOpens() {
        val r = Recorder()
        rule.setContent { app.robfx.ui.theme.RobFxTheme(app.robfx.data.ThemeMode.Amoled, false) { app.robfx.ui.HomeContent(ui(false), r) } }
        rule.onNodeWithText("USD/HKD").performTouchInput { click() }
        rule.onNodeWithText("USD/HKD").performTouchInput { longClick() }
        assertEquals(listOf("open USD/HKD", "startEditing"), r.log)
    }

    @Test fun inEditModeRowTapDoesNothingAndMinusRemoves() {
        val r = Recorder()
        rule.setContent { app.robfx.ui.theme.RobFxTheme(app.robfx.data.ThemeMode.Amoled, false) { app.robfx.ui.HomeContent(ui(true), r) } }
        rule.onNodeWithText("USD/HKD").performTouchInput { click() }
        assertEquals(emptyList<String>(), r.log)
        rule.onNodeWithContentDescription("Remove USD/HKD").performClick()
        assertEquals(listOf("remove USD/HKD"), r.log)
    }
}
