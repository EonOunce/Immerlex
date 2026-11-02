package com.immerlex.core.material

import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialWordsTest {

    @Test
    fun oneWordPerLineSortedAndDeduplicated() {
        assertEquals(
            "affect\napple\nzebra",
            wordListOf(listOf("zebra", "apple", " Affect ", "APPLE")),
        )
    }

    @Test
    fun blankEntriesAreDropped() {
        assertEquals("apple", wordListOf(listOf("", "  ", "apple")))
    }

    @Test
    fun nothingToExportIsAnEmptyString() {
        assertEquals("", wordListOf(emptyList()))
    }
}
