package app.robfx.unitprice

import app.robfx.calc.Calc
import app.robfx.data.PairRate
import app.robfx.data.Quality
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class UnitGroup(val label: String) { Weight("Weight"), Volume("Volume"), Length("Length"), Count("Count") }

/** [factor] is how many of the group's base unit (g, ml, mm, each) make one of this unit. */
data class MeasureUnit(val id: String, val short: String, val name: String, val group: UnitGroup, val factor: Double)

/** A "per ..." yardstick the unit prices are shown against, e.g. per 100 g or per lb. */
data class Basis(val id: String, val label: String, val unitId: String, val amount: Double) {
    val baseAmount: Double get() = amount * Units.byId.getValue(unitId).factor
}

object Units {
    // Exact definitions: the avoirdupois pound and the US and imperial gallons; everything else follows from them.
    private const val LB = 453.59237
    private const val US_GAL = 3785.411784
    private const val UK_GAL = 4546.09

    val all: List<MeasureUnit> = listOf(
        MeasureUnit("mg", "mg", "Milligram", UnitGroup.Weight, 0.001),
        MeasureUnit("g", "g", "Gram", UnitGroup.Weight, 1.0),
        MeasureUnit("kg", "kg", "Kilogram", UnitGroup.Weight, 1000.0),
        MeasureUnit("oz", "oz", "Ounce", UnitGroup.Weight, LB / 16),
        MeasureUnit("lb", "lb", "Pound", UnitGroup.Weight, LB),

        MeasureUnit("ml", "ml", "Millilitre", UnitGroup.Volume, 1.0),
        MeasureUnit("l", "L", "Litre", UnitGroup.Volume, 1000.0),
        MeasureUnit("floz_us", "fl oz (US)", "US fluid ounce", UnitGroup.Volume, US_GAL / 128),
        MeasureUnit("floz_uk", "fl oz (UK)", "UK fluid ounce", UnitGroup.Volume, UK_GAL / 160),
        MeasureUnit("cup_us", "cup (US)", "US cup", UnitGroup.Volume, US_GAL / 16),
        MeasureUnit("pint_us", "pint (US)", "US pint", UnitGroup.Volume, US_GAL / 8),
        MeasureUnit("pint_uk", "pint (UK)", "UK pint", UnitGroup.Volume, UK_GAL / 8),
        MeasureUnit("qt_us", "quart (US)", "US quart", UnitGroup.Volume, US_GAL / 4),
        MeasureUnit("gal_us", "gal (US)", "US gallon", UnitGroup.Volume, US_GAL),
        MeasureUnit("gal_uk", "gal (UK)", "UK gallon", UnitGroup.Volume, UK_GAL),

        MeasureUnit("mm", "mm", "Millimetre", UnitGroup.Length, 1.0),
        MeasureUnit("cm", "cm", "Centimetre", UnitGroup.Length, 10.0),
        MeasureUnit("m", "m", "Metre", UnitGroup.Length, 1000.0),
        MeasureUnit("in", "in", "Inch", UnitGroup.Length, 25.4),
        MeasureUnit("ft", "ft", "Foot", UnitGroup.Length, 304.8),
        MeasureUnit("yd", "yd", "Yard", UnitGroup.Length, 914.4),

        // No "pack": a pack of unknown size can't be compared. A 10-pack is quantity 10 of "each".
        MeasureUnit("each", "each", "Each", UnitGroup.Count, 1.0),
        MeasureUnit("pair", "pair", "Pair", UnitGroup.Count, 2.0),
        MeasureUnit("dozen", "dozen", "Dozen", UnitGroup.Count, 12.0),
    )

    val byId: Map<String, MeasureUnit> = all.associateBy { it.id }
    fun ofGroup(g: UnitGroup): List<MeasureUnit> = all.filter { it.group == g }

    val bases: Map<UnitGroup, List<Basis>> = mapOf(
        UnitGroup.Weight to listOf(Basis("100g", "100 g", "g", 100.0), Basis("kg", "kg", "kg", 1.0), Basis("oz", "oz", "oz", 1.0), Basis("lb", "lb", "lb", 1.0)),
        UnitGroup.Volume to listOf(Basis("100ml", "100 ml", "ml", 100.0), Basis("l", "L", "l", 1.0), Basis("floz_us", "fl oz", "floz_us", 1.0), Basis("gal_us", "gal", "gal_us", 1.0)),
        UnitGroup.Length to listOf(Basis("m", "m", "m", 1.0), Basis("10m", "10 m", "m", 10.0), Basis("ft", "ft", "ft", 1.0), Basis("yd", "yd", "yd", 1.0)),
        UnitGroup.Count to listOf(Basis("each", "each", "each", 1.0), Basis("dozen", "dozen", "dozen", 1.0)),
    )

