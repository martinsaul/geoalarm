package io.saul.geoalarm.alarm

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.saul.geoalarm.MainActivity
import io.saul.geoalarm.R
import io.saul.geoalarm.data.DeliveryMode
import io.saul.geoalarm.data.Fence
import io.saul.geoalarm.data.GeoAlarmDatabase
import io.saul.geoalarm.data.Schedule
import io.saul.geoalarm.data.TriggerLog
import io.saul.geoalarm.data.TriggerOutcome
import io.saul.geoalarm.engine.Permissions
import io.saul.geoalarm.engine.Transition
import java.time.LocalDateTime

/** Turns an engine transition into what the user sees. Both engines call this. */
object TriggerDispatcher {
    private const val TAG = "TriggerDispatcher"

    suspend fun dispatch(context: Context, fenceId: Long, transition: Transition, now: LocalDateTime = LocalDateTime.now()) {
        val db = GeoAlarmDatabase.get(context)
        val fence = db.fenceDao().byId(fenceId) ?: return
        if (!fence.enabled) return
        val entered = transition == Transition.ENTER
        if (entered && !fence.onEnter) return
        if (!entered && !fence.onExit) return

        if (!Schedule.isActive(fence, now)) {
            Log.i(TAG, "Fence ${fence.id} $transition outside its schedule; not alerting")
            db.triggerLogDao().insert(TriggerLog(fenceId = fence.id, label = fence.label, entered = entered, outcome = TriggerOutcome.SKIPPED_SCHEDULE))
            return
        }
        Log.i(TAG, "Fence ${fence.id} '${fence.label}' $transition (${fence.mode})")
        if (!fence.repeat) db.fenceDao().upsert(fence.copy(enabled = false))

        Notifications.ensureChannels(context)
        when (fence.mode) {
            DeliveryMode.ALARM -> ring(context, fence, entered)
            DeliveryMode.REMINDER -> {
                postReminder(context, fence.id, fence.label, fence.note, entered)
                db.triggerLogDao().insert(TriggerLog(fenceId = fence.id, label = fence.label, entered = entered, outcome = TriggerOutcome.NOTIFIED))
            }
        }
    }

    suspend fun ringAgain(context: Context, fenceId: Long, entered: Boolean) {
        val fence = GeoAlarmDatabase.get(context).fenceDao().byId(fenceId) ?: return
        ring(context, fence, entered)
    }

    private suspend fun ring(context: Context, fence: Fence, entered: Boolean) {
        val alarm = RingingAlarm(fence.id, fence.label, fence.note, entered)
        val rang = runCatching { AlarmRingService.start(context, alarm, fence.soundUri) }
        if (rang.isFailure) {
            // Android refused a background foreground-service start. Still tell the user, loudly.
            Log.w(TAG, "Ringing service refused; falling back to a notification", rang.exceptionOrNull())
            postReminder(context, fence.id, fence.label, fence.note, entered)
        }
        GeoAlarmDatabase.get(context).triggerLogDao().insert(
            TriggerLog(fenceId = fence.id, label = fence.label, entered = entered, outcome = if (rang.isSuccess) TriggerOutcome.RANG else TriggerOutcome.NOTIFIED),
        )
    }

    fun title(context: Context, label: String, entered: Boolean): String =
        context.getString(if (entered) R.string.alert_arrived else R.string.alert_left, label)

    fun postMissed(context: Context, alarm: RingingAlarm) {
        postReminder(context, alarm.fenceId, context.getString(R.string.alarm_missed, alarm.label), alarm.note, alarm.entered, raw = true)
    }

    @SuppressLint("MissingPermission")
    private fun postReminder(context: Context, id: Long, label: String, note: String, entered: Boolean, raw: Boolean = false) {
        if (!Permissions.notifications(context)) {
            Log.w(TAG, "Notifications not permitted; cannot alert for fence $id")
            return
        }
        val open = PendingIntent.getActivity(
            context, id.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_fence)
            .setContentTitle(if (raw) label else title(context, label, entered))
            .setContentText(note.ifBlank { null })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.fenceNotificationId(id), n)
    }
}
