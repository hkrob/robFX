package app.parity.data

/** Pure rule: which active alerts does the current set of rates trip? */
object AlertChecker {
    fun triggered(alerts: List<Alert>, rateFor: (String, String) -> Double?): List<Pair<Alert, Double>> =
        alerts.filter { it.active }.mapNotNull { a ->
            val r = rateFor(a.from, a.to) ?: return@mapNotNull null
            if ((a.above && r >= a.target) || (!a.above && r <= a.target)) a to r else null
        }
}
