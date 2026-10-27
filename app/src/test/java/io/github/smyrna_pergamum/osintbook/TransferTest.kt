package io.github.smyrna_pergamum.osintbook

import io.github.smyrna_pergamum.osintbook.data.Evidence
import io.github.smyrna_pergamum.osintbook.data.Finding
import io.github.smyrna_pergamum.osintbook.data.Investigation
import io.github.smyrna_pergamum.osintbook.data.Note
import io.github.smyrna_pergamum.osintbook.data.QueryRun
import io.github.smyrna_pergamum.osintbook.data.Relation
import io.github.smyrna_pergamum.osintbook.data.AssertionType
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.export.Csv
import io.github.smyrna_pergamum.osintbook.export.ImportException
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import io.github.smyrna_pergamum.osintbook.export.JsonTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TransferTest {
    private fun sample(): InvestigationBundle {
        val inv = Investigation(title = "Örnek araştırma ğüşiöç", description = "d")
        val t = Target(investigationId = inv.id, type = TargetType.DOMAIN, rawValue = "example.com", normalizedValue = "example.com", displayValue = "example.com")
        val r = QueryRun(targetId = t.id, providerId = "google_dns")
        val f = Finding(queryRunId = r.id, investigationId = inv.id, targetId = t.id, providerId = "google_dns", type = "DNS_RECORD",
            label = "A", value = "93.184.216.34", normalizedValue = "93.184.216.34", sourceUrl = "https://dns.google/resolve?name=example.com", retrievedAt = "2026-10-26T10:00:00Z")
        val e = Evidence(findingId = f.id, excerpt = "{}", rawResponseRef = "a".repeat(64) + ".raw", sha256 = "00", capturedAt = "2026-10-26T10:00:00Z")
        val rel = Relation(investigationId = inv.id, fromId = t.id, toId = f.id, type = "resolves to", evidenceId = e.id, assertionType = AssertionType.SOURCE_STATED)
        val n = Note(investigationId = inv.id, findingId = f.id, content = "not")
        return InvestigationBundle(investigation = inv, targets = listOf(t), queryRuns = listOf(r), findings = listOf(f), evidence = listOf(e), relations = listOf(rel), notes = listOf(n))
    }

    @Test fun roundTripKeepsContentWithNewIds() {
        val b = sample()
        val json = JsonTransfer.encode(b)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertFalse("raw refs never exported", json.contains(".raw"))
        val back = JsonTransfer.decode(json.toByteArray())
        assertEquals(b.investigation.title, back.investigation.title)
        assertNotEquals(b.investigation.id, back.investigation.id)
        assertEquals(1, back.findings.size)
        assertEquals("93.184.216.34", back.findings[0].value)
        val rel = back.relations.single()
        assertEquals(back.targets[0].id, rel.fromId)
        assertEquals(back.findings[0].id, rel.toId)
        assertEquals(back.evidence[0].id, rel.evidenceId)
        assertEquals(back.findings[0].id, back.notes[0].findingId)
        assertNull(back.evidence[0].rawResponseRef)
    }

    @Test fun brokenFilesRejected() {
        listOf("not json", "{}", "{\"schema\":\"other\",\"investigation\":{\"title\":\"x\"}}").forEach {
            try { JsonTransfer.decode(it.toByteArray()); fail(it) } catch (_: ImportException) {}
        }
        try { JsonTransfer.decode(ByteArray((JsonTransfer.MAX_BYTES + 1).toInt())); fail() } catch (e: ImportException) { assertEquals("too_large", e.reason) }
    }

    @Test fun danglingReferencesDropped() {
        val b = sample()
        val bad = b.copy(findings = b.findings + b.findings[0].copy(id = "x", queryRunId = "missing", normalizedValue = "other"))
        assertEquals(1, JsonTransfer.decode(JsonTransfer.encode(bad).toByteArray()).findings.size)
    }

    @Test fun importedNonWebLinksBlanked() {
        val b = sample()
        val bad = b.copy(findings = listOf(b.findings[0].copy(sourceUrl = "javascript:alert(1)")))
        assertEquals("", JsonTransfer.decode(JsonTransfer.encode(bad).toByteArray()).findings[0].sourceUrl)
    }

    @Test fun csvFormulaInjectionNeutralised() {
        assertEquals("'=1+1", Csv.cell("=1+1"))
        assertEquals("'+cmd", Csv.cell("+cmd"))
        assertEquals("'-2", Csv.cell("-2"))
        assertEquals("'@SUM(A1)", Csv.cell("@SUM(A1)"))
        assertEquals("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"", Csv.cell("=HYPERLINK(\"x\",\"y\")"))
        assertEquals("ğüşiöç", Csv.cell("ğüşiöç"))
        assertEquals("\"a,b\"", Csv.cell("a,b"))
    }

    @Test fun csvHasBomAndHeader() {
        val out = Csv.write(listOf(Csv.Column.VALUE), listOf(mapOf(Csv.Column.VALUE to "x")))
        assertTrue(out.startsWith("﻿value\r\nx\r\n"))
    }
}
