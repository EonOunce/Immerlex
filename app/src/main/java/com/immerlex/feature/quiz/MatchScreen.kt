package com.immerlex.feature.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.capsule
import com.immerlex.app.ui.signed

@Composable
fun MatchScreen(vm: MatchViewModel, onExit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }

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
                    stringResource(R.string.match_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            when (state.phase) {
                MatchPhase.LOADING -> MatchNotice(stringResource(R.string.match_loading)) {
                    OutlinedButton(onClick = onExit) { Text(stringResource(R.string.action_back)) }
                }
                MatchPhase.EMPTY -> MatchNotice(stringResource(R.string.match_empty)) {
                    OutlinedButton(onClick = onExit) { Text(stringResource(R.string.action_back)) }
                }
                MatchPhase.PLAYING -> PlayBody(state, vm::tap)
                MatchPhase.DONE -> ResultBody(state, onExit, vm::again)
            }
        }
    }
}

@Composable
private fun PlayBody(state: MatchState, onTap: (MatchSide, Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.match_progress, state.boardIndex + 1, state.boardCount, state.remaining),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Meta,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.left.forEach { pair ->
                    MatchCard(
                        text = pair.word,
                        serif = true,
                        matched = pair.wordId in state.matched,
                        picked = state.pickedSide == MatchSide.LEFT && state.pickedId == pair.wordId,
                        wrong = pair.wordId in state.wrongIds,
                        onClick = { onTap(MatchSide.LEFT, pair.wordId) },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.right.forEach { pair ->
                    MatchCard(
                        text = pair.gloss,
                        serif = false,
                        matched = pair.wordId in state.matched,
                        picked = state.pickedSide == MatchSide.RIGHT && state.pickedId == pair.wordId,
                        wrong = pair.wordId in state.wrongIds,
                        onClick = { onTap(MatchSide.RIGHT, pair.wordId) },
                    )
                }
            }
        }
    }
}

/**
 * 一张牌四种样子：选中是主色、连错是红、连上是淡下去，其余是普通胶囊。
 * 连上之后不能再点，也就不会再被选中。
 */
@Composable
private fun MatchCard(
    text: String,
    serif: Boolean,
    matched: Boolean,
    picked: Boolean,
    wrong: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        picked -> LocalAccent.current
        wrong -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val color = when {
        picked -> ImmerlexColors.Ink
        wrong -> MaterialTheme.colorScheme.onErrorContainer
        matched -> ImmerlexColors.Meta
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(if (matched) Modifier.alpha(0.45f) else Modifier)
            .capsule(radius = 16.dp, elevation = if (matched) 0.dp else 2.dp, color = background)
            .clickable(enabled = !matched, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (serif) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontFamily = if (serif) FontFamily.Serif else null,
            fontWeight = if (serif) FontWeight.Bold else FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 结果页和四选一那边同一套：一个主角（一次就连对了几对），一句小字，一堵没一下连对的词墙。
 * 连对那个数才写得出"破纪录"和巅峰涨跌，所以那几句等留底写完才出现。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultBody(state: MatchState, onExit: () -> Unit, onAgain: () -> Unit) {
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
                stringResource(R.string.match_result_label),
                style = MaterialTheme.typography.titleMedium,
                color = ImmerlexColors.Ink.copy(alpha = 0.72f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.firstTry.toString(),
                    fontSize = 72.sp,
                    lineHeight = 68.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = ImmerlexColors.Ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    stringResource(R.string.match_result_of, state.totalPairs),
                    fontSize = 24.sp,
                    color = ImmerlexColors.Ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
                )
            }
        }
        val notes = buildList {
            state.record?.peakChange?.let { add(stringResource(R.string.quiz_result_peak, signed(it))) }
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
                    stringResource(R.string.match_missed, state.missed.size),
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
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.match_again))
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quiz_finish))
        }
    }
}

@Composable
private fun MatchNotice(text: String, action: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, color = ImmerlexColors.Body)
        Spacer(Modifier.height(16.dp))
        action()
    }
}
