package com.immerlex.core.schedule

import androidx.room.withTransaction
import com.immerlex.core.db.FSRS_STATE_LEARNING
import com.immerlex.core.db.FSRS_STATE_RELEARNING
import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.LOG_KIND_LEARN
import com.immerlex.core.db.LOG_KIND_RECALL
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Card
import com.immerlex.core.fsrs.CardState
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TodayQueue(
    val reviewWords: List<WordEntity>,
    val newWords: List<WordEntity>,
)

/**
 * 点开词卡时这次评分会记什么（docs/adr/0012-recall-events-and-quiz-types.md）。
 * 阅读页的按钮直接由它决定，UI 不会承诺服务端不做的记账。
 */
enum class WordAccess {
    /** 第一次遇到：选评分只建立排期，记一条 learn，不算一次回忆成功。 */
    Learn,

    /** 答案收起、词也到期：这次评分算一次真实回忆。 */
    Recall,

    /**
     * 答案收起、但还没到复习时间。判断发生在点开之前，所以"没想起来"是一次
     * 诚实的失败回忆，值得记；"想起来了"发生在到期日之前，记了会把间隔推远，
     * 因此不记账，只收起卡片。
     */
    Early,

    /** 只看释义：学习模式，或这个词根本没进词库。 */
    ViewOnly,
}

data class GradeResult(
    val word: WordEntity,
    val kind: String,
    val logId: Long,
    val undoableUntilSec: Long,
)

data class UndoResult(val word: WordEntity, val kind: String)

/** Read side of the schedule, so generators can be tested without a database. */
interface WordQueue {
    suspend fun todaysQueue(now: Instant, dailyNewLimit: Int): TodayQueue
}

