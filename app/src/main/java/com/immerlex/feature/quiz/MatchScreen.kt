package com.immerlex.feature.quiz

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.immerlex.app.ui.KeyCapture
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.capsule
import com.immerlex.app.ui.signed

/** 数字键 1–5 就是这一屏从上到下的五对。 */
private val MATCH_ROWS = listOf(
    KeyEvent.KEYCODE_1,
    KeyEvent.KEYCODE_2,
    KeyEvent.KEYCODE_3,
    KeyEvent.KEYCODE_4,
    KeyEvent.KEYCODE_5,
)

private val MATCH_GAP = 12.dp
private val MATCH_CARD_MIN = 58.dp
private val MATCH_CARD_MAX = 140.dp

/** 进度那一行连同它下面的间距，估一个高度：摊不摊得开就看剩下多少给牌。 */
private val MATCH_PROGRESS_BLOCK = 32.dp

/** 与阅读页正文同一个限宽：牌摊到平板两头，眼睛就得来回扫一整屏。 */
private val MATCH_BOARD_MAX_WIDTH = 720.dp

@Composable
fun MatchScreen(vm: MatchViewModel, onExit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }

    // 1–5 是这一屏从上到下的五对：第一次按点左边那张，第二次按点右边那张，6 把刚才点的那张放回去。
    // 这几颗键不进设置页——它们不是"哪个动作绑哪个键"，就是这一屏的五个位置。
    val keyHandler: (Int) -> Boolean = { code ->
        when {
            state.phase != MatchPhase.PLAYING -> false
            code == KeyEvent.KEYCODE_6 -> {
                vm.cancelPick()
                true
            }
            else -> {
                val row = MATCH_ROWS.indexOf(code)
                if (row < 0) {
                    false
                } else {
                    val side = if (state.pickedSide == MatchSide.LEFT) MatchSide.RIGHT else MatchSide.LEFT
                    val pairs = if (side == MatchSide.LEFT) state.left else state.right
                    val pair = pairs.getOrNull(row)
                    if (pair == null) false else {
                        vm.tap(side, pair.wordId)
                        true
                    }
                }
            }
        }
    }

    KeyCapture(
        onKey = keyHandler,
        requestOn = state,
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
}

/**
 * 一屏牌摊满整屏：五张牌把可用高度分掉，牌面自己长高（最多 140dp），剩的空当上下均分，
 * 宽屏上限宽 720dp 居中——牌摊到平板两头，眼睛就得来回扫一整屏。
 * 摊不开的时候（三星小窗收成半屏）才退回滚动，牌仍是 58dp 起步。
 */
@Composable
private fun PlayBody(state: MatchState, onTap: (MatchSide, Long) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        val rows = maxOf(state.left.size, state.right.size)
        val needed = MATCH_PROGRESS_BLOCK + MATCH_GAP +
            MATCH_CARD_MIN * rows + MATCH_GAP * (rows - 1)
        val board = Modifier.fillMaxHeight().widthIn(max = MATCH_BOARD_MAX_WIDTH).align(Alignment.TopCenter)
        if (maxHeight >= needed) {
            Column(modifier = board, verticalArrangement = Arrangement.spacedBy(MATCH_GAP)) {
                MatchProgress(state)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(MATCH_GAP),
                ) {
                    MatchColumn(state, MatchSide.LEFT, onTap, Modifier.weight(1f).fillMaxHeight(), fill = true)
                    MatchColumn(state, MatchSide.RIGHT, onTap, Modifier.weight(1f).fillMaxHeight(), fill = true)
                }
            }
        } else {
            Column(
                modifier = board.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MATCH_GAP),
            ) {
                MatchProgress(state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MATCH_GAP),
                ) {
                    MatchColumn(state, MatchSide.LEFT, onTap, Modifier.weight(1f), fill = false)
                    MatchColumn(state, MatchSide.RIGHT, onTap, Modifier.weight(1f), fill = false)
                }
            }
        }
    }
}

@Composable
private fun MatchProgress(state: MatchState) {
    Text(
        stringResource(R.string.match_progress, state.boardIndex + 1, state.boardCount, state.remaining),
        style = MaterialTheme.typography.bodySmall,
        color = ImmerlexColors.Meta,
    )
}

/**
 * 一列牌：左列是英文、右列是中文。[fill] 时五张牌把这一列的高度分掉（每张最多 140dp，
 * 多出来的空当上下均分），不 fill 时按牌面自己的最小高度往下排、整列可滚。
 */
@Composable
private fun MatchColumn(
    state: MatchState,
    side: MatchSide,
    onTap: (MatchSide, Long) -> Unit,
    modifier: Modifier,
    fill: Boolean,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MATCH_GAP, Alignment.CenterVertically),
    ) {
        val pairs = if (side == MatchSide.LEFT) state.left else state.right
        pairs.forEach { pair ->
            // 份给外层的格、牌面自己封顶：weight 会把子项钉死成整份高度，
            // 直接写在牌上时 heightIn(max) 永远够不着，牌就会跟着格子一起长到半屏高。
            val slot = if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
            Box(modifier = slot, contentAlignment = Alignment.Center) {
                MatchCard(
                    text = if (side == MatchSide.LEFT) pair.word else pair.gloss,
                    serif = side == MatchSide.LEFT,
                    matched = pair.wordId in state.matched,
                    picked = state.pickedSide == side && state.pickedId == pair.wordId,
                    wrong = pair.wordId in state.wrongIds,
                    onClick = { onTap(side, pair.wordId) },
                    // 先封顶再撑满：反过来写（撑满在前）时 min 会被钉成整格高度，
                    // max 再小也压不住，牌就跟着格子长到半屏高。
                    modifier = if (fill) Modifier.heightIn(max = MATCH_CARD_MAX).fillMaxHeight() else Modifier,
                )
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MATCH_CARD_MIN)
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
