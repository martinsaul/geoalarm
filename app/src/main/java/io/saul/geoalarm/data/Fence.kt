package io.saul.geoalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeliveryMode { ALARM, REMINDER }

@Entity(tableName = "fences")
data class Fence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val onEnter: Boolean = true,
    val onExit: Boolean = false,
    val mode: DeliveryMode = DeliveryMode.ALARM,
    val enabled: Boolean = true,
    val note: String = "",
    /** false = one-shot: the fence disables itself after it fires once. */
    val repeat: Boolean = true,
    /** Bit 0 = Monday ... bit 6 = Sunday. [Schedule.ALL_DAYS] means every day. */
    val activeDays: Int = Schedule.ALL_DAYS,
    /** Optional daily window in minutes after midnight; both null = all day. End < start wraps past midnight. */
    val windowStartMinutes: Int? = null,
    val windowEndMinutes: Int? = null,
    /** Alarm sound (content URI); null = system default alarm. */
    val soundUri: String? = null,
)
