package com.immerlex.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: `review_logs` gains the pre-rating card snapshot that makes
 * "amend a rating" possible without inflating the review count
 * (docs/adr/0004-chapter-word-units.md). Rows that already exist get the
 * [PRE_DUE_LEGACY] sentinel because their pre-rating state is unrecoverable.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preStability REAL")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preDifficulty REAL")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preState INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preDueEpochSec INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preLastReviewEpochSec INTEGER")
    }
}

/**
 * v2 -> v3: the story bible stops being a single overwritten `synopsis` string.
 * `bible_entries` holds the setting lines the user can edit, `character_states` holds one row per
 * person with their latest known situation, and `story_bibles.seedText` keeps the original
 * one-paragraph idea so the expansion can be re-rolled (docs/adr/0005-story-bible-from-one-prompt.md).
 * The old `charactersJson` / `synopsis` columns stay — dropping a column is not reliable on
 * minSdk 26 — and [com.immerlex.core.bible.StoryBibleRepository] empties them once they are adopted.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bible_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `genre` TEXT NOT NULL, `section` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`ordinal` INTEGER NOT NULL, `authoredByUser` INTEGER NOT NULL, `updatedAtSec` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `character_states` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `genre` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `firstChapterNumber` INTEGER NOT NULL, `lastChapterNumber` INTEGER NOT NULL, " +
                "`authoredByUser` INTEGER NOT NULL, `conflictChapterNumber` INTEGER NOT NULL, " +
                "`conflictText` TEXT NOT NULL, `updatedAtSec` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_character_states_genre_name` " +
                "ON `character_states` (`genre`, `name`)",
        )
        db.execSQL("ALTER TABLE `story_bibles` ADD COLUMN `seedText` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 -> v4: the daily loop needs two facts that cannot be re-derived later — whether a given day's
 * due queue ended up empty, and whether that day spent the week's single makeup credit.
 * "Did the user check in that day" is deliberately not stored; it already lives in `review_logs`.
 * `backup_runs` records each upload attempt so the settings page can show a truthful last result.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_loop` (`epochDay` INTEGER NOT NULL PRIMARY KEY, " +
                "`dueCleared` INTEGER NOT NULL, `makeupConsumed` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `backup_runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `atSec` INTEGER NOT NULL, `ok` INTEGER NOT NULL, `remoteName` TEXT NOT NULL, " +
                "`bytes` INTEGER NOT NULL, `detail` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_runs_atSec` ON `backup_runs` (`atSec`)")
    }
}

/**
 * v4 -> v5: v1.5 的书架与短篇。`chapters.kind` 区分连载章节（老行全是它）与单篇短篇；
 * `reading_positions` 每个书架条目一行，记住读到哪章，"上次在看的东西"取其中最新的一行。
 * 老章节靠列默认值直接归为 serial，不重建表、不动任何正文。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chapters` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'serial'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_positions` (`key` TEXT NOT NULL, " +
                "`chapterId` INTEGER NOT NULL, `updatedAtSec` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
