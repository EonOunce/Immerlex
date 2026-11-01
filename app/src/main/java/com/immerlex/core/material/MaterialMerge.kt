package com.immerlex.core.material

import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.quiz.EnrichedDistractor
import com.immerlex.core.quiz.MaterialPool

/**
 * 素材包怎么并进库里那一行。规矩只有一条：**包给了的字段盖掉旧的，包没说的字段留住原有的**。
 *
 * 这样重导一份修好的包真能把写错的那句例句换掉；而一个只给了释义的包，
 * 不会把 AI 当场补出来的例句和易混池一起清空。例句和它的中文对照必须成对换——
 * 旧的对照对不上新的句子，留着比换掉更误导人。
 */
object MaterialMerge {

    /** @return 内容真的变了的行；重导同一份包时这里是空的。 */
    fun plan(
        entries: List<MaterialEntry>,
        existing: Map<String, QuizMaterialEntity>,
        atSec: Long,
        glossOf: (String) -> String,
    ): List<QuizMaterialEntity> = entries.mapNotNull { entry ->
        val current = existing[entry.word]
        merge(current, entry, atSec, glossOf).takeIf { !sameContent(current, it) }
    }

    fun merge(
        current: QuizMaterialEntity?,
        entry: MaterialEntry,
        atSec: Long,
        glossOf: (String) -> String,
    ): QuizMaterialEntity {
        val example = entry.example.ifBlank { current?.example.orEmpty() }
        return QuizMaterialEntity(
            word = entry.word,
            definition = entry.definition.ifBlank { current?.definition.orEmpty() },
            example = example,
            exampleZh = if (entry.example.isBlank()) current?.exampleZh.orEmpty() else entry.exampleZh,
            distractors = MaterialPool.encode(candidates(entry, current, glossOf)),
            collocations = MaterialPool.encode(collocationPool(entry, current, glossOf)),
            updatedAtSec = atSec,
        )
    }

    /** 与易混池同一条规矩：包给了就整池换，一个都没给才留住旧的。 */
    private fun collocationPool(
        entry: MaterialEntry,
        current: QuizMaterialEntity?,
        glossOf: (String) -> String,
    ): List<EnrichedDistractor> {
        val fromPack = glossedCollocations(entry.collocations, glossOf)
        return if (fromPack.isNotEmpty()) fromPack else current?.let { MaterialPool.decode(it.collocations) }.orEmpty()
    }

    /** 搭配的释义与易混候选同一套补法：包没写就查词典，查不到就丢掉这一条。 */
    fun glossedCollocations(
        collocations: List<Collocation>,
        glossOf: (String) -> String,
    ): List<EnrichedDistractor> =
        collocations.mapNotNull { item ->
            val gloss = item.zh.ifBlank { glossOf(item.en) }.take(MaterialPack.MAX_GLOSS)
            if (gloss.isBlank()) null else EnrichedDistractor(item.en, gloss)
        }

    /** 池子整池换：包给了候选就用包的，一个都没给才留住旧的——两边掺在一起就没人说得清是谁的池子。 */
    private fun candidates(
        entry: MaterialEntry,
        current: QuizMaterialEntity?,
        glossOf: (String) -> String,
    ): List<EnrichedDistractor> {
        val fromPack = glossed(entry.confusables, glossOf)
        return if (fromPack.isNotEmpty()) fromPack else current?.let { MaterialPool.decode(it.distractors) }.orEmpty()
    }

    /** 只写词面的候选由词典把释义补上；补不上的那一个丢掉——选项不能是一个空白。 */
    fun glossed(confusables: List<Confusable>, glossOf: (String) -> String): List<EnrichedDistractor> =
        confusables.mapNotNull { item ->
            val gloss = item.zh.ifBlank { glossOf(item.word) }.take(MaterialPack.MAX_GLOSS)
            if (gloss.isBlank()) null else EnrichedDistractor(item.word, gloss)
        }

    private fun sameContent(current: QuizMaterialEntity?, merged: QuizMaterialEntity): Boolean {
        if (current == null) return false
        if (current.definition != merged.definition) return false
        if (current.example != merged.example) return false
        if (current.exampleZh != merged.exampleZh) return false
        if (MaterialPool.decode(current.collocations) != MaterialPool.decode(merged.collocations)) return false
        return MaterialPool.decode(current.distractors) == MaterialPool.decode(merged.distractors)
    }
}
