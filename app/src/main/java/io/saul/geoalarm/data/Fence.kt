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
)
