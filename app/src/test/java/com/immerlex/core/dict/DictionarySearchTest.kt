package com.immerlex.core.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionarySearchTest {

    @Test
    fun anEnglishWordBecomesAPrefixQuery() {
        assertEquals(DictionaryQuery(SearchColumn.WORD, "need%"), DictionarySearch.of("Need"))
    }

    @Test
    fun aChineseNeedleSearchesTranslationsByContainment() {
        assertEquals(DictionaryQuery(SearchColumn.TRANSLATION, "%需要%"), DictionarySearch.of("需要"))
    }

    /** 词里常见的空格、连字符和撇号仍当英文查，否则 "in case of" 会掉进释义那边。 */
    @Test
    fun phrasesWithSpacesHyphensAndApostrophesStayEnglish() {
        assertEquals(DictionaryQuery(SearchColumn.WORD, "in case of%"), DictionarySearch.of("in case of"))
        assertEquals(DictionaryQuery(SearchColumn.WORD, "well-known%"), DictionarySearch.of("well-known"))
        assertEquals(DictionaryQuery(SearchColumn.WORD, "o'clock%"), DictionarySearch.of("o'clock"))
    }

    /** `%` 和 `_` 是 LIKE 的通配符：用户真打了这两个字，就必须按字面查。 */
    @Test
    fun wildcardsAreEscapedInsteadOfMatchingEverything() {
        assertEquals(DictionaryQuery(SearchColumn.TRANSLATION, "%50\\%%"), DictionarySearch.of("50%"))
        assertEquals(DictionaryQuery(SearchColumn.TRANSLATION, "%a\\_b%"), DictionarySearch.of("a_b"))
        assertEquals(DictionaryQuery(SearchColumn.TRANSLATION, "%a\\\\b%"), DictionarySearch.of("a\\b"))
    }

    @Test
    fun tooShortOrBlankNeedlesDoNotQueryTheDictionary() {
        assertNull(DictionarySearch.of("a"))
        assertNull(DictionarySearch.of("   "))
    }

    @Test
    fun surroundingSpacesAreTrimmedBeforeTheLengthCheck() {
        assertEquals(DictionaryQuery(SearchColumn.WORD, "ab%"), DictionarySearch.of("  ab  "))
    }
}
