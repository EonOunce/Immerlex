package com.immerlex.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.capsule
import com.immerlex.app.ui.signed
import com.immerlex.core.quiz.QuizMode
import com.immerlex.core.quiz.QuizRun
import com.immerlex.core.schedule.PeakScore
import com.immerlex.core.schedule.Rank
import com.immerlex.core.schedule.RankKind
import com.immerlex.core.schedule.RankTier
import com.immerlex.core.schedule.WeekDuel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 影子对决：巅峰分这一条线，这周最高对上上周最高。对手只有以前的自己——不排行、不上传。
 * 记录还太少时这一栏不叫分，只说重放了多少次，与战绩页别处同一个规矩。
 */
@Composable
internal fun DuelCard(duel: WeekDuel, peak: PeakScore, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .capsule()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.stats_duel_title),
            style = MaterialTheme.typography.labelLarge,
            color = ImmerlexColors.Body,
        )
        if (!peak.usable) {
            Text(
                stringResource(R.string.stats_peak_thin, peak.samples),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                DuelSide(stringResource(R.string.stats_duel_this), duel.thisWeek)
                Spacer(Modifier.width(22.dp))
                DuelSide(stringResource(R.string.stats_duel_last), duel.lastWeek)
                Spacer(Modifier.weight(1f))
                // 只有一边打过就没有得比，这句话说都不说。
                duel.change?.let {
                    Text(
                        stringResource(R.string.stats_duel_change, signed(it)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (it >= 0) MaterialTheme.colorScheme.primary else ImmerlexColors.Rust,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuelSide(label: String, score: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            score?.toString() ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = if (score == null) ImmerlexColors.Meta else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = ImmerlexColors.Meta)
    }
}

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
            .capsule()
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
                    // 这一组自己在巅峰分上动了多少：算不准的那几组（升级前的旧账）这一行干脆不写。
                    run.peakChange?.let {
                        Text(
                            stringResource(R.string.stats_run_peak, signed(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (it < 0) ImmerlexColors.Rust else ImmerlexColors.Meta,
                        )
                    }
                }
            }
        }
    }
}

/** 升下一档还差几个词；词圣已经到头，那句就不差什么了。 */
@Composable
internal fun rankNextLine(rank: Rank): String =
    rank.tier.next?.let { stringResource(R.string.stats_rank_next, rank.toNext, rankTitleOf(it)) }
        ?: stringResource(R.string.stats_rank_top)

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
    QuizMode.REVIEW -> R.string.quiz_mode_review
    QuizMode.EARLY -> R.string.quiz_mode_early
    QuizMode.TRICKY -> R.string.quiz_mode_tricky
    QuizMode.NEMESIS -> R.string.quiz_mode_nemesis
}

private fun accuracy(run: QuizRun): Int =
    if (run.asked <= 0) 0 else (100.0 * run.correct / run.asked).roundToInt()

private fun formatted(instant: Instant): String = instant
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
