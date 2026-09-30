package io.saul.geoalarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FenceDao {
    @Query("SELECT * FROM fences ORDER BY id")
    fun observeAll(): Flow<List<Fence>>

    @Query("SELECT * FROM fences WHERE enabled = 1")
    suspend fun enabled(): List<Fence>

    @Upsert
    suspend fun upsert(fence: Fence): Long

    @Delete
    suspend fun delete(fence: Fence)
}
