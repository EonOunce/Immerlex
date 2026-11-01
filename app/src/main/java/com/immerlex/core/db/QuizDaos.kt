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
