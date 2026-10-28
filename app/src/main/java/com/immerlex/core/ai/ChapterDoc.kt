package com.immerlex.core.ai

import com.immerlex.core.bible.CharacterChange

/**
 * Chapter document as the model must emit it. Field names and the
 * k:"t"/k:"w" discriminator mirror prototype/reading.html so the reader
 * and the generator share one contract.
 */
@kotlinx.serialization.Serializable
data class ChapterDoc(
    val title: String,
    val summary: String = "",
    val characters: List<String> = emptyList(),
    /** 本章处境发生变化的人物，用来合并进人物状态表（docs/adr/0005 第 3 条）。 */
    val changes: List<ChangeDoc> = emptyList(),
    val paragraphs: List<List<RawSegment>> = emptyList(),
)

@kotlinx.serialization.Serializable
data class ChangeDoc(val who: String = "", val what: String = "")

@kotlinx.serialization.Serializable
data class RawSegment(
    val k: String,
    val v: String? = null,
    val id: String? = null,
    val en: String? = null,
    val zh: String? = null,
    val ipa: String? = null,
    val st: String? = null,
    val def: String? = null,
    val ex: String? = null,
)

/** Reader-facing model: word lookups are normalised so they can be joined to the word bank. */
data class ParsedChapter(
    val title: String,
    val summary: String,
    val characters: List<String>,
    val changes: List<CharacterChange>,
    val paragraphs: List<List<Segment>>,
)

sealed interface Segment {
    data class Text(val value: String) : Segment
    data class Embedded(val word: EmbeddedWord) : Segment
}

data class EmbeddedWord(
    val lookup: String,
    val zh: String,
    val ipa: String,
    val def: String,
    val example: String,
)

/** How thickly English words should be woven into 1000 Han characters. */
enum class Density(val minPerKilo: Int, val maxPerKilo: Int, val maxRepeat: Int) {
    SPARSE(10, 15, 2),
    MEDIUM(20, 30, 3),
    DENSE(35, 50, 4),
    ;

    companion object {
        /**
         * 词库不足时自动降档：取用户所选档位之下、最密的一档，使得每个词只需出现一次
         * 就能达到该档的下限——模型不会为凑密度反复塞同一个词。
         */
        fun bestFor(wordCount: Int, targetHanChars: Int, limit: Density): Density =
            entries.lastOrNull {
                it <= limit && it.minPerKilo.toLong() * targetHanChars <= wordCount.toLong() * 1000
            } ?: SPARSE

        /** 篇幅随词量收缩：低于此字数，任何档位都只能靠重复填充才达标。 */
        fun charsFor(wordCount: Int, targetHanChars: Int, tier: Density, minChars: Int): Int =
            maxOf(minChars, minOf(targetHanChars, wordCount * 1000 / tier.minPerKilo))
    }
}
