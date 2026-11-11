package app.robfx

/** How the Markets list is ordered. [Custom] is the order pairs were added in. */
enum class WatchSort(val label: String, val defaultDescending: Boolean) {
    Custom("Added", false),
    Pair("A–Z", false),
    Change("Change", true),
    Rate("Rate", true),
}

object WatchSorting {
    /** Pairs without a value yet (still loading, or no history) always sink to the bottom, whichever way you sort. */
    fun sort(rows: List<WatchRow>, sort: WatchSort, descending: Boolean): List<WatchRow> {
        fun <T : Comparable<T>> byValue(selector: (WatchRow) -> T?): List<WatchRow> {
            val (known, unknown) = rows.partition { selector(it) != null }
            val ordered = if (descending) known.sortedByDescending(selector) else known.sortedBy(selector)
            return ordered + unknown
        }
        return when (sort) {
            WatchSort.Custom -> rows
            WatchSort.Pair -> if (descending) rows.sortedByDescending { it.pair.key } else rows.sortedBy { it.pair.key }
            WatchSort.Change -> byValue { it.change }
            WatchSort.Rate -> byValue { it.rate?.rate }
        }
    }

    /** Tapping the active chip flips its direction; tapping another starts at that sort's natural direction. */
    fun tap(current: WatchSort, descending: Boolean, tapped: WatchSort): Pair<WatchSort, Boolean> = when {
        tapped == WatchSort.Custom -> WatchSort.Custom to false
        tapped == current -> tapped to !descending
        else -> tapped to tapped.defaultDescending
    }
}
