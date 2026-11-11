package com.immerlex.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.PillSegmented
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.accentLabel
import com.immerlex.app.ui.textFor
import com.immerlex.core.quiz.EarlyReview

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    material: MaterialViewModel,
    onOpenBible: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val materialState by material.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val opened = rememberSaveable { mutableStateOf<String?>(SECTION_BACKUP) }

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
            // 换机要先解决的事排在最前面：学完上传、学前下载，所以这一节是唯一默认摊开的。
            Section(
                SECTION_BACKUP,
                R.string.settings_backup_section,
                stringResource(
                    when {
                        !state.backupConfigured -> R.string.settings_summary_unset
                        state.backupFailed -> R.string.settings_summary_failed
                        else -> R.string.settings_summary_ready
                    },
                ),
                opened,
                alert = !state.backupConfigured || state.backupFailed,
            ) {
                BackupSection(state, vm)
            }
            Section(
                SECTION_MATERIAL,
                R.string.settings_material_section,
                stringResource(R.string.settings_summary_material, materialState.words),
                opened,
                alert = materialState.words == 0,
            ) {
                MaterialSection(material, snackbar)
            }
            Section(
                SECTION_APPEARANCE,
                R.string.settings_appearance_section,
                stringResource(
                    R.string.settings_summary_appearance,
                    state.fontScale.toInt(),
                    stringResource(if (state.darkMode) R.string.settings_theme_dark else R.string.settings_theme_light),
                    stringResource(accentLabel(state.accent)),
                ),
                opened,
            ) {
                AppearanceSection(state, vm, onOpenKeyboard)
            }
            Section(
                SECTION_REMINDER,
                R.string.settings_reminder_section,
                if (state.reminderEnabled) stringResource(R.string.settings_reminder_time, state.reminderTime)
                else stringResource(R.string.settings_summary_reminder_off),
                opened,
            ) {
                ReminderSection(state, vm)
            }
            Section(
                SECTION_QUIZ,
                R.string.settings_quiz_section,
                stringResource(R.string.settings_summary_quiz, state.earlyGroupSize),
                opened,
            ) {
                Text(stringResource(R.string.settings_early_group), style = MaterialTheme.typography.labelLarge)
                PillSegmented(
                    options = EarlyReview.GROUP_SIZES.map { it to stringResource(R.string.settings_early_group_count, it) },
                    selected = state.earlyGroupSize,
                    onPick = vm::onEarlyGroupSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Section(
                SECTION_GENERATE,
                R.string.settings_generate_section,
                densitySummary(state.density, state.dailyNewLimit),
                opened,
            ) {
                GenerateSection(state, vm, onOpenBible)
            }
            Section(
                SECTION_AI,
                R.string.settings_ai_section,
                state.model.takeIf { state.aiConfigured } ?: stringResource(R.string.settings_summary_unset),
                opened,
                alert = !state.aiConfigured,
            ) {
                AiSection(state, vm)
            }

            InfoSection(
                stringResource(R.string.settings_bank_section),
                stringResource(R.string.settings_bank_summary, state.bankTotal, state.bankNew, state.bankDue),
            )

            NavSection(stringResource(R.string.about_open), onOpenAbout)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一节：标题 + 折起来时那一行现状，点开才是控件。全页只有一把钥匙 [opened]，所以一次只摊开一节。 */
@Composable
private fun Section(
    key: String,
    @StringRes title: Int,
    summary: String,
    opened: MutableState<String?>,
    alert: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    DisclosureSection(
        title = stringResource(title),
        summary = summary,
        opened = opened.value == key,
        onToggle = { opened.value = if (opened.value == key) null else key },
        alert = alert,
        content = content,
    )
}

private const val SECTION_BACKUP = "backup"
private const val SECTION_MATERIAL = "material"
private const val SECTION_APPEARANCE = "appearance"
private const val SECTION_REMINDER = "reminder"
private const val SECTION_QUIZ = "quiz"
private const val SECTION_GENERATE = "generate"
private const val SECTION_AI = "ai"
