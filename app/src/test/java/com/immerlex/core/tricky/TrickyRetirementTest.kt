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
    fun correctRecognitionsSinceTheMarkAccumulateIntoThePoints() {
        val marked = 1_000L
        val logs = listOf(log(Rating.GOOD.value, marked + 10), log(Rating.GOOD.value, marked + 20))

        assertEquals(2, TrickyRetirement.correctPointsSinceMarked(logs, marked))
    }

    @Test
    fun aWrongAnswerEndsThePointsEvenWithLongerHistoryBehindIt() {
        val marked = 1_000L
        val logs = List(5) { log(Rating.GOOD.value, marked + 10L + it) } +
            log(Rating.AGAIN.value, marked + 100) +
            log(Rating.GOOD.value, marked + 200)

        assertEquals(1, TrickyRetirement.correctPointsSinceMarked(logs, marked))
    }

    @Test
    fun recordsFromBeforeTheMarkAreNotCountedAsEvidence() {
        val marked = 1_000L
        val logs = List(9) { log(Rating.GOOD.value, it.toLong()) } + log(Rating.GOOD.value, marked + 5)

        assertEquals(1, TrickyRetirement.correctPointsSinceMarked(logs, marked))
    }

    @Test
    fun aClozeOrDefinitionAnswerCountsDoubleLikeItDoesAtTheGate() {
        val marked = 1_000L
        val logs = listOf(
            log(Rating.GOOD.value, marked + 10, "CLOZE"),
            log(Rating.GOOD.value, marked + 20, "DEFINITION"),
        )

        assertEquals("挖空与释义选词各算 2 分", 4, TrickyRetirement.correctPointsSinceMarked(logs, marked))
    }

    @Test
    fun thePromptFiresExactlyAtSixPointsAndNotOneEarlier() {
        val marked = 1_000L
        val five = List(5) { log(Rating.GOOD.value, marked + it.toLong()) }
        val six = five + log(Rating.GOOD.value, marked + 5)

        assertFalse(TrickyRetirement.shouldRetire(TrickyRetirement.correctPointsSinceMarked(five, marked)))
        assertTrue(TrickyRetirement.shouldRetire(TrickyRetirement.correctPointsSinceMarked(six, marked)))

        val threeHeavy = List(3) { log(Rating.GOOD.value, marked + it.toLong(), "CLOZE") }
        assertTrue("三次挖空就够 6 分", TrickyRetirement.shouldRetire(TrickyRetirement.correctPointsSinceMarked(threeHeavy, marked)))
    }

    private fun log(rating: Int, atSec: Long, type: String? = null) = ReviewLogEntity(
        wordId = 1,
        rating = rating,
        source = "quiz",
        reviewEpochSec = atSec,
        elapsedDays = 0,
        scheduledDays = 3,
        kind = if (rating == Rating.AGAIN.value) LOG_KIND_RECALL else LOG_KIND_ATTEMPT,
        questionType = type,
    )
}
