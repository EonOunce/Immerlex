package com.immerlex.core.quiz

import com.immerlex.core.db.QuizSessionDao
import com.immerlex.core.db.QuizSessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 没做完的那一组题。题目本身也存下来，所以退出去再回来看到的是同一组、同一题，
 * 不会重新出题，也不会为同一批词再发一次请求。作答早已逐题记账，续做只补 UI 进度。
 */
data class QuizResume(
    val mode: QuizMode,
    val questions: List<QuizQuestion>,
    val alternatives: Map<Long, QuizQuestion>,
    val wrongIds: List<Long>,
    val excludedIds: Set<Long>,
    val index: Int,
    val inPractice: Boolean,
    val chosen: Int?,
    val correct: Boolean,
    val correctCount: Int,
    val group: Int,
)

/** 只留当前这一组；做完或换组就按入口删掉，读不到（含存坏了）一律当没存过。 */
class QuizResumeStore(private val dao: QuizSessionDao) {

    suspend fun load(mode: QuizMode): QuizResume? =
        dao.byMode(mode.name)?.payload?.let(::decodeQuizResume)

    suspend fun save(resume: QuizResume) {
        dao.upsert(QuizSessionEntity(mode = resume.mode.name, payload = encodeQuizResume(resume)))
    }

    suspend fun clear(mode: QuizMode) {
        dao.delete(mode.name)
    }

    /** 今日页入口要据此决定"开始测验"还是"继续测验"：答完最后一题未点完成时，候选会先变成 0。 */
    suspend fun has(mode: QuizMode): Boolean = dao.exists(mode.name)
}

internal fun encodeQuizResume(resume: QuizResume): String =
    resumeJson.encodeToString(ResumeRow.serializer(), resume.toRow())

internal fun decodeQuizResume(payload: String): QuizResume? =
    runCatching { resumeJson.decodeFromString(ResumeRow.serializer(), payload).toModel() }
        .getOrNull()

private fun QuizResume.toRow() = ResumeRow(
    mode = mode.name,
    questions = questions.map { it.toRow() },
    alternatives = alternatives.values.map { it.toRow() },
    wrongIds = wrongIds,
    excludedIds = excludedIds.toList(),
    index = index,
    inPractice = inPractice,
    chosen = chosen,
    correct = correct,
    correctCount = correctCount,
    group = group,
)

private fun QuizQuestion.toRow() = QuestionRow(
    wordId = wordId,
    word = word,
    type = type.key,
    prompt = prompt,
    hint = promptHint,
    options = options.map { OptionRow(it.text, it.gloss, it.correct) },
)

private fun ResumeRow.toModel(): QuizResume? {
    val quizMode = QuizMode.entries.firstOrNull { it.name == mode } ?: return null
    val restored = questions.map { it.toModel() ?: return null }
    if (restored.isEmpty()) return null
    val retries = alternatives.map { it.toModel() ?: return null }.associateBy { it.wordId }
    val practice = wrongIds.mapNotNull { retries[it] }
    val total = if (inPractice) practice.size else restored.size
    if (index !in 0 until total) return null
    val current = if (inPractice) practice[index] else restored[index]
    if (chosen != null && chosen !in current.options.indices) return null
    return QuizResume(
        mode = quizMode,
        questions = restored,
        alternatives = retries,
        wrongIds = wrongIds,
        excludedIds = excludedIds.toSet(),
        index = index,
        inPractice = inPractice,
        chosen = chosen,
        correct = correct,
        correctCount = correctCount,
        group = group,
    )
}

private fun QuestionRow.toModel(): QuizQuestion? {
    val quizType = QuizType.fromKey(type) ?: return null
    if (options.isEmpty()) return null
    return QuizQuestion(
        wordId = wordId,
        word = word,
        type = quizType,
        prompt = prompt,
        promptHint = hint,
        options = options.map { QuizOption(it.text, it.gloss, it.correct) },
    )
}

private val resumeJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class ResumeRow(
    val mode: String,
    val questions: List<QuestionRow>,
    val alternatives: List<QuestionRow> = emptyList(),
    val wrongIds: List<Long> = emptyList(),
    val excludedIds: List<Long> = emptyList(),
    val index: Int = 0,
    val inPractice: Boolean = false,
    val chosen: Int? = null,
    val correct: Boolean = false,
    val correctCount: Int = 0,
    val group: Int = 1,
)

@Serializable
private data class QuestionRow(
    val wordId: Long,
    val word: String,
    val type: String,
    val prompt: String,
    val hint: String = "",
    val options: List<OptionRow>,
)

@Serializable
private data class OptionRow(
    val text: String,
    val gloss: String,
    val correct: Boolean = false,
)
