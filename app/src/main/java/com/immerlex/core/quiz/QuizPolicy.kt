package com.immerlex.core.quiz

import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.fsrs.Rating

/** 某个词在某个题型上的历史表现；没考过就是 0 次。 */
data class TypeStat(
    val attempts: Int = 0,
    val successes: Int = 0,
    val lastAskedSec: Long = 0,
) {
    /** 加一平滑：没考过 = 0.5，考过一次答对 = 0.67，越稳越高。 */
    val smoothedSuccess: Double get() = (successes + 1.0) / (attempts + 2.0)
}

/**
 * 题型闸门与选题策略（docs/adr/0012 第 3 条）。这里全是纯函数：
 * FSRS 仍只回答"什么时候复习"，策略只回答"这次用哪种题型问"。
 */
object QuizPolicy {

    /** 新词就能用的两种题型。 */
    val BASE_TYPES = listOf(QuizType.EN_ZH, QuizType.CLOZE)

    fun recallLogs(logs: List<ReviewLogEntity>): List<ReviewLogEntity> =
        logs.filter { it.kind == LOG_KIND_RECALL }

    private fun ReviewLogEntity.passed(): Boolean = rating > Rating.AGAIN.value

    /** 这个题型的闸门开到了第几档。 */
    fun unlockedTier(logs: List<ReviewLogEntity>): Int {
        val recalls = recallLogs(logs)
        if (recalls.none { it.passed() }) return 0
        if (recalls.none { it.questionType == QuizType.ZH_EN.key && it.passed() }) return 1
        return 2
    }

    /** 最近一次回忆答错了哪个题型；没答错就是 null。 */
    fun lastFailedType(logs: List<ReviewLogEntity>): QuizType? {
        val last = recallLogs(logs).lastOrNull() ?: return null
        if (last.rating > Rating.AGAIN.value) return null
        return QuizType.fromKey(last.questionType)
    }

    /** 答错后先退回英→中/挖空；易错词只在没答错时享受"全题型开放"。 */
    fun availableTypes(logs: List<ReviewLogEntity>, tricky: Boolean): List<QuizType> {
        if (lastFailedType(logs) != null) return BASE_TYPES
        if (tricky) return QuizType.entries.toList()
        val tier = unlockedTier(logs)
        return QuizType.entries.filter { it.tier <= tier }
    }

    fun statsOf(logs: List<ReviewLogEntity>): Map<QuizType, TypeStat> {
        val out = mutableMapOf<QuizType, TypeStat>()
        recallLogs(logs).forEach { log ->
            val type = QuizType.fromKey(log.questionType) ?: return@forEach
            val stat = out[type] ?: TypeStat()
            out[type] = stat.copy(
                attempts = stat.attempts + 1,
                successes = stat.successes + if (log.passed()) 1 else 0,
                lastAskedSec = maxOf(stat.lastAskedSec, log.reviewEpochSec),
            )
        }
        return out
    }

    /**
     * 把可用题型排成"先考哪个"：成功率最低优先，其次最久没问，
     * 最后用词 id + 会话种子做确定性打散——同一天同一词不会永远同一个顺序。
     * 刚答错的题型挪到最后，实在没有别的题可出时才回到它。
     */
    fun orderedTypes(
        wordId: Long,
        logs: List<ReviewLogEntity>,
        tricky: Boolean,
        seed: Long,
    ): List<QuizType> {
        val available = availableTypes(logs, tricky)
        val stats = statsOf(logs)
        val failed = lastFailedType(logs)
        return available.sortedWith(
            compareBy<QuizType> { if (it == failed) 1 else 0 }
                .thenBy { stats[it]?.smoothedSuccess ?: 0.5 }
                .thenBy { stats[it]?.lastAskedSec ?: 0L }
                .thenBy { tieBreak(wordId, it, seed) },
        )
    }

    private fun tieBreak(wordId: Long, type: QuizType, seed: Long): Long =
        (wordId * 1_000_003L + type.ordinal * 7_919L + seed) xor (wordId shl 17)
}
