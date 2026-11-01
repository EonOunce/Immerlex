package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity

data class EnrichedDistractor(val word: String, val zh: String)

data class EnrichedWord(
    val definition: String = "",
    val example: String = "",
    /** [example] 的中文对照。只有素材包里的英文例句才带这一句，模型补的中文句子不需要。 */
    val exampleZh: String = "",
    val distractors: List<EnrichedDistractor> = emptyList(),
    /** 真实搭配（take measures / a reluctant smile）。只从素材包来——模型补不出来可靠搭配，也不值得再发一次请求。 */
    val collocations: List<EnrichedDistractor> = emptyList(),
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
