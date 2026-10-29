package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.domain.TargetParser
import io.github.smyrna_pergamum.osintbook.net.*
import io.github.smyrna_pergamum.osintbook.providers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

val webProviders = Providers.all.filter { it.id != "ping" } + WindowsPing()
fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class Queries(private val store: Store, private val http: Http = Http(), val secrets: Secrets = Secrets(store)) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val providerJobs = ConcurrentHashMap<String, Job>()
    private val cancelledProviders = ConcurrentHashMap.newKeySet<String>()
    private val locks = webProviders.associate { it.id to Mutex() }
    private val next = ConcurrentHashMap<String, Long>()
    private val permits = Semaphore(3)

    @Synchronized fun start(inv: String, target: Target, selected: List<Provider>, extra: Map<String,String>, language: String = "en") {
        require(!jobs.containsKey(target.id)) { "query_running" }
        require(jobs.size < 20) { "query_queue_full" }
        val runs = selected.map { QueryRun(targetId = target.id, providerId = it.id) }
        store.update(inv) {
            require(it.investigation.archivedAt == null) { "archived" }
            require(it.targets.any { t -> t.id == target.id }) { "target_not_found" }
            it.copy(queryRuns = it.queryRuns + runs)
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            selected.zip(runs).map { (p,r) ->
                val child=launch(start=CoroutineStart.LAZY) { run(inv,target,p,r,extra,language) }
                val key=target.id+"|"+p.id
                providerJobs[key]=child
                child.invokeOnCompletion {
                    store.update(inv) { b -> b.copy(queryRuns=b.queryRuns.map {
                        if(it.id==r.id && !it.status.isFinal) it.copy(status=RunStatus.CANCELLED,completedAt=Time.nowIso()) else it
                    }) }
                    providerJobs.remove(key,child)
                }
                if(cancelledProviders.remove(key)) child.cancel() else child.start()
                child
            }.joinAll()
        }
        jobs[target.id] = job
        job.invokeOnCompletion {
            store.update(inv) { b -> b.copy(queryRuns = b.queryRuns.map {
                if (it.id in runs.map { r -> r.id } && !it.status.isFinal) it.copy(status = RunStatus.CANCELLED, completedAt = Time.nowIso()) else it
            }) }
            jobs.remove(target.id, job)
            cancelledProviders.removeIf { it.startsWith(target.id+"|") }
        }
        job.start()
    }
    fun cancel(target: String, provider: String? = null) {
        if(provider==null) jobs[target]?.cancel()
        else if(jobs.containsKey(target)) {
            val key=target+"|"+provider
            cancelledProviders.add(key);providerJobs[key]?.cancel()
        }
    }
    fun busy(b: io.github.smyrna_pergamum.osintbook.export.InvestigationBundle) = b.targets.any { jobs[it.id]?.isActive == true }
    @Synchronized fun delete(inv: String) {
        val b = store.get(inv) ?: throw NoSuchElementException()
        require(b.targets.none { jobs.containsKey(it.id) }) { "query_wait_delete" }
        store.delete(inv)
    }
    @Synchronized fun deleteTarget(inv: String, target: String) {
        require(!jobs.containsKey(target)) { "query_wait_delete" }
        store.update(inv) { b ->
            require(b.investigation.archivedAt == null) { "archived" }
            if (b.targets.none { it.id == target }) throw NoSuchElementException()
            val findings = b.findings.filter { it.targetId == target }.map { it.id }.toSet()
            val removedNodes = findings + target
            b.copy(targets = b.targets.filterNot { it.id == target },
                queryRuns = b.queryRuns.filterNot { it.targetId == target },
                findings = b.findings.filterNot { it.id in findings },
                evidence = b.evidence.filterNot { it.findingId in findings },
                tags = b.tags.filterNot { it.findingId in findings },
                notes = b.notes.filterNot { it.findingId in findings },
                relations = b.relations.filterNot { it.fromId in removedNodes || it.toId in removedNodes })
        }
    }

    private suspend fun run(inv: String, target: Target, p: Provider, initial: QueryRun, extra: Map<String,String>, language: String) {
        var requests = 0
        var blocked = 0L
        var quotaOwned = false
        fun finish(status: RunStatus, error: String? = null) {
            store.update(inv) { b -> b.copy(queryRuns = b.queryRuns.map {
                if (it.id == initial.id) it.copy(status = status, errorCode = error, completedAt = Time.nowIso(), requestCount = requests) else it
            }) }
        }
        try {
            locks.getValue(p.id).withLock {
                val day = Time.nowIso().take(10)
                val q = store.quota(p.id)
                val used = if (q.first == day) q.second else 0
                if (q.third > System.currentTimeMillis() || p.dailyLimit?.let { used + p.estimateRequests(target) > it } == true) {
                    finish(RunStatus.RATE_LIMITED, "quota"); return
                }
                val key = secrets.get(p.id)
                if (p.keyNeed == KeyNeed.REQUIRED && key == null) { finish(RunStatus.NO_KEY); return }
                quotaOwned = true
                blocked = q.third
                try {
                    delay(((next[p.id] ?: 0) - System.currentTimeMillis()).coerceAtLeast(0))
                    val result = permits.withPermit {
                        store.update(inv) { b -> b.copy(queryRuns = b.queryRuns.map { if (it.id == initial.id) it.copy(status = RunStatus.RUNNING) else it }) }
                        withTimeout(180_000) {
                            p.query(QueryContext(target, http, key, language, object : QuotaListener {
                                override fun onRequest() { requests++ }
                            }, extra))
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    val now = Time.nowIso()
                    store.update(inv) { b ->
                        val fs = b.findings.toMutableList()
                        val es = b.evidence.toMutableList()
                        for (d in result.findings) {
                            val normalized = TargetParser.normalizeValue(d.value).take(4000)
                            if (fs.any { it.providerId == p.id && it.type == d.type && it.label == d.label && it.normalizedValue == normalized && it.sourceRecordId == d.sourceRecordId }) continue
                            val f = Finding(queryRunId = initial.id, investigationId = inv, targetId = target.id,
                                providerId = p.id, type = d.type, label = d.label, value = d.value.take(4000), normalizedValue = normalized,
                                sourceUrl = d.sourceUrl, sourceRecordId = d.sourceRecordId, retrievedAt = now, publishedAt = d.publishedAt)
                            fs += f
                            val raw = result.raw.firstOrNull { it.url == d.sourceUrl }?.body ?: d.excerpt
                            es += Evidence(findingId = f.id, excerpt = d.excerpt.take(4000), sha256 = digest(raw.toByteArray()), capturedAt = now)
                        }
                        b.copy(findings = fs, evidence = es)
                    }
                    finish(if (result.findings.isEmpty()) RunStatus.NO_RESULT else RunStatus.SUCCESS)
                } catch (e: ProviderException) { blocked = maxOf(blocked, e.blockedUntil); finish(e.status, e.code) }
                finally {
                    if (quotaOwned) {
                        store.quota(p.id, day, used + requests, blocked)
                        next[p.id] = System.currentTimeMillis() + p.minIntervalMs
                    }
                }
            }
        } catch (e: TimeoutCancellationException) { finish(RunStatus.TIMEOUT, "query_timeout") }
        catch (e: CancellationException) { finish(RunStatus.CANCELLED); throw e }
        catch (e: Exception) { finish(RunStatus.PARSE, e.javaClass.simpleName) }
    }
    override fun close() { runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() } }
}
