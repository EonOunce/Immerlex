package com.immerlex.core.schedule

import com.immerlex.core.db.DailyLoopDao
import com.immerlex.core.db.ReviewLogDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "今天是哪一天"的唯一判定（docs/adr/0025）。默认零点换日，只留一夜宽限：
 * 那一天过了凌晨 1 点还动过手、到期词又没清完，那么第二天 00:00–00:59 仍算前一天。
 * 清完到期词或者当天根本没学，零点照常翻页。
 *
 * 判断只看 `review_logs` 的原始时刻与 `daily_loop.dueCleared`，不看设备时钟的分数秒；
 * 每天最多查一次昨天的记录，调用方（今日页/测验/阅读）不必自己再判一遍。
 */
class DayBoundary(
    private val loopDao: DailyLoopDao,
    private val logDao: ReviewLogDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun dayOf(now: Instant): LocalDate {
        val local = now.atZone(zone)
        if (local.hour >= GRACE_UNTIL_HOUR) return local.toLocalDate()
        val yesterday = local.toLocalDate().minusDays(1)
        if (!hadDaytimeReview(yesterday)) return local.toLocalDate()
        return if (loopDao.byDay(yesterday.toEpochDay())?.dueCleared == true) {
            local.toLocalDate()
        } else {
            yesterday
        }
    }

    /** 这一天从哪一秒开始；宽限里给的是昨天的零点，统计口径跟着 [dayOf] 走。 */
    suspend fun startOf(now: Instant): Long =
        dayOf(now).atStartOfDay(zone).toEpochSecond()

    /**
     * 一条记录落在本地日历的哪一天。宽限只回答"今天算哪天"，
     * 已经过去的每一天按它自己的零点算，不把历史往回粘一天。
     */
    fun dayOf(epochSec: Long): Long =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate().toEpochDay()

    /**
     * 那天有没有宽限时段以外的复习。只认 01:00 以后的记录，避免把上一个宽限夜里
     * 00:xx 的那几笔当成"新的一天动过手"，否则连着一周每天熬夜会把日期一天天粘住。
     */
    private suspend fun hadDaytimeReview(day: LocalDate): Boolean {
        val from = day.atTime(GRACE_UNTIL_HOUR, 0).atZone(zone).toEpochSecond()
        val to = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
        return logDao.reviewsSince(from).any { it.reviewEpochSec < to }
    }

    companion object {
        const val GRACE_UNTIL_HOUR = 1
    }
}
