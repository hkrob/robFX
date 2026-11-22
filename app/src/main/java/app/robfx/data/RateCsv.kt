package app.robfx.data

import java.time.LocalDate
import java.time.Month

/** A date span offered for export, labelled the way an accountant would ask for it. */
data class ExportPeriod(val label: String, val start: LocalDate, val end: LocalDate) {
    companion object {
        /**
         * Rolling spans, then calendar and financial years: Australia's runs 1 Jul to 30 Jun, Hong Kong's 1 Apr to 31 Mar.
         * The year in progress ends today.
         */
        fun all(today: LocalDate): List<ExportPeriod> = buildList {
            add(ExportPeriod("Past month", today.minusMonths(1), today))
            add(ExportPeriod("Past 3 months", today.minusMonths(3), today))
            add(ExportPeriod("Past year", today.minusYears(1), today))
            val y = today.year
            add(ExportPeriod("$y so far", LocalDate.of(y, 1, 1), today))
            add(ExportPeriod("${y - 1}", LocalDate.of(y - 1, 1, 1), LocalDate.of(y - 1, 12, 31)))
            add(fy("Australian FY", today, Month.JULY, current = true))
            add(fy("Australian FY", today, Month.JULY, current = false))
            add(fy("Hong Kong FY", today, Month.APRIL, current = true))
            add(fy("Hong Kong FY", today, Month.APRIL, current = false))
        }

        private fun fy(name: String, today: LocalDate, startMonth: Month, current: Boolean): ExportPeriod {
            var start = LocalDate.of(today.year, startMonth, 1)
            if (start > today) start = start.minusYears(1)
            if (!current) start = start.minusYears(1)
            val end = start.plusYears(1).minusDays(1)
            val label = "$name ${start.year}–${(start.year + 1) % 100}".let { if (current) "$it so far" else it }
            return ExportPeriod(label, start, if (current) minOf(end, today) else end)
        }
    }
}

object RateCsv {
    fun fileName(from: String, to: String, p: ExportPeriod) = "robFX_${from}-${to}_${p.start}_${p.end}.csv"

    /**
     * One row per calendar day. Weekends and holidays have no published rate, so they carry the last one forward and say
     * which day it came from: that is the rate a tax office expects for a transaction on such a day.
     */
    fun build(from: String, to: String, p: ExportPeriod, points: List<HistoryPoint>): String {
        val sorted = points.sortedBy { it.date }
        val sb = StringBuilder("date,1 $from in $to,rate published\n")
        var i = 0
        var last: HistoryPoint? = null
        var day = p.start
        while (day <= p.end) {
            while (i < sorted.size && sorted[i].date <= day) last = sorted[i++]
            last?.let { sb.append(day).append(',').append(it.value.toBigDecimal().stripTrailingZeros().toPlainString()).append(',').append(it.date).append('\n') }
            day = day.plusDays(1)
        }
        return sb.toString()
    }
}
