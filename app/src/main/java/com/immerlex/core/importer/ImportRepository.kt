package com.immerlex.core.importer

import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictionarySource
import java.time.Instant

data class ImportSummary(
    val parsed: Int,
    val added: Int,
    val dictMatched: Int,
    val duplicates: Int,
    val missingGloss: Int,
)

class ImportRepository(
    private val wordDao: WordDao,
    private val dictionary: DictionarySource,
) {
    suspend fun importWords(parsed: List<ParsedWord>, source: String): ImportSummary {
        var added = 0
        var matched = 0
        var duplicates = 0
        var missingGloss = 0
        val now = Instant.now().epochSecond

        for (p in parsed) {
            if (wordDao.findByWord(p.word) != null) {
                duplicates++
                continue
            }
            val entry = dictionary.lookup(p.word)
            if (entry != null) matched++
            // ECDICT wins; the user's own gloss is kept even when it is English,
            // because phrases (in spite of) are exactly where ECDICT is silent.
            val translation = entry?.translation?.ifEmpty { null } ?: p.gloss ?: ""
            if (translation.isEmpty()) missingGloss++
            val inserted = wordDao.insert(
                WordEntity(
                    word = p.word,
                    phonetic = entry?.phonetic.orEmpty(),
                    translation = translation,
                    definition = entry?.definition.orEmpty(),
                    source = source,
                    createdAtSec = now,
                ),
            )
            if (inserted != -1L) added++ else duplicates++
        }
        return ImportSummary(parsed.size, added, matched, duplicates, missingGloss)
    }
}
