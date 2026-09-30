package io.saul.geoalarm.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.saul.geoalarm.R
import io.saul.geoalarm.data.GeoAlarmDatabase
import io.saul.geoalarm.data.TriggerLog
import io.saul.geoalarm.data.TriggerOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What's ringing right now; the alarm screen observes this and closes when it goes null. */
data class RingingAlarm(val fenceId: Long, val label: String, val note: String, val entered: Boolean)

/** Rings one alarm in the foreground until it is dismissed, snoozed, or times out. */
class AlarmRingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ringer: Ringer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ringer = Ringer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> finish(TriggerOutcome.DISMISSED)
            ACTION_SNOOZE -> {
                _current.value?.let { scheduleSnooze(this, it) }
                finish(TriggerOutcome.SNOOZED)
            }
            else -> ring(intent)
        }
        return START_NOT_STICKY
    }

    private fun ring(intent: Intent?) {
        val alarm = intent?.let {
            RingingAlarm(
                fenceId = it.getLongExtra(EXTRA_FENCE_ID, -1),
                label = it.getStringExtra(EXTRA_LABEL).orEmpty(),
                note = it.getStringExtra(EXTRA_NOTE).orEmpty(),
                entered = it.getBooleanExtra(EXTRA_ENTERED, true),
            )
        }
        if (alarm == null || alarm.fenceId < 0) {
            stopSelf()
            return
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this, Notifications.RINGING_ID, ringingNotification(alarm),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }
        if (started.isFailure) {
            Log.w(TAG, "Could not start ringing in the foreground", started.exceptionOrNull())
            stopSelf()
            return
        }
        _current.value = alarm
        ringer.start(intent.getStringExtra(EXTRA_SOUND))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish(TriggerOutcome.TIMED_OUT) }, TIMEOUT_MS)
    }

    private fun finish(outcome: TriggerOutcome) {
        val alarm = _current.value
        _current.value = null
        handler.removeCallbacksAndMessages(null)
        ringer.stop()
        if (alarm != null) {
            val app = applicationContext
            scope.launch {
                GeoAlarmDatabase.get(app).triggerLogDao()
                    .insert(TriggerLog(fenceId = alarm.fenceId, label = alarm.label, entered = alarm.entered, outcome = outcome))
            }
            if (outcome == TriggerOutcome.TIMED_OUT) TriggerDispatcher.postMissed(app, alarm)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ringer.stop()
        handler.removeCallbacksAndMessages(null)
        if (_current.value != null) _current.value = null
        super.onDestroy()
    }

    private fun ringingNotification(alarm: RingingAlarm): Notification {
        Notifications.ensureChannels(this)
        val title = TriggerDispatcher.title(this, alarm.label, alarm.entered)
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_fence)
            .setContentTitle(title)
            .setContentText(alarm.note.ifBlank { getString(R.string.alarm_tap_to_open) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, getString(R.string.alarm_snooze), actionIntent(this, ACTION_SNOOZE))
            .addAction(0, getString(R.string.alarm_dismiss), actionIntent(this, ACTION_DISMISS))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        const val ACTION_DISMISS = "io.saul.geoalarm.DISMISS"
        const val ACTION_SNOOZE = "io.saul.geoalarm.SNOOZE"
        const val EXTRA_FENCE_ID = "fenceId"
        const val EXTRA_LABEL = "label"
        const val EXTRA_NOTE = "note"
        const val EXTRA_ENTERED = "entered"
        const val EXTRA_SOUND = "sound"
        const val TIMEOUT_MS = 5 * 60_000L
        const val SNOOZE_MS = 5 * 60_000L

        private val _current = MutableStateFlow<RingingAlarm?>(null)
        val current: StateFlow<RingingAlarm?> = _current.asStateFlow()

        fun ringIntent(context: Context, alarm: RingingAlarm, soundUri: String?) =
            Intent(context, AlarmRingService::class.java)
                .putExtra(EXTRA_FENCE_ID, alarm.fenceId)
                .putExtra(EXTRA_LABEL, alarm.label)
                .putExtra(EXTRA_NOTE, alarm.note)
                .putExtra(EXTRA_ENTERED, alarm.entered)
                .putExtra(EXTRA_SOUND, soundUri)

        /** Throws if Android refuses a foreground-service start from the current state. */
        fun start(context: Context, alarm: RingingAlarm, soundUri: String?) {
            ContextCompat.startForegroundService(context, ringIntent(context, alarm, soundUri))
        }

        fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, AlarmRingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

        /** Exact alarm, so the snooze can start the ringing service from the background. */
        private fun scheduleSnooze(context: Context, alarm: RingingAlarm) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context, alarm.fenceId.toInt(),
                Intent(context, SnoozeReceiver::class.java).putExtra(EXTRA_FENCE_ID, alarm.fenceId).putExtra(EXTRA_ENTERED, alarm.entered),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = System.currentTimeMillis() + SNOOZE_MS
            if (AlarmManagerCompat.canScheduleExactAlarms(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
