package com.immerlex.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.immerlex.app.ui.MasteryTones
import com.immerlex.core.db.WordEntity
import com.immerlex.core.reader.ReaderSegment
import com.immerlex.core.word.mastery

/** Annotation tag used to map a tap offset back to the tapped embedded word. */
const val WORD_ANNOTATION_TAG = "immerlex-word"

/**
 * Renders the chapter as one annotated body: learn mode shows `word（释义）`, review mode shows
 * only `word` — the reader's judgement happens before the tap, so an empty slot is noise.
 *
 * The gloss is glued to the word with full-width brackets and no space: an ASCII space is a
 * line-break opportunity, which used to fling the gloss onto the next line by itself.
 *
 * With [englishOnly] every Chinese character (gloss included) stays in the layout but is
 * painted transparent, so line breaks and the English words' positions do not move.
 */
fun buildChapterText(
    blocks: List<List<ReaderSegment>>,
    mode: ReaderMode,
    bank: Map<String, WordEntity>,
    tones: MasteryTones,
    englishOnly: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { index, paragraph ->
        paragraph.forEach { segment ->
            when (segment) {
                is ReaderSegment.Text -> if (englishOnly) {
                    withStyle(SpanStyle(color = Color.Transparent)) { append(segment.value) }
                } else {
                    append(segment.value)
                }

                is ReaderSegment.Word -> {
                    val word = segment.word
                    val (soft, deep) = word.wordId
                        ?.let { id -> bank[word.lookup]?.takeIf { it.id == id } }
                        ?.let { tones.of(it.mastery()) }
                        ?: tones.new
                    val highlight = SpanStyle(color = deep, background = soft, fontWeight = FontWeight.Bold)
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
