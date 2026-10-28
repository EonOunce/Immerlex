package com.immerlex.feature.wordbank

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.ImmerlexApplication
import com.immerlex.app.R
import com.immerlex.app.readPlainText
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.masteryPalette
import com.immerlex.app.ui.textFor
import com.immerlex.core.db.WordEntity
import com.immerlex.core.word.Mastery
import com.immerlex.core.word.mastery
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun WordBankScreen(vm: WordBankViewModel) {
    val words by vm.words.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val container = (context.applicationContext as ImmerlexApplication).container
    val importFailedText = stringResource(R.string.bank_import_failed)
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WordEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<WordEntity?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = context.readPlainText(uri)
                if (text == null) {
                    snackbar.showSnackbar(importFailedText)
                } else {
                    vm.importText(text)
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val lines = container.ocr.lines(context, uri)
                when {
                    lines == null -> snackbar.showSnackbar(context.getString(R.string.bank_ocr_failed))
                    lines.isEmpty() -> snackbar.showSnackbar(context.getString(R.string.bank_ocr_empty))
                    else -> vm.importText(lines.joinToString("\n"), source = "ocr")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.textFor(msg))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.bank_add)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            BankHeader(
                total = words.size,
                query = query,
                vm = vm,
                onImport = { importPicker.launch(arrayOf("text/plain", "text/csv", "*/*")) },
                onImportImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.bank_working),
                        style = MaterialTheme.typography.labelLarge,
                        color = ImmerlexColors.Meta,
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(words, key = { it.id }) { word ->
                    WordRow(word, onClick = { detail = word })
                }
                if (words.isEmpty()) {
                    item(key = "empty") { EmptyHint(hasQuery = query.isNotBlank()) }
                }
            }
        }
    }

    if (showAdd) {
        AddWordDialog(
            onDismiss = { showAdd = false },
            onSubmit = { word, gloss ->
                vm.addWord(word, gloss)
                showAdd = false
            },
        )
    }
    detail?.let { word ->
        WordDetailDialog(
            word = word,
            onDismiss = { detail = null },
            onDelete = {
                pendingDelete = word
                detail = null
            },
        )
    }
    pendingDelete?.let { word ->
        DeleteWordDialog(
            word = word,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                vm.delete(word)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun BankHeader(
    total: Int,
    query: String,
    vm: WordBankViewModel,
    onImport: () -> Unit,
    onImportImage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.bank_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.bank_count, total),
                style = MaterialTheme.typography.labelLarge,
                color = ImmerlexColors.Meta,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onImport) {
                Text(stringResource(R.string.bank_import_txt))
            }
            TextButton(onClick = onImportImage) {
                Text(stringResource(R.string.bank_import_image))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.bank_search)) },
        )
    }
}

@Composable
private fun WordRow(word: WordEntity, onClick: () -> Unit) {
    val (soft, deep) = masteryPalette(word)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .background(soft, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(word.word, style = MaterialTheme.typography.titleMedium)
            if (word.translation.isNotEmpty()) {
                Text(
                    word.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(masteryLabel(word)),
                style = MaterialTheme.typography.labelLarge,
                color = deep,
            )
            word.dueEpochSec?.let {
                Text(formatDue(it), style = MaterialTheme.typography.labelSmall, color = ImmerlexColors.Meta)
            }
        }
    }
}

@Composable
private fun EmptyHint(hasQuery: Boolean) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(if (hasQuery) R.string.bank_empty_search else R.string.bank_empty_all),
            color = ImmerlexColors.Meta,
        )
    }
}

internal fun masteryLabel(word: WordEntity): Int = when (word.mastery()) {
    Mastery.NEW -> R.string.mastery_new
    Mastery.LEARNING -> R.string.mastery_learning
    Mastery.MASTERED -> R.string.mastery_mastered
}

internal fun formatDue(epochSec: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .format(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))
