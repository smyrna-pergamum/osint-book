package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A provider from https://github.com/OuterSpacee/free-ai-apis (or a local server) reachable through the OpenAI chat-completions API. */
@Serializable data class CloudPreset(val id: String, val name: String, val baseUrl: String, val model: String, val signupUrl: String,
    val keyRequired: Boolean = true, val freeTier: String = "", val local: Boolean = false)
@Serializable data class CloudProvider(val id: String, val name: String, val preset: String, val baseUrl: String, val model: String,
    val enabled: Boolean = true, val contextTokens: Int = 32768, val createdAt: String = Time.nowIso(),
    val maxOutputTokens: Int = 8192, val authHeader: String = "bearer", val headers: Map<String, String> = emptyMap())

/** Editable connection settings. authHeader: how the key is sent ("bearer" = Authorization: Bearer, "api-key", "x-api-key", "none"). */
@Serializable data class CloudSettings(val name: String = "", val baseUrl: String = "", val model: String = "", val contextTokens: Int = 32768,
    val maxOutputTokens: Int = 8192, val authHeader: String = "bearer", val headers: Map<String, String> = emptyMap()) {
    fun validated(): CloudSettings {
        require(model.isNotBlank() && model.length<=200) { "ai_cloud_model_required" }
        require(contextTokens in 2048..2_000_000 && maxOutputTokens in 256..131_072) { "ai_invalid_settings" }
        require(authHeader in setOf("bearer","api-key","x-api-key","none")) { "ai_invalid_settings" }
        // Extra headers are plain configuration (e.g. OpenRouter's HTTP-Referer); keys belong in the API key field.
        require(headers.size<=10 && headers.all { (k,v) -> Regex("[A-Za-z0-9-]{1,64}").matches(k) && k.lowercase() !in reservedHeaders &&
            v.length<=500 && v.none { it=='\r' || it=='\n' } }) { "ai_cloud_invalid_header" }
        return copy(name=name.trim().take(80),model=model.trim(),baseUrl=CloudAi.baseUrl(baseUrl))
    }
    companion object { private val reservedHeaders=setOf("authorization","api-key","x-api-key","host","content-length","content-type","cookie","user-agent","connection","transfer-encoding") }
}

