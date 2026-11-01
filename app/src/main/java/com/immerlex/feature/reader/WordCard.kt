package com.immerlex.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAppearance
import com.immerlex.app.ui.SpeechProblem
import com.immerlex.app.ui.rememberWordSpeaker
import com.immerlex.app.ui.speechProblemText
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.WordAccess
import com.immerlex.core.tricky.TrickyCard
import com.immerlex.core.tricky.TrickyWord

/**
 * 词卡的两种外壳，内容只有一份：手机上是底部抽屉，平板够宽时（≥1000dp）是右边一栏。
 * 抽屉靠下滑关掉，栏里没有可下滑的地方，所以栏多一个「关闭」。
 */
@Composable
fun WordCardHost(
    asPane: Boolean,
    word: ReaderWord,
    access: WordAccess,
    previews: List<Pair<Rating, Int>>,
    tricky: TrickyCard?,
    graded: Boolean,
    undo: UndoPrompt?,
    onGrade: (Rating) -> Unit,
    onUndo: () -> Unit,
    onToggleTricky: () -> Unit,
    onLinkTricky: (TrickyWord) -> Unit,
    onUnlinkTricky: (TrickyWord) -> Unit,
    onOpenTricky: (TrickyWord) -> Unit,
    onAddTricky: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (asPane) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(12.dp).width(READER_CARD_PANE_DP.dp).fillMaxHeight(),
        ) {
            WordCardContent(
                word = word,
                access = access,
                previews = previews,
                tricky = tricky,
                graded = graded,
                undo = undo,
                onGrade = onGrade,
                onUndo = onUndo,
                onToggleTricky = onToggleTricky,
                onLinkTricky = onLinkTricky,
                onUnlinkTricky = onUnlinkTricky,
                onOpenTricky = onOpenTricky,
                onAddTricky = onAddTricky,
                onDismiss = onDismiss,
                showClose = true,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    } else {
        WordCardSheet(
            word = word,
            access = access,
            previews = previews,
            tricky = tricky,
            graded = graded,
            undo = undo,
            onGrade = onGrade,
            onUndo = onUndo,
            onToggleTricky = onToggleTricky,
            onLinkTricky = onLinkTricky,
            onUnlinkTricky = onUnlinkTricky,
            onOpenTricky = onOpenTricky,
            onAddTricky = onAddTricky,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordCardSheet(
    word: ReaderWord,
    access: WordAccess,
    previews: List<Pair<Rating, Int>>,
    tricky: TrickyCard?,
    graded: Boolean,
    undo: UndoPrompt?,
    onGrade: (Rating) -> Unit,
    onUndo: () -> Unit,
    onToggleTricky: () -> Unit,
    onLinkTricky: (TrickyWord) -> Unit,
    onUnlinkTricky: (TrickyWord) -> Unit,
    onOpenTricky: (TrickyWord) -> Unit,
    onAddTricky: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WordCardContent(
            word = word,
            access = access,
            previews = previews,
            tricky = tricky,
            graded = graded,
            undo = undo,
            onGrade = onGrade,
            onUndo = onUndo,
            onToggleTricky = onToggleTricky,
            onLinkTricky = onLinkTricky,
            onUnlinkTricky = onUnlinkTricky,
            onOpenTricky = onOpenTricky,
            onAddTricky = onAddTricky,
            onDismiss = onDismiss,
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun WordCardContent(
    word: ReaderWord,
    access: WordAccess,
    previews: List<Pair<Rating, Int>>,
    tricky: TrickyCard?,
    graded: Boolean,
    undo: UndoPrompt?,
    onGrade: (Rating) -> Unit,
    onUndo: () -> Unit,
    onToggleTricky: () -> Unit,
    onLinkTricky: (TrickyWord) -> Unit,
    onUnlinkTricky: (TrickyWord) -> Unit,
    onOpenTricky: (TrickyWord) -> Unit,
    onAddTricky: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = false,
) {
    val speaker = rememberWordSpeaker()
    val autoSpeak = LocalAppearance.current.autoSpeak
    // 开了自动发音，词卡一打开就读这一遍：引擎还没就绪时由 WordSpeaker 等初始化完补读，不在这里等。
    LaunchedEffect(word.lookup) {
        if (autoSpeak) speaker.speak(word.lookup)
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.lookup, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (word.ipa.isNotEmpty()) {
                Text(word.ipa, style = MaterialTheme.typography.bodyMedium, color = ImmerlexColors.Meta)
            }
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = { speaker.speak(word.lookup) },
                enabled = speaker.problem != SpeechProblem.NoEngine && speaker.problem != SpeechProblem.NoEnglish,
            ) { Text(stringResource(R.string.reader_speak)) }
            if (showClose) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
        if (speaker.problem != SpeechProblem.None) {
            Text(
                stringResource(speechProblemText(speaker.problem)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(word.zh.ifBlank { stringResource(R.string.reader_no_gloss) })
        if (word.def.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(word.def, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (word.example.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(word.example, style = MaterialTheme.typography.bodyMedium, color = ImmerlexColors.Meta)
        }
        Spacer(Modifier.height(14.dp))
        if (word.wordId == null) {
            Text(stringResource(R.string.reader_not_in_bank), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.reader_cannot_grade), color = ImmerlexColors.Meta)
        } else if (!graded) {
            GradeControls(
                access = access,
                previews = previews,
                onGrade = onGrade,
                onDismiss = onDismiss,
            )
        }
        undo?.let { UndoRow(it, onUndo) }
        TrickySection(
            word = word.lookup,
            card = tricky,
            onToggle = onToggleTricky,
            onLink = onLinkTricky,
            onUnlink = onUnlinkTricky,
            onOpen = onOpenTricky,
            onAddManual = onAddTricky,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GradeControls(
    access: WordAccess,
    previews: List<Pair<Rating, Int>>,
    onGrade: (Rating) -> Unit,
    onDismiss: () -> Unit,
) {
    when (access) {
        WordAccess.Learn, WordAccess.Recall -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rating.entries.forEach { rating ->
                    val days = previews.firstOrNull { it.first == rating }?.second
                    OutlinedButton(onClick = { onGrade(rating) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(gradeLabel(rating)))
                            if (days != null) {
                                Text(
                                    stringResource(R.string.grade_days, days),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            if (access == WordAccess.Learn) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.reader_note_learn),
                    style = MaterialTheme.typography.labelMedium,
                    color = ImmerlexColors.Meta,
                )
            }
        }

        WordAccess.Early -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 两个按钮同权重：不引导用户挑"更好看"的那个答案。没想起来才算这次回忆，想起来了只是走开。
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_early_remembered))
            }
            val days = previews.firstOrNull { it.first == Rating.AGAIN }?.second
            OutlinedButton(onClick = { onGrade(Rating.AGAIN) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.reader_early_forgot))
                    if (days != null) {
                        Text(
                            stringResource(R.string.grade_days, days),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        WordAccess.ViewOnly -> Text(
            stringResource(R.string.reader_note_learning),
            color = ImmerlexColors.Meta,
        )
    }
}

/** 撤销挂在词卡里并写清是哪个词：底部浮条挡正文，也对不上刚点的是哪一个。 */
@Composable
private fun UndoRow(undo: UndoPrompt, onUndo: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onUndo) {
        Text(stringResource(R.string.reader_undo_word, undo.word))
    }
}

private fun gradeLabel(rating: Rating): Int = when (rating) {
    Rating.AGAIN -> R.string.grade_again
    Rating.HARD -> R.string.grade_hard
    Rating.GOOD -> R.string.grade_good
    Rating.EASY -> R.string.grade_easy
}
