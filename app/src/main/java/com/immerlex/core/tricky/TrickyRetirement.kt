package com.immerlex.core.tricky

import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.schedule.RecognitionGate

/**
 * 一个易错词什么时候"不再易错"：标成易错之后，答对攒满 [RETIRE_POINTS] 分就提示可以把它移出去。
 * 每次答对值几分与认出闸门是同一把尺（挖空、英文释义选词各 2 分，其余 1 分）；
 * 碰到一次答错(AGAIN)或标记之前的记录就从头计。见 docs/adr/0028、0037。
 */
object TrickyRetirement {

    /** 六分：光做四选一要六次；挖空、释义选词掺着来，四五次就够。 */
    const val RETIRE_POINTS = 6

    /** [logs] 必须按时间升序（[com.immerlex.core.db.ReviewLogDao.forWord] 就是这个顺序）。 */
    fun correctPointsSinceMarked(logs: List<ReviewLogEntity>, markedAtSec: Long): Int {
        var points = 0
        for (log in logs.asReversed()) {
            if (log.reviewEpochSec < markedAtSec) break
            if (log.rating == Rating.AGAIN.value) break
            points += RecognitionGate.pointsFor(log.questionType)
        }
        return points
    }

    fun shouldRetire(points: Int, threshold: Int = RETIRE_POINTS): Boolean = points >= threshold
}
