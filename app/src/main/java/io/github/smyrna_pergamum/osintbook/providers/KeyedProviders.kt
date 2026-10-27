package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.domain.TargetParser
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import java.net.URI
import java.util.Base64

private fun requireKey(ctx: QueryContext): String =
    ctx.key ?: throw ProviderException(RunStatus.NO_KEY, "no_key")

/**
 * VirusTotal public API [S8]: reads existing reports only. Nothing is submitted for scanning (SEC-10).
 * Public tier: 4 requests/minute, 500/day.
 */
class VirusTotalProvider : Provider {
    override val id = "virustotal"
    override val name = "VirusTotal"
    override val version = 1
    override val host = "www.virustotal.com"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP, TargetType.HASH)
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://docs.virustotal.com/reference/public-vs-premium-api"
    override val signupUrl = "https://www.virustotal.com/gui/join-us"
    override val minIntervalMs = 15_000L
    override val dailyLimit = 500
    override val reportsReputation = true

    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val key = requireKey(ctx)
        val t = ctx.target
        val path = when (t.type) {
            TargetType.IP -> "ip_addresses/${t.normalizedValue}"
            TargetType.HASH -> "files/${t.normalizedValue}"
            TargetType.URL -> "urls/" + Base64.getUrlEncoder().withoutPadding().encodeToString(t.normalizedValue.toByteArray())
            else -> "domains/${t.normalizedValue}"
        }
        val url = "https://www.virustotal.com/api/v3/$path"
        val resp = ctx.http.get(url, mapOf("x-apikey" to key), carriesSecret = true, quota = ctx.quota)
        val attrs = parseJson(resp.body)["data"]["attributes"]
        val gui = "https://www.virustotal.com/gui/" + when (t.type) {
            TargetType.IP -> "ip-address/${t.normalizedValue}"
            TargetType.HASH -> "file/${t.normalizedValue}"
            TargetType.URL -> "url/" + (parseJson(resp.body)["data"]["id"].str() ?: "")
            else -> "domain/${t.normalizedValue}"
        }
        val out = mutableListOf<FindingDraft>()
        val stats = attrs["last_analysis_stats"]
        if (stats != null) {
            val text = listOf("malicious", "suspicious", "harmless", "undetected").joinToString(", ") { "$it: ${stats[it].str() ?: "0"}" }
            val date = attrs["last_analysis_date"].str()?.toLongOrNull()?.let { java.time.Instant.ofEpochSecond(it).toString() }
            out += FindingDraft("PROVIDER_ASSESSMENT", "virustotal_last_analysis", text, gui, "", date, excerptOf(stats))
        }
        attrs["reputation"].str()?.let { out += FindingDraft("PROVIDER_ASSESSMENT", "virustotal_reputation", it, gui, excerpt = it) }
        attrs["categories"].obj()?.forEach { (engine, cat) -> cat.str()?.let { out += FindingDraft("PROVIDER_ASSESSMENT", "category:$engine", it, gui, excerpt = it) } }
        attrs["meaningful_name"].str()?.let { out += FindingDraft("FILE", "meaningful_name", it, gui, excerpt = it) }
        attrs["type_description"].str()?.let { out += FindingDraft("FILE", "type_description", it, gui, excerpt = it) }
        attrs["as_owner"].str()?.let { out += FindingDraft("NETWORK", "as_owner", it, gui, excerpt = it) }
        attrs["country"].str()?.let { out += FindingDraft("NETWORK", "country", it, gui, excerpt = it) }
        if (out.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "empty")
        return ProviderResult(out, listOf(RawPart(url, resp.body, resp.mediaType)))
    }
}

/** AbuseIPDB check endpoint. Free tier with the user's own key. */
class AbuseIpDbProvider : Provider {
    override val id = "abuseipdb"
    override val name = "AbuseIPDB"
    override val version = 1
    override val host = "api.abuseipdb.com"
    override val supports = setOf(TargetType.IP)
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://docs.abuseipdb.com/"
    override val signupUrl = "https://www.abuseipdb.com/register"
    override val dailyLimit = 1000
    override val reportsReputation = true

    override fun estimateRequests(target: Target) = 1

