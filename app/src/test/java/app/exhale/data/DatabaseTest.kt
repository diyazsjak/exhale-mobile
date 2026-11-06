package app.exhale.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.exhale.domain.Trigger
import app.exhale.domain.Triggers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The trigger table: seeded on a fresh install, and added to a v1 database without touching its logs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun freshInstall_seedsBuiltIns() = runBlocking {
        val db = ExhaleDatabase.create(context)
        assertEquals(Triggers.defaults, db.triggers().observeAll().first().map { it.toDomain() })
        db.close()
    }

    @Test fun migration1to2_keepsLogsAndSeedsBuiltIns() = runBlocking {
        writeVersion1 { v1 ->
            v1.execSQL("INSERT INTO smoke_log (atMillis, trigger, fromUrge) VALUES (1000, 'COFFEE', 0)")
            v1.execSQL("INSERT INTO smoke_log (atMillis, trigger, fromUrge) VALUES (2000, NULL, 0)")
            v1.execSQL("INSERT INTO urge (startedMillis, endedMillis, outcome, trigger) VALUES (1, 2, 'SURVIVED', 'STRESS')")
        }
        // Opening through the app's own builder runs the migration and Room's schema check.
        val db = ExhaleDatabase.create(context)
        val triggers = db.triggers().observeAll().first().map { it.toDomain() }
        assertEquals(Triggers.defaults, triggers)
        val byKey = triggers.associate { it.key to it.trigger }
        assertEquals(listOf(Trigger.COFFEE, null), db.logs().observeAll().first().map { it.toDomain(byKey).trigger })
        assertEquals(Trigger.STRESS, db.urges().observeAll().first().single().toDomain(byKey).trigger)
        db.close()
    }

    @Test fun countTagged_countsLogsAndCravings_evenUnknownKeys() = runBlocking {
        val db = ExhaleDatabase.create(context)
        db.logs().insert(SmokeLogEntity(atMillis = 1, trigger = "custom:gone", fromUrge = false))
        db.logs().insert(SmokeLogEntity(atMillis = 2, trigger = "COFFEE", fromUrge = false))
        db.urges().insert(UrgeEntity(startedMillis = 1, endedMillis = 2, outcome = "SURVIVED", trigger = "custom:gone"))
        assertEquals(2, db.logs().countTagged("custom:gone"))
        assertEquals(1, db.logs().countTagged("COFFEE"))
        assertEquals(0, db.logs().countTagged("STRESS"))
        db.close()
    }

    /** Creates exhale.db exactly as version 1 left it, from the exported schema. */
    private fun writeVersion1(fill: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/app.exhale.data.ExhaleDatabase/1.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath("exhale.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 1
            fill(db)
        }
    }
}
