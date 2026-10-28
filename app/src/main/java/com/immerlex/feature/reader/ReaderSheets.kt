package com.immerlex.feature.reader

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.db.ChapterOption
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.GradeMode
import com.immerlex.core.schedule.recorded
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCardSheet(
    word: ReaderWord,
    gradeMode: GradeMode,
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
            } else {
                gradeHint(gradeMode)?.let { hint ->
                    Text(stringResource(hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                // 未到期的词只给释义：打分按钮压根不出现，手滑也就无从滑起。
                if (gradeMode != GradeMode.Reveal) {
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
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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

private fun gradeHint(mode: GradeMode): Int? = when (mode) {
    GradeMode.Review -> null
    GradeMode.Relearn -> R.string.reader_grade_relearn
    GradeMode.Amend -> R.string.reader_grade_amend
    GradeMode.Reveal -> R.string.reader_grade_not_due
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
