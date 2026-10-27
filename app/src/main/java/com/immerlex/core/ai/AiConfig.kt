package com.immerlex.core.ai

class LlmException(val httpStatus: Int, override val message: String) : RuntimeException(message)

/** Minimal OpenAI-compatible chat endpoint used for chapter generation. */
interface LlmClient {
    /** @return raw assistant text, usually a JSON object but possibly fenced. */
    suspend fun complete(system: String, user: String): String

    /** @return null when reachable and authorised, otherwise a human-readable reason. */
    suspend fun probe(): String?
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

    /** Accepts a host root, /v1, or the full /v1/chat/completions path. */
    fun chatUrl(): String = normalize(baseUrl) + "/chat/completions"
    fun modelsUrl(): String = normalize(baseUrl) + "/models"

    private fun normalize(base: String): String {
        val trimmed = base.trim().removeSuffix("/")
        val withoutSuffix = trimmed.removeSuffix("/chat/completions")
        return if (withoutSuffix.endsWith("/v1")) withoutSuffix else "$withoutSuffix/v1"
    }
}
