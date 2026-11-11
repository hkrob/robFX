package app.robfx.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The keypad's tiny expression engine. Pure, so it is unit-tested without Android. */
object Calc {
    const val MAX_DIGITS = 12
    const val ADD = '+'
    const val SUB = '−'
    const val MUL = '×'
    const val DIV = '÷'
    private val ops = setOf(ADD, SUB, MUL, DIV)

    private fun isOp(c: Char) = c in ops
    private fun currentNumber(expr: String) = expr.takeLastWhile { !isOp(it) }

    fun key(expr: String, key: String): String = when (key) {
        "C" -> ""
        "<" -> expr.dropLast(1)
        "=" -> evaluate(expr)?.let(::plain) ?: expr
        "." -> {
            val n = currentNumber(expr)
            when {
                '.' in n -> expr
                n.isEmpty() -> expr + "0."
                else -> expr + "."
            }
        }
        "+", "-", "*", "/", "\u2212", "\u00D7", "\u00F7" -> operator(expr, normaliseOp(key))
        else -> digits(expr, key)
    }

    private fun normaliseOp(k: String) = when (k) {
        "-" -> SUB
        "*" -> MUL
        "/" -> DIV
        else -> k[0]
    }

    private fun operator(expr: String, op: Char): String = when {
        expr.isEmpty() -> if (op == SUB) "$SUB" else expr
        expr == "$SUB" -> expr
        isOp(expr.last()) -> {
            // A second operator replaces the first, except that "×" then "−" can't make a negative here: keep it simple.
            val head = expr.dropLast(1)
            if (head.isEmpty()) expr else head + op
        }
        else -> expr + op
    }

    private fun digits(expr: String, d: String): String {
        val n = currentNumber(expr)
        val significant = n.count { it.isDigit() }
        return when {
            significant >= MAX_DIGITS -> expr
            d == "00" && (n.isEmpty() || n == "0") -> if (n.isEmpty()) expr + "0" else expr
            n == "0" -> expr.dropLast(1) + d.trimStart('0').ifEmpty { "0" }
            else -> expr + d
        }
    }

    /** Trailing operators are ignored, so "45 ÷" is still 45 while you type the next operand. */
    fun evaluate(expr: String): BigDecimal? {
        if (expr.isEmpty()) return BigDecimal.ZERO
        var s = expr
        while (s.isNotEmpty() && isOp(s.last())) s = s.dropLast(1)
        if (s.isEmpty() || s == "$SUB") return BigDecimal.ZERO

        val nums = ArrayList<BigDecimal>()
        val opsList = ArrayList<Char>()
        var i = 0
        var negative = false
        if (s[0] == SUB) { negative = true; i = 1 }
        val buf = StringBuilder()
        fun flush() {
            val t = buf.toString().removeSuffix(".")
            var v = t.toBigDecimalOrNull() ?: BigDecimal.ZERO
            if (negative) { v = v.negate(); negative = false }
            nums += v
            buf.clear()
        }
        while (i < s.length) {
            val c = s[i]
            if (isOp(c)) { flush(); opsList += c } else buf.append(c)
            i++
        }
        flush()

        // × and ÷ first.
        val n2 = ArrayList<BigDecimal>().apply { add(nums[0]) }
        val o2 = ArrayList<Char>()
        for (k in opsList.indices) {
            val rhs = nums[k + 1]
            when (opsList[k]) {
                MUL -> n2[n2.lastIndex] = n2.last().multiply(rhs)
                DIV -> {
                    if (rhs.signum() == 0) return null
                    n2[n2.lastIndex] = n2.last().divide(rhs, MathContext.DECIMAL64)
                }
                else -> { o2 += opsList[k]; n2 += rhs }
            }
        }
        var acc = n2[0]
        for (k in o2.indices) acc = if (o2[k] == ADD) acc.add(n2[k + 1]) else acc.subtract(n2[k + 1])
        return acc
    }

    /** Plain, ungrouped, no float noise: what "=" writes back into the entry. */
    fun plain(v: BigDecimal): String {
        val r = v.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        return (if (r.signum() == 0) BigDecimal.ZERO else r).toPlainString()
    }
}

object Format {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private fun grouped() = DecimalFormat("#,##0.##########", symbols)

    /** Amounts: up to 4 decimals above 1, 5 significant digits below it, zeros trimmed, thousands grouped. */
    fun amount(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        if (v == 0.0) return "0"
        val bd = BigDecimal(v)
        val rounded = if (bd.abs() >= BigDecimal.ONE) bd.setScale(4, RoundingMode.HALF_UP) else bd.round(MathContext(5))
        return grouped().format(rounded)
    }

    fun amount(v: BigDecimal): String = grouped().format(
        if (v.abs() >= BigDecimal.ONE) v.setScale(4, RoundingMode.HALF_UP) else v.round(MathContext(5)),
    )

    /** The shorter form used when [amount] won't fit: two decimals above 1, three significant digits below. */
    fun amountCompact(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        if (v == 0.0) return "0"
        val bd = BigDecimal(v)
        return grouped().format(if (bd.abs() >= BigDecimal.ONE) bd.setScale(2, RoundingMode.HALF_UP) else bd.round(MathContext(3)))
    }

    /** Rates keep six significant digits so 0.0067 JPY and 7.8449 HKD are both honest. */
    fun rate(v: Double, significant: Int = 6): String {
        if (v.isNaN() || v.isInfinite() || v == 0.0) return "—"
        return grouped().format(BigDecimal(v).round(MathContext(significant)))
    }

    fun percent(frac: Double): String {
        val p = frac * 100
        val s = String.format(Locale.US, "%.2f%%", kotlin.math.abs(p))
        return (if (p > 0.005) "+" else if (p < -0.005) "−" else "") + s
    }

    /** Groups every operand of the entry for display and spaces out the operators: "45÷12" -> "45 ÷ 12". */
    fun pretty(expr: String): String {
        if (expr.isEmpty()) return "0"
        val sb = StringBuilder()
        val num = StringBuilder()
        fun flush() {
            if (num.isEmpty()) return
            val t = num.toString()
            val dot = t.indexOf('.')
            val int = if (dot >= 0) t.substring(0, dot) else t
            val frac = if (dot >= 0) t.substring(dot) else ""
            sb.append(int.toBigDecimalOrNull()?.let { DecimalFormat("#,##0", symbols).format(it) } ?: int).append(frac)
            num.clear()
        }
        for ((i, c) in expr.withIndex()) {
            if (c in "+−×÷") {
                flush()
                if (i == 0) sb.append(c) else sb.append(' ').append(c).append(' ')
            } else num.append(c)
        }
        flush()
        return sb.toString()
    }
}
