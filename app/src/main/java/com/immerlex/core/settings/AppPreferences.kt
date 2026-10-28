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

    /** 每日到期提醒的开关。关了就不排任务，打开时排到当天的设定时刻。 */
    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER_ON, value).apply()

    /** 本地一天中的第几分钟，默认 20:30。WorkManager 没有"每天几点"，到点靠一次性任务自排明天。 */
    var reminderMinuteOfDay: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MINUTE, Math.floorMod(value, MINUTES_PER_DAY)).apply()

    /** 同一天只提醒一次：任务被系统重跑或用户改了时间重新排队时，不能连着弹第二条。 */
    var lastRemindedEpochDay: Long
        get() = prefs.getLong(KEY_REMINDED_DAY, 0)
        set(value) = prefs.edit().putLong(KEY_REMINDED_DAY, value).apply()

    /** WebDAV 根地址，预填坚果云。真正上传落在它下面的 Immerlex 目录里。 */
    var webDavUrl: String
        get() = prefs.getString(KEY_WEBDAV_URL, PRESET_WEBDAV_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_WEBDAV_URL, value.trim()).apply()

    /** 坚果云的登录邮箱；密码不进这里，应用密码走加密存储。 */
    var webDavAccount: String
        get() = prefs.getString(KEY_WEBDAV_ACCOUNT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_WEBDAV_ACCOUNT, value.trim()).apply()

    /** 默认关：没填账号和密码就每天失败一次，比不备份更烦人。填全后由用户自己打开。 */
    var backupEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_ON, value).apply()

    companion object {
        const val DEFAULT_NEW_LIMIT = 25
        const val DEFAULT_REMINDER_MINUTE = 20 * 60 + 30
        const val MINUTES_PER_DAY = 24 * 60
        const val PRESET_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_GENRE = "genre"
        private const val KEY_DENSITY = "density"
        private const val KEY_NEW_LIMIT = "daily_new_limit"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_REMINDER_ON = "reminder_enabled"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_REMINDED_DAY = "last_reminded_day"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_ACCOUNT = "webdav_account"
        private const val KEY_BACKUP_ON = "backup_enabled"
    }
}
