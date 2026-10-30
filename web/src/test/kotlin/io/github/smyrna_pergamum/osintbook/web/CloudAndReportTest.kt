package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.nio.file.Files
import kotlin.test.*

class CloudAndReportTest {
    private fun bundle(): InvestigationBundle {
        val inv=Investigation(title="1.1.1.1 <review>")
        val t=Target(investigationId=inv.id,type=TargetType.IP,rawValue="1.1.1.1",normalizedValue="1.1.1.1",displayValue="1.1.1.1")
        fun f(p: String, label: String, value: String)=Finding(queryRunId="run",investigationId=inv.id,targetId=t.id,providerId=p,type="text",label=label,value=value,normalizedValue=value,sourceUrl="https://$p.example/1.1.1.1",retrievedAt="2026-10-29T09:00:00Z")
        val fs=listOf(f("ipwhois","city","Brisbane"),f("ipapico","city","Sydney"),f("geojs","asn","AS13335").copy(reviewStatus=ReviewStatus.VERIFIED))
        return InvestigationBundle(investigation=inv,targets=listOf(t),findings=fs,evidence=listOf(Evidence(findingId=fs[0].id,excerpt="x",sha256="abc",capturedAt="2026-10-29T09:00:00Z")))
    }

    @Test fun baseUrlRequiresHttpsExceptOnPrivateNetworks() {
        assertEquals("https://api.groq.com/openai/v1",CloudAi.baseUrl("https://api.groq.com/openai/v1/"))
        assertEquals("http://localhost:11434/v1",CloudAi.baseUrl("http://localhost:11434/v1"))
        assertEquals("http://192.168.1.5:11434/v1",CloudAi.baseUrl("http://192.168.1.5:11434/v1"))
        listOf("http://api.groq.com/v1","https://user:pw@x.com/v1","https://x.com/v1?k=1","ftp://x.com","not a url").forEach {
            assertFailsWith<IllegalArgumentException>(it) { CloudAi.baseUrl(it) }
        }
        assertEquals("{\"a\":1}",CloudAi.extractJson("Here you go:\n```json\n{\"a\":1}\n```"))
        assertTrue(CloudPresets.all.none { !it.local && it.baseUrl.startsWith("http://") })
    }

    @Test fun cloudProviderStoresKeyEncryptedAndRunsAnOpenAiCompatibleAnalysis() {
        val dir=Files.createTempDirectory("cloud")
        val store=Store(dir.resolve("db.sqlite"))
        MockWebServer().use { server ->
            val answer="""{"summary":"Two sources disagree on the city.","observations":[{"title":"City differs","text":"Geolocation differs","findingIds":["F1","F2"],"counterpoints":"Anycast network","confidence":"medium"}],"contradictions":[{"text":"Brisbane vs Sydney","findingIds":["F1","F2"]}],"missingInformation":["Owner"],"verificationSteps":["Check RDAP"],"risks":[{"category":"staleness","rating":"low","explanation":"Fresh"}]}"""
            // First call rejects JSON mode, the retry without it succeeds.
            server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
            server.enqueue(MockResponse().setBody(buildJsonObject { put("choices",buildJsonArray { add(buildJsonObject { put("finish_reason","stop");put("message",buildJsonObject { put("content","```json\n$answer\n```") }) }) }) }.toString()))
            val cloud=CloudAi(dir,Secrets(store))
            val p=cloud.add("litellm","Test","http://127.0.0.1:${server.port}/v1","test-model","secret-key",32768)
            assertTrue(cloud.hasKey(p.id))
            assertFalse(Files.readString(dir.resolve("ai-cloud.json")).contains("secret-key"))
            val b=bundle()
            val out=kotlinx.coroutines.runBlocking { AiEvaluator(cloud.backend(p.id)).run(b,b.findings.map { it.id },32768,"tr") }
            // References follow the target/attribute/provider order: F1 = geojs asn, F2 = ipapico city.
            assertEquals(listOf(b.findings[2].id,b.findings[1].id),out.observations.single().findingIds)
            assertEquals("medium",out.observations.single().confidence)
            assertEquals("staleness",out.risks.single().category)
            server.takeRequest()
            val req=server.takeRequest()
            assertEquals("Bearer secret-key",req.getHeader("Authorization"))
            val body=Json.parseToJsonElement(req.body.readUtf8()).jsonObject
            assertEquals("test-model",body["model"]!!.jsonPrimitive.content)
            assertTrue(body["messages"].toString().contains("write every text value in Turkish"))
            cloud.delete(p.id)
            assertFalse(cloud.hasKey(p.id))
        }
        store.close()
    }

