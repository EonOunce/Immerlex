package com.immerlex.core.ai

/**
 * 短篇的腔调预设（AGENTS.md §2 的 v1.5 项）：只描述写法，不复制任何杂志样文。
 * 名字落库在 `chapters.genre`，重写短篇时据此找回同一个腔调。
 */
enum class ShortTone(val brief: String) {
    EVERYMAN("写一个普通人在具体处境里的小选择，结尾用一个动作或一句对话点题，不要哲理总结。"),
    LUSH("情绪浓度高、意象密集、句子有音乐感，但别堆砌形容词；结尾落在情绪的最高点。"),
    COLD("先给一个清晰、略带陌生的设定，情节按设定的规则推进，最后用一次冷处理的反转或留白收尾。"),
    ;

    companion object {
        fun of(name: String): ShortTone = entries.firstOrNull { it.name == name } ?: EVERYMAN
    }
}