object CloudPresets {
    /** Model IDs change often; administrators can pick a current one with "Fetch models". */
    val all = listOf(
        CloudPreset("google","Google AI Studio (Gemini)","https://generativelanguage.googleapis.com/v1beta/openai","gemini-2.5-flash","https://aistudio.google.com/apikey",freeTier="Free tier with daily request limits"),
        CloudPreset("groq","Groq","https://api.groq.com/openai/v1","openai/gpt-oss-120b","https://console.groq.com/keys",freeTier="Free tier, about 1,000 requests/day"),
        CloudPreset("cerebras","Cerebras","https://api.cerebras.ai/v1","llama-3.3-70b","https://cloud.cerebras.ai",freeTier="Free tier"),
        CloudPreset("openrouter","OpenRouter","https://openrouter.ai/api/v1","meta-llama/llama-3.3-70b-instruct:free","https://openrouter.ai/keys",freeTier="Models ending in :free cost nothing"),
        CloudPreset("huggingface","Hugging Face Inference","https://router.huggingface.co/v1","meta-llama/Llama-3.1-8B-Instruct","https://huggingface.co/settings/tokens",freeTier="Monthly free credits"),
        CloudPreset("mistral","Mistral AI","https://api.mistral.ai/v1","mistral-small-latest","https://console.mistral.ai/api-keys",freeTier="Free experiment plan"),
        CloudPreset("cohere","Cohere","https://api.cohere.ai/compatibility/v1","command-a-03-2025","https://dashboard.cohere.com/api-keys",freeTier="Rate-limited trial key"),
        CloudPreset("together","Together AI","https://api.together.xyz/v1","meta-llama/Llama-3.3-70B-Instruct-Turbo-Free","https://api.together.ai/settings/api-keys",freeTier="Free models and sign-up credit"),
        CloudPreset("fireworks","Fireworks AI","https://api.fireworks.ai/inference/v1","accounts/fireworks/models/llama-v3p3-70b-instruct","https://fireworks.ai/account/api-keys",freeTier="Sign-up credit"),
        CloudPreset("deepinfra","DeepInfra","https://api.deepinfra.com/v1/openai","meta-llama/Llama-3.3-70B-Instruct","https://deepinfra.com/dash/api_keys",freeTier="Sign-up credit"),
        CloudPreset("sambanova","SambaNova Cloud","https://api.sambanova.ai/v1","Meta-Llama-3.3-70B-Instruct","https://cloud.sambanova.ai/apis",freeTier="Free tier"),
        CloudPreset("novita","Novita AI","https://api.novita.ai/v3/openai","meta-llama/llama-3.3-70b-instruct","https://novita.ai/settings/key-management",freeTier="Sign-up credit"),
        CloudPreset("hyperbolic","Hyperbolic","https://api.hyperbolic.xyz/v1","meta-llama/Llama-3.3-70B-Instruct","https://app.hyperbolic.xyz/settings",freeTier="Free tier"),
        CloudPreset("aimlapi","AI/ML API","https://api.aimlapi.com/v1","meta-llama/Llama-3.3-70B-Instruct-Turbo","https://aimlapi.com/app/keys",freeTier="Free tier"),
        CloudPreset("deepseek","DeepSeek","https://api.deepseek.com/v1","deepseek-chat","https://platform.deepseek.com/api_keys",freeTier="Low-cost; sign-up credit"),
        CloudPreset("ollama","Ollama (local server)","http://localhost:11434/v1","llama3.2","https://ollama.com",keyRequired=false,freeTier="Runs on your own computer",local=true),
        CloudPreset("lmstudio","LM Studio (local server)","http://localhost:1234/v1","local-model","https://lmstudio.ai",keyRequired=false,freeTier="Runs on your own computer",local=true),
        CloudPreset("litellm","LiteLLM proxy","http://localhost:4000/v1","gpt-4o-mini","https://github.com/BerriAI/litellm",keyRequired=false,freeTier="Self-hosted proxy",local=true),
        CloudPreset("custom","Other OpenAI-compatible API","https://","","",keyRequired=false),
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: throw IllegalArgumentException("ai_cloud_invalid_preset")
}

/** An error code plus the provider's own explanation (for example "model decommissioned" or "quota exceeded"), shown to the user. */
class CloudError(code: String, val detail: String?) : IllegalArgumentException(code)

/** Cloud providers are configured by administrators; API keys stay encrypted in the server key vault and are never returned. */
class CloudAi(private val root: Path, private val secrets: Secrets,
              private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(10,TimeUnit.MINUTES).followRedirects(false).build()) {
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private val file=root.resolve("ai-cloud.json")
    private fun secretId(id: String)="ai_cloud_$id"

    companion object {
        /** HTTPS for internet services; plain HTTP only for this computer or private-network servers such as Ollama. */
        fun baseUrl(raw: String): String {
            val url=raw.trim().trimEnd('/').toHttpUrlOrNull() ?: throw IllegalArgumentException("ai_cloud_invalid_url")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) { "ai_cloud_invalid_url" }
            if(url.scheme=="http") require(privateHost(url.host)) { "ai_cloud_https_required" }
            return url.toString().trimEnd('/')
        }
        private fun privateHost(host: String): Boolean {
            if(host=="localhost") return true
            if(!Regex("[0-9.:a-fA-F]+").matches(host)) return false
            val address=runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            return address.isLoopbackAddress || address.isSiteLocalAddress
        }
        /** Models often wrap JSON in prose or code fences; keep the outermost object. */
        fun extractJson(content: String): String {
            val start=content.indexOf('{');val end=content.lastIndexOf('}')
            require(start>=0 && end>start) { "ai_invalid_output" }
            return content.substring(start,end+1)
        }
    }