    @Test fun largeSelectionsAreSplitIntoPartsAndMerged() = kotlinx.coroutines.runBlocking {
        val b=bundle();val calls=mutableListOf<Int>()
        val backend=object : LocalInferenceBackend {
            var findings=0
            override suspend fun render(system: String, user: String): String { findings=Regex("\"ref\":\"F\\d+\"").findAll(user).count();return user }
            // More than one finding does not fit: forces the split down to single findings.
            override suspend fun tokenize(prompt: String)=if(prompt.startsWith("Part ")) 50 else if(findings>1) 5000 else 100
            override suspend fun generate(prompt: String, maxOutputTokens: Int, schema: JsonObject, onToken: (Int) -> Unit): String {
                if(prompt.startsWith("Part ")) return """{"summary":"Merged summary"}"""
                calls+=findings
                return """{"summary":"Part","observations":[{"title":"T","text":"Obs ${calls.size}","findingIds":["F1"],"counterpoints":"","confidence":"low"}],"contradictions":[],"missingInformation":["Same"],"verificationSteps":[],"risks":[{"category":"coverage","rating":"${if(calls.size==2) "high" else "low"}","explanation":"x"}]}"""
            }
        }
        val phases=mutableListOf<String>()
        val out=AiEvaluator(backend).run(b,b.findings.map { it.id },4096,"en") { p,_ -> phases+=p }
        assertEquals(3,out.parts);assertEquals(listOf(1,1,1),calls)
        assertEquals("Merged summary",out.summary)
        assertEquals(3,out.observations.size);assertEquals(listOf("Same"),out.missingInformation)
        assertEquals("high",out.risks.single().rating)
        assertTrue("merging" in phases)
        // Every observation still points at a real finding of this investigation.
        assertTrue(out.observations.all { o -> o.findingIds.all { id -> b.findings.any { it.id==id } } })
    }

    @Test fun providerErrorMessagesReachTheUser() {
        val dir=Files.createTempDirectory("cloud-err");val store=Store(dir.resolve("db.sqlite"))
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"The model `old-model` has been decommissioned.","type":"invalid_request_error"}}""")) }
            val cloud=CloudAi(dir,Secrets(store))
            val p=cloud.add("litellm","Test","http://127.0.0.1:${server.port}/v1","old-model","k",32768)
            val e=assertFailsWith<CloudError> { cloud.test(p.id) }
            assertEquals("ai_cloud_http_400",e.message);assertEquals("The model `old-model` has been decommissioned.",e.detail)
        }
        store.close()
    }

    @Test fun reportFollowsTheSchemaAndEscapesUntrustedText() {
        val dir=Files.createTempDirectory("report")
        val reports=Reports(dir) { it }
        val b=bundle()
        val saved=reports.save(b.investigation.id,ReportSettings(researchQuestion="Where is 1.1.1.1 hosted?",confidence="medium",decision="approved",verifier="analyst"))
        assertEquals(1,saved.number);assertNotNull(saved.verifiedAt)
        val analysis=buildJsonObject {
            put("state","complete");put("findingIds",JsonArray(b.findings.map { JsonPrimitive(it.id) }))
            put("output",Json.encodeToJsonElement(AiOutput("Summary <script>",listOf(AiObservation("Interpretation",listOf(b.findings[0].id,b.findings[2].id),"Title","Alt","high")),
                emptyList(),listOf("Owner"),listOf("Check RDAP"),0,listOf(AiRisk("coverage","high","Few sources")))))
        }
        val d=reports.data(b,analysis,"tr")
        assertEquals(setOf("report","target","scope","metrics","sources","findings","quality","risks","recommendations","review"),d.keys)
        assertEquals("RPR-0001",d["report"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("public_entity",d["target"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val sources=d["sources"]!!.jsonArray
        assertEquals(3,sources.size);assertEquals("K-001",sources[0].jsonObject["id"]!!.jsonPrimitive.content)
        val finding=d["findings"]!!.jsonArray.single().jsonObject
        assertEquals("B-01",finding["id"]!!.jsonPrimitive.content)
        assertEquals(2,finding["source_ids"]!!.jsonArray.size)
        assertEquals("pending",finding["verification_status"]!!.jsonPrimitive.content)
        assertEquals(3,d["risks"]!!.jsonArray.size)
        val html=reports.html(d,"tr")
        assertFalse(html.contains("<script>"));assertTrue(html.contains("Summary &lt;script&gt;"))
        assertTrue(html.contains("lang=\"tr\""))
        assertTrue(reports.html(d,"ar").contains("dir=\"rtl\""))
        // Without an AI analysis the findings are still listed, each with its own source.
        assertEquals(3,reports.data(b,null,"en")["findings"]!!.jsonArray.size)
    }

    @Test fun interfaceScriptOffersNineLanguages() {
        assertEquals(9,I18n.languages.size)
        assertTrue(I18n.script.contains("function tr(key)"))
        assertEquals("ai_unknown_code",I18n.t("tr","ai_unknown_code"))
        assertNotEquals("ai_model_not_loaded",I18n.t("en","ai_model_not_loaded"))
    }
}
