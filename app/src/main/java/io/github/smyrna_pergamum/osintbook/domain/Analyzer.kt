package io.github.smyrna_pergamum.osintbook.domain

import io.github.smyrna_pergamum.osintbook.data.Finding
import io.github.smyrna_pergamum.osintbook.data.QueryRun
import io.github.smyrna_pergamum.osintbook.data.Relation
import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.providers.COORDINATES_LABEL
import io.github.smyrna_pergamum.osintbook.providers.KeyNeed
import io.github.smyrna_pergamum.osintbook.providers.Providers
import io.github.smyrna_pergamum.osintbook.providers.parseCoordinates
import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rule-based reading of the findings. Every observation names the findings it rests on; nothing is
 * merged into an overall score (§5) and provider verdicts stay attributed to their provider (FR-07).
 */
enum class Level { INFO, WARN, MISSING }

enum class Kind {
    SUMMARY, REVIEW_STATE,
    DOMAIN_AGE, NEW_DOMAIN, EXPIRES_SOON, EXPIRED, RECENTLY_CHANGED, STATUS_HOLD, STATUS_DELETE, DOMAIN_LOCKED, REGISTRAR,
    NO_ADDRESS, IPV6_YES, IPV6_NO, NO_MX, NO_SPF, SPF_STRICT, SPF_SOFT, SPF_WEAK, NO_DMARC, DMARC_NONE, DMARC_POLICY,
    NO_CAA, DNSSEC_ON, DNSSEC_OFF, CDN,
    GEO_SPREAD, GEO_FAR, COUNTRY_MISMATCH, COARSE_LOCATION, NETWORK_OWNER,
    VT_FLAGGED, VT_CLEAN, ABUSE_HIGH, ABUSE_LOW, URLHAUS_LISTED, URLHAUS_CLEAN, SB_MATCH, SB_CLEAN, OTX_PULSES, OTX_NONE, MB_KNOWN, MB_UNKNOWN,
    SUGGEST_ADD_IP, SUGGEST_RELATION, ARCHIVE_BEFORE_REGISTRATION,
    VULNS, VULN_NO_VERSION, NO_VULNS, REPO_STALE, REPO_ARCHIVED, REPO_NO_LICENSE,
    PROVIDERS_FAILED, NOT_QUERIED, KEYS_MISSING,
}

sealed interface Action {
    data class AddTarget(val type: TargetType, val value: String) : Action
    data class AddRelation(val fromId: String, val toId: String, val type: String) : Action
    data object OpenSettings : Action
}

data class Observation(
    val targetId: String?,
    val level: Level,
    val kind: Kind,
    val args: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val action: Action? = null,
)

data class TimelineEvent(val at: String, val targetId: String, val providerId: String, val label: String, val value: String, val findingId: String)

data class Analysis(val observations: List<Observation>, val timeline: List<TimelineEvent>)

object Analyzer {
    private val FAILED = setOf(RunStatus.RATE_LIMITED, RunStatus.UNAUTHORIZED, RunStatus.NETWORK, RunStatus.TIMEOUT, RunStatus.SERVER, RunStatus.PARSE, RunStatus.TOO_LARGE)
    private val CDN_HINTS = linkedMapOf(
        "cloudflare" to "Cloudflare", "akamai" to "Akamai", "fastly" to "Fastly", "cloudfront" to "Amazon CloudFront",
        "awsdns" to "Amazon Route 53", "amazon" to "Amazon AWS", "googledomains" to "Google", "google" to "Google",
        "azure" to "Microsoft Azure", "microsoft" to "Microsoft", "incapsula" to "Imperva", "sucuri" to "Sucuri",
    )

