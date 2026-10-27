package com.immerlex.feature.reader

import com.immerlex.app.ui.LightTones
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextTest {

    private val embedded = ReaderSegment.Word(
        ReaderWord(
            lookup = "reluctant",
            zh = "不情愿的",
            ipa = "/rɪˈlʌktənt/",
            def = "unwilling",
            example = "",
            wordId = null,
        ),
    )

    private val blocks = listOf(
        listOf(ReaderSegment.Text("他 "), embedded, ReaderSegment.Text(" 关上了门。")),
        listOf(ReaderSegment.Text("第二段的正文。")),
    )

    @Test
    fun learnModeShowsGloss() {
        val text = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones).text
        assertEquals("他 reluctant (不情愿的) 关上了门。\n\n第二段的正文。", text)
    }

    @Test
    fun reviewModeHidesGlossButKeepsSlot() {
        val text = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones).text
        assertTrue("gloss slot must stay for the reveal tap", text.contains("reluctant (　)"))
        assertFalse("gloss must not leak into review mode", text.contains("不情愿的"))
    }

    @Test
    fun everyEmbeddedWordIsTappable() {
        val annotated = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones)
        val items = annotated.getStringAnnotations(WORD_ANNOTATION_TAG, 0, annotated.length).map { it.item }
        assertEquals(listOf("reluctant"), items)
    }
}
