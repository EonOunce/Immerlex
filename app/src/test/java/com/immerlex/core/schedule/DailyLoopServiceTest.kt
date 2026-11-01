package com.immerlex.core.schedule

import com.immerlex.core.db.DailyLoopEntity
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.LOG_SOURCE_READER
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.testsupport.FakeDailyLoopDao
import com.immerlex.testsupport.FakeReviewLogDao
import com.immerlex.testsupport.FakeWordDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
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
    private val words = FakeWordDao()

    private fun service(zone: ZoneId = shanghai) = DailyLoopService(loop, logs, words, zone)

    /** [daysAgo] 天前的本地中午，记一次"忘了"以外的评分。 */
    private suspend fun reviewAt(
        daysAgo: Int,
        wordId: Long = 1,
        kind: String = LOG_KIND_RECALL,
        source: String = LOG_SOURCE_READER,
    ) {
        val at = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, shanghai).minusDays(daysAgo.toLong())
        logs.insert(
            ReviewLogEntity(
                wordId = wordId, rating = 3, source = source, reviewEpochSec = at.toEpochSecond(),
                elapsedDays = 1, scheduledDays = 3, preDueEpochSec = 0, kind = kind,
            ),
        )
    }

    private fun seedWord(id: Long, word: String) {
        words.items[id] = WordEntity(id = id, word = word, source = "test", createdAtSec = 0)
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
    fun aReviewAfterMidnightStillCountsForTheUnfinishedDay() = runTest {
        seedWord(7, "lantern")
        seedWord(8, "pebble")
        reviewAt(daysAgo = 1, wordId = 7)
        val late = ZonedDateTime.of(2026, 10, 28, 0, 30, 0, 0, shanghai)
        logs.insert(
            ReviewLogEntity(
                wordId = 8, rating = 3, source = LOG_SOURCE_READER, reviewEpochSec = late.toEpochSecond(),
                elapsedDays = 1, scheduledDays = 3, preDueEpochSec = 0,
            ),
        )

        val info = service().info(late.toInstant(), dueCount = 5)

        assertEquals("凌晨背的那一个还算昨天", 2, info.reviewedToday)
        assertEquals(1, info.streakDays)
    }

    @Test
    fun finishingTheDueListDuringTheGraceHourRollsTheDayForward() = runTest {
        reviewAt(daysAgo = 1)
        val grace = ZonedDateTime.of(2026, 10, 28, 0, 30, 0, 0, shanghai)

        val before = service().info(grace.toInstant(), dueCount = 0)
        assertEquals("没清完之前，今天仍是昨天", 1, before.reviewedToday)
        assertEquals(LocalDate.of(2026, 10, 27).toEpochDay(), loop.items.keys.single())

        val after = service().info(grace.plusMinutes(1).toInstant(), dueCount = 0)
        assertEquals("清完到期词，零点就翻页", 0, after.reviewedToday)
    }

    @Test
    fun todayTallySplitsWordsTheReaderMetFromWordsQuizzed() = runTest {
        seedWord(7, "lantern")
        seedWord(8, "harbour")
        seedWord(9, "pebble")
        reviewAt(daysAgo = 0, wordId = 7, kind = LOG_KIND_LEARN)
        reviewAt(daysAgo = 0, wordId = 8)
        reviewAt(daysAgo = 0, wordId = 9, source = LOG_SOURCE_QUIZ)

        val tally = service().todayTally(now)

        assertEquals(listOf("lantern"), tally.learned)
        assertEquals(listOf("harbour", "pebble"), tally.recalled)
        assertEquals(listOf("pebble"), tally.quizzed)
    }

    @Test
    fun aReviewYesterdayLeavesTodaysListsEmpty() = runTest {
        seedWord(7, "lantern")
        reviewAt(daysAgo = 1, wordId = 7)

        val tally = service().todayTally(now)

        assertTrue(tally.recalled.isEmpty())
        assertTrue(tally.quizzed.isEmpty())
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
