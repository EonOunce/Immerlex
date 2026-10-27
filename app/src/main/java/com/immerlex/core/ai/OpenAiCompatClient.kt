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
import okhttp3.Response

/**
 * Talks to any OpenAI-compatible /chat/completions gateway. Deliberately thin:
 * retries, repair passes and validation live in [ChapterGenerator].
 */
class OpenAiCompatClient(
    private val config: AiConfig,
    private val http: OkHttpClient = buildClient(config),
) : LlmClient {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    override suspend fun complete(system: String, user: String): String {
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

        return call(request) { text ->
            extractContent(text) ?: throw LlmException(0, "response has no message content")
        }
    }

    override suspend fun probe(): String? = withContext(Dispatchers.IO) {
        try {
            val response =
                http.newCall(Request.Builder().url(config.modelsUrl()).get().build()).execute()
            response.use { if (it.isSuccessful) null else "HTTP ${it.code}: ${it.peekBodySnippet()}" }
        } catch (e: IOException) {
            "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
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

    private suspend fun <T> call(request: Request, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw LlmException(response.code, text.take(MAX_ERROR_BODY))
                    }
                    decode(text)
                }
            } catch (e: IOException) {
                throw LlmException(0, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            }
        }

    private fun extractContent(raw: String): String? = runCatching {
        val root = jsonParser.parseToJsonElement(raw).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        (choice["message"]?.jsonObject?.get("content") ?: choice["text"])
            ?.jsonPrimitive?.content
    }.getOrNull()

    private fun Response.peekBodySnippet(): String =
        runCatching { body?.string()?.take(MAX_ERROR_BODY).orEmpty() }.getOrNull()
            ?: "<unreadable body>"

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
