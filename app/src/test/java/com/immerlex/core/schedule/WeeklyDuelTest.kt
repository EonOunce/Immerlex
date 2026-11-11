package com.immerlex.core.schedule

import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.fsrs.Rating
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 影子对决的周界与取高：一周的界线跟着日历上的周一，一周说的是"最高到过哪"，
 * 不是"周末那天剩多少"——分掉了不该把这周说成从没涨过。
 */
class WeeklyDuelTest {

    private fun event(rating: Rating = Rating.GOOD, wordId: Long = 1) = RecallEventRow(
        wordId = wordId,
        rating = rating.value,
        source = LOG_SOURCE_QUIZ,
        difficulty = 5.0,
        scheduledDays = 1,
        epochSec = 0L,
    )

    private fun epochDay(month: Int, day: Int) = LocalDate.of(2026, month, day).toEpochDay()

    @Test
    fun aWeekStartsOnMonday() {
        val monday = epochDay(11, 9)

        assertEquals("周一到周日是同一周", WeeklyDuel.weekOf(monday), WeeklyDuel.weekOf(epochDay(11, 15)))
        assertEquals("周日过完才翻页", WeeklyDuel.weekOf(monday) + 1, WeeklyDuel.weekOf(epochDay(11, 16)))
        assertEquals(WeeklyDuel.weekOf(monday) - 1, WeeklyDuel.weekOf(epochDay(11, 8)))
    }

    @Test
    fun aWeekKeepsTheHighestItReachedNotItsLastStep() {
        val thisWeek = WeeklyDuel.weekOf(epochDay(11, 9))
        val events = listOf(event(wordId = 1), event(wordId = 2), event(wordId = 3, rating = Rating.AGAIN))
        val scores = PeakScoreReplay.trajectory(events)

        val duel = WeeklyDuel.of(events, List(events.size) { thisWeek }, thisWeek)

        assertEquals(scores.maxOrZero(), duel.thisWeek)
        assertTrue("最后答错一次，这一周最高仍留在错之前", (duel.thisWeek ?: 0) > scores.last())
    }

    @Test
    fun lastWeekIsNotColouredByThisWeek() {
        val thisWeek = 100L
        val events = listOf(
            event(wordId = 1),
            event(wordId = 2, rating = Rating.AGAIN),
            event(wordId = 3),
            event(wordId = 4),
        )
        val weeks = listOf(thisWeek - 1, thisWeek - 1, thisWeek, thisWeek)
        val scores = PeakScoreReplay.trajectory(events)

        val duel = WeeklyDuel.of(events, weeks, thisWeek)

        assertEquals(scores[0], duel.lastWeek)
        assertEquals(scores[3], duel.thisWeek)
        assertEquals("两边都打过才有得比", (duel.thisWeek ?: 0) - (duel.lastWeek ?: 0), duel.change)
    }

    @Test
    fun aWeekNobodyPlayedDoesNotClaimAScore() {
        val events = listOf(event(wordId = 1), event(wordId = 2))

        val onlyThisWeek = WeeklyDuel.of(events, listOf(3L, 3L), thisWeek = 3L)
        assertNull(onlyThisWeek.lastWeek)
        assertNull("上周没记录就不说涨跌，不拿起点 1200 冒充打过一场", onlyThisWeek.change)

        val nothingAtAll = WeeklyDuel.of(emptyList(), emptyList(), thisWeek = 3L)
        assertNull(nothingAtAll.thisWeek)
        assertNull(nothingAtAll.lastWeek)
        assertNull(nothingAtAll.change)
    }

    private fun List<Int>.maxOrZero() = maxOrNull() ?: 0
}
