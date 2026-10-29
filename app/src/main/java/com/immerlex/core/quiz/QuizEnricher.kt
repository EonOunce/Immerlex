package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity

data class EnrichedDistractor(val word: String, val zh: String)

data class EnrichedWord(
    val definition: String = "",
    val example: String = "",
    val distractors: List<EnrichedDistractor> = emptyList(),
)

/** 按词库 id 索引：出题时直接 `of(word)`，不用再按拼写匹配。 */
data class QuizEnrichment(val entries: Map<Long, EnrichedWord> = emptyMap()) {
    fun of(word: WordEntity): EnrichedWord? = entries[word.id]
}

/**
 * AI 只做补全：缺英文释义、缺例句、干扰项不够时给一点素材。
 * 失败、超时、没配置都必须安静地退化成纯本地题库——测验不依赖 API key。
 */
interface QuizEnricher {
    suspend fun enrich(words: List<WordEntity>): QuizEnrichment
}
