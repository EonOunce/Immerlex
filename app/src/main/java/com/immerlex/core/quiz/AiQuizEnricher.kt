package com.immerlex.core.quiz

import com.immerlex.core.ai.LlmClient
import com.immerlex.core.db.WordEntity
import kotlinx.coroutines.CancellationException

/** 一次会话最多补这么多词，避免把整本词库塞进 prompt；上限跟一组题数一致，40 个词一次问完。 */
class AiQuizEnricher(
    private val clientFor: () -> LlmClient,
    private val aiUsable: () -> Boolean = { true },
) : QuizEnricher {

    override suspend fun enrich(words: List<WordEntity>): QuizEnrichment {
        if (words.isEmpty() || !aiUsable()) return QuizEnrichment()
        return try {
            val raw = clientFor().complete(
                QuizPrompts.system(),
                QuizPrompts.user(words.take(MAX_WORDS)),
                timeoutMillis = TIMEOUT_MS,
            )
            QuizEnrichmentParser.parse(raw, words)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 测验必须能离线用：AI 只是补全，失败就退回本地题库。
            QuizEnrichment()
        }
    }

    private companion object {
        /** 到点就掐断；慢的网关再等也不会在几十秒内变快。 */
        const val TIMEOUT_MS = 45_000L
        const val MAX_WORDS = 40
    }
}
