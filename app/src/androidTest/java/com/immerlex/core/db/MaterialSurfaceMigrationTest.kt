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
 * v14 -> v15 把出题素材的主键从"词库那一行"换成"这个词本身"。
 * 删了再加回来的词、以及只存在于素材包里还没进词库的考研词，都不该因为换了一次行就重新向模型要一遍。
 */
@RunWith(AndroidJUnit4::class)
class MaterialSurfaceMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun materialFollowsTheWordNotTheBankRow() {
        helper.createDatabase(DB_V14, 14).apply {
            execSQL(wordSql("Harbour", 1700000000))
            execSQL(wordSql("ubiquitous", 1700000001))
            execSQL(materialSql(1, "a place of shelter", "The boat returned to the Harbour.", DESK, SEEDED))
            execSQL(materialSql(2, "present everywhere", "Phones are ubiquitous.", "[{\"w\":\"unique\",\"zh\":\"唯一的\"}]", SEEDED + 1))
            // 词早就删掉了，素材行还挂着：没有词面可折，这一行只能丢。
            execSQL(materialSql(999, "orphan material", "orphan example", "[]", SEEDED + 2))
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V14, 15, true, MIGRATION_14_15)

        db.query("SELECT COUNT(*) FROM quiz_material").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("找不到自己那个词的素材行只能丢掉", 2, cursor.getInt(0))
        }
        db.query(
            "SELECT definition, example, exampleZh, distractors, updatedAtSec FROM quiz_material WHERE word = 'harbour'",
        ).use { cursor ->
            assertTrue("首字母大写的写法要折成小写词面才能对上", cursor.moveToFirst())
            assertEquals("a place of shelter", cursor.getString(0))
            assertEquals("The boat returned to the Harbour.", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            assertEquals(DESK, cursor.getString(3))
            assertEquals(SEEDED, cursor.getLong(4))
        }
        db.query("SELECT definition FROM quiz_material WHERE word = 'ubiquitous'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("present everywhere", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun twoSpellingsOfTheSameWordShareOneRow() {
        helper.createDatabase(DB_V14, 14).apply {
            execSQL(wordSql("Adapt", 1700000000))
            execSQL(wordSql("adapt", 1700000001))
            execSQL(materialSql(1, "the first one", "example one", "[]", SEEDED))
            execSQL(materialSql(2, "the second one", "example two", "[]", SEEDED + 1))
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V14, 15, true, MIGRATION_14_15)

        db.query("SELECT COUNT(*) FROM quiz_material WHERE word = 'adapt'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT definition FROM quiz_material WHERE word = 'adapt'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("同一两个写法撞车时留最早那条", "the first one", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun v1RowsChainThroughToTheSurfaceTable() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            15,
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

    private fun wordSql(word: String, createdAt: Long) =
        "INSERT INTO words (word, phonetic, translation, definition, source, createdAtSec, " +
            "fsrsState, reps, lapses, intervalDays) " +
            "VALUES ('$word', '', '译文', '', 'manual', $createdAt, 3, 2, 0, 6)"

    private fun materialSql(bankId: Int, definition: String, example: String, distractors: String, atSec: Long) =
        "INSERT INTO quiz_material (wordId, definition, example, distractors, createdAtSec) " +
            "VALUES ($bankId, '$definition', '$example', '$distractors', $atSec)"

    private companion object {
        const val DB_V14 = "material-surface-migration-14.db"
        const val DB_V1 = "material-surface-migration-1.db"
        const val SEEDED = 1700000100L
        const val DESK = "[{\"w\":\"desk\",\"zh\":\"书桌\"}]"
    }
}
