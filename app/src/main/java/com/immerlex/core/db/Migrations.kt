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

val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
