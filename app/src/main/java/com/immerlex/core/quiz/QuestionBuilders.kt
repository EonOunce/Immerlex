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
        val options = optionsFor(word, context, Face.GLOSS) ?: return null
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

private fun wordOptions(word: WordEntity, context: QuestionContext): List<QuizOption>? =
    optionsFor(word, context, Face.WORD)

/** 选项的面：英文单词本身，还是中文释义——两种题型只是取的面不同，来源顺序一样。 */
private enum class Face { WORD, GLOSS }

private fun optionsFor(word: WordEntity, context: QuestionContext, face: Face): List<QuizOption>? {
    val correct = correctChoice(word, face) ?: return null
    val key = correct.text.trim().lowercase()
    val confusable = context.confusables[word.id].orEmpty()
        .mapNotNull { choice(it.word, it.zh, face) }
        .filterNot { it.text.trim().lowercase() == key }
        .distinctBy { it.text.trim().lowercase() }
    val ai = context.enrichment.of(word)?.distractors.orEmpty().mapNotNull { choice(it.word, it.zh, face) }
    val bank = context.pool
        .filter { it.id != word.id }
        .mapNotNull { choice(it.word, it.translation, face) }
    val kept = confusable.take(KEEP_CONFUSABLES)
    val fixed = kept.map { it.text.trim().lowercase() }.toSet()
    val rest = (confusable.drop(kept.size) + ai + bank)
        .filterNot { val text = it.text.trim().lowercase(); text == key || text in fixed }
        .distinctBy { it.text.trim().lowercase() }
    val picked = kept + rotate(rest, context.attempts[word.id] ?: 0, MAX_DISTRACTORS - kept.size)
    if (picked.isEmpty()) return null
    return (listOf(correct) + picked)
        .sortedBy { stableHash(it.text.trim().lowercase(), context.seed + 31) }
        .map {
            QuizOption(
                text = it.text,
                gloss = it.gloss,
                correct = it.text.trim().lowercase() == key,
            )
        }
}

private fun correctChoice(word: WordEntity, face: Face): Choice? {
    val zh = word.translation.trim()
    if (zh.isBlank()) return null
    return when (face) {
        Face.WORD -> Choice(word.word.trim(), zh.ifBlank { word.definition.trim() })
        Face.GLOSS -> Choice(zh, zh)
    }
}

private fun choice(en: String, zh: String, face: Face): Choice? {
    val word = en.trim()
    val gloss = zh.trim()
    if (word.isEmpty() || gloss.isEmpty()) return null
    return if (face == Face.WORD) Choice(word, gloss) else Choice(gloss, gloss)
}

/**
 * 同一个词第 N 次被考，就从候选池的第 N×3 格开始抽，抽完一圈再回头。
 * 用户标过的易混词最多固定留两个，其余位置每次都换——避免连着几次看到同一道题。
 */
private fun rotate(items: List<Choice>, attempt: Int, size: Int): List<Choice> {
    if (size <= 0 || items.isEmpty()) return emptyList()
    if (items.size <= size) return items
    val start = Math.floorMod(attempt * size, items.size)
    return (items.drop(start) + items.take(start)).distinctBy { it.text.lowercase() }.take(size)
}

private fun stableHash(text: String, seed: Long): Long =
    (text.hashCode().toLong() * 1_000_003L + seed) xor (seed shl 7)

private fun blankWord(text: String, word: String): String? {
    val index = text.indexOf(word, ignoreCase = true)
    if (index < 0) return null
    return text.replaceRange(index, index + word.length, ClozeSentences.BLANK)
}

private const val MAX_DISTRACTORS = 3
private const val KEEP_CONFUSABLES = 2
