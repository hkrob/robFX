package app.robfx.widget

import android.content.Context
import app.robfx.data.HistoryRepository
import app.robfx.data.source.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * History for widgets. A widget is drawn by a short-lived background process, so the series behind its sparkline and
 * change is kept on disk: fresh enough is served straight from the file, and when a fetch fails the last one is shown.
 */
class WidgetSeries(context: Context, private val history: HistoryRepository, private val clock: () -> Long = System::currentTimeMillis) {
    @Serializable
    data class Entry(val at: Long, val values: List<Double>)

    private val file = File(context.filesDir, "widget_series.json")
    private val mutex = Mutex()
    private val serializer = MapSerializer(String.serializer(), Entry.serializer())

    private fun read(): Map<String, Entry> = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())

    suspend fun get(from: String, to: String, period: WidgetPeriod): List<Double>? = withContext(Dispatchers.IO) {
        val key = "$from/$to/${period.range.name}"
        val cached = mutex.withLock { read()[key] }
        if (cached != null && clock() - cached.at < FRESH_MS) return@withContext cached.values

        val fetched = withTimeoutOrNull(FETCH_TIMEOUT_MS) { history.load(from, to, period.range).getOrNull() }?.map { it.value }
        if (fetched != null && fetched.size >= 2) {
            mutex.withLock {
                // Drop entries nobody has asked for in a month so the file can't grow without bound.
                val kept = read().filterValues { clock() - it.at < 30L * 24 * 3_600_000 }
                runCatching { file.writeText(json.encodeToString(serializer, kept + (key to Entry(clock(), fetched)))) }
            }
            fetched
        } else {
            cached?.values
        }
    }

    companion object {
        const val FRESH_MS = 3L * 3_600_000
        const val FETCH_TIMEOUT_MS = 6_000L
    }
}
