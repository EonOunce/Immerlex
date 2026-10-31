package com.immerlex.core.schedule

import com.immerlex.core.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderToneTest {

    private val now = 1_700_000_000L

    private fun word(id: Long, due: Long?) = WordEntity(
        id = id,
        word = "reluctant",
        source = "txt",
        createdAtSec = now - 1000,
        dueEpochSec = due,
    )

    @Test
    fun aWordTheReaderNeverTouchedIsRawEvenWhenItIsDue() {
        val tone = readerTone(word(1, now - 1), ReaderMarks(), now)
        assertEquals(ReaderTone.NEW, tone)
    }

    @Test
    fun learningItInTheReaderTurnsItGreen() {
        val marks = ReaderMarks(learned = setOf(1L))
        assertEquals(ReaderTone.LEARNED, readerTone(word(1, now + 86_000), marks, now))
    }

    @Test
    fun aWordNotYetScheduledIsGreenOnceLearned() {
        val marks = ReaderMarks(learned = setOf(1L))
        assertEquals(ReaderTone.LEARNED, readerTone(word(1, null), marks, now))
    }

    @Test
    fun dueTodayInTheReaderTurnsItBlue() {
        val marks = ReaderMarks(learned = setOf(1L))
        assertEquals(ReaderTone.DUE, readerTone(word(1, now), marks, now))
    }

    @Test
    fun reviewingItTodayTurnsItGreenEvenIfFsrsPutsItBackOnToday() {
        val marks = ReaderMarks(learned = setOf(1L), reviewedToday = setOf(1L))
        assertEquals(ReaderTone.LEARNED, readerTone(word(1, now), marks, now))
    }

    @Test
    fun aWordTheModelInventedAndTheBankDoesNotHaveIsRaw() {
        assertEquals(ReaderTone.NEW, readerTone(null, ReaderMarks(), now))
    }
}
