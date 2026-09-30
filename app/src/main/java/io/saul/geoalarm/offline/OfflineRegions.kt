package io.saul.geoalarm.offline

import android.content.Context
import android.util.Log
import io.saul.geoalarm.offline.TileMath.Bounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** A downloaded (or downloading) map region, as the UI sees it. */
data class RegionInfo(
    val id: Long,
    val name: String,
    val bounds: Bounds,
    val createdAt: Long,
    val active: Boolean = false,
    val complete: Boolean = false,
    val completed: Long = 0,
    val required: Long = 0,
    val bytes: Long = 0,
    val error: String? = null,
) {
    val progress: Float get() = if (required > 0) (completed.toFloat() / required).coerceIn(0f, 1f) else 0f
}

/**
 * User-chosen offline map areas, stored by MapLibre in its offline database. Tiles come from the
 * current style's source, so any MapLibre style URL works. Must be used from the main thread.
 */
class OfflineRegions private constructor(context: Context) {
    private val manager = OfflineManager.getInstance(context.applicationContext).apply {
        setOfflineMapboxTileCountLimit(TileMath.MAX_TILES)
    }
    private val regions = mutableMapOf<Long, OfflineRegion>()
    private val _state = MutableStateFlow<List<RegionInfo>>(emptyList())
    val state: StateFlow<List<RegionInfo>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val list = offlineRegions.orEmpty()
                list.forEach { r ->
                    regions[r.id] = r
                    observe(r)
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) { status?.let { applyStatus(r.id, it) } }
                        override fun onError(error: String?) = Unit
                    })
                }
                _state.value = list.mapNotNull(::describe).sortedByDescending { it.createdAt }
            }

            override fun onError(error: String) {
                Log.w(TAG, "listOfflineRegions: $error")
            }
        })
    }

    fun download(name: String, bounds: Bounds, styleUrl: String, pixelRatio: Float, onError: (String) -> Unit = {}) {
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
            TileMath.MIN_ZOOM.toDouble(), TileMath.MAX_ZOOM.toDouble(), pixelRatio,
        )
        val meta = JSONObject().put("name", name).put("createdAt", System.currentTimeMillis()).toString().toByteArray()
        manager.createOfflineRegion(definition, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                regions[offlineRegion.id] = offlineRegion
                describe(offlineRegion)?.let { info -> _state.update { listOf(info.copy(active = true)) + it } }
                observe(offlineRegion)
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) = onError(error)
        })
    }

    fun setActive(id: Long, active: Boolean) {
        val r = regions[id] ?: return
        r.setDownloadState(if (active) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE)
        _state.update { list -> list.map { if (it.id == id) it.copy(active = active, error = null) else it } }
    }

    /** Re-download everything in the region (e.g. after the map data was refreshed upstream). */
    fun update(id: Long) {
        val r = regions[id] ?: return
        r.invalidate(object : OfflineRegion.OfflineRegionInvalidateCallback {
            override fun onInvalidate() = setActive(id, true)
            override fun onError(error: String) { Log.w(TAG, "invalidate: $error") }
        })
    }

    fun delete(id: Long) {
        val r = regions.remove(id) ?: return
        r.setDownloadState(OfflineRegion.STATE_INACTIVE)
        r.setObserver(null)
        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { _state.update { list -> list.filterNot { it.id == id } } }
            override fun onError(error: String) { Log.w(TAG, "delete: $error") }
        })
    }

    fun covers(lat: Double, lon: Double): Boolean =
        _state.value.any { it.complete && TileMath.contains(it.bounds, lat, lon) } ||
            _state.value.any { it.active && TileMath.contains(it.bounds, lat, lon) }

    private fun observe(r: OfflineRegion) {
        r.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                applyStatus(r.id, status)
                if (status.isComplete) r.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }

            override fun onError(error: OfflineRegionError) {
                // Transient network errors are retried by MapLibre; just surface the latest one.
                _state.update { list -> list.map { if (it.id == r.id) it.copy(error = error.message) else it } }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                _state.update { list -> list.map { if (it.id == r.id) it.copy(active = false, error = "Too large (over $limit tiles)") else it } }
            }
        })
    }

    private fun applyStatus(id: Long, s: OfflineRegionStatus) {
        _state.update { list ->
            list.map {
                if (it.id != id) it else it.copy(
                    active = s.downloadState == OfflineRegion.STATE_ACTIVE && !s.isComplete,
                    complete = s.isComplete,
                    completed = s.completedResourceCount,
                    required = s.requiredResourceCount,
                    bytes = s.completedResourceSize,
                    error = if (s.isComplete) null else it.error,
                )
            }
        }
    }

    private fun describe(r: OfflineRegion): RegionInfo? {
        val def = r.definition as? OfflineTilePyramidRegionDefinition ?: return null
        val b = def.bounds ?: return null
        val meta = runCatching { JSONObject(String(r.metadata)) }.getOrNull()
        return RegionInfo(
            id = r.id,
            name = meta?.optString("name")?.ifBlank { null } ?: "Region ${r.id}",
            bounds = Bounds(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast),
            createdAt = meta?.optLong("createdAt") ?: 0L,
        )
    }

    companion object {
        private const val TAG = "OfflineRegions"
        @Volatile private var instance: OfflineRegions? = null

        fun get(context: Context): OfflineRegions =
            instance ?: synchronized(this) { instance ?: OfflineRegions(context).also { instance = it } }
    }
}