    fun basis(group: UnitGroup, id: String): Basis = bases.getValue(group).firstOrNull { it.id == id } ?: bases.getValue(group).first()

    /** Metric unless the locale shelves its labels in ounces and pounds. */
    fun defaultUnit(group: UnitGroup, us: Boolean): String = when (group) {
        UnitGroup.Weight -> if (us) "oz" else "g"
        UnitGroup.Volume -> if (us) "floz_us" else "ml"
        UnitGroup.Length -> if (us) "ft" else "cm"
        UnitGroup.Count -> "each"
    }

    fun defaultBasis(group: UnitGroup, us: Boolean): String = when (group) {
        UnitGroup.Weight -> if (us) "oz" else "100g"
        UnitGroup.Volume -> if (us) "floz_us" else "100ml"
        UnitGroup.Length -> if (us) "ft" else "m"
        UnitGroup.Count -> "each"
    }

    fun isUs(locale: Locale = Locale.getDefault()) = locale.country == "US"
}

/** Fields hold what was typed on the keypad (an expression such as "6+6"), so "12 x 330" style maths works anywhere. */
@Serializable
data class UnitItem(
    val price: String = "",
    val size: String = "",
    val qty: String = "",
    val unit: String,
    val currency: String,
)

@Serializable
data class Comparison(
    val version: Int = 1,
    val group: UnitGroup,
    val basis: String,
    /** The currency every result is shown in. */
    val currency: String,
    val items: List<UnitItem>,
) {
    companion object {
        const val MAX_ITEMS = 6

        fun fresh(group: UnitGroup, home: String, us: Boolean, basis: String? = null): Comparison {
            val item = UnitItem(unit = Units.defaultUnit(group, us), currency = home)
            return Comparison(group = group, basis = basis ?: Units.defaultBasis(group, us), currency = home, items = listOf(item, item))
        }
    }

    fun withGroup(g: UnitGroup, us: Boolean): Comparison = copy(
        group = g,
        basis = Units.defaultBasis(g, us),
        items = items.map { it.copy(unit = Units.defaultUnit(g, us)) },
    )

    /** New items start like the first one, which is what you're comparing everything against. */
    fun withItemAdded(): Comparison {
        if (items.size >= MAX_ITEMS) return this
        val first = items.first()
        return copy(items = items + UnitItem(unit = first.unit, currency = first.currency))
    }

    fun withItemRemoved(index: Int): Comparison =
        if (items.size <= 2 || index !in items.indices) this else copy(items = items.filterIndexed { i, _ -> i != index })

    /** Home currency changes carry along every item that was priced in the old one. */
    fun withHomeCurrency(code: String): Comparison =
        copy(currency = code, items = items.map { if (it.currency == currency) it.copy(currency = code) else it })
}

sealed interface ItemResult {
    /** Price or size missing, zero or unreadable. Never treated as 0. */
    data object Incomplete : ItemResult

    /** The item's unit isn't in this comparison's group, e.g. grams against millilitres. */
    data object Mismatch : ItemResult

    /** Priced in a currency we have no rate for. Never falls back to 1:1. */
    data class NoRate(val currency: String) : ItemResult

    data class Ready(
        val unitPrice: Double,
        val totalBase: Double,
        val priceHome: Double,
        val rate: Double,
        val converted: Boolean,
        val stale: Boolean,
        val rank: Int,
        val percentAboveBest: Double,
    ) : ItemResult
}

sealed interface Verdict {
    data object None : Verdict

    /** [savings] is how much cheaper the winner is than the runner-up, as a fraction of the runner-up's price. */
    data class Winner(val index: Int, val savings: Double) : Verdict

    data class Tie(val indices: List<Int>) : Verdict
}

data class Evaluation(val items: List<ItemResult>, val verdict: Verdict) {
    val readyCount: Int get() = items.count { it is ItemResult.Ready }
}

object UnitPrice {
    /** Anything closer than this is "the same value": shelf rounding makes a smaller gap meaningless. */
    const val TIE = 0.005

    private fun number(expr: String): Double? {
        val v = Calc.evaluate(expr)?.toDouble() ?: return null
        return v.takeIf { it > 0 && it.isFinite() }
    }

