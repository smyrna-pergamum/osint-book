package io.github.smyrna_pergamum.osintbook.data

import androidx.room.withTransaction
import io.github.smyrna_pergamum.osintbook.BuildConfig
import io.github.smyrna_pergamum.osintbook.domain.RawStore
import io.github.smyrna_pergamum.osintbook.export.InvestigationBundle
import io.github.smyrna_pergamum.osintbook.export.JsonTransfer
import io.github.smyrna_pergamum.osintbook.security.KeyVault
import io.github.smyrna_pergamum.osintbook.domain.QueryEngine
import io.github.smyrna_pergamum.osintbook.providers.Providers

/** Local operations on investigations. Nothing here touches the network (AT-02, AT-10). */
class Repository(
    val db: AppDatabase,
    private val raw: RawStore,
    private val vault: KeyVault,
) {
    suspend fun createInvestigation(title: String, description: String): Investigation =
        Investigation(title = title.trim().take(300), description = description.trim()).also { db.investigations().insert(it) }

    suspend fun rename(inv: Investigation, title: String, description: String) =
        db.investigations().update(inv.copy(title = title.trim().take(300), description = description.trim(), updatedAt = Time.nowIso()))

    suspend fun setArchived(inv: Investigation, archived: Boolean) =
        db.investigations().update(inv.copy(archivedAt = if (archived) Time.nowIso() else null, updatedAt = Time.nowIso()))

    /** FR-10: removes the investigation, its notes, findings and local raw copies. Exported files are the user's to delete. */
    suspend fun delete(inv: Investigation) {
        val refs = db.evidence().rawRefsFor(inv.id)
        db.investigations().delete(inv)
        val stillUsed = db.evidence().allRawRefs().toSet()
        refs.filterNot { it in stillUsed }.forEach { raw.delete(it) }
    }

    /** FR-10: a full independent copy with new ids. */
    suspend fun copy(inv: Investigation, titleSuffix: String): Investigation {
        val bundle = JsonTransfer.decode(JsonTransfer.encode(bundle(inv.id)).toByteArray())
        val copy = bundle.copy(investigation = bundle.investigation.copy(title = (inv.title + titleSuffix).take(300)))
        insertBundle(copy)
        return copy.investigation
    }

    suspend fun bundle(invId: String): InvestigationBundle {
        val inv = db.investigations().get(invId) ?: error("missing")
        return InvestigationBundle(
            appVersion = BuildConfig.VERSION_NAME,
            investigation = inv,
            targets = db.targets().listFor(invId),
            queryRuns = db.queryRuns().listForInvestigation(invId),
            findings = db.findings().listFor(invId),
            evidence = db.evidence().listForInvestigation(invId),
            relations = db.relations().listFor(invId),
            notes = db.notes().listFor(invId),
            tags = db.tags().listFor(invId),
        )
    }

    /** DATA-04: all or nothing. */
    suspend fun insertBundle(b: InvestigationBundle) = db.withTransaction {
        db.investigations().insert(b.investigation.copy(updatedAt = Time.nowIso()))
        db.targets().insertAll(b.targets)
        db.queryRuns().insertAll(b.queryRuns)
        db.findings().insertAll(b.findings)
        db.evidence().insertAll(b.evidence)
        db.relations().insertAll(b.relations)
        db.notes().insertAll(b.notes)
        db.tags().insertAll(b.tags)
    }

    suspend fun importJson(bytes: ByteArray): Investigation {
        val b = JsonTransfer.decode(bytes)
        insertBundle(b)
        return b.investigation
    }

    suspend fun addTarget(t: Target) {
        db.targets().insert(t)
        db.investigations().touch(t.investigationId)
    }

    suspend fun deleteTarget(t: Target) = db.targets().delete(t)

    suspend fun saveNote(n: Note) {
        db.notes().upsert(n.copy(updatedAt = Time.nowIso()))
        db.investigations().touch(n.investigationId)
    }

    suspend fun deleteNote(n: Note) = db.notes().delete(n)

    suspend fun addTag(f: Finding, label: String) {
        val l = label.trim().take(60)
        if (l.isNotEmpty()) db.tags().insert(Tag(investigationId = f.investigationId, findingId = f.id, label = l))
    }

    suspend fun removeTag(t: Tag) = db.tags().delete(t)

    suspend fun setReview(id: String, s: ReviewStatus) = db.findings().setReview(id, s)

    suspend fun setFavorite(id: String, fav: Boolean) = db.findings().setFavorite(id, fav)

    suspend fun addRelation(r: Relation) = db.relations().insert(r)

    suspend fun deleteRelation(r: Relation) = db.relations().delete(r)

    suspend fun setProviderEnabled(id: String, enabled: Boolean) {
        val s = db.providerSettings().get(id) ?: ProviderSettings(id)
        db.providerSettings().upsert(s.copy(enabled = enabled))
    }

    suspend fun saveKey(id: String, key: String) {
        val ref = QueryEngine.refFor(id)
        vault.put(ref, key.trim())
        val s = db.providerSettings().get(id) ?: ProviderSettings(id)
        db.providerSettings().upsert(s.copy(credentialReference = ref))
    }

    suspend fun deleteKey(id: String) {
        val ref = QueryEngine.refFor(id)
        vault.remove(ref)
        val s = db.providerSettings().get(id) ?: ProviderSettings(id)
        db.providerSettings().upsert(s.copy(credentialReference = null))
    }

    fun hasKey(id: String) = vault.has(QueryEngine.refFor(id))

    suspend fun ensureProviderRows() {
        Providers.all.forEach { p -> if (db.providerSettings().get(p.id) == null) db.providerSettings().upsert(ProviderSettings(p.id)) }
    }

    /** DATA-02: raw copies older than 30 days go; user notes are never deleted automatically. */
    suspend fun purgeOldRaw() {
        raw.purgeOlderThan(RawStore.RETENTION_MS).forEach { db.evidence().clearRawRef(it) }
    }

    suspend fun clearRaw() {
        db.evidence().allRawRefs().forEach { db.evidence().clearRawRef(it) }
        raw.clear()
    }

    fun rawSize() = raw.sizeBytes()

    fun verifyRaw(ref: String, sha256: String) = raw.verify(ref, sha256)

    suspend fun deleteEverything() {
        db.investigations().deleteAll()
        raw.clear()
    }

    fun deleteAllKeys() = vault.clear()
}
