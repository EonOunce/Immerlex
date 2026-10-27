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

data class TodayQueue(
    val reviewWords: List<WordEntity>,
    val newWords: List<WordEntity>,
)

/** Read side of the schedule, so generators can be tested without a database. */
interface WordQueue {
    suspend fun todaysQueue(now: Instant, dailyNewLimit: Int): TodayQueue
}

class ScheduleService(
    private val wordDao: WordDao,
    private val logDao: ReviewLogDao,
    private val scheduler: Scheduler,
) : WordQueue {
    suspend fun review(
        word: WordEntity,
        rating: Rating,
        source: String,
        now: Instant = Instant.now(),
    ): WordEntity {
        val result = scheduler.reviewCard(word.toCard(now), rating, now)
        val updated = word.applyCard(result.card, rating, result.log.scheduledDays)
        wordDao.update(updated)
        logDao.insert(
            ReviewLogEntity(
                wordId = word.id,
                rating = rating.value,
                source = source,
                reviewEpochSec = now.epochSecond,
                elapsedDays = result.log.elapsedDays,
                scheduledDays = result.log.scheduledDays,
            ),
        )
        return updated
    }

    override suspend fun todaysQueue(now: Instant, dailyNewLimit: Int): TodayQueue {
        val due = wordDao.dueWords(now.epochSecond, 1000)
        return TodayQueue(
            reviewWords = due.filter { !it.isNew },
            newWords = due.filter { it.isNew }.take(dailyNewLimit),
        )
    }

    /** Interval a rating would produce, without touching the database. */
    fun preview(word: WordEntity, rating: Rating, now: Instant = Instant.now()): Int =
        scheduler.reviewCard(word.toCard(now), rating, now).log.scheduledDays
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
