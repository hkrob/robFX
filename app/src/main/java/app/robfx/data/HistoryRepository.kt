package app.robfx.data

import app.robfx.data.source.RateSource
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** Series for the chart, tried source by source in order, cached in memory for a while. */
class HistoryRepository(
    private val sources: List<RateSource>,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val from: String, val to: String, val range: Range, val day: LocalDate)
    private class Entry(val at: Long, val points: List<HistoryPoint>)

    private val cache = ConcurrentHashMap<Key, Entry>()

    suspend fun load(from: String, to: String, range: Range, force: Boolean = false): Result<List<HistoryPoint>> {
        val end = today()
        val key = Key(from, to, range, end)
        if (!force) cache[key]?.takeIf { clock() - it.at < 30 * 60_000L }?.let { return Result.success(it.points) }

        var lastError: Throwable? = null
        for (s in sources) {
            try {
                val pts = s.history(from, to, end.minusDays(range.days), end)
                if (!pts.isNullOrEmpty()) {
                    cache[key] = Entry(clock(), pts)
                    return Result.success(pts)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("No source has history for $from/$to"))
    }
}
