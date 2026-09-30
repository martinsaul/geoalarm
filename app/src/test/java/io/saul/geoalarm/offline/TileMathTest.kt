package io.saul.geoalarm.offline

import io.saul.geoalarm.offline.TileMath.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {
    @Test
    fun `whole world at zoom 0 is one tile`() {
        assertEquals(1L, TileMath.tileCount(Bounds(-85.0, -180.0, 85.0, 180.0), 0, 0))
    }

    @Test
    fun `whole world through z2 is 1 + 4 + 16`() {
        assertEquals(21L, TileMath.tileCount(Bounds(-85.0, -180.0, 85.0, 180.0), 0, 2))
    }

    @Test
    fun `known tile coordinates for Seattle at z14`() {
        // Space Needle: z14 tile x=2623, y=5721 (standard slippy-map numbering).
        assertEquals(2623, TileMath.lonToTileX(-122.3493, 14))
        assertEquals(5721, TileMath.latToTileY(47.6205, 14))
    }

    @Test
    fun `a 20 km city box is a few hundred tiles`() {
        val b = TileMath.around(47.6205, -122.3493, 10_000.0)
        val n = TileMath.tileCount(b)
        assertTrue("got $n", n in 150L..600L)
    }

    @Test
    fun `antimeridian box counts both sides`() {
        val b = Bounds(-10.0, 170.0, 10.0, -170.0)
        val z4 = TileMath.tileCount(b, 4, 4)
        // 20 degrees of longitude straddling 180 spans 2 columns at z4 (each column is 22.5 degrees).
        assertEquals(2L * (TileMath.latToTileY(-10.0, 4) - TileMath.latToTileY(10.0, 4) + 1), z4)
    }

    @Test
    fun `contains handles antimeridian`() {
        val b = Bounds(-10.0, 170.0, 10.0, -170.0)
        assertTrue(TileMath.contains(b, 0.0, 179.0))
        assertTrue(TileMath.contains(b, 0.0, -175.0))
        assertFalse(TileMath.contains(b, 0.0, 0.0))
    }

    @Test
    fun `size formatting`() {
        assertEquals("500 KB", TileMath.formatBytes(500_000))
        assertEquals("42 MB", TileMath.formatBytes(42_000_000))
        assertEquals("1.5 GB", TileMath.formatBytes(1_500_000_000))
    }
}
