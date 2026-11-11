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
    /** 搭配题：题干是一个词自己的真实短语，四个选项是中文释义，答完各自揭开对应的英文短语。 */
    COLLOCATION("COLLOCATION", 2),
    ;

    companion object {
        fun fromKey(key: String?): QuizType? = entries.firstOrNull { it.key == key }
    }
}

/** [TRICKY] 只练用户亲手标过的易错词，给"今天还没练够"留一条不用阅读的路。 */
enum class QuizMode { DUE, EARLY, TRICKY, NEMESIS }

/** 选项：[text] 是摆在屏上的那一面，[gloss] 是答完才揭开的另一面。 */
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
    /** 这句题干的中文对照，只有素材包给的英文例句才有；答完才显示。 */
    val exampleZh: String = "",
)

/**
 * [alternatives] 是答错后用另一种题型再练一遍的备用题，练习不写复习记录。
 * [trickyIds] 是这一组里已经被标过易错的词：界面上只对没标过的词问一句"要不要标"。
 */
data class QuizSession(
    val mode: QuizMode,
    val questions: List<QuizQuestion>,
    val alternatives: Map<Long, QuizQuestion> = emptyMap(),
    val trickyIds: Set<Long> = emptySet(),
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
    /** 用户手动标过的易混词，比任何候选池都优先。 */
    val confusables: Map<Long, List<EnrichedDistractor>> = emptyMap(),
    /** 这个词被考过几次——决定这一轮从候选池的哪一格开始抽选项。 */
    val attempts: Map<Long, Int> = emptyMap(),
    /**
     * 别的词的搭配短语，只用来做搭配题的干扰项。用户自己的易混关系与它无涉：
     * 这里没有任何一条会被写回 `tricky_words`。
     */
    val collocationPool: List<EnrichedDistractor> = emptyList(),
    val seed: Long = 0,
)
