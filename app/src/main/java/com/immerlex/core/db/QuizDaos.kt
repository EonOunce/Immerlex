package com.immerlex.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuizMaterialDao {
    /** 词面必须已经洗过小写与首尾空格：这一行的主键就是它。 */
    @Query("SELECT * FROM quiz_material WHERE word IN (:words)")
    suspend fun byWords(words: List<String>): List<QuizMaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QuizMaterialEntity>)

    @Query("SELECT COUNT(*) FROM quiz_material")
    suspend fun count(): Int

    /** 导出素材包用：整表读出来，一个词一行。 */
    @Query("SELECT * FROM quiz_material ORDER BY word")
    suspend fun all(): List<QuizMaterialEntity>

    /** 只取词面：给别处出包的人当排除表，不需要释义那一堆列。 */
    @Query("SELECT word FROM quiz_material ORDER BY word")
    suspend fun allWords(): List<String>
}

@Dao
interface QuizSessionDao {
    @Query("SELECT * FROM quiz_session WHERE mode = :mode")
    suspend fun byMode(mode: String): QuizSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuizSessionEntity)

    @Query("DELETE FROM quiz_session WHERE mode = :mode")
    suspend fun delete(mode: String)

    @Query("SELECT EXISTS(SELECT 1 FROM quiz_session WHERE mode = :mode)")
    suspend fun exists(mode: String): Boolean
}

/**
 * 做完的一组题的成绩，只进不出：一组一行，战绩页按时间倒序读最近几组。
 * 老组留着不删是有原因的——「破了自己纪录」要的就是这一串历史。
 */
@Dao
interface QuizRunDao {
    @Insert
    suspend fun insert(entity: QuizRunEntity): Long

    @Query("SELECT * FROM quiz_runs ORDER BY finishedAtSec DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<QuizRunEntity>

    /** 插这一组之前先问一句：这个入口以前最好对几个。一行都没有时返回 null。 */
    @Query("SELECT MAX(correct) FROM quiz_runs WHERE mode = :mode")
    suspend fun bestCorrect(mode: String): Int?
}
