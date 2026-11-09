package app.exhale.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.exhale.domain.Triggers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "smoke_log")
data class SmokeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val trigger: String?,
    val fromUrge: Boolean,
)

@Entity(tableName = "urge")
data class UrgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedMillis: Long,
    val endedMillis: Long,
    val outcome: String,
    val trigger: String?,
)

/** The user's trigger list in chip order ([position]); hidden ones come after the shown ones. */
@Entity(tableName = "user_trigger")
data class TriggerEntity(
    /** A built-in's name ("COFFEE") or a custom key; logs and urges store it in their `trigger` column. */
    @PrimaryKey val id: String,
    val label: String,
    val position: Int,
    val hidden: Boolean,
)

@Dao
interface LogDao {
    @Query("SELECT * FROM smoke_log ORDER BY atMillis ASC")
    fun observeAll(): Flow<List<SmokeLogEntity>>

    @Insert
    suspend fun insert(log: SmokeLogEntity): Long

    @Insert
    suspend fun insertAll(logs: List<SmokeLogEntity>)

    @Query("DELETE FROM smoke_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE smoke_log SET trigger = :trigger WHERE id = :id")
    suspend fun setTrigger(id: Long, trigger: String?)

    @Query("UPDATE smoke_log SET atMillis = :atMillis, trigger = :trigger WHERE id = :id")
    suspend fun update(id: Long, atMillis: Long, trigger: String?)

    /** How many logs and cravings carry the trigger [key], resolvable or not. */
    @Query("SELECT (SELECT COUNT(*) FROM smoke_log WHERE `trigger` = :key) + (SELECT COUNT(*) FROM urge WHERE `trigger` = :key)")
    suspend fun countTagged(key: String): Int

    @Query("SELECT COUNT(*) FROM smoke_log WHERE atMillis >= :from AND atMillis < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("DELETE FROM smoke_log")
    suspend fun clear()
}

@Dao
interface UrgeDao {
    @Query("SELECT * FROM urge ORDER BY startedMillis ASC")
    fun observeAll(): Flow<List<UrgeEntity>>

    @Insert
    suspend fun insert(urge: UrgeEntity): Long

    @Insert
    suspend fun insertAll(urges: List<UrgeEntity>)

    @Query("DELETE FROM urge")
    suspend fun clear()
}

@Dao
interface TriggerDao {
    @Query("SELECT * FROM user_trigger ORDER BY position ASC")
    fun observeAll(): Flow<List<TriggerEntity>>

    @Insert
    suspend fun insertAll(triggers: List<TriggerEntity>)

    @Query("DELETE FROM user_trigger")
    suspend fun clear()

    /** Saves the whole list at once; it is short and its order is the point. */
    @Transaction
    suspend fun replaceAll(triggers: List<TriggerEntity>) {
        clear()
        insertAll(triggers)
    }
}

@Database(
    entities = [SmokeLogEntity::class, UrgeEntity::class, TriggerEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ExhaleDatabase : RoomDatabase() {
    abstract fun logs(): LogDao
    abstract fun urges(): UrgeDao
    abstract fun triggers(): TriggerDao

    companion object {
        fun create(context: Context): ExhaleDatabase =
            Room.databaseBuilder(context, ExhaleDatabase::class.java, "exhale.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seedTriggers(db)
                })
                .build()

        /** v2: the user's own trigger list, starting with the built-ins. Logs keep their stored names. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_trigger` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                seedTriggers(db)
            }
        }

        private fun seedTriggers(db: SupportSQLiteDatabase) {
            Triggers.defaults.forEachIndexed { i, e ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `user_trigger` (`id`, `label`, `position`, `hidden`) VALUES (?, ?, ?, 0)",
                    arrayOf<Any>(e.key, e.trigger.label, i),
                )
            }
        }
    }
}
