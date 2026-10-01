package io.saul.geoalarm.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EnginePolicyTest {
    @Test
    fun `online with Play Services uses gms`() =
        assertEquals(EnginePolicy.GMS, EnginePolicy.pick(hasPlayServices = true, fenceCount = 3, online = true))

    @Test
    fun `offline always uses local`() =
        assertEquals(EnginePolicy.LOCAL, EnginePolicy.pick(hasPlayServices = true, fenceCount = 3, online = false))

    @Test
    fun `no Play Services uses local`() =
        assertEquals(EnginePolicy.LOCAL, EnginePolicy.pick(hasPlayServices = false, fenceCount = 3, online = true))

    @Test
    fun `over the Play Services fence limit uses local`() =
        assertEquals(EnginePolicy.LOCAL, EnginePolicy.pick(hasPlayServices = true, fenceCount = 101, online = true))

    @Test
    fun `no fences is local (which then does nothing)`() =
        assertEquals(EnginePolicy.LOCAL, EnginePolicy.pick(hasPlayServices = true, fenceCount = 0, online = true))
}
