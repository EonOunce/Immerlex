package com.immerlex.core.fsrs

import java.time.Duration
import java.time.Instant
import kotlin.math.E
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * FSRS-6 scheduler, ported from open-spaced-repetition/py-fsrs (MIT).
 * Any formula change must keep FsrsReferenceTest green against vectors.json.
 */
class Scheduler(
    parameters: DoubleArray = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumInterval: Int = 36500,
    val enableFuzzing: Boolean = true,
    private val random: Random = Random.Default,
) {
    val parameters: DoubleArray = parameters.copyOf()

    private val decay = -this.parameters[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    init {
        require(this.parameters.size == DEFAULT_PARAMETERS.size) {
            "Expected ${DEFAULT_PARAMETERS.size} parameters, got ${this.parameters.size}"
        }
        this.parameters.forEachIndexed { i, p ->
            require(p in LOWER_BOUNDS[i]..UPPER_BOUNDS[i]) { "parameters[$i]=$p out of bounds" }
        }
    }

    fun retrievability(card: Card, now: Instant): Double {
        val s = card.stability ?: return 0.0
        val last = card.lastReview ?: return 0.0
        val elapsedDays = max(0, daysBetween(last, now))
        return (1 + factor * elapsedDays / s).pow(decay)
    }

    fun reviewCard(
        card: Card,
        rating: Rating,
        now: Instant,
        durationMillis: Long? = null,
    ): ReviewResult {
        val stateBefore = card.state
        var c = card
        val daysSinceLastReview = c.lastReview?.let { daysBetween(it, now) }
        val nextInterval: Duration = when (c.state) {
            CardState.LEARNING -> reviewLearning(c, rating, daysSinceLastReview, now).let {
                c = it.first; it.second
            }
            CardState.REVIEW -> reviewReview(c, rating, daysSinceLastReview, now).let {
                c = it.first; it.second
            }
            CardState.RELEARNING -> reviewRelearning(c, rating, daysSinceLastReview, now).let {
                c = it.first; it.second
            }
        }

        var interval = nextInterval
        if (enableFuzzing && c.state == CardState.REVIEW) {
            interval = fuzzedInterval(interval)
        }
        c = c.copy(due = now.plus(interval), lastReview = now)
        val log = ReviewLog(
            rating = rating,
            reviewDatetime = now,
            reviewDurationMillis = durationMillis,
            elapsedDays = daysSinceLastReview ?: 0,
            scheduledDays = interval.toDays().toInt(),
            stateBeforeReview = stateBefore,
        )
        return ReviewResult(c, log)
    }

    private fun reviewLearning(
        card: Card,
        rating: Rating,
        daysSinceLastReview: Int?,
        now: Instant,
    ): Pair<Card, Duration> {
        var c = card
        val step = c.step ?: 0
        val s = c.stability
        val d = c.difficulty
        if (s == null || d == null) {
            c = c.copy(
                stability = initialStability(rating),
                difficulty = initialDifficulty(rating, clamp = true),
            )
        } else if (daysSinceLastReview != null && daysSinceLastReview < 1) {
            c = c.copy(
                stability = shortTermStability(s, rating),
                difficulty = nextDifficulty(d, rating),
            )
        } else {
            c = c.copy(
                stability = nextStability(d, s, retrievability(c, now), rating),
                difficulty = nextDifficulty(d, rating),
            )
        }

        if (learningSteps.isEmpty() || (step >= learningSteps.size && rating != Rating.AGAIN)) {
            return c.copy(state = CardState.REVIEW, step = null) to dayInterval(c.stability!!)
        }
        return when (rating) {
            Rating.AGAIN -> c.copy(step = 0) to learningSteps[0]
            Rating.HARD -> {
                val ivl = if (step == 0 && learningSteps.size == 1) {
                    Duration.ofMillis((learningSteps[0].toMillis() * 1.5).toLong())
                } else if (step == 0 && learningSteps.size >= 2) {
                    Duration.ofMillis((learningSteps[0].toMillis() + learningSteps[1].toMillis()) / 2)
                } else {
                    learningSteps[step]
                }
                c to ivl
            }
            Rating.GOOD -> if (step + 1 == learningSteps.size) {
                c.copy(state = CardState.REVIEW, step = null) to dayInterval(c.stability!!)
            } else {
                c.copy(step = step + 1) to learningSteps[step + 1]
            }
            Rating.EASY -> c.copy(state = CardState.REVIEW, step = null) to dayInterval(c.stability!!)
        }
    }

    private fun reviewReview(
        card: Card,
        rating: Rating,
        daysSinceLastReview: Int?,
        now: Instant,
    ): Pair<Card, Duration> {
        var c = card
        val s = c.stability!!
        val d = c.difficulty!!
        c = if (daysSinceLastReview != null && daysSinceLastReview < 1) {
            c.copy(stability = shortTermStability(s, rating))
        } else {
            c.copy(stability = nextStability(d, s, retrievability(c, now), rating))
        }
        c = c.copy(difficulty = nextDifficulty(c.difficulty!!, rating))

        if (rating == Rating.AGAIN && relearningSteps.isNotEmpty()) {
            return c.copy(state = CardState.RELEARNING, step = 0) to relearningSteps[0]
        }
        return c to dayInterval(c.stability!!)
    }

    private fun reviewRelearning(
        card: Card,
        rating: Rating,
        daysSinceLastReview: Int?,
        now: Instant,
    ): Pair<Card, Duration> {
        var c = card
        val step = c.step ?: 0
        if (daysSinceLastReview != null && daysSinceLastReview < 1) {
            c = c.copy(
                stability = shortTermStability(c.stability!!, rating),
                difficulty = nextDifficulty(c.difficulty!!, rating),
            )
        } else {
            c = c.copy(
                stability = nextStability(c.difficulty!!, c.stability!!, retrievability(c, now), rating),
                difficulty = nextDifficulty(c.difficulty!!, rating),
            )
        }

        if (relearningSteps.isEmpty() || (step >= relearningSteps.size && rating != Rating.AGAIN)) {
            return c.copy(state = CardState.REVIEW, step = null) to dayInterval(c.stability!!)
        }
        return when (rating) {
            Rating.AGAIN -> c.copy(step = 0) to relearningSteps[0]
            Rating.HARD -> {
                val ivl = if (step == 0 && relearningSteps.size == 1) {
                    Duration.ofMillis((relearningSteps[0].toMillis() * 1.5).toLong())
                } else if (step == 0 && relearningSteps.size >= 2) {
                    Duration.ofMillis((relearningSteps[0].toMillis() + relearningSteps[1].toMillis()) / 2)
                } else {
                    relearningSteps[step]
                }
                c to ivl
            }
            Rating.GOOD -> if (step + 1 == relearningSteps.size) {
                c.copy(state = CardState.REVIEW, step = null) to dayInterval(c.stability!!)
            } else {
                c.copy(step = step + 1) to relearningSteps[step + 1]
            }
            Rating.EASY -> c.copy(state = CardState.REVIEW, step = null) to dayInterval(c.stability!!)
        }
    }

    private fun initialStability(rating: Rating): Double =
        max(parameters[rating.value - 1], STABILITY_MIN)

    private fun initialDifficulty(rating: Rating, clamp: Boolean): Double {
        val raw = parameters[4] - E.pow(parameters[5] * (rating.value - 1)) + 1
        return if (clamp) clampDifficulty(raw) else raw
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val arg1 = initialDifficulty(Rating.EASY, clamp = false)
        val delta = -(parameters[6] * (rating.value - 3))
        val arg2 = difficulty + (10.0 - difficulty) * delta / 9.0
        return clampDifficulty(parameters[7] * arg1 + (1 - parameters[7]) * arg2)
    }

    private fun clampDifficulty(difficulty: Double): Double =
        difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun clampStability(stability: Double): Double = max(stability, STABILITY_MIN)

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = E.pow(parameters[17] * (rating.value - 3 + parameters[18])) *
            stability.pow(-parameters[19])
        if (rating.value >= Rating.HARD.value) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double = clampStability(
        if (rating == Rating.AGAIN) {
            nextForgetStability(difficulty, stability, retrievability)
        } else {
            nextRecallStability(difficulty, stability, retrievability, rating)
        }
    )

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val longTerm = parameters[11] *
            d.pow(-parameters[12]) *
            ((s + 1).pow(parameters[13]) - 1) *
            E.pow((1 - r) * parameters[14])
        val shortTermCap = s / E.pow(parameters[17] * parameters[18])
        return min(longTerm, shortTermCap)
    }

    private fun nextRecallStability(
        d: Double,
        s: Double,
        r: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) parameters[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) parameters[16] else 1.0
        return s * (
            1 + E.pow(parameters[8]) * (11 - d) * s.pow(-parameters[9]) *
                (E.pow((1 - r) * parameters[10]) - 1) * hardPenalty * easyBonus
            )
    }

    private fun dayInterval(stability: Double): Duration =
        Duration.ofDays(nextIntervalDays(stability).toLong())

    private fun nextIntervalDays(stability: Double): Int {
        val raw = (stability / factor) * (desiredRetention.pow(1.0 / decay) - 1)
        val rounded = Math.rint(raw).toInt()
        return rounded.coerceIn(1, maximumInterval)
    }

    private fun fuzzedInterval(interval: Duration): Duration {
        val days = interval.toDays().toInt()
        if (days < 2.5) return interval
        var delta = 1.0
        for (range in FUZZ_RANGES) {
            delta += range.factor * max(min(days.toDouble(), range.end) - range.start, 0.0)
        }
        var minIvl = Math.rint(days - delta).toInt()
        var maxIvl = Math.rint(days + delta).toInt()
        minIvl = max(2, minIvl)
        maxIvl = min(maxIvl, maximumInterval)
        minIvl = min(minIvl, maxIvl)
        val fuzzed = random.nextDouble() * (maxIvl - minIvl + 1) + minIvl
        return Duration.ofDays(min(Math.rint(fuzzed).toLong(), maximumInterval.toLong()))
    }

    private fun daysBetween(from: Instant, to: Instant): Int =
        Duration.between(from, to).seconds.floorDiv(86400L).toInt()

    private data class FuzzRange(val start: Double, val end: Double, val factor: Double)

    companion object {
        const val STABILITY_MIN = 0.001
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0

        val DEFAULT_PARAMETERS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        private val LOWER_BOUNDS = doubleArrayOf(
            0.001, 0.001, 0.001, 0.001, 1.0, 0.001, 0.001, 0.001, 0.0, 0.0,
            0.001, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1,
        )

        private val UPPER_BOUNDS = doubleArrayOf(
            100.0, 100.0, 100.0, 100.0, 10.0, 4.0, 4.0, 0.75, 4.5, 0.8,
            3.5, 5.0, 0.25, 0.9, 4.0, 1.0, 6.0, 2.0, 2.0, 0.8, 0.8,
        )

        private val FUZZ_RANGES = listOf(
            FuzzRange(2.5, 7.0, 0.15),
            FuzzRange(7.0, 20.0, 0.1),
            FuzzRange(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}
