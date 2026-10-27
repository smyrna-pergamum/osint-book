package io.github.smyrna_pergamum.osintbook.net

import io.github.smyrna_pergamum.osintbook.BuildConfig
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thrown by providers and [Http]; [status] is what the run records (FR-04). */
class ProviderException(
    val status: RunStatus,
    val code: String,
    /** NET-04: epoch millis before which the provider must not be asked again. */
    val blockedUntil: Long = 0,
) : Exception(code)

class HttpResponse(
    val url: String,
    val code: Int,
    val body: String,
    val headers: Map<String, String>,
    val mediaType: String,
)

/** Counts requests (NET-03) and receives every response's headers for quota display (NET-05). */
interface QuotaListener {
    fun onRequest() {}
    fun onHeaders(headers: Map<String, String>) {}
}

/**
 * The only way the app talks to the network. Enforces the spec's limits:
 * NET-02 timeouts and 5 MiB cap, NET-04 retry rules, SEC-06/07 HTTPS only and
 * no cross-host redirects for requests that carry a key.
 */
class Http(
    base: OkHttpClient = defaultClient(),
    private val maxBytes: Long = MAX_BYTES,
    private val retryDelaysMs: List<Long> = listOf(1_000, 3_000),
    private val now: () -> Long = System::currentTimeMillis,
    /** Tests point this at a local mock server; release code never relaxes it. */
    private val requireHttps: Boolean = true,
) {
    private val open = base
    // Keys go in headers; a redirect could carry them to another host, so keyed calls never follow one.
    private val keyed = base.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), carriesSecret: Boolean = false, quota: QuotaListener? = null): HttpResponse =
        execute(Request.Builder().url(url).get(), headers, carriesSecret, quota)

    /** Binary download (map tiles), with the same limits as every other request. */
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val b = Request.Builder().url(url).get().header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> b.header(k, v) }
        val request = b.build()
        if (requireHttps && !request.url.isHttps) throw ProviderException(RunStatus.NETWORK, "cleartext_blocked")
        open.newCall(request).await().use { resp ->
            if (!resp.isSuccessful) throw ProviderException(RunStatus.SERVER, "http_${resp.code}")
            val body = resp.body ?: throw ProviderException(RunStatus.PARSE, "no_body")
            val source = body.source()
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw ProviderException(RunStatus.TOO_LARGE, "too_large")
            return source.buffer.readByteArray()
        }
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap(), carriesSecret: Boolean = false, quota: QuotaListener? = null): HttpResponse =
        execute(Request.Builder().url(url).post(json.toRequestBody(JSON)), headers, carriesSecret, quota)

    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap(), carriesSecret: Boolean = false, quota: QuotaListener? = null): HttpResponse {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(form), headers, carriesSecret, quota)
    }

    private suspend fun execute(builder: Request.Builder, headers: Map<String, String>, carriesSecret: Boolean, quota: QuotaListener?): HttpResponse {
        builder.header("User-Agent", USER_AGENT).header("Accept", "application/json")
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.build()
        if (requireHttps && !request.url.isHttps) throw ProviderException(RunStatus.NETWORK, "cleartext_blocked")
        val client = if (carriesSecret) keyed else open

        var attempt = 0
        while (true) {
            quota?.onRequest()
            val result = try {
                client.newCall(request).await().use { read(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException) {
                throw e
            } catch (e: IOException) {
                val timeout = e is SocketTimeoutException || e is InterruptedIOException
                if (attempt < retryDelaysMs.size) { delay(retryDelaysMs[attempt++]); continue }
                throw ProviderException(if (timeout) RunStatus.TIMEOUT else RunStatus.NETWORK, e.javaClass.simpleName)
            }
            quota?.onHeaders(result.headers)
            when (result.code) {
                in 200..299 -> return result
                404 -> throw ProviderException(RunStatus.NO_RESULT, "http_404")
                // NET-04: no blind retry on auth failures.
                401, 403 -> throw ProviderException(RunStatus.UNAUTHORIZED, "http_${result.code}")
                429 -> throw ProviderException(RunStatus.RATE_LIMITED, "http_429", retryAfter(result.headers))
                500, 502, 503, 504 -> {
                    if (attempt < retryDelaysMs.size) { delay(retryDelaysMs[attempt++]); continue }
                    throw ProviderException(RunStatus.SERVER, "http_${result.code}")
                }
                in 300..399 -> throw ProviderException(RunStatus.SERVER, "redirect_blocked")
                else -> throw ProviderException(RunStatus.SERVER, "http_${result.code}")
            }
        }
    }

    private fun read(resp: Response): HttpResponse {
        val body = resp.body ?: throw ProviderException(RunStatus.PARSE, "no_body")
        val declared = body.contentLength()
        if (declared > maxBytes) throw ProviderException(RunStatus.TOO_LARGE, "too_large")
        val source = body.source()
        // Read at most maxBytes + 1 so an undeclared huge body is caught without loading it all.
        source.request(maxBytes + 1)
        if (source.buffer.size > maxBytes) throw ProviderException(RunStatus.TOO_LARGE, "too_large")
        val text = source.buffer.readUtf8()
        val headers = resp.headers.associate { (k, v) -> k.lowercase() to v }
        return HttpResponse(resp.request.url.toString(), resp.code, text, headers, body.contentType()?.toString() ?: "application/octet-stream")
    }

    /** Retry-After as seconds or HTTP date; falls back to one minute when absent. */
    fun retryAfter(headers: Map<String, String>): Long {
        val v = headers["retry-after"]?.trim()
        v?.toLongOrNull()?.let { return now() + it * 1000 }
        if (v != null) runCatching {
            return ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }
        headers["x-ratelimit-reset"]?.toLongOrNull()?.let { return it * 1000 }
        return now() + 60_000
    }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
        val USER_AGENT = "OSINT-Book/${BuildConfig.VERSION_NAME} (Android; open-source OSINT app)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            // A redirect must never downgrade to http.
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
