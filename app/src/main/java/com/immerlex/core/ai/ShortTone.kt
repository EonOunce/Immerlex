package com.immerlex.core.ai

/**
 * 短篇的腔调预设（AGENTS.md §2 的 v1.5 项）：只描述写法，不复制任何杂志样文。
 * 名字落库在 `chapters.genre`，重写短篇时据此找回同一个腔调。
 */
enum class ShortTone(val brief: String) {
    EVERYMAN(
        "写一个普通人在一件具体小事里的选择：开头三句内落到具体场景和他的麻烦，" +
            "中段用一个小转折逼他做决定，结尾用一个动作或一句对话点题。" +
            "语言简洁温暖，不写哲理总结、不喊励志口号，也不刻意制造凄惨。",
    ),
    LUSH(
        "写一段青春或都市里的情感错过：用第一人称或贴身的第三人称，把喜欢、误会与遗憾" +
            "落进具体物件和场景（校服、车站、旧短信）。句子可以有画面和音乐感，形容词要少而准；" +
            "结尾停在没说出口的话或一个反复出现的意象上，不解释结局。",
    ),
    COLD(
        "从一个一句话能说清的核心设定出发，只给一个点子，从事件中间切入；" +
            "设定靠人物的操作和后果露出来，不写说明文。人物要在规则里做一次有代价的选择，" +
            "结尾冷处理：反转、留白或一个冷静的后果，不总结道理。",
    ),
    ;

    companion object {
        fun of(name: String): ShortTone = entries.firstOrNull { it.name == name } ?: EVERYMAN
    }
}

/** 用户自己写腔调时，`chapters.genre` 里放这个键，原话在 `chapters.toneText`。 */
const val CUSTOM_TONE = "custom"

/**
 * 一篇短篇的写法：腔调（预设，或用户自己写的一句）加上"这篇想写什么"。
 * 三个字段一起落库，重写同一篇时原样喂回模型——再点一次重写不该换成另一篇文章。
 */
data class ShortPlan(
    val tone: ShortTone = ShortTone.EVERYMAN,
    val toneText: String = "",
    val idea: String = "",
) {
    /** 自己写的腔调优先于预设。 */
    val toneBrief: String get() = toneText.ifBlank { tone.brief }

    /** 自己写了腔调就不再是预设名，落库时用 [CUSTOM_TONE] 占位。 */
    val isCustomTone: Boolean get() = toneText.isNotBlank()

    val genreKey: String get() = if (isCustomTone) CUSTOM_TONE else tone.name

    companion object {
        /** 从落库的三列复原写法。 */
        fun of(genre: String, toneText: String, idea: String): ShortPlan =
            ShortPlan(tone = ShortTone.of(genre), toneText = toneText, idea = idea)
    }
}
