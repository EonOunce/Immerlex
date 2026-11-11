package com.immerlex.core.schedule

import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NemesisTest {

    private fun event(wordId: Long, rating: Rating) = RecallEventRow(
        wordId = wordId,
        rating = rating.value,
        source = "quiz",
        difficulty = 5.0,
        scheduledDays = 1,
    )

    @Test
    fun nobodyFailedSoNobodyIsANemesis() {
        val events = listOf(event(1, Rating.GOOD), event(2, Rating.EASY), event(3, Rating.HARD))

        assertTrue(Nemesis.wordIds(events).isEmpty())
    }

    @Test
    fun aLaterSuccessTakesTheWordOffTheList() {
        // 时间升序：先答错，后来认出来了——最后一次说话才算数。
        val events = listOf(event(7, Rating.AGAIN), event(7, Rating.GOOD))

        assertTrue(Nemesis.wordIds(events).isEmpty())
    }

    @Test
    fun aLaterFailureKeepsItOnTheList() {
        val events = listOf(event(7, Rating.GOOD), event(7, Rating.AGAIN))

        assertEquals(listOf(7L), Nemesis.wordIds(events))
    }

    @Test
    fun theListHoldsOnlyWhatIsStillOwed() {
        val events = listOf(
            event(1, Rating.AGAIN),
            event(2, Rating.AGAIN),
            event(3, Rating.GOOD),
            event(1, Rating.GOOD),
        )

        assertEquals("2 是唯一还欠着一次复仇的词", listOf(2L), Nemesis.wordIds(events))
    }

    @Test
    fun orderingRunsFromTheLatestDefeatBackwards() {
        val events = listOf(
            event(11, Rating.AGAIN),
            event(12, Rating.AGAIN),
            event(13, Rating.AGAIN),
        )

        assertEquals(listOf(13L, 12L, 11L), Nemesis.wordIds(events))
    }

    @Test
    fun aWordAppearsOnceHoweverManyTimesItBites() {
        val events = listOf(
            event(5, Rating.AGAIN),
            event(6, Rating.AGAIN),
            event(5, Rating.AGAIN),
            event(5, Rating.AGAIN),
        )

        assertEquals(listOf(5L, 6L), Nemesis.wordIds(events))
    }
}