    @Synchronized fun list(): List<CloudProvider> = runCatching { json.decodeFromString<List<CloudProvider>>(Files.readString(file)) }.getOrDefault(emptyList())
    @Synchronized private fun save(providers: List<CloudProvider>) {
        val tmp=root.resolve("ai-cloud.json.part");Files.writeString(tmp,json.encodeToString(providers))
        Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
    }
    fun get(id: String)=list().firstOrNull { it.id==id } ?: throw NoSuchElementException()
    fun hasKey(id: String)=secrets.get(secretId(id))!=null
    fun view(p: CloudProvider)=buildJsonObject {
        put("id",p.id);put("name",p.name);put("preset",p.preset);put("baseUrl",p.baseUrl);put("model",p.model);put("enabled",p.enabled)
        put("contextTokens",p.contextTokens);put("hasKey",hasKey(p.id));put("local",CloudPresets.all.firstOrNull { it.id==p.preset }?.local==true)
        put("maxOutputTokens",p.maxOutputTokens);put("authHeader",p.authHeader);put("headers",JsonObject(p.headers.mapValues { JsonPrimitive(it.value) }))
    }

    fun add(preset: String, name: String, baseUrl: String, model: String, apiKey: String, contextTokens: Int) =
        add(preset,CloudSettings(name,baseUrl,model,contextTokens),apiKey)
    @Synchronized fun add(preset: String, settings: CloudSettings, apiKey: String): CloudProvider {
        val pre=CloudPresets.get(preset)
        require(!pre.keyRequired || apiKey.isNotBlank() || settings.authHeader=="none") { "ai_cloud_key_required" }
        val s=settings.copy(baseUrl=settings.baseUrl.ifBlank { pre.baseUrl }).validated()
        val p=CloudProvider(UUID.randomUUID().toString(),s.name.ifBlank { pre.name },pre.id,s.baseUrl,s.model,true,s.contextTokens,
            maxOutputTokens=s.maxOutputTokens,authHeader=s.authHeader,headers=s.headers)
        if(apiKey.isNotBlank()) secrets.save(secretId(p.id),apiKey.trim())
        save(list()+p);return p
    }
    fun update(id: String, model: String?, enabled: Boolean?, apiKey: String?, contextTokens: Int?) {
        val c=get(id)
        update(id,if(model!=null || contextTokens!=null) CloudSettings(c.name,c.baseUrl,model ?: c.model,contextTokens ?: c.contextTokens,c.maxOutputTokens,c.authHeader,c.headers) else null,enabled,apiKey,false)
    }
    /** Every setting of a saved provider can change; an empty key keeps the stored one, clearKey removes it. */
    @Synchronized fun update(id: String, settings: CloudSettings?, enabled: Boolean?, apiKey: String?, clearKey: Boolean) {
        val current=get(id)
        val s=settings?.validated()
        if(clearKey) secrets.save(secretId(id),null)
        if(!apiKey.isNullOrBlank()) secrets.save(secretId(id),apiKey.trim())
        save(list().map { if(it.id!=id) it else current.copy(enabled=enabled ?: it.enabled).let { p -> if(s==null) p else p.copy(name=s.name.ifBlank { p.name },baseUrl=s.baseUrl,
            model=s.model,contextTokens=s.contextTokens,maxOutputTokens=s.maxOutputTokens,authHeader=s.authHeader,headers=s.headers) } })
    }
    @Synchronized fun delete(id: String) { get(id);secrets.save(secretId(id),null);save(list().filterNot { it.id==id }) }

