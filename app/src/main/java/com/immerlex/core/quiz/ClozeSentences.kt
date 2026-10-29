package com.immerlex.core.quiz

import com.immerlex.core.ai.ChapterParser
import com.immerlex.core.ai.Segment

/** 从已写好的章节里抽出可用作挖空的句子；没有句子就不出挖空题，不硬编一句。 */
object ClozeSentences {

    const val BLANK = "____"
    private const val MAX_BEFORE = 52
    private const val MAX_AFTER = 56
    private const val MIN_SENTENCE = 12
    private const val MAX_SENTENCE = 170
    private const val MAX_PER_WORD = 6
    private val STOPS = charArrayOf('。', '！', '？', '；', '…', '\n')

    fun fromChapters(segmentsJsonList: List<String>): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        segmentsJsonList.forEach { raw ->
            val chapter = ChapterParser.parse(raw) ?: return@forEach
            chapter.paragraphs.forEach { paragraph -> collect(paragraph, out) }
        }
        return out
    }

    private fun collect(paragraph: List<Segment>, out: MutableMap<String, MutableList<String>>) {
        paragraph.forEachIndexed { index, segment ->
            if (segment !is Segment.Embedded) return@forEachIndexed
            val lookup = segment.word.lookup.trim().lowercase()
            if (lookup.isBlank()) return@forEachIndexed
            val existing = out[lookup]
            if (existing != null && existing.size >= MAX_PER_WORD) return@forEachIndexed
            val sentence = sentenceAround(paragraph, index) ?: return@forEachIndexed
            out.getOrPut(lookup) { mutableListOf() }.add(sentence)
        }
    }

    private fun sentenceAround(paragraph: List<Segment>, target: Int): String? {
        val before = StringBuilder()
        var i = target - 1
        while (i >= 0 && before.length < MAX_BEFORE) {
            val text = plain(paragraph[i])
            val cut = text.indexOfLast { it in STOPS }
            if (cut >= 0) {
                before.insert(0, text.substring(cut + 1))
                break
            }
            before.insert(0, text)
            i--
        }
        val after = StringBuilder()
        var j = target + 1
        while (j < paragraph.size && after.length < MAX_AFTER) {
            val text = plain(paragraph[j])
            val cut = text.indexOfFirst { it in STOPS }
            if (cut >= 0) {
                after.append(text.substring(0, cut))
                break
            }
            after.append(text)
            j++
        }
        val sentence = (before.toString().takeLast(MAX_BEFORE) + BLANK + after.toString().take(MAX_AFTER)).trim()
        if (sentence.length < MIN_SENTENCE) return null
        return sentence.take(MAX_SENTENCE)
    }

    private fun plain(segment: Segment): String = when (segment) {
        is Segment.Text -> segment.value
        is Segment.Embedded -> segment.word.lookup
    }
}
