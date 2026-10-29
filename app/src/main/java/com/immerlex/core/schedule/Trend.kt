package com.immerlex.core.schedule

import com.immerlex.core.db.ReviewMoment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 趋势图的一天：本地日历日与那天背/复习过的不重复词数。 */
data class TrendDay(val epochDay: Long, val words: Int)

const val TREND_DAYS = 30

/**
 * 近 [days] 天（含今天）的每日不重复词数，按日期升序，最后一天落在 [today]。
 * 计数口径与热力图一致：同一天同一个词只算一个，未来的记录忽略。
 */
fun trendSeriesOf(
    moments: List<ReviewMoment>,
    today: LocalDate,
    days: Int = TREND_DAYS,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TrendDay> {
    require(days > 0) { "days must be positive" }
    val start = today.minusDays((days - 1).toLong())
    val wordsByDay = HashMap<Long, MutableSet<Long>>()
    moments.forEach { moment ->
        val day = Instant.ofEpochSecond(moment.reviewEpochSec).atZone(zone).toLocalDate()
        if (!day.isBefore(start) && !day.isAfter(today)) {
            wordsByDay.getOrPut(day.toEpochDay()) { HashSet() }.add(moment.wordId)
        }
    }
    return (0 until days).map { offset ->
        val day = start.plusDays(offset.toLong())
        TrendDay(day.toEpochDay(), wordsByDay[day.toEpochDay()]?.size ?: 0)
    }
}
