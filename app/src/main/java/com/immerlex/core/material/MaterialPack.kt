package com.immerlex.core.material

import com.immerlex.core.db.QuizMaterialEntity
import com.immerlex.core.word.wordSurface
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 一个词的素材。[example] 可以是英文原句，也可以是中文句子里嵌着这个英文词——两种都能出挖空题。 */
data class MaterialEntry(
    val word: String,
    val definition: String = "",
    val example: String = "",
    val exampleZh: String = "",
    val confusables: List<Confusable> = emptyList(),
    /** 真实搭配（take measures / a reluctant smile），只给搭配题用，不单独排复习。 */
    val collocations: List<Collocation> = emptyList(),
) {
    /** 一个字段都没有的行没有存在价值：库里那一行只是"这个词已经处理过"的记号，空记号会让出题以为不用再问。 */
    val hasContent: Boolean
        get() = definition.isNotBlank() || example.isNotBlank() || exampleZh.isNotBlank() ||
            confusables.isNotEmpty() || collocations.isNotEmpty()
}

/** 易混候选。[zh] 留空时由导入的一方从词典补，补不上就丢掉这一个候选。 */
data class Confusable(val word: String, val zh: String = "")

/** 搭配短语。[zh] 同样允许留空交给词典补；[en] 必须真的含有这个词，否则是别的词的搭配。 */
data class Collocation(val en: String, val zh: String = "")

/** 校验闸门丢掉一条的三种理由，界面按这个报数字，不自己猜。 */
enum class DropReason { NO_WORD, NO_CONTENT, DUPLICATE }

/**
 * 读完整份包的结果：能用的行，每种理由各丢了多少行，以及有几条搭配因为不含该词被丢掉。
 * 搭配单独报数：它丢的是一条短语而不是一行词，混进 [dropped] 的话两边都说不清。
 */
data class PackRead(
    val entries: List<MaterialEntry>,
    val dropped: Map<DropReason, Int>,
    val badCollocations: Int = 0,
) {
    fun droppedTotal(): Int = dropped.values.sum()
}

/** 一份文件根本不成形状。技术细节（第几个字符崩了）留在调用方，中文在 app/ui。 */
enum class PackFailure { NOT_JSON, NOT_ARRAY, EMPTY }

/** [failure] 非空时 [read] 一定是空的；两者都到手才谈得上"丢了几条"。 */
data class PackParse(val failure: PackFailure?, val read: PackRead)

/**
 * 素材包的读与写。包是人从外面（另一个 AI）生成的，所以这里的规矩是：
 * **单条不合格只丢那一条**，整包照收；一份读不懂的文件要直说，不能当成"这个包是空的"。
 * 字段一律截到上限而不是报错——多出来的字是模型话痨，不是数据坏了。
 */
object MaterialPack {
    const val MAX_WORD = 48
    const val MAX_TEXT = 220
    const val MAX_DEFINITION = 320
    const val MAX_GLOSS = 60
    const val MAX_CONFUSABLES = 12
    const val MAX_COLLOCATIONS = 4

