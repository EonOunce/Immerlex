package com.immerlex.feature.reader

import com.immerlex.core.db.ChapterEntity
import com.immerlex.core.db.KIND_SERIAL
import com.immerlex.core.db.KIND_SHORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStateTest {

    private fun chapter(kind: String, number: Int, title: String, genre: String) = ChapterEntity(
        id = 7,
        number = number,
        title = title,
        genre = genre,
        createdAtSec = 0,
        segmentsJson = "{}",
        kind = kind,
    )

    private fun show(chapter: ChapterEntity?, book: String) = ReaderState().showing(
        chapter = chapter,
        blocks = emptyList(),
        bank = emptyMap(),
        chapters = emptyList(),
        books = emptyList(),
        shorts = emptyList(),
        book = book,
    )

    @Test
    fun aShortNeverBorrowsTheBookItWasReadAfter() {
        val state = show(chapter(KIND_SHORT, 0, "灯下的信", "冷"), book = "宫斗")
        assertEquals("", state.book)
        assertTrue(state.isShort)
        assertEquals("灯下的信", state.title)
    }

    @Test
    fun aSerialChapterKeepsItsOwnBook() {
        val state = show(chapter(KIND_SERIAL, 3, "雨夜的门", "宫斗"), book = "宫斗")
        assertEquals("宫斗", state.book)
        assertFalse(state.isShort)
        assertEquals(3, state.number)
    }

    @Test
    fun aBookWrittenNothingYetIsStillNamed() {
        val state = show(null, book = "科幻")
        assertEquals("科幻", state.book)
        assertFalse(state.hasChapter)
    }
}
