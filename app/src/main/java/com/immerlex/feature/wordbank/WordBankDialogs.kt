package com.immerlex.feature.wordbank

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.db.WordEntity

@Composable
fun AddWordDialog(onDismiss: () -> Unit, onSubmit: (word: String, gloss: String) -> Unit) {
    var word by remember { mutableStateOf("") }
    var gloss by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bank_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bank_field_word)) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gloss,
                    onValueChange = { gloss = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.bank_field_gloss_optional)) },
                    supportingText = { Text(stringResource(R.string.bank_gloss_hint)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(word, gloss) },
                enabled = word.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun DeleteWordDialog(word: WordEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bank_delete_title)) },
        text = { Text(stringResource(R.string.bank_delete_body, word.word)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun WordDetailDialog(word: WordEntity, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, style = MaterialTheme.typography.titleLarge)
                if (word.phonetic.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(word.phonetic, color = ImmerlexColors.Meta)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (word.translation.isNotEmpty()) {
                    Text(word.translation)
                    Spacer(Modifier.height(8.dp))
                }
                if (word.definition.isNotEmpty()) {
                    Text(
                        word.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    stringResource(
                        R.string.bank_fsrs_summary,
                        stringResource(masteryLabel(word)),
                        word.reps,
                        word.lapses,
                        word.intervalDays,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = ImmerlexColors.Meta,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), modifier = Modifier.padding(end = 4.dp))
            }
        },
    )
}
