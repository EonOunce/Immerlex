package com.immerlex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.material.MaterialEntry

/**
 * 词典卡：一个词在词库里长什么样、素材里还有什么。词库页与测验页共用一张，
 * 素材随开随读。[onAdd] 为 null 时不摆「加入词库」（测验里只查，不动词库）。
 */
@Composable
fun DictionaryCardDialog(
    entry: DictEntry,
    loadMaterial: suspend (String) -> MaterialEntry?,
    onAdd: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var material by remember(entry.word) { mutableStateOf<MaterialEntry?>(null) }
    LaunchedEffect(entry.word) { material = loadMaterial(entry.word) }
    val definition = entry.definition.ifBlank { material?.definition.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.word, style = MaterialTheme.typography.titleLarge)
                if (entry.phonetic.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(entry.phonetic, color = ImmerlexColors.Meta)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                if (entry.translation.isNotEmpty()) {
                    Text(entry.translation)
                    Spacer(Modifier.height(8.dp))
                }
                if (definition.isNotEmpty()) {
                    Text(
                        definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                material?.let { MaterialLines(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd ?: onDismiss) {
                Text(stringResource(if (onAdd == null) R.string.action_close else R.string.bank_dict_add))
            }
        },
        dismissButton = if (onAdd == null) {
            null
        } else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
        },
    )
}

@Composable
private fun MaterialLines(material: MaterialEntry) {
    if (material.example.isNotEmpty()) {
        Text(material.example, style = MaterialTheme.typography.bodyMedium)
        if (material.exampleZh.isNotEmpty()) {
            Text(
                material.exampleZh,
                style = MaterialTheme.typography.bodyMedium,
                color = ImmerlexColors.Meta,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    if (material.collocations.isNotEmpty()) {
        Text(
            stringResource(
                R.string.bank_dict_collocations,
                material.collocations.joinToString(" · ") { it.en },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = ImmerlexColors.Meta,
        )
        Spacer(Modifier.height(8.dp))
    }
    if (material.confusables.isNotEmpty()) {
        Text(
            stringResource(
                R.string.bank_dict_confusables,
                material.confusables.joinToString(" · ") { it.word },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = ImmerlexColors.Meta,
        )
    }
}
