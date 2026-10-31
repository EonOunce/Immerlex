package com.immerlex.core.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 正文颜色的两条查询。词在测验或提前复习里被排了期，不该让它在故事里换个脸色——
 * 那两行改的是排期，这一行回答的是"我在阅读里学到哪一步"。
 */
@RunWith(AndroidJUnit4::class)
class ReaderColourQueryTest {

    private lateinit var db: ImmerlexDatabase
    private val now = 1_700_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ImmerlexDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun quizAndEarlyReviewLeaveTheColourOfAWordInTheStoryAlone() = runBlocking {
        val read = seed("affect", LOG_SOURCE_READER, LOG_KIND_LEARN, now)
        val tested = seed("effect", "quiz", LOG_KIND_RECALL, now)
        val reviewed = seed("adopt", LOG_SOURCE_READER, LOG_KIND_RECALL, now - 100)
        val ids = listOf(read, tested, reviewed)

        assertEquals(
            "测验记的词不算阅读页学过",
            listOf(read, reviewed),
            db.reviewLogDao().readerLearnedIdsAmong(ids).sorted(),
        )
        assertEquals(
            "只有阅读页的回忆才算今天复习过",
            listOf(reviewed),
            db.reviewLogDao().readerRecalledIdsSinceAmong(ids, now - 500),
        )
        assertEquals(
            "昨天的回忆不该把词留在绿色里",
            emptyList<Long>(),
            db.reviewLogDao().readerRecalledIdsSinceAmong(ids, now + 1),
        )
    }

    private suspend fun seed(word: String, source: String, kind: String, atSec: Long): Long {
        val wordId = db.wordDao().insert(WordEntity(word = word, source = "test", createdAtSec = atSec))
        db.reviewLogDao().insert(
            ReviewLogEntity(
                wordId = wordId,
                rating = 3,
                source = source,
                reviewEpochSec = atSec,
                elapsedDays = 0,
                scheduledDays = 1,
                kind = kind,
            ),
        )
        return wordId
    }
}
