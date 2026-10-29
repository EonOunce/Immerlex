package com.immerlex.core.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClozeSentencesTest {

    @Test
    fun extractsTheSentenceAroundAnEmbeddedWord() {
        val sentences = ClozeSentences.fromChapters(
            listOf(
                chapter(
                    text("雨停之后，她一个人走到"),
                    embedded("harbour"),
                    text("边，风从海面吹过来。"),
                ),
            ),
        )

        val sentence = sentences.getValue("harbour").single()
        assertTrue(sentence.contains(ClozeSentences.BLANK))
        assertTrue(sentence.contains("雨停之后"))
        assertFalse("挖空后不能把原词留在句子里", sentence.contains("harbour"))
    }

    @Test
    fun cutsAtThePreviousSentenceBoundary() {
        val sentences = ClozeSentences.fromChapters(
            listOf(
                chapter(
                    text("前面无关的一句话。她走到"),
                    embedded("harbour"),
                    text("边的风很大，她没有回头。"),
                ),
            ),
        )

        val sentence = sentences.getValue("harbour").single()
        assertFalse(sentence.contains("前面无关"))
        assertTrue(sentence.startsWith("她走到"))
    }

    @Test
    fun parsesChaptersEvenWhenTheJsonIsFenced() {
        val raw = "```json\n" + chapter(text("她走到"), embedded("harbour"), text("边的风很大，她没有回头。")) + "\n```"

        val sentences = ClozeSentences.fromChapters(listOf(raw))

        assertTrue(sentences.containsKey("harbour"))
    }

    @Test
    fun ignoresSegmentsWithoutAUsableWord() {
        val sentences = ClozeSentences.fromChapters(
            listOf(
                chapter(text("她走到"), embedded("harbour"), text("边的风很大，她没有回头。")),
                """{"title":"坏数据","paragraphs":[[{"k":"w","en":""}]]}""",
                "not json at all",
            ),
        )

        assertEquals(setOf("harbour"), sentences.keys)
    }

    private fun chapter(vararg segments: String): String =
        """{"title":"测试","summary":"","paragraphs":[[${segments.joinToString(",")}]]}"""

    private fun text(value: String): String = """{"k":"t","v":"$value"}"""

    private fun embedded(word: String): String =
        """{"k":"w","en":"$word","zh":"港口","ipa":"/ˈhɑːbə/","def":"a place for ships","ex":""}"""
}
