package com.immerlex.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.immerlex.core.reader.ReaderWord

/** 阅读页上那几层浮层各自开没开。放在一处，免得六个 rememberSaveable 把页面编排挤没了。 */
private class ReaderOverlays {
    var chaptersOpen by mutableStateOf(false)
    var shelfOpen by mutableStateOf(false)
    var newBookOpen by mutableStateOf(false)
    var composerOpen by mutableStateOf(false)
    var rewriteOpen by mutableStateOf(false)
    var deleteTarget by mutableStateOf<Long?>(null)
}

@Composable
fun ReaderScreen(vm: ReaderViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val widthDp = LocalConfiguration.current.screenWidthDp
    val panes = remember(widthDp) { readerPanesOf(widthDp) }
    val layoutState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val overlays = remember { ReaderOverlays() }
    var englishOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.textFor(msg))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = RootHandledInsets) { padding ->
        ReaderPanes(
            vm = vm,
            state = state,
            panes = panes,
            layoutState = layoutState,
            englishOnly = englishOnly,
            overlays = overlays,
            padding = padding,
            onOpenSettings = onOpenSettings,
            onToggleEnglish = { englishOnly = !englishOnly },
        )
    }

    if (!panes.card) {
        state.selected?.let { word -> ReaderWordCard(vm, state, asPane = false, word = word) }
    }
    ReaderSheets(overlays, vm, state)
    ReaderDialogs(overlays, vm, state)
}

@Composable
private fun ReaderPanes(
    vm: ReaderViewModel,
    state: ReaderState,
    panes: ReaderPanes,
    layoutState: MutableState<TextLayoutResult?>,
    englishOnly: Boolean,
    overlays: ReaderOverlays,
    padding: PaddingValues,
    onOpenSettings: () -> Unit,
    onToggleEnglish: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (panes.chapters) {
            ReaderChapterRail(
                chapters = state.chapters,
                currentId = state.chapterId,
                generating = state.generating,
                onPick = vm::openChapter,
                onDelete = { overlays.deleteTarget = it },
                onRewrite = { overlays.rewriteOpen = state.chapterId != 0L },
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                modifier = Modifier.widthIn(max = READER_BODY_MAX_WIDTH_DP.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
            ) {
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
                    chaptersAsSheet = !panes.chapters,
                    onSelectMode = vm::setMode,
                    onToggleEnglish = onToggleEnglish,
                    onGenerate = vm::generate,
                    onOpenChapters = { overlays.chaptersOpen = true },
                    onOpenShelf = { overlays.shelfOpen = true },
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
        if (panes.card) {
            // 宽度一直占着：词卡开合时正文不重新折行，刚点过的词不会跳走。
            Box(Modifier.width(READER_CARD_PANE_DP.dp + 24.dp).fillMaxHeight()) {
                state.selected?.let { word -> ReaderWordCard(vm, state, asPane = true, word = word) }
            }
        }
    }
}

@Composable
private fun ReaderSheets(overlays: ReaderOverlays, vm: ReaderViewModel, state: ReaderState) {
    if (overlays.chaptersOpen) {
        ChapterSheet(
            chapters = state.chapters,
            currentId = state.chapterId,
            generating = state.generating,
            onPick = { id ->
                overlays.chaptersOpen = false
                vm.openChapter(id)
            },
            onDismiss = { overlays.chaptersOpen = false },
            onRewrite = {
                overlays.chaptersOpen = false
                overlays.rewriteOpen = state.chapterId != 0L
            },
            onDelete = { id ->
                overlays.chaptersOpen = false
                overlays.deleteTarget = id
            },
        )
    }
    if (overlays.shelfOpen) {
        ShelfPage(
            state = state,
            onSelectBook = vm::switchBook,
            onOpenShort = vm::openChapter,
            onNewBook = { overlays.newBookOpen = true },
            onWriteShort = { overlays.composerOpen = true },
            onRequestDeleteShorts = { ids ->
                overlays.shelfOpen = false
                vm.deleteShorts(ids)
            },
            onBack = { overlays.shelfOpen = false },
        )
    }
    if (overlays.newBookOpen) {
        NewBookDialog(
            onDismiss = { overlays.newBookOpen = false },
            onCreate = { name ->
                overlays.newBookOpen = false
                vm.switchBook(name)
            },
        )
    }
    if (overlays.composerOpen) {
        ShortComposer(
            onDismiss = { overlays.composerOpen = false },
            onWrite = { plan ->
                overlays.composerOpen = false
                overlays.shelfOpen = false
                vm.generateShort(plan)
            },
        )
    }
}

@Composable
private fun ReaderDialogs(overlays: ReaderOverlays, vm: ReaderViewModel, state: ReaderState) {
    if (overlays.rewriteOpen) {
        ConfirmDialog(
            title = stringResource(R.string.reader_rewrite),
            body = stringResource(R.string.reader_rewrite_body, state.wordCount),
            confirmLabel = stringResource(R.string.reader_rewrite_action),
            onConfirm = {
                overlays.rewriteOpen = false
                vm.rewrite()
            },
            onDismiss = { overlays.rewriteOpen = false },
        )
    }
    overlays.deleteTarget?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.reader_chapter_delete),
            body = stringResource(R.string.reader_delete_body, state.chapters.find { it.id == id }?.number ?: 0),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                overlays.deleteTarget = null
                vm.deleteChapter(id)
            },
            onDismiss = { overlays.deleteTarget = null },
        )
    }
}

@Composable
private fun ReaderWordCard(vm: ReaderViewModel, state: ReaderState, asPane: Boolean, word: ReaderWord) {
    WordCardHost(
        asPane = asPane,
        word = word,
        access = state.access,
        previews = state.previews,
        tricky = state.tricky,
        graded = state.graded,
        undo = state.pendingUndo,
        onGrade = vm::grade,
        onUndo = vm::undo,
        onToggleTricky = vm::toggleTricky,
        onLinkTricky = vm::linkTricky,
        onUnlinkTricky = vm::unlinkTricky,
        onOpenTricky = vm::openTricky,
        onAddTricky = vm::addTrickyManual,
        onDismiss = vm::dismissCard,
    )
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
    chaptersAsSheet: Boolean,
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
                    .clickable(enabled = hasChapter && !isShort && chaptersAsSheet, onClick = onOpenChapters),
            )
            // 左边已经有章节目录时，标题不再当入口，省得同一件事有两个开关。
            if (hasChapter && !isShort && chaptersAsSheet) {
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
