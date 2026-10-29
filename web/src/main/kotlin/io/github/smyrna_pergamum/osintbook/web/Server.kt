package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.domain.*
import io.github.smyrna_pergamum.osintbook.export.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

val wire = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private fun JsonObject.text(name: String, max: Int = 2048): String = (this[name] as? JsonPrimitive)?.content.orEmpty().also { require(it.length <= max) { "field_too_long" } }
private fun JsonObject.flag(name: String) = (this[name] as? JsonPrimitive)?.booleanOrNull ?: false
private fun JsonObject.strings(name: String) = (this[name] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
private fun obj(vararg values: Pair<String, String>) = buildJsonObject { values.forEach { put(it.first, it.second) } }
suspend fun ApplicationCall.json(value: JsonElement, status: HttpStatusCode = HttpStatusCode.OK) = respondText(value.toString(), ContentType.Application.Json, status)
suspend fun ApplicationCall.body(limit: Int = 64 * 1024): ByteArray {
    require((request.header("Content-Length")?.toLongOrNull() ?: 0) <= limit) { "request_too_large" }
    val channel = receiveChannel()
    val out = ByteArrayOutputStream()
    val bytes = ByteArray(8192)
    while (true) {
        val n = channel.readAvailable(bytes)
        if (n < 0) break
        require(out.size() + n <= limit) { "request_too_large" }
        out.write(bytes, 0, n)
    }
    return out.toByteArray()
}
suspend fun ApplicationCall.input(): JsonObject = wire.parseToJsonElement(body().toString(Charsets.UTF_8)).jsonObject

enum class Panel { USER, ADMIN, BOTH }
fun Application.webModule(store: Store, queries: Queries, password: String, hosts: Set<String>, initialUsername: String = "admin", panel: Panel = Panel.BOTH, access: Access = Access(store), ai: LocalAi? = null) {
    if(store.users().isEmpty()) store.addUser(initialUsername,password,true)
    val cookieName = if(panel == Panel.ADMIN) "osint_admin_session" else "osint_session"
    val hub = HuggingFaceHub()
    intercept(ApplicationCallPipeline.Call) {
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Referrer-Policy", "no-referrer")
        call.response.header("Cache-Control", "no-store")
        call.response.header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-src https://www.openstreetmap.org; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        val hostHeader = call.request.header("Host").orEmpty()
        val host = runCatching { URI("http://$hostHeader").host?.trim('[', ']')?.lowercase() }.getOrNull()
        if (host !in hosts) { call.respond(HttpStatusCode.Forbidden); finish(); return@intercept }
        if (call.request.httpMethod != HttpMethod.Get && call.request.httpMethod != HttpMethod.Head) {
            val origin = call.request.header("Origin")
            val originHost = origin?.let { runCatching { URI(it).rawAuthority }.getOrNull() }
            if (call.request.header("X-OSINT-Request") != "1" || (origin != null && originHost != hostHeader)) {
                call.respond(HttpStatusCode.Forbidden); finish(); return@intercept
            }
        }
        val path = call.request.path()
        if ((panel == Panel.USER && path.startsWith("/api/admin/")) ||
            (panel == Panel.ADMIN && path.startsWith("/api/") && !path.startsWith("/api/admin/") && path !in setOf("/api/login","/api/logout","/api/me"))) {
            call.respond(HttpStatusCode.NotFound); finish(); return@intercept
        }
        val token = call.request.cookies[cookieName]
        if (path.startsWith("/api/") && path != "/api/login" && path != "/api/password-reset-request" && !access.valid(token)) {
            call.json(obj("error" to "login_required"), HttpStatusCode.Unauthorized); finish(); return@intercept
        }
        if((path.startsWith("/api/admin/") || (panel == Panel.ADMIN && path.startsWith("/api/") && path != "/api/login")) && access.user(token)?.admin != true) {
            call.json(obj("error" to "admin_required"),HttpStatusCode.Forbidden);finish();return@intercept
        }
        try {
            proceed()
            if (call.request.httpMethod !in setOf(HttpMethod.Get,HttpMethod.Head) && path.startsWith("/api/") &&
                path !in setOf("/api/login","/api/logout","/api/password-reset-request") &&
                !path.startsWith("/api/admin/users") && (call.response.status()?.value ?: 200) in 200..299) {
                access.user(token)?.let { store.audit(it.name,"${call.request.httpMethod.value} $path") }
            }
        }
        catch (e: NoSuchElementException) { call.json(obj("error" to "not_found"), HttpStatusCode.NotFound) }
        catch (e: ImportException) { call.json(obj("error" to "import_failed"), HttpStatusCode.BadRequest) }
        catch (e: IllegalArgumentException) { call.json(obj("error" to (e.message ?: "invalid_request")), HttpStatusCode.BadRequest) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { call.json(obj("error" to "operation_failed"), HttpStatusCode.InternalServerError) }
    }
    routing {
        get("/") { call.respondText(resource(if(panel == Panel.ADMIN) "admin.html" else "index.html"), ContentType.Text.Html) }
        get("/admin.js") { if(panel == Panel.USER) call.respond(HttpStatusCode.NotFound) else call.respondText(resource("admin.js"),ContentType.Application.JavaScript) }
        get("/app.js") { call.respondText(resource("app.js"), ContentType.Application.JavaScript) }
        get("/style.css") { call.respondText(resource("style.css"), ContentType.Text.CSS) }
        get("/i18n.js") { call.respondText(resource("i18n.js"),ContentType.Application.JavaScript) }
        get("/analysis/{lang}.json") {
            val lang=call.parameters["lang"]!!;require(lang in setOf("en","tr","de"))
            call.respondText(resource("analysis-$lang.json"),ContentType.Application.Json)
        }
        post("/api/login") {
            val input=call.input()
            val name=input.text("username",40).lowercase()
            val token = access.login(name,input.text("password", 256))
            val accepted = token != null && (panel != Panel.ADMIN || access.user(token)?.admin == true)
            store.audit(if(accepted) name else "unknown",if(accepted) "login_success" else "login_failed",panel.name.lowercase())
            if (!accepted) { access.logout(token); call.json(obj("error" to "invalid_credentials"), HttpStatusCode.Unauthorized) }
            else {
                call.response.cookies.append(Cookie(cookieName, token!!, path = "/", httpOnly = true, maxAge = 28800, extensions = mapOf("SameSite" to "Strict")))
                call.json(obj("status" to "ok"))
            }
        }
        post("/api/logout") {
            access.user(call.request.cookies[cookieName])?.let { store.audit(it.name,"logout",panel.name.lowercase()) }
            access.logout(call.request.cookies[cookieName])
            call.response.cookies.append(Cookie(cookieName, "", path = "/", maxAge = 0))
            call.json(obj("status" to "ok"))
        }
        get("/api/me") {
            val user=access.user(call.request.cookies[cookieName])!!
            call.json(buildJsonObject {put("username",user.name);put("admin",user.admin)})
        }
        get("/api/admin/users") { call.json(buildJsonArray { store.users().forEach { user -> add(buildJsonObject {
            put("username",user.name);put("admin",user.admin);put("enabled",user.enabled)
        }) } }) }
        post("/api/admin/users") {
            val input=call.input();store.addUser(input.text("username",40),input.text("password",256),input.flag("admin"))
            store.audit(access.user(call.request.cookies[cookieName])!!.name,"user_created",input.text("username",40).lowercase())
            call.json(obj("status" to "ok"))
        }
        patch("/api/admin/users/{name}") {
            val name=call.parameters["name"]!!;val input=call.input()
            store.changeUser(name,input.text("password",256).takeIf {it.isNotEmpty()},if("enabled" in input) input.flag("enabled") else null,if("admin" in input) input.flag("admin") else null)
            if(input.text("password",256).isNotEmpty()) store.resolvePasswordRequests(name)
            store.audit(access.user(call.request.cookies[cookieName])!!.name,if(input.text("password",256).isNotEmpty()) "password_reset" else "user_permissions_changed",name)
            access.revoke(name);call.json(obj("status" to "ok"))
        }
        delete("/api/admin/users/{name}") {
            val name=call.parameters["name"]!!
            val actor=access.user(call.request.cookies[cookieName])!!.name
            require(name != actor) { "cannot_delete_self" }
            store.deleteUser(name)
            access.revoke(name)
            store.audit(actor,"user_deleted",name)
            call.json(obj("status" to "ok"))
        }
        post("/api/password-reset-request") {
            val name=call.input().text("username",40).trim().lowercase()
            require(Regex("[a-z0-9_.-]{3,40}").matches(name)) { "username_invalid" }
            access.requestReset(name)
            store.audit("anonymous","password_reset_requested")
            call.json(obj("status" to "ok"))
        }
        get("/api/admin/password-requests") { call.json(wire.encodeToJsonElement(store.passwordRequests())) }
        get("/api/admin/logs") { call.json(wire.encodeToJsonElement(store.auditLog())) }
        fun localAi()=ai ?: throw IllegalArgumentException("ai_runtime_missing")
        get("/api/admin/ai/hardware") { call.json(withContext(Dispatchers.IO) { ai?.hardware() ?: AiHostInfo.snapshot(emptyList(),false,false) }) }
        get("/api/admin/ai/status") { val a=localAi(); call.json(buildJsonObject {
            put("download",a.downloadStatus());put("engine",a.engineStatus());put("installed",wire.encodeToJsonElement(a.installed()))
            put("active",wire.encodeToJsonElement(a.activeModel()));put("settings",wire.encodeToJsonElement(a.activeSettings()));put("defaultThreads",a.defaultThreads())
        }) }
        get("/api/admin/ai/recommendations") { call.json(localAi().recommendations()) }
        post("/api/admin/ai/download") { val input=call.input();localAi().download(input.text("repository",200),input.text("filename",240));call.json(obj("status" to "started")) }
        post("/api/admin/ai/download/cancel") { localAi().cancelDownload();call.json(obj("status" to "cancelled")) }
        post("/api/admin/ai/activate") {
            val a=localAi();val input=call.input()
            val settings=EngineSettings(input.text("id",64),input.text("backend",8).ifBlank { "cpu" },input.text("context",8).toIntOrNull() ?: 8192,input.text("threads",4).toIntOrNull() ?: 0)
            withContext(Dispatchers.IO) { a.activate(settings) };call.json(obj("status" to "loading"),HttpStatusCode.Accepted)
        }
        post("/api/admin/ai/unload") { withContext(Dispatchers.IO) { localAi().unload() };call.json(obj("status" to "ok")) }
        delete("/api/admin/ai/models/{id}") { localAi().delete(call.parameters["id"]!!);call.json(obj("status" to "ok")) }
        get("/api/admin/ai/search") {
            val query=call.request.queryParameters["q"].orEmpty()
            call.json(withContext(Dispatchers.IO) { hub.search(query,call.request.queryParameters["popular"]=="1") })
        }
        get("/api/admin/ai/repository") {
            val repo=call.request.queryParameters["id"].orEmpty()
            call.json(withContext(Dispatchers.IO) { localAi().repository(repo) })
        }
        get("/api/ai/status") {
            val a=ai;val model=a?.activeModel()
            call.json(buildJsonObject {
                put("state",a?.engineStatus()?.get("state") ?: JsonPrimitive("unavailable"))
                put("model",model?.let { m -> buildJsonObject {put("repository",m.repository);put("filename",m.filename);put("quantization",AiCatalog.quantization(m.filename)?.let(::JsonPrimitive) ?: JsonNull)} } ?: JsonNull)
                put("backend",a?.activeSettings()?.backend?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
        get("/api/admin/keys") { call.json(buildJsonArray { webProviders.filter {it.keyNeed!=io.github.smyrna_pergamum.osintbook.providers.KeyNeed.NONE}.forEach { p -> add(buildJsonObject {
            put("id",p.id);put("name",p.name);put("configured",queries.secrets.get(p.id)!=null);put("stored",queries.secrets.stored(p.id))
        }) } }) }
        put("/api/admin/keys/{id}") {
            val id=call.parameters["id"]!!
            require(webProviders.any {it.id==id && it.keyNeed!=io.github.smyrna_pergamum.osintbook.providers.KeyNeed.NONE}){"invalid_provider"}
            queries.secrets.save(id,call.input().text("key",4096));call.json(obj("status" to "ok"))
        }
        delete("/api/admin/keys/{id}") {
            queries.secrets.save(call.parameters["id"]!!,null);call.json(obj("status" to "ok"))
        }
        get("/api/providers") { call.json(buildJsonArray { webProviders.forEach { p -> add(buildJsonObject {
            put("id",p.id); put("name",p.name); put("host",p.host); put("docs",p.docsUrl)
            put("keyNeed",p.keyNeed.name); put("hasKey",queries.secrets.get(p.id) != null); put("manualOnly",p.manualOnly);put("active",p.active)
            put("supports",JsonArray(p.supports.map { JsonPrimitive(it.name) }))
        }) } }) }
        get("/api/investigations") { call.json(buildJsonArray { store.list().sortedByDescending { it.investigation.updatedAt }.forEach { b -> add(buildJsonObject {
            put("investigation", wire.encodeToJsonElement(b.investigation)); put("targets", b.targets.size); put("findings", b.findings.size)
        }) } }) }
        post("/api/investigations") {
            val input = call.input()
            val title = input.text("title", 300).trim(); require(title.isNotEmpty()) { "title_required" }
            val b = InvestigationBundle(investigation = Investigation(title = title, description = input.text("description", 20000)))
            store.insert(b); call.json(wire.encodeToJsonElement(b))
        }
        post("/api/import") {
            val imported = JsonTransfer.decode(call.body(JsonTransfer.MAX_BYTES.toInt()))
            val b = imported.copy(queryRuns = imported.queryRuns.map {
                if (it.status.isFinal) it else it.copy(status = RunStatus.CANCELLED, completedAt = Time.nowIso(), errorCode = "imported_not_running")
            })
            store.insert(b); call.json(wire.encodeToJsonElement(b))
        }
        get("/api/investigations/{id}") {
            val b = store.get(call.parameters["id"]!!) ?: throw NoSuchElementException()
            call.json(wire.encodeToJsonElement(b))
        }
        post("/api/investigations/{id}/ai-analysis") {
            val b=store.get(call.parameters["id"]!!) ?: throw NoSuchElementException()
            localAi().analyze(b,call.input().strings("findingIds"));call.json(obj("status" to "started"),HttpStatusCode.Accepted)
        }
        get("/api/investigations/{id}/ai-analysis") {
            val b=store.get(call.parameters["id"]!!) ?: throw NoSuchElementException()
            val result=localAi().analysisStatus(b.investigation.id)
            // Flag stored assessments whose underlying findings were edited or deleted afterwards.
            val stale=result["fingerprint"]?.jsonPrimitive?.contentOrNull?.let { fp ->
                val ids=result["findingIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                runCatching { AiEvidencePolicy.fingerprint(AiEvidencePolicy.select(b,ids)) != fp }.getOrDefault(true)
            } ?: false
            call.json(JsonObject(result+("evidenceChanged" to JsonPrimitive(stale))))
        }
        post("/api/investigations/{id}/ai-analysis/cancel") {
            localAi().cancelAnalysis(call.parameters["id"]!!);call.json(obj("status" to "cancelled"))
        }
        patch("/api/investigations/{id}") {
            val input = call.input()
            val b = store.update(call.parameters["id"]!!) { b -> b.copy(investigation = b.investigation.copy(
                title = if ("title" in input) input.text("title",300).trim().also { require(it.isNotEmpty()) } else b.investigation.title,
                description = if ("description" in input) input.text("description",20000) else b.investigation.description,
                archivedAt = if ("archived" in input) { if (input.flag("archived")) Time.nowIso() else null } else b.investigation.archivedAt
            )) }
            call.json(wire.encodeToJsonElement(b))
        }
        delete("/api/investigations/{id}") {
            val id = call.parameters["id"]!!
            queries.delete(id); ai?.forget(id); call.json(obj("status" to "ok"))
        }
        post("/api/investigations/{id}/targets") {
            val id = call.parameters["id"]!!; val input = call.input(); val raw = input.text("value")
            val type = input.text("type").takeIf { it.isNotBlank() }?.let { TargetType.valueOf(it) } ?: TargetParser.detect(raw)
            val parsed = TargetParser.parse(type, raw)
            require(parsed is ParseResult.Ok) { "invalid_target" }
            // Do not retain or transmit secret-looking URL values in this shared web workspace.
            val safe = parsed.cleaned ?: parsed.normalized
            val target = Target(type = type, investigationId = id, rawValue = safe, normalizedValue = safe, displayValue = if (parsed.cleaned != null) safe else parsed.display)
            val b = store.update(id) { b ->
                require(b.investigation.archivedAt == null) { "archived" }
                require(b.targets.none { it.type == type && it.normalizedValue == safe }) { "target_exists" }
                b.copy(targets = b.targets + target)
            }
            call.json(buildJsonObject { put("bundle",wire.encodeToJsonElement(b)); put("warnings",JsonArray(parsed.warnings.map { JsonPrimitive(it.name) })) })
        }
        delete("/api/investigations/{id}/targets/{target}") {
            queries.deleteTarget(call.parameters["id"]!!, call.parameters["target"]!!)
            call.json(obj("status" to "ok"))
        }
        post("/api/investigations/{id}/query") {
            val id = call.parameters["id"]!!; val input = call.input(); val b = store.get(id) ?: throw NoSuchElementException()
            require(b.investigation.archivedAt == null) { "archived" }
            val target = b.targets.first { it.id == input.text("targetId") }
            val ids = input.strings("providers").toSet()
            val selected = webProviders.filter { it.id in ids && target.type in it.supports }
            require(selected.isNotEmpty() && selected.size == ids.size) { "select_sources" }
            val extra = (input["extra"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            require(selected.none { it.active } || input.flag("confirmActive")){"active_confirmation_required"}
            val language=input.text("language").takeIf { it in setOf("en","tr","de") } ?: "en"
            queries.start(id,target,selected,extra,language); call.json(obj("status" to "started"), HttpStatusCode.Accepted)
        }
        post("/api/investigations/{id}/cancel") {
            val b = store.get(call.parameters["id"]!!) ?: throw NoSuchElementException()
            val input = call.input()
            val targets = if(input.text("targetId").isBlank()) b.targets else listOf(b.targets.first { it.id==input.text("targetId") })
            targets.forEach { queries.cancel(it.id,input.text("providerId").takeIf { p -> p.isNotBlank() }) }; call.json(obj("status" to "cancelled"))
        }
        patch("/api/investigations/{id}/findings/{finding}") {
            val input = call.input(); val finding = call.parameters["finding"]!!
            val b = store.update(call.parameters["id"]!!) { b ->
                require(b.findings.any { it.id == finding }) { "not_found" }
                b.copy(findings = b.findings.map { f -> if (f.id != finding) f else f.copy(
                    favorite = if ("favorite" in input) input.flag("favorite") else f.favorite,
                    reviewStatus = if ("reviewStatus" in input) ReviewStatus.valueOf(input.text("reviewStatus")) else f.reviewStatus
                ) }, tags = if ("tags" in input) b.tags.filter { it.findingId != finding } + input.strings("tags").distinct().take(20).map { Tag(investigationId = b.investigation.id, findingId = finding, label = it.take(60)) } else b.tags)
            }; call.json(wire.encodeToJsonElement(b))
        }
        post("/api/investigations/{id}/notes") {
            val id = call.parameters["id"]!!; val input = call.input(); val content = input.text("content",20000).trim()
            require(content.isNotEmpty()) { "note_required" }
            val b = store.update(id) { b ->
                val finding = input.text("findingId").takeIf { it.isNotBlank() }
                require(finding == null || b.findings.any { it.id == finding })
                b.copy(notes = b.notes + Note(investigationId = id, findingId = finding, content = content))
            }; call.json(wire.encodeToJsonElement(b))
        }
        post("/api/investigations/{id}/relations") {
            val id = call.parameters["id"]!!; val input = call.input()
            val b = store.update(id) { b ->
                val nodes = b.targets.map { it.id }.toSet() + b.findings.map { it.id }
                val from = input.text("fromId"); val to = input.text("toId")
                require(from in nodes && to in nodes && from != to)
                val type = input.text("type",100).trim(); require(type.isNotEmpty())
                b.copy(relations = b.relations + Relation(investigationId = id, fromId = from, toId = to, type = type,
                    assertionType = AssertionType.INFERRED, note = input.text("note",20000)))
            }; call.json(wire.encodeToJsonElement(b))
        }
        get("/api/investigations/{id}/analysis") {
            val b = store.get(call.parameters["id"]!!) ?: throw NoSuchElementException()
            val analysis = Analyzer.analyze(b.targets,b.findings,b.queryRuns,b.relations,webProviders.filter { queries.secrets.get(it.id) != null }.map { it.id }.toSet())
            call.json(buildJsonObject {
                put("observations",buildJsonArray { analysis.observations.forEach { o -> add(buildJsonObject {
                    put("targetId", o.targetId); put("level",o.level.name); put("kind",o.kind.name)
                    put("args",JsonArray(o.args.map { JsonPrimitive(it) })); put("evidence",JsonArray(o.evidence.map { JsonPrimitive(it) }))
                }) } })
                put("timeline",buildJsonArray { analysis.timeline.forEach { e -> add(obj("at" to e.at,"targetId" to e.targetId,"providerId" to e.providerId,"label" to e.label,"value" to e.value,"findingId" to e.findingId)) } })
            })
        }
        get("/api/investigations/{id}/export/{format}") {
            val b = store.get(call.parameters["id"]!!) ?: throw NoSuchElementException()
            val format = call.parameters["format"]!!; require(format in setOf("json","csv"))
            call.response.header("Content-Disposition", "attachment; filename=\"osint-book-${b.investigation.id}.$format\"")
            if (format == "json") call.respondText(JsonTransfer.encode(b.copy(exportedAt = Time.nowIso(), appVersion = "0.2.0-web")),ContentType.Application.Json)
            else {
                val rows = b.findings.map { f -> mapOf(
                    Csv.Column.PROVIDER to f.providerId, Csv.Column.TARGET to (b.targets.firstOrNull { it.id == f.targetId }?.displayValue ?: ""),
                    Csv.Column.TYPE to f.type, Csv.Column.LABEL to f.label, Csv.Column.VALUE to f.value, Csv.Column.SOURCE_URL to f.sourceUrl,
                    Csv.Column.RETRIEVED_AT to f.retrievedAt, Csv.Column.PUBLISHED_AT to f.publishedAt.orEmpty(), Csv.Column.REVIEW to f.reviewStatus.name,
                    Csv.Column.FAVORITE to f.favorite.toString(), Csv.Column.NOTES to b.notes.filter { it.findingId == f.id }.joinToString(" | ") { it.content }
                ) }
                call.respondText(Csv.write(Csv.Column.entries, rows),ContentType.Text.CSV)
            }
        }
    }
}
private fun resource(name: String) = object {}.javaClass.getResourceAsStream("/static/$name")!!.bufferedReader().use { it.readText() }

fun main() {
    val host = System.getenv("OSINT_HOST") ?: "127.0.0.1"
    val dir = Path.of(System.getenv("OSINT_DATA") ?: "data")
    Files.createDirectories(dir)
    val hosts = mutableSetOf("localhost", "127.0.0.1", "::1")
    if (host != "127.0.0.1" && host != "::1") {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.forEach { hosts += it.hostAddress.substringBefore('%').lowercase() }
        System.getenv("COMPUTERNAME")?.let { hosts += it.lowercase() }
    }
    val store = Store(dir.resolve("osint-book.sqlite"))
    var username=System.getenv("OSINT_USERNAME").orEmpty()
    var password=System.getenv("OSINT_PASSWORD").orEmpty()
    val console=System.console()
    val ports=configurePorts(dir,System.getenv("OSINT_PORT"),System.getenv("OSINT_ADMIN_PORT"),console?.let { c -> { prompt -> c.readLine("%s",prompt) } })
    setupInitialUsers(store, username, password,
        console?.let { c -> { prompt -> c.readLine("%s", prompt) } },
        console?.let { c -> { prompt -> c.readPassword("%s", prompt)?.concatToString() } })
    val queries = Queries(store)
    val ai=LocalAi(dir,Path.of(System.getenv("OSINT_AI_RUNTIME") ?: "ai-runtime","llama-server.exe"),Path.of(System.getenv("OSINT_AI_RUNTIME_GPU") ?: "ai-runtime-gpu","llama-server.exe"))
    val access=Access(store)
    val server = embeddedServer(Netty, host = host, port = ports.user) { webModule(store, queries, password, hosts, username,Panel.USER,access,ai) }
    val admin = embeddedServer(Netty, host = "127.0.0.1", port = ports.admin) { webModule(store, queries, password, setOf("localhost","127.0.0.1"), username,Panel.ADMIN,access,ai) }
    Runtime.getRuntime().addShutdownHook(Thread { admin.stop(500,3000); server.stop(500,3000); ai.close(); queries.close(); store.close() })
    try {
        admin.start(wait=false)
        server.start(wait=false)
    } catch(e: Exception) {
        admin.stop(0,0);server.stop(0,0)
        System.err.println("Unable to start the selected ports. Check whether they are in use and edit data/ports.properties or set OSINT_PORT and OSINT_ADMIN_PORT.")
        throw e
    }
    println("User panel: http://localhost:${ports.user} | bind=$host")
    println("Admin panel (server computer only): http://localhost:${ports.admin}")
    println("Data: ${dir.toAbsolutePath()}")
    java.util.concurrent.CountDownLatch(1).await()
}
