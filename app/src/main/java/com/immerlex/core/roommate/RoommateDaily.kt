package com.immerlex.core.roommate

data class GeneratedEpisode(
    val line: String,
    val choiceA: String,
    val choiceB: String,
    val habit: String?,
    val assetTag: String?,
)

data class GeneratedWork(val title: String, val body: String)

/**
 * 一天一包内容。生成只负责写这些文字；价格、能不能买、菜谱何时现身
 * 都由 [OfferPolicy] 与 [RoommateService] 按事实决定。
 */
data class GiftPlan(
    val name: String,
    val story: String,
    val reaction: String,
    val followUp: String,
    val tier: String,
)

data class StallPlan(
    val name: String,
    val story: String,
    val reaction: String,
)

data class RecipePlan(
    /** 需要先买齐的礼物下标（0..2），同一天有效。 */
    val giftIndices: List<Int>,
    val name: String,
    val story: String,
    /** 买齐后第一口免费时它说的话。 */
    val freeLine: String,
    /** 之后想再吃一份时买下的反应。 */
    val restLine: String,
)

data class PetGiftPlan(
    val name: String,
    val story: String,
    val line: String,
)

data class NearPlan(
    val name: String,
    val story: String,
    val reaction: String,
    val followUp: String,
)

data class DailyPack(
    val episode: GeneratedEpisode,
    val gifts: List<GiftPlan>,
    val stall: List<StallPlan>,
    val recipe: RecipePlan?,
    val petGift: PetGiftPlan?,
    val coquetry: List<String>,
    val near: NearPlan?,
)

/** 它送过的东西，按时间倒序展示在「它送的」里。 */
data class PetGiftView(
    val id: Long,
    val dayKey: Long,
    val name: String,
    val story: String,
    val line: String,
    val cause: String,
)
