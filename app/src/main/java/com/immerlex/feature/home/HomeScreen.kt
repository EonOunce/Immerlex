package com.immerlex.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.AccentSwatches
import com.immerlex.app.ui.FishIcon
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.LocalAppearance
import com.immerlex.app.ui.capsule

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onStartReading: () -> Unit,
    onStartQuiz: () -> Unit,
    onStartEarly: () -> Unit,
    onStartTricky: () -> Unit,
    onOpenRoom: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val appearance = LocalAppearance.current
    var showShare by rememberSaveable { mutableStateOf(false) }
    // 阅读页打完分回到这一栏，数字必须跟着重算，否则"今日完成"会停在进来时的值。
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            AccentSwatches(selected = appearance.accent, onPick = vm::setAccent, size = 22.dp)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { showShare = true }, enabled = !state.loading) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.home_share))
            }
        }

        HeroCard(state, accent, onStartReading)

        SecondaryStack(state, onStartQuiz, onStartEarly, onStartTricky)

        RoommateNote(state, onOpenRoom)

        SmallStatLine(state)

        RecentCard(days = state.heat, trend = state.trend)
        MakeupLine(state)
    }

    if (showShare) {
        ShareReportDialog(state = state, onDismiss = { showShare = false })
    }
}

/**
 * 整页唯一的主角：一个铺满主色的大圆角块，只回答"今天到期几个词、从哪开始"。
 * 连续天数做成压在右上角的圆形印章，探出去半截——它靠叠压成为第二层，而不是另起一张卡。
 * 卡片恒为亮主色，所以上面的字与药丸一律用固定墨色，深浅色都不跟主题走。
 */
@Composable
private fun HeroCard(state: HomeState, accent: Color, onStartReading: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
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
                stringResource(R.string.home_due),
                style = MaterialTheme.typography.titleMedium,
                color = ImmerlexColors.Ink.copy(alpha = 0.72f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (state.loading) "—" else state.due.toString(),
                    fontSize = 72.sp,
                    lineHeight = 68.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = ImmerlexColors.Ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    stringResource(R.string.home_hero_unit),
                    fontSize = 24.sp,
                    color = ImmerlexColors.Ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(ImmerlexColors.Ink)
                    .clickable(enabled = !state.loading, onClick = onStartReading)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        when {
                            state.loading -> R.string.home_loading
                            !state.hasChapter -> R.string.home_start_first
                            else -> R.string.home_hero_start
                        },
                    ) + "  →",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ImmerlexColors.Paper,
                )
            }
        }
        StreakStamp(state.streak, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
    }
}

@Composable
private fun StreakStamp(streak: Int, modifier: Modifier = Modifier) {
    val days = stringResource(R.string.home_streak_days, streak)
    Column(
        modifier = modifier
            .rotate(8f)
            .size(74.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(ImmerlexColors.Paper),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(days, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.home_streak_unit),
            style = MaterialTheme.typography.labelSmall,
            color = ImmerlexColors.Meta,
        )
    }
}

/** 测验与提前复习压成一对略歪、彼此叠靠的白卡——第二层，比主角小一圈、也不铺色。 */
@Composable
private fun SecondaryStack(
    state: HomeState,
    onStartQuiz: () -> Unit,
    onStartEarly: () -> Unit,
    onStartTricky: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy((-14).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).rotate(-1.4f)) {
            SecondaryCard(
                label = stringResource(R.string.home_early_card),
                value = if (state.earlyCount > 0) stringResource(R.string.home_early_card_count, state.earlyCount) else stringResource(R.string.home_early_card_none),
                dot = ImmerlexColors.Rust,
                enabled = !state.loading && state.earlyCount > 0,
                onClick = onStartEarly,
            )
        }
        Box(Modifier.weight(1f).rotate(1.4f)) {
            SecondaryCard(
                label = stringResource(R.string.home_quiz_card),
                value = when {
                    state.quizResumable -> stringResource(R.string.home_quiz_card_resume)
                    state.quizCount > 0 -> stringResource(R.string.home_quiz_card_count, state.quizCount)
                    else -> stringResource(R.string.home_quiz_card_empty)
                },
                dot = MaterialTheme.colorScheme.primary,
                enabled = !state.loading && (state.quizCount > 0 || state.quizResumable),
                onClick = onStartQuiz,
                // 到期词测完了还能只练易错词，今天这一次练习就不用去阅读页点词。
                extra = if (state.trickyCount > 0) {
                    SecondaryAction(
                        text = stringResource(R.string.home_tricky_card, state.trickyCount),
                        onClick = onStartTricky,
                    )
                } else {
                    null
                },
            )
        }
    }
}

private class SecondaryAction(val text: String, val onClick: () -> Unit)

@Composable
private fun SecondaryCard(
    label: String,
    value: String,
    dot: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    extra: SecondaryAction? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .capsule()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (enabled) dot else ImmerlexColors.Line))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) ImmerlexColors.Body else ImmerlexColors.Meta)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else ImmerlexColors.Meta,
        )
        extra?.let { action ->
            Text(
                action.text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = action.onClick),
            )
        }
    }
}

/** 室友是一张贴在角落的便签，不是一张标准卡：暖底、衬线、歪一点点，只占一行。 */
@Composable
private fun RoommateNote(state: HomeState, onOpenRoom: () -> Unit) {
    val fishColor = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .rotate(-0.8f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onOpenRoom)
                .padding(start = 13.dp, end = 13.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = when {
                    !state.roomMet -> stringResource(R.string.home_room_unmet)
                    state.roomLine.isNotBlank() ->
                        stringResource(R.string.home_room_slip, state.roomName, state.roomLine)
                    else -> state.roomName
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = fishColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.roomMet) {
                FishIcon(Modifier.size(11.dp), fishColor)
                Text(
                    state.roomBalance.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = fishColor,
                )
            }
        }
    }
}

@Composable
private fun SmallStatLine(state: HomeState) {
    val parts = mutableListOf(stringResource(R.string.home_hero_done_count, state.done))
    if (state.newWords > 0) parts += stringResource(R.string.home_new_words, state.newWords)
    Text(
        parts.joinToString("　·　"),
        style = MaterialTheme.typography.bodySmall,
        color = ImmerlexColors.Meta,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 补卡是"漏一天不归零"的唯一机制，所以它用没用过要摆在明面上。 */
@Composable
private fun MakeupLine(state: HomeState) {
    val text = when {
        state.makeupPending -> R.string.home_makeup_pending
        state.makeupUsedThisWeek -> R.string.home_makeup_used
        else -> R.string.home_makeup_unused
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Body)
}
