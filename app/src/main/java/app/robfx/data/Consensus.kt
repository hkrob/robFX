package app.robfx.data

/**
 * Combines several sources into one rate. Each source is crossed independently
 * (to/from through its own USD base) and the median of those crosses wins, so a
 * single bad table can't move the answer once there are three of them.
 */
object Consensus {
    /** ECB reference rates and mid-market feeds routinely differ by a couple of tenths of a percent; beyond this, say so. */
    const val DIVERGENCE = 0.005
    const val FRESH_MS = 12L * 60 * 60 * 1000

    /**
     * A source whose data is this much older than the freshest source's is left out. Daily feeds are all "fresh" when
     * fetched, but on 25 Nov 2026 two of three were still on the previous day while AUD/HKD had moved 1%: their median
     * outvoted the one current source and the pill still said the sources agreed.
     */
    const val LAG_MS = 18L * 60 * 60 * 1000

    fun pair(tables: Collection<RateTable>, from: String, to: String, now: Long): PairRate? {
        if (tables.isEmpty()) return null
        val fresh = tables.filter { now - it.fetchedAt <= FRESH_MS }
        val pool = fresh.ifEmpty { tables }.filter { it.rates[from] != null && it.rates[to] != null }
        val newest = pool.maxOfOrNull { it.dataTime } ?: return null
        val use = pool.filter { newest - it.dataTime <= LAG_MS }

        val per = LinkedHashMap<String, Double>()
        for (t in use) {
            val f = t.rates[from] ?: continue
            val q = t.rates[to] ?: continue
            if (f > 0 && q > 0) per[t.source] = q / f
        }
        if (per.isEmpty()) return null

        val v = per.values.sorted()
        val median = if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
        val spread = if (v.size > 1) (v.last() - v.first()) / median else 0.0
        val quality = when {
            fresh.isEmpty() -> Quality.Stale
            v.size == 1 -> Quality.Single
            spread > DIVERGENCE -> Quality.Divergent
            else -> Quality.Consensus
        }
        // The time shown is when the newest numbers were set, not merely when they were downloaded.
        return PairRate(median, per, spread, quality, minOf(newest, use.maxOf { it.fetchedAt }))
    }
}
