package com.immerlex.feature.reader

import com.immerlex.app.ui.WindowClass
import com.immerlex.app.ui.windowClassOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLayoutTest {

    @Test
    fun theSizeClassBreaksAtTheMaterialThresholds() {
        assertEquals(WindowClass.COMPACT, windowClassOf(0))
        assertEquals(WindowClass.COMPACT, windowClassOf(599))
        assertEquals(WindowClass.MEDIUM, windowClassOf(600))
        assertEquals(WindowClass.MEDIUM, windowClassOf(839))
        assertEquals(WindowClass.EXPANDED, windowClassOf(840))
        assertEquals(WindowClass.EXPANDED, windowClassOf(1199))
        assertEquals(WindowClass.WIDE, windowClassOf(1200))
    }

    @Test
    fun aPhoneKeepsTheSheetAndTheDropdown() {
        val phone = readerPanesOf(393)
        assertFalse(phone.chapters)
        assertFalse(phone.card)
    }

    @Test
    fun aTabletGetsTheChapterRailBeforeItGetsTheCardPane() {
        val portrait = readerPanesOf(800)
        assertTrue(portrait.chapters)
        assertFalse(portrait.card)

        val landscape = readerPanesOf(1280)
        assertTrue(landscape.chapters)
        assertTrue(landscape.card)
    }

    @Test
    fun theCardPaneOnlyShowsUpWhereTheBodyStillHasRoom() {
        val wide = readerPanesOf(WIDE_WIDTH_FOR_TESTS)
        assertTrue(wide.card)
        // 三栏全摆出来时正文至少还有 600dp：少于这个数，一行就短到读不下去。
        assertTrue(
            WIDE_WIDTH_FOR_TESTS - READER_CHAPTER_RAIL_DP - READER_CARD_PANE_DP - 24 >= 600,
        )
    }
}

private const val WIDE_WIDTH_FOR_TESTS = 1200
