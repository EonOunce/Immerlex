package com.immerlex.feature.stats

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.LocalAccent
import com.immerlex.app.ui.capsule
import com.immerlex.core.schedule.RetentionReport

@Composable
fun StatsScreen(vm: StatsViewModel, onTakeRevenge: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    var showShare by rememberSaveable { mutableStateOf(false) }
    // 打完分回到这一栏要重算：此刻能想起几个词跟着最近一次回忆走。
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showShare = true }, enabled = !state.loading && state.report.learnedWords > 0) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.stats_share))
            }
        }

        if (state.report.learnedWords == 0) {
            Text(
                stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = ImmerlexColors.Meta,
            )
        } else {
            StatsHero(state.report, accent)
            DuelCard(state.report.duel, state.report.peak)
            SecondaryRow(state.report)
            if (state.report.rivals.isNotEmpty()) {
                RevengeRow(state.report.rivals.size, onTakeRevenge)
            }
            if (state.runs.isNotEmpty()) {
                RecentRunsCard(state.runs)
            }
        }
    }

    if (showShare) {
        StatsShareDialog(report = state.report, onDismiss = { showShare = false })
    }
}

/** 这一栏只有一个主角：段位。巅峰分只在这张卡右上角留一行小字，其余都在下面那一张。 */
@Composable
private fun StatsHero(report: RetentionReport, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(accent)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stats_rank_label),
                style = MaterialTheme.typography.titleMedium,
                color = ImmerlexColors.Ink.copy(alpha = 0.72f),
            )
            Spacer(Modifier.weight(1f))
            if (report.peak.usable) {
                Text(
                    stringResource(R.string.stats_peak_badge, report.peak.current),
                    style = MaterialTheme.typography.titleSmall,
                    color = ImmerlexColors.Ink.copy(alpha = 0.72f),
                )
            }
        }
        Text(
            rankTitle(report.rank),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 44.sp,
            lineHeight = 44.sp,
            color = ImmerlexColors.Ink,
        )
        Text(
            rankNextLine(report.rank),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Ink.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SecondaryRow(report: RetentionReport) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.stats_can_recall_label),
                value = report.canRecall.toString(),
                unit = stringResource(R.string.stats_words_unit),
                // 以前最好的一天是影子表里留下的那一格：没有对照就不说这句话。
                detail = if (report.bestDay > 0) {
                    if (report.canRecall > report.bestDay) stringResource(R.string.stats_beat_best)
                    else stringResource(R.string.stats_best_day, report.bestDay)
                } else {
                    null
                },
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.stats_mastered_label),
                value = "${report.mastered}",
                unit = stringResource(R.string.stats_words_unit),
            )
        }
        if (report.mostFailedWord != null) {
            StatCard(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.stats_nemesis_label),
                word = report.mostFailedWord,
                gloss = report.mostFailedGloss,
                detail = if (report.mostFailedLapses > 0) {
                    stringResource(R.string.stats_nemesis_count, report.mostFailedLapses)
                } else {
                    null
                },
                detailColor = ImmerlexColors.Rust,
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String? = null,
    unit: String? = null,
    word: String? = null,
    gloss: String? = null,
    detail: String? = null,
    detailColor: Color = ImmerlexColors.Meta,
) {
    Column(
        modifier = modifier
            .capsule()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ImmerlexColors.Body)
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                if (unit != null) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ImmerlexColors.Meta,
                        modifier = Modifier.padding(start = 4.dp).alignByBaseline(),
                    )
                }
            }
        } else if (word != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(word, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (!gloss.isNullOrBlank()) {
                Text(gloss, style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Meta)
            }
        } else {
            Text("—", style = MaterialTheme.typography.headlineMedium, color = ImmerlexColors.Meta)
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = detailColor)
        }
    }
}
