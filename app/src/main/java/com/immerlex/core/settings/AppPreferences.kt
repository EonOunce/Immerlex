package com.immerlex.core.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.immerlex.core.ai.Density
import com.immerlex.core.quiz.EarlyReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_FONT_SCALE = 17f
const val MIN_FONT_SCALE = 14f
const val MAX_FONT_SCALE = 21f

/**
 * 全 app 的主色，用户在设置页与今日页一键切换。core 只存 ARGB 整数，
 * 换成 Compose 颜色、派生 materialScheme 都留在 `app/ui/Theme.kt`——这一层不碰界面。
 *
 * 两档都是"够亮的暖色"：杏黄与珊瑚橘。因为它们本身偏亮，深底浅底都能直接当 primary，
 * 反色一律用墨黑（配在亮黄/亮橘上比白色更清楚，也满足对比度）。
 */
enum class Accent(val key: String, val argb: Int, val onArgb: Int, val containerLight: Int, val containerDark: Int) {
    WARM("warm", 0xFFFFC94D.toInt(), 0xFF201D1A.toInt(), 0xFFFFE9A8.toInt(), 0xFF4A3A12.toInt()),
    CORAL("coral", 0xFFFF8A5C.toInt(), 0xFF201D1A.toInt(), 0xFFFFD3BC.toInt(), 0xFF4A271A.toInt()),
    ;

    companion object {
        val DEFAULT = WARM

        /** 存的字符串认不出来（老版本、手改、备份里带来）一律退回默认，不崩。 */
        fun saneOr(stored: String?): Accent = entries.firstOrNull { it.key == stored } ?: DEFAULT
    }
}

/** Presentation settings that must take effect immediately, without a restart or a save button. */
data class Appearance(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: Boolean = false,
    /** 点开词卡、翻出一道题就自动朗读英文。 */
    val autoSpeak: Boolean = false,
    /** 全 app 主色，两档暖色，改了立即生效。 */
    val accent: Accent = Accent.DEFAULT,
)

/** Non-secret app preferences. The API key lives in [com.immerlex.core.ai.ApiKeyStore]. */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("immerlex_prefs", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(readAppearance())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private val _keyBindings = MutableStateFlow(readKeyBindings())
    val keyBindings: StateFlow<KeyMap> = _keyBindings.asStateFlow()

    // SharedPreferences keeps listeners weakly, so the listener must be a field or it is collected.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_FONT_SCALE, KEY_DARK, KEY_AUTO_SPEAK, KEY_ACCENT -> _appearance.value = readAppearance()
            KEY_KEY_BINDINGS -> _keyBindings.value = readKeyBindings()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    private fun readAppearance() = Appearance(
        fontScale = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE),
        darkMode = prefs.getBoolean(KEY_DARK, false),
        autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, false),
        accent = Accent.saneOr(prefs.getString(KEY_ACCENT, null)),
    )

    private fun readKeyBindings() = KeyMap.decode(prefs.getString(KEY_KEY_BINDINGS, "").orEmpty())

    /** 改一个动作的键位：新键原先的主人跟它换过来，一个场景里不会有两个动作抢同一个键。 */
    fun bindKey(action: KeyAction, keyCode: Int) {
        prefs.edit().putString(KEY_KEY_BINDINGS, readKeyBindings().bind(action, keyCode).encode()).apply()
    }

    fun resetKeyBindings() {
        prefs.edit().remove(KEY_KEY_BINDINGS).apply()
    }

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

    /** 提前复习一组几个词，5 / 10 / 20 三档；坏值退回默认的 5。 */
    var earlyGroupSize: Int
        get() = EarlyReview.saneOr(prefs.getInt(KEY_EARLY_GROUP_SIZE, EarlyReview.DEFAULT_GROUP_SIZE))
        set(value) = prefs.edit().putInt(KEY_EARLY_GROUP_SIZE, EarlyReview.saneOr(value)).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    /** 默认关：在公共场合连着出声比听不到更糟，所以由用户自己打开。 */
    var autoSpeak: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    /** 全 app 主色，两档暖色，改了立即生效（走 appearance StateFlow）。 */
    var accent: Accent
        get() = Accent.saneOr(prefs.getString(KEY_ACCENT, null))
        set(value) = prefs.edit().putString(KEY_ACCENT, value.key).apply()

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

    /**
     * 这台机器叫什么，进备份文件名。两台设备传上去的文件全靠它区分；
     * 没填就用机型名，机型名里的空格和中文由 `BackupNaming.deviceTag` 洗掉。
     */
    var deviceName: String
        get() = prefs.getString(KEY_DEVICE, "").orEmpty().ifBlank { Build.MODEL.orEmpty().ifBlank { DEFAULT_DEVICE } }
        set(value) = prefs.edit().putString(KEY_DEVICE, value.trim()).apply()

    /**
     * 素材包清单的地址。默认最新 release 上那一份 `manifest.json`；分片按相对地址解析，
     * 所以换成自己的镜像或本机服务器只改这一行。
     */
    var materialManifestUrl: String
        get() = prefs.getString(KEY_MATERIAL_URL, PRESET_MATERIAL_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_MATERIAL_URL, value.trim()).apply()

    companion object {
        const val DEFAULT_NEW_LIMIT = 25
        const val DEFAULT_REMINDER_MINUTE = 20 * 60 + 30
        const val MINUTES_PER_DAY = 24 * 60
        const val PRESET_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"
        const val PRESET_MATERIAL_URL = "https://github.com/EonOunce/Immerlex/releases/latest/download/manifest.json"
        const val DEFAULT_DEVICE = "device"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_GENRE = "genre"
        private const val KEY_DENSITY = "density"
        private const val KEY_NEW_LIMIT = "daily_new_limit"
        private const val KEY_EARLY_GROUP_SIZE = "early_group_size"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_ACCENT = "accent"
        private const val KEY_REMINDER_ON = "reminder_enabled"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_REMINDED_DAY = "last_reminded_day"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_ACCOUNT = "webdav_account"
        private const val KEY_BACKUP_ON = "backup_enabled"
        private const val KEY_DEVICE = "device_name"
        private const val KEY_MATERIAL_URL = "material_manifest_url"
        private const val KEY_KEY_BINDINGS = "key_bindings"
    }
}
