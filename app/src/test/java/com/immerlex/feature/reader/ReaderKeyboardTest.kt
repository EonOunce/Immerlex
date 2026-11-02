package com.immerlex.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderKeyboardTest {

    @Test
    fun theFirstPressLandsOnTheFirstWordFromEitherDirection() {
        assertEquals(0, nextCursor(null, -1, 5))
        assertEquals(0, nextCursor(null, 1, 5))
    }

    @Test
    fun itStopsAtBothEndsInsteadOfWrappingAround() {
        assertEquals(0, nextCursor(0, -1, 5))
        assertEquals(4, nextCursor(4, 1, 5))
        assertEquals(3, nextCursor(2, 1, 5))
        assertEquals(1, nextCursor(2, -1, 5))
    }

    @Test
    fun aChapterWithNoWordsHasNowhereToGo() {
        assertEquals(null, nextCursor(null, 1, 0))
        assertEquals(null, nextCursor(3, -1, 0))
    }
}
