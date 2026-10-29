package com.immerlex.core.word

import com.immerlex.core.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WordCsvTest {

    private fun word(text: String, translation: String) = WordEntity(
        id = 0,
        word = text,
        translation = translation,
        source = "test",
        createdAtSec = 0,
    )

    @Test
    fun rendersTwoColumnsInInputOrder() {
        val csv = wordCsvOf(listOf(word("apple", "苹果"), word("lantern", "灯笼")))

        assertEquals("apple,苹果\nlantern,灯笼\n", csv)
    }

    @Test
    fun commasQuotesAndNewlinesAreEscaped() {
        val csv = wordCsvOf(listOf(word("odd", "a, b"), word("quote", "他说\"好\""), word("multi", "第一行\n第二行")))

        assertEquals(
            "odd,\"a, b\"\n" +
                "quote,\"他说\"\"好\"\"\"\n" +
                "multi,\"第一行\n第二行\"\n",
            csv,
        )
    }

    @Test
    fun blankTranslationKeepsTheEmptyColumn() {
        assertEquals("lonely,\n", wordCsvOf(listOf(word("lonely", ""))))
    }
}
