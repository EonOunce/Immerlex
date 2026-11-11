package com.immerlex.feature.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.KeyCapture
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.capsule
import com.immerlex.app.ui.signed
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizStage
import com.immerlex.core.settings.KeyAction
import com.immerlex.core.settings.KeyContext
import com.immerlex.core.settings.KeyMap

@Composable
fun QuizScreen(vm: QuizViewModel, keyMap: KeyMap, onExit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    // 数字 1–4 只在还没答的那一刻换成 ABCD；答完只认右键，免得连按把下一题也点掉。
    val keyHandler: (Int) -> Boolean = { code ->
        val action = if (state.phase == QuizPhase.QUESTION) keyMap.actionFor(KeyContext.QUIZ, code) else null
        if (action == null) {
            false
        } else {
            when (action) {
                KeyAction.QUIZ_OPTION_A -> vm.answer(0)
                KeyAction.QUIZ_OPTION_B -> vm.answer(1)
                KeyAction.QUIZ_OPTION_C -> vm.answer(2)
                KeyAction.QUIZ_OPTION_D -> vm.answer(3)
                KeyAction.QUIZ_DOUBT -> if (!state.answered) vm.doubt()
                KeyAction.QUIZ_NEXT -> if (state.answered) vm.next()
                else -> Unit
            }
            true
        }
    }

    KeyCapture(
        onKey = keyHandler,
        requestOn = state.phase,
        modifier = Modifier.fillMaxSize(),
    ) {
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
                        onRetire = vm::retire,
                        onNext = vm::next,
                    )
                    QuizPhase.DONE -> ResultBody(state, onExit, vm::again, vm::review)
                    QuizPhase.ERROR -> ErrorBody(vm::retry, onExit)
                }
            }
        }
    }
}

/**
 * 结果页只有一个主角：这一组答对了几个，铺成一块主色的大字，与今日页那张主卡同一套皮。
 * 连对、巅峰涨跌、破纪录收成主角下面一行小字；答错的词落在下面那堵墙里——
 * 它比"有 N 个词答错"这句话更直观，也正是「再过一遍」那个按钮存在的理由。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultBody(
    state: QuizState,
    onExit: () -> Unit,
    onAgain: () -> Unit,
    onReview: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(accent)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.quiz_result_label),
                style = MaterialTheme.typography.titleMedium,
                color = ImmerlexColors.Ink.copy(alpha = 0.72f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.correctCount.toString(),
                    fontSize = 72.sp,
                    lineHeight = 68.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = ImmerlexColors.Ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    stringResource(R.string.quiz_result_of, state.roundTotal),
                    fontSize = 24.sp,
                    color = ImmerlexColors.Ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
                )
            }
        }
        val notes = buildList {
            if (state.bestStreak >= 2) add(stringResource(R.string.quiz_result_streak, state.bestStreak))
            // 涨跌要当场说：这一组对巅峰分动了多少，留底写完才知道，算不准的那一组不写这一句。
            state.record?.peakChange?.let { add(stringResource(R.string.quiz_result_peak, signed(it))) }
            // 这一句同样是留底写完才有的：没有比过，就不说有纪录可破。
            if (state.record?.isRecord == true) add(stringResource(R.string.quiz_record_beaten))
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString("　·　"),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        }
        if (state.missed.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().capsule().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.quiz_result_missed, state.missed.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = ImmerlexColors.Meta,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.missed.forEach { word ->
                        Text(
                            word,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        // 首测刚做完，正事是把这一批词再过一遍（连着认出三次才算一次真回忆）；
        // 那几个词要查库才数得清，数出来之前这颗按钮先不摆，免得它一秒里换个意思。
        val left = state.reviewCount
        if (left != null) {
            if (left > 0) {
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.quiz_review_now))
                }
            } else {
                Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (state.mode == QuizMode.EARLY) R.string.quiz_again_group else R.string.quiz_again_due,
                        ),
                    )
                }
            }
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
            stringResource(
                when (mode) {
                    QuizMode.EARLY -> R.string.quiz_early_empty
                    QuizMode.TRICKY -> R.string.quiz_tricky_empty
                    QuizMode.NEMESIS -> R.string.quiz_nemesis_empty
                    QuizMode.DUE -> R.string.quiz_empty
                    QuizMode.REVIEW -> R.string.quiz_review_empty
                },
            ),
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
