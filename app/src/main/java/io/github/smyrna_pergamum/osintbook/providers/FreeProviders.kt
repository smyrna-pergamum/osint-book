package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun hostOf(t: Target): String =
    if (t.type == TargetType.URL) URI(t.normalizedValue).host else t.normalizedValue

/** Google Public DNS JSON API [S6]. No key. */
class GoogleDnsProvider : Provider {
    override val id = "google_dns"
    override val name = "Google Public DNS"
    override val version = 1
    override val host = "dns.google"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://developers.google.com/speed/public-dns/docs/doh/json"
    private val types = listOf("A", "AAAA", "MX", "NS", "TXT", "CNAME", "CAA")

    override fun sentValue(target: Target) = hostOf(target)
    override fun estimateRequests(target: Target) = types.size + 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val name = hostOf(ctx.target)
        val findings = mutableListOf<FindingDraft>()
        val raw = mutableListOf<RawPart>()
        // The DMARC policy lives in a TXT record at _dmarc.<name>; it is stored under its own label.
        for ((type, qname, label) in types.map { Triple(it, name, it) } + Triple("TXT", "_dmarc.$name", "DMARC")) {
            val url = "https://dns.google/resolve?name=${qname.urlEncode()}&type=$type"
            val resp = ctx.http.get(url, quota = ctx.quota)
            raw += RawPart(url, resp.body, resp.mediaType)
            val root = parseJson(resp.body)
            // AD = the resolver validated the answer with DNSSEC; recorded once, from the A lookup.
            if (label == "A") findings += FindingDraft("DNS_RECORD", "DNSSEC", if (root["AD"].str() == "true") "validated" else "not validated", url, excerpt = "AD=${root["AD"].str()}")
            // Status 3 is NXDOMAIN: a valid answer, just an empty one.
            val answers = root["Answer"].arr() ?: continue
            for (a in answers) {
                val data = a["data"].str() ?: continue
                val rrType = DNS_TYPES[a["type"].str()?.toIntOrNull()] ?: type
                if (rrType != type) continue // CNAME chain entries appear under every query type
                findings += FindingDraft("DNS_RECORD", label, data.trim('"').let { if (type == "TXT") it.replace("\" \"", "") else it }, url, excerpt = excerptOf(a))
            }
        }
        return ProviderResult(findings, raw)
    }

    private companion object {
        val DNS_TYPES = mapOf(1 to "A", 28 to "AAAA", 15 to "MX", 2 to "NS", 16 to "TXT", 5 to "CNAME", 257 to "CAA")
    }
}

/**
 * IANA RDAP bootstrap (RFC 9224): which registry serves a TLD or IP block. Fetched from
 * data.iana.org once per process and day, so no third-party redirector or its rate limit is involved.
 */
object RdapBootstrap {
    private val cache = HashMap<String, Pair<Long, kotlinx.serialization.json.JsonElement>>()
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun services(ctx: QueryContext, kind: String): kotlinx.serialization.json.JsonArray {
        val root = lock.withLockCompat {
            cache[kind]?.takeIf { System.currentTimeMillis() - it.first < 24 * 3600_000L }?.second
                ?: parseJson(ctx.http.get("https://data.iana.org/rdap/$kind.json", quota = ctx.quota).body)
                    .also { cache[kind] = System.currentTimeMillis() to it }
        }
        return root["services"].arr() ?: throw ProviderException(RunStatus.PARSE, "bootstrap_shape")
    }

    /** First https base URL for the matching entry, always ending in '/'. */
    fun httpsBase(urls: kotlinx.serialization.json.JsonArray?): String? =
        urls?.mapNotNull { it.str() }?.firstOrNull { it.startsWith("https://") }?.let { if (it.endsWith("/")) it else "$it/" }

