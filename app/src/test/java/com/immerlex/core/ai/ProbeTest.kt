package com.immerlex.core.ai

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeTest {

    @Test
    fun classifiesAuthStatusByWhetherAKeyWasSent() {
        assertEquals(ProbeFailure.MISSING_KEY, Probe.classify(401, hasKey = false))
        assertEquals(ProbeFailure.UNAUTHORIZED, Probe.classify(401, hasKey = true))
        assertEquals(ProbeFailure.UNAUTHORIZED, Probe.classify(403, hasKey = true))
        assertEquals(ProbeFailure.NOT_FOUND, Probe.classify(404, hasKey = true))
        assertEquals(ProbeFailure.HTTP_ERROR, Probe.classify(500, hasKey = true))
        assertEquals(ProbeFailure.NONE, Probe.classify(200, hasKey = false))
    }

    @Test
    fun classifiesTransportErrors() {
        assertEquals(ProbeFailure.TIMEOUT, Probe.classify(SocketTimeoutException()))
        assertEquals(ProbeFailure.TLS, Probe.classify(SSLException("handshake")))
        assertEquals(ProbeFailure.UNREACHABLE, Probe.classify(IOException("no route")))
    }

    @Test
    fun readsModelIdsFromEveryShapeGatewaysUse() {
        assertEquals(
            listOf("glm-4.5", "glm-4-plus"),
            Probe.modelIds("""{"data":[{"id":"glm-4.5"},{"id":"glm-4-plus"},{"built_in":true}]}"""),
        )
        assertEquals(listOf("a", "b"), Probe.modelIds("""{"models":[{"name":"a"},{"id":"b"}]}"""))
        assertEquals(listOf("a", "b"), Probe.modelIds("""["a","b"]"""))
        assertEquals(listOf("a"), Probe.modelIds("""{"data":[{"id":"a"},{"id":"a "},{"id":" "}]}"""))
        assertEquals(emptyList<String>(), Probe.modelIds("""{"error":{"message":"upstream"}}"""))
        assertEquals(emptyList<String>(), Probe.modelIds("not json at all"))
    }

    @Test
    fun normalizesEveryBaseUrlStyleToItsOwnVersionSegment() {
        assertEquals(
            "https://api.deepseek.com/v1/models",
            AiConfig(baseUrl = "https://api.deepseek.com").modelsUrl(),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/models",
            AiConfig(baseUrl = "https://open.bigmodel.cn/api/paas/v4").modelsUrl(),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiConfig(baseUrl = "https://api.openai.com/v1/chat/completions").chatUrl(),
        )
        assertEquals(
            "http://192.168.1.9:11434/v1/models",
            AiConfig(baseUrl = "http://192.168.1.9:11434/").modelsUrl(),
        )
    }
}

class OpenAiCompatClientProbeTest {

    private class Stub(status: Int, private val body: String, private val failure: IOException? = null) {
        var authorization: String? = null
            private set
        var requestedUrl: String? = null
            private set

        val http: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                authorization = request.header("Authorization")
                requestedUrl = request.url.toString()
                failure?.let { throw it }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("stub")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
    }

    @Test
    fun probeSendsTheKeyAndReadsTheModelList(): Unit = runBlocking {
        val stub = Stub(200, """{"data":[{"id":"deepseek-flash"},{"id":"deepseek-v4"}]}""")
        val result = OpenAiCompatClient(
            AiConfig(baseUrl = "https://api.deepseek.com", apiKey = "sk-test", model = "deepseek-flash"),
            http = stub.http,
        ).probe()

        assertTrue(result.ok)
        assertEquals(listOf("deepseek-flash", "deepseek-v4"), result.models)
        assertEquals("https://api.deepseek.com/v1/models", stub.requestedUrl)
        assertEquals("Bearer sk-test", stub.authorization)
    }

    @Test
    fun probeWithoutAKeyNeverSendsTheHeaderAndSaysSo(): Unit = runBlocking {
        val stub = Stub(401, """{"error":{"message":"Authentication Fails (missing key)"}}""")
        val result = OpenAiCompatClient(
            AiConfig(baseUrl = "https://api.deepseek.com", model = "deepseek-flash"),
            http = stub.http,
        ).probe()

        assertFalse(result.ok)
        assertNull(stub.authorization)
        assertEquals(ProbeFailure.MISSING_KEY, result.failure)
        assertTrue(result.detail.contains("HTTP 401"))
    }

    @Test
    fun probeTurnsIoFailuresIntoMachineCodes(): Unit = runBlocking {
        val stub = Stub(200, "", failure = IOException("Unable to resolve host"))
        val result = OpenAiCompatClient(
            AiConfig(baseUrl = "https://api.deepseek.com", apiKey = "sk-test", model = "deepseek-flash"),
            http = stub.http,
        ).probe()

        assertEquals(ProbeFailure.UNREACHABLE, result.failure)
        assertTrue(result.detail.startsWith("IOException"))
    }
}
