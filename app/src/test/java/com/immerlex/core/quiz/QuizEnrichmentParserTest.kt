package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEnrichmentParserTest {

    private val words = listOf(
        WordEntity(id = 1, word = "harbour", translation = "港口", source = "test", createdAtSec = 0),
        WordEntity(id = 2, word = "balcony", translation = "阳台", source = "test", createdAtSec = 0),
    )

    @Test
    fun mapsItemsBackToWordsIgnoringCaseAndUnknownEntries() {
        val raw = """
            {
              "items": [
                {
                  "word": "Harbour",
                  "definition": "a place where ships stay",
                  "example": "The harbour is quiet.",
                  "distractors": [
                    {"word": "dock", "zh": "码头"},
                    {"word": "port", "zh": "港口"}
                  ]
                },
                {"word": "not-in-bank", "definition": "ignored"}
              ]
            }
        """.trimIndent()

        val enrichment = QuizEnrichmentParser.parse(raw, words)

        assertEquals(1, enrichment.entries.size)
        val entry = enrichment.of(words[0])!!
        assertEquals("a place where ships stay", entry.definition)
        assertEquals(2, entry.distractors.size)
        assertEquals("dock", entry.distractors.first().word)
        assertNull(enrichment.of(words[1]))
    }

    @Test
    fun acceptsFencedJsonAndDropsEmptyFields() {
        val raw = "```json\n" + """
            {"items":[{"word":"harbour","definition":"","example":"","distractors":[]}]}
        """.trimIndent() + "\n```"

        val enrichment = QuizEnrichmentParser.parse(raw, words)

        assertTrue("没有素材的条目不入库", enrichment.entries.isEmpty())
    }

    @Test
    fun capsLongTextAndExtraDistractors() {
        val long = "x".repeat(400)
        val distractors = (1..10).joinToString(",") { """{"word":"w$it","zh":"释义$it"}""" }
        val raw = """{"items":[{"word":"harbour","definition":"$long","example":"","distractors":[$distractors]}]}"""

        val entry = QuizEnrichmentParser.parse(raw, words).of(words[0])!!

        assertEquals(200, entry.definition.length)
        assertEquals("候选池要够轮换，但也不能让模型无限写", 8, entry.distractors.size)
    }

    @Test
    fun theWordItselfNeverComesBackAsADistractor() {
        val raw = """
            {"items":[{"word":"harbour","definition":"","example":"",
              "distractors":[{"word":"Harbour","zh":"港口"},{"word":"dock","zh":"码头"}]}]}
        """.trimIndent()

        val entry = QuizEnrichmentParser.parse(raw, words).of(words[0])!!

        assertEquals(listOf("dock"), entry.distractors.map { it.word })
    }

    @Test
    fun garbageBecomesAnEmptyEnrichmentInsteadOfThrowing() {
        assertTrue(QuizEnrichmentParser.parse("sorry, no json here", words).entries.isEmpty())
        assertTrue(QuizEnrichmentParser.parse("""{"items": "wrong shape"}""", words).entries.isEmpty())
    }
}
