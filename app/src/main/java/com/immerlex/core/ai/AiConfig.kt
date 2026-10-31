package com.immerlex.core.ai

/**
 * [failure] is the machine code the UI turns into wording; [message] keeps the raw gateway
 * text for anyone who opens a log. Never show [message] on its own — for a transport error
 * it is a Java exception name.
 */
class LlmException(
    val httpStatus: Int,
    override val message: String,
    val failure: ProbeFailure = ProbeFailure.HTTP_ERROR,
) : RuntimeException(message)

/** Minimal OpenAI-compatible chat endpoint used for chapter generation. */
interface LlmClient {
    /** @return raw assistant text, usually a JSON object but possibly fenced. */
    suspend fun complete(system: String, user: String): String

    /**
     * 单次调用限时。阅读/生成用默认长超时，测验这种"可等可不等"的补全用短超时，
     * 到点必须真的掐断连接，不能只取消协程让底层阻塞读继续挂两分钟。
     */
    suspend fun complete(system: String, user: String, timeoutMillis: Long): String =
        complete(system, user)

    /** Hits the gateway's own model list, which doubles as the connectivity and key check. */
    suspend fun probe(): ProbeResult
}

data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.9,
    val timeoutSeconds: Int = 180,
    /** Sends response_format=json_object; some gateways reject unknown fields. */
    val strictJson: Boolean = true,
) {
    val isUsable: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()

    /** Accepts a host root, a versioned path (/v1, or 智谱's /v4), or the full completions path. */
    fun chatUrl(): String = normalize(baseUrl) + "/chat/completions"
    fun modelsUrl(): String = normalize(baseUrl) + "/models"

    private fun normalize(base: String): String {
        val trimmed = base.trim().removeSuffix("/")
        val withoutSuffix = trimmed.removeSuffix("/chat/completions").removeSuffix("/completions")
        return if (VERSION_SEGMENT.matches(withoutSuffix.substringAfterLast('/'))) withoutSuffix
        else "$withoutSuffix/v1"
    }

    private companion object {
        val VERSION_SEGMENT = Regex("v\\d+")
    }
}
