package com.immerlex.feature.wordbank

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.capsule
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.material.MaterialEntry

/**
 * 词库搜不到时，词典里还有的那些词。这一组只摆结果：没进词库的词不排复习、没有颜色，
 * 点开是词典卡，卡上只有一样的"加入词库"一个动作。
 */
fun LazyListScope.dictionaryGroup(hits: List<DictEntry>, onOpen: (DictEntry) -> Unit) {
    if (hits.isEmpty()) return
    item(key = "dict_header") {
        Text(
            stringResource(R.string.bank_dict_more, hits.size),
            style = MaterialTheme.typography.labelLarge,
            color = ImmerlexColors.Meta,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        )
    }
    items(hits, key = { "dict:${it.word}" }) { entry ->
        DictionaryRow(entry, onClick = { onOpen(entry) })
    }
}

@Composable
private fun DictionaryRow(entry: DictEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .capsule(radius = 18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (entry.phonetic.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(entry.phonetic, style = MaterialTheme.typography.labelSmall, color = ImmerlexColors.Meta)
                }
            }
            if (entry.translation.isNotEmpty()) {
                Text(
                    entry.translation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 词典卡：词库那一行不在的时候，这个词还有什么可以看的。素材随开随读。 */
@Composable
fun DictionaryCardDialog(
    entry: DictEntry,
    loadMaterial: suspend (String) -> MaterialEntry?,
    onAdd: () -> Unit,
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
            TextButton(onClick = onAdd) { Text(stringResource(R.string.bank_dict_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
