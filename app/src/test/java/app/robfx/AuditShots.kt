package app.robfx

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders of whole screens and sheets in the real app, for looking at. Assertions live in the other flow tests. */
abstract class AuditBase : FlowBase() {
    protected val sample = Comparison(
        group = UnitGroup.Weight, basis = "100g", currency = "HKD", tag = "Pasta",
        items = listOf(UnitItem("5", "100", "", "g", "HKD", label = "Coles"), UnitItem("7", "150", "", "g", "HKD", label = "Woolworths")),
    )

    protected fun settle() { rule.waitForIdle(); rule.mainClock.advanceTimeBy(1200); rule.waitForIdle() }

    protected fun all(prefix: String) {
        seed(sample)
        launch(); settle(); shot("${prefix}_markets")
        rule.onNodeWithContentDescription("Settings").performClick(); settle(); shot("${prefix}_settings")
        androidx.test.espresso.Espresso.pressBack(); settle()
        rule.onNodeWithContentDescription("Rate alerts").performClick(); settle(); shot("${prefix}_alerts")
        androidx.test.espresso.Espresso.pressBack(); settle()
        if (rule.onAllNodesWithText("Add pair").fetchSemanticsNodes().isEmpty()) {
            rule.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText("Add pair")); settle()
        }
        rule.onNodeWithText("Add pair").performClick(); settle(); shot("${prefix}_picker")
        androidx.test.espresso.Espresso.pressBack(); settle()
        rule.onAllNodesWithText("USD", substring = true)[0].performClick(); settle(); shot("${prefix}_converter")
        androidx.test.espresso.Espresso.pressBack(); settle()
        openUnitTab(); settle()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Coles").fetchSemanticsNodes().isNotEmpty() }
        shot("${prefix}_unit")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
class AuditPhone : AuditBase() { @Test fun shots() = all("audit_phone") }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w360dp-h640dp-port-xhdpi")
class AuditSmall : AuditBase() { @Test fun shots() = all("audit_small") }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
class AuditBigFont : AuditBase() {
    @Test fun shots() { RuntimeEnvironment.setFontScale(1.3f); all("audit_font13") }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w800dp-h360dp-land-xhdpi")
class AuditLandscape : AuditBase() { @Test fun shots() = all("audit_land") }
