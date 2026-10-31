package com.immerlex.core.schedule

import com.immerlex.core.db.WordEntity

/**
 * 正文里一个词该染哪种颜色。只听阅读页自己写下的记录：测验和提前复习排的是同一个词，
 * 但这一行的颜色回答的是"我在故事里学到哪一步了"，不是"系统什么时候要考我"。
 */
enum class ReaderTone { NEW, LEARNED, DUE }

/** 这一章里阅读页自己记过的词：[learned] 碰过（学过或复习过），[reviewedToday] 今天已经回忆过。 */
data class ReaderMarks(
    val learned: Set<Long> = emptySet(),
    val reviewedToday: Set<Long> = emptySet(),
)

/**
 * 没学过是生的；学过就绿；今天到期、还没在阅读里回忆过是蓝——复习完立刻转绿，
 * 哪怕 FSRS 因为答"忘了"把词又排回今天，也认这一次回忆已经发生。
 */
fun readerTone(word: WordEntity?, marks: ReaderMarks, nowSec: Long): ReaderTone = when {
    word == null || word.id !in marks.learned -> ReaderTone.NEW
    word.id in marks.reviewedToday -> ReaderTone.LEARNED
    word.dueEpochSec?.let { it <= nowSec } == true -> ReaderTone.DUE
    else -> ReaderTone.LEARNED
}
