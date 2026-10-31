package com.immerlex.feature.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizOption
import com.immerlex.core.quiz.QuizQuestion
import com.immerlex.core.quiz.QuizStage
import com.immerlex.core.quiz.QuizType

@Composable
fun QuizScreen(vm: QuizViewModel, onExit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = RootHandledInsets) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    stringResource(R.string.quiz_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            when (state.phase) {
                QuizPhase.LOADING -> CenterNote(stringResource(stageLabel(state.stage)))
                QuizPhase.EMPTY -> EmptyBody(state.mode, onExit)
                QuizPhase.QUESTION -> QuestionBody(state, vm::answer, vm::next)
                QuizPhase.DONE -> ResultBody(state, onExit, vm::again)
                QuizPhase.ERROR -> ErrorBody(vm::retry, onExit)
            }
        }
    }
}

@Composable
private fun QuestionBody(state: QuizState, onAnswer: (Int) -> Unit, onNext: () -> Unit) {
    val question = state.question ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.mode == QuizMode.EARLY) Badge(stringResource(R.string.quiz_early_badge))
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
        PromptCard(question)

        if (state.answered) {
            Text(
                stringResource(R.string.quiz_all_options),
                style = MaterialTheme.typography.labelMedium,
                color = ImmerlexColors.Meta,
            )
        }
        question.options.forEachIndexed { index, option ->
            OptionCard(
                option = option,
                chosen = state.chosen,
                index = index,
                onPick = onAnswer,
            )
        }
        if (state.answered) {
            Text(
                stringResource(
                    if (state.correct) R.string.quiz_correct else R.string.quiz_wrong,
                    question.correctText,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.showsFinish) R.string.quiz_finish else R.string.quiz_next))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PromptCard(question: QuizQuestion) {
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
            Text(
                question.prompt,
                style = if (question.type == QuizType.EN_ZH) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (question.type == QuizType.EN_ZH) FontWeight.Bold else FontWeight.Normal,
            )
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

/** 答完就把每个选项的释义摆出来，选错的人也能立刻知道别的词差在哪。 */
@Composable
private fun OptionCard(option: QuizOption, chosen: Int?, index: Int, onPick: (Int) -> Unit) {
    val answered = chosen != null
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
                Text(
                    stringResource(R.string.quiz_option_row, option.text, option.gloss),
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Meta,
                )
            }
        }
    }
}

@Composable
private fun ResultBody(state: QuizState, onExit: () -> Unit, onAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        if (state.mode == QuizMode.EARLY) R.string.quiz_early_result else R.string.quiz_result,
                        state.roundTotal,
                        state.correctCount,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.wrongCount > 0) {
                    Text(
                        stringResource(R.string.quiz_result_wrong_done, state.wrongCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = ImmerlexColors.Meta,
                    )
                    Text(
                        stringResource(R.string.quiz_retry_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = ImmerlexColors.Meta,
                    )
                }
            }
        }
        if (state.mode == QuizMode.EARLY) {
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_again_group))
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_finish))
            }
        } else {
            Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_again_due))
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_finish))
            }
        }
    }
}

@Composable
private fun ErrorBody(onRetry: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.quiz_error),
            textAlign = TextAlign.Center,
            color = ImmerlexColors.Body,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.quiz_retry)) }
        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.action_back)) }
    }
}

@Composable
private fun EmptyBody(mode: QuizMode, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (mode == QuizMode.EARLY) R.string.quiz_early_empty else R.string.quiz_empty),
            textAlign = TextAlign.Center,
            color = ImmerlexColors.Body,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onExit) { Text(stringResource(R.string.action_back)) }
    }
}

@Composable
private fun CenterNote(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = ImmerlexColors.Meta)
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
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun typeLabel(type: QuizType): Int = when (type) {
    QuizType.EN_ZH -> R.string.quiz_type_en_zh
    QuizType.ZH_EN -> R.string.quiz_type_zh_en
    QuizType.CLOZE -> R.string.quiz_type_cloze
    QuizType.DEFINITION -> R.string.quiz_type_definition
}

private fun stageLabel(stage: QuizStage): Int = when (stage) {
    QuizStage.COLLECTING -> R.string.quiz_stage_words
    QuizStage.READING_LOGS -> R.string.quiz_stage_records
    QuizStage.ASKING_AI -> R.string.quiz_stage_ai
    QuizStage.BUILDING -> R.string.quiz_stage_build
}

private fun promptLabel(type: QuizType): Int = when (type) {
    QuizType.EN_ZH -> R.string.quiz_prompt_en_zh
    QuizType.ZH_EN -> R.string.quiz_prompt_zh_en
    QuizType.CLOZE -> R.string.quiz_prompt_cloze
    QuizType.DEFINITION -> R.string.quiz_prompt_definition
}
