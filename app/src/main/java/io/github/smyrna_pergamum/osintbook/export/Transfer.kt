package io.github.smyrna_pergamum.osintbook.export

import io.github.smyrna_pergamum.osintbook.data.Evidence
import io.github.smyrna_pergamum.osintbook.data.Finding
import io.github.smyrna_pergamum.osintbook.data.Investigation
import io.github.smyrna_pergamum.osintbook.data.Note
import io.github.smyrna_pergamum.osintbook.data.QueryRun
import io.github.smyrna_pergamum.osintbook.data.Relation
import io.github.smyrna_pergamum.osintbook.data.Tag
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.Time
import io.github.smyrna_pergamum.osintbook.data.newId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable, versioned investigation file (FR-13, §5). It never contains API keys, auth headers,
 * provider settings or raw responses.
 */
@Serializable
data class InvestigationBundle(
    val schema: String = SCHEMA,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String = Time.nowIso(),
    val appVersion: String = "",
    val investigation: Investigation,
    val targets: List<Target> = emptyList(),
    val queryRuns: List<QueryRun> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val relations: List<Relation> = emptyList(),
    val notes: List<Note> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    companion object {
        const val SCHEMA = "osintbook.investigation"
        const val SCHEMA_VERSION = 1
    }
}

class ImportException(val reason: String) : Exception(reason)

object JsonTransfer {
    const val MAX_BYTES = 20L * 1024 * 1024
    const val MAX_RECORDS = 50_000
    private const val MAX_TEXT = 20_000

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(b: InvestigationBundle): String =
        json.encodeToString(InvestigationBundle.serializer(), b.copy(evidence = b.evidence.map { it.copy(rawResponseRef = null) }))

    /**
     * FR-14: size, field and record limits; every id is replaced so an import never collides with
     * or overwrites local data. Nothing in the file starts a query or runs code.
     */
    fun decode(bytes: ByteArray): InvestigationBundle {
        if (bytes.size > MAX_BYTES) throw ImportException("too_large")
        val b = try {
            json.decodeFromString(InvestigationBundle.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw ImportException("bad_format")
        }
        if (b.schema != InvestigationBundle.SCHEMA) throw ImportException("wrong_schema")
        if (b.schemaVersion > InvestigationBundle.SCHEMA_VERSION) throw ImportException("newer_version")
        val lists = listOf(b.targets, b.queryRuns, b.findings, b.evidence, b.relations, b.notes, b.tags)
        if (lists.any { it.size > MAX_RECORDS }) throw ImportException("too_many_records")
        return remap(b)
    }

    private fun clip(s: String, max: Int = MAX_TEXT) = s.take(max)

    /** Only http(s) links survive import (SEC-07). */
    private fun safeUrl(s: String) = if (s.startsWith("https://", true) || s.startsWith("http://", true)) clip(s, 4000) else ""

    private fun remap(b: InvestigationBundle): InvestigationBundle {
        val ids = HashMap<String, String>()
        fun id(old: String) = ids.getOrPut(old) { newId() }
        val inv = b.investigation.copy(
            id = id(b.investigation.id), title = clip(b.investigation.title, 300).ifBlank { "Import" },
            description = clip(b.investigation.description), archivedAt = null,
        )
        val targets = b.targets.filter { it.investigationId == b.investigation.id }.map {
            it.copy(id = id(it.id), investigationId = inv.id, rawValue = clip(it.rawValue, 2048),
                normalizedValue = clip(it.normalizedValue, 2048), displayValue = clip(it.displayValue, 2048))
        }
        val targetIds = targets.map { it.id }.toSet()
        val runs = b.queryRuns.mapNotNull { r ->
            val t = ids[r.targetId] ?: return@mapNotNull null
            r.copy(id = id(r.id), targetId = t, providerId = clip(r.providerId, 64), errorCode = r.errorCode?.let { clip(it, 200) })
        }
        val runIds = runs.map { it.id }.toSet()
        val findings = b.findings.mapNotNull { f ->
            val run = ids[f.queryRunId]?.takeIf { it in runIds } ?: return@mapNotNull null
            val target = ids[f.targetId]?.takeIf { it in targetIds } ?: return@mapNotNull null
            f.copy(id = id(f.id), queryRunId = run, investigationId = inv.id, targetId = target,
                providerId = clip(f.providerId, 64), type = clip(f.type, 64), label = clip(f.label, 200),
                value = clip(f.value, 4000), normalizedValue = clip(f.normalizedValue, 4000),
                sourceUrl = safeUrl(f.sourceUrl), sourceRecordId = clip(f.sourceRecordId, 200))
        }.distinctBy { listOf(it.providerId, it.type, it.label, it.normalizedValue, it.sourceRecordId) }
        val findingIds = findings.map { it.id }.toSet()
        val evidence = b.evidence.mapNotNull { e ->
            val f = ids[e.findingId]?.takeIf { it in findingIds } ?: return@mapNotNull null
            e.copy(id = id(e.id), findingId = f, excerpt = clip(e.excerpt, 4000), rawResponseRef = null, mediaType = clip(e.mediaType, 100))
        }
        val nodeIds = targetIds + findingIds
        val evidenceIds = evidence.map { it.id }.toSet()
        val relations = b.relations.mapNotNull { r ->
            val from = ids[r.fromId]?.takeIf { it in nodeIds } ?: return@mapNotNull null
            val to = ids[r.toId]?.takeIf { it in nodeIds } ?: return@mapNotNull null
            r.copy(id = id(r.id), investigationId = inv.id, fromId = from, toId = to, type = clip(r.type, 100),
                evidenceId = r.evidenceId?.let { ids[it] }?.takeIf { it in evidenceIds }, note = clip(r.note))
        }
        val notes = b.notes.map { n ->
            n.copy(id = id(n.id), investigationId = inv.id, findingId = n.findingId?.let { ids[it] }?.takeIf { it in findingIds }, content = clip(n.content))
        }
        val tags = b.tags.mapNotNull { t ->
            val f = ids[t.findingId]?.takeIf { it in findingIds } ?: return@mapNotNull null
            t.copy(id = id(t.id), investigationId = inv.id, findingId = f, label = clip(t.label, 60))
        }.distinctBy { it.findingId to it.label }
        return b.copy(investigation = inv, targets = targets, queryRuns = runs, findings = findings,
            evidence = evidence, relations = relations, notes = notes, tags = tags)
    }
}

/** FR-13: UTF-8 CSV with spreadsheet formula injection neutralised. */
object Csv {
    enum class Column { PROVIDER, TARGET, TYPE, LABEL, VALUE, SOURCE_URL, RETRIEVED_AT, PUBLISHED_AT, REVIEW, FAVORITE, NOTES }

    fun cell(raw: String): String {
        var s = raw
        if (s.isNotEmpty() && s[0] in DANGEROUS) s = "'$s"
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun write(columns: List<Column>, rows: List<Map<Column, String>>): String = buildString {
        append('﻿') // BOM so spreadsheet apps detect UTF-8 (Turkish characters)
        append(columns.joinToString(",") { cell(it.name.lowercase()) }).append("\r\n")
        rows.forEach { row -> append(columns.joinToString(",") { cell(row[it].orEmpty()) }).append("\r\n") }
    }

    private val DANGEROUS = charArrayOf('=', '+', '-', '@', '\t', '\r')
}
