package io.saul.geoalarm.engine

import android.content.Context

/**
 * Last known inside/outside state per fence. Persisted so that if the process dies while you're
 * outside and you walk in before it restarts, the next fix still counts as an ENTER.
 */
class FenceStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fence_state", Context.MODE_PRIVATE)

    fun load(): Map<Long, Boolean> =
        prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Boolean)?.let { id to it } } }.toMap()

    fun save(state: Map<Long, Boolean>) {
        prefs.edit().clear().apply {
            state.forEach { (id, inside) -> putBoolean(id.toString(), inside) }
        }.apply()
    }

    fun set(id: Long, inside: Boolean) {
        prefs.edit().putBoolean(id.toString(), inside).apply()
    }
}