    override fun quotaFromHeaders(headers: Map<String, String>): String? {
        val rem = headers["x-ratelimit-remaining"] ?: return null
        return "$rem / ${headers["x-ratelimit-limit"] ?: "?"}"
    }

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val key = requireKey(ctx)
        val ip = ctx.target.normalizedValue
        val url = "https://api.abuseipdb.com/api/v2/check?ipAddress=${ip.urlEncode()}&maxAgeInDays=90"
        val resp = ctx.http.get(url, mapOf("Key" to key), carriesSecret = true, quota = ctx.quota)
        val d = parseJson(resp.body)["data"] ?: throw ProviderException(RunStatus.PARSE, "no_data")
        val page = "https://www.abuseipdb.com/check/$ip"
        val out = mutableListOf<FindingDraft>()
        fun add(type: String, label: String, value: String?, published: String? = null) {
            if (!value.isNullOrBlank()) out += FindingDraft(type, label, value, page, "", published, excerptOf(d, 400))
        }
        add("PROVIDER_ASSESSMENT", "abuseConfidenceScore", d["abuseConfidenceScore"].str()?.let { "$it / 100" })
        add("PROVIDER_ASSESSMENT", "totalReports_90d", d["totalReports"].str())
        add("PROVIDER_ASSESSMENT", "lastReportedAt", d["lastReportedAt"].str())
        add("NETWORK", "isp", d["isp"].str())
        add("NETWORK", "usageType", d["usageType"].str())
        add("NETWORK", "domain", d["domain"].str())
        add("NETWORK", "countryCode", d["countryCode"].str())
        add("NETWORK", "isTor", d["isTor"].str())
        return ProviderResult(out, listOf(RawPart(url, resp.body, resp.mediaType)))
    }
}

/** AlienVault OTX indicator API with the user's own key. */
class OtxProvider : Provider {
    override val id = "otx"
    override val name = "AlienVault OTX"
    override val version = 1
    override val host = "otx.alienvault.com"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP)
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://otx.alienvault.com/assets/static/external_api.html"
    override val signupUrl = "https://otx.alienvault.com/"
    override val reportsReputation = true

    override fun sentValue(target: Target) =
        if (target.type == TargetType.URL) URI(target.normalizedValue).host else target.normalizedValue

    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val key = requireKey(ctx)
        val v = sentValue(ctx.target)
        val section = when {
            TargetParser.parseIpv4(v) != null -> "IPv4"
            TargetParser.parseIpv6(v) != null -> "IPv6"
            else -> "domain"
        }
        val url = "https://otx.alienvault.com/api/v1/indicators/$section/${v.urlEncode()}/general"
        val resp = ctx.http.get(url, mapOf("X-OTX-API-KEY" to key), carriesSecret = true, quota = ctx.quota)
        val root = parseJson(resp.body)
        val page = "https://otx.alienvault.com/indicator/${section.lowercase().replace("ipv", "ip")}/$v"
        val out = mutableListOf<FindingDraft>()
        val pulses = root["pulse_info"]
        pulses["count"].str()?.let { out += FindingDraft("PROVIDER_ASSESSMENT", "otx_pulse_count", it, page, excerpt = it) }
        pulses["pulses"].arr()?.take(10)?.forEach { p ->
            val id = p["id"].str() ?: return@forEach
            out += FindingDraft("THREAT_PULSE", "otx_pulse", p["name"].str() ?: id, "https://otx.alienvault.com/pulse/$id", id, p["created"].str()?.let { if (it.endsWith("Z")) it else "${it.substringBefore('.')}Z" }, excerptOf(p, 300))
        }
        root["asn"].str()?.let { out += FindingDraft("NETWORK", "asn", it, page, excerpt = it) }
        root["country_name"].str()?.let { out += FindingDraft("NETWORK", "country", it, page, excerpt = it) }
        if (out.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "empty")
        return ProviderResult(out, listOf(RawPart(url, resp.body, resp.mediaType)))
    }
}

/** API-01 / DEL-03: the single place where the provider list lives. Quotas are on each adapter. */
object Providers {
    val all: List<Provider> = listOf(
        GoogleDnsProvider(), RdapProvider(), WaybackProvider(), GitHubProvider(),
        WikipediaProvider(), OsvProvider(), VirusTotalProvider(), AbuseIpDbProvider(), OtxProvider(), UrlhausProvider(), MalwareBazaarProvider(), SafeBrowsingProvider(), UrlscanProvider(), IpWhoIsProvider(), IpApiCoProvider(), GeoJsProvider(), BigDataCloudProvider(), NominatimProvider(), PingProvider(),
    )

    fun byId(id: String): Provider? = all.firstOrNull { it.id == id }

    fun forType(type: TargetType): List<Provider> = all.filter { type in it.supports && !it.manualOnly }
}
