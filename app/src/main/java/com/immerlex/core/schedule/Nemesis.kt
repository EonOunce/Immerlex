package com.immerlex.core.schedule

import com.immerlex.core.db.RecallEventRow
import com.immerlex.core.fsrs.Rating

/**
 * 宿敌：最后一次真记账是"没想起来"的那些词——它们欠一次复仇。
 *
 * 只在读的时候现算，不建表：一个词只要在之后任何一次入口里被真记账地认出来（一次真实回忆，
 * 或攒满三次"认出"升成的那一次），它自己就从名单上下去。排在最前面的是最近刚打过的那个。
 *
 * 这里只收 `recall`，四选一攒着的 attempt 不算——那条既没动过卡片，也不算一次真正的失败。
 * 见 docs/adr/0030。
 */
object Nemesis {
    /** [events] 必须按时间升序；返回的词 id 按"最后一次失败"从现在到过去排。 */
    fun wordIds(events: List<RecallEventRow>): List<Long> {
        val decided = mutableSetOf<Long>()
        val failed = mutableListOf<Long>()
        for (event in events.asReversed()) {
            // 同一个词只看最近那一次：之后认出来了就不算宿敌，之前答错了也已经被覆盖。
            if (!decided.add(event.wordId)) continue
            if (event.rating == Rating.AGAIN.value) failed += event.wordId
        }
        return failed
    }
}
