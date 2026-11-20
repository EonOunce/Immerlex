package com.immerlex.core.quiz

/** 记账时写在 `questionType` 上的名字。连连看不是一个 [QuizType]：它没有题干与四选一选项。 */
const val MATCH_QUESTION_TYPE = "MATCH"

/** 连连看的一对：左边英文、右边中文，两边点名彼此才算连上。 */
data class MatchPair(val wordId: Long, val word: String, val gloss: String)

/** 一屏牌：两列各自打乱，同一对词在左右两列的位置互不相干。 */
data class MatchBoard(val left: List<MatchPair>, val right: List<MatchPair>) {
    init {
        require(left.map { it.wordId }.toSet() == right.map { it.wordId }.toSet()) {
            "一屏的左右两列必须是同一批词"
        }
    }
}

/** 一组连连看：分几屏、一共几对。 */
data class MatchPlan(val boards: List<MatchBoard>) {
    val pairCount: Int get() = boards.sumOf { it.left.size }
    val isEmpty: Boolean get() = boards.isEmpty()
}

/** 这一屏摆出的每一对都连上了：连完才翻下一屏，不是整组连完才翻。 */
fun boardCleared(board: MatchBoard, matched: Set<Long>): Boolean =
    board.left.isNotEmpty() && board.left.all { it.wordId in matched }

/**
 * 连连看的牌面：把"词 + 中文"分成几屏、两列各自打散。纯函数，不碰数据库。
 * 一屏不超过 [BOARD_SIZE] 对——再多就要滑着找，一眼看不全就不是连连看了；
 * 屏数尽量平均（21 对是 5/4/4/4/4，不是 5/5/5/5/1）。
 */
object MatchBoards {

    const val BOARD_SIZE = 5

    /** 一对都不成局：连一个算不上连连看，入口上也不该显示个数。 */
    const val MIN_PAIRS = 2

    /**
     * 能上桌的牌：两边都有字，同一个中文面只留先来的那张。
     * harbor / harbour 这种释义逐字相同的词若一屏摆出两块一样的中文，
     * 连哪个都说得通，另一块就自动错了——所以整组只留一个。
     */
    fun usable(pairs: List<MatchPair>): List<MatchPair> {
        val seenWords = HashSet<String>()
        val seenGlosses = HashSet<String>()
        return pairs.mapNotNull { pair ->
            val word = pair.word.trim()
            val gloss = pair.gloss.trim()
            if (word.isEmpty() || gloss.isEmpty()) return@mapNotNull null
            if (!seenWords.add(word.lowercase())) return@mapNotNull null
            if (!seenGlosses.add(gloss)) return@mapNotNull null
            pair.copy(word = word, gloss = gloss)
        }
    }

    /** 可用的词不足 [MIN_PAIRS] 对就一屏都不出。 */
    fun boards(pairs: List<MatchPair>, seed: Long): List<MatchBoard> {
        val usable = usable(pairs)
        if (usable.size < MIN_PAIRS) return emptyList()
        return chunks(usable).mapIndexed { index, chunk ->
            MatchBoard(
                left = chunk.sortedBy { stableHash(it.word.lowercase(), seed + index * 31L) },
                right = chunk.sortedBy { stableHash(it.gloss, seed + index * 31L + 13L) },
            )
        }
    }

    /** 平均切成 ceil(n / [BOARD_SIZE]) 段：先每段多给一对，尾巴才不会只剩一对。 */
    private fun chunks(pairs: List<MatchPair>): List<List<MatchPair>> {
        val count = (pairs.size + BOARD_SIZE - 1) / BOARD_SIZE
        val base = pairs.size / count
        val extra = pairs.size % count
        var start = 0
        return (0 until count).map { index ->
            val size = base + if (index < extra) 1 else 0
            pairs.subList(start, start + size).also { start += size }
        }
    }
}
