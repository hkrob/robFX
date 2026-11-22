package app.robfx.data

/** Pure rule: which active alerts does the current set of rates trip? */
object AlertChecker {
    /** True when a new alert would fire on the very next check, i.e. the rate is already past the target. */
    fun alreadyMet(above: Boolean, target: Double, now: Double): Boolean = if (above) now >= target else now <= target

    fun triggered(alerts: List<Alert>, rateFor: (String, String) -> Double?): List<Pair<Alert, Double>> =
        alerts.filter { it.active }.mapNotNull { a ->
            val r = rateFor(a.from, a.to) ?: return@mapNotNull null
            if ((a.above && r >= a.target) || (!a.above && r <= a.target)) a to r else null
        }
}
