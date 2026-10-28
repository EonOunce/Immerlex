package com.immerlex.core.ai

import com.immerlex.core.bible.BibleDraft
import com.immerlex.core.bible.CastSeed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BibleDoc(
    val tone: String = "",
    val protagonist: String = "",
    val ending: String = "",
    val forbidden: List<String> = emptyList(),
    val cast: List<CastDoc> = emptyList(),
)

@Serializable
private data class CastDoc(val name: String = "", val role: String = "", val start: String = "")

sealed interface BibleOutcome {
    data class Drafted(val draft: BibleDraft, val attempts: Int) : BibleOutcome
    data class Rejected(val issues: List<ValidationIssue>, val attempts: Int) : BibleOutcome
    data class Failed(val failure: ProbeFailure? = null, val detail: String) : BibleOutcome
}

/**
 * 一段口述 → 一份故事档案，只花一次调用（docs/adr/0005 第 1 条：入口不做成聊天）。
 * 输出必须过 schema 校验才交给仓库落库；修不好就明说，禁止静默丢弃。
 */
class BibleExpander(
    private val clientFor: () -> LlmClient,
    private val maxRepairs: Int = 2,
) {
    suspend fun expand(seed: String): BibleOutcome {
        val client = clientFor()
        val task = PromptBuilder.bibleUser(seed)
        var previous = ""
        var issues = listOf(ValidationIssue("unparseable_json"))

        for (attempt in 0..maxRepairs) {
            val userPrompt = if (attempt == 0) task else PromptBuilder.repair(task, issues, previous)
            val raw = try {
                client.complete(PromptBuilder.bibleSystem(), userPrompt)
            } catch (e: LlmException) {
                return BibleOutcome.Failed(e.failure, e.message ?: "request failed")
            }
            previous = raw

            val parsed = ChapterParser.extractJson(raw)?.let { text ->
                runCatching { json.decodeFromString(BibleDoc.serializer(), text) }.getOrNull()
            }
            issues = validate(parsed)
            if (issues.isEmpty() && parsed != null) {
                return BibleOutcome.Drafted(parsed.toDraft(), attempt + 1)
            }
        }
        return BibleOutcome.Rejected(issues, maxRepairs + 1)
    }

    /** 缺栏打回去补；多余的栏位与超长的句子在 [toDraft] 里截掉，不必浪费一次调用。 */
    private fun validate(doc: BibleDoc?): List<ValidationIssue> {
        if (doc == null) return listOf(ValidationIssue("unparseable_json"))
        val issues = mutableListOf<ValidationIssue>()
        if (doc.tone.isBlank()) issues += ValidationIssue("missing_tone")
        if (doc.protagonist.isBlank()) issues += ValidationIssue("missing_protagonist")
        if (doc.cast.none { it.name.isNotBlank() }) issues += ValidationIssue("missing_cast")
        return issues
    }

    private fun BibleDoc.toDraft() = BibleDraft(
        tone = clip(tone),
        protagonist = clip(protagonist),
        ending = clip(ending),
        forbidden = forbidden.map { clip(it) }.filter { it.isNotBlank() }.take(MAX_FORBIDDEN),
        cast = cast.map { CastSeed(clip(it.name), clip(it.role), clip(it.start)) }
            .filter { it.name.isNotBlank() }
            .take(MAX_CAST),
    )

    private fun clip(text: String) = text.trim().take(MAX_LINE_CHARS)

    private companion object {
        const val MAX_LINE_CHARS = 160
        const val MAX_FORBIDDEN = 8
        const val MAX_CAST = 12

        val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    }
}
