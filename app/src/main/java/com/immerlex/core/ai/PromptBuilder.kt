package com.immerlex.core.ai

import com.immerlex.core.bible.BibleSection
import com.immerlex.core.bible.CastLine
import com.immerlex.core.bible.StoryContext

/** One bank word as presented to the model. */
data class WordBrief(val id: Long, val word: String, val gloss: String, val state: String)

data class ChapterRequest(
    val genre: String,
    val chapterNumber: Int,
    val targetHanChars: Int,
    val density: Density,
    val newWords: List<WordBrief>,
    val reviewWords: List<WordBrief>,
    /** 三层连贯上下文：档案条目、人物状态、全书梗概（docs/adr/0005）。 */
    val story: StoryContext = StoryContext(),
    /** 短篇的写法；连载用不到，保持默认值。 */
    val shortPlan: ShortPlan = ShortPlan(),
) {
    val allWords: List<WordBrief> get() = newWords + reviewWords
}

/**
 * Versioned prompt templates. Bump [VERSION] whenever wording changes in a way
 * that alters output shape; the stored version lets old chapters be identified.
 */
object PromptBuilder {
    /** V3：连贯层从"最近 3 章"换成故事档案 + 人物状态表 + 全书梗概，并要求回报本章变化。 */
    const val VERSION = 3

    private const val SCHEMA = """
{
  "title": "第N章 · 小标题",
  "summary": "80字以内的本章梗概，供后续章节保持连贯",
  "characters": ["落薇 · 皇后", "刘禧 · 皇帝近侍"],
  "changes": [ {"who": "落薇", "what": "被放出冷宫，晋为贵妃，与皇帝达成交易"} ],
  "paragraphs": [
    [ {"k":"t","v":"中文正文片段"}, {"k":"w","id":"123","en":"resist","zh":"忍住","ipa":"/rɪˈzɪst/","st":"new","def":"v. 忍住，抵挡","ex":"She could hardly resist laughing. 她几乎忍不住笑出来。"}, {"k":"t","v":"，继续的中文正文"} ],
    [ {"k":"t","v":"另一段纯中文正文"} ]
  ]
}
"""

    private const val BIBLE_SCHEMA = """
{
  "tone": "全文基调，一句话",
  "protagonist": "主角与其追求的目标，一句话",
  "ending": "结局倾向，一句话",
  "forbidden": ["不许出现的设定 1", "不许出现的设定 2"],
  "cast": [ {"name": "落薇", "role": "皇后", "start": "被禁足冷宫，手里握着母亲留下的密信"} ]
}
"""

