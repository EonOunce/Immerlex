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
    /** v2：一个词的素材只问一回，所以要把轮换用的易混候选一次要够。 */
    const val VERSION = 2

    fun system(): String = """
        你是英语教学测验的出题助手。你只负责补全素材，不负责编排复习计划。
        只输出 JSON，不要解释，不要 Markdown 代码块。
    """.trimIndent()

    fun user(words: List<WordEntity>): String = buildJsonObject {
        put("task", "为下面的单词补全英文释义、中文例句和一组易混干扰项")
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
                "distractors 给 6 到 8 个同词性的英文词，带中文释义：既要长得像、又要意思不同，" +
                "优先给拼写或词形容易看错的（affect/effect、adapt/adopt）、近义词里用法不同的、" +
                "前缀后缀容易混的；不能出现原词本身，也不能出现意思完全相同的词；" +
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
                            add(buildJsonObject { put("word", "specimen"); put("zh", "标本") })
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
            val key = item.word.trim().lowercase()
            val word = byWord[key] ?: return@forEach
            val entry = EnrichedWord(
                definition = item.definition.trim().take(MAX_TEXT),
                example = item.example.trim().take(MAX_TEXT),
                distractors = item.distractors
                    .map { EnrichedDistractor(it.word.trim().take(MAX_WORD), it.zh.trim().take(MAX_GLOSS)) }
                    .filter { it.word.isNotBlank() && it.zh.isNotBlank() }
                    .filterNot { it.word.lowercase() == key }
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

    /** 存下来的是一整池可轮换的候选，不是这一道题要用的三个选项。 */
    private const val MAX_DISTRACTORS = 8
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
