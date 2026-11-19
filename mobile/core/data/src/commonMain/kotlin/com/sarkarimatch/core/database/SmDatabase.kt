package com.sarkarimatch.core.database

import androidx.room.ColumnInfo
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

/*
 * Room KMP (data-layer.md §3), the same schema on Android, iOS and the desktop dev target with the bundled SQLite
 * driver. Room is the only copy of the profile and the user's actions, so there is NO destructive migration fallback.
 * Dates are stored as ISO strings (IST calendar dates, never via UTC); instants as epoch millis. Schemas are exported
 * to core/data/schemas. Version 1 is still unreleased (no installs), so it was extended in place for FE-13/FE-16.
 */

const val DATABASE_NAME = "sm.db"

// ---------------------------------------------------------------- catalog (public data, replaced by batches)

/** One catalog notice (`Notice` minus posts, as JSON for the fetched locale set). */
@Entity(tableName = "catalog_notices")
data class CatalogNoticeEntity(
    @PrimaryKey val id: String,
    val version: Int,
    @ColumnInfo(name = "catalog_seq") val catalogSeq: Long,
    val status: String,
    @ColumnInfo(name = "apply_end") val applyEnd: String?,
    @ColumnInfo(name = "apply_end_time") val applyEndTime: String?,
    @ColumnInfo(name = "new_match_paused") val newMatchPaused: Boolean,
    @ColumnInfo(name = "body_json") val bodyJson: String,
)

/** Public catalog post with its published rule document (rule.schema.json, review/quotes stripped). */
@Entity(tableName = "catalog_posts", indices = [Index("notice_id")])
data class CatalogPostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "notice_id") val noticeId: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    @ColumnInfo(name = "evaluator_min_version") val evaluatorMinVersion: Int,
    val status: String,
    @ColumnInfo(name = "rule_json") val ruleJson: String,
    @ColumnInfo(name = "post_json") val postJson: String,
)

/** Sync position per locale set (`en,hi`, …). */
@Entity(tableName = "catalog_state")
data class CatalogStateEntity(
    @PrimaryKey val locales: String,
    val seq: Long,
    @ColumnInfo(name = "snapshot_seq") val snapshotSeq: Long,
    @ColumnInfo(name = "synced_at") val syncedAtEpochMs: Long,
    @ColumnInfo(name = "server_offset_ms") val serverOffsetMs: Long,
)

// ---------------------------------------------------------------- the user's data (device only)

/** The single local profile row (id = 1); JSON of the on-device profile (`LocalProfile`). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val version: Int,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

/** A local match (m_ + post suffix): only eligible / check_yourself verdicts are stored. */
@Entity(tableName = "matches", indices = [Index("status"), Index("last_date"), Index("notice_id")])
data class MatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "notice_id") val noticeId: String,
    val verdict: String,
    /** new | seen | snoozed | applied | not_interested | expired | withdrawn */
    val status: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    /** Comma-separated `ReasonCode` ordinals of the non-pass lines. */
    @ColumnInfo(name = "reason_codes") val reasonCodes: String,
    @ColumnInfo(name = "fee_paise") val feePaise: Long?,
    @ColumnInfo(name = "last_date") val lastDate: String,
    @ColumnInfo(name = "last_date_time") val lastDateTime: String?,
    @ColumnInfo(name = "matched_at") val matchedAtEpochMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

/** Append-only local action log (undo, backup merge, opt-in counters). */
@Entity(tableName = "match_actions", indices = [Index("match_id")])
data class MatchActionEntity(
    @PrimaryKey @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "match_id") val matchId: String,
    val type: String,
    @ColumnInfo(name = "occurred_at") val occurredAtEpochMs: Long,
    @ColumnInfo(name = "previous_status") val previousStatus: String,
    val payload: String?,
)

/** A tracked application (a_ + post suffix), created by "Applied". */
@Entity(tableName = "applications", indices = [Index("notice_id")])
data class ApplicationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "notice_id") val noticeId: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "applied_at") val appliedAtEpochMs: Long,
    @ColumnInfo(name = "fee_paid") val feePaid: Boolean?,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

/** The account this install is signed in to (single row; absent for guests without an account). */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "auth_provider") val authProvider: String,
    @ColumnInfo(name = "billing_account_ref") val billingAccountRef: String?,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

/** Last `Entitlement` from the server, with its signed token (single row). */
@Entity(tableName = "entitlement")
data class EntitlementEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    @ColumnInfo(name = "fetched_at") val fetchedAtEpochMs: Long,
)

/** Last `AppConfig` and its ETag (single row). */
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val etag: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAtEpochMs: Long,
)

// ---------------------------------------------------------------- DAOs

@Dao
abstract class CatalogDao {
    @Query("SELECT * FROM catalog_state WHERE locales = :locales")
    abstract suspend fun state(locales: String): CatalogStateEntity?

    @Upsert
    abstract suspend fun saveState(state: CatalogStateEntity)

    @Query("SELECT * FROM catalog_notices")
    abstract suspend fun notices(): List<CatalogNoticeEntity>

    @Query("SELECT * FROM catalog_notices WHERE id = :id")
    abstract suspend fun notice(id: String): CatalogNoticeEntity?

