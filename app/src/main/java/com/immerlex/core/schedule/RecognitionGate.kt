package com.immerlex.core.schedule

/**
 * 「认出来」攒到几分才算一次真正的回忆（用户 v4.4 定的按题型加权）。
 *
 * 四选一英→中、中→英这样的认出最容易；句子挖空要自己想起拼写，英文释义选词要先读懂英文、
 * 再认出词形，这两种一次记 [HEAVY_POINTS] 分。攒满 [GOOD_POINTS] 分才折算成一次
 * recall GOOD，答错照样当场清零。
 *
 * 只按记录里已经存下的题型字符串算（`review_logs.questionType`），所以升级前的 attempt
 * 自动按新权重生效，不改表、不回填。
 */
object RecognitionGate {

    /** 攒够这么多分，折算成一次真正的回忆。 */
    const val GOOD_POINTS = 3

    /** 句子挖空、英文释义选词一次算两分。 */
    const val HEAVY_POINTS = 2

    /** 题型一律是 [com.immerlex.core.quiz.QuizType.key] 那种大写字符串；认不出来按最轻的算。 */
    fun pointsFor(questionType: String?): Int = when (questionType) {
        "CLOZE", "DEFINITION" -> HEAVY_POINTS
        else -> 1
    }
}