    /** True when [ip] lies inside [cidr] ("41.0.0.0/8", "2001:200::/23"). Literals only; never resolves names. */
    fun inCidr(ip: ByteArray, cidr: String): Boolean {
        val net = cidr.substringBefore('/')
        val bits = cidr.substringAfter('/', "").toIntOrNull() ?: return false
        val literal = io.github.smyrna_pergamum.osintbook.domain.TargetParser.parseIp(net) ?: return false
        val n = java.net.InetAddress.getByName(literal).address
        if (n.size != ip.size) return false
        var left = bits
        for (i in n.indices) {
            if (left <= 0) return true
            val mask = if (left >= 8) 0xFF else (0xFF shl (8 - left)) and 0xFF
            if ((n[i].toInt() and mask) != (ip[i].toInt() and mask)) return false
            left -= 8
        }
        return true
    }

    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(block: suspend () -> T): T {
        lock(); try { return block() } finally { unlock() }
    }
}

/** RDAP straight from the responsible registry: registration data for domains and IP networks. No key. */
class RdapProvider : Provider {
    override val id = "rdap"
    override val name = "RDAP"
    override val version = 2
    override val host = "data.iana.org + registry"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://www.iana.org/assignments/rdap-dns/rdap-dns.xhtml"

    override fun sentValue(target: Target) = hostOf(target)
    override fun estimateRequests(target: Target) = 2 // bootstrap (cached) + registry

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val v = hostOf(ctx.target).trim('[', ']')
        val ip = io.github.smyrna_pergamum.osintbook.domain.TargetParser.parseIp(v)
        val isIp = ip != null
        val (resp, src) = if (ip != null) {
            // The literal was validated by TargetParser, so getByName parses it without a DNS lookup.
            val bytes = java.net.InetAddress.getByName(ip).address
            val services = RdapBootstrap.services(ctx, if (bytes.size == 4) "ipv4" else "ipv6")
            val base = services.firstOrNull { svc -> svc.arr()?.getOrNull(0).arr()?.any { c -> c.str()?.let { RdapBootstrap.inCidr(bytes, it) } == true } == true }
                ?.let { RdapBootstrap.httpsBase(it.arr()?.getOrNull(1).arr()) }
                ?: throw ProviderException(RunStatus.NO_RESULT, "no_rdap_for_range")
            val url = base + "ip/" + ip.urlEncode()
            ctx.http.get(url, headers = mapOf("Accept" to "application/rdap+json"), quota = ctx.quota).let { it to it.url }
        } else {
            val tld = v.substringAfterLast('.')
            val services = RdapBootstrap.services(ctx, "dns")
            val base = services.firstOrNull { svc -> svc.arr()?.getOrNull(0).arr()?.any { it.str()?.equals(tld, true) == true } == true }
                ?.let { RdapBootstrap.httpsBase(it.arr()?.getOrNull(1).arr()) }
                ?: throw ProviderException(RunStatus.NO_RESULT, "no_rdap_for_tld:.$tld")
            // A subdomain is not registered on its own: drop leading labels until the registry knows the name.
            var name = v
            var result: io.github.smyrna_pergamum.osintbook.net.HttpResponse? = null
            while (result == null) {
                try {
                    result = ctx.http.get(base + "domain/" + name.urlEncode(), headers = mapOf("Accept" to "application/rdap+json"), quota = ctx.quota)
                } catch (e: ProviderException) {
                    if (e.status != RunStatus.NO_RESULT || name.count { it == '.' } < 2) throw e
                    name = name.substringAfter('.')
                }
            }
            result to result.url
        }
        val root = parseJson(resp.body)
        val out = mutableListOf<FindingDraft>()
        fun add(type: String, label: String, value: String?, published: String? = null) {
            if (!value.isNullOrBlank()) out += FindingDraft(type, label, value, src, root["handle"].str() ?: "", published, excerptOf(root, 400))
        }
        add("REGISTRATION", "handle", root["handle"].str())
        add("REGISTRATION", "ldhName", root["ldhName"].str()?.lowercase())
        add("REGISTRATION", "name", root["name"].str())
        add("REGISTRATION", "country", root["country"].str())
        add("REGISTRATION", "type", root["type"].str())
        if (isIp) add("REGISTRATION", "range", listOfNotNull(root["startAddress"].str(), root["endAddress"].str()).joinToString(" – "))
        root["status"].arr()?.forEach { add("REGISTRATION", "status", it.str()) }
        root["events"].arr()?.forEach { e ->
            val action = e["eventAction"].str() ?: return@forEach
            // The event date is part of the fact itself, not a publication time.
            add("REGISTRATION_EVENT", action, e["eventDate"].str())
        }
        root["nameservers"].arr()?.forEach { ns -> add("NAMESERVER", "nameserver", ns["ldhName"].str()?.lowercase()) }
        root["entities"].arr()?.forEach { ent ->
            val roles = ent["roles"].arr()?.mapNotNull { it.str() } ?: return@forEach
            // Only organisation-level names; personal contact details are out of scope (§1).
            val vcard = ent["vcardArray"].arr()?.getOrNull(1).arr()
            val fn = vcard?.firstOrNull { it.arr()?.getOrNull(0).str() == "fn" }.arr()?.getOrNull(3).str()
            val label = roles.joinToString(",")
            if (roles.any { it == "registrar" || it == "registrant" }) add("ENTITY", label, fn ?: ent["handle"].str())
        }
        if (out.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "empty")
        return ProviderResult(out, listOf(RawPart(src, resp.body, resp.mediaType)))
    }
}

