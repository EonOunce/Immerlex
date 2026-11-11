package com.immerlex.core.quiz

import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.QuizRunEntity
import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.schedule.PeakScoreReplay
import com.immerlex.testsupport.FakeQuizRunDao
import com.immerlex.testsupport.FakeReviewHistoryDao
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每做完一组留下的那行账：破纪录只跟同一个入口比，
 * 巅峰分的涨跌只算这一组自己写下的那几笔。
 */
class QuizRunStoreTest {

    private val at: Instant = Instant.parse("2024-06-01T04:00:00Z")
    private val dao = FakeQuizRunDao()
    private val history = FakeReviewHistoryDao()
    private val store = QuizRunStore(dao, history)

    private fun record(mode: QuizMode, correct: Int, asked: Int = 20, startedAtSec: Long = 0) =
        QuizRunRecord(
            mode = mode,
            asked = asked,
            correct = correct,
            wrong = asked - correct,
            bestStreak = correct,
            startedAtSec = startedAtSec,
        )

    /** 组开始前就已有的那段历史：够多了，巅峰分才算得出数。 */
    private fun pastMinutes(count: Int = PeakScoreReplay.MIN_SAMPLES) {
        history.items += List(count) { index ->
            event(index.toLong(), Rating.GOOD, at.epochSecond - 1_000L + index)
        }
    }

    private fun event(wordId: Long, rating: Rating, epochSec: Long) = RecallEventRow(
        wordId = wordId,
        rating = rating.value,
        source = LOG_SOURCE_QUIZ,
        difficulty = 5.0,
        scheduledDays = 1,
        epochSec = epochSec,
    )

    @Test
    fun theFirstGroupOfAnEntryHasNothingToBeatYet() = runBlocking {
        val saved = store.save(record(QuizMode.DUE, correct = 8), at)

        assertFalse("库里一行都还没有，就不该说有纪录可破", saved.isRecord)
        assertNull(saved.previousBest)
    }

    @Test
    fun aRecordIsMeasuredAgainstTheSameEntryOnly() = runBlocking {
        store.save(record(QuizMode.DUE, correct = 8), at)

        val equal = store.save(record(QuizMode.EARLY, correct = 8), at.plusSeconds(60))
        val beaten = store.save(record(QuizMode.EARLY, correct = 9), at.plusSeconds(120))

        assertFalse("别的入口考得再好，也不是这一组的成绩", equal.isRecord)
        assertTrue(beaten.isRecord)
        assertEquals(8, beaten.previousBest)
    }

    @Test
    fun aRunKeepsWhatCorrectAndWrongCannotSay() = runBlocking {
        store.save(QuizRunRecord(QuizMode.TRICKY, asked = 10, correct = 6, wrong = 4, bestStreak = 5), at)

        val run = store.recent(5).single()

        assertEquals(QuizMode.TRICKY, run.mode)
        assertEquals(at, run.finishedAt)
        assertEquals(10, run.asked)
        assertEquals(6, run.correct)
        assertEquals("连对最长的一段只在当时那次走子里存在", 5, run.bestStreak)
    }

    @Test
    fun recentAnswersNewestFirstAndSkipsRowsItCannotName() = runBlocking {
        store.save(record(QuizMode.DUE, correct = 3), at)
        store.save(record(QuizMode.DUE, correct = 7), at.plusSeconds(60))
        dao.items += QuizRunEntity(
            mode = "MYSTERY",
            finishedAtSec = at.plusSeconds(120).epochSecond,
            asked = 1,
            correct = 1,
            wrong = 0,
            bestStreak = 1,
        )

        val runs = store.recent(10)

        assertEquals("来路不明的入口整行跳过，不猜它是哪种测验", 2, runs.size)
        assertEquals(listOf(7, 3), runs.map { it.correct })
    }

    @Test
    fun aGroupRemembersWhetherItLiftedOrDroppedThePeak() = runBlocking {
        pastMinutes()
        history.items += event(90, Rating.AGAIN, at.epochSecond + 10)

        val saved = store.save(record(QuizMode.DUE, correct = 12, startedAtSec = at.epochSecond), at.plusSeconds(60))

        assertNotNull(saved.peakChange)
        assertTrue("这一组把分说倒了，涨跌就该是负的", (saved.peakChange ?: 0) < 0)
        assertEquals("留的那一行带着同一个数", saved.peakChange, store.recent(1).single().peakChange)
    }

    @Test
    fun aGroupThatWroteNoRealRecallSaysZeroNotNothing() = runBlocking {
        pastMinutes()

        val saved = store.save(record(QuizMode.NEMESIS, correct = 5, startedAtSec = at.epochSecond), at.plusSeconds(60))

        assertEquals("四选一全攒着当 attempt：这一组确实一个字都没动分", 0, saved.peakChange)
    }

    @Test
    fun aGroupFromBeforeThisBuildStaysSilent() = runBlocking {
        pastMinutes()
        history.items += event(90, Rating.GOOD, at.epochSecond + 10)

        val saved = store.save(record(QuizMode.DUE, correct = 18), at.plusSeconds(60))

        assertNull("升级前留下的会话没记开始时刻，不猜一个数顶上", saved.peakChange)
        assertNull(store.recent(1).single().peakChange)
    }
}
