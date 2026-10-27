package io.github.smyrna_pergamum.osintbook

import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.domain.ParseResult
import io.github.smyrna_pergamum.osintbook.domain.TargetParser
import io.github.smyrna_pergamum.osintbook.domain.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetParserTest {
    private fun ok(t: TargetType, s: String) = TargetParser.parse(t, s) as ParseResult.Ok

    @Test fun idnDomainKeepsBothForms() {
        val r = ok(TargetType.DOMAIN, "Bücher.DE.")
        assertEquals("xn--bcher-kva.de", r.normalized)
        assertEquals("bücher.de", r.display)
    }

    @Test fun domainFieldAcceptsPastedUrl() {
        assertEquals("example.com", ok(TargetType.DOMAIN, "https://example.com/path?x=1").normalized)
    }

    @Test fun badDomainsRejected() {
        listOf("localhost", "-a.com", "a..com", "exa mple.com", "1.2.3.4", "a.c0m").forEach {
            assertTrue(it, TargetParser.parse(TargetType.DOMAIN, it) is ParseResult.Error)
        }
    }

    @Test fun urlNormalisedAndSecretsFlagged() {
        val r = ok(TargetType.URL, "HTTPS://user:pw@Example.com:443/a?q=1&api_key=SECRET#frag")
        assertEquals("https://example.com/a?q=1&api_key=SECRET", r.normalized)
        assertEquals("https://example.com/a?q=1", r.cleaned)
        assertTrue(Warning.SECRET_PARAMS in r.warnings)
        assertTrue(Warning.USERINFO in r.warnings)
    }

    @Test fun nonWebSchemesRejected() {
        assertTrue(TargetParser.parse(TargetType.URL, "javascript:alert(1)") is ParseResult.Error)
        assertTrue(TargetParser.parse(TargetType.URL, "file:///etc/passwd") is ParseResult.Error)
    }

    @Test fun ipv4Strict() {
        assertEquals("8.8.8.8", TargetParser.parseIpv4("8.8.8.8"))
        assertNull(TargetParser.parseIpv4("010.1.1.1"))
        assertNull(TargetParser.parseIpv4("256.1.1.1"))
        assertNull(TargetParser.parseIpv4("1.2.3"))
    }

    @Test fun ipv6CanonicalForm() {
        assertEquals("2001:db8::1", TargetParser.parseIpv6("2001:0DB8:0000:0000:0000:0000:0000:0001"))
        assertEquals("::1", TargetParser.parseIpv6("::1"))
        assertEquals("::ffff:c000:280", TargetParser.parseIpv6("::ffff:192.0.2.128"))
        assertNull(TargetParser.parseIpv6("1::2::3"))
        assertNull(TargetParser.parseIpv6("12345::"))
    }

    @Test fun privateIpWarned() {
        assertTrue(Warning.PRIVATE_IP in ok(TargetType.IP, "192.168.1.1").warnings)
        assertTrue(ok(TargetType.IP, "1.1.1.1").warnings.isEmpty())
    }

    @Test fun repoForms() {
        assertEquals("octocat/hello-world", ok(TargetType.REPO, "https://github.com/octocat/Hello-World.git").normalized)
        assertEquals("mozilla", ok(TargetType.REPO, "mozilla").normalized)
        assertTrue(TargetParser.parse(TargetType.REPO, "a/../b") is ParseResult.Error)
    }

    @Test fun packageForms() {
        assertEquals("npm:@scope/pkg@1.0.0", ok(TargetType.PACKAGE, "NPM:@scope/pkg@1.0.0").normalized)
        assertTrue(Warning.NO_VERSION in ok(TargetType.PACKAGE, "PyPI:requests").warnings)
        assertTrue(TargetParser.parse(TargetType.PACKAGE, "foo:bar") is ParseResult.Error)
    }

    @Test fun detection() {
        assertEquals(TargetType.IP, TargetParser.detect("2001:db8::1"))
        assertEquals(TargetType.URL, TargetParser.detect("https://example.com/x"))
        assertEquals(TargetType.REPO, TargetParser.detect("https://github.com/a/b"))
        assertEquals(TargetType.DOMAIN, TargetParser.detect("example.com"))
        assertEquals(TargetType.PACKAGE, TargetParser.detect("npm:lodash"))
        assertEquals(TargetType.ORG, TargetParser.detect("Türkiye Bilimsel Kurum"))
    }

    @Test fun dedupeNormalisation() {
        assertEquals(TargetParser.normalizeValue("  Foo   BAR "), TargetParser.normalizeValue("foo bar"))
    }
}
