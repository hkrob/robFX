package app.robfx

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Not "is there a keypad" but "is every key on screen and a usable size", across the shapes real phones come in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class)
class KeypadSweepTest {
    @get:Rule val rule = createEmptyComposeRule()

    private data class Shape(val q: String, val font: Float)

    private val shapes = listOf(
        Shape("w360dp-h640dp-xhdpi", 1.0f), Shape("w360dp-h780dp-xxhdpi", 1.0f), Shape("w384dp-h854dp-xxhdpi", 1.15f),
        Shape("w412dp-h915dp-xxhdpi", 1.0f), Shape("w412dp-h915dp-xxhdpi", 1.3f), Shape("w412dp-h915dp-xxhdpi", 1.5f),
        Shape("w420dp-h911dp-440dpi", 1.0f), Shape("w420dp-h911dp-440dpi", 1.3f), Shape("w450dp-h1000dp-xxxhdpi", 1.0f),
        Shape("w450dp-h1000dp-xxxhdpi", 1.3f), Shape("w480dp-h1040dp-xxxhdpi", 1.0f), Shape("w320dp-h568dp-xhdpi", 1.0f),
        Shape("w411dp-h891dp-xxhdpi", 2.0f), Shape("w600dp-h960dp-xhdpi", 1.0f),
    )

    @Test fun everyKeyIsOnScreenAndUsableOnEveryShape() {
        val problems = mutableListOf<String>()
        for (s in shapes) {
            RuntimeEnvironment.setQualifiers("+${s.q}")
            RuntimeEnvironment.setFontScale(s.font)
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            rule.waitForIdle()
            rule.onNodeWithText("Unit price").performClick()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(1000)

            val root = rule.onRoot().getUnclippedBoundsInRoot()
            for (key in listOf("7", "0", "Equals", "Next field", "Delete")) {
                val b = runCatching { rule.onNodeWithContentDescription(key).getUnclippedBoundsInRoot() }.getOrNull()
                when {
                    b == null -> problems += "${s.q} font ${s.font}: key '$key' missing"
                    b.bottom > root.bottom + 1.dp -> problems += "${s.q} font ${s.font}: key '$key' bottom ${b.bottom} below screen ${root.bottom}"
                    b.top < 0.dp -> problems += "${s.q} font ${s.font}: key '$key' above screen"
                    (b.bottom - b.top) < 40.dp -> problems += "${s.q} font ${s.font}: key '$key' only ${(b.bottom - b.top)} tall"
                    (b.right - b.left) < 60.dp -> problems += "${s.q} font ${s.font}: key '$key' only ${(b.right - b.left)} wide"
                }
            }
            scenario.close()
        }
        println("KEYPAD SWEEP PROBLEMS (${problems.size}):\n" + problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
