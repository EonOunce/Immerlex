package com.immerlex.feature.reader

import com.immerlex.app.ui.LightTones
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun learnModeGluesTheGlossToTheWord() {
        val text = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones).text
        // 词与括号之间没有空格：那个空格既是换行点，又被刷上了高亮底色。
        assertEquals("他 reluctant（不情愿的） 关上了门。\n\n第二段的正文。", text)
    }

    @Test
    fun reviewModeShowsOnlyTheWord() {
        val text = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones).text
        assertEquals("他 reluctant 关上了门。\n\n第二段的正文。", text)
        assertFalse("no empty bracket left behind", text.contains("（"))
    }

    @Test
    fun aWordWithoutAGlossGetsNoBrackets() {
        val bare = listOf(
            listOf(
                ReaderSegment.Word(embedded.word.copy(zh = "")),
                ReaderSegment.Text("剩下的正文。"),
            ),
        )
        assertEquals("reluctant剩下的正文。", buildChapterText(bare, ReaderMode.LEARN, emptyMap(), LightTones).text)
    }

    @Test
    fun everyEmbeddedWordIsTappable() {
        val annotated = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones)
        val items = annotated.getStringAnnotations(WORD_ANNOTATION_TAG, 0, annotated.length).map { it.item }
        assertEquals(listOf("reluctant"), items)
    }

    @Test
    fun englishOnlyKeepsLayoutAndHidesEveryChineseCharacter() {
        val annotated = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones, englishOnly = true)
        val normal = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones)

        // 汉字与释义都没删，只是画成透明：字符串完全相同意味着换行和英文位置分毫不动。
        assertEquals(normal.text, annotated.text)
        val hidden = annotated.spanStyles.filter { it.item.color == Color.Transparent }
        assertEquals(4, hidden.size)
        assertEquals(0, hidden.first().start)
        assertEquals("他 ".length, hidden.first().end)
        assertEquals("reluctant", visibleText(annotated))
    }

    @Test
    fun englishOnlyInReviewModeStillLeavesJustTheWords() {
        val annotated = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, englishOnly = true)
        val normal = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones)

        assertEquals(normal.text, annotated.text)
        assertEquals("reluctant", visibleText(annotated))
    }

    private fun visibleText(annotated: androidx.compose.ui.text.AnnotatedString): String {
        val hidden = annotated.spanStyles.filter { it.item.color == Color.Transparent }
        return annotated.text.filterIndexed { index, _ ->
            hidden.none { index >= it.start && index < it.end }
        }.trim()
    }

    @Test
    fun englishOnlyWordsStayTappable() {
        val annotated = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, englishOnly = true)
        val items = annotated.getStringAnnotations(WORD_ANNOTATION_TAG, 0, annotated.length).map { it.item }
        assertEquals(listOf("reluctant"), items)
    }
}
