package com.immerlex.core.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class WordListParserTest {

    @Test
    fun plainWords() {
        val out = WordListParser.parse("apple\nresist\n  bin \n")
        assertEquals(listOf("apple", "resist", "bin"), out.map { it.word })
        assertEquals(listOf(null, null, null), out.map { it.gloss })
    }

    @Test
    fun tabCommaSemicolonAndWhitespaceGlosses() {
        val out = WordListParser.parse(
            """
            apple	苹果
            resist, 忍住
            bin;箱子
            incentive 激励
            """.trimIndent(),
        )
        assertEquals(4, out.size)
        assertEquals("苹果", out[0].gloss)
        assertEquals("忍住", out[1].gloss)
        assertEquals("箱子", out[2].gloss)
        assertEquals("激励", out[3].gloss)
    }

    @Test
    fun dropsNonEnglishHeadsAndDedupes() {
        val out = WordListParser.parse("1. apple\n中文行\nApple\napple\n# note")
        assertEquals(listOf("apple"), out.map { it.word })
    }

    @Test
    fun multiWordPhrasesAllowed() {
        val out = WordListParser.parse("later on, 稍后")
        assertEquals("later on", out.single().word)
        assertEquals("稍后", out.single().gloss)
    }
}
