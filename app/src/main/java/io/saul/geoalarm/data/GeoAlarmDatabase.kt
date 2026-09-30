package io.saul.geoalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Fence::class], version = 1, exportSchema = false)
abstract class GeoAlarmDatabase : RoomDatabase() {
    abstract fun fenceDao(): FenceDao

    companion object {
        @Volatile private var instance: GeoAlarmDatabase? = null

        fun get(context: Context): GeoAlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GeoAlarmDatabase::class.java, "geoalarm.db")
                    .build().also { instance = it }
            }
    }
}
