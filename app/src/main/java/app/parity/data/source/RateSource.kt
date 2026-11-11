package app.parity.data.source

import app.parity.data.Currencies
import app.parity.data.HistoryPoint
import app.parity.data.RateTable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface RateSource {
    val name: String

    /** Units of every supported currency per 1 USD. */
    suspend fun latest(): RateTable

    /** Daily series of [from] -> [to], or null when this source can't produce one. */
    suspend fun history(from: String, to: String, start: LocalDate, end: LocalDate): List<HistoryPoint>? = null
}

internal val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.numbers(upper: Boolean): Map<String, Double> = buildMap {
    for ((k, v) in this@numbers) {
        val d = (v as? JsonPrimitive)?.doubleOrNull ?: continue
        val code = if (upper) k.uppercase() else k
        if (code in Currencies.codes && d > 0) put(code, d)
    }
}

/** ECB reference rates. Daily, authoritative, ~30 currencies, and the only source here with a real time-series endpoint. */
class FrankfurterSource(private val http: Http, private val clock: () -> Long = System::currentTimeMillis) : RateSource {
    override val name = "Frankfurter"
    private val base = "https://api.frankfurter.dev/v1"

    override suspend fun latest(): RateTable {
        val o = json.parseToJsonElement(http.get("$base/latest?base=USD")).jsonObject
        val rates = o.getValue("rates").jsonObject.numbers(upper = true) + ("USD" to 1.0)
        return RateTable(name, clock(), o["date"]?.jsonPrimitive?.content, rates)
    }

    override suspend fun history(from: String, to: String, start: LocalDate, end: LocalDate): List<HistoryPoint>? {
        val body = try {
            http.get("$base/$start..$end?base=$from&symbols=$to")
        } catch (e: HttpStatusException) {
            // 404/422: a currency the ECB doesn't publish. Not a failure, just not ours to answer.
            if (e.code in 400..499) return null else throw e
        }
        val rates = json.parseToJsonElement(body).jsonObject["rates"]?.jsonObject ?: return null
        return rates.mapNotNull { (day, v) ->
            val d = v.jsonObject[to]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            HistoryPoint(LocalDate.parse(day), d)
        }.sortedBy { it.date }.ifEmpty { null }
    }
}

/** fawazahmed0/currency-api: static JSON on two CDNs, 200+ currencies, and a dated snapshot per day. */
class FawazSource(private val http: Http, private val clock: () -> Long = System::currentTimeMillis) : RateSource {
    override val name = "Currency-API"

    private fun urls(version: String, code: String) = listOf(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$version/v1/currencies/${code.lowercase()}.min.json",
        "https://$version.currency-api.pages.dev/v1/currencies/${code.lowercase()}.min.json",
    )

    private suspend fun fetch(version: String, code: String): JsonObject {
        var last: Exception? = null
        for (u in urls(version, code)) {
            try {
                return json.parseToJsonElement(http.get(u)).jsonObject
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                last = e
            }
        }
        throw last ?: IllegalStateException("no url")
    }

    override suspend fun latest(): RateTable {
        val o = fetch("latest", "usd")
        val rates = o.getValue("usd").jsonObject.numbers(upper = true) + ("USD" to 1.0)
        return RateTable(name, clock(), o["date"]?.jsonPrimitive?.content, rates)
    }

    /**
     * No range endpoint, so sample dated snapshots. Pages can be missing for a given day; those are skipped,
     * and a series with fewer than a handful of points is treated as unavailable.
     */
    override suspend fun history(from: String, to: String, start: LocalDate, end: LocalDate): List<HistoryPoint>? {
        val span = ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
        val samples = minOf(span + 1, 28)
        val days = (0 until samples).map { start.plusDays(Math.round(it * span.toDouble() / (samples - 1).coerceAtLeast(1))) }.distinct()
        val gate = Semaphore(6)
        val points = coroutineScope {
            days.map { day ->
                async {
                    gate.withPermit {
                        // "latest" for today: a dated snapshot for today may not be published yet.
                        val version = if (day >= end) "latest" else day.toString()
                        try {
                            val v = fetch(version, from).getValue(from.lowercase()).jsonObject[to.lowercase()]?.jsonPrimitive?.doubleOrNull
                            if (v != null) HistoryPoint(day, v) else null
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return points.sortedBy { it.date }.takeIf { it.size >= 4 }
    }
}

/** ExchangeRate-API's open endpoint: ~160 currencies, refreshed daily, no key. */
class ErApiSource(private val http: Http, private val clock: () -> Long = System::currentTimeMillis) : RateSource {
    override val name = "ExchangeRate-API"

    override suspend fun latest(): RateTable {
        val o = json.parseToJsonElement(http.get("https://open.er-api.com/v6/latest/USD")).jsonObject
        require(o["result"]?.jsonPrimitive?.content == "success") { "open.er-api result=${o["result"]}" }
        val rates = o.getValue("rates").jsonObject.numbers(upper = true) + ("USD" to 1.0)
        return RateTable(name, clock(), o["time_last_update_utc"]?.jsonPrimitive?.content, rates)
    }
}
