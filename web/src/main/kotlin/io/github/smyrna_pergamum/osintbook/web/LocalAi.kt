package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.Time
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.*
import java.security.MessageDigest
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable data class LocalModel(val id:String,val repository:String,val revision:String,val filename:String,val size:Long,val sha256:String,val downloadedAt:String?=null)
@Serializable data class EngineSettings(val modelId:String,val backend:String="cpu",val context:Int=8192,val threads:Int=0)

/** One managed llama.cpp process (CPU or Vulkan GPU build shipped with the package) and one download at a time.
 *  Executables are never user supplied; the engine listens on 127.0.0.1 with a random port and API key. */
class LocalAi(private val root: Path, private val cpuRuntime: Path, private val gpuRuntime: Path, private val hub: HuggingFaceHub = HuggingFaceHub()) : AutoCloseable {
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private val dir=root.resolve("models")
    private val analysisDir=root.resolve("ai-analyses")
    private val activeFile=root.resolve("ai-active.json")
    private val logFile=root.resolve("ai-engine.log")
    private val pidFile=root.resolve("ai-engine.pid")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val downloadClient=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(0,TimeUnit.SECONDS).build()
    private val engineClient=OkHttpClient.Builder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
    // Prompt processing of a large selection on a CPU can take minutes before the first token arrives.
    private val generateClient=engineClient.newBuilder().readTimeout(20,TimeUnit.MINUTES).build()
    @Volatile private var downloadCall:Call?=null
    @Volatile private var downloadJob:Job?=null
    @Volatile private var download:JsonObject=buildJsonObject { put("state","idle") }
    @Volatile private var process:Process?=null
    @Volatile private var active:LocalModel?=null
    @Volatile private var settings:EngineSettings?=null
    @Volatile private var engine:JsonObject=buildJsonObject { put("state","idle") }
    @Volatile private var loadJob:Job?=null
    @Volatile private var analysisJob:Job?=null
    @Volatile private var analysisInvestigation:String?=null
    @Volatile private var generateCall:Call?=null
    private val analyses=ConcurrentHashMap<String,JsonObject>()
    @Volatile private var devices:List<GpuDevice>?=null
    private var port=0
    private var token=""

    init {
        Files.createDirectories(dir);Files.createDirectories(analysisDir)
        Files.list(dir).use { s -> s.filter { it.fileName.toString().endsWith(".part") }.toList() }.forEach { Files.deleteIfExists(it) }
        // A killed server (Task Manager, crash) cannot stop its engine; stop that leftover llama-server before starting a new one.
        runCatching { Files.readString(pidFile).trim().toLong() }.getOrNull()?.let { pid ->
            val runtimes=listOf(cpuRuntime,gpuRuntime).map { it.toAbsolutePath().normalize().toString().lowercase() }
            ProcessHandle.of(pid).filter { h -> h.info().command().map { it.lowercase() in runtimes }.orElse(false) }.ifPresent { it.destroyForcibly() }
            Files.deleteIfExists(pidFile)
        }
        // Restore the model that was active when the server last stopped. Works offline.
        runCatching { json.decodeFromString<EngineSettings>(Files.readString(activeFile)) }.getOrNull()?.let { saved ->
            runCatching { activate(saved,persist=false) }.onFailure { engine=failed(it.message ?: "ai_model_load_failed") }
        }
    }

    // ---- Hardware ----
    val cpuAvailable get()=Files.isRegularFile(cpuRuntime)
    val gpuAvailable get()=Files.isRegularFile(gpuRuntime)
    fun gpus():List<GpuDevice> = devices ?: synchronized(this) { devices ?: probeDevices().also { devices=it } }
    private fun probeDevices():List<GpuDevice> {
        if(!gpuAvailable) return emptyList()
        return runCatching {
            val p=ProcessBuilder(gpuRuntime.toAbsolutePath().toString(),"--list-devices").directory(gpuRuntime.toAbsolutePath().parent.toFile()).redirectErrorStream(true).start()
            val out=scope.async { p.inputStream.bufferedReader().readText() }
            if(!p.waitFor(30,TimeUnit.SECONDS)){p.destroyForcibly();return emptyList()}
            AiHostInfo.parseDevices(runBlocking { out.await() })
        }.getOrDefault(emptyList())
    }
    fun hardware()=AiHostInfo.snapshot(gpus(),cpuAvailable,gpuAvailable)
    fun defaultThreads()=(Runtime.getRuntime().availableProcessors()/2).coerceAtLeast(1)

