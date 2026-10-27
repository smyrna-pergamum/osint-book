package io.github.smyrna_pergamum.osintbook

import io.github.smyrna_pergamum.osintbook.data.Finding
import io.github.smyrna_pergamum.osintbook.data.Investigation
import io.github.smyrna_pergamum.osintbook.data.QueryRun
import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.domain.Action
import io.github.smyrna_pergamum.osintbook.domain.Analyzer
import io.github.smyrna_pergamum.osintbook.domain.Kind
import io.github.smyrna_pergamum.osintbook.domain.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AnalyzerTest {
    private val now = Instant.parse("2026-10-27T00:00:00Z")
    private val inv = Investigation(title = "t")
    private val dom = Target(investigationId = inv.id, type = TargetType.DOMAIN, rawValue = "ex.com", normalizedValue = "ex.com", displayValue = "ex.com")
    private val ip = Target(investigationId = inv.id, type = TargetType.IP, rawValue = "1.2.3.4", normalizedValue = "1.2.3.4", displayValue = "1.2.3.4")

    private fun run(t: Target, p: String, s: RunStatus = RunStatus.SUCCESS) = QueryRun(targetId = t.id, providerId = p, status = s)
    private fun f(t: Target, p: String, type: String, label: String, value: String, published: String? = null) =
        Finding(queryRunId = "r", investigationId = inv.id, targetId = t.id, providerId = p, type = type, label = label, value = value,
            normalizedValue = value.lowercase(), sourceUrl = "https://x", retrievedAt = "2026-10-27T00:00:00Z", publishedAt = published)

    private fun kinds(targets: List<Target>, fs: List<Finding>, runs: List<QueryRun>) =
        Analyzer.analyze(targets, fs, runs, emptyList(), emptySet(), now).observations.map { it.kind }

    @Test fun newDomainAndEmailPolicy() {
        val fs = listOf(
            f(dom, "rdap", "REGISTRATION_EVENT", "registration", "2026-10-01T00:00:00Z"),
            f(dom, "rdap", "REGISTRATION_EVENT", "expiration", "2026-11-10T00:00:00Z"),
            f(dom, "google_dns", "DNS_RECORD", "A", "1.2.3.4"),
            f(dom, "google_dns", "DNS_RECORD", "MX", "10 mail.ex.com."),
            f(dom, "google_dns", "DNS_RECORD", "TXT", "v=spf1 include:x ~all"),
            f(dom, "google_dns", "DNS_RECORD", "DMARC", "v=DMARC1; p=none; rua=mailto:a@ex.com"),
            f(dom, "google_dns", "DNS_RECORD", "DNSSEC", "not validated"),
            f(dom, "google_dns", "DNS_RECORD", "NS", "ns1.cloudflare.com."),
        )
        val k = kinds(listOf(dom), fs, listOf(run(dom, "rdap"), run(dom, "google_dns")))
        listOf(Kind.DOMAIN_AGE, Kind.NEW_DOMAIN, Kind.EXPIRES_SOON, Kind.SPF_SOFT, Kind.DMARC_NONE, Kind.DNSSEC_OFF, Kind.CDN, Kind.IPV6_NO, Kind.SUGGEST_ADD_IP)
            .forEach { assertTrue("$it", it in k) }
        assertFalse(Kind.NO_SPF in k)
    }

    @Test fun missingSpfWarnsOnlyWithMx() {
        val noMx = kinds(listOf(dom), listOf(f(dom, "google_dns", "DNS_RECORD", "A", "9.9.9.9")), listOf(run(dom, "google_dns")))
        assertTrue(Kind.NO_MX in noMx && Kind.NO_SPF in noMx)
        val obs = Analyzer.analyze(listOf(dom), listOf(f(dom, "google_dns", "DNS_RECORD", "MX", "mx")), listOf(run(dom, "google_dns")), emptyList(), emptySet(), now).observations
        assertEquals(Level.WARN, obs.first { it.kind == Kind.NO_SPF }.level)
    }

    @Test fun geoSpreadAndCountryMismatch() {
        val fs = listOf(
            f(ip, "ipwhois", "GEOLOCATION", "coordinates", "41.00000, 29.00000"),
            f(ip, "geojs", "GEOLOCATION", "coordinates", "39.00000, 35.00000"),
            f(ip, "ipwhois", "GEOLOCATION", "country", "Turkey"),
            f(ip, "geojs", "GEOLOCATION", "country", "Türkiye"),
            f(ip, "geojs", "GEOLOCATION", "accuracy_km", "± 1000 km"),
        )
        val obs = Analyzer.analyze(listOf(ip), fs, listOf(run(ip, "ipwhois"), run(ip, "geojs")), emptyList(), emptySet(), now).observations
        val spread = obs.first { it.kind == Kind.GEO_FAR || it.kind == Kind.GEO_SPREAD }
        assertEquals(Kind.GEO_FAR, spread.kind) // Istanbul to central Anatolia is over 500 km
        assertTrue(obs.any { it.kind == Kind.COUNTRY_MISMATCH })
        assertTrue(obs.any { it.kind == Kind.COARSE_LOCATION })
    }

    @Test fun threatVerdictsStayPerProvider() {
        val fs = listOf(
            f(ip, "virustotal", "PROVIDER_ASSESSMENT", "virustotal_last_analysis", "malicious: 3, suspicious: 1, harmless: 60, undetected: 20"),
            f(ip, "abuseipdb", "PROVIDER_ASSESSMENT", "abuseConfidenceScore", "12 / 100"),
        )
        val k = kinds(listOf(ip), fs, listOf(run(ip, "virustotal"), run(ip, "abuseipdb"), run(ip, "safebrowsing", RunStatus.NO_RESULT)))
        assertTrue(Kind.VT_FLAGGED in k && Kind.ABUSE_LOW in k)
    }

    @Test fun relationSuggestedWhenDomainPointsToIpTarget() {
        val fs = listOf(f(dom, "google_dns", "DNS_RECORD", "A", "1.2.3.4"))
        val obs = Analyzer.analyze(listOf(dom, ip), fs, listOf(run(dom, "google_dns")), emptyList(), emptySet(), now).observations
        val rel = obs.first { it.kind == Kind.SUGGEST_RELATION }
        assertEquals(Action.AddRelation(dom.id, ip.id, "resolves to"), rel.action)
        assertFalse(obs.any { it.kind == Kind.SUGGEST_ADD_IP }) // already a target
    }

    @Test fun gapsReported() {
        val obs = Analyzer.analyze(listOf(dom, ip), emptyList(), listOf(run(dom, "rdap", RunStatus.RATE_LIMITED)), emptyList(), emptySet(), now).observations
        assertTrue(obs.any { it.kind == Kind.PROVIDERS_FAILED && it.targetId == dom.id })
        assertTrue(obs.any { it.kind == Kind.NOT_QUERIED && it.targetId == ip.id })
        assertTrue(obs.any { it.kind == Kind.KEYS_MISSING && it.action == Action.OpenSettings })
    }

    @Test fun timelineIsChronological() {
        val fs = listOf(
            f(dom, "rdap", "REGISTRATION_EVENT", "registration", "2010-05-01T00:00:00Z"),
            f(dom, "wayback", "ARCHIVE_CAPTURE", "first_capture", "x", published = "2004-01-01T00:00:00Z"),
        )
        val a = Analyzer.analyze(listOf(dom), fs, listOf(run(dom, "rdap"), run(dom, "wayback")), emptyList(), emptySet(), now)
        assertEquals(listOf("first_capture", "registration"), a.timeline.map { it.label })
        assertTrue(a.observations.any { it.kind == Kind.ARCHIVE_BEFORE_REGISTRATION })
    }
}