    /** 短篇没有连载章号，所以标题示例也换成一个普通短篇名。 */
    private const val SHORT_SCHEMA = """
{
  "title": "短篇标题",
  "summary": "",
  "characters": [],
  "changes": [],
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
${continuity(r.story, r.chapterNumber)}

【输出格式】严格 JSON，键顺序与下面一致，不要输出注释、不要用 ``` 包裹：
$SCHEMA
        """.trimIndent()
    }

    fun shortUser(request: ChapterRequest, plan: ShortPlan): String {
        val r = request
        val toneLine = if (plan.isCustomTone) {
            "腔调（读者自己写的，务必遵守）：${plan.toneBrief}"
        } else {
            "腔调：${plan.toneBrief}"
        }
        val subjectLine = if (plan.idea.isBlank()) {
            "- 写什么由你定：从今天要用的这些词里挑一个能装下它们的小故事。"
        } else {
            "- 读者指定了这篇写什么：${plan.idea}\n" +
                "  只展开读者给的这条线，不要另起题材；他没写到的细节由你补全。"
        }
        return """
【任务】写一篇单篇自足的中文短篇小说，约 ${r.targetHanChars} 个汉字（1500–2500 之间）。
目标读者是正在学英语的中文母语者。
$toneLine

【短篇规则】
$subjectLine
- 故事在本篇内开始、在本篇内结束：不要留续集钩子，不要出现"第一章""欲知后事"这类连载痕迹。
- title 是一个短篇标题，不带"第 N 章"。
- 不需要连载档案：summary 填空字符串 ""，characters 与 changes 填空数组 []。

【词汇嵌入要求】
把下面给出的英文词原样嵌入中文句子中（形如"他终究 or 没有 resist"这类中英混排），每个词一次占一个段落片段的位置。
每个词出现 1 次即可，最多 2 次；不要为了凑次数把同一个词反复塞进句子。
生词（必须出现，且在上下文中能通过语境猜出含义，不要生硬）：
${renderWords(r.newWords)}

复习词（优先安排进情节，语境可宽松）：
${renderWords(r.reviewWords)}

【段落切分规则】
每段是 segment 数组。嵌入英文词必须写成独立的 k="w" 对象，其左右的中文字各成 k="t" 对象；
未嵌词的段落只放一个 k="t"。id 用上面给出的数字 id。
st 字段：生词填 "new"，复习词填 "lea" 或 "mas"。
zh 字段填该词在**本句语境**中的中文意思（不是词典义）；def 填词典义。

【输出格式】严格 JSON，键顺序与下面一致，不要输出注释、不要用 ``` 包裹：
$SHORT_SCHEMA
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

    // ---- 开局展开：一段话 → 一份故事档案 ----

    fun bibleSystem(): String = """
你是"Immerlex"的故事档案生成引擎，服务对象是中文母语、正在学英语的读者。
你输出的必须是一个合法 JSON 对象，绝不输出 JSON 以外的任何文字、解释或 Markdown 代码块。
    """.trim()

    fun bibleUser(seed: String): String {
        val idea = seed.ifBlank { "（读者没写想法，按这类题材的常见套路给一份能直接开写的档案）" }
        return """
【任务】把下面这段读者随手写的想法，展开成一本中文小说的故事档案。读者可能只写一句"我想看宫斗"，也可能写一大段，都要展开成下面五栏。

读者的想法：
$idea

【要求】
- tone：全文基调（叙事腔调、情绪浓度），一句话。
- protagonist：主角是谁、在追求什么，一句话。
- ending：结局倾向，一句话；读者没暗示时按题材惯例给一个方向。
- forbidden：2–4 条"本书不许出现"的设定，要具体可执行，不要写"不要太俗"这类没法执行的。
- cast：3–6 个起始人物，start 填开篇时此人的处境与手里握着的东西，不要写未来剧情。
- 只展开读者给的线索：不要另起题材、不要塞进读者没提的主角名字；读者写过的细节原样保留。

【输出格式】严格 JSON，键顺序与下面一致，不要输出注释、不要用 ``` 包裹：
$BIBLE_SCHEMA
        """.trimIndent()
    }

    private fun renderWords(words: List<WordBrief>): String =
        if (words.isEmpty()) {
            "（无）"
        } else {
            words.joinToString("\n") { "- id=${it.id} ${it.word}：${it.gloss.ifEmpty { "（无释义，请自行给出）" }}" }
        }

    /**
     * 三层连贯上下文。读者的手写条目排在最前并要求务必遵守；模型回报的剧情只在
     * 档案里没写过时才起作用（docs/adr/0005 第 4 条）。
     */
    private fun continuity(story: StoryContext, chapterNumber: Int): String {
        if (story.isEmpty) return "本章为该类型的第一章，请建立人物与场景。"
        return buildString {
            if (story.entries.isNotEmpty()) {
                appendLine("【故事档案】（" + USER_NOTE + "）")
                story.entries.forEach { appendLine("- ${it.section.label()}：${it.text}${if (it.authoredByUser) USER_MARK else ""}") }
            }
            if (story.cast.isNotEmpty()) {
                appendLine("【人物当前状态】")
                story.cast.forEach { appendLine(castLine(it)) }
            }
            if (story.recaps.isNotEmpty()) {
                appendLine("【全书梗概（由旧到新）】")
                story.recaps.forEach { appendLine("- 第${it.number}章《${it.title}》：${it.summary}") }
            }
            appendLine()
            append(
                "情节必须从上述内容自然续写，不要重启故事、不要失忆另起炉灶；标了「务必遵守」的条目优先级高于任何剧情线索。" +
                    "characters 填本章全部登场角色（含新出场的），格式\"名字 · 身份\"；" +
                    "changes 只填本章里处境发生变化的人物，写清变成了什么，没变化就留空数组。本章是第$chapterNumber 章。",
            )
        }.trim()
    }

    private fun castLine(person: CastLine): String = buildString {
        append("- ${person.name}")
        if (person.role.isNotBlank()) append("（${person.role}）")
        append("：")
        append(person.status.ifBlank { "尚无状态记录" })
        if (person.authoredByUser) append(USER_MARK)
        if (person.conflictChapter > 0) {
            append("｜注意：第${person.conflictChapter}章把它写成了「${person.conflictText}」，请以读者设定为准，除非你打算在本章里正面处理这个矛盾")
        }
    }

    private fun BibleSection.label(): String = when (this) {
        BibleSection.TONE -> "基调"
        BibleSection.PROTAGONIST -> "主角与目标"
        BibleSection.ENDING -> "结局倾向"
        BibleSection.FORBIDDEN -> "禁止事项"
    }

    private const val USER_NOTE = "读者亲自写过的条目"
    private const val USER_MARK = "（读者设定，务必遵守）"
}
