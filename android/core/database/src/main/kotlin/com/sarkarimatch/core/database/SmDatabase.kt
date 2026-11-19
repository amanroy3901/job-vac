package com.sarkarimatch.core.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/*
 * Room stub (data-layer.md §3). Room is the only copy of the profile and the user's actions, so there is NO
 * destructive migration fallback. Dates are stored as ISO strings (IST calendar dates, never via UTC) until the
 * converters land with FE-10.
 */

/** The single local profile row (id = 1); JSON of the on-device profile. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val version: Int,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

/** Public catalog post with its published rule document (rule.schema.json, review/source stripped). */
@Entity(tableName = "catalog_posts", indices = [Index("notice_id")])
data class CatalogPostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "notice_id") val noticeId: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    @ColumnInfo(name = "evaluator_min_version") val evaluatorMinVersion: Int,
    @ColumnInfo(name = "rule_json") val ruleJson: String,
)

/** A local match (m_ + post suffix): only eligible / check_yourself verdicts are stored. */
@Entity(tableName = "matches", indices = [Index("status"), Index("last_date")])
data class MatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    val verdict: String,
    val status: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    @ColumnInfo(name = "fee_paise") val feePaise: Long?,
    @ColumnInfo(name = "last_date") val lastDate: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE status NOT IN ('not_interested', 'expired', 'withdrawn') ORDER BY last_date")
    fun observeOpen(): Flow<List<MatchEntity>>

    @Upsert
    suspend fun upsert(matches: List<MatchEntity>)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Database(
    entities = [ProfileEntity::class, CatalogPostEntity::class, MatchEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SmDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao
    abstract fun profileDao(): ProfileDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SmDatabase =
        Room.databaseBuilder(context, SmDatabase::class.java, "sm.db").build()

    @Provides
    fun matchDao(db: SmDatabase): MatchDao = db.matchDao()

    @Provides
    fun profileDao(db: SmDatabase): ProfileDao = db.profileDao()
}