    fun analyze(
        targets: List<Target>, findings: List<Finding>, runs: List<QueryRun>, relations: List<Relation>,
        providersWithKey: Set<String>, now: Instant = Instant.now(),
    ): Analysis {
        val out = mutableListOf<Observation>()
        val byTarget = findings.groupBy { it.targetId }
        val latestRuns = runs.groupBy { it.targetId to it.providerId }.mapValues { (_, v) -> v.maxBy { it.startedAt } }.values

        for (t in targets) {
            val fs = byTarget[t.id].orEmpty()
            val tRuns = latestRuns.filter { it.targetId == t.id }
            if (tRuns.isEmpty()) { out += Observation(t.id, Level.MISSING, Kind.NOT_QUERIED); continue }
            val ok = tRuns.count { it.status == RunStatus.SUCCESS || it.status == RunStatus.NO_RESULT }
            out += Observation(t.id, Level.INFO, Kind.SUMMARY, listOf("${tRuns.size}", "$ok", "${fs.size}"))
            val failed = tRuns.filter { it.status in FAILED }
            if (failed.isNotEmpty()) out += Observation(t.id, Level.MISSING, Kind.PROVIDERS_FAILED, listOf(failed.joinToString { name(it.providerId) }))

            registration(t, fs, now, out)
            dns(t, fs, targets, out)
            geo(t, fs, out)
            threats(t, fs, tRuns, out)
            archive(t, fs, out)
            software(t, fs, now, out)
        }
        links(targets, findings, relations, out)

        val reviewed = findings.count { it.reviewStatus == ReviewStatus.VERIFIED }
        val conflicting = findings.count { it.reviewStatus == ReviewStatus.CONFLICTING }
        if (findings.isNotEmpty()) out += Observation(null, Level.INFO, Kind.REVIEW_STATE, listOf("${findings.size - reviewed - conflicting}", "$reviewed", "$conflicting"))

        // Key-based sources that could have answered for these target types but have no key yet.
        val types = targets.map { it.type }.toSet()
        val missingKeys = Providers.all.filter { it.keyNeed == KeyNeed.REQUIRED && it.id !in providersWithKey && it.supports.any { s -> s in types } }
        if (missingKeys.isNotEmpty()) out += Observation(null, Level.MISSING, Kind.KEYS_MISSING, listOf(missingKeys.joinToString { it.name }), action = Action.OpenSettings)

        return Analysis(out, timeline(findings))
    }

    private fun name(id: String) = Providers.byId(id)?.name ?: id

    private fun days(from: Instant, to: Instant) = Duration.between(from, to).toDays()

