package io.github.smyrna_pergamum.osintbook.export

import android.content.Context
import io.github.smyrna_pergamum.osintbook.R
import io.github.smyrna_pergamum.osintbook.data.Finding
import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.data.Time
import io.github.smyrna_pergamum.osintbook.providers.Providers
import io.github.smyrna_pergamum.osintbook.providers.COORDINATES_LABEL
import io.github.smyrna_pergamum.osintbook.providers.parseCoordinates
import io.github.smyrna_pergamum.osintbook.ui.formatTime
import io.github.smyrna_pergamum.osintbook.domain.Analysis
import io.github.smyrna_pergamum.osintbook.ui.groupedObservations
import io.github.smyrna_pergamum.osintbook.ui.levelRes
import io.github.smyrna_pergamum.osintbook.ui.observationText
import io.github.smyrna_pergamum.osintbook.ui.reviewRes
import io.github.smyrna_pergamum.osintbook.ui.runStatusRes

/** What the user chose to include; also printed into the report (§5: active filters). */
data class ReportOptions(
    val providers: Set<String>? = null,
    val reviews: Set<ReviewStatus> = ReviewStatus.entries.toSet(),
    val favoritesOnly: Boolean = false,
    val includeNotes: Boolean = true,
    val includeEvidence: Boolean = false,
    val csvColumns: List<Csv.Column> = Csv.Column.entries,
    /** Adds a static OpenStreetMap image of all coordinates; needs the network once. */
    val includeMap: Boolean = true,
)

fun InvestigationBundle.selectedFindings(o: ReportOptions): List<Finding> = findings.filter {
    (o.providers == null || it.providerId in o.providers) && it.reviewStatus in o.reviews && (!o.favoritesOnly || it.favorite)
}

/** FR-12: title, targets, method, selected findings, sources and notes; filters and missing sources are stated. */
/** Coordinates among the selected findings, in report order; numbers on the map follow this list. */
fun InvestigationBundle.mapMarkers(o: ReportOptions): List<Pair<Finding, MapMarker>> {
    val targets = targets.associateBy { it.id }
    return selectedFindings(o).filter { it.label == COORDINATES_LABEL }.mapNotNull { f ->
        parseCoordinates(f.value)?.let { (lat, lon) -> f to MapMarker(lat, lon, targets[f.targetId]?.displayValue ?: "") }
    }
}

