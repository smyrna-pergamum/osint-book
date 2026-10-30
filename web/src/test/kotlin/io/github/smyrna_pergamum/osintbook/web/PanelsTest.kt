package io.github.smyrna_pergamum.osintbook.web

import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class PanelsTest {
    @Test fun portsAreChosenSavedAndValidated() {
        val dir=Files.createTempDirectory("panel-ports-")
        val answers=ArrayDeque(listOf("8888","8888","8999"))
        assertEquals(Ports(8888,8999),configurePorts(dir,null,null) { answers.removeFirst() })
        assertEquals(Ports(8888,8999),configurePorts(dir,null,null) { error("must not prompt again") })
        assertEquals(Ports(9000,9001),configurePorts(dir,"9000","9001",null))
        assertEquals(Ports(8888,8999),configurePorts(dir,null,null,null))
        assertFailsWith<IllegalArgumentException> { configurePorts(dir,"9000","9000",null) }
        assertFailsWith<IllegalArgumentException> { configurePorts(dir,"65536","9001",null) }
    }
    @Test fun userAndAdminPanelsIsolateAccountsRequestsAndSessions() {
        val store=Store(Files.createTempDirectory("panels-").resolve("db.sqlite"));val queries=Queries(store)
        try {
            store.addUser("admin","administrator-password",true);store.addUser("member","member-password-123",false)
            val access=Access(store)
            var userCookie=""
            testApplication {
                application { webModule(store,queries,"",setOf("localhost"),panel=Panel.USER,access=access) }
                val http=createClient { defaultRequest { header(HttpHeaders.Host,"localhost") } }
                val login=http.post("/api/login") { header("X-OSINT-Request","1");setBody("""{"username":"member","password":"member-password-123"}""") }
                assertEquals(HttpStatusCode.OK,login.status);userCookie=login.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                assertEquals(HttpStatusCode.NotFound,http.get("/api/admin/users") { header(HttpHeaders.Cookie,userCookie) }.status)
                val request=http.post("/api/password-reset-request") { header("X-OSINT-Request","1");setBody("""{"username":"member"}""") }
                assertEquals(HttpStatusCode.OK,request.status)
                http.post("/api/password-reset-request") { header("X-OSINT-Request","1");setBody("""{"username":"nobody"}""") }
                assertEquals(1,store.passwordRequests().size)
            }
            testApplication {
                application { webModule(store,queries,"",setOf("localhost"),panel=Panel.ADMIN,access=access) }
                val http=createClient { defaultRequest { header(HttpHeaders.Host,"localhost") } }
                assertEquals(HttpStatusCode.Unauthorized,http.get("/api/admin/users") { header(HttpHeaders.Cookie,userCookie) }.status)
                assertEquals(HttpStatusCode.Unauthorized,http.post("/api/login") { header("X-OSINT-Request","1");setBody("""{"username":"member","password":"member-password-123"}""") }.status)
                val admin=http.post("/api/login") { header("X-OSINT-Request","1");setBody("""{"username":"admin","password":"administrator-password"}""") }
                assertEquals(HttpStatusCode.OK,admin.status)
                val cookie=admin.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                assertTrue(cookie.startsWith("osint_admin_session="))
                assertEquals(HttpStatusCode.NotFound,http.get("/api/investigations") {header(HttpHeaders.Cookie,cookie)}.status)
                val reset=http.patch("/api/admin/users/member") {header("X-OSINT-Request","1");header(HttpHeaders.Cookie,cookie);setBody("""{"password":"new-member-password"}""")}
                assertEquals(HttpStatusCode.OK,reset.status)
                assertEquals("resolved",store.passwordRequests().single()["status"])
                assertNull(access.user(userCookie.substringAfter('=')))
                assertTrue(Passwords.verify("new-member-password",store.users().first { it.name=="member" }.password))
                assertTrue(store.auditLog().any { it["action"]=="password_reset" && it["subject"]=="member" })
                assertFalse(http.get("/api/admin/logs") { header(HttpHeaders.Cookie,cookie) }.bodyAsText().contains("new-member-password"))
            }
        } finally {queries.close();store.close()}
    }
}
