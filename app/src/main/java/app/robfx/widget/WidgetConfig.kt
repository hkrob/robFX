package app.robfx.widget

import app.robfx.data.Currencies
import app.robfx.data.Range
import app.robfx.data.WatchPair
import kotlinx.serialization.Serializable

@Serializable enum class WidgetType(val title: String) { Single("Single pair"), Watchlist("Watchlist"), Convert("Quick convert") }

@Serializable enum class WidgetTheme(val label: String) { Black("Black"), Dark("Dark"), Light("Light"), System("Auto"), Dynamic("Material You") }

@Serializable enum class WidgetPeriod(val label: String, val range: Range) { Day("Day", Range.W1), Week("Week", Range.W1), Month("Month", Range.M1) }

@Serializable enum class WidgetTap(val label: String) { Converter("Open converter"), Markets("Open Markets"), Refresh("Refresh rates") }

/**
 * Everything one widget instance shows and how it looks. One of these is stored per widget on the home screen.
 * Every field has a default, so configs saved by older versions keep loading as fields are added.
 */
@Serializable
data class WidgetConfig(
    val type: WidgetType = WidgetType.Single,
    // Single pair, and the source currency of Quick convert.
    val from: String = "USD",
    val to: String = "EUR",
    /** Typed amount; the rate is shown for this many units. Kept as text so "1,000" or an empty field can't crash a widget. */
    val amount: String = "1",
    // Watchlist: mirror the Markets screen, or its own list of "USD/HKD" keys.
    val followMarkets: Boolean = true,
    val pairs: List<String> = emptyList(),
    // Quick convert: the currencies to convert into.
    val targets: List<String> = emptyList(),
    val theme: WidgetTheme = WidgetTheme.Black,
    /** Background transparency in percent. */
    val transparency: Int = 0,
    val showSpark: Boolean = true,
    val showChange: Boolean = true,
    val showFlags: Boolean = true,
    val showUpdated: Boolean = true,
    val period: WidgetPeriod = WidgetPeriod.Week,
    val tap: WidgetTap = WidgetTap.Converter,
) {
    companion object {
        const val MAX_PAIRS = 8
        const val MAX_TARGETS = 6
        val TRANSPARENCY_STEPS = listOf(0, 15, 30, 50)

        /** What a widget shows before it has been set up: sensible content drawn from what the app is already showing. */
        fun default(type: WidgetType, from: String, to: String): WidgetConfig = when (type) {
            WidgetType.Single -> WidgetConfig(type, from, to)
            WidgetType.Watchlist -> WidgetConfig(type, from, to)
            WidgetType.Convert -> WidgetConfig(
                type, from, to, amount = "100",
                targets = (listOf(to) + listOf("USD", "EUR", "GBP", "JPY")).filter { it != from }.distinct().take(3),
            )
        }
    }

    /** The amount as a number: anything unreadable or non-positive falls back to 1 rather than breaking the widget. */
    val amountValue: Double
        get() = amount.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: 1.0

    val ownPairs: List<WatchPair>
        get() = pairs.mapNotNull {
            val p = it.split('/')
            if (p.size == 2 && p[0] in Currencies.codes && p[1] in Currencies.codes && p[0] != p[1]) WatchPair(p[0], p[1]) else null
        }

    val validTargets: List<String> get() = targets.filter { it in Currencies.codes && it != from }.distinct()

    fun flipped(): WidgetConfig = copy(from = to, to = from)

    fun withPairAdded(from: String, to: String): WidgetConfig =
        if (from == to || pairs.size >= MAX_PAIRS) this else copy(pairs = (pairs + "$from/$to").distinct())

    fun withTargetAdded(code: String): WidgetConfig =
        if (code == from || targets.size >= MAX_TARGETS) this else copy(targets = (targets + code).distinct())
}

object WidgetMath {
    /** Change over the period from a series of daily values, oldest first. Day compares the last two points. */
    fun change(series: List<Double>?, period: WidgetPeriod): Double? {
        if (series == null || series.size < 2) return null
        val base = if (period == WidgetPeriod.Day) series[series.size - 2] else series.first()
        return if (base == 0.0) null else series.last() / base - 1
    }
}
