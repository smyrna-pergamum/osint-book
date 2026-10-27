package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.BuildConfig
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI

private fun hostOf(t: Target) = if (t.type == TargetType.URL) URI(t.normalizedValue).host else t.normalizedValue

private fun needKey(ctx: QueryContext) = ctx.key ?: throw ProviderException(RunStatus.NO_KEY, "no_key")

/** abuse.ch URLhaus: is a host or URL listed as a malware distribution site? Lookup only. */
class UrlhausProvider : Provider {
    override val id = "urlhaus"
    override val name = "URLhaus (abuse.ch)"
    override val version = 1
    override val host = "urlhaus-api.abuse.ch"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP)
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://urlhaus-api.abuse.ch/"
    override val signupUrl = "https://auth.abuse.ch/"
    override val reportsReputation = true

    override fun sentValue(target: Target) = target.normalizedValue
    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val key = needKey(ctx)
        val t = ctx.target
        val isUrl = t.type == TargetType.URL
        val endpoint = "https://urlhaus-api.abuse.ch/v1/${if (isUrl) "url" else "host"}/"
        val resp = ctx.http.postForm(endpoint, mapOf((if (isUrl) "url" else "host") to t.normalizedValue), mapOf("Auth-Key" to key), carriesSecret = true, quota = ctx.quota)
        val root = parseJson(resp.body)
        val status = root["query_status"].str()
        if (status != "ok") throw ProviderException(RunStatus.NO_RESULT, status ?: "no_results")
        val out = mutableListOf<FindingDraft>()
        val page = root["urlhaus_reference"].str() ?: "https://urlhaus.abuse.ch/browse.php?search=${hostOf(t).urlEncode()}"
        if (isUrl) {
            val text = listOfNotNull(root["threat"].str(), root["url_status"].str()?.let { "status: $it" }).joinToString(" · ")
            out += FindingDraft("PROVIDER_ASSESSMENT", "urlhaus_listing", text, page, root["id"].str() ?: "", root["date_added"].str()?.toIso(), excerptOf(root, 500))
            root["tags"].arr()?.mapNotNull { it.str() }?.forEach { out += FindingDraft("PROVIDER_ASSESSMENT", "urlhaus_tag", it, page, excerpt = it) }
        } else {
            root["url_count"].str()?.let { out += FindingDraft("PROVIDER_ASSESSMENT", "urlhaus_url_count", it, page, excerpt = it) }
            root["urls"].arr()?.take(20)?.forEach { u ->
                val url = u["url"].str() ?: return@forEach
                val text = listOfNotNull(url, u["threat"].str(), u["url_status"].str()).joinToString(" · ")
                out += FindingDraft("PROVIDER_ASSESSMENT", "urlhaus_url", text, u["urlhaus_reference"].str() ?: page, u["id"].str() ?: url, u["date_added"].str()?.toIso(), excerptOf(u, 300))
            }
        }
        return ProviderResult(out, listOf(RawPart(endpoint, resp.body, resp.mediaType)))
    }
}

/** abuse.ch MalwareBazaar: what is known about a file hash. Never downloads or uploads samples. */
class MalwareBazaarProvider : Provider {
    override val id = "malwarebazaar"
    override val name = "MalwareBazaar (abuse.ch)"
    override val version = 1
    override val host = "mb-api.abuse.ch"
    override val supports = setOf(TargetType.HASH)
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://bazaar.abuse.ch/api/"
    override val signupUrl = "https://auth.abuse.ch/"
    override val reportsReputation = true

    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val key = needKey(ctx)
        val hash = ctx.target.normalizedValue
        val endpoint = "https://mb-api.abuse.ch/api/v1/"
        val resp = ctx.http.postForm(endpoint, mapOf("query" to "get_info", "hash" to hash), mapOf("Auth-Key" to key), carriesSecret = true, quota = ctx.quota)
        val root = parseJson(resp.body)
        val status = root["query_status"].str()
        if (status != "ok") throw ProviderException(RunStatus.NO_RESULT, status ?: "not_found")
        val d = root["data"].arr()?.firstOrNull() ?: throw ProviderException(RunStatus.NO_RESULT, "empty")
        val sha = d["sha256_hash"].str() ?: hash
        val page = "https://bazaar.abuse.ch/sample/$sha/"
        val out = mutableListOf<FindingDraft>()
        fun add(type: String, label: String, v: String?) {
            if (!v.isNullOrBlank()) out += FindingDraft(type, label, v, page, sha, d["first_seen"].str()?.toIso(), excerptOf(d, 400))
        }
        add("PROVIDER_ASSESSMENT", "signature", d["signature"].str())
        add("FILE", "file_name", d["file_name"].str())
        add("FILE", "file_type", d["file_type"].str())
        add("FILE", "file_size", d["file_size"].str())
        add("FILE", "sha256", sha)
        add("FILE", "md5", d["md5_hash"].str())
        add("FILE", "first_seen", d["first_seen"].str())
        add("FILE", "last_seen", d["last_seen"].str())
        d["tags"].arr()?.mapNotNull { it.str() }?.forEach { add("PROVIDER_ASSESSMENT", "tag", it) }
        return ProviderResult(out, listOf(RawPart(endpoint, resp.body, resp.mediaType)))
    }
}

