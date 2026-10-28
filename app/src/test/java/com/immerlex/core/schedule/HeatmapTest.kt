package com.immerlex.core.schedule

import com.immerlex.core.db.ReviewMoment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 28) // 周三

    private fun moment(wordId: Long, date: LocalDate, hour: Int = 12) =
        ReviewMoment(wordId, date.atTime(hour, 0).atZone(zone).toEpochSecond())

    private fun todayCell(days: List<HeatDay>) = days.single { it.epochDay == today.toEpochDay() }

    @Test
    fun gridIsThirteenWholeWeeksStartingOnMonday() {
        val days = heatGridOf(emptyList(), today, zone = zone)

        assertEquals(91, days.size)
        val start = LocalDate.ofEpochDay(days.first().epochDay)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(today.with(DayOfWeek.MONDAY).minusWeeks(12), start)
        assertEquals(start.plusDays(90), LocalDate.ofEpochDay(days.last().epochDay))
    }

    @Test
    fun duplicateScoresOnTheSameWordCountOnce() {
        val days = heatGridOf(
            listOf(moment(7, today, 9), moment(7, today, 21), moment(8, today)),
            today,
            zone = zone,
        )

        assertEquals(2, todayCell(days).words)
        assertEquals(1, todayCell(days).level)
    }

    @Test
    fun daysOutsideTheWindowAreIgnored() {
        val days = heatGridOf(
            listOf(moment(1, today.minusDays(90)), moment(2, today.plusDays(1)), moment(3, today)),
            today,
            zone = zone,
        )

        assertEquals(1, todayCell(days).words)
        assertEquals(1, days.sumOf { it.words })
    }

    @Test
    fun dayBoundaryFollowsTheGivenZone() {
        // 2026-10-27 23:30 UTC 已经是上海 10-28 07:30，应记在今天这一格。
        val epochSec = LocalDate.of(2026, 10, 27).atTime(23, 30).toInstant(ZoneOffset.UTC).epochSecond
        val days = heatGridOf(listOf(ReviewMoment(1, epochSec)), today, zone = zone)

        assertEquals(1, todayCell(days).words)
    }

    @Test
    fun levelsUseFixedThresholds() {
        assertEquals(0, heatLevel(0))
        assertEquals(1, heatLevel(1))
        assertEquals(1, heatLevel(4))
        assertEquals(2, heatLevel(5))
        assertEquals(2, heatLevel(9))
        assertEquals(3, heatLevel(10))
        assertEquals(3, heatLevel(19))
        assertEquals(4, heatLevel(20))
    }
}
