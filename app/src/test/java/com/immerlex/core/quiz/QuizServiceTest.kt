package com.immerlex.core.quiz

import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.LOG_SOURCE_QUIZ
import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.TrickyLinkEntity
import com.immerlex.core.db.TrickyWordEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.core.schedule.DayBoundary
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.testsupport.FakeChapterDao
import com.immerlex.testsupport.FakeDailyLoopDao
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
        boundary = DayBoundary(FakeDailyLoopDao(), logs, ZoneId.of("Asia/Shanghai")),
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
    fun aDueSessionStopsAtFortyWordsAndKeepsTheMostOverdue() = runBlocking {
        val (service, words, logs) = parts()
        val ids = (1..45).map { index -> seedDue(words, logs, "w$index", daysOverdue = index.toLong()) }

        val session = service.planDue(now, dailyNewLimit = 10, seed = 7)

        assertEquals(QuizService.SESSION_LIMIT, session.questions.size)
        assertEquals("逾期最久的 40 个留下，剩下一组再考", ids.drop(5).toSet(), session.wordIds)
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
        assertEquals(LOG_SOURCE_QUIZ, written.source)
        assertEquals(question.type.key, written.questionType)
        assertEquals(Rating.GOOD.value, written.rating)
    }

    @Test
    fun earlyGroupsTakeTheWordsClosestToDueAndCycleWhenFewRemain() = runBlocking {
        val (service, words, logs) = parts()
        // 七个都学过、都还没到期：只有到期日远近能分先后。
        val byDue = (1..7).map { index -> seedFuture(words, logs, "w$index", daysAhead = index.toLong()) }

        val first = service.planEarly(now, excludeWordIds = emptySet(), seed = 3)
        assertEquals(5, first.questions.size)
        assertEquals("最近到期的 5 个", byDue.take(5).toSet(), first.wordIds)

        val second = service.planEarly(now, excludeWordIds = first.wordIds, seed = 4)
        assertEquals(5, second.questions.size)
        assertTrue("剩下没练过的两个必须进下一组", second.wordIds.containsAll(byDue.drop(5)))
        assertTrue(second.wordIds.any { it in first.wordIds })

        val third = service.planEarly(now, excludeWordIds = byDue.toSet(), seed = 5)
        assertEquals("全练过一遍后循环出题，不再有每日上限", 5, third.questions.size)
    }

    @Test
    fun earlyGroupSizeFollowsTheChosenTier() = runBlocking {
        val (service, words, logs) = parts()
        val byDue = (1..22).map { index -> seedFuture(words, logs, "w$index", daysAhead = index.toLong()) }

        val ten = service.planEarly(now, excludeWordIds = emptySet(), groupSize = 10, seed = 3)
        assertEquals(10, ten.questions.size)
        assertEquals("最近到期的 10 个", byDue.take(10).toSet(), ten.wordIds)

        val twenty = service.planEarly(now, excludeWordIds = emptySet(), groupSize = 20, seed = 4)
        assertEquals(20, twenty.questions.size)
        assertEquals("最近到期的 20 个", byDue.take(20).toSet(), twenty.wordIds)
    }

    @Test
    fun aLargerGroupTopsUpFromAlreadyPractisedWordsWhenFewRemain() = runBlocking {
        val (service, words, logs) = parts()
        val byDue = (1..7).map { index -> seedFuture(words, logs, "w$index", daysAhead = index.toLong()) }

        val session = service.planEarly(now, excludeWordIds = byDue.toSet(), groupSize = 10, seed = 5)

        assertEquals("凑不满十个别空转，循环拿练过的补", 7, session.questions.size)
    }

    @Test
    fun anUnknownEarlyGroupSizeFallsBackToFive() = runBlocking {
        val (service, words, logs) = parts()
        (1..8).forEach { index -> seedFuture(words, logs, "w$index", daysAhead = index.toLong()) }

        val session = service.planEarly(now, excludeWordIds = emptySet(), groupSize = 12, seed = 3)

        assertEquals(EarlyReview.DEFAULT_GROUP_SIZE, session.questions.size)
    }

    @Test
    fun earlyCandidatesTakeAnyLearnedWordNotYetDueEvenIfUntouchedToday() = runBlocking {
        val (service, words, logs) = parts()
        // 三天前学的、今天没碰过、也还没到期——提前复习照样认得它。
        val older = seedFuture(words, logs, "harbour", daysAhead = 9, learnedAt = now.minus(3, ChronoUnit.DAYS))
        words.insert(word("balcony"))
        val due = seedDue(words, logs, "pebble")

        val candidates = service.earlyCandidates(now).map { it.id }

        assertEquals(listOf(older), candidates)
        assertFalse(due in candidates)
    }

    @Test
    fun earlyCandidatesSkipWordsRecalledTodayButKeepOnesLearnedToday() = runBlocking {
        val (service, words, logs) = parts()
        val recalled = seedFuture(words, logs, "harbour", daysAhead = 2)
        logs.insert(log(recalled, LOG_KIND_RECALL, now.minusSeconds(600)).copy(source = LOG_SOURCE_QUIZ))
        val learnedToday = seedFuture(words, logs, "lantern", daysAhead = 5)

        val candidates = service.earlyCandidates(now).map { it.id }

        assertEquals(listOf(learnedToday), candidates)
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
    fun thePrepReportCountsWordsTheTableAlreadyCovers() = runBlocking {
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
        seedDue(words, logs, "harbour")
        val missing = seedDue(words, logs, "pebble")
        store.items["harbour"] = material("harbour", "[]")
        var report: QuizMaterialPrep? = null

        service.planDue(now, dailyNewLimit = 10, seed = 1, onMaterial = { report = it })

        assertEquals(QuizMaterialPrep(ready = 1, toAsk = 1, canAsk = true), report)
        assertEquals("只有没素材的那个去问模型", listOf(missing), asked)
    }

    @Test
    fun withoutAKeyThePrepReportDoesNotPromiseAnAiRequest() = runBlocking {
        val words = FakeWordDao()
        val logs = FakeReviewLogDao()
        val service = service(
            words,
            logs,
            FakeTrickyDao(),
            enricher = AiQuizEnricher(clientFor = { error("没配 key 不该发请求") }, aiUsable = { false }),
        )
        seedDue(words, logs, "harbour")
        var report: QuizMaterialPrep? = null

        service.planDue(now, dailyNewLimit = 10, seed = 1, onMaterial = { report = it })

        assertEquals(QuizMaterialPrep(ready = 0, toAsk = 1, canAsk = false), report)
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

    @Test
    fun aStoredCollocationBecomesAPhraseQuestionOnceTheWordIsFarEnoughAlong() = runBlocking {
        val words = FakeWordDao()
        val logs = FakeReviewLogDao()
        val store = FakeQuizMaterialDao()
        val harbour = words.insert(
            word("harbour").copy(
                translation = "",
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
            log(harbour, LOG_KIND_RECALL, now.minus(3, ChronoUnit.DAYS))
                .copy(questionType = QuizType.CLOZE.key),
        )
        logs.insert(
            log(harbour, LOG_KIND_RECALL, now.minus(2, ChronoUnit.DAYS))
                .copy(questionType = QuizType.ZH_EN.key),
        )
        words.insert(word("take"))
        words.insert(word("draw"))
        words.insert(word("pay"))
        store.items["harbour"] = material("harbour", """[{"w":"harbour wall","zh":"防波堤"}]""")
        store.items["take"] = material("take", """[{"w":"take measures","zh":"采取措施"}]""")
        store.items["draw"] = material("draw", """[{"w":"draw a conclusion","zh":"下结论"}]""")
        store.items["pay"] = material("pay", """[{"w":"pay attention","zh":"注意"}]""")
        val service = service(words, logs, FakeTrickyDao(), material = QuizMaterialStore(store))

        // 这个词没有译文、释义和例句，前四种题型都出不了，只剩搭配题。
        val question = service.planDue(now, dailyNewLimit = 10, seed = 5).questions.single()

        assertEquals(QuizType.COLLOCATION, question.type)
        assertEquals("题干是这个自己的短语，选项问它的意思", "harbour wall", question.prompt)
        assertEquals("防波堤", question.options.first { it.correct }.text)
        assertEquals(4, question.options.size)
        assertTrue(
            "选项全是释义，不能靠在里面找这个词选答案",
            question.options.none { it.text.contains("harbour", ignoreCase = true) },
        )
        assertTrue("每个选项都能揭开自己原本的短语", question.options.all { it.gloss.isNotBlank() })
    }

    private fun material(word: String, collocations: String) =
        QuizMaterialEntity(word = word, collocations = collocations, updatedAtSec = 1)

    /** 记下模型被问过哪些词——落库之后不该再有第二次。 */
    private class RecordingEnricher(
        private val asked: MutableList<Long>,
        private val answerWith: List<EnrichedDistractor> = (1..6).map { EnrichedDistractor("dock$it", "释义$it") },
    ) : QuizEnricher {
        override suspend fun enrich(words: List<WordEntity>): QuizEnrichment {
            asked += words.map { it.id }
            if (answerWith.isEmpty()) return QuizEnrichment()
            return QuizEnrichment(
                words.associate { it.id to EnrichedWord("a place where ships stay", "船进了 harbour。", distractors = answerWith) },
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
        stability: Double = 2.0,
        daysAhead: Long = 3,
        learnedAt: Instant = now.minusSeconds(3600),
    ): Long {
        val id = words.insert(
            word(name).copy(
                stability = stability,
                difficulty = 6.0,
                dueEpochSec = now.plus(daysAhead, ChronoUnit.DAYS).epochSecond,
                lastReviewEpochSec = now.minusSeconds(3600).epochSecond,
                fsrsState = FSRS_STATE_REVIEW,
                reps = 1,
                intervalDays = daysAhead.toInt(),
            ),
        )
        logs.insert(log(id, LOG_KIND_LEARN, learnedAt))
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
