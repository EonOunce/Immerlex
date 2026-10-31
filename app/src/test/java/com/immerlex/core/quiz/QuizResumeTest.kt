package com.immerlex.core.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 中途退出再回来必须看到同一组题、同一道题：题目和进度都要能原样写进库里再读出来。
 * 存坏了宁可当没存过（重排一组），也不能拿一半的数据去拼界面。
 */
class QuizResumeTest {

    @Test
    fun aHalfDoneGroupComesBackWithTheSameQuestionAndScore() {
        val questions = listOf(question(1, "harbour"), question(2, "lantern"), question(3, "pebble"))
        val retry = question(1, "harbour", QuizType.ZH_EN)
        val resume = QuizResume(
            mode = QuizMode.DUE,
            questions = questions,
            alternatives = mapOf(1L to retry),
            wrongIds = listOf(1),
            excludedIds = setOf(8L, 9L),
            index = 2,
            inPractice = false,
            chosen = 1,
            correct = false,
            correctCount = 1,
            group = 2,
        )

        val back = decodeQuizResume(encodeQuizResume(resume))

        assertNotNull(back)
        assertEquals(resume, back)
    }

    @Test
    fun aGroupInterruptedDuringPracticeComesBackInPractice() {
        val questions = listOf(question(1, "harbour"), question(2, "lantern"))
        val retry = question(1, "harbour", QuizType.ZH_EN)
        val resume = QuizResume(
            mode = QuizMode.EARLY,
            questions = questions,
            alternatives = mapOf(1L to retry),
            wrongIds = listOf(1),
            excludedIds = emptySet(),
            index = 0,
            inPractice = true,
            chosen = 0,
            correct = true,
            correctCount = 1,
            group = 1,
        )

        val back = decodeQuizResume(encodeQuizResume(resume))

        assertNotNull(back)
        assertEquals(resume, back)
    }

    @Test
    fun garbageOrOutOfRangePayloadsMeanNoSavedGroup() {
        assertNull(decodeQuizResume("not json"))
        assertNull(decodeQuizResume("{}"))

        val questions = listOf(question(1, "harbour"))
        val beyondEnd = QuizResume(
            mode = QuizMode.DUE,
            questions = questions,
            alternatives = emptyMap(),
            wrongIds = emptyList(),
            excludedIds = emptySet(),
            index = 5,
            inPractice = false,
            chosen = null,
            correct = false,
            correctCount = 0,
            group = 1,
        )
        assertNull(decodeQuizResume(encodeQuizResume(beyondEnd)))

        val badChosen = beyondEnd.copy(index = 0, chosen = 9)
        assertNull(decodeQuizResume(encodeQuizResume(badChosen)))
    }

    @Test
    fun anUnknownQuestionTypeDropsTheWholeGroupInsteadOfShowingHalf() {
        val saved = encodeQuizResume(
            QuizResume(
                mode = QuizMode.DUE,
                questions = listOf(question(1, "harbour")),
                alternatives = emptyMap(),
                wrongIds = emptyList(),
                excludedIds = emptySet(),
                index = 0,
                inPractice = false,
                chosen = null,
                correct = false,
                correctCount = 0,
                group = 1,
            ),
        ).replace("\"EN_ZH\"", "\"POETRY\"")

        assertNull(decodeQuizResume(saved))
    }

    private fun question(id: Long, word: String, type: QuizType = QuizType.EN_ZH) = QuizQuestion(
        wordId = id,
        word = word,
        type = type,
        prompt = "题目-$word",
        promptHint = "提示-$word",
        options = listOf(
            QuizOption("$word-a", "释义甲", true),
            QuizOption("$word-b", "释义乙", false),
        ),
    )
}
