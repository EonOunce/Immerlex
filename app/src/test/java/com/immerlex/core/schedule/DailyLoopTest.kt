package com.immerlex.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 打卡与补卡规则（docs/adr/0006 第 2 条）。用例固定落在 2026-10-28（周三，ISO 第 44 周），
 * 这样"本周/上周"是有确定答案的：本周一 = 10-26，上周日 = 10-25。
 */
class DailyLoopTest {

    private val today = LocalDate.of(2026, 10, 28)

    /** 相对今天的天数偏移，负数表示过去。 */
    private fun day(offset: Long): Long = today.plusDays(offset).toEpochDay()

    private fun info(
        reviewed: Set<Long>,
        cleared: Set<Long> = emptySet(),
        makeup: Set<Long> = emptySet(),
        dueCount: Int = 0,
        reviewedToday: Int = if (day(0) in reviewed) 1 else 0,
    ) = dailyInfoOf(DailyLoopInput(today, reviewed, cleared, makeup, dueCount, reviewedToday))

    @Test
    fun countsEveryConsecutiveDayIncludingToday() {
        val i = info(reviewed = setOf(day(0), day(-1), day(-2)))
        assertEquals(3, i.streakDays)
        assertTrue(i.checkedInToday)
    }

    @Test
    fun notCheckingInTodayAloneDoesNotBreakTheStreak() {
        val i = info(reviewed = setOf(day(-1), day(-2)))
        assertEquals(2, i.streakDays)
        assertFalse(i.checkedInToday)
    }

    @Test
    fun clearingTodaysQueueRestoresYesterdaysCard() {
        val i = info(reviewed = setOf(day(0), day(-2), day(-3)), cleared = setOf(day(0)))
        assertTrue(i.makeupGrantedToday)
        // 今天 + 补回来的昨天 + 前天 + 大前天。
        assertEquals(4, i.streakDays)
        assertTrue(i.makeupUsedThisWeek)
    }

    @Test
    fun makeupWaitsUntilTheQueueIsActuallyEmpty() {
        val i = info(reviewed = setOf(day(0), day(-2)), dueCount = 5)
        assertFalse(i.makeupGrantedToday)
        assertTrue(i.makeupPendingToday)
        // 昨天补不上，连续就停在今一天。
        assertEquals(1, i.streakDays)
    }

    @Test
    fun onlyTheDayImmediatelyBeforeTodayCanBeBridged() {
        val i = info(reviewed = setOf(day(0), day(-3)), cleared = setOf(day(0)))
        assertTrue(i.makeupGrantedToday)
        // 前天与大前天之间那道更老的缺口不补，连续数到今天加昨天为止。
        assertEquals(2, i.streakDays)
    }

    @Test
    fun secondMakeupInTheSameIsoWeekIsRefused() {
        // 本周一（10-26）已经补过一次，今天（周三）不能再补。
        val i = info(
            reviewed = setOf(day(0), day(-2), day(-3)),
            cleared = setOf(day(0)),
            makeup = setOf(day(-2)),
        )
        assertFalse(i.makeupGrantedToday)
        assertTrue(i.makeupUsedThisWeek)
        assertEquals(1, i.streakDays)
    }

    @Test
    fun makeupInPreviousIsoWeekDoesNotBlockThisWeek() {
        // 10-25 是上周日，用的是上一周的额度。
        val i = info(
            reviewed = setOf(day(0), day(-3), day(-4)),
            cleared = setOf(day(0)),
            makeup = setOf(day(-3)),
        )
        assertTrue(i.makeupGrantedToday)
    }

    @Test
    fun aMakeupAlreadyOnRecordKeepsCountingWithoutBeingRecharged() {
        // 周二（10-27）补过、把周一（10-26）那道缺口续上了；今天还没打卡。
        val i = info(
            reviewed = setOf(day(-1), day(-3), day(-4)),
            cleared = setOf(day(-1)),
            makeup = setOf(day(-1)),
        )
        assertEquals(4, i.streakDays)
        assertFalse(i.makeupGrantedToday)
        assertTrue(i.makeupUsedThisWeek)
    }

    @Test
    fun readingTheSameDayTwiceGivesTheSameAnswer() {
        val reviewed = setOf(day(0), day(-2))
        val cleared = setOf(day(0))
        val first = info(reviewed = reviewed, cleared = cleared)
        val second = info(reviewed = reviewed, cleared = cleared, makeup = setOf(day(0)))
        assertEquals(first.streakDays, second.streakDays)
        // 今天 + 补回来的昨天 + 前天。
        assertEquals(3, second.streakDays)
        assertTrue(second.makeupGrantedToday)
    }

    @Test
    fun firstEverDayEarnsNothingByBridging() {
        // 昨天之前没有任何打卡，就无所谓"漏了一天"。
        val i = info(reviewed = setOf(day(0)), cleared = setOf(day(0)), dueCount = 0)
        assertFalse(i.makeupGrantedToday)
        assertFalse(i.makeupPendingToday)
        assertEquals(1, i.streakDays)
    }

    @Test
    fun emptyHistoryIsZero() {
        val i = info(reviewed = emptySet(), dueCount = 0, reviewedToday = 0)
        assertEquals(0, i.streakDays)
        assertFalse(i.checkedInToday)
    }

    @Test
    fun todayAndDoneAreReportedForTheHomeScreen() {
        val i = info(reviewed = setOf(day(0), day(-1)), dueCount = 7, reviewedToday = 3)
        assertEquals(7, i.dueCount)
        assertEquals(3, i.reviewedToday)
        assertEquals(2, i.streakDays)
    }
}
