package com.immerlex.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.AccentSwatches
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.accentLabel
import com.immerlex.core.ai.Density
import com.immerlex.core.ai.ProviderPresets

@Composable
internal fun AiSection(state: SettingsState, vm: SettingsViewModel) {
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
}

@Composable
internal fun GenerateSection(state: SettingsState, vm: SettingsViewModel, onOpenBible: () -> Unit) {
    OutlinedTextField(
        value = state.genre,
        onValueChange = vm::onGenre,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_genre)) },
    )
    Text(stringResource(R.string.settings_density), style = MaterialTheme.typography.labelLarge)
    SegmentedRow(
        options = Density.entries.map { it to stringResource(densityLabel(it)) },
        selected = state.density,
        onPick = vm::onDensity,
    )
    Text(
        stringResource(densityRange(state.density)),
        style = MaterialTheme.typography.bodySmall,
        color = ImmerlexColors.Meta,
    )
    StepperRow(
        label = stringResource(R.string.settings_new_limit),
        value = state.dailyNewLimit.toString(),
        enabled = !state.busy,
        onMinus = { vm.onNewLimit(state.dailyNewLimit - 5) },
        onPlus = { vm.onNewLimit(state.dailyNewLimit + 5) },
    )
    Button(onClick = vm::generateChapter, enabled = !state.busy) {
        Text(
            if (state.busy) stringResource(R.string.settings_working)
            else stringResource(R.string.settings_generate),
        )
    }
    OutlinedButton(onClick = onOpenBible) {
        Text(stringResource(R.string.settings_open_bible))
    }
}

@Composable
internal fun AppearanceSection(state: SettingsState, vm: SettingsViewModel, onOpenKeyboard: () -> Unit) {
    StepperRow(
        label = stringResource(R.string.settings_font_size),
        value = state.fontScale.toInt().toString(),
        onMinus = { vm.onFontScale(-1f) },
        onPlus = { vm.onFontScale(1f) },
    )
    SwitchRow(stringResource(R.string.settings_dark_mode), state.darkMode, vm::onDarkMode)
    SwitchRow(stringResource(R.string.settings_auto_speak), state.autoSpeak, vm::onAutoSpeak)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.settings_accent),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        AccentSwatches(selected = state.accent, onPick = vm::onAccent)
    }
    OutlinedButton(onClick = onOpenKeyboard) {
        Text(stringResource(R.string.settings_keyboard))
    }
}

@Composable
internal fun ReminderSection(state: SettingsState, vm: SettingsViewModel) {
    SwitchRow(stringResource(R.string.settings_reminder_on), state.reminderEnabled, vm::onReminderEnabled)
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
        val context = LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> vm.onNotificationPermissionResult() }
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
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onMinus, enabled = enabled) { Text("−") }
        Spacer(Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        OutlinedButton(onClick = onPlus, enabled = enabled) { Text("＋") }
    }
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

internal fun densityLabel(tier: Density): Int = when (tier) {
    Density.SPARSE -> R.string.density_sparse
    Density.MEDIUM -> R.string.density_medium
    Density.DENSE -> R.string.density_dense
}

private fun densityRange(tier: Density): Int = when (tier) {
    Density.SPARSE -> R.string.density_range_sparse
    Density.MEDIUM -> R.string.density_range_medium
    Density.DENSE -> R.string.density_range_dense
}

/** 折起来的标题要说清现在是什么档，不然点开才知道。 */
@Composable
internal fun densitySummary(tier: Density, dailyNewLimit: Int): String =
    stringResource(R.string.settings_summary_generate, stringResource(densityLabel(tier)), dailyNewLimit)

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
