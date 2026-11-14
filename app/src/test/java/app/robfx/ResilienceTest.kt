package app.robfx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.robfx.data.source.Http
import app.robfx.diag.Diagnostics
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class NetworkBoundTest {
    /** A burst of slow requests must never hold more than a small slice of the shared IO pool. */
    @Test fun noMoreThanEightRequestsRunAtOnce() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newFixedThreadPool(40)
            createContext("/") { ex ->
                peak.accumulateAndGet(active.incrementAndGet()) { a, b -> maxOf(a, b) }
                Thread.sleep(150)
                active.decrementAndGet()
                val body = "{}".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val http = Http()
            (1..30).map { async(Dispatchers.Default) { http.get("http://127.0.0.1:${server.address.port}/x") } }.awaitAll()
        } finally {
            server.stop(0)
        }
        assertTrue("expected real concurrency, saw ${peak.get()}", peak.get() >= 2)
        assertTrue("saw ${peak.get()} concurrent requests", peak.get() <= 8)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiagnosticsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun exitReasonsAreNamed() {
        assertEquals("ANR (app not responding)", Diagnostics.reasonName(6))
        assertEquals("CRASH", Diagnostics.reasonName(4))
        assertEquals("USER_REQUESTED", Diagnostics.reasonName(10))
        assertEquals("UNKNOWN(99)", Diagnostics.reasonName(99))
    }

    @Test fun aRecordedCrashComesBackInTheReport() {
        assertEquals(null, Diagnostics.lastCrash(ctx))
        Diagnostics.record(ctx, Thread.currentThread(), IllegalStateException("boom from a test"))
        assertTrue(Diagnostics.lastCrash(ctx)!!.contains("boom from a test"))
        val report = Diagnostics.report(ctx)
        assertTrue(report.contains("robFX diagnostics"))
        assertTrue(report.contains("boom from a test"))
        assertTrue(report.contains("Recent process exits"))
        assertTrue(report.contains("Android"))
    }

    @Test fun crashFileIsBounded() {
        Diagnostics.record(ctx, Thread.currentThread(), RuntimeException("x".repeat(50_000)))
        assertTrue(Diagnostics.lastCrash(ctx)!!.length <= 8_000)
    }
}