fun buildReport(ctx: Context, b: InvestigationBundle, o: ReportOptions, map: android.graphics.Bitmap? = null, analysis: Analysis? = null): List<Block> {
    val out = mutableListOf<Block>()
    val inv = b.investigation
    val findings = b.selectedFindings(o)
    val targetsById = b.targets.associateBy { it.id }
    fun name(p: String) = Providers.byId(p)?.name ?: p

    out += Block.Title(inv.title)
    if (inv.description.isNotBlank()) out += Block.Para(inv.description)
    out += Block.Para(ctx.getString(R.string.rep_generated, formatTime(Time.nowIso()), Time.nowIso()), muted = true)

    // The analysis comes first so a reader sees the observations before the raw findings.
    if (analysis != null && analysis.observations.isNotEmpty()) {
        out += Block.Heading(ctx.getString(R.string.rep_analysis))
        out += Block.Para(ctx.getString(R.string.an_intro), muted = true)
        groupedObservations(analysis, b.targets).forEach { (t, obs) ->
            val title = t?.let { "${it.type.name}: ${it.displayValue}" } ?: ctx.getString(R.string.an_general)
            out += Block.Item(title, obs.map { "[${ctx.getString(levelRes(it.level))}] ${observationText(ctx, it)}" })
        }
        if (analysis.timeline.isNotEmpty()) {
            out += Block.Heading(ctx.getString(R.string.an_timeline))
            val names = b.targets.associate { it.id to it.displayValue }
            analysis.timeline.forEach { e ->
                out += Block.Para("${e.at.take(10)} · ${names[e.targetId] ?: ""} · ${e.label}: ${e.value} (${name(e.providerId)})")
            }
        }
    }

    out += Block.Heading(ctx.getString(R.string.rep_targets))
    b.targets.forEach { t ->
        out += Block.Item(t.displayValue, listOfNotNull(t.type.name, t.normalizedValue.takeIf { it != t.displayValue }))
    }

    out += Block.Heading(ctx.getString(R.string.rep_method))
    out += Block.Para(ctx.getString(R.string.rep_method_body))
    val latest = b.queryRuns.groupBy { it.targetId to it.providerId }.mapValues { (_, v) -> v.maxBy { it.startedAt } }.values
    latest.sortedBy { it.providerId }.forEach { r ->
        val t = targetsById[r.targetId]?.displayValue ?: "?"
        out += Block.Para("• ${name(r.providerId)} → $t: ${ctx.getString(runStatusRes(r.status))} (${formatTime(r.completedAt ?: r.startedAt)}, ${r.requestCount} req)")
    }
    val missing = latest.filter { it.status.name !in setOf("SUCCESS", "NO_RESULT") }
    if (missing.isNotEmpty()) out += Block.Para(ctx.getString(R.string.rep_missing, missing.joinToString { name(it.providerId) }), muted = true)

    out += Block.Heading(ctx.getString(R.string.rep_filters))
    out += Block.Para(
        listOf(
            ctx.getString(R.string.rep_filter_sources, o.providers?.joinToString { name(it) } ?: ctx.getString(R.string.all_sources)),
            ctx.getString(R.string.rep_filter_review, o.reviews.joinToString { ctx.getString(reviewRes(it)) }),
            if (o.favoritesOnly) ctx.getString(R.string.rep_filter_fav) else null,
            ctx.getString(R.string.rep_count, findings.size, b.findings.size),
        ).filterNotNull().joinToString("\n")
    )
    out += Block.Para(ctx.getString(R.string.rep_no_score), muted = true)

    val markers = b.mapMarkers(o)
    if (o.includeMap && map != null && markers.isNotEmpty()) {
        out += Block.Heading(ctx.getString(R.string.rep_map))
        out += Block.Image(map, ctx.getString(R.string.map_attribution))
        markers.forEachIndexed { i, (f, m) ->
            out += Block.Para("${i + 1}. ${m.label} — ${name(f.providerId)}: ${ctx.getString(R.string.lat_lon, "%.5f".format(java.util.Locale.ROOT, m.lat), "%.5f".format(java.util.Locale.ROOT, m.lon))}")
        }
        out += Block.Para(ctx.getString(R.string.geo_disclaimer), muted = true)
    }

    out += Block.Heading(ctx.getString(R.string.rep_findings))
    val notesByFinding = b.notes.filter { it.findingId != null }.groupBy { it.findingId }
    val evidenceByFinding = b.evidence.groupBy { it.findingId }
    findings.groupBy { it.providerId }.forEach { (p, list) ->
        out += Block.Para(name(p))
        list.forEach { f ->
            val lines = mutableListOf(
                "${ctx.getString(R.string.rep_target)}: ${targetsById[f.targetId]?.displayValue ?: "?"}",
                "${ctx.getString(R.string.source_url)}: ${f.sourceUrl}",
                "${ctx.getString(R.string.retrieved_at)}: ${f.retrievedAt}",
                "${ctx.getString(R.string.published_at)}: ${f.publishedAt ?: ctx.getString(R.string.not_stated)}",
                "${ctx.getString(R.string.review_status)}: ${ctx.getString(reviewRes(f.reviewStatus))}",
            )
            if (o.includeEvidence) evidenceByFinding[f.id]?.forEach { lines += "SHA-256: ${it.sha256}"; lines += it.excerpt.take(400) }
            if (o.includeNotes) notesByFinding[f.id]?.forEach { lines += "${ctx.getString(R.string.note)}: ${it.content}" }
            out += Block.Item("${f.type} · ${f.label}: ${f.value}", lines)
        }
    }

    if (b.relations.isNotEmpty()) {
        out += Block.Heading(ctx.getString(R.string.relations))
        val names = b.targets.associate { it.id to it.displayValue } + b.findings.associate { it.id to "${it.label}: ${it.value.take(80)}" }
        b.relations.forEach { r ->
            val kind = ctx.getString(if (r.assertionType.name == "SOURCE_STATED") R.string.assert_source else R.string.assert_inferred)
            out += Block.Item("${names[r.fromId] ?: "?"} → ${r.type} → ${names[r.toId] ?: "?"}", listOfNotNull(kind, r.note.takeIf { it.isNotBlank() }))
        }
    }

    if (o.includeNotes) {
        val general = b.notes.filter { it.findingId == null }
        if (general.isNotEmpty()) {
            out += Block.Heading(ctx.getString(R.string.notes))
            general.forEach { out += Block.Para("${formatTime(it.updatedAt)}\n${it.content}") }
        }
    }

    out += Block.Heading(ctx.getString(R.string.rep_sources))
    findings.map { it.providerId }.distinct().forEach { p ->
        Providers.byId(p)?.let { out += Block.Para("• ${it.name} — ${it.docsUrl}") }
    }
    out += Block.Para(ctx.getString(R.string.rep_disclaimer), muted = true)
    return out
}

fun buildCsv(b: InvestigationBundle, o: ReportOptions, reviewLabel: (ReviewStatus) -> String): String {
    val targets = b.targets.associateBy { it.id }
    val notes = b.notes.filter { it.findingId != null }.groupBy { it.findingId }
    val rows = b.selectedFindings(o).map { f ->
        mapOf(
            Csv.Column.PROVIDER to (Providers.byId(f.providerId)?.name ?: f.providerId),
            Csv.Column.TARGET to (targets[f.targetId]?.normalizedValue ?: ""),
            Csv.Column.TYPE to f.type,
            Csv.Column.LABEL to f.label,
            Csv.Column.VALUE to f.value,
            Csv.Column.SOURCE_URL to f.sourceUrl,
            Csv.Column.RETRIEVED_AT to f.retrievedAt,
            Csv.Column.PUBLISHED_AT to (f.publishedAt ?: ""),
            Csv.Column.REVIEW to reviewLabel(f.reviewStatus),
            Csv.Column.FAVORITE to if (f.favorite) "1" else "0",
            Csv.Column.NOTES to (notes[f.id]?.joinToString(" | ") { it.content } ?: ""),
        )
    }
    return Csv.write(o.csvColumns, rows)
}
