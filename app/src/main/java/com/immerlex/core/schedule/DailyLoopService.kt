package com.immerlex.core.schedule

import com.immerlex.core.db.DailyLoopDao
import com.immerlex.core.db.DailyLoopEntity
import com.immerlex.core.db.ReviewLogDao
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

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
