package io.github.smyrna_pergamum.osintbook.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class InvestigationSummary(
    val id: String,
    val title: String,
    val description: String,
    val updatedAt: String,
    val archivedAt: String?,
    val targetCount: Int,
    val findingCount: Int,
)

data class FindingRow(
    val id: String,
    val investigationId: String,
    val targetId: String,
    val providerId: String,
    val type: String,
    val label: String,
    val value: String,
    val sourceUrl: String,
    val retrievedAt: String,
    val publishedAt: String?,
    val reviewStatus: ReviewStatus,
    val favorite: Boolean,
    val targetValue: String,
    val queryRunId: String,
    val runStartedAt: String,
)

@Dao
interface InvestigationDao {
    @Query(
        """SELECT i.id, i.title, i.description, i.updatedAt, i.archivedAt,
           (SELECT COUNT(*) FROM target t WHERE t.investigationId = i.id) AS targetCount,
           (SELECT COUNT(*) FROM finding f WHERE f.investigationId = i.id) AS findingCount
           FROM investigation i
           WHERE ((:archived = 1 AND i.archivedAt IS NOT NULL) OR (:archived = 0 AND i.archivedAt IS NULL))
             AND (:q = '' OR i.title LIKE '%' || :q || '%' OR i.description LIKE '%' || :q || '%'
                  OR EXISTS (SELECT 1 FROM finding f WHERE f.investigationId = i.id AND f.value LIKE '%' || :q || '%')
                  OR EXISTS (SELECT 1 FROM target t WHERE t.investigationId = i.id AND t.normalizedValue LIKE '%' || :q || '%'))
           ORDER BY i.updatedAt DESC"""
    )
    fun observeSummaries(q: String, archived: Boolean): Flow<List<InvestigationSummary>>

    @Query("SELECT * FROM investigation WHERE id = :id")
    fun observe(id: String): Flow<Investigation?>

    @Query("SELECT * FROM investigation WHERE id = :id")
    suspend fun get(id: String): Investigation?

    @Insert
    suspend fun insert(i: Investigation)

    @Update
    suspend fun update(i: Investigation)

    @Query("UPDATE investigation SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: String = Time.nowIso())

    @Delete
    suspend fun delete(i: Investigation)

    @Query("DELETE FROM investigation")
    suspend fun deleteAll()
}

@Dao
interface TargetDao {
    @Query("SELECT * FROM target WHERE investigationId = :invId ORDER BY createdAt")
    fun observeFor(invId: String): Flow<List<Target>>

    @Query("SELECT * FROM target WHERE investigationId = :invId ORDER BY createdAt")
    suspend fun listFor(invId: String): List<Target>

    @Query("SELECT * FROM target WHERE id = :id")
    suspend fun get(id: String): Target?

    @Insert
    suspend fun insert(t: Target)

    @Insert
    suspend fun insertAll(t: List<Target>)

    @Delete
    suspend fun delete(t: Target)
}

@Dao
interface QueryRunDao {
    @Query("SELECT * FROM query_run WHERE targetId = :targetId ORDER BY startedAt DESC")
    fun observeFor(targetId: String): Flow<List<QueryRun>>

    @Query("SELECT q.* FROM query_run q JOIN target t ON t.id = q.targetId WHERE t.investigationId = :invId ORDER BY q.startedAt")
    suspend fun listForInvestigation(invId: String): List<QueryRun>

    @Query("SELECT q.* FROM query_run q JOIN target t ON t.id = q.targetId WHERE t.investigationId = :invId ORDER BY q.startedAt")
    fun observeForInvestigation(invId: String): Flow<List<QueryRun>>

    @Insert
    suspend fun insert(r: QueryRun)

    @Insert
    suspend fun insertAll(r: List<QueryRun>)

    @Update
    suspend fun update(r: QueryRun)

    /** DATA-04: runs interrupted by process death are marked, not left as RUNNING. */
    @Query("UPDATE query_run SET status = 'CANCELLED', errorCode = 'interrupted' WHERE status IN ('PENDING', 'RUNNING')")
    suspend fun markInterrupted()
}

@Dao
interface FindingDao {
    @Query(
        """SELECT f.id, f.investigationId, f.targetId, f.providerId, f.type, f.label, f.value, f.sourceUrl,
                  f.retrievedAt, f.publishedAt, f.reviewStatus, f.favorite, t.displayValue AS targetValue,
                  f.queryRunId, q.startedAt AS runStartedAt
           FROM finding f JOIN target t ON t.id = f.targetId JOIN query_run q ON q.id = f.queryRunId
           WHERE f.investigationId = :invId ORDER BY q.startedAt DESC, t.displayValue, f.providerId, f.type, f.label, f.value"""
    )
    fun observeRows(invId: String): Flow<List<FindingRow>>

