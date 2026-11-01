package com.immerlex.core.material

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 取素材包的编排：清单 → 逐片 → 校验值 → 导入。`MaterialFetch` 就是那个网络边界，测试里换成内存字节。 */
class MaterialSyncTest {

    @Test
    fun everyPackInTheManifestIsFetchedInOrder() {
        val pack1 = packBody("harbour", "habitat")
        val pack2 = packBody("reluctant")
        val fetch = FakeFetch(site(pack1 to PACK_01, pack2 to PACK_02))
        val imported = mutableListOf<String>()
        val steps = mutableListOf<MaterialSyncStep>()

        val outcome = runBlocking { syncFetchingInto(fetch, imported).fetchAll { steps += it } }

        assertEquals(MaterialSyncOutcome.Fetched(2, 3), outcome)
        assertEquals(listOf(pack1, pack2), imported)
        assertEquals(listOf(MaterialSyncStep(1, 2, PACK_01), MaterialSyncStep(2, 2, PACK_02)), steps)
        // 分片相对清单地址解析，所以换镜像只改那一个地址。
        assertEquals(listOf(MANIFEST_URL, "$BASE/$PACK_01", "$BASE/$PACK_02"), fetch.asked)
    }

    @Test
    fun aChecksumMismatchStopsThatPackWithoutImportingIt() {
        val good = packBody("harbour")
        val files = site(good to PACK_01, packBody("reluctant") to PACK_02).toMutableMap()
        // 清单写着原来那份字节的校验值，到手的却多了一个空格：这就不该进库。
        files["$BASE/$PACK_02"] = (packBody("reluctant") + " ").toByteArray()
        val imported = mutableListOf<String>()

        val outcome = runBlocking { syncFetchingInto(FakeFetch(files), imported).fetchAll() }

        assertEquals(MaterialSyncFailure.CHECKSUM, failed(outcome))
        assertEquals(listOf(good), imported)
    }

    @Test
    fun anUnreadableManifestImportsNothing() {
        val files = site(packBody("harbour") to PACK_01).toMutableMap()
        files[MANIFEST_URL] = "这里没有清单".toByteArray()
        val imported = mutableListOf<String>()

        val outcome = runBlocking { syncFetchingInto(FakeFetch(files), imported).fetchAll() }

        assertEquals(MaterialSyncFailure.MANIFEST, failed(outcome))
        assertTrue(imported.isEmpty())
    }

    @Test
    fun aPackTheLibraryRefusesStopsTheRunAndTheEarlierOnesStay() {
        val good = packBody("harbour")
        val broken = packBody("")
        val fetch = FakeFetch(
            site(good to PACK_01, broken to PACK_02, packBody("reluctant") to PACK_03),
        )
        val imported = mutableListOf<String>()
        val sync = MaterialSync({ MANIFEST_URL }, fetch::get) { text ->
            imported += text
            if (text == broken) ImportOutcome.Rejected(PackFailure.NOT_ARRAY) else ONE_ROW
        }

        assertEquals(MaterialSyncFailure.PACK, failed(runBlocking { sync.fetchAll() }))
        // 前一片已经进库的事实不回滚（素材是填充式合并），但坏掉之后的那片不再取。
        assertEquals(listOf(good, broken), imported)
    }

    @Test
    fun droppedRowsAndBadCollocationsAddUpAcrossPacks() {
        val fetch = FakeFetch(site(packBody("harbour") to PACK_01, packBody("reluctant") to PACK_02))
        val sync = MaterialSync({ MANIFEST_URL }, fetch::get) {
            ImportOutcome.Imported(1, mapOf(DropReason.NO_WORD to 2, DropReason.NO_CONTENT to 1), badCollocations = 3)
        }

        val outcome = runBlocking { sync.fetchAll() } as MaterialSyncOutcome.Fetched
        assertEquals(6, outcome.droppedTotal())
        assertEquals(6, outcome.badCollocations)
    }

    @Test
    fun noUrlAndNotAUrlBothFailBeforeAnyNetworkCall() {
        val fetch = FakeFetch(site(packBody("harbour") to PACK_01))

        fun failureOf(url: String) = failed(runBlocking { MaterialSync({ url }, fetch::get) { ONE_ROW }.fetchAll() })

        assertEquals(MaterialSyncFailure.NOT_CONFIGURED, failureOf(""))
        assertEquals(MaterialSyncFailure.BAD_URL, failureOf("dav.jianguoyun.com"))
        assertTrue(fetch.asked.isEmpty())
    }

    @Test
    fun aConnectionFailureKeepsItsOwnCode() {
        val files = site(packBody("harbour") to PACK_01)

        fun failureWhen(throwable: Throwable): MaterialSyncFailure {
            val fetch = FakeFetch(files).apply { failing = mapOf(MANIFEST_URL to throwable) }
            return failed(runBlocking { MaterialSync({ MANIFEST_URL }, fetch::get) { ONE_ROW }.fetchAll() })
        }

        assertEquals(MaterialSyncFailure.TIMEOUT, failureWhen(SocketTimeoutException("timeout")))
        assertEquals(MaterialSyncFailure.HTTP, failureWhen(MaterialSyncException(MaterialSyncFailure.HTTP, "404")))
        // 实现方没折成机器码的连接错误也算连不上，不能报成本机写坏。
        assertEquals(MaterialSyncFailure.UNREACHABLE, failureWhen(IllegalStateException("no route")))
    }

    private fun syncFetchingInto(fetch: MaterialFetch, imported: MutableList<String>) =
        MaterialSync({ MANIFEST_URL }, fetch::get) { text ->
            imported += text
            ONE_ROW
        }

    /** 几片素材 + 那份写着它们的清单，全摆在同一个目录下。 */
    private fun site(vararg packs: Pair<String, String>): Map<String, ByteArray> {
        val refs = packs.joinToString(",") { (body, name) ->
            """{"file":"$name","words":${body.split(WORD_KEY).size - 1},""" +
                """"sha256":"${MaterialChecksum.sha256(body.toByteArray())}"}"""
        }
        val files = LinkedHashMap<String, ByteArray>()
        packs.forEach { (body, name) -> files["$BASE/$name"] = body.toByteArray() }
        files[MANIFEST_URL] =
            """{"materialVersion":1,"generatedAt":"2026-11-02","license":"cc","packs":[$refs]}""".toByteArray()
        return files
    }

    private fun packBody(vararg words: String): String =
        words.joinToString(prefix = "[", postfix = "]") { """{"word":"$it","definition":"a definition"}""" }

    private class FakeFetch(private val files: Map<String, ByteArray>) : MaterialFetch {
        val asked = mutableListOf<String>()
        var failing: Map<String, Throwable> = emptyMap()

        override suspend fun get(url: String): ByteArray {
            asked += url
            failing[url]?.let { throw it }
            return files[url] ?: throw MaterialSyncException(MaterialSyncFailure.HTTP, "404 $url")
        }
    }

    private companion object {
        const val BASE = "https://example.com/releases/latest/download"
        const val MANIFEST_URL = "$BASE/manifest.json"
        const val PACK_01 = "material-pack-01.json"
        const val PACK_02 = "material-pack-02.json"
        const val PACK_03 = "material-pack-03.json"
        const val WORD_KEY = "\"word\":"
        val ONE_ROW = ImportOutcome.Imported(1, emptyMap())

        fun failed(outcome: MaterialSyncOutcome): MaterialSyncFailure = (outcome as MaterialSyncOutcome.Failed).failure
    }
}
