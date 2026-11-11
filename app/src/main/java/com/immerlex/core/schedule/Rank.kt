package com.immerlex.core.schedule

/** 称号怎么念：入门六档带"级"，进阶三档带"段"，顶上一档什么都不带。 */
enum class RankKind { LEVEL, DUAN, SUMMIT }

/**
 * 段位阶梯数的是**已掌握的词**——间隔已经推到 [ScheduleService.MASTERED_INTERVAL_DAYS] 天以上的那些，
 * 不是"学过的词"：光读过一遍不算掌握，这条口径与战绩页那个"已掌握"是同一个数。
 *
 * 档位名由用户定（docs/adr/0030）：入门 白词·青词·蓝词·绿词·橙词·紫词，进阶 赤段·玄段·金段，顶级 词圣。
 * 门槛前面密、后面疏：前三档在考研词汇量前面很快就到，往上每一档都要真把一批词的间隔推远才行。
 */
enum class RankTier(val kind: RankKind, val level: Int, val atLeastMastered: Int) {
    WHITE(RankKind.LEVEL, 1, 0),
    CYAN(RankKind.LEVEL, 2, 20),
    BLUE(RankKind.LEVEL, 3, 60),
    GREEN(RankKind.LEVEL, 4, 150),
    ORANGE(RankKind.LEVEL, 5, 300),
    VIOLET(RankKind.LEVEL, 6, 600),
    CRIMSON(RankKind.DUAN, 1, 1_000),
    OBSIDIAN(RankKind.DUAN, 2, 1_600),
    GOLD(RankKind.DUAN, 3, 2_400),
    SAGE(RankKind.SUMMIT, 0, 3_500),
    ;

    /** 再往上还有哪一档；词圣已经到头，返回 null。 */
    val next: RankTier? get() = entries.getOrNull(ordinal + 1)
}

data class Rank(val tier: RankTier, val mastered: Int, val toNext: Int)

object RankLadder {
    /** 一个都不认识时是白词，不是"没有段位"——从零开始也该有个名字。 */
    fun of(mastered: Int): Rank {
        val tier = RankTier.entries.last { it.atLeastMastered <= mastered }
        val next = tier.next
        return Rank(tier, mastered, next?.let { (it.atLeastMastered - mastered).coerceAtLeast(0) } ?: 0)
    }
}