    private fun request(base: String, path: String, key: String?, auth: String="bearer", extra: Map<String,String> = emptyMap()) =
        Request.Builder().url("$base/$path").header("User-Agent","OSINT-Book-Web/0.2.1").apply {
            if(!key.isNullOrBlank()) when(auth) { "bearer" -> header("Authorization","Bearer $key"); "api-key" -> header("api-key",key); "x-api-key" -> header("x-api-key",key) }
            extra.forEach { (k,v) -> header(k,v) }
        }
    private fun failure(code: Int)=when(code) { 401,403 -> "ai_cloud_key_rejected"; 404 -> "ai_cloud_model_not_found"; 429 -> "ai_cloud_rate_limited"; else -> "ai_cloud_http_$code" }
    /** OpenAI-style {"error":{"message":...}}, {"error":"..."} or {"message":...}; falls back to the start of the body. */
    private fun providerMessage(body: String): String? {
        val root=runCatching { json.parseToJsonElement(body) }.getOrNull()
        val err=(root as? JsonObject)?.get("error")
        val text=((err as? JsonObject)?.get("message") ?: err ?: (root as? JsonObject)?.get("message") ?: (root as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.get("error")?.let { e -> (e as? JsonObject)?.get("message") ?: e } }) as? JsonPrimitive
        return (text?.contentOrNull ?: body.takeIf { root==null && it.isNotBlank() })?.replace(Regex("\\s+")," ")?.take(300)
    }
    private fun fail(code: Int, body: String): Nothing = throw CloudError(failure(code),providerMessage(body))

    /** Lists model IDs from GET /models, with either a stored provider key or a key typed in the form. */
    fun models(baseUrl: String, apiKey: String?, providerId: String?, auth: String?=null, extra: Map<String,String>?=null): List<String> {
        val base=baseUrl(baseUrl);val key=apiKey?.takeIf { it.isNotBlank() } ?: providerId?.let { secrets.get(secretId(it)) }
        val saved=providerId?.let { id -> list().firstOrNull { it.id==id } }
        val a=auth ?: saved?.authHeader ?: "bearer";val h=extra ?: saved?.headers ?: emptyMap()
        try {
            client.newCall(request(base,"models",key,a,h).build()).execute().use { r ->
                val body=r.body!!.byteStream().use { it.readNBytes(4_000_001) }
                if(!r.isSuccessful) fail(r.code,body.toString(Charsets.UTF_8))
                require(body.size<=4_000_000) { "ai_hub_response_too_large" }
                val root=json.parseToJsonElement(body.toString(Charsets.UTF_8))
                val items=(root as? JsonObject)?.get("data") as? JsonArray ?: (root as? JsonObject)?.get("models") as? JsonArray ?: root as? JsonArray ?: JsonArray(emptyList())
                return items.mapNotNull { item -> ((item as? JsonObject)?.let { it["id"] ?: it["name"] } as? JsonPrimitive)?.contentOrNull?.removePrefix("models/") }.distinct().sorted().take(500)
            }
        } catch(e: java.io.IOException) { throw IllegalArgumentException("ai_cloud_unreachable") }
    }

    /** Chat models only: speech, embedding, image and guard models cannot write an analysis. */
    private fun suggestions(p: CloudProvider, key: String?): String? = runCatching { models(p.baseUrl,key,null,p.authHeader,p.headers) }.getOrNull()
        ?.filterNot { Regex("(?i)whisper|tts|embed|guard|orpheus|audio|image|vision|moderation|rerank").containsMatchIn(it) }
        ?.take(10)?.takeIf { it.isNotEmpty() }?.joinToString(", ",prefix="Available models: ")

    /** Sends a short prompt to confirm the key and model answer. Reasoning models need room to think, so the limit is generous. */
    fun test(id: String): String { probe(backend(id));return get(id).model }
    /** Tests settings typed in the form before they are saved; an empty key falls back to the saved provider's key. */
    fun testSettings(settings: CloudSettings, apiKey: String?, providerId: String?): String {
        val s=settings.validated()
        val key=apiKey?.takeIf { it.isNotBlank() } ?: providerId?.let { secrets.get(secretId(it)) }
        probe(CloudBackend(CloudProvider("test",s.name,"custom",s.baseUrl,s.model,true,s.contextTokens,maxOutputTokens=s.maxOutputTokens,authHeader=s.authHeader,headers=s.headers),key))
        return s.model
    }
    private fun probe(backend: CloudBackend) = kotlinx.coroutines.runBlocking {
        backend.generate(backend.render("You are a test endpoint. Respond only with JSON.","Return the JSON object {\"ok\": true}."),1024,JsonObject(emptyMap()))
    }

