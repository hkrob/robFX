package app.parity.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

class Http(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    // Blocking call on IO; callTimeout bounds it, and coroutine cancellation is honoured between requests.
    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Parity/1.0").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, url)
            r.body?.string() ?: throw IOException("Empty body from $url")
        }
    }
}
