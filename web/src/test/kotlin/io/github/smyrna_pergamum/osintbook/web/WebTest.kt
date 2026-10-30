package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.export.*
import io.ktor.client.request.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.*

class WebTest {
    private val ApplicationTestBuilder.http get() = createClient { defaultRequest { header(HttpHeaders.Host,"localhost") } }
    private fun withServer(block: suspend ApplicationTestBuilder.(Store) -> Unit) {
        val store = Store(Files.createTempDirectory("osint-web-test-").resolve("test.sqlite"))
        val queries = Queries(store)
        try {
            testApplication {
                application { webModule(store,queries,"test-password-only",setOf("localhost")) }
                block(store)
            }
        } finally { queries.close(); store.close() }
    }
    private suspend fun ApplicationTestBuilder.login(): String {
        val response = http.post("/api/login") {
            header("X-OSINT-Request","1")
            setBody("""{"username":"admin","password":"test-password-only"}""")
        }
        assertEquals(HttpStatusCode.OK,response.status)
        val cookie=response.headers[HttpHeaders.SetCookie]!!
        assertTrue(cookie.contains("HttpOnly",true)); assertTrue(cookie.contains("SameSite=Strict",true))
        return cookie.substringBefore(';')
    }
    @Test fun authenticationAndCrossOriginGuards() = withServer {
        assertEquals(HttpStatusCode.Unauthorized,http.get("/api/investigations").status)
        assertEquals(HttpStatusCode.Forbidden,client.get("/"){header(HttpHeaders.Host,"attacker.invalid")}.status)
        assertEquals(HttpStatusCode.Forbidden,client.post("/api/login"){setBody("{}");header(HttpHeaders.Origin,"http://attacker.invalid")}.status)
        val cookie=login()
        assertEquals(HttpStatusCode.OK,http.get("/api/investigations"){header(HttpHeaders.Cookie,cookie)}.status)
        assertEquals(HttpStatusCode.Forbidden,http.post("/api/investigations"){
            header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1");header(HttpHeaders.Origin,"http://attacker.invalid");setBody("""{"title":"x"}""")
        }.status)
        http.post("/api/logout"){header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1");setBody("{}")}
        assertEquals(HttpStatusCode.Unauthorized,http.get("/api/investigations"){header(HttpHeaders.Cookie,cookie)}.status)
    }
    @Test fun deleteTargetRemovesOnlyItsDependentRecords() = withServer { store ->
        val inv=Investigation(title="Delete target")
        fun target(value: String) = Target(investigationId=inv.id,type=TargetType.DOMAIN,rawValue=value,normalizedValue=value,displayValue=value)
        val removed=target("a.example"); val kept=target("b.example")
        fun run(t: Target) = QueryRun(targetId=t.id,providerId="dns",status=RunStatus.SUCCESS)
        val runA=run(removed);val runB=run(kept)
        fun finding(t: Target,r: QueryRun) = Finding(queryRunId=r.id,investigationId=inv.id,targetId=t.id,providerId="dns",type="text",label="label",value="value",normalizedValue="value",sourceUrl="https://example.org",retrievedAt=Time.nowIso())
        val fa=finding(removed,runA);val fb=finding(kept,runB)
        fun evidence(f: Finding)=Evidence(findingId=f.id,excerpt="data",sha256="hash",capturedAt=Time.nowIso())
        val linked=Note(investigationId=inv.id,findingId=fa.id,content="linked")
        val general=Note(investigationId=inv.id,content="general")
        store.insert(InvestigationBundle(investigation=inv,targets=listOf(removed,kept),queryRuns=listOf(runA,runB),findings=listOf(fa,fb),evidence=listOf(evidence(fa),evidence(fb)),tags=listOf(Tag(investigationId=inv.id,findingId=fa.id,label="x")),notes=listOf(linked,general),relations=listOf(Relation(investigationId=inv.id,fromId=removed.id,toId=fb.id,type="related",assertionType=AssertionType.INFERRED))))
        val path="/api/investigations/${inv.id}/targets/${removed.id}"
        assertEquals(HttpStatusCode.Unauthorized,http.delete(path){header("X-OSINT-Request","1")}.status)
        val cookie=login()
        assertEquals(HttpStatusCode.OK,http.delete(path){header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1")}.status)
        val after=store.get(inv.id)!!
        assertEquals(listOf(kept.id),after.targets.map { it.id })
        assertEquals(listOf(runB.id),after.queryRuns.map { it.id })
        assertEquals(listOf(fb.id),after.findings.map { it.id })
        assertEquals(listOf(fb.id),after.evidence.map { it.findingId })
        assertEquals(listOf(general.id),after.notes.map { it.id })
        assertTrue(after.relations.isEmpty());assertTrue(after.tags.isEmpty())
        assertEquals(HttpStatusCode.NotFound,http.delete(path){header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1")}.status)
    }
    @Test fun createNormalizeImportAndExportWithoutRequests() = withServer { store ->
        val cookie=login()
        val created=http.post("/api/investigations"){
            header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1");setBody("""{"title":"Deneme Türkçe"}""")
        }
        val b=wire.decodeFromString<InvestigationBundle>(created.bodyAsText())
        val id=b.investigation.id
        val target=http.post("/api/investigations/$id/targets"){
            header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1")
            setBody("""{"type":"URL","value":"https://example.org/path?token=secret-value&ok=yes"}""")
        }
        assertEquals(HttpStatusCode.OK,target.status)
        assertFalse(store.get(id)!!.targets.single().normalizedValue.contains("secret-value"))
        assertFalse(store.get(id)!!.targets.single().rawValue.contains("secret-value"))
        val exported=http.get("/api/investigations/$id/export/json"){header(HttpHeaders.Cookie,cookie)}
        assertEquals(HttpStatusCode.OK,exported.status)
        val imported=http.post("/api/import"){
            header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1");setBody(exported.bodyAsText())
        }
        assertEquals(HttpStatusCode.OK,imported.status)
        val copy=wire.decodeFromString<InvestigationBundle>(imported.bodyAsText())
        assertNotEquals(id,copy.investigation.id)
        assertTrue(copy.queryRuns.isEmpty())
        assertEquals(2,store.list().size)
        val providers=http.get("/api/providers"){header(HttpHeaders.Cookie,cookie)}.bodyAsText()
        assertTrue(providers.contains("\"ping\""))
    }
    @Test fun parallelUpdatesPersistAndInterruptedRunsRecover() {
        val file=Files.createTempDirectory("osint-store-test-").resolve("test.sqlite")
        val b=InvestigationBundle(investigation=Investigation(title="Concurrency"))
        Store(file).use { store ->
            store.insert(b)
            val pool=Executors.newFixedThreadPool(6)
            try {
                (1..50).map { n -> pool.submit { store.update(b.investigation.id) { old -> old.copy(notes=old.notes+Note(investigationId=b.investigation.id,content="note $n")) } } }.forEach { it.get() }
            } finally { pool.shutdown() }
            assertEquals(50,store.get(b.investigation.id)!!.notes.size)
            store.update(b.investigation.id) { it.copy(queryRuns=listOf(QueryRun(targetId="t",providerId="google_dns",status=RunStatus.RUNNING))) }
        }
        Store(file).use { store ->
            val recovered=store.get(b.investigation.id)!!
            assertEquals(50,recovered.notes.size)
            assertEquals(RunStatus.CANCELLED,recovered.queryRuns.single().status)
        }
    }
    @Test fun queryPersistsEvidenceAndIsolatesProviderFailure() = runBlocking {
        val store=Store(Files.createTempDirectory("osint-query-test-").resolve("test.sqlite"))
        val queries=Queries(store)
        val inv=Investigation(title="Query")
        val target=Target(investigationId=inv.id,type=TargetType.DOMAIN,rawValue="example.org",normalizedValue="example.org",displayValue="example.org")
        store.insert(InvestigationBundle(investigation=inv,targets=listOf(target)))
        fun fake(id: String, fail: Boolean) = object : io.github.smyrna_pergamum.osintbook.providers.Provider {
            override val id=id; override val name=id; override val version=1; override val host="example.org"
            override val supports=setOf(TargetType.DOMAIN)
            override val keyNeed=io.github.smyrna_pergamum.osintbook.providers.KeyNeed.NONE
            override val docsUrl="https://example.org"
            override fun estimateRequests(target: Target)=1
            override suspend fun query(ctx: io.github.smyrna_pergamum.osintbook.providers.QueryContext): io.github.smyrna_pergamum.osintbook.providers.ProviderResult {
                ctx.quota.onRequest()
                if(fail) throw io.github.smyrna_pergamum.osintbook.net.ProviderException(RunStatus.NETWORK,"fake_failure")
                return io.github.smyrna_pergamum.osintbook.providers.ProviderResult(listOf(io.github.smyrna_pergamum.osintbook.providers.FindingDraft("DNS","A","192.0.2.1","https://example.org",excerpt="fixture")),emptyList())
            }
        }
        try {
            queries.start(inv.id,target,listOf(fake("google_dns",false),fake("rdap",true)),emptyMap())
            withTimeout(5000) { while(store.get(inv.id)!!.queryRuns.any { !it.status.isFinal }) delay(10) }
            queries.close()
            val b=store.get(inv.id)!!
            assertEquals(setOf(RunStatus.SUCCESS,RunStatus.NETWORK),b.queryRuns.map { it.status }.toSet())
            assertEquals("192.0.2.1",b.findings.single().value)
            assertEquals(digest("fixture".toByteArray()),b.evidence.single().sha256)
            assertEquals(1,store.quota("google_dns").second)
        } finally { queries.close();store.close() }
    }
}
