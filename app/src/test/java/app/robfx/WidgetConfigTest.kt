package app.robfx

import app.robfx.data.source.json
import app.robfx.widget.WidgetConfig
import app.robfx.widget.WidgetMath
import app.robfx.widget.WidgetPeriod
import app.robfx.widget.WidgetTap
import app.robfx.widget.WidgetTheme
import app.robfx.widget.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigTest {
    @Test fun survivesJsonAndSparseOldConfigsStillLoad() {
        val c = WidgetConfig(
            WidgetType.Watchlist, "HKD", "USD", amount = "250", followMarkets = false, pairs = listOf("USD/HKD", "AUD/USD"),
            theme = WidgetTheme.Dynamic, transparency = 30, showSpark = false, period = WidgetPeriod.Month, tap = WidgetTap.Refresh,
        )
        assertEquals(c, json.decodeFromString(WidgetConfig.serializer(), json.encodeToString(WidgetConfig.serializer(), c)))
        val sparse = json.decodeFromString(WidgetConfig.serializer(), """{"type":"Convert","from":"HKD"}""")
        assertEquals(WidgetType.Convert, sparse.type)
        assertEquals(WidgetTheme.Black, sparse.theme)
        assertEquals(WidgetPeriod.Week, sparse.period)
        assertTrue(sparse.showChange)
    }

    @Test fun amountNeverBreaksTheWidget() {
        fun v(s: String) = WidgetConfig(amount = s).amountValue
        assertEquals(1000.0, v("1,000"), 0.0)
        assertEquals(2.5, v(" 2.5 "), 0.0)
        assertEquals(1.0, v(""), 0.0)
        assertEquals(1.0, v("abc"), 0.0)
        assertEquals(1.0, v("0"), 0.0)
        assertEquals(1.0, v("-5"), 0.0)
    }

    @Test fun defaultsDrawOnWhatTheAppShows() {
        val conv = WidgetConfig.default(WidgetType.Convert, "HKD", "AUD")
        assertEquals("100", conv.amount)
        assertEquals(listOf("AUD", "USD", "EUR"), conv.targets)
        assertTrue(WidgetConfig.default(WidgetType.Watchlist, "HKD", "USD").followMarkets)
        assertEquals("HKD" to "USD", WidgetConfig.default(WidgetType.Single, "HKD", "USD").let { it.from to it.to })
        // The source currency is never also a target.
        assertTrue("USD" !in WidgetConfig.default(WidgetType.Convert, "USD", "EUR").targets)
    }

    @Test fun pairAndTargetEditingRules() {
        var c = WidgetConfig(WidgetType.Watchlist, followMarkets = false)
        c = c.withPairAdded("USD", "HKD").withPairAdded("USD", "HKD").withPairAdded("USD", "USD")
        assertEquals(listOf("USD/HKD"), c.pairs)
        repeat(20) { c = c.withPairAdded("USD", listOf("EUR", "GBP", "JPY", "CNY", "AUD", "CAD", "CHF", "NZD", "SGD", "KRW")[it % 10]) }
        assertEquals(WidgetConfig.MAX_PAIRS, c.pairs.size)
        // Junk in storage is ignored rather than shown.
        assertEquals(1, c.copy(pairs = listOf("USD/HKD", "XXX/YYY", "broken", "USD/USD")).ownPairs.size)

        var t = WidgetConfig(WidgetType.Convert, from = "HKD")
        t = t.withTargetAdded("USD").withTargetAdded("USD").withTargetAdded("HKD")
        assertEquals(listOf("USD"), t.targets)
        assertEquals(listOf("USD"), t.copy(targets = listOf("USD", "HKD", "ZZZ")).validTargets)
        assertEquals("EUR" to "USD", WidgetConfig(from = "USD", to = "EUR").flipped().let { it.from to it.to })
    }

    @Test fun changeOverEachPeriod() {
        val s = listOf(100.0, 102.0, 101.0, 104.0)
        assertEquals(0.04, WidgetMath.change(s, WidgetPeriod.Week)!!, 1e-9)
        assertEquals(0.04, WidgetMath.change(s, WidgetPeriod.Month)!!, 1e-9)
        assertEquals(104.0 / 101.0 - 1, WidgetMath.change(s, WidgetPeriod.Day)!!, 1e-9)
        assertNull(WidgetMath.change(listOf(1.0), WidgetPeriod.Week))
        assertNull(WidgetMath.change(null, WidgetPeriod.Day))
        assertNull(WidgetMath.change(listOf(0.0, 1.0), WidgetPeriod.Week))
    }
}
