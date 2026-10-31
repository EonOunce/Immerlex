package com.immerlex.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.immerlex.core.db.ChapterOption
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.WordAccess
import com.immerlex.core.tricky.TrickyCard
import com.immerlex.core.tricky.TrickyWord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCardSheet(
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
    val speaker = rememberWordSpeaker()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
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
                        // 两个按钮同权重：不引导用户挑"更好看"的那个答案。
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
}

/** 撤销挂在词卡里并写清是哪个词：底部浮条挡正文，也对不上刚点的是哪一个。 */
@Composable
private fun UndoRow(undo: UndoPrompt, onUndo: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onUndo) {
        Text(stringResource(R.string.reader_undo_word, undo.word))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSheet(
    chapters: List<ChapterOption>,
    currentId: Long,
    generating: Boolean,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    onRewrite: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.reader_chapters_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (chapters.isEmpty()) {
                Text(stringResource(R.string.reader_chapters_empty), color = ImmerlexColors.Meta)
            }
            chapters.forEach { chapter ->
                val current = chapter.id == currentId
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onPick(chapter.id) }, modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (current) {
                                stringResource(R.string.reader_chapter_current, chapter.title)
                            } else {
                                stringResource(R.string.reader_chapter_entry, chapter.title, chapter.number)
                            },
                            color = if (current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    IconButton(onClick = { onDelete(chapter.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.reader_chapter_delete),
                            tint = ImmerlexColors.Meta,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRewrite, enabled = !generating && chapters.isNotEmpty()) {
                Text(stringResource(if (generating) R.string.reader_generating else R.string.reader_rewrite))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun NewBookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shelf_new_book_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_BOOK_NAME) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.shelf_new_book_field)) },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.shelf_new_book_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_BOOK_NAME = 20

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun gradeLabel(rating: Rating): Int = when (rating) {
    Rating.AGAIN -> R.string.grade_again
    Rating.HARD -> R.string.grade_hard
    Rating.GOOD -> R.string.grade_good
    Rating.EASY -> R.string.grade_easy
}

private fun speechProblemText(problem: SpeechProblem): Int = when (problem) {
    SpeechProblem.NoEngine -> R.string.reader_speak_no_engine
    SpeechProblem.NoEnglish -> R.string.reader_speak_no_english
    else -> R.string.reader_speak_failed
}
