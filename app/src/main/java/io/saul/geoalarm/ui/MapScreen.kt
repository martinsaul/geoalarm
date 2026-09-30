package io.saul.geoalarm.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.saul.geoalarm.engine.GeoPoint
import io.saul.geoalarm.offline.OfflineRegions
import io.saul.geoalarm.offline.TileMath
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import org.maplibre.android.geometry.LatLngBounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(modifier: Modifier = Modifier, vm: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val fences by vm.fences.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val styleUrl by vm.styleUrl.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var hasLocation by remember { mutableStateOf(CurrentLocation.hasPermission(context)) }
    var showList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCoordinates by remember { mutableStateOf(false) }
    var mapLoadFailed by remember { mutableStateOf(false) }
    var showOffline by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<Pair<TileMath.Bounds, String>?>(null) }
    val offline = remember { OfflineRegions.get(context) }
    val regions by offline.state.collectAsStateWithLifecycle()

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { mapLoadFailed = true }
            addOnDidFinishLoadingStyleListener { mapLoadFailed = false }
            getMapAsync { m ->
                m.cameraPosition = CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.5).build()
                m.addOnMapClickListener { latLng ->
                    vm.onMapTap(GeoPoint(latLng.latitude, latLng.longitude))
                    true
                }
                map = m
            }
        }
    }
    MapLifecycle(mapView)

    fun flyTo(p: GeoPoint, zoom: Double? = null) {
        val m = map ?: return
        val z = zoom ?: maxOf(m.cameraPosition.zoom, 13.0)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), z))
    }

    // Keep the fence being edited visible above the editor card.
    LaunchedEffect(map, draft != null) {
        val m = map ?: return@LaunchedEffect
        val bottom = if (draft != null) mapView.height * 0.45 else 0.0
        m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, bottom))
    }

    fun withLocation(action: (GeoPoint) -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val job = launch { snackbar.showSnackbar("Getting a GPS fix...") }
            val here = CurrentLocation.get(context)
            job.cancel()
            if (here == null) snackbar.showSnackbar("No location fix yet. Try again outdoors.")
            else action(here)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = CurrentLocation.hasPermission(context)
    }
    val askLocation = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // (Re)load the basemap whenever the style URL changes.
    LaunchedEffect(map, styleUrl) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
            MapLayers.install(loaded)
            style = loaded
        }
    }

    // Offline, MapLibre doesn't fail the style load, it waits for connectivity. Treat "not loaded yet" as unavailable.
    LaunchedEffect(map, style) {
        if (map != null && style == null) {
            delay(8_000)
            mapLoadFailed = true
        } else if (style != null) {
            mapLoadFailed = false
        }
    }

    LaunchedEffect(style, fences, draft) {
        style?.takeIf { it.isFullyLoaded }?.let { MapLayers.render(it, fences, draft) }
    }

    LaunchedEffect(style, hasLocation) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (hasLocation) enableLocationPuck(m, s, context)
    }

    // First launch: ask for location and centre on the user once.
    LaunchedEffect(Unit) {
        if (!hasLocation) askLocation()
    }
    var centredOnce by remember { mutableStateOf(false) }
    LaunchedEffect(map, hasLocation) {
        if (map != null && hasLocation && !centredOnce) {
            centredOnce = true
            CurrentLocation.get(context, timeoutMs = 10_000)?.let { flyTo(it, 13.0) }
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Map" },
        )

        Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(onClick = { showList = true }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Fences")
            }
            FilledTonalIconButton(onClick = {
                if (!hasLocation) askLocation() else withLocation { flyTo(it, 15.0) }
            }) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location")
            }
            FilledTonalIconButton(onClick = { offline.refresh(); showOffline = true }) {
                Icon(Icons.Default.DownloadForOffline, contentDescription = "Offline maps")
            }
            FilledTonalIconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Only nag once there's something to protect.
            if (draft == null && fences.any { it.enabled }) PermissionSteps()
            val d = draft
            if (d != null) {
                FenceEditor(
                    draft = d,
                    onChange = vm::updateDraft,
                    onSave = vm::saveDraft,
                    onCancel = vm::cancelDraft,
                    onDelete = vm::deleteDraft,
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tap the map to place a fence", style = MaterialTheme.typography.titleMedium)
                        Row2(
                            left = {
                                TextButton(onClick = {
                                    if (!hasLocation) askLocation()
                                    else withLocation { vm.startDraftAt(it); flyTo(it) }
                                }) { Text("Fence at my location") }
                            },
                            right = { TextButton(onClick = { showCoordinates = true }) { Text("Enter coordinates") } },
                        )
                    }
                }
            }
        }

        if (mapLoadFailed) {
            Card(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).padding(end = 64.dp)) {
                Text(
                    "Map not available offline here. Fences and alarms still work: use your location or coordinates. Save areas for offline use under Offline maps.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }

    if (showList) {
        FenceListSheet(
            fences = fences,
            history = history,
            onDismiss = { showList = false },
            onSelect = { f ->
                showList = false
                vm.edit(f)
                flyTo(GeoPoint(f.latitude, f.longitude))
            },
            onToggle = vm::setEnabled,
        )
    }
    // After creating a fence, offer the map around it for offline use if we don't have it yet.
    LaunchedEffect(Unit) {
        vm.created.collect { f ->
            if (offline.covers(f.latitude, f.longitude)) return@collect
            val result = snackbar.showSnackbar(
                "Save the map around \"${f.label}\" for offline use?", actionLabel = "Download", duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                val half = maxOf(f.radiusMeters * 3, 5_000.0)
                pendingDownload = TileMath.around(f.latitude, f.longitude, half) to "Around ${f.label}"
            }
        }
    }

    if (showOffline) {
        OfflineSheet(
            regions = regions,
            onDismiss = { showOffline = false },
            onDownloadVisible = {
                map?.projection?.visibleRegion?.latLngBounds?.let { b ->
                    showOffline = false
                    pendingDownload = TileMath.Bounds(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast) to
                        "Area ${regions.size + 1}"
                }
            },
            onToggle = { offline.setActive(it.id, !it.active) },
            onUpdate = { offline.update(it.id) },
            onDelete = { offline.delete(it.id) },
            onShow = { r ->
                showOffline = false
                map?.animateCamera(CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.from(r.bounds.north, r.bounds.east, r.bounds.south, r.bounds.west), 48,
                ))
            },
        )
    }
    pendingDownload?.let { (bounds, name) ->
        DownloadRegionDialog(
            bounds = bounds,
            suggestedName = name,
            onDismiss = { pendingDownload = null },
            onConfirm = { chosen ->
                pendingDownload = null
                offline.download(chosen, bounds, styleUrl, context.resources.displayMetrics.density) { err ->
                    scope.launch { snackbar.showSnackbar("Download failed: $err") }
                }
                scope.launch { snackbar.showSnackbar("Downloading \"$chosen\". Progress is under Offline maps.") }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(current = styleUrl, onDismiss = { showSettings = false }, onSave = {
            vm.setStyleUrl(it)
            showSettings = false
        })
    }
    if (showCoordinates) {
        CoordinatesDialog(onDismiss = { showCoordinates = false }, onConfirm = { p ->
            showCoordinates = false
            vm.startDraftAt(p)
            flyTo(p)
        })
    }
}

@SuppressLint("MissingPermission")
private fun enableLocationPuck(map: MapLibreMap, style: Style, context: android.content.Context) {
    val lc = map.locationComponent
    // Default engine is Android's LocationManager: no Play Services, works offline.
    lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
    lc.isLocationComponentEnabled = true
    lc.cameraMode = CameraMode.NONE
}

@Composable
private fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