/** Internet Archive Wayback availability + CDX APIs. No key. */
class WaybackProvider : Provider {
    override val id = "wayback"
    override val name = "Internet Archive"
    override val version = 1
    override val host = "archive.org"
    override val supports = setOf(TargetType.DOMAIN, TargetType.URL)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://archive.org/help/wayback_api.php"

    override fun estimateRequests(target: Target) = 3

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val v = ctx.target.normalizedValue
        val out = mutableListOf<FindingDraft>()
        val raw = mutableListOf<RawPart>()
        // First and most recent capture from CDX (limit=-1 returns the last row). CDX limits bursts,
        // so a refusal there keeps the availability lookup below and is reported only if nothing came back.
        var cdxLimited: ProviderException? = null
        for ((label, limit) in listOf("first_capture" to "1", "last_capture" to "-1")) {
            if (cdxLimited != null) break
            val url = "https://web.archive.org/cdx/search/cdx?url=${v.urlEncode()}&output=json&fl=timestamp,original,statuscode&limit=$limit"
            val resp = try {
                ctx.http.get(url, quota = ctx.quota)
            } catch (e: ProviderException) {
                if (e.status != RunStatus.RATE_LIMITED && e.status != RunStatus.SERVER) throw e
                cdxLimited = e
                continue
            }
            raw += RawPart(url, resp.body, resp.mediaType)
            if (resp.body.isBlank()) continue
            val rows = parseJson(resp.body).arr() ?: throw ProviderException(RunStatus.PARSE, "cdx_shape")
            val row = rows.drop(1).firstOrNull().arr() ?: continue // row 0 is the header
            val ts = row.getOrNull(0).str() ?: continue
            val original = row.getOrNull(1).str() ?: v
            out += FindingDraft("ARCHIVE_CAPTURE", label, "${waybackTime(ts)} · $original", "https://web.archive.org/web/$ts/$original", ts, waybackTime(ts), excerptOf(row))
        }
        val avail = "https://archive.org/wayback/available?url=${v.urlEncode()}"
        val resp = ctx.http.get(avail, quota = ctx.quota)
        raw += RawPart(avail, resp.body, resp.mediaType)
        val closest = parseJson(resp.body)["archived_snapshots"]["closest"]
        val snapUrl = closest["url"].str()
        val ts = closest["timestamp"].str()
        if (snapUrl != null && ts != null) {
            out += FindingDraft("ARCHIVE_CAPTURE", "closest_snapshot", "${waybackTime(ts)} · HTTP ${closest["status"].str() ?: "?"}", snapUrl.replaceFirst("http://", "https://"), ts, waybackTime(ts), excerptOf(closest))
        }
        if (out.isEmpty()) throw cdxLimited ?: ProviderException(RunStatus.NO_RESULT, "no_captures")
        return ProviderResult(out, raw)
    }

    private fun waybackTime(ts: String): String = runCatching {
        LocalDateTime.parse(ts.take(14).padEnd(14, '0'), DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toInstant(ZoneOffset.UTC).toString()
    }.getOrDefault(ts)
}

