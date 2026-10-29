package com.immerlex.core.schedule

import com.immerlex.core.db.ReviewMoment
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 28)

    private fun moment(wordId: Long, date: LocalDate, hour: Int = 12) =
        ReviewMoment(wordId, date.atTime(hour, 0).atZone(zone).toEpochSecond())

    @Test
    fun seriesEndsTodayAndCoversTheWholeWindow() {
        val days = trendSeriesOf(emptyList(), today, zone = zone)

        assertEquals(30, days.size)
        assertEquals(today.toEpochDay(), days.last().epochDay)
        assertEquals(today.minusDays(29).toEpochDay(), days.first().epochDay)
    }

    @Test
    fun duplicateScoresOnTheSameWordCountOnce() {
        val days = trendSeriesOf(
            listOf(moment(7, today, 9), moment(7, today, 21), moment(8, today)),
            today,
            zone = zone,
        )

        assertEquals(2, days.last().words)
    }

    @Test
    fun daysOutsideTheWindowAreIgnored() {
        val days = trendSeriesOf(
            listOf(moment(1, today.minusDays(30)), moment(2, today.plusDays(1)), moment(3, today)),
            today,
            zone = zone,
        )

        assertEquals(1, days.last().words)
        assertEquals(1, days.sumOf { it.words })
    }

    @Test
    fun dayBoundaryFollowsTheGivenZone() {
        // 2026-10-27 23:30 UTC 已经是上海 10-28 07:30，应记在今天这根柱子上。
        val epochSec = LocalDate.of(2026, 10, 27).atTime(23, 30).toInstant(ZoneOffset.UTC).epochSecond
        val days = trendSeriesOf(listOf(ReviewMoment(1, epochSec)), today, zone = zone)

        assertEquals(1, days.last().words)
    }
}
