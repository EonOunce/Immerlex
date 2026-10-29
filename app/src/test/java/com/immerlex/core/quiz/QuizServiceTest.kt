package com.immerlex.core.quiz

import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.testsupport.FakeChapterDao
import com.immerlex.testsupport.FakeReviewLogDao
import com.immerlex.testsupport.FakeTrickyDao
import com.immerlex.testsupport.FakeWordDao
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizServiceTest {

    private val now: Instant = Instant.parse("2024-06-01T04:00:00Z")

    private fun parts(): Triple<QuizService, FakeWordDao, FakeReviewLogDao> {
        val words = FakeWordDao()
        val logs = FakeReviewLogDao()
        val schedule = ScheduleService(
            wordDao = words,
            logDao = logs,
            scheduler = Scheduler(
                learningSteps = emptyList(),
                relearningSteps = emptyList(),
                maximumInterval = 3650,
                enableFuzzing = false,
            ),
        )
        return Triple(
            QuizService(
                wordDao = words,
                logDao = logs,
                chapterDao = FakeChapterDao(),
                trickyDao = FakeTrickyDao(),
                schedule = schedule,
                zone = ZoneId.of("Asia/Shanghai"),
            ),
            words,
            logs,
        )
    }

    @Test
    fun dueCandidatesAreDueWordsPlusWordsLearnedTodayMinusRecalledToday() = runBlocking {
        val (service, words, logs) = parts()
        val due = seedDue(words, logs, "harbour")
        val fresh = words.insert(word("balcony"))
        val learned = words.insert(word("lantern"))
        logs.insert(log(learned, LOG_KIND_LEARN, now.minusSeconds(3600)))
        val recalled = seedDue(words, logs, "pebble")
        logs.insert(log(recalled, LOG_KIND_RECALL, now.minusSeconds(1800)))

        val ids = service.dueCandidateIds(now, dailyNewLimit = 10)

        assertTrue("到期词要考", due in ids)
        assertTrue("今天学过但还没回忆的词要考", learned in ids)
        assertFalse("没学过的生词不考", fresh in ids)
        assertFalse("今天已经回忆过的不重复考", recalled in ids)
    }

    @Test
    fun planDueBuildsOneQuestionPerCandidateAndOffersADifferentRetryType() = runBlocking {
        val (service, words, logs) = parts()
        val due = seedDue(words, logs, "harbour")
        val learned = words.insert(word("lantern"))
        logs.insert(log(learned, LOG_KIND_LEARN, now.minusSeconds(3600)))
        words.insert(word("balcony"))
        words.insert(word("pebble"))

        val session = service.planDue(now, dailyNewLimit = 10, seed = 1)

        assertEquals(QuizMode.DUE, session.mode)
        assertEquals(setOf(due, learned), session.wordIds)
        assertEquals(2, session.questions.size)
        session.questions.forEach { question ->
            assertEquals(1, question.options.count { it.correct })
            assertTrue(question.options.size >= 2)
            session.alternatives[question.wordId]?.let { retry ->
                assertNotEquals("答错后换一种题型练", question.type, retry.type)
            }
        }
    }

    @Test
    fun recordingAnAnswerWritesOneRecallWithItsQuestionType() = runBlocking {
        val (service, words, logs) = parts()
        val learned = words.insert(word("lantern"))
        logs.insert(log(learned, LOG_KIND_LEARN, now.minusSeconds(3600)))
        words.insert(word("balcony"))
        words.insert(word("pebble"))
        val question = service.planDue(now, dailyNewLimit = 10, seed = 2).questions.single()

        assertTrue(service.record(question, correct = true, now = now.plusSeconds(5)))

        val written = logs.items.last()
        assertEquals(LOG_KIND_RECALL, written.kind)
        assertEquals(QuizService.SOURCE_QUIZ, written.source)
        assertEquals(question.type.key, written.questionType)
        assertEquals(Rating.GOOD.value, written.rating)
    }

    @Test
    fun earlyGroupsTakeTheFiveLeastStableWordsAndCycleWhenFewRemain() = runBlocking {
        val (service, words, logs) = parts()
        val ids = (1..7).map { index -> seedFuture(words, logs, "w$index", stability = index.toDouble()) }

        val first = service.planEarly(now, excludeWordIds = emptySet(), seed = 3)
        assertEquals(5, first.questions.size)
        assertEquals("最不稳定的 5 个", ids.take(5).toSet(), first.wordIds)

        val second = service.planEarly(now, excludeWordIds = first.wordIds, seed = 4)
        assertEquals(5, second.questions.size)
        assertTrue("剩下没练过的两个必须进下一组", second.wordIds.containsAll(ids.drop(5)))
        assertTrue(second.wordIds.any { it in first.wordIds })

        val third = service.planEarly(now, excludeWordIds = ids.toSet(), seed = 5)
        assertEquals("全练过一遍后循环出题，不再有每日上限", 5, third.questions.size)
    }

    @Test
    fun earlyCandidatesIgnoreUntouchedAndDueWords() = runBlocking {
        val (service, words, logs) = parts()
        val future = seedFuture(words, logs, "harbour", stability = 2.0)
        words.insert(word("balcony"))
        val due = seedDue(words, logs, "pebble")

        val candidates = service.earlyCandidates(now).map { it.id }

        assertEquals(listOf(future), candidates)
        assertFalse(due in candidates)
    }

    private suspend fun seedDue(words: FakeWordDao, logs: FakeReviewLogDao, name: String): Long {
        val id = words.insert(
            word(name).copy(
                stability = 5.0,
                difficulty = 6.0,
                dueEpochSec = now.minus(1, ChronoUnit.DAYS).epochSecond,
                lastReviewEpochSec = now.minus(2, ChronoUnit.DAYS).epochSecond,
                fsrsState = FSRS_STATE_REVIEW,
                reps = 1,
                intervalDays = 1,
            ),
        )
        logs.insert(
            ReviewLogEntity(
                wordId = id,
                rating = Rating.GOOD.value,
                source = "reader",
                reviewEpochSec = now.minus(2, ChronoUnit.DAYS).epochSecond,
                elapsedDays = 0,
                scheduledDays = 1,
                kind = LOG_KIND_RECALL,
            ),
        )
        return id
    }

    private suspend fun seedFuture(
        words: FakeWordDao,
        logs: FakeReviewLogDao,
        name: String,
        stability: Double,
    ): Long {
        val id = words.insert(
            word(name).copy(
                stability = stability,
                difficulty = 6.0,
                dueEpochSec = now.plus(3, ChronoUnit.DAYS).epochSecond,
                lastReviewEpochSec = now.minusSeconds(3600).epochSecond,
                fsrsState = FSRS_STATE_REVIEW,
                reps = 1,
                intervalDays = 3,
            ),
        )
        logs.insert(log(id, LOG_KIND_LEARN, now.minusSeconds(3600)))
        return id
    }

    private fun word(name: String) = WordEntity(
        word = name,
        translation = "释义-$name",
        source = "test",
        createdAtSec = 0,
    )

    private fun log(wordId: Long, kind: String, at: Instant) = ReviewLogEntity(
        wordId = wordId,
        rating = Rating.GOOD.value,
        source = "test",
        reviewEpochSec = at.epochSecond,
        elapsedDays = 0,
        scheduledDays = 3,
        kind = kind,
    )
}
