package com.immerlex.core.importer

import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictionarySource
import com.immerlex.core.word.SameGloss
import java.time.Instant

data class ImportSummary(
    val parsed: Int,
    val added: Int,
    val dictMatched: Int,
    val duplicates: Int,
    val missingGloss: Int,
    /** 用户选了"跳过这些"时，释义和别的词逐字相同、这次没进库的条数。 */
    val skippedVariants: Int = 0,
)

/** 一个准备进词库的词：[translation] 与 [definition] 已按 ECDICT 补全。 */
data class PreparedWord(
    val word: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
)

/** [newWord] 的释义与 [otherWord]（词库里已有的，或同一批里排在前面的）逐字相同。 */
data class SameGlossPair(val newWord: String, val otherWord: String, val gloss: String)

/**
 * 一次导入的两步：先 [ImportRepository.prepare] 算出要写什么、有没有同义词，
 * 由界面问过用户之后再 [ImportRepository.commit]。没有同义词时两步连着走，
 * 和原来的 [ImportRepository.importWords] 完全一样。
 */
data class ImportPlan(
    val words: List<PreparedWord>,
    val variants: List<SameGlossPair>,
    val parsed: Int,
    val duplicates: Int,
    val dictMatched: Int,
    val missingGloss: Int,
)

class ImportRepository(
    private val wordDao: WordDao,
    private val dictionary: DictionarySource,
) {
    /** 解析、补全释义，再和新词之外的所有词比一遍释义（[SameGloss]）。不写库。 */
    suspend fun prepare(parsed: List<ParsedWord>): ImportPlan {
        val bank = wordDao.allWords()
        val taken = bank.mapTo(HashSet()) { it.word }
        val byGloss = mutableMapOf<String, String>()
        bank.forEach { word ->
            val gloss = word.translation.trim()
            if (gloss.isNotEmpty()) byGloss.putIfAbsent(gloss, word.word)
        }
        val prepared = mutableListOf<PreparedWord>()
        val variants = mutableListOf<SameGlossPair>()
        var duplicates = 0
        var matched = 0
        var missingGloss = 0

        for (p in parsed) {
            if (!taken.add(p.word)) {
                duplicates++
                continue
            }
            val entry = dictionary.lookup(p.word)
            if (entry != null) matched++
            // ECDICT wins; the user's own gloss is kept even when it is English,
            // because phrases (in spite of) are exactly where ECDICT is silent.
            val translation = entry?.translation?.ifEmpty { null } ?: p.gloss ?: ""
            if (translation.isEmpty()) missingGloss++
            prepared += PreparedWord(
                word = p.word,
                phonetic = entry?.phonetic.orEmpty(),
                translation = translation,
                definition = entry?.definition.orEmpty(),
            )
            val gloss = translation.trim()
            if (gloss.isNotEmpty()) {
                val other = byGloss[gloss]
                if (other != null && other != p.word) variants += SameGlossPair(p.word, other, gloss)
                byGloss.putIfAbsent(gloss, p.word)
            }
        }
        return ImportPlan(prepared, variants, parsed.size, duplicates, matched, missingGloss)
    }

    /** [skipVariants] = true 时，[ImportPlan.variants] 里那些新词一条都不写。 */
    suspend fun commit(plan: ImportPlan, source: String, skipVariants: Boolean = false): ImportSummary {
        val skipped = if (skipVariants) plan.variants.mapTo(HashSet()) { it.newWord } else emptySet()
        val now = Instant.now().epochSecond
        var added = 0
        var duplicates = plan.duplicates
        var missingGloss = plan.missingGloss - plan.words.count { it.word in skipped && it.translation.isEmpty() }

        for (w in plan.words) {
            if (w.word in skipped) continue
            val inserted = wordDao.insert(
                WordEntity(
                    word = w.word,
                    phonetic = w.phonetic,
                    translation = w.translation,
                    definition = w.definition,
                    source = source,
                    createdAtSec = now,
                ),
            )
            if (inserted != -1L) added++ else duplicates++
        }
        return ImportSummary(
            parsed = plan.parsed,
            added = added,
            dictMatched = plan.dictMatched,
            duplicates = duplicates,
            missingGloss = missingGloss,
            skippedVariants = skipped.size,
        )
    }

    /** 没有界面可问时的整批导入：同义词也照常写进去。 */
    suspend fun importWords(parsed: List<ParsedWord>, source: String): ImportSummary =
        commit(prepare(parsed), source)
}
