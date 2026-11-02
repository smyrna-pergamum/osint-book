package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.*
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*

class QueueAndLimitsTest {
    private fun bundle(title: String = "inv", values: List<String> = listOf("Brisbane","Sydney","AS13335")): InvestigationBundle {
        val inv=Investigation(title=title)
        val t=Target(investigationId=inv.id,type=TargetType.IP,rawValue="1.1.1.1",normalizedValue="1.1.1.1",displayValue="1.1.1.1")
        val fs=values.mapIndexed { i,v -> Finding(queryRunId="run",investigationId=inv.id,targetId=t.id,providerId="p$i",type="text",label="city",value=v,normalizedValue=v,sourceUrl="https://x/$i",retrievedAt="2026-10-29T09:00:00Z") }
        return InvestigationBundle(investigation=inv,targets=listOf(t),findings=fs)
    }
    private val answer="""{"summary":"S","observations":[{"title":"T","text":"Obs","findingIds":["F1"],"counterpoints":"","confidence":"low"}],"contradictions":[],"missingInformation":[],"verificationSteps":[],"risks":[]}"""
    private fun chat(content: String=answer)=MockResponse().setBody(buildJsonObject { put("choices",buildJsonArray { add(buildJsonObject { put("finish_reason","stop");put("message",buildJsonObject { put("content",content) }) }) }) }.toString())

    @Test fun rateWindowWaitsForTokensAndRequestsPerMinute() {
        val w=RateWindow()
        assertEquals(0,w.reserve(4000,7000,30,now=0))
        assertTrue(w.reserve(4000,7000,30,now=1_000)>0)          // would exceed 7,000 tokens in the minute
        assertEquals(0,w.reserve(4000,7000,30,now=61_000))      // the first request left the window
        val r=RateWindow()
        assertEquals(0,r.reserve(10,0,1,now=0))
        assertEquals(30,r.reserve(10,0,1,now=31_000))           // one request per minute: wait 30 s
        assertEquals(0,RateWindow().reserve(50_000,7000,0,now=0)) // a single oversized request is left to the provider (413)
    }

    @Test fun tooLargeErrorTeachesTheLimitAndTheSelectionIsSplit() {
        val dir=Files.createTempDirectory("limits");val store=Store(dir.resolve("db.sqlite"))
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(413).setBody("""{"error":{"message":"Request too large for model `qwen/qwen3.8-27b` on tokens per minute (TPM): Limit 7000, Requested 11730, please reduce your message size and try again."}}"""))
            repeat(6) { server.enqueue(chat()) }
            val cloud=CloudAi(dir,Secrets(store))
            val p=cloud.add("litellm","Groq-like","http://127.0.0.1:${server.port}/v1","m","k",32768)
            val backend=cloud.backend(p.id)
            val b=bundle()
            val out=kotlinx.coroutines.runBlocking { AiEvaluator(backend).run(b,b.findings.map { it.id },32768,"en") }
            assertEquals("S",out.summary)
            assertEquals(6300,backend.requestTokenLimit)
            server.takeRequest()
            val retry=Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(retry["max_tokens"]!!.jsonPrimitive.int<6300,"output budget now fits the learned limit")
        }
        store.close()
    }

    @Test fun rateLimitedRequestsWaitAndRetry() {
        val dir=Files.createTempDirectory("retry");val store=Store(dir.resolve("db.sqlite"))
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Rate limit reached. Please try again in 1.2s."}}"""))
            server.enqueue(chat())
            val cloud=CloudAi(dir,Secrets(store))
            val p=cloud.add("litellm","RL","http://127.0.0.1:${server.port}/v1","m","k",32768)
            val waits=mutableListOf<Int>()
            val backend=cloud.backend(p.id)
            val b=bundle(values=listOf("Brisbane"))
            val out=kotlinx.coroutines.runBlocking { AiEvaluator(backend).run(b,b.findings.map { it.id },32768,"en") { phase,n -> if(phase=="waiting") waits+=n } }
            assertEquals("S",out.summary);assertEquals(listOf(2),waits)
        }
        store.close()
    }

    @Test fun aFindingThatNeverFitsIsSkippedInsteadOfFailingTheAnalysis() = kotlinx.coroutines.runBlocking {
        val b=bundle(values=listOf("Brisbane","HUGE","Sydney"))
        val backend=object : LocalInferenceBackend {
            override suspend fun render(system: String, user: String)=user
            override suspend fun tokenize(prompt: String)=if(prompt.contains("HUGE")) 99_999 else 200
            override suspend fun generate(prompt: String, maxOutputTokens: Int, schema: JsonObject, onToken: (Int) -> Unit)=
                if(prompt.startsWith("Part ")) """{"summary":"Merged"}""" else answer
        }
        val out=AiEvaluator(backend).run(b,b.findings.map { it.id },2048,"en")
        assertEquals(listOf(b.findings[1].id),out.skippedFindingIds)
        assertTrue(out.observations.isNotEmpty())
    }

    @Test fun analysesAreQueuedAndRunOneAfterAnother() {
        val dir=Files.createTempDirectory("queue");val store=Store(dir.resolve("db.sqlite"))
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(chat().setBodyDelay(1500,TimeUnit.MILLISECONDS)) }
            val cloud=CloudAi(dir,Secrets(store))
            val p=cloud.add("litellm","Slow","http://127.0.0.1:${server.port}/v1","m","k",32768)
            val ai=LocalAi(dir,dir.resolve("none/llama-server.exe"),dir.resolve("none-gpu/llama-server.exe"))
            val first=bundle("first",listOf("A"));val second=bundle("second",listOf("B"))
            ai.analyze(first,first.findings.map { it.id },LocalAi.Engine.Cloud(cloud.backend(p.id)))
            ai.analyze(second,second.findings.map { it.id },LocalAi.Engine.Cloud(cloud.backend(p.id)))
            Thread.sleep(400)
            assertEquals("queued",ai.analysisStatus(second.investigation.id)["state"]!!.jsonPrimitive.content)
            assertEquals(1,ai.analysisStatus(second.investigation.id)["position"]!!.jsonPrimitive.int)
            val deadline=System.currentTimeMillis()+15_000
            while(ai.analysisStatus(second.investigation.id)["state"]!!.jsonPrimitive.content!="complete" && System.currentTimeMillis()<deadline) Thread.sleep(200)
            assertEquals("complete",ai.analysisStatus(first.investigation.id)["state"]!!.jsonPrimitive.content)
            assertEquals("complete",ai.analysisStatus(second.investigation.id)["state"]!!.jsonPrimitive.content)
            ai.close()
        }
        store.close()
    }
}
