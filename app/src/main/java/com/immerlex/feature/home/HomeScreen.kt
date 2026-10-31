package com.immerlex.feature.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.FishPill
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.schedule.HeatDay
import com.immerlex.core.schedule.TrendDay

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onStartReading: () -> Unit,
    onStartQuiz: () -> Unit,
    onStartEarly: () -> Unit,
    onOpenRoom: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 阅读页打完分回到这一栏，数字必须跟着重算，否则"今日完成"会停在进来时的值。
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)

        StreakCard(state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountCard(stringResource(R.string.home_due), state.due, Modifier.weight(1f))
            CountCard(stringResource(R.string.home_done), state.done, Modifier.weight(1f))
        }
        HeatmapCard(days = state.heat, todayWords = state.done)
        TrendCard(days = state.trend)
        if (state.newWords > 0) {
            Text(
                stringResource(R.string.home_new_words, state.newWords),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        }
        MakeupLine(state)

        Button(
            onClick = onStartReading,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            Text(
                stringResource(
                    when {
                        state.loading -> R.string.home_loading
                        !state.hasChapter -> R.string.home_start_first
                        else -> R.string.home_start_reading
                    },
                ),
            )
        }
        if (state.hasChapter) {
            Text(
                stringResource(R.string.home_continue, state.chapterNumber),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        }

        RoommateCard(state, onOpenRoom)

        FilledTonalButton(
            onClick = onStartQuiz,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading && (state.quizCount > 0 || state.quizResumable),
        ) {
            Text(
                when {
                    state.quizResumable -> stringResource(R.string.home_quiz_resume)
                    state.quizCount > 0 -> stringResource(R.string.home_quiz_count, state.quizCount)
                    else -> stringResource(R.string.home_quiz_empty)
                },
            )
        }
        OutlinedButton(
            onClick = onStartEarly,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading && state.earlyCount > 0,
        ) {
            Text(stringResource(R.string.home_early_count, state.earlyCount))
        }
    }
}

@Composable
private fun RoommateCard(state: HomeState, onOpenRoom: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRoom)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.roomMet) {
                        stringResource(R.string.home_room_met, state.roomName)
                    } else {
                        stringResource(R.string.home_room_unmet)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.roomMet) {
                    Spacer(Modifier.weight(1f))
                    FishPill(state.roomBalance)
                }
            }
            if (state.roomMet && state.roomLine.isNotBlank()) {
                Text(
                    state.roomLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Body,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(state: HomeState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.home_streak, state.streak),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(if (state.checkedIn) R.string.home_checked_in else R.string.home_not_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.checkedIn) MaterialTheme.colorScheme.primary else ImmerlexColors.Meta,
            )
        }
    }
}

@Composable
private fun CountCard(label: String, value: Int, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Meta)
        }
    }
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

/**
 * 13 周 × 7 天的格子，颜色只回答一件事：那天背过或复习过多少词。
 * 未来的几天留在网格里但不给颜色，免得最后一行看起来像"没背"。
 */
@Composable
private fun HeatmapCard(days: List<HeatDay>, todayWords: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.home_heat_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.home_heat_caption, todayWords),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                days.chunked(7).forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day -> HeatCell(day.level) }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    stringResource(R.string.home_heat_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
                (0..4).forEach { HeatCell(it) }
                Text(
                    stringResource(R.string.home_heat_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
            }
        }
    }
}

@Composable
private fun HeatCell(level: Int) {
    val color = if (level == 0) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f + 0.2f * level)
    }
    Box(Modifier.size(18.dp).background(color, RoundedCornerShape(3.dp)))
}

/**
 * 近 30 天的柱状趋势：每根柱是一天的去重词数，最后一根是今天。
 * 0 个词的日子留一条浅色底座，避免"没背"和"漏画"分不清。
 */
@Composable
private fun TrendCard(days: List<TrendDay>) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val max = days.maxOfOrNull { it.words } ?: 0
    val chartHeight = 84.dp
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.home_trend_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.home_trend_caption),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
            Row {
                Column(
                    modifier = Modifier.width(20.dp).height(chartHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        if (max > 0) max.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = ImmerlexColors.Meta,
                    )
                    Text("0", style = MaterialTheme.typography.labelSmall, color = ImmerlexColors.Meta)
                }
                Canvas(Modifier.padding(start = 6.dp).weight(1f).height(chartHeight)) {
                    if (days.isEmpty()) return@Canvas
                    val slot = size.width / days.size
                    val gap = 1.dp.toPx()
                    val base = 2.dp.toPx()
                    days.forEachIndexed { index, day ->
                        val height = if (max == 0) base else (size.height * day.words / max).coerceAtLeast(base)
                        drawRoundRect(
                            color = if (day.words == 0) emptyColor else barColor.copy(
                                alpha = if (index == days.lastIndex) 1f else 0.55f,
                            ),
                            topLeft = Offset(index * slot + gap, size.height - height),
                            size = Size((slot - 2 * gap).coerceAtLeast(1f), height),
                            cornerRadius = CornerRadius(base, base),
                        )
                    }
                }
            }
            Row(Modifier.padding(start = 26.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.home_trend_start, days.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
                Text(
                    stringResource(R.string.home_trend_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
            }
        }
    }
}
