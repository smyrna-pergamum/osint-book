package io.github.smyrna_pergamum.osintbook.providers

import io.github.smyrna_pergamum.osintbook.data.RunStatus
import io.github.smyrna_pergamum.osintbook.data.Target
import io.github.smyrna_pergamum.osintbook.data.TargetType
import io.github.smyrna_pergamum.osintbook.net.Http
import io.github.smyrna_pergamum.osintbook.net.ProviderException
import io.github.smyrna_pergamum.osintbook.net.QuotaListener
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class KeyNeed { NONE, OPTIONAL, REQUIRED }

/** One fact a provider returned, before it is stored as a Finding with its Evidence. */
data class FindingDraft(
    val type: String,
    val label: String,
    val value: String,
    val sourceUrl: String,
    val sourceRecordId: String = "",
    val publishedAt: String? = null,
    val excerpt: String = "",
)

data class RawPart(val url: String, val body: String, val mediaType: String)

data class ProviderResult(val findings: List<FindingDraft>, val raw: List<RawPart>)

class QueryContext(
    val target: Target,
    val http: Http,
    val key: String?,
    /** UI language for providers that serve localized content (Wikipedia). */
    val language: String,
    val quota: QuotaListener,
    /** Inputs beyond the target, e.g. coordinates for reverse geocoding. */
    val extra: Map<String, String> = emptyMap(),
)

/**
 * NET-01: one adapter per provider with a fixed contract. Adapters are compiled in;
 * nothing is downloaded or loaded at run time.
 */
interface Provider {
    val id: String
    val name: String
    val version: Int
    /** Host the query goes to, shown before running (FR-03, SEC-02). */
    val host: String
    val supports: Set<TargetType>
    val keyNeed: KeyNeed
    val docsUrl: String
    val signupUrl: String? get() = null
    /** Client-side spacing between requests, for providers with per-minute limits. */
    val minIntervalMs: Long get() = 0
    /** Provider's published daily limit, or null when none or unknown (NET-05). */
    val dailyLimit: Int? get() = null
    /** True when the provider labels things as malicious/benign; the UI then states it is the provider's view (FR-07). */
    val reportsReputation: Boolean get() = false
    /** True when the module contacts the target itself instead of a third-party source; never preselected. */
    val active: Boolean get() = false
    /** Started from a specific finding rather than listed on the query screen. */
    val manualOnly: Boolean get() = false

    /** Exactly what is sent to [host] for this target (API-04: nothing more). */
    fun sentValue(target: Target): String = target.normalizedValue

    fun estimateRequests(target: Target): Int

    suspend fun query(ctx: QueryContext): ProviderResult

    /** Human-readable quota text from response headers, when the provider sends any. */
    fun quotaFromHeaders(headers: Map<String, String>): String? = null
}

internal val json = Json { ignoreUnknownKeys = true; isLenient = false }

internal fun parseJson(body: String): JsonElement =
    try { json.parseToJsonElement(body) } catch (e: Exception) { throw ProviderException(RunStatus.PARSE, "bad_json") }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** Short readable excerpt of a JSON fragment for Evidence. */
internal fun excerptOf(e: JsonElement?, max: Int = 600): String = e?.toString()?.take(max) ?: ""

/** SEC-08: provider HTML (e.g. search snippets) is reduced to plain text, never rendered. */
internal fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]*>"), "")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()

internal fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