    @Query("SELECT * FROM finding WHERE investigationId = :invId ORDER BY providerId, type, label, value")
    suspend fun listFor(invId: String): List<Finding>

    @Query("SELECT * FROM finding WHERE investigationId = :invId")
    fun observeFor(invId: String): Flow<List<Finding>>

    @Query("SELECT * FROM finding WHERE id = :id")
    fun observe(id: String): Flow<Finding?>

    @Query("SELECT * FROM finding WHERE id = :id")
    suspend fun get(id: String): Finding?

    /** FR-06: returns -1 when the dedupe key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(f: Finding): Long

    @Insert
    suspend fun insertAll(f: List<Finding>)

    @Query("UPDATE finding SET reviewStatus = :status WHERE id = :id")
    suspend fun setReview(id: String, status: ReviewStatus)

    @Query("UPDATE finding SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("SELECT COUNT(*) FROM finding")
    suspend fun count(): Int
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence WHERE findingId = :findingId")
    fun observeFor(findingId: String): Flow<List<Evidence>>

    @Query("SELECT e.* FROM evidence e JOIN finding f ON f.id = e.findingId WHERE f.investigationId = :invId")
    suspend fun listForInvestigation(invId: String): List<Evidence>

    @Query("SELECT rawResponseRef FROM evidence e JOIN finding f ON f.id = e.findingId WHERE f.investigationId = :invId AND rawResponseRef IS NOT NULL")
    suspend fun rawRefsFor(invId: String): List<String>

    @Query("SELECT rawResponseRef FROM evidence WHERE rawResponseRef IS NOT NULL")
    suspend fun allRawRefs(): List<String>

    @Query("UPDATE evidence SET rawResponseRef = NULL WHERE rawResponseRef = :ref")
    suspend fun clearRawRef(ref: String)

    @Insert
    suspend fun insert(e: Evidence)

    @Insert
    suspend fun insertAll(e: List<Evidence>)
}

@Dao
interface RelationDao {
    @Query("SELECT * FROM relation WHERE investigationId = :invId ORDER BY createdAt")
    fun observeFor(invId: String): Flow<List<Relation>>

    @Query("SELECT * FROM relation WHERE investigationId = :invId ORDER BY createdAt")
    suspend fun listFor(invId: String): List<Relation>

    @Insert
    suspend fun insert(r: Relation)

    @Insert
    suspend fun insertAll(r: List<Relation>)

    @Delete
    suspend fun delete(r: Relation)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE investigationId = :invId ORDER BY updatedAt DESC")
    fun observeFor(invId: String): Flow<List<Note>>

    @Query("SELECT * FROM note WHERE findingId = :findingId ORDER BY updatedAt DESC")
    fun observeForFinding(findingId: String): Flow<List<Note>>

    @Query("SELECT * FROM note WHERE investigationId = :invId ORDER BY updatedAt")
    suspend fun listFor(invId: String): List<Note>

    @Upsert
    suspend fun upsert(n: Note)

    @Insert
    suspend fun insertAll(n: List<Note>)

    @Delete
    suspend fun delete(n: Note)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag WHERE findingId = :findingId ORDER BY label")
    fun observeForFinding(findingId: String): Flow<List<Tag>>

    @Query("SELECT * FROM tag WHERE investigationId = :invId ORDER BY label")
    suspend fun listFor(invId: String): List<Tag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(t: List<Tag>)

    @Delete
    suspend fun delete(t: Tag)
}

@Dao
interface ProviderSettingsDao {
    @Query("SELECT * FROM provider_settings")
    fun observeAll(): Flow<List<ProviderSettings>>

    @Query("SELECT * FROM provider_settings WHERE providerId = :id")
    suspend fun get(id: String): ProviderSettings?

    @Upsert
    suspend fun upsert(s: ProviderSettings)
}

@Database(
    entities = [
        Investigation::class, Target::class, QueryRun::class, Finding::class, Evidence::class,
        Relation::class, Note::class, Tag::class, ProviderSettings::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun investigations(): InvestigationDao
    abstract fun targets(): TargetDao
    abstract fun queryRuns(): QueryRunDao
    abstract fun findings(): FindingDao
    abstract fun evidence(): EvidenceDao
    abstract fun relations(): RelationDao
    abstract fun notes(): NoteDao
    abstract fun tags(): TagDao
    abstract fun providerSettings(): ProviderSettingsDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "osint.db")
                // Future schema changes ship explicit migrations (DATA-04); never destructive fallback.
                .build()
    }
}
