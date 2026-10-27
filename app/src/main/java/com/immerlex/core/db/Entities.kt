package com.immerlex.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "words", indices = [Index(value = ["word"], unique = true)])
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val phonetic: String = "",
    val translation: String = "",
    val definition: String = "",
    val source: String,
    val createdAtSec: Long,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueEpochSec: Long? = null,
    val lastReviewEpochSec: Long? = null,
    val fsrsState: Int = FSRS_STATE_LEARNING,
    val reps: Int = 0,
    val lapses: Int = 0,
    val intervalDays: Int = 0,
) {
    val isNew: Boolean get() = stability == null
}

const val FSRS_STATE_LEARNING = 0
const val FSRS_STATE_REVIEW = 1
const val FSRS_STATE_RELEARNING = 2

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val title: String,
    val genre: String,
    val createdAtSec: Long,
    val segmentsJson: String,
    val summary: String = "",
)

@Entity(
    tableName = "review_logs",
    indices = [Index("wordId"), Index("reviewEpochSec")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val rating: Int,
    val source: String,
    val reviewEpochSec: Long,
    val elapsedDays: Int,
    val scheduledDays: Int,
)

@Entity(tableName = "story_bibles", indices = [Index(value = ["genre"], unique = true)])
data class StoryBibleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    val charactersJson: String = "[]",
    val synopsis: String = "",
    val lastChapterNumber: Int = 0,
    val updatedAtSec: Long,
)