/** GitHub REST API [S7]. Works without a key at 60 requests/hour/IP; a personal token raises it. */
class GitHubProvider : Provider {
    override val id = "github"
    override val name = "GitHub REST"
    override val version = 1
    override val host = "api.github.com"
    override val supports = setOf(TargetType.REPO, TargetType.ORG)
    override val keyNeed = KeyNeed.OPTIONAL
    override val docsUrl = "https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api"
    override val signupUrl = "https://github.com/settings/personal-access-tokens"

    override fun sentValue(target: Target) = account(target)
    override fun estimateRequests(target: Target) = 2

    /** For an organisation name, the GitHub login guess is the name without spaces. */
    private fun account(t: Target) = if (t.type == TargetType.ORG) t.normalizedValue.replace(" ", "").lowercase() else t.normalizedValue

    override fun quotaFromHeaders(headers: Map<String, String>): String? {
        val rem = headers["x-ratelimit-remaining"] ?: return null
        return "$rem / ${headers["x-ratelimit-limit"] ?: "?"}"
    }

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val auth = ctx.key?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        val h = auth + mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
        val secret = ctx.key != null
        val acct = account(ctx.target)
        val out = mutableListOf<FindingDraft>()
        val raw = mutableListOf<RawPart>()

        if (acct.contains('/')) {
            val url = "https://api.github.com/repos/$acct"
            val resp = ctx.http.get(url, h, secret, ctx.quota)
            raw += RawPart(url, resp.body, resp.mediaType)
            val r = parseJson(resp.body)
            val page = r["html_url"].str() ?: "https://github.com/$acct"
            val rid = r["id"].str() ?: ""
            fun add(label: String, value: String?) { if (!value.isNullOrBlank()) out += FindingDraft("REPOSITORY", label, value, page, rid, r["created_at"].str(), excerptOf(r, 400)) }
            add("full_name", r["full_name"].str())
            add("description", r["description"].str())
            add("homepage", r["homepage"].str())
            add("language", r["language"].str())
            add("license", r["license"]["spdx_id"].str())
            add("stars", r["stargazers_count"].str())
            add("forks", r["forks_count"].str())
            add("open_issues", r["open_issues_count"].str())
            add("created_at", r["created_at"].str())
            add("pushed_at", r["pushed_at"].str())
            add("archived", r["archived"].str())
            add("owner", r["owner"]["login"].str())
            r["topics"].arr()?.forEach { add("topic", it.str()) }
        } else {
            val url = "https://api.github.com/users/${acct.urlEncode()}"
            val resp = ctx.http.get(url, h, secret, ctx.quota)
            raw += RawPart(url, resp.body, resp.mediaType)
            val u = parseJson(resp.body)
            val page = u["html_url"].str() ?: "https://github.com/$acct"
            val rid = u["id"].str() ?: ""
            fun add(label: String, value: String?) { if (!value.isNullOrBlank()) out += FindingDraft("ACCOUNT", label, value, page, rid, u["created_at"].str(), excerptOf(u, 400)) }
            add("login", u["login"].str())
            add("type", u["type"].str())
            add("name", u["name"].str())
            add("blog", u["blog"].str())
            add("location", u["location"].str())
            add("public_repos", u["public_repos"].str())
            add("created_at", u["created_at"].str())

            val reposUrl = "https://api.github.com/users/${acct.urlEncode()}/repos?per_page=30&sort=updated"
            val rr = ctx.http.get(reposUrl, h, secret, ctx.quota)
            raw += RawPart(reposUrl, rr.body, rr.mediaType)
            parseJson(rr.body).arr()?.forEach { repo ->
                val full = repo["full_name"].str() ?: return@forEach
                val desc = repo["description"].str()
                out += FindingDraft("REPOSITORY", "public_repo", if (desc.isNullOrBlank()) full else "$full — $desc",
                    repo["html_url"].str() ?: "https://github.com/$full", repo["id"].str() ?: full, repo["created_at"].str(), excerptOf(repo, 300))
            }
        }
        return ProviderResult(out, raw)
    }
}

/** Wikipedia search and Wikidata entity search for organisation context. No key. */
class WikipediaProvider : Provider {
    override val id = "wikipedia"
    override val name = "Wikipedia + Wikidata"
    override val version = 1
    override val host = "wikipedia.org, wikidata.org"
    override val supports = setOf(TargetType.ORG, TargetType.DOMAIN)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://www.mediawiki.org/wiki/API:Main_page"

