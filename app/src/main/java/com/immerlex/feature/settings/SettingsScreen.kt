package com.immerlex.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.textFor
import com.immerlex.core.ai.Density
import com.immerlex.core.ai.ProviderPresets

@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenBible: () -> Unit, onOpenAbout: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.onNotificationPermissionResult() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.textFor(msg))
        }
    }

    BackupDialogs(state, vm)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = RootHandledInsets) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_ai_section))
            Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.labelLarge)
            ChipRow(
                options = ProviderPresets.map { it.baseUrl to presetName(it.id) },
                selected = state.baseUrl.trim().removeSuffix("/"),
                onPick = { url -> vm.applyPreset(ProviderPresets.first { it.baseUrl == url }) },
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::onBaseUrl,
                singleLine = true,
                label = { Text(stringResource(R.string.settings_base_url)) },
                supportingText = { Text(stringResource(R.string.settings_base_url_hint)) },
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = vm::onModel,
                singleLine = true,
                label = { Text(stringResource(R.string.settings_model)) },
            )
            if (state.models.isNotEmpty()) {
                Text(stringResource(R.string.settings_model_pick), style = MaterialTheme.typography.labelLarge)
                ChipRow(
                    options = state.models.take(MAX_MODEL_CHIPS).map { it to it },
                    selected = state.model,
                    onPick = vm::onPickModel,
                )
            }
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = vm::onApiKey,
                singleLine = true,
                label = { Text(stringResource(R.string.settings_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        stringResource(
                            if (state.keyPersistent) R.string.settings_key_hint
                            else R.string.settings_key_volatile,
                        ),
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::save, enabled = !state.busy) {
                    Text(stringResource(R.string.settings_save))
                }
                OutlinedButton(onClick = vm::testConnection, enabled = !state.busy) {
                    Text(stringResource(R.string.settings_test))
                }
            }

            SectionTitle(stringResource(R.string.settings_generate_section))
            OutlinedTextField(
                value = state.genre,
                onValueChange = vm::onGenre,
                singleLine = true,
                label = { Text(stringResource(R.string.settings_genre)) },
            )
            Text(stringResource(R.string.settings_density), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Density.entries.forEach { tier ->
                    val selected = tier == state.density
                    if (selected) {
                        Button(onClick = { vm.onDensity(tier) }, shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(densityLabel(tier)))
                        }
                    } else {
                        OutlinedButton(onClick = { vm.onDensity(tier) }, shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(densityLabel(tier)))
                        }
                    }
                }
            }
            Text(
                stringResource(densityRange(state.density)),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_new_limit), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { vm.onNewLimit(state.dailyNewLimit - 5) }) { Text("−") }
                Spacer(Modifier.width(10.dp))
                Text(state.dailyNewLimit.toString(), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { vm.onNewLimit(state.dailyNewLimit + 5) }) { Text("＋") }
            }
            Button(onClick = vm::generateChapter, enabled = !state.busy) {
                Text(if (state.busy) stringResource(R.string.settings_working) else stringResource(R.string.settings_generate))
            }
            OutlinedButton(onClick = onOpenBible) {
                Text(stringResource(R.string.settings_open_bible))
            }

            SectionTitle(stringResource(R.string.settings_appearance_section))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { vm.onFontScale(-1f) }) { Text("A−") }
                Spacer(Modifier.width(10.dp))
                Text(state.fontScale.toInt().toString(), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { vm.onFontScale(1f) }) { Text("A＋") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_dark_mode),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.darkMode, onCheckedChange = vm::onDarkMode)
            }

            SectionTitle(stringResource(R.string.settings_reminder_section))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_reminder_on),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.reminderEnabled, onCheckedChange = vm::onReminderEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_reminder_time, state.reminderTime),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { vm.onReminderShift(-30) }) { Text("−") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { vm.onReminderShift(30) }) { Text("＋") }
            }
            if (!state.notificationsAllowed) {
                // 权限没给，提醒排得再准也弹不出来，所以这里给一个直接去授权的入口。
                // Android 13 以下没有这个运行时权限，申请不会弹框，只能把人送到系统设置里。
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openNotificationSettings()
                    }
                }) {
                    Text(stringResource(R.string.settings_notify_allow))
                }
                Text(
                    stringResource(R.string.settings_notify_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Rust,
                )
            }

            SectionTitle(stringResource(R.string.settings_backup_section))
            BackupSection(state, vm)

            SectionTitle(stringResource(R.string.settings_bank_section))
            Text(
                stringResource(R.string.settings_bank_summary, state.bankTotal, state.bankNew, state.bankDue),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(onClick = onOpenAbout) {
                Text(stringResource(R.string.about_open))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun densityLabel(tier: Density): Int = when (tier) {
    Density.SPARSE -> R.string.density_sparse
    Density.MEDIUM -> R.string.density_medium
    Density.DENSE -> R.string.density_dense
}

private fun densityRange(tier: Density): Int = when (tier) {
    Density.SPARSE -> R.string.density_range_sparse
    Density.MEDIUM -> R.string.density_range_medium
    Density.DENSE -> R.string.density_range_dense
}

private const val MAX_MODEL_CHIPS = 24

@Composable
private fun presetName(id: String): String = when (id) {
    "deepseek" -> stringResource(R.string.settings_preset_deepseek)
    "zhipu" -> stringResource(R.string.settings_preset_zhipu)
    "moonshot" -> stringResource(R.string.settings_preset_moonshot)
    "dashscope" -> stringResource(R.string.settings_preset_dashscope)
    "openai" -> stringResource(R.string.settings_preset_openai)
    "openrouter" -> stringResource(R.string.settings_preset_openrouter)
    else -> id
}

/** One wrapped row of choice chips; used for the gateway list and the fetched model list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, name) ->
            FilterChip(onClick = { onPick(value) }, selected = value == selected, label = { Text(name) })
        }
    }
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
