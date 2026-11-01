package com.immerlex.core.schedule

import com.immerlex.core.db.DailyLoopDao
import com.immerlex.core.db.DailyLoopEntity
import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.WordDao
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * 今日碰过的词面，分三种：新学、回忆（阅读与提前复习也算）、测验。
 * 分享卡上的数字和词墙出自同一份记录，不会一个说 12 个、另一个只列 8 个。
 */
data class TodayTally(
    val learned: List<String>,
    val recalled: List<String>,
    val quizzed: List<String>,
)

/**
 * 今日页的唯一取数入口（docs/adr/0006 第 1 条）：把已经存在的事实拼成 [DailyInfo]，
 * 并顺手记下两件过后查不到的事——今天的到期词清空了、今天用掉了本周的补卡。
 *
 * [dueCount] 由调用方从 [ScheduleService.todaysQueue] 传来，而不是在这里另算一遍，
 * 这样阅读页头部和今日页永远说同一个数。
 */
class DailyLoopService(
    private val loopDao: DailyLoopDao,
    private val logDao: ReviewLogDao,
    private val wordDao: WordDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun info(now: Instant, dueCount: Int): DailyInfo {
        val today = now.atZone(zone).toLocalDate()
        val todayDay = today.toEpochDay()
        val moments = logDao.reviewsSince(today.minusDays(STREAK_LOOKBACK_DAYS.toLong()).atStartOfDay(zone).toEpochSecond())
        val rows = loopDao.since(todayDay - STREAK_LOOKBACK_DAYS)
        val info = dailyInfoOf(
            DailyLoopInput(
                today = today,
                reviewedDays = moments.mapTo(HashSet(moments.size)) { epochDay(it.reviewEpochSec) },
                clearedDays = rows.filter { it.dueCleared }.mapTo(HashSet()) { it.epochDay }
                    .apply { if (dueCount == 0) add(todayDay) },
                makeupDays = rows.filter { it.makeupConsumed }.mapTo(HashSet()) { it.epochDay },
                dueCount = dueCount,
                reviewedToday = moments.filter { epochDay(it.reviewEpochSec) == todayDay }
                    .map { it.wordId }.distinct().size,
            ),
        )
        if (dueCount == 0 || info.makeupGrantedToday) {
            val existing = loopDao.byDay(todayDay)
            loopDao.upsert(
                DailyLoopEntity(
                    epochDay = todayDay,
                    dueCleared = existing?.dueCleared == true || dueCount == 0,
                    makeupConsumed = existing?.makeupConsumed == true || info.makeupGrantedToday,
                ),
            )
        }
        return info
    }

    /** 输入与 [info] 同源：都按本机时区把 `review_logs` 的时刻落到日历日上。 */
    suspend fun heatGrid(now: Instant, weeks: Int = HEATMAP_WEEKS): List<HeatDay> {
        val today = now.atZone(zone).toLocalDate()
        val start = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
        val since = start.atStartOfDay(zone).toEpochSecond()
        return heatGridOf(logDao.reviewsSince(since), today, weeks, zone)
    }

    /**
     * 分享卡上的今日：数字按不重复词数算，与热力图一个口径；词墙用同一批词，按字母排。
     * 一个词今天既学过又回忆过，会同时出现在两边——这三行回答的是三种动作，不是加法。
     */
    suspend fun todayTally(now: Instant): TodayTally {
        val since = now.atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()
        return TodayTally(
            learned = wordsOf(logDao.learnedWordIdsSince(since)),
            recalled = wordsOf(logDao.recalledWordIdsSince(since)),
            quizzed = wordsOf(logDao.recalledWordIdsBySourceSince(LOG_SOURCE_QUIZ, since)),
        )
    }

    /** 词面按字母排；卡片上的数字也数这一份，两边不会各说各的。 */
    private suspend fun wordsOf(ids: List<Long>): List<String> =
        if (ids.isEmpty()) emptyList() else wordDao.findByIds(ids).map { it.word }.sortedBy { it.lowercase() }

    /** 趋势图与热力图同源，只是窗口从整周网格换成最近 [days] 天。 */
    suspend fun trend(now: Instant, days: Int = TREND_DAYS): List<TrendDay> {
        val today = now.atZone(zone).toLocalDate()
        val start = today.minusDays((days - 1).toLong())
        return trendSeriesOf(
            logDao.reviewsSince(start.atStartOfDay(zone).toEpochSecond()),
            today,
            days,
            zone,
        )
    }

    private fun epochDay(epochSec: Long): Long =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate().toEpochDay()
}
