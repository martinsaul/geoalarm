package io.saul.geoalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/** Looping alarm sound on the alarm stream (plays through silent mode), with a volume ramp and vibration. */
class Ringer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampStep = 0

    fun start(soundUri: String?) {
        stop()
        val uri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching { create(uri) }
            .recoverCatching { create(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) }
            .onFailure { Log.w(TAG, "No playable alarm sound", it) }
            .getOrNull()
        rampStep = 0
        handler.post(ramp)
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun create(uri: Uri) = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        setDataSource(context, uri)
        isLooping = true
        setVolume(RAMP_START, RAMP_START)
        prepare()
        start()
    }

    private val ramp = object : Runnable {
        override fun run() {
            val p = player ?: return
            rampStep++
            val v = (RAMP_START + (1f - RAMP_START) * rampStep / RAMP_STEPS).coerceAtMost(1f)
            runCatching { p.setVolume(v, v) }
            if (rampStep < RAMP_STEPS) handler.postDelayed(this, RAMP_MS / RAMP_STEPS)
        }
    }

    fun stop() {
        handler.removeCallbacks(ramp)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator().cancel()
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private companion object {
        const val TAG = "Ringer"
        const val RAMP_START = 0.15f
        const val RAMP_STEPS = 20
        const val RAMP_MS = 20_000L
    }
}
