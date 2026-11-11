package com.immerlex.core.tricky

import com.immerlex.core.db.LOG_KIND_ATTEMPT
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrickyRetirementTest {

    @Test
    fun correctRecognitionsSinceTheMarkAccumulateIntoTheStreak() {
        val marked = 1_000L
        val logs = listOf(log(Rating.GOOD.value, marked + 10), log(Rating.GOOD.value, marked + 20))

        assertEquals(2, TrickyRetirement.correctStreakSinceMarked(logs, marked))
    }

    @Test
    fun aWrongAnswerEndsTheStreakEvenWithLongerHistoryBehindIt() {
        val marked = 1_000L
        val logs = List(5) { log(Rating.GOOD.value, marked + 10L + it) } +
            log(Rating.AGAIN.value, marked + 100) +
            log(Rating.GOOD.value, marked + 200)

        assertEquals(1, TrickyRetirement.correctStreakSinceMarked(logs, marked))
    }

    @Test
    fun recordsFromBeforeTheMarkAreNotCountedAsEvidence() {
        val marked = 1_000L
        val logs = List(9) { log(Rating.GOOD.value, it.toLong()) } + log(Rating.GOOD.value, marked + 5)

        assertEquals(1, TrickyRetirement.correctStreakSinceMarked(logs, marked))
    }

    @Test
    fun thePromptFiresExactlyAtSixAndNotOneEarlier() {
        val marked = 1_000L
        val five = List(5) { log(Rating.GOOD.value, marked + it.toLong()) }
        val six = five + log(Rating.GOOD.value, marked + 5)

        assertFalse(TrickyRetirement.shouldRetire(TrickyRetirement.correctStreakSinceMarked(five, marked)))
        assertTrue(TrickyRetirement.shouldRetire(TrickyRetirement.correctStreakSinceMarked(six, marked)))
    }

    private fun log(rating: Int, atSec: Long) = ReviewLogEntity(
        wordId = 1,
        rating = rating,
        source = "quiz",
        reviewEpochSec = atSec,
        elapsedDays = 0,
        scheduledDays = 3,
        kind = if (rating == Rating.AGAIN.value) LOG_KIND_RECALL else LOG_KIND_ATTEMPT,
    )
}
