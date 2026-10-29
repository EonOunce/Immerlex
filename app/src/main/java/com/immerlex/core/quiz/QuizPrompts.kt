package com.immerlex.core.quiz

import com.immerlex.core.db.WordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 测验素材补全的 prompt 版本；改问法就加一版，方便排查旧输出。 */
object QuizPrompts {
    const val VERSION = 1

    fun system(): String = """
        你是英语教学测验的出题助手。你只负责补全素材，不负责编排复习计划。
        只输出 JSON，不要解释，不要 Markdown 代码块。
    """.trimIndent()

    fun user(words: List<WordEntity>): String = buildJsonObject {
        put("task", "为下面的单词补全英文释义、中文例句和干扰项")
        put("version", VERSION)
        putJsonArray("words") {
            words.forEach { word ->
                add(
                    buildJsonObject {
                        put("word", word.word)
                        put("zh", word.translation)
                        put("definition", word.definition)
                    },
                )
            }
        }
        put(
            "rules",
            "definition 用简单英文解释这个词（不超过 30 词）；" +
                "example 写一句自然的中文句子，句子里必须原样出现该英文单词（不超过 50 字）；" +
                "distractors 给 2 到 3 个同词性的英文词，带中文释义，意思要明显不同、不能和原词同义；" +
                "每个词只出现一次；实在想不出就把该词的字段留空。",
        )
        putJsonObject("output") {
            put("format", "json")
            putJsonArray("items") {
                add(
                    buildJsonObject {
                        put("word", "example")
                        put("definition", "a short English definition")
                        put("example", "这句中文例句里出现了 example 这个词。")
                        putJsonArray("distractors") {
                            add(buildJsonObject { put("word", "sample"); put("zh", "样本") })
                            add(buildJsonObject { put("word", "instance"); put("zh", "实例") })
                        }
                    },
                )
            }
        }
    }.toString()
}

object QuizEnrichmentParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(raw: String, words: List<WordEntity>): QuizEnrichment {
        val text = com.immerlex.core.ai.ChapterParser.extractJson(raw) ?: return QuizEnrichment()
        val doc = runCatching { json.decodeFromString(EnrichmentDoc.serializer(), text) }.getOrNull()
            ?: return QuizEnrichment()
        val byWord = words.associateBy { it.word.trim().lowercase() }
        val entries = mutableMapOf<Long, EnrichedWord>()
        doc.items.forEach { item ->
            val word = byWord[item.word.trim().lowercase()] ?: return@forEach
            val entry = EnrichedWord(
                definition = item.definition.trim().take(MAX_TEXT),
                example = item.example.trim().take(MAX_TEXT),
                distractors = item.distractors
                    .map { EnrichedDistractor(it.word.trim().take(MAX_WORD), it.zh.trim().take(MAX_GLOSS)) }
                    .filter { it.word.isNotBlank() && it.zh.isNotBlank() }
                    .distinctBy { it.word.lowercase() }
                    .take(MAX_DISTRACTORS),
            )
            if (entry.definition.isNotBlank() || entry.example.isNotBlank() || entry.distractors.isNotEmpty()) {
                entries[word.id] = entry
            }
        }
        return QuizEnrichment(entries)
    }

    private const val MAX_TEXT = 200
    private const val MAX_WORD = 60
    private const val MAX_GLOSS = 80
    private const val MAX_DISTRACTORS = 3
}

@kotlinx.serialization.Serializable
private data class EnrichmentDoc(val items: List<EnrichmentItem> = emptyList())

@kotlinx.serialization.Serializable
private data class EnrichmentItem(
    val word: String = "",
    val definition: String = "",
    val example: String = "",
    val distractors: List<EnrichmentDistractor> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class EnrichmentDistractor(val word: String = "", val zh: String = "")
