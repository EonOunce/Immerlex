package com.immerlex.core.schedule

import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.testsupport.FakeReviewLogDao
import com.immerlex.testsupport.FakeWordDao
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleServiceTest {

    private fun service() = FakeWordDao().let { words ->
        val logs = FakeReviewLogDao()
        ScheduleService(
            wordDao = words,
            logDao = logs,
            scheduler = Scheduler(
                learningSteps = emptyList(),
                relearningSteps = emptyList(),
                maximumInterval = 3650,
                enableFuzzing = false,
            ),
        ) to Pair(words, logs)
    }

    @Test
    fun firstGoodReviewGraduatesToTwoDays() = runBlocking {
        val (svc, parts) = service()
        val (words, logs) = parts
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
        val (svc, parts) = service()
        val (words, _) = parts
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
        val (svc, parts) = service()
        val (words, _) = parts
        repeat(5) { words.insert(WordEntity(word = "w$it", source = "test", createdAtSec = 0)) }
        val now = Instant.parse("2024-06-01T00:00:00Z")

        val queue = svc.todaysQueue(now, dailyNewLimit = 2)

        assertEquals(2, queue.newWords.size)
        assertEquals(0, queue.reviewWords.size)
    }
}
