package com.immerlex.feature.wordbank

import androidx.room.withTransaction
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.WordDao
import com.immerlex.core.db.WordEntity
import com.immerlex.core.dict.DictionarySource
import com.immerlex.core.importer.ImportRepository
import com.immerlex.core.importer.ImportSummary
import com.immerlex.core.importer.WordListParser
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Word bank data operations for the UI layer. Imports go through
 * [ImportRepository] so ECDICT enrichment stays in one place.
 */
class WordBankRepository(
    private val db: ImmerlexDatabase,
    private val dictionary: DictionarySource,
    private val imports: ImportRepository,
) {
    private val words: WordDao get() = db.wordDao()

    fun observeWords(): Flow<List<WordEntity>> = words.observeAll()

    // First call also copies the 11MB ECDICT asset out of the APK.
    suspend fun importText(text: String, source: String): ImportSummary =
        withContext(Dispatchers.IO) { imports.importWords(WordListParser.parse(text), source) }

    /** @return false when the word is blank or already in the bank. */
    suspend fun addManual(word: String, gloss: String): Boolean = withContext(Dispatchers.IO) {
        val clean = word.trim().lowercase()
        if (clean.isEmpty() || words.findByWord(clean) != null) {
            false
        } else {
            val entry = dictionary.lookup(clean)
            words.insert(
                WordEntity(
                    word = clean,
                    phonetic = entry?.phonetic.orEmpty(),
                    translation = gloss.trim().ifEmpty { entry?.translation.orEmpty() },
                    definition = entry?.definition.orEmpty(),
                    source = SOURCE_MANUAL,
                    createdAtSec = Instant.now().epochSecond,
                ),
            )
            true
        }
    }

    suspend fun delete(wordId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.reviewLogDao().deleteForWord(wordId)
            words.deleteById(wordId)
        }
    }

    companion object {
        const val SOURCE_MANUAL = "manual"
    }
}
