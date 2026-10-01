package io.saul.geoalarm.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * While Play Services is the active engine our process may be dead, so a lost connection would go
 * unnoticed. A cheap inexact alarm re-checks every 15 minutes and hands over to GPS if needed.
 */
class ResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                GeofenceController.sync(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private fun pi(context: Context) = PendingIntent.getBroadcast(
            context, 0, Intent(context, ResyncReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )

        fun schedule(context: Context) {
            context.getSystemService(AlarmManager::class.java).setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pi(context),
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(pi(context))
        }
    }
}
