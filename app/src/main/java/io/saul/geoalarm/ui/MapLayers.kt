package io.saul.geoalarm.ui

import io.saul.geoalarm.data.Fence
import io.saul.geoalarm.engine.GeoMath
import io.saul.geoalarm.engine.GeoPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Fence and draft overlays drawn as GeoJSON layers on top of the basemap. */
internal object MapLayers {
    private const val FENCES = "geoalarm-fences"
    private const val DRAFT = "geoalarm-draft"
    private const val DRAFT_CENTER = "geoalarm-draft-center"

    private const val COLOR_ENABLED = "#1E6B52"
    private const val COLOR_DISABLED = "#8A8A8A"
    private const val COLOR_DRAFT = "#E0632B"

    fun install(style: Style) {
        if (style.getSource(FENCES) != null) return
        style.addSource(GeoJsonSource(FENCES))
        style.addSource(GeoJsonSource(DRAFT))
        style.addSource(GeoJsonSource(DRAFT_CENTER))

        val fenceColor = Expression.switchCase(
            Expression.get("enabled"), Expression.literal(COLOR_ENABLED), Expression.literal(COLOR_DISABLED),
        )
        style.addLayer(FillLayer("$FENCES-fill", FENCES).withProperties(
            PropertyFactory.fillColor(fenceColor), PropertyFactory.fillOpacity(0.18f),
        ))
        style.addLayer(LineLayer("$FENCES-line", FENCES).withProperties(
            PropertyFactory.lineColor(fenceColor), PropertyFactory.lineWidth(2f),
        ))
        style.addLayer(FillLayer("$DRAFT-fill", DRAFT).withProperties(
            PropertyFactory.fillColor(COLOR_DRAFT), PropertyFactory.fillOpacity(0.22f),
        ))
        style.addLayer(LineLayer("$DRAFT-line", DRAFT).withProperties(
            PropertyFactory.lineColor(COLOR_DRAFT), PropertyFactory.lineWidth(3f),
            PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
        ))
        style.addLayer(CircleLayer("$DRAFT_CENTER-dot", DRAFT_CENTER).withProperties(
            PropertyFactory.circleColor(COLOR_DRAFT), PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f),
        ))
    }

    fun render(style: Style, fences: List<Fence>, draft: FenceDraft?) {
        val editingId = draft?.id?.takeIf { it != 0L }
        val fenceFeatures = fences.filter { it.id != editingId }.map { f ->
            circleFeature(GeoPoint(f.latitude, f.longitude), f.radiusMeters).apply {
                addBooleanProperty("enabled", f.enabled)
            }
        }
        style.getSourceAs<GeoJsonSource>(FENCES)?.setGeoJson(FeatureCollection.fromFeatures(fenceFeatures))
        style.getSourceAs<GeoJsonSource>(DRAFT)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(draft?.let { circleFeature(it.center, it.radiusMeters) })),
        )
        style.getSourceAs<GeoJsonSource>(DRAFT_CENTER)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(draft?.let {
                Feature.fromGeometry(Point.fromLngLat(it.center.longitude, it.center.latitude))
            })),
        )
    }

    private fun circleFeature(center: GeoPoint, radiusMeters: Double): Feature {
        val ring = GeoMath.circleRing(center, radiusMeters).map { Point.fromLngLat(it.longitude, it.latitude) }
        return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    }
}
