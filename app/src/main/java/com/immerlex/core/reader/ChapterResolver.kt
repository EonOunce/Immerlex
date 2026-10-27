package com.immerlex.core.reader

import com.immerlex.core.ai.ParsedChapter
import com.immerlex.core.ai.Segment
import com.immerlex.core.db.WordEntity

sealed interface ReaderSegment {
    data class Text(val value: String) : ReaderSegment
    data class Word(val word: ReaderWord) : ReaderSegment
}

data class ReaderWord(
    val lookup: String,
    val zh: String,
    val ipa: String,
    val def: String,
    val example: String,
    val wordId: Long?,
) {
    /** A word the model invented that is not in the bank: readable, but not schedulable. */
    val inBank: Boolean get() = wordId != null
}

/** Joins chapter word segments to the word bank so the reader can colour and grade them. */
object ChapterResolver {
    fun resolve(chapter: ParsedChapter, bank: Map<String, WordEntity>): List<List<ReaderSegment>> =
        chapter.paragraphs.map { paragraph ->
            paragraph.map { segment ->
                when (segment) {
                    is Segment.Text -> ReaderSegment.Text(segment.value)
                    is Segment.Embedded -> {
                        val word = segment.word
                        val entity = bank[word.lookup]
                        ReaderSegment.Word(
                            ReaderWord(
                                lookup = word.lookup,
                                // The model's contextual gloss wins; the bank gloss is the fallback.
                                zh = word.zh.ifBlank { entity?.translation.orEmpty() },
                                ipa = word.ipa.ifBlank { entity?.phonetic.orEmpty() },
                                def = word.def.ifBlank { entity?.definition.orEmpty() },
                                example = word.example,
                                wordId = entity?.id,
                            ),
                        )
                    }
                }
            }
        }

    fun bankIndex(words: List<WordEntity>): Map<String, WordEntity> =
        words.associateByTo(HashMap()) { it.word.lowercase() }
}
