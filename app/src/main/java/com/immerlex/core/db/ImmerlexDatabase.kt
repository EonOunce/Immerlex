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
    ],
    version = 1,
    exportSchema = false,
)
abstract class ImmerlexDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun chapterDao(): ChapterDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun storyBibleDao(): StoryBibleDao

    companion object {
        fun create(context: Context): ImmerlexDatabase =
            Room.databaseBuilder(context, ImmerlexDatabase::class.java, "immerlex.db").build()
    }
}
