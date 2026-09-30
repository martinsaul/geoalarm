package io.saul.geoalarm.offline

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** Web-mercator tile arithmetic for size estimates before a region is downloaded. Pure, unit tested. */
object TileMath {
    /** OpenMapTiles vector tiles stop at z14; MapLibre overzooms them with no loss of detail. */
    const val MAX_ZOOM = 14
    const val MIN_ZOOM = 0

    /** Polite ceiling per region, and the limit we hand to MapLibre (its default is 6000). */
    const val MAX_TILES = 30_000L

    /**
     * Average OpenMapTiles vector tile, calibrated on a Seattle city box (246 tiles -> 23 MB on disk).
     * Cities are the worst case; rural areas come in well under the estimate.
     */
    private const val AVG_TILE_BYTES = 80_000L
    /** Style, sprites and glyphs downloaded once per region. */
    private const val FIXED_BYTES = 3_000_000L

    data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double)

    fun lonToTileX(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = Math.toRadians(clamped)
        return floor((1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileYToLat(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl z)
        return Math.toDegrees(atan(sinh(n)))
    }

    /** Tiles needed for [b] over [minZoom]..[maxZoom]. Handles boxes that cross the antimeridian (west > east). */
    fun tileCount(b: Bounds, minZoom: Int = MIN_ZOOM, maxZoom: Int = MAX_ZOOM): Long {
        var total = 0L
        for (z in minZoom..maxZoom) {
            val n = 1L shl z
            val x0 = lonToTileX(b.west, z)
            val x1 = lonToTileX(b.east, z)
            val xs = if (b.west <= b.east) (x1 - x0 + 1).toLong() else (n - x0) + (x1 + 1)
            val ys = (latToTileY(b.south, z) - latToTileY(b.north, z) + 1).toLong()
            total += minOf(xs, n) * ys
        }
        return total
    }

    fun estimateBytes(tiles: Long): Long = FIXED_BYTES + tiles * AVG_TILE_BYTES

    /** Square box of [halfSizeMeters] around a point, used for "download around this fence". */
    fun around(lat: Double, lon: Double, halfSizeMeters: Double): Bounds {
        val dLat = halfSizeMeters / 111_320.0
        val dLon = halfSizeMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return Bounds(
            south = (lat - dLat).coerceAtLeast(-85.0),
            west = ((lon - dLon + 540.0) % 360.0) - 180.0,
            north = (lat + dLat).coerceAtMost(85.0),
            east = ((lon + dLon + 540.0) % 360.0) - 180.0,
        )
    }

    fun contains(b: Bounds, lat: Double, lon: Double): Boolean {
        if (lat < b.south || lat > b.north) return false
        return if (b.west <= b.east) lon in b.west..b.east else lon >= b.west || lon <= b.east
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1_000_000 -> "${bytes / 1_000} KB"
        bytes < 1_000_000_000 -> "%.0f MB".format(bytes / 1e6)
        else -> "%.1f GB".format(bytes / 1e9)
    }
}
