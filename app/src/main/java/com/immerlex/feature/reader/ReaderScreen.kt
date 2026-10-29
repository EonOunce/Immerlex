package com.immerlex.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.textFor
import com.immerlex.core.db.LOG_KIND_LEARN

@Composable
fun ReaderScreen(vm: ReaderViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val layoutState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var chaptersOpen by remember { mutableStateOf(false) }
    var shelfOpen by remember { mutableStateOf(false) }
    var newBookOpen by remember { mutableStateOf(false) }
    var composerOpen by remember { mutableStateOf(false) }
    var rewriteOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var englishOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.textFor(msg))
        }
    }

    LaunchedEffect(Unit) {
        vm.undoPrompts.collect { prompt ->
            val result = snackbar.showSnackbar(
                message = context.getString(
                    if (prompt.kind == LOG_KIND_LEARN) {
                        R.string.reader_grade_saved_learn
                    } else {
                        R.string.reader_grade_saved_recall
                    },
                ),
                actionLabel = context.getString(R.string.reader_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(prompt)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = RootHandledInsets) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReaderHeader(
                title = state.title,
                book = state.book,
                number = state.number,
                mode = state.mode,
                wordCount = state.wordCount,
                learned = state.learned,
                recalled = state.recalled,
                loading = state.loading,
                hasChapter = state.hasChapter,
                isShort = state.isShort,
                generating = state.generating,
                englishOnly = englishOnly,
                onSelectMode = vm::setMode,
                onToggleEnglish = { englishOnly = !englishOnly },
                onGenerate = vm::generate,
                onOpenChapters = { chaptersOpen = true },
                onOpenShelf = { shelfOpen = true },
            )
            when {
                state.loading -> CenteredNote(stringResource(R.string.reader_loading))
                !state.hasChapter -> EmptyChapter(
                    generating = state.generating,
                    onGenerate = vm::generate,
                    onOpenSettings = onOpenSettings,
                )
                else -> ChapterBody(
                    state = state,
                    layoutState = layoutState,
                    englishOnly = englishOnly,
                    onWordTapped = vm::select,
                )
            }
        }
    }

    state.selected?.let { word ->
        WordCardSheet(
            word = word,
            access = state.access,
            previews = state.previews,
            tricky = state.tricky,
            onGrade = vm::grade,
            onToggleTricky = vm::toggleTricky,
            onLinkTricky = vm::linkTricky,
            onUnlinkTricky = vm::unlinkTricky,
            onOpenTricky = vm::openTricky,
            onAddTricky = vm::addTrickyManual,
            onDismiss = vm::dismissCard,
        )
    }
    if (chaptersOpen) {
        ChapterSheet(
            chapters = state.chapters,
            currentId = state.chapterId,
            generating = state.generating,
            onPick = { id ->
                chaptersOpen = false
                vm.openChapter(id)
            },
            onDismiss = { chaptersOpen = false },
            onRewrite = {
                chaptersOpen = false
                rewriteOpen = state.chapterId != 0L
            },
            onDelete = { id ->
                chaptersOpen = false
                deleteTarget = id
            },
        )
    }
    if (shelfOpen) {
        ShelfPage(
            state = state,
            onSelectBook = vm::switchBook,
            onOpenShort = vm::openChapter,
            onNewBook = { newBookOpen = true },
            onWriteShort = { composerOpen = true },
            onRequestDeleteShorts = { ids ->
                shelfOpen = false
                vm.deleteShorts(ids)
            },
            onBack = { shelfOpen = false },
        )
    }
    if (newBookOpen) {
        NewBookDialog(
            onDismiss = { newBookOpen = false },
            onCreate = { name ->
                newBookOpen = false
                vm.switchBook(name)
            },
        )
    }
    if (composerOpen) {
        ShortComposer(
            onDismiss = { composerOpen = false },
            onWrite = { plan ->
                composerOpen = false
                shelfOpen = false
                vm.generateShort(plan)
            },
        )
    }
    if (rewriteOpen) {
        ConfirmDialog(
            title = stringResource(R.string.reader_rewrite),
            body = stringResource(R.string.reader_rewrite_body, state.wordCount),
            confirmLabel = stringResource(R.string.reader_rewrite_action),
            onConfirm = {
                rewriteOpen = false
                vm.rewrite()
            },
            onDismiss = { rewriteOpen = false },
        )
    }
    deleteTarget?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.reader_chapter_delete),
            body = stringResource(R.string.reader_delete_body, state.chapters.find { it.id == id }?.number ?: 0),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteTarget = null
                vm.deleteChapter(id)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ReaderHeader(
    title: String,
    book: String,
    number: Int,
    mode: ReaderMode,
    wordCount: Int,
    learned: Int,
    recalled: Int,
    loading: Boolean,
    hasChapter: Boolean,
    isShort: Boolean,
    generating: Boolean,
    englishOnly: Boolean,
    onSelectMode: (ReaderMode) -> Unit,
    onToggleEnglish: () -> Unit,
    onGenerate: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenShelf: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    loading -> stringResource(R.string.reader_loading)
                    else -> title.ifBlank { book }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
                    .clickable(enabled = hasChapter && !isShort, onClick = onOpenChapters),
            )
            if (hasChapter && !isShort) {
                IconButton(onClick = onOpenChapters, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.reader_chapters_title))
                }
            }
            TextButton(onClick = onOpenShelf) { Text(stringResource(R.string.reader_shelf)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            ReaderMode.entries.forEach { entry ->
                val selected = entry == mode
                if (selected) {
                    Button(onClick = { onSelectMode(entry) }, shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(modeLabel(entry)))
                    }
                } else {
                    OutlinedButton(onClick = { onSelectMode(entry) }, shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(modeLabel(entry)))
                    }
                }
            }
            if (hasChapter && !isShort) {
                TextButton(onClick = onGenerate, enabled = !generating) {
                    Text(
                        stringResource(
                            if (generating) R.string.reader_generating else R.string.reader_generate,
                        ),
                    )
                }
            }
        }
        if (hasChapter) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isShort) {
                        stringResource(R.string.reader_short_progress, wordCount, learned, recalled)
                    } else {
                        stringResource(R.string.reader_progress, number, wordCount, learned, recalled)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = ImmerlexColors.Meta,
                )
                TextButton(onClick = onToggleEnglish) {
                    Text(
                        stringResource(
                            if (englishOnly) R.string.reader_english_only_on else R.string.reader_english_only,
                        ),
                    )
                }
            }
        }
        if (generating) {
            Text(
                stringResource(R.string.reader_generating),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun modeLabel(mode: ReaderMode): Int = when (mode) {
    ReaderMode.LEARN -> R.string.reader_mode_learn
    ReaderMode.REVIEW -> R.string.reader_mode_review
}
