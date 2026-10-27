package com.immerlex.core.fsrs

import java.time.Instant

enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
}

enum class CardState {
    LEARNING,
    REVIEW,
    RELEARNING,
}

data class Card(
    val state: CardState = CardState.LEARNING,
    val step: Int? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val due: Instant = Instant.now(),
    val lastReview: Instant? = null,
)

data class ReviewLog(
    val rating: Rating,
    val reviewDatetime: Instant,
    val reviewDurationMillis: Long?,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val stateBeforeReview: CardState,
)

data class ReviewResult(
    val card: Card,
    val log: ReviewLog,
)
