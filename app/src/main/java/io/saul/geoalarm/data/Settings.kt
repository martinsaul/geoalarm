package io.saul.geoalarm.data

import android.content.Context
import io.saul.geoalarm.BuildConfig

/** Small user settings; not worth a DataStore yet. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var styleUrl: String
        get() = prefs.getString(KEY_STYLE_URL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_STYLE_URL
        set(value) = prefs.edit().putString(KEY_STYLE_URL, value.trim()).apply()

    private companion object {
        const val KEY_STYLE_URL = "style_url"
    }
}
