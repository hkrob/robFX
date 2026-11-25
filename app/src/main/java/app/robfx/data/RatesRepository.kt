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
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class RatesRepository(
    context: Context,
    private val sources: List<RateSource>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wise: app.robfx.data.source.WiseQuotes? = null,
    /** The pairs worth a per-pair quote: what's on screen, in Markets and in alerts. */
    private val pairsInUse: suspend () -> Collection<Pair<String, String>> = { emptyList() },
) {
    private val file = File(context.filesDir, "snapshot.json")
    private val mutex = Mutex()

    private val _snapshot = MutableStateFlow(load())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _status = MutableStateFlow(sources.map { s ->
        val t = _snapshot.value.tables[s.name]
        SourceStatus(s.name, ok = t != null, updatedAt = t?.fetchedAt, dataAt = t?.dataTime)
    } + listOfNotNull(wise?.let { w -> quoteStatus(w.name, ok = _snapshot.value.quotes.isNotEmpty(), latency = null, error = null) }))
    val status: StateFlow<List<SourceStatus>> = _status.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Small file, read once at construction so the first frame already has numbers.
    private fun load(): Snapshot = runCatching { json.decodeFromString<Snapshot>(file.readText()) }.getOrDefault(Snapshot())

    fun pair(from: String, to: String): PairRate? {
        if (from == to) return PairRate(1.0, emptyMap(), 0.0, Quality.Consensus, clock())
        val snap = _snapshot.value
        // A per-pair quote is just one more table; Consensus ignores it for every pair it doesn't cover.
        val tables = snap.quotes[pairKey(from, to)]?.let { snap.tables.values + it } ?: snap.tables.values
        return Consensus.pair(tables, from, to, clock())
    }

    private fun quoteStatus(name: String, ok: Boolean, latency: Long?, error: String?): SourceStatus {
        val q = _snapshot.value.quotes.values
        return SourceStatus(name, ok, latency, q.maxOfOrNull { it.fetchedAt }, error, q.maxOfOrNull { it.dataTime })
    }

    /** One pair's quote, when the converter opens a pair that has none yet or only an old one. */
    suspend fun refreshPair(from: String, to: String) {
        val w = wise ?: return
        if (from == to) return
        val have = _snapshot.value.quotes[pairKey(from, to)]
        if (have != null && clock() - have.fetchedAt < 10 * 60_000L) return
        val q = runCatching { w.quote(from, to) }.getOrNull() ?: return
        _snapshot.update { it.copy(quotes = it.quotes + (pairKey(from, to) to q)) }
        persist()
    }

    /** Quotes for every pair in use, a few at a time. Returns (answered, latency of the slowest, last error). */
    private suspend fun refreshQuotes(): Triple<Int, Long?, String?> {
        val w = wise ?: return Triple(0, null, null)
        val pairs = runCatching { pairsInUse() }.getOrDefault(emptyList())
            .filter { it.first != it.second }.distinctBy { pairKey(it.first, it.second) }.take(MAX_QUOTED_PAIRS)
        if (pairs.isEmpty()) return Triple(0, null, null)
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        val t0 = clock()
        val lastError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val got = coroutineScope {
            pairs.map { (f, t) ->
                async {
                    gate.withPermit {
                        try {
                            w.quote(f, t)?.let { pairKey(f, t) to it }
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastError.set(e.message ?: e.javaClass.simpleName)
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (got.isNotEmpty()) _snapshot.update { it.copy(quotes = it.quotes + got) }
        return Triple(got.size, clock() - t0, if (got.isEmpty()) lastError.get() ?: "No quotes" else null)
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
            if (ok.isNotEmpty()) _snapshot.update { snap -> snap.copy(tables = snap.tables + ok.associate { (n, v) -> n to v.first }) }
            val (quoted, quoteMs, quoteError) = refreshQuotes()
            if (ok.isNotEmpty() || quoted > 0) persist()
            _status.value = results.map { (n, r) ->
                val prev = _snapshot.value.tables[n]
                r.fold(
                    { SourceStatus(n, true, it.second, it.first.fetchedAt, dataAt = it.first.dataTime) },
                    { SourceStatus(n, false, null, prev?.fetchedAt, it.message ?: it.javaClass.simpleName, prev?.dataTime) },
                )
            } + listOfNotNull(wise?.let { quoteStatus(it.name, ok = quoteError == null, latency = quoteMs, error = quoteError) })
            ok.isNotEmpty() || quoted > 0
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        // Write-then-rename: a process killed mid-write leaves the previous snapshot intact instead of a torn file.
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(Snapshot.serializer(), _snapshot.value))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    companion object {
        /** Enough for a full Markets list plus the converter and a few alerts, without hammering Wise. */
        const val MAX_QUOTED_PAIRS = 16
    }
}
