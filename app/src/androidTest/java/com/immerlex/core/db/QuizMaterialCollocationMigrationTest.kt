package com.immerlex.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v15 -> v16 只给 `quiz_material` 加一列搭配短语。老行留空列表：
 * 搭配题对它们不可用，不拿别的字段凑，也不把旧行当成坏行。
 */
@RunWith(AndroidJUnit4::class)
class QuizMaterialCollocationMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun oldRowsSurviveAndSayTheyHaveNoCollocations() {
        helper.createDatabase(DB_V15, 15).apply {
            execSQL(
                "INSERT INTO quiz_material (word, definition, example, exampleZh, distractors, updatedAtSec) " +
                    "VALUES ('harbour', 'a place of shelter', 'The boat returned to the harbour.', " +
                    "'船回到了港湾。', '[{\"w\":\"dock\",\"zh\":\"码头\"}]', 1700000100)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V15, 16, true, MIGRATION_15_16)

        db.query(
            "SELECT definition, example, exampleZh, distractors, collocations, updatedAtSec FROM quiz_material",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("a place of shelter", cursor.getString(0))
            assertEquals("The boat returned to the harbour.", cursor.getString(1))
            assertEquals("船回到了港湾。", cursor.getString(2))
            assertEquals("[{\"w\":\"dock\",\"zh\":\"码头\"}]", cursor.getString(3))
            assertEquals("旧行没有搭配就说没有", "[]", cursor.getString(4))
            assertEquals(1700000100, cursor.getLong(5))
        }
        db.execSQL(
            "INSERT INTO quiz_material (word, definition, example, exampleZh, distractors, collocations, updatedAtSec) " +
                "VALUES ('take', '', '', '', '[]', '[{\"w\":\"take measures\",\"zh\":\"采取措施\"}]', 1700000101)",
        )
        db.query("SELECT collocations FROM quiz_material WHERE word = 'take'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[{\"w\":\"take measures\",\"zh\":\"采取措施\"}]", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun anOldLibraryChainsAllTheWayToTheNewColumn() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            16,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
        )

        db.query("SELECT title FROM chapters WHERE number = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第1章", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM quiz_material").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_V15 = "quiz-material-collocation-migration-15.db"
        const val DB_V1 = "quiz-material-collocation-migration-1.db"
    }
}
