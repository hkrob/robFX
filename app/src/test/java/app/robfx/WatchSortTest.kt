package app.robfx

import app.robfx.data.HistoryPoint
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.WatchPair
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WatchSortTest {
    private fun row(key: String, rate: Double?, change: Double?): WatchRow {
        val (f, t) = key.split("/")
        val pts = change?.let { listOf(HistoryPoint(LocalDate.of(2026, 1, 1), 1.0), HistoryPoint(LocalDate.of(2026, 1, 2), 1.0 + it)) }
        return WatchRow(WatchPair(f, t), rate?.let { PairRate(it, emptyMap(), 0.0, Quality.Consensus, 0) }, pts)
    }

    private val rows = listOf(
        row("USD/JPY", 154.0, 0.02),
        row("AUD/USD", 0.65, null),
        row("EUR/GBP", 0.84, -0.01),
        row("HKD/USD", null, 0.005),
    )

    private fun keys(l: List<WatchRow>) = l.map { it.pair.key }

    @Test fun customKeepsTheOrderTheyWereAddedIn() {
        assertEquals(keys(rows), keys(WatchSorting.sort(rows, WatchSort.Custom, false)))
    }

    @Test fun pairSortsAlphabeticallyBothWays() {
        assertEquals(listOf("AUD/USD", "EUR/GBP", "HKD/USD", "USD/JPY"), keys(WatchSorting.sort(rows, WatchSort.Pair, false)))
        assertEquals(listOf("USD/JPY", "HKD/USD", "EUR/GBP", "AUD/USD"), keys(WatchSorting.sort(rows, WatchSort.Pair, true)))
    }

    @Test fun changeSortsAndUnknownAlwaysSinksLast() {
        assertEquals(listOf("USD/JPY", "HKD/USD", "EUR/GBP", "AUD/USD"), keys(WatchSorting.sort(rows, WatchSort.Change, true)))
        assertEquals(listOf("EUR/GBP", "HKD/USD", "USD/JPY", "AUD/USD"), keys(WatchSorting.sort(rows, WatchSort.Change, false)))
    }

    @Test fun rateSortsAndUnknownAlwaysSinksLast() {
        assertEquals(listOf("USD/JPY", "EUR/GBP", "AUD/USD", "HKD/USD"), keys(WatchSorting.sort(rows, WatchSort.Rate, true)))
        assertEquals(listOf("AUD/USD", "EUR/GBP", "USD/JPY", "HKD/USD"), keys(WatchSorting.sort(rows, WatchSort.Rate, false)))
    }

    @Test fun tappingTheActiveChipFlipsOthersStartNaturally() {
        assertEquals(WatchSort.Change to false, WatchSorting.tap(WatchSort.Change, true, WatchSort.Change))
        assertEquals(WatchSort.Change to true, WatchSorting.tap(WatchSort.Pair, false, WatchSort.Change))
        assertEquals(WatchSort.Pair to false, WatchSorting.tap(WatchSort.Rate, true, WatchSort.Pair))
        assertEquals(WatchSort.Custom to false, WatchSorting.tap(WatchSort.Change, true, WatchSort.Custom))
    }
}
