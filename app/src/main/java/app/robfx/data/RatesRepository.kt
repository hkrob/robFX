package app.robfx.data

import android.content.Context
import app.robfx.data.source.RateSource
import app.robfx.data.source.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class RatesRepository(
    context: Context,
    private val sources: List<RateSource>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(context.filesDir, "snapshot.json")
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow(sources.map { s ->
        val t = _snapshot.value.tables[s.name]
        SourceStatus(s.name, ok = t != null, updatedAt = t?.fetchedAt)
    })
    val status: StateFlow<List<SourceStatus>> = _status.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Small file, read once at construction so the first frame already has numbers.
    private fun load(): Snapshot = runCatching { json.decodeFromString<Snapshot>(file.readText()) }.getOrDefault(Snapshot())

    fun pair(from: String, to: String): PairRate? {
        if (from == to) return PairRate(1.0, emptyMap(), 0.0, Quality.Consensus, clock())
        return Consensus.pair(_snapshot.value.tables.values, from, to, clock())
    }

    fun newestFetch(): Long? = _snapshot.value.tables.values.maxOfOrNull { it.fetchedAt }

    fun isStale(maxAgeMs: Long = 15 * 60 * 1000L): Boolean = newestFetch()?.let { clock() - it > maxAgeMs } ?: true

    /** Queries every source at once; a source that fails keeps its previous table and is marked as failing. Returns true if any answered. */
    suspend fun refresh(): Boolean = mutex.withLock {
        _refreshing.value = true
        try {
            val results = coroutineScope {
                sources.map { s ->
                    async {
                        val t0 = clock()
                        try {
                            val table = s.latest()
                            s.name to Result.success(table to (clock() - t0))
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            s.name to Result.failure(e)
                        }
                    }
                }.awaitAll()
            }
            val ok = results.mapNotNull { (n, r) -> r.getOrNull()?.let { n to it } }
            if (ok.isNotEmpty()) {
                _snapshot.update { snap -> Snapshot(snap.tables + ok.associate { (n, v) -> n to v.first }) }
                persist()
            }
            _status.value = results.map { (n, r) ->
                val prev = _snapshot.value.tables[n]
                r.fold(
                    { SourceStatus(n, true, it.second, it.first.fetchedAt) },
                    { SourceStatus(n, false, null, prev?.fetchedAt, it.message ?: it.javaClass.simpleName) },
                )
            }
            ok.isNotEmpty()
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        runCatching { file.writeText(json.encodeToString(Snapshot.serializer(), _snapshot.value)) }
    }
}
