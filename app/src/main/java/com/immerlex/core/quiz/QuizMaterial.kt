package com.immerlex.core.quiz

import com.immerlex.core.db.QuizMaterialDao
import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.db.WordEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 一个词的出题素材只向模型要一回：存下来的英文释义、例句和易混候选池以后都读这一行。
 * 整行只写一次——哪怕那次只给出一句例句，也算这个词已经处理过，不再补问。
 */
class QuizMaterialStore(private val dao: QuizMaterialDao) {

    suspend fun load(words: List<WordEntity>): Map<Long, EnrichedWord> =
        if (words.isEmpty()) {
            emptyMap()
        } else {
            dao.byWordIds(words.map { it.id }).associate { it.wordId to it.entry() }
        }

    suspend fun save(enrichment: QuizEnrichment, atSec: Long) {
        if (enrichment.entries.isEmpty()) return
        dao.upsertAll(
            enrichment.entries.map { (wordId, entry) ->
                QuizMaterialEntity(
                    wordId = wordId,
                    definition = entry.definition,
                    example = entry.example,
                    distractors = encode(entry.distractors),
                    createdAtSec = atSec,
                )
            },
        )
    }
}

private fun QuizMaterialEntity.entry(): EnrichedWord =
    EnrichedWord(definition = definition, example = example, distractors = decode(distractors))

@Serializable
private data class DistractorRow(val w: String = "", val zh: String = "")

private val materialJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val poolSerializer = ListSerializer(DistractorRow.serializer())

private fun encode(list: List<EnrichedDistractor>): String =
    materialJson.encodeToString(poolSerializer, list.map { DistractorRow(it.word, it.zh) })

/** 存坏了就当没存过：素材缺了只影响一道题长什么样，不影响记账。 */
private fun decode(text: String): List<EnrichedDistractor> =
    runCatching { materialJson.decodeFromString(poolSerializer, text) }
        .getOrNull()
        .orEmpty()
        .map { EnrichedDistractor(it.w, it.zh) }
        .filter { it.word.isNotBlank() && it.zh.isNotBlank() }
