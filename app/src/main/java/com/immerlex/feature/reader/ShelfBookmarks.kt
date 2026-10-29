package com.immerlex.feature.reader

import com.immerlex.core.db.ChapterDao
import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.KIND_SHORT
import com.immerlex.core.db.READING_BOOK_PREFIX
import com.immerlex.core.db.READING_SHORT_PREFIX
import com.immerlex.core.db.ReadingPositionDao
import com.immerlex.core.db.ReadingPositionEntity
import com.immerlex.core.db.bookReadingKey
import com.immerlex.core.db.shortReadingKey
import com.immerlex.core.settings.AppPreferences
import java.time.Instant

/**
 * 书架与阅读位置的记账：书架上列哪几本书、下次打开落在哪一章、翻到某章后把落点写回哪里。
 * 连载按书记（`book:<题材>`），短篇按篇记（`short:<章 id>`），最新写入的时间戳即上次阅读处。
 */
class ShelfBookmarks(
    private val chapterDao: ChapterDao,
    private val positionDao: ReadingPositionDao,
    private val prefs: AppPreferences,
) {

    /**
     * 书架上的一本本连载书：章数来自 chapters，"读到第 N 章"与"上次什么时候"来自阅读位置。
     * 位置指向的章号一次批量取回，不在循环里查库。
     */
    suspend fun books(current: String): List<BookOption> {
        val rows = chapterDao.serialBooks()
        val positions = positionDao.all()
            .filter { it.key.startsWith(READING_BOOK_PREFIX) }
            .associateBy { it.key.removePrefix(READING_BOOK_PREFIX) }
        val remembered = positions.values.map { it.chapterId }.filter { it != 0L }
        val numbers = if (remembered.isEmpty()) {
            emptyMap()
        } else {
            chapterDao.optionsFor(remembered).associate { it.id to it.number }
        }
        val books = rows.map { row ->
            val position = positions[row.genre]
            BookOption(
                genre = row.genre,
                chapters = row.chapters,
                lastReadNumber = numbers[position?.chapterId ?: 0L] ?: 0,
                lastReadSec = position?.updatedAtSec ?: 0L,
            )
        }
        return if (books.any { it.genre == current }) books else books + BookOption(current, 0)
    }

    /**
     * 上次关掉应用时在看的东西：阅读位置表里最新的一行。指向的章节被删了，
     * 就回这本书的最后一章；整本书都没了，就回设置里的题材。
     */
    suspend fun remembered(): ChapterEntity? {
        val position = positionDao.latest() ?: return chapterDao.latest(prefs.genre)
        val stored = position.chapterId.takeIf { it != 0L }?.let { chapterDao.byId(it) }
        if (position.key.startsWith(READING_SHORT_PREFIX)) {
            return stored ?: chapterDao.latest(prefs.genre)
        }
        val genre = position.key.removePrefix(READING_BOOK_PREFIX)
        if (genre.isNotBlank()) prefs.genre = genre
        return stored?.takeIf { it.kind == KIND_SERIAL && it.genre == prefs.genre }
            ?: chapterDao.latest(prefs.genre)
    }

    /** 换一本书时优先回到它上次读到的那一章，没读过就给它最后一章。 */
    suspend fun lastChapterOf(genre: String): ChapterEntity? {
        val remembered = positionDao.byKey(bookReadingKey(genre))?.chapterId ?: 0
        return remembered.takeIf { it != 0L }?.let { chapterDao.byId(it) }
            ?.takeIf { it.kind == KIND_SERIAL && it.genre == genre }
            ?: chapterDao.latest(genre)
    }

    suspend fun remember(chapter: ChapterEntity?) {
        val key = when {
            chapter == null -> bookReadingKey(prefs.genre)
            chapter.kind == KIND_SHORT -> shortReadingKey(chapter.id)
            else -> bookReadingKey(chapter.genre)
        }
        positionDao.upsert(ReadingPositionEntity(key, chapter?.id ?: 0, Instant.now().epochSecond))
    }
}
