package com.immerlex.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WordEntity::class,
        ChapterEntity::class,
        ReviewLogEntity::class,
        StoryBibleEntity::class,
        BibleEntryEntity::class,
        CharacterStateEntity::class,
        DailyLoopEntity::class,
        BackupRunEntity::class,
        ReadingPositionEntity::class,
        TrickyWordEntity::class,
        TrickyLinkEntity::class,
        RoommateProfileEntity::class,
        RoommateAssetEntity::class,
        RoommateEventEntity::class,
        RoommateLedgerEntity::class,
        RoommateHabitEntity::class,
        RoommateEvidenceEntity::class,
        RoommateWorkEntity::class,
        RoommateStateEntity::class,
        RoommateLineEntity::class,
        RoommateDailyEntity::class,
        RoommateOfferEntity::class,
        RoommatePetGiftEntity::class,
        QuizMaterialEntity::class,
        QuizSessionEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class ImmerlexDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun chapterDao(): ChapterDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun storyBibleDao(): StoryBibleDao
    abstract fun bibleEntryDao(): BibleEntryDao
    abstract fun characterStateDao(): CharacterStateDao
    abstract fun dailyLoopDao(): DailyLoopDao
    abstract fun backupRunDao(): BackupRunDao
    abstract fun readingPositionDao(): ReadingPositionDao
    abstract fun trickyDao(): TrickyDao
    abstract fun roommateProfileDao(): RoommateProfileDao
    abstract fun roommateAssetDao(): RoommateAssetDao
    abstract fun roommateEventDao(): RoommateEventDao
    abstract fun roommateLedgerDao(): RoommateLedgerDao
    abstract fun roommateHabitDao(): RoommateHabitDao
    abstract fun roommateEvidenceDao(): RoommateEvidenceDao
    abstract fun roommateWorkDao(): RoommateWorkDao
    abstract fun roommateStateDao(): RoommateStateDao
    abstract fun roommateSettlementDao(): RoommateSettlementDao
    abstract fun roommateLineDao(): RoommateLineDao
    abstract fun roommateDailyDao(): RoommateDailyDao
    abstract fun roommateOfferDao(): RoommateOfferDao
    abstract fun roommatePetGiftDao(): RoommatePetGiftDao
    abstract fun quizMaterialDao(): QuizMaterialDao
    abstract fun quizSessionDao(): QuizSessionDao

    companion object {
        const val NAME = "immerlex.db"

        fun create(context: Context): ImmerlexDatabase {
            // 上次恢复备份如果在换文件的中途被杀，这里先把旧库放回原位再开库。
            DbFileSwap.recover(context.getDatabasePath(NAME))
            return Room.databaseBuilder(context, ImmerlexDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // 备份靠"先 checkpoint 再复制主库文件"取一致性快照，所以显式钉住 WAL，不靠默认值。
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
