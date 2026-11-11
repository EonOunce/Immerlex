package com.immerlex.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizRun
import com.immerlex.core.schedule.PeakScore
import com.immerlex.core.schedule.Rank
import com.immerlex.core.schedule.RankKind
import com.immerlex.core.schedule.RankTier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 段位那一档 + 巅峰分。两个都是"给人看的数"，这张卡上一个动作都没有。 */
@Composable
internal fun RankCard(rank: Rank, peak: PeakScore, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.stats_rank_label),
            style = MaterialTheme.typography.labelLarge,
            color = ImmerlexColors.Body,
        )
        Text(
            rankTitleOf(rank.tier),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            rank.tier.next?.let { stringResource(R.string.stats_rank_next, rank.toNext, rankTitleOf(it)) }
                ?: stringResource(R.string.stats_rank_top),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Meta,
        )
        Text(peakLine(peak), style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Meta)
    }
}

/** 记录还不够的时候这个数没有意义，所以宁可不叫它巅峰分。 */
@Composable
private fun peakLine(peak: PeakScore): String =
    if (peak.usable) stringResource(R.string.stats_peak_now, peak.current, peak.best)
    else stringResource(R.string.stats_peak_thin, peak.samples)

/** 宿敌不止一个词，所以这一行只报数，把名单交给那一组题。 */
@Composable
internal fun RevengeRow(count: Int, onRevenge: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 18.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.stats_revenge_label, count),
            style = MaterialTheme.typography.bodyMedium,
            color = ImmerlexColors.Body,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onRevenge) {
            Text(stringResource(R.string.stats_revenge_action))
        }
    }
}

@Composable
internal fun RecentRunsCard(runs: List<QuizRun>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.stats_runs_title),
            style = MaterialTheme.typography.labelLarge,
            color = ImmerlexColors.Body,
        )
        runs.forEach { run ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(modeLabel(run.mode)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        formatted(run.finishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = ImmerlexColors.Meta,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${run.correct}/${run.asked}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.stats_run_rate, accuracy(run)),
                        style = MaterialTheme.typography.labelSmall,
                        color = ImmerlexColors.Meta,
                    )
                }
            }
        }
    }
}

/** 称号怎么拼：入门六档带"级"，进阶三档带"段"，词圣什么都不带。 */
@Composable
private fun rankTitleOf(tier: RankTier): String {
    val name = stringResource(rankName(tier))
    return when (tier.kind) {
        RankKind.LEVEL -> stringResource(R.string.rank_level_form, name, tier.level)
        RankKind.DUAN -> stringResource(R.string.rank_duan_form, name, tier.level)
        RankKind.SUMMIT -> name
    }
}

/** 分享卡上也要拼同一句称号，所以这一层单独露出去。 */
@Composable
internal fun rankTitle(rank: Rank): String = rankTitleOf(rank.tier)

@StringRes
private fun rankName(tier: RankTier): Int = when (tier) {
    RankTier.WHITE -> R.string.rank_white
    RankTier.CYAN -> R.string.rank_cyan
    RankTier.BLUE -> R.string.rank_blue
    RankTier.GREEN -> R.string.rank_green
    RankTier.ORANGE -> R.string.rank_orange
    RankTier.VIOLET -> R.string.rank_violet
    RankTier.CRIMSON -> R.string.rank_crimson
    RankTier.OBSIDIAN -> R.string.rank_obsidian
    RankTier.GOLD -> R.string.rank_gold
    RankTier.SAGE -> R.string.rank_sage
}

@StringRes
private fun modeLabel(mode: QuizMode): Int = when (mode) {
    QuizMode.DUE -> R.string.quiz_mode_due
    QuizMode.EARLY -> R.string.quiz_mode_early
    QuizMode.TRICKY -> R.string.quiz_mode_tricky
    QuizMode.NEMESIS -> R.string.quiz_mode_nemesis
}

private fun accuracy(run: QuizRun): Int =
    if (run.asked <= 0) 0 else (100.0 * run.correct / run.asked).roundToInt()

private fun formatted(instant: Instant): String = instant
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
