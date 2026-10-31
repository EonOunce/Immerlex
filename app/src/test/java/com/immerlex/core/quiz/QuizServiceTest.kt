package com.immerlex.core.quiz

import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.TrickyLinkEntity
import com.immerlex.core.db.TrickyWordEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.testsupport.FakeChapterDao
import com.immerlex.testsupport.FakeQuizMaterialDao
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
        return Triple(service(words, logs, FakeTrickyDao()), words, logs)
    }

    private fun service(
        words: FakeWordDao,
        logs: FakeReviewLogDao,
        tricky: FakeTrickyDao,
        enricher: QuizEnricher? = null,
        material: QuizMaterialStore? = null,
    ): QuizService = QuizService(
        wordDao = words,
        logDao = logs,
        chapterDao = FakeChapterDao(),
        trickyDao = tricky,
        schedule = ScheduleService(
            wordDao = words,
            logDao = logs,
            scheduler = Scheduler(
                learningSteps = emptyList(),
                relearningSteps = emptyList(),
                maximumInterval = 3650,
                enableFuzzing = false,
            ),
        ),
        enricher = enricher,
        material = material,
        zone = ZoneId.of("Asia/Shanghai"),
    )

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
    fun aDueSessionStopsAtTwentyWordsAndKeepsTheMostOverdue() = runBlocking {
        val (service, words, logs) = parts()
        val ids = (1..25).map { index -> seedDue(words, logs, "w$index", daysOverdue = index.toLong()) }

        val session = service.planDue(now, dailyNewLimit = 10, seed = 7)

        assertEquals(QuizService.SESSION_LIMIT, session.questions.size)
        assertEquals("逾期最久的 20 个留下，剩下 5 个明天再考", ids.drop(5).toSet(), session.wordIds)
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

    @Test
    fun aWordIsAskedOfTheModelOnceAndReadFromTheTableAfterThat() = runBlocking {
        val words = FakeWordDao()
        val logs = FakeReviewLogDao()
        val store = FakeQuizMaterialDao()
        val asked = mutableListOf<Long>()
        val service = service(
            words,
            logs,
            FakeTrickyDao(),
            RecordingEnricher(asked),
            QuizMaterialStore(store),
        )
        val due = seedDue(words, logs, "harbour")

        val first = service.planDue(now, dailyNewLimit = 10, seed = 1)
        val second = service.planDue(now, dailyNewLimit = 10, seed = 2)

        assertEquals("第二次出题一个字都不该再问", listOf(due), asked)
        assertEquals(1, store.items.size)
        second.questions.forEach { question ->
            assertEquals(1, question.options.count { it.correct })
            assertTrue(question.options.size >= 2)
        }
        assertEquals(first.questions.single().wordId, second.questions.single().wordId)
    }

    @Test
    fun nothingComeBackFromTheModelMeansTheWordIsAskedAgain() = runBlocking {
        val words = FakeWordDao()
        val logs = FakeReviewLogDao()
        val store = FakeQuizMaterialDao()
        val asked = mutableListOf<Long>()
        val service = service(
            words,
            logs,
            FakeTrickyDao(),
            RecordingEnricher(asked, answerWith = emptyList()),
            QuizMaterialStore(store),
        )
        seedDue(words, logs, "harbour")

        service.planDue(now, dailyNewLimit = 10, seed = 1)
        service.planDue(now, dailyNewLimit = 10, seed = 2)

        assertTrue(store.items.isEmpty())
        assertEquals(2, asked.count())
    }

    @Test
    fun aConfusableTheUserMarkedOutranksTheStoredPool() = runBlocking {
        val words = FakeWordDao()
        val logs = FakeReviewLogDao()
        val tricky = FakeTrickyDao()
        tricky.insertLink(TrickyLinkEntity(wordA = "harbour", wordB = "harbor", createdAtSec = 0))
        tricky.insertIgnore(TrickyWordEntity(word = "harbor", gloss = "停泊处", createdAtSec = 0))
        val asked = mutableListOf<Long>()
        val service = service(
            words,
            logs,
            tricky,
            RecordingEnricher(asked),
            QuizMaterialStore(FakeQuizMaterialDao()),
        )
        seedDue(words, logs, "harbour")

        val question = service.planDue(now, dailyNewLimit = 10, seed = 1).questions.single()

        assertTrue(
            "用户亲手标的易混词必须出现在选项里",
            question.options.any { it.text == "停泊处" || it.gloss == "停泊处" },
        )
    }

    /** 记下模型被问过哪些词——落库之后不该再有第二次。 */
    private class RecordingEnricher(
        private val asked: MutableList<Long>,
        private val answerWith: List<EnrichedDistractor> = (1..6).map { EnrichedDistractor("dock$it", "释义$it") },
    ) : QuizEnricher {
        override suspend fun enrich(words: List<WordEntity>): QuizEnrichment {
            asked += words.map { it.id }
            if (answerWith.isEmpty()) return QuizEnrichment()
            return QuizEnrichment(
                words.associate { it.id to EnrichedWord("a place where ships stay", "船进了 harbour。", answerWith) },
            )
        }
    }

    private suspend fun seedDue(
        words: FakeWordDao,
        logs: FakeReviewLogDao,
        name: String,
        daysOverdue: Long = 1,
    ): Long {
        val id = words.insert(
            word(name).copy(
                stability = 5.0,
                difficulty = 6.0,
                dueEpochSec = now.minus(daysOverdue, ChronoUnit.DAYS).epochSecond,
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
