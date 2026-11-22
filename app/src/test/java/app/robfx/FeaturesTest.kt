package app.robfx

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.robfx.data.Alert
import app.robfx.data.AlertChecker
import app.robfx.data.ExportPeriod
import app.robfx.data.HistoryPoint
import app.robfx.data.RateCsv
import app.robfx.data.SourceStatus
import app.robfx.ui.offlineText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

class MoveAndRepeatAlertTest {
    private fun eval(a: Alert, r: Double) = AlertChecker.evaluate(listOf(a), 100L) { _, _ -> r }

    @Test fun moveAlertFiresEitherWayAndStopsUnlessRepeating() {
        val a = Alert(1, "AUD", "HKD", 5.0, true, kind = Alert.MOVE, movePct = 2.0, base = 5.0)
        assertTrue(eval(a, 5.09).fired.isEmpty())
        val down = eval(a, 4.89)
        assertEquals(1, down.fired.size)
        assertTrue(!down.alerts.single().active)
        val again = eval(a.copy(repeat = true), 5.11)
        assertEquals(1, again.fired.size)
        // Next move is measured from where this one ended.
        assertEquals(5.11, again.alerts.single().base!!, 1e-9)
        assertTrue(again.alerts.single().active)
    }

    @Test fun moveAlertWithoutABaseTakesTodaysRateFirst() {
        val a = Alert(1, "AUD", "HKD", 0.0, true, kind = Alert.MOVE, movePct = 2.0)
        val e = eval(a, 5.2)
        assertTrue(e.fired.isEmpty())
        assertEquals(5.2, e.alerts.single().base!!, 1e-9)
    }

    @Test fun repeatingTargetWaitsForTheRateToComeBack() {
        val a = Alert(1, "AUD", "HKD", 5.3, above = true, repeat = true)
        val hit = eval(a, 5.31)
        assertEquals(1, hit.fired.size)
        val waiting = hit.alerts.single()
        assertTrue(waiting.active && waiting.waiting)
        assertTrue(eval(waiting, 5.35).fired.isEmpty())
        val rearmed = eval(waiting, 5.25).alerts.single()
        assertTrue(!rearmed.waiting)
        assertEquals(1, eval(rearmed, 5.32).fired.size)
    }
}

class RateCsvTest {
    @Test fun weekendsCarryTheLastPublishedRate() {
        // 2026-07-03 is a Friday.
        val p = ExportPeriod("t", LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6))
        val pts = listOf(HistoryPoint(LocalDate.of(2026, 7, 3), 5.25), HistoryPoint(LocalDate.of(2026, 7, 6), 5.3))
        val lines = RateCsv.build("AUD", "HKD", p, pts).trim().lines()
        assertEquals("date,1 AUD in HKD,rate published", lines[0])
        assertEquals("2026-07-04,5.25,2026-07-03", lines[2])
        assertEquals("2026-07-05,5.25,2026-07-03", lines[3])
        assertEquals("2026-07-06,5.3,2026-07-06", lines[4])
    }

    @Test fun financialYearsFollowAustraliaAndHongKong() {
        val ps = ExportPeriod.all(LocalDate.of(2026, 11, 22))
        val au = ps.first { it.label == "Australian FY 2025–26" }
        assertEquals(LocalDate.of(2025, 7, 1), au.start)
        assertEquals(LocalDate.of(2026, 6, 30), au.end)
        val auNow = ps.first { it.label == "Australian FY 2026–27 so far" }
        assertEquals(LocalDate.of(2026, 11, 22), auNow.end)
        val hk = ps.first { it.label == "Hong Kong FY 2025–26" }
        assertEquals(LocalDate.of(2025, 4, 1), hk.start)
        assertEquals(LocalDate.of(2026, 3, 31), hk.end)
    }
}

class OfflineTextTest {
    private val now = 1_000_000_000L

    @Test fun quietWhileAnySourceWorks() {
        assertNull(offlineText(listOf(SourceStatus("a", true, updatedAt = now), SourceStatus("b", false, updatedAt = now, error = "x")), now))
    }

    @Test fun saysHowOldTheRatesAreWhenEverySourceFails() {
        val t = offlineText(listOf(SourceStatus("a", false, updatedAt = now - 3_600_000L, error = "x")), now)
        assertNotNull(t)
        assertTrue(t!!.startsWith("Offline"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class NewSheetsFlow : FlowBase() {
    private fun settle() { rule.waitForIdle(); rule.mainClock.advanceTimeBy(800); rule.waitForIdle() }

    @Test fun moreMenuReachesOtherCurrenciesExportAndMoveAlerts() {
        launch()
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasText("/", substring = true))[0].performClick()
        settle()
        tap("More"); settle()
        rule.onNodeWithText("In other currencies").performClick(); settle()
        rule.onNodeWithText("Add currency").assertExists()
        shot("feature_multi")
        androidx.test.espresso.Espresso.pressBack(); settle()

        tap("More"); settle()
        rule.onNodeWithText("Export rate history").performClick(); settle()
        rule.onNodeWithText("Australian FY 2025\u201326").assertExists()
        shot("feature_export")
        androidx.test.espresso.Espresso.pressBack(); settle()

        tap("Rate alerts"); settle()
        rule.onNodeWithText("Moves ±").performClick(); settle()
        rule.onNodeWithText("Keep watching").assertExists()
        shot("feature_move_alert")
    }
}
