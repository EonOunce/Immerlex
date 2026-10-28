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

    @Update
    suspend fun update(entity: ChapterEntity)

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun byId(id: Long): ChapterEntity?

    @Query("SELECT id, number, title FROM chapters WHERE genre = :genre ORDER BY number DESC, id DESC")
    suspend fun titlesForGenre(genre: String): List<ChapterOption>

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM chapters WHERE genre = :genre ORDER BY number DESC LIMIT 1")
    suspend fun latest(genre: String): ChapterEntity?

    /** [excludeId] keeps a rewritten chapter out of its own recap; pass 0 for a new chapter. */
    @Query("SELECT number, title, summary FROM chapters WHERE genre = :genre AND id <> :excludeId ORDER BY number ASC, id ASC")
    suspend fun recaps(genre: String, excludeId: Long): List<ChapterRecap>
}

/** A row of the chapter switcher on the reading page. */
data class ChapterOption(
    val id: Long,
    val number: Int,
    val title: String,
)

data class ChapterRecap(
    val number: Int,
    val title: String,
    val summary: String,
)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(entity: ReviewLogEntity): Long

    @Update
    suspend fun update(entity: ReviewLogEntity)

    @Query("SELECT * FROM review_logs WHERE wordId = :wordId ORDER BY reviewEpochSec ASC, id ASC")
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

    /** 按本地日切归谁由调用方的时区说了算，所以这里只取原始时刻，不在 SQL 里除 86400。 */
    @Query("SELECT wordId, reviewEpochSec FROM review_logs WHERE reviewEpochSec >= :sinceSec")
    suspend fun reviewsSince(sinceSec: Long): List<ReviewMoment>

    @Query("SELECT rating, COUNT(*) as n FROM review_logs WHERE reviewEpochSec >= :sinceSec GROUP BY rating")
    suspend fun ratingHistogram(sinceSec: Long): List<RatingCount>

    @Query("DELETE FROM review_logs WHERE wordId = :wordId")
    suspend fun deleteForWord(wordId: Long)
}

data class ReviewStats(val total: Int, val passed: Int)
data class RatingCount(val rating: Int, val n: Int)
data class ReviewMoment(val wordId: Long, val reviewEpochSec: Long)

@Dao
interface StoryBibleDao {
    @Query("SELECT * FROM story_bibles WHERE genre = :genre LIMIT 1")
    suspend fun forGenre(genre: String): StoryBibleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StoryBibleEntity): Long

    /** 升级前只存单一梗概与角色表的那些本子，仓库启动时搬一次。 */
    @Query("SELECT * FROM story_bibles WHERE charactersJson <> '[]' OR synopsis <> ''")
    suspend fun withLegacyCache(): List<StoryBibleEntity>
}

@Dao
interface BibleEntryDao {
    /** 分区间顺序由 [com.immerlex.core.bible.StoryBibleRepository] 按枚举排，这里只保证同区内稳定。 */
    @Query("SELECT * FROM bible_entries WHERE genre = :genre ORDER BY ordinal ASC, id ASC")
    suspend fun forGenre(genre: String): List<BibleEntryEntity>

    @Query("SELECT * FROM bible_entries WHERE id = :id")
    suspend fun byId(id: Long): BibleEntryEntity?

    @Insert
    suspend fun insert(entity: BibleEntryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<BibleEntryEntity>)

    @Update
    suspend fun update(entity: BibleEntryEntity)

    @Query("DELETE FROM bible_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 重掷：只清掉模型生成的条目，用户手写的原样留着。 */
    @Query("DELETE FROM bible_entries WHERE genre = :genre AND authoredByUser = 0")
    suspend fun deleteGenerated(genre: String)
}

@Dao
interface CharacterStateDao {
    @Query("SELECT * FROM character_states WHERE genre = :genre ORDER BY firstChapterNumber ASC, id ASC")
    suspend fun forGenre(genre: String): List<CharacterStateEntity>

    @Query("SELECT * FROM character_states WHERE id = :id")
    suspend fun byId(id: Long): CharacterStateEntity?

    @Insert
    suspend fun insert(entity: CharacterStateEntity): Long

    @Update
    suspend fun update(entity: CharacterStateEntity)

    @Query("DELETE FROM character_states WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DailyLoopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyLoopEntity)

    @Query("SELECT * FROM daily_loop WHERE epochDay = :epochDay")
    suspend fun byDay(epochDay: Long): DailyLoopEntity?

    /** 连续天数最多回看一年，多余的行不参与计算。 */
    @Query("SELECT * FROM daily_loop WHERE epochDay >= :fromDay ORDER BY epochDay ASC")
    suspend fun since(fromDay: Long): List<DailyLoopEntity>
}

@Dao
interface BackupRunDao {
    @Insert
    suspend fun insert(entity: BackupRunEntity): Long

    @Query("SELECT * FROM backup_runs ORDER BY atSec DESC, id DESC LIMIT 1")
    suspend fun latest(): BackupRunEntity?
}
