package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import io.github.smyrna_pergamum.osintbook.providers.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class FeaturesTest {
    private suspend fun HttpClient.call(path: String, method: HttpMethod = HttpMethod.Get, cookie: String = "", body: String? = null) = request(path) {
        this.method=method;header(HttpHeaders.Cookie,cookie);header("X-OSINT-Request","1")
        if(body!=null)setBody(body)
    }
    private suspend fun HttpClient.signIn(name: String, password: String): String {
        val response=call("/api/login",HttpMethod.Post,body="""{"username":"$name","password":"$password"}""")
        assertEquals(HttpStatusCode.OK,response.status,response.bodyAsText())
        return response.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
    }
    @Test fun deletingUserRequiresAdminRevokesSessionsAndClearsPendingRequests() {
        val store=Store(Files.createTempDirectory("osint-delete-user-").resolve("store.sqlite"))
        val queries=Queries(store)
        try { testApplication {
            application { webModule(store,queries,"initial-admin-password",setOf("localhost")) }
            val http=createClient { defaultRequest { header(HttpHeaders.Host,"localhost") } }
            val admin=http.signIn("admin","initial-admin-password")
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/users",HttpMethod.Post,admin,"""{"username":"analyst","password":"analyst-password-123"}""").status)
            val member=http.signIn("analyst","analyst-password-123")
            assertEquals(HttpStatusCode.Unauthorized,http.call("/api/admin/ai/hardware").status)
            assertEquals(HttpStatusCode.Forbidden,http.call("/api/admin/ai/hardware",cookie=member).status)
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/ai/hardware",cookie=admin).status)
            store.requestPasswordReset("analyst")
            assertEquals(HttpStatusCode.Forbidden,http.call("/api/admin/users/analyst",HttpMethod.Delete,member).status)
            assertEquals(HttpStatusCode.BadRequest,http.call("/api/admin/users/admin",HttpMethod.Delete,admin).status)
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/users/analyst",HttpMethod.Delete,admin).status)
            assertEquals(HttpStatusCode.Unauthorized,http.call("/api/me",cookie=member).status)
            assertTrue(store.passwordRequests().none { it["username"] == "analyst" && it["status"] == "pending" })
            assertTrue(store.auditLog().any { it["action"] == "user_deleted" && it["subject"] == "analyst" })
            assertEquals(HttpStatusCode.NotFound,http.call("/api/admin/users/analyst",HttpMethod.Delete,admin).status)
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/users",HttpMethod.Post,admin,"""{"username":"analyst","password":"another-password-123"}""").status)
            assertEquals(HttpStatusCode.Unauthorized,http.call("/api/me",cookie=member).status)
        } } finally { queries.close(); store.close() }
    }
    @Test fun adminPermissionsKeySecrecyAndSessionRevocation() {
        val store=Store(Files.createTempDirectory("osint-access-").resolve("store.sqlite"))
        val queries=Queries(store)
        try { testApplication {
            application {webModule(store,queries,"initial-admin-password",setOf("localhost"))}
            val http=createClient {defaultRequest {header(HttpHeaders.Host,"localhost")}}
            val admin=http.signIn("admin","initial-admin-password")
            val created=http.call("/api/admin/users",HttpMethod.Post,admin,"""{"username":"analyst","password":"analyst-password-123","admin":false}""")
            assertEquals(HttpStatusCode.OK,created.status)
            val member=http.signIn("analyst","analyst-password-123")
            assertEquals(HttpStatusCode.Forbidden,http.call("/api/admin/keys",cookie=member).status)
            assertEquals(HttpStatusCode.Forbidden,http.call("/api/admin/users",HttpMethod.Post,member,"{}").status)
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/keys/virustotal",HttpMethod.Put,admin,"""{"key":"test-secret-not-a-real-key"}""").status)
            assertEquals("test-secret-not-a-real-key",queries.secrets.get("virustotal"))
            assertFalse(store.secret("virustotal")!!.contains("test-secret"))
            assertFalse(http.call("/api/admin/keys",cookie=admin).bodyAsText().contains("test-secret"))
            assertFalse(http.call("/api/providers",cookie=member).bodyAsText().contains("test-secret"))
            assertFalse(http.call("/api/admin/users",cookie=admin).bodyAsText().contains("password"))
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/users/analyst",HttpMethod.Patch,admin,"""{"enabled":false}""").status)
            assertEquals(HttpStatusCode.Unauthorized,http.call("/api/me",cookie=member).status)
            assertEquals(HttpStatusCode.BadRequest,http.call("/api/admin/users/admin",HttpMethod.Patch,admin,"""{"enabled":false}""").status)
            http.call("/api/admin/users/analyst",HttpMethod.Patch,admin,"""{"enabled":true,"password":"new-analyst-password"}""")
            val newMember=http.signIn("analyst","new-analyst-password")
            assertEquals(HttpStatusCode.OK,http.call("/api/me",cookie=newMember).status)
            assertEquals(HttpStatusCode.OK,http.call("/api/admin/keys/virustotal",HttpMethod.Delete,admin).status)
            assertNull(store.secret("virustotal"))
        } } finally {queries.close();store.close()}
    }
    @Test fun accountsAndVaultSurviveRestartWithoutPlaintextPasswords() {
        val dir=Files.createTempDirectory("osint-persistence-");val path=dir.resolve("store.sqlite")
        Store(path).use {store ->
            store.addUser("Admin","correct-password-123",true)
            assertNotEquals("correct-password-123",store.users().single().password)
            Secrets(store).save("virustotal","vault-fixture-key")
        }
        Store(path).use {store ->
            assertEquals("admin",store.users().single().name)
            assertTrue(Passwords.verify("correct-password-123",store.users().single().password))
            assertFalse(Passwords.verify("wrong-password",store.users().single().password))
            assertEquals("vault-fixture-key",Secrets(store).get("virustotal"))
            assertNotEquals(Passwords.hash("same-password-123"),Passwords.hash("same-password-123"))
            assertFailsWith<IllegalArgumentException>{store.addUser("admin","another-password",false)}
        }
    }
    @Test fun englishDefaultAndAnalysisTranslationsAreServed() {
        val store=Store(Files.createTempDirectory("osint-locales-").resolve("store.sqlite"));val queries=Queries(store)
        try {testApplication {
            application {webModule(store,queries,"initial-admin-password",setOf("localhost"))}
            val http=createClient{defaultRequest{header(HttpHeaders.Host,"localhost")}}
            val html=http.get("/").bodyAsText()
            assertTrue(html.contains("lang=\"en\""));assertTrue(html.contains("i18n.js"))
            for(lang in listOf("en","tr","de")) {
                val r=http.get("/analysis/$lang.json")
                assertEquals(HttpStatusCode.OK,r.status)
                assertTrue(wire.parseToJsonElement(r.bodyAsText()).jsonObject.containsKey("SUMMARY"))
            }
        }} finally {queries.close();store.close()}
    }
    @Test fun pingNeedsExplicitConsentAndUsesBoundedArguments() {
        val store=Store(Files.createTempDirectory("osint-ping-").resolve("store.sqlite"));val queries=Queries(store)
        val inv=Investigation(title="Ping test")
        val target=Target(investigationId=inv.id,type=TargetType.IP,rawValue="127.0.0.1",normalizedValue="127.0.0.1",displayValue="127.0.0.1")
        store.insert(InvestigationBundle(investigation=inv,targets=listOf(target)))
        assertEquals(listOf("-4","-n","4","-w","2000","127.0.0.1"),WindowsPing().command(target).drop(1))
        assertFailsWith<IllegalArgumentException>{WindowsPing().command(target.copy(normalizedValue="-t"))}
        try {testApplication {
            application {webModule(store,queries,"initial-admin-password",setOf("localhost"))}
            val http=createClient{defaultRequest{header(HttpHeaders.Host,"localhost")}}
            val admin=http.signIn("admin","initial-admin-password")
            val result=http.call("/api/investigations/${inv.id}/query",HttpMethod.Post,admin,"""{"targetId":"${target.id}","providers":["ping"]}""")
            assertEquals(HttpStatusCode.BadRequest,result.status)
            assertTrue(store.get(inv.id)!!.queryRuns.isEmpty())
        }} finally {queries.close();store.close()}
    }
    @Test fun cancellingOneProviderKeepsSuccessfulFindings() = runBlocking {
        val store=Store(Files.createTempDirectory("osint-cancel-").resolve("store.sqlite"));val queries=Queries(store)
        val inv=Investigation(title="Cancellation")
        val target=Target(investigationId=inv.id,type=TargetType.DOMAIN,rawValue="example.org",normalizedValue="example.org",displayValue="example.org")
        store.insert(InvestigationBundle(investigation=inv,targets=listOf(target)))
        fun fake(id: String, slow: Boolean)=object:Provider {
            override val id=id;override val name=id;override val version=1;override val host="example.org"
            override val supports=setOf(TargetType.DOMAIN);override val keyNeed=KeyNeed.NONE;override val docsUrl="https://example.org"
            override fun estimateRequests(target: Target)=1
            override suspend fun query(ctx: QueryContext): ProviderResult {
                if(slow)delay(10000)
                return ProviderResult(listOf(FindingDraft("DNS","A","192.0.2.1","https://example.org")),emptyList())
            }
        }
        try {
            queries.start(inv.id,target,listOf(fake("google_dns",false),fake("rdap",true)),emptyMap())
            queries.cancel(target.id,"rdap") // includes cancellation before the child has started
            withTimeout(5000){while(store.get(inv.id)!!.queryRuns.any{!it.status.isFinal})delay(10)}
            queries.close()
            val b=store.get(inv.id)!!
            assertEquals(RunStatus.CANCELLED,b.queryRuns.first{it.providerId=="rdap"}.status)
            assertEquals(RunStatus.SUCCESS,b.queryRuns.first{it.providerId=="google_dns"}.status)
            assertEquals(1,b.findings.size)
        } finally {queries.close();store.close()}
    }
}
