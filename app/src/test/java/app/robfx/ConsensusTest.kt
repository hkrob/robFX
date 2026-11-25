package app.robfx

import app.robfx.data.Alert
import app.robfx.data.AlertChecker
import app.robfx.data.Consensus
import app.robfx.data.Quality
import app.robfx.data.RateTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsensusTest {
    private val now = 1_000_000_000L
    private fun t(name: String, hkd: Double, eur: Double = 0.9, age: Long = 0) =
        RateTable(name, now - age, null, mapOf("USD" to 1.0, "HKD" to hkd, "EUR" to eur))

    @Test fun medianOfThreeIgnoresOneBadSource() {
        val p = Consensus.pair(listOf(t("a", 7.84), t("b", 7.85), t("c", 9.9)), "USD", "HKD", now)!!
        assertEquals(7.85, p.rate, 1e-9)
        assertEquals(Quality.Divergent, p.quality)
    }

    @Test fun agreeingSourcesAreConsensus() {
        val p = Consensus.pair(listOf(t("a", 7.8449), t("b", 7.8461), t("c", 7.8440)), "USD", "HKD", now)!!
        assertEquals(Quality.Consensus, p.quality)
        assertEquals(3, p.perSource.size)
        assertEquals(7.8449, p.rate, 1e-9)
    }

    @Test fun twoSourcesAverage() {
        val p = Consensus.pair(listOf(t("a", 7.80), t("b", 7.90)), "USD", "HKD", now)!!
        assertEquals(7.85, p.rate, 1e-9)
    }

    @Test fun crossRateGoesThroughEachSourcesOwnBase() {
        val p = Consensus.pair(listOf(t("a", 8.0, 0.8)), "EUR", "HKD", now)!!
        assertEquals(10.0, p.rate, 1e-9)
        assertEquals(Quality.Single, p.quality)
    }

    @Test fun missingCurrencyIsSkippedNotFatal() {
        val sparse = RateTable("ecb", now, null, mapOf("USD" to 1.0, "EUR" to 0.9))
        val p = Consensus.pair(listOf(sparse, t("b", 7.85)), "USD", "HKD", now)!!
        assertEquals(setOf("b"), p.perSource.keys)
    }

    @Test fun nothingKnownIsNull() {
        assertNull(Consensus.pair(emptyList(), "USD", "HKD", now))
        assertNull(Consensus.pair(listOf(t("a", 7.8)), "USD", "XYZ", now))
    }

    @Test fun oldTablesAreStaleButStillUsed() {
        val p = Consensus.pair(listOf(t("a", 7.8, age = Consensus.FRESH_MS + 1)), "USD", "HKD", now)!!
        assertEquals(Quality.Stale, p.quality)
        assertEquals(7.8, p.rate, 1e-9)
    }

    @Test fun freshTablesOutvoteStaleOnes() {
        val p = Consensus.pair(listOf(t("old", 1.0, age = Consensus.FRESH_MS + 1), t("new", 7.8)), "USD", "HKD", now)!!
        assertEquals(setOf("new"), p.perSource.keys)
    }

    @Test fun aDayOldSourceIsLeftOutWhenFresherOnesHaveMoved() {
        // 25 Nov 2026, 11:05 UTC, AUD/HKD per source (units per 1 USD with USD/AUD 1.5): two daily feeds still on 24 Nov.
        val h = 3_600_000L
        fun tab(name: String, audHkd: Double, dataAgo: Long) =
            RateTable(name, now, null, mapOf("USD" to 1.0, "AUD" to 1.5, "HKD" to 1.5 * audHkd), dataAt = now - dataAgo)
        val p = Consensus.pair(
            listOf(tab("ecb", 5.2081, 20 * h), tab("er", 5.2027, 11 * h), tab("fawaz", 5.1506, 11 * h), tab("coinbase", 5.1497, 0)),
            "AUD", "HKD", now,
        )!!
        assertEquals(setOf("er", "fawaz", "coinbase"), p.perSource.keys)
        assertEquals(5.1506, p.rate, 1e-4)
        assertEquals(Quality.Divergent, p.quality)
    }

    @Test fun oldDataOnATradingDayIsFlaggedAsDelayed() {
        // Wednesday 25 Nov 2026 11:00 UTC; every source is 8h behind.
        val wed = java.time.Instant.parse("2026-11-25T11:00:00Z").toEpochMilli()
        val tabs = listOf("a", "b").map { RateTable(it, wed, null, mapOf("USD" to 1.0, "HKD" to 7.8), dataAt = wed - 8 * 3_600_000L) }
        assertEquals(Quality.Delayed, Consensus.pair(tabs, "USD", "HKD", wed)!!.quality)
    }

    @Test fun fridaysCloseIsNotDelayedAtTheWeekend() {
        val sat = java.time.Instant.parse("2026-11-28T11:00:00Z").toEpochMilli()
        val tabs = listOf("a", "b").map { RateTable(it, sat, null, mapOf("USD" to 1.0, "HKD" to 7.8), dataAt = sat - 13 * 3_600_000L) }
        assertEquals(Quality.Consensus, Consensus.pair(tabs, "USD", "HKD", sat)!!.quality)
        assertTrue(!Consensus.marketOpen(sat))
        assertTrue(Consensus.marketOpen(java.time.Instant.parse("2026-11-29T21:30:00Z").toEpochMilli()))
    }

    @Test fun aPerPairQuoteVotesOnItsPairInEitherDirectionAndNowhereElse() {
        val wise = RateTable("Wise", now, null, mapOf("AUD" to 1.0, "HKD" to 5.15), dataAt = now)
        fun tab(name: String, audHkd: Double) = RateTable(name, now, null, mapOf("USD" to 1.0, "AUD" to 1.5, "HKD" to 1.5 * audHkd, "EUR" to 0.9))
        val tables = listOf(tab("a", 5.14), tab("b", 5.16), wise)
        assertEquals(1 / 5.15, Consensus.pair(tables, "HKD", "AUD", now)!!.perSource.getValue("Wise"), 1e-9)
        assertEquals(setOf("a", "b"), Consensus.pair(tables, "USD", "EUR", now)!!.perSource.keys)
    }

    @Test fun tablesWithoutADataTimeCountAsFetched() {
        val p = Consensus.pair(listOf(t("a", 7.80), t("b", 7.81), t("c", 7.82)), "USD", "HKD", now)!!
        assertEquals(3, p.perSource.size)
    }

    @Test fun alertsTripOnTheRightSide() {
        val up = Alert(1, "USD", "HKD", 7.85, above = true)
        val down = Alert(2, "USD", "HKD", 7.80, above = false)
        val off = Alert(3, "USD", "HKD", 1.0, above = true, active = false)
        val hits = AlertChecker.triggered(listOf(up, down, off)) { _, _ -> 7.86 }
        assertEquals(listOf(1L), hits.map { it.first.id })
        assertTrue(AlertChecker.triggered(listOf(up)) { _, _ -> null }.isEmpty())
    }

    @Test fun aTargetAlreadyPassedIsCaughtBeforeItIsSaved() {
        assertTrue(AlertChecker.alreadyMet(above = true, target = 5.20, now = 5.23))
        assertTrue(AlertChecker.alreadyMet(above = false, target = 5.30, now = 5.23))
        assertTrue(!AlertChecker.alreadyMet(above = true, target = 5.40, now = 5.23))
        assertTrue(!AlertChecker.alreadyMet(above = false, target = 5.10, now = 5.23))
    }

    @Test fun alertsSavedBeforeTriggerTimesStillLoad() {
        val old = """[{"id":1,"from":"AUD","to":"HKD","target":5.4,"above":true,"active":false}]"""
        val l = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(kotlinx.serialization.builtins.ListSerializer(Alert.serializer()), old)
        assertEquals(null, l.single().triggeredAt)
    }
}

class WatchlistDefaultsTest {
    @org.junit.Test fun localPairLeadsAndNothingRepeats() {
        val l = app.robfx.data.defaultWatchlist("HKD" to "USD")
        org.junit.Assert.assertEquals("HKD/USD", l.first().key)
        org.junit.Assert.assertEquals(l.size, l.map { it.key }.toSet().size)
        org.junit.Assert.assertTrue(l.none { it.from == it.to })
        org.junit.Assert.assertTrue(l.size <= 6)
    }

    @org.junit.Test fun usdLocaleDoesNotDuplicateUsdEur() {
        val l = app.robfx.data.defaultWatchlist("USD" to "EUR")
        org.junit.Assert.assertEquals(1, l.count { it.key == "USD/EUR" })
    }
}
