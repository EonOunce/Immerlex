package com.immerlex.core.quiz

/**
 * 题型只决定"怎么问"，不决定"什么时候复习"：一个词始终只有一张 FSRS 卡。
 * [tier] 是解锁档位——0 是新词就能考的题型，1 要成功回忆过一次，2 要中→英也答对过。
 * 以后加题型：加一个枚举 + 一个 [QuestionBuilder]，闸门、弱项选择、降档都会自动生效。
 * 见 docs/adr/0012-recall-events-and-quiz-types.md。
 */
enum class QuizType(val key: String, val tier: Int) {
    EN_ZH("EN_ZH", 0),
    CLOZE("CLOZE", 0),
    ZH_EN("ZH_EN", 1),
    DEFINITION("DEFINITION", 2),
    ;

    companion object {
        fun fromKey(key: String?): QuizType? = entries.firstOrNull { it.key == key }
    }
}

enum class QuizMode { DUE, EARLY }

/** 选项：[text] 是选项本身，[gloss] 是答完后要展示给用户的释义。 */
data class QuizOption(
    val text: String,
    val gloss: String,
    val correct: Boolean,
)

data class QuizQuestion(
    val wordId: Long,
    val word: String,
    val type: QuizType,
    val prompt: String,
    val promptHint: String = "",
    val options: List<QuizOption>,
) {
    val correctText: String get() = options.firstOrNull { it.correct }?.text.orEmpty()
    val correctGloss: String get() = options.firstOrNull { it.correct }?.gloss.orEmpty()
}

/** [alternatives] 是答错后用另一种题型再练一遍的备用题，练习不写复习记录。 */
data class QuizSession(
    val mode: QuizMode,
    val questions: List<QuizQuestion>,
    val alternatives: Map<Long, QuizQuestion> = emptyMap(),
) {
    val isEmpty: Boolean get() = questions.isEmpty()
    val wordIds: Set<Long> get() = questions.mapTo(LinkedHashSet()) { it.wordId }
}

/** 出题时可用的本地素材与 AI 补全结果。 */
data class QuestionContext(
    val pool: List<com.immerlex.core.db.WordEntity> = emptyList(),
    /** 小写单词 → 已挖空的句子，来自最近写过的章节。 */
    val sentences: Map<String, List<String>> = emptyMap(),
    val enrichment: QuizEnrichment = QuizEnrichment(),
    val seed: Long = 0,
)