    fun evaluate(c: Comparison, rate: (from: String, to: String) -> PairRate?): Evaluation {
        val basis = Units.basis(c.group, c.basis)
        val raw = c.items.map { item ->
            val unit = Units.byId[item.unit]
            val price = number(item.price)
            val size = number(item.size)
            val qty = if (item.qty.isBlank()) 1.0 else number(item.qty)
            when {
                unit == null || unit.group != c.group -> ItemResult.Mismatch
                price == null || size == null || qty == null -> ItemResult.Incomplete
                else -> {
                    val converted = item.currency != c.currency
                    val r = if (converted) rate(item.currency, c.currency) else null
                    if (converted && r == null) {
                        ItemResult.NoRate(item.currency)
                    } else {
                        val fx = r?.rate ?: 1.0
                        val total = qty * size * unit.factor
                        val home = price * fx
                        ItemResult.Ready(
                            unitPrice = home / total * basis.baseAmount,
                            totalBase = total,
                            priceHome = home,
                            rate = fx,
                            converted = converted,
                            stale = r?.quality == Quality.Stale,
                            rank = 0,
                            percentAboveBest = 0.0,
                        )
                    }
                }
            }
        }

        val ready = raw.withIndex().filter { it.value is ItemResult.Ready }.sortedBy { (it.value as ItemResult.Ready).unitPrice }
        if (ready.isEmpty()) return Evaluation(raw, Verdict.None)

        val best = (ready.first().value as ItemResult.Ready).unitPrice
        val ranked = raw.toMutableList()
        ready.forEachIndexed { rank, (i, v) ->
            val r = v as ItemResult.Ready
            ranked[i] = r.copy(rank = rank, percentAboveBest = r.unitPrice / best - 1)
        }
        val verdict = when {
            ready.size < 2 -> Verdict.None
            else -> {
                val second = (ready[1].value as ItemResult.Ready).unitPrice
                if (second / best - 1 < TIE) {
                    Verdict.Tie(ready.filter { (it.value as ItemResult.Ready).unitPrice / best - 1 < TIE }.map { it.index })
                } else {
                    Verdict.Winner(ready.first().index, 1 - best / second)
                }
            }
        }
        return Evaluation(ranked, verdict)
    }

    fun itemLabel(index: Int) = "Item ${('A' + index)}"
}

object UnitFormat {
    private val symbols = DecimalFormatSymbols(Locale.US)

    /** Two decimals once it's a normal price; three significant digits below 1, so 0.0523 per gram isn't "0.05". */
    fun number(v: Double): String {
        if (!v.isFinite()) return "—"
        if (v == 0.0) return "0"
        return if (v >= 1) DecimalFormat("#,##0.00", symbols).format(v) else BigDecimal(v).round(MathContext(3)).toPlainString()
    }

    fun money(code: String, v: Double): String {
        val sym = runCatching { java.util.Currency.getInstance(code).getSymbol(Locale.getDefault()) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
        return sym + number(v)
    }

    fun plainPercent(frac: Double): String {
        val p = frac * 100
        return if (p < 10) String.format(Locale.US, "%.1f%%", p) else String.format(Locale.US, "%.0f%%", p)
    }

    /** "6 × 330 ml = 1,980 ml" for multipacks, nothing for a single item. */
    fun total(qtyExpr: String, sizeExpr: String, unit: MeasureUnit): String? {
        val q = Calc.evaluate(qtyExpr)?.toDouble() ?: return null
        val s = Calc.evaluate(sizeExpr)?.toDouble() ?: return null
        if (qtyExpr.isBlank() || q <= 0 || s <= 0 || q == 1.0) return null
        return "${Calc.plain(BigDecimal(q))} × ${Calc.plain(BigDecimal(s))} ${unit.short} = ${number2(q * s)} ${unit.short}"
    }

    private fun number2(v: Double) = DecimalFormat("#,##0.##", symbols).format(v)
}

@Serializable
data class SavedComparison(val savedAt: Long, val summary: String, val comparison: Comparison)

@Serializable
data class SavedFile(val version: Int = 1, val items: List<SavedComparison> = emptyList())

object Recents {
    const val CAP = 10

    /** Newest first, identical comparisons collapse into one, and the list never grows past [CAP]. */
    fun add(list: List<SavedComparison>, saved: SavedComparison): List<SavedComparison> =
        (listOf(saved) + list.filterNot { it.comparison == saved.comparison }).take(CAP)

    fun summary(c: Comparison, e: Evaluation): String {
        val best = e.items.withIndex().filter { it.value is ItemResult.Ready }.minByOrNull { (it.value as ItemResult.Ready).rank }
        val basis = Units.basis(c.group, c.basis)
        val head = "${c.group.label} · ${c.items.size} items"
        return if (best == null) head else "$head · best ${UnitPrice.itemLabel(best.index)} ${UnitFormat.money(c.currency, (best.value as ItemResult.Ready).unitPrice)} / ${basis.label}"
    }
}
