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
    fun doubtOpensTheAnswerWithoutPickingAnOption() {
        val state = QuizState(phase = QuizPhase.QUESTION, total = 1).afterDoubt()

        assertEquals(true, state.answered)
        assertEquals(null, state.chosen)
        assertEquals(false, state.correct)
        assertEquals(0, state.correctCount)
    }

    @Test
    fun theNextQuestionStartsCleanAgain() {
        val marked = QuizState(phase = QuizPhase.QUESTION, total = 1)
            .copy(trickyIds = setOf(7L), retirement = "affect")

        assertEquals(false, marked.afterDoubt().fresh().answered)
        assertEquals(true, marked.fresh().trickyIds.contains(7L))
        assertEquals(null, marked.fresh().retirement)
    }

    @Test
    fun aStreakSurvivesOneMissAndTheBestStreakStays() {
        val three = QuizState(phase = QuizPhase.QUESTION, total = 5)
            .afterAnswer(0, correct = true).fresh()
            .afterAnswer(0, correct = true).fresh()
            .afterAnswer(0, correct = true)
        assertEquals(3, three.streak)

        val missed = three.afterAnswer(1, correct = false).fresh()
        assertEquals("答错这一次归零", 0, missed.streak)
        assertEquals("最长连过的那一段留到结算", 3, missed.bestStreak)

        val practised = missed.copy(practice = true).afterAnswer(0, correct = true)
        assertEquals("当堂重练不计分，也不往上加连对", 0, practised.streak)
        assertEquals(3, practised.bestStreak)

        val doubted = three.fresh().afterDoubt()
        assertEquals("按「不确定」这一段也断了", 0, doubted.streak)
        assertEquals(3, doubted.bestStreak)
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
