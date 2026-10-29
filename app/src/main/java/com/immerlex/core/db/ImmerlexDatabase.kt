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
    ],
    version = 8,
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

    companion object {
        fun create(context: Context): ImmerlexDatabase =
            Room.databaseBuilder(context, ImmerlexDatabase::class.java, "immerlex.db")
                .addMigrations(*MIGRATIONS)
                // 备份靠"先 checkpoint 再复制主库文件"取一致性快照，所以显式钉住 WAL，不靠默认值。
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
