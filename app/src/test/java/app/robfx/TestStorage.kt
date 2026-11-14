package app.robfx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.robfx.unitprice.Comparison
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * DataStore is one instance per process, so without this a test inherits whatever the previous one saved.
 * Writes run on their own thread with a deadline: blocking the test's main thread on storage once stalled a whole run.
 */
object TestStorage {
    private fun onWorker(what: String, block: suspend (AppContainer) -> Unit) {
        val container = ApplicationProvider.getApplicationContext<Context>().container
        var failure: Throwable? = null
        val t = Thread { try { runBlocking { block(container) } } catch (e: Throwable) { failure = e } }
        t.isDaemon = true
        t.start()
        t.join(20_000)
        check(!t.isAlive) { "storage did not answer while trying to $what" }
        failure?.let { throw it }
    }

    fun recentLabels(): List<String> {
        var out = emptyList<String>()
        onWorker("read recent labels") { out = it.settings.recentLabels.first() }
        return out
    }

    fun reset() = onWorker("reset storage") { it.settings.clearAll() }

    fun seed(c: Comparison) = onWorker("seed a comparison") { it.settings.setUnitComparison(c) }
}
