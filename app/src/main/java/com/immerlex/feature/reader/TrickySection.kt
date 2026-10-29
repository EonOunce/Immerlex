package com.immerlex.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.tricky.TrickyCard
import com.immerlex.core.tricky.TrickyWord

/** 词卡底部的易错区：标记、双向易混列表、推荐候选与手动添加。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrickySection(
    word: String,
    card: TrickyCard?,
    onToggle: () -> Unit,
    onLink: (TrickyWord) -> Unit,
    onUnlink: (TrickyWord) -> Unit,
    onOpen: (TrickyWord) -> Unit,
    onAddManual: (String) -> Unit,
) {
    if (card == null) return
    var manualOpen by remember(word) { mutableStateOf(false) }

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.tricky_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggle) {
            Text(stringResource(if (card.marked) R.string.tricky_unmark else R.string.tricky_mark))
        }
    }
    if (!card.marked) {
        Text(
            stringResource(R.string.tricky_hint_unmarked),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Meta,
        )
        return
    }

    Text(
        stringResource(R.string.tricky_links_title),
        style = MaterialTheme.typography.labelLarge,
        color = ImmerlexColors.Meta,
    )
    if (card.links.isEmpty()) {
        Text(
            stringResource(R.string.tricky_links_empty),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Meta,
        )
    }
    card.links.forEach { link ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(link) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(link.word, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    link.gloss.ifBlank { stringResource(R.string.tricky_no_gloss) },
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Meta,
                )
            }
            IconButton(onClick = { onUnlink(link) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.tricky_remove),
                    tint = ImmerlexColors.Meta,
                )
            }
        }
    }

    if (card.suggestions.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.tricky_suggestions),
            style = MaterialTheme.typography.labelMedium,
            color = ImmerlexColors.Meta,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            card.suggestions.forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = { onLink(suggestion) },
                    label = { Text(suggestion.word) },
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { manualOpen = true }) {
        Text(stringResource(R.string.tricky_manual_label))
    }

    if (manualOpen) {
        ManualTrickyDialog(
            onDismiss = { manualOpen = false },
            onConfirm = {
                manualOpen = false
                onAddManual(it)
            },
        )
    }
}

@Composable
private fun ManualTrickyDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tricky_manual_label)) },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_MANUAL_WORD) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tricky_manual_label)) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tricky_manual_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(typed) }, enabled = typed.isNotBlank()) {
                Text(stringResource(R.string.tricky_manual_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MAX_MANUAL_WORD = 40
