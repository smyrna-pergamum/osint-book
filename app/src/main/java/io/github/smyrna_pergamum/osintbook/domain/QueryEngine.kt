package io.github.smyrna_pergamum.osintbook.domain

import androidx.room.withTransaction
import io.github.smyrna_pergamum.osintbook.data.AppDatabase
import io.github.smyrna_pergamum.osintbook.data.Evidence
import io.github.smyrna_pergamum.osintbook.data.Finding
import io.github.smyrna_pergamum.osintbook.data.ProviderSettings
import io.github.smyrna_pergamum.osintbook.data.QueryRun
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.Time
import io.github.smyrna_pergamum.osintbook.net.Http
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import io.github.smyrna_pergamum.osintbook.net.QuotaListener
import io.github.smyrna_pergamum.osintbook.providers.KeyNeed
import io.github.smyrna_pergamum.osintbook.providers.Provider
import io.github.smyrna_pergamum.osintbook.providers.ProviderResult
import io.github.smyrna_pergamum.osintbook.providers.QueryContext
import io.github.smyrna_pergamum.osintbook.security.KeyVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ProviderProgress(
    val providerId: String,
    val status: RunStatus,
    val requests: Int = 0,
    val newFindings: Int = 0,
    val errorCode: String? = null,
)

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Runs user-started queries. Providers are independent (FR-04), at most three run at once (NET-03),
 * rate limits are honoured before any request leaves (NET-04, AT-05) and cancelling keeps what
 * already arrived (NET-06).
 */
