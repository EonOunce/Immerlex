package com.immerlex.core.tricky

/**
 * 用户手动确认过的易错词。词条不依赖词库：阅读页遇到的词即使没入库，也能标成易错。
 * [gloss] 来自阅读卡片、词库或离线词典，可能为空。
 */
data class TrickyWord(val word: String, val gloss: String, val ipa: String)

/** 词卡上的易错区：是否已标记、已关联的易混词、可以一键加入的候选。 */
data class TrickyCard(
    val marked: Boolean,
    val links: List<TrickyWord>,
    val suggestions: List<TrickyWord>,
)

/** 打开某个易混词时需要的展示信息；[wordId] 为空表示它不在词库里。 */
data class TrickyDetail(
    val word: String,
    val gloss: String,
    val ipa: String,
    val definition: String,
    val wordId: Long?,
)

data class AddTrickyResult(val card: TrickyCard, val added: Boolean)
