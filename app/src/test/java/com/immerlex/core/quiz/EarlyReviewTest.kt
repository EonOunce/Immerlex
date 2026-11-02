package com.immerlex.core.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyReviewTest {

    @Test
    fun theThreeTiersAreFiveTenAndTwenty() {
        assertEquals(listOf(5, 10, 20), EarlyReview.GROUP_SIZES)
    }

    @Test
    fun theDefaultIsTheSmallestTier() {
        assertEquals(5, EarlyReview.DEFAULT_GROUP_SIZE)
        assertTrue(EarlyReview.DEFAULT_GROUP_SIZE in EarlyReview.GROUP_SIZES)
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        assertEquals(5, EarlyReview.saneOr(0))
        assertEquals(5, EarlyReview.saneOr(7))
        assertEquals(5, EarlyReview.saneOr(-20))
        assertEquals(5, EarlyReview.saneOr(100))
        assertEquals(10, EarlyReview.saneOr(10))
        assertEquals(20, EarlyReview.saneOr(20))
    }
}
