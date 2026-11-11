package app.robfx.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** One source's view of the world: units of each currency per 1 USD. */
@Serializable
data class RateTable(val source: String, val fetchedAt: Long, val asOf: String? = null, val rates: Map<String, Double>)

@Serializable
data class Snapshot(val tables: Map<String, RateTable> = emptyMap())

enum class Quality { Consensus, Single, Divergent, Stale }

data class PairRate(
    val rate: Double,
    val perSource: Map<String, Double>,
    val spread: Double,
    val quality: Quality,
    val updatedAt: Long,
)

data class SourceStatus(
    val name: String,
    val ok: Boolean,
    val latencyMs: Long? = null,
    val updatedAt: Long? = null,
    val error: String? = null,
)

data class HistoryPoint(val date: LocalDate, val value: Double)

enum class Range(val label: String, val days: Long) {
    W1("1W", 7), M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365), Y5("5Y", 1826),
}

enum class ThemeMode(val label: String) { System("Auto"), Light("Light"), Dark("Dark"), Amoled("AMOLED") }
