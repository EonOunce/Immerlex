package com.immerlex.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 巅峰分要按时间一笔一笔重放整段历史，所以这里只取**真的动过卡片**的那些记录：
 * `learn` 是第一次看见答案、`attempt` 是四选一"认出来了但还差几次"，两边都不该单独算一次对抗
 * （attempt 攒满三次才升成一条 recall，那条升上来的 recall 就是它的账）。
 *
 * [RecallEventRow.difficulty] 取评分前卡片上的难度：老记录没写过快照就退回这个词现在的难度，
 * 词也删了谁都想不起来，按 FSRS 的默认难度 5.0 算——不为一条查不到来源的记录把整段重放废掉。
 */
@Dao
interface ReviewHistoryDao {
    @Query(
        "SELECT l.wordId AS wordId, l.rating AS rating, l.source AS source, " +
            "COALESCE(l.preDifficulty, w.difficulty, 5.0) AS difficulty, " +
            "l.scheduledDays AS scheduledDays, l.reviewEpochSec AS epochSec " +
            "FROM review_logs l LEFT JOIN words w ON w.id = l.wordId " +
            "WHERE l.kind = 'recall' ORDER BY l.reviewEpochSec ASC, l.id ASC",
    )
    suspend fun recallEvents(): List<RecallEventRow>
}

data class RecallEventRow(
    val wordId: Long,
    val rating: Int,
    val source: String,
    val difficulty: Double,
    val scheduledDays: Int,
    /** 这一笔是什么时候记的：影子对决按周把它折起来，一组测验的涨跌按它的起止把它认出来。 */
    val epochSec: Long,
)

/**
 * 每天一个影子，只在更高时抬上去：先按那天占一行，占不到（已经有了）且新值更大才改写。
 * 这样"今天算出过 137 个"不会因为随后又算了一次 130 就被抹掉。
 */
@Dao
interface RecallSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: RecallSnapshotEntity)

    @Query(
        "UPDATE recall_snapshots SET canRecall = :canRecall, mastered = :mastered, atSec = :atSec " +
            "WHERE epochDay = :epochDay AND canRecall < :canRecall",
    )
    suspend fun raiseBest(epochDay: Long, canRecall: Int, mastered: Int, atSec: Long): Int

    /** 以前最好的一天：不含今天，今天的数字是此刻现算的，跟自己比没意义。 */
    @Query("SELECT COALESCE(MAX(canRecall), 0) FROM recall_snapshots WHERE epochDay < :today")
    suspend fun bestBefore(today: Long): Int
}
