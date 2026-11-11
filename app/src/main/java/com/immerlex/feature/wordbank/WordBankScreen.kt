package com.immerlex.feature.wordbank

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.ImmerlexApplication
import com.immerlex.app.R
import com.immerlex.app.readPlainText
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.masteryInk
import com.immerlex.app.ui.textFor
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictEntry
import com.immerlex.core.ocr.PdfScan
import com.immerlex.core.word.Mastery
import com.immerlex.core.word.mastery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun WordBankScreen(vm: WordBankViewModel, onOpenTricky: () -> Unit) {
    val words by vm.words.collectAsStateWithLifecycle()
    val dictHits by vm.dictionaryHits.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val container = (context.applicationContext as ImmerlexApplication).container
    val importFailedText = stringResource(R.string.bank_import_failed)
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WordEntity?>(null) }
    var dictDetail by remember { mutableStateOf<DictEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<WordEntity?>(null) }
    var pdfLine by remember { mutableStateOf<String?>(null) }
    var pdfNote by remember { mutableStateOf<String?>(null) }

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

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                pdfNote = null
                val scan = container.pdfOcr.scan(context, uri) { done, total ->
                    pdfLine = context.getString(R.string.bank_pdf_page, done, total)
                }
                pdfLine = null
                when (scan) {
                    is PdfScan.Ok -> {
                        if (scan.truncated) {
                            pdfNote = context.getString(R.string.bank_pdf_truncated, scan.totalPages, scan.pages)
                        }
                        vm.importText(scan.lines.joinToString("\n"), source = "pdf")
                    }

                    PdfScan.CannotOpen -> snackbar.showSnackbar(context.getString(R.string.bank_pdf_failed))
                    PdfScan.Empty -> snackbar.showSnackbar(context.getString(R.string.bank_pdf_empty))
                }
            }
        }
    }

    val exportPicker = rememberWordCsvExport(vm, snackbar)

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.textFor(msg))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = RootHandledInsets,
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
                onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onExport = {
                    val stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    exportPicker.launch("immerlex-words-$stamp.csv")
                },
                onOpenTricky = onOpenTricky,
            )
            pdfNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.labelMedium,
                    color = ImmerlexColors.Meta,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            val progressText = pdfLine ?: if (busy) stringResource(R.string.bank_working) else null
            if (progressText != null) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        progressText,
                        style = MaterialTheme.typography.labelLarge,
                        color = ImmerlexColors.Meta,
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(words, key = { it.id }) { word ->
                    WordRow(word, onClick = { detail = word })
                }
                dictionaryGroup(dictHits, onOpen = { dictDetail = it })
                if (words.isEmpty() && dictHits.isEmpty()) {
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
    dictDetail?.let { entry ->
        DictionaryCardDialog(
            entry = entry,
            loadMaterial = vm::materialFor,
            onAdd = {
                vm.addWord(entry.word, "")
                dictDetail = null
            },
            onDismiss = { dictDetail = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankHeader(
    total: Int,
    query: String,
    vm: WordBankViewModel,
    onImport: () -> Unit,
    onImportImage: () -> Unit,
    onImportPdf: () -> Unit,
    onExport: () -> Unit,
    onOpenTricky: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.bank_search)) },
            trailingIcon = {
                IconButton(onClick = onImportImage) {
                    Icon(
                        painterResource(R.drawable.ic_camera),
                        contentDescription = stringResource(R.string.bank_import_image),
                    )
                }
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BankChip(R.string.bank_import_txt, onClick = onImport)
            BankChip(R.string.bank_import_pdf, onClick = onImportPdf)
            BankChip(R.string.bank_export, onClick = onExport, enabled = total > 0)
            BankChip(R.string.bank_tricky, onClick = onOpenTricky)
        }
    }
}

@Composable
private fun BankChip(@StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ImmerlexColors.Meta,
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun WordRow(word: WordEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(word.word, style = MaterialTheme.typography.titleSmall)
            if (word.translation.isNotEmpty()) {
                Text(
                    word.translation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(masteryLabel(word)),
                style = MaterialTheme.typography.labelMedium,
                color = masteryInk(word),
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
