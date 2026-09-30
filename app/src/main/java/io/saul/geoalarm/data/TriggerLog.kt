package io.saul.geoalarm.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class TriggerOutcome { RANG, NOTIFIED, SKIPPED_SCHEDULE, DISMISSED, SNOOZED, TIMED_OUT }

/** One line of trigger history. Label is copied so history survives fence deletion. */
@Entity(tableName = "trigger_log")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fenceId: Long,
    val label: String,
    val entered: Boolean,
    val outcome: TriggerOutcome,
    val timestampMillis: Long = System.currentTimeMillis(),
)

@Dao
interface TriggerLogDao {
    @Insert
    suspend fun insert(entry: TriggerLog)

    @Query("SELECT * FROM trigger_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<TriggerLog>>

    @Query("DELETE FROM trigger_log WHERE timestampMillis < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
