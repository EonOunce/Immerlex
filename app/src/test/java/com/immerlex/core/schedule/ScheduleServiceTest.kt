package com.immerlex.core.schedule

import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.testsupport.FakeReviewLogDao
import com.immerlex.testsupport.FakeWordDao
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleServiceTest {

    private fun parts(zone: ZoneId = UTC) = FakeWordDao().let { words ->
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
                zone = zone,
            ),
            words,
            logs,
        )
    }

    @Test
    fun firstGoodReviewGraduatesToTwoDays() = runBlocking {
        val (svc, words, logs) = parts()
        val id = words.insert(WordEntity(word = "resist", source = "test", createdAtSec = 0))
        val now = Instant.parse("2024-01-01T00:00:00Z")

        val updated = svc.review(words.findById(id)!!, Rating.GOOD, "card", now)

        // Matches FSRS reference vector: new + Good -> interval 2 days.
        assertEquals(2, updated.intervalDays)
        assertEquals(now.plus(2, ChronoUnit.DAYS).epochSecond, updated.dueEpochSec)
        assertEquals(1, updated.reps)
        assertEquals(0, updated.lapses)
        assertEquals(1, logs.items.size)
    }

    @Test
    fun againCountsLapseAndKeepsWordDue() = runBlocking {
        val (svc, words, _) = parts()
        val id = words.insert(WordEntity(word = "bin", source = "test", createdAtSec = 0))
        val t0 = Instant.parse("2024-01-01T00:00:00Z")
        var w = svc.review(words.findById(id)!!, Rating.AGAIN, "card", t0)

        assertEquals(1, w.lapses)
        assertEquals(1, w.intervalDays) // clamped to minimum 1 day

        val t1 = t0.plus(1, ChronoUnit.DAYS)
        w = svc.review(w, Rating.GOOD, "card", t1)
        assertEquals(1, w.lapses)
        assertEquals(2, w.reps)
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

    @Test
    fun fiveTapsOnOneDueWordRecordOneReview() = runBlocking {
        val (svc, words, logs) = parts()
        val id = seed(words, "memory")
        val now = Instant.parse("2024-03-01T10:00:00Z")

        repeat(5) { svc.grade(words.findById(id)!!, Rating.GOOD, SOURCE, now) }

        assertEquals(1, logs.items.size)
        assertEquals(1, words.findById(id)!!.reps)
    }

    @Test
    fun amendLandsExactlyWhereTheNewRatingWouldHavePutTheCard() = runBlocking {
        val (svc, words, logs) = parts()
        val id = seed(words, "column")
        val now = Instant.parse("2024-03-01T10:00:00Z")
        svc.grade(words.findById(id)!!, Rating.GOOD, SOURCE, now)
        val amended = svc.grade(words.findById(id)!!, Rating.HARD, SOURCE, now).word

        val (other, otherWords, _) = parts()
        val otherId = seed(otherWords, "column")
        val straight = other.grade(otherWords.findById(otherId)!!, Rating.HARD, SOURCE, now).word

        assertEquals(GradeMode.Amend, svc.gradeModeFor(words.findById(id)!!, now))
        assertEquals("改分后的卡片必须与一开始就选这个分一致", straight, amended)
        assertEquals(1, logs.items.size)
        assertEquals(Rating.HARD.value, logs.items.single().rating)
        assertEquals(straight.intervalDays, logs.items.single().scheduledDays)
    }

    @Test
    fun againThenLearnedBackCountsTwoReviews() = runBlocking {
        val (svc, words, logs) = parts()
        val id = seed(words, "ledger")
        val now = Instant.parse("2024-03-01T10:00:00Z")

        assertEquals(GradeMode.Review, svc.grade(words.findById(id)!!, Rating.AGAIN, SOURCE, now).mode)
        val relearned = svc.grade(words.findById(id)!!, Rating.GOOD, SOURCE, now)
        assertEquals(GradeMode.Relearn, relearned.mode)

        assertEquals(2, logs.items.size)
        assertEquals(2, relearned.word.reps)
        assertEquals(1, relearned.word.lapses)
    }

    @Test
    fun thirdTapOfTheDayAmendsInsteadOfRecording() = runBlocking {
        val (svc, words, logs) = parts()
        val id = seed(words, "quay")
        val now = Instant.parse("2024-03-01T10:00:00Z")
        svc.grade(words.findById(id)!!, Rating.AGAIN, SOURCE, now)
        svc.grade(words.findById(id)!!, Rating.GOOD, SOURCE, now)

        val third = svc.grade(words.findById(id)!!, Rating.HARD, SOURCE, now)

        assertEquals(GradeMode.Amend, third.mode)
        assertEquals("同日封顶两条", 2, logs.items.size)
        assertEquals(2, third.word.reps)
        assertEquals(Rating.HARD.value, logs.items.last().rating)
    }

    @Test
    fun wordNotDueYetIsRevealedWithoutScoring() = runBlocking {
        val (svc, words, logs) = parts()
        val id = seed(words, "harbour", dueInDays = 4)

        val result = svc.grade(words.findById(id)!!, Rating.GOOD, SOURCE, Instant.parse("2024-03-01T10:00:00Z"))

        assertEquals(GradeMode.Reveal, result.mode)
        assertEquals(0, logs.items.size)
        assertEquals(words.findById(id)!!, result.word)
    }

    @Test
    fun v1LogWithoutSnapshotIsNotAmendable() = runBlocking {
        val (svc, words, logs) = parts()
        val id = seed(words, "kiln")
        val now = Instant.parse("2024-03-01T10:00:00Z")
        // 升级前的老记录：只有评分，没有评前快照。
        logs.insert(
            ReviewLogEntity(
                wordId = id, rating = Rating.GOOD.value, source = SOURCE,
                reviewEpochSec = now.epochSecond, elapsedDays = 0, scheduledDays = 2,
            ),
        )
        val notYetDue = words.findById(id)!!.copy(dueEpochSec = now.plus(1, ChronoUnit.DAYS).epochSecond)
        words.update(notYetDue)

        val result = svc.grade(words.findById(id)!!, Rating.HARD, SOURCE, now)

        assertEquals("没有快照就回滚不了，只能不算分", GradeMode.Reveal, result.mode)
        assertEquals(1, logs.items.size)
        assertEquals(Rating.GOOD.value, logs.items.single().rating)
    }

    private suspend fun seed(words: FakeWordDao, word: String, dueInDays: Long? = null): Long {
        val now = Instant.parse("2024-03-01T10:00:00Z")
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
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