class QueryEngine(
    private val db: AppDatabase,
    private val http: Http,
    private val vault: KeyVault,
    private val raw: RawStore,
    private val scope: CoroutineScope,
    private val keepRaw: suspend () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val permits = Semaphore(3)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lastRequest = ConcurrentHashMap<String, Long>()
    private val settingsLock = Mutex()

    private val _progress = MutableStateFlow<Map<String, Map<String, ProviderProgress>>>(emptyMap())
    /** targetId → providerId → progress of the latest run started in this process. */
    val progress: StateFlow<Map<String, Map<String, ProviderProgress>>> = _progress

    fun isRunning(targetId: String) = jobs[targetId]?.isActive == true

    fun start(target: Target, providers: List<Provider>, language: String, extra: Map<String, String> = emptyMap()) {
        if (isRunning(target.id)) return
        _progress.update { it + (target.id to providers.associate { p -> p.id to ProviderProgress(p.id, RunStatus.PENDING) }) }
        val job = scope.launch {
            val children = providers.map { p -> launch { runOne(target, p, language, extra) } }
            children.forEach { it.join() }
        }
        jobs[target.id] = job
        job.invokeOnCompletion { jobs.remove(target.id) }
    }

    /** No new request starts after this; finished providers' findings stay saved. */
    fun cancel(targetId: String) {
        jobs[targetId]?.cancel()
    }

    private fun report(targetId: String, p: ProviderProgress) =
        _progress.update { all -> all + (targetId to ((all[targetId] ?: emptyMap()) + (p.providerId to p))) }

    private suspend fun runOne(target: Target, provider: Provider, language: String, extra: Map<String, String>) {
        val run = QueryRun(targetId = target.id, providerId = provider.id)
        db.queryRuns().insert(run)
        val requests = AtomicInteger(0)
        var quotaText: String? = null
        val listener = object : QuotaListener {
            override fun onRequest() {
                requests.incrementAndGet()
                report(target.id, ProviderProgress(provider.id, RunStatus.RUNNING, requests.get()))
            }
            override fun onHeaders(headers: Map<String, String>) {
                provider.quotaFromHeaders(headers)?.let { quotaText = it }
            }
        }

        suspend fun finish(status: RunStatus, code: String? = null, newFindings: Int = 0, blockedUntil: Long = 0) {
            withContext(NonCancellable) {
                db.queryRuns().update(run.copy(status = status, errorCode = code, completedAt = Time.nowIso(), requestCount = requests.get()))
                recordUsage(provider, requests.get(), quotaText, blockedUntil)
                report(target.id, ProviderProgress(provider.id, status, requests.get(), newFindings, code))
            }
        }

        try {
            val settings = db.providerSettings().get(provider.id) ?: ProviderSettings(provider.id)
            if (!settings.enabled) return finish(RunStatus.CANCELLED, "disabled")
            preflight(provider, target, settings)?.let { (status, code) -> return finish(status, code) }

            val key = when (provider.keyNeed) {
                KeyNeed.NONE -> null
                else -> when (val r = vault.get(refFor(provider.id))) {
                    is KeyVault.Read.Value -> r.secret
                    KeyVault.Read.Missing -> if (provider.keyNeed == KeyNeed.REQUIRED) return finish(RunStatus.NO_KEY, "no_key") else null
                    KeyVault.Read.Unreadable -> return finish(RunStatus.NO_KEY, "key_unreadable")
                }
            }

            val result = permits.withPermit {
                // Per-minute limits are kept on our side by spacing requests.
                val wait = (lastRequest[provider.id] ?: 0L) + provider.minIntervalMs - now()
                if (wait > 0) delay(wait)
                lastRequest[provider.id] = now()
                report(target.id, ProviderProgress(provider.id, RunStatus.RUNNING))
                provider.query(QueryContext(target, http, key, language, listener, extra))
            }
            val added = withContext(NonCancellable) { persist(target, run, provider, result) }
            finish(if (result.findings.isEmpty()) RunStatus.NO_RESULT else RunStatus.SUCCESS, newFindings = added)
        } catch (e: ProviderException) {
            finish(e.status, e.code, blockedUntil = e.blockedUntil)
        } catch (e: CancellationException) {
            finish(RunStatus.CANCELLED, "cancelled")
            throw e
        } catch (e: Exception) {
            // A bug in one adapter must not take the others down; never log the response or key (SEC-10).
            finish(RunStatus.PARSE, e.javaClass.simpleName)
        }
    }

    /** Checks done before any request leaves the device. */
    private fun preflight(provider: Provider, target: Target, s: ProviderSettings): Pair<RunStatus, String>? {
        if (target.type !in provider.supports) return RunStatus.UNSUPPORTED to "type"
        if (s.blockedUntil > now()) return RunStatus.RATE_LIMITED to "blocked_until:${Time.iso(s.blockedUntil)}"
        val limit = provider.dailyLimit ?: return null
        val used = if (s.dayKey == today()) s.requestsToday else 0
        if (used + provider.estimateRequests(target) > limit) return RunStatus.RATE_LIMITED to "daily_app_counter"
        return null
    }

    private suspend fun recordUsage(provider: Provider, count: Int, quotaText: String?, blockedUntil: Long) = settingsLock.withLock {
        val s = db.providerSettings().get(provider.id) ?: ProviderSettings(provider.id)
        val day = today()
        val used = (if (s.dayKey == day) s.requestsToday else 0) + count
        db.providerSettings().upsert(
            s.copy(
                requestsToday = used,
                dayKey = day,
                quotaState = quotaText ?: s.quotaState,
                blockedUntil = maxOf(s.blockedUntil, blockedUntil),
            )
        )
    }

    private fun today() = LocalDate.now(ZoneOffset.UTC).toString()

    /** DATA-04: findings and their evidence are written in one transaction. */
    private suspend fun persist(target: Target, run: QueryRun, provider: Provider, result: ProviderResult): Int {
        val retrievedAt = Time.nowIso()
        val store = keepRaw()
        val rawRefs = result.raw.associate { part ->
            val bytes = part.body.toByteArray()
            part.url to Pair(sha256Hex(bytes), if (store) raw.write(bytes) else null)
        }
        var added = 0
        db.withTransaction {
            for (d in result.findings) {
                val f = Finding(
                    queryRunId = run.id, investigationId = target.investigationId, targetId = target.id,
                    providerId = provider.id, type = d.type, label = d.label, value = d.value.take(4000),
                    normalizedValue = TargetParser.normalizeValue(d.value).take(4000), sourceUrl = d.sourceUrl,
                    sourceRecordId = d.sourceRecordId, retrievedAt = retrievedAt, publishedAt = d.publishedAt,
                )
                if (db.findings().insert(f) == -1L) continue
                added++
                val (hash, ref) = rawRefs[d.sourceUrl] ?: rawRefs.values.firstOrNull() ?: (sha256Hex(d.excerpt.toByteArray()) to null)
                db.evidence().insert(
                    Evidence(findingId = f.id, excerpt = d.excerpt, rawResponseRef = ref, sha256 = hash, capturedAt = retrievedAt)
                )
            }
            db.investigations().touch(target.investigationId)
        }
        return added
    }

    companion object {
        fun refFor(providerId: String) = "vault:$providerId"
    }
}
