package com.immerlex.feature.quiz

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizStateTest {

    @Test
    fun gradedCorrectAnswerCounts() {
        val state = QuizState(phase = QuizPhase.QUESTION, total = 2)

        assertEquals(1, state.afterAnswer(0, correct = true).correctCount)
    }

    @Test
    fun wrongAnswerDoesNotCount() {
        val state = QuizState(phase = QuizPhase.QUESTION, total = 1)

        assertEquals(0, state.afterAnswer(0, correct = false).correctCount)
    }

    @Test
    fun practiceAnswerNeverCounts() {
        val state = QuizState(phase = QuizPhase.QUESTION, total = 1, practice = true)

        assertEquals(0, state.afterAnswer(0, correct = true).correctCount)
    }

    @Test
    fun lastGradedQuestionOnlySaysFinishWhenNothingIsLeftToPractise() {
        val queued = QuizState(phase = QuizPhase.QUESTION, index = 1, total = 2, practiceQueued = true)
        val clean = QuizState(phase = QuizPhase.QUESTION, index = 1, total = 2)
        val lastPractice = QuizState(phase = QuizPhase.QUESTION, index = 0, total = 1, practice = true)

        assertEquals(false, queued.showsFinish)
        assertEquals(true, clean.showsFinish)
        assertEquals(true, lastPractice.showsFinish)
    }
}
