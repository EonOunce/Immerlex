package com.immerlex.core.quiz

import com.immerlex.core.db.QuizRunEntity
import com.immerlex.testsupport.FakeQuizRunDao
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizRunStoreTest {

    private val at: Instant = Instant.parse("2024-06-01T04:00:00Z")

    private fun record(mode: QuizMode, correct: Int, asked: Int = 20) =
        QuizRunRecord(mode = mode, asked = asked, correct = correct, wrong = asked - correct, bestStreak = correct)

    @Test
    fun theFirstGroupOfAnEntryHasNothingToBeatYet() = runBlocking {
        val store = QuizRunStore(FakeQuizRunDao())

        val saved = store.save(record(QuizMode.DUE, correct = 8), at)

        assertFalse("库里一行都还没有，就不该说有纪录可破", saved.isRecord)
        assertNull(saved.previousBest)
    }

    @Test
    fun aRecordIsMeasuredAgainstTheSameEntryOnly() = runBlocking {
        val store = QuizRunStore(FakeQuizRunDao())
        store.save(record(QuizMode.DUE, correct = 8), at)

        val equal = store.save(record(QuizMode.EARLY, correct = 8), at.plusSeconds(60))
        val beaten = store.save(record(QuizMode.EARLY, correct = 9), at.plusSeconds(120))

        assertFalse("别的入口考得再好，也不是这一组的成绩", equal.isRecord)
        assertTrue(beaten.isRecord)
        assertEquals(8, beaten.previousBest)
    }

    @Test
    fun aRunKeepsWhatCorrectAndWrongCannotSay() = runBlocking {
        val store = QuizRunStore(FakeQuizRunDao())
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
        val dao = FakeQuizRunDao()
        val store = QuizRunStore(dao)
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
}
