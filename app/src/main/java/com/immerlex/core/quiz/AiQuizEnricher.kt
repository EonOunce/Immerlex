package com.immerlex.core.quiz

import com.immerlex.core.ai.LlmClient
import com.immerlex.core.db.WordEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/** 一次会话最多补这么多词，避免把整本词库塞进 prompt。 */
class AiQuizEnricher(
    private val clientFor: () -> LlmClient,
    private val aiUsable: () -> Boolean = { true },
) : QuizEnricher {

    override suspend fun enrich(words: List<WordEntity>): QuizEnrichment {
        if (words.isEmpty() || !aiUsable()) return QuizEnrichment()
        return try {
            withTimeout(TIMEOUT_MS) {
                val raw = clientFor().complete(QuizPrompts.system(), QuizPrompts.user(words.take(MAX_WORDS)))
                QuizEnrichmentParser.parse(raw, words)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 测验必须能离线用：AI 只是补全，失败就退回本地题库。
            QuizEnrichment()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 12_000L
        const val MAX_WORDS = 30
    }
}
