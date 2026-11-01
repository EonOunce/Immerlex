package com.immerlex.core.material

import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 取素材包用的那一次 GET：只把字节交回来，不认识 JSON，也不写任何本机状态。
 * 失败一律折成带 [MaterialSyncFailure] 的异常，中文文案在 app/ui 那头按机器码措辞。
 */
class HttpMaterialFetcher(
    private val http: OkHttpClient = buildClient(),
) : MaterialFetch {

    override suspend fun get(url: String): ByteArray = withContext(Dispatchers.IO) {
        val response = runCatching { http.newCall(Request.Builder().url(url).build()).execute() }
            .getOrElse { throw MaterialSyncException(classify(it), "${it.javaClass.simpleName}: ${it.message.orEmpty()}") }
        response.use {
            when {
                it.code == 404 -> throw MaterialSyncException(MaterialSyncFailure.HTTP, "404 $url")
                !it.isSuccessful -> throw MaterialSyncException(MaterialSyncFailure.HTTP, "GET $url -> HTTP ${it.code}")
                else -> it.body?.bytes()
                    ?: throw MaterialSyncException(MaterialSyncFailure.HTTP, "$url has no body")
            }
        }
    }

    private companion object {
        fun classify(e: Throwable): MaterialSyncFailure = when (e) {
            is SSLException -> MaterialSyncFailure.TLS
            is SocketTimeoutException -> MaterialSyncFailure.TIMEOUT
            else -> MaterialSyncFailure.UNREACHABLE
        }

        // 一片素材包约 40KB，慢网下 30 秒读完绰绰有余；整套 48 片靠总超时兜住不会永远转圈。
        fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
