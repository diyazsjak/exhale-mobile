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

@Database(entities = [SmokeLogEntity::class, UrgeEntity::class], version = 1, exportSchema = true)
abstract class ExhaleDatabase : RoomDatabase() {
    abstract fun logs(): LogDao
    abstract fun urges(): UrgeDao

    companion object {
        fun create(context: Context): ExhaleDatabase =
            Room.databaseBuilder(context, ExhaleDatabase::class.java, "exhale.db").build()
    }
}