    /** 词面进库之前先洗过：只留字母和英文词里真会用的那几个符号。 */
    private val WORD = Regex("^[a-z][a-z' .@/-]{0,${MAX_WORD - 1}}$")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): PackParse {
        val root = runCatching { json.parseToJsonElement(text) }
            .getOrElse { return PackParse(PackFailure.NOT_JSON, PackRead(emptyList(), emptyMap())) }
        val array = when (root) {
            is JsonArray -> root
            // 有的生成器会把包写成 {"words": [...]}：认这一种，其余照样算读不懂。
            is JsonObject -> (root["words"] ?: root["data"]) as? JsonArray
                ?: return PackParse(PackFailure.NOT_ARRAY, PackRead(emptyList(), emptyMap()))

            else -> return PackParse(PackFailure.NOT_ARRAY, PackRead(emptyList(), emptyMap()))
        }
        if (array.isEmpty()) return PackParse(PackFailure.EMPTY, PackRead(emptyList(), emptyMap()))
        val entries = ArrayList<MaterialEntry>(array.size)
        val dropped = mutableMapOf<DropReason, Int>()
        val seen = mutableSetOf<String>()
        var badCollocations = 0
        array.forEach { element ->
            val picked = entryOf(element)
            badCollocations += picked.badCollocations
            val entry = picked.entry
            when {
                entry == null -> bump(dropped, DropReason.NO_WORD)
                !entry.hasContent -> bump(dropped, DropReason.NO_CONTENT)
                !seen.add(entry.word) -> bump(dropped, DropReason.DUPLICATE)
                else -> entries += entry
            }
        }
        return PackParse(null, PackRead(entries, dropped, badCollocations))
    }

    /** 把库里的素材写成一份可以拿去另一台机器导入的包；字段名与 [parse] 认的完全一致。 */
    fun render(rows: List<QuizMaterialEntity>): String {
        val items = rows.map { row ->
            JsonObject(
                mapOf(
                    "word" to jsonValue(row.word),
                    "definition" to jsonValue(row.definition),
                    "example" to jsonValue(row.example),
                    "exampleZh" to jsonValue(row.exampleZh),
                    "distractors" to JsonArray(confusablesOf(row.distractors).map(::confusableValue)),
                    "collocations" to JsonArray(collocationsOf(row.collocations).map(::collocationValue)),
                ),
            )
        }
        return json.encodeToString(JsonArray.serializer(), JsonArray(items))
    }

    /** 一条词条读出来的样子，和它名下有几条搭配没能通过校验。 */
    private data class Picked(val entry: MaterialEntry?, val badCollocations: Int = 0)

    private fun entryOf(element: JsonElement): Picked {
        val obj = element as? JsonObject ?: return Picked(null)
        val surface = wordSurface(stringOf(obj, "word"))
        // 带空格的多词短语（a few、in case of）也是考研词，但只有符号没有字母的不算词。
        if (surface.isEmpty() || !WORD.matches(surface) || !surface.any { it in 'a'..'z' }) return Picked(null)
        val collocations = collocationsOf(obj["collocations"] ?: obj["collocation"], surface)
        return Picked(
            MaterialEntry(
                word = surface,
                definition = clip(stringOf(obj, "definition"), MAX_DEFINITION),
                example = clip(stringOf(obj, "example"), MAX_TEXT),
                exampleZh = clip(zhOf(obj), MAX_TEXT),
                confusables = confusablesOf(obj["distractors"] ?: obj["confusables"]).take(MAX_CONFUSABLES),
                collocations = collocations.kept.take(MAX_COLLOCATIONS),
            ),
            badCollocations = collocations.dropped,
        )
    }

    /**
     * 搭配必须真的含有这个词：包一旦把 make 的搭配写到 take 名下，搭配题就会拿一个
     * 根本用不上的短语当正确答案，比没有这道题更糟。`measures` 里含 `measure`，追加式变形靠子串就让过。
     *
     * 被这一条刷掉的短语要数出来：那是包写错了，用户拿去让生成方改，不该无声无息地少一条。
     */
    private fun collocationsOf(element: JsonElement?, word: String): Collocations {
        val array = element as? JsonArray ?: return Collocations(emptyList(), 0)
        var bad = 0
        val kept = array.mapNotNull { item ->
            val (en, zh) = when (item) {
                is JsonPrimitive -> item.contentOrNull.orEmpty() to ""
                is JsonObject -> stringOf(item, "en", "phrase", "text", "word") to
                    stringOf(item, "zh", "translation", "gloss")

                else -> "" to ""
            }
            val phrase = en.trim().replace(Regex("\\s+"), " ").take(MAX_TEXT)
            val valid = phrase.length >= word.length + 1 && phrase.lowercase().contains(word)
            if (!valid) {
                bad++
                return@mapNotNull null
            }
            Collocation(phrase, clip(zh, MAX_GLOSS))
        }.distinctBy { it.en.lowercase() }.toList()
        return Collocations(kept, bad)
    }

    private data class Collocations(val kept: List<Collocation>, val dropped: Int)

    /** 候选池里既认 `{"word":..,"zh":..}`，也认直接写一个字符串——省得包把词典里有的释义再抄一遍。 */
    private fun confusablesOf(element: JsonElement?): List<Confusable> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> {
                    val surface = wordSurface(item.contentOrNull.orEmpty())
                    surface.takeIf { it != "" && WORD.matches(it) && it.any { c -> c in 'a'..'z' } }?.let { Confusable(it) }
                }

                is JsonObject -> {
                    val surface = wordSurface(stringOf(item, "word", "w"))
                    val gloss = clip(stringOf(item, "zh", "translation", "gloss"), MAX_GLOSS)
                    surface.takeIf { it != "" && WORD.matches(it) && it.any { c -> c in 'a'..'z' } }
                        ?.let { Confusable(it, gloss) }
                }

                else -> null
            }
        }.distinctBy { it.word }.toList()
    }

    /** 库里那一行读回成同一个形状：界面上的词典卡和包里的词没有两副面孔。 */
    fun entryOf(row: QuizMaterialEntity): MaterialEntry = MaterialEntry(
        word = row.word,
        definition = row.definition,
        example = row.example,
        exampleZh = row.exampleZh,
        confusables = confusablesOf(row.distractors),
        collocations = collocationsOf(row.collocations),
    )

    /** 库里存的那一小段 `[{"w":"","zh":""}]` 读回来：`core.quiz` 写进去的就是这个形状。 */
    fun confusablesOf(stored: String): List<Confusable> =
        runCatching { json.parseToJsonElement(stored).jsonArray }
            .getOrNull()
            .orEmpty()
            .mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val word = textOf(obj, "w").ifBlank { textOf(obj, "word") }
                word.takeIf { it.isNotBlank() }?.let { Confusable(it, textOf(obj, "zh")) }
            }

    /** 搭配那一列与易混池同一个形状，只是字段名按短语读：`w` / `en` / `phrase` 都认。 */
    fun collocationsOf(stored: String): List<Collocation> =
        runCatching { json.parseToJsonElement(stored).jsonArray }
            .getOrNull()
            .orEmpty()
            .mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val en = textOf(obj, "w").ifBlank { textOf(obj, "en") }.ifBlank { textOf(obj, "phrase") }
                en.takeIf { it.isNotBlank() }?.let { Collocation(it, textOf(obj, "zh")) }
            }

    private fun confusableValue(item: Confusable): JsonElement =
        JsonObject(mapOf("word" to jsonValue(item.word), "zh" to jsonValue(item.zh)))

    private fun collocationValue(item: Collocation): JsonElement =
        JsonObject(mapOf("en" to jsonValue(item.en), "zh" to jsonValue(item.zh)))

    /** 例句的中文对照只认明确写了这一件事的键：`translation` 是词义，不是句意，不能拿来充当。 */
    private fun zhOf(obj: JsonObject): String =
        stringOf(obj, "exampleZh").ifBlank { stringOf(obj, "exampleCn") }.ifBlank { stringOf(obj, "example_zh") }

    private fun stringOf(obj: JsonObject, vararg keys: String): String =
        keys.firstOrNull { obj[it] != null }?.let { textOf(obj, it) }.orEmpty()

    private fun textOf(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun jsonValue(text: String) = JsonPrimitive(text)

    private fun bump(dropped: MutableMap<DropReason, Int>, reason: DropReason) {
        dropped[reason] = (dropped[reason] ?: 0) + 1
    }

    private fun clip(text: String, max: Int): String = text.trim().take(max)
}
