package com.immerlex.core.quiz

import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.testsupport.FakeQuizMaterialDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizMaterialStoreTest {

    private val harbour = WordEntity(id = 1, word = "harbour", translation = "港口", source = "test", createdAtSec = 0)
    private val balcony = WordEntity(id = 2, word = "balcony", translation = "阳台", source = "test", createdAtSec = 0)

    @Test
    fun theWholeCandidatePoolSurvivesAWriteAndARead() = runBlocking {
        val dao = FakeQuizMaterialDao()
        val store = QuizMaterialStore(dao)
        val pool = (1..6).map { EnrichedDistractor("dock$it", "释义$it") }

        store.save(QuizEnrichment(mapOf(1L to EnrichedWord("a place where ships stay", "船进了 harbour。", pool))), atSec = 100)
        val loaded = store.load(listOf(harbour, balcony))

        assertEquals(setOf(1L), loaded.keys)
        assertEquals("a place where ships stay", loaded[1L]?.definition)
        assertEquals(pool, loaded[1L]?.distractors)
        assertEquals(100L, dao.items.getValue(1L).createdAtSec)
    }

    @Test
    fun wordsWithNoStoredRowAreSimplyAbsent() = runBlocking {
        val store = QuizMaterialStore(FakeQuizMaterialDao())

        assertTrue(store.load(listOf(harbour, balcony)).isEmpty())
    }

    @Test
    fun aCorruptRowDegradesToNoCandidatesWithoutLosingTheRest() = runBlocking {
        val dao = FakeQuizMaterialDao()
        dao.items[1L] = QuizMaterialEntity(wordId = 1, definition = "a place where ships stay", distractors = "{oops", createdAtSec = 0)

        val entry = QuizMaterialStore(dao).load(listOf(harbour)).getValue(1L)

        assertTrue(entry.distractors.isEmpty())
        assertEquals("a place where ships stay", entry.definition)
    }
}
