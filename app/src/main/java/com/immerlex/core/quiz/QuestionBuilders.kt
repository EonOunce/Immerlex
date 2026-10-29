package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity

/**
 * 扩展契约：新题型 = 新 [QuizType] + 一个本接口实现。闸门、弱项选题、答错重练
 * 都由 [QuizPolicy] 和 [QuizService] 统一处理，不需要改调度与记账。
 */
interface QuestionBuilder {
    val type: QuizType
    fun build(word: WordEntity, context: QuestionContext): QuizQuestion?
}

private data class Choice(val text: String, val gloss: String)

/** 英→中：选项是中文释义。 */
class EnToZhBuilder : QuestionBuilder {
    override val type = QuizType.EN_ZH

    override fun build(word: WordEntity, context: QuestionContext): QuizQuestion? {
        val correct = word.translation.trim()
        if (correct.isBlank()) return null
        val extra = context.enrichment.of(word)?.distractors.orEmpty()
            .map { Choice(it.zh.trim(), it.zh.trim()) }
        val options = optionsOf(
            correct = Choice(correct, correct),
            candidates = context.pool
                .filter { it.id != word.id }
                .map { Choice(it.translation.trim(), it.translation.trim()) }
                .filter { it.text.isNotBlank() },
            extra = extra,
            seed = context.seed + word.id,
        ) ?: return null
        return QuizQuestion(
            wordId = word.id,
            word = word.word,
            type = type,
            prompt = word.word,
            promptHint = word.phonetic,
            options = options,
        )
    }
}

/** 中→英：题干是中文，选项是英文单词。 */
class ZhToEnBuilder : QuestionBuilder {
    override val type = QuizType.ZH_EN

    override fun build(word: WordEntity, context: QuestionContext): QuizQuestion? {
        val prompt = word.translation.trim()
        if (prompt.isBlank()) return null
        val options = wordOptions(word, context) ?: return null
        return QuizQuestion(
            wordId = word.id,
            word = word.word,
            type = type,
            prompt = prompt,
            options = options,
        )
    }
}

/** 句子挖空：优先用章节里真实出现过的句子，其次用 AI 补的例句。 */
class ClozeBuilder : QuestionBuilder {
    override val type = QuizType.CLOZE

    override fun build(word: WordEntity, context: QuestionContext): QuizQuestion? {
        val key = word.word.trim().lowercase()
        val sentence = context.sentences[key]?.firstOrNull()
            ?: context.enrichment.of(word)?.example?.let { blankWord(it, word.word) }
            ?: return null
        val options = wordOptions(word, context) ?: return null
        return QuizQuestion(
            wordId = word.id,
            word = word.word,
            type = type,
            prompt = sentence,
            options = options,
        )
    }
}

/** 英文释义选词：用词库里的英文释义，没有就等 AI 补；仍没有则该题型不可用。 */
class DefinitionBuilder : QuestionBuilder {
    override val type = QuizType.DEFINITION

    override fun build(word: WordEntity, context: QuestionContext): QuizQuestion? {
        val definition = word.definition.trim()
            .ifBlank { context.enrichment.of(word)?.definition?.trim().orEmpty() }
        if (definition.isBlank()) return null
        val options = wordOptions(word, context) ?: return null
        return QuizQuestion(
            wordId = word.id,
            word = word.word,
            type = type,
            prompt = definition,
            options = options,
        )
    }
}

val DEFAULT_QUESTION_BUILDERS: List<QuestionBuilder> = listOf(
    EnToZhBuilder(),
    ClozeBuilder(),
    ZhToEnBuilder(),
    DefinitionBuilder(),
)

private fun wordOptions(word: WordEntity, context: QuestionContext): List<QuizOption>? {
    val correctGloss = word.translation.trim().ifBlank { word.definition.trim() }
    if (correctGloss.isBlank()) return null
    val extra = context.enrichment.of(word)?.distractors.orEmpty()
        .map { Choice(it.word.trim(), it.zh.trim()) }
    return optionsOf(
        correct = Choice(word.word, correctGloss),
        candidates = context.pool
            .filter { it.id != word.id }
            .map { Choice(it.word, it.translation.trim().ifBlank { it.definition.trim() }) }
            .filter { it.text.isNotBlank() && it.gloss.isNotBlank() },
        extra = extra.filter { it.text.isNotBlank() && it.gloss.isNotBlank() },
        seed = context.seed + word.id,
    )
}

private fun optionsOf(
    correct: Choice,
    candidates: List<Choice>,
    extra: List<Choice>,
    seed: Long,
): List<QuizOption>? {
    val correctKey = correct.text.trim().lowercase()
    val distractors = (candidates + extra)
        .filter { it.text.isNotBlank() && it.gloss.isNotBlank() }
        .filter { it.text.trim().lowercase() != correctKey }
        .distinctBy { it.text.trim().lowercase() }
        .sortedBy { stableHash(it.text.trim().lowercase(), seed) }
        .take(MAX_DISTRACTORS)
    if (distractors.isEmpty()) return null
    return (listOf(correct) + distractors)
        .sortedBy { stableHash(it.text.trim().lowercase(), seed + 31) }
        .map {
            QuizOption(
                text = it.text,
                gloss = it.gloss,
                correct = it.text == correct.text,
            )
        }
}

private fun stableHash(text: String, seed: Long): Long =
    (text.hashCode().toLong() * 1_000_003L + seed) xor (seed shl 7)

private fun blankWord(text: String, word: String): String? {
    val index = text.indexOf(word, ignoreCase = true)
    if (index < 0) return null
    return text.replaceRange(index, index + word.length, ClozeSentences.BLANK)
}

private const val MAX_DISTRACTORS = 3
