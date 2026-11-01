package com.immerlex.core.material

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** 清单里的一片：文件名、这一片写了多少个词、这份字节的 SHA-256。 */
data class MaterialPackRef(
    val file: String,
    val words: Int,
    val sha256: String,
)

/** 一整套分片素材包的来历。`materialVersion` 是格式版本，不是词的版本。 */
data class MaterialManifest(
    val materialVersion: Int,
    val generatedAt: String,
    val license: String,
    val packs: List<MaterialPackRef>,
) {
    fun wordTotal(): Int = packs.sumOf { it.words }
}

/**
 * 素材包清单的读法。清单只有四件事：这套素材是什么版本、什么时候生成、什么许可、
 * 由哪几片组成、每片多少词、每片的校验值。
 *
 * 校验值必须是小写 64 位十六进制，文件名必须是一个裸文件名（不含路径）——
 * 分片和清单永远放在同一个目录里，能解析出目录就等于允许清单把人往别处指。
 */
object MaterialManifestParser {
    /** 比这个新的清单本应用读不了，让它去失败，不要按半套字段猜。 */
    const val MAX_SUPPORTED_VERSION = 1

    private const val MAX_FILE = 120
    private const val SHA256_HEX = 64

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 读不懂、不成清单、版本比应用新——一律 null，调用方只说"清单用不了"。 */
    fun parse(text: String): MaterialManifest? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val version = intOf(root, "materialVersion") ?: return null
        if (version > MAX_SUPPORTED_VERSION) return null
        val packs = root["packs"] as? JsonArray ?: return null
        if (packs.isEmpty()) return null
        val refs = packs.map { item ->
            val obj = item as? JsonObject ?: return null
            val file = bareNameOf(obj) ?: return null
            val sha = shaOf(obj) ?: return null
            MaterialPackRef(file, intOf(obj, "words") ?: 0, sha)
        }
        if (refs.isEmpty()) return null
        return MaterialManifest(
            materialVersion = version,
            generatedAt = textOf(root, "generatedAt"),
            license = textOf(root, "license"),
            packs = refs,
        )
    }

    /** 只认裸文件名：带路径分隔符或 `..` 的一律当读不懂，不去解析它指向哪儿。 */
    private fun bareNameOf(obj: JsonObject): String? {
        val raw = textOf(obj, "file", "name").trim()
        if (raw.isEmpty() || raw.length > MAX_FILE) return null
        if (raw.any { it == '/' || it == '\\' } || raw.contains("..")) return null
        return raw
    }

    private fun shaOf(obj: JsonObject): String? {
        val raw = textOf(obj, "sha256", "sha").trim().lowercase()
        return raw.takeIf { it.length == SHA256_HEX && it.all(::isHexDigit) }
    }

    private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f'

    private fun textOf(obj: JsonObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()

    private fun intOf(obj: JsonObject, key: String): Int? = when (val item = obj[key]) {
        is JsonPrimitive -> item.intOrNull ?: item.contentOrNull?.trim()?.toIntOrNull()
        else -> null
    }
}