/** Google Safe Browsing Lookup API v4. The key travels in a header, never in the URL. */
class SafeBrowsingProvider : Provider {
    override val id = "safebrowsing"
    override val name = "Google Safe Browsing"
    override val version = 1
    override val host = "safebrowsing.googleapis.com"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL)
    override val keyNeed = KeyNeed.REQUIRED
    override val docsUrl = "https://developers.google.com/safe-browsing/v4/lookup-api"
    override val signupUrl = "https://console.cloud.google.com/apis/library/safebrowsing.googleapis.com"
    override val reportsReputation = true

    private fun urls(t: Target) =
        if (t.type == TargetType.URL) listOf(t.normalizedValue) else listOf("http://${t.normalizedValue}/", "https://${t.normalizedValue}/")

    override fun sentValue(target: Target) = urls(target).joinToString(", ")
    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val key = needKey(ctx)
        val body = buildJsonObject {
            putJsonObject("client") { put("clientId", "osintbook"); put("clientVersion", BuildConfig.VERSION_NAME) }
            putJsonObject("threatInfo") {
                putJsonArray("threatTypes") { listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION").forEach { add(it) } }
                putJsonArray("platformTypes") { add("ANY_PLATFORM") }
                putJsonArray("threatEntryTypes") { add("URL") }
                putJsonArray("threatEntries") { urls(ctx.target).forEach { u -> addJsonObject { put("url", u) } } }
            }
        }.toString()
        val endpoint = "https://safebrowsing.googleapis.com/v4/threatMatches:find"
        val resp = ctx.http.postJson(endpoint, body, mapOf("X-Goog-Api-Key" to key), carriesSecret = true, quota = ctx.quota)
        val matches = parseJson(resp.body)["matches"].arr()
        // An empty answer means "not on Google's lists right now", which is recorded as no result.
        if (matches.isNullOrEmpty()) throw ProviderException(RunStatus.NO_RESULT, "not_listed")
        val page = "https://transparencyreport.google.com/safe-browsing/search?url=${hostOf(ctx.target).urlEncode()}"
        val out = matches.map { m ->
            val text = listOfNotNull(m["threatType"].str(), m["platformType"].str(), m["threat"]["url"].str()).joinToString(" · ")
            FindingDraft("PROVIDER_ASSESSMENT", "safebrowsing_match", text, page, excerpt = excerptOf(m, 300))
        }
        return ProviderResult(out, listOf(RawPart(endpoint, resp.body, resp.mediaType)))
    }
}

/** urlscan.io search over existing public scans. This app never submits new scans. */
class UrlscanProvider : Provider {
    override val id = "urlscan"
    override val name = "urlscan.io"
    override val version = 1
    override val host = "urlscan.io"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP)
    override val keyNeed = KeyNeed.OPTIONAL
    override val docsUrl = "https://urlscan.io/docs/api/"
    override val signupUrl = "https://urlscan.io/user/signup"

    private fun q(t: Target) = when (t.type) {
        TargetType.IP -> "ip:\"${t.normalizedValue}\""
        else -> "page.domain:${hostOf(t)}"
    }

    override fun sentValue(target: Target) = q(target)
    override fun estimateRequests(target: Target) = 1

    override fun quotaFromHeaders(headers: Map<String, String>): String? {
        val rem = headers["x-rate-limit-remaining"] ?: return null
        return "$rem / ${headers["x-rate-limit-limit"] ?: "?"}"
    }

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val headers = ctx.key?.let { mapOf("API-Key" to it) } ?: emptyMap()
        val url = "https://urlscan.io/api/v1/search/?q=${q(ctx.target).urlEncode()}&size=20"
        val resp = ctx.http.get(url, headers, carriesSecret = ctx.key != null, quota = ctx.quota)
        val results = parseJson(resp.body)["results"].arr()
        if (results.isNullOrEmpty()) throw ProviderException(RunStatus.NO_RESULT, "no_scans")
        val out = results.mapNotNull { r ->
            val id = r["_id"].str() ?: return@mapNotNull null
            val pageUrl = r["page"]["url"].str() ?: r["task"]["url"].str() ?: return@mapNotNull null
            val meta = listOfNotNull(r["page"]["ip"].str(), r["page"]["country"].str(), r["page"]["server"].str(), r["page"]["asnname"].str())
            FindingDraft("SCAN_HISTORY", "urlscan_scan", (listOf(pageUrl) + meta).joinToString(" · "), "https://urlscan.io/result/$id/", id,
                r["task"]["time"].str(), excerptOf(r["page"], 300))
        }
        return ProviderResult(out, listOf(RawPart(url, resp.body, resp.mediaType)))
    }
}

/** abuse.ch dates look like "2024-01-02 03:04:05 UTC"; ISO 8601 is stored (DATA-01). */
private fun String.toIso(): String = Regex("^(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2}:\\d{2})").find(this)?.let { "${it.groupValues[1]}T${it.groupValues[2]}Z" } ?: this
