package com.immerlex.core.word

import com.immerlex.core.db.FSRS_STATE_LEARNING
import com.immerlex.core.db.WordEntity

enum class Mastery { NEW, LEARNING, MASTERED }

/** Stability below which a reviewed word still counts as "学习中". */
const val MASTERY_STABILITY = 30.0

fun WordEntity.mastery(): Mastery = when {
    stability == null || fsrsState == FSRS_STATE_LEARNING -> Mastery.NEW
    (stability ?: 0.0) < MASTERY_STABILITY -> Mastery.LEARNING
    else -> Mastery.MASTERED
}

/** Token used by the chapter JSON contract. */
fun Mastery.toSegmentToken(): String = when (this) {
    Mastery.NEW -> "new"
    Mastery.LEARNING -> "lea"
    Mastery.MASTERED -> "mas"
}
