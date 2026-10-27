package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.domain.TargetParser
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Reachability check with the system ping tool. Unlike every other module this one is active:
 * packets go straight to the target, which sees the device's IP. It is never preselected and the
 * UI says so. Four echo requests at most; no port or service probing (§1).
 */
class PingProvider : Provider {
    override val id = "ping"
    override val name = "Ping (ICMP)"
    override val version = 1
    override val host = "ICMP"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://en.wikipedia.org/wiki/Ping_(networking_utility)"
    override val active = true

    override fun sentValue(target: Target) =
        if (target.type == TargetType.URL) URI(target.normalizedValue).host.trim('[', ']') else target.normalizedValue

    override fun estimateRequests(target: Target) = COUNT

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val host = sentValue(ctx.target)
        // The host was validated by TargetParser; it is still checked here because it reaches a process argument.
        val valid = TargetParser.parseIp(host) != null || TargetParser.toAsciiHost(host) == host
        if (!valid || host.startsWith("-")) throw ProviderException(RunStatus.UNSUPPORTED, "bad_host")
        val v6 = TargetParser.parseIpv6(host) != null
        repeat(COUNT) { ctx.quota.onRequest() }

        val output = try {
            withTimeout(20_000) { run(listOf(if (v6) "ping6" else "ping", "-c", "$COUNT", "-W", "2", "-w", "10", host)) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw ProviderException(RunStatus.TIMEOUT, "ping_timeout")
        } catch (e: java.io.IOException) {
            throw ProviderException(RunStatus.UNSUPPORTED, "ping_unavailable")
        }
        val src = "ping:$host"
        val out = mutableListOf<FindingDraft>()
        Regex("^PING\\s+\\S+\\s+\\(([^)]+)\\)", RegexOption.MULTILINE).find(output)?.let {
            out += FindingDraft("REACHABILITY", "resolved_address", it.groupValues[1], src, excerpt = it.value)
        }
        val stats = Regex("(\\d+) packets transmitted, (\\d+) (?:packets )?received.*?([\\d.]+)% packet loss").find(output)
        if (stats != null) {
            val (sent, recv, loss) = stats.destructured
            out += FindingDraft("REACHABILITY", "packet_loss", "$loss% ($recv/$sent)", src, excerpt = stats.value)
        }
        Regex("=\\s*([\\d.]+)/([\\d.]+)/([\\d.]+)").find(output)?.let {
            val (min, avg, max) = it.destructured
            out += FindingDraft("REACHABILITY", "rtt_ms", "min $min · avg $avg · max $max", src, excerpt = it.value)
        }
        Regex("ttl=(\\d+)").find(output)?.let { out += FindingDraft("REACHABILITY", "ttl", it.groupValues[1], src, excerpt = it.value) }
        if (stats == null) throw ProviderException(RunStatus.NETWORK, "ping_no_reply")
        return ProviderResult(out, listOf(RawPart(src, output.take(20_000), "text/plain")))
    }

    private suspend fun run(cmd: List<String>): String = runInterruptible(Dispatchers.IO) {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        try {
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor(1, TimeUnit.SECONDS)
            text
        } finally {
            p.destroy()
        }
    }

    private companion object {
        const val COUNT = 4
    }
}
