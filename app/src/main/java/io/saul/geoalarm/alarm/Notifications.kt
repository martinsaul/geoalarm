package io.saul.geoalarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.saul.geoalarm.R

object Notifications {
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_REMINDERS = "reminders"
    /** Silent on purpose: AlarmRingService plays the sound itself on the alarm stream, with a ramp. */
    const val CHANNEL_ALARMS = "alarm_ring"

    const val TRACKING_ID = 1
    const val RINGING_ID = 2

    /** Per-fence notification id, so each fence has at most one alert showing. */
    fun fenceNotificationId(fenceId: Long) = 1_000 + fenceId.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Pre-release channel from an early GEO-4 build had different settings; channels are immutable.
        nm.deleteNotificationChannel("alarms")
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TRACKING, context.getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_ALARMS, context.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                },
            ),
        )
    }
}