    @Query("SELECT * FROM catalog_posts")
    abstract suspend fun posts(): List<CatalogPostEntity>

    @Query("SELECT * FROM catalog_posts WHERE id = :id")
    abstract suspend fun post(id: String): CatalogPostEntity?

    @Query("SELECT * FROM catalog_posts WHERE id = :id")
    abstract fun observePost(id: String): Flow<CatalogPostEntity?>

    @Query("SELECT COUNT(*) FROM catalog_posts")
    abstract fun observePostCount(): Flow<Int>

    @Upsert
    protected abstract suspend fun upsertNotices(notices: List<CatalogNoticeEntity>)

    @Upsert
    protected abstract suspend fun upsertPosts(posts: List<CatalogPostEntity>)

    @Query("DELETE FROM catalog_posts WHERE notice_id IN (:noticeIds)")
    protected abstract suspend fun deletePostsOf(noticeIds: List<String>)

    @Query("DELETE FROM catalog_notices WHERE id IN (:noticeIds)")
    protected abstract suspend fun deleteNotices(noticeIds: List<String>)

    @Query("DELETE FROM catalog_posts")
    protected abstract suspend fun deleteAllPosts()

    @Query("DELETE FROM catalog_notices")
    protected abstract suspend fun deleteAllNotices()

    /** Applies one snapshot ([replaceAll]) or batch in one transaction: notices are replaced whole. */
    @Transaction
    open suspend fun apply(
        notices: List<CatalogNoticeEntity>,
        posts: List<CatalogPostEntity>,
        removedNoticeIds: List<String>,
        replaceAll: Boolean,
        state: CatalogStateEntity,
    ) {
        if (replaceAll) {
            deleteAllPosts()
            deleteAllNotices()
        } else {
            val touched = notices.map { it.id } + removedNoticeIds
            if (touched.isNotEmpty()) deletePostsOf(touched)
            if (removedNoticeIds.isNotEmpty()) deleteNotices(removedNoticeIds)
        }
        upsertNotices(notices)
        upsertPosts(posts)
        saveState(state)
    }
}

@Dao
abstract class MatchDao {
    @Query("SELECT * FROM matches WHERE status NOT IN ('applied', 'not_interested', 'expired', 'withdrawn') ORDER BY last_date")
    abstract fun observeOpen(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches")
    abstract suspend fun all(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :id")
    abstract suspend fun get(id: String): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :id")
    abstract fun observe(id: String): Flow<MatchEntity?>

    @Upsert
    abstract suspend fun upsert(matches: List<MatchEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAction(action: MatchActionEntity)

    @Upsert
    protected abstract suspend fun upsertApplication(application: ApplicationEntity)

    @Query("DELETE FROM applications WHERE match_id = :matchId")
    protected abstract suspend fun deleteApplicationOf(matchId: String)

    @Query("SELECT * FROM match_actions WHERE match_id = :matchId ORDER BY occurred_at")
    abstract suspend fun actionsOf(matchId: String): List<MatchActionEntity>

    @Query("SELECT * FROM applications ORDER BY applied_at DESC")
    abstract fun observeApplications(): Flow<List<ApplicationEntity>>

    @Query("SELECT * FROM applications")
    abstract suspend fun applications(): List<ApplicationEntity>

    /** One transaction: status change + action log (+ application row for Applied, removed again on undo). */
    @Transaction
    open suspend fun record(updated: MatchEntity, action: MatchActionEntity, application: ApplicationEntity?, removeApplication: Boolean) {
        upsert(listOf(updated))
        insertAction(action)
        if (application != null) upsertApplication(application)
        if (removeApplication) deleteApplicationOf(updated.id)
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM account WHERE id = 1")
    suspend fun account(): AccountEntity?

    @Upsert
    suspend fun saveAccount(account: AccountEntity)

    @Query("SELECT * FROM entitlement WHERE id = 1")
    fun observeEntitlement(): Flow<EntitlementEntity?>

    @Upsert
    suspend fun saveEntitlement(entitlement: EntitlementEntity)

    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun config(): AppConfigEntity?

    @Query("SELECT * FROM app_config WHERE id = 1")
    fun observeConfig(): Flow<AppConfigEntity?>

    @Upsert
    suspend fun saveConfig(config: AppConfigEntity)
}

@Database(
    entities = [
        CatalogNoticeEntity::class, CatalogPostEntity::class, CatalogStateEntity::class, ProfileEntity::class,
        MatchEntity::class, MatchActionEntity::class, ApplicationEntity::class, AccountEntity::class,
        EntitlementEntity::class, AppConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(SmDatabaseConstructor::class)
abstract class SmDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun matchDao(): MatchDao
    abstract fun profileDao(): ProfileDao
    abstract fun accountDao(): AccountDao
}

/** Generated by Room's KSP processor for every target. */
@Suppress("KotlinNoActualForExpect")
expect object SmDatabaseConstructor : RoomDatabaseConstructor<SmDatabase> {
    override fun initialize(): SmDatabase
}

/** Finishes a platform builder (Android: Context + name; iOS/desktop: file path). */
fun buildSmDatabase(builder: RoomDatabase.Builder<SmDatabase>): SmDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
