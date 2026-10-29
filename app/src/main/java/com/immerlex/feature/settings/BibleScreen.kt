package com.immerlex.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.textFor
import com.immerlex.core.bible.BibleSection
import com.immerlex.core.bible.sectionOf
import com.immerlex.core.db.BibleEntryEntity
import com.immerlex.core.db.CharacterStateEntity

private const val DIALOG_NONE = 0
private const val DIALOG_ENTRY = 1
private const val DIALOG_CAST = 2
private const val DIALOG_DELETE_ENTRY = 3
private const val DIALOG_DELETE_CAST = 4

@Composable
fun BibleScreen(vm: BibleViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbar.showSnackbar(context.textFor(msg)) }
    }

    // 弹层目标用平铺的可保存字段：转屏不该丢掉正在打的字。
    var dialog by rememberSaveable { mutableIntStateOf(DIALOG_NONE) }
    var targetId by rememberSaveable { mutableLongStateOf(0L) }
    var targetSection by rememberSaveable { mutableIntStateOf(0) }
    var fieldName by rememberSaveable { mutableStateOf("") }
    var fieldRole by rememberSaveable { mutableStateOf("") }
    var fieldText by rememberSaveable { mutableStateOf("") }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = RootHandledInsets) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.bible_back))
                }
                Text(stringResource(R.string.bible_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.bible_subtitle, state.genre),
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Meta,
                )
            }

            OutlinedTextField(
                value = state.seed,
                onValueChange = vm::onSeed,
                label = { Text(stringResource(R.string.bible_seed_label)) },
                supportingText = { Text(stringResource(R.string.bible_seed_hint)) },
                minLines = 3,
            )
            Button(onClick = vm::expand, enabled = !state.busy) {
                Text(
                    stringResource(
                        when {
                            state.busy -> R.string.bible_working
                            state.entries.isEmpty() -> R.string.bible_expand
                            else -> R.string.bible_reroll
                        },
                    ),
                )
            }
            if (state.entries.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
            }

            if (state.entries.isEmpty() && state.cast.isEmpty()) {
                Text(stringResource(R.string.bible_empty))
            }
            // 一栏都不空着手也能写：没配密钥、或只想自己定一条禁止事项的读者，不必先叫 AI 跑一次。
            Title(stringResource(R.string.bible_sections_section))
            BibleSection.entries.forEach { section ->
                SectionHeader(
                    label = stringResource(sectionLabel(section)),
                    onAdd = {
                        targetId = 0L
                        targetSection = section.ordinal
                        fieldText = ""
                        dialog = DIALOG_ENTRY
                    },
                )
                state.entries.filter { sectionOf(it.section) == section }.forEach { entry ->
                    EntryRow(
                        entry = entry,
                        onEdit = {
                            targetId = entry.id
                            fieldText = entry.text
                            dialog = DIALOG_ENTRY
                        },
                        onDelete = {
                            targetId = entry.id
                            dialog = DIALOG_DELETE_ENTRY
                        },
                    )
                }
            }

            SectionHeader(
                label = stringResource(R.string.bible_cast_section),
                addHint = R.string.bible_add_person,
                bold = true,
                onAdd = {
                    targetId = 0L
                    fieldName = ""
                    fieldRole = ""
                    fieldText = ""
                    dialog = DIALOG_CAST
                },
            )
            state.cast.forEach { person ->
                PersonRow(
                    person = person,
                    onEdit = {
                        targetId = person.id
                        fieldName = person.name
                        fieldRole = person.role
                        fieldText = person.status
                        dialog = DIALOG_CAST
                    },
                    onDelete = {
                        targetId = person.id
                        dialog = DIALOG_DELETE_CAST
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        DIALOG_ENTRY -> TextEditorDialog(
            title = stringResource(R.string.bible_field_text),
            initial = fieldText,
            onSave = {
                if (targetId == 0L) {
                    vm.addEntry(BibleSection.entries[targetSection], it)
                } else {
                    vm.saveEntry(targetId, it)
                }
                dialog = DIALOG_NONE
            },
            onDismiss = { dialog = DIALOG_NONE },
        )

        DIALOG_CAST -> PersonEditorDialog(
            isNew = targetId == 0L,
            name = fieldName,
            role = fieldRole,
            status = fieldText,
            onSave = { name, role, status ->
                vm.saveCast(targetId, name, role, status)
                dialog = DIALOG_NONE
            },
            onDismiss = { dialog = DIALOG_NONE },
        )

        DIALOG_DELETE_ENTRY -> ConfirmDialog(
            title = stringResource(R.string.action_delete),
            body = stringResource(R.string.bible_delete_entry_body),
            onConfirm = {
                vm.deleteEntry(targetId)
                dialog = DIALOG_NONE
            },
            onDismiss = { dialog = DIALOG_NONE },
        )

        DIALOG_DELETE_CAST -> ConfirmDialog(
            title = stringResource(R.string.action_delete),
            body = stringResource(R.string.bible_delete_person_body),
            onConfirm = {
                vm.deleteCast(targetId)
                dialog = DIALOG_NONE
            },
            onDismiss = { dialog = DIALOG_NONE },
        )
    }
}

@Composable
private fun SectionHeader(label: String, addHint: Int = R.string.bible_add_entry, bold: Boolean = false, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(addHint))
        }
    }
}

@Composable
private fun EntryRow(entry: BibleEntryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(entry.text, modifier = Modifier.weight(1f))
        if (entry.authoredByUser) UserBadge()
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.bible_edit))
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
    }
}

@Composable
private fun PersonRow(person: CharacterStateEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (person.role.isBlank()) person.name else "${person.name}（${person.role}）",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (person.authoredByUser) UserBadge()
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.bible_edit))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
        Text(
            person.status.ifBlank { stringResource(R.string.bible_status_unknown) },
            style = MaterialTheme.typography.bodyMedium,
            color = ImmerlexColors.Meta,
        )
        if (person.conflictChapterNumber > 0) {
            Text(
                stringResource(R.string.bible_conflict, person.conflictChapterNumber, person.conflictText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun UserBadge() {
    Text(
        stringResource(R.string.bible_user_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 6.dp),
    )
}

@Composable
private fun TextEditorDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, minLines = 3, label = { Text(title) })
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PersonEditorDialog(
    isNew: Boolean,
    name: String,
    role: String,
    status: String,
    onSave: (name: String, role: String, status: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editedName by remember(name) { mutableStateOf(name) }
    var editedRole by remember(role) { mutableStateOf(role) }
    var editedStatus by remember(status) { mutableStateOf(status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNew) R.string.bible_add_person else R.string.bible_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bible_field_name)) },
                )
                OutlinedTextField(
                    value = editedRole,
                    onValueChange = { editedRole = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bible_field_role)) },
                )
                OutlinedTextField(
                    value = editedStatus,
                    onValueChange = { editedStatus = it },
                    minLines = 2,
                    label = { Text(stringResource(R.string.bible_field_status)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editedName, editedRole, editedStatus) }) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

private fun sectionLabel(section: BibleSection): Int = when (section) {
    BibleSection.TONE -> R.string.bible_section_tone
    BibleSection.PROTAGONIST -> R.string.bible_section_protagonist
    BibleSection.ENDING -> R.string.bible_section_ending
    BibleSection.FORBIDDEN -> R.string.bible_section_forbidden
}