    fun backend(id: String): CloudBackend {
        val p=get(id);require(p.enabled) { "ai_cloud_disabled" }
        val preset=CloudPresets.all.firstOrNull { it.id==p.preset }
        val key=secrets.get(secretId(id))
        require(key!=null || preset?.keyRequired==false) { "ai_cloud_key_required" }
        return CloudBackend(p,key)
    }

    inner class CloudBackend(val provider: CloudProvider, private val key: String?) : LocalInferenceBackend {
        @Volatile private var call: Call?=null
        /** Messages travel as JSON so the chat API receives separate system and user roles. */
        override suspend fun render(system: String, user: String)=buildJsonArray {
            add(buildJsonObject { put("role","system");put("content",system) });add(buildJsonObject { put("role","user");put("content",user) })
        }.toString()
        /** Providers do not share a tokenizer API; about 3 characters per token is a conservative estimate for mixed languages. */
        override suspend fun tokenize(prompt: String)=prompt.length/3+64
        /** Hosted models have large windows, and reasoning models spend part of the output budget on thinking. */
        override val maxOutputTokens get()=provider.maxOutputTokens
        override suspend fun generate(prompt: String, maxOutputTokens: Int, schema: JsonObject, onToken: (Int) -> Unit): String=withContext(Dispatchers.IO) {
            val messages=json.parseToJsonElement(prompt)
            // Structured output: JSON schema where supported, then plain JSON mode, then no constraint.
            val formats=listOfNotNull(
                schema.takeIf { it.isNotEmpty() }?.let { buildJsonObject { put("type","json_schema");put("json_schema",buildJsonObject { put("name","osint_analysis");put("schema",it) }) } },
                buildJsonObject { put("type","json_object") }, null)
            fun send(format: JsonObject?): Pair<Int,String> {
                val body=buildJsonObject {
                    put("model",provider.model);put("messages",messages);put("temperature",0.1);put("max_tokens",maxOutputTokens)
                    format?.let { put("response_format",it) }
                }.toString().toRequestBody("application/json".toMediaType())
                val c=client.newCall(request(provider.baseUrl,"chat/completions",key,provider.authHeader,provider.headers).post(body).build());call=c
                try { c.execute().use { r -> return r.code to (r.body?.byteStream()?.use { it.readNBytes(8_000_000) }?.toString(Charsets.UTF_8).orEmpty()) } }
                catch(e: java.io.IOException) { ensureActive();throw IllegalArgumentException("ai_cloud_unreachable") }
                finally { call=null }
            }
            var code=0;var text=""
            for(format in formats) { val r=send(format);code=r.first;text=r.second;if(code!=400 && code!=422) break }
            if(code !in 200..299) {
                // A retired or unavailable model name: list what the provider offers now, so the fix is one click away.
                val msg=providerMessage(text)
                if(code==404 || msg?.contains(Regex("(?i)does not exist|decommission|model_not_found|unknown model|model not found"))==true)
                    throw CloudError("ai_cloud_model_not_found",listOfNotNull(msg,suggestions(provider,key)).joinToString(" "))
                fail(code,text)
            }
            val choice=runCatching { json.parseToJsonElement(text).jsonObject["choices"]!!.jsonArray.first().jsonObject }.getOrNull() ?: throw CloudError("ai_invalid_output",providerMessage(text))
            val content=choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            if(choice["finish_reason"]?.jsonPrimitive?.contentOrNull=="length") throw CloudError("ai_output_truncated",content.takeIf { it.isBlank() }?.let { "The model used the whole output limit (often for reasoning) without an answer." })
            if(content.isBlank()) throw CloudError("ai_invalid_output","The provider returned an empty answer.")
            onToken(content.length/4)
            runCatching { extractJson(content) }.getOrElse { throw CloudError("ai_invalid_output",content.take(200)) }
        }
        override fun cancel() { call?.cancel() }
    }
}
