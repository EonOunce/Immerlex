package com.immerlex.feature.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAppearance
import com.immerlex.app.ui.SpeechProblem
import com.immerlex.app.ui.WordSpeaker
import com.immerlex.app.ui.rememberWordSpeaker
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizOption
import com.immerlex.core.quiz.QuizQuestion
import com.immerlex.core.quiz.QuizType

@Composable
internal fun QuestionBody(
    state: QuizState,
    onAnswer: (Int) -> Unit,
    onDoubt: () -> Unit,
    onMarkTricky: () -> Unit,
    onRetire: () -> Unit,
    onNext: () -> Unit,
) {
    val question = state.question ?: return
    val speaker = rememberWordSpeaker()
    val autoSpeak = LocalAppearance.current.autoSpeak
    // 英→中题干就是那个单词，出题即读；中→英读了等于把答案念出来，只在翻开答案之后读。
    LaunchedEffect(question.wordId, state.answered) {
        if (autoSpeak && speaksNow(question.type, state.answered)) speaker.speak(question.word)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.mode) {
                QuizMode.EARLY -> Badge(stringResource(R.string.quiz_early_badge))
                QuizMode.TRICKY -> Badge(stringResource(R.string.quiz_tricky_badge))
                QuizMode.NEMESIS -> Badge(stringResource(R.string.quiz_nemesis_badge))
                QuizMode.DUE -> Unit
                QuizMode.REVIEW -> Badge(stringResource(R.string.quiz_review_badge))
            }
            if (state.practice) Badge(stringResource(R.string.quiz_retry_badge))
        }
        Text(
            stringResource(
                if (state.practice) R.string.quiz_retry_progress else R.string.quiz_progress,
                state.index + 1,
                state.total,
                stringResource(typeLabel(question.type)),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = ImmerlexColors.Meta,
        )
        PromptCard(question, state.answered, speaker)

        question.options.forEachIndexed { index, option ->
            OptionCard(
                option = option,
                chosen = state.chosen,
                index = index,
                answered = state.answered,
                onPick = onAnswer,
            )
        }
        if (state.answered) {
            Text(
                // 「不确定」不是答错，但排的是同一笔账：这句话只说结果，不解释为什么。
                stringResource(
                    when {
                        state.correct -> R.string.quiz_correct
                        state.doubt -> R.string.quiz_doubt_result
                        else -> R.string.quiz_wrong
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            // 正确答案那一项自己标着"正确"，这句话不再重复一遍那个词。
            if (!state.correct && question.wordId !in state.trickyIds) {
                TextButton(onClick = onMarkTricky) { Text(stringResource(R.string.tricky_mark)) }
            }
            // 连着认出来这么多回，这个词大概已经不是易错词了——问一句要不要移出，不自己动。
            if (state.retirement != null) {
                TextButton(onClick = onRetire) { Text(stringResource(R.string.quiz_tricky_retire)) }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.showsFinish) R.string.quiz_finish else R.string.quiz_next))
            }
        } else {
            OutlinedButton(onClick = onDoubt, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_doubt))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PromptCard(question: QuizQuestion, answered: Boolean, speaker: WordSpeaker) {
    // 念得出题干里的英文、又不会先把答案说出来，才给这个按钮。
    val speakable = question.type == QuizType.EN_ZH || answered
    val unusable = speaker.problem == SpeechProblem.NoEngine || speaker.problem == SpeechProblem.NoEnglish
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(promptLabel(question.type)),
                style = MaterialTheme.typography.labelMedium,
                color = ImmerlexColors.Meta,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    question.prompt,
                    modifier = Modifier.weight(1f),
                    style = if (isHeadwordPrompt(question.type)) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (isHeadwordPrompt(question.type)) FontWeight.Bold else FontWeight.Normal,
                )
                if (speakable && !unusable) {
                    TextButton(onClick = { speaker.speak(question.word) }) {
                        Text(stringResource(R.string.reader_speak))
                    }
                }
            }
            if (question.promptHint.isNotBlank()) {
                Text(
                    question.promptHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ImmerlexColors.Meta,
                )
            }
        }
    }
}

/** 答完就把每个选项的另一面摆出来：中文选项底下是英文单词，英文选项底下是中文释义。 */
@Composable
private fun OptionCard(
    option: QuizOption,
    chosen: Int?,
    index: Int,
    answered: Boolean,
    onPick: (Int) -> Unit,
) {
    val isChosen = chosen == index
    val highlighted = answered && (option.correct || isChosen)
    val borderColor = when {
        answered && option.correct -> MaterialTheme.colorScheme.primary
        answered && isChosen -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) { onPick(index) },
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    option.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (answered && option.correct) {
                    Tag(stringResource(R.string.quiz_option_correct), MaterialTheme.colorScheme.primary)
                } else if (answered && isChosen) {
                    Tag(stringResource(R.string.quiz_option_yours), MaterialTheme.colorScheme.error)
                }
            }
            if (answered && option.gloss.isNotBlank() && option.gloss != option.text) {
                Text(option.gloss, style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Meta)
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/**
 * 自动发音只在不会先把答案念出来的那一刻响：题干就是那个词时出题即读，中→英要等翻开答案。
 * 搭配题的题干是一整条短语，而发音只念 [QuizQuestion.word]（这个词本身），念出来和屏上的不一样，所以不读。
 */
internal fun speaksNow(type: QuizType, answered: Boolean): Boolean = when (type) {
    QuizType.EN_ZH -> !answered
    QuizType.ZH_EN -> answered
    else -> false
}

/** 题干只是一个单词的题型：字要放大，不然满屏字里认不出今天的词。短语不放大，一整行更清楚。 */
internal fun isHeadwordPrompt(type: QuizType): Boolean = type == QuizType.EN_ZH
