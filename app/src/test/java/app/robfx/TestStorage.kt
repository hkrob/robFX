package app.robfx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.robfx.unitprice.Comparison
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * DataStore is one instance per process, so without this a test inherits whatever the previous one saved.
 * Writes run on their own thread with a deadline, while this (main) thread keeps its looper turning.
 */
object TestStorage {
    private fun onWorker(what: String, block: suspend (AppContainer) -> Unit) {
        val container = ApplicationProvider.getApplicationContext<Context>().container
        var failure: Throwable? = null
        val t = Thread { try { runBlocking { block(container) } } catch (e: Throwable) { failure = e } }
        t.isDaemon = true
        t.start()
        // DataStore finishes each write in the caller's context. Screens write from the main thread, so a write still
        // pending from an earlier test needs the main looper to run; blocking it here would deadlock both. Keep it turning.
        val deadline = System.currentTimeMillis() + 20_000
        while (t.isAlive && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            t.join(25)
        }
        if (t.isAlive) {
            // Say what everything was doing, so a stall can be diagnosed instead of guessed at.
            val stacks = Thread.getAllStackTraces().entries
                .filter { (th, st) -> st.any { f -> f.className.startsWith("androidx.datastore") || f.className.startsWith("app.robfx") || f.className.startsWith("okio") } || th === t }
                .joinToString("\n") { (th, st) -> "## ${th.name} [${th.state}]\n" + st.take(18).joinToString("\n") { "   at $it" } }
            error("storage did not answer while trying to $what\n$stacks")
        }
        failure?.let { throw it }
    }

    fun recentLabels(): List<String> {
        var out = emptyList<String>()
        onWorker("read recent labels") { out = it.settings.recentLabels.first() }
        return out
    }

    /**
     * Call at the end of a test, while its main looper still exists. Robolectric throws away the main thread's message
     * queue when a test ends; a settings write caught mid-flight would lose its "finish on the main thread" message and
     * the storage queue would wait on it forever, stalling every later test. A no-op write queues behind anything
     * pending, and waiting for it (with the looper turning) proves the queue is empty.
     */
    fun drain() = onWorker("drain pending writes") { it.settings.setLastUpdateCheck(it.settings.lastUpdateCheck()) }

    fun reset() = onWorker("reset storage") { it.settings.clearAll() }

    fun seed(c: Comparison) = onWorker("seed a comparison") { it.settings.setUnitComparison(c) }
}
