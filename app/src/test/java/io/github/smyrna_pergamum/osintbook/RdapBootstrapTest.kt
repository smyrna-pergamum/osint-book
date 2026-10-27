package io.github.smyrna_pergamum.osintbook

import io.github.smyrna_pergamum.osintbook.providers.RdapBootstrap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class RdapBootstrapTest {
    private fun ip(s: String) = InetAddress.getByName(s).address

    @Test fun ipv4Ranges() {
        assertTrue(RdapBootstrap.inCidr(ip("8.8.8.8"), "8.0.0.0/8"))
        assertTrue(RdapBootstrap.inCidr(ip("85.105.1.1"), "85.96.0.0/12"))
        assertFalse(RdapBootstrap.inCidr(ip("85.95.1.1"), "85.96.0.0/12"))
        assertTrue(RdapBootstrap.inCidr(ip("1.2.3.4"), "0.0.0.0/0"))
    }

    @Test fun ipv6Ranges() {
        assertTrue(RdapBootstrap.inCidr(ip("2001:db8::1"), "2001:db8::/32"))
        assertFalse(RdapBootstrap.inCidr(ip("2001:db9::1"), "2001:db8::/32"))
        assertTrue(RdapBootstrap.inCidr(ip("2a00:1450::1"), "2a00::/12"))
    }

    @Test fun familiesNeverMix() {
        assertFalse(RdapBootstrap.inCidr(ip("8.8.8.8"), "2001:db8::/32"))
        assertFalse(RdapBootstrap.inCidr(ip("2001:db8::1"), "8.0.0.0/8"))
    }

    @Test fun httpsBasePreferredAndNormalised() {
        val urls = Json.parseToJsonElement("""["http://rdap.example/", "https://rdap.example/v1"]""").jsonArray
        assertEquals("https://rdap.example/v1/", RdapBootstrap.httpsBase(urls))
        assertNull(RdapBootstrap.httpsBase(Json.parseToJsonElement("""["http://only.example/"]""").jsonArray))
    }
}