class ScheduleService(
    private val wordDao: WordDao,
    private val logDao: ReviewLogDao,
    private val scheduler: Scheduler,
    /** 生产环境传入，把"改卡片 + 写日志"放进同一个 Room 事务；无库的单测可省略。 */
    private val database: ImmerlexDatabase? = null,
) : WordQueue {

    // 阅读页的双击与测验的快速翻页都可能并发进来；先读日志再写日志必须串行，
    // 否则同一瞬间两次评分会各自认为"这是唯一一次"，各写一条。
    private val mutex = Mutex()

    suspend fun accessFor(
        word: WordEntity,
        answersHidden: Boolean,
        now: Instant = Instant.now(),
    ): WordAccess = mutex.withLock {
        val logs = logDao.forWord(word.id)
        when {
            logs.isEmpty() -> WordAccess.Learn
            !answersHidden -> WordAccess.ViewOnly
            isDue(word, now) -> WordAccess.Recall
            else -> WordAccess.Early
        }
    }

    /** 第一次看到词：评分只用来初始化 FSRS，记 learn 而不是 recall。 */
    suspend fun learn(
        word: WordEntity,
        rating: Rating,
        source: String,
        now: Instant = Instant.now(),
    ): GradeResult? = mutex.withLock {
        if (logDao.forWord(word.id).isNotEmpty()) return@withLock null
        record(word, rating, source, LOG_KIND_LEARN, questionType = null, at = now)
    }

    /**
     * 一次独立回忆：先回忆再反馈，记 recall。测验的提前复习传 [allowEarly]；
     * 与上一次记录相隔不足 [MIN_RECALL_GAP_SECONDS] 的视为连点手滑，不重复记账。
     */
    suspend fun recall(
        word: WordEntity,
        rating: Rating,
        source: String,
        questionType: String? = null,
        now: Instant = Instant.now(),
        allowEarly: Boolean = false,
    ): GradeResult? = mutex.withLock {
        val logs = logDao.forWord(word.id)
        if (logs.isEmpty()) return@withLock null
        if (!allowEarly && !isDue(word, now)) return@withLock null
        val last = logs.lastOrNull()
        if (last != null && now.epochSecond - last.reviewEpochSec < MIN_RECALL_GAP_SECONDS) {
            return@withLock null
        }
        record(word, rating, source, LOG_KIND_RECALL, questionType, now)
    }

    /**
     * 阅读页的几秒内撤销：删掉最后一条记录，把卡片恢复成评分前的样子。
     * 之后的评分就按新事实继续累积，不再允许覆盖。
     */
    suspend fun undo(logId: Long, now: Instant = Instant.now()): UndoResult? = mutex.withLock {
        val log = logDao.byId(logId) ?: return@withLock null
        if (!log.hasPreRatingSnapshot) return@withLock null
        val age = now.epochSecond - log.reviewEpochSec
        if (age < 0 || age > UNDO_WINDOW_SECONDS) return@withLock null
        val logs = logDao.forWord(log.wordId)
        if (logs.lastOrNull()?.id != log.id) return@withLock null
        val word = wordDao.findById(log.wordId) ?: return@withLock null
        val earlier = logs.filterNot { it.id == log.id }
        val restored = word.copy(
            stability = log.preStability,
            difficulty = log.preDifficulty,
            dueEpochSec = log.preDueEpochSec.takeIf { it != 0L },
            lastReviewEpochSec = log.preLastReviewEpochSec,
            fsrsState = log.preState,
            reps = earlier.size,
            lapses = earlier.count { it.rating == Rating.AGAIN.value },
            intervalDays = earlier.lastOrNull()?.scheduledDays ?: 0,
        )
        write {
            wordDao.update(restored)
            logDao.deleteById(log.id)
        }
        UndoResult(restored, log.kind)
    }
    /** Interval a rating would produce, without touching the database. */
    fun preview(word: WordEntity, rating: Rating, now: Instant = Instant.now()): Int =
        scheduler.reviewCard(word.toCard(now), rating, now).log.scheduledDays

    override suspend fun todaysQueue(now: Instant, dailyNewLimit: Int): TodayQueue {
        val due = wordDao.dueWords(now.epochSecond, 1000)
        return TodayQueue(
            reviewWords = due.filter { !it.isNew },
            newWords = due.filter { it.isNew }.take(dailyNewLimit),
        )
    }

    /**
     * 今日页、生成器、设置页共用这一个数：新词被每日上限截断之后，今天到底还有几个词要读。
     * 各算各的话，首页会说"还有 40 个"，而生成的章节只塞了 25 个。
     */
    suspend fun todayDueCount(now: Instant, dailyNewLimit: Int): Int =
        todaysQueue(now, dailyNewLimit).let { it.reviewWords.size + it.newWords.size }

    private suspend fun record(
        word: WordEntity,
        rating: Rating,
        source: String,
        kind: String,
        questionType: String?,
        at: Instant,
    ): GradeResult {
        val (updated, log) = evaluate(word, rating, source, at, kind, questionType)
        val logId = write {
            wordDao.update(updated)
            logDao.insert(log)
        }
        return GradeResult(updated, kind, logId, at.epochSecond + UNDO_WINDOW_SECONDS)
    }

    /** 卡片与日志必须同生同死：崩在两次写之间会留下"分了级但没有记录"的卡片。 */
    private suspend fun <T> write(block: suspend () -> T): T =
        if (database != null) database.withTransaction { block() } else block()

    private fun isDue(word: WordEntity, now: Instant): Boolean =
        word.dueEpochSec == null || word.dueEpochSec <= now.epochSecond

    private fun evaluate(
        word: WordEntity,
        rating: Rating,
        source: String,
        at: Instant,
        kind: String,
        questionType: String?,
    ): Pair<WordEntity, ReviewLogEntity> {
        val result = scheduler.reviewCard(word.toCard(at), rating, at)
        val updated = word.applyCard(result.card, rating, result.log.scheduledDays)
        return updated to ReviewLogEntity(
            wordId = word.id,
            rating = rating.value,
            source = source,
            reviewEpochSec = at.epochSecond,
            elapsedDays = result.log.elapsedDays,
            scheduledDays = result.log.scheduledDays,
            preStability = word.stability,
            preDifficulty = word.difficulty,
            preState = word.fsrsState,
            preDueEpochSec = word.dueEpochSec ?: 0L,
            preLastReviewEpochSec = word.lastReviewEpochSec,
            kind = kind,
            questionType = questionType,
        )
    }

    companion object {
        /** 阅读页点错评分后的撤销窗口；过了就是真实记录。 */
        const val UNDO_WINDOW_SECONDS = 8L

        /** 同一词的两次评分相隔不足这么久，按连点手滑处理，不写第二条。 */
        const val MIN_RECALL_GAP_SECONDS = 2L
    }
}

private fun WordEntity.toCard(now: Instant): Card {
    val s = stability
    val d = difficulty
    if (s == null || d == null) return Card(due = now)
    return Card(
        state = when (fsrsState) {
            FSRS_STATE_REVIEW -> CardState.REVIEW
            FSRS_STATE_RELEARNING -> CardState.RELEARNING
            else -> CardState.LEARNING
        },
        stability = s,
        difficulty = d,
        due = dueEpochSec?.let(Instant::ofEpochSecond) ?: now,
        lastReview = lastReviewEpochSec?.let(Instant::ofEpochSecond),
    )
}

private fun WordEntity.applyCard(card: Card, rating: Rating, scheduledDays: Int): WordEntity =
    copy(
        stability = card.stability,
        difficulty = card.difficulty,
        dueEpochSec = card.due.epochSecond,
        lastReviewEpochSec = card.lastReview?.epochSecond,
        fsrsState = when (card.state) {
            CardState.REVIEW -> FSRS_STATE_REVIEW
            CardState.RELEARNING -> FSRS_STATE_RELEARNING
            CardState.LEARNING -> FSRS_STATE_LEARNING
        },
        reps = reps + 1,
        lapses = lapses + if (rating == Rating.AGAIN) 1 else 0,
        intervalDays = scheduledDays,
    )
