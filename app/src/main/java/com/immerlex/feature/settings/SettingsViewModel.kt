package com.immerlex.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.app.ui.backupDetailOf
import com.immerlex.app.ui.failureMessage
import com.immerlex.app.ui.kiloBytesOf
import com.immerlex.app.ui.probeDetailOf
import com.immerlex.app.ui.probeFailureLabel
import com.immerlex.app.ui.storedBackupDetail
import com.immerlex.app.ui.storedBackupFailure
import com.immerlex.app.ui.toUiMessage
import com.immerlex.core.ai.AiConfig
import com.immerlex.core.ai.ApiKeyStore
import com.immerlex.core.ai.ChapterGenerator
import com.immerlex.core.ai.Density
import com.immerlex.core.ai.OpenAiCompatClient
import com.immerlex.core.ai.ProviderPreset
import com.immerlex.core.backup.BackupService
import com.immerlex.core.backup.Listing
import com.immerlex.core.backup.RemoteFile
import com.immerlex.core.backup.RestoreOutcome
import com.immerlex.core.backup.SnapshotProblem
import com.immerlex.core.db.BackupRunEntity
import com.immerlex.core.db.WordDao
import com.immerlex.core.notify.GenerationNotifier
import com.immerlex.core.notify.ReminderControl
import com.immerlex.core.settings.AppPreferences
import com.immerlex.core.settings.DEFAULT_FONT_SCALE
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val keyPersistent: Boolean = true,
    val genre: String = "",
    val density: Density = Density.MEDIUM,
    val dailyNewLimit: Int = AppPreferences.DEFAULT_NEW_LIMIT,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: Boolean = false,
    /** Model IDs reported by the gateway, or the preset's guesses before a probe. */
    val models: List<String> = emptyList(),
    val bankTotal: Int = 0,
    val bankNew: Int = 0,
    val bankDue: Int = 0,
    val busy: Boolean = false,
    val reminderEnabled: Boolean = true,
    val reminderMinute: Int = AppPreferences.DEFAULT_REMINDER_MINUTE,
    val notificationsAllowed: Boolean = true,
    val backupUrl: String = "",
    val backupAccount: String = "",
    /** 应用密码只进不出：这里存的只是本次页面上的草稿，保存后立刻清空。 */
    val backupPassword: String = "",
    val backupPasswordSaved: Boolean = false,
    val backupAuto: Boolean = false,
    /** "还没备份过 / 上次备份… / 上次备份失败…" 这一整行，措辞都在 strings.xml 里。 */
    val backupLine: UiMessage = UiMessage(R.string.backup_never),
    val remoteBackups: List<RemoteFile> = emptyList(),
    val pickerOpen: Boolean = false,
    val pendingRestore: String? = null,
    val refusedIssues: List<SnapshotProblem> = emptyList(),
    /** 本地库连接已经关掉：页面只剩"重开应用"这一个出口。[restartDetail] 非空说明是没换成的那一种。 */
    val restartRequired: Boolean = false,
    val restartDetail: String = "",
) {
    val reminderTime: String get() = LocalTime.ofSecondOfDay(reminderMinute.toLong() * 60).toString()

    /** 那行字失败时要标红，但内容已经整条在那条消息里了，这里只认它是哪一种。 */
    val backupFailed: Boolean get() = backupLine.resId == R.string.backup_last_failed
}

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val keyStore: ApiKeyStore,
    private val generator: ChapterGenerator,
    private val wordDao: WordDao,
    private val notifier: GenerationNotifier,
    private val reminder: ReminderControl,
    private val backup: BackupService,
) : ViewModel() {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun onBaseUrl(value: String) { _state.value = _state.value.copy(baseUrl = value) }
    fun onModel(value: String) { _state.value = _state.value.copy(model = value) }
    fun onApiKey(value: String) { _state.value = _state.value.copy(apiKey = value) }
    fun onGenre(value: String) { _state.value = _state.value.copy(genre = value) }
    fun onDensity(value: Density) { _state.value = _state.value.copy(density = value) }
    fun onNewLimit(value: Int) { _state.value = _state.value.copy(dailyNewLimit = value.coerceIn(1, 200)) }

    /** Appearance writes through immediately: the theme and reader observe AppPreferences.appearance. */
    fun onFontScale(delta: Float) = applyAppearance(_state.value.fontScale + delta, _state.value.darkMode)

    fun onDarkMode(value: Boolean) = applyAppearance(_state.value.fontScale, value)

    /** 提醒也是改了即生效：它只是一次性 WorkManager 任务的重排，不需要"保存"。 */
    fun onReminderEnabled(value: Boolean) {
        reminder.enabled = value
        _state.value = _state.value.copy(reminderEnabled = reminder.enabled)
    }

    /** 半小时一格地挪，20:30 点一下就是 21:00；越过午夜绕回当天。 */
    fun onReminderShift(deltaMinutes: Int) {
        reminder.minuteOfDay = Math.floorMod(_state.value.reminderMinute + deltaMinutes, AppPreferences.MINUTES_PER_DAY)
        _state.value = _state.value.copy(reminderMinute = reminder.minuteOfDay)
    }

    /** 用户刚从系统授权页回来：权限状态是新的，当天的提醒也要重新排。 */
    fun onNotificationPermissionResult() {
        reminder.rearm()
        _state.value = _state.value.copy(notificationsAllowed = reminder.areNotificationsAllowed())
    }

    fun onBackupUrl(value: String) { _state.value = _state.value.copy(backupUrl = value) }
    fun onBackupAccount(value: String) { _state.value = _state.value.copy(backupAccount = value) }
    fun onBackupPassword(value: String) { _state.value = _state.value.copy(backupPassword = value) }

    /** 开关同时改期望状态和 WorkManager 里那份真实排班，所以不等"保存"。 */
    fun onBackupAuto(value: Boolean) {
        backup.setAutoBackup(value)
        _state.value = _state.value.copy(backupAuto = prefs.backupEnabled)
    }

    fun saveBackupConfig() = mutate {
        backup.saveConfig(_state.value.backupUrl, _state.value.backupAccount, _state.value.backupPassword)
        refreshBackup()
        _messages.emit(UiMessage(R.string.settings_backup_saved))
    }

    fun backupNow() = mutate {
        _messages.emit(backup.run().toUiMessage())
        refreshBackup()
    }

    fun listBackups() = mutate {
        when (val listing = backup.listRemote()) {
            is Listing.Files ->
                if (listing.files.isEmpty()) _messages.emit(UiMessage(R.string.backup_pick_empty))
                else _state.value = _state.value.copy(remoteBackups = listing.files, pickerOpen = true)

            is Listing.Failed -> _messages.emit(failureMessage(listing.failure, listing.detail))
        }
    }

    fun onPickBackup(name: String) {
        _state.value = _state.value.copy(pickerOpen = false, pendingRestore = name)
    }

    fun closePicker() { _state.value = _state.value.copy(pickerOpen = false) }

    fun cancelRestore() { _state.value = _state.value.copy(pendingRestore = null) }

    fun dismissRefused() { _state.value = _state.value.copy(refusedIssues = emptyList()) }

    fun restoreBackup(name: String) = mutate {
        _state.value = _state.value.copy(pendingRestore = null)
        when (val outcome = backup.restore(name)) {
            is RestoreOutcome.Restored -> {
                _messages.emit(UiMessage(R.string.backup_restored, listOf(outcome.words, outcome.chapters)))
                _state.value = _state.value.copy(restartRequired = true)
            }

            is RestoreOutcome.Refused ->
                _state.value = _state.value.copy(refusedIssues = outcome.issues)

            is RestoreOutcome.Failed ->
                _messages.emit(failureMessage(outcome.failure, outcome.detail))

            // 库连接已经关了，无论文件换没换成，这台机器都必须重开一次应用才能再用。
            is RestoreOutcome.RestartRequired ->
                _state.value = _state.value.copy(restartRequired = true, restartDetail = backupDetailOf(outcome.detail))
        }
    }

    private suspend fun refreshBackup() {
        _state.value = _state.value.copy(
            backupUrl = prefs.webDavUrl,
            backupAccount = prefs.webDavAccount,
            backupPassword = "",
            backupPasswordSaved = backup.hasPassword,
            backupAuto = prefs.backupEnabled,
            backupLine = backupLineOf(backup.lastRun()),
        )
    }

    /** 设置页只报库里真实发生过的那一次，不自己记。 */
    private fun backupLineOf(last: BackupRunEntity?): UiMessage {
        if (last == null) return UiMessage(R.string.backup_never)
        val stamp = BACKUP_STAMP.format(Instant.ofEpochSecond(last.atSec).atZone(zone))
        return if (last.ok) {
            UiMessage(R.string.backup_last_ok, listOf(stamp, last.remoteName, kiloBytesOf(last.bytes)))
        } else {
            UiMessage(
                R.string.backup_last_failed,
                listOf(stamp, failureMessage(storedBackupFailure(last.detail), storedBackupDetail(last.detail))),
            )
        }
    }

    private fun applyAppearance(fontScale: Float, darkMode: Boolean) {
        prefs.fontScale = fontScale
        prefs.darkMode = darkMode
        _state.value = _state.value.copy(fontScale = prefs.fontScale, darkMode = prefs.darkMode)
    }

    fun save() = mutate {
        prefs.baseUrl = _state.value.baseUrl
        prefs.model = _state.value.model
        prefs.genre = _state.value.genre.ifBlank { "宫斗" }
        prefs.density = _state.value.density
        prefs.dailyNewLimit = _state.value.dailyNewLimit
        keyStore.save(_state.value.apiKey)
        _state.value = load().copy(models = _state.value.models)
        _messages.emit(UiMessage(R.string.settings_saved))
    }

    fun testConnection() = mutate {
        val config = draftConfig()
        if (!config.isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@mutate
        }
        if (config.apiKey.isBlank()) {
            _messages.emit(UiMessage(R.string.settings_probe_missing_key))
            return@mutate
        }
        val result = OpenAiCompatClient(config).probe()
        if (result.ok) {
            // The gateway's own list is the only trustworthy source of model IDs: product
            // names and API names differ, and the user cannot tell them apart by eye.
            _state.value = _state.value.copy(models = result.models)
            _messages.emit(
                UiMessage(
                    if (result.models.isEmpty()) R.string.settings_probe_ok
                    else R.string.settings_probe_ok_models,
                    listOf(result.models.size),
                ),
            )
        } else {
            _messages.emit(
                UiMessage(
                    probeFailureLabel(result.failure),
                    listOf(probeDetailOf(result.detail)),
                ),
            )
        }
    }

    /** Fills the address the user keeps mistyping and offers its known model IDs. */
    fun applyPreset(preset: ProviderPreset) {
        _state.value = _state.value.copy(
            baseUrl = preset.baseUrl,
            models = preset.knownModels,
            model = _state.value.model.ifBlank { preset.knownModels.firstOrNull().orEmpty() },
        )
    }

    fun onPickModel(value: String) {
        _state.value = _state.value.copy(model = value)
    }

    fun generateChapter() = mutate {
        val config = draftConfig()
        if (!config.isUsable) {
            _messages.emit(UiMessage(R.string.settings_need_config))
            return@mutate
        }
        val outcome = notifier.runTracked {
            generator.generate(prefs.genre, prefs.density, prefs.dailyNewLimit)
        }
        _messages.emit(outcome.toUiMessage())
    }

    /** Runs an action with the busy flag set, then refreshes the bank counters. */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                block()
            } finally {
                _state.value = _state.value.copy(busy = false)
                // 恢复之后本机库连接已经关掉，再查一次数就是拿一个已经关掉的库：
                // 这时候页面上只剩"重开应用"一个对话框，不需要也不能再查。
                if (!_state.value.restartRequired) refreshCounts()
            }
        }
    }

    private fun load(): SettingsState = SettingsState(
        baseUrl = prefs.baseUrl,
        model = prefs.model,
        apiKey = keyStore.key(),
        keyPersistent = keyStore.isPersistent,
        genre = prefs.genre,
        density = prefs.density,
        dailyNewLimit = prefs.dailyNewLimit,
        fontScale = prefs.fontScale,
        darkMode = prefs.darkMode,
        reminderEnabled = reminder.enabled,
        reminderMinute = reminder.minuteOfDay,
        notificationsAllowed = reminder.areNotificationsAllowed(),
        backupUrl = prefs.webDavUrl,
        backupAccount = prefs.webDavAccount,
        backupAuto = prefs.backupEnabled,
        backupPasswordSaved = backup.hasPassword,
    )

    private suspend fun refreshCounts() {
        _state.value = _state.value.copy(
            bankTotal = wordDao.totalCount(),
            bankNew = wordDao.newCount(),
            bankDue = wordDao.dueCount(Instant.now().epochSecond),
        )
    }

    private fun draftConfig() = AiConfig(
        baseUrl = _state.value.baseUrl,
        apiKey = _state.value.apiKey,
        model = _state.value.model,
    )

    init {
        viewModelScope.launch {
            refreshCounts()
            refreshBackup()
        }
    }

    private companion object {
        val zone: ZoneId = ZoneId.systemDefault()
        val BACKUP_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
