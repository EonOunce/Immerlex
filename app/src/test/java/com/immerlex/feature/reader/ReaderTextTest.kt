package com.immerlex.feature.reader

import com.immerlex.app.ui.LightTones
import com.immerlex.core.db.WordEntity
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.reader.ReaderWord
import com.immerlex.core.schedule.ReaderMarks
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderTextTest {

    private val now = 1_700_000_000L

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

    private fun bank(due: Long?) = mapOf(
        "reluctant" to WordEntity(id = 7L, word = "reluctant", source = "txt", createdAtSec = 0, dueEpochSec = due),
    )

    private fun inBank() = listOf(listOf(ReaderSegment.Word(embedded.word.copy(wordId = 7L))))

    private fun inkOf(marks: ReaderMarks): Color =
        buildChapterText(inBank(), ReaderMode.REVIEW, bank(now), LightTones, marks, now)
            .spanStyles.first { it.item.background != Color.Unspecified }
            .item.color

    @Test
    fun learnModeGluesTheGlossToTheWord() {
        val text = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones, ReaderMarks(), now).text
        // 词与括号之间没有空格：那个空格既是换行点，又被刷上了高亮底色。
        assertEquals("他 reluctant（不情愿的） 关上了门。\n\n第二段的正文。", text)
    }

    @Test
    fun reviewModeShowsOnlyTheWord() {
        val text = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now).text
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
        assertEquals(
            "reluctant剩下的正文。",
            buildChapterText(bare, ReaderMode.LEARN, emptyMap(), LightTones, ReaderMarks(), now).text,
        )
    }

    @Test
    fun everyEmbeddedWordIsTappable() {
        val annotated = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones, ReaderMarks(), now)
        val items = annotated.getStringAnnotations(WORD_ANNOTATION_TAG, 0, annotated.length).map { it.item }
        assertEquals(listOf("reluctant"), items)
    }

    @Test
    fun englishOnlyKeepsLayoutAndHidesEveryChineseCharacter() {
        val annotated =
            buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones, ReaderMarks(), now, englishOnly = true)
        val normal = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones, ReaderMarks(), now)

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
        val annotated =
            buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now, englishOnly = true)
        val normal = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now)

        assertEquals(normal.text, annotated.text)
        assertEquals("reluctant", visibleText(annotated))
    }

    @Test
    fun theColourOfAWordComesFromWhatTheReaderDidToIt() {
        // 没学过是橙；学过且今天到期是蓝；今天在阅读里回忆过就转绿。
        assertEquals(LightTones.new.second, inkOf(ReaderMarks()))
        assertEquals(LightTones.mastered.second, inkOf(ReaderMarks(learned = setOf(7L))))
        assertEquals(
            LightTones.learning.second,
            inkOf(ReaderMarks(learned = setOf(7L), reviewedToday = setOf(7L))),
        )
    }

    @Test
    fun theRightEdgeOfAWordStillTapsThatWord() {
        val annotated = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now)
        val ranges = wordRanges(annotated)
        // "他 reluctant 关上了门。"：词占 2 到 10，11 是它右边那条边界，点在最后一个字母的右半边就落在这里。
        val word = ranges.single()
        assertEquals(2, word.start)
        assertEquals(11, word.end)

        assertEquals("reluctant", wordAt(ranges, word.start))
        assertEquals("reluctant", wordAt(ranges, word.end - 1))
        assertEquals("reluctant", wordAt(ranges, word.end))
        assertEquals(null, wordAt(ranges, word.end + 1))
        assertEquals(null, wordAt(ranges, word.start - 1))
    }

    @Test
    fun learnModeCoversTheGlossWithTheSameTapTarget() {
        val annotated = buildChapterText(blocks, ReaderMode.LEARN, emptyMap(), LightTones, ReaderMarks(), now)
        val word = wordRanges(annotated).single()

        assertEquals("reluctant（不情愿的）", annotated.text.substring(word.start, word.end))
    }

    @Test
    fun theKeyboardUnderlineSitsOnOneOccurrenceOnly() {
        val twice = listOf(
            listOf(
                ReaderSegment.Text("他 "),
                embedded,
                ReaderSegment.Text(" 和 "),
                ReaderSegment.Word(embedded.word.copy(zh = "")),
                ReaderSegment.Text(" 都关了门。"),
            ),
        )
        val annotated = buildChapterText(
            twice, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now, selectedIndex = 1,
        )
        val underlined = annotated.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        val ranges = wordRanges(annotated)

        assertEquals(1, underlined.size)
        assertEquals(ranges[1].start, underlined.single().start)
        assertEquals(ranges[1].end, underlined.single().end)
    }

    @Test
    fun noUnderlineWhenTheKeyboardHasNotPickedAWord() {
        val annotated = buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now)
        assertEquals(0, annotated.spanStyles.count { it.item.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun tappingCountsWhichOccurrenceItWas() {
        val twice = listOf(
            listOf(
                ReaderSegment.Text("他 "),
                embedded,
                ReaderSegment.Text(" 和 "),
                ReaderSegment.Word(embedded.word.copy(zh = "")),
                ReaderSegment.Text(" 都关了门。"),
            ),
        )
        val ranges = wordRanges(
            buildChapterText(twice, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now),
        )

        assertEquals(0, wordIndexAt(ranges, ranges[0].start))
        assertEquals(1, wordIndexAt(ranges, ranges[1].start))
        // 短词右半边落在词尾后面那条边界上，仍然算这个词（与 wordAt 同一套规则）。
        assertEquals(1, wordIndexAt(ranges, ranges[1].end))
        assertEquals(null, wordIndexAt(ranges, ranges[1].end + 1))
        assertEquals(null, wordIndexAt(ranges, 0))
    }

    private fun visibleText(annotated: androidx.compose.ui.text.AnnotatedString): String {
        val hidden = annotated.spanStyles.filter { it.item.color == Color.Transparent }
        return annotated.text.filterIndexed { index, _ ->
            hidden.none { index >= it.start && index < it.end }
        }.trim()
    }

    @Test
    fun englishOnlyWordsStayTappable() {
        val annotated =
            buildChapterText(blocks, ReaderMode.REVIEW, emptyMap(), LightTones, ReaderMarks(), now, englishOnly = true)
        val items = annotated.getStringAnnotations(WORD_ANNOTATION_TAG, 0, annotated.length).map { it.item }
        assertEquals(listOf("reluctant"), items)
    }
}
