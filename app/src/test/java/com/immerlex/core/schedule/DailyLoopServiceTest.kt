package com.immerlex.core.schedule

import com.immerlex.core.db.DailyLoopEntity
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.testsupport.FakeDailyLoopDao
import com.immerlex.testsupport.FakeReviewLogDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 服务层只做两件事：把已有事实拼成 [DailyInfo]，以及把"过后查不到"的两件写进 daily_loop。
 * 规则本身的边界测在 [DailyLoopTest]。
 */
class DailyLoopServiceTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, shanghai).toInstant()
    private val loop = FakeDailyLoopDao()
    private val logs = FakeReviewLogDao()

    private fun service(zone: ZoneId = shanghai) = DailyLoopService(loop, logs, zone)

    /** [daysAgo] 天前的本地中午，记一次"忘了"以外的评分。 */
    private suspend fun reviewAt(daysAgo: Int, wordId: Long = 1) {
        val at = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, shanghai).minusDays(daysAgo.toLong())
        logs.insert(
            ReviewLogEntity(
                wordId = wordId, rating = 3, source = "reader", reviewEpochSec = at.toEpochSecond(),
                elapsedDays = 1, scheduledDays = 3, preDueEpochSec = 0,
            ),
        )
    }

    @Test
    fun emptyQueueTodayIsRememberedAsCleared() = runTest {
        service().info(now, dueCount = 0)
        val row = loop.items.getValue(ZonedDateTime.ofInstant(now, shanghai).toLocalDate().toEpochDay())
        assertTrue(row.dueCleared)
        assertFalse(row.makeupConsumed)
    }

    @Test
    fun wordsStillDueLeaveTodayUncleared() = runTest {
        service().info(now, dueCount = 5)
        assertTrue(loop.items.isEmpty())
    }

    @Test
    fun grantedMakeupIsWrittenOnceAndReadBackTheSame() = runTest {
        reviewAt(daysAgo = 0)
        reviewAt(daysAgo = 2)
        reviewAt(daysAgo = 5)
        val first = service().info(now, dueCount = 0)
        assertTrue(first.makeupGrantedToday)
        // 今天 + 补回来的昨天 + 前天。
        assertEquals(3, first.streakDays)

        val second = service().info(now, dueCount = 0)
        assertEquals(first.streakDays, second.streakDays)
        assertEquals(listOf(true), loop.items.values.map { it.makeupConsumed })
    }

    @Test
    fun reviewedTodayCountIsDistinctWordsNotClicks() = runTest {
        reviewAt(daysAgo = 0, wordId = 7)
        reviewAt(daysAgo = 0, wordId = 7)
        reviewAt(daysAgo = 0, wordId = 8)
        assertEquals(2, service().info(now, dueCount = 3).reviewedToday)
    }

    @Test
    fun aLateNightReviewBelongsToTheLocalDayNotUtc() = runTest {
        // 上海 10-27 凌晨 1 点 = UTC 10-26 傍晚，落在哪一天完全取决于时区。
        // 这里刻意让今天仍有到期词：不写库、不补卡，只比时区对"哪一天打过卡"的判断。
        val at = ZonedDateTime.of(2026, 10, 27, 1, 0, 0, 0, shanghai)
        logs.insert(
            ReviewLogEntity(
                wordId = 1, rating = 3, source = "reader", reviewEpochSec = at.toEpochSecond(),
                elapsedDays = 1, scheduledDays = 3, preDueEpochSec = 0,
            ),
        )
        assertEquals(1, service().info(now, dueCount = 5).streakDays)
        assertEquals(0, service(zone = ZoneId.of("UTC")).info(now, dueCount = 5).streakDays)
    }

    @Test
    fun storingADayTwiceKeepsBothFlags() = runTest {
        val day = ZonedDateTime.ofInstant(now, shanghai).toLocalDate().toEpochDay()
        loop.upsert(DailyLoopEntity(epochDay = day, dueCleared = false, makeupConsumed = true))
        service().info(now, dueCount = 0)
        val row = loop.items.getValue(day)
        assertTrue(row.dueCleared)
        assertTrue(row.makeupConsumed)
    }
}
