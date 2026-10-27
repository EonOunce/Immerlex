package com.immerlex.core.importer

/**
 * Supported line formats (first token must look like an English word/phrase):
 *   apple
 *   apple\t苹果
 *   apple, 苹果 (also ';' separated)
 *   apple 苹果   (whitespace split, gloss = remainder)
 * Lines whose head is not English are dropped (Anki headers, notes, Chinese lines).
 */
data class ParsedWord(val word: String, val gloss: String?)

object WordListParser {
    private val HEAD_RE = Regex("^[A-Za-z][A-Za-z' .\\-]{0,23}$")

    fun parse(text: String): List<ParsedWord> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = splitLine(line)
                val head = parts.first().trim()
                if (!HEAD_RE.matches(head)) return@mapNotNull null
                ParsedWord(head.lowercase(), parts.getOrNull(1)?.trim()?.ifEmpty { null })
            }
            .toList()
            .distinctBy { it.word }

    private fun splitLine(line: String): List<String> = when {
        line.contains('\t') -> line.split('\t', limit = 2)
        line.contains(',') -> line.split(',', limit = 2)
        line.contains(';') -> line.split(';', limit = 2)
        else -> Regex("\\s+").split(line, limit = 2)
    }
}
