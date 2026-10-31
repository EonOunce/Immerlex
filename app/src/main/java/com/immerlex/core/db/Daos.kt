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

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<WordEntity>

    /** 干扰项来源：抽一批词，保证选项不总是同一组。 */
    @Query("SELECT * FROM words ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomWords(limit: Int): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WordEntity): Long

    @Update
    suspend fun update(entity: WordEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM words ORDER BY word")
    fun observeAll(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words ORDER BY word")
    suspend fun allWords(): List<WordEntity>

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

    @Query("SELECT id, number, title FROM chapters WHERE genre = :genre AND kind = 'serial' ORDER BY number DESC, id DESC")
    suspend fun titlesForGenre(genre: String): List<ChapterOption>

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM chapters WHERE genre = :genre AND kind = 'serial' ORDER BY number DESC LIMIT 1")
    suspend fun latest(genre: String): ChapterEntity?

    /** [excludeId] keeps a rewritten chapter out of its own recap; pass 0 for a new chapter. */
    @Query(
        "SELECT number, title, summary FROM chapters " +
            "WHERE genre = :genre AND kind = 'serial' AND id <> :excludeId ORDER BY number ASC, id ASC",
    )
    suspend fun recaps(genre: String, excludeId: Long): List<ChapterRecap>

    /** 书架上的一本连载书：一行一本，按最近更新时间排。 */
    @Query(
        "SELECT genre, COUNT(*) AS chapters, MAX(createdAtSec) AS updatedAtSec FROM chapters " +
            "WHERE kind = 'serial' GROUP BY genre ORDER BY updatedAtSec DESC",
    )
    suspend fun serialBooks(): List<BookRow>

    /** 书架上的短篇：标题 + 腔调（预设名，或 custom 时看 [ShortRow.toneText]）。 */
    @Query(
        "SELECT id, title, genre, toneText, createdAtSec FROM chapters " +
            "WHERE kind = 'short' ORDER BY createdAtSec DESC, id DESC",
    )
    suspend fun shortStories(): List<ShortRow>

    /** 书架卡片上的"读到第 N 章"：一次把所有阅读位置指向的章号取回，不在循环里查库。 */
    @Query("SELECT id, number, title FROM chapters WHERE id IN (:ids)")
    suspend fun optionsFor(ids: List<Long>): List<ChapterOption>

    /** 挖空题找句子用：最近写过的章节正文，连载与短篇都算。 */
    @Query("SELECT segmentsJson FROM chapters ORDER BY createdAtSec DESC, id DESC LIMIT :limit")
    suspend fun recentSegments(limit: Int): List<String>
}

/** A row of the bookshelf's serial section. */
data class BookRow(
    val genre: String,
    val chapters: Int,
    val updatedAtSec: Long,
)

/** A row of the bookshelf's short-story section. */
data class ShortRow(
    val id: Long,
    val title: String,
    /** 腔调预设的枚举名；用户自己写腔调时是 "custom"，原话在 [toneText]。 */
    val genre: String,
    val toneText: String,
    val createdAtSec: Long,
)

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

    @Query("SELECT * FROM review_logs WHERE id = :id")
    suspend fun byId(id: Long): ReviewLogEntity?

    @Query("DELETE FROM review_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 测验一次要算一批词的题型强度，逐词查会变成 N 次往返。 */
    @Query("SELECT * FROM review_logs WHERE wordId IN (:wordIds) ORDER BY reviewEpochSec ASC, id ASC")
    suspend fun forWords(wordIds: List<Long>): List<ReviewLogEntity>

    @Query(
        "SELECT DISTINCT wordId FROM review_logs " +
            "WHERE kind = 'learn' AND reviewEpochSec >= :sinceSec",
    )
    suspend fun learnedWordIdsSince(sinceSec: Long): List<Long>

    @Query(
        "SELECT DISTINCT wordId FROM review_logs " +
            "WHERE kind = 'recall' AND reviewEpochSec >= :sinceSec",
    )
    suspend fun recalledWordIdsSince(sinceSec: Long): List<Long>

    /** 今天碰过的词（学过或回忆过），提前复习从这里挑。 */
    @Query("SELECT DISTINCT wordId FROM review_logs WHERE reviewEpochSec >= :sinceSec")
    suspend fun touchedWordIdsSince(sinceSec: Long): List<Long>

    /**
     * 阅读页的颜色只听阅读页自己的记录：测验和提前复习排的是同一个词，
     * 但不改它在正文里的颜色。所以这两个查询都钉死 `source = 'reader'`。
     */
    @Query(
        "SELECT DISTINCT wordId FROM review_logs " +
            "WHERE source = 'reader' AND wordId IN (:wordIds)",
    )
    suspend fun readerLearnedIdsAmong(wordIds: List<Long>): List<Long>

    @Query(
        "SELECT DISTINCT wordId FROM review_logs " +
            "WHERE kind = 'recall' AND source = 'reader' AND reviewEpochSec >= :sinceSec " +
            "AND wordId IN (:wordIds)",
    )
    suspend fun readerRecalledIdsSinceAmong(wordIds: List<Long>, sinceSec: Long): List<Long>

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
interface ReadingPositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingPositionEntity)

    /** 上次在看的那一本/那一篇：时间最新的一行，同一秒按 key 决出唯一一条。 */
    @Query("SELECT * FROM reading_positions ORDER BY updatedAtSec DESC, key DESC LIMIT 1")
    suspend fun latest(): ReadingPositionEntity?

    @Query("SELECT * FROM reading_positions WHERE key = :key")
    suspend fun byKey(key: String): ReadingPositionEntity?

    /** 整个书架的位置：每张书卡都要知道自己上次读到哪一章、什么时候。 */
    @Query("SELECT * FROM reading_positions")
    suspend fun all(): List<ReadingPositionEntity>

    /** 删短篇时清掉它的位置行；连载书的位置行留着，由阅读页改到这本书剩下的最后一章。 */
    @Query("DELETE FROM reading_positions WHERE chapterId = :chapterId AND key LIKE 'short:%'")
    suspend fun clearShortByChapter(chapterId: Long)
}

@Dao
interface BackupRunDao {
    @Insert
    suspend fun insert(entity: BackupRunEntity): Long

    @Query("SELECT * FROM backup_runs ORDER BY atSec DESC, id DESC LIMIT 1")
    suspend fun latest(): BackupRunEntity?
}

@Dao
interface TrickyDao {
    @Query("SELECT * FROM tricky_words WHERE word = :word")
    suspend fun byWord(word: String): TrickyWordEntity?

    @Query("SELECT * FROM tricky_words WHERE word IN (:words)")
    suspend fun byWords(words: List<String>): List<TrickyWordEntity>

    @Query("SELECT * FROM tricky_words ORDER BY word")
    suspend fun all(): List<TrickyWordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TrickyWordEntity): Long

    /** 先标成易错但没拿到释义时，后来的词卡或词典可以补上，不覆盖已有的非空值。 */
    @Query(
        "UPDATE tricky_words SET gloss = CASE WHEN gloss = '' THEN :gloss ELSE gloss END, " +
            "ipa = CASE WHEN ipa = '' THEN :ipa ELSE ipa END WHERE word = :word",
    )
    suspend fun fillBlanks(word: String, gloss: String, ipa: String)

    @Query("DELETE FROM tricky_words WHERE word = :word")
    suspend fun delete(word: String)

    @Query("SELECT * FROM tricky_links ORDER BY wordA, wordB")
    suspend fun allLinks(): List<TrickyLinkEntity>

    @Query("SELECT * FROM tricky_links WHERE wordA = :word OR wordB = :word")
    suspend fun linksOf(word: String): List<TrickyLinkEntity>

    /** 测验一次要一批词的易混关系，逐词查会变成 N 次往返。 */
    @Query("SELECT * FROM tricky_links WHERE wordA IN (:words) OR wordB IN (:words)")
    suspend fun linksAmong(words: List<String>): List<TrickyLinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(entity: TrickyLinkEntity)

    @Query("DELETE FROM tricky_links WHERE (wordA = :a AND wordB = :b) OR (wordA = :b AND wordB = :a)")
    suspend fun deleteLink(a: String, b: String)

    @Query("DELETE FROM tricky_links WHERE wordA = :word OR wordB = :word")
    suspend fun deleteLinksOf(word: String)
}

@Dao
interface QuizMaterialDao {
    @Query("SELECT * FROM quiz_material WHERE wordId IN (:wordIds)")
    suspend fun byWordIds(wordIds: List<Long>): List<QuizMaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QuizMaterialEntity>)
}
