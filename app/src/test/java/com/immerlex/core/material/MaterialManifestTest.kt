package com.immerlex.core.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MaterialManifestTest {

    @Test
    fun aWellFormedManifestListsEveryPackInOrder() {
        val manifest = MaterialManifestParser.parse(
            """
            {
              "materialVersion": 1,
              "generatedAt": "2026-11-02T09:00:00Z",
              "license": "CC-BY-NC-4.0",
              "packs": [
                {"file":"material-pack-01.json","words":100,"sha256":"${SHA}"},
                {"file":"material-pack-02.json","words":40,"sha256":"${SHA_UPPER}"}
              ]
            }
            """.trimIndent(),
        )

        assertNotNull(manifest)
        val read = manifest!!
        assertEquals(1, read.materialVersion)
        assertEquals("2026-11-02T09:00:00Z", read.generatedAt)
        assertEquals("CC-BY-NC-4.0", read.license)
        assertEquals(listOf("material-pack-01.json", "material-pack-02.json"), read.packs.map { it.file })
        assertEquals(listOf(100, 40), read.packs.map { it.words })
        // 校验值一律折成小写：清单里手写成大写也算同一份字节。
        assertEquals(SHA, read.packs[1].sha256)
        assertEquals(140, read.wordTotal())
    }

    @Test
    fun aManifestWithoutTheDownloadCriticalFieldsIsNoManifestAtAll() {
        assertNull(MaterialManifestParser.parse("not json at all"))
        assertNull(MaterialManifestParser.parse("""[{"file":"a.json"}]"""))
        assertNull(MaterialManifestParser.parse("""{"materialVersion":1,"packs":[]}"""))
        assertNull(MaterialManifestParser.parse("""{"materialVersion":1}"""))
        // 缺校验值的分片没法验字节，收下就等于闭眼导入。
        assertNull(MaterialManifestParser.parse("""{"materialVersion":1,"packs":[{"file":"a.json","words":10}]}"""))
        // 短一位、长了别的字符，都不是 SHA-256。
        assertNull(
            MaterialManifestParser.parse(
                """{"materialVersion":1,"packs":[{"file":"a.json","sha256":"${SHA.drop(1)}"}]}""",
            ),
        )
        assertNull(
            MaterialManifestParser.parse(
                """{"materialVersion":1,"packs":[{"file":"a.json","sha256":"${SHA.dropLast(1)}z"}]}""",
            ),
        )
    }

    @Test
    fun aPackNameThatPointsSomewhereElseIsRejected() {
        // 分片和清单在同一目录里；能带路径就等于清单可以把人往别处指。
        listOf("../evil.json", "sub/dir.json", "https://example.com/a.json", "dir\\a.json").forEach { name ->
            assertNull(name, MaterialManifestParser.parse("""{"materialVersion":1,"packs":[{"file":"$name","sha256":"$SHA"}]}"""))
        }
    }

    @Test
    fun aNewerFormatThanThisAppCanReadIsNotGuessedAt() {
        val newer = MaterialManifestParser.MAX_SUPPORTED_VERSION + 1
        assertNull(
            MaterialManifestParser.parse(
                """{"materialVersion":$newer,"packs":[{"file":"a.json","sha256":"$SHA"}]}""",
            ),
        )
    }

    @Test
    fun aManifestMayLeaveOutTheDescriptiveFields() {
        val manifest = MaterialManifestParser.parse(
            """{"materialVersion":1,"packs":[{"name":"pack-07.json","sha":"$SHA"}]}""",
        )

        assertNotNull(manifest)
        assertEquals("pack-07.json", manifest!!.packs.single().file)
        assertEquals(0, manifest.packs.single().words)
        assertEquals("", manifest.license)
    }

    @Test
    fun theChecksumOfKnownBytesMatchesThePublishedVector() {
        assertEquals(ABC_SHA, MaterialChecksum.sha256("abc".toByteArray()))
        assertEquals(true, MaterialChecksum.matches("abc".toByteArray(), ABC_SHA.uppercase()))
        assertEquals(false, MaterialChecksum.matches("abcd".toByteArray(), ABC_SHA))
    }

    private companion object {
        const val SHA = "7db780f1b0c1a2d3e4f5061728394a5b6c7d8e9fa0b1c2d3e4f5061728394a5b"
        const val SHA_UPPER = "7DB780F1B0C1A2D3E4F5061728394A5B6C7D8E9FA0B1C2D3E4F5061728394A5B"
        const val ABC_SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }
}
