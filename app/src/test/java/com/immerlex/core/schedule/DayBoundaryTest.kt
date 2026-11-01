package com.immerlex.core.schedule

import com.immerlex.core.db.DailyLoopEntity
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.testsupport.FakeDailyLoopDao
import com.immerlex.testsupport.FakeReviewLogDao
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 一条规则四个出口：白天/晚上动过手、到期词没清完，宽限到凌晨 1 点；其余一律零点翻页。
 * 这里只测"今天算哪天"，统计口径由 [DailyLoopServiceTest] 接住。
 */
class DayBoundaryTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val loop = FakeDailyLoopDao()
    private val logs = FakeReviewLogDao()
    private val boundary = DayBoundary(loop, logs, shanghai)

    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, shanghai)

    private suspend fun review(at: ZonedDateTime) {
        logs.insert(
            ReviewLogEntity(
                wordId = 1, rating = 3, source = "reader", reviewEpochSec = at.toEpochSecond(),
                elapsedDays = 1, scheduledDays = 3, preDueEpochSec = 0,
            ),
        )
    }

    @Test
    fun anUnfinishedDayKeepsRunningUntilOneInTheMorning() = runTest {
        review(at(1, 22))

        assertEquals(LocalDate.of(2026, 11, 1), boundary.dayOf(at(2, 0, 30).toInstant()))
        assertEquals(
            "宽限里的一天从昨天零点算起",
            at(1, 0).toEpochSecond(),
            boundary.startOf(at(2, 0, 30).toInstant()),
        )
    }

    @Test
    fun oneInTheMorningEndsTheGrace() = runTest {
        review(at(1, 22))

        assertEquals(LocalDate.of(2026, 11, 2), boundary.dayOf(at(2, 1, 0).toInstant()))
    }

    @Test
    fun aClearedDueListEndsTheDayAtMidnight() = runTest {
        review(at(1, 22))
        loop.upsert(DailyLoopEntity(epochDay = LocalDate.of(2026, 11, 1).toEpochDay(), dueCleared = true))

        assertEquals(LocalDate.of(2026, 11, 2), boundary.dayOf(at(2, 0, 30).toInstant()))
    }

    @Test
    fun aDayWithoutStudyIsNotFoundByTheGrace() = runTest {
        assertEquals(LocalDate.of(2026, 11, 2), boundary.dayOf(at(2, 0, 30).toInstant()))
    }

    @Test
    fun onlyLateNightReviewsDoNotStretchTheGraceForever() = runTest {
        // 前一天只在宽限时段里背过（00:30），那不算"白天动过手"，今天照常零点翻页。
        review(at(1, 0, 30))

        assertEquals(LocalDate.of(2026, 11, 2), boundary.dayOf(at(2, 0, 30).toInstant()))
    }
}
