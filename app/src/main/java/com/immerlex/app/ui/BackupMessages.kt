package com.immerlex.app.ui

import com.immerlex.app.R
import com.immerlex.core.backup.BackupFailure
import com.immerlex.core.backup.BackupOutcome
import com.immerlex.core.backup.SnapshotIssue

fun BackupOutcome.toUiMessage(): UiMessage = when (this) {
    is BackupOutcome.Uploaded ->
        UiMessage(R.string.backup_uploaded, listOf(remoteName, kiloBytesOf(bytes), pruned))

    is BackupOutcome.Failed -> failureMessage(failure, detail)
}

fun backupFailureLabel(failure: BackupFailure?): Int = when (failure) {
    null -> R.string.backup_failed
    BackupFailure.NOT_CONFIGURED -> R.string.backup_need_config
    BackupFailure.UNAUTHORIZED -> R.string.backup_unauthorized
    BackupFailure.MISSING_FOLDER -> R.string.backup_missing_folder
    BackupFailure.TIMEOUT -> R.string.backup_timeout
    BackupFailure.TLS -> R.string.backup_tls
    BackupFailure.UNREACHABLE -> R.string.backup_unreachable
    BackupFailure.HTTP -> R.string.backup_http
    BackupFailure.CORRUPT -> R.string.backup_corrupt
    BackupFailure.LOCAL -> R.string.backup_local
}

/** 校验没过的那些原因，一条一句人话；细节（版本号、缺哪张表）由 core 那边算好带过来。 */
fun backupIssueLabel(issue: SnapshotIssue): Int = when (issue) {
    SnapshotIssue.EMPTY -> R.string.backup_issue_empty
    SnapshotIssue.NOT_GZIP -> R.string.backup_issue_not_gzip
    SnapshotIssue.NOT_SQLITE -> R.string.backup_issue_not_sqlite
    SnapshotIssue.NO_VERSION -> R.string.backup_issue_no_version
    SnapshotIssue.TOO_NEW -> R.string.backup_issue_too_new
    SnapshotIssue.CHECK_FAILED -> R.string.backup_issue_check_failed
    SnapshotIssue.MISSING_TABLES -> R.string.backup_issue_missing_tables
}

fun failureMessage(failure: BackupFailure?, detail: String): UiMessage =
    UiMessage(backupFailureLabel(failure), listOf(backupDetailOf(detail)))

/** 备份按 KB 报数就够看了，字节数只会让人去心算。 */
fun kiloBytesOf(bytes: Long): Int = (bytes / 1024).toInt().coerceAtLeast(1)

/** 网络与网关的原始文本用户读不懂，留个头就够了：该说的上面那句已经说了。 */
fun backupDetailOf(detail: String): String = detail.take(MAX_DETAIL).ifBlank { " " }

/** 库里存的失败短句是"枚举名: 原始信息"，拆开才能各归各位。 */
fun storedBackupFailure(detail: String): BackupFailure? =
    BackupFailure.entries.firstOrNull { detail.startsWith("${it.name}: ") }

fun storedBackupDetail(detail: String): String = backupDetailOf(detail.substringAfter(": ", detail))

private const val MAX_DETAIL = 160
