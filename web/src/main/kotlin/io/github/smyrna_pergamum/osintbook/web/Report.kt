package io.github.smyrna_pergamum.osintbook.web

import io.github.smyrna_pergamum.osintbook.data.ReviewStatus
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Analyst-entered report fields, saved per investigation. The report number is assigned once and never reused. */
@Serializable data class ReportSettings(val number: Int = 0, val researchQuestion: String = "", val status: String = "draft",
    val confidence: String = "not_determined", val verifier: String = "", val owner: String = "", val decision: String = "draft", val verifiedAt: String? = null)

/** Builds reports following OSINT_Rapor_Veri_Semasi (JSON) and OSINT_Analiz_Rapor_Sablonu (layout). Model names are deliberately not printed. */
class Reports(private val root: Path, private val providerName: (String) -> String) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val dir = root.resolve("reports").also { Files.createDirectories(it) }
    val statuses = listOf("draft", "in_review", "final")
    val confidences = listOf("not_determined", "low", "medium", "high")
    val decisions = listOf("draft", "approved", "rejected")

    private fun path(id: String): Path { require(Regex("[A-Za-z0-9-]{1,64}").matches(id)) { "not_found" }; return dir.resolve("$id.json") }
    @Synchronized fun settings(id: String): ReportSettings = runCatching { json.decodeFromString<ReportSettings>(Files.readString(path(id))) }.getOrDefault(ReportSettings())
    @Synchronized fun save(id: String, input: ReportSettings): ReportSettings {
        require(input.status in statuses && input.confidence in confidences && input.decision in decisions) { "invalid_request" }
        require(input.researchQuestion.length <= 2000 && input.verifier.length <= 120 && input.owner.length <= 120) { "field_too_long" }
        val current = settings(id)
        val number = current.number.takeIf { it > 0 } ?: nextNumber()
        val verifiedAt = if (input.decision == "approved") current.verifiedAt ?: now() else null
        return input.copy(number = number, verifiedAt = verifiedAt).also { Files.writeString(path(id), json.encodeToString(it)) }
    }
    fun forget(id: String) { runCatching { Files.deleteIfExists(path(id)) } }
    private fun nextNumber(): Int {
        val counter = dir.resolve("counter.txt")
        val n = (runCatching { Files.readString(counter).trim().toInt() }.getOrDefault(0)) + 1
        Files.writeString(counter, n.toString()); return n
    }
    private fun now() = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", "+00:00")

    companion object {
        fun sourceType(provider: String) = when (provider) {
            "google_dns" -> "dns"; "rdap" -> "public_records"; "wayback" -> "web_archive"; "github" -> "code_repositories"
            "wikipedia" -> "web"; "osv" -> "vulnerability_database"; "ping" -> "active_check"
            "ipwhois", "ipapico", "geojs", "bigdatacloud", "nominatim" -> "ip_geolocation"
            else -> "threat_intelligence"
        }
        val sourceTypeLabels = mapOf("web" to "Web page", "public_records" to "Public records", "code_repositories" to "Code repository", "dns" to "DNS records",
            "web_archive" to "Web archive", "threat_intelligence" to "Threat intelligence", "ip_geolocation" to "IP geolocation",
            "vulnerability_database" to "Vulnerability database", "active_check" to "Active check")
        fun targetType(types: Set<TargetType>) = when {
            types.size != 1 -> "topic"
            types.single() in setOf(TargetType.DOMAIN, TargetType.URL) -> "domain"
            types.single() in setOf(TargetType.ORG, TargetType.REPO) -> "organization"
            types.single() == TargetType.IP -> "public_entity"
            else -> "topic"
        }
        private fun verification(statuses: Collection<ReviewStatus>) = when {
            statuses.any { it == ReviewStatus.CONFLICTING } -> "conflicting"
            statuses.isNotEmpty() && statuses.all { it == ReviewStatus.VERIFIED } -> "verified"
            else -> "pending"
        }
    }

    /** Report data exactly in the schema's structure. Findings come from the latest AI analysis when there is one. */
    fun data(bundle: InvestigationBundle, analysis: JsonObject?, lang: String): JsonObject {
        val s = settings(bundle.investigation.id)
        fun t(key: String) = I18n.t(lang, key)
        val output = analysis?.takeIf { it["state"]?.jsonPrimitive?.content == "complete" }?.get("output")?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromJsonElement<AiOutput>(it) }.getOrNull() }
        val selectedIds = analysis?.get("findingIds")?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
        val included = bundle.findings.filter { output == null || selectedIds == null || it.id in selectedIds }
        val byId = bundle.findings.associateBy { it.id }

        // Sources: one entry per provider and source URL, numbered K-001 in first-retrieved order.
        val groups = included.sortedBy { it.retrievedAt }.groupBy { it.providerId to it.sourceUrl }.entries.toList()
        val sourceId = mutableMapOf<String, String>()
        val sources = groups.mapIndexed { i, (key, fs) ->
            val id = "K-%03d".format(i + 1); fs.forEach { sourceId[it.id] = id }
            val sha = bundle.evidence.firstOrNull { e -> fs.any { it.id == e.findingId } && e.sha256.isNotBlank() }?.sha256
            buildJsonObject {
                put("id", id); put("type", sourceType(key.first)); put("url", key.second)
                put("collected_at_utc", fs.minOf { it.retrievedAt }); put("sha256_or_archive", sha?.let { "sha256:$it" } ?: "")
                put("observation", fs.take(8).joinToString("; ") { "${it.label}: ${it.value.take(160)}" } + if (fs.size > 8) " …(+${fs.size - 8})" else "")
                put("verification", verification(fs.map { it.reviewStatus }))
                put("provider", providerName(key.first))
            }
        }
        fun sourcesOf(ids: List<String>) = ids.mapNotNull { sourceId[it] }.distinct()

        val findings = if (output != null) output.observations.mapIndexed { i, o ->
            buildJsonObject {
                put("id", "B-%02d".format(i + 1)); put("title", o.title.ifBlank { o.text.take(90) })
                put("observation", o.findingIds.mapNotNull { byId[it] }.joinToString("; ") { "${it.label}: ${it.value.take(160)} (${providerName(it.providerId)})" })
                put("source_ids", JsonArray(sourcesOf(o.findingIds).map(::JsonPrimitive)))
                put("ai_interpretation", o.text); put("counterpoints", o.counterpoints)
                put("confidence", o.confidence.ifBlank { "low" })
                put("verification_status", verification(o.findingIds.mapNotNull { byId[it]?.reviewStatus }))
            }
        } else included.take(30).mapIndexed { i, f ->
            buildJsonObject {
                put("id", "B-%02d".format(i + 1)); put("title", "${f.label} · ${providerName(f.providerId)}")
                put("observation", f.value.take(600)); put("source_ids", JsonArray(listOfNotNull(sourceId[f.id]).map(::JsonPrimitive)))
                put("ai_interpretation", ""); put("counterpoints", ""); put("confidence", "low")
                put("verification_status", verification(listOf(f.reviewStatus)))
            }
        }

        val runs = bundle.queryRuns.groupBy { it.targetId to it.providerId }.values.map { r -> r.maxBy { it.startedAt } }
        val answered = runs.count { it.status.name == "SUCCESS" }
        val coverage = "${t("Sources queried")}: ${runs.size} · ${t("answered")}: $answered · " +
            runs.map { it.providerId }.distinct().joinToString(", ") { providerName(it) }
        val appConflicts = AiEvidencePolicy.conflicts(runCatching { AiEvidencePolicy.select(bundle, included.take(100).map { it.id }) }.getOrDefault(emptyList()))
        val conflicts = (output?.contradictions?.map { it.text } ?: appConflicts).ifEmpty { listOf(t("No contradictions identified.")) }
        val risks = AiEvidencePolicy.riskCategories.map { cat ->
            val r = output?.risks?.firstOrNull { it.category == cat }
            buildJsonObject { put("category", cat); put("rating", r?.rating ?: "medium"); put("explanation", r?.explanation ?: t("Not assessed by AI; review manually.")) }
        }
        val dates = included.map { it.retrievedAt.take(10) }.sorted()
        val types = bundle.targets.map { it.type }.toSet()
        return buildJsonObject {
            put("report", buildJsonObject {
                put("id", "RPR-%04d".format(s.number.coerceAtLeast(0))); put("created_at", now()); put("status", s.status)
                put("research_question", s.researchQuestion.ifBlank { bundle.investigation.description.ifBlank { bundle.investigation.title } })
                put("executive_summary", output?.summary ?: t("No AI analysis has been run. Findings are listed without interpretation."))
            })
            put("target", buildJsonObject {
                put("type", targetType(types)); put("label", bundle.investigation.title)
                put("identifiers", JsonArray(bundle.targets.map { JsonPrimitive(it.displayValue) }))
            })
            put("scope", buildJsonObject {
                put("from", dates.firstOrNull() ?: ""); put("to", dates.lastOrNull() ?: "")
                put("public_source_types", JsonArray(included.map { sourceType(it.providerId) }.distinct().map(::JsonPrimitive)))
            })
            put("metrics", buildJsonObject { put("sources", sources.size); put("findings", findings.size); put("confidence", s.confidence) })
            put("sources", JsonArray(sources)); put("findings", JsonArray(findings))
            put("quality", buildJsonObject {
                put("coverage", coverage); put("conflicts", conflicts.joinToString(" | "))
                put("limitations", (output?.missingInformation ?: emptyList()).ifEmpty { listOf(t("Only the selected public sources were consulted.")) }.joinToString(" | "))
            })
            put("risks", JsonArray(risks))
            put("recommendations", JsonArray((output?.verificationSteps ?: emptyList()).ifEmpty { listOf(t("Verify each finding with an independent primary source.")) }.mapIndexed { i, a ->
                buildJsonObject { put("action", a); put("priority", if (i < 2) "high" else "medium"); put("owner", s.owner.ifBlank { t("Analyst") }) }
            }))
            put("review", buildJsonObject {
                put("verifier", s.verifier); put("verified_at", s.verifiedAt?.let(::JsonPrimitive) ?: JsonNull); put("decision", s.decision)
                put("checks", JsonArray(findings.map { f ->
                    buildJsonObject {
                        put("method", "${f["id"]!!.jsonPrimitive.content}: ${t("cross-check with an independent source")}")
                        put("status", if (f["verification_status"]!!.jsonPrimitive.content == "verified") "verified" else "pending")
                    }
                }))
            })
        }
    }

    // ---- HTML following the PDF template ----
    private fun esc(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.o(k: String) = this[k]!!.jsonObject
    private fun JsonObject.a(k: String) = this[k]!!.jsonArray.map { it.jsonObject }

    fun html(d: JsonObject, lang: String): String {
        fun t(key: String) = esc(I18n.t(lang, key))
        val levelLabel = mapOf("low" to "Low", "medium" to "Medium", "high" to "High", "not_determined" to "Not determined")
        fun level(v: String) = t(levelLabel[v] ?: v)
        fun levelClass(v: String) = when (v) { "high" -> "hi"; "medium" -> "md"; else -> "lo" }
        val statusLabel = mapOf("draft" to "Draft", "in_review" to "In review", "final" to "Final", "approved" to "Approved", "rejected" to "Rejected",
            "pending" to "Check pending", "verified" to "Verified", "conflicting" to "Conflicting")
        fun status(v: String) = t(statusLabel[v] ?: v)
        val targetLabel = mapOf("domain" to "Domain", "organization" to "Organization", "public_entity" to "Public entity", "topic" to "Topic")
        val report = d.o("report"); val target = d.o("target"); val scope = d.o("scope"); val metrics = d.o("metrics")
        val sources = d.a("sources"); val findings = d.a("findings"); val quality = d.o("quality"); val review = d.o("review")
        val sourceTypes = scope["public_source_types"]!!.jsonArray.joinToString(", ") { t(sourceTypeLabels[it.jsonPrimitive.content] ?: it.jsonPrimitive.content) }.ifBlank { "—" }
        fun ids(f: JsonObject) = f["source_ids"]!!.jsonArray.joinToString(", ") { it.jsonPrimitive.content }.ifBlank { "—" }
        val rid = esc(report.s("id"))
        fun head(no: String, kicker: String, title: String) = """<div class="ph"><span>${t("OSINT / OPEN SOURCE ANALYSIS REPORT")}</span><span>$rid / ${t("VERSION")} 1.0</span></div>
<div class="kick">$no / ${t(kicker)}</div><h1>${t(title)}</h1><div class="rule"></div>"""
        fun foot(no: String) = """<div class="pf"><span>${t("OSINT / OPEN SOURCE ANALYSIS")}</span><span>${t("Generated automatically; requires human review")}</span><span>$no</span></div>"""
        val sb = StringBuilder()
        sb.append("""<!doctype html><html lang="$lang" dir="${if (lang == "ar") "rtl" else "ltr"}"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
<title>$rid · ${esc(target.s("label"))}</title><style>$CSS</style></head><body>""")
        // Cover
        sb.append("""<section class="cover"><div class="bar"></div><div class="crumbs">${t("INTELLIGENCE / RESEARCH / REPORTING")}</div>
<div class="badge">${t("PDF REPORT")}</div><div class="ctitle">${t("Open Source")}<br>${t("Intelligence")}</div><div class="caccent">${t("Analysis Report")}</div>
<p class="csub">${t("A report that separates observations from different sources from AI interpretations and records the evidence chain and verification.")}</p>
<div class="steps">${listOf("Data collection", "Evidence check", "AI analysis", "Report").mapIndexed { i, x -> "<div><b>${i + 1}</b><span>${t(x)}</span></div>" }.joinToString("<i></i>")}</div>
<div class="cgrid"><div><small>${t("REPORT ID")}</small><p>$rid</p></div><div><small>${t("RESEARCH SUBJECT")}</small><p>${esc(target.s("label"))}</p></div>
<div><small>${t("CREATED")}</small><p>${esc(report.s("created_at"))}</p></div><div><small>${t("REPORT STATUS")}</small><p>${status(report.s("status"))}</p></div></div>
<div class="cnote">${t("Generated automatically from public-source findings. AI interpretations are not verified facts about a person or organization and require human review.")}</div></section>""")
        // 01 Summary
        sb.append("""<section class="page">${head("01", "SUMMARY", "Executive summary")}<p class="lead">${t("A short, clear assessment of the research target and the evidence obtained, to support decisions.")}</p>
<div class="metrics"><div><small>${t("SOURCES")}</small><b>${metrics.s("sources")}</b><span>${t("Deduplicated sources")}</span></div>
<div><small>${t("FINDINGS")}</small><b>${metrics.s("findings")}</b><span>${t("Findings linked to sources")}</span></div>
<div><small>${t("OVERALL CONFIDENCE")}</small><b>${level(metrics.s("confidence"))}</b><span>${t("Low / Medium / High")}</span></div></div>
<h3>${t("RESEARCH QUESTION")}</h3><div class="box q">${esc(report.s("research_question"))}</div>
<h3>${t("EXECUTIVE ASSESSMENT")}</h3><p>${esc(report.s("executive_summary"))}</p>
<div class="card"><h3>${t("KEY FINDINGS")}</h3>${findings.take(3).joinToString("") { f ->
            """<div class="kf"><span class="id">${esc(f.s("id"))}</span><div><b>${esc(f.s("title"))}</b><small>${t("Evidence")}: ${esc(ids(f))}</small></div><em class="${levelClass(f.s("confidence"))}">${level(f.s("confidence"))}</em></div>"""
        }.ifBlank { "<p>—</p>" }}</div>
<div class="box info"><h3>${t("READING GUIDE")}</h3>${t("Finding: an observation supported by sources. AI interpretation: an explanation or inference based on the data. The two are not presented with the same certainty.")}</div>${foot("01")}</section>""")
        // 02 Scope
        sb.append("""<section class="page">${head("02", "RESEARCH", "Scope and method")}<h3>${t("SCOPE DEFINITION")}</h3>
<div class="two"><div class="box"><small>${t("TARGET / ENTITY")}</small><b>${t(targetLabel[target.s("type")] ?: target.s("type"))} · ${esc(target.s("label"))}</b>
<p class="mono">${target["identifiers"]!!.jsonArray.joinToString(", ") { esc(it.jsonPrimitive.content) }}</p></div>
<div class="box"><small>${t("DATE RANGE")}</small><b>${esc(scope.s("from"))} – ${esc(scope.s("to"))}</b></div></div>
<div class="box"><small>${t("INCLUDED SOURCES")}</small><b>$sourceTypes</b></div>
<h3>${t("WORKFLOW")}</h3><div class="flow">${listOf("Collect", "Normalize", "Match", "Verify", "AI interpretation", "Human review").mapIndexed { i, x -> "<div class=\"${if (i > 3) "g" else ""}\"><b>${i + 1}</b><span>${t(x)}</span></div>" }.joinToString("")}</div>
<h3>${t("ANALYSIS AND VERIFICATION PRINCIPLES")}</h3><table class="kv">
<tr><td>${t("Source coverage")}</td><td>$sourceTypes</td></tr><tr><td>${t("Review status")}</td><td>${status(review.s("decision"))}</td></tr>
<tr><td>${t("Assessment principle")}</td><td>${t("Evidence-based analysis and cross-verification")}</td></tr>
<tr><td>${t("Processing method")}</td><td>${t("Findings are linked to evidence; interpretations are kept separate")}</td></tr></table>
<div class="box warn"><h3>${t("METHODOLOGICAL LIMIT")}</h3>${t("Only lawful, publicly accessible sources are assessed. Automated analysis results alone are not evidence or identity verification; critical claims are confirmed with independent sources.")}</div>${foot("02")}</section>""")
        // 03 Sources
        sb.append("""<section class="page">${head("03", "TRACEABILITY", "Source and evidence register")}<p class="lead">${t("Every important claim is linked to a unique evidence ID with access time and, where possible, an archive or content hash.")}</p>
${sources.joinToString("") { k ->
            """<div class="src"><div class="srch"><span class="id">${esc(k.s("id"))}</span><b>${t(sourceTypeLabels[k.s("type")] ?: k.s("type"))} · ${esc(k.s("provider"))}</b><em>${status(k.s("verification"))}</em></div>
<table class="kv small"><tr><td>${t("SOURCE / URL")}</td><td class="mono">${esc(k.s("url"))}</td></tr><tr><td>${t("OBSERVATION")}</td><td>${esc(k.s("observation"))}</td></tr></table>
<small>${t("Time")}: ${esc(k.s("collected_at_utc"))} · ${esc(k.s("sha256_or_archive").ifBlank { "—" })}</small></div>"""
        }}<h3>${t("FINDING - EVIDENCE MAPPING")}</h3><table class="grid"><thead><tr><th>${t("FINDING")}</th><th>${t("EVIDENCE IDS")}</th><th>${t("CROSS-CHECK")}</th><th>${t("STATUS")}</th></tr></thead>
${findings.zip(review.a("checks")).joinToString("") { (f, c) -> "<tr><td><b>${esc(f.s("id"))}</b></td><td>${esc(ids(f))}</td><td>${esc(c.s("method"))}</td><td>${status(c.s("status"))}</td></tr>" }}</table>${foot("03")}</section>""")
        // 04 Findings
        sb.append("""<section class="page">${head("04", "ANALYSIS", "Findings and AI inferences")}<p class="lead">${t("For each finding the observed data and the analytic inference are kept apart; source links and alternative explanations are preserved.")}</p>
${findings.joinToString("") { f ->
            """<div class="fd"><div class="srch"><span class="id">${esc(f.s("id"))}</span><b class="ft">${esc(f.s("title"))}</b><em class="${levelClass(f.s("confidence"))}">${t("CONFIDENCE")}: ${level(f.s("confidence"))}</em></div>
<h4>${t("DIRECT OBSERVATION")}</h4><p>${esc(f.s("observation"))}</p><h4>${t("AI INTERPRETATION / HYPOTHESIS")}</h4><p>${esc(f.s("ai_interpretation").ifBlank { "—" })}</p>
<div class="ff"><b>${t("Evidence")}: ${esc(ids(f))}</b><i>${t("Alternative")}: ${esc(f.s("counterpoints").ifBlank { "—" })}</i></div></div>"""
        }}<h3>${t("RELATIONSHIP MAP / SCHEMATIC")}</h3><div class="map"><div class="n t"><small>${t("TARGET")}</small>${esc(target.s("label").take(40))}</div><i></i>
<div class="n"><small>${t("ENTITY")}</small>${esc(target["identifiers"]!!.jsonArray.firstOrNull()?.jsonPrimitive?.content?.take(40) ?: "—")}</div><i></i>
<div class="n"><small>${t("SOURCE")}</small>${esc(sources.firstOrNull()?.s("id") ?: "—")} … ${esc(sources.lastOrNull()?.s("id") ?: "")}</div></div>
<small class="note">${t("Note: this drawing represents automatic matching; it is not evidence of identity or causality.")}</small>${foot("04")}</section>""")
        // 05 Assessment
        val riskNames = mapOf("identity_linkage" to "Identity linkage", "staleness" to "Source currency", "coverage" to "Data completeness")
        sb.append("""<section class="page">${head("05", "ASSESSMENT", "Risks, limitations and recommendations")}<h3>${t("RISK / UNCERTAINTY MATRIX")}</h3>
<table class="grid"><thead><tr><th>${t("AREA")}</th><th>${t("ASSESSMENT")}</th><th>${t("PRIORITY")}</th></tr></thead>${d.a("risks").joinToString("") { r ->
            "<tr><td><b>${t(riskNames[r.s("category")] ?: r.s("category"))}</b></td><td>${esc(r.s("explanation"))}</td><td><em class=\"${levelClass(r.s("rating"))}\">${level(r.s("rating"))}</em></td></tr>"
        }}</table><h3>${t("RECOMMENDED ACTIONS")}</h3>${d.a("recommendations").mapIndexed { i, r ->
            "<div class=\"rec\"><span class=\"id\">${"%02d".format(i + 1)}</span><b>${esc(r.s("action"))}</b><small>${level(r.s("priority"))} · ${esc(r.s("owner"))}</small></div>"
        }.joinToString("")}<h3>${t("QUALITY CONTROL")}</h3><table class="kv"><tr><td>${t("Source coverage")}</td><td>${esc(quality.s("coverage"))}</td></tr>
<tr><td>${t("Conflicting findings")}</td><td>${esc(quality.s("conflicts"))}</td></tr><tr><td>${t("Limitations")}</td><td>${esc(quality.s("limitations"))}</td></tr></table>
<div class="box warn"><h3>${t("LIMITATIONS AND PUBLICATION CONTROL")}</h3><ul>
<li>${t("Claims in AI output are not presented as certain until compared with primary sources.")}</li>
<li>${t("The absence of invisible, deleted or private content is not treated as negative evidence.")}</li>
<li>${t("Purpose limitation, data minimisation and an appropriate retention period apply to personal data.")}</li>
<li>${t("Unnecessary personal and sensitive data are removed or masked before sharing.")}</li></ul></div>
<h3>${t("HUMAN REVIEW")}</h3><table class="kv"><tr><td>${t("Reviewer")}</td><td>${esc(review.s("verifier").ifBlank { "—" })}</td></tr>
<tr><td>${t("Decision")}</td><td>${status(review.s("decision"))}</td></tr><tr><td>${t("Reviewed at")}</td><td>${esc(review.s("verified_at").ifBlank { "—" })}</td></tr></table>${foot("05")}</section>""")
        sb.append("</body></html>")
        return sb.toString()
    }

    /** Prints the HTML to PDF with Microsoft Edge (or Chrome) in headless mode: Windows ships Edge, and it renders all nine languages. */
    fun pdf(html: String): ByteArray {
        val browser = listOfNotNull(System.getenv("OSINT_PDF_BROWSER"),
            System.getenv("ProgramFiles(x86)")?.let { "$it\\Microsoft\\Edge\\Application\\msedge.exe" },
            System.getenv("ProgramFiles")?.let { "$it\\Microsoft\\Edge\\Application\\msedge.exe" },
            System.getenv("ProgramFiles")?.let { "$it\\Google\\Chrome\\Application\\chrome.exe" })
            .map(Path::of).firstOrNull { Files.isRegularFile(it) } ?: throw IllegalArgumentException("report_pdf_unavailable")
        val work = Files.createTempDirectory(root, "pdf-")
        try {
            val input = work.resolve("report.html").also { Files.writeString(it, html) }
            val output = work.resolve("report.pdf")
            val p = ProcessBuilder(browser.toString(), "--headless=new", "--disable-gpu", "--no-first-run", "--disable-extensions", "--disable-sync",
                "--disable-background-networking", "--no-pdf-header-footer", "--user-data-dir=${work.resolve("profile")}",
                "--print-to-pdf=$output", input.toUri().toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (!p.waitFor(90, TimeUnit.SECONDS)) { p.destroyForcibly(); throw IllegalArgumentException("report_pdf_failed") }
            require(Files.isRegularFile(output) && Files.size(output) > 0) { "report_pdf_failed" }
            return Files.readAllBytes(output)
        } finally { runCatching { work.toFile().deleteRecursively() } }
    }
}

private val CSS = """
@page{size:A4;margin:0}*{box-sizing:border-box}body{margin:0;font-family:"Segoe UI",Lato,Arial,sans-serif;color:#1c2b3a;font-size:10.5pt;-webkit-print-color-adjust:exact;print-color-adjust:exact}
section{width:210mm;min-height:297mm;padding:14mm 16mm 22mm;position:relative;page-break-after:always;break-after:page}
.cover{background:#0e1b2e linear-gradient(#ffffff10 1px,transparent 1px) 0 0/38px 38px;color:#fff;padding:0 16mm}.cover .bar{height:4mm;background:#1f8c8a;margin:0 -16mm}
.crumbs{margin-top:14mm;font-size:9pt;letter-spacing:.06em;color:#9fc4d8;font-weight:700}.badge{display:inline-block;margin-top:12mm;background:#24405a;border-radius:6px;padding:7px 16px;font-size:8.5pt;font-weight:700;color:#cfe3ee}
.ctitle{font-size:34pt;font-weight:700;line-height:1.15;margin-top:16mm}.caccent{font-size:34pt;font-weight:700;color:#6cc9bd;margin-top:4mm}.csub{color:#c9d6e2;font-size:11.5pt;line-height:1.6;max-width:150mm}
.steps{display:flex;align-items:center;margin:22mm 6mm 0}.steps div{text-align:center;width:28mm}.steps b{display:inline-flex;width:13mm;height:13mm;border-radius:50%;border:1px solid #6cc9bd;background:#24405a;align-items:center;justify-content:center;font-size:14pt}
.steps span{display:block;font-size:7.5pt;font-weight:700;margin-top:3mm;color:#cfe3ee;text-transform:uppercase}.steps i{flex:1;height:1px;background:#6cc9bd;margin-bottom:8mm}
.cgrid{display:grid;grid-template-columns:1fr 1fr;margin-top:24mm;border:1px solid #2e4b66;border-radius:14px;background:#16304a}.cgrid div{padding:8mm 7mm;border-bottom:1px solid #2e4b66}.cgrid div:nth-child(odd){border-right:1px solid #2e4b66}
.cgrid small{font-size:7.5pt;font-weight:700;color:#9fc4d8}.cgrid p{font-size:12pt;font-weight:700;margin:4mm 0 0;overflow-wrap:anywhere}.cnote{margin-top:10mm;background:#24405a;border-radius:8px;padding:4mm 5mm;font-size:8pt;color:#dbe6ef}
.ph{display:flex;justify-content:space-between;font-size:8pt;font-weight:700;color:#2a6f9e;border-bottom:1px solid #dfe7ee;padding-bottom:3mm;text-transform:uppercase}
.kick{margin-top:7mm;font-size:8.5pt;font-weight:700;color:#1f8c8a}h1{font-size:25pt;margin:2mm 0 4mm;color:#14243a}.rule{height:3px;background:#e5eef5;margin-bottom:6mm}
.lead{color:#536475}h3{font-size:8.5pt;font-weight:700;color:#1f8c8a;margin:5mm 0 2.5mm;text-transform:uppercase}h4{font-size:8pt;color:#1f8c8a;margin:4mm 0 1mm;text-transform:uppercase}
.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:4mm}.metrics div,.box{background:#f2f6f9;border-radius:10px;padding:5mm}.metrics small,.box small{display:block;font-size:7.5pt;font-weight:700;color:#536475}
.metrics b{display:block;font-size:15pt;margin:2mm 0 4mm}.metrics span{font-size:7.5pt;color:#7a8a99}.box{margin-bottom:4mm}.box b{display:block;margin-top:2mm}.q{font-weight:700}
.info{background:#e8f1f8}.warn{background:#fbf3e6}.warn h3{color:#a3681a}.card{border:1px solid #dfe7ee;border-radius:12px;padding:2mm 6mm;margin:5mm 0}
.kf{display:flex;gap:5mm;align-items:flex-start;padding:3mm 0;border-bottom:1px solid #eef2f5}.kf:last-child{border:0}.kf div{flex:1}.kf small{display:block;color:#7a8a99;margin-top:1mm}
.id{display:inline-block;background:#e6f0f8;color:#2a6f9e;border-radius:6px;padding:2px 8px;font-size:8pt;font-weight:700;white-space:nowrap}
em{font-style:normal;font-weight:700;font-size:8pt;text-transform:uppercase}em.hi{color:#1f8c8a}em.md{color:#b7791f}em.lo{color:#7a8a99}
.two{display:grid;grid-template-columns:1fr 1fr;gap:4mm}.mono{font-family:Consolas,monospace;font-size:8.5pt;overflow-wrap:anywhere}
.flow{display:grid;grid-template-columns:repeat(6,1fr);gap:2mm}.flow div{background:#e8f1f8;border-radius:8px;text-align:center;padding:2mm 1mm 4mm}.flow .g{background:#e6f4f1}.flow b{display:block;color:#2a6f9e;font-size:13pt}.flow span{font-size:6.5pt;font-weight:700;text-transform:uppercase}
table{width:100%;border-collapse:separate;border-spacing:0 2mm}.kv td{padding:3mm 4mm;vertical-align:top}.kv tr:nth-child(odd) td{background:#f2f6f9}.kv td:first-child{width:38%;font-weight:600;color:#33475b}.small td{padding:1.5mm 0;background:none!important}
.grid th{background:#14243a;color:#fff;text-align:start;font-size:8pt;padding:3mm 4mm}.grid th:first-child{border-radius:8px 0 0 8px}.grid th:last-child{border-radius:0 8px 8px 0}.grid td{padding:3mm 4mm;background:#f2f6f9;vertical-align:top;font-size:9pt}
.src,.fd{border:1px solid #dfe7ee;border-left:4px solid #2a6f9e;border-radius:10px;padding:4mm 5mm;margin-bottom:4mm;break-inside:avoid}.fd{border-left-color:#1f8c8a}
.srch{display:flex;gap:4mm;align-items:center;border-bottom:1px solid #eef2f5;padding-bottom:2mm}.srch b{flex:1}.ft{font-size:12pt}.src small{color:#7a8a99;font-size:7.5pt;overflow-wrap:anywhere}
.ff{display:flex;justify-content:space-between;gap:4mm;border-top:1px solid #eef2f5;padding-top:2mm;font-size:8.5pt}.ff i{color:#7a8a99}
.map{display:flex;align-items:center;justify-content:space-between;background:#f2f6f9;border-radius:12px;padding:6mm 12mm}.map i{flex:1;height:1px;background:#9fb3c4;margin:0 3mm}
.n{width:30mm;height:30mm;border-radius:50%;border:2px solid #2a6f9e;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;font-size:7pt;padding:3mm;overflow:hidden;overflow-wrap:anywhere}.n.t{border-color:#1f8c8a}.n small{font-weight:700;color:#1f8c8a;font-size:7.5pt}
.note{color:#7a8a99;font-size:7.5pt}.rec{display:flex;gap:5mm;align-items:center;background:#f2f6f9;border-radius:10px;padding:4mm 5mm;margin-bottom:2mm}.rec b{flex:1}.rec small{color:#7a8a99}
thead{display:table-header-group}tr,.kf,.rec,.box{break-inside:avoid}h3{break-after:avoid}ul{margin:0;padding-inline-start:5mm}li{margin:1.5mm 0}
.pf{position:absolute;left:16mm;right:16mm;bottom:9mm;display:flex;justify-content:space-between;border-top:1px solid #dfe7ee;padding-top:3mm;font-size:7.5pt;color:#7a8a99}.pf span:first-child{font-weight:700;color:#33475b}
@media screen{body{background:#5b6672}section{margin:8mm auto;box-shadow:0 4px 24px #0006;background-color:#fff}section.cover{background-color:#0e1b2e}}
"""
