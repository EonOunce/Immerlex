package com.immerlex.core.schedule

import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.testsupport.FakeReviewLogDao
import com.immerlex.testsupport.FakeWordDao
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleServiceTest {

    private fun parts() = FakeWordDao().let { words ->
        val logs = FakeReviewLogDao()
        Triple(
            ScheduleService(
                wordDao = words,
                logDao = logs,
                scheduler = Scheduler(
                    learningSteps = emptyList(),
                    relearningSteps = emptyList(),
                    maximumInterval = 3650,
                    enableFuzzing = false,
                ),
            ),
            words,
            logs,
        )
    }

    @Test
    fun firstExposureIsLearnInBothModes() = runBlocking {
        val (svc, words, _) = parts()
        val id = words.insert(WordEntity(word = "resist", source = "test", createdAtSec = 0))
        val now = Instant.parse("2024-01-01T00:00:00Z")

        assertEquals(WordAccess.Learn, svc.accessFor(words.findById(id)!!, answersHidden = false, now))
        assertEquals(WordAccess.Learn, svc.accessFor(words.findById(id)!!, answersHidden = true, now))
    }

    @Test
    fun learnStartsTheScheduleWithoutCountingAsRecall() = runBlocking {
        val (svc, words, logs) = parts()
        val id = words.insert(WordEntity(word = "resist", source = "test", createdAtSec = 0))
        val now = Instant.parse("2024-01-01T00:00:00Z")

        val result = svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)!!

        // Matches FSRS reference vector: new + Good -> interval 2 days.
        assertEquals(2, result.word.intervalDays)
        assertEquals(now.plus(2, ChronoUnit.DAYS).epochSecond, result.word.dueEpochSec)
        assertEquals(1, result.word.reps)
        assertEquals(LOG_KIND_LEARN, result.kind)
        assertEquals(LOG_KIND_LEARN, logs.items.single().kind)
        assertNull("阅读页没有题型", logs.items.single().questionType)
    }

    @Test
    fun secondLookIsNotLearnedAgain() = runBlocking {
        val (svc, words, logs) = parts()
        val id = words.insert(WordEntity(word = "column", source = "test", createdAtSec = 0))
        val now = Instant.parse("2024-01-01T00:00:00Z")
        svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        assertNull(svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now.plusSeconds(5)))
        assertEquals(1, logs.items.size)
    }

    @Test
    fun learningModeNeverScoresAfterTheFirstLook() = runBlocking {
        val (svc, words, _) = parts()
        val id = words.insert(WordEntity(word = "bin", source = "test", createdAtSec = 0))
        val now = Instant.parse("2024-01-01T00:00:00Z")
        svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        assertEquals(WordAccess.ViewOnly, svc.accessFor(words.findById(id)!!, answersHidden = false, now))
    }

    @Test
    fun reviewModeGradesAllRatingsWhenDueAndOnlyTheForgottenOneBefore() = runBlocking {
        val (svc, words, _) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = words.insert(WordEntity(word = "bin", source = "test", createdAtSec = 0))
        svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        assertEquals(WordAccess.Early, svc.accessFor(words.findById(id)!!, answersHidden = true, now))
        val later = now.plus(2, ChronoUnit.DAYS)
        assertEquals(WordAccess.Recall, svc.accessFor(words.findById(id)!!, answersHidden = true, later))
    }

    @Test
    fun notDueWordCannotBeRecalledUnlessEarlyIsAllowed() = runBlocking {
        val (svc, words, logs) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = seed(words, "harbour", dueInDays = 4)
        logs.insert(
            ReviewLogEntity(
                wordId = id, rating = Rating.GOOD.value, source = SOURCE,
                reviewEpochSec = now.minus(1, ChronoUnit.DAYS).epochSecond,
                elapsedDays = 0, scheduledDays = 4,
                preState = FSRS_STATE_REVIEW,
                preDueEpochSec = now.plus(4, ChronoUnit.DAYS).epochSecond,
            ),
        )

        assertNull(svc.recall(words.findById(id)!!, Rating.GOOD, SOURCE, now = now))
        assertEquals(1, logs.items.size)

        val early = svc.recall(
            words.findById(id)!!,
            Rating.HARD,
            SOURCE,
            questionType = "CLOZE",
            now = now,
            allowEarly = true,
        )
        assertEquals(LOG_KIND_RECALL, early!!.kind)
        assertEquals("CLOZE", logs.items.last().questionType)
    }

    @Test
    fun twoRatingsInTheSameInstantRecordOnce() = runBlocking {
        val (svc, words, logs) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = words.insert(WordEntity(word = "memory", source = "test", createdAtSec = 0))
        svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        val at = now.plus(2, ChronoUnit.DAYS)
        val first = svc.recall(words.findById(id)!!, Rating.GOOD, SOURCE, now = at)
        val second = svc.recall(words.findById(id)!!, Rating.GOOD, SOURCE, now = at)

        assertTrue(first != null)
        assertNull("连点手滑不能多记一次", second)
        assertEquals(2, logs.items.size)
        assertEquals(2, words.findById(id)!!.reps)
    }

    @Test
    fun repeatedIndependentRecallsAreAllKept() = runBlocking {
        val (svc, words, logs) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = words.insert(WordEntity(word = "ledger", source = "test", createdAtSec = 0))
        svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        val day1 = now.plus(2, ChronoUnit.DAYS)
        svc.recall(words.findById(id)!!, Rating.AGAIN, SOURCE, now = day1)
        val day2 = day1.plus(1, ChronoUnit.DAYS)
        svc.recall(words.findById(id)!!, Rating.GOOD, SOURCE, now = day2)

        assertEquals(3, logs.items.size)
        assertEquals(listOf(LOG_KIND_LEARN, LOG_KIND_RECALL, LOG_KIND_RECALL), logs.items.map { it.kind })
        assertEquals("每一次都参与排期", 3, words.findById(id)!!.reps)
        assertEquals(1, words.findById(id)!!.lapses)
    }

    @Test
    fun undoRestoresThePreRatingCardAndDeletesTheLog() = runBlocking {
        val (svc, words, logs) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = words.insert(WordEntity(word = "quay", source = "test", createdAtSec = 0))
        val learned = svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)!!.word

        val at = now.plus(2, ChronoUnit.DAYS)
        val result = svc.recall(words.findById(id)!!, Rating.AGAIN, SOURCE, now = at)!!
        val restored = svc.undo(result.logId, at.plusSeconds(3))!!

        assertEquals("撤销后必须与评分前完全一致", learned, restored.word)
        assertEquals(LOG_KIND_RECALL, restored.kind)
        assertEquals(learned, words.findById(id)!!)
        assertEquals(1, logs.items.size)
        assertFalse(logs.items.any { it.id == result.logId })
    }

    @Test
    fun undoOnlyWorksForTheLastLogInsideTheWindow() = runBlocking {
        val (svc, words, logs) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = words.insert(WordEntity(word = "kiln", source = "test", createdAtSec = 0))
        svc.learn(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        val at = now.plus(2, ChronoUnit.DAYS)
        val first = svc.recall(words.findById(id)!!, Rating.GOOD, SOURCE, now = at)!!
        val second = svc.recall(
            words.findById(id)!!,
            Rating.HARD,
            SOURCE,
            now = at.plusSeconds(3),
            allowEarly = true,
        )!!

        assertNull("只允许撤最后一条", svc.undo(first.logId, at.plusSeconds(4)))
        assertNull("窗口过了就是真实记录", svc.undo(second.logId, at.plusSeconds(3 + 9)))
        assertEquals(3, logs.items.size)
    }

    @Test
    fun v1LogWithoutSnapshotCannotBeUndone() = runBlocking {
        val (svc, words, logs) = parts()
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val id = seed(words, "resin")
        // 升级前的老记录：只有评分，没有评前快照。
        val legacyId = logs.insert(
            ReviewLogEntity(
                wordId = id, rating = Rating.GOOD.value, source = SOURCE,
                reviewEpochSec = now.epochSecond, elapsedDays = 0, scheduledDays = 2,
            ),
        )

        assertNull(svc.undo(legacyId, now.plusSeconds(1)))
        assertEquals(1, logs.items.size)
    }

    @Test
    fun todaysQueueCapsNewWords() = runBlocking {
        val (svc, words, _) = parts()
        repeat(5) { words.insert(WordEntity(word = "w$it", source = "test", createdAtSec = 0)) }
        val now = Instant.parse("2024-06-01T00:00:00Z")

        val queue = svc.todaysQueue(now, dailyNewLimit = 2)

        assertEquals(2, queue.newWords.size)
        assertEquals(0, queue.reviewWords.size)
    }

    private suspend fun seed(words: FakeWordDao, word: String, dueInDays: Long? = null): Long {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        return words.insert(
            WordEntity(
                word = word,
                source = "test",
                createdAtSec = 0,
                stability = dueInDays?.let { 5.0 },
                difficulty = dueInDays?.let { 6.0 },
                dueEpochSec = dueInDays?.let { now.plus(it, ChronoUnit.DAYS).epochSecond },
                lastReviewEpochSec = dueInDays?.let { now.minus(1, ChronoUnit.DAYS).epochSecond },
                fsrsState = if (dueInDays == null) 0 else FSRS_STATE_REVIEW,
                reps = if (dueInDays == null) 0 else 1,
                intervalDays = dueInDays?.toInt() ?: 0,
            ),
        )
    }

    private companion object {
        const val SOURCE = "reader"
    }
}
