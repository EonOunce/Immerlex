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
 * 易错词是纯新增：v5 的词库与章节必须原样保留，新表从空开始，
 * 关联表只允许同一对词出现一次。
 */
@RunWith(AndroidJUnit4::class)
class TrickyMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v5KeepsDataAndAddsTrickyTables() {
        helper.createDatabase(DB_V5, 5).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('affect', '', '', '', 'manual', 1700000000, 0, 0, 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V5, 6, true, MIGRATION_5_6)

        db.query("SELECT word FROM words").use { cursor ->
            assertTrue("迁移弄丢了词库", cursor.moveToFirst())
            assertEquals("affect", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM tricky_words").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.execSQL("INSERT INTO tricky_words (word, gloss, ipa, createdAtSec) VALUES ('affect', '影响', '/əˈfekt/', 1700000000)")
        db.execSQL("INSERT INTO tricky_words (word, gloss, ipa, createdAtSec) VALUES ('effect', '效果', '/ɪˈfekt/', 1700000000)")
        db.execSQL("INSERT INTO tricky_links (wordA, wordB, createdAtSec) VALUES ('affect', 'effect', 1700000000)")
        try {
            db.execSQL("INSERT INTO tricky_links (wordA, wordB, createdAtSec) VALUES ('affect', 'effect', 1700000001)")
            fail("同一对易混词只能有一行")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
        }

        db.query("SELECT wordB FROM tricky_links WHERE wordA = 'affect'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("effect", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun v1RowsChainThroughAllFiveMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
                    "fsrsState, reps, lapses, intervalDays) " +
                    "VALUES ('effect', '', '', '', 'manual', 1700000000, 0, 0, 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            6,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        )

        db.query("SELECT COUNT(*) FROM tricky_words").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V5 = "tricky-migration-5.db"
        const val DB_V1 = "tricky-migration-1.db"
    }
}
