package com.immerlex.core.ai

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
    val paragraphs: List<List<RawSegment>> = emptyList(),
)

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
}