    private fun instant(s: String?): Instant? = s?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { Instant.parse(it.substringBefore('.') + "Z") }.getOrNull() }

    private fun registration(t: Target, fs: List<Finding>, now: Instant, out: MutableList<Observation>) {
        val rdap = fs.filter { it.providerId == "rdap" }
        if (rdap.isEmpty() || t.type == TargetType.IP) return
        fun event(vararg names: String) = rdap.firstOrNull { it.type == "REGISTRATION_EVENT" && it.label in names }
        event("registration")?.let { f ->
            instant(f.value)?.let { at ->
                val d = days(at, now)
                out += Observation(t.id, Level.INFO, Kind.DOMAIN_AGE, listOf("$d", f.value.take(10)), listOf(f.id))
                if (d < 90) out += Observation(t.id, Level.WARN, Kind.NEW_DOMAIN, listOf("$d"), listOf(f.id))
            }
        }
        event("expiration")?.let { f ->
            instant(f.value)?.let { at ->
                val d = days(now, at)
                when {
                    d < 0 -> out += Observation(t.id, Level.WARN, Kind.EXPIRED, listOf(f.value.take(10)), listOf(f.id))
                    d <= 30 -> out += Observation(t.id, Level.WARN, Kind.EXPIRES_SOON, listOf("$d", f.value.take(10)), listOf(f.id))
                }
            }
        }
        event("last changed")?.let { f ->
            instant(f.value)?.let { at -> if (days(at, now) <= 30) out += Observation(t.id, Level.INFO, Kind.RECENTLY_CHANGED, listOf(f.value.take(10)), listOf(f.id)) }
        }
        val status = rdap.filter { it.label == "status" }
        status.filter { it.value.contains("hold", true) }.takeIf { it.isNotEmpty() }?.let { out += Observation(t.id, Level.WARN, Kind.STATUS_HOLD, listOf(it.joinToString { s -> s.value }), it.map { s -> s.id }) }
        status.filter { it.value.contains("redemption", true) || it.value.contains("pending delete", true) }.takeIf { it.isNotEmpty() }
            ?.let { out += Observation(t.id, Level.WARN, Kind.STATUS_DELETE, listOf(it.joinToString { s -> s.value }), it.map { s -> s.id }) }
        status.filter { it.value.contains("transfer prohibited", true) }.takeIf { it.isNotEmpty() }?.let { out += Observation(t.id, Level.INFO, Kind.DOMAIN_LOCKED, evidence = it.map { s -> s.id }) }
        rdap.firstOrNull { it.type == "ENTITY" && it.label.contains("registrar") }?.let { out += Observation(t.id, Level.INFO, Kind.REGISTRAR, listOf(it.value), listOf(it.id)) }
    }

    private fun dns(t: Target, fs: List<Finding>, targets: List<Target>, out: MutableList<Observation>) {
        val dns = fs.filter { it.providerId == "google_dns" }
        if (dns.isEmpty()) return
        fun rec(label: String) = dns.filter { it.label == label }
        val a = rec("A"); val aaaa = rec("AAAA"); val mx = rec("MX")
        if (a.isEmpty() && aaaa.isEmpty()) out += Observation(t.id, Level.INFO, Kind.NO_ADDRESS)
        if (aaaa.isNotEmpty()) out += Observation(t.id, Level.INFO, Kind.IPV6_YES, evidence = aaaa.map { it.id })
        else if (a.isNotEmpty()) out += Observation(t.id, Level.INFO, Kind.IPV6_NO)
        if (mx.isEmpty()) out += Observation(t.id, Level.INFO, Kind.NO_MX)

        val spf = rec("TXT").firstOrNull { it.value.trim().startsWith("v=spf1", true) }
        when {
            spf == null -> out += Observation(t.id, if (mx.isNotEmpty()) Level.WARN else Level.INFO, Kind.NO_SPF)
            spf.value.contains("-all") -> out += Observation(t.id, Level.INFO, Kind.SPF_STRICT, evidence = listOf(spf.id))
            spf.value.contains("~all") -> out += Observation(t.id, Level.INFO, Kind.SPF_SOFT, evidence = listOf(spf.id))
            else -> out += Observation(t.id, Level.WARN, Kind.SPF_WEAK, evidence = listOf(spf.id))
        }
        val dmarcRan = dns.any { it.label == "DNSSEC" } // the DMARC lookup was added together with DNSSEC
        val dmarc = rec("DMARC").firstOrNull { it.value.contains("v=DMARC1", true) }
        val policy = dmarc?.let { Regex("(?i)\\bp\\s*=\\s*(\\w+)").find(it.value)?.groupValues?.get(1)?.lowercase() }
        when {
            dmarc == null && dmarcRan -> out += Observation(t.id, Level.WARN, Kind.NO_DMARC)
            dmarc == null -> Unit
            policy == "none" -> out += Observation(t.id, Level.WARN, Kind.DMARC_NONE, evidence = listOf(dmarc.id))
            else -> out += Observation(t.id, Level.INFO, Kind.DMARC_POLICY, listOf(policy ?: "?"), listOf(dmarc.id))
        }
        if (rec("CAA").isEmpty()) out += Observation(t.id, Level.INFO, Kind.NO_CAA)
        rec("DNSSEC").firstOrNull()?.let { out += Observation(t.id, Level.INFO, if (it.value == "validated") Kind.DNSSEC_ON else Kind.DNSSEC_OFF, evidence = listOf(it.id)) }

        // CDN / cloud hints from name servers, CNAMEs and any network owner seen for this target.
        val hintSources = (rec("NS") + rec("CNAME") + fs.filter { it.label in setOf("org", "isp", "as_owner", "asn", "name") })
        val hits = hintSources.flatMap { f -> CDN_HINTS.filterKeys { f.value.contains(it, true) }.values.map { it to f.id } }
        if (hits.isNotEmpty()) out += Observation(t.id, Level.INFO, Kind.CDN, listOf(hits.map { it.first }.distinct().joinToString()), hits.map { it.second }.distinct())

        // Suggest querying the addresses the domain points to when they are not targets yet.
        val ipTargets = targets.filter { it.type == TargetType.IP }.map { it.normalizedValue }.toSet()
        (a + aaaa).mapNotNull { f -> TargetParser.parseIp(f.value)?.let { it to f } }.filter { it.first !in ipTargets }.take(3).forEach { (ip, f) ->
            out += Observation(t.id, Level.INFO, Kind.SUGGEST_ADD_IP, listOf(ip), listOf(f.id), Action.AddTarget(TargetType.IP, ip))
        }
    }

    private fun km(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    private fun geo(t: Target, fs: List<Finding>, out: MutableList<Observation>) {
        val coords = fs.filter { it.label == COORDINATES_LABEL }.mapNotNull { f -> parseCoordinates(f.value)?.let { f to it } }
        if (coords.size >= 2) {
            var best = Triple(0.0, coords[0].first, coords[1].first)
            for (i in coords.indices) for (j in i + 1 until coords.size) {
                val d = km(coords[i].second, coords[j].second)
                if (d > best.first) best = Triple(d, coords[i].first, coords[j].first)
            }
            val k = if (best.first > 500) Kind.GEO_FAR else Kind.GEO_SPREAD
            out += Observation(t.id, if (k == Kind.GEO_FAR) Level.WARN else Level.INFO, k,
                listOf("${best.first.roundToInt()}", name(best.second.providerId), name(best.third.providerId)), listOf(best.second.id, best.third.id))
        }
        val countries = fs.filter { it.type == "GEOLOCATION" && it.label == "country" }
        if (countries.map { it.value.trim().lowercase() }.distinct().size > 1) {
            out += Observation(t.id, Level.WARN, Kind.COUNTRY_MISMATCH, listOf(countries.joinToString { "${name(it.providerId)}: ${it.value}" }), countries.map { it.id })
        }
        fs.filter { it.label == "accuracy_km" }.forEach { f ->
            val km = Regex("\\d+").find(f.value)?.value?.toIntOrNull() ?: return@forEach
            if (km >= 500) out += Observation(t.id, Level.INFO, Kind.COARSE_LOCATION, listOf(name(f.providerId), "$km"), listOf(f.id))
        }
        val owners = fs.filter { it.type == "NETWORK" && it.label in setOf("org", "isp", "as_owner") }
        if (owners.isNotEmpty()) out += Observation(t.id, Level.INFO, Kind.NETWORK_OWNER, listOf(owners.map { it.value }.distinctBy { it.lowercase() }.joinToString()), owners.map { it.id })
    }

    private fun threats(t: Target, fs: List<Finding>, runs: List<QueryRun>, out: MutableList<Observation>) {
        fun ran(id: String, vararg ok: RunStatus) = runs.any { it.providerId == id && it.status in ok }
        fs.firstOrNull { it.providerId == "virustotal" && it.label == "virustotal_last_analysis" }?.let { f ->
            val mal = Regex("malicious: (\\d+)").find(f.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val sus = Regex("suspicious: (\\d+)").find(f.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            out += if (mal + sus > 0) Observation(t.id, Level.WARN, Kind.VT_FLAGGED, listOf("$mal", "$sus"), listOf(f.id))
            else Observation(t.id, Level.INFO, Kind.VT_CLEAN, evidence = listOf(f.id))
        }
        fs.firstOrNull { it.providerId == "abuseipdb" && it.label == "abuseConfidenceScore" }?.let { f ->
            val score = Regex("\\d+").find(f.value)?.value?.toIntOrNull() ?: 0
            out += Observation(t.id, if (score >= 50) Level.WARN else Level.INFO, if (score >= 50) Kind.ABUSE_HIGH else Kind.ABUSE_LOW, listOf("$score"), listOf(f.id))
        }
        val uh = fs.filter { it.providerId == "urlhaus" }
        if (uh.isNotEmpty()) out += Observation(t.id, Level.WARN, Kind.URLHAUS_LISTED, listOf("${uh.count { it.label == "urlhaus_url" || it.label == "urlhaus_listing" }}"), uh.map { it.id })
        else if (ran("urlhaus", RunStatus.NO_RESULT)) out += Observation(t.id, Level.INFO, Kind.URLHAUS_CLEAN)
        val sb = fs.filter { it.providerId == "safebrowsing" }
        if (sb.isNotEmpty()) out += Observation(t.id, Level.WARN, Kind.SB_MATCH, listOf(sb.joinToString { it.value.substringBefore(" ·") }), sb.map { it.id })
        else if (ran("safebrowsing", RunStatus.NO_RESULT)) out += Observation(t.id, Level.INFO, Kind.SB_CLEAN)
        fs.firstOrNull { it.providerId == "otx" && it.label == "otx_pulse_count" }?.let { f ->
            val n = f.value.toIntOrNull() ?: 0
            out += Observation(t.id, if (n > 0) Level.WARN else Level.INFO, if (n > 0) Kind.OTX_PULSES else Kind.OTX_NONE, listOf("$n"), listOf(f.id))
        }
        fs.firstOrNull { it.providerId == "malwarebazaar" && it.label == "signature" }?.let { out += Observation(t.id, Level.WARN, Kind.MB_KNOWN, listOf(it.value), listOf(it.id)) }
            ?: run { if (ran("malwarebazaar", RunStatus.NO_RESULT)) out += Observation(t.id, Level.INFO, Kind.MB_UNKNOWN) }
    }

    private fun archive(t: Target, fs: List<Finding>, out: MutableList<Observation>) {
        val first = fs.firstOrNull { it.providerId == "wayback" && it.label == "first_capture" }
        val reg = fs.firstOrNull { it.providerId == "rdap" && it.type == "REGISTRATION_EVENT" && it.label == "registration" }
        val firstAt = instant(first?.publishedAt)
        val regAt = instant(reg?.value)
        if (first != null && reg != null && firstAt != null && regAt != null && firstAt.isBefore(regAt.minus(Duration.ofDays(1)))) {
            out += Observation(t.id, Level.WARN, Kind.ARCHIVE_BEFORE_REGISTRATION, listOf(first.publishedAt!!.take(10), reg.value.take(10)), listOf(first.id, reg.id))
        }
    }

    private fun software(t: Target, fs: List<Finding>, now: Instant, out: MutableList<Observation>) {
        val vulns = fs.filter { it.providerId == "osv" }
        if (t.type == TargetType.PACKAGE) {
            if (vulns.isEmpty()) out += Observation(t.id, Level.INFO, Kind.NO_VULNS)
            else {
                val newest = vulns.maxBy { it.publishedAt ?: "" }
                out += Observation(t.id, Level.WARN, Kind.VULNS, listOf("${vulns.size}", newest.value.substringBefore(" —"), newest.publishedAt?.take(10) ?: "?"), vulns.map { it.id })
                if (vulns.any { it.label == "any_version" }) out += Observation(t.id, Level.INFO, Kind.VULN_NO_VERSION)
            }
        }
        val repo = fs.filter { it.providerId == "github" && it.type == "REPOSITORY" }
        if (repo.any { it.label == "full_name" }) {
            repo.firstOrNull { it.label == "pushed_at" }?.let { f ->
                instant(f.value)?.let { at -> days(at, now).let { d -> if (d > 365) out += Observation(t.id, Level.WARN, Kind.REPO_STALE, listOf("$d", f.value.take(10)), listOf(f.id)) } }
            }
            repo.firstOrNull { it.label == "archived" && it.value == "true" }?.let { out += Observation(t.id, Level.WARN, Kind.REPO_ARCHIVED, evidence = listOf(it.id)) }
            if (repo.none { it.label == "license" }) out += Observation(t.id, Level.INFO, Kind.REPO_NO_LICENSE)
        }
    }

    /** Shared name servers or addresses between targets, offered as relations the user can confirm. */
    private fun links(targets: List<Target>, findings: List<Finding>, relations: List<Relation>, out: MutableList<Observation>) {
        val related = relations.flatMap { listOf(it.fromId to it.toId, it.toId to it.fromId) }.toSet()
        val shareable = findings.filter { it.label in setOf("A", "AAAA", "NS", "nameserver") }
        val byValue = shareable.groupBy { it.value.trim().trimEnd('.').lowercase() }
        val seen = HashSet<Pair<String, String>>()
        for ((value, fs) in byValue) {
            val ts = fs.map { it.targetId }.distinct()
            for (i in ts.indices) for (j in i + 1 until ts.size) {
                val pair = ts[i] to ts[j]
                if (pair in related || !seen.add(pair)) continue
                out += Observation(null, Level.INFO, Kind.SUGGEST_RELATION, listOf(nameOf(targets, ts[i]), nameOf(targets, ts[j]), value),
                    fs.filter { it.targetId in pair.toList() }.map { it.id }, Action.AddRelation(ts[i], ts[j], "shares $value"))
            }
        }
        // A domain whose A/AAAA record equals an IP target.
        val ipTargets = targets.filter { it.type == TargetType.IP }.associateBy { it.normalizedValue }
        findings.filter { it.label == "A" || it.label == "AAAA" }.forEach { f ->
            val ip = TargetParser.parseIp(f.value) ?: return@forEach
            val ipT = ipTargets[ip] ?: return@forEach
            val pair = f.targetId to ipT.id
            if (pair in related || !seen.add(pair)) return@forEach
            out += Observation(null, Level.INFO, Kind.SUGGEST_RELATION, listOf(nameOf(targets, f.targetId), ipT.displayValue, "DNS ${f.label}"), listOf(f.id),
                Action.AddRelation(f.targetId, ipT.id, "resolves to"))
        }
    }

    private fun nameOf(targets: List<Target>, id: String) = targets.firstOrNull { it.id == id }?.displayValue ?: "?"

    private fun timeline(findings: List<Finding>): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()
        for (f in findings) {
            val at = when {
                f.type == "REGISTRATION_EVENT" -> f.value
                f.providerId == "wayback" && f.label in setOf("first_capture", "last_capture") -> f.publishedAt
                f.providerId == "github" && f.label in setOf("created_at", "pushed_at") -> f.value
                f.providerId == "otx" && f.label == "otx_pulse" -> f.publishedAt
                f.providerId == "malwarebazaar" && f.label in setOf("first_seen", "last_seen") -> f.publishedAt?.takeIf { f.label == "first_seen" } ?: f.value
                f.providerId == "osv" -> f.publishedAt
                f.providerId == "urlhaus" && f.label in setOf("urlhaus_url", "urlhaus_listing") -> f.publishedAt
                f.providerId == "urlscan" -> f.publishedAt
                else -> null
            } ?: continue
            val inst = instant(at) ?: continue
            events += TimelineEvent(inst.toString(), f.targetId, f.providerId, f.label, f.value.take(120), f.id)
        }
        // Long series (e.g. many scans) keep only their first and two latest entries so the timeline stays readable.
        return events.sortedBy { it.at }.distinctBy { listOf(it.at.take(10), it.targetId, it.label, it.value) }
            .groupBy { Triple(it.targetId, it.providerId, it.label) }.values
            .flatMap { s -> if (s.size <= 3) s else listOf(s.first()) + s.takeLast(2) }
            .sortedBy { it.at }
    }
}
