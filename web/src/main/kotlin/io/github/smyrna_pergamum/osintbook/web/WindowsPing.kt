package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.domain.TargetParser
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import io.github.smyrna_pergamum.osintbook.providers.*
import kotlinx.coroutines.*
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Path

class WindowsPing : Provider {
    override val id="ping"
    override val name="Ping (ICMP)"
    override val host="Direct ICMP · server → target"
    override val version=1
    override val keyNeed=KeyNeed.NONE
    override val supports=setOf(TargetType.DOMAIN,TargetType.URL,TargetType.IP)
    override val docsUrl="https://learn.microsoft.com/windows-server/administration/windows-commands/ping"
    override val active=true
    override fun estimateRequests(target: Target)=4
    override fun sentValue(target: Target)=if(target.type==TargetType.URL) URI(target.normalizedValue).host.trim('[',']') else target.normalizedValue
    fun command(target: Target): List<String> {
        val host=sentValue(target)
        require(!host.startsWith('-') && (TargetParser.parseIp(host)!=null || TargetParser.toAsciiHost(host)==host)){"invalid_ping_host"}
        val executable=Path.of(System.getenv("SystemRoot") ?: "C:\\Windows","System32","PING.EXE").toString()
        return listOf(executable,if(TargetParser.parseIpv6(host)!=null) "-6" else "-4","-n","4","-w","2000",host)
    }
    override suspend fun query(ctx: QueryContext): ProviderResult {
        if(!System.getProperty("os.name").startsWith("Windows")) throw ProviderException(RunStatus.UNSUPPORTED,"windows_ping_only")
        val process=ProcessBuilder(command(ctx.target)).redirectErrorStream(true).start()
        try {
            repeat(4){ctx.quota.onRequest()}
            withTimeout(15000){while(process.isAlive){delay(100)}}
            currentCoroutineContext().ensureActive()
            val bytes=process.inputStream.readNBytes(20000)
            val output=bytes.toString(System.console()?.charset() ?: Charset.forName("IBM850"))
            val source="ping:${sentValue(ctx.target)}"
            return ProviderResult(listOf(
                FindingDraft("REACHABILITY","ping_output",output,source,excerpt=output),
                FindingDraft("REACHABILITY","ping_exit_code",process.exitValue().toString(),source,excerpt="Process exit code; inspect the output for reachability.")
            ),listOf(RawPart(source,output,"text/plain")))
        } finally {if(process.isAlive){process.destroyForcibly();process.waitFor(1,java.util.concurrent.TimeUnit.SECONDS)};process.inputStream.close()}
    }
}
