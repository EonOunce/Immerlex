package com.immerlex.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.5 的书架只能"新长出来"：老章节必须原样归为连载（`kind = 'serial'`），
 * 否则升级后整本书会从书架上消失、生成下一章也会和旧章撞号。
 */
@RunWith(AndroidJUnit4::class)
class BookshelfMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v4ChaptersStaySerialAndPositionsRememberTheBook() {
        helper.createDatabase(DB_V4, 4).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (7, '第7章 · 长廊', '宫斗', 1700000000, '{}', '灯又灭了。')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V4, 5, true, MIGRATION_4_5)

        db.query("SELECT number, genre, kind FROM chapters").use { cursor ->
            assertTrue("迁移弄丢了老章节", cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals("宫斗", cursor.getString(1))
            assertEquals("老章节必须仍然是连载", KIND_SERIAL, cursor.getString(2))
        }

        db.execSQL("INSERT INTO reading_positions (`key`, chapterId, updatedAtSec) VALUES ('book:宫斗', 1, 1700000000)")
        try {
            db.execSQL("INSERT INTO reading_positions (`key`, chapterId, updatedAtSec) VALUES ('book:宫斗', 2, 1700000100)")
            fail("一本书只能有一行阅读位置")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // 主键靠这条约束兜底，切换书籍时由 REPLACE 语义覆盖。
        }
        db.query("SELECT chapterId FROM reading_positions WHERE `key` = 'book:宫斗'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        db.close()
    }

    @Test
    fun v1RowsChainThroughAllFourMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            5,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )

        db.query("SELECT kind FROM chapters").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(KIND_SERIAL, cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM reading_positions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V4 = "bookshelf-migration-4.db"
        const val DB_V1 = "bookshelf-migration-1.db"
    }
}
