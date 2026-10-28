package com.immerlex.core.schedule

import com.immerlex.core.db.ReviewMoment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 热力图的一格：本地日历日、那天背/复习过的不重复词数、配色档位（0–4）。 */
data class HeatDay(val epochDay: Long, val words: Int, val level: Int)

const val HEATMAP_WEEKS = 13

/**
 * 近 [weeks] 周的日历网格（周一当第一列），最后一列是本周；未来的格子也保留，
 * UI 不用自己补位。同一天里同一个词不论复习了几次只算一个——这页说的是"背过多少词"，
 * 不是"点过多少次按钮"。
 */
fun heatGridOf(
    moments: List<ReviewMoment>,
    today: LocalDate,
    weeks: Int = HEATMAP_WEEKS,
    zone: ZoneId = ZoneId.systemDefault(),
): List<HeatDay> {
    val start = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
    val wordsByDay = HashMap<Long, MutableSet<Long>>()
    moments.forEach { moment ->
        val day = Instant.ofEpochSecond(moment.reviewEpochSec).atZone(zone).toLocalDate()
        if (!day.isBefore(start) && !day.isAfter(today)) {
            wordsByDay.getOrPut(day.toEpochDay()) { HashSet() }.add(moment.wordId)
        }
    }
    return (0 until weeks * 7).map { offset ->
        val day = start.plusDays(offset.toLong())
        val words = wordsByDay[day.toEpochDay()]?.size ?: 0
        HeatDay(day.toEpochDay(), words, heatLevel(words))
    }
}

fun heatLevel(words: Int): Int = when {
    words <= 0 -> 0
    words < 5 -> 1
    words < 10 -> 2
    words < 20 -> 3
    else -> 4
}
