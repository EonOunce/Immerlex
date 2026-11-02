package com.immerlex.core.material

import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 取素材包这一步能出的错。UI 只按它措辞，原始文本留在 detail 里。 */
enum class MaterialSyncFailure { NOT_CONFIGURED, BAD_URL, UNREACHABLE, TIMEOUT, TLS, HTTP, MANIFEST, CHECKSUM, PACK, LOCAL }

class MaterialSyncException(
    val failure: MaterialSyncFailure,
    override val message: String,
) : RuntimeException(message)

/** 正在取第几片：总数要到清单读回来才知道。 */
data class MaterialSyncStep(val index: Int, val total: Int, val file: String)

sealed interface MaterialSyncOutcome {
    /**
     * [words] 是清单自己报的词数，不是本机库里的行数——两边说的是两件事。
     * [added] 是本机原先没有的词，[updated] 是本来就有、这次内容变了的词；再取一遍同一套包时两者都是 0。
     */
    data class Fetched(
        val packs: Int,
        val words: Int,
        val dropped: Map<DropReason, Int> = emptyMap(),
        val badCollocations: Int = 0,
        val added: Int = 0,
        val updated: Int = 0,
    ) : MaterialSyncOutcome {
        fun droppedTotal(): Int = dropped.values.sum()
    }

    data class Failed(val failure: MaterialSyncFailure, val detail: String = "") : MaterialSyncOutcome
}

/** 一次 GET。真实实现是 OkHttp（失败自带 [MaterialSyncFailure]），测试里换成内存里的字节。 */
fun interface MaterialFetch {
    suspend fun get(url: String): ByteArray
}

/** 整片字节一起算，不断流、不分块：对不上就说明到手的不是清单里那一份。 */
object MaterialChecksum {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun matches(bytes: ByteArray, expected: String): Boolean = sha256(bytes) == expected.lowercase()

    fun decode(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)
}

/**
 * 从清单地址把分片素材包一片一片取回来、按校验值收下、导入。
 *
 * 清单说什么都不算，校验值对不上就不导入那一片；一片导入是一个事务，所以中途失败时
 * 已经取回的前几片照旧留在库里——素材是填充式合并，重试不会把已有的字段改掉。
 * 分片文件名一律相对清单地址解析，所以换镜像只改一个地址。
 */
class MaterialSync(
    private val manifestUrl: () -> String,
    private val fetch: MaterialFetch,
    private val importPack: suspend (String) -> ImportOutcome,
) {

    suspend fun fetchAll(onStep: (MaterialSyncStep) -> Unit = {}): MaterialSyncOutcome = try {
        val base = requireBaseUrl()
        val manifest = MaterialManifestParser.parse(MaterialChecksum.decode(get(base.toString())))
            ?: throw MaterialSyncException(MaterialSyncFailure.MANIFEST, "unreadable manifest")
        val dropped = mutableMapOf<DropReason, Int>()
        var badCollocations = 0
        var added = 0
        var updated = 0
        manifest.packs.forEachIndexed { index, ref ->
            onStep(MaterialSyncStep(index + 1, manifest.packs.size, ref.file))
            val url = base.resolve(ref.file)
                ?: throw MaterialSyncException(MaterialSyncFailure.MANIFEST, "cannot resolve ${ref.file}")
            val bytes = get(url.toString())
            if (!MaterialChecksum.matches(bytes, ref.sha256)) {
                throw MaterialSyncException(MaterialSyncFailure.CHECKSUM, ref.file)
            }
            when (val outcome = importChecked(MaterialChecksum.decode(bytes))) {
                is ImportOutcome.Rejected -> throw MaterialSyncException(
                    MaterialSyncFailure.PACK,
                    "${ref.file} -> ${outcome.failure}",
                )

                is ImportOutcome.Imported -> {
                    outcome.dropped.forEach { (reason, count) -> dropped[reason] = (dropped[reason] ?: 0) + count }
                    badCollocations += outcome.badCollocations
                    added += outcome.added
                    updated += outcome.updated()
                }
            }
        }
        MaterialSyncOutcome.Fetched(
            manifest.packs.size, manifest.wordTotal(), dropped, badCollocations, added, updated,
        )
    } catch (e: MaterialSyncException) {
        MaterialSyncOutcome.Failed(e.failure, e.message)
    } catch (e: CancellationException) {
        // 页面没了不是"本机写坏了"，取消要照常往上传。
        throw e
    } catch (e: Exception) {
        // 库写不进去是最后一件事：它不是网络问题，别把它报成"连不上"。
        MaterialSyncOutcome.Failed(MaterialSyncFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }

    private fun requireBaseUrl() = manifestUrl().trim().let { url ->
        when {
            url.isEmpty() -> throw MaterialSyncException(MaterialSyncFailure.NOT_CONFIGURED, "no manifest URL")
            else -> url.toHttpUrlOrNull()
                ?: throw MaterialSyncException(MaterialSyncFailure.BAD_URL, "not a URL: $url")
        }
    }

    /** 取一次：实现方没把异常折成机器码的，这里按连接类故障补齐，UI 才说得出人话。 */
    private suspend fun get(url: String): ByteArray = try {
        fetch.get(url)
    } catch (e: MaterialSyncException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MaterialSyncException(classify(e), "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }

    private suspend fun importChecked(text: String): ImportOutcome = try {
        importPack(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MaterialSyncException(MaterialSyncFailure.LOCAL, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }
}

private fun classify(e: Throwable): MaterialSyncFailure = when (e) {
    is java.net.SocketTimeoutException -> MaterialSyncFailure.TIMEOUT
    is javax.net.ssl.SSLException -> MaterialSyncFailure.TLS
    else -> MaterialSyncFailure.UNREACHABLE
}
