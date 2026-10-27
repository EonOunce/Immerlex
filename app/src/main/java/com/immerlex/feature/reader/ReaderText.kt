package com.immerlex.feature.reader

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
 * Renders the chapter as one annotated body: learn mode shows
 * `word (释义)`, review mode shows `word (  )` so the reader recalls the gloss.
 */
fun buildChapterText(
    blocks: List<List<ReaderSegment>>,
    mode: ReaderMode,
    bank: Map<String, WordEntity>,
    tones: MasteryTones,
): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { index, paragraph ->
        paragraph.forEach { segment ->
            when (segment) {
                is ReaderSegment.Text -> append(segment.value)
                is ReaderSegment.Word -> {
                    val word = segment.word
                    val (soft, deep) = word.wordId
                        ?.let { id -> bank[word.lookup]?.takeIf { it.id == id } }
                        ?.let { tones.of(it.mastery()) }
                        ?: tones.new
                    val gloss = if (mode == ReaderMode.LEARN) word.zh else "　"
                    withStyle(SpanStyle(color = deep, background = soft, fontWeight = FontWeight.Bold)) {
                        pushStringAnnotation(WORD_ANNOTATION_TAG, word.lookup)
                        append("${word.lookup} ($gloss)")
                        pop()
                    }
                }
            }
        }
        if (index != blocks.lastIndex) append("\n\n")
    }
}
