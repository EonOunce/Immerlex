package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionBuildersTest {

    private val harbour = word(1, "harbour", "港口", def = "a place where ships stay")
    private val balcony = word(2, "balcony", "阳台")
    private val lantern = word(3, "lantern", "灯笼")
    private val pebble = word(4, "pebble", "鹅卵石")
    private val pool = listOf(harbour, balcony, lantern, pebble)

    @Test
    fun enToZhAsksForTheChineseMeaningWithDistinctDistractors() {
        val question = EnToZhBuilder().build(harbour, QuestionContext(pool = pool, seed = 1))!!

        assertEquals(QuizType.EN_ZH, question.type)
        assertEquals("harbour", question.prompt)
        assertEquals(4, question.options.size)
        assertEquals(1, question.options.count { it.correct })
        assertEquals("港口", question.correctText)
        assertTrue(question.options.all { it.gloss.isNotBlank() })
    }

    @Test
    fun aWordNeedsAtLeastOneDistractorToBecomeAChoiceQuestion() {
        assertNull(EnToZhBuilder().build(harbour, QuestionContext(pool = listOf(harbour))))
        assertNull(ZhToEnBuilder().build(harbour, QuestionContext(pool = listOf(harbour))))
    }

    @Test
    fun zhToEnAsksForTheEnglishWordAndShowsGlosses() {
        val question = ZhToEnBuilder().build(harbour, QuestionContext(pool = pool, seed = 2))!!

        assertEquals(QuizType.ZH_EN, question.type)
        assertEquals("港口", question.prompt)
        assertEquals("harbour", question.correctText)
        assertEquals("港口", question.correctGloss)
    }

    @Test
    fun clozeUsesTheChapterSentenceWhenThereIsOne() {
        val context = QuestionContext(
            pool = pool,
            sentences = mapOf("harbour" to listOf("雨停之后，她走到____边。")),
            seed = 3,
        )

        val question = ClozeBuilder().build(harbour, context)!!

        assertEquals(QuizType.CLOZE, question.type)
        assertEquals("雨停之后，她走到____边。", question.prompt)
        assertEquals("harbour", question.correctText)
    }

    @Test
    fun clozeFallsBackToAnExampleProvidedByTheEnricher() {
        val context = QuestionContext(
            pool = pool,
            enrichment = QuizEnrichment(mapOf(1L to EnrichedWord(example = "The harbour is quiet."))),
            seed = 4,
        )

        val question = ClozeBuilder().build(harbour, context)!!

        assertTrue(question.prompt.contains(ClozeSentences.BLANK))
        assertFalse(question.prompt.contains("harbour"))
    }

    @Test
    fun clozeIsUnavailableWithoutASentenceOrExample() {
        assertNull(ClozeBuilder().build(harbour, QuestionContext(pool = pool)))
    }

    @Test
    fun definitionUsesTheBankDefinitionAndFallsBackToTheEnricher() {
        val fromBank = DefinitionBuilder().build(harbour, QuestionContext(pool = pool, seed = 5))!!
        assertEquals("a place where ships stay", fromBank.prompt)

        val bare = word(5, "quay", "码头")
        assertNull(DefinitionBuilder().build(bare, QuestionContext(pool = pool)))
        val enriched = DefinitionBuilder().build(
            bare,
            QuestionContext(
                pool = pool,
                enrichment = QuizEnrichment(mapOf(5L to EnrichedWord(definition = "a landing place"))),
                seed = 6,
            ),
        )!!
        assertEquals("a landing place", enriched.prompt)
    }

    @Test
    fun extraDistractorsFromTheEnricherJoinTheOptionPool() {
        val context = QuestionContext(
            pool = listOf(harbour),
            enrichment = QuizEnrichment(
                mapOf(1L to EnrichedWord(distractors = listOf(EnrichedDistractor("dock", "码头")))),
            ),
            seed = 7,
        )

        val question = EnToZhBuilder().build(harbour, context)!!
        assertTrue(question.options.any { it.text == "码头" })
        assertNotNull(question.options.firstOrNull { it.correct })
    }

    private fun word(id: Long, text: String, zh: String, def: String = "") = WordEntity(
        id = id,
        word = text,
        translation = zh,
        definition = def,
        source = "test",
        createdAtSec = 0,
    )
}
