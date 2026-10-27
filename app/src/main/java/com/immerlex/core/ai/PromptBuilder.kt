package com.immerlex.core.ai

/** One bank word as presented to the model. */
data class WordBrief(val id: Long, val word: String, val gloss: String, val state: String)

/** One earlier chapter, newest last, so the model can continue instead of restarting. */
data class RecapLine(val number: Int, val title: String, val summary: String)

data class ChapterRequest(
    val genre: String,
    val chapterNumber: Int,
    val targetHanChars: Int,
    val density: Density,
    val newWords: List<WordBrief>,
    val reviewWords: List<WordBrief>,
    val recaps: List<RecapLine> = emptyList(),
    val characters: List<String> = emptyList(),
) {
    val allWords: List<WordBrief> get() = newWords + reviewWords
}

/**
 * Versioned prompt templates. Bump [VERSION] whenever wording changes in a way
 * that alters output shape; the stored version lets old chapters be identified.
 */
object PromptBuilder {
    const val VERSION = 2

    private const val SCHEMA = """
{
  "title": "第N章 · 小标题",
  "summary": "80字以内的本章梗概，供后续章节保持连贯",
  "characters": ["落薇 · 皇后", "刘禧 · 皇帝近侍"],
  "paragraphs": [
    [ {"k":"t","v":"中文正文片段"}, {"k":"w","id":"123","en":"resist","zh":"忍住","ipa":"/rɪˈzɪst/","st":"new","def":"v. 忍住，抵挡","ex":"She could hardly resist laughing. 她几乎忍不住笑出来。"}, {"k":"t","v":"，继续的中文正文"} ],
    [ {"k":"t","v":"另一段纯中文正文"} ]
  ]
}
"""

    fun system(): String = """
你是"Immerlex"的小说生成引擎，写作语言为简体中文，目标读者是正在学英语的中文母语者。
你输出的必须是一个合法 JSON 对象，绝不输出 JSON 以外的任何文字、解释或 Markdown 代码块。
    """.trim()

    fun user(request: ChapterRequest): String {
        val r = request
        return """
【任务】写一篇${r.genre}风格的中文小说章节，编号第${r.chapterNumber}章，正文约 ${r.targetHanChars} 个汉字。

【词汇嵌入要求】
把下面给出的英文词原样嵌入中文句子中（形如"他终究 or 没有 resist"这类中英混排），每个词一次占一个段落片段的位置。
密度：每 1000 汉字嵌入 ${r.density.minPerKilo}-${r.density.maxPerKilo} 个英文词，即全章约 ${estimateWords(r)} 个词。
每个英文词最多重复 ${r.density.maxRepeat} 次。

生词（必须出现，且在上下文中能通过语境猜出含义，不要生硬）：
${renderWords(r.newWords)}

复习词（优先安排进情节，语境可宽松）：
${renderWords(r.reviewWords)}

【段落切分规则】
每段是 segment 数组。嵌入英文词必须写成独立的 k="w" 对象，其左右的中文字各成 k="t" 对象；
未嵌词的段落只放一个 k="t"。id 用上面给出的数字 id。
st 字段：生词填 "new"，复习词填 "lea" 或 "mas"。
zh 字段填该词在**本句语境**中的中文意思（不是词典义）；def 填词典义。

【连贯性】
${continuity(r)}

【输出格式】严格 JSON，键顺序与下面一致，不要输出注释、不要用 ``` 包裹：
$SCHEMA
        """.trimIndent()
    }

    fun repair(originalTask: String, issues: List<ValidationIssue>, previous: String): String = """
$originalTask

【上一次输出不合格】
问题：
${issues.joinToString("\n") { "- ${it.forRepair()}" }}
请修正后重新输出**完整** JSON（只输出 JSON，不要解释）。上次输出如下，供参考：
$previous
    """.trimIndent()

    fun estimateWords(r: ChapterRequest): Int =
        (r.targetHanChars * (r.density.minPerKilo + r.density.maxPerKilo) / 2000.0).toInt()

    private fun renderWords(words: List<WordBrief>): String =
        if (words.isEmpty()) {
            "（无）"
        } else {
            words.joinToString("\n") { "- id=${it.id} ${it.word}：${it.gloss.ifEmpty { "（无释义，请自行给出）" }}" }
        }

    private fun continuity(r: ChapterRequest): String =
        if (r.recaps.isEmpty() && r.characters.isEmpty()) {
            "本章为该类型的第一章，请建立人物与场景。"
        } else {
            buildString {
                if (r.characters.isNotEmpty()) {
                    appendLine("既有角色（必须沿用这些名字与身份，不得改姓换名）：${r.characters.joinToString("、")}")
                }
                if (r.recaps.isNotEmpty()) {
                    appendLine("前情提要（由旧到新）：")
                    r.recaps.forEach { appendLine("- 第${it.number}章《${it.title}》：${it.summary}") }
                }
                append("情节必须从上述内容自然续写，不要重启故事、不要失忆另起炉灶。characters 填本章全部登场角色（含新出场的），格式\"名字 · 身份\"。")
            }.trim()
        }
}
