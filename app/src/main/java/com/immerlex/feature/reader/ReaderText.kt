package com.immerlex.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.immerlex.app.ui.MasteryTones
import com.immerlex.core.db.WordEntity
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.schedule.ReaderMarks
import com.immerlex.core.schedule.readerTone

/** Annotation tag used to map a tap offset back to the tapped embedded word. */
const val WORD_ANNOTATION_TAG = "immerlex-word"

/** 一个可点的词在正文里占的字符区间：[start] 含，[end] 不含。 */
internal data class WordRange(val lookup: String, val start: Int, val end: Int)

/**
 * 点在最后一个字母的右半边时，[position] 会落到词尾之后——那个边界也算这个词，
 * 否则三四个字母的短词右半边一整片点不动。
 */
internal fun wordAt(ranges: List<WordRange>, position: Int): String? =
    (ranges.firstOrNull { position in it.start until it.end } ?: ranges.firstOrNull { position == it.end })?.lookup

/** 与 [wordAt] 同一套命中规则，但要的是第几次出现：点词之后键盘上下键从这里接着走。 */
internal fun wordIndexAt(ranges: List<WordRange>, position: Int): Int? =
    ranges.indexOfFirst { position in it.start until it.end }
        .takeIf { it >= 0 }
        ?: ranges.indexOfFirst { position == it.end }.takeIf { it >= 0 }

/** 把正文里标出来的词一次性挑成区间，点一下只做查找：整篇标注不必每次重扫。 */
internal fun wordRanges(annotated: AnnotatedString): List<WordRange> =
    annotated.getStringAnnotations(WORD_ANNOTATION_TAG, 0, annotated.length)
        .map { WordRange(it.item, it.start, it.end) }

/**
 * Renders the chapter as one annotated body: learn mode shows `word（释义）`, review mode shows
 * only `word` — the reader's judgement happens before the tap, so an empty slot is noise.
 *
 * The gloss is glued to the word with full-width brackets and no space: an ASCII space is a
 * line-break opportunity, which used to fling the gloss onto the next line by itself.
 *
 * With [englishOnly] every Chinese character (gloss included) stays in the layout but is
 * painted transparent, so line breaks and the English words' positions do not move.
 *
 * [selectedIndex] 是键盘停在第几个词（按出现顺序），只加一条下划线：不换底色、不改字宽，
 * 所以选中时正文一行都不会重排。
 */
fun buildChapterText(
    blocks: List<List<ReaderSegment>>,
    mode: ReaderMode,
    bank: Map<String, WordEntity>,
    tones: MasteryTones,
    marks: ReaderMarks,
    atSec: Long,
    englishOnly: Boolean = false,
    selectedIndex: Int? = null,
): AnnotatedString = buildAnnotatedString {
    var wordIndex = -1
    blocks.forEachIndexed { index, paragraph ->
        paragraph.forEach { segment ->
            when (segment) {
                is ReaderSegment.Text -> if (englishOnly) {
                    withStyle(SpanStyle(color = Color.Transparent)) { append(segment.value) }
                } else {
                    append(segment.value)
                }

                is ReaderSegment.Word -> {
                    wordIndex += 1
                    val word = segment.word
                    val entity = word.wordId?.let { id -> bank[word.lookup]?.takeIf { it.id == id } }
                    val (soft, deep) = tones.of(readerTone(entity, marks, atSec))
                    val highlight = SpanStyle(
                        color = deep,
                        background = soft,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (wordIndex == selectedIndex) TextDecoration.Underline else null,
                    )
                    pushStringAnnotation(WORD_ANNOTATION_TAG, word.lookup)
                    withStyle(highlight) { append(word.lookup) }
                    if (mode == ReaderMode.LEARN && word.zh.isNotBlank()) {
                        val gloss = "（${word.zh}）"
                        if (englishOnly) {
                            withStyle(SpanStyle(color = Color.Transparent)) { append(gloss) }
                        } else {
                            withStyle(highlight) { append(gloss) }
                        }
                    }
                    pop()
                }
            }
        }
        if (index != blocks.lastIndex) append("\n\n")
    }
}
