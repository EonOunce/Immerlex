package com.immerlex.core.quiz

/**
 * 一组几道题不写死：今天的词数 ÷ 想要的段数（用户 v4.4 定的「收法二」）。
 * 词多的时候一段自动变长，词少的时候自动变短，一天想分几段坐由设置页里的 2 / 3 / 4 决定。
 * 段数只是"今天分几次坐"，不是"一个词要考几遍"——一个词该考几遍由认出闸门管。
 */
object QuizRound {

    const val DEFAULT_SEGMENTS = 3

    val SEGMENT_CHOICES: List<Int> = listOf(2, 3, 4)

    fun saneSegments(raw: Int): Int = if (raw in SEGMENT_CHOICES) raw else DEFAULT_SEGMENTS

    /** 今天有 [total] 个词、分 [segments] 段，一段多少道；词数为 0 时就是 0。 */
    fun perSegment(total: Int, segments: Int = DEFAULT_SEGMENTS): Int {
        if (total <= 0) return 0
        val n = saneSegments(segments)
        return (total + n - 1) / n
    }

    /**
     * 今天的词数算不出一段的时候（休息日一个到期词都没有），退回这个入口自己的候选池；
     * 否则提前复习、易错练习在没词可学的日子会显示 0 个，永远进不去。
     */
    fun perSegmentOr(total: Int, poolSize: Int, segments: Int = DEFAULT_SEGMENTS): Int =
        perSegment(if (total > 0) total else poolSize, segments)
}
