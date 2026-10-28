package com.immerlex.core.backup

import java.io.File
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 备份失败的机器码：UI 只按它措辞，原始文本留在 detail 里。 */
enum class BackupFailure { NOT_CONFIGURED, UNAUTHORIZED, MISSING_FOLDER, TIMEOUT, TLS, UNREACHABLE, HTTP, CORRUPT, LOCAL }

class WebDavException(val failure: BackupFailure, override val message: String) : RuntimeException(message)

/**
 * 坚果云 WebDAV 的最小客户端：列目录、建目录、传、删、下。
 * 传输由 HTTPS 保证，文件本身不加密（docs/adr/0006 第 5 条），所以这里没有任何加解密。
 */
class WebDavClient(
    endpoint: String,
    account: String,
    password: String,
    private val http: OkHttpClient = buildClient(),
) {
    private val root: HttpUrl = endpoint.trim().toHttpUrlOrNull()
        ?: throw WebDavException(BackupFailure.NOT_CONFIGURED, "not a WebDAV URL: $endpoint")

    private val credentials = Credentials.basic(account.trim(), password)

    suspend fun list(): List<RemoteFile> =
        call(xmlPropfind()) { body -> Propfind.parse(body) }

    /** 目录不存在就 MKCOL 一次；坚果云对已存在的目录回 405，那正是"已经有了"。 */
    suspend fun ensureFolder() {
        val code = code(builder(folder()).method("MKCOL", EMPTY_BODY).build())
        if (code in 200..299 || code == 405) return
        throw WebDavException(BackupFailure.MISSING_FOLDER, "MKCOL $COLLECTION -> HTTP $code")
    }

    suspend fun upload(name: String, file: File) {
        val body = withContext(Dispatchers.IO) { file.readBytes() }.toRequestBody(GZIP_TYPE)
        val code = code(builder(child(name)).put(body).build())
        if (code !in 200..299) throw WebDavException(BackupFailure.HTTP, "PUT $name -> HTTP $code")
    }

    suspend fun delete(name: String) {
        val code = code(builder(child(name)).delete().build())
        // 404 说明它已经不在云端了，不算失败。
        if (code !in 200..299 && code != 404) {
            throw WebDavException(BackupFailure.HTTP, "DELETE $name -> HTTP $code")
        }
    }

    suspend fun download(name: String, into: File) = withContext(Dispatchers.IO) {
        val response = runCatching { http.newCall(builder(child(name)).get().build()).execute() }
            .getOrElse { throw WebDavException(classify(it), describe(it)) }
        response.use {
            when {
                it.code == 401 || it.code == 403 -> throw unauthorized(it.code)
                !it.isSuccessful -> throw WebDavException(BackupFailure.HTTP, "GET $name -> HTTP ${it.code}")
                else -> it.body?.byteStream()?.use { source ->
                    into.outputStream().use { target -> source.copyTo(target) }
                } ?: throw WebDavException(BackupFailure.CORRUPT, "$name has no body")
            }
        }
    }

    /** 读一个文本响应（PROPFIND 的 XML），非 2xx 一律变成带码的异常。 */
    private suspend fun <T> call(request: Request, decode: (String) -> T): T = withContext(Dispatchers.IO) {
        val response = runCatching { http.newCall(request).execute() }
            .getOrElse { throw WebDavException(classify(it), describe(it)) }
        response.use {
            val body = it.body?.string().orEmpty()
            when {
                it.code == 401 || it.code == 403 -> throw unauthorized(it.code)
                !it.isSuccessful -> throw WebDavException(BackupFailure.HTTP, "HTTP ${it.code}: ${body.take(MAX_BODY)}")
                else -> decode(body)
            }
        }
    }

    private suspend fun code(request: Request): Int = withContext(Dispatchers.IO) {
        runCatching { http.newCall(request).execute().use { it.code } }
            .getOrElse { throw WebDavException(classify(it), describe(it)) }
    }

    private fun builder(url: HttpUrl): Request.Builder =
        Request.Builder().url(url).addHeader("Authorization", credentials)

    private fun xmlPropfind(): Request = builder(folder())
        .addHeader("Depth", "1")
        .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_TYPE))
        .build()

    private fun folder(): HttpUrl = root.newBuilder().addPathSegment(COLLECTION).build()

    private fun child(name: String): HttpUrl = folder().newBuilder().addPathSegment(name).build()

    private fun unauthorized(status: Int) =
        WebDavException(BackupFailure.UNAUTHORIZED, "HTTP $status")

    private companion object {
        const val COLLECTION = BackupNaming.COLLECTION
        const val MAX_BODY = 200
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        val XML_TYPE = "application/xml; charset=utf-8".toMediaType()
        val GZIP_TYPE = "application/gzip".toMediaType()
        const val PROPFIND_BODY =
            """<?xml version="1.0"?><propfind xmlns="DAV:"><prop>""" +
                """<getcontentlength xmlns="DAV:"/><displayname xmlns="DAV:"/>""" +
                """<getlastmodified xmlns="DAV:"/></prop></propfind>"""

        fun classify(e: Throwable): BackupFailure = when (e) {
            is SSLException -> BackupFailure.TLS
            is java.net.SocketTimeoutException -> BackupFailure.TIMEOUT
            else -> BackupFailure.UNREACHABLE
        }

        fun describe(e: Throwable): String = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
    }
}

private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .callTimeout(180, TimeUnit.SECONDS)
    .build()
