package com.immerlex.feature.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import java.util.Locale

@Composable
fun ReaderScreen(vm: ReaderViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val layoutState = remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.showSnackbar(context.getString(msg.resId, *msg.args.toTypedArray()))
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
            previews = state.previews,
            onGrade = vm::grade,
            onDismiss = vm::dismissCard,
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
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (loading) stringResource(R.string.reader_loading) else title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordCardSheet(
    word: ReaderWord,
    previews: List<Pair<Rating, Int>>,
    onGrade: (Rating) -> Unit,
    onDismiss: () -> Unit,
) {
    val speak = rememberTtsSpeaker()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.lookup, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (word.ipa.isNotEmpty()) {
                    Text(word.ipa, style = MaterialTheme.typography.bodyMedium, color = ImmerlexColors.Meta)
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { speak(word.lookup) }) { Text(stringResource(R.string.reader_speak)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(word.zh.ifBlank { stringResource(R.string.reader_no_gloss) })
            if (word.def.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    word.def,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (word.example.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(word.example, style = MaterialTheme.typography.bodyMedium, color = ImmerlexColors.Meta)
            }
            if (word.wordId == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.reader_not_in_bank),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previews.isEmpty()) {
                    Text(stringResource(R.string.reader_cannot_grade))
                }
                previews.forEach { (rating, days) ->
                    OutlinedButton(onClick = { onGrade(rating) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(gradeLabel(rating)))
                            Text(
                                stringResource(R.string.grade_days, days),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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

private fun gradeLabel(rating: Rating): Int = when (rating) {
    Rating.AGAIN -> R.string.grade_again
    Rating.HARD -> R.string.grade_hard
    Rating.GOOD -> R.string.grade_good
    Rating.EASY -> R.string.grade_easy
}

@Composable
private fun rememberTtsSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    val engine = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        engine.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.value?.setLanguage(Locale.US)
            }
        }
        onDispose {
            engine.value?.shutdown()
            engine.value = null
        }
    }
    return { text -> engine.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "immerlex-word") }
}
