package io.github.smyrna_pergamum.osintbook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** DATA-01: every stored time is UTC ISO 8601 with second precision, so strings sort chronologically. */
object Time {
    fun nowIso(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).toString()
}

fun newId(): String = UUID.randomUUID().toString()

enum class TargetType { DOMAIN, URL, IP, ORG, REPO, PACKAGE, HASH }

/** FR-07: the user's own review verdict. A provider's risk label never sets this. */
enum class ReviewStatus { UNREVIEWED, VERIFIED, CONFLICTING }

/** FR-04: every outcome is a separate state; one provider failing never erases another's results. */
enum class RunStatus {
    PENDING, RUNNING, SUCCESS, NO_RESULT, RATE_LIMITED, UNAUTHORIZED, NO_KEY,
    NETWORK, TIMEOUT, SERVER, PARSE, TOO_LARGE, CANCELLED, UNSUPPORTED;

    val isFinal get() = this != PENDING && this != RUNNING
}

/** FR-08: a relation the source states explicitly vs. one the user inferred. */
enum class AssertionType { SOURCE_STATED, INFERRED }

@Entity(tableName = "investigation")
@Serializable
data class Investigation(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val description: String = "",
    val createdAt: String = Time.nowIso(),
    val updatedAt: String = createdAt,
    val archivedAt: String? = null,
)

@Entity(
    tableName = "target",
    foreignKeys = [ForeignKey(Investigation::class, ["id"], ["investigationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("investigationId")],
)
@Serializable
data class Target(
    @PrimaryKey val id: String = newId(),
    val investigationId: String,
    val type: TargetType,
    val rawValue: String,
    val normalizedValue: String,
    /** Unicode form of an IDN host, shown next to the ASCII form (FR-02). */
    val displayValue: String,
    val createdAt: String = Time.nowIso(),
)

@Entity(
    tableName = "query_run",
    foreignKeys = [ForeignKey(Target::class, ["id"], ["targetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("targetId")],
)
@Serializable
data class QueryRun(
    @PrimaryKey val id: String = newId(),
    val targetId: String,
    val providerId: String,
    val startedAt: String = Time.nowIso(),
    val completedAt: String? = null,
    val status: RunStatus = RunStatus.PENDING,
    val errorCode: String? = null,
    val requestCount: Int = 0,
)

@Entity(
    tableName = "finding",
    foreignKeys = [
        ForeignKey(QueryRun::class, ["id"], ["queryRunId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Investigation::class, ["id"], ["investigationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("queryRunId"),
        Index("investigationId"),
        Index("value"),
        // FR-06: dedupe key. Same value from two providers stays two findings.
        Index(value = ["investigationId", "providerId", "type", "label", "normalizedValue", "sourceRecordId"], unique = true),
    ],
)
@Serializable
data class Finding(
    @PrimaryKey val id: String = newId(),
    val queryRunId: String,
    val investigationId: String,
    val targetId: String,
    val providerId: String,
    val type: String,
    val label: String,
    val value: String,
    val normalizedValue: String,
    val sourceUrl: String,
    val sourceRecordId: String = "",
    /** FR-05: when we fetched it. Never presented as a publication time. */
    val retrievedAt: String,
    /** FR-05: only when the provider states one. */
    val publishedAt: String? = null,
    val reviewStatus: ReviewStatus = ReviewStatus.UNREVIEWED,
    val favorite: Boolean = false,
)

@Entity(
    tableName = "evidence",
    foreignKeys = [ForeignKey(Finding::class, ["id"], ["findingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("findingId")],
)
@Serializable
data class Evidence(
    @PrimaryKey val id: String = newId(),
    val findingId: String,
    val excerpt: String,
    /** File name under filesDir/raw, only when raw retention is on (DATA-02). */
    val rawResponseRef: String? = null,
    val mediaType: String = "application/json",
    /** DATA-03: detects changes to the local copy; it does not prove the content is true. */
    val sha256: String,
    val capturedAt: String,
)

@Entity(
    tableName = "relation",
    foreignKeys = [ForeignKey(Investigation::class, ["id"], ["investigationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("investigationId")],
)
@Serializable
data class Relation(
    @PrimaryKey val id: String = newId(),
    val investigationId: String,
    /** Id of a Target or a Finding. */
    val fromId: String,
    val toId: String,
    val type: String,
    val evidenceId: String? = null,
    val assertionType: AssertionType,
    val note: String = "",
    val createdAt: String = Time.nowIso(),
)

@Entity(
    tableName = "note",
    foreignKeys = [ForeignKey(Investigation::class, ["id"], ["investigationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("investigationId"), Index("findingId")],
)
@Serializable
data class Note(
    @PrimaryKey val id: String = newId(),
    val investigationId: String,
    val findingId: String? = null,
    val content: String,
    val updatedAt: String = Time.nowIso(),
)

@Entity(
    tableName = "tag",
    foreignKeys = [ForeignKey(Investigation::class, ["id"], ["investigationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("investigationId"), Index(value = ["findingId", "label"], unique = true)],
)
@Serializable
data class Tag(
    @PrimaryKey val id: String = newId(),
    val investigationId: String,
    val findingId: String,
    val label: String,
    val updatedAt: String = Time.nowIso(),
)

@Entity(tableName = "provider_settings")
data class ProviderSettings(
    @PrimaryKey val providerId: String,
    val enabled: Boolean = true,
    /** Name of the encrypted entry in [io.github.smyrna_pergamum.osintbook.security.KeyVault]; never the key itself. */
    val credentialReference: String? = null,
    /** NET-05: our own counter, shown as such; it is not the provider's exact quota. */
    val quotaState: String? = null,
    val requestsToday: Int = 0,
    val dayKey: String = "",
    /** NET-04: epoch millis before which no request may go out (from Retry-After or a reset header). */
    val blockedUntil: Long = 0,
    val configVersion: Int = 1,
)
