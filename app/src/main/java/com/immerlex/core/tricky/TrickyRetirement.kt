package com.immerlex.core.tricky

import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.fsrs.Rating

/**
 * 一个易错词什么时候"不再易错"：标成易错之后，只要连着答对满 [STREAK] 次（认出来的 attempt
 * 与真正回忆的 recall 都算一次答对），就提示可以把它从易错里移出去。碰到一次答错(AGAIN)或标记之前的
 * 记录就从头计。见 docs/adr/0028。
 */
object TrickyRetirement {

    const val STREAK = 6

    /** [logs] 必须按时间升序（[com.immerlex.core.db.ReviewLogDao.forWord] 就是这个顺序）。 */
    fun correctStreakSinceMarked(logs: List<ReviewLogEntity>, markedAtSec: Long): Int {
        var n = 0
        for (log in logs.asReversed()) {
            if (log.reviewEpochSec < markedAtSec) break
            if (log.rating == Rating.AGAIN.value) break
            n++
        }
        return n
    }

    fun shouldRetire(streak: Int, threshold: Int = STREAK): Boolean = streak >= threshold
}
