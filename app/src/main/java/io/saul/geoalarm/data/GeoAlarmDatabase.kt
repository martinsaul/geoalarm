package io.saul.geoalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Fence::class, TriggerLog::class], version = 2, exportSchema = true)
abstract class GeoAlarmDatabase : RoomDatabase() {
    abstract fun fenceDao(): FenceDao
    abstract fun triggerLogDao(): TriggerLogDao

    companion object {
        @Volatile private var instance: GeoAlarmDatabase? = null

        /** v1 (GEO-2) -> v2 (GEO-5): schedule, one-shot, sound, trigger history. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fences ADD COLUMN `repeat` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE fences ADD COLUMN activeDays INTEGER NOT NULL DEFAULT ${Schedule.ALL_DAYS}")
                db.execSQL("ALTER TABLE fences ADD COLUMN windowStartMinutes INTEGER")
                db.execSQL("ALTER TABLE fences ADD COLUMN windowEndMinutes INTEGER")
                db.execSQL("ALTER TABLE fences ADD COLUMN soundUri TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS trigger_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fenceId INTEGER NOT NULL, label TEXT NOT NULL, " +
                        "entered INTEGER NOT NULL, outcome TEXT NOT NULL, timestampMillis INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): GeoAlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GeoAlarmDatabase::class.java, "geoalarm.db")
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
