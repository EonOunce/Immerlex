package com.immerlex.core.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to any OpenAI-compatible /chat/completions gateway. Deliberately thin:
 * retries, repair passes and validation live in [ChapterGenerator].
 */
class OpenAiCompatClient(
    private val config: AiConfig,
    private val http: OkHttpClient = buildClient(config),
) : LlmClient {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun complete(system: String, user: String): String =
        complete(system, user, config.timeoutSeconds * 1_000L)

    override suspend fun complete(system: String, user: String, timeoutMillis: Long): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", config.temperature)
            putJsonArray("messages") {
                add(jsonMessage("system", system))
                add(jsonMessage("user", user))
            }
            if (config.strictJson) putJsonObject("response_format") { put("type", "json_object") }
        }.toString()

        val request = baseBuilder(config.chatUrl())
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        return call(request, timeoutMillis) { text ->
            extractContent(text) ?: throw LlmException(0, "response has no message content")
        }
    }

    override suspend fun probe(): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val request = baseBuilder(config.modelsUrl()).get().build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val failure = Probe.classify(response.code, config.apiKey.isNotBlank())
                ProbeResult(
                    failure = failure,
                    models = if (failure == ProbeFailure.NONE) Probe.modelIds(body) else emptyList(),
                    detail = if (failure == ProbeFailure.NONE) "" else "HTTP ${response.code}: ${body.take(MAX_ERROR_BODY)}",
                )
            }
        } catch (e: IOException) {
            ProbeResult(Probe.classify(e), detail = "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    private fun baseBuilder(url: String): Request.Builder =
        Request.Builder().url(url).apply {
            if (config.apiKey.isNotBlank()) addHeader("Authorization", "Bearer ${config.apiKey}")
        }

    private fun jsonMessage(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    private suspend fun <T> call(request: Request, timeoutMillis: Long?, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                val call = http.newCall(request)
                if (timeoutMillis != null) {
                    call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
                }
                call.execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw LlmException(
                            response.code,
                            text.take(MAX_ERROR_BODY),
                            Probe.classify(response.code, config.apiKey.isNotBlank()),
                        )
                    }
                    decode(text)
                }
            } catch (e: IOException) {
                throw LlmException(
                    0,
                    "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                    Probe.classify(e),
                )
            }
        }

    private fun extractContent(raw: String): String? = runCatching {
        val root = jsonParser.parseToJsonElement(raw).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        (choice["message"]?.jsonObject?.get("content") ?: choice["text"])
            ?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        private const val MAX_ERROR_BODY = 400
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildClient(config: AiConfig): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }
}
