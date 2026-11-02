package com.immerlex.core.quiz

/**
 * 提前复习一组几个词。三档可选，默认最小的一档：
 * 一次被考二十个之前，用户得先自己选过。
 */
object EarlyReview {
    const val DEFAULT_GROUP_SIZE = 5
    val GROUP_SIZES: List<Int> = listOf(5, 10, 20)

    fun saneOr(raw: Int): Int = if (raw in GROUP_SIZES) raw else DEFAULT_GROUP_SIZE
}
