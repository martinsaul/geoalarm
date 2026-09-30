package io.saul.geoalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.saul.geoalarm.data.DeliveryMode
import io.saul.geoalarm.data.Fence
import io.saul.geoalarm.data.GeoAlarmDatabase
import io.saul.geoalarm.data.Schedule
import io.saul.geoalarm.data.Settings
import io.saul.geoalarm.data.TriggerLog
import io.saul.geoalarm.engine.GeoMath
import io.saul.geoalarm.engine.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A fence being created or edited. [id] == 0 means new. */
data class FenceDraft(
    val id: Long = 0,
    val center: GeoPoint,
    val radiusMeters: Double = 300.0,
    val label: String = "",
    val onEnter: Boolean = true,
    val onExit: Boolean = false,
    val mode: DeliveryMode = DeliveryMode.ALARM,
    val enabled: Boolean = true,
    val note: String = "",
    val repeat: Boolean = true,
    val activeDays: Int = Schedule.ALL_DAYS,
    val windowStartMinutes: Int? = null,
    val windowEndMinutes: Int? = null,
    val soundUri: String? = null,
) {
    fun toFence() = Fence(
        id = id,
        label = label.ifBlank { "Fence" },
        latitude = center.latitude,
        longitude = center.longitude,
        radiusMeters = radiusMeters,
        onEnter = onEnter,
        onExit = onExit,
        mode = mode,
        enabled = enabled,
        note = note,
        repeat = repeat,
        activeDays = activeDays,
        windowStartMinutes = windowStartMinutes,
        windowEndMinutes = windowEndMinutes,
        soundUri = soundUri,
    )

    val canSave: Boolean get() = (onEnter || onExit) && activeDays != 0

    companion object {
        fun from(f: Fence) = FenceDraft(
            id = f.id, center = GeoPoint(f.latitude, f.longitude), radiusMeters = f.radiusMeters,
            label = f.label, onEnter = f.onEnter, onExit = f.onExit, mode = f.mode, enabled = f.enabled, note = f.note,
            repeat = f.repeat, activeDays = f.activeDays, windowStartMinutes = f.windowStartMinutes,
            windowEndMinutes = f.windowEndMinutes, soundUri = f.soundUri,
        )
    }
}

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = GeoAlarmDatabase.get(app).fenceDao()
    private val settings = Settings(app)

    val fences: StateFlow<List<Fence>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<TriggerLog>> =
        GeoAlarmDatabase.get(app).triggerLogDao().observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<FenceDraft?>(null)
    val draft: StateFlow<FenceDraft?> = _draft.asStateFlow()

    private val _styleUrl = MutableStateFlow(settings.styleUrl)
    val styleUrl: StateFlow<String> = _styleUrl.asStateFlow()

    /** Tap on the map: edit the smallest fence containing the point, otherwise start a new one there. */
    fun onMapTap(point: GeoPoint) {
        val current = _draft.value
        if (current != null) {
            // While editing, a tap moves the centre.
            _draft.value = current.copy(center = point)
            return
        }
        val hit = fences.value
            .filter { GeoMath.distanceMeters(point, GeoPoint(it.latitude, it.longitude)) <= it.radiusMeters }
            .minByOrNull { it.radiusMeters }
        _draft.value = hit?.let(FenceDraft::from) ?: FenceDraft(center = point)
    }

    fun startDraftAt(point: GeoPoint) {
        _draft.value = (_draft.value ?: FenceDraft(center = point)).copy(center = point)
    }

    fun edit(fence: Fence) {
        _draft.value = FenceDraft.from(fence)
    }

    fun updateDraft(transform: (FenceDraft) -> FenceDraft) {
        _draft.value = _draft.value?.let(transform)
    }

    fun cancelDraft() {
        _draft.value = null
    }

    /** Emits newly created fences so the screen can offer an offline download around them. */
    private val _created = MutableSharedFlow<Fence>(extraBufferCapacity = 1)
    val created: SharedFlow<Fence> = _created.asSharedFlow()

    fun saveDraft() {
        // Saving re-enables a one-shot fence that already fired.
        val d = _draft.value?.takeIf { it.canSave }?.copy(enabled = true) ?: return
        viewModelScope.launch {
            val rowId = dao.upsert(d.toFence())
            _draft.value = null
            if (d.id == 0L) _created.tryEmit(d.toFence().copy(id = rowId))
        }
    }

    fun deleteDraft() {
        val d = _draft.value ?: return
        viewModelScope.launch {
            if (d.id != 0L) dao.delete(d.toFence())
            _draft.value = null
        }
    }

    fun setEnabled(fence: Fence, enabled: Boolean) {
        viewModelScope.launch { dao.upsert(fence.copy(enabled = enabled)) }
    }

    fun setStyleUrl(url: String) {
        settings.styleUrl = url
        _styleUrl.value = settings.styleUrl
    }
}
