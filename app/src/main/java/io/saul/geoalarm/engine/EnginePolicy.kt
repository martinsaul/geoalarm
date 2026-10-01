package io.saul.geoalarm.engine

/**
 * Which engine should run. Pure so it can be unit tested.
 *
 * Play Services geofencing leans on Wi-Fi/cell positioning and polls about every 5 minutes, so it's
 * cheap on battery but unreliable offline. Use it only while there is a network connection;
 * otherwise (airplane mode, no signal) the on-device GPS engine takes over.
 */
object EnginePolicy {
    const val GMS = "gms"
    const val LOCAL = "local"

    fun pick(hasPlayServices: Boolean, fenceCount: Int, online: Boolean, maxGmsFences: Int = 100): String =
        if (hasPlayServices && online && fenceCount in 1..maxGmsFences) GMS else LOCAL
}
