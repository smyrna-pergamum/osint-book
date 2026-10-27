package io.github.smyrna_pergamum.osintbook

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.net.Http
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import io.github.smyrna_pergamum.osintbook.net.QuotaListener
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** AT-04, AT-05, AT-09 against a fake server. */
class HttpTest {
    private lateinit var server: MockWebServer
    private var now = 1_000_000L
    private val client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).readTimeout(1, TimeUnit.SECONDS).build()
    private fun http(max: Long = Http.MAX_BYTES) = Http(client, maxBytes = max, retryDelaysMs = listOf(1, 1), now = { now }, requireHttps = false)

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun expect(status: RunStatus, block: suspend () -> Unit): ProviderException {
        try { runBlocking { block() }; fail("expected $status") } catch (e: ProviderException) { assertEquals(status, e.status); return e }
        throw AssertionError()
    }

    @Test fun okBody() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"a\":1}"))
        assertEquals("{\"a\":1}", http().get(server.url("/").toString()).body)
    }

    @Test fun authErrorsAreNotRetried() {
        server.enqueue(MockResponse().setResponseCode(401))
        expect(RunStatus.UNAUTHORIZED) { http().get(server.url("/").toString()) }
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(403))
        expect(RunStatus.UNAUTHORIZED) { http().get(server.url("/").toString()) }
        assertEquals(2, server.requestCount)
    }

    @Test fun rateLimitCarriesRetryAfter() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        val e = expect(RunStatus.RATE_LIMITED) { http().get(server.url("/").toString()) }
        assertEquals(now + 120_000, e.blockedUntil)
        assertEquals(1, server.requestCount)
    }

    @Test fun serverErrorsRetriedTwiceThenReported() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        var counted = 0
        val q = object : QuotaListener { override fun onRequest() { counted++ } }
        expect(RunStatus.SERVER) { http().get(server.url("/").toString(), quota = q) }
        assertEquals(3, server.requestCount)
        assertEquals(3, counted)
    }

    @Test fun recoversAfterOneServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", http().get(server.url("/").toString()).body)
    }

    @Test fun notFoundIsNoResult() {
        server.enqueue(MockResponse().setResponseCode(404))
        expect(RunStatus.NO_RESULT) { http().get(server.url("/").toString()) }
    }

    @Test fun oversizedBodyRejected() {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2048))))
        expect(RunStatus.TOO_LARGE) { http(max = 1024).get(server.url("/").toString()) }
    }

    @Test fun timeoutReported() {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
        expect(RunStatus.TIMEOUT) { http().get(server.url("/").toString()) }
    }

    @Test fun keyedRequestDoesNotFollowRedirectToOtherHost() {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/steal").toString()))
            expect(RunStatus.SERVER) { http().get(server.url("/").toString(), mapOf("x-apikey" to "k"), carriesSecret = true) }
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }

    @Test fun cleartextBlockedInProductionMode() {
        val strict = Http(client)
        expect(RunStatus.NETWORK) { strict.get("http://example.com/") }
    }
}