    override fun estimateRequests(target: Target) = 2

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val q = ctx.target.normalizedValue
        val lang = if (ctx.language == "tr") "tr" else "en"
        val out = mutableListOf<FindingDraft>()
        val raw = mutableListOf<RawPart>()

        val wp = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=${q.urlEncode()}&srlimit=5&format=json&formatversion=2"
        val r1 = ctx.http.get(wp, quota = ctx.quota)
        raw += RawPart(wp, r1.body, r1.mediaType)
        parseJson(r1.body)["query"]["search"].arr()?.forEach { hit ->
            val title = hit["title"].str() ?: return@forEach
            val snippet = stripHtml(hit["snippet"].str().orEmpty())
            val page = "https://$lang.wikipedia.org/wiki/${title.replace(' ', '_').urlEncode()}"
            // Wikipedia's "timestamp" is the last edit, not publication; keep it in the text only.
            out += FindingDraft("WIKI_ARTICLE", "wikipedia_$lang", if (snippet.isEmpty()) title else "$title — $snippet", page, hit["pageid"].str() ?: title, null, excerptOf(hit, 400))
        }

        val wd = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${q.urlEncode()}&language=$lang&uselang=$lang&limit=5&format=json"
        val r2 = ctx.http.get(wd, quota = ctx.quota)
        raw += RawPart(wd, r2.body, r2.mediaType)
        parseJson(r2.body)["search"].arr()?.forEach { e ->
            val id = e["id"].str() ?: return@forEach
            val label = e["label"].str() ?: id
            val desc = e["description"].str()
            out += FindingDraft("WIKIDATA_ENTITY", id, if (desc.isNullOrBlank()) label else "$label — $desc", "https://www.wikidata.org/wiki/$id", id, null, excerptOf(e, 400))
        }
        if (out.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "no_hits")
        return ProviderResult(out, raw)
    }
}

/** OSV.dev known vulnerabilities for a package. No key. */
class OsvProvider : Provider {
    override val id = "osv"
    override val name = "OSV.dev"
    override val version = 1
    override val host = "api.osv.dev"
    override val supports = setOf(TargetType.PACKAGE)
    override val keyNeed = KeyNeed.NONE
    override val docsUrl = "https://google.github.io/osv.dev/api/"

    override fun estimateRequests(target: Target) = 1

    override suspend fun query(ctx: QueryContext): ProviderResult {
        val v = ctx.target.normalizedValue
        val eco = v.substringBefore(':')
        val rest = v.substringAfter(':')
        val at = rest.lastIndexOf('@')
        val (pkg, version) = if (at > 0) rest.substring(0, at) to rest.substring(at + 1) else rest to null
        val body = buildJsonObject {
            putJsonObject("package") { put("name", pkg); put("ecosystem", eco) }
            if (version != null) put("version", version)
        }.toString()
        val url = "https://api.osv.dev/v1/query"
        val resp = ctx.http.postJson(url, body, quota = ctx.quota)
        val vulns = parseJson(resp.body)["vulns"].arr() ?: JsonArray(emptyList())
        if (vulns.isEmpty()) throw ProviderException(RunStatus.NO_RESULT, "no_vulns")
        // Without a version OSV returns records for any version: the label says so (§3, version ambiguity).
        val label = if (version == null) "any_version" else "version_$version"
        val out = vulns.mapNotNull { vu ->
            val id = vu["id"].str() ?: return@mapNotNull null
            val summary = vu["summary"].str() ?: vu["details"].str()?.take(200) ?: ""
            val aliases = vu["aliases"].arr()?.mapNotNull { it.str() }?.joinToString(", ").orEmpty()
            FindingDraft("VULNERABILITY", label, listOf(id, summary, aliases).filter { it.isNotBlank() }.joinToString(" — "),
                "https://osv.dev/vulnerability/$id", id, vu["published"].str(), excerptOf(JsonPrimitive(summary)))
        }
        return ProviderResult(out, listOf(RawPart(url, resp.body, resp.mediaType)))
    }
}
