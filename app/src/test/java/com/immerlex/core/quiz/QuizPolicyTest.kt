package com.immerlex.core.quiz

import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizPolicyTest {

    @Test
    fun newWordOnlyGetsBaseTypes() {
        val types = QuizPolicy.availableTypes(emptyList(), tricky = false)

        assertEquals(listOf(QuizType.EN_ZH, QuizType.CLOZE), types)
        assertEquals(0, QuizPolicy.unlockedTier(emptyList()))
    }

    @Test
    fun learnLogsAreNotRecallAndDoNotUnlockAnything() {
        val logs = listOf(log(rating = Rating.GOOD, kind = LOG_KIND_LEARN, type = QuizType.CLOZE))

        assertEquals(0, QuizPolicy.unlockedTier(logs))
        assertFalse(QuizType.ZH_EN in QuizPolicy.availableTypes(logs, tricky = false))
    }

    @Test
    fun onePassingRecallUnlocksZhToEn() {
        val logs = listOf(log(Rating.GOOD, LOG_KIND_RECALL, QuizType.CLOZE))

        assertEquals(1, QuizPolicy.unlockedTier(logs))
        assertTrue(QuizType.ZH_EN in QuizPolicy.availableTypes(logs, tricky = false))
        assertFalse(QuizType.DEFINITION in QuizPolicy.availableTypes(logs, tricky = false))
    }

    @Test
    fun passingZhToEnUnlocksDefinition() {
        val logs = listOf(
            log(Rating.GOOD, LOG_KIND_RECALL, QuizType.CLOZE, at = 100),
            log(Rating.GOOD, LOG_KIND_RECALL, QuizType.ZH_EN, at = 200),
        )

        assertEquals(2, QuizPolicy.unlockedTier(logs))
        assertEquals(QuizType.entries.toList(), QuizPolicy.availableTypes(logs, tricky = false))
    }

    @Test
    fun aWrongAnswerFallsBackToBaseTypesAndAvoidsThatType() {
        val logs = listOf(
            log(Rating.GOOD, LOG_KIND_RECALL, QuizType.CLOZE, at = 100),
            log(Rating.GOOD, LOG_KIND_RECALL, QuizType.ZH_EN, at = 200),
            log(Rating.AGAIN, LOG_KIND_RECALL, QuizType.CLOZE, at = 300),
        )

        assertEquals(QuizType.CLOZE, QuizPolicy.lastFailedType(logs))
        assertEquals(listOf(QuizType.EN_ZH, QuizType.CLOZE), QuizPolicy.availableTypes(logs, tricky = false))
        assertEquals(
            "刚答错的题型排在最后",
            QuizType.EN_ZH,
            QuizPolicy.orderedTypes(1, logs, tricky = false, seed = 7).first(),
        )
    }

    @Test
    fun trickyWordsSeeEveryTypeOnlyWhileTheLastRecallPassed() {
        val passed = listOf(log(Rating.GOOD, LOG_KIND_RECALL, QuizType.EN_ZH))
        assertEquals(QuizType.entries.toList(), QuizPolicy.availableTypes(passed, tricky = true))

        val failed = passed + log(Rating.AGAIN, LOG_KIND_RECALL, QuizType.EN_ZH, at = 200)
        assertEquals(listOf(QuizType.EN_ZH, QuizType.CLOZE), QuizPolicy.availableTypes(failed, tricky = true))
    }

    @Test
    fun weakestTypeComesFirstAndStrongTypesGoLast() {
        val logs = listOf(
            log(Rating.AGAIN, LOG_KIND_RECALL, QuizType.EN_ZH, at = 100),
            log(Rating.AGAIN, LOG_KIND_RECALL, QuizType.EN_ZH, at = 200),
            log(Rating.GOOD, LOG_KIND_RECALL, QuizType.CLOZE, at = 300),
            log(Rating.GOOD, LOG_KIND_RECALL, QuizType.CLOZE, at = 400),
        )

        val ordered = QuizPolicy.orderedTypes(42, logs, tricky = false, seed = 0)

        assertEquals("成功率最低的先考", QuizType.EN_ZH, ordered.first())
        assertTrue(ordered.indexOf(QuizType.CLOZE) > ordered.indexOf(QuizType.ZH_EN))
    }

    @Test
    fun orderingIsDeterministicForTheSameSeed() {
        val logs = emptyList<ReviewLogEntity>()

        val first = QuizPolicy.orderedTypes(9, logs, tricky = false, seed = 123)
        val second = QuizPolicy.orderedTypes(9, logs, tricky = false, seed = 123)

        assertEquals(first, second)
        assertEquals(QuizType.entries.filter { it.tier == 0 }.toSet(), first.toSet())
    }

    private fun log(
        rating: Rating,
        kind: String,
        type: QuizType?,
        at: Long = 100,
    ) = ReviewLogEntity(
        wordId = 1,
        rating = rating.value,
        source = "test",
        reviewEpochSec = at,
        elapsedDays = 0,
        scheduledDays = 1,
        kind = kind,
        questionType = type?.key,
    )
}