    /** Curated models with live Hugging Face size, popularity and a fit estimate for this server. */
    suspend fun recommendations():JsonArray = coroutineScope {
        val ram=AiHostInfo.totalRam();val gpus=gpus()
        val rows=AiCatalog.models.map { m -> async(Dispatchers.IO) { m to runCatching { hub.info(m.repository) } } }.awaitAll()
        val sized=rows.map { (m,info) -> Triple(m,info.getOrNull(),info.getOrNull()?.get("files")?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["filename"]?.jsonPrimitive?.content==m.filename }) }
        val sizes=sized.map { (_,_,file) -> file?.get("size")?.jsonPrimitive?.longOrNull }
        val fits=sizes.map { it?.let { size -> AiHostInfo.fit(size,ram,gpus) } }
        val best=AiHostInfo.recommend(AiCatalog.models,sizes,ram,gpus)
        val installed=installed().map { it.repository to it.filename }.toSet()
        buildJsonArray { sized.forEachIndexed { i,(m,info,file) -> add(buildJsonObject {
            put("repository",m.repository);put("filename",m.filename);put("title",m.title);put("parameters",m.parameters);put("note",m.note)
            put("quantization",AiCatalog.quantization(m.filename))
            put("size",file?.get("size") ?: JsonNull);put("downloads",info?.get("downloads") ?: JsonNull);put("likes",info?.get("likes") ?: JsonNull)
            put("license",info?.get("license") ?: JsonNull);put("fit",fits[i]?.let(::JsonPrimitive) ?: JsonNull);put("recommended",i==best);put("installed",(m.repository to m.filename) in installed)
            put("available",file?.get("downloadable")?.jsonPrimitive?.booleanOrNull==true)
            if(info==null) put("error",rows[i].second.exceptionOrNull()?.message ?: "ai_hub_unavailable")
        }) } }
    }
    fun repository(id:String):JsonObject {
        val info=hub.info(id);val ram=AiHostInfo.totalRam();val gpus=gpus()
        val installed=installed().filter { it.repository==info["repository"]!!.jsonPrimitive.content }.map { it.filename }.toSet()
        return JsonObject(info+("files" to JsonArray(info["files"]!!.jsonArray.sortedBy { it.jsonObject["size"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE }.map { f -> val o=f.jsonObject
            JsonObject(o+mapOf("fit" to (o["size"]?.jsonPrimitive?.longOrNull?.let { JsonPrimitive(AiHostInfo.fit(it,ram,gpus)) } ?: JsonNull),
                "installed" to JsonPrimitive(o["filename"]!!.jsonPrimitive.content in installed))) })))
    }

    // ---- Models ----
    fun downloadStatus():JsonObject=download
    fun installed():List<LocalModel> = Files.list(dir).use { stream -> stream.filter { it.fileName.toString().endsWith(".json") }.toList() }
        .mapNotNull { runCatching { json.decodeFromString<LocalModel>(Files.readString(it)) }.getOrNull() }
        .filter { Files.isRegularFile(idPath(it.id,"gguf")) }.sortedBy { it.size }
    fun activeModel():LocalModel?=active?.takeIf { process?.isAlive==true }
    fun engineStatus():JsonObject {
        val p=process
        if(engine["state"]?.jsonPrimitive?.content=="ready" && p?.isAlive!=true) engine=failed("ai_engine_stopped")
        return engine
    }
    fun activeSettings()=settings
    private fun idPath(id:String,ext:String):Path { require(Regex("[a-f0-9]{64}").matches(id)){"ai_invalid_model"};return dir.resolve("$id.$ext") }
    @Synchronized fun download(repo:String, filename:String) {
        require(downloadJob?.isCompleted!=false){"ai_download_running"}
        val repository=HuggingFaceHub.repository(repo)
        require(filename.length<=240 && HuggingFaceHub.downloadable(filename)) {"ai_unsupported_file"}
        download=buildJsonObject {put("state","preparing");put("repository",repository);put("filename",filename)}
        downloadJob=scope.launch {
            var part:Path?=null
            try {
                val info=hub.info(repository);val rev=info.getValue("revision").jsonPrimitive.content
                require(info["gated"]?.jsonPrimitive?.booleanOrNull!=true){"ai_repository_access_denied"}
                val file=info.getValue("files").jsonArray.map { it.jsonObject }.firstOrNull { it["filename"]?.jsonPrimitive?.content==filename } ?: throw IllegalArgumentException("ai_file_missing")
                val size=file["size"]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("ai_size_unavailable")
                val sha=file["sha256"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("ai_hash_unavailable")
                require(size>0 && Regex("[a-f0-9]{64}").matches(sha)){"ai_invalid_metadata"}
                val id=digest("$repository\n$rev\n$filename".toByteArray());val dest=idPath(id,"gguf");val manifest=idPath(id,"json");part=idPath(id,"part")
                if(Files.exists(dest) && Files.exists(manifest)){download=buildJsonObject {put("state","complete");put("id",id);put("filename",filename);put("alreadyInstalled",true)};return@launch}
                require(Files.getFileStore(dir).usableSpace > size+512L*1024*1024){"ai_insufficient_disk"}
                val url=HttpUrl.Builder().scheme("https").host("huggingface.co").addPathSegments(repository).addPathSegment("resolve").addPathSegment(rev).addPathSegment(filename).build()
                val call=downloadClient.newCall(Request.Builder().url(url).header("User-Agent","OSINT-Book-Web/0.3").build());downloadCall=call
                val started=System.nanoTime()
                call.execute().use { resp ->
                    when(resp.code){401,403->throw IllegalArgumentException("ai_repository_access_denied");404->throw IllegalArgumentException("ai_file_missing");429->throw IllegalArgumentException("ai_hub_rate_limited")}
                    require(resp.isSuccessful){"ai_download_http_${resp.code}"}
                    val body=resp.body ?: throw IllegalArgumentException("ai_download_failed")
                    val digest=MessageDigest.getInstance("SHA-256");var count=0L;var reported=0L
                    Files.newOutputStream(part,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING).use { out -> body.byteStream().use { input ->
                        val bytes=ByteArray(262144)
                        while(true){
                            ensureActive();val n=input.read(bytes);if(n<0)break
                            count+=n;require(count<=size){"ai_size_mismatch"};out.write(bytes,0,n);digest.update(bytes,0,n)
                            if(count-reported>=4L*1024*1024 || count==size){
                                reported=count;val seconds=(System.nanoTime()-started)/1e9
                                download=buildJsonObject {put("state","downloading");put("repository",repository);put("filename",filename);put("downloaded",count);put("total",size);put("bytesPerSecond",if(seconds>0)(count/seconds).toLong() else 0)}
                            }
                        }
                    } }
                    require(count==size){"ai_size_mismatch"}
                    download=buildJsonObject {put("state","verifying");put("filename",filename);put("downloaded",count);put("total",size)}
                    require(digest.digest().joinToString(""){"%02x".format(it)}==sha){"ai_hash_mismatch"}
                }
                ensureActive(); Files.move(part,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);part=null
                val model=LocalModel(id,repository,rev,filename,size,sha,Time.nowIso());val tmp=idPath(id,"manifest-part")
                Files.writeString(tmp,json.encodeToString(model));Files.move(tmp,manifest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                download=buildJsonObject {put("state","complete");put("id",id);put("filename",filename)}
            } catch(e:CancellationException){download=buildJsonObject {put("state","cancelled");put("filename",filename)}}
            catch(e:java.io.IOException){download=buildJsonObject {put("state",if(downloadCall?.isCanceled()==true)"cancelled" else "failed");put("filename",filename);put("error","ai_download_interrupted")}}
            catch(e:Exception){download=buildJsonObject {put("state","failed");put("filename",filename);put("error",e.message?.take(120)?:"ai_download_failed")}}
            finally {downloadCall=null;part?.let { runCatching { Files.deleteIfExists(it) } }}
        }
    }
    @Synchronized fun cancelDownload(){downloadCall?.cancel();downloadJob?.cancel()}
    @Synchronized fun delete(id:String) {
        require(active?.id!=id && settings?.modelId!=id){"ai_unload_first"}
        val model=installed().firstOrNull {it.id==id} ?: throw NoSuchElementException()
        Files.deleteIfExists(idPath(model.id,"json"));Files.deleteIfExists(idPath(model.id,"gguf"))
    }

    // ---- Engine ----
    private fun failed(error:String,detail:String?=null)=buildJsonObject {put("state","failed");put("error",error);detail?.let { put("detail",it) }}
    @Synchronized fun unload(){
        require(analysisJob?.isCompleted!=false){"ai_analysis_running"}
        loadJob?.cancel();stopProcess();settings=null;engine=buildJsonObject { put("state","idle") }
        Files.deleteIfExists(activeFile)
    }
    private fun stopProcess(){process?.let { it.destroy();if(!it.waitFor(5,TimeUnit.SECONDS)) it.destroyForcibly();runCatching { Files.deleteIfExists(pidFile) } };process=null;active=null}
    /** Validates synchronously, then loads in the background; poll [engineStatus]. */
    @Synchronized fun activate(request:EngineSettings,persist:Boolean=true) {
        require(analysisJob?.isCompleted!=false){"ai_analysis_running"}
        val s=request.copy(threads=request.threads.takeIf { it>0 } ?: defaultThreads())
        require(s.backend in setOf("cpu","gpu") && s.context in 1024..32768 && s.threads in 1..Runtime.getRuntime().availableProcessors()){"ai_invalid_settings"}
        val model=installed().firstOrNull {it.id==s.modelId} ?: throw NoSuchElementException()
        val file=idPath(model.id,"gguf");require(Files.size(file)==model.size){"ai_model_incomplete"}
        val runtime=if(s.backend=="gpu") gpuRuntime else cpuRuntime
        require(Files.isRegularFile(runtime)){"ai_runtime_missing"}
        if(s.backend=="gpu") require(gpus().isNotEmpty()){"ai_gpu_unavailable"}
        val replacing=process!=null
        loadJob?.cancel();stopProcess()
        // Weights are memory-mapped, so Windows can reclaim cache for them; require the weights plus KV cache headroom to be free.
        // Windows releases a stopped engine's memory with a short delay.
        if(s.backend=="cpu") {
            val need=model.size+384L*1024*1024
            var free=AiHostInfo.availableRam()
            repeat(if(replacing) 10 else 0) { if(free!=null && free!! <= need){Thread.sleep(500);free=AiHostInfo.availableRam()} }
            if(free!=null && free!! <= need){settings=null;engine=failed("ai_memory_insufficient");Files.deleteIfExists(activeFile);throw IllegalArgumentException("ai_memory_insufficient")}
        }
        settings=s;engine=buildJsonObject {put("state","loading");put("modelId",model.id);put("backend",s.backend)}
        if(persist) Files.writeString(activeFile,json.encodeToString(s))
        loadJob=scope.launch {
            val newPort=ServerSocket(0).use { it.localPort };val newToken=UUID.randomUUID().toString()+UUID.randomUUID().toString()
            val args=mutableListOf(runtime.toAbsolutePath().toString(),"-m",file.toAbsolutePath().toString(),"--host","127.0.0.1","--port",newPort.toString(),
                "--api-key",newToken,"--no-webui","-c",s.context.toString(),"-t",s.threads.toString(),"-tb",s.threads.toString(),"-np","1","--reasoning","off")
            // GPU: llama.cpp fits as many layers as GPU memory allows and keeps the rest on the CPU.
            if(s.backend=="cpu") args+=listOf("-ngl","0")
            val started=ProcessBuilder(args).directory(runtime.toAbsolutePath().parent.toFile()).redirectErrorStream(true).redirectOutput(logFile.toFile()).start()
            synchronized(this@LocalAi){process=started}
            runCatching { Files.writeString(pidFile,started.pid().toString()) }
            try {
                val deadline=System.currentTimeMillis()+15*60_000
                var ready=false
                while(!ready){
                    ensureActive()
                    if(!started.isAlive) throw IllegalStateException("ai_model_load_failed")
                    require(System.currentTimeMillis()<deadline){"ai_model_load_timeout"}
                    delay(500)
                    ready=runCatching { engineClient.newCall(Request.Builder().url("http://127.0.0.1:$newPort/health").header("Authorization","Bearer $newToken").build()).execute().use {it.isSuccessful} }.getOrDefault(false)
                }
                synchronized(this@LocalAi){port=newPort;token=newToken;active=model
                    engine=buildJsonObject {put("state","ready");put("modelId",model.id);put("backend",s.backend)}}
            } catch(e:CancellationException){started.destroyForcibly();throw e}
            catch(e:Exception){
                started.destroyForcibly();started.waitFor(5,TimeUnit.SECONDS)
                synchronized(this@LocalAi){if(process===started){process=null;active=null};engine=failed(e.message ?: "ai_model_load_failed",logReason())}
            }
        }
    }
    /** The most relevant engine log lines, for example an out-of-memory or unsupported architecture message. */
    private fun logReason():String? = runCatching {
        val lines=Files.readAllLines(logFile).takeLast(200)
        lines.filter { Regex("(?i)error|failed|unknown model architecture|out of memory|unable|exception").containsMatchIn(it) }.takeLast(3).joinToString(" | ").take(400).ifBlank { null }
    }.getOrNull()

    private fun post(endpoint:String, payload:JsonObject):JsonObject {
        require(process?.isAlive==true){"ai_model_not_loaded"}
        val request=Request.Builder().url("http://127.0.0.1:$port/$endpoint").header("Authorization","Bearer $token").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return engineClient.newCall(request).execute().use { r -> require(r.isSuccessful){"ai_engine_error"}; val body=r.body ?: throw IllegalArgumentException("ai_engine_error");json.parseToJsonElement(body.string()).jsonObject }
    }
    private inner class LlamaBackend : LocalInferenceBackend {
        override suspend fun render(system:String,user:String)=withContext(Dispatchers.IO){
            val messages=buildJsonArray {add(buildJsonObject {put("role","system");put("content",system)});add(buildJsonObject {put("role","user");put("content",user)})}
            post("apply-template",buildJsonObject {put("messages",messages);put("chat_template_kwargs",buildJsonObject {put("enable_thinking",false)})}).getValue("prompt").jsonPrimitive.content
        }
        override suspend fun tokenize(prompt:String)=withContext(Dispatchers.IO){ post("tokenize",buildJsonObject {put("content",prompt);put("add_special",true)}).getValue("tokens").jsonArray.size }
        override suspend fun generate(prompt:String,maxOutputTokens:Int,schema:JsonObject,onToken:(Int)->Unit):String=withContext(Dispatchers.IO){
            require(process?.isAlive==true){"ai_model_not_loaded"}
            val payload=buildJsonObject {put("prompt",prompt);put("n_predict",maxOutputTokens);put("temperature",0.1);put("json_schema",schema);put("stream",true);put("cache_prompt",false)}
            val call=generateClient.newCall(Request.Builder().url("http://127.0.0.1:$port/completion").header("Authorization","Bearer $token").post(payload.toString().toRequestBody("application/json".toMediaType())).build())
            // Cancelling the analysis closes the connection; llama-server then stops generating but keeps the model loaded.
            generateCall=call
            try {
                ensureActive()
                call.execute().use { r ->
                    require(r.isSuccessful){"ai_engine_error"}
                    val text=StringBuilder();var tokens=0;var stopType:String?=null
                    r.body!!.source().use { source ->
                        while(true){
                            val line=source.readUtf8Line() ?: break
                            if(!line.startsWith("data: ")) continue
                            val chunk=json.parseToJsonElement(line.removePrefix("data: ")).jsonObject
                            chunk["content"]?.jsonPrimitive?.contentOrNull?.let { if(it.isNotEmpty()){text.append(it);tokens++;onToken(tokens)} }
                            if(chunk["stop"]?.jsonPrimitive?.booleanOrNull==true){stopType=chunk["stop_type"]?.jsonPrimitive?.contentOrNull;break}
                        }
                    }
                    require(stopType!="limit"){"ai_output_truncated"}
                    text.toString()
                }
            } catch(e:java.io.IOException){ ensureActive();throw IllegalArgumentException("ai_engine_error") }
            finally { generateCall=null }
        }
    }

    // ---- Analysis ----
    private fun analysisPath(investigationId:String):Path { require(Regex("[A-Za-z0-9-]{1,64}").matches(investigationId)){"not_found"};return analysisDir.resolve("$investigationId.json") }
    fun analysisStatus(investigationId:String):JsonObject = analyses[investigationId]
        ?: runCatching { json.parseToJsonElement(Files.readString(analysisPath(investigationId))).jsonObject }.getOrNull()?.also { analyses[investigationId]=it }
        ?: buildJsonObject { put("state","idle") }
    /** A failed or cancelled attempt does not hide the last saved result; it is attached as lastAttempt. */
    private fun keepPrevious(investigationId:String,attempt:JsonObject):JsonObject =
        runCatching { json.parseToJsonElement(Files.readString(analysisPath(investigationId))).jsonObject }.getOrNull()?.let { JsonObject(it+("lastAttempt" to attempt)) } ?: attempt
    fun forget(investigationId:String){analyses.remove(investigationId);runCatching { Files.deleteIfExists(analysisPath(investigationId)) }}
    @Synchronized fun analyze(bundle:InvestigationBundle,ids:List<String>) {
        val invId=bundle.investigation.id;analysisPath(invId)
        // isCompleted, not isActive: a cancelled job may still be closing its engine connection.
        require(analysisJob?.isCompleted!=false){if(analysisInvestigation==invId)"ai_analysis_running" else "ai_analysis_busy"}
        val model=activeModel() ?: throw IllegalArgumentException("ai_model_not_loaded")
        val s=settings!!
        val evidence=AiEvidencePolicy.select(bundle,ids)
        val startedAt=Time.nowIso();val t0=System.currentTimeMillis()
        fun base(state:String)=buildJsonObject {put("state",state);put("investigationId",invId);put("startedAt",startedAt);put("findingIds",json.encodeToJsonElement(ids))
            put("model",buildJsonObject {put("repository",model.repository);put("filename",model.filename);put("quantization",AiCatalog.quantization(model.filename)?.let(::JsonPrimitive) ?: JsonNull)});put("backend",s.backend)}
        analyses[invId]=JsonObject(base("running")+("phase" to JsonPrimitive("preparing")))
        analysisInvestigation=invId
        analysisJob=scope.launch {
            var inputTokens=0
            try {
                val output=AiEvaluator(LlamaBackend()).run(bundle,ids,s.context) { phase,n ->
                    if(phase=="reading") inputTokens=n
                    analyses[invId]=JsonObject(base("running")+mapOf("phase" to JsonPrimitive(phase),"inputTokens" to JsonPrimitive(inputTokens),"generatedTokens" to JsonPrimitive(if(phase=="writing") n else 0)))
                }
                ensureActive()
                val result=JsonObject(base("complete")+mapOf("output" to json.encodeToJsonElement(output),"fingerprint" to JsonPrimitive(AiEvidencePolicy.fingerprint(evidence)),
                    "completedAt" to JsonPrimitive(Time.nowIso()),"durationMs" to JsonPrimitive(System.currentTimeMillis()-t0),"inputTokens" to JsonPrimitive(inputTokens),
                    "promptVersion" to JsonPrimitive(AiEvaluator.PROMPT_VERSION)))
                Files.writeString(analysisPath(invId),result.toString());analyses[invId]=result
            }catch(e:CancellationException){analyses[invId]=keepPrevious(invId,base("cancelled"))}
            catch(e:Exception){analyses[invId]=keepPrevious(invId,JsonObject(base("failed")+("error" to JsonPrimitive(e.message?.take(120) ?: "ai_analysis_failed"))))}
        }
    }
    @Synchronized fun cancelAnalysis(investigationId:String){ if(analysisInvestigation==investigationId){analysisJob?.cancel();generateCall?.cancel()} }
    override fun close(){cancelDownload();analysisJob?.cancel();generateCall?.cancel();loadJob?.cancel();stopProcess();scope.cancel()}
}
