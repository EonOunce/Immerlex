package com.immerlex.core.dict

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 词典检索的 SQL 只能在真库上验：`DictionarySearchTest` 锁的是"怎么拼模式"，
 * 这里锁的是"拼好的模式在 ECDICT 里确实按词头 / 按释义命中"。
 */
class EcdictSearchTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dictionary: EcdictDictionary

    @Before
    fun setUp() {
        dictionary = EcdictDictionary.openFromAssets(context)
    }

    @After
    fun tearDown() {
        dictionary.close()
    }

    @Test
    fun anEnglishNeedleOnlyMatchesWordHeads() {
        val need = dictionary.search("need", 20)

        assertTrue("应有的词：$need", need.isNotEmpty())
        assertTrue(need.all { it.word.startsWith("need") })
        assertTrue("needle 应该在里面：$need", need.any { it.word == "needle" })
    }

    @Test
    fun aChineseNeedleFindsEntriesByTheirGloss() {
        val found = dictionary.search("需要", 10)

        assertTrue("应有带「需要」的释义：$found", found.isNotEmpty())
        assertTrue(found.all { it.translation.contains("需要") })
    }

    /** 一个 `%` 不能把整本词典捞出来。 */
    @Test
    fun aBareWildcardMatchesNothingInsteadOfEverything() {
        assertEquals(emptyList<DictEntry>(), dictionary.search("%", 10))
    }

    @Test
    fun anAsciiNeedleDoesNotFallThroughToGlosses() {
        val found = dictionary.search("needle", 5)

        assertTrue(found.isNotEmpty())
        assertEquals("needle", found.first().word)
    }

    @Test
    fun theLimitIsRespected() {
        val wide = dictionary.search("ne", Int.MAX_VALUE)
        assertTrue(wide.size > 2)
        assertEquals(2, dictionary.search("ne", 2).size)
    }

    @Test
    fun aShortNeedleIsRejectedBeforeReachingSql() {
        assertEquals(emptyList<DictEntry>(), dictionary.search("n", 10))
    }
}
