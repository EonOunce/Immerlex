package com.immerlex.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE word = :word LIMIT 1")
    suspend fun findByWord(word: String): WordEntity?

    @Query("SELECT * FROM words WHERE word IN (:words)")
    suspend fun findByWords(words: List<String>): List<WordEntity>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun findById(id: Long): WordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WordEntity): Long

    @Update
    suspend fun update(entity: WordEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM words ORDER BY word")
    fun observeAll(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE word LIKE '%' || :query || '%' OR translation LIKE '%' || :query || '%' ORDER BY word LIMIT :limit")
    suspend fun search(query: String, limit: Int = 100): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM words WHERE stability IS NULL")
    suspend fun newCount(): Int

    // Unreviewed words first, then by earliest due; NULL due sorts with the unreviewed head.
    @Query(
        """
        SELECT * FROM words
        WHERE dueEpochSec IS NULL OR dueEpochSec <= :nowSec
        ORDER BY CASE WHEN dueEpochSec IS NULL THEN 1 ELSE 0 END, dueEpochSec ASC
        LIMIT :limit
        """,
    )
    suspend fun dueWords(nowSec: Long, limit: Int): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words WHERE dueEpochSec IS NULL OR dueEpochSec <= :nowSec")
    suspend fun dueCount(nowSec: Long): Int

    @Query("SELECT * FROM words WHERE stability IS NOT NULL ORDER BY dueEpochSec ASC LIMIT :limit")
    suspend fun upcomingReviews(limit: Int): List<WordEntity>
}

@Dao
interface ChapterDao {
    @Insert
    suspend fun insert(entity: ChapterEntity): Long

    @Query("SELECT * FROM chapters WHERE genre = :genre ORDER BY number DESC LIMIT 1")
    suspend fun latest(genre: String): ChapterEntity?

    @Query("SELECT number, title, summary FROM chapters WHERE genre = :genre ORDER BY number DESC LIMIT :limit")
    suspend fun recentRecaps(genre: String, limit: Int): List<ChapterRecap>
}

data class ChapterRecap(
    val number: Int,
    val title: String,
    val summary: String,
)

@Dao
interface ReviewLogDao {    @Insert
    suspend fun insert(entity: ReviewLogEntity): Long

    @Query("SELECT * FROM review_logs WHERE wordId = :wordId ORDER BY reviewEpochSec ASC")
    suspend fun forWord(wordId: Long): List<ReviewLogEntity>

    @Query(
        """
        SELECT COUNT(*) as total,
               SUM(CASE WHEN rating >= 3 THEN 1 ELSE 0 END) as passed
        FROM review_logs
        WHERE reviewEpochSec >= :sinceSec
        """,
    )
    suspend fun statsSince(sinceSec: Long): ReviewStats

    @Query("SELECT rating, COUNT(*) as n FROM review_logs WHERE reviewEpochSec >= :sinceSec GROUP BY rating")
    suspend fun ratingHistogram(sinceSec: Long): List<RatingCount>

    @Query("DELETE FROM review_logs WHERE wordId = :wordId")
    suspend fun deleteForWord(wordId: Long)
}

data class ReviewStats(val total: Int, val passed: Int)
data class RatingCount(val rating: Int, val n: Int)

@Dao
interface StoryBibleDao {
    @Query("SELECT * FROM story_bibles WHERE genre = :genre LIMIT 1")
    suspend fun forGenre(genre: String): StoryBibleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StoryBibleEntity): Long
}
