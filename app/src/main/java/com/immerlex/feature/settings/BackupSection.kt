package com.immerlex.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.restartApp
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.backupIssueLabel
import com.immerlex.app.ui.kiloBytesOf
import com.immerlex.app.ui.textFor
import com.immerlex.core.backup.SnapshotProblem

/** 备份与恢复整段。四个对话框由 [BackupDialogs] 单独挂在 Scaffold 之外，免得滚动容器裁掉。 */
@Composable
internal fun BackupSection(state: SettingsState, vm: SettingsViewModel) {
    val context = LocalContext.current

    OutlinedTextField(
        value = state.backupUrl,
        onValueChange = vm::onBackupUrl,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_backup_url)) },
        supportingText = { Text(stringResource(R.string.settings_backup_url_hint)) },
    )
    OutlinedTextField(
        value = state.backupAccount,
        onValueChange = vm::onBackupAccount,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_backup_account)) },
        supportingText = { Text(stringResource(R.string.settings_backup_account_hint)) },
    )
    OutlinedTextField(
        value = state.backupPassword,
        onValueChange = vm::onBackupPassword,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_backup_password)) },
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            Text(
                stringResource(
                    if (state.backupPasswordSaved) R.string.settings_backup_password_hint
                    else R.string.settings_backup_password_needed,
                ),
            )
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = vm::saveBackupConfig, enabled = !state.busy) {
            Text(stringResource(R.string.settings_backup_save))
        }
        OutlinedButton(onClick = vm::backupNow, enabled = !state.busy) {
            Text(stringResource(R.string.settings_backup_now))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.settings_backup_auto),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = state.backupAuto, onCheckedChange = vm::onBackupAuto)
    }
    Text(
        context.textFor(state.backupLine),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.backupFailed) ImmerlexColors.Rust else ImmerlexColors.Meta,
    )
    OutlinedButton(onClick = vm::listBackups, enabled = !state.busy) {
        Text(stringResource(R.string.settings_backup_restore))
    }
}

@Composable
internal fun BackupDialogs(state: SettingsState, vm: SettingsViewModel) {
    val context = LocalContext.current
    if (state.pickerOpen) BackupPicker(state, vm)
    state.pendingRestore?.let { RestoreConfirm(it, vm) }
    if (state.refusedIssues.isNotEmpty()) RefusedDialog(state.refusedIssues, vm)
    if (state.restartRequired) RestartDialog(state.restartDetail) { context.restartApp() }
}

/** 云端文件列表：名字里就带日期，所以直接当标签用。 */
@Composable
private fun BackupPicker(state: SettingsState, vm: SettingsViewModel) {
    AlertDialog(
        onDismissRequest = vm::closePicker,
        title = { Text(stringResource(R.string.backup_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.remoteBackups.forEach { file ->
                    TextButton(onClick = { vm.onPickBackup(file.name) }) {
                        Text(stringResource(R.string.backup_file_row, file.name, kiloBytesOf(file.sizeBytes)))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = vm::closePicker) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RestoreConfirm(name: String, vm: SettingsViewModel) {
    AlertDialog(
        onDismissRequest = vm::cancelRestore,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = { Text(stringResource(R.string.backup_confirm_body, name)) },
        confirmButton = {
            Button(onClick = { vm.restoreBackup(name) }) {
                Text(stringResource(R.string.backup_action_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RefusedDialog(issues: List<SnapshotProblem>, vm: SettingsViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissRefused,
        title = { Text(stringResource(R.string.backup_refused)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                issues.forEach { Text(stringResource(backupIssueLabel(it.kind), it.detail)) }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::dismissRefused) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** 库连接已经关了，这个对话框没有第二条出路：关掉它只会让页面继续读一个不存在的库。 */
@Composable
private fun RestartDialog(detail: String, onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    if (detail.isEmpty()) R.string.backup_restart_title else R.string.backup_restart_broken_title,
                ),
            )
        },
        text = {
            Text(
                if (detail.isEmpty()) stringResource(R.string.backup_restart_body)
                else stringResource(R.string.backup_restart_broken, detail),
            )
        },
        confirmButton = {
            Button(onClick = onRestart) { Text(stringResource(R.string.backup_restart_action)) }
        },
    )
}
