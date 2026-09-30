package io.saul.geoalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Rings a snoozed alarm again. Schedule rules were already satisfied when it first rang. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmRingService.EXTRA_FENCE_ID, -1)
        if (id < 0) return
        val entered = intent.getBooleanExtra(AlarmRingService.EXTRA_ENTERED, true)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                TriggerDispatcher.ringAgain(context, id, entered)
            } finally {
                pending.finish()
            }
        }
    }
}
