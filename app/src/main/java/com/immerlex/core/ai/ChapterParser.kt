package com.immerlex.core.ai

import java.util.Locale
import kotlinx.serialization.json.Json

data class ValidationIssue(val code: String, val detail: String = "") {
    fun forRepair() = if (detail.isEmpty()) code else "$code ($detail)"
}

/**
 * Turns a model response into a [ParsedChapter] and checks it against the
 * request it was built from. Issues are machine codes, fed back to the model
 * for a repair pass rather than shown to the user.
 */
object ChapterParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(raw: String): ParsedChapter? = extractJson(raw)?.let { text ->
        runCatching { json.decodeFromString(ChapterDoc.serializer(), text) }
            .getOrNull()
            ?.toParsed()
    }

    private fun ChapterDoc.toParsed() = ParsedChapter(
        title = title,
        summary = summary,
        characters = characters.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        paragraphs = paragraphs.map { paragraph ->
            paragraph.mapNotNull { segment ->
                when (segment.k) {
                    "t" -> segment.v?.takeIf { it.isNotEmpty() }?.let { Segment.Text(it) }
                    "w" -> segment.en?.takeIf { it.isNotBlank() }?.let { en ->
                        Segment.Embedded(
                            EmbeddedWord(
                                lookup = en.trim().lowercase(),
                                zh = segment.zh.orEmpty(),
                                ipa = segment.ipa.orEmpty(),
                                def = segment.def.orEmpty(),
                                example = segment.ex.orEmpty(),
                            ),
                        )
                    }

                    else -> null
                }
            }
        },
    )

    /** Strips markdown fences and any prose before/after the outermost JSON object. */
    fun extractJson(raw: String): String? {
        val unwrapped = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = unwrapped.indexOf('{')
        val end = unwrapped.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return unwrapped.substring(start, end + 1)
    }
}

object ChapterValidator {
    fun validate(
        chapter: ParsedChapter?,
        requested: List<String>,
        density: Density,
        minHanChars: Int,
    ): List<ValidationIssue> {
        if (chapter == null) return listOf(ValidationIssue("unparseable_json"))
        val issues = mutableListOf<ValidationIssue>()
        if (chapter.paragraphs.none { it.isNotEmpty() }) issues += ValidationIssue("no_paragraphs")
        if (chapter.title.isBlank()) issues += ValidationIssue("no_title")

        val hanChars = countHan(chapter)
        if (hanChars < minHanChars) issues += ValidationIssue("too_short", "got $hanChars")

        val embedded = chapter.paragraphs.flatten().filterIsInstance<Segment.Embedded>()
        val words = embedded.map { it.word }
        if (words.isEmpty()) {
            issues += ValidationIssue("no_embedded_words")
            return issues
        }

        val perKilo = words.size * 1000.0 / maxOf(hanChars, 1)
        if (perKilo < density.minPerKilo) issues += ValidationIssue("density_low", formatDensity(perKilo))
        if (perKilo > density.maxPerKilo) issues += ValidationIssue("density_high", formatDensity(perKilo))

        val allowed = requested.map { it.lowercase() }.toSet()
        words.map { it.lookup }.distinct().filter { it !in allowed }.forEach {
            issues += ValidationIssue("word_not_requested", it)
        }
        words.groupingBy { it.lookup }.eachCount().filterValues { it > density.maxRepeat }.forEach {
            (word, count) -> issues += ValidationIssue("word_overused", "$word x$count")
        }
        words.filter { it.zh.isBlank() || it.def.isBlank() }.forEach {
            issues += ValidationIssue("missing_gloss", it.lookup)
        }
        return issues
    }

    fun countHan(chapter: ParsedChapter): Int {
        val text = chapter.paragraphs.flatten().filterIsInstance<Segment.Text>().joinToString("") { it.value }
        return text.count { it in '\u4E00'..'\u9FFF' }
    }

    private fun formatDensity(perKilo: Double) = String.format(Locale.US, "%.1f per 1000 chars", perKilo)
}
