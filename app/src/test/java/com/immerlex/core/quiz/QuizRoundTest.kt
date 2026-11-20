package com.immerlex.core.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizRoundTest {

    @Test
    fun theChoicesAreTwoThreeOrFourSegmentsAndThreeIsTheDefault() {
        assertEquals(listOf(2, 3, 4), QuizRound.SEGMENT_CHOICES)
        assertEquals(3, QuizRound.DEFAULT_SEGMENTS)
        assertTrue(QuizRound.DEFAULT_SEGMENTS in QuizRound.SEGMENT_CHOICES)
    }

    @Test
    fun aBrokenSegmentCountFallsBackToThree() {
        assertEquals(3, QuizRound.saneSegments(0))
        assertEquals(3, QuizRound.saneSegments(5))
        assertEquals(3, QuizRound.saneSegments(-2))
        assertEquals(2, QuizRound.saneSegments(2))
        assertEquals(4, QuizRound.saneSegments(4))
    }

    @Test
    fun aSegmentIsTodaysWordsDividedByTheChosenSegments_RoundedUp() {
        assertEquals(10, QuizRound.perSegment(30, 3))
        assertEquals(8, QuizRound.perSegment(22, 3))
        assertEquals(5, QuizRound.perSegment(10, 2))
        assertEquals(3, QuizRound.perSegment(10, 4))
        assertEquals(1, QuizRound.perSegment(1, 4))
    }

    @Test
    fun thereIsNoPerSegmentCap() {
        assertEquals(60, QuizRound.perSegment(120, 2))
        assertEquals(100, QuizRound.perSegment(300, 3))
    }

    @Test
    fun anEmptyDayFallsBackToTheEntrancesOwnPool() {
        assertEquals(17, QuizRound.perSegmentOr(0, 50, 3))
        assertEquals(25, QuizRound.perSegmentOr(0, 50, 2))
        assertEquals(13, QuizRound.perSegmentOr(0, 50, 4))
        assertEquals(0, QuizRound.perSegmentOr(0, 0, 3))
    }

    @Test
    fun aNonEmptyDayIgnoresThePoolSize() {
        assertEquals(10, QuizRound.perSegmentOr(30, 50, 3))
        assertEquals("池子只有 4 个也照今天的词数算", 10, QuizRound.perSegmentOr(30, 4, 3))
    }
}
