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
    // 评分前的卡片状态。改评分必须回到这里、再用新评分在原时刻重算，
    // 否则会把"改一个分"累积成"多复习一次"。见 docs/adr/0004-chapter-word-units.md。
    val preStability: Double? = null,
    val preDifficulty: Double? = null,
    val preState: Int = FSRS_STATE_LEARNING,
    // 0 表示"当时还没有排期"（首次复习）；[PRE_DUE_LEGACY] 表示升级前的老记录，没有快照、不可回滚。
    val preDueEpochSec: Long = PRE_DUE_LEGACY,
    val preLastReviewEpochSec: Long? = null,
) {
    val hasPreRatingSnapshot: Boolean get() = preDueEpochSec != PRE_DUE_LEGACY
}

/** v1 rows carry no snapshot; ALTER TABLE backfills them with this sentinel. */
const val PRE_DUE_LEGACY = -1L

@Entity(tableName = "story_bibles", indices = [Index(value = ["genre"], unique = true)])
data class StoryBibleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    // Superseded by bible_entries + character_states + per-chapter summaries, but kept as columns:
    // ALTER TABLE DROP COLUMN is not reliable on minSdk 26, so the old cache is adopted once into
    // the new tables and then left blank rather than dropped.
    val charactersJson: String = "[]",
    val synopsis: String = "",
    val seedText: String = "",
    val lastChapterNumber: Int = 0,
    val updatedAtSec: Long,
)

/**
 * One line of the story bible. [authoredByUser] marks lines the user wrote or edited by hand —
 * automatic merging must never overwrite them, only flag a conflict (docs/adr/0005).
 */
@Entity(tableName = "bible_entries")
data class BibleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    /** [com.immerlex.core.bible.BibleSection] name. */
    val section: String,
    val text: String,
    val ordinal: Int,
    val authoredByUser: Boolean = false,
    val updatedAtSec: Long,
)

/** 人物状态表：一个人一行，[status] 是最新已知状态，由模型回报的"本章变化"合并而来。 */
@Entity(
    tableName = "character_states",
    indices = [Index(value = ["genre", "name"], unique = true)],
)
data class CharacterStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val genre: String,
    val name: String,
    val role: String = "",
    val status: String = "",
    val firstChapterNumber: Int = 0,
    val lastChapterNumber: Int = 0,
    val authoredByUser: Boolean = false,
    /** 用户手写条目与最新剧情对不上时写在这里；状态本身不动。0 = 没有冲突。 */
    val conflictChapterNumber: Int = 0,
    val conflictText: String = "",
    val updatedAtSec: Long,
)
