package com.immerlex.core.tricky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfusableSuggestionsTest {

    @Test
    fun commonPairIsSuggestedFromEitherSide() {
        assertEquals(listOf("effect"), ConfusableSuggestions.suggestionsOf("affect", emptyList()))
        assertEquals(listOf("affect"), ConfusableSuggestions.suggestionsOf("effect", emptyList()))
    }

    @Test
    fun spellingNeighboursFromTheBankComeAfterCuratedPairs() {
        val result = ConfusableSuggestions.suggestionsOf(
            "adapt",
            listOf("adopt", "adept", "adapter", "unrelated"),
            4,
        )
        assertEquals(listOf("adopt", "adept", "adapter"), result)
    }

    @Test
    fun selfIsNeverSuggestedAndLimitHolds() {
        val result = ConfusableSuggestions.suggestionsOf(
            "cat",
            listOf("cat", "car", "cut", "cot", "coat"),
            2,
        )
        assertEquals(listOf("car", "cot"), result)
    }

    @Test
    fun shortWordsAllowOnlyOneEdit() {
        val result = ConfusableSuggestions.suggestionsOf("ship", listOf("shop", "sheep"), 4)
        assertEquals(listOf("shop"), result)
        assertTrue("sheep 与 ship 差两个字符，不该混进来", "sheep" !in result)
    }

    @Test
    fun blankInputHasNoSuggestions() {
        assertTrue(ConfusableSuggestions.suggestionsOf("  ", listOf("affect")).isEmpty())
    }
}
