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
 * 短篇的两个新列是纯附加：v7 已有的正文一行都不许动，老行靠默认值变成"没写主题、用预设腔调"。
 * 这两列是重写同一篇短篇时找回原要求的唯一依据，所以默认值必须是空串而不是 NULL。
 */
@RunWith(AndroidJUnit4::class)
class ShortPlanMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ImmerlexDatabase::class.java,
    )

    @Test
    fun v7KeepsEveryChapterAndGainsTheTwoBlankColumns() {
        helper.createDatabase(DB_V7, 7).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary, kind) " +
                    "VALUES (3, '第3章 · 长廊', '宫斗', 1700000000, '{}', '灯又灭了。', 'serial')",
            )
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary, kind) " +
                    "VALUES (0, '盐田的第七班', 'COLD', 1700000001, '{}', '', 'short')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_V7, 8, true, MIGRATION_7_8)

        db.query(
            "SELECT number, summary, kind, toneText, idea FROM chapters " +
                "WHERE genre = 'COLD' AND kind = 'short'",
        ).use { cursor ->
            assertTrue("迁移弄丢了短篇", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals("", cursor.getString(1))
            assertEquals(KIND_SHORT, cursor.getString(2))
            assertEquals("老短篇该用预设腔调", "", cursor.getString(3))
            assertEquals("老短篇没写过主题", "", cursor.getString(4))
        }
        db.query("SELECT COUNT(*) FROM chapters WHERE kind = 'serial' AND number = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("连载正文不该被动", 1, cursor.getInt(0))
        }

        // 新写的短篇把自己说过的两句话原样存下来，重写才找得回同一个要求。
        db.execSQL(
            "UPDATE chapters SET toneText = '句子要短，结尾不停在道理上', idea = '把迷路的眼镜猴送到渔港' " +
                "WHERE kind = 'short'",
        )
        db.query("SELECT toneText, idea FROM chapters WHERE kind = 'short'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("句子要短，结尾不停在道理上", cursor.getString(0))
            assertEquals("把迷路的眼镜猴送到渔港", cursor.getString(1))
        }
        db.close()
    }

    @Test
    fun v1RowsChainThroughAllSevenMigrations() {
        helper.createDatabase(DB_V1, 1).apply {
            execSQL(
                "INSERT INTO chapters (number, title, genre, createdAtSec, segmentsJson, summary) " +
                    "VALUES (1, '第1章', '宫斗', 1700000000, '{}', '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_V1,
            8,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
        )

        db.query("SELECT kind, toneText, idea FROM chapters").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("老章节归为连载", KIND_SERIAL, cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }
        db.close()
    }

    private companion object {
        const val DB_V7 = "short-plan-migration-7.db"
        const val DB_V1 = "short-plan-migration-1.db"
    }
}
