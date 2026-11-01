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

        store.save(
            QuizEnrichment(mapOf(1L to EnrichedWord("a place where ships stay", "船进了 harbour。", distractors = pool))),
            listOf(harbour, balcony),
            atSec = 100,
        )
        val loaded = store.load(listOf(harbour, balcony))

        assertEquals(setOf(1L), loaded.keys)
        assertEquals("a place where ships stay", loaded[1L]?.definition)
        assertEquals(pool, loaded[1L]?.distractors)
        assertEquals(100L, dao.items.getValue("harbour").updatedAtSec)
    }

    @Test
    fun aWordDeletedAndAddedAgainStillKnowsItsMaterial() = runBlocking {
        val dao = FakeQuizMaterialDao()
        QuizMaterialStore(dao).save(
            QuizEnrichment(mapOf(1L to EnrichedWord(definition = "a place where ships stay"))),
            listOf(harbour),
            atSec = 100,
        )

        // 词库里的 harbour 被删掉又重进，这是一条全新的行、一个新的 id。
        val reAdded = harbour.copy(id = 41, word = "Harbour")
        val loaded = QuizMaterialStore(dao).load(listOf(reAdded))

        assertEquals("a place where ships stay", loaded[41L]?.definition)
    }

    @Test
    fun aWordOutsideThisBatchIsNotWritten() = runBlocking {
        val dao = FakeQuizMaterialDao()

        QuizMaterialStore(dao).save(
            QuizEnrichment(mapOf(9L to EnrichedWord(definition = "没有这个词的素材"))),
            listOf(harbour, balcony),
            atSec = 100,
        )

        assertTrue("词面查不到就不该凭 id 造一行", dao.items.isEmpty())
    }

    @Test
    fun wordsWithNoStoredRowAreSimplyAbsent() = runBlocking {
        val store = QuizMaterialStore(FakeQuizMaterialDao())

        assertTrue(store.load(listOf(harbour, balcony)).isEmpty())
    }

    @Test
    fun collocationsSurviveTheStoreRoundTrip() = runBlocking {
        val dao = FakeQuizMaterialDao()
        val collocations = listOf(EnrichedDistractor("harbour wall", "防波堤"))

        QuizMaterialStore(dao).save(
            QuizEnrichment(mapOf(1L to EnrichedWord(collocations = collocations))),
            listOf(harbour),
            atSec = 100,
        )
        val loaded = QuizMaterialStore(dao).load(listOf(harbour))

        assertEquals("素材包给的搭配要能原样读回来，不然搭配题永远没数据", collocations, loaded[1L]?.collocations)
    }

    @Test
    fun aCorruptRowDegradesToNoCandidatesWithoutLosingTheRest() = runBlocking {
        val dao = FakeQuizMaterialDao()
        dao.items["harbour"] = QuizMaterialEntity(
            word = "harbour",
            definition = "a place where ships stay",
            distractors = "{oops",
            updatedAtSec = 0,
        )

        val entry = QuizMaterialStore(dao).load(listOf(harbour)).getValue(1L)

        assertTrue(entry.distractors.isEmpty())
        assertEquals("a place where ships stay", entry.definition)
    }
}
