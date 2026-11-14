package app.robfx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class TestApp : RobFxApp() {
    override fun scheduleBackgroundWork() {}
}

/** The real MainActivity, the real ViewModels and DataStore: what a person does, not a composable in isolation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class AppFlowTest : FlowBase() {
    @Test fun openingUnitPriceShowsTheKeypad() {
        launch()
        openUnitTab()
        assertKeypad()
        shot("flow_unit_price")
    }
}
