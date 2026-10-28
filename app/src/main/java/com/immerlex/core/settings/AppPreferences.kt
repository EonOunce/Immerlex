package com.immerlex.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.immerlex.core.ai.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_FONT_SCALE = 17f
const val MIN_FONT_SCALE = 14f
const val MAX_FONT_SCALE = 21f

/** Presentation settings that must take effect immediately, without a restart or a save button. */
data class Appearance(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: Boolean = false,
)

/** Non-secret app preferences. The API key lives in [com.immerlex.core.ai.ApiKeyStore]. */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("immerlex_prefs", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(readAppearance())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    // SharedPreferences keeps listeners weakly, so the listener must be a field or it is collected.
    private val appearanceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_FONT_SCALE || key == KEY_DARK) _appearance.value = readAppearance()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(appearanceListener)
    }

    private fun readAppearance() = Appearance(
        fontScale = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE),
        darkMode = prefs.getBoolean(KEY_DARK, false),
    )

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    var genre: String
        get() = prefs.getString(KEY_GENRE, "宫斗").orEmpty()
        set(value) = prefs.edit().putString(KEY_GENRE, value.trim()).apply()

    var density: Density
        get() = runCatching { Density.valueOf(prefs.getString(KEY_DENSITY, null) ?: Density.MEDIUM.name) }
            .getOrDefault(Density.MEDIUM)
        set(value) = prefs.edit().putString(KEY_DENSITY, value.name).apply()

    var dailyNewLimit: Int
        get() = prefs.getInt(KEY_NEW_LIMIT, DEFAULT_NEW_LIMIT)
        set(value) = prefs.edit().putInt(KEY_NEW_LIMIT, value.coerceIn(1, 200)).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    companion object {
        const val DEFAULT_NEW_LIMIT = 25
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_GENRE = "genre"
        private const val KEY_DENSITY = "density"
        private const val KEY_NEW_LIMIT = "daily_new_limit"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_DARK = "dark_mode"
    }
}
