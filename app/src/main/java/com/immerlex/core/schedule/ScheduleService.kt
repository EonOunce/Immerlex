package com.immerlex.core.schedule

import com.immerlex.core.db.FSRS_STATE_LEARNING
import com.immerlex.core.db.FSRS_STATE_RELEARNING
import com.immerlex.core.db.FSRS_STATE_REVIEW
import com.immerlex.core.db.ReviewLogDao
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.fsrs.Card
import com.immerlex.core.fsrs.CardState
import com.immerlex.core.fsrs.Rating
import com.immerlex.core.fsrs.Scheduler
import java.time.Instant
import java.time.ZoneId

data class TodayQueue(
    val reviewWords: List<WordEntity>,
    val newWords: List<WordEntity>,
)

/**
 * What a tap on a rating button will do to the word's schedule
 * (docs/adr/0004-chapter-word-units.md). The reading page shows its buttons from
 * this same value, so the UI can never promise a scoring the service won't do.
 */
enum class GradeMode {
    /** 到期了：这一算一次复习。 */
    Review,

    /** 今天刚点过"忘了"，再点一次算重学——先不认识、后来认识是真实的学习过程。 */
    Relearn,

    /** 今天已经复习过：这次是修改上一次的评分，复习次数不变。 */
    Amend,

    /** 没到期、今天也没碰过：只揭示释义，不计分。 */
    Reveal,
}

/** 会不会真的多出一笔复习记录——UI 的间隔预览和"本次已复习"计数都跟着它走。 */
val GradeMode.recorded: Boolean get() = this == GradeMode.Review || this == GradeMode.Relearn

data class GradeResult(val word: WordEntity, val mode: GradeMode) {
    val recorded: Boolean get() = mode.recorded
}

/** Read side of the schedule, so generators can be tested without a database. */
interface WordQueue {
    suspend fun todaysQueue(now: Instant, dailyNewLimit: Int): TodayQueue
}

class ScheduleService(
    private val wordDao: WordDao,
    private val logDao: ReviewLogDao,
    private val scheduler: Scheduler,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : WordQueue {

    suspend fun gradeModeFor(word: WordEntity, now: Instant = Instant.now()): GradeMode =
        decide(word, logDao.forWord(word.id), now).mode

    /** One tap on a rating: records, relearns, amends or merely reveals, per [GradeMode]. */
    suspend fun grade(
        word: WordEntity,
        rating: Rating,
        source: String,
        now: Instant = Instant.now(),
    ): GradeResult {
        val logs = logDao.forWord(word.id)
        val decision = decide(word, logs, now)
        return when (decision.mode) {
            GradeMode.Review, GradeMode.Relearn ->
                GradeResult(review(word, rating, source, now), decision.mode)

            GradeMode.Amend -> GradeResult(amend(word, decision.log!!, logs, rating), decision.mode)

            GradeMode.Reveal -> GradeResult(word, decision.mode)
        }
    }

    suspend fun review(
        word: WordEntity,
        rating: Rating,
        source: String,
        now: Instant = Instant.now(),
    ): WordEntity {
        val (updated, log) = evaluate(word, rating, source, now)
        wordDao.update(updated)
        logDao.insert(log)
        return updated
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

    private data class Decision(val mode: GradeMode, val log: ReviewLogEntity? = null)

    private fun decide(word: WordEntity, logs: List<ReviewLogEntity>, now: Instant): Decision {
        if (word.dueEpochSec == null || word.dueEpochSec <= now.epochSecond) {
            return Decision(GradeMode.Review)
        }
        val today = logs.inReviewOrder().filter { isSameDay(it.reviewEpochSec, now) }
        val last = today.lastOrNull() ?: return Decision(GradeMode.Reveal)
        // 同日同词封顶 2 条：只有"忘了"之后那一次允许重学，第三次起一律走修改。
        if (last.rating == Rating.AGAIN.value && today.size < MAX_SAME_DAY_REVIEWS) {
            return Decision(GradeMode.Relearn)
        }
        // 升级前的老记录没有评前快照，回滚不了，只能当天不再计分。
        return if (last.hasPreRatingSnapshot) Decision(GradeMode.Amend, last) else Decision(GradeMode.Reveal)
    }

    /**
     * 改评分 = 回到那次复习之前，用新分在同一时刻重跑一遍。
     * 复习条数不变，reps / lapses 由记录重算，所以改分后的卡片与"当初就选这个分"完全一致。
     */
    private suspend fun amend(
        word: WordEntity,
        log: ReviewLogEntity,
        logs: List<ReviewLogEntity>,
        rating: Rating,
    ): WordEntity {
        val earlier = logs.filterNot { it.id == log.id }.inReviewOrder()
        val baseline = word.copy(
            stability = log.preStability,
            difficulty = log.preDifficulty,
            dueEpochSec = log.preDueEpochSec.takeIf { it != 0L },
            lastReviewEpochSec = log.preLastReviewEpochSec,
            fsrsState = log.preState,
            reps = earlier.size,
            lapses = earlier.count { it.rating == Rating.AGAIN.value },
            intervalDays = earlier.lastOrNull()?.scheduledDays ?: 0,
        )
        val at = Instant.ofEpochSecond(log.reviewEpochSec)
        val result = scheduler.reviewCard(baseline.toCard(at), rating, at)
        val updated = baseline.applyCard(result.card, rating, result.log.scheduledDays)
        wordDao.update(updated)
        logDao.update(log.copy(rating = rating.value, scheduledDays = result.log.scheduledDays))
        return updated
    }

    private fun isSameDay(epochSec: Long, now: Instant): Boolean =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()

    private fun evaluate(
        word: WordEntity,
        rating: Rating,
        source: String,
        at: Instant,
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
        )
    }

    private companion object {
        /** 同日同词最多两条记录——"忘了"紧接"学回"，再多就是手滑。 */
        const val MAX_SAME_DAY_REVIEWS = 2
    }
}

/** 同一秒里的两次点击也要有确定先后：按记录时间，再按行号。 */
private fun List<ReviewLogEntity>.inReviewOrder(): List<ReviewLogEntity> =
    sortedWith(compareBy({ it.reviewEpochSec }, { it.id }))

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
