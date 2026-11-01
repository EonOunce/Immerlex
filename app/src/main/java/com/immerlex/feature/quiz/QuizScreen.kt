package com.immerlex.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.immerlex.core.quiz.QuizStage

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
                QuizPhase.LOADING -> LoadingBody(
                    stage = state.stage,
                    ready = state.materialReady,
                    toAsk = state.materialToAsk,
                    canAsk = state.materialCanAsk,
                )
                QuizPhase.EMPTY -> EmptyBody(state.mode, onExit)
                QuizPhase.QUESTION -> QuestionBody(
                    state = state,
                    onAnswer = vm::answer,
                    onDoubt = vm::doubt,
                    onMarkTricky = vm::markTricky,
                    onNext = vm::next,
                )
                QuizPhase.DONE -> ResultBody(state, onExit, vm::again)
                QuizPhase.ERROR -> ErrorBody(vm::retry, onExit)
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
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (state.mode == QuizMode.EARLY) R.string.quiz_again_group else R.string.quiz_again_due,
                ),
            )
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quiz_finish))
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

/**
 * 出题时先说一句素材账：几个词库里已经有了、几个还得等模型。
 * 素材账要等词选出来才算得出来，之前只显示阶段文案。
 */
@Composable
private fun LoadingBody(stage: QuizStage, ready: Int, toAsk: Int, canAsk: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(stageLabel(stage)), color = ImmerlexColors.Meta)
        if (ready + toAsk > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    toAsk == 0 -> stringResource(R.string.quiz_material_ready, ready)
                    canAsk -> stringResource(R.string.quiz_material_ready_and_ask, ready, toAsk)
                    else -> stringResource(R.string.quiz_material_ready_missing, ready, toAsk)
                },
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
                textAlign = TextAlign.Center,
            )
        }
    }
}
