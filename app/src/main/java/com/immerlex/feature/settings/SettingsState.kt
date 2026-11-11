package com.immerlex.feature.settings

import com.immerlex.app.R
import com.immerlex.app.ui.UiMessage
import com.immerlex.core.ai.Density
import com.immerlex.core.backup.DownloadVerdict
import com.immerlex.core.backup.RemoteFile
import com.immerlex.core.backup.SnapshotProblem
import com.immerlex.core.quiz.EarlyReview
import com.immerlex.core.settings.Accent
import com.immerlex.core.settings.AppPreferences
import com.immerlex.core.settings.DEFAULT_FONT_SCALE
import java.time.LocalTime

data class SettingsState(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val keyPersistent: Boolean = true,
    /** "配过了"只认保存进本机的那一份（load 时算）：草稿填到一半不算，不然表单会在点保存之前收起来。 */
    val aiConfigSaved: Boolean = false,
    val genre: String = "",
    val density: Density = Density.MEDIUM,
    val dailyNewLimit: Int = AppPreferences.DEFAULT_NEW_LIMIT,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: Boolean = false,
    val autoSpeak: Boolean = false,
    /** 全 app 主色，两档暖色，改了立即生效。 */
    val accent: Accent = Accent.DEFAULT,
    /** 提前复习一组几个词：5 / 10 / 20。 */
    val earlyGroupSize: Int = EarlyReview.DEFAULT_GROUP_SIZE,
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
    /** 这台机器在备份文件名里叫什么；留空表示用机型名，core 那边还会再洗一遍。 */
    val backupDevice: String = "",
    val backupAuto: Boolean = false,
    /** "还没备份过 / 上次备份… / 上次备份失败…" 这一整行，措辞都在 strings.xml 里。 */
    val backupLine: UiMessage = UiMessage(R.string.backup_never),
    val remoteBackups: List<RemoteFile> = emptyList(),
    /** 覆盖本机之前自动留下的那几份，不联网就能列出来。 */
    val localBackups: List<RemoteFile> = emptyList(),
    val pickerOpen: Boolean = false,
    val pendingRestore: PendingRestore? = null,
    val refusedIssues: List<SnapshotProblem> = emptyList(),
    /** 本地库连接已经关掉：页面只剩"重开应用"这一个出口。[restartDetail] 非空说明是没换成的那一种。 */
    val restartRequired: Boolean = false,
    val restartDetail: String = "",
) {
    val reminderTime: String get() = LocalTime.ofSecondOfDay(reminderMinute.toLong() * 60).toString()

    /** 那行字失败时要标红，但内容已经整条在那条消息里了，这里只认它是哪一种。 */
    val backupFailed: Boolean get() = backupLine.resId == R.string.backup_last_failed

    /** 网盘地址是预填的，所以"配过了"只认账号与应用密码这两样用户自己填的。 */
    val backupConfigured: Boolean get() = backupAccount.isNotBlank() && backupPasswordSaved

    val aiConfigured: Boolean get() = aiConfigSaved
}

/**
 * 选中的那一份，和点它那一刻本机的处境。判定跟着目标一起存，对话框才不会在重绘时
 * 拿另一台机器此刻的状态去问第二个问题。
 */
data class PendingRestore(
    val name: String,
    /** true = 退回本机留的快照，不联网。 */
    val local: Boolean,
    val verdict: DownloadVerdict,
)
