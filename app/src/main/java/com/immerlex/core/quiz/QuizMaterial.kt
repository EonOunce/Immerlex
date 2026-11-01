package com.immerlex.core.quiz

import com.immerlex.core.db.QuizMaterialDao
import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.db.WordEntity
import com.immerlex.core.word.wordSurface
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 一个词的出题素材只向模型要一回：存下来的英文释义、例句和易混候选池以后都读这一行。
 * 库里那一行的主键是小写词面而不是词库 id（Room v15），所以删了再加回来的词、素材包里
 * 那些还没进词库的考研词都认得自己的素材；对外仍旧按词库 id 发，出题那边一个字不用改。
 */
class QuizMaterialStore(private val dao: QuizMaterialDao) {

    suspend fun load(words: List<WordEntity>): Map<Long, EnrichedWord> {
        if (words.isEmpty()) return emptyMap()
        val ids = words.associateBy({ wordSurface(it.word) }, { it.id })
        return dao.byWords(ids.keys.toList()).mapNotNull { row -> ids[row.word]?.let { it to row.entry() } }.toMap()
    }

    /** 写的时候才需要词面，所以整批词一起传进来；不在这一批里的 id 不会落库。 */
    suspend fun save(enrichment: QuizEnrichment, words: List<WordEntity>, atSec: Long) {
        if (enrichment.entries.isEmpty()) return
        val surfaces = words.associate { it.id to wordSurface(it.word) }
        dao.upsertAll(
            enrichment.entries.mapNotNull { (wordId, material) ->
                val surface = surfaces[wordId] ?: return@mapNotNull null
                QuizMaterialEntity(
                    word = surface,
                    definition = material.definition,
                    example = material.example,
                    exampleZh = material.exampleZh,
                    distractors = MaterialPool.encode(material.distractors),
                    collocations = MaterialPool.encode(material.collocations),
                    updatedAtSec = atSec,
                )
            },
        )
    }
}

private fun QuizMaterialEntity.entry(): EnrichedWord =
    EnrichedWord(
        definition = definition,
        example = example,
        exampleZh = exampleZh,
        distractors = MaterialPool.decode(distractors),
        collocations = MaterialPool.decode(collocations),
    )

@Serializable
internal data class DistractorRow(val w: String = "", val zh: String = "")

/**
 * `quiz_material.distractors` 那一列只有一种形状：`[{"w":"","zh":""}]`。
 * AI 补的、素材包导的都写这里，所以编解码只留这一份。
 */
internal object MaterialPool {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serializer = ListSerializer(DistractorRow.serializer())

    fun encode(list: List<EnrichedDistractor>): String =
        json.encodeToString(serializer, list.map { DistractorRow(it.word, it.zh) })

    /** 存坏了就当没存过：素材缺了只影响一道题长什么样，不影响记账。 */
    fun decode(text: String): List<EnrichedDistractor> =
        runCatching { json.decodeFromString(serializer, text) }
            .getOrNull()
            .orEmpty()
            .map { EnrichedDistractor(it.w, it.zh) }
            .filter { it.word.isNotBlank() && it.zh.isNotBlank() }
}
