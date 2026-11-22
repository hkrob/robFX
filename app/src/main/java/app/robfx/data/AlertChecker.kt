package app.robfx.data

import kotlin.math.abs

object AlertChecker {
    /** True when a new alert would fire on the very next check, i.e. the rate is already past the target. */
    fun alreadyMet(above: Boolean, target: Double, now: Double): Boolean = if (above) now >= target else now <= target

    /** The alert list after one check, and the alerts that fired (as they were before firing) with the rate that fired them. */
    data class Evaluation(val alerts: List<Alert>, val fired: List<Pair<Alert, Double>>)

    fun evaluate(alerts: List<Alert>, now: Long, rateFor: (String, String) -> Double?): Evaluation {
        val fired = mutableListOf<Pair<Alert, Double>>()
        val next = alerts.map { a ->
            if (!a.active) return@map a
            val r = rateFor(a.from, a.to) ?: return@map a
            if (a.isMove) {
                val base = a.base ?: return@map a.copy(base = r)
                val pct = a.movePct ?: return@map a
                if (base <= 0 || abs(r / base - 1) * 100 < pct) return@map a
                fired += a to r
                // A repeating move alert measures the next move from where this one ended.
                if (a.repeat) a.copy(base = r, triggeredAt = now, triggeredRate = r)
                else a.copy(active = false, triggeredAt = now, triggeredRate = r)
            } else {
                val met = alreadyMet(a.above, a.target, r)
                when {
                    // Fired before: re-arm only once the rate has come back, so it can't fire every half hour.
                    a.waiting -> if (met) a else a.copy(waiting = false)
                    !met -> a
                    else -> {
                        fired += a to r
                        if (a.repeat) a.copy(waiting = true, triggeredAt = now, triggeredRate = r)
                        else a.copy(active = false, triggeredAt = now, triggeredRate = r)
                    }
                }
            }
        }
        return Evaluation(next, fired)
    }

    fun triggered(alerts: List<Alert>, rateFor: (String, String) -> Double?): List<Pair<Alert, Double>> =
        evaluate(alerts, 0L, rateFor).fired
}
