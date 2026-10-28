package com.immerlex.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAppearance
import com.immerlex.app.ui.LocalMasteryTones
import com.immerlex.app.ui.textFor
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord

@Composable
fun ReaderScreen(vm: ReaderViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val layoutState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var chaptersOpen by remember { mutableStateOf(false) }
    var rewriteOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.textFor(msg))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReaderHeader(
                title = state.title,
                number = state.number,
                mode = state.mode,
                wordCount = state.wordCount,
                graded = state.graded,
                loading = state.loading,
                hasChapter = state.hasChapter,
                generating = state.generating,
                onSelectMode = vm::setMode,
                onGenerate = vm::generate,
                onOpenChapters = { chaptersOpen = true },
            )
            when {
                state.loading -> CenteredNote(stringResource(R.string.reader_loading))
                !state.hasChapter -> EmptyChapter(
                    generating = state.generating,
                    onGenerate = vm::generate,
                    onOpenSettings = onOpenSettings,
                )
                else -> ChapterBody(state = state, layoutState = layoutState, onWordTapped = vm::select)
            }
        }
    }

    state.selected?.let { word ->
        WordCardSheet(
            word = word,
            gradeMode = state.gradeMode,
            previews = state.previews,
            onGrade = vm::grade,
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
        val chapter = state.chapters.find { it.id == id }
        ConfirmDialog(
            title = stringResource(R.string.reader_chapter_delete),
            body = stringResource(R.string.reader_delete_body, chapter?.number ?: 0),
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
    number: Int,
    mode: ReaderMode,
    wordCount: Int,
    graded: Int,
    loading: Boolean,
    hasChapter: Boolean,
    generating: Boolean,
    onSelectMode: (ReaderMode) -> Unit,
    onGenerate: () -> Unit,
    onOpenChapters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (loading) stringResource(R.string.reader_loading) else title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
                    .clickable(enabled = hasChapter, onClick = onOpenChapters),
            )
            if (hasChapter) {
                IconButton(onClick = onOpenChapters, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.reader_chapters_title))
                }
            }
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
            if (hasChapter) {
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
            Text(
                stringResource(R.string.reader_progress, number, wordCount, graded),
                style = MaterialTheme.typography.labelMedium,
                color = ImmerlexColors.Meta,
            )
            if (mode == ReaderMode.REVIEW) {
                Text(
                    stringResource(R.string.reader_review_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ChapterBody(
    state: ReaderState,
    layoutState: MutableState<TextLayoutResult?>,
    onWordTapped: (ReaderWord) -> Unit,
) {
    val wordsByLookup = remember(state.blocks) {
        state.blocks.flatten().filterIsInstance<ReaderSegment.Word>().associateBy { it.word.lookup }
    }
    val tones = LocalMasteryTones.current
    val fontScale = LocalAppearance.current.fontScale
    val annotated = remember(state.blocks, state.mode, state.bankWords, tones) {
        buildChapterText(state.blocks, state.mode, state.bankWords, tones)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = annotated,
            style = TextStyle(
                fontSize = fontScale.sp,
                lineHeight = (fontScale * 2f).sp,
                textAlign = TextAlign.Justify,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .pointerInput(annotated) {
                    detectTapGestures { offset ->
                        val layout = layoutState.value ?: return@detectTapGestures
                        val position = layout.getOffsetForPosition(offset)
                        annotated.getStringAnnotations(WORD_ANNOTATION_TAG, position, position)
                            .firstOrNull()?.item
                            ?.let { lookup -> wordsByLookup[lookup]?.word?.let { onWordTapped(it) } }
                    }
                },
            onTextLayout = { layoutState.value = it },
        )
    }
}

@Composable
private fun EmptyChapter(
    generating: Boolean,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.reader_empty), color = ImmerlexColors.Meta, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGenerate, enabled = !generating) {
            Text(stringResource(if (generating) R.string.reader_generating else R.string.reader_generate_first))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.reader_go_settings)) }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = ImmerlexColors.Meta)
    }
}

private fun modeLabel(mode: ReaderMode): Int = when (mode) {
    ReaderMode.LEARN -> R.string.reader_mode_learn
    ReaderMode.REVIEW -> R.string.reader_mode_review
}
