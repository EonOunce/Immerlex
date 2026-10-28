package com.immerlex.core.ai

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Machine codes for why a gateway could not be reached; the UI owns the wording. */
enum class ProbeFailure { NONE, MISSING_KEY, UNAUTHORIZED, NOT_FOUND, TLS, TIMEOUT, UNREACHABLE, HTTP_ERROR }

data class ProbeResult(
    val failure: ProbeFailure,
    val models: List<String> = emptyList(),
    val detail: String = "",
) {
    val ok: Boolean get() = failure == ProbeFailure.NONE
}

/**
 * Turns transport and HTTP outcomes into [ProbeFailure], and reads model IDs out of
 * a /models payload. Gateways disagree on shape, so all three common layouts are accepted.
 */
object Probe {

    fun classify(status: Int, hasKey: Boolean): ProbeFailure = when {
        status in 200..299 -> ProbeFailure.NONE
        (status == 401 || status == 403) && !hasKey -> ProbeFailure.MISSING_KEY
        status == 401 || status == 403 -> ProbeFailure.UNAUTHORIZED
        status == 404 -> ProbeFailure.NOT_FOUND
        else -> ProbeFailure.HTTP_ERROR
    }

    fun classify(error: IOException): ProbeFailure = when (error) {
        is SocketTimeoutException -> ProbeFailure.TIMEOUT
        is SSLException -> ProbeFailure.TLS
        else -> ProbeFailure.UNREACHABLE
    }

    fun modelIds(raw: String): List<String> = runCatching {
        when (val root = Json.parseToJsonElement(raw)) {
            is JsonArray -> root.ids()
            is JsonObject ->
                (root["data"] as? JsonArray ?: root["models"] as? JsonArray ?: root["body"] as? JsonArray)?.ids()
                    ?: emptyList()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun JsonArray.ids(): List<String> = mapNotNull { element ->
        when (element) {
            is JsonObject -> (element["id"] ?: element["name"])?.jsonPrimitive?.contentOrNull
            else -> element.jsonPrimitive.contentOrNull
        }
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
